package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

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
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Caso 2 · Cambio de tasa: en el mes 11 la EA pasa de 87,32 % a 80 %; desde la primera cuota de
 * ese mes (periodo 44) se recalcula la cuota conservando la fecha final (cuota 86). Hoja
 * Caso2_CambioTasa. Reglas en docs/reglas/caso-2.md.
 */
class Caso2CambioTasaTest {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final HojaExcel INPUTS = HojaExcel.cargar("inputs");
    private static final HojaExcel VALIDACIONES = HojaExcel.cargar("validaciones");

    /** Inputs!C36: mes del cambio de tasa (11). */
    private static final int MES_CAMBIO = entero(input("C36"));
    /** Inputs!C37: EA nueva (0,80). */
    private static final BigDecimal EA_NUEVA = input("C37");
    /** R-2.1: ROUNDDOWN((mes − 1) × 52/12) + 1 = 44. */
    private static final int PERIODO_CAMBIO = Calendario.primerPeriodoDelMes(MES_CAMBIO, P);

    private final List<Movimiento> motor = new MotorCartera().ejecutar(
            new Escenario(Operacion.ejemploExcel(), P).en(PERIODO_CAMBIO, new CambioTasa(EA_NUEVA)));

    @Test
    void reproduceElCronogramaDelExcelCeldaPorCelda() {
        ComparadorExcel comparador = new ComparadorExcel()
                .dinero("Saldo inicial", Movimiento::saldoInicial)
                .tasa("Tasa semanal", Movimiento::tasaSemanal)
                .dinero("Interés", Movimiento::interesCorriente)
                .dinero("Cuota financiera", Movimiento::cuotaFinanciera)
                .dinero("Capital", Movimiento::pagoCapital)
                .dinero("FCC+IVA", m -> m.serviciosCausados().fcc())
                .dinero("Asistencia+IVA", m -> m.serviciosCausados().asistencia())
                .dinero("Seguro vida", m -> m.serviciosCausados().seguro())
                .dinero("Servicios", m -> m.serviciosCausados().total())
                .dinero("Pago total", Movimiento::pagoTotalCliente)
                .dinero("Saldo final", Movimiento::saldoFinal)
                .dinero("Pago FCC", m -> m.pagoTerceros().fcc())
                .dinero("Pago asistencia", m -> m.pagoTerceros().asistencia())
                .dinero("Pago aseguradora", m -> m.pagoTerceros().seguro());

        assertThat(comparador.diferencias(HojaExcel.cargar("Caso2_CambioTasa"), motor)).isEmpty();
    }

    @Test
    void elEventoDeCambioDeTasaQuedaEnElPeriodoDelExcel() {
        HojaExcel hoja = HojaExcel.cargar("Caso2_CambioTasa");
        assertThat(hoja.filaDelPeriodo(PERIODO_CAMBIO).get("Evento")).isEqualTo("Cambio de tasa");
        assertThat(fila(PERIODO_CAMBIO).evento()).isEqualTo("Cambio de tasa");
        assertThat(motor).filteredOn(m -> m.evento().equals("Cambio de tasa")).hasSize(1);
    }

    // ---- Panel lateral "Indicadores del caso" (T1:U5) ----

    @Test
    void indicadorNuevaCuota() {
        // U2 = INDEX(G4:G89, Inputs!C39)
        assertThat(fila(PERIODO_CAMBIO).cuotaFinanciera())
                .isCloseTo(new BigDecimal("109308.38403873703"), offset(ComparadorExcel.TOLERANCIA_DINERO));
    }

    @Test
    void indicadorInteresesTotales() {
        // U3 = SUM(F4:F89)
        assertThat(interesesTotales(motor))
                .isCloseTo(new BigDecimal("3569497.8543667374"), offset(ComparadorExcel.TOLERANCIA_DINERO));
    }

    @Test
    void indicadorReduccionDeInteresesFrenteAlCaso1() {
        // U4 = SUM(Caso1_Normal!F4:F89) − SUM(F4:F89)
        List<Movimiento> caso1 = new MotorCartera().ejecutar(new Escenario(Operacion.ejemploExcel(), P));
        BigDecimal reduccion = Calc.restar(interesesTotales(caso1), interesesTotales(motor));
        assertThat(reduccion).isCloseTo(new BigDecimal("73376.82703535538"), offset(ComparadorExcel.TOLERANCIA_DINERO));
    }

    @Test
    void indicadorSaldoFinalYFechaFinalConservada() {
        // U5 = N89. El cambio de tasa conserva la fecha final: el crédito cierra en la cuota 86.
        Movimiento ultimo = motor.getLast();
        assertThat(ultimo.periodo()).isEqualTo(Calendario.numeroCuotas(P)).isEqualTo(86);
        assertThat(ultimo.estado()).isEqualTo(EstadoCredito.CANCELADO);
        assertThat(ultimo.saldoFinal()).isCloseTo(BigDecimal.ZERO, offset(ComparadorExcel.TOLERANCIA_DINERO));
    }

    @Test
    void elCambioDeTasaNoGeneraMovimientoExtraordinario() {
        // Panel T7:W10: "Sin asiento extraordinario". La semana 44 es un pago contractual normal con la
        // cuota nueva; no hay abonos, prepagos, cartera vencida ni cierre que contabilizar.
        Movimiento cambio = fila(PERIODO_CAMBIO);
        assertThat(cambio.pagoRecibido()).isEqualByComparingTo(
                Calc.sumar(cambio.cuotaFinanciera(), cambio.serviciosCausados().total()));
        assertThat(List.of(cambio.abonoExtra(), cambio.prepagoCapital(), cambio.capitalVencido(), cambio.interesMora(),
                cambio.cobranzaCausada(), cambio.deudaAExtinguir()))
                // Capital vencido y mora quedan en ~1e-27 por el ruido de DECIMAL128 (saldoPlan vs saldo).
                .allSatisfy(v -> assertThat(v).isCloseTo(BigDecimal.ZERO, offset(ComparadorExcel.TOLERANCIA_DINERO)));
        assertThat(cambio.estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
    }

    // ---- Hoja Validaciones, pruebas 6 a 9 ----

    @Test
    void validacion6PeriodoDelCambioEs44Exacto() {
        Map<String, String> v = validacion(6);
        assertThat(BigDecimal.valueOf(PERIODO_CAMBIO))
                .isCloseTo(input("C39"), offset(tolerancia(v)))
                .isCloseTo(new BigDecimal(v.get("Periodo")), offset(tolerancia(v)));
        assertThat(PERIODO_CAMBIO).isEqualTo(44);
        assertThat(Calendario.mes(PERIODO_CAMBIO, P)).isEqualTo(entero(new BigDecimal(v.get("Mes"))));
    }

    @Test
    void validacion7TasaSemanalNueva() {
        Map<String, String> v = validacion(7);
        BigDecimal esperada = input("C38"); // (1 + C37)^(1/52) − 1
        assertThat(fila(periodo(v)).tasaSemanal()).isCloseTo(esperada, offset(tolerancia(v)));
        assertThat(Tasas.desdeEA(EA_NUEVA, 52, 12).semanal()).isCloseTo(esperada, offset(tolerancia(v)));
        // Antes del cambio rige la tasa original (Inputs!B17).
        assertThat(fila(PERIODO_CAMBIO - 1).tasaSemanal()).isCloseTo(input("B17"), offset(tolerancia(v)));
    }

    @Test
    void validacion8NuevaCuota() {
        Map<String, String> v = validacion(8);
        Movimiento cambio = fila(periodo(v));
        BigDecimal esperada = HojaExcel.numero(HojaExcel.cargar("Caso2_CambioTasa").filaDelPeriodo(periodo(v)),
                "Cuota financiera").orElseThrow();
        assertThat(cambio.cuotaFinanciera()).isCloseTo(esperada, offset(tolerancia(v)));
        // G47 = PMT(tasa nueva, 86 − 44 + 1, saldo inicial del periodo 44)
        BigDecimal pmt = Amortizacion.cuotaFija(cambio.tasaSemanal(), Calendario.numeroCuotas(P) - PERIODO_CAMBIO + 1,
                cambio.saldoInicial());
        assertThat(cambio.cuotaFinanciera()).isCloseTo(pmt, offset(tolerancia(v)));
        // La cuota nueva se mantiene hasta el final.
        assertThat(motor.subList(PERIODO_CAMBIO, motor.size()))
                .allSatisfy(m -> assertThat(m.cuotaFinanciera()).isCloseTo(esperada, offset(tolerancia(v))));
    }

    @Test
    void validacion9SaldoFinal() {
        Map<String, String> v = validacion(9);
        assertThat(fila(periodo(v)).saldoFinal()).isCloseTo(BigDecimal.ZERO, offset(tolerancia(v)));
    }

    // ---- utilidades ----

    private Movimiento fila(int periodo) {
        return motor.stream().filter(m -> m.periodo() == periodo).findFirst().orElseThrow();
    }

    private static BigDecimal interesesTotales(List<Movimiento> movimientos) {
        return movimientos.stream().map(Movimiento::interesCorriente).reduce(BigDecimal.ZERO, Calc::sumar);
    }

    private static BigDecimal input(String celda) {
        return INPUTS.filas().stream().filter(f -> f.get("celda").equals(celda)).findFirst()
                .flatMap(f -> HojaExcel.numero(f, "valor"))
                .orElseThrow(() -> new IllegalArgumentException("Inputs sin la celda " + celda));
    }

    private static Map<String, String> validacion(int numero) {
        Map<String, String> v = VALIDACIONES.filaDelPeriodo(numero);
        assertThat(v.get("Caso")).isEqualTo("Caso 2");
        return v;
    }

    private static BigDecimal tolerancia(Map<String, String> validacion) {
        return HojaExcel.numero(validacion, "Tolerancia").orElseThrow();
    }

    private static int periodo(Map<String, String> validacion) {
        return Integer.parseInt(validacion.get("Periodo"));
    }

    private static int entero(BigDecimal valor) {
        return valor.intValueExact();
    }
}
