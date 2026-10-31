package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Ronda 9 de Diego (2026-10-30): momento del primer cobro de cobranza (R9-COBRO-MOMENTO, lectura A), seguro todo
 * riesgo nuevo que se suma al de vida (R9-SEGURO-TODO-RIESGO) y servicios suspendidos que se pierden al ponerse al día
 * (R9-SERVICIOS-AL-PONERSE-AL-DIA).
 *
 * <p>Cifras a mano con la operación del Excel ({@link Operacion#ejemploExcel()}): monto financiado = 5.100.000 + 171.100 +
 * 373.300 + 120.000 + 80.000 + 60.000 = <b>5.904.400</b>; semanal: 52 / 12 = 4,3333… periodos por mes.
 * <ul>
 *   <li>Fondo por cuota = 5.904.400 × 3 % / 4,3333 × 1,19 = 40.876,6154 × 1,19 = <b>48.643,1723</b>.</li>
 *   <li>Asistencia por cuota = 40.000 / 4,3333 × 1,19 = 9.230,7692 × 1,19 = <b>10.984,6154</b>.</li>
 *   <li>Seguro de vida por cuota = 5.904.400 × 0,25 % / 4,3333 = 14.761 / 4,3333 = <b>3.406,3846</b>.</li>
 *   <li>Seguro todo riesgo por cuota = 5.904.400 × 0,3 % / 4,3333 = 17.713,2 / 4,3333 = <b>4.087,6615</b>; su IVA =
 *       4.087,6615 × 19 % = <b>776,6557</b>; al cliente 4.864,3172 por semana.</li>
 *   <li>Mensual: 17.713,20 + IVA 3.365,508 = 21.078,708; quincenal: 8.856,60 + IVA 1.682,754 = 10.539,354.</li>
 * </ul>
 */
class RespuestasDiegoRonda9Test {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final BigDecimal CUARENTA_MIL = bd(40_000);
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.0001");
    private static final BigDecimal FONDO = new BigDecimal("48643.1723");
    private static final BigDecimal ASISTENCIA = new BigDecimal("10984.6154");
    private static final BigDecimal SEGURO_VIDA = new BigDecimal("3406.3846");
    private static final BigDecimal TODO_RIESGO = new BigDecimal("4087.6615");
    private static final BigDecimal IVA_TODO_RIESGO = new BigDecimal("776.6557");
    private final MotorCartera motor = new MotorCartera();

    private static ReglasNegocio negocio() {
        try {
            return ReglasNegocio.desde(ReglasNegocioConfiguradaTest.configuracion());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Movimiento fila(List<Movimiento> m, int periodo) {
        return m.stream().filter(x -> x.periodo() == periodo).findFirst().orElseThrow();
    }

    private static Escenario conOperacionDelExcel() {
        return new Escenario(Operacion.ejemploExcel(), P);
    }

    @Test
    void lasClavesDeLaRondaNueveEstanEnNegocioYNoEnElExcel() {
        ReglasNegocio n = negocio();
        assertThat(n.umbralesCuentanCuotaEnCurso()).isTrue();
        assertThat(n.seguroTodoRiesgoPorcentajeMensual()).isEqualByComparingTo("0.003");
        assertThat(ReglasNegocio.EXCEL.umbralesCuentanCuotaEnCurso()).isFalse();
        assertThat(ReglasNegocio.EXCEL.porcentajeTodoRiesgo()).isZero();
        ReglasNegocio leidas = ReglasNegocio.desdeEstricto(Map.of("umbralesCuentanCuotaEnCurso", "true",
                "seguroTodoRiesgoPorcentajeMensual", "0.003"));
        assertThat(leidas.umbralesCuentanCuotaEnCurso()).isTrue();
        assertThat(leidas.porcentajeTodoRiesgo()).isEqualByComparingTo("0.003");
        assertThat(ReglasNegocio.EXCEL.conSeguroTodoRiesgoPorcentajeMensual(null).porcentajeTodoRiesgo()).isZero();
        assertThatThrownBy(() -> ReglasNegocio.EXCEL.conSeguroTodoRiesgoPorcentajeMensual(bd("-0.001")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * R9-COBRO-MOMENTO = segunda-cuota (lectura A). El cliente deja de pagar desde la semana 1: al terminar la semana 2
     * tiene 2 cuotas atrasadas, se cobran 40.000 y se suspenden fondo y asistencia; en la semana 3 otros 40.000 (80.000
     * en total), y así cada semana. Lectura B (NEGOCIO de la ronda 8): el primer cobro llegaba en la 3.ª cuota atrasada.
     */
    @Nested
    class CobroDesdeLaSegundaCuotaAtrasada {

        private final Escenario sinPagarDesdeLaUno = conOperacionDelExcel().entre(1, 3, new SinPago(true));

        @Test
        void elEjemploDeDiegoCobraEnLaSegundaYEnLaTerceraSemana() {
            List<Movimiento> m = motor.ejecutar(sinPagarDesdeLaUno.conReglas(negocio()));
            // Semana 1: 1 cuota atrasada, ni cobranza ni suspensión.
            assertThat(fila(m, 1).cobranzaCausada()).isZero();
            assertThat(fila(m, 1).serviciosCausados().fondoGarantias()).isCloseTo(FONDO, within(TOLERANCIA));
            // Semana 2: la 2.ª cuota atrasada cobra 40.000 y suspende fondo y asistencia en ese mismo periodo.
            Movimiento dos = fila(m, 2);
            assertThat(dos.cobranzaCausada()).isEqualByComparingTo(CUARENTA_MIL);
            assertThat(dos.serviciosCausados().fondoGarantias()).isZero();
            assertThat(dos.serviciosCausados().asistencia()).isZero();
            assertThat(dos.serviciosCausados().seguro()).isCloseTo(SEGURO_VIDA, within(TOLERANCIA));
            assertThat(dos.cuotasVencidas()).isEqualTo(2);
            // Semana 3: otros 40.000; la CxC de cobranza suma 80.000.
            Movimiento tres = fila(m, 3);
            assertThat(tres.cobranzaCausada()).isEqualByComparingTo(CUARENTA_MIL);
            assertThat(tres.cxcCobranza()).isEqualByComparingTo(bd(80_000));
        }

        @Test
        void conLaLecturaBElPrimerCobroEsEnLaTerceraCuotaAtrasada() {
            List<Movimiento> m = motor.ejecutar(sinPagarDesdeLaUno.conReglas(negocio().conUmbralesCuentanCuotaEnCurso(false)));
            assertThat(fila(m, 2).cobranzaCausada()).isZero();
            assertThat(fila(m, 2).serviciosCausados().fondoGarantias()).isCloseTo(FONDO, within(TOLERANCIA));
            assertThat(fila(m, 3).cobranzaCausada()).isEqualByComparingTo(CUARENTA_MIL);
            assertThat(fila(m, 3).cxcCobranza()).isEqualByComparingTo(CUARENTA_MIL);
            assertThat(fila(m, 3).serviciosCausados().fondoGarantias()).isZero();
        }

        @Test
        void unPagoParcialOUnValorMenorQueLaCuotaTambienEsCuotaAtrasada() {
            for (Instruccion corto : List.of(new PagoParcial(new BigDecimal("0.4"), 0), new PagoValor(bd(1_000), 0))) {
                Movimiento dos = fila(motor.ejecutar(conOperacionDelExcel().en(1, new SinPago(true)).en(2, corto)
                        .conReglas(negocio())), 2);
                assertThat(dos.cobranzaCausada()).as("%s", corto).isEqualByComparingTo(CUARENTA_MIL);
                assertThat(dos.serviciosCausados().fondoGarantias()).as("%s", corto).isZero();
            }
        }

        @Test
        void unPeriodoQueCubreLaCuotaNoCuentaComoAtrasado() {
            // Con 1 cuota atrasada previa, pagar la cuota del periodo (contractual o un valor que la cubre) no llega a 2.
            BigDecimal cuotaTotal = bd(1_000_000);
            for (Instruccion cubre : List.of(new PagoContractual(0), new PagoValor(cuotaTotal, 0), new PagoAlDia())) {
                Movimiento dos = fila(motor.ejecutar(conOperacionDelExcel().en(1, new SinPago(true)).en(2, cubre)
                        .conReglas(negocio())), 2);
                assertThat(dos.cobranzaCausada()).as("%s", cubre).isZero();
                assertThat(dos.serviciosCausados().fondoGarantias()).as("%s", cubre).isCloseTo(FONDO, within(TOLERANCIA));
            }
        }

        @Test
        void unPagoQueSoloCubreLaCuotaConServiciosSuspendidosNoAdelantaElCobro() {
            // Zona sin lectura coherente (ADR 0005): el valor cubre cuota + seguros (la cuota con fondo y asistencia
            // suspendidos) pero no la cuota completa. No se adelanta ni el cobro ni la suspensión; el contador sí la
            // cuenta como atrasada y la 3.ª cuota atrasada cobra como en la ronda 8.
            BigDecimal cuota = fila(motor.ejecutar(conOperacionDelExcel().conReglas(negocio())), 2).cuotaFinanciera();
            BigDecimal valor = Calc.sumar(cuota, SEGURO_VIDA, TODO_RIESGO, IVA_TODO_RIESGO, bd(1_000));
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().en(1, new SinPago(true))
                    .en(2, new PagoValor(valor, 0)).en(3, new SinPago(true)).conReglas(negocio()));
            assertThat(fila(m, 2).cobranzaCausada()).isZero();
            assertThat(fila(m, 2).serviciosCausados().fondoGarantias()).isCloseTo(FONDO, within(TOLERANCIA));
            assertThat(fila(m, 2).cuotasVencidas()).isEqualTo(2);
            assertThat(fila(m, 3).cobranzaCausada()).isEqualByComparingTo(CUARENTA_MIL);
            assertThat(fila(m, 3).serviciosCausados().fondoGarantias()).isZero();
        }

        @Test
        void sinUmbralesLaClaveNoCambiaNadaDelExcel() {
            for (Escenario e : List.of(EscenariosExcel.caso8(P), EscenariosExcel.caso9(P), EscenariosExcel.caso11(P),
                    EscenariosExcel.caso12(P))) {
                List<Movimiento> excel = motor.ejecutar(e.conReglas(ReglasNegocio.EXCEL));
                List<Movimiento> conClave = motor.ejecutar(e.conReglas(ReglasNegocio.EXCEL.conUmbralesCuentanCuotaEnCurso(true)));
                assertThat(conClave).isEqualTo(excel);
            }
        }
    }

    /**
     * R9-SEGURO-TODO-RIESGO = seguro-nuevo-se-suma: 0,3 % mensual sobre el monto financiado inicial, periodizado como el
     * seguro de vida, con IVA del 19 % encima y segmentado en la contabilidad.
     */
    @Nested
    class SeguroTodoRiesgo {

        @Test
        void elEjemploDeDiegoDiezMilMasIvaSonOnceMilNovecientos() {
            // 5.000.000 × 0,2 % = 10.000 al mes; con IVA del 19 %, 11.900 al cliente (en mensual, la cuota lleva el mes).
            ParametrosProducto mensual = ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.MENSUAL, 24);
            Servicios s = Servicios.porCuota(bd(5_000_000), mensual, new BigDecimal("0.002"));
            assertThat(s.seguroTodoRiesgo()).isEqualByComparingTo(bd(10_000));
            assertThat(s.ivaSeguroTodoRiesgo()).isEqualByComparingTo(bd(1_900));
            assertThat(s.todoRiesgoConIva()).isEqualByComparingTo(bd(11_900));
        }

        @Test
        void seSumaAlSeguroDeVidaYSePeriodizaIgual() {
            BigDecimal monto = Operacion.ejemploExcel().montoFinanciado();
            assertThat(monto).isEqualByComparingTo(bd(5_904_400));
            BigDecimal tr = new BigDecimal("0.003");
            Servicios semanal = Servicios.porCuota(monto, P, tr);
            assertThat(semanal.seguro()).isCloseTo(SEGURO_VIDA, within(TOLERANCIA));
            assertThat(semanal.seguroTodoRiesgo()).isCloseTo(TODO_RIESGO, within(TOLERANCIA));
            assertThat(semanal.ivaSeguroTodoRiesgo()).isCloseTo(IVA_TODO_RIESGO, within(TOLERANCIA));
            // El resto de los servicios no cambia y el total suma el seguro todo riesgo con su IVA.
            Servicios sin = Servicios.porCuota(monto, P);
            assertThat(Calc.restar(semanal.total(), sin.total())).isCloseTo(new BigDecimal("4864.3172"), within(TOLERANCIA));
            assertThat(Servicios.porCuota(monto, ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.MENSUAL, 24), tr)
                    .todoRiesgoConIva()).isCloseTo(new BigDecimal("21078.708"), within(TOLERANCIA));
            assertThat(Servicios.porCuota(monto, ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.QUINCENAL, 48), tr)
                    .todoRiesgoConIva()).isCloseTo(new BigDecimal("10539.354"), within(TOLERANCIA));
        }

        @Test
        void negocioLoCausaEnCadaCuotaYElExcelNo() {
            Movimiento negocio = fila(motor.ejecutar(conOperacionDelExcel().conReglas(negocio())), 1);
            assertThat(negocio.serviciosCausados().seguroTodoRiesgo()).isCloseTo(TODO_RIESGO, within(TOLERANCIA));
            assertThat(negocio.serviciosCausados().ivaSeguroTodoRiesgo()).isCloseTo(IVA_TODO_RIESGO, within(TOLERANCIA));
            assertThat(negocio.pagosServicios().seguroTodoRiesgo()).isCloseTo(TODO_RIESGO, within(TOLERANCIA));
            assertThat(motor.ejecutar(EscenariosExcel.caso1(P)))
                    .allMatch(m -> m.serviciosCausados().todoRiesgoConIva().signum() == 0);
        }

        @Test
        void sigueCausandoseConElSeguroDeVidaTrasLaSuspension() {
            // Decisión provisional (pregunta de la ronda 11): Diego dijo "solo se causa el seguro" sin precisar cuál.
            Movimiento suspendido = fila(motor.ejecutar(conOperacionDelExcel().entre(1, 3, new SinPago(true))
                    .conReglas(negocio())), 3);
            assertThat(suspendido.serviciosCausados().fondoGarantias()).isZero();
            assertThat(suspendido.serviciosCausados().seguro()).isCloseTo(SEGURO_VIDA, within(TOLERANCIA));
            assertThat(suspendido.serviciosCausados().seguroTodoRiesgo()).isCloseTo(TODO_RIESGO, within(TOLERANCIA));
            assertThat(suspendido.serviciosCausados().ivaSeguroTodoRiesgo()).isCloseTo(IVA_TODO_RIESGO, within(TOLERANCIA));
        }

        @Test
        void vaDespuesDelSeguroDeVidaEnLaPrelacionYPrimeroElSeguroQueSuIva() {
            Servicios porCobrar = new Servicios(bd(100), bd(50), bd(30), bd(20), new BigDecimal("3.8"));
            assertThat(porCobrar.pagarHasta(bd(190))).isEqualTo(new Servicios(bd(100), bd(50), bd(30), bd(10), bd(0)));
            assertThat(porCobrar.pagarHasta(bd(202))).isEqualTo(new Servicios(bd(100), bd(50), bd(30), bd(20), bd(2)));
            assertThat(porCobrar.descontarDe(bd(250))).isEqualByComparingTo("46.2");
        }

        @Test
        void laContabilidadSegmentaElSeguroYElIva() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().conReglas(negocio()));
            Asiento cuota = Contabilizador.para(negocio()).contabilizar(fila(m, 1)).getFirst();
            assertThat(cuota.cuadra()).isTrue();
            assertThat(cuota.lineas()).filteredOn(l -> l.cuenta() == Cuenta.IVA_SEGUROS_POR_PAGAR).singleElement()
                    .satisfies(l -> assertThat(l.credito()).isCloseTo(IVA_TODO_RIESGO, within(TOLERANCIA)));
            assertThat(cuota.lineas()).filteredOn(l -> l.cuenta() == Cuenta.CXP_SEGUROS).singleElement()
                    .satisfies(l -> assertThat(l.credito()).isCloseTo(TODO_RIESGO, within(TOLERANCIA)));
            // Fondo + asistencia + seguro de vida siguen en su línea de siempre.
            assertThat(cuota.lineas()).filteredOn(l -> l.cuenta() == Cuenta.CXP_SERVICIOS_TERCEROS
                    && !l.explicacion().contains("todo riesgo")).singleElement()
                    .satisfies(l -> assertThat(l.credito()).isCloseTo(Calc.sumar(FONDO, ASISTENCIA, SEGURO_VIDA),
                            within(TOLERANCIA)));
            // Con el Excel no hay línea de IVA en ningún caso.
            for (Escenario e : List.of(EscenariosExcel.caso1(P), EscenariosExcel.caso3(P, true), EscenariosExcel.caso8(P),
                    EscenariosExcel.caso9(P))) {
                assertThat(Contabilizador.estandar().contabilizar(motor.ejecutar(e)))
                        .flatMap(Asiento::lineas).noneMatch(l -> l.cuenta() == Cuenta.IVA_SEGUROS_POR_PAGAR);
            }
        }

        @Test
        void laInstantaneaConservaElSeguroTodoRiesgoPendiente() {
            Escenario e = conOperacionDelExcel().entre(3, 4, new SinPago(true)).conReglas(negocio());
            Instantanea i = motor.ejecutarHasta(e, 4, x -> { });
            assertThat(i.cxcServicios().seguroTodoRiesgo()).isPositive();
            assertThat(FormatoInstantanea.leer(FormatoInstantanea.escribir(i))).isEqualTo(i);
        }
    }

    /**
     * R9-SERVICIOS-AL-PONERSE-AL-DIA = se-pierden: el fondo y la asistencia de los periodos suspendidos no se cobran al
     * ponerse al día; se retoman desde el periodo siguiente.
     *
     * <p>Escenario: paga las semanas 1 y 2, no paga de la 3 a la 7 y se pone al día en la 8. Con la lectura A la semana
     * 3 es la 1.ª cuota atrasada (causa los tres servicios), de la 4 a la 7 hay 4 periodos suspendidos (solo seguros) y la
     * semana 8, en la que se pone al día, todavía tiene cuotas atrasadas previas y también solo causa seguros. Lo que paga
     * en la 8 por servicios es el fondo y la asistencia de UNA semana (la 3) más 6 semanas de seguros (3 a 8):
     * 48.643,1723 + 10.984,6154 + 6 × (3.406,3846 + 4.087,6615 + 776,6557) = 59.627,7877 + 49.624,2108 = 109.251,9985
     * (109.251,9988 con todos los decimales).
     * La cobranza son 4 × 40.000 = 160.000 (semanas 4 a 7). Desde la semana 9 vuelven los tres servicios.
     */
    @Nested
    class ServiciosSuspendidosSePierden {

        private final Escenario seAtrasaYSePoneAlDia = conOperacionDelExcel().entre(3, 7, new SinPago(true))
                .en(8, new PagoAlDia());

        @Test
        void alPonerseAlDiaNoSeCobraElFondoNiLaAsistenciaDeLosPeriodosSuspendidos() {
            List<Movimiento> m = motor.ejecutar(seAtrasaYSePoneAlDia.conReglas(negocio()));
            for (int periodo = 4; periodo <= 8; periodo++) {
                assertThat(fila(m, periodo).serviciosCausados().fondoGarantias()).as("periodo %d", periodo).isZero();
                assertThat(fila(m, periodo).serviciosCausados().asistencia()).as("periodo %d", periodo).isZero();
            }
            Movimiento ocho = fila(m, 8);
            assertThat(ocho.pagosServicios().fondoGarantias()).isCloseTo(FONDO, within(TOLERANCIA));
            assertThat(ocho.pagosServicios().asistencia()).isCloseTo(ASISTENCIA, within(TOLERANCIA));
            assertThat(ocho.pagoServicios()).isCloseTo(new BigDecimal("109251.9988"), within(TOLERANCIA));
            assertThat(ocho.pagoCobranza()).isEqualByComparingTo(bd(160_000));
            assertThat(ocho.cxcServicios()).isZero();
            assertThat(ocho.cuotasVencidas()).isZero();
            // Se retoman desde el periodo siguiente, sin nada retroactivo.
            Movimiento nueve = fila(m, 9);
            assertThat(nueve.serviciosCausados().fondoGarantias()).isCloseTo(FONDO, within(TOLERANCIA));
            assertThat(nueve.serviciosCausados().asistencia()).isCloseTo(ASISTENCIA, within(TOLERANCIA));
            assertThat(nueve.pagosServicios().total()).isCloseTo(nueve.serviciosCausados().total(), within(TOLERANCIA));
            // En las 9 semanas solo se causó el fondo de 4 (1, 2, 3 y 9).
            BigDecimal fondoTotal = Calc.sumar(m.stream().filter(x -> x.periodo() >= 1 && x.periodo() <= 9)
                    .map(x -> x.serviciosCausados().fondoGarantias()).toArray(BigDecimal[]::new));
            assertThat(fondoTotal).isCloseTo(Calc.multiplicar(FONDO, bd(4)), within(TOLERANCIA));
        }

        @Test
        void unAbonoQuePagaTodoTampocoReviveLosServiciosSuspendidos() {
            // R4-CASO13: el excedente del abono paga lo no causado del periodo, pero lo suspendido no es "no causado".
            Escenario conAbono = conOperacionDelExcel().entre(3, 7, new SinPago(true))
                    .en(8, new PagoContractual(0)).en(8, new AbonoExtra(bd(1_000_000), Instruccion.ModalidadAbono.MENOR_PLAZO));
            Movimiento ocho = fila(motor.ejecutar(conAbono.conReglas(negocio())), 8);
            assertThat(ocho.serviciosCausados().fondoGarantias()).isZero();
            assertThat(ocho.cxcServicios()).isZero();
            assertThat(Calc.sumar(ocho.pagosServicios().fondoGarantias(),
                    ocho.pendientesPagadosExtra().servicios().fondoGarantias())).isCloseTo(FONDO, within(TOLERANCIA));
        }
    }
}
