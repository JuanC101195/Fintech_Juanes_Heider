package co.financiera.cartera.casos;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.excel.ComparadorExcel;
import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Caso 10 · Cambio de tasa con cuota fija: desde el mes 11 (periodo 44) la tasa pasa a 80 % EA, la
 * cuota financiera se mantiene y se recalcula el número de cuotas hasta saldo cero; la última se
 * ajusta. Hoja Caso10_TasaCuotaFija.
 */
class Caso10TasaCuotaFijaTest {

    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    private final List<Movimiento> motor = new MotorCartera().ejecutar(EscenariosExcel.caso10(ParametrosProducto.replicaExcel()));

    @Test
    void reproduceElCronogramaDelExcelCeldaPorCelda() {
        ComparadorExcel comparador = new ComparadorExcel()
                .dinero("Mes", m -> bd(m.mes()))
                .dinero("Saldo inicial", Movimiento::saldoInicial)
                .tasa("Tasa periódica", Movimiento::tasaPeriodica)
                .dinero("Interés", Movimiento::interesCorriente)
                // G = MIN(cuota, D + F): lo que se cobra de cuota financiera, ajustado en la última.
                .dinero("Cuota financiera", m -> Calc.sumar(m.pagoInteresCorriente(), m.pagoCapital()))
                .dinero("Capital", Movimiento::pagoCapital)
                .dinero("FCC+IVA", m -> m.serviciosCausados().fondoGarantias())
                .dinero("Asistencia+IVA", m -> m.serviciosCausados().asistencia())
                .dinero("Seguro vida", m -> m.serviciosCausados().seguro())
                .dinero("Servicios", m -> m.serviciosCausados().total())
                .dinero("Pago total", Movimiento::pagoTotalCliente)
                .dinero("Saldo final", Movimiento::saldoFinal)
                .dinero("Pago FCC", m -> m.pagoTerceros().fondoGarantias())
                .dinero("Pago asistencia", m -> m.pagoTerceros().asistencia())
                .dinero("Pago aseguradora", m -> m.pagoTerceros().seguro());

        assertThat(comparador.diferencias(HojaExcel.cargar("Caso10_TasaCuotaFija"), motor)).isEmpty();
    }

    /** Panel "Indicadores del caso" (T2:U13). */
    @Test
    void reproduceLosIndicadoresDelPanel() {
        Movimiento ultimo = motor.getLast();
        assertThat(motor.get(44).cuotaFinanciera()).isCloseTo(new BigDecimal("100406.2741819886"), within(UN_PESO)); // U2
        assertThat(motor.get(44).tasaPeriodica()).isCloseTo(new BigDecimal("0.011367716673677863"),
                within(ComparadorExcel.TOLERANCIA_TASA)); // U3
        assertThat(ultimo.periodo()).isEqualTo(103); // U6: una cuota menos que las 104 pactadas (U8)
        assertThat(Calc.sumar(ultimo.pagoInteresCorriente(), ultimo.pagoCapital()))
                .isCloseTo(new BigDecimal("12303.302513695795"), within(UN_PESO)); // U10
        BigDecimal intereses = motor.stream().map(Movimiento::interesCorriente).reduce(BigDecimal.ZERO, Calc::sumar);
        assertThat(intereses).isCloseTo(new BigDecimal("4349343.269076532"), within(UN_PESO)); // U11
        assertThat(ultimo.saldoFinal()).isCloseTo(BigDecimal.ZERO, within(UN_PESO)); // U13
        assertThat(ultimo.estado()).isEqualTo(EstadoCredito.CANCELADO);
    }

    @Test
    void laCuotaNoCambiaConLaTasaNueva() {
        assertThat(motor.subList(1, motor.size() - 1))
                .allSatisfy(m -> assertThat(Calc.sumar(m.pagoInteresCorriente(), m.pagoCapital()))
                        .isCloseTo(new BigDecimal("100406.2741819886"), within(UN_PESO)));
    }
}
