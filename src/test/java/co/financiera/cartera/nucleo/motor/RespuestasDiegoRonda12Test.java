package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.TipoRecuperacion;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.AlcanceProrrateo;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.BaseMoraCobranza;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.ComparacionCobranza;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Ronda 12 de Diego (2026-11-01): una prueba (o invariante) por decisión, D74 a D79 (ver
 * {@code docs/respuestas-diego-2026-11-01-ronda12.md}). Lo que no tiene código (D80 a D83: valor exigible, servicios no
 * recaudados al cierre, justificaciones y valor de la moto reentregada) queda documentado y en la ronda 13.
 */
class RespuestasDiegoRonda12Test {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final BigDecimal CUARENTA_MIL = bd(40_000);
    private static final BigDecimal CENTAVO = new BigDecimal("0.01");
    private final MotorCartera motor = new MotorCartera();

    private static ReglasNegocio negocio() {
        return ReglasNegocioConfiguradaTest.negocioAntesRonda15();   // cifras de la ronda, antes de D88 y D89
    }

    private static Movimiento fila(List<Movimiento> m, int periodo) {
        return m.stream().filter(x -> x.periodo() == periodo).findFirst().orElseThrow();
    }

    private static Escenario conOperacionDelExcel() {
        return new Escenario(Operacion.ejemploExcel(), P);
    }

    /** D74 (R12-COBRANZA-SALDO-MORA): la totalidad de lo pendiente y desde IGUAL o MAYOR a 2. */
    @Nested
    class CobranzaDesdeDos {

        @Test
        void negocioCuentaLaTotalidadDeLoPendienteYCobraDesdeDosCuotas() {
            ReglasNegocio n = negocio();
            assertThat(n.baseMoraCobranza()).isEqualTo(BaseMoraCobranza.TOTAL_PENDIENTE);
            assertThat(n.comparacionCobranza()).isEqualTo(ComparacionCobranza.MAYOR_O_IGUAL);
            // Exactamente 2 cuotas (con la tolerancia de medio centavo) ya llegan al umbral; un poco menos, no.
            assertThat(n.llegaAlUmbralDeCobranza(bd(400), bd(200))).isTrue();
            assertThat(n.llegaAlUmbralDeCobranza(new BigDecimal("399.996"), bd(200))).isTrue();
            assertThat(n.llegaAlUmbralDeCobranza(new BigDecimal("399.99"), bd(200))).isFalse();
            // El Excel no tiene cociente.
            assertThat(ReglasNegocio.EXCEL.cobranzaPorCociente()).isFalse();
        }

        @Test
        void elEjemploDeDiegoSigueIgualSemanaTresYCuatroCobran40Mil() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 4, new SinPago(true)).conReglas(negocio()));
            assertThat(fila(m, 2).cobranzaCausada()).isZero();
            assertThat(fila(m, 3).cobranzaCausada()).isEqualByComparingTo(CUARENTA_MIL);
            assertThat(fila(m, 4).cobranzaCausada()).isEqualByComparingTo(CUARENTA_MIL);
        }

        @Test
        void conDosCuotasExactasSinMoraEnElNumeradorAhoraCobra() {
            // Con solo lo que fue cuota en el numerador, al iniciar la semana 3 se deben 2 cuotas exactas (al peso).
            ReglasNegocio soloCuotas = negocio().conBaseMoraCobranza(BaseMoraCobranza.CUOTAS_VENCIDAS);
            Escenario e = conOperacionDelExcel().entre(1, 4, new SinPago(true));
            assertThat(fila(motor.ejecutar(e.conReglas(soloCuotas)), 3).cobranzaCausada()).isEqualByComparingTo(CUARENTA_MIL);
            assertThat(fila(motor.ejecutar(e.conReglas(soloCuotas.conComparacionCobranza(ComparacionCobranza.MAYOR))), 3)
                    .cobranzaCausada()).isZero();
        }
    }

    /** D75 (R12-COBRANZA-DENOMINADOR, R12-COBRANZA-PERIODICIDADES, R12-GIRO-TERCEROS): se confirma lo que ya hacía. */
    @Nested
    class Confirmadas {

        @Test
        void elDenominadorEsLaCuotaVigenteTrasUnAbonoAMenorCuota() {
            // Con un abono a menor cuota en la 10 la cuota baja: la misma racha sin pago llega antes a 2 cuotas vigentes.
            Escenario conAbono = conOperacionDelExcel().en(10, new AbonoExtra(bd(3_000_000), ModalidadAbono.MENOR_CUOTA))
                    .entre(11, 14, new SinPago(true));
            List<Movimiento> m = motor.ejecutar(conAbono.conReglas(negocio()));
            Movimiento once = fila(m, 11);
            assertThat(once.cuotaFinanciera()).as("la cuota vigente bajó").isLessThan(fila(m, 10).cuotaFinanciera());
            int primeraCobranza = m.stream().filter(x -> x.cobranzaCausada().signum() > 0).mapToInt(Movimiento::periodo)
                    .min().orElseThrow();
            assertThat(primeraCobranza).isEqualTo(13);
            // El cociente se compara con la cuota vigente (la nueva) más los servicios por cuota.
            Servicios porCuota = Servicios.porCuota(Operacion.ejemploExcel().montoFinanciado(), P, negocio().porcentajeTodoRiesgo());
            BigDecimal cuotaTotal = Calc.sumar(once.cuotaFinanciera(), porCuota.total());
            Movimiento doce = fila(m, 12);
            BigDecimal saldoEnMora = Calc.sumar(doce.capitalVencido(), doce.cxcInteresCorriente(), doce.cxcServicios(),
                    doce.cxcMora(), doce.cxcCobranza());
            assertThat(saldoEnMora).isGreaterThanOrEqualTo(Calc.multiplicar(bd(2), Calc.restar(cuotaTotal, BigDecimal.ONE)));
        }

        @Test
        void elUmbralYElValorSonIgualesEnLasTresPeriodicidades() {
            for (Periodicidad per : Periodicidad.values()) {
                ParametrosProducto p = ParametrosProducto.de(LineaCredito.CPPU, per, per.cuotasEnMeses(24));
                List<Movimiento> m = motor.ejecutar(new Escenario(Operacion.ejemploExcel(), p).entre(1, 4, new SinPago(true))
                        .conReglas(negocio()));
                assertThat(fila(m, 2).cobranzaCausada()).as("%s periodo 2", per).isZero();
                assertThat(fila(m, 3).cobranzaCausada()).as("%s periodo 3", per).isEqualByComparingTo(CUARENTA_MIL);
            }
        }

        @Test
        void elGiroATercerosEsMensualAlCerrarCadaMes() {
            List<Movimiento> m = motor.ejecutar(EscenariosExcel.caso1(P).conReglas(negocio()));
            List<Movimiento> conGiro = m.stream().filter(x -> x.pagoTerceros().total().signum() > 0).toList();
            assertThat(conGiro).isNotEmpty().allSatisfy(x -> assertThat(Calendario.mes(x.periodo() + 1, P) > Calendario.mes(x.periodo(), P)
                    || x.estado() == EstadoCredito.CANCELADO).as("periodo %d", x.periodo()).isTrue());
            BigDecimal recaudado = m.stream().map(Movimiento::pagoServicios).reduce(Calc.CERO, Calc::sumar);
            BigDecimal girado = m.stream().map(x -> x.pagoTerceros().total()).reduce(Calc.CERO, Calc::sumar);
            assertThat(girado).isCloseTo(recaudado, within(CENTAVO));
        }
    }

    /** D76 (R12-SERVICIOS-DESPUES-PLAZO): después de la última cuota, mientras haya saldo, los servicios siguen. */
    @Nested
    class ServiciosDespuesDelPlazo {

        private Escenario atrasadoAlFinal() {
            // Sin pago en las 3 últimas cuotas y 6 periodos más de plazo límite para seguir debiendo.
            ParametrosProducto p = P.conPlazoLimite(110);
            return new Escenario(Operacion.ejemploExcel(), p).desde(102, new SinPago(true));
        }

        @Test
        void negocioSigueCausandoLosServiciosMientrasHayaSaldo() {
            assertThat(negocio().serviciosDespuesDelPlazo()).isTrue();
            assertThat(ReglasNegocio.EXCEL.serviciosDespuesDelPlazo()).isFalse();
            List<Movimiento> m = motor.ejecutar(atrasadoAlFinal().conReglas(negocio()));
            Servicios porCuota = Servicios.porCuota(Operacion.ejemploExcel().montoFinanciado(), P, negocio().porcentajeTodoRiesgo());
            for (int k = 105; k <= 110; k++) {
                Movimiento x = fila(m, k);
                assertThat(x.capitalContractual()).as("periodo %d", k).isZero();
                assertThat(x.saldoInicial()).as("periodo %d", k).isPositive();
                assertThat(x.serviciosCausados().total()).as("periodo %d", k).isCloseTo(porCuota.total(), within(CENTAVO));
            }
            // Solo dentro del plazo (como hasta la ronda 11): después de la 104 ya no se causan.
            List<Movimiento> antes = motor.ejecutar(atrasadoAlFinal().conReglas(negocio().conServiciosDespuesDelPlazo(false)));
            assertThat(fila(antes, 105).serviciosCausados().total()).isZero();
        }

        @Test
        void elPeriodoDeUnaDacionDespuesDelPlazoNoCausaServicios() {
            Escenario e = atrasadoAlFinal().en(107, new CierrePorRecuperacion(TipoRecuperacion.DACION_EN_PAGO, bd(3_000_000), bd(0)));
            List<Movimiento> m = motor.ejecutar(e.conReglas(negocio()));
            assertThat(fila(m, 106).serviciosCausados().total()).isPositive();
            assertThat(m.getLast().periodo()).isEqualTo(107);
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CERRADO_POR_EVENTO);
            assertThat(m.getLast().serviciosCausados().total()).isZero();
        }

        @Test
        void sinSaldoNoHayServiciosAunqueQuedenPendientes() {
            // Un crédito cancelado en la cuota 104 no sigue: el motor termina y no hay periodos después.
            List<Movimiento> m = motor.ejecutar(new Escenario(Operacion.ejemploExcel(), P.conPlazoLimite(110)).conReglas(negocio()));
            assertThat(m.getLast().periodo()).isEqualTo(104);
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
        }
    }

    /** D77 (R12-MARCA-POR-COCIENTE): la marca se mide con el mismo cociente de la cobranza y persiste (D67). */
    @Nested
    class MarcaPorCociente {

        @Test
        void negocioMarcaPorCocienteYElExcelNoMarca() {
            assertThat(negocio().marcaPorCociente()).isTrue();
            assertThat(negocio().marcaRecuperacionPorCociente()).isTrue();
            assertThat(ReglasNegocio.EXCEL.marcaPorCociente()).isFalse();
            // Sin umbral de cociente la clave no aplica: la marca sigue por eventos.
            assertThat(negocio().conUmbralCocienteCobranza(null).marcaRecuperacionPorCociente()).isFalse();
            assertThat(ReglasNegocio.desdeEstricto(Map.of("marcaPorCociente", "true")).marcaPorCociente()).isTrue();
        }

        @Test
        void laPrimeraMarcaLlegaUnPeriodoAntesQueLaPrimeraCobranza() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 4, new SinPago(true)).conReglas(negocio()));
            assertThat(fila(m, 1).listoParaRecuperacion()).isFalse();
            // Al cerrar la 2 debe 2 cuotas más la mora: queda marcado y la 3 cobra.
            assertThat(fila(m, 2).listoParaRecuperacion()).isTrue();
            assertThat(fila(m, 2).cobranzaCausada()).isZero();
            assertThat(fila(m, 3).cobranzaCausada()).isEqualByComparingTo(CUARENTA_MIL);
        }

        @Test
        void elCaso11YaNoSeMarcaPorqueNuncaDebeDosCuotas() {
            List<Movimiento> m = motor.ejecutar(EscenariosExcel.caso11(P, 2).conReglas(negocio()));
            assertThat(m).noneMatch(Movimiento::listoParaRecuperacion);
            // Por eventos (PPER-9) se marcaba en el 44: 2 periodos atrasados aunque deba ~1,6 cuotas.
            List<Movimiento> eventos = motor.ejecutar(EscenariosExcel.caso11(P, 2).conReglas(negocio().conMarcaPorCociente(false)));
            assertThat(fila(eventos, 44).listoParaRecuperacion()).isTrue();
        }

        @Test
        void laMarcaSeDesactivaAlBajarElCocienteYLaCobranzaSoloMientrasSigaEnDos() {
            // Sin pago en la 1, 2 y 3; en la 4 paga 600.000: el cociente baja de 2 y (R13-COBRANZA-TRAS-MARCA, D85) la marca se quita.
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 3, new SinPago(true))
                    .en(4, new PagoValor(bd(600_000), 0)).conReglas(negocio()));
            assertThat(fila(m, 2).listoParaRecuperacion()).isTrue();
            assertThat(fila(m, 4).estado()).isEqualTo(EstadoCredito.EN_MORA);
            for (int k = 4; k <= 8; k++) {
                assertThat(fila(m, k).listoParaRecuperacion()).as("periodo %d", k).isFalse();
            }
            // COB-4: la cobranza solo mientras haya mora de 2 cuotas o más (R13-COBRANZA-TRAS-MARCA, D85).
            assertThat(fila(m, 5).cobranzaCausada()).isZero();
            assertThat(fila(m, 6).cobranzaCausada()).isZero();
        }
    }

    /** D78 (R12-IVA-FONDO-ASISTENCIAS y R12-CUENTA-TERCEROS): el seguro riesgo total va en el gran total, sin segmentar. */
    @Nested
    class SeguroEnElGranTotal {

        @Test
        void yaNoExistenLasCuentasSegmentadasDelSeguro() {
            assertThat(Arrays.stream(Cuenta.values()).map(Enum::name))
                    .isNotEmpty()
                    .doesNotContain("CXP_SEGUROS", "IVA_SEGUROS_POR_PAGAR", "IVA_POR_PAGAR");
        }

        @Test
        void diezMilMasIvaSonOnceMilNovecientosEnUnaSolaLineaParaTerceros() {
            // El cliente sigue pagando 10.000 + 19 % = 11.900 (D63); la contabilidad lo lleva como un solo valor.
            Servicios soloRiesgoTotal = new Servicios(bd(0), bd(0), bd(0), bd(10_000), bd(1_900));
            assertThat(soloRiesgoTotal.todoRiesgoConIva()).isEqualByComparingTo(bd(11_900));
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().conReglas(negocio()));
            Movimiento uno = fila(m, 1);
            List<Asiento> asientos = Contabilizador.para(negocio()).contabilizar(uno);
            assertThat(asientos).isNotEmpty().allSatisfy(a -> assertThat(a.cuadra()).isTrue());
            List<Asiento.Linea> terceros = asientos.stream().flatMap(a -> a.lineas().stream())
                    .filter(l -> l.cuenta() == Cuenta.INGRESOS_RECIBIDOS_PARA_TERCEROS).toList();
            assertThat(terceros).singleElement().satisfies(l -> assertThat(l.credito())
                    .isCloseTo(uno.serviciosCausados().total(), within(CENTAVO)));
            assertThat(uno.serviciosCausados().todoRiesgoConIva()).isPositive();
        }
    }

    /** D79 (R10-PRIMERA-CUOTA + R12-PRIMERA-CUOTA-DETALLE): la primera cuota completa, proporcional a los días. */
    @Nested
    class PrimeraCuotaCompleta {

        /** Desembolso sábado 10-oct-2026 y primer martes 13-oct-2026: 3 días de 7. */
        private final CalendarioPagos tresDias = CalendarioPagos.semanal(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 13));

        private List<Movimiento> correr(ReglasNegocio reglas) {
            return motor.ejecutar(conOperacionDelExcel().conReglas(reglas).conCalendario(tresDias));
        }

        @Test
        void capitalYServiciosVanEnProporcionALosDiasYElInteresPorLosDiasReales() {
            assertThat(negocio().alcanceProrrateoPrimeraCuota()).isEqualTo(AlcanceProrrateo.CUOTA_COMPLETA_SIN_INTERES);
            assertThat(ReglasNegocio.EXCEL.alcanceProrrateoPrimeraCuota()).isEqualTo(AlcanceProrrateo.SOLO_INTERES);
            List<Movimiento> completa = correr(negocio());
            List<Movimiento> soloInteres = correr(negocio().conAlcanceProrrateoPrimeraCuota(AlcanceProrrateo.SOLO_INTERES));
            Movimiento uno = fila(completa, 1);
            Movimiento p5 = fila(soloInteres, 1);
            BigDecimal tresSeptimos = Calc.dividir(bd(3), bd(7));
            assertThat(uno.capitalContractual()).isCloseTo(Calc.multiplicar(p5.capitalContractual(), tresSeptimos), within(CENTAVO));
            assertThat(uno.serviciosCausados().total())
                    .isCloseTo(Calc.multiplicar(p5.serviciosCausados().total(), tresSeptimos), within(CENTAVO));
            // El interés de los 3 días es el mismo de P5 (hasta el residuo del redondeo al peso).
            assertThat(uno.interesCorriente()).isCloseTo(p5.interesCorriente(), within(BigDecimal.ONE));
            // Con la operación del Excel: 71.940 en la primera semana (antes 127.038 con solo el interés prorrateado).
            assertThat(uno.pagoRecibido()).isEqualByComparingTo(bd(71_940));
            assertThat(p5.pagoRecibido()).isEqualByComparingTo(bd(127_038));
            assertThat(uno.estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
        }

        @Test
        void elCapitalNoCobradoLoRecogeLaUltimaCuotaYElPlanCierraEnLaCuotaN() {
            List<Movimiento> m = correr(negocio());
            assertThat(m.getLast().periodo()).isEqualTo(104);
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
            BigDecimal capital = m.stream().map(Movimiento::capitalContractual).reduce(Calc.CERO, Calc::sumar);
            assertThat(capital).isCloseTo(Operacion.ejemploExcel().montoFinanciado(), within(CENTAVO));
            // La cuota fija no cambia entre la 2 y la 103; la 104 es mayor: recoge el saldo.
            BigDecimal cuotaFija = fila(m, 2).cuotaFinanciera();
            assertThat(fila(m, 103).cuotaFinanciera()).isEqualByComparingTo(cuotaFija);
            assertThat(fila(m, 104).cuotaFinanciera()).isGreaterThan(cuotaFija);
            assertThat(fila(m, 104).capitalContractual()).isCloseTo(fila(m, 104).saldoInicial(), within(CENTAVO));
        }

        @Test
        void losServiciosNoCobradosSePierden() {
            List<Movimiento> m = correr(negocio());
            Servicios porCuota = Servicios.porCuota(Operacion.ejemploExcel().montoFinanciado(), P, negocio().porcentajeTodoRiesgo());
            BigDecimal causados = m.stream().map(x -> x.serviciosCausados().total()).reduce(Calc.CERO, Calc::sumar);
            BigDecimal esperado = Calc.multiplicar(porCuota.total(), Calc.sumar(bd(103), Calc.dividir(bd(3), bd(7))));
            assertThat(causados).isCloseTo(esperado, within(CENTAVO));
            assertThat(fila(m, 1).cxcServicios()).isZero();
        }

        @Test
        void conMasDiasQueElPeriodoCapitalYServiciosVanCompletos() {
            // Con CUOTA_COMPLETA (D79, sin la ronda 13): 10 días de 7, solo el interés cuenta los días de más. NEGOCIO ya no lo
            // permite (R13-PRIMERA-CUOTA-LARGA, D84): ver RespuestasDiegoRonda13Test.
            ReglasNegocio d79 = negocio().conAlcanceProrrateoPrimeraCuota(AlcanceProrrateo.CUOTA_COMPLETA);
            CalendarioPagos diez = CalendarioPagos.semanal(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 13));
            Movimiento uno = fila(motor.ejecutar(conOperacionDelExcel().conReglas(d79).conCalendario(diez)), 1);
            Movimiento sinFechas = fila(motor.ejecutar(conOperacionDelExcel().conReglas(d79)), 1);
            assertThat(uno.capitalContractual()).isEqualByComparingTo(sinFechas.capitalContractual());
            assertThat(uno.serviciosCausados()).isEqualTo(sinFechas.serviciosCausados());
            assertThat(uno.interesCorriente()).isGreaterThan(sinFechas.interesCorriente());
        }
    }

    /** D83 (R10-VALOR-MOTO-REENTREGADA): el valor del crédito nuevo es un input del asesor; el motor no lo calcula. */
    @Test
    void elValorDeLaMotoReentregadaEsUnInputDelCreditoNuevo() {
        Operacion reentregada = new Operacion(bd(3_500_000), bd(171_100), bd(373_300), bd(120_000), bd(80_000), bd(60_000));
        assertThat(reentregada.valorMoto()).isEqualByComparingTo(bd(3_500_000));
        assertThat(reentregada.montoFinanciado()).isEqualByComparingTo(reentregada.valorTotal());
    }
}
