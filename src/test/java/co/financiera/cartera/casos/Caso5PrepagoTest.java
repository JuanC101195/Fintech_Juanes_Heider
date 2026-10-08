package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.reglas.AbonoExtraordinario;
import co.financiera.cartera.excel.ComparadorExcel;
import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Caso 5 · Pago anticipado total: en la semana del mes 10 (cuota 43) el cliente paga la cuota
 * ordinaria y, después, todo el saldo de capital. Hoja Caso5_Prepago. Reglas en docs/reglas/caso-5.md.
 */
class Caso5PrepagoTest {

    private static final ParametrosProducto PARAMETROS = ParametrosProducto.replicaExcel();
    /** Inputs!C50: mes del prepago total. */
    private static final int MES_PREPAGO = 10;
    /** Inputs!C51 = ROUNDDOWN(C50 × B14) (R-5.1). */
    private static final int PERIODO_PREPAGO = Calendario.periodoDelMes(MES_PREPAGO, PARAMETROS);

    // Indicadores del caso (Caso5_Prepago!U2:U8), valores calculados por Excel.
    private static final BigDecimal SALDO_ANTES_CUOTA = new BigDecimal("3766846.5117740124"); // U3
    private static final BigDecimal CUOTA_ORDINARIA = new BigDecimal("111014.82187676849"); // U4
    private static final BigDecimal PREPAGO_CAPITAL = new BigDecimal("3701573.622003108"); // U5
    private static final BigDecimal PAGO_FINANCIERO_EVENTO = new BigDecimal("3812588.4438798763"); // U6
    private static final BigDecimal INTERESES_EVITADOS = new BigDecimal("1072063.7186979419"); // U7
    private static final BigDecimal SALDO_POSTERIOR = BigDecimal.ZERO; // U8

    private static final BigDecimal TOLERANCIA = ComparadorExcel.TOLERANCIA_DINERO;

    private final List<Movimiento> motor = new MotorCartera().ejecutar(escenario(PERIODO_PREPAGO));

    private static Escenario escenario(int periodoPrepago) {
        return new Escenario(Operacion.ejemploExcel(), PARAMETROS).en(periodoPrepago, new Instruccion.PrepagoTotal());
    }

    @Test
    void elPeriodoDelPrepagoEsLaCuota43() {
        assertThat(PERIODO_PREPAGO).isEqualTo(43);
    }

    @Test
    void reproduceElCronogramaDelExcelCeldaPorCelda() {
        ComparadorExcel comparador = new ComparadorExcel()
                .dinero("Saldo inicial", Movimiento::saldoInicial)
                .dinero("Interés", Movimiento::interesCorriente)
                .dinero("Cuota ordinaria", Movimiento::cuotaFinanciera)
                .dinero("Capital ordinario", Movimiento::pagoCapital)
                .dinero("Prepago capital", Movimiento::prepagoCapital)
                .dinero("Saldo final", Movimiento::saldoFinal)
                .dinero("FCC+IVA", m -> m.serviciosCausados().fcc())
                .dinero("Asistencia+IVA", m -> m.serviciosCausados().asistencia())
                .dinero("Seguro vida", m -> m.serviciosCausados().seguro())
                .dinero("Servicios", m -> m.serviciosCausados().total())
                .dinero("Pago total cliente", Movimiento::pagoTotalCliente)
                .dinero("Pago FCC", m -> m.pagoTerceros().fcc())
                .dinero("Pago asistencia", m -> m.pagoTerceros().asistencia())
                .dinero("Pago aseguradora", m -> m.pagoTerceros().seguro());

        assertThat(comparador.diferencias(HojaExcel.cargar("Caso5_Prepago"), motor)).isEmpty();
    }

    @Test
    void elEstadoYElEventoCoincidenConElExcel() {
        HojaExcel hoja = HojaExcel.cargar("Caso5_Prepago");
        for (Movimiento m : motor) {
            Map<String, String> fila = hoja.filaDelPeriodo(m.periodo());
            assertThat(estadoExcel(m.estado())).as("estado periodo %d", m.periodo()).isEqualTo(fila.get("Estado"));
            assertThat(m.evento()).as("evento periodo %d", m.periodo()).isEqualTo(fila.get("Evento"));
        }
    }

    @Test
    void elCreditoTerminaEnLaSemanaDelPrepagoSinCuotasPosteriores() {
        Movimiento ultimo = motor.getLast();
        assertThat(ultimo.periodo()).isEqualTo(PERIODO_PREPAGO);
        assertThat(ultimo.estado()).isEqualTo(EstadoCredito.CANCELADO);
        assertThat(ultimo.saldoFinal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(motor).hasSize(PERIODO_PREPAGO + 1); // desembolso + 43 semanas
    }

    @Test
    void indicadoresDelPanel() {
        Movimiento evento = motor.get(PERIODO_PREPAGO);
        assertThat(evento.saldoInicial()).isCloseTo(SALDO_ANTES_CUOTA, within(TOLERANCIA));
        assertThat(evento.cuotaFinanciera()).isCloseTo(CUOTA_ORDINARIA, within(TOLERANCIA));
        assertThat(evento.prepagoCapital()).isCloseTo(PREPAGO_CAPITAL, within(TOLERANCIA));
        // U6 = cuota ordinaria + prepago (sin servicios).
        assertThat(Calc.sumar(evento.cuotaFinanciera(), evento.prepagoCapital()))
                .isCloseTo(PAGO_FINANCIERO_EVENTO, within(TOLERANCIA));
        assertThat(evento.saldoFinal()).isCloseTo(SALDO_POSTERIOR, within(TOLERANCIA));
        // Cuota ordinaria del evento: interés 45.741,93 y capital 65.272,89.
        assertThat(evento.interesCorriente()).isCloseTo(new BigDecimal("45741.93210586396"), within(TOLERANCIA));
        assertThat(evento.pagoCapital()).isCloseTo(new BigDecimal("65272.88977090453"), within(TOLERANCIA));
        // Pago total del cliente = cuota + prepago + servicios (O46).
        assertThat(evento.pagoTotalCliente()).isCloseTo(new BigDecimal("3875622.6161875688"), within(TOLERANCIA));
    }

    @Test
    void interesesFuturosEvitadosSonLosDelCaso1DespuesDelPrepago() {
        // U7 = SUMIF(Caso1_Normal!A:A, ">"&C51, Caso1_Normal!F:F)
        List<Movimiento> normal = new MotorCartera().ejecutar(new Escenario(Operacion.ejemploExcel(), PARAMETROS));
        BigDecimal evitados = normal.stream()
                .filter(m -> m.periodo() > PERIODO_PREPAGO)
                .map(Movimiento::interesCorriente)
                .reduce(Calc.CERO, Calc::sumar);
        assertThat(evitados).isCloseTo(INTERESES_EVITADOS, within(TOLERANCIA));
    }

    @Test
    void validaciones17y18() {
        HojaExcel validaciones = HojaExcel.cargar("validaciones");
        Movimiento evento = motor.get(PERIODO_PREPAGO);
        Map<Integer, BigDecimal> resultadoSoftware = Map.of(17, evento.prepagoCapital(), 18, evento.saldoFinal());
        Map<Integer, BigDecimal> esperadoExcel = Map.of(17, PREPAGO_CAPITAL, 18, SALDO_POSTERIOR);
        for (int n : List.of(17, 18)) {
            Map<String, String> fila = validaciones.filas().stream()
                    .filter(f -> f.get("#").equals(String.valueOf(n)))
                    .findFirst()
                    .orElseThrow();
            assertThat(fila.get("Caso")).isEqualTo("Caso 5");
            assertThat(Integer.parseInt(fila.get("Periodo"))).isEqualTo(PERIODO_PREPAGO);
            assertThat(Integer.parseInt(fila.get("Mes"))).isEqualTo(evento.mes());
            BigDecimal tolerancia = HojaExcel.numero(fila, "Tolerancia").orElseThrow();
            assertThat(resultadoSoftware.get(n)).as("validación %d", n)
                    .isCloseTo(esperadoExcel.get(n), within(tolerancia));
        }
    }

    @Test
    void asientoDelPrepagoIgualAlPanelContable() {
        // Caso5_Prepago!T13:W15: débito Caja / crédito Cartera de créditos por el prepago.
        Asiento asiento = new AbonoExtraordinario().contabilizar(motor.get(PERIODO_PREPAGO)).orElseThrow();
        assertThat(asiento.cuadra()).isTrue();
        assertThat(asiento.lineas()).hasSize(2);
        assertThat(debito(asiento, Cuenta.CAJA)).isCloseTo(PREPAGO_CAPITAL, within(TOLERANCIA));
        assertThat(credito(asiento, Cuenta.CARTERA_VIGENTE)).isCloseTo(PREPAGO_CAPITAL, within(TOLERANCIA));
        assertThat(asiento.lineas()).extracting(l -> l.cuenta().nombreExcel())
                .containsExactly("Caja", "Cartera de créditos");
    }

    @Test
    void laReglaNoGeneraAsientoEnSemanasSinAbonoNiPrepago() {
        AbonoExtraordinario regla = new AbonoExtraordinario();
        assertThat(motor.subList(0, PERIODO_PREPAGO)).allSatisfy(m -> assertThat(regla.contabilizar(m)).isEmpty());
    }

    @Test
    void elContabilizadorEstandarIncluyeElAsientoDelPrepago() {
        List<Asiento> asientos = Contabilizador.estandar().contabilizar(motor.get(PERIODO_PREPAGO));
        assertThat(asientos).anySatisfy(a -> {
            assertThat(debito(a, Cuenta.CAJA)).isCloseTo(PREPAGO_CAPITAL, within(TOLERANCIA));
            assertThat(a.lineas()).anyMatch(l -> l.explicacion().contains("prepago"));
        });
    }

    @Test
    void asientoDelAbonoExtraDeLosCasos6y7() {
        // Caso6_MenorPlazo!U11:X13 y Caso7_MenorCuota!U14:X16: débito Caja / crédito Cartera 2.000.000.
        BigDecimal abono = Calc.bd(2_000_000);
        for (ModalidadAbono modalidad : ModalidadAbono.values()) {
            List<Movimiento> corrida = new MotorCartera().ejecutar(new Escenario(Operacion.ejemploExcel(), PARAMETROS)
                    .en(PERIODO_PREPAGO, new Instruccion.AbonoExtra(abono, modalidad)));
            Asiento asiento = new AbonoExtraordinario().contabilizar(corrida.get(PERIODO_PREPAGO)).orElseThrow();
            assertThat(asiento.cuadra()).isTrue();
            assertThat(asiento.lineas()).hasSize(2);
            assertThat(debito(asiento, Cuenta.CAJA)).as(modalidad.name()).isCloseTo(abono, within(TOLERANCIA));
            assertThat(credito(asiento, Cuenta.CARTERA_VIGENTE)).as(modalidad.name()).isCloseTo(abono, within(TOLERANCIA));
            assertThat(asiento.lineas()).extracting(Asiento.Linea::explicacion)
                    .containsExactly("Abono extraordinario recibido", "Aplicación 100% a capital");
        }
    }

    @Test
    void giroATercerosEnLaSemana43EsElMesCompleto() {
        // La cuota 43 es la última del mes 10 (mes(44) = 11): el mes 10 tiene las cuotas 40 a 43 y se
        // giran las 4 semanas, igual que en el caso 1 (P46:R46 = 4 × servicio semanal).
        assertThat(Calendario.mes(PERIODO_PREPAGO, PARAMETROS)).isEqualTo(10);
        assertThat(Calendario.mes(PERIODO_PREPAGO + 1, PARAMETROS)).isEqualTo(11);
        assertThat(Calendario.mes(39, PARAMETROS)).isEqualTo(9);
        Servicios giro = motor.get(PERIODO_PREPAGO).pagoTerceros();
        assertThat(giro.fcc()).isCloseTo(new BigDecimal("194572.68923076926"), within(TOLERANCIA));
        assertThat(giro.asistencia()).isCloseTo(new BigDecimal("43938.461538461546"), within(TOLERANCIA));
        assertThat(giro.seguro()).isCloseTo(new BigDecimal("13625.538461538463"), within(TOLERANCIA));
    }

    @Test
    void prepagoAMitadDeMesGiraLasSemanasCausadasDelMesAlCancelar() {
        // Fuera del Excel: prepago en la cuota 41 (segunda semana del mes 10). El motor gira al
        // cancelar las semanas 40 y 41 ya causadas; la fórmula P del Excel haría lo mismo porque
        // la fila siguiente queda vacía (OR($B47="", ...)). No se gira nada por las semanas 42 y 43.
        List<Movimiento> corrida = new MotorCartera().ejecutar(escenario(41));
        Movimiento ultimo = corrida.getLast();
        assertThat(ultimo.periodo()).isEqualTo(41);
        assertThat(ultimo.estado()).isEqualTo(EstadoCredito.CANCELADO);
        Servicios semanal = corrida.get(1).serviciosCausados();
        assertThat(ultimo.pagoTerceros().fcc()).isCloseTo(Calc.multiplicar(semanal.fcc(), Calc.bd(2)), within(TOLERANCIA));
        assertThat(ultimo.pagoTerceros().total()).isCloseTo(Calc.multiplicar(semanal.total(), Calc.bd(2)),
                within(TOLERANCIA));
    }

    private static BigDecimal debito(Asiento a, Cuenta cuenta) {
        return a.lineas().stream().filter(l -> l.cuenta() == cuenta).map(Asiento.Linea::debito)
                .reduce(Calc.CERO, Calc::sumar);
    }

    private static BigDecimal credito(Asiento a, Cuenta cuenta) {
        return a.lineas().stream().filter(l -> l.cuenta() == cuenta).map(Asiento.Linea::credito)
                .reduce(Calc.CERO, Calc::sumar);
    }

    private static String estadoExcel(EstadoCredito estado) {
        return switch (estado) {
            case DESEMBOLSADO -> "Desembolsado";
            case VIGENTE_AL_DIA -> "Vigente al día";
            case CANCELADO -> "Cancelado";
            default -> estado.name();
        };
    }
}
