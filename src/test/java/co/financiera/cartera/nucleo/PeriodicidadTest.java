package co.financiera.cartera.nucleo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** ADR 0002, paso 1 · Catálogo de periodicidades (Inputs ⑤ y respuestas PPER de Diego). */
class PeriodicidadTest {

    @Test
    void elCatalogoEsSemanalQuincenalYMensualSinDiaria() {
        assertThat(Periodicidad.values())
                .containsExactly(Periodicidad.SEMANAL, Periodicidad.QUINCENAL, Periodicidad.MENSUAL);
    }

    @ParameterizedTest
    @CsvSource({
            "SEMANAL,   52, 7,  semanal,   cuotas semanales,   104, 100, 120",
            "QUINCENAL, 24, 15, quincenal, cuotas quincenales,  48,  46,  55",
            "MENSUAL,   12, 30, mensual,   cuotas mensuales,    24,  23,  27"})
    void atributosYConversionesConRedondeoHaciaAbajo(Periodicidad per, int ppa, int dias, String adjetivo, String unidad,
            int cuotas24Meses, int periodos100Semanas, int periodos120Semanas) {
        assertThat(per.periodosPorAnio()).isEqualTo(ppa);
        assertThat(per.diasNominales()).isEqualTo(dias);
        assertThat(per.adjetivo()).isEqualTo(adjetivo);
        assertThat(per.unidadCuota()).isEqualTo(unidad);
        assertThat(per.cuotasEnMeses(24)).isEqualTo(cuotas24Meses);
        assertThat(per.periodosDesdeSemanas(100)).isEqualTo(periodos100Semanas);
        assertThat(per.periodosDesdeSemanas(120)).isEqualTo(periodos120Semanas);
        assertThat(per.periodosPorMes()).isEqualByComparingTo(Calc.dividir(Calc.bd(ppa), Calc.bd(12)));
    }

    @Test
    void sieteMesesSemanalesSeRedondeanHaciaAbajo() {
        // 7 × 52 / 12 = 30,33 → 30 (PPER-3, Inputs B15).
        assertThat(Periodicidad.SEMANAL.cuotasEnMeses(7)).isEqualTo(30);
        assertThat(Periodicidad.QUINCENAL.cuotasEnMeses(7)).isEqualTo(14);
        assertThat(Periodicidad.MENSUAL.cuotasEnMeses(7)).isEqualTo(7);
    }

    @Test
    void elMensajeDeCuotasFueraDeRangoUsaLaUnidadDeLaPeriodicidad() {
        assertThatThrownBy(() -> ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.QUINCENAL, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cuotas quincenales");
    }
}
