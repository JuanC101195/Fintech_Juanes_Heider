package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.excel.ComparadorExcel;
import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Caso 6 · Abono extra como menor plazo. Hoja Caso6_MenorPlazo. En el mes 10 (periodo 43) el
 * cliente paga la cuota ordinaria y abona 2.000.000 a capital; la cuota financiera se mantiene y
 * el crédito termina antes (periodo 61). Reglas en docs/reglas/caso-6.md.
 */
class Caso6MenorPlazoTest {

    private static final BigDecimal ABONO = Calc.bd("2000000"); // Inputs!C53
    private static final BigDecimal UN_PESO = ComparadorExcel.TOLERANCIA_DINERO;

    private final ParametrosProducto parametros = ParametrosProducto.replicaExcel();
    /** Inputs!C54 = ROUNDDOWN(mes 10 × semanas por mes) = 43. */
    private final int periodoAbono = Calendario.periodoDelMes(10, parametros);
    private final HojaExcel hoja = HojaExcel.cargar("Caso6_MenorPlazo");
    private final List<Movimiento> motor = new MotorCartera().ejecutar(escenario(ABONO));

    private Escenario escenario(BigDecimal abono) {
        return new Escenario(Operacion.ejemploExcel(), parametros)
                .en(periodoAbono, new AbonoExtra(abono, ModalidadAbono.MENOR_PLAZO));
    }

    private static Movimiento delPeriodo(List<Movimiento> movimientos, int periodo) {
        return movimientos.stream().filter(m -> m.periodo() == periodo).findFirst().orElseThrow();
    }

    /** Lo que el cliente paga de cuota financiera (columna F): en la última semana es menor que la cuota pactada. */
    private static BigDecimal cuotaFinancieraPagada(Movimiento m) {
        return Calc.sumar(m.pagoInteresCorriente(), m.pagoCapital());
    }

    // ---------------------------------------------------------------- réplica del Excel

    @Test
    void reproduceElCronogramaDelExcelCeldaPorCelda() {
        ComparadorExcel comparador = new ComparadorExcel()
                .dinero("Saldo inicial", Movimiento::saldoInicial)
                .dinero("Interés", Movimiento::interesCorriente)
                .dinero("Cuota financiera", Caso6MenorPlazoTest::cuotaFinancieraPagada)
                .dinero("Capital ordinario", Movimiento::pagoCapital)
                .dinero("Abono extra", Movimiento::abonoExtra)
                .dinero("Pago financiero total", m -> Calc.sumar(cuotaFinancieraPagada(m), m.abonoExtra()))
                .dinero("Saldo final", Movimiento::saldoFinal)
                .dinero("FCC+IVA", m -> m.serviciosCausados().fcc())
                .dinero("Asistencia+IVA", m -> m.serviciosCausados().asistencia())
                .dinero("Seguro vida", m -> m.serviciosCausados().seguro())
                .dinero("Servicios", m -> m.serviciosCausados().total())
                .dinero("Pago total cliente", Movimiento::pagoTotalCliente)
                .dinero("Pago FCC", m -> m.pagoTerceros().fcc())
                .dinero("Pago asistencia", m -> m.pagoTerceros().asistencia())
                .dinero("Pago aseguradora", m -> m.pagoTerceros().seguro());

        assertThat(comparador.diferencias(hoja, motor)).isEmpty();
    }

    @Test
    void mesEventoYEstadoCoincidenConElExcel() {
        for (Map<String, String> fila : hoja.filas()) {
            int periodo = Integer.parseInt(fila.get("Periodo"));
            Movimiento m = delPeriodo(motor, periodo);
            assertThat(m.mes()).as("mes del periodo %d", periodo).isEqualTo(Integer.parseInt(fila.get("Mes")));
            if (periodo == periodoAbono) {
                assertThat(m.evento()).isEqualTo(fila.get("Evento"));
            }
            EstadoCredito esperado = switch (fila.get("Estado")) {
                case "Desembolsado" -> EstadoCredito.DESEMBOLSADO;
                case "Vigente" -> EstadoCredito.VIGENTE_AL_DIA;
                case "Amortizado" -> EstadoCredito.CANCELADO;
                default -> throw new IllegalStateException("Estado no esperado: " + fila.get("Estado"));
            };
            assertThat(m.estado()).as("estado del periodo %d", periodo).isEqualTo(esperado);
        }
    }

    /** Panel lateral U2:V6 de la hoja Caso6_MenorPlazo. */
    @Test
    void indicadoresDelPanelLateral() {
        Movimiento conAbono = motor.stream().filter(m -> m.abonoExtra().signum() > 0).findFirst().orElseThrow();
        assertThat(conAbono.periodo()).as("V2 Periodo abono").isEqualTo(43);
        assertThat(conAbono.abonoExtra()).as("V3 Abono extra").isCloseTo(ABONO, within(UN_PESO));
        assertThat(conAbono.saldoFinal()).as("V4 Saldo tras abono")
                .isCloseTo(new BigDecimal("1701573.6220031078"), within(UN_PESO));
        assertThat(delPeriodo(motor, 44).cuotaFinanciera()).as("V5 Cuota posterior")
                .isCloseTo(new BigDecimal("111014.82187676849"), within(UN_PESO));
        assertThat(motor.getLast().periodo()).as("V6 Último periodo con pago").isEqualTo(61);
    }

    @Test
    void laCuotaFinancieraNoCambiaDespuesDelAbonoYLaUltimaEsElResiduo() {
        BigDecimal cuotaPactada = delPeriodo(motor, 1).cuotaFinanciera();
        assertThat(motor.subList(1, motor.size()))
                .allSatisfy(m -> assertThat(m.cuotaFinanciera()).isCloseTo(cuotaPactada, within(UN_PESO)));
        // F64 = MIN(cuota, D64 + E64): la última semana solo paga lo que queda.
        assertThat(cuotaFinancieraPagada(motor.getLast())).isCloseTo(new BigDecimal("6998.926719691869"), within(UN_PESO));
    }

    // ---------------------------------------------------------------- hoja Validaciones

    @Test
    void validacion19AbonoAplicado() {
        assertThat(delPeriodo(motor, 43).abonoExtra()).isCloseTo(ABONO, within(UN_PESO));
    }

    @Test
    void validacion20UltimoPeriodoConPago() {
        int ultimoExcel = hoja.filas().stream().mapToInt(f -> Integer.parseInt(f.get("Periodo"))).max().orElseThrow();
        assertThat(ultimoExcel).isEqualTo(61);
        assertThat(motor.getLast().periodo()).isEqualTo(61);
        assertThat(motor.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
    }

    /** No hay fórmula en el Excel: se define como cuotas pactadas (Inputs!B15 = 86) − último periodo con pago. */
    @Test
    void validacion21CuotasEliminadas() {
        int cuotasEliminadas = Calendario.numeroCuotas(parametros) - motor.getLast().periodo();
        assertThat(cuotasEliminadas).isEqualTo(25);
    }

    // ---------------------------------------------------------------- servicios y giro a terceros

    @Test
    void losServiciosSeCobranHastaLaUltimaSemanaPagadaYNoDespues() {
        BigDecimal serviciosSemana = delPeriodo(motor, 1).serviciosCausados().total();
        assertThat(motor.subList(1, motor.size()))
                .allSatisfy(m -> assertThat(m.serviciosCausados().total()).isCloseTo(serviciosSemana, within(UN_PESO)));
        // Semana 61 (mes 15, que empieza en la 61): servicios completos aunque la cuota financiera sea solo el residuo.
        assertThat(motor.getLast().pagoTotalCliente()).isCloseTo(new BigDecimal("70033.09902738419"), within(UN_PESO));
    }

    @Test
    void elGiroATercerosDeLaUltimaSemanaSoloLlevaElMesIncompleto() {
        Movimiento ultimo = motor.getLast();
        assertThat(ultimo.mes()).isEqualTo(15);
        // Q64:S64: solo la semana 61, que es la única del mes 15.
        assertThat(ultimo.pagoTerceros().total()).isCloseTo(ultimo.serviciosCausados().total(), within(UN_PESO));
        // El mes 14 (semanas 57 a 60) se gira completo en la 60.
        assertThat(delPeriodo(motor, 60).pagoTerceros().fcc()).isCloseTo(new BigDecimal("194572.68923076926"), within(UN_PESO));
        // Todo lo cobrado por servicios se gira.
        BigDecimal cobrado = motor.stream().map(m -> m.serviciosCausados().total()).reduce(BigDecimal.ZERO, Calc::sumar);
        BigDecimal girado = motor.stream().map(m -> m.pagoTerceros().total()).reduce(BigDecimal.ZERO, Calc::sumar);
        assertThat(girado).isCloseTo(cobrado, within(UN_PESO));
    }

    // ---------------------------------------------------------------- casos borde

    /** H46 = MIN(abono, D46 − G46): el abono no puede superar el saldo después del capital ordinario. */
    @Test
    void unAbonoMayorQueElSaldoSeTopaYCancelaElCredito() {
        List<Movimiento> movimientos = new MotorCartera().ejecutar(escenario(Calc.bd("10000000")));
        Movimiento m = movimientos.getLast();
        BigDecimal saldoTrasCuota = Calc.restar(m.saldoInicial(), m.pagoCapital());

        assertThat(m.periodo()).isEqualTo(43);
        assertThat(m.abonoExtra()).isCloseTo(saldoTrasCuota, within(UN_PESO));
        assertThat(m.abonoExtra()).isCloseTo(new BigDecimal("3701573.6220031078"), within(UN_PESO));
        assertThat(m.saldoFinal()).isZero();
        assertThat(m.estado()).isEqualTo(EstadoCredito.CANCELADO);
        // Solo se registra lo aplicado; el excedente (6.298.426,38) no entra al crédito (pregunta para Diego).
        assertThat(m.pagoTotalCliente()).isCloseTo(Calc.sumar(m.pagoRecibido(), saldoTrasCuota), within(UN_PESO));
        // Al cancelar se gira lo cobrado del mes 10.
        assertThat(m.pagoTerceros().fcc()).isCloseTo(new BigDecimal("194572.68923076926"), within(UN_PESO));
    }

    /** El capital contractual sigue al plan nuevo: con la misma cuota y saldo menor no aparece capital vencido. */
    @Test
    void elCapitalContractualSeAjustaAlPlanNuevoSinVencidoFalso() {
        assertThat(motor).allSatisfy(m -> {
            // Residuo de redondeo DECIMAL128 (~1E-27) desde el periodo 1, también sin abono: se tolera con esCero.
            assertThat(Calc.esCero(m.capitalVencido())).as("capital vencido periodo %d", m.periodo()).isTrue();
            assertThat(m.capitalContractual()).as("capital contractual periodo %d", m.periodo())
                    .isCloseTo(m.pagoCapital(), within(UN_PESO));
            assertThat(m.estado()).isNotEqualTo(EstadoCredito.EN_MORA);
            assertThat(m.cuotasVencidas()).isZero();
        });
        // G47: primer capital ordinario después del abono.
        assertThat(delPeriodo(motor, 44).capitalContractual()).isCloseTo(new BigDecimal("90352.10975204768"), within(UN_PESO));
    }

    /** Si después del abono el cliente deja de pagar, el vencido es el capital del plan nuevo, no el del original. */
    @Test
    void unaCuotaNoPagadaDespuesDelAbonoVenceElCapitalDelPlanNuevo() {
        List<Movimiento> movimientos = new MotorCartera().ejecutar(escenario(ABONO).en(50, new SinPago(true)));
        BigDecimal capitalExcel = HojaExcel.numero(hoja.filaDelPeriodo(50), "Capital ordinario").orElseThrow();
        Movimiento m = delPeriodo(movimientos, 50);

        assertThat(m.capitalContractual()).isCloseTo(capitalExcel, within(UN_PESO));
        assertThat(m.capitalVencido()).isCloseTo(capitalExcel, within(UN_PESO));
        assertThat(m.estado()).isEqualTo(EstadoCredito.EN_MORA);
    }
}
