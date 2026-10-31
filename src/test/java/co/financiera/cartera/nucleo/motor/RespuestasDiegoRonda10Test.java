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
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.BaseMoraCobranza;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.CausacionServicios;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.ComparacionCobranza;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Ronda 10 de Diego (2026-10-31), R10-CONTADOR-VENCIDAS, con R11-SEGUROS-TRAS-SUSPENSION y R10-PERIODO-ATRASADO (D70 a D73,
 * ADR 0006):
 * <ol>
 *   <li>El cliente siempre paga los servicios (fondo + asistencia + seguros) en la cuota, al día o atrasado
 *       ({@code causacionServicios = SIEMPRE}); sin suspensión, los dos seguros se causan siempre.</li>
 *   <li>La cuenta con terceros solo nace con el recaudo efectivo (ya era así; ahora a "Ingresos recibidos para
 *       terceros").</li>
 *   <li>La cobranza se ejecuta cuando saldo en mora / valor total de la cuota &gt; 2; con abonos parciales que no llegan a
 *       2 cuotas no hay cobranza pero sí interés de mora ({@code umbralCocienteCobranza = 2}).</li>
 *   <li>En dación o retoma se suspenden por completo los servicios (cierra el crédito).</li>
 * </ol>
 * Supuestos provisionales (ronda 12): el saldo en mora es la totalidad de lo pendiente ({@code TOTAL_PENDIENTE}), la comparación es
 * estricta ({@code MAYOR}) y el cociente se evalúa al iniciar el periodo con los saldos del cierre del anterior.
 */
class RespuestasDiegoRonda10Test {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final BigDecimal CUARENTA_MIL = bd(40_000);
    private final MotorCartera motor = new MotorCartera();

    private static ReglasNegocio negocio() {
        return ReglasNegocioConfiguradaTest.negocio();
    }

    private static Movimiento fila(List<Movimiento> m, int periodo) {
        return m.stream().filter(x -> x.periodo() == periodo).findFirst().orElseThrow();
    }

    private static Escenario conOperacionDelExcel() {
        return new Escenario(Operacion.ejemploExcel(), P);
    }

    private static Map<String, Escenario> casos() {
        Map<String, Function<ParametrosProducto, Escenario>> f = new LinkedHashMap<>();
        f.put("caso 1", EscenariosExcel::caso1);
        f.put("caso 2", EscenariosExcel::caso2);
        f.put("caso 3", p -> EscenariosExcel.caso3(p, false));
        f.put("caso 4", EscenariosExcel::caso4);
        f.put("caso 5", EscenariosExcel::caso5);
        f.put("caso 6", EscenariosExcel::caso6);
        f.put("caso 7", EscenariosExcel::caso7);
        f.put("caso 8", EscenariosExcel::caso8);
        f.put("caso 9", EscenariosExcel::caso9);
        f.put("caso 10", EscenariosExcel::caso10);
        f.put("caso 11", p -> EscenariosExcel.caso11(p, 2));
        f.put("caso 11 largo", EscenariosExcel::caso11);
        f.put("caso 12", EscenariosExcel::caso12);
        f.put("caso 13", EscenariosExcel::caso13);
        Map<String, Escenario> e = new LinkedHashMap<>();
        f.forEach((nombre, escenario) -> e.put(nombre, escenario.apply(P)));
        return e;
    }

    @Test
    void lasClavesDeLaRondaDiezEstanEnNegocioYNoEnElExcel() {
        ReglasNegocio n = negocio();
        assertThat(n.causacionServicios()).isEqualTo(CausacionServicios.SIEMPRE);
        assertThat(n.umbralCocienteCobranza()).isEqualByComparingTo("2");
        assertThat(n.baseMoraCobranza()).isEqualTo(BaseMoraCobranza.TOTAL_PENDIENTE);
        assertThat(n.comparacionCobranza()).isEqualTo(ComparacionCobranza.MAYOR);
        // Lo que reemplazan queda fuera de NEGOCIO (las rondas 8 y 9 lo siguen probando con negocioRonda9()).
        assertThat(n.cuotasVencidasParaSuspenderServicios()).isNull();
        assertThat(n.umbralesCuentanCuotaEnCurso()).isFalse();
        assertThat(ReglasNegocio.EXCEL.causacionServicios()).isEqualTo(CausacionServicios.SOLO_AL_DIA);
        assertThat(ReglasNegocio.EXCEL.umbralCocienteCobranza()).isNull();
        assertThat(ReglasNegocio.EXCEL.cobranzaPorCociente()).isFalse();
        ReglasNegocio leidas = ReglasNegocio.desdeEstricto(Map.of("causacionServicios", "SIEMPRE",
                "umbralCocienteCobranza", "2", "baseMoraCobranza", "CUOTAS_VENCIDAS", "comparacionCobranza", "MAYOR_O_IGUAL"));
        assertThat(leidas.causacionServicios()).isEqualTo(CausacionServicios.SIEMPRE);
        assertThat(leidas.baseMoraCobranza()).isEqualTo(BaseMoraCobranza.CUOTAS_VENCIDAS);
        assertThat(leidas.comparacionCobranza()).isEqualTo(ComparacionCobranza.MAYOR_O_IGUAL);
        BigDecimal negativo = bd(-1);
        assertThatThrownBy(() -> ReglasNegocio.EXCEL.conUmbralCocienteCobranza(negativo)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReglasNegocio.EXCEL.conCausacionServicios(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void elUmbralDelCocienteComparaSinDividir() {
        ReglasNegocio mayor = ReglasNegocio.EXCEL.conUmbralCocienteCobranza(bd(2));
        assertThat(mayor.llegaAlUmbralDeCobranza(bd(400), bd(200))).as("exactamente 2 no es > 2").isFalse();
        assertThat(mayor.llegaAlUmbralDeCobranza(new BigDecimal("400.01"), bd(200))).isTrue();
        assertThat(mayor.conComparacionCobranza(ComparacionCobranza.MAYOR_O_IGUAL).llegaAlUmbralDeCobranza(bd(400), bd(200)))
                .isTrue();
        assertThat(mayor.llegaAlUmbralDeCobranza(bd(1_000), Calc.CERO)).as("sin cuota no hay cociente").isFalse();
        assertThat(ReglasNegocio.EXCEL.llegaAlUmbralDeCobranza(bd(1_000_000), bd(1))).as("sin umbral").isFalse();
    }

    /** (1) y R11-SEGUROS-TRAS-SUSPENSION: dentro del plazo los servicios se causan siempre, al día o atrasado. */
    @Nested
    class ServiciosSiempre {

        @Test
        void sinPagoDuranteDiezSemanasCausaFondoAsistenciaYLosDosSegurosCadaSemana() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 10, new SinPago(true)).conReglas(negocio()));
            Servicios porCuota = Servicios.porCuota(Operacion.ejemploExcel().montoFinanciado(), P, negocio().porcentajeTodoRiesgo());
            for (int k = 1; k <= 10; k++) {
                Servicios sc = fila(m, k).serviciosCausados();
                assertThat(sc.total()).as("semana %d", k).isCloseTo(porCuota.total(), within(new BigDecimal("0.000001")));
                assertThat(sc.fondoGarantias()).as("semana %d", k).isPositive();
                assertThat(sc.asistencia()).as("semana %d", k).isPositive();
                assertThat(sc.seguro()).as("semana %d", k).isPositive();
                assertThat(sc.seguroTodoRiesgo()).as("semana %d", k).isPositive();
            }
            // Con la ronda 9 (suspensión por umbral) desde la 2.ª cuota atrasada solo se causaban los seguros.
            List<Movimiento> r9 = motor.ejecutar(conOperacionDelExcel().entre(1, 10, new SinPago(true))
                    .conReglas(ReglasNegocioConfiguradaTest.negocioRonda9()));
            assertThat(fila(r9, 5).serviciosCausados().fondoGarantias()).isZero();
        }

        /** Invariante por decisión: en NEGOCIO, dentro del plazo (hay capital contractual), fondo y asistencia > 0. */
        @Test
        void enLosTreceCasosConNegocioDentroDelPlazoNuncaFaltanFondoNiAsistencia() {
            casos().forEach((nombre, e) -> motor.ejecutar(e.conReglas(negocio())).stream()
                    .filter(x -> x.periodo() >= 1 && x.capitalContractual().signum() > 0
                            && x.estado() != EstadoCredito.CERRADO_POR_EVENTO)
                    .forEach(x -> {
                        assertThat(x.serviciosCausados().fondoGarantias()).as("%s, periodo %d", nombre, x.periodo()).isPositive();
                        assertThat(x.serviciosCausados().asistencia()).as("%s, periodo %d", nombre, x.periodo()).isPositive();
                        assertThat(x.serviciosCausados().seguro()).as("%s, periodo %d", nombre, x.periodo()).isPositive();
                    }));
        }

        /** (2): los servicios causados y no pagados no generan cuenta con terceros; solo lo recaudado. */
        @Test
        void laCuentaConTercerosSoloNaceConElRecaudo() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(5, 6, new SinPago(true)).en(7, new PagoAlDia())
                    .conReglas(negocio()));
            Contabilizador c = Contabilizador.para(negocio());
            for (int k = 5; k <= 6; k++) {
                assertThat(fila(m, k).serviciosCausados().total()).isPositive();
                assertThat(creditos(c.contabilizar(fila(m, k)), Cuenta.INGRESOS_RECIBIDOS_PARA_TERCEROS)).as("semana %d", k).isZero();
                assertThat(creditos(c.contabilizar(fila(m, k)), Cuenta.CXP_SERVICIOS_TERCEROS)).as("semana %d", k).isZero();
            }
            // La semana 7 recauda los servicios de la 5, la 6 y la 7: ahí nace la cuenta con terceros (sin el riesgo total,
            // que va a CxP seguros y su IVA, D69).
            Movimiento siete = fila(m, 7);
            BigDecimal esperado = Calc.restar(siete.pagoServicios(), siete.pagosServicios().todoRiesgoConIva());
            assertThat(creditos(c.contabilizar(siete), Cuenta.INGRESOS_RECIBIDOS_PARA_TERCEROS)).isCloseTo(esperado, within(bd("0.01")));
            assertThat(creditos(c.contabilizar(siete), Cuenta.CXP_SERVICIOS_TERCEROS)).isZero();
            // El Excel conserva su cuenta.
            List<Movimiento> excel = motor.ejecutar(EscenariosExcel.caso1(P));
            assertThat(creditos(Contabilizador.para(ReglasNegocio.EXCEL).contabilizar(fila(excel, 1)), Cuenta.CXP_SERVICIOS_TERCEROS))
                    .isPositive();
        }
    }

    /** (3): cobranza por el cociente saldo en mora / cuota total. */
    @Nested
    class CobranzaPorCociente {

        private final Escenario sinPagarDesdeLaUno = conOperacionDelExcel().entre(1, 4, new SinPago(true));

        @Test
        void sinPagarDesdeLaSemanaUnoLaCobranzaNaceAlSuperarDosCuotas() {
            List<Movimiento> m = motor.ejecutar(sinPagarDesdeLaUno.conReglas(negocio()));
            // Al iniciar la semana 2 se debe 1 cuota; al iniciar la 3, 2 cuotas más la mora de la 2: > 2.
            assertThat(fila(m, 1).cobranzaCausada()).isZero();
            assertThat(fila(m, 2).cobranzaCausada()).isZero();
            assertThat(fila(m, 2).interesMora()).as("la mora sí se causa").isPositive();
            assertThat(fila(m, 3).cobranzaCausada()).isEqualByComparingTo(CUARENTA_MIL);
            assertThat(fila(m, 4).cobranzaCausada()).isEqualByComparingTo(CUARENTA_MIL);
            assertThat(fila(m, 4).cxcCobranza()).isEqualByComparingTo(bd(80_000));
        }

        @Test
        void conExactamenteDosCuotasLaComparacionEstrictaNoCobraYLaNoEstrictaSi() {
            // Sin la mora en el numerador (CUOTAS_VENCIDAS), al iniciar la semana 3 el cociente es 2 exacto (cuotas al peso).
            ReglasNegocio soloCuotas = negocio().conBaseMoraCobranza(BaseMoraCobranza.CUOTAS_VENCIDAS);
            assertThat(fila(motor.ejecutar(sinPagarDesdeLaUno.conReglas(soloCuotas)), 3).cobranzaCausada()).isZero();
            assertThat(fila(motor.ejecutar(sinPagarDesdeLaUno.conReglas(soloCuotas
                    .conComparacionCobranza(ComparacionCobranza.MAYOR_O_IGUAL))), 3).cobranzaCausada()).isEqualByComparingTo(CUARENTA_MIL);
            // Al iniciar la 4 ya son 3 cuotas: cobra con las dos comparaciones.
            assertThat(fila(motor.ejecutar(sinPagarDesdeLaUno.conReglas(soloCuotas)), 4).cobranzaCausada())
                    .isEqualByComparingTo(CUARENTA_MIL);
        }

        @Test
        void conAbonosParcialesQueNoLleganADosCuotasNoHayCobranzaPeroSiMora() {
            // Paga la mitad de la cuota cada semana: la deuda sube ~0,5 cuotas por semana.
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 6, new PagoParcial(new BigDecimal("0.5"), 0))
                    .conReglas(negocio()));
            for (int k = 1; k <= 4; k++) {
                assertThat(fila(m, k).cobranzaCausada()).as("semana %d", k).isZero();
            }
            for (int k = 2; k <= 4; k++) {
                assertThat(fila(m, k).interesMora()).as("semana %d", k).isPositive();
                assertThat(fila(m, k).cuotasVencidas()).as("semana %d", k).isEqualTo(k);
            }
            // Al iniciar la 5 la deuda pasa de 2 cuotas totales: cobra.
            assertThat(fila(m, 5).cobranzaCausada()).isEqualByComparingTo(CUARENTA_MIL);
        }

        @Test
        void laCobranzaNoDependeDeLaInstruccionDelPeriodoSinoDeLosSaldosConQueEmpieza() {
            // Determinístico: con 2 semanas sin pago, la semana 3 causa la cobranza pague o no; si paga todo (PagoAlDia) la
            // cobranza va primero en la prelación y desde la 4 ya no hay cobranza.
            List<Movimiento> pagaTodo = motor.ejecutar(conOperacionDelExcel().entre(1, 2, new SinPago(true)).en(3, new PagoAlDia())
                    .conReglas(negocio()));
            assertThat(fila(pagaTodo, 3).cobranzaCausada()).isEqualByComparingTo(CUARENTA_MIL);
            assertThat(fila(pagaTodo, 3).pagoCobranza()).isEqualByComparingTo(CUARENTA_MIL);
            assertThat(fila(pagaTodo, 3).estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
            assertThat(fila(pagaTodo, 4).cobranzaCausada()).isZero();
            List<Movimiento> noPaga = motor.ejecutar(sinPagarDesdeLaUno.conReglas(negocio()));
            assertThat(fila(noPaga, 3).cobranzaCausada()).isEqualByComparingTo(fila(pagaTodo, 3).cobranzaCausada());
        }

        @Test
        void sinCobranzaActivaElCocienteNoCobra() {
            List<Movimiento> m = motor.ejecutar(sinPagarDesdeLaUno.conReglas(negocio()).conCobranzaActiva(false));
            assertThat(m).isNotEmpty().allSatisfy(x -> assertThat(x.cobranzaCausada()).isZero());
        }

        @Test
        void sinCobroFijoSonLosDiasNominalesPorElValorDiario() {
            List<Movimiento> m = motor.ejecutar(sinPagarDesdeLaUno.conReglas(negocio().conCobranzaPorPeriodo(null)));
            assertThat(fila(m, 3).cobranzaCausada()).isEqualByComparingTo(Calc.multiplicar(bd(7), P.cobranzaDiaria()));
        }
    }

    /** (4): en dación o retoma se suspenden por completo los servicios. */
    @Nested
    class RecuperacionSuspendeLosServicios {

        @Test
        void elPeriodoDeLaDacionOLaRetomaNoCausaServicios() {
            for (Escenario e : List.of(EscenariosExcel.caso11(P, 2), EscenariosExcel.caso3(P, false), EscenariosExcel.caso4(P))) {
                List<Movimiento> m = motor.ejecutar(e.conReglas(negocio()));
                Movimiento cierre = m.getLast();
                assertThat(cierre.estado()).isEqualTo(EstadoCredito.CERRADO_POR_EVENTO);
                assertThat(cierre.serviciosCausados().total()).as("periodo %d", cierre.periodo()).isZero();
                assertThat(cierre.interesMora()).as("la mora del periodo sí se causa").isPositive();
                assertThat(fila(m, cierre.periodo() - 1).serviciosCausados().fondoGarantias()).isPositive();
                // Con la ronda 9 el periodo del cierre causaba al menos los seguros.
                List<Movimiento> r9 = motor.ejecutar(e.conReglas(ReglasNegocioConfiguradaTest.negocioRonda9()));
                assertThat(r9.getLast().serviciosCausados().seguro()).isPositive();
            }
        }

        @Test
        void losServiciosQueCubreLaMotoSonRecaudoEfectivoParaTerceros() {
            List<Movimiento> m = motor.ejecutar(EscenariosExcel.caso11(P, 2).conReglas(negocio()));
            Movimiento cierre = m.getLast();
            BigDecimal cubiertos = Calc.restar(cierre.pagosServicios().total(), cierre.pagoServicios());
            assertThat(cubiertos).as("la moto (3.000.000) cubre los servicios pendientes").isPositive();
            List<Asiento> asientos = Contabilizador.para(negocio()).contabilizar(cierre);
            BigDecimal aTerceros = creditos(asientos, Cuenta.INGRESOS_RECIBIDOS_PARA_TERCEROS)
                    .add(creditos(asientos, Cuenta.CXP_SEGUROS)).add(creditos(asientos, Cuenta.IVA_SEGUROS_POR_PAGAR));
            assertThat(aTerceros).isCloseTo(cubiertos, within(bd("0.01")));
            // Lo cubierto ya no se extingue contra las cuentas por cobrar de cada servicio.
            BigDecimal cxc = creditos(asientos, Cuenta.CXC_FONDO_CLIENTE).add(creditos(asientos, Cuenta.CXC_ASISTENCIAS))
                    .add(creditos(asientos, Cuenta.CXC_SEGUROS));
            assertThat(cxc).isCloseTo(Calc.restar(cierre.extinguido().servicios().total(), cubiertos), within(bd("0.01")));
            // El Excel conserva la extinción de su panel (la totalidad contra las CxC).
            List<Asiento> excel = Contabilizador.estandar().contabilizar(motor.ejecutar(EscenariosExcel.caso11(P)).getLast());
            assertThat(creditos(excel, Cuenta.INGRESOS_RECIBIDOS_PARA_TERCEROS)).isZero();
        }
    }

    /**
     * R10-PERIODO-ATRASADO (Diego 2026-10-31): el capital no cubierto queda vencido, se suma a la cuota siguiente y genera
     * mora. Es lo que ya hacía el motor; queda probado.
     */
    @Test
    void elCapitalNoCubiertoQuedaVencidoSeSumaALaCuotaSiguienteYGeneraMora() {
        List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().en(5, new PagoParcial(new BigDecimal("0.4"), 0))
                .en(6, new PagoAlDia()).conReglas(negocio()));
        Movimiento cinco = fila(m, 5);
        Movimiento seis = fila(m, 6);
        assertThat(cinco.capitalVencido()).isPositive();
        assertThat(seis.interesMora()).isCloseTo(Calc.multiplicar(cinco.capitalVencido(), seis.tasaPeriodica()),
                within(bd("0.000001")));
        BigDecimal cuotaTotalSeis = Calc.sumar(seis.cuotaFinanciera(), seis.serviciosCausados().total());
        BigDecimal pendienteCinco = Calc.sumar(cinco.capitalVencido(), cinco.cxcInteresCorriente(), cinco.cxcServicios(),
                cinco.cxcMora(), cinco.cxcCobranza());
        assertThat(seis.pagoRecibido()).isCloseTo(Calc.sumar(cuotaTotalSeis, pendienteCinco, seis.interesMora()), within(bd("0.01")));
        assertThat(seis.estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
    }

    private static BigDecimal creditos(List<Asiento> asientos, Cuenta cuenta) {
        return asientos.stream().flatMap(a -> a.lineas().stream()).filter(l -> l.cuenta() == cuenta)
                .map(Asiento.Linea::credito).reduce(Calc.CERO, Calc::sumar);
    }
}
