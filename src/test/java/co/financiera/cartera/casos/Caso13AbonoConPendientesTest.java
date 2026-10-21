package co.financiera.cartera.casos;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.DeudaExtinguida;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.AplicacionAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Caso 13 · Abono extra con pendientes (ABN-1, Diego ronda 3, 2026-10-20). No está en el Excel: lo arma
 * el equipo y Diego valida los valores (docs/reglas/caso-13.md). Réplica del Excel; la semana 40 no paga,
 * la 41 paga 5.000 con 3 días de atraso y la 42 no paga la cuota pero trae un abono extra de 1.500.000.
 * Con {@code aplicacionAbono = PRIMERO_PENDIENTES} (A4/D21) el abono paga primero los pendientes por
 * prelación y el excedente va al capital vencido y después al vigente (ABN-2).
 */
class Caso13AbonoConPendientesTest {

    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    static final int SIN_PAGO = 40;
    static final int PAGO_TARDIO = 41;
    static final int ABONO = 42;
    static final BigDecimal VALOR_ABONO = bd(1_500_000);
    static final ReglasNegocio DIEGO = ReglasNegocio.EXCEL.conAplicacionAbono(AplicacionAbono.PRIMERO_PENDIENTES);

    private final MotorCartera motor = new MotorCartera();

    static Escenario conPendientes() {
        return new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel())
                .en(SIN_PAGO, new SinPago(true))
                .en(PAGO_TARDIO, new PagoValor(bd(5_000), 3))
                .en(ABONO, new SinPago(true));
    }

    static Escenario caso13(BigDecimal abono, ModalidadAbono modalidad) {
        return conPendientes().en(ABONO, new AbonoExtra(abono, modalidad)).conReglas(DIEGO);
    }

    /** Lo pendiente al cierre de la semana del abono si el cliente no abonara. */
    private Movimiento sinAbono() {
        return motor.ejecutar(conPendientes().conReglas(DIEGO)).get(ABONO);
    }

    @Test
    void antesDelAbonoHayPendientesDeTodosLosConceptos() {
        Movimiento antes = sinAbono();
        assertThat(antes.cxcCobranza()).isCloseTo(bd(4_000), within(UN_PESO));
        assertThat(antes.cxcMora()).isCloseTo(bd("1916.83"), within(UN_PESO));
        assertThat(antes.cxcInteresCorriente()).isCloseTo(bd("162364.39"), within(UN_PESO));
        // Semanas sin pago al día: solo se causa el seguro (Inputs ⑥, P-07).
        assertThat(antes.cxcServiciosPorConcepto().seguro()).isCloseTo(bd("10219.15"), within(UN_PESO));
        assertThat(antes.cxcServiciosPorConcepto().fondoGarantias()).isZero();
        assertThat(antes.capitalVencido()).isCloseTo(bd("138854.44"), within(UN_PESO));
        assertThat(antes.cuotasVencidas()).isEqualTo(3);
        assertThat(antes.estado()).isEqualTo(EstadoCredito.EN_MORA);
    }

    @Test
    void elAbonoCubreCadaPendienteYElExcedenteVaACapitalVigente() {
        Movimiento antes = sinAbono();
        Movimiento semana = motor.ejecutar(caso13(VALOR_ABONO, ModalidadAbono.MENOR_PLAZO)).get(ABONO);
        DeudaExtinguida pagado = semana.pendientesPagadosExtra();

        assertThat(pagado.cobranza()).isEqualByComparingTo(antes.cxcCobranza());
        assertThat(pagado.mora()).isEqualByComparingTo(antes.cxcMora());
        assertThat(pagado.interesCorriente()).isEqualByComparingTo(antes.cxcInteresCorriente());
        assertThat(pagado.servicios().total()).isEqualByComparingTo(antes.cxcServicios());
        assertThat(pagado.capital()).isEqualByComparingTo(antes.capitalVencido());
        assertThat(pagado.total()).isCloseTo(bd("317354.81"), within(UN_PESO));

        // El excedente va a capital vigente y el cliente queda al día.
        assertThat(semana.abonoExtra()).isCloseTo(bd("1182645.20"), within(UN_PESO));
        assertThat(Calc.sumar(pagado.total(), semana.abonoExtra())).isCloseTo(VALOR_ABONO, within(Calc.EPSILON_SALDO));
        assertThat(semana.saldoFinal()).isCloseTo(bd("3169909.34"), within(UN_PESO));
        assertThat(semana.cxcCobranza()).isZero();
        assertThat(semana.cxcMora()).isZero();
        assertThat(semana.cxcInteresCorriente()).isZero();
        assertThat(semana.cxcServicios()).isZero();
        assertThat(semana.capitalVencido()).isZero();
        assertThat(semana.cuotasVencidas()).isZero();
        assertThat(semana.estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
        assertThat(semana.pagoTotalCliente()).isCloseTo(VALOR_ABONO, within(Calc.EPSILON_SALDO));
    }

    /** ABN-2: el excedente cubre primero el capital vencido y solo después el vigente. */
    @Test
    void unAbonoQueNoAlcanzaElVencidoNoTocaElCapitalVigente() {
        Movimiento antes = sinAbono();
        BigDecimal sinCapital = Calc.sumar(antes.cxcCobranza(), antes.cxcMora(), antes.cxcInteresCorriente(),
                antes.cxcServicios());
        BigDecimal abono = Calc.sumar(sinCapital, bd(50_000));
        Movimiento semana = motor.ejecutar(caso13(abono, ModalidadAbono.MENOR_PLAZO)).get(ABONO);

        assertThat(semana.pendientesPagadosExtra().capital()).isCloseTo(bd(50_000), within(Calc.EPSILON_SALDO));
        assertThat(semana.abonoExtra()).isZero();
        assertThat(semana.capitalVencido()).isCloseTo(Calc.restar(antes.capitalVencido(), bd(50_000)), within(UN_PESO));
        assertThat(semana.capitalVigente()).isCloseTo(antes.capitalVigente(), within(UN_PESO));
        assertThat(semana.estado()).isEqualTo(EstadoCredito.EN_MORA);
    }

    @Nested
    class DespuesDelAbono {

        @Test
        void aMenorPlazoLaCuotaSeMantieneYElCreditoTerminaEnLa83() {
            List<Movimiento> m = motor.ejecutar(caso13(VALOR_ABONO, ModalidadAbono.MENOR_PLAZO));
            BigDecimal cuota = m.get(ABONO).cuotaFinanciera();
            assertThat(cuota).isCloseTo(bd("100406.27"), within(UN_PESO));
            assertThat(m.get(ABONO + 1).cuotaFinanciera()).isEqualByComparingTo(cuota);
            assertThat(m.get(ABONO + 1).interesCorriente()).isCloseTo(bd("38591.88"), within(UN_PESO));
            Movimiento ultimo = m.getLast();
            assertThat(ultimo.periodo()).isEqualTo(83);
            assertThat(ultimo.evento()).isEqualTo("Pago final");
            assertThat(ultimo.saldoFinal()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
            assertThat(ultimo.estado()).isEqualTo(EstadoCredito.CANCELADO);
        }

        @Test
        void aMenorCuotaLaCuotaSeRecalculaSobreElCapitalVigenteYTerminaEnLa104() {
            List<Movimiento> m = motor.ejecutar(caso13(VALOR_ABONO, ModalidadAbono.MENOR_CUOTA));
            assertThat(m.get(ABONO).cuotaFinanciera()).isCloseTo(bd("100406.27"), within(UN_PESO));
            assertThat(m.get(ABONO + 1).cuotaFinanciera()).isCloseTo(bd("73124.59"), within(UN_PESO));
            assertThat(m.getLast().periodo()).isEqualTo(104);
            assertThat(m.getLast().saldoFinal()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
        }

        @Test
        void losAsientosCuadran() {
            // El Contabilizador lanza excepción si algún asiento no cuadra.
            Contabilizador.estandar().contabilizar(motor.ejecutar(caso13(VALOR_ABONO, ModalidadAbono.MENOR_PLAZO)));
            Contabilizador.estandar().contabilizar(motor.ejecutar(caso13(VALOR_ABONO, ModalidadAbono.MENOR_CUOTA)));
        }
    }

    /** Contraste: con las reglas del Excel (SOLO_CAPITAL) todo el abono va a capital y lo pendiente sigue pendiente. */
    @Test
    void conLasReglasDelExcelElAbonoVaTodoACapital() {
        Movimiento antes = sinAbono();
        Movimiento semana = motor.ejecutar(conPendientes().en(ABONO, new AbonoExtra(VALOR_ABONO))).get(ABONO);
        assertThat(semana.abonoExtra()).isEqualByComparingTo(VALOR_ABONO);
        assertThat(semana.pendientesPagadosExtra().total()).isZero();
        assertThat(semana.cxcInteresCorriente()).isEqualByComparingTo(antes.cxcInteresCorriente());
        assertThat(semana.estado()).isEqualTo(EstadoCredito.EN_MORA);
    }
}
