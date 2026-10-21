package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.excel.ComparadorExcel;
import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.Amortizacion;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.ClasificacionPago;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.Tasas;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadCambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.TipoRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.UsuraVigente;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Respuestas de Diego de la ronda 3 (2026-10-20 en la tarde) que el motor aplica: USU-6, ABN-4, ABN-5,
 * SRV-2, SRV-3, REC-1/REC-2 y REC-8. ABN-1/ABN-2 están en {@code Caso13AbonoConPendientesTest}. Ver
 * docs/reglas/caso-2.md, caso-3.md, caso-6.md, caso-7.md y caso-13.md.
 */
class RespuestasDiegoRonda3Test {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    private static final BigDecimal MONTO = Operacion.ejemploExcel().montoFinanciado();
    private final MotorCartera motor = new MotorCartera();

    private static Escenario base() {
        return new Escenario(Operacion.ejemploExcel(), P);
    }

    /** USU-6: cuando la usura obliga a bajar la tasa, se mantiene la cuota y baja el plazo (caso 10). */
    @Nested
    class Usu6UsuraConLaMismaCuota {

        private final ReglasNegocio diego = ReglasNegocio.EXCEL.conModalidadUsura(ModalidadCambioTasa.MISMA_CUOTA);

        @Test
        void conUsuraDe81PublicadaEnLa43ReproduceElCaso10DelExcelCeldaPorCelda() {
            int periodo44 = Calendario.primerPeriodoDelMes(11, P);
            List<Movimiento> m = motor.ejecutar(base().en(periodo44 - 1, new UsuraVigente(bd("0.81"))).conReglas(diego));
            ComparadorExcel comparador = new ComparadorExcel()
                    .dinero("Saldo inicial", Movimiento::saldoInicial)
                    .tasa("Tasa periódica", Movimiento::tasaPeriodica)
                    .dinero("Interés", Movimiento::interesCorriente)
                    .dinero("Cuota financiera", x -> Calc.sumar(x.pagoInteresCorriente(), x.pagoCapital()))
                    .dinero("Capital", Movimiento::pagoCapital)
                    .dinero("Servicios", x -> x.serviciosCausados().total())
                    .dinero("Saldo final", Movimiento::saldoFinal);
            assertThat(comparador.diferencias(HojaExcel.cargar("Caso10_TasaCuotaFija"), m)).isEmpty();
            assertThat(m.getLast().periodo()).isEqualTo(103);
        }

        @Test
        void laCuotaNoCambiaYElPlazoBaja() {
            List<Movimiento> m = motor.ejecutar(base().en(43, new UsuraVigente(bd("0.80"))).conReglas(diego));
            BigDecimal cuota = m.get(43).cuotaFinanciera();
            Movimiento cambio = m.get(44);
            assertThat(cambio.evento()).isEqualTo("Cambio de tasa");
            assertThat(cambio.tasaPeriodica()).isCloseTo(Tasas.desdeEA(bd("0.79"), 52, 12).periodica(),
                    within(ComparadorExcel.TOLERANCIA_TASA));
            assertThat(m.subList(44, m.size())).allSatisfy(x -> assertThat(x.cuotaFinanciera()).isEqualByComparingTo(cuota));
            assertThat(m.getLast().periodo()).isLessThan(Calendario.numeroCuotas(P));
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
            assertThat(m.getLast().saldoFinal()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
        }

        @Test
        void dosBajasDeUsuraAcortanElPlazoDosVecesSinCambiarLaCuota() {
            List<Movimiento> una = motor.ejecutar(base().en(43, new UsuraVigente(bd("0.80"))).conReglas(diego));
            List<Movimiento> dos = motor.ejecutar(base().en(43, new UsuraVigente(bd("0.80")))
                    .en(70, new UsuraVigente(bd("0.75"))).conReglas(diego));
            assertThat(dos.get(71).evento()).isEqualTo("Cambio de tasa");
            assertThat(dos.get(71).cuotaFinanciera()).isEqualByComparingTo(dos.get(43).cuotaFinanciera());
            assertThat(dos.getLast().periodo()).isLessThanOrEqualTo(una.getLast().periodo());
            assertThat(dos.getLast().saldoFinal()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
        }

        @Test
        void conLasReglasDelExcelSigueRecalculandoLaCuotaComoElCaso2() {
            List<Movimiento> m = motor.ejecutar(base().en(43, new UsuraVigente(bd("0.80"))));
            assertThat(m.get(44).cuotaFinanciera()).isNotEqualByComparingTo(m.get(43).cuotaFinanciera());
            assertThat(m.getLast().periodo()).isEqualTo(Calendario.numeroCuotas(P));
        }

        @Test
        void laClaveSeLeeDeLaConfiguracionYDiegoEligioLaMismaCuota() throws IOException {
            assertThat(ReglasNegocio.CLAVES).contains("modalidadUsura");
            assertThat(ReglasNegocio.EXCEL.modalidadUsura()).isEqualTo(ModalidadCambioTasa.MISMA_FECHA_FINAL);
            assertThat(ReglasNegocio.desde(Map.of("modalidadUsura", "MISMA_CUOTA")).modalidadUsura())
                    .isEqualTo(ModalidadCambioTasa.MISMA_CUOTA);
            assertThat(ReglasNegocio.desde(ReglasNegocioConfiguradaTest.configuracion()).modalidadUsura())
                    .isEqualTo(ModalidadCambioTasa.MISMA_CUOTA);
        }
    }

    /** ABN-5: cada abono a menor cuota recalcula la cuota sobre las cuotas restantes. */
    @Test
    void abn5CadaAbonoAMenorCuotaRecalculaLaCuota() {
        List<Movimiento> m = motor.ejecutar(base().en(43, new AbonoExtra(bd(500_000), ModalidadAbono.MENOR_CUOTA))
                .en(60, new AbonoExtra(bd(300_000), ModalidadAbono.MENOR_CUOTA)));
        int cuotas = Calendario.numeroCuotas(P);
        BigDecimal tasa = Tasas.de(P).periodica();
        BigDecimal primera = Amortizacion.cuotaFija(tasa, cuotas - 43, m.get(43).saldoFinal());
        BigDecimal segunda = Amortizacion.cuotaFija(tasa, cuotas - 60, m.get(60).saldoFinal());
        assertThat(m.get(44).cuotaFinanciera()).isCloseTo(primera, within(UN_PESO));
        assertThat(m.get(60).cuotaFinanciera()).isCloseTo(primera, within(UN_PESO));
        assertThat(m.get(61).cuotaFinanciera()).isCloseTo(segunda, within(UN_PESO));
        assertThat(segunda).isLessThan(primera);
        assertThat(m.getLast().periodo()).isEqualTo(cuotas);
        assertThat(m.getLast().saldoFinal()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
    }

    /** SRV-2: si un abono acorta el plazo, los servicios terminan con el nuevo plazo. */
    @Nested
    class Srv2ServiciosConElNuevoPlazo {

        @Test
        void sinAtrasoElCreditoYLosServiciosTerminanEnLaNuevaUltimaCuota() {
            List<Movimiento> m = motor.ejecutar(EscenariosExcel.caso6(P));
            assertThat(m.getLast().periodo()).isEqualTo(71);
            assertThat(m.getLast().evento()).isEqualTo("Pago final");
        }

        @Test
        void conAtrasoDespuesDelAbonoNoSeCausanServiciosDespuesDelNuevoPlazo() {
            List<Movimiento> m = motor.ejecutar(EscenariosExcel.caso6(P).en(50, new SinPago(true)));
            Movimiento ultimaPactada = m.get(71);
            assertThat(ultimaPactada.serviciosCausados().total()).isPositive();
            assertThat(ultimaPactada.saldoFinal()).isPositive();
            Movimiento adicional = m.get(72);
            assertThat(adicional.evento()).isEqualTo("Periodo adicional");
            assertThat(adicional.serviciosCausados().total()).isZero();
            assertThat(adicional.capitalContractual()).isZero();
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
        }
    }

    /** SRV-3: la última cuota lleva los servicios completos aunque la cuota financiera sea solo el residuo. */
    @Test
    void srv3LaUltimaCuotaLlevaLosServiciosCompletos() {
        Servicios completos = Servicios.porCuota(MONTO, P);
        for (Escenario e : List.of(EscenariosExcel.caso1(P), EscenariosExcel.caso6(P), EscenariosExcel.caso10(P))) {
            Movimiento ultimo = motor.ejecutar(e).getLast();
            assertThat(ultimo.serviciosCausados()).isEqualTo(completos);
            assertThat(ultimo.pagosServicios().total()).isCloseTo(completos.total(), within(UN_PESO));
        }
        Movimiento ultimoCaso6 = motor.ejecutar(EscenariosExcel.caso6(P)).getLast();
        assertThat(Calc.sumar(ultimoCaso6.pagoInteresCorriente(), ultimoCaso6.pagoCapital()))
                .isLessThan(ultimoCaso6.cuotaFinanciera());
    }

    /** REC-1 / REC-2: en la dación el fondo de garantías cubre todo el faltante, de todos los conceptos. */
    @Nested
    class Rec1Rec2FondoCubreTodo {

        private static final int CIERRE = 44;
        private static final BigDecimal MOTO = bd(1_000_000);

        private Escenario atrasado() {
            return base().en(40, new PagoValor(bd(5_000), 3)).entre(41, 43, new SinPago(true));
        }

        @Test
        void elFaltanteIncluyeCapitalInteresesMoraServiciosYCobranza() {
            Movimiento antes = motor.ejecutar(atrasado().en(CIERRE, new SinPago(false))).get(CIERRE);
            Movimiento cierre = motor.ejecutar(atrasado()
                    .en(CIERRE, new CierrePorRecuperacion(TipoRecuperacion.DACION_EN_PAGO, MOTO))).get(CIERRE);

            assertThat(antes.cxcCobranza()).isPositive();
            assertThat(antes.cxcMora()).isPositive();
            assertThat(antes.cxcInteresCorriente()).isPositive();
            assertThat(antes.cxcServicios()).isPositive();
            BigDecimal deuda = Calc.sumar(antes.saldoFinal(), antes.cxcCobranza(), antes.cxcMora(), antes.cxcInteresCorriente(),
                    antes.cxcServicios());
            assertThat(cierre.deudaAExtinguir()).isCloseTo(deuda, within(UN_PESO));
            DeudaExtinguida extinguido = cierre.extinguido();
            assertThat(extinguido.cobranza()).isEqualByComparingTo(antes.cxcCobranza());
            assertThat(extinguido.mora()).isEqualByComparingTo(antes.cxcMora());
            assertThat(extinguido.interesCorriente()).isEqualByComparingTo(antes.cxcInteresCorriente());
            assertThat(extinguido.servicios().total()).isEqualByComparingTo(antes.cxcServicios());
            assertThat(extinguido.capital()).isEqualByComparingTo(antes.saldoFinal());
            // Moto + fondo = toda la deuda: el cliente no queda debiendo nada (REC-3 solo aplica si el fondo no alcanza).
            assertThat(Calc.sumar(cierre.inventario(), cierre.cxcFondoGarantias())).isCloseTo(deuda, within(UN_PESO));
            assertThat(cierre.cxpDeudor()).isZero();
            assertThat(cierre.estado()).isEqualTo(EstadoCredito.CERRADO_POR_EVENTO);
            Contabilizador.estandar().contabilizar(motor.ejecutar(atrasado()
                    .en(CIERRE, new CierrePorRecuperacion(TipoRecuperacion.DACION_EN_PAGO, MOTO))));
        }
    }

    /** REC-8: la semana del evento (dación o retoma) cuenta como vencida. */
    @Nested
    class Rec8SemanaDelEventoVencida {

        @Test
        void enElCaso3LaSemanaDelSiniestroEsLaCuartaVencida() {
            Movimiento evento = motor.ejecutar(EscenariosExcel.caso3(P, false)).getLast();
            assertThat(evento.evento()).isEqualTo("Evento de cierre");
            assertThat(evento.cuotasVencidas()).isEqualTo(4);
            assertThat(evento.pagoRecibido()).isZero();
        }

        @Test
        void siElEscenarioNoDiceComoPagaLaSemanaDelEventoNoSeSuponeUnPagoATiempo() {
            CierrePorRecuperacion dacion = new CierrePorRecuperacion(TipoRecuperacion.DACION_EN_PAGO, bd(3_000_000));
            Escenario implicito = base().entre(31, 33, new SinPago(false)).en(34, dacion);
            Escenario explicito = base().entre(31, 34, new SinPago(false)).en(34, dacion);
            List<Movimiento> m = motor.ejecutar(implicito);
            assertThat(m).isEqualTo(motor.ejecutar(explicito));
            assertThat(m.getLast().pagoRecibido()).isZero();
            assertThat(m.getLast().cuotasVencidas()).isEqualTo(m.get(33).cuotasVencidas() + 1);
        }

        @Test
        void unPagoExplicitoEnLaSemanaDelEventoSeRespeta() {
            CierrePorRecuperacion retoma = new CierrePorRecuperacion(TipoRecuperacion.RETOMA, bd(6_800_000));
            Movimiento evento = motor.ejecutar(base().en(34, new PagoValor(bd(50_000), 0)).en(34, retoma)).get(34);
            assertThat(evento.pagoRecibido()).isEqualByComparingTo("50000");
        }
    }

    /** ABN-4: el valor exacto de la cuota es pago corriente y lo que la supere es abono extra. */
    @Test
    void abn4LaClasificacionDelPagoAlimentaAlMotorComoCuotaMasAbonoExtra() {
        Movimiento semana43 = motor.ejecutar(base()).get(43);
        BigDecimal cuotaTotal = Calc.sumar(semana43.cuotaFinanciera(), semana43.serviciosCausados().total());
        ClasificacionPago c = ClasificacionPago.clasificar(Calc.sumar(cuotaTotal, bd(500_000)), cuotaTotal);
        List<Movimiento> clasificado = motor.ejecutar(base().en(43, new PagoValor(c.pagoCorriente(), 0))
                .en(43, new AbonoExtra(c.abonoExtra())));
        List<Movimiento> esperado = motor.ejecutar(base().en(43, new AbonoExtra(bd(500_000))));
        assertThat(c.abonoExtra()).isEqualByComparingTo("500000");
        assertThat(clasificado.get(43).abonoExtra()).isEqualByComparingTo(esperado.get(43).abonoExtra());
        assertThat(clasificado.get(43).saldoFinal()).isCloseTo(esperado.get(43).saldoFinal(), within(UN_PESO));
        assertThat(clasificado.getLast().periodo()).isEqualTo(esperado.getLast().periodo());
    }
}
