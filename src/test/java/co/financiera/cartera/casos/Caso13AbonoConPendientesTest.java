package co.financiera.cartera.casos;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.Calc;
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
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Caso 13 · Abono extra con pendientes (ABN-1, Diego ronda 3, 2026-10-20). No está en el Excel: lo arma
 * el equipo y Diego valida los valores (docs/reglas/caso-13.md). Réplica del Excel; la semana 40 no paga,
 * la 41 paga 5.000 con 3 días de atraso y la 42 no paga la cuota pero trae un abono extra de 1.500.000.
 * Con {@code aplicacionAbono = PRIMERO_PENDIENTES} (A4/D21) el abono paga primero los pendientes por
 * prelación y el excedente va al capital vencido y después al vigente (ABN-2). Esas son las cifras de la
 * ronda 3 ("antes"). Desde la ronda 4 (R4-CASO13) el perfil de negocio usa {@code PRELACION_COMPLETA}: el
 * excedente vuelve a pasar por la prelación sobre lo causado de la semana 42 ({@link RondaCuatro}).
 */
class Caso13AbonoConPendientesTest {

    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    static final int SIN_PAGO = 40;
    static final int PAGO_TARDIO = 41;
    static final int ABONO = 42;
    static final BigDecimal VALOR_ABONO = bd(1_500_000);
    static final ReglasNegocio DIEGO = ReglasNegocio.EXCEL.conAplicacionAbono(AplicacionAbono.PRIMERO_PENDIENTES);
    /** R4-CASO13 (Diego, ronda 4, 2026-10-21): el excedente vuelve a pasar por la prelación. */
    static final ReglasNegocio RONDA4 = ReglasNegocio.EXCEL.conAplicacionAbono(AplicacionAbono.PRELACION_COMPLETA);

    private final MotorCartera motor = new MotorCartera();

    static Escenario conPendientes() {
        return EscenariosExcel.caso13ConPendientes(ParametrosProducto.replicaExcel());
    }

    static Escenario caso13(BigDecimal abono, ModalidadAbono modalidad) {
        return EscenariosExcel.caso13(ParametrosProducto.replicaExcel(), abono, modalidad).conReglas(DIEGO);
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

    /**
     * R4-CASO13. Fuente: respuesta de Diego 2026-10-21 (ronda 4), pregunta R4-CASO13. "Sí se aplica todo lo
     * vencido de acuerdo al ejemplo, pero del 1.182.645,20 sobrante se vuelve a respetar la prelación de
     * aplicación de pagos de las 7 líneas": después de lo vencido, el excedente pasa otra vez por cobranza →
     * mora → interés corriente → fondo → asistencia → seguro → capital sobre lo causado de la semana 42, y
     * solo el resto va a capital. Como la semana 42 no se pagó al día solo había causado el seguro (D17); con
     * el abono su cuota queda pagada en la semana, así que el excedente cubre su fondo y su asistencia.
     */
    @Nested
    class RondaCuatro {

        private Escenario caso13Ronda4(BigDecimal abono, ModalidadAbono modalidad) {
            return EscenariosExcel.caso13(ParametrosProducto.replicaExcel(), abono, modalidad).conReglas(RONDA4);
        }

        @Test
        void elExcedentePagaElFondoYLaAsistenciaDeLaSemana42YSoloElRestoVaACapital() {
            Movimiento antes = motor.ejecutar(caso13(VALOR_ABONO, ModalidadAbono.MENOR_PLAZO)).get(ABONO);
            Movimiento semana = motor.ejecutar(caso13Ronda4(VALOR_ABONO, ModalidadAbono.MENOR_PLAZO)).get(ABONO);
            DeudaExtinguida pagado = semana.pendientesPagadosExtra();

            // Lo vencido se paga igual que en la ronda 3.
            assertThat(pagado.cobranza()).isCloseTo(bd(4_000), within(UN_PESO));
            assertThat(pagado.mora()).isCloseTo(bd("1916.83"), within(UN_PESO));
            assertThat(pagado.interesCorriente()).isCloseTo(bd("162364.39"), within(UN_PESO));
            assertThat(pagado.capital()).isCloseTo(bd("138854.44"), within(UN_PESO));
            // Segunda pasada: fondo y asistencia de la cuota 42; el seguro ya estaba causado y pagado.
            assertThat(pagado.servicios().fondoGarantias()).isCloseTo(bd("48643.17"), within(UN_PESO));
            assertThat(pagado.servicios().asistencia()).isCloseTo(bd("10984.62"), within(UN_PESO));
            assertThat(pagado.servicios().seguro()).isCloseTo(bd("10219.15"), within(UN_PESO));
            assertThat(pagado.total()).isCloseTo(bd("376982.59"), within(UN_PESO));
            assertThat(semana.serviciosCausados().total()).isCloseTo(bd("63034.17"), within(UN_PESO));

            assertThat(semana.abonoExtra()).isCloseTo(bd("1123017.41"), within(UN_PESO));
            assertThat(Calc.restar(antes.abonoExtra(), semana.abonoExtra())).isCloseTo(bd("59627.79"), within(UN_PESO));
            assertThat(semana.saldoFinal()).isCloseTo(bd("3229537.12"), within(UN_PESO));
            assertThat(semana.pagoTotalCliente()).isCloseTo(VALOR_ABONO, within(Calc.EPSILON_SALDO));
            assertThat(semana.cxcServicios()).isZero();
            assertThat(semana.cuotasVencidas()).isZero();
            assertThat(semana.estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
        }

        @Test
        void aMenorPlazoTerminaEnLa84YAMenorCuotaLaCuotaBajaA74500() {
            List<Movimiento> plazo = motor.ejecutar(caso13Ronda4(VALOR_ABONO, ModalidadAbono.MENOR_PLAZO));
            assertThat(plazo.get(ABONO + 1).cuotaFinanciera()).isCloseTo(bd("100406.27"), within(UN_PESO));
            assertThat(plazo.get(ABONO + 1).interesCorriente()).isCloseTo(bd("39317.82"), within(UN_PESO));
            assertThat(plazo.getLast().periodo()).isEqualTo(84);
            assertThat(plazo.getLast().evento()).isEqualTo("Pago final");
            assertThat(plazo.getLast().pagoRecibido()).isCloseTo(bd("69406.12"), within(UN_PESO));
            assertThat(plazo.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);

            List<Movimiento> cuota = motor.ejecutar(caso13Ronda4(VALOR_ABONO, ModalidadAbono.MENOR_CUOTA));
            assertThat(cuota.get(ABONO + 1).cuotaFinanciera()).isCloseTo(bd("74500.11"), within(UN_PESO));
            assertThat(cuota.getLast().periodo()).isEqualTo(104);
            assertThat(cuota.getLast().saldoFinal()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
        }

        /** A4 se mantiene: no se cobra nada de periodos futuros; la semana 43 causa y cobra lo de siempre. */
        @Test
        void nuncaSeCobranConceptosDePeriodosFuturos() {
            List<Movimiento> m = motor.ejecutar(caso13Ronda4(VALOR_ABONO, ModalidadAbono.MENOR_PLAZO));
            Movimiento semana43 = m.get(ABONO + 1);
            assertThat(semana43.serviciosCausados().total()).isCloseTo(bd("63034.17"), within(UN_PESO));
            assertThat(semana43.pagoServicios()).isCloseTo(bd("63034.17"), within(UN_PESO));
            assertThat(semana43.pagoInteresCorriente()).isEqualByComparingTo(semana43.interesCorriente());
            // Lo recaudado de la semana 42 se gira a terceros al cerrar el mes, con la 43.
            assertThat(semana43.pagoTerceros().fondoGarantias()).isCloseTo(bd("97286.34"), within(UN_PESO));
        }

        /** Si la cuota de la semana ya se pagó al día, no hay nada más que cobrar: igual que PRIMERO_PENDIENTES. */
        @Test
        void conLaCuotaPagadaAlDiaEsIgualQuePrimeroPendientes() {
            Escenario caso6 = EscenariosExcel.caso6(ParametrosProducto.replicaExcel());
            assertThat(motor.ejecutar(caso6.conReglas(RONDA4))).isEqualTo(motor.ejecutar(caso6.conReglas(DIEGO)));
        }

        /** Un excedente que no alcanza la cuota corriente paga en orden fondo → asistencia y no deja CxC. */
        @Test
        void unExcedenteCortoPagaPrimeroElFondoYNoDejaPendientes() {
            Movimiento antes = sinAbono();
            BigDecimal vencido = Calc.sumar(antes.cxcCobranza(), antes.cxcMora(), antes.cxcInteresCorriente(),
                    antes.cxcServicios(), antes.capitalVencido());
            Movimiento semana = motor.ejecutar(caso13Ronda4(Calc.sumar(vencido, bd(30_000)), ModalidadAbono.MENOR_PLAZO))
                    .get(ABONO);
            assertThat(semana.pendientesPagadosExtra().servicios().fondoGarantias()).isCloseTo(bd(30_000), within(UN_PESO));
            assertThat(semana.pendientesPagadosExtra().servicios().asistencia()).isZero();
            assertThat(semana.abonoExtra()).isZero();
            assertThat(semana.cxcServicios()).isZero();
            assertThat(semana.estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
        }

        @Test
        void losAsientosCuadranYElAbonoAcreditaLosServiciosATerceros() {
            List<Movimiento> m = motor.ejecutar(caso13Ronda4(VALOR_ABONO, ModalidadAbono.MENOR_PLAZO));
            Contabilizador.estandar().contabilizar(m);
            Contabilizador.estandar().contabilizar(motor.ejecutar(caso13Ronda4(VALOR_ABONO, ModalidadAbono.MENOR_CUOTA)));
        }
    }
}
