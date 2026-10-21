package co.financiera.cartera.nucleo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.financiera.cartera.simulaciones.EscenariosExcel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @CsvSource({"SEMANAL, 104, 120", "QUINCENAL, 48, 55", "MENSUAL, 24, 27"})
    void elPlazoSePactaEnCuotasConMaximoDe24MesesYElLimiteVaEnPeriodos(Periodicidad per, int maximo, int limite) {
        ParametrosProducto p = ParametrosProducto.de(LineaCredito.CPPU, per, maximo);
        assertThat(p.periodicidad()).isEqualTo(per);
        assertThat(p.plazoLimitePeriodos()).isEqualTo(limite);
        assertThatThrownBy(() -> ParametrosProducto.de(LineaCredito.CPPU, per, maximo + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(per.unidadCuota());
        // La línea ORDINARIO permite 36 meses, pero ningún crédito pasa de 24 (PPER-2).
        assertThatThrownBy(() -> ParametrosProducto.de(LineaCredito.ORDINARIO, per, maximo + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elPlazoEnMesesSeConvierteACuotasHaciaAbajo() {
        assertThat(ParametrosProducto.deMeses(LineaCredito.CPPU, Periodicidad.SEMANAL, 7).numeroCuotas()).isEqualTo(30);
        assertThat(ParametrosProducto.deMeses(LineaCredito.CPPU, Periodicidad.QUINCENAL, 24).numeroCuotas()).isEqualTo(48);
        assertThat(ParametrosProducto.deMeses(LineaCredito.CPPU, Periodicidad.MENSUAL, 24).numeroCuotas()).isEqualTo(24);
    }

    @Test
    void laReplicaDelExcelConservaLosLimitesDe100Y120Semanas() {
        ParametrosProducto p = ParametrosProducto.replicaExcel();
        assertThat(p.plazoLimitePeriodos()).isEqualTo(120);
        assertThat(EscenariosExcel.caso8(p).parametros().plazoLimitePeriodos()).isEqualTo(100);
        assertThat(EscenariosExcel.caso9(p).parametros().plazoLimitePeriodos()).isEqualTo(120);
    }

    @Test
    void elMensajeDeCuotasFueraDeRangoUsaLaUnidadDeLaPeriodicidad() {
        assertThatThrownBy(() -> ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.QUINCENAL, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cuotas quincenales");
    }
}
