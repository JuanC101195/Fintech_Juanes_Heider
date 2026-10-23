package co.financiera.cartera.nucleo;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * D26 / D44: valor diario de la cobranza con vigencias; cada crédito guarda el vigente cuando se origina y un cambio
 * posterior de la tabla no lo afecta. Solo lo cambia un administrador y queda en la bitácora (D45).
 */
class VigenciasCobranzaTest {

    private static final LocalDate ENERO = LocalDate.of(2026, 1, 1);
    private static final LocalDate JULIO = LocalDate.of(2026, 7, 1);
    private static final VigenciasCobranza TABLA = VigenciasCobranza.vacia()
            .conVigencia(ENERO, bd("3000"))
            .conVigencia(JULIO, bd("3500"));

    @Test
    void elValorVigenteEsElDeLaUltimaVigenciaQueYaEmpezo() {
        assertThat(TABLA.valorEn(LocalDate.of(2026, 3, 15))).contains(bd("3000"));
        assertThat(TABLA.valorEn(LocalDate.of(2026, 6, 30))).contains(bd("3000"));
        assertThat(TABLA.valorEn(JULIO)).contains(bd("3500"));
        assertThat(TABLA.valorEn(LocalDate.of(2027, 2, 1))).contains(bd("3500"));
        // Antes de la primera vigencia no hay valor en la tabla: se usa el por defecto.
        assertThat(TABLA.valorEn(LocalDate.of(2025, 12, 31))).isEmpty();
        assertThat(TABLA.valorEn(LocalDate.of(2025, 12, 31), bd("3000"))).isEqualByComparingTo("3000");
    }

    @Test
    void elCreditoGuardaElValorDeSuOriginacionYUnCambioPosteriorNoLoAfecta() {
        ParametrosProducto base = ParametrosProducto.replicaExcel();
        ParametrosProducto deMarzo = base.conCobranzaVigente(TABLA, LocalDate.of(2026, 3, 10));
        ParametrosProducto deAgosto = base.conCobranzaVigente(TABLA, LocalDate.of(2026, 8, 10));
        assertThat(deMarzo.cobranzaDiaria()).isEqualByComparingTo("3000");
        assertThat(deAgosto.cobranzaDiaria()).isEqualByComparingTo("3500");

        // El administrador sube el valor desde octubre: la tabla nueva es otra; el crédito de agosto no cambia.
        VigenciasCobranza nueva = TABLA.conVigencia(LocalDate.of(2026, 10, 1), bd("4000"));
        assertThat(deAgosto.cobranzaDiaria()).isEqualByComparingTo("3500");
        assertThat(TABLA.valorEn(LocalDate.of(2026, 11, 1))).contains(bd("3500"));
        assertThat(nueva.valorEn(LocalDate.of(2026, 11, 1))).contains(bd("4000"));
        assertThat(base.conCobranzaVigente(nueva, LocalDate.of(2026, 11, 1)).cobranzaDiaria()).isEqualByComparingTo("4000");
    }

    @Test
    void sinTablaQuedaElValorPorDefecto() {
        ParametrosProducto base = ParametrosProducto.replicaExcel();
        assertThat(base.conCobranzaVigente(VigenciasCobranza.vacia(), JULIO)).isSameAs(base);
        assertThat(base.conCobranzaVigente(TABLA, LocalDate.of(2025, 6, 1))).isSameAs(base);
        assertThat(base.cobranzaDiaria()).isEqualByComparingTo("3000");
    }

    /**
     * En el motor la cobranza usa el valor que el crédito guardó. A mano: un pago con 5 días de atraso causa 5 × 3.500
     * = 17.500 en un crédito de agosto, aunque la tabla ya diga 4.000 desde octubre.
     */
    @Test
    void elMotorCobraConElValorGuardadoEnElCredito() {
        ParametrosProducto deAgosto = ParametrosProducto.replicaExcel().conCobranzaVigente(TABLA, LocalDate.of(2026, 8, 10));
        TABLA.conVigencia(LocalDate.of(2026, 10, 1), bd("4000"));
        List<Movimiento> filas = new MotorCartera().ejecutar(
                new Escenario(Operacion.ejemploExcel(), deAgosto).en(3, new PagoContractual(5)));
        Movimiento tercera = filas.stream().filter(m -> m.periodo() == 3).findFirst().orElseThrow();
        assertThat(tercera.cobranzaCausada()).isEqualByComparingTo("17500");
    }

    @Test
    void lasVigenciasSoloSeAgreganHaciaAdelante() {
        assertThatThrownBy(() -> TABLA.conVigencia(JULIO, bd("5000"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TABLA.conVigencia(LocalDate.of(2026, 3, 1), bd("5000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TABLA.conVigencia(LocalDate.of(2027, 1, 1), bd("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TABLA.vigencias().put(LocalDate.of(2027, 1, 1), BigDecimal.TEN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void elCambioDelAdministradorQuedaEnLaBitacora() {
        List<CambioParametro> bitacora = new ArrayList<>();
        LocalDateTime cuando = LocalDateTime.of(2026, 9, 20, 10, 30);
        VigenciasCobranza nueva = TABLA.cambiar(LocalDate.of(2026, 10, 1), bd("4000"), "director.financiero", cuando,
                "Ajuste de la política de cobranza", bitacora::add);
        assertThat(nueva.valorEn(LocalDate.of(2026, 10, 1))).contains(bd("4000"));
        assertThat(bitacora).containsExactly(new CambioParametro("cobranzaDiaria", LocalDate.of(2026, 10, 1), bd("3500"),
                bd("4000"), "director.financiero", cuando, "Ajuste de la política de cobranza"));
    }

    @Test
    void sinAdministradorOJustificacionORetroactivoNoSeCambiaNiSeRegistra() {
        List<CambioParametro> bitacora = new ArrayList<>();
        LocalDateTime cuando = LocalDateTime.of(2026, 9, 20, 10, 30);
        LocalDate octubre = LocalDate.of(2026, 10, 1);
        assertThatThrownBy(() -> TABLA.cambiar(octubre, bd("4000"), " ", cuando, "motivo", bitacora::add))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TABLA.cambiar(octubre, bd("4000"), "admin", cuando, "", bitacora::add))
                .isInstanceOf(IllegalArgumentException.class);
        // Registrado el 20-sep con vigencia desde el 1-sep: retroactivo.
        assertThatThrownBy(() -> TABLA.cambiar(LocalDate.of(2026, 9, 1), bd("4000"), "admin", cuando, "motivo",
                bitacora::add)).isInstanceOf(IllegalArgumentException.class);
        assertThat(bitacora).isEmpty();
        // La primera vigencia de una tabla vacía no tenía valor anterior.
        VigenciasCobranza.vacia().cambiar(octubre, bd("3000"), "admin", cuando, "valor inicial", bitacora::add);
        assertThat(bitacora.get(0).valorAnterior()).isNull();
        assertThat(TABLA.cambiar(octubre, bd("4000"), "admin", cuando, "motivo", BitacoraParametros.NINGUNA)
                .valorEn(octubre)).contains(bd("4000"));
    }
}
