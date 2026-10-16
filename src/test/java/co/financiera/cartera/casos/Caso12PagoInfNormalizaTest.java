package co.financiera.cartera.casos;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.excel.ComparadorExcel;
import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Caso 12 · Pago inferior con normalización: en el periodo 43 paga el 40 % de la cuota y queda en mora;
 * en el periodo 60 (mes 14) paga la cuota más todo lo atrasado y vuelve a estar al día hasta la cuota
 * 104. Hoja Caso12_PagoInfNormaliza.
 */
class Caso12PagoInfNormalizaTest {

    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    private static final int PERIODO_INFERIOR = 43;
    private static final int PERIODO_NORMALIZACION = 60;
    private final List<Movimiento> motor = new MotorCartera().ejecutar(EscenariosExcel.caso12(ParametrosProducto.replicaExcel()));

    @Test
    void reproduceElCronogramaDelExcelCeldaPorCelda() {
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

        assertThat(comparador.diferencias(HojaExcel.cargar("Caso12_PagoInfNormaliza"), motor)).isEmpty();
    }

    /** Panel "Indicadores del caso" (AH2:AI9). */
    @Test
    void reproduceLosIndicadoresDelPanel() {
        Movimiento inferior = motor.get(PERIODO_INFERIOR);
        assertThat(inferior.pagoRecibido()).isCloseTo(new BigDecimal("65376.17859587236"), within(UN_PESO)); // AI3
        assertThat(inferior.capitalVencido()).isCloseTo(new BigDecimal("38436.48020150085"), within(UN_PESO)); // AI4
        assertThat(inferior.cxcInteresCorriente()).isCloseTo(BigDecimal.ZERO, within(UN_PESO)); // AI5
        assertThat(motor.get(PERIODO_INFERIOR + 1).interesMora())
                .isCloseTo(new BigDecimal("467.9427881008084"), within(UN_PESO)); // AI6
        assertThat(motor.getLast().periodo()).isEqualTo(104); // AI7
        assertThat(inferior.pagoServicios()).isCloseTo(new BigDecimal("3406.3846153846157"), within(UN_PESO)); // AI8
    }

    @Test
    void laNormalizacionDejaElCreditoAlDiaHastaElFinal() {
        Movimiento normalizacion = motor.get(PERIODO_NORMALIZACION);
        assertThat(normalizacion.evento()).isEqualTo("Normalización - paga mes + atrasado");
        assertThat(normalizacion.capitalVencido()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
        assertThat(normalizacion.cxcMora()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
        assertThat(normalizacion.cxcServicios()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
        assertThat(motor.subList(PERIODO_NORMALIZACION, motor.size()))
                .allSatisfy(m -> assertThat(m.estado()).isIn(EstadoCredito.VIGENTE_AL_DIA, EstadoCredito.CANCELADO));
        assertThat(motor.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
        assertThat(bd(motor.getLast().periodo())).isEqualByComparingTo("104");
        assertThat(Contabilizador.estandar().contabilizar(motor)).allSatisfy(a -> assertThat(a.cuadra()).isTrue());
    }
}
