package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.reglas.CobranzaYMora;
import co.financiera.cartera.excel.ComparadorExcel;
import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Caso 9 · Pago tardío y gastos de cobranza. Hoja Caso9_Cobranza.
 *
 * <p>Desde el mes 10 (periodo 43) el cliente paga cada cuota con 14 días de atraso: se causan
 * 42.000 de cobranza y mora sobre el vencido + mora por días sobre el capital de la cuota. La cuota
 * pactada ya no alcanza para amortizar el plan, el capital se va a vencido y al horizonte de 120
 * semanas el crédito sigue en mora con saldo 3.319.011,75.
 */
class Caso9CobranzaTest {

    private static final int PERIODO_INICIO = 43;
    private static final int DIAS_ATRASO = 14;
    private static final int PLAZO_LIMITE = 120;
    private static final BigDecimal TOL = ComparadorExcel.TOLERANCIA_DINERO;

    private final HojaExcel hoja = HojaExcel.cargar("Caso9_Cobranza");
    private final List<Movimiento> motor = new MotorCartera().ejecutar(escenario());

    static Escenario escenario() {
        return new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel().conPlazoLimite(PLAZO_LIMITE))
                .desde(PERIODO_INICIO, new PagoContractual(DIAS_ATRASO));
    }

    private Movimiento periodo(int n) {
        return motor.stream().filter(m -> m.periodo() == n).findFirst().orElseThrow();
    }

    private BigDecimal excel(int periodo, String columna) {
        return HojaExcel.numero(hoja.filaDelPeriodo(periodo), columna).orElseThrow();
    }

    // ---------------------------------------------------------------- cronograma

    @Test
    void reproduceTodasLasColumnasNumericasDelExcelHastaElPeriodo120() {
        ComparadorExcel comparador = new ComparadorExcel()
                .dinero("Mes", m -> Calc.bd(m.mes()))
                .dinero("Saldo inicial", Movimiento::saldoInicial)
                .dinero("Interés corriente", Movimiento::interesCorriente)
                .dinero("Interés mora", Movimiento::interesMora)
                .dinero("Capital contractual", Movimiento::capitalContractual)
                .dinero("Cobranza causada", Movimiento::cobranzaCausada)
                .dinero("Pago recibido (cuota total)", Movimiento::pagoRecibido)
                .dinero("Pago cobranza", Movimiento::pagoCobranza)
                .dinero("Pago mora", Movimiento::pagoMora)
                .dinero("Pago interés corriente", Movimiento::pagoInteresCorriente)
                .dinero("Pago capital", Movimiento::pagoCapital)
                .dinero("CxC cobranza", Movimiento::cxcCobranza)
                .dinero("CxC mora", Movimiento::cxcMora)
                .dinero("CxC interés corriente", Movimiento::cxcInteresCorriente)
                .dinero("Capital vencido", Movimiento::capitalVencido)
                .dinero("Capital vigente", Movimiento::capitalVigente)
                .dinero("Saldo capital final", Movimiento::saldoFinal)
                .dinero("FCC+IVA", m -> m.serviciosCausados().fcc())
                .dinero("Asistencia+IVA", m -> m.serviciosCausados().asistencia())
                .dinero("Seguro vida", m -> m.serviciosCausados().seguro())
                .dinero("Servicios causados", m -> m.serviciosCausados().total())
                .dinero("Pago servicios", Movimiento::pagoServicios)
                .dinero("CxC servicios", Movimiento::cxcServicios)
                .dinero("Pago FCC", m -> m.pagoTerceros().fcc())
                .dinero("Pago asistencia", m -> m.pagoTerceros().asistencia())
                .dinero("Pago aseguradora", m -> m.pagoTerceros().seguro());

        assertThat(comparador.diferencias(hoja, motor)).isEmpty();
    }

    @Test
    void elEstadoDeCadaSemanaCoincideConElExcel() {
        Map<String, EstadoCredito> estados = Map.of("Desembolsado", EstadoCredito.DESEMBOLSADO, "Vigente al día",
                EstadoCredito.VIGENTE_AL_DIA, "En mora", EstadoCredito.EN_MORA, "Cancelado", EstadoCredito.CANCELADO);
        List<String> errores = new ArrayList<>();
        for (Map<String, String> fila : hoja.filas()) {
            int n = Integer.parseInt(fila.get("Periodo"));
            EstadoCredito esperado = estados.get(fila.get("Estado"));
            if (periodo(n).estado() != esperado) {
                errores.add("periodo " + n + ": Excel " + fila.get("Estado") + ", motor " + periodo(n).estado());
            }
        }
        assertThat(errores).isEmpty();
    }

    @Test
    void elCreditoNoTerminaYSeProyectaHastaElPlazoLimite() {
        assertThat(motor.getLast().periodo()).isEqualTo(PLAZO_LIMITE);
        assertThat(motor.getLast().estado()).isEqualTo(EstadoCredito.EN_MORA);
    }

    // ---------------------------------------------------------------- validaciones 29 a 33

    /** Fila de la hoja Validaciones con el # dado. */
    private static Map<String, String> validacion(int numero) {
        return HojaExcel.cargar("validaciones").filas().stream()
                .filter(f -> f.get("#").equals(String.valueOf(numero)))
                .findFirst().orElseThrow();
    }

    private void validarDinero(int numero, String variable, String columnaCaso9, Function<Movimiento, BigDecimal> dato,
            String valorExcel) {
        Map<String, String> v = validacion(numero);
        assertThat(v.get("Caso")).isEqualTo("Caso 9");
        assertThat(v.get("Variable")).isEqualTo(variable);
        int n = Integer.parseInt(v.get("Periodo"));
        assertThat(n).isEqualTo(PERIODO_INICIO);
        assertThat(v.get("Mes")).isEqualTo("10");
        BigDecimal tolerancia = new BigDecimal(v.get("Tolerancia"));
        // El valor de la hoja del caso y el valor conocido del Excel (AF5:AF8) deben coincidir con el motor.
        assertThat(dato.apply(periodo(n))).isCloseTo(excel(n, columnaCaso9), within(tolerancia));
        assertThat(dato.apply(periodo(n))).isCloseTo(new BigDecimal(valorExcel), within(tolerancia));
    }

    @Test
    void validacion29CobranzaCausadaEnElPrimerAtraso() {
        validarDinero(29, "Cobranza causada", "Cobranza causada", Movimiento::cobranzaCausada, "42000");
    }

    @Test
    void validacion30InteresMoraEnElPrimerAtraso() {
        validarDinero(30, "Interés mora", "Interés mora", Movimiento::interesMora, "1590.4588122676764");
    }

    @Test
    void validacion31CapitalPagadoEnElPrimerAtraso() {
        validarDinero(31, "Capital pagado", "Pago capital", Movimiento::pagoCapital, "21682.43095863687");
    }

    @Test
    void validacion32CapitalVencidoAlCierreDelPrimerAtraso() {
        validarDinero(32, "Capital vencido", "Capital vencido", Movimiento::capitalVencido, "43590.45881226766");
    }

    @Test
    void validacion33EstadoAlHorizonteEnMoraExacto() {
        Map<String, String> v = validacion(33);
        assertThat(v.get("Variable")).isEqualTo("Estado");
        assertThat(v.get("Tipo")).isEqualTo("Exacto");
        String estadoExcel = hoja.filas().getLast().get("Estado");
        assertThat(estadoExcel).isEqualTo("En mora");
        assertThat(motor.getLast().estado()).isEqualTo(EstadoCredito.EN_MORA);
    }

    // ---------------------------------------------------------------- indicadores AE3:AF12

    @Test
    void indicadoresDelPanel() {
        Movimiento primer = periodo(PERIODO_INICIO);
        Movimiento ultimo = motor.getLast();
        assertThat(primer.cobranzaCausada()).isCloseTo(new BigDecimal("42000"), within(TOL)); // AF5
        assertThat(primer.interesMora()).isCloseTo(new BigDecimal("1590.4588122676764"), within(TOL)); // AF6
        assertThat(primer.pagoCapital()).isCloseTo(new BigDecimal("21682.43095863687"), within(TOL)); // AF7
        assertThat(primer.capitalVencido()).isCloseTo(new BigDecimal("43590.45881226766"), within(TOL)); // AF8
        assertThat(ultimo.saldoFinal()).isCloseTo(new BigDecimal("3319011.7496506763"), within(TOL)); // AF9
        assertThat(ultimo.estado()).isEqualTo(EstadoCredito.EN_MORA); // AF10
        assertThat(primer.pagoServicios()).isCloseTo(new BigDecimal("63034.172307692315"), within(TOL)); // AF11
        // AF12 es una fórmula matricial =LOOKUP(2,1/(A4:A123<>""),Z4:Z123): la CxC servicios de la última fila.
        assertThat(ultimo.cxcServicios()).isCloseTo(new BigDecimal("70732.20002945513"), within(TOL));
        assertThat(ultimo.cxcServicios()).isCloseTo(excel(PLAZO_LIMITE, "CxC servicios"), within(TOL));
    }

    // ---------------------------------------------------------------- contabilidad AE13:AH25

    @Test
    void laReglaContableReproduceElPanelDelPeriodo43() {
        Asiento a = new CobranzaYMora().contabilizar(periodo(PERIODO_INICIO)).orElseThrow();

        assertThat(a.lineas()).hasSize(10);
        linea(a, 0, Cuenta.CXC_GASTOS_COBRANZA, "42000", "0");
        linea(a, 1, Cuenta.INGRESOS_COBRANZA, "0", "42000");
        linea(a, 2, Cuenta.CXC_INTERES_MORA, "1590.4588122676764", "0");
        linea(a, 3, Cuenta.INGRESOS_MORA, "0", "1590.4588122676764");
        linea(a, 4, Cuenta.CAJA, "174048.9941844608", "0");
        linea(a, 5, Cuenta.CXC_GASTOS_COBRANZA, "0", "42000");
        linea(a, 6, Cuenta.CXC_INTERES_MORA, "0", "1590.4588122676764");
        linea(a, 7, Cuenta.CXC_INTERES_CORRIENTE, "0", "45741.932105863954");
        linea(a, 8, Cuenta.CXP_SERVICIOS_TERCEROS, "0", "63034.172307692315");
        linea(a, 9, Cuenta.CARTERA_VIGENTE, "0", "21682.43095863687");
        // Control AF25 = AG25 = 217.639,45
        assertThat(a.totalDebitos()).isCloseTo(new BigDecimal("217639.45299672848"), within(TOL));
        assertThat(a.totalCreditos()).isCloseTo(new BigDecimal("217639.4529967285"), within(TOL));
        assertThat(a.cuadra()).isTrue();
    }

    private static void linea(Asiento a, int i, Cuenta cuenta, String debito, String credito) {
        Asiento.Linea l = a.lineas().get(i);
        assertThat(l.cuenta()).as("cuenta de la línea %d", i).isEqualTo(cuenta);
        assertThat(l.debito()).as("débito %s", cuenta).isCloseTo(new BigDecimal(debito), within(TOL));
        assertThat(l.credito()).as("crédito %s", cuenta).isCloseTo(new BigDecimal(credito), within(TOL));
    }

    @Test
    void laReglaSoloAplicaALosPagosTardiosConCobranza() {
        for (Movimiento m : motor) {
            Optional<Asiento> a = new CobranzaYMora().contabilizar(m);
            assertThat(a.isPresent()).as("periodo %d", m.periodo()).isEqualTo(m.periodo() >= PERIODO_INICIO);
        }
    }

    @Test
    void todosLosAsientosDelCasoCuadran() {
        // El contabilizador lanza AsientoDescuadradoException si alguno no cuadra.
        List<Asiento> asientos = Contabilizador.estandar().contabilizar(motor);
        assertThat(asientos).isNotEmpty();
    }

    @Test
    void enUnPagoTardioLaCajaSeDebitaUnaSolaVez() {
        // Frontera con RecaudoCuota (caso 1) y PagoInferior (caso 8): con cobranza causada solo
        // CobranzaYMora contabiliza el recaudo; si otra regla también lo hiciera, la caja se duplicaría.
        Movimiento m = periodo(PERIODO_INICIO);
        BigDecimal caja = Contabilizador.estandar().contabilizar(m).stream()
                .flatMap(a -> a.lineas().stream())
                .filter(l -> l.cuenta() == Cuenta.CAJA)
                .map(Asiento.Linea::debito)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(caja).isCloseTo(m.pagoTotalCliente(), within(TOL));
    }
}
