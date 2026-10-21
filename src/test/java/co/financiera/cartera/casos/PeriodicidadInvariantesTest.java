package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * ADR 0002, paso 6 · Validación de quincenal y mensual sin un Excel nuevo (decisión 2026-10-20): la
 * línea estándar de Inputs (CPPU 87,62 % EA, operación del ejemplo) a 24 meses en cada periodicidad.
 * Los valores esperados se calculan aquí con fórmulas cerradas en {@code double}, independientes
 * del motor: tasa del periodo (1 + EA)^(1/ppa) − 1 (PPER) y la cuota con la fórmula de PAGO() de
 * Excel, P × i / (1 − (1 + i)^−n).
 */
class PeriodicidadInvariantesTest {

    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    private static final BigDecimal TOLERANCIA_TASA = new BigDecimal("0.000001");

    private static List<Movimiento> plan(Periodicidad per) {
        ParametrosProducto p = ParametrosProducto.deMeses(LineaCredito.CPPU, per, 24);
        return new MotorCartera().ejecutar(new Escenario(Operacion.ejemploExcel(), p));
    }

    private static double tasaDelPeriodo(Periodicidad per) {
        return Math.pow(1 + LineaCredito.CPPU.tasaEA().doubleValue(), 1.0 / per.periodosPorAnio()) - 1;
    }

    /** PAGO(i; n; −P) de Excel con su fórmula cerrada. */
    private static double pago(double i, int n, double valor) {
        return valor * i / (1 - Math.pow(1 + i, -n));
    }

    private static List<Movimiento> cuotas(List<Movimiento> plan) {
        return plan.subList(1, plan.size()); // la fila 0 es el desembolso
    }

    @ParameterizedTest
    @CsvSource({"SEMANAL, 104", "QUINCENAL, 48", "MENSUAL, 24"})
    void elSaldoFinalDaCeroEnLaUltimaCuota(Periodicidad per, int cuotasPactadas) {
        List<Movimiento> plan = plan(per);
        Movimiento ultimo = plan.getLast();
        assertThat(ultimo.periodo()).isEqualTo(cuotasPactadas);
        assertThat(ultimo.estado()).isEqualTo(EstadoCredito.CANCELADO);
        assertThat(ultimo.saldoFinal()).isCloseTo(BigDecimal.ZERO, offset(UN_PESO));
    }

    @ParameterizedTest
    @EnumSource(Periodicidad.class)
    void laSumaDelCapitalPagadoEsElMonto(Periodicidad per) {
        BigDecimal capital = cuotas(plan(per)).stream().map(Movimiento::pagoCapital).reduce(Calc.CERO, BigDecimal::add);
        assertThat(capital).isCloseTo(Operacion.ejemploExcel().montoFinanciado(), offset(UN_PESO));
    }

    @ParameterizedTest
    @EnumSource(Periodicidad.class)
    void elInteresDeCadaCuotaEsElSaldoPorLaTasaDelPeriodo(Periodicidad per) {
        BigDecimal tasa = BigDecimal.valueOf(tasaDelPeriodo(per));
        for (Movimiento m : cuotas(plan(per))) {
            assertThat(m.tasaPeriodica()).as("tasa de la cuota %d", m.periodo()).isCloseTo(tasa, offset(TOLERANCIA_TASA));
            assertThat(m.interesCorriente()).as("interés de la cuota %d", m.periodo())
                    .isCloseTo(Calc.multiplicar(m.saldoInicial(), tasa), offset(UN_PESO));
        }
    }

    @ParameterizedTest
    @EnumSource(Periodicidad.class)
    void laCuotaCoincideConPagoDeExcel(Periodicidad per) {
        int n = per.cuotasEnMeses(24);
        double esperada = pago(tasaDelPeriodo(per), n, Operacion.ejemploExcel().montoFinanciado().doubleValue());
        for (Movimiento m : cuotas(plan(per))) {
            if (m.periodo() < n) { // la última cuota puede ser solo el residuo
                assertThat(m.cuotaFinanciera()).as("cuota %d", m.periodo())
                        .isCloseTo(BigDecimal.valueOf(esperada), offset(UN_PESO));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"QUINCENAL, 24", "MENSUAL, 12"})
    void laTasaMensualYQuincenalSonLaConversionDeLaEA(Periodicidad per, int ppa) {
        // (1 + tasa del periodo)^ppa = 1 + EA: la tasa es efectiva, no nominal (D9).
        double tasa = tasaDelPeriodo(per);
        assertThat(per.periodosPorAnio()).isEqualTo(ppa);
        assertThat(Math.pow(1 + tasa, ppa) - 1).isCloseTo(LineaCredito.CPPU.tasaEA().doubleValue(), offset(1e-9));
        assertThat(plan(per).get(1).tasaPeriodica()).isCloseTo(BigDecimal.valueOf(tasa), offset(TOLERANCIA_TASA));
    }
}
