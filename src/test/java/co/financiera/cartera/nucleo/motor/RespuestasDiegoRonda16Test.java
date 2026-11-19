package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.AlcanceProrrateo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R16-PRIMERA-CUOTA-EXCEL (Diego 2026-11-19, D90): el Excel «logica para 1ra cuota parcial mejorado» (hoja «Plan 1ª cuota
 * proporcional»). Monto 5.603.760, EA 89,77 %, 64 semanas, primera cuota de 5 días. El Excel convierte la tasa con ^(1/52),
 * por eso la comparación celda a celda corre con base 365; con base 360 (NEGOCIO) solo cambia la tasa, no la lógica.
 */
class RespuestasDiegoRonda16Test {

    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    private static final Operacion MONTO_DEL_EXCEL = new Operacion(bd(5_603_760), Calc.CERO, Calc.CERO, Calc.CERO,
            Calc.CERO, Calc.CERO);
    private static final ParametrosProducto P = ParametrosProducto.replicaExcel()
            .conTasaEA(new BigDecimal("0.8977")).conNumeroCuotas(64);
    /** Jueves 5-nov-2026 a martes 10-nov-2026: 5 días de 7, como el Excel. */
    private static final CalendarioPagos CINCO_DIAS =
            CalendarioPagos.semanal(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 10));

    private final MotorCartera motor = new MotorCartera();

    private static ReglasNegocio reglas() {
        return ReglasNegocioConfiguradaTest.negocio()
                .conAlcanceProrrateoPrimeraCuota(AlcanceProrrateo.CUOTA_PROPORCIONAL_RECALCULADA);
    }

    private List<Movimiento> correr(ReglasNegocio reglas) {
        return motor.ejecutar(new Escenario(MONTO_DEL_EXCEL, P).conReglas(reglas).conCalendario(CINCO_DIAS));
    }

    private static Movimiento fila(List<Movimiento> m, int periodo) {
        return m.stream().filter(x -> x.periodo() == periodo).findFirst().orElseThrow();
    }

    @Test
    void reproduceElPlanDelExcelDeDiego() {
        List<Movimiento> m = correr(reglas().conBaseDiasAnio(365));
        Movimiento primera = fila(m, 1);
        // Fila 21: cuota 90.964,95 = 127.350,92 × 5/7; interés de 5 días 49.530,91; capital 41.434,04.
        assertThat(primera.interesCorriente()).isCloseTo(new BigDecimal("49530.91"), within(UN_PESO));
        assertThat(primera.capitalContractual()).isCloseTo(new BigDecimal("41434.04"), within(UN_PESO));
        assertThat(primera.cuotaFinanciera()).isCloseTo(new BigDecimal("90964.95"), within(UN_PESO));
        // B17: cuota recalculada 127.728,70 = PMT(tasa, 63, 5.562.325,96), igual de la 2 a la 63.
        assertThat(fila(m, 2).cuotaFinanciera()).isCloseTo(new BigDecimal("127728.70"), within(UN_PESO));
        assertThat(fila(m, 2).interesCorriente()).isCloseTo(new BigDecimal("68952.01"), within(UN_PESO));
        assertThat(fila(m, 63).cuotaFinanciera()).isCloseTo(new BigDecimal("127728.70"), within(UN_PESO));
        // Fila 84: la última cuota cierra el saldo (126.164,36 de capital + 1.563,97 de interés).
        assertThat(fila(m, 64).capitalContractual()).isCloseTo(new BigDecimal("126164.36"), within(UN_PESO));
        assertThat(m.getLast().periodo()).isEqualTo(64);
        assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
        // B87: la suma del capital es el monto desembolsado (sin capital diferido).
        BigDecimal capital = m.stream().map(Movimiento::capitalContractual).reduce(Calc.CERO, Calc::sumar);
        assertThat(capital).isCloseTo(bd(5_603_760), within(new BigDecimal("0.01")));
    }

    @Test
    void losServiciosDeLaPrimeraCuotaVanEnLaMismaProporcion() {
        List<Movimiento> m = correr(reglas());
        BigDecimal primera = fila(m, 1).serviciosCausados().total();
        BigDecimal completa = fila(m, 2).serviciosCausados().total();
        assertThat(primera).isCloseTo(Calc.multiplicar(completa, Calc.dividir(bd(5), bd(7))), within(new BigDecimal("0.01")));
    }

    @Test
    void conBase360ElPlanTambienCierraEnLaCuotaNSinCapitalDiferido() {
        List<Movimiento> m = correr(reglas());
        assertThat(m.getLast().periodo()).isEqualTo(64);
        assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
        BigDecimal capital = m.stream().map(Movimiento::capitalContractual).reduce(Calc.CERO, Calc::sumar);
        assertThat(capital).isCloseTo(bd(5_603_760), within(new BigDecimal("0.01")));
        // La primera cuota financiera es la cuota fija sobre el monto × 5/7 (antes de redondear al peso).
        BigDecimal cuotaFija = co.financiera.cartera.nucleo.Amortizacion.cuotaFija(fila(m, 1).tasaPeriodica(), 64, bd(5_603_760));
        assertThat(fila(m, 1).cuotaFinanciera())
                .isCloseTo(Calc.multiplicar(cuotaFija, Calc.dividir(bd(5), bd(7))), within(UN_PESO));
        // Las cuotas 2 a 63 son iguales (cuota recalculada).
        assertThat(fila(m, 63).cuotaFinanciera()).isCloseTo(fila(m, 2).cuotaFinanciera(), within(UN_PESO));
    }

    @Test
    void sinFechasNoCambiaNada() {
        List<Movimiento> a = motor.ejecutar(new Escenario(MONTO_DEL_EXCEL, P).conReglas(reglas()));
        List<Movimiento> b = motor.ejecutar(new Escenario(MONTO_DEL_EXCEL, P).conReglas(ReglasNegocioConfiguradaTest.negocio()));
        assertThat(fila(a, 64).cuotaFinanciera()).isEqualByComparingTo(fila(b, 64).cuotaFinanciera());
    }
}
