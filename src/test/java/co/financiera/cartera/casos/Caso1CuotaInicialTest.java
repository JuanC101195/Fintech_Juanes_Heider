package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * P-09 (Diego, 2026-10-16) · El pago inicial es opcional: lo digita el asesor y se resta del valor
 * total de la operación. El Excel no tiene inicial, así que los esperados se calculan aquí con las
 * fórmulas cerradas, independientes del motor. Se usa una inicial de 590.440 (el 10 % que se había
 * supuesto antes) para conservar las cifras de referencia.
 */
class Caso1CuotaInicialTest {

    private static final Offset<BigDecimal> UN_PESO = Offset.offset(BigDecimal.ONE);

    private final ParametrosProducto producto = ParametrosProducto.replicaExcel();
    private final Operacion operacion = Operacion.ejemploExcel().conPagoInicial(new BigDecimal("590440"));
    private final List<Movimiento> motor = new MotorCartera().ejecutar(new Escenario(operacion, producto));

    // Cálculo independiente en doble precisión (como lo haría una calculadora financiera).
    private static final double FINANCIADO = 5_313_960.0;
    private static final double TASA_SEMANAL = Math.pow(1.8732, 1.0 / 52) - 1;
    private static final int CUOTAS = 86;
    private static final double CUOTA = FINANCIADO * TASA_SEMANAL / (1 - Math.pow(1 + TASA_SEMANAL, -CUOTAS));
    private static final double SEMANAS_MES = 52.0 / 12.0;

    private static BigDecimal bd(double valor) {
        return BigDecimal.valueOf(valor);
    }

    @Test
    void valorTotalInicialYMontoFinanciado() {
        assertThat(operacion.valorTotal()).isEqualByComparingTo("5904400");
        assertThat(operacion.pagoInicial()).isEqualByComparingTo("590440");
        assertThat(operacion.montoFinanciado()).isEqualByComparingTo("5313960");
        assertThat(Calendario.numeroCuotas(producto)).isEqualTo(86);
        // Con la inicial el LTV sigue por encima de 100 %: 5.313.960 / 5.100.000.
        assertThat(operacion.ltv()).isCloseTo(new BigDecimal("1.041952941"), Offset.offset(new BigDecimal("0.000001")));
    }

    @Test
    void sinInicialSeFinanciaElValorTotal() {
        assertThat(Operacion.ejemploExcel().pagoInicial()).isZero();
        assertThat(Operacion.ejemploExcel().montoFinanciado()).isEqualByComparingTo("5904400");
    }

    @Test
    void ejemploDeDiegoSeisQuinientosMenosSetecientos() {
        // Moto + alistamiento = 6.500.000; inicial de 700.000 → todo el crédito parte de 5.800.000.
        Operacion op = new Operacion(new BigDecimal("6000000"), new BigDecimal("200000"), new BigDecimal("300000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("700000"));
        assertThat(op.valorTotal()).isEqualByComparingTo("6500000");
        assertThat(op.montoFinanciado()).isEqualByComparingTo("5800000");
        List<Movimiento> m = new MotorCartera().ejecutar(new Escenario(op, producto));
        assertThat(m.getFirst().saldoFinal()).isEqualByComparingTo("5800000");
    }

    @Test
    void elPagoInicialNoPuedeSerNegativoNiCubrirTodaLaOperacion() {
        Operacion op = Operacion.ejemploExcel();
        assertThatThrownBy(() -> op.conPagoInicial(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> op.conPagoInicial(op.valorTotal())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elDesembolsoEsElMontoFinanciadoYNoElValorTotal() {
        Movimiento desembolso = motor.getFirst();
        assertThat(desembolso.saldoFinal()).isEqualByComparingTo("5313960");
        assertThat(motor.get(1).saldoInicial()).isEqualByComparingTo("5313960");
    }

    @Test
    void laCuotaFinancieraEsElPmtSobreElMontoFinanciado() {
        assertThat(CUOTA).isBetween(99_900.0, 99_930.0);
        assertThat(motor.subList(1, motor.size()))
                .allSatisfy(m -> assertThat(m.cuotaFinanciera()).isCloseTo(bd(CUOTA), UN_PESO));
        // La cuota es lineal en el monto: 90 % de la cuota del Excel (Inputs!B20).
        assertThat(bd(CUOTA)).isCloseTo(new BigDecimal("111014.82187676849").multiply(new BigDecimal("0.9")), UN_PESO);
    }

    @Test
    void losServiciosSeCalculanSobreElMontoFinanciado() {
        double fcc = FINANCIADO * 0.03 / SEMANAS_MES * 1.19;
        double asistencia = 40_000 / SEMANAS_MES * 1.19;
        double seguro = FINANCIADO * 0.0025 / SEMANAS_MES;
        assertThat(motor.subList(1, motor.size())).allSatisfy(m -> {
            assertThat(m.serviciosCausados().fondoGarantias()).isCloseTo(bd(fcc), UN_PESO);
            assertThat(m.serviciosCausados().asistencia()).isCloseTo(bd(asistencia), UN_PESO);
            assertThat(m.serviciosCausados().seguro()).isCloseTo(bd(seguro), UN_PESO);
            assertThat(m.pagoRecibido()).isCloseTo(bd(CUOTA + fcc + asistencia + seguro), UN_PESO);
        });
        // FCC y seguro bajan al 90 % del Excel; la asistencia es un valor fijo y no cambia (P11).
        assertThat(bd(fcc)).isCloseTo(new BigDecimal("48643.172307692315").multiply(new BigDecimal("0.9")), UN_PESO);
        assertThat(bd(asistencia)).isCloseTo(new BigDecimal("10984.615384615387"), UN_PESO);
    }

    @Test
    void terminaEnLaCuota86ConSaldoCero() {
        Movimiento ultimo = motor.getLast();
        assertThat(ultimo.periodo()).isEqualTo(86);
        assertThat(ultimo.estado()).isEqualTo(EstadoCredito.CANCELADO);
        assertThat(ultimo.saldoFinal()).isCloseTo(BigDecimal.ZERO, UN_PESO);
        BigDecimal capital = motor.stream().map(Movimiento::pagoCapital).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(capital).isCloseTo(new BigDecimal("5313960"), UN_PESO);
    }
}
