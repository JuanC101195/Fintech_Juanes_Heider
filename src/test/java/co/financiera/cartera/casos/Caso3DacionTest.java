package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.reglas.DacionEnPago;
import co.financiera.cartera.excel.ComparadorExcel;
import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.TipoRecuperacion;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Caso 3 · Dación en pago. Hoja Caso3_Dacion. El cliente paga hasta el periodo 30 (mes 7), deja
 * de pagar del 31 al 33 y en el 34 (mes 8, siniestro) entrega la moto por 3.000.000; el faltante
 * queda como CxC al fondo de garantías (FCC).
 *
 * <p>Mientras no paga, el Excel solo acumula interés corriente: sin mora, sin capital vencido y sin
 * servicios (D8, pendiente P7). Por eso se usa {@code SinPago(false)}.
 */
class Caso3DacionTest {

    private static final String HOJA = "Caso3_Dacion";

    private final HojaExcel inputs = HojaExcel.cargar("inputs");
    /** Inputs!C43: ROUNDDOWN(último mes pagado × 52/12). */
    private final int ultimoPeriodoPagado = entero("C43");
    /** Inputs!C44: ROUNDDOWN(mes del siniestro × 52/12). */
    private final int periodoSiniestro = entero("C44");
    /** Inputs!C42. */
    private final BigDecimal valorDacion = decimal("C42");

    private final List<Movimiento> motor = new MotorCartera().ejecutar(
            new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel())
                    .entre(ultimoPeriodoPagado + 1, periodoSiniestro, new SinPago(false))
                    .en(periodoSiniestro, new CierrePorRecuperacion(TipoRecuperacion.DACION_EN_PAGO, valorDacion)));

    @Test
    void losParametrosDelCasoSonLosDelExcel() {
        assertThat(ultimoPeriodoPagado).isEqualTo(30);
        assertThat(periodoSiniestro).isEqualTo(34);
        assertThat(valorDacion).isEqualByComparingTo("3000000");
    }

    @Test
    void reproduceElCronogramaDelExcelCeldaPorCelda() {
        ComparadorExcel comparador = new ComparadorExcel()
                .dinero("Saldo inicial", Movimiento::saldoInicial)
                .dinero("Interés corriente", Movimiento::interesCorriente)
                // F: solo la parte financiera del pago (interés + capital); los servicios van en T.
                .dinero("Pago financiero", m -> m.pagoRecibido().subtract(m.pagoServicios()))
                .dinero("Interés pagado", Movimiento::pagoInteresCorriente)
                .dinero("Capital pagado", Movimiento::pagoCapital)
                .dinero("CxC interés corriente", Movimiento::cxcInteresCorriente)
                .dinero("Saldo capital final", Movimiento::saldoFinal)
                .dinero("Cuotas vencidas", m -> BigDecimal.valueOf(m.cuotasVencidas()))
                .dinero("Deuda a extinguir", Movimiento::deudaAExtinguir)
                .dinero("Inventario", Movimiento::inventario)
                .dinero("CxC FCC", Movimiento::cxcFcc)
                .dinero("CxP deudor", Movimiento::cxpDeudor)
                .dinero("FCC+IVA", m -> m.serviciosCausados().fcc())
                .dinero("Asistencia+IVA", m -> m.serviciosCausados().asistencia())
                .dinero("Seguro vida", m -> m.serviciosCausados().seguro())
                .dinero("Servicios", m -> m.serviciosCausados().total())
                .dinero("Pago total cliente", Movimiento::pagoTotalCliente)
                .dinero("Pago FCC", m -> m.pagoTerceros().fcc())
                .dinero("Pago asistencia", m -> m.pagoTerceros().asistencia())
                .dinero("Pago aseguradora", m -> m.pagoTerceros().seguro());

        assertThat(comparador.diferencias(HojaExcel.cargar(HOJA), motor)).isEmpty();
    }

    @Test
    void elEventoYElEstadoDeCadaSemanaSonLosDelExcel() {
        HojaExcel hoja = HojaExcel.cargar(HOJA);
        List<String> diferencias = new ArrayList<>();
        for (Map<String, String> fila : hoja.filas()) {
            int periodo = Integer.parseInt(fila.get("Periodo"));
            Movimiento m = delPeriodo(periodo);
            if (!fila.get("Evento").equals(m.evento())) {
                diferencias.add("periodo " + periodo + " · Evento: Excel " + fila.get("Evento") + ", motor " + m.evento());
            }
            if (!fila.get("Estado").equals(estadoExcel(m.estado()))) {
                diferencias.add("periodo " + periodo + " · Estado: Excel " + fila.get("Estado") + ", motor " + m.estado());
            }
            if (Integer.parseInt(fila.get("Mes")) != m.mes()) {
                diferencias.add("periodo " + periodo + " · Mes: Excel " + fila.get("Mes") + ", motor " + m.mes());
            }
        }
        assertThat(diferencias).isEmpty();
    }

    @Test
    void mientrasNoPagaSoloAcumulaInteresCorriente() {
        for (int periodo = ultimoPeriodoPagado + 1; periodo < periodoSiniestro; periodo++) {
            Movimiento m = delPeriodo(periodo);
            // Al cerrar el periodo 30 queda un residuo de redondeo DECIMAL128 (~1e-29) en capital
            // vencido: por eso "cero" es con la tolerancia de saldo de Calc.esCero.
            assertThat(Calc.esCero(m.interesMora())).as("mora en %d: %s", periodo, m.interesMora()).isTrue();
            assertThat(Calc.esCero(m.capitalVencido())).as("capital vencido en %d", periodo).isTrue();
            assertThat(m.capitalContractual()).as("capital contractual en %d", periodo).isZero();
            assertThat(m.cobranzaCausada()).as("cobranza en %d", periodo).isZero();
            assertThat(m.serviciosCausados().total()).as("servicios en %d", periodo).isZero();
            assertThat(m.cxcServicios()).as("CxC servicios en %d", periodo).isZero();
        }
    }

    @Test
    void elCreditoTerminaEnElSiniestro() {
        assertThat(motor.getLast().periodo()).isEqualTo(periodoSiniestro);
        assertThat(motor.getLast().estado()).isEqualTo(EstadoCredito.CERRADO_POR_EVENTO);
    }

    /** Panel "Indicadores del caso" (Z2:AA5): INDEX de las columnas M, N, O y J en el periodo del siniestro. */
    @Test
    void indicadoresDelPanel() {
        Map<String, String> excel = HojaExcel.cargar(HOJA).filaDelPeriodo(periodoSiniestro);
        Movimiento m = delPeriodo(periodoSiniestro);
        assertThat(m.inventario()).as("AA2 Inventario reconocido").isCloseTo(numero(excel, "Inventario"), dinero());
        assertThat(m.cxcFcc()).as("AA3 CxC FCC").isCloseTo(numero(excel, "CxC FCC"), dinero());
        assertThat(m.cxpDeudor()).as("AA4 CxP deudor").isCloseTo(numero(excel, "CxP deudor"), dinero());
        assertThat(m.saldoFinal()).as("AA5 Saldo capital posterior").isCloseTo(numero(excel, "Saldo capital final"), dinero());
        // Valores calculados del Excel (data_only), por si cambia la hoja sin regenerar el CSV.
        assertThat(m.deudaAExtinguir()).isCloseTo(new BigDecimal("4709819.753416771"), dinero());
        assertThat(m.cxcFcc()).isCloseTo(new BigDecimal("1709819.753416771"), dinero());
    }

    /** Validaciones 10 a 13: la variable de la hoja Caso3 en el periodo y mes indicados, con su tolerancia. */
    @Test
    void validaciones10a13() {
        HojaExcel validaciones = HojaExcel.cargar("validaciones");
        HojaExcel hoja = HojaExcel.cargar(HOJA);
        List<String> revisadas = new ArrayList<>();
        for (Map<String, String> v : validaciones.filas()) {
            int numero = Integer.parseInt(v.get("#"));
            if (numero < 10 || numero > 13) {
                continue;
            }
            assertThat(v.get("Caso")).isEqualTo("Caso 3");
            int periodo = Integer.parseInt(v.get("Periodo"));
            String variable = v.get("Variable");
            BigDecimal tolerancia = new BigDecimal(v.get("Tolerancia"));
            BigDecimal esperado = numero(hoja.filaDelPeriodo(periodo), variable);
            Movimiento m = delPeriodo(periodo);
            assertThat(m.mes()).as("validación %d: mes", numero).isEqualTo(Integer.parseInt(v.get("Mes")));
            BigDecimal obtenido = switch (variable) {
                case "Cuotas vencidas" -> BigDecimal.valueOf(m.cuotasVencidas());
                case "Deuda a extinguir" -> m.deudaAExtinguir();
                case "Inventario" -> m.inventario();
                case "CxC FCC" -> m.cxcFcc();
                default -> throw new IllegalStateException("Variable no esperada: " + variable);
            };
            assertThat(obtenido.subtract(esperado).abs())
                    .as("validación %d (%s): Excel %s, motor %s", numero, variable, esperado, obtenido)
                    .isLessThanOrEqualTo(tolerancia);
            revisadas.add(variable);
        }
        assertThat(revisadas).containsExactly("Cuotas vencidas", "Deuda a extinguir", "Inventario", "CxC FCC");
        // Conteo con tolerancia 0: exactamente 4 cuotas vencidas (periodos 31 a 34).
        assertThat(delPeriodo(periodoSiniestro).cuotasVencidas()).isEqualTo(4);
    }

    /**
     * Panel "Movimiento contable clave del evento" (Z8:AC14). Las fórmulas del panel se aplican
     * sobre la fila del siniestro del CSV: AA10 = M, AA11 = N, AB12 = D, AB13 = L − D.
     */
    @Test
    void asientoDeLaDacionReproduceElPanelContable() {
        Map<String, String> excel = HojaExcel.cargar(HOJA).filaDelPeriodo(periodoSiniestro);
        List<Asiento> asientos = Contabilizador.estandar().contabilizar(motor).stream()
                .filter(a -> a.periodo() == periodoSiniestro)
                .toList();
        Asiento asiento = new DacionEnPago().contabilizar(delPeriodo(periodoSiniestro)).orElseThrow();
        assertThat(asientos).contains(asiento);

        assertThat(debito(asiento, Cuenta.INVENTARIO_MOTOS)).as("AA10").isCloseTo(numero(excel, "Inventario"), dinero());
        assertThat(debito(asiento, Cuenta.CXC_FCC)).as("AA11").isCloseTo(numero(excel, "CxC FCC"), dinero());
        assertThat(credito(asiento, Cuenta.CARTERA_VIGENTE)).as("AB12").isCloseTo(numero(excel, "Saldo inicial"), dinero());
        assertThat(credito(asiento, Cuenta.CXC_INTERES_CORRIENTE)).as("AB13")
                .isCloseTo(numero(excel, "Deuda a extinguir").subtract(numero(excel, "Saldo inicial")), dinero());
        // Valores calculados del panel.
        assertThat(credito(asiento, Cuenta.CARTERA_VIGENTE)).isCloseTo(new BigDecimal("4491646.196595652"), dinero());
        assertThat(credito(asiento, Cuenta.CXC_INTERES_CORRIENTE)).isCloseTo(new BigDecimal("218173.55682111904"), dinero());
        // Control AC14 = AA14 − AB14 = 0, y solo las cuatro cuentas del panel.
        assertThat(asiento.totalDebitos()).isCloseTo(new BigDecimal("4709819.753416771"), dinero());
        assertThat(asiento.cuadra()).isTrue();
        assertThat(asiento.lineas()).extracting(Asiento.Linea::cuenta).containsExactly(Cuenta.INVENTARIO_MOTOS,
                Cuenta.CXC_FCC, Cuenta.CARTERA_VIGENTE, Cuenta.CXC_INTERES_CORRIENTE);
    }

    @Test
    void laReglaDeDacionNoAplicaEnSemanasNormalesNiSinPago() {
        DacionEnPago regla = new DacionEnPago();
        assertThat(motor.stream().filter(m -> m.periodo() != periodoSiniestro).map(regla::contabilizar))
                .allMatch(Optional::isEmpty);
    }

    // --- ayudas ---

    private Movimiento delPeriodo(int periodo) {
        return motor.stream().filter(m -> m.periodo() == periodo).findFirst().orElseThrow();
    }

    private static String estadoExcel(EstadoCredito estado) {
        return switch (estado) {
            case DESEMBOLSADO -> "Desembolsado";
            case VIGENTE_AL_DIA -> "Vigente al día";
            case EN_MORA -> "En mora";
            case CANCELADO -> "Cancelado";
            case CERRADO_POR_EVENTO -> "Cerrado por evento";
        };
    }

    private static BigDecimal numero(Map<String, String> fila, String columna) {
        return HojaExcel.numero(fila, columna).orElseThrow();
    }

    private static org.assertj.core.data.Offset<BigDecimal> dinero() {
        return within(ComparadorExcel.TOLERANCIA_DINERO);
    }

    private static BigDecimal debito(Asiento a, Cuenta cuenta) {
        return a.lineas().stream().filter(l -> l.cuenta() == cuenta).findFirst().orElseThrow().debito();
    }

    private static BigDecimal credito(Asiento a, Cuenta cuenta) {
        return a.lineas().stream().filter(l -> l.cuenta() == cuenta).findFirst().orElseThrow().credito();
    }

    private String valorInput(String celda) {
        return inputs.filas().stream()
                .filter(f -> f.get("celda").equals(celda))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("inputs.csv no tiene " + celda))
                .get("valor");
    }

    private int entero(String celda) {
        return new BigDecimal(valorInput(celda)).intValueExact();
    }

    private BigDecimal decimal(String celda) {
        return new BigDecimal(valorInput(celda));
    }
}
