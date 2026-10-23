package co.financiera.cartera.nucleo;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Límites exactos de {@link Calc}, {@link Amortizacion}, {@link Tasas} y {@link VigenciasCobranza}, pensados para
 * matar los mutantes que sobrevivieron en la auditoría con PIT (comparaciones {@code <=}/{@code <}, empates, cero).
 */
class CalcLimitesTest {

    // ------------------------------------------------------------------ Calc.min / Calc.max (empates)

    @Test
    void enUnEmpateMinYMaxDevuelvenElPrimerArgumento() {
        BigDecimal uno = new BigDecimal("1.0");
        BigDecimal otroUno = new BigDecimal("1.00"); // mismo valor, distinta escala: permite saber cuál devolvió
        assertThat(Calc.min(uno, otroUno)).isSameAs(uno);
        assertThat(Calc.max(uno, otroUno)).isSameAs(uno);
        assertThat(Calc.min(otroUno, uno)).isSameAs(otroUno);
        assertThat(Calc.max(otroUno, uno)).isSameAs(otroUno);
    }

    @Test
    void minYMaxEligenElMenorYElMayor() {
        assertThat(Calc.min(bd(3), bd(5))).isEqualByComparingTo("3");
        assertThat(Calc.min(bd(5), bd(3))).isEqualByComparingTo("3");
        assertThat(Calc.max(bd(3), bd(5))).isEqualByComparingTo("5");
        assertThat(Calc.max(bd(5), bd(3))).isEqualByComparingTo("5");
        assertThat(Calc.min(bd(-2), bd(0))).isEqualByComparingTo("-2");
        assertThat(Calc.max(bd(-2), bd(0))).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ Calc.noNegativo (ruido de 1e-12)

    @Test
    void noNegativoLlevaACeroLoNegativoYElRuidoPeroConservaDesdeUnoEnMenosDoce() {
        assertThat(Calc.noNegativo(bd(-5))).isSameAs(Calc.CERO);
        assertThat(Calc.noNegativo(BigDecimal.ZERO)).isSameAs(Calc.CERO);
        assertThat(Calc.noNegativo(new BigDecimal("1e-27"))).isSameAs(Calc.CERO);
        assertThat(Calc.noNegativo(new BigDecimal("9.99e-13"))).isSameAs(Calc.CERO);
        BigDecimal limite = new BigDecimal("1e-12");
        assertThat(Calc.noNegativo(limite)).isSameAs(limite); // el umbral exacto ya es dinero
        BigDecimal poco = new BigDecimal("1.1e-12");
        assertThat(Calc.noNegativo(poco)).isSameAs(poco);
        assertThat(Calc.noNegativo(bd(7))).isEqualByComparingTo("7");
    }

    // ------------------------------------------------------------------ Calc.esCero (tolerancia 0,005)

    @ParameterizedTest
    @CsvSource({"0, true", "0.005, true", "-0.005, true", "0.0049999, true", "0.0050001, false", "-0.0050001, false",
            "0.01, false", "-0.01, false", "1, false"})
    void esCeroToleraMedioCentavoEnAmbosSentidos(String valor, boolean esperado) {
        assertThat(Calc.esCero(new BigDecimal(valor))).isEqualTo(esperado);
    }

    // ------------------------------------------------------------------ Calc.potencia

    @Test
    void laPotenciaFraccionariaDaLosDigitosQueMuestraExcel() {
        // 1,8762^(1/52) = 1,012174444320802724 en doble precisión
        assertThat(Calc.potencia(new BigDecimal("1.8762"), 1.0 / 52).toPlainString()).startsWith("1.0121744443208027");
        assertThat(Calc.potencia(bd(2), 3)).isEqualByComparingTo("8");
        assertThat(Calc.potencia(bd(5), 0)).isEqualByComparingTo("1");
    }

    // ------------------------------------------------------------------ Amortizacion.cuotaFija

    @ParameterizedTest
    @CsvSource({"0", "-1", "-104"})
    void sinPeriodosPositivosLaCuotaFijaRechazaElPlazo(int periodos) {
        assertThatThrownBy(() -> Amortizacion.cuotaFija(new BigDecimal("0.01"), periodos, bd(1000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodos debe ser positivo");
    }

    @Test
    void conTasaCeroLaCuotaReparteElValorEnPartesIguales() {
        assertThat(Amortizacion.cuotaFija(BigDecimal.ZERO, 4, bd(1000))).isEqualByComparingTo("250");
        assertThat(Amortizacion.cuotaFija(new BigDecimal("0.000000"), 3, bd(900))).isEqualByComparingTo("300");
        assertThat(Amortizacion.cuotaFija(BigDecimal.ZERO, 1, bd(777))).isEqualByComparingTo("777");
    }

    @Test
    void conUnSoloPeriodoLaCuotaEsElSaldoMasSuInteres() {
        assertThat(Amortizacion.cuotaFija(new BigDecimal("0.02"), 1, bd(1000)).doubleValue()).isCloseTo(1020.0, offset(1e-9));
        assertThat(Amortizacion.cuotaFija(new BigDecimal("0.0121744443208"), 1, bd(5904400)).doubleValue())
                .isCloseTo(5904400 * 1.0121744443208, offset(1e-6));
    }

    @Test
    void laCuotaFijaCoincideConElPagoDeExcel() {
        // PAGO(0,02; 12; −1000) = 94,5596 (valor × i / (1 − (1 + i)^−n))
        assertThat(Amortizacion.cuotaFija(new BigDecimal("0.02"), 12, bd(1000)).doubleValue()).isCloseTo(94.5596, offset(1e-4));
        // Con tasa positiva la cuota es mayor que el reparto lineal.
        assertThat(Amortizacion.cuotaFija(new BigDecimal("0.02"), 12, bd(1000)))
                .isGreaterThan(Amortizacion.cuotaFija(BigDecimal.ZERO, 12, bd(1000)));
    }

    // ------------------------------------------------------------------ Tasas (conversión efectiva)

    @Test
    void laConversionDeLaEfectivaAnualDaLasTasasConocidas() {
        Tasas t = Tasas.desdeEA(new BigDecimal("0.8762"), 52, 12);
        assertThat(t.efectivaAnual()).isEqualByComparingTo("0.8762");
        assertThat(t.periodica().doubleValue()).isCloseTo(0.012174444320802724, offset(1e-15));
        assertThat(t.diaria().doubleValue()).isCloseTo(0.0017254552568628512, offset(1e-15));
        assertThat(t.mensual().doubleValue()).isCloseTo(0.05383655951980937, offset(1e-15));
    }

    @Test
    void laTasaPeriodicaUsaLosPeriodosPorAnioLaDiariaSiempre365YLaMensualLosMesesDelAnio() {
        Tasas quincenal = Tasas.desdeEA(new BigDecimal("0.8762"), 24, 12);
        assertThat(quincenal.periodica().doubleValue()).isCloseTo(0.026565419016152836, offset(1e-15));
        assertThat(quincenal.diaria().doubleValue()).isCloseTo(0.0017254552568628512, offset(1e-15));
        assertThat(quincenal.mensual().doubleValue()).isCloseTo(0.05383655951980937, offset(1e-15));
        // En mensual la periódica y la mensual son la misma tasa.
        Tasas mensual = Tasas.desdeEA(new BigDecimal("0.8762"), Periodicidad.MENSUAL);
        assertThat(mensual.periodica()).isEqualByComparingTo(mensual.mensual());
    }

    @Test
    void tasasDesdeUnaPeriodicidadYDesdeUnProductoDanLoMismoQueConEnteros() {
        BigDecimal ea = new BigDecimal("0.8762");
        assertThat(Tasas.desdeEA(ea, Periodicidad.SEMANAL).periodica()).isEqualByComparingTo(Tasas.desdeEA(ea, 52, 12).periodica());
        assertThat(Tasas.desdeEA(ea, Periodicidad.QUINCENAL).periodica().doubleValue()).isCloseTo(0.026565419016152836, offset(1e-15));
        assertThat(Tasas.de(ParametrosProducto.replicaExcel())).isEqualTo(Tasas.desdeEA(ea, Periodicidad.SEMANAL));
    }

    @Test
    void conEaCeroTodasLasTasasSonCero() {
        Tasas t = Tasas.desdeEA(BigDecimal.ZERO, 52, 12);
        assertThat(t.periodica()).isEqualByComparingTo("0");
        assertThat(t.diaria()).isEqualByComparingTo("0");
        assertThat(t.mensual()).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ VigenciasCobranza.estaVacia

    @Test
    void laTablaVaciaEstaVaciaYConUnaVigenciaYaNo() {
        VigenciasCobranza vacia = VigenciasCobranza.vacia();
        assertThat(vacia.estaVacia()).isTrue();
        assertThat(vacia.vigencias()).isEmpty();

        VigenciasCobranza conUna = vacia.conVigencia(LocalDate.of(2026, 1, 1), bd(3000));
        assertThat(conUna.estaVacia()).isFalse();
        assertThat(conUna.vigencias()).hasSize(1);
        // La tabla original no cambia (es inmutable).
        assertThat(vacia.estaVacia()).isTrue();
        assertThat(VigenciasCobranza.vacia().estaVacia()).isTrue();
    }
}
