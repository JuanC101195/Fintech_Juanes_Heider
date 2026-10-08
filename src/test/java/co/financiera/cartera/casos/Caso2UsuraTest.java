package co.financiera.cartera.casos;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import co.financiera.cartera.excel.ComparadorExcel;
import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.Amortizacion;
import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Tasas;
import co.financiera.cartera.nucleo.Usura;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Caso 2 · Regla de usura (R-2.7 a R-2.9): si la EA pactada supera la usura vigente, el crédito
 * se lleva a usura − 1 punto con la misma mecánica del cambio de tasa del Excel.
 */
class Caso2UsuraTest {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final int PERIODO_44 = Calendario.primerPeriodoDelMes(11, P);

    @Test
    void eaPorEncimaDeLaUsuraSeAjustaAUsuraMenosUnPunto() {
        assertThat(Usura.superaUsura(bd("0.8732"), bd("0.80"))).isTrue();
        assertThat(Usura.ajustar(bd("0.8732"), bd("0.80")))
                .hasValueSatisfying(c -> assertThat(c.nuevaTasaEA()).isEqualByComparingTo("0.79"));
    }

    @Test
    void eaPorDebajoDeLaUsuraNoCambia() {
        assertThat(Usura.superaUsura(bd("0.8732"), bd("0.90"))).isFalse();
        assertThat(Usura.ajustar(bd("0.8732"), bd("0.90"))).isEmpty();
    }

    @Test
    void eaIgualALaUsuraNoCambia() {
        // Cobrar exactamente la usura es legal; solo se ajusta si la supera (pregunta abierta en caso-2.md).
        assertThat(Usura.ajustar(bd("0.80"), bd("0.80"))).isEmpty();
    }

    @Test
    void rechazaTasasNoPositivas() {
        assertThatThrownBy(() -> Usura.ajustar(bd("0"), bd("0.80"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Usura.ajustar(bd("0.8732"), bd("-0.1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Usura.tasaAjustada(bd("0.01"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elAjustePorUsuraConservaLaFechaFinalEnElMotor() {
        CambioTasa ajuste = Usura.ajustar(P.tasaEA(), bd("0.80")).orElseThrow();
        List<Movimiento> motor = new MotorCartera().ejecutar(
                new Escenario(Operacion.ejemploExcel(), P).en(PERIODO_44, ajuste));

        Movimiento cambio = motor.get(PERIODO_44);
        BigDecimal tasa79 = Tasas.desdeEA(bd("0.79"), 52, 12).semanal();
        assertThat(cambio.tasaSemanal()).isCloseTo(tasa79, offset(ComparadorExcel.TOLERANCIA_TASA));
        assertThat(cambio.cuotaFinanciera()).isCloseTo(
                Amortizacion.cuotaFija(tasa79, Calendario.numeroCuotas(P) - PERIODO_44 + 1, cambio.saldoInicial()),
                offset(ComparadorExcel.TOLERANCIA_DINERO));

        Movimiento ultimo = motor.getLast();
        assertThat(ultimo.periodo()).isEqualTo(86);
        assertThat(ultimo.estado()).isEqualTo(EstadoCredito.CANCELADO);
        assertThat(ultimo.saldoFinal()).isCloseTo(BigDecimal.ZERO, offset(ComparadorExcel.TOLERANCIA_DINERO));
    }

    @Test
    void conUsuraDe81ElAjusteReproduceElCaso2DelExcel() {
        // Usura 81 % → EA 80 %, la misma del Excel: el cronograma debe coincidir celda por celda.
        CambioTasa ajuste = Usura.ajustar(P.tasaEA(), bd("0.81")).orElseThrow();
        List<Movimiento> motor = new MotorCartera().ejecutar(
                new Escenario(Operacion.ejemploExcel(), P).en(PERIODO_44, ajuste));

        ComparadorExcel comparador = new ComparadorExcel()
                .dinero("Saldo inicial", Movimiento::saldoInicial)
                .tasa("Tasa semanal", Movimiento::tasaSemanal)
                .dinero("Interés", Movimiento::interesCorriente)
                .dinero("Cuota financiera", Movimiento::cuotaFinanciera)
                .dinero("Capital", Movimiento::pagoCapital)
                .dinero("Pago total", Movimiento::pagoTotalCliente)
                .dinero("Saldo final", Movimiento::saldoFinal);
        assertThat(comparador.diferencias(HojaExcel.cargar("Caso2_CambioTasa"), motor)).isEmpty();
    }
}
