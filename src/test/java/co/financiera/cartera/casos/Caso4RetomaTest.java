package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.reglas.Retoma;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Caso 4 · Retoma con excedente: el cliente paga hasta la cuota 13, no paga de la 14 a la 16 y en
 * la 17 se retoma la moto por un avalúo (6.800.000) mayor que la deuda; el excedente queda como
 * CxP a favor del deudor. Hoja Caso4_Retoma.
 */
class Caso4RetomaTest {

    /** Inputs C48 = ROUNDDOWN((4 − 1) × 52 / 12). */
    private static final int ULTIMO_PERIODO_PAGADO = 13;
    /** Inputs C49 = ROUNDDOWN(4 × 52 / 12). */
    private static final int PERIODO_RETOMA = 17;
    /** Inputs C47. */
    private static final BigDecimal AVALUO_MOTO = Calc.bd("6800000");

    private static final Offset<BigDecimal> UN_PESO = Offset.offset(BigDecimal.ONE);

    private final HojaExcel hoja = HojaExcel.cargar("Caso4_Retoma");

    private final List<Movimiento> motor = new MotorCartera().ejecutar(
            new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel())
                    // D8 / P7: el Excel no causa mora, vencido ni servicios mientras el cliente no paga.
                    .entre(ULTIMO_PERIODO_PAGADO + 1, PERIODO_RETOMA, new SinPago(false))
                    .en(PERIODO_RETOMA, new CierrePorRecuperacion(TipoRecuperacion.RETOMA, AVALUO_MOTO)));

    @Test
    void reproduceElCronogramaDelExcelCeldaPorCelda() {
        ComparadorExcel comparador = new ComparadorExcel()
                .dinero("Mes", m -> Calc.bd(m.mes()))
                .dinero("Saldo inicial", Movimiento::saldoInicial)
                .dinero("Interés corriente (s/ capital vigente)", Movimiento::interesCorriente)
                .dinero("Pago financiero", m -> Calc.restar(m.pagoRecibido(), m.pagoServicios()))
                .dinero("Interés pagado", Movimiento::pagoInteresCorriente)
                .dinero("Capital pagado", Movimiento::pagoCapital)
                .dinero("CxC interés corriente", Movimiento::cxcInteresCorriente)
                .dinero("Saldo capital final", Movimiento::saldoFinal)
                .dinero("Cuotas vencidas", m -> Calc.bd(m.cuotasVencidas()))
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

        assertThat(comparador.diferencias(hoja, motor)).isEmpty();
    }

    @Test
    void eventoYEstadoCoincidenConElExcel() {
        for (Map<String, String> fila : hoja.filas()) {
            int periodo = Integer.parseInt(fila.get("Periodo"));
            Movimiento m = motor.get(periodo);
            assertThat(m.periodo()).isEqualTo(periodo);
            if (periodo > 0) {
                assertThat(m.evento()).as("evento del periodo %d", periodo).isEqualTo(fila.get("Evento"));
            }
            assertThat(estadoExcel(m.estado())).as("estado del periodo %d", periodo).isEqualTo(fila.get("Estado"));
        }
        assertThat(motor.getLast().periodo()).isEqualTo(PERIODO_RETOMA);
    }

    /** Panel lateral "Indicadores del caso" (Z2:AA8), valores calculados por Excel. */
    @Test
    void reproduceLosIndicadoresDelPanelLateral() {
        Movimiento retoma = motor.get(PERIODO_RETOMA);
        assertThat(motor.get(ULTIMO_PERIODO_PAGADO).evento()).isEqualTo("Pago normal");
        assertThat(motor.get(ULTIMO_PERIODO_PAGADO + 1).evento()).isEqualTo("Mora - sin pago");
        assertThat(retoma.periodo()).isEqualTo(PERIODO_RETOMA);
        assertThat(retoma.cuotasVencidas()).isEqualTo(4);
        assertThat(retoma.deudaAExtinguir()).isCloseTo(new BigDecimal("5786981.423229206"), UN_PESO);
        assertThat(retoma.inventario()).isCloseTo(new BigDecimal("6800000"), UN_PESO);
        assertThat(retoma.cxpDeudor()).isCloseTo(new BigDecimal("1013018.5767707936"), UN_PESO);
        assertThat(retoma.saldoFinal()).isCloseTo(BigDecimal.ZERO, UN_PESO);
        assertThat(retoma.cxcFondoGarantias()).isZero();
        assertThat(retoma.estado()).isEqualTo(EstadoCredito.CERRADO_POR_EVENTO);
    }

    /** Hoja Validaciones, filas 14 a 16: se comparan contra la hoja del caso en el periodo indicado. */
    @Test
    void cumpleLasValidaciones14a16() {
        Map<String, Function<Movimiento, BigDecimal>> variables = Map.of(
                "Deuda a extinguir", Movimiento::deudaAExtinguir,
                "Inventario", Movimiento::inventario,
                "CxP deudor", Movimiento::cxpDeudor);
        List<Map<String, String>> validaciones = HojaExcel.cargar("validaciones").filas().stream()
                .filter(v -> v.get("Caso").equals("Caso 4"))
                .toList();

        assertThat(validaciones).extracting(v -> v.get("#")).containsExactly("14", "15", "16");
        for (Map<String, String> v : validaciones) {
            int periodo = Integer.parseInt(v.get("Periodo"));
            String variable = v.get("Variable");
            BigDecimal tolerancia = new BigDecimal(v.get("Tolerancia"));
            BigDecimal esperado = HojaExcel.numero(hoja.filaDelPeriodo(periodo), variable).orElseThrow();
            Movimiento m = motor.get(periodo);

            assertThat(m.mes()).as("validación %s: mes", v.get("#")).isEqualTo(Integer.parseInt(v.get("Mes")));
            assertThat(variables.get(variable).apply(m)).as("validación %s: %s", v.get("#"), variable)
                    .isCloseTo(esperado, Offset.offset(tolerancia));
        }
    }

    /** Panel "Movimiento contable clave del evento" (AJ13:AM22): fondo y asistencia del cliente valen 0 y no generan línea. */
    @Test
    void laReglaContableReproduceElPanelDelExcel() {
        Asiento asiento = new Retoma().contabilizar(motor.get(PERIODO_RETOMA)).orElseThrow();

        assertThat(asiento.periodo()).isEqualTo(PERIODO_RETOMA);
        assertThat(asiento.lineas()).hasSize(6);
        assertThat(linea(asiento, Cuenta.INVENTARIO_MOTOS).debito()).isCloseTo(new BigDecimal("6800000"), UN_PESO);
        // AL15:AL21: un crédito por concepto extinguido según la prelación, y el excedente al deudor.
        assertThat(linea(asiento, Cuenta.CARTERA_VIGENTE).credito()).isCloseTo(new BigDecimal("5505261.868413894"), UN_PESO);
        assertThat(linea(asiento, Cuenta.CXC_INTERES_CORRIENTE).credito())
                .isCloseTo(new BigDecimal("265635.6644724451"), UN_PESO);
        assertThat(linea(asiento, Cuenta.CXC_INTERES_MORA).credito()).isCloseTo(new BigDecimal("2458.351881328221"), UN_PESO);
        assertThat(linea(asiento, Cuenta.CXC_SEGUROS).credito()).isCloseTo(new BigDecimal("13625.538461538463"), UN_PESO);
        assertThat(linea(asiento, Cuenta.CXP_DEUDOR).credito()).isCloseTo(new BigDecimal("1013018.5767707936"), UN_PESO);
        // Control AC18: débitos − créditos = 0.
        assertThat(asiento.totalDebitos()).isCloseTo(new BigDecimal("6800000"), UN_PESO);
        assertThat(asiento.totalCreditos()).isCloseTo(new BigDecimal("6800000"), UN_PESO);
        assertThat(asiento.cuadra()).isTrue();
    }

    @Test
    void laReglaDeRetomaSoloAplicaAlCierreConExcedente() {
        Retoma regla = new Retoma();
        assertThat(motor).filteredOn(m -> regla.contabilizar(m).isPresent())
                .extracting(Movimiento::periodo)
                .containsExactly(PERIODO_RETOMA);
    }

    @Test
    void elContabilizadorEstandarIncluyeLaRetoma() {
        List<Asiento> asientos = Contabilizador.estandar().contabilizar(motor.get(PERIODO_RETOMA));
        assertThat(asientos).anySatisfy(a -> assertThat(a.lineas())
                .anySatisfy(l -> assertThat(l.cuenta()).isEqualTo(Cuenta.CXP_DEUDOR)));
    }

    private static Asiento.Linea linea(Asiento asiento, Cuenta cuenta) {
        Optional<Asiento.Linea> l = asiento.lineas().stream().filter(x -> x.cuenta() == cuenta).findFirst();
        return l.orElseThrow(() -> new AssertionError("Falta la cuenta " + cuenta));
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
}
