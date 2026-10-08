package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.excel.ComparadorExcel;
import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Caso 1 · Operación normal: el cliente paga todas las cuotas a tiempo. Hoja Caso1_Normal. */
class Caso1NormalTest {

    private final List<Movimiento> motor = new MotorCartera()
            .ejecutar(new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel()));

    @Test
    void reproduceElCronogramaDelExcelCeldaPorCelda() {
        ComparadorExcel comparador = new ComparadorExcel()
                .dinero("Saldo inicial", Movimiento::saldoInicial)
                .tasa("Tasa semanal", Movimiento::tasaSemanal)
                .dinero("Interés corriente", Movimiento::interesCorriente)
                .dinero("Capital pagado", Movimiento::pagoCapital)
                .dinero("FCC+IVA", m -> m.serviciosCausados().fcc())
                .dinero("Asistencia+IVA", m -> m.serviciosCausados().asistencia())
                .dinero("Seguro vida", m -> m.serviciosCausados().seguro())
                .dinero("Servicios", m -> m.serviciosCausados().total())
                .dinero("Pago total cliente", Movimiento::pagoTotalCliente)
                .dinero("Saldo final", Movimiento::saldoFinal)
                .dinero("Pago FCC", m -> m.pagoTerceros().fcc())
                .dinero("Pago asistencia", m -> m.pagoTerceros().asistencia())
                .dinero("Pago aseguradora", m -> m.pagoTerceros().seguro());

        assertThat(comparador.diferencias(HojaExcel.cargar("Caso1_Normal"), motor)).isEmpty();
    }

    @Test
    void laCuotaFinancieraDelExcelSeMantieneDuranteTodoElCredito() {
        assertThat(motor.subList(1, motor.size()))
                .allSatisfy(m -> assertThat(m.cuotaFinanciera()).isCloseTo(new BigDecimal("111014.82187676849"),
                        org.assertj.core.data.Offset.offset(BigDecimal.ONE)));
    }

    @Test
    void terminaEnLaCuota86ConSaldoCero() {
        Movimiento ultimo = motor.getLast();
        assertThat(ultimo.periodo()).isEqualTo(86);
        assertThat(ultimo.estado()).isEqualTo(EstadoCredito.CANCELADO);
        assertThat(ultimo.saldoFinal()).isLessThan(BigDecimal.ONE);
    }
}
