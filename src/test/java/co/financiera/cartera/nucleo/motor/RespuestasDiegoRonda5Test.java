package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.ClasificacionPago;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Tasas;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Respuestas de Diego de las rondas 5 y 6 que cambian el motor: P5-PRORRATEO, P5-PAGO-ANTES-PRIMERA,
 * P5-COSTO-DACION (sin cambio de comportamiento), R6-LIMITE-CORTO y R6-RECUP-AUTO. Las cifras esperadas se
 * calcularon aparte con la fórmula (monto 5.904.400, EA 87,62 %): ver cada prueba.
 */
class RespuestasDiegoRonda5Test {

    private static final ParametrosProducto SEMANAL = ParametrosProducto.replicaExcel();
    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    private static final BigDecimal MONTO = Operacion.ejemploExcel().montoFinanciado();
    private final MotorCartera motor = new MotorCartera();

    private static ReglasNegocio negocio() {
        try {
            return ReglasNegocio.desde(ReglasNegocioConfiguradaTest.configuracion());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * NEGOCIO sin R8-REDONDEO-PESO: aísla el prorrateo (P5) del redondeo de la cuota al peso, que mueve el interés del
     * periodo hasta medio peso.
     */
    private static ReglasNegocio negocioSinRedondeo() {
        return negocio().conRedondeoCuotaAlPeso(false);
    }

    private static Movimiento fila(List<Movimiento> m, int periodo) {
        return m.stream().filter(x -> x.periodo() == periodo).findFirst().orElseThrow();
    }

    /** Interés de {@code dias} días reales: saldo × ((1 + tasa diaria)^días − 1). */
    private static BigDecimal interesPorDias(BigDecimal saldo, ParametrosProducto p, int dias) {
        BigDecimal diaria = Tasas.de(p).diaria();
        return Calc.multiplicar(saldo, Calc.restar(Calc.potencia(Calc.sumar(Calc.UNO, diaria), dias), Calc.UNO));
    }

    /**
     * P5-PRORRATEO. Fuente: respuesta de Diego, ronda 5, pregunta P5-PRORRATEO. La primera cuota se prorratea
     * entre el desembolso y la próxima fecha de pago: solo el interés, por los días reales; capital y servicios
     * complementarios van completos ("sumándole los días previos a la primera cuota"). En las tres periodicidades.
     */
    @Nested
    class P5Prorrateo {

        /** Semanal: desembolso jueves 1-oct-2026, primer martes 13-oct-2026 = 12 días. */
        @Test
        void semanalProrrateaSoloElInteresDeLaPrimeraCuota() {
            CalendarioPagos cal = CalendarioPagos.semanal(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 13));
            assertThat(cal.diasDelPeriodo(1)).isEqualTo(12);
            List<Movimiento> base = motor.ejecutar(new Escenario(Operacion.ejemploExcel(), SEMANAL).conReglas(negocioSinRedondeo()));
            List<Movimiento> m = motor.ejecutar(new Escenario(Operacion.ejemploExcel(), SEMANAL).conReglas(negocioSinRedondeo())
                    .conCalendario(cal));
            Movimiento uno = fila(m, 1);
            Movimiento sin = fila(base, 1);
            // A mano: interés 5.904.400 × (1,8762^(12/365) − 1) = 123.420,22; capital 100.406,27 − 71.882,79 = 28.523,49.
            assertThat(uno.interesCorriente()).isCloseTo(bd("123420.22"), within(UN_PESO));
            assertThat(uno.interesCorriente()).isCloseTo(interesPorDias(MONTO, SEMANAL, 12), within(bd("0.000001")));
            assertThat(sin.interesCorriente()).isCloseTo(bd("71882.79"), within(UN_PESO));
            assertThat(uno.capitalContractual()).isCloseTo(bd("28523.49"), within(UN_PESO))
                    .isEqualByComparingTo(sin.capitalContractual());
            assertThat(uno.cuotaFinanciera()).isCloseTo(bd("151943.70"), within(UN_PESO));
            // Servicios completos.
            assertThat(uno.serviciosCausados()).isEqualTo(sin.serviciosCausados());
            // Paga la cuota prorrateada completa: queda al día y el saldo baja solo el capital de la cuota.
            assertThat(uno.pagoRecibido()).isCloseTo(Calc.sumar(uno.cuotaFinanciera(), uno.serviciosCausados().total()),
                    within(bd("0.000001")));
            assertThat(uno.estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
            assertThat(uno.saldoFinal()).isEqualByComparingTo(sin.saldoFinal());
            // Desde la segunda cuota el plan es el mismo.
            assertThat(fila(m, 2)).isEqualTo(fila(base, 2));
            assertThat(m).hasSameSizeAs(base);
            assertThat(m.getLast()).isEqualTo(base.getLast());
        }

        /** Quincenal: desembolso 8-oct-2026, primer vencimiento el 20 = 12 días. */
        @Test
        void quincenalUsaLosDiasHastaEl5oEl20() {
            ParametrosProducto q = ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.QUINCENAL, 48);
            CalendarioPagos cal = CalendarioPagos.quincenal(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 20));
            Movimiento uno = fila(motor.ejecutar(new Escenario(Operacion.ejemploExcel(), q).conReglas(negocio())
                    .conCalendario(cal)), 1);
            // A mano: cuota 219.092,94; interés normal 156.852,86; capital 62.240,08; interés de 12 días 123.420,22.
            assertThat(uno.capitalContractual()).isCloseTo(bd("62240.08"), within(UN_PESO));
            assertThat(uno.interesCorriente()).isCloseTo(bd("123420.22"), within(UN_PESO));
            assertThat(uno.cuotaFinanciera()).isCloseTo(bd("185660.30"), within(UN_PESO));
        }

        /** Mensual: el día 5. Del 15-oct al 5-nov son 21 días (menos interés); del 1-oct al 5-nov, 35 (más). */
        @Test
        void mensualAlDia5ConMenosOMasDeUnMes() {
            ParametrosProducto mes = ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.MENSUAL, 24);
            CalendarioPagos corto = CalendarioPagos.mensual(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 5));
            CalendarioPagos largo = CalendarioPagos.mensual(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 5));
            assertThat(corto.diasDelPeriodo(1)).isEqualTo(21);
            assertThat(largo.diasDelPeriodo(1)).isEqualTo(35);
            Movimiento c = fila(motor.ejecutar(new Escenario(Operacion.ejemploExcel(), mes).conReglas(negocio())
                    .conCalendario(corto)), 1);
            Movimiento l = fila(motor.ejecutar(new Escenario(Operacion.ejemploExcel(), mes).conReglas(negocio())
                    .conCalendario(largo)), 1);
            // A mano: cuota 444.006,17; interés del mes 317.872,58; capital 126.133,58.
            assertThat(c.capitalContractual()).isCloseTo(bd("126133.58"), within(UN_PESO));
            assertThat(c.interesCorriente()).isCloseTo(bd("217675.49"), within(UN_PESO));
            assertThat(c.cuotaFinanciera()).isCloseTo(bd("343809.08"), within(UN_PESO));
            assertThat(l.capitalContractual()).isEqualByComparingTo(c.capitalContractual());
            assertThat(l.interesCorriente()).isCloseTo(bd("367232.76"), within(UN_PESO));
            assertThat(l.cuotaFinanciera()).isCloseTo(bd("493366.34"), within(UN_PESO));
        }

        @Test
        void conElExcelOSinFechasNoCambiaNada() {
            CalendarioPagos cal = CalendarioPagos.semanal(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 13));
            List<Movimiento> excel = motor.ejecutar(EscenariosExcel.caso1(SEMANAL));
            assertThat(motor.ejecutar(EscenariosExcel.caso1(SEMANAL).conCalendario(cal))).isEqualTo(excel);
            List<Movimiento> sinFechas = motor.ejecutar(EscenariosExcel.caso1(SEMANAL).conReglas(negocioSinRedondeo()));
            assertThat(fila(sinFechas, 1).interesCorriente()).isEqualByComparingTo(fila(excel, 1).interesCorriente());
            assertThat(fila(sinFechas, 1).cuotaFinanciera()).isEqualByComparingTo(fila(excel, 1).cuotaFinanciera());
        }

        @Test
        void laClaveVieneDelPerfilDeNegocioYElCalendarioDebeSerDeLaMismaPeriodicidad() {
            assertThat(ReglasNegocio.EXCEL.prorrateoPrimeraCuota()).isFalse();
            assertThat(negocio().prorrateoPrimeraCuota()).isTrue();
            assertThat(ReglasNegocio.CLAVES).contains("prorrateoPrimeraCuota");
            assertThat(ReglasNegocio.desde(Map.of("prorrateoPrimeraCuota", "false")).prorrateoPrimeraCuota()).isFalse();
            CalendarioPagos quincenal = CalendarioPagos.quincenal(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 20));
            Escenario semanal = new Escenario(Operacion.ejemploExcel(), SEMANAL);
            assertThatThrownBy(() -> semanal.conCalendario(quincenal)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * P5-PAGO-ANTES-PRIMERA. Fuente: respuesta de Diego, ronda 5. Un pago antes de la primera cuota, cuando no hay
     * cuota causada, es abono extra a capital. Ya era así: no hubo que corregir nada.
     */
    @Nested
    class P5PagoAntesDeLaPrimera {

        @Test
        void sinCuotaCausadaTodoElPagoEsAbonoExtra() {
            ClasificacionPago c = ClasificacionPago.clasificar(bd("500000"), Calc.CERO);
            assertThat(c.pagoCorriente()).isZero();
            assertThat(c.abonoExtra()).isEqualByComparingTo("500000");
        }

        /**
         * En el motor el abono entra con la primera cuota y va 100 % a capital: no hay pendientes que pagar ni se
         * adelanta nada de la cuota 1, que se sigue cobrando completa. A mano: 5.904.400 − 28.523,49 − 500.000 =
         * 5.375.876,51.
         */
        @Test
        void enElMotorVaTodoACapitalYNoAdelantaLaPrimeraCuota() {
            Escenario e = new Escenario(Operacion.ejemploExcel(), SEMANAL)
                    .en(1, new AbonoExtra(bd("500000"), ModalidadAbono.MENOR_PLAZO)).conReglas(negocio());
            Movimiento uno = fila(motor.ejecutar(e), 1);
            assertThat(uno.abonoExtra()).isEqualByComparingTo("500000");
            assertThat(uno.pendientesPagadosExtra().total()).isZero();
            assertThat(uno.pagoCapital()).isCloseTo(bd("28523.49"), within(UN_PESO));
            assertThat(uno.saldoFinal()).isCloseTo(bd("5375876.51"), within(UN_PESO));
            assertThat(uno.estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
            // Igual que con las reglas del Excel (SOLO_CAPITAL): sin pendientes las dos aplicaciones coinciden.
            Movimiento excel = fila(motor.ejecutar(new Escenario(Operacion.ejemploExcel(), SEMANAL)
                    .en(1, new AbonoExtra(bd("500000"), ModalidadAbono.MENOR_PLAZO))), 1);
            assertThat(uno.saldoFinal()).isEqualByComparingTo(excel.saldoFinal());
        }
    }

    /**
     * P5-COSTO-DACION. Fuente: respuesta de Diego, ronda 5. En la dación el costo real de la recuperación se suma al
     * faltante que cubre el fondo de garantías: es lo que ya hacía {@code costoRecuperacionAlFondo = true}.
     */
    @Test
    void p5CostoDacionConfirmaLaReglaQueYaEstaba() {
        assertThat(negocio().costoRecuperacionAlFondo()).isTrue();
        assertThat(ReglasNegocio.EXCEL.costoRecuperacionAlFondo()).isTrue();
    }

    /**
     * R6-LIMITE-CORTO. Fuente: respuesta de Diego, ronda 6. El plazo límite es min(plazo propio del crédito, 24
     * meses) en periodos del crédito: un crédito de 12 meses tiene límite de 12 meses. Los casos 8 y 9 conservan sus
     * 100 y 120 semanas.
     */
    @Nested
    class R6LimiteCorto {

        @Test
        void elLimiteEsElMenorEntreElPlazoYLos24Meses() {
            ReglasNegocio diego = negocio();
            assertThat(diego.plazoLimitePeriodos(Periodicidad.MENSUAL, 12)).hasValue(12);
            assertThat(diego.plazoLimitePeriodos(Periodicidad.SEMANAL, 52)).hasValue(52);
            assertThat(diego.plazoLimitePeriodos(Periodicidad.QUINCENAL, 24)).hasValue(24);
            assertThat(diego.plazoLimitePeriodos(Periodicidad.SEMANAL, 104)).hasValue(104);
            assertThat(diego.plazoLimitePeriodos(Periodicidad.MENSUAL, 24)).hasValue(24);
        }

        @Test
        void unCreditoMensualDe12CuotasEnMoraSeProyectaHastaLaCuota12() {
            ParametrosProducto doce = ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.MENSUAL, 12);
            ParametrosProducto conLimite = doce.conPlazoLimite(
                    negocio().plazoLimitePeriodos(Periodicidad.MENSUAL, 12).orElseThrow());
            List<Movimiento> m = motor.ejecutar(new Escenario(Operacion.ejemploExcel(), conLimite)
                    .entre(10, 12, new SinPago(true)).conReglas(negocio()));
            assertThat(m.getLast().periodo()).isEqualTo(12);
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.EN_MORA);
        }

        @Test
        void losCasos8y9ConservanSusValores() {
            assertThat(EscenariosExcel.caso8(SEMANAL).conReglas(negocio()).parametros().plazoLimitePeriodos()).isEqualTo(100);
            assertThat(EscenariosExcel.caso9(SEMANAL).conReglas(negocio()).parametros().plazoLimitePeriodos()).isEqualTo(120);
        }
    }

    /**
     * R6-RECUP-AUTO. Fuente: respuesta de Diego, ronda 6. El motor solo marca el crédito listo para recuperación,
     * nunca ejecuta la dación ni la retoma; una persona decide. La marca persiste aunque el cliente se ponga al día.
     */
    @Nested
    class R6RecuperacionSoloSeMarca {

        @Test
        void laMarcaPersisteAunqueElClienteSePongaAlDia() {
            Escenario e = new Escenario(Operacion.ejemploExcel(), SEMANAL).entre(30, 31, new SinPago(true))
                    .en(32, new PagoAlDia()).conReglas(negocio());
            List<Movimiento> m = motor.ejecutar(e);
            assertThat(fila(m, 30).listoParaRecuperacion()).isFalse();
            assertThat(fila(m, 31).listoParaRecuperacion()).isTrue();
            assertThat(fila(m, 32).estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
            assertThat(fila(m, 32).cuotasVencidas()).isZero();
            for (int periodo = 32; periodo < m.getLast().periodo(); periodo++) {
                assertThat(fila(m, periodo).listoParaRecuperacion()).as("periodo %d", periodo).isTrue();
            }
            // El motor no ejecuta nada: el crédito termina pagando su plan.
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
            assertThat(m.getLast().listoParaRecuperacion()).isFalse();
        }

        @Test
        void cuandoElCreditoTerminaYaNoHayNadaQueRecuperar() {
            Escenario e = new Escenario(Operacion.ejemploExcel(), SEMANAL).entre(30, 31, new SinPago(true))
                    .en(32, new PagoAlDia()).en(40, new PrepagoTotal(0)).conReglas(negocio());
            List<Movimiento> m = motor.ejecutar(e);
            assertThat(fila(m, 39).listoParaRecuperacion()).isTrue();
            assertThat(fila(m, 40).estado()).isEqualTo(EstadoCredito.CANCELADO);
            assertThat(fila(m, 40).listoParaRecuperacion()).isFalse();
        }

        @Test
        void conElExcelNuncaSeMarca() {
            Escenario e = new Escenario(Operacion.ejemploExcel(), SEMANAL).entre(30, 31, new SinPago(true))
                    .en(32, new PagoAlDia());
            assertThat(motor.ejecutar(e)).noneMatch(Movimiento::listoParaRecuperacion);
        }
    }
}
