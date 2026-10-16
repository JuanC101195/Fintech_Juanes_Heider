package co.financiera.cartera.contabilidad.reglas;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** Panel contable AB12:AE20 de la hoja Caso8_PagoInferior. */
class PagoInferiorTest {

    private static final Offset<BigDecimal> UN_PESO = Offset.offset(BigDecimal.ONE);

    private final List<Movimiento> motor = new MotorCartera().ejecutar(
            new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel().conPlazoLimite(100))
                    .en(43, new PagoParcial(bd("0.4"), 0)));

    private Movimiento periodo(int p) {
        return motor.stream().filter(m -> m.periodo() == p).findFirst().orElseThrow();
    }

    private static BigDecimal debito(Asiento a, Cuenta c) {
        return a.lineas().stream().filter(l -> l.cuenta() == c).map(Asiento.Linea::debito)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal credito(Asiento a, Cuenta c) {
        return a.lineas().stream().filter(l -> l.cuenta() == c).map(Asiento.Linea::credito)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void reproduceElPanelContableDelEvento() {
        Asiento a = new PagoInferior().contabilizar(periodo(43)).orElseThrow();

        assertThat(debito(a, Cuenta.CAJA)).isCloseTo(bd("65376.17859587236"), UN_PESO); // AI14
        assertThat(credito(a, Cuenta.CXC_INTERES_CORRIENTE)).isCloseTo(bd("52989.932797465175"), UN_PESO); // AJ15
        assertThat(credito(a, Cuenta.CXP_SERVICIOS_TERCEROS)).isCloseTo(bd("3406.3846153846157"), UN_PESO); // AJ17
        assertThat(debito(a, Cuenta.CARTERA_VENCIDA)).isCloseTo(bd("38436.48020150085"), UN_PESO); // AI18
        // AJ16 (capital 8.979,86) + AJ19 (contrapartida de la reclasificación 38.436,48).
        assertThat(credito(a, Cuenta.CARTERA_VIGENTE)).isCloseTo(bd("47416.341384524423"), UN_PESO);
        assertThat(credito(a, Cuenta.CXC_INTERES_MORA)).isZero();
        assertThat(credito(a, Cuenta.CXC_GASTOS_COBRANZA)).isZero();
        // AI20 = AJ20 = 103.812,66 y AK20 = 0.
        assertThat(a.totalDebitos()).isCloseTo(bd("103812.65879737321"), UN_PESO);
        assertThat(a.cuadra()).isTrue();
    }

    @Test
    void soloAplicaALaSemanaDelPagoInferior() {
        PagoInferior regla = new PagoInferior();
        assertThat(motor).filteredOn(PagoInferior::aplica).extracting(Movimiento::periodo).containsExactly(43);
        assertThat(motor).filteredOn(m -> m.periodo() != 43)
                .allSatisfy(m -> assertThat(regla.contabilizar(m)).isEmpty());
    }

    /**
     * Guarda contra doble asiento: con todas las reglas registradas, la Caja de la semana del pago
     * inferior se debita una sola vez (si la regla de recaudo normal no excluye
     * {@link PagoInferior#aplica}, esta prueba falla).
     */
    @Test
    void conTodasLasReglasLaCajaDelEventoSeDebitaUnaSolaVez() {
        List<Asiento> asientos = Contabilizador.estandar().contabilizar(periodo(43));
        BigDecimal caja = asientos.stream().map(a -> debito(a, Cuenta.CAJA)).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(caja).isCloseTo(bd("65376.17859587236"), UN_PESO);
        assertThat(Contabilizador.estandar().contabilizar(motor)).allSatisfy(a -> assertThat(a.cuadra()).isTrue());
    }
}
