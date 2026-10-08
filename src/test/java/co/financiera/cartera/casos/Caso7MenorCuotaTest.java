package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.excel.ComparadorExcel;
import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.Amortizacion;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Tasas;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Caso 7 · Abono extra como menor cuota: en el mes 10 (periodo 43), después de la cuota ordinaria,
 * el cliente abona 2.000.000 a capital; se conserva la cuota 86 como última y se recalcula una
 * cuota menor. Hoja Caso7_MenorCuota. Reglas en docs/reglas/caso-7.md.
 */
class Caso7MenorCuotaTest {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    /** Inputs C57 = ROUNDDOWN(C55 × B14, 0) con C55 = mes 10. */
    private static final int PERIODO_ABONO = Calendario.periodoDelMes(10, P);
    /** Inputs C56. */
    private static final BigDecimal ABONO = new BigDecimal("2000000");
    private static final BigDecimal UN_PESO = BigDecimal.ONE;

    private static final HojaExcel HOJA = HojaExcel.cargar("Caso7_MenorCuota");

    private final List<Movimiento> motor = correr(new Escenario(Operacion.ejemploExcel(), P)
            .en(PERIODO_ABONO, new AbonoExtra(ABONO, ModalidadAbono.MENOR_CUOTA)));

    private static List<Movimiento> correr(Escenario escenario) {
        return new MotorCartera().ejecutar(escenario);
    }

    private static Movimiento periodo(List<Movimiento> movs, int periodo) {
        return movs.stream().filter(m -> m.periodo() == periodo).findFirst().orElseThrow();
    }

    private static BigDecimal excel(int periodo, String columna) {
        return HojaExcel.numero(HOJA.filaDelPeriodo(periodo), columna).orElseThrow();
    }

    @Test
    void elPeriodoDelAbonoEsEl43() {
        assertThat(PERIODO_ABONO).isEqualTo(43);
    }

    @Test
    void reproduceElCronogramaDelExcelCeldaPorCelda() {
        ComparadorExcel comparador = new ComparadorExcel()
                .dinero("Saldo inicial", Movimiento::saldoInicial)
                .dinero("Interés", Movimiento::interesCorriente)
                .dinero("Cuota financiera", Movimiento::cuotaFinanciera)
                .dinero("Capital ordinario", Movimiento::pagoCapital)
                .dinero("Abono extra", Movimiento::abonoExtra)
                .dinero("Pago financiero total",
                        m -> Calc.sumar(m.pagoInteresCorriente(), m.pagoCapital(), m.abonoExtra()))
                .dinero("Saldo final", Movimiento::saldoFinal)
                .dinero("FCC+IVA", m -> m.serviciosCausados().fcc())
                .dinero("Asistencia+IVA", m -> m.serviciosCausados().asistencia())
                .dinero("Seguro vida", m -> m.serviciosCausados().seguro())
                .dinero("Servicios", m -> m.serviciosCausados().total())
                .dinero("Pago total cliente", Movimiento::pagoTotalCliente)
                .dinero("Pago FCC", m -> m.pagoTerceros().fcc())
                .dinero("Pago asistencia", m -> m.pagoTerceros().asistencia())
                .dinero("Pago aseguradora", m -> m.pagoTerceros().seguro());

        assertThat(comparador.diferencias(HOJA, motor)).isEmpty();
    }

    @Test
    void elEstadoYElEventoCoincidenConElExcel() {
        for (Map<String, String> fila : HOJA.filas()) {
            int p = Integer.parseInt(fila.get("Periodo"));
            if (p == 0) {
                continue;
            }
            Movimiento m = periodo(motor, p);
            EstadoCredito esperado = "Amortizado".equals(fila.get("Estado"))
                    ? EstadoCredito.CANCELADO : EstadoCredito.VIGENTE_AL_DIA;
            assertThat(m.estado()).as("estado del periodo %d", p).isEqualTo(esperado);
            assertThat(m.evento()).as("evento del periodo %d", p).isEqualTo(fila.get("Evento"));
        }
    }

    /** Panel lateral U1:V8 de la hoja Caso7_MenorCuota. */
    @Test
    void reproduceLosIndicadoresDelPanelLateral() {
        Movimiento abono = periodo(motor, PERIODO_ABONO);
        Movimiento siguiente = periodo(motor, PERIODO_ABONO + 1);
        BigDecimal cuotaOriginal = periodo(motor, 1).cuotaFinanciera();

        // V2 periodo abono, V3 abono extra
        assertThat(abono.abonoExtra()).isCloseTo(ABONO, within(UN_PESO));
        // V4 saldo tras abono = INDEX(J, C57)
        assertThat(abono.saldoFinal()).isCloseTo(new BigDecimal("1701573.6220031078"), within(UN_PESO));
        // V5 nueva cuota = INDEX(F, C57 + 1)
        assertThat(siguiente.cuotaFinanciera()).isCloseTo(new BigDecimal("51032.32080378282"), within(UN_PESO));
        // V6 reducción de cuota = B20 − nueva cuota
        assertThat(Calc.restar(cuotaOriginal, siguiente.cuotaFinanciera()))
                .isCloseTo(new BigDecimal("59982.501072985666"), within(UN_PESO));
        // V7 periodo final, V8 saldo final
        assertThat(motor.getLast().periodo()).isEqualTo(86);
        assertThat(motor.getLast().saldoFinal()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
    }

    @Test
    void laNuevaCuotaEsElPmtSobreElSaldoTrasAbonoConLasCuotasRestantes() {
        Movimiento abono = periodo(motor, PERIODO_ABONO);
        BigDecimal pmt = Amortizacion.cuotaFija(Tasas.de(P).semanal(), 86 - PERIODO_ABONO, abono.saldoFinal());
        assertThat(motor.subList(PERIODO_ABONO + 1, motor.size()))
                .allSatisfy(m -> assertThat(m.cuotaFinanciera()).isCloseTo(pmt, within(UN_PESO)));
        // La cuota de la semana del abono sigue siendo la original (F46 = MIN(B20, D + E)).
        assertThat(abono.cuotaFinanciera()).isCloseTo(excel(PERIODO_ABONO, "Cuota financiera"), within(UN_PESO));
    }

    /** Hoja Validaciones, filas 22, 23 y 24 (tolerancia Inputs B71 = 1 COP). */
    @Nested
    class Validaciones {

        private final HojaExcel validaciones = HojaExcel.cargar("validaciones");

        private int periodoDe(int numero) {
            return Integer.parseInt(validaciones.filaDelPeriodo(numero).get("Periodo"));
        }

        private BigDecimal toleranciaDe(int numero) {
            return HojaExcel.numero(validaciones.filaDelPeriodo(numero), "Tolerancia").orElseThrow();
        }

        @Test
        void v22AbonoAplicado() {
            int p = periodoDe(22);
            assertThat(p).isEqualTo(43);
            assertThat(periodo(motor, p).abonoExtra()).isCloseTo(excel(p, "Abono extra"), within(toleranciaDe(22)));
        }

        @Test
        void v23NuevaCuotaEnElPeriodo44() {
            int p = periodoDe(23);
            assertThat(p).isEqualTo(44);
            assertThat(periodo(motor, p).cuotaFinanciera())
                    .isCloseTo(excel(p, "Cuota financiera"), within(toleranciaDe(23)));
        }

        @Test
        void v24SaldoFinalEnElPeriodo86() {
            int p = periodoDe(24);
            assertThat(p).isEqualTo(86);
            assertThat(periodo(motor, p).saldoFinal()).isCloseTo(excel(p, "Saldo final"), within(toleranciaDe(24)));
            assertThat(periodo(motor, p).estado()).isEqualTo(EstadoCredito.CANCELADO);
        }
    }

    /** Revisiones del motor fuera de lo que cubre el Excel. */
    @Nested
    class Bordes {

        /** (a) El plan vigente sigue la nueva cuota: el capital contractual es el capital pagado y no hay vencido. */
        @Test
        void elCapitalContractualSigueLaNuevaCuotaSinGenerarVencido() {
            assertThat(motor.subList(1, motor.size())).allSatisfy(m -> {
                assertThat(m.capitalVencido()).as("vencido periodo %d", m.periodo()).isCloseTo(BigDecimal.ZERO,
                        within(UN_PESO));
                assertThat(m.capitalContractual()).as("contractual periodo %d", m.periodo())
                        .isCloseTo(m.pagoCapital(), within(UN_PESO));
                assertThat(m.interesMora()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
                assertThat(m.estado()).isIn(EstadoCredito.VIGENTE_AL_DIA, EstadoCredito.CANCELADO);
            });
        }

        /** (b) Abono en la última cuota: no hay cuotas restantes para el PMT; no debe fallar. */
        @Test
        void abonoEnLaUltimaCuotaNoRompeElPmt() {
            Escenario e = new Escenario(Operacion.ejemploExcel(), P)
                    .en(86, new AbonoExtra(ABONO, ModalidadAbono.MENOR_CUOTA));
            assertThatCode(() -> correr(e)).doesNotThrowAnyException();
            List<Movimiento> movs = correr(e);
            Movimiento ultimo = movs.getLast();
            assertThat(ultimo.periodo()).isEqualTo(86);
            assertThat(ultimo.estado()).isEqualTo(EstadoCredito.CANCELADO);
            // Tras la cuota 86 ya no queda saldo: el abono aplicado es ~0 (el excedente no se aplica).
            assertThat(ultimo.abonoExtra()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
        }

        /** (b) Abono en la penúltima cuota: PMT con 1 periodo; la cuota 86 liquida el saldo. */
        @Test
        void abonoEnLaPenultimaCuotaDejaUnaSolaCuota() {
            List<Movimiento> movs = correr(new Escenario(Operacion.ejemploExcel(), P)
                    .en(85, new AbonoExtra(new BigDecimal("50000"), ModalidadAbono.MENOR_CUOTA)));
            Movimiento p85 = periodo(movs, 85);
            Movimiento p86 = periodo(movs, 86);
            BigDecimal esperado = Calc.multiplicar(p85.saldoFinal(), Calc.sumar(Calc.UNO, Tasas.de(P).semanal()));
            assertThat(p86.cuotaFinanciera()).isCloseTo(esperado, within(UN_PESO));
            assertThat(p86.saldoFinal()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
            assertThat(p86.estado()).isEqualTo(EstadoCredito.CANCELADO);
        }

        /** (b) Abono mayor que el saldo: se limita al saldo (H = MIN(C56, D − G)) y el crédito se cancela. */
        @Test
        void abonoMayorQueElSaldoCancelaElCreditoSinRecalcularCuota() {
            List<Movimiento> movs = correr(new Escenario(Operacion.ejemploExcel(), P)
                    .en(PERIODO_ABONO, new AbonoExtra(new BigDecimal("10000000"), ModalidadAbono.MENOR_CUOTA)));
            Movimiento abono = movs.getLast();
            assertThat(abono.periodo()).isEqualTo(PERIODO_ABONO);
            assertThat(abono.abonoExtra()).isCloseTo(excel(PERIODO_ABONO, "Saldo inicial")
                    .subtract(excel(PERIODO_ABONO, "Capital ordinario")), within(UN_PESO));
            assertThat(abono.saldoFinal()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
            assertThat(abono.estado()).isEqualTo(EstadoCredito.CANCELADO);
        }

        /** (c) Dos abonos en meses distintos: cada uno recalcula la cuota con las cuotas que quedan. */
        @Test
        void dosAbonosEnMesesDistintosRecalculanLaCuotaCadaVez() {
            int segundo = Calendario.periodoDelMes(14, P);
            List<Movimiento> movs = correr(new Escenario(Operacion.ejemploExcel(), P)
                    .en(PERIODO_ABONO, new AbonoExtra(ABONO, ModalidadAbono.MENOR_CUOTA))
                    .en(segundo, new AbonoExtra(new BigDecimal("500000"), ModalidadAbono.MENOR_CUOTA)));
            BigDecimal tasa = Tasas.de(P).semanal();

            // Entre los dos abonos se replica el Excel del caso 7.
            for (int p = 1; p < segundo; p++) {
                assertThat(periodo(movs, p).saldoFinal()).as("saldo periodo %d", p)
                        .isCloseTo(excel(p, "Saldo final"), within(UN_PESO));
            }
            Movimiento s = periodo(movs, segundo);
            assertThat(s.cuotaFinanciera()).isCloseTo(excel(segundo, "Cuota financiera"), within(UN_PESO));
            assertThat(s.saldoFinal()).isCloseTo(excel(segundo, "Saldo final").subtract(new BigDecimal("500000")),
                    within(UN_PESO));

            BigDecimal segundaCuota = Amortizacion.cuotaFija(tasa, 86 - segundo, s.saldoFinal());
            assertThat(periodo(movs, segundo + 1).cuotaFinanciera()).isCloseTo(segundaCuota, within(UN_PESO));
            assertThat(segundaCuota).isLessThan(excel(segundo + 1, "Cuota financiera"));
            assertThat(movs.getLast().periodo()).isEqualTo(86);
            assertThat(movs.getLast().saldoFinal()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
            assertThat(movs.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
            assertThat(movs).allSatisfy(m -> assertThat(m.capitalVencido()).isCloseTo(BigDecimal.ZERO, within(UN_PESO)));
        }

        /**
         * Discrepancia D-7.1: el Excel calcula la nueva cuota con la tasa original (Inputs B17). El motor
         * usa la tasa vigente; si antes hubo un cambio de tasa (caso 2) solo así el saldo llega a 0 en la 86.
         */
        @Test
        void conCambioDeTasaPrevioLaNuevaCuotaUsaLaTasaVigente() {
            BigDecimal nuevaEA = new BigDecimal("0.80");
            int periodoCambio = Calendario.primerPeriodoDelMes(9, P);
            List<Movimiento> movs = correr(new Escenario(Operacion.ejemploExcel(), P)
                    .en(periodoCambio, new CambioTasa(nuevaEA))
                    .en(PERIODO_ABONO, new AbonoExtra(ABONO, ModalidadAbono.MENOR_CUOTA)));
            Tasas vigente = Tasas.desdeEA(nuevaEA, P.semanasPorAnio(), P.mesesPorAnio());
            Movimiento abono = periodo(movs, PERIODO_ABONO);
            BigDecimal esperada = Amortizacion.cuotaFija(vigente.semanal(), 86 - PERIODO_ABONO, abono.saldoFinal());
            BigDecimal conTasaOriginal = Amortizacion.cuotaFija(Tasas.de(P).semanal(), 86 - PERIODO_ABONO,
                    abono.saldoFinal());

            assertThat(periodo(movs, PERIODO_ABONO + 1).cuotaFinanciera()).isCloseTo(esperada, within(UN_PESO));
            assertThat(esperada).isLessThan(conTasaOriginal);
            assertThat(movs.getLast().periodo()).isEqualTo(86);
            assertThat(movs.getLast().saldoFinal()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
        }
    }
}
