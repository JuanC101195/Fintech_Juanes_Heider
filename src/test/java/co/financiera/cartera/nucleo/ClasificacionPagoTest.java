package co.financiera.cartera.nucleo;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * ABN-4 (Diego, ronda 3, 2026-10-20, pregunta ABN-4): el valor exacto de una cuota ya causada es pago
 * corriente aunque llegue antes del martes; lo que lo supere es abono extra. Ver docs/reglas/caso-13.md (R-13.8).
 */
class ClasificacionPagoTest {

    /** Cuota total de la réplica del Excel: cuota financiera 100.406,27 + servicios 63.034,17. */
    private static final BigDecimal CUOTA_TOTAL = bd("163440.45");

    @Test
    void elValorExactoDeLaCuotaCausadaEsPagoCorriente() {
        ClasificacionPago c = ClasificacionPago.clasificar(CUOTA_TOTAL, CUOTA_TOTAL);
        assertThat(c.pagoCorriente()).isEqualByComparingTo(CUOTA_TOTAL);
        assertThat(c.abonoExtra()).isZero();
        assertThat(c.tieneAbonoExtra()).isFalse();
    }

    @Test
    void loQueSuperaLaCuotaEsAbonoExtra() {
        ClasificacionPago c = ClasificacionPago.clasificar(bd("663440.45"), CUOTA_TOTAL);
        assertThat(c.pagoCorriente()).isEqualByComparingTo(CUOTA_TOTAL);
        assertThat(c.abonoExtra()).isEqualByComparingTo("500000");
        assertThat(c.tieneAbonoExtra()).isTrue();
    }

    @Test
    void unPagoMenorQueLaCuotaEsTodoPagoCorriente() {
        ClasificacionPago c = ClasificacionPago.clasificar(bd("50000"), CUOTA_TOTAL);
        assertThat(c.pagoCorriente()).isEqualByComparingTo("50000");
        assertThat(c.abonoExtra()).isZero();
    }

    @Test
    void conPendientesLoExigibleIncluyeLoAtrasado() {
        BigDecimal exigible = Calc.sumar(CUOTA_TOTAL, bd("120000"));
        ClasificacionPago c = ClasificacionPago.clasificar(bd("300000"), exigible);
        assertThat(c.pagoCorriente()).isEqualByComparingTo(exigible);
        assertThat(c.abonoExtra()).isEqualByComparingTo("16559.55");
    }

    @Test
    void sinCuotaCausadaTodoEsAbonoExtra() {
        ClasificacionPago c = ClasificacionPago.clasificar(bd("200000"), BigDecimal.ZERO);
        assertThat(c.pagoCorriente()).isZero();
        assertThat(c.abonoExtra()).isEqualByComparingTo("200000");
    }

    @Test
    void lasDiferenciasDeRedondeoNoSonAbonoExtra() {
        ClasificacionPago c = ClasificacionPago.clasificar(bd("163440.454"), CUOTA_TOTAL);
        assertThat(c.abonoExtra()).isZero();
        assertThat(c.pagoCorriente()).isEqualByComparingTo("163440.454");
    }

    @Test
    void rechazaValoresNegativos() {
        BigDecimal menosUno = bd("-1");
        assertThatThrownBy(() -> ClasificacionPago.clasificar(menosUno, CUOTA_TOTAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClasificacionPago.clasificar(CUOTA_TOTAL, menosUno))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
