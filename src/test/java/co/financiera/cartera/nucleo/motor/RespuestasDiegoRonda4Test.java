package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.TipoRecuperacion;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Respuestas de Diego de la ronda 4 (2026-10-21) que el motor aplica: R4-R2 (costo de la recuperación),
 * R4-COBRANZA-CONTABLE y R4-PLAZO-LIMITE. R4-CASO13 está en {@code Caso13AbonoConPendientesTest}, R4-QUIN y
 * R4-PLAZO-LINEA en {@code PeriodicidadTest} y R4-MENSUAL-DIA en {@code CalendarioPagosTest}.
 */
class RespuestasDiegoRonda4Test {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    private final MotorCartera motor = new MotorCartera();

    private static Escenario atrasado() {
        return new Escenario(Operacion.ejemploExcel(), P).entre(31, 33, new SinPago(false));
    }

    private static BigDecimal debito(Asiento a, Cuenta c) {
        return a.lineas().stream().filter(l -> l.cuenta() == c).map(Asiento.Linea::debito).reduce(Calc.CERO, Calc::sumar);
    }

    private static BigDecimal credito(Asiento a, Cuenta c) {
        return a.lineas().stream().filter(l -> l.cuenta() == c).map(Asiento.Linea::credito).reduce(Calc.CERO, Calc::sumar);
    }

    private static Asiento asientoDelCierre(List<Movimiento> m, ReglasNegocio reglas) {
        Movimiento cierre = m.getLast();
        return Contabilizador.para(reglas).contabilizar(cierre).stream()
                .filter(a -> a.evento().equals("Evento de cierre")).findFirst().orElseThrow();
    }

    /**
     * R4-R2. Fuente: respuesta de Diego 2026-10-21 (ronda 4), pregunta R4-R2. En la recuperación se descuenta el
     * costo real con soporte (grúa, bodegaje, avalúo, trámites), en dación y en retoma.
     */
    @Nested
    class R2CostoDeLaRecuperacion {

        private static final BigDecimal COSTO = bd(250_000);

        private List<Movimiento> cierre(TipoRecuperacion tipo, BigDecimal moto, BigDecimal costo, ReglasNegocio reglas) {
            return motor.ejecutar(atrasado().en(34, new CierrePorRecuperacion(tipo, moto, costo)).conReglas(reglas));
        }

        @Test
        void sinCostoTodoQuedaComoElExcel() {
            CierrePorRecuperacion sinCosto = new CierrePorRecuperacion(TipoRecuperacion.RETOMA, bd(6_800_000));
            assertThat(sinCosto.costoRecuperacion()).isZero();
            assertThat(motor.ejecutar(atrasado().en(34, sinCosto)))
                    .isEqualTo(cierre(TipoRecuperacion.RETOMA, bd(6_800_000), Calc.CERO, ReglasNegocio.EXCEL));
            assertThatThrownBy(() -> new CierrePorRecuperacion(TipoRecuperacion.RETOMA, bd(1), bd(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void enLaRetomaElCostoReduceElExcedenteQueSeDevuelve() {
            Movimiento sin = cierre(TipoRecuperacion.RETOMA, bd(6_800_000), Calc.CERO, ReglasNegocio.EXCEL).getLast();
            List<Movimiento> m = cierre(TipoRecuperacion.RETOMA, bd(6_800_000), COSTO, ReglasNegocio.EXCEL);
            Movimiento con = m.getLast();
            assertThat(con.cxpDeudor()).isCloseTo(Calc.restar(sin.cxpDeudor(), COSTO), within(UN_PESO));
            assertThat(con.costoRecuperacion()).isEqualByComparingTo(COSTO);
            assertThat(con.costoRecuperacionAsumido()).isZero();
            assertThat(con.cxcFondoGarantias()).isZero();
            Asiento a = asientoDelCierre(m, ReglasNegocio.EXCEL);
            assertThat(credito(a, Cuenta.CXP_COSTOS_RECUPERACION)).isEqualByComparingTo(COSTO);
            assertThat(credito(a, Cuenta.CXP_DEUDOR)).isCloseTo(con.cxpDeudor(), within(UN_PESO));
            assertThat(a.cuadra()).isTrue();
        }

        /** Provisional (P5-COSTO-DACION): la opción más conservadora, el costo se suma al faltante del fondo. */
        @Test
        void enLaDacionElCostoSeSumaAlFaltanteQueCubreElFondo() {
            ReglasNegocio alFondo = ReglasNegocio.EXCEL.conCostoRecuperacionAlFondo(true);
            Movimiento sin = cierre(TipoRecuperacion.DACION_EN_PAGO, bd(3_000_000), Calc.CERO, alFondo).getLast();
            List<Movimiento> m = cierre(TipoRecuperacion.DACION_EN_PAGO, bd(3_000_000), COSTO, alFondo);
            Movimiento con = m.getLast();
            assertThat(con.cxcFondoGarantias()).isCloseTo(Calc.sumar(sin.cxcFondoGarantias(), COSTO), within(UN_PESO));
            assertThat(con.costoRecuperacionAsumido()).isZero();
            Asiento a = asientoDelCierre(m, alFondo);
            assertThat(debito(a, Cuenta.CXC_FONDO_GARANTIAS)).isCloseTo(con.cxcFondoGarantias(), within(UN_PESO));
            assertThat(credito(a, Cuenta.CXP_COSTOS_RECUPERACION)).isEqualByComparingTo(COSTO);
            assertThat(debito(a, Cuenta.GASTO_COSTOS_RECUPERACION)).isZero();
        }

        @Test
        void sinCargoAlFondoElCostoDeLaDacionLoAsumeLaFinanciera() {
            ReglasNegocio asume = ReglasNegocio.EXCEL.conCostoRecuperacionAlFondo(false);
            Movimiento sin = cierre(TipoRecuperacion.DACION_EN_PAGO, bd(3_000_000), Calc.CERO, asume).getLast();
            List<Movimiento> m = cierre(TipoRecuperacion.DACION_EN_PAGO, bd(3_000_000), COSTO, asume);
            Movimiento con = m.getLast();
            assertThat(con.cxcFondoGarantias()).isCloseTo(sin.cxcFondoGarantias(), within(UN_PESO));
            assertThat(con.costoRecuperacionAsumido()).isEqualByComparingTo(COSTO);
            Asiento a = asientoDelCierre(m, asume);
            assertThat(debito(a, Cuenta.GASTO_COSTOS_RECUPERACION)).isEqualByComparingTo(COSTO);
            assertThat(credito(a, Cuenta.CXP_COSTOS_RECUPERACION)).isEqualByComparingTo(COSTO);
        }

        /** Retoma cuyo excedente no alcanza el costo: con el fondo el resto es faltante; sin él, gasto. */
        @Test
        void unaRetomaConExcedenteMenorQueElCostoCuadraConLasDosOpciones() {
            Movimiento base = cierre(TipoRecuperacion.RETOMA, bd(6_800_000), Calc.CERO, ReglasNegocio.EXCEL).getLast();
            BigDecimal moto = Calc.sumar(base.deudaAExtinguir(), bd(100_000));
            for (boolean alFondo : new boolean[] {true, false}) {
                ReglasNegocio r = ReglasNegocio.EXCEL.conCostoRecuperacionAlFondo(alFondo);
                List<Movimiento> m = cierre(TipoRecuperacion.RETOMA, moto, COSTO, r);
                Movimiento con = m.getLast();
                assertThat(con.cxpDeudor()).isZero();
                assertThat(con.cxcFondoGarantias()).isCloseTo(alFondo ? bd(150_000) : Calc.CERO, within(UN_PESO));
                assertThat(con.costoRecuperacionAsumido()).isCloseTo(alFondo ? Calc.CERO : bd(150_000), within(UN_PESO));
                assertThat(asientoDelCierre(m, r).cuadra()).isTrue();
            }
        }

        @Test
        void elPerfilDeNegocioTraeLaOpcionConservadora() {
            assertThat(ReglasNegocio.desde(Map.of("costoRecuperacionAlFondo", "true")).costoRecuperacionAlFondo()).isTrue();
            assertThat(ReglasNegocio.desde(Map.of("costoRecuperacionAlFondo", "false")).costoRecuperacionAlFondo()).isFalse();
            assertThat(ReglasNegocio.CLAVES).contains("costoRecuperacionAlFondo", "cobranzaIngresoNoOperacional",
                    "plazoLimiteMeses");
        }
    }

    /**
     * R4-COBRANZA-CONTABLE. Fuente: respuesta de Diego 2026-10-21 (ronda 4), pregunta R4-COBRANZA-CONTABLE. Los
     * gastos de cobranza se registran como ingresos no operacionales (PUC clase 42).
     */
    @Nested
    class CobranzaComoIngresoNoOperacional {

        private final List<Movimiento> caso9 = motor.ejecutar(EscenariosExcel.caso9(P));

        @Test
        void conElPerfilDeNegocioLaCobranzaVaAIngresosNoOperacionales() {
            ReglasNegocio diego = ReglasNegocio.EXCEL.conCobranzaIngresoNoOperacional(true);
            BigDecimal causada = caso9.stream().map(Movimiento::cobranzaCausada).reduce(Calc.CERO, Calc::sumar);
            List<Asiento> asientos = Contabilizador.para(diego).contabilizar(caso9);
            BigDecimal noOperacional = asientos.stream().map(a -> credito(a, Cuenta.INGRESOS_NO_OPERACIONALES_COBRANZA))
                    .reduce(Calc.CERO, Calc::sumar);
            assertThat(causada).isPositive();
            assertThat(noOperacional).isEqualByComparingTo(causada);
            assertThat(asientos.stream().map(a -> credito(a, Cuenta.INGRESOS_COBRANZA)).reduce(Calc.CERO, Calc::sumar))
                    .isZero();
            assertThat(asientos.stream().map(a -> credito(a, Cuenta.INGRESOS_INTERESES)).reduce(Calc.CERO, Calc::sumar))
                    .isEqualByComparingTo(Contabilizador.estandar().contabilizar(caso9).stream()
                            .map(a -> credito(a, Cuenta.INGRESOS_INTERESES)).reduce(Calc.CERO, Calc::sumar));
        }

        /** El Excel (Caso9_Cobranza AE15:AG16) conserva "Ingresos por cobranza": la réplica no cambia. */
        @Test
        void conLasReglasDelExcelLosAsientosSonLosDeSiempre() {
            assertThat(Contabilizador.para(ReglasNegocio.EXCEL).contabilizar(caso9))
                    .isEqualTo(Contabilizador.estandar().contabilizar(caso9));
        }
    }

    /**
     * R4-PLAZO-LIMITE. Fuente: respuesta de Diego 2026-10-21 (ronda 4), pregunta R4-PLAZO-LIMITE. El plazo límite
     * de recuperación es de 24 meses, convertidos a periodos según la periodicidad.
     */
    @Test
    void elPlazoLimiteDe24MesesSeConvierteAPeriodosDelCredito() {
        ReglasNegocio diego = ReglasNegocio.desde(Map.of("plazoLimiteMeses", "24"));
        assertThat(diego.plazoLimiteMeses()).isEqualTo(24);
        assertThat(diego.plazoLimitePeriodos(Periodicidad.SEMANAL, 104)).hasValue(104);
        assertThat(diego.plazoLimitePeriodos(Periodicidad.QUINCENAL, 48)).hasValue(48);
        assertThat(diego.plazoLimitePeriodos(Periodicidad.MENSUAL, 12)).hasValue(24);
        // Nunca menos que las cuotas pactadas.
        assertThat(ReglasNegocio.EXCEL.conPlazoLimiteMeses(6).plazoLimitePeriodos(Periodicidad.MENSUAL, 12)).hasValue(12);
        // El Excel no lo fija: quedan los 100 y 120 semanas de los casos 8 y 9.
        assertThat(ReglasNegocio.EXCEL.plazoLimitePeriodos(Periodicidad.SEMANAL, 104)).isEmpty();
        assertThat(EscenariosExcel.caso9(P).conReglas(diego).parametros().plazoLimitePeriodos()).isEqualTo(120);
        assertThatThrownBy(() -> ReglasNegocio.EXCEL.conPlazoLimiteMeses(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
