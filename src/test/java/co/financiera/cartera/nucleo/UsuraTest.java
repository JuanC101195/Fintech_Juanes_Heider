package co.financiera.cartera.nucleo;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** {@link Usura#tasaAjustada(BigDecimal, BigDecimal, BigDecimal)}: T2 y T4, Diego 2026-10-20 (R-2.11 a R-2.13). */
class UsuraTest {

    private static final BigDecimal UN_PUNTO = BigDecimal.ONE;

    @Test
    void siLaTasaSuperaLaUsuraQuedaEnUsuraMenosElMargen() {
        assertThat(Usura.tasaAjustada(bd("0.8732"), bd("0.80"), UN_PUNTO)).isEqualByComparingTo("0.79");
        assertThat(Usura.tasaAjustada(bd("0.8732"), bd("0.80"), bd("0.5"))).isEqualByComparingTo("0.795");
        assertThat(Usura.tasaAjustada(bd("0.8732"), bd("0.80"), bd("2"))).isEqualByComparingTo("0.78");
        assertThat(Usura.tasaAjustada(bd("0.8732"), bd("0.80"), BigDecimal.ZERO)).isEqualByComparingTo("0.80");
    }

    @Test
    void siNoLaSuperaNoCambia() {
        assertThat(Usura.tasaAjustada(bd("0.8732"), bd("0.90"), UN_PUNTO)).isEqualByComparingTo("0.8732");
        // Cobrar exactamente la usura es legal.
        assertThat(Usura.tasaAjustada(bd("0.80"), bd("0.80"), UN_PUNTO)).isEqualByComparingTo("0.80");
    }

    @Test
    void siLaUsuraSubeLaTasaAjustadaNoVuelveASubir() {
        BigDecimal ajustada = Usura.tasaAjustada(bd("0.8732"), bd("0.80"), UN_PUNTO);
        assertThat(Usura.tasaAjustada(ajustada, bd("0.95"), UN_PUNTO)).isEqualByComparingTo("0.79");
    }

    @Test
    void siLaUsuraBajaOtraVezSeAjustaConElMismoMargen() {
        BigDecimal ajustada = Usura.tasaAjustada(bd("0.8732"), bd("0.80"), UN_PUNTO);
        assertThat(Usura.tasaAjustada(ajustada, bd("0.785"), UN_PUNTO)).isEqualByComparingTo("0.775");
        // Una baja que la deja todavía por debajo de la usura no la toca.
        assertThat(Usura.tasaAjustada(ajustada, bd("0.795"), UN_PUNTO)).isEqualByComparingTo("0.79");
    }

    @Test
    void coincideConElAjusteDeUnPuntoAnterior() {
        assertThat(Usura.tasaAjustada(bd("0.8732"), bd("0.81"), UN_PUNTO))
                .isEqualByComparingTo(Usura.tasaAjustada(bd("0.81")));
    }

    @Test
    void rechazaMargenesNegativosYTasasNoPositivas() {
        BigDecimal pactada = bd("0.8732");
        BigDecimal usura = bd("0.80");
        BigDecimal negativo = bd("-1");
        BigDecimal cero = BigDecimal.ZERO;
        BigDecimal muyBaja = bd("0.005");
        assertThatThrownBy(() -> Usura.tasaAjustada(pactada, usura, negativo)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Usura.tasaAjustada(cero, usura, UN_PUNTO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Usura.tasaAjustada(pactada, muyBaja, UN_PUNTO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Usura.tasaAjustada(pactada, usura, null)).isInstanceOf(NullPointerException.class);
    }
}
