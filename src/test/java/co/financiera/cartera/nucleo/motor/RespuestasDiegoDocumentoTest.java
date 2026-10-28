package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.nucleo.Amortizacion;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Tasas;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadCambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.UsuraVigente;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Respuestas de Diego por documento Word ("rptas software", 2026-10-22) que el motor aplica: R4-T5, X1, COB-4,
 * COB-6, PPER-9 y X4. COB-5 no tiene código (requisito del módulo futuro de cobranza, ver docs/reglas/caso-9.md).
 */
class RespuestasDiegoDocumentoTest {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    private final MotorCartera motor = new MotorCartera();

    /** Las reglas del perfil NEGOCIO tal como están en config/reglas-negocio.properties. */
    /** El NEGOCIO de antes de R7-COBRANZA-ACTIVA: aísla COB-4 (cobranza solo en mora) del umbral de periodos. */
    private static ReglasNegocio sinUmbralDeCobranza() {
        return negocio().conCuotasVencidasParaCobranza(null);
    }

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

    /** Los 13 casos sobre la réplica del Excel (caso 3 como el Excel, con mora y servicios en las semanas sin pago). */
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
        f.put("caso 11", EscenariosExcel::caso11);
        f.put("caso 12", EscenariosExcel::caso12);
        f.put("caso 13", EscenariosExcel::caso13);
        Map<String, Escenario> e = new LinkedHashMap<>();
        f.forEach((nombre, escenario) -> e.put(nombre, escenario.apply(P)));
        return e;
    }

    /**
     * R4-T5. Fuente: respuesta de Diego 2026-10-22 (documento 'rptas software'), pregunta R4-T5. Si el cliente
     * está en mora cuando cambia la tasa, la cuota nueva se recalcula sobre el saldo capital vigente; lo vencido
     * se sigue cobrando aparte y no se liquidan intereses sobre intereses.
     */
    @Nested
    class T5CambioDeTasaConElClienteEnMora {

        private static final BigDecimal NUEVA_EA = bd("0.80");
        private static final int CAMBIO = 44;

        /** Tres semanas sin pago (41 a 43) y cambio de tasa en la 44, conservando la fecha final. */
        private Escenario enMora(ReglasNegocio reglas) {
            return new Escenario(Operacion.ejemploExcel(), P).entre(41, 43, new SinPago(true))
                    .en(CAMBIO, new CambioTasa(NUEVA_EA)).conReglas(reglas);
        }

        private BigDecimal pmt(BigDecimal base) {
            return Amortizacion.cuotaFija(Tasas.desdeEA(NUEVA_EA, P.periodicidad()).periodica(),
                    P.numeroCuotas() - CAMBIO + 1, base);
        }

        @Test
        void laCuotaNuevaSeCalculaSoloSobreElCapitalVigente() {
            ReglasNegocio diego = ReglasNegocio.EXCEL.conCambioTasaSobreCapitalVigente(true);
            List<Movimiento> m = motor.ejecutar(enMora(diego));
            Movimiento antes = fila(m, CAMBIO - 1);
            assertThat(antes.capitalVencido()).isPositive();
            assertThat(antes.cxcInteresCorriente().add(antes.cxcMora())).isPositive();
            BigDecimal vigente = Calc.restar(antes.saldoFinal(), antes.capitalVencido());
            assertThat(fila(m, CAMBIO).cuotaFinanciera()).isCloseTo(pmt(vigente), within(bd("0.000001")));
            // Antes de Diego (y en EXCEL) la base era el saldo total, con el vencido: la cuota salía mayor.
            List<Movimiento> excel = motor.ejecutar(enMora(ReglasNegocio.EXCEL));
            assertThat(fila(excel, CAMBIO).cuotaFinanciera()).isCloseTo(pmt(antes.saldoFinal()), within(bd("0.000001")));
            assertThat(fila(m, CAMBIO).cuotaFinanciera()).isLessThan(fila(excel, CAMBIO).cuotaFinanciera());
        }

        @Test
        void loVencidoSeSigueCobrandoAparteYNoSeCapitalizanIntereses() {
            List<Movimiento> m = motor.ejecutar(enMora(ReglasNegocio.EXCEL.conCambioTasaSobreCapitalVigente(true)));
            Movimiento cambio = fila(m, CAMBIO);
            // El vencido no entró a la cuota nueva: sigue como vencido y el crédito sigue en mora.
            assertThat(cambio.capitalVencido()).isPositive();
            assertThat(cambio.estado()).isEqualTo(EstadoCredito.EN_MORA);
            // El plan nuevo amortiza exactamente el capital vigente hasta la fecha final.
            BigDecimal vigente = Calc.restar(fila(m, CAMBIO - 1).saldoFinal(), fila(m, CAMBIO - 1).capitalVencido());
            BigDecimal contractual = m.stream().filter(x -> x.periodo() >= CAMBIO && x.periodo() <= P.numeroCuotas())
                    .map(Movimiento::capitalContractual).reduce(Calc.CERO, Calc::sumar);
            assertThat(contractual).isCloseTo(vigente, within(UN_PESO));
            // Sin anatocismo: el capital nunca crece y el interés corriente nunca se causa sobre intereses pendientes.
            for (int i = 1; i < m.size(); i++) {
                Movimiento x = m.get(i);
                assertThat(x.saldoFinal()).as("periodo %d", x.periodo()).isLessThanOrEqualTo(m.get(i - 1).saldoFinal());
                BigDecimal baseVigente = Calc.noNegativo(Calc.restar(x.saldoInicial(), m.get(i - 1).capitalVencido()));
                assertThat(x.interesCorriente()).as("periodo %d", x.periodo())
                        .isCloseTo(Calc.multiplicar(baseVigente, x.tasaPeriodica()), within(bd("0.000001")));
            }
        }

        /** La usura con la modalidad del Excel (MISMA_FECHA_FINAL) usa la misma base. */
        @Test
        void laUsuraConLaModalidadDelExcelUsaLaMismaBase() {
            ReglasNegocio diego = ReglasNegocio.EXCEL.conCambioTasaSobreCapitalVigente(true);
            Escenario usura = new Escenario(Operacion.ejemploExcel(), P).entre(41, 43, new SinPago(true))
                    .en(CAMBIO - 1, new UsuraVigente(bd("0.81"))).conReglas(diego);
            assertThat(fila(motor.ejecutar(usura), CAMBIO).cuotaFinanciera())
                    .isCloseTo(fila(motor.ejecutar(enMora(diego)), CAMBIO).cuotaFinanciera(), within(bd("0.000001")));
        }

        /** MISMA_CUOTA (caso 10, la usura en NEGOCIO) ya recalculaba el plazo sobre el capital vigente. */
        @Test
        void conLaMismaCuotaNadaCambia() {
            Escenario e = new Escenario(Operacion.ejemploExcel(), P).entre(41, 43, new SinPago(true))
                    .en(CAMBIO, new CambioTasa(NUEVA_EA, ModalidadCambioTasa.MISMA_CUOTA));
            assertThat(motor.ejecutar(e.conReglas(ReglasNegocio.EXCEL.conCambioTasaSobreCapitalVigente(true))))
                    .isEqualTo(motor.ejecutar(e.conReglas(ReglasNegocio.EXCEL)));
        }

        /** Con el cliente al día las dos bases son iguales: el caso 2 del Excel no cambia. */
        @Test
        void conElClienteAlDiaElCaso2NoCambia() {
            assertThat(motor.ejecutar(EscenariosExcel.caso2(P).conReglas(ReglasNegocio.EXCEL.conCambioTasaSobreCapitalVigente(true))))
                    .isEqualTo(motor.ejecutar(EscenariosExcel.caso2(P)));
        }

        @Test
        void laClaveVieneDelPerfilDeNegocio() {
            assertThat(ReglasNegocio.EXCEL.cambioTasaSobreCapitalVigente()).isFalse();
            assertThat(negocio().cambioTasaSobreCapitalVigente()).isTrue();
            assertThat(ReglasNegocio.CLAVES).contains("cambioTasaSobreCapitalVigente", "cobranzaDiaria",
                    "cuotasVencidasParaRecuperacion");
        }
    }

    /**
     * X1. Fuente: respuesta de Diego 2026-10-22 (documento 'rptas software'), pregunta X1. Todas las operaciones
     * tienen servicios complementarios (fondo de garantías, asistencia y seguro); el Excel los mostró en un solo
     * caso como ejemplo. El motor ya los causaba en todos: en el Excel del 11 de octubre las 12 hojas los traen.
     */
    @Nested
    class X1ServiciosEnTodosLosCasos {

        @Test
        void losTreceCasosCausanFondoAsistenciaYSeguroConLosDosPerfiles() {
            for (ReglasNegocio reglas : List.of(ReglasNegocio.EXCEL, negocio())) {
                casos().forEach((nombre, escenario) -> {
                    List<Movimiento> m = motor.ejecutar(escenario.conReglas(reglas));
                    BigDecimal fondo = m.stream().map(x -> x.serviciosCausados().fondoGarantias()).reduce(Calc.CERO, Calc::sumar);
                    BigDecimal asistencia = m.stream().map(x -> x.serviciosCausados().asistencia()).reduce(Calc.CERO, Calc::sumar);
                    assertThat(fondo).as(nombre).isPositive();
                    assertThat(asistencia).as(nombre).isPositive();
                    // Todo periodo del plazo vigente causa al menos el seguro (D17: en atraso solo el seguro). Los
                    // "Periodo adicional" (después de la última cuota, o del plazo que acortó un abono, SRV-2) no
                    // causan servicios: es la pregunta P6, que X1 no responde.
                    m.stream().filter(x -> x.periodo() >= 1 && !x.evento().equals("Periodo adicional")).forEach(x ->
                            assertThat(x.serviciosCausados().seguro()).as("%s, periodo %d", nombre, x.periodo()).isPositive());
                    // Los periodos pagados al día causan los tres.
                    m.stream().filter(x -> x.periodo() >= 1 && x.evento().startsWith("Pago normal")).forEach(x ->
                            assertThat(x.serviciosCausados().fondoGarantias().min(x.serviciosCausados().asistencia()))
                                    .as("%s, periodo %d", nombre, x.periodo()).isPositive());
                });
            }
        }
    }

    /**
     * COB-4. Fuente: respuesta de Diego 2026-10-22 (documento 'rptas software'), pregunta COB-4. Los gastos de
     * cobranza solo se cobran cuando el cliente está en mora; si se pone al día o termina de pagar, no se liquidan.
     * El motor ya lo hacía: la cobranza sale de los días de atraso del pago.
     */
    @Nested
    class Cob4CobranzaSoloEnMora {

        @Test
        void enElCaso9SoloSeCausaEnLosPagosEnMora() {
            List<Movimiento> m = motor.ejecutar(EscenariosExcel.caso9(P).conReglas(sinUmbralDeCobranza()));
            for (int i = 1; i < m.size(); i++) {
                Movimiento x = m.get(i);
                if (x.periodo() < 43) {
                    assertThat(x.cobranzaCausada()).as("periodo %d", x.periodo()).isZero();
                } else {
                    // Paga con 14 días de atraso: está en mora y se causa; después de la última cuota sigue en mora.
                    assertThat(x.cobranzaCausada()).as("periodo %d", x.periodo()).isEqualByComparingTo("42000");
                    assertThat(x.estado()).as("periodo %d", x.periodo()).isEqualTo(EstadoCredito.EN_MORA);
                }
            }
        }

        @Test
        void alPonerseAlDiaDejaDeLiquidarse() {
            Escenario e = new Escenario(Operacion.ejemploExcel(), P).entre(43, 46, new PagoContractual(14))
                    .en(47, new PagoAlDia()).conReglas(sinUmbralDeCobranza());
            List<Movimiento> m = motor.ejecutar(e);
            assertThat(fila(m, 46).cobranzaCausada()).isPositive();
            assertThat(fila(m, 47).estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
            assertThat(fila(m, 47).cxcCobranza()).isZero();
            m.stream().filter(x -> x.periodo() >= 47).forEach(x -> {
                assertThat(x.cobranzaCausada()).as("periodo %d", x.periodo()).isZero();
                assertThat(x.cxcCobranza()).as("periodo %d", x.periodo()).isZero();
            });
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
        }

        @Test
        void alCancelarDejaDeLiquidarse() {
            List<Movimiento> m = motor.ejecutar(EscenariosExcel.caso9(P).en(60, new PrepagoTotal()).conReglas(sinUmbralDeCobranza()));
            Movimiento ultimo = m.getLast();
            assertThat(ultimo.periodo()).isEqualTo(60);
            assertThat(ultimo.estado()).isEqualTo(EstadoCredito.CANCELADO);
            assertThat(ultimo.cobranzaCausada()).isZero();
            assertThat(ultimo.cxcCobranza()).isZero();
        }
    }

    /**
     * COB-6. Fuente: respuesta de Diego 2026-10-22 (documento 'rptas software'), pregunta COB-6. El cobro de
     * cobranza lo faculta el contrato de mutuo; un responsable activa a qué créditos se les inicia; los 3.000 por
     * día son solo un ejemplo y el valor puede cambiar en el tiempo.
     */
    @Nested
    class Cob6ActivacionYValorDeLaCobranza {

        @Test
        void sinActivarLaCobranzaElCreditoNoLaCausa() {
            List<Movimiento> activa = motor.ejecutar(EscenariosExcel.caso9(P));
            List<Movimiento> inactiva = motor.ejecutar(EscenariosExcel.caso9(P).conCobranzaActiva(false));
            assertThat(EscenariosExcel.caso9(P).cobranzaActiva()).isTrue();
            assertThat(activa).isEqualTo(motor.ejecutar(EscenariosExcel.caso9(P).conCobranzaActiva(true)));
            assertThat(activa.stream().map(Movimiento::cobranzaCausada).reduce(Calc.CERO, Calc::sumar)).isPositive();
            assertThat(inactiva).allSatisfy(x -> {
                assertThat(x.cobranzaCausada()).isZero();
                assertThat(x.pagoCobranza()).isZero();
            });
            // La mora por días se sigue causando: solo se apaga la cobranza.
            assertThat(fila(inactiva, 43).interesMora()).isEqualByComparingTo(fila(activa, 43).interesMora());
        }

        @Test
        void elValorDiarioEsUnParametro() {
            assertThat(P.cobranzaDiaria()).isEqualByComparingTo("3000");
            ParametrosProducto otro = P.conCobranzaDiaria(bd(4_000));
            Movimiento m = fila(motor.ejecutar(EscenariosExcel.caso9(otro)), 43);
            assertThat(m.cobranzaCausada()).isEqualByComparingTo("56000");
            assertThatThrownBy(() -> P.conCobranzaDiaria(bd(-1))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void elValorDiarioVigenteSaleDeLasReglas() {
            assertThat(ReglasNegocio.EXCEL.cobranzaDiaria()).isNull();
            assertThat(negocio().cobranzaDiaria()).isEqualByComparingTo("3000");
            assertThat(ReglasNegocio.desde(Map.of("cobranzaDiaria", "3500")).cobranzaDiaria()).isEqualByComparingTo("3500");
            assertThatThrownBy(() -> ReglasNegocio.EXCEL.conCobranzaDiaria(bd(-1))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * PPER-9. Fuente: respuesta de Diego 2026-10-22 (documento 'rptas software'), pregunta PPER-9. La política es
     * ejecutar dación o retoma después de 2 cuotas atrasadas, en periodos de la periodicidad pactada. El motor solo
     * marca el crédito "listo para recuperación"; si la ejecuta solo es la pregunta R6-RECUP-AUTO.
     */
    @Nested
    class Pper9ListoParaRecuperacion {

        @Test
        void elCaso11CorregidoEspera2PeriodosYLaCobranzaNaceEnLaTerceraCuota() {
            // R7-RECUP-VS-CASO11 (Diego 2026-10-27): el caso 11 espera 2 periodos, no 4.
            List<Movimiento> m = motor.ejecutar(EscenariosExcel.caso11(P, negocio().cuotasVencidasParaRecuperacion())
                    .conReglas(negocio()));
            assertThat(m.getLast().periodo()).isEqualTo(45);
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CERRADO_POR_EVENTO);
            // R7-COBRANZA-ACTIVA: con 2 periodos en mora, la cobranza empieza en la tercera cuota vencida.
            List<Movimiento> largo = motor.ejecutar(EscenariosExcel.caso11(P).conReglas(negocio()));
            assertThat(fila(largo, 43).cobranzaCausada()).isZero();
            assertThat(fila(largo, 44).cobranzaCausada()).isZero();
            assertThat(fila(largo, 45).cobranzaCausada()).isPositive();
        }

        @Test
        void conLasReglasDelExcelNingunCasoSeMarca() {
            casos().forEach((nombre, e) -> assertThat(motor.ejecutar(e.conReglas(ReglasNegocio.EXCEL)))
                    .as(nombre).noneMatch(Movimiento::listoParaRecuperacion));
        }

        @Test
        void elCaso11SeMarcaConDosCuotasAtrasadasPeroLaDacionSigueEnElCuartoPeriodo() {
            // Este escenario conserva los 4 periodos de espera del Excel; con NEGOCIO el catálogo usa 2 (ver
            // elCaso11CorregidoEspera2PeriodosYLaCobranzaNaceEnLaTerceraCuota).
            List<Movimiento> excel = motor.ejecutar(EscenariosExcel.caso11(P));
            List<Movimiento> m = motor.ejecutar(EscenariosExcel.caso11(P).conReglas(negocio()));
            assertThat(fila(m, 43).cuotasVencidas()).isEqualTo(1);
            assertThat(fila(m, 43).listoParaRecuperacion()).isFalse();
            assertThat(fila(m, 44).cuotasVencidas()).isEqualTo(2);
            assertThat(fila(m, 44).listoParaRecuperacion()).isTrue();
            assertThat(fila(m, 46).listoParaRecuperacion()).isTrue();
            // El motor no ejecuta la dación: el cierre sigue en el periodo 47, como el Excel (4 periodos de espera).
            assertThat(m.getLast().periodo()).isEqualTo(47).isEqualTo(excel.getLast().periodo());
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CERRADO_POR_EVENTO);
            assertThat(m.getLast().listoParaRecuperacion()).isFalse();
        }

        @Test
        void unaSolaCuotaAtrasadaNoLoMarcaYPonerseAlDiaNoLoDesmarca() {
            assertThat(motor.ejecutar(EscenariosExcel.caso8(P).conReglas(negocio())))
                    .noneMatch(Movimiento::listoParaRecuperacion);
            Escenario e = new Escenario(Operacion.ejemploExcel(), P).entre(30, 31, new SinPago(true))
                    .en(32, new PagoAlDia()).conReglas(negocio());
            List<Movimiento> m = motor.ejecutar(e);
            assertThat(fila(m, 30).listoParaRecuperacion()).isFalse();
            assertThat(fila(m, 31).listoParaRecuperacion()).isTrue();
            // R6-RECUP-AUTO (Diego): la marca persiste aunque el cliente se ponga al día; una persona decide.
            assertThat(fila(m, 32).listoParaRecuperacion()).isTrue();
            assertThat(fila(m, 32).estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
        }

        /** El umbral va en cuotas de la periodicidad: en quincenal son 2 quincenas. */
        @Test
        void elUmbralVaEnPeriodosDeLaPeriodicidad() {
            ParametrosProducto quincenal = ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.QUINCENAL, 48);
            List<Movimiento> m = motor.ejecutar(new Escenario(Operacion.ejemploExcel(), quincenal)
                    .entre(10, 11, new SinPago(true)).conReglas(negocio()));
            assertThat(fila(m, 10).listoParaRecuperacion()).isFalse();
            assertThat(fila(m, 11).listoParaRecuperacion()).isTrue();
        }

        @Test
        void laClaveVieneDelPerfilDeNegocio() {
            assertThat(ReglasNegocio.EXCEL.cuotasVencidasParaRecuperacion()).isNull();
            assertThat(negocio().cuotasVencidasParaRecuperacion()).isEqualTo(2);
            assertThat(ReglasNegocio.EXCEL.llegaAlUmbralDeRecuperacion(50)).isFalse();
            assertThatThrownBy(() -> ReglasNegocio.EXCEL.conCuotasVencidasParaRecuperacion(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * X4. Fuente: respuesta de Diego 2026-10-22 (documento 'rptas software'), pregunta X4. El plazo límite del caso 8
     * se amplía hasta 104 cuotas, que es {@code plazoLimiteMeses = 24} en periodos semanales. El caso 8 del Excel
     * conserva sus 100 semanas.
     */
    @Test
    void x4ElCaso8ConElPerfilDeNegocioLlegaA104Cuotas() {
        ReglasNegocio diego = negocio();
        Escenario negocio = EscenariosExcel.caso8(P, diego.plazoLimitePeriodos(P.periodicidad(), P.numeroCuotas()));
        assertThat(negocio.parametros().plazoLimitePeriodos()).isEqualTo(104);
        assertThat(EscenariosExcel.caso8(P).parametros().plazoLimitePeriodos()).isEqualTo(100);
        assertThat(EscenariosExcel.caso8(P, ReglasNegocio.EXCEL.plazoLimitePeriodos(P.periodicidad(), P.numeroCuotas()))
                .parametros().plazoLimitePeriodos()).isEqualTo(100);
        List<Movimiento> m = motor.ejecutar(negocio.conReglas(diego));
        assertThat(m.getLast().periodo()).isGreaterThan(100).isLessThanOrEqualTo(104);
    }
}
