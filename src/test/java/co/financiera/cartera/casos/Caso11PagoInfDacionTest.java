package co.financiera.cartera.casos;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.reglas.DacionEnPago;
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
 * Caso 11 · Pago inferior + espera en mora + dación: en el periodo 43 paga el 40 % de la cuota, no paga
 * los tres periodos siguientes (solo se causan mora y seguro) y en el 47 entrega la moto por
 * 3.000.000. Lo pagado y el valor de la moto se aplican por prelación; el faltante es CxC al fondo de
 * garantías. Hoja Caso11_PagoInfDacion.
 */
class Caso11PagoInfDacionTest {

    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    private static final int PERIODO_INFERIOR = 43;
    private static final int PERIODO_DACION = 47;
    private final List<Movimiento> motor = new MotorCartera().ejecutar(EscenariosExcel.caso11(ParametrosProducto.replicaExcel()));

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

        assertThat(comparador.diferencias(HojaExcel.cargar("Caso11_PagoInfDacion"), motor)).isEmpty();
    }

    /** Panel "Indicadores del caso" (AJ2:AK7). */
    @Test
    void reproduceLosIndicadoresDelPanel() {
        Movimiento dacion = motor.get(PERIODO_DACION);
        assertThat(dacion.estado()).isEqualTo(EstadoCredito.CERRADO_POR_EVENTO);
        assertThat(dacion.inventario()).isCloseTo(bd(3_000_000), within(UN_PESO)); // AK2
        assertThat(dacion.cxcFondoGarantias()).isCloseTo(new BigDecimal("1568722.6407263055"), within(UN_PESO)); // AK3
        assertThat(dacion.cxpDeudor()).isZero(); // AK4
        assertThat(dacion.saldoFinal()).isZero(); // AK5
        assertThat(motor.get(PERIODO_INFERIOR).pagoRecibido())
                .isCloseTo(new BigDecimal("65376.17859587236"), within(UN_PESO)); // AK6
        assertThat(dacion.capitalVencido()).isCloseTo(new BigDecimal("233945.229790238"), within(UN_PESO)); // AK7
        assertThat(dacion.deudaAExtinguir()).isCloseTo(new BigDecimal("4568722.640726306"), within(UN_PESO)); // AL36
    }

    /** Panel "Movimiento contable clave del evento" (AJ9:AM18). */
    @Test
    void asientoDeLaDacionReproduceElPanelContable() {
        Asiento asiento = new DacionEnPago().contabilizar(motor.get(PERIODO_DACION)).orElseThrow();
        assertThat(Contabilizador.estandar().contabilizar(motor)).contains(asiento);
        assertThat(asiento.lineas()).extracting(Asiento.Linea::cuenta).containsExactly(Cuenta.INVENTARIO_MOTOS,
                Cuenta.CXC_FONDO_GARANTIAS, Cuenta.CARTERA_VIGENTE, Cuenta.CXC_INTERES_CORRIENTE, Cuenta.CXC_INTERES_MORA,
                Cuenta.CXC_SEGUROS);
        assertThat(credito(asiento, Cuenta.CARTERA_VIGENTE)).isCloseTo(new BigDecimal("4343574.670354831"), within(UN_PESO));
        assertThat(credito(asiento, Cuenta.CXC_INTERES_CORRIENTE))
                .isCloseTo(new BigDecimal("206116.3471392173"), within(UN_PESO));
        assertThat(credito(asiento, Cuenta.CXC_INTERES_MORA)).isCloseTo(new BigDecimal("5406.084770718442"), within(UN_PESO));
        assertThat(credito(asiento, Cuenta.CXC_SEGUROS)).isCloseTo(new BigDecimal("13625.538461538463"), within(UN_PESO));
        assertThat(asiento.totalDebitos()).isCloseTo(new BigDecimal("4568722.640726306"), within(UN_PESO));
        assertThat(asiento.cuadra()).isTrue();
    }

    private static BigDecimal credito(Asiento a, Cuenta c) {
        return a.lineas().stream().filter(l -> l.cuenta() == c).map(Asiento.Linea::credito)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
