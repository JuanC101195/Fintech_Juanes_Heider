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
    private final int ultimoPeriodoPagado = entero("C47");
    /** Inputs!C44: ROUNDDOWN(mes del siniestro × 52/12). */
    private final int periodoSiniestro = entero("C48");
    /** Inputs!C42. */
    private final BigDecimal valorDacion = decimal("C46");

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
                .dinero("Interés corriente (s/ capital vigente)", Movimiento::interesCorriente)
                // F: solo la parte financiera del pago (interés + capital); los servicios van en T.
                .dinero("Pago financiero", m -> m.pagoRecibido().subtract(m.pagoServicios()))
                .dinero("Interés pagado", Movimiento::pagoInteresCorriente)
                .dinero("Capital pagado", Movimiento::pagoCapital)
                .dinero("CxC interés corriente", Movimiento::cxcInteresCorriente)
                .dinero("Saldo capital final", Movimiento::saldoFinal)
                .dinero("Cuotas vencidas", m -> BigDecimal.valueOf(m.cuotasVencidas()))
                .dinero("Deuda a extinguir", Movimiento::deudaAExtinguir)
                .dinero("Inventario", Movimiento::inventario)
                .dinero("CxC FCC", Movimiento::cxcFondoGarantias)
                .dinero("CxP deudor", Movimiento::cxpDeudor)
                .dinero("FCC+IVA", m -> m.serviciosCausados().fondoGarantias())
                .dinero("Asistencia+IVA", m -> m.serviciosCausados().asistencia())
                .dinero("Seguro vida", m -> m.serviciosCausados().seguro())
                .dinero("Servicios", m -> m.serviciosCausados().total())
                .dinero("Pago total cliente", Movimiento::pagoTotalCliente)
                .dinero("Pago FCC", m -> m.pagoTerceros().fondoGarantias())
                .dinero("Pago asistencia", m -> m.pagoTerceros().asistencia())
                .dinero("Pago aseguradora", m -> m.pagoTerceros().seguro())
                .dinero("Capital contractual", Movimiento::capitalContractual)
                .dinero("Capital vencido", Movimiento::capitalVencido)
                .dinero("Interés mora causado", Movimiento::interesMora)
                .dinero("CxC interés mora", Movimiento::cxcMora)
                .dinero("Pago FCC+IVA aplicado", m -> m.pagosServicios().fondoGarantias())
                .dinero("Pago asistencia+IVA aplicado", m -> m.pagosServicios().asistencia())
                .dinero("Pago seguro aplicado", m -> m.pagosServicios().seguro())
                .dinero("CxC FCC+IVA cliente", m -> m.cxcServiciosPorConcepto().fondoGarantias())
                .dinero("CxC asistencia+IVA", m -> m.cxcServiciosPorConcepto().asistencia())
                .dinero("CxC seguro", m -> m.cxcServiciosPorConcepto().seguro());

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

    /**
     * Inputs ⑥ y P-16: mientras el cliente no paga se acumula capital vencido, se causa mora sobre él
     * (capital vencido × tasa periódica) y solo el seguro; fondo de garantías y asistencia no se causan.
     */
    @Test
    void mientrasNoPagaCausaMoraYSoloElSeguro() {
        BigDecimal vencidoAnterior = BigDecimal.ZERO;
        for (int periodo = ultimoPeriodoPagado + 1; periodo <= periodoSiniestro; periodo++) {
            Movimiento m = delPeriodo(periodo);
            assertThat(m.pagoRecibido()).as("pago en %d", periodo).isZero();
            assertThat(m.interesMora()).as("mora en %d", periodo)
                    .isCloseTo(Calc.multiplicar(vencidoAnterior, m.tasaPeriodica()), dinero());
            assertThat(m.capitalContractual()).as("capital contractual en %d", periodo).isPositive();
            assertThat(m.serviciosCausados().fondoGarantias()).as("fondo en %d", periodo).isZero();
            assertThat(m.serviciosCausados().asistencia()).as("asistencia en %d", periodo).isZero();
            assertThat(m.serviciosCausados().seguro()).as("seguro en %d", periodo).isPositive();
            assertThat(m.cobranzaCausada()).as("cobranza en %d", periodo).isZero();
            vencidoAnterior = m.capitalVencido();
        }
        // Cuatro semanas sin pago: el vencido es la suma de su capital contractual.
        assertThat(delPeriodo(periodoSiniestro).capitalVencido()).isPositive();
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
        assertThat(m.cxcFondoGarantias()).as("AA3 CxC FCC").isCloseTo(numero(excel, "CxC FCC"), dinero());
        assertThat(m.cxpDeudor()).as("AA4 CxP deudor").isCloseTo(numero(excel, "CxP deudor"), dinero());
        assertThat(m.saldoFinal()).as("AA5 Saldo capital posterior").isCloseTo(numero(excel, "Saldo capital final"), dinero());
        // Valores calculados del Excel (data_only), por si cambia la hoja sin regenerar el CSV.
        assertThat(m.deudaAExtinguir()).isCloseTo(new BigDecimal("5130190.03154863"), dinero());
        assertThat(m.cxcFondoGarantias()).isCloseTo(new BigDecimal("2130190.0315486304"), dinero());
    }

    /** Validaciones 10 a 13: la variable de la hoja Caso3 en el periodo y mes indicados, con su tolerancia. */
    @Test
    void validaciones10a13() {
        HojaExcel validaciones = HojaExcel.cargar("validaciones");
        HojaExcel hoja = HojaExcel.cargar(HOJA);
        List<String> revisadas = new ArrayList<>();
        for (Map<String, String> v : validaciones.filas()) {
            if (!v.get("#").matches("\\d+")) {
                break; // fin de la tabla principal: después vienen los controles, con su propia numeración
            }
            int numero = Integer.parseInt(v.get("#"));
            if (numero < 10 || numero > 13) {
                continue;
            }
            assertThat(v).containsEntry("Caso", "Caso 3");
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
                case "CxC FCC" -> m.cxcFondoGarantias();
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
     * Panel "Movimiento contable clave del evento" (AJ9:AM18): débitos a inventario y CxC FCC; un crédito
     * por cada concepto extinguido con la moto según la prelación (AL29:AN36).
     */
    @Test
    void asientoDeLaDacionReproduceElPanelContable() {
        Map<String, String> excel = HojaExcel.cargar(HOJA).filaDelPeriodo(periodoSiniestro);
        List<Asiento> asientos = Contabilizador.estandar().contabilizar(motor).stream()
                .filter(a -> a.periodo() == periodoSiniestro)
                .toList();
        Asiento asiento = new DacionEnPago().contabilizar(delPeriodo(periodoSiniestro)).orElseThrow();
        assertThat(asientos).contains(asiento);

        assertThat(debito(asiento, Cuenta.INVENTARIO_MOTOS)).as("AK10").isCloseTo(numero(excel, "Inventario"), dinero());
        assertThat(debito(asiento, Cuenta.CXC_FONDO_GARANTIAS)).as("AK11").isCloseTo(numero(excel, "CxC FCC"), dinero());
        assertThat(credito(asiento, Cuenta.CARTERA_VIGENTE)).as("AL12").isCloseTo(new BigDecimal("4878969.521936266"), dinero());
        assertThat(credito(asiento, Cuenta.CXC_INTERES_CORRIENTE)).as("AL13")
                .isCloseTo(new BigDecimal("234575.12163955838"), dinero());
        assertThat(credito(asiento, Cuenta.CXC_INTERES_MORA)).as("AL14").isCloseTo(new BigDecimal("3019.84951126784"), dinero());
        assertThat(credito(asiento, Cuenta.CXC_SEGUROS)).as("AL17").isCloseTo(new BigDecimal("13625.538461538463"), dinero());
        // Control AM18 = AK18 − AL18 = 0. Fondo y asistencia del cliente valen 0 y no generan línea.
        assertThat(asiento.totalDebitos()).isCloseTo(new BigDecimal("5130190.03154863"), dinero());
        assertThat(asiento.cuadra()).isTrue();
        assertThat(asiento.lineas()).extracting(Asiento.Linea::cuenta).containsExactly(Cuenta.INVENTARIO_MOTOS,
                Cuenta.CXC_FONDO_GARANTIAS, Cuenta.CARTERA_VIGENTE, Cuenta.CXC_INTERES_CORRIENTE, Cuenta.CXC_INTERES_MORA,
                Cuenta.CXC_SEGUROS);
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
