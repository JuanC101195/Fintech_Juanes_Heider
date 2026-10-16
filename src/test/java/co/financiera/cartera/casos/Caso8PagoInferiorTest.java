package co.financiera.cartera.casos;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.excel.ComparadorExcel;
import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Caso 8 · Pago inferior: en el mes 10 (periodo 43) el cliente paga el 40 % de la cuota total;
 * después vuelve a pagar la cuota pactada y los vencidos se recuperan por prelación. Hoja
 * Caso8_PagoInferior. Reglas en docs/reglas/caso-8.md.
 */
class Caso8PagoInferiorTest {

    /** Inputs!C60 = ROUNDDOWN(mes 10 × 52/12) = 43. */
    static final int PERIODO_EVENTO = 43;
    /** Inputs!C59. */
    static final BigDecimal FRACCION_PAGADA = bd("0.4");
    /** Inputs!C61. */
    static final int PLAZO_LIMITE = 100;

    static final Offset<BigDecimal> UN_PESO = Offset.offset(ComparadorExcel.TOLERANCIA_DINERO);

    static Escenario escenario() {
        return new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel().conPlazoLimite(PLAZO_LIMITE))
                .en(PERIODO_EVENTO, new PagoParcial(FRACCION_PAGADA, 0));
    }

    private final List<Movimiento> motor = new MotorCartera().ejecutar(escenario());
    private final HojaExcel hoja = HojaExcel.cargar("Caso8_PagoInferior");

    private Movimiento periodo(int periodo) {
        return motor.stream().filter(m -> m.periodo() == periodo).findFirst().orElseThrow();
    }

    private static BigDecimal excel(HojaExcel hoja, int periodo, String columna) {
        return HojaExcel.numero(hoja.filaDelPeriodo(periodo), columna).orElseThrow();
    }

    @Test
    void reproduceTodasLasColumnasNumericasDelExcelCeldaPorCelda() {
        ComparadorExcel comparador = new ComparadorExcel()
                .dinero("Mes", m -> bd(m.mes()))
                .dinero("Saldo inicial", Movimiento::saldoInicial)
                .dinero("Interés corriente (s/ capital vigente)", Movimiento::interesCorriente)
                .dinero("Interés mora", Movimiento::interesMora)
                .dinero("Capital contractual", Movimiento::capitalContractual)
                .dinero("Pago recibido (cuota total)", Movimiento::pagoRecibido)
                .dinero("Pago mora", Movimiento::pagoMora)
                .dinero("Pago interés corriente", Movimiento::pagoInteresCorriente)
                .dinero("Pago capital", Movimiento::pagoCapital)
                .dinero("CxC interés mora", Movimiento::cxcMora)
                .dinero("CxC interés corriente", Movimiento::cxcInteresCorriente)
                .dinero("Capital vencido", Movimiento::capitalVencido)
                .dinero("Capital vigente", Movimiento::capitalVigente)
                .dinero("Saldo capital final", Movimiento::saldoFinal)
                .dinero("FCC+IVA", m -> m.serviciosCausados().fondoGarantias())
                .dinero("Asistencia+IVA", m -> m.serviciosCausados().asistencia())
                .dinero("Seguro vida", m -> m.serviciosCausados().seguro())
                .dinero("Servicios causados", m -> m.serviciosCausados().total())
                .dinero("Pago servicios", Movimiento::pagoServicios)
                .dinero("CxC servicios", Movimiento::cxcServicios)
                .dinero("Pago FCC", m -> m.pagoTerceros().fondoGarantias())
                .dinero("Pago asistencia", m -> m.pagoTerceros().asistencia())
                .dinero("Pago aseguradora", m -> m.pagoTerceros().seguro())
                .dinero("Pago FCC+IVA (4)", m -> m.pagosServicios().fondoGarantias())
                .dinero("Pago asistencia+IVA (5)", m -> m.pagosServicios().asistencia())
                .dinero("Pago seguro (6)", m -> m.pagosServicios().seguro())
                .dinero("CxC FCC+IVA", m -> m.cxcServiciosPorConcepto().fondoGarantias())
                .dinero("CxC asistencia+IVA", m -> m.cxcServiciosPorConcepto().asistencia())
                .dinero("CxC seguro", m -> m.cxcServiciosPorConcepto().seguro());

        assertThat(comparador.diferencias(hoja, motor)).isEmpty();
    }

    @Test
    void elEstadoCoincideConElExcelEnCadaPeriodo() {
        Map<String, EstadoCredito> estados = Map.of(
                "Desembolsado", EstadoCredito.DESEMBOLSADO,
                "Vigente al día", EstadoCredito.VIGENTE_AL_DIA,
                "En mora", EstadoCredito.EN_MORA,
                "Cancelado", EstadoCredito.CANCELADO);
        List<String> errores = new ArrayList<>();
        for (Map<String, String> fila : hoja.filas()) {
            int p = Integer.parseInt(fila.get("Periodo"));
            EstadoCredito esperado = estados.get(fila.get("Estado"));
            if (periodo(p).estado() != esperado) {
                errores.add("periodo " + p + ": Excel " + fila.get("Estado") + ", motor " + periodo(p).estado());
            }
        }
        assertThat(errores).isEmpty();
    }

    @Test
    void elPeriodo43QuedaEnMoraConElCapitalContractualVencido() {
        Movimiento m = periodo(PERIODO_EVENTO);
        // 40 % de la cuota total pactada (Inputs B36); esa semana solo se causa el seguro (Inputs ⑥).
        assertThat(m.pagoRecibido()).isCloseTo(bd("65376.17859587236"), UN_PESO);
        assertThat(m.pagoInteresCorriente()).isCloseTo(bd("52989.932797465175"), UN_PESO);
        assertThat(m.pagoServicios()).isCloseTo(bd("3406.3846153846157"), UN_PESO);
        assertThat(m.pagoCapital()).isCloseTo(bd("8979.861183022573"), UN_PESO);
        assertThat(m.cxcServicios()).isCloseTo(BigDecimal.ZERO, UN_PESO);
        assertThat(m.capitalVencido()).isCloseTo(bd("38436.48020150085"), UN_PESO);
        assertThat(m.estado()).isEqualTo(EstadoCredito.EN_MORA);
    }

    @Test
    void elPeriodo44CobraMoraYRecuperaVencidosPorPrelacion() {
        Movimiento m = periodo(PERIODO_EVENTO + 1);
        assertThat(m.interesMora()).isCloseTo(bd("467.9427881008084"), UN_PESO);
        assertThat(m.pagoMora()).isCloseTo(bd("467.9427881008084"), UN_PESO);
        assertThat(m.pagoInteresCorriente()).isCloseTo(bd("52412.66518938314"), UN_PESO);
        assertThat(m.pagoServicios()).isCloseTo(bd("63034.172307692315"), UN_PESO);
        assertThat(m.pagoCapital()).isCloseTo(bd("47525.66620450465"), UN_PESO);
        assertThat(m.capitalVencido()).isCloseTo(bd("38904.422989601684"), UN_PESO);
    }

    /** Panel "INDICADORES DEL CASO" (AH2:AI9). */
    @Test
    void reproduceLosIndicadoresDelPanel() {
        int ultimo = motor.getLast().periodo();
        Movimiento evento = periodo(PERIODO_EVENTO);
        assertThat(evento.pagoRecibido()).isCloseTo(excel(hoja, PERIODO_EVENTO, "Pago recibido (cuota total)"), UN_PESO); // AC3
        assertThat(evento.capitalVencido()).isCloseTo(excel(hoja, PERIODO_EVENTO, "Capital vencido"), UN_PESO); // AC4
        assertThat(evento.cxcInteresCorriente()).isCloseTo(BigDecimal.ZERO, UN_PESO); // AC5
        assertThat(periodo(PERIODO_EVENTO + 1).interesMora()).isCloseTo(bd("467.9427881008084"), UN_PESO); // AI6
        assertThat(ultimo).isEqualTo(100); // AI7
        assertThat(evento.pagoServicios()).isCloseTo(bd("3406.3846153846157"), UN_PESO); // AI8
        assertThat(evento.cxcServicios()).isCloseTo(BigDecimal.ZERO, UN_PESO); // AI9
    }

    // ---- Validaciones 25 a 28 de la hoja Validaciones ----

    @Test
    void validacion25PagoRecibidoEnElEvento() {
        assertThat(periodo(43).pagoRecibido()).isCloseTo(excel(hoja, 43, "Pago recibido (cuota total)"), UN_PESO);
        assertThat(periodo(43).mes()).isEqualTo(10);
    }

    @Test
    void validacion26CapitalVencidoEnElEvento() {
        assertThat(periodo(43).capitalVencido()).isCloseTo(excel(hoja, 43, "Capital vencido"), UN_PESO);
    }

    @Test
    void validacion27InteresMoraEnElPeriodoSiguiente() {
        assertThat(periodo(44).interesMora()).isCloseTo(excel(hoja, 44, "Interés mora"), UN_PESO);
    }

    /**
     * "Periodos adicionales" = último periodo con pago − cuotas pactadas. En el Excel del 11 de octubre
     * el plazo límite del caso sigue en 100 semanas (Inputs C65) pero las cuotas subieron a 104: la
     * tabla se corta en la 100 con el crédito en mora y no hay periodos adicionales (pregunta para Diego).
     */
    @Test
    void validacion28PeriodosAdicionalesExacto() {
        int pactadas = Calendario.numeroCuotas(escenario().parametros());
        int ultimoExcel = hoja.filas().stream().mapToInt(f -> Integer.parseInt(f.get("Periodo"))).max().orElseThrow();
        long adicionales = motor.stream().filter(m -> m.periodo() > pactadas).count();
        assertThat(pactadas).isEqualTo(104);
        assertThat(ultimoExcel).isEqualTo(PLAZO_LIMITE);
        assertThat(motor.getLast().periodo()).isEqualTo(ultimoExcel);
        assertThat(adicionales).isZero();
        assertThat(motor.getLast().estado()).isEqualTo(EstadoCredito.EN_MORA);
    }
}
