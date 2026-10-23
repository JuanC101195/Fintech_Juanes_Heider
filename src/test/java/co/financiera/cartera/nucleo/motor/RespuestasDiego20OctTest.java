package co.financiera.cartera.nucleo.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.reglas.AbonoExtraordinario;
import co.financiera.cartera.excel.ComparadorExcel;
import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.Amortizacion;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Tasas;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadCambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.Instruccion.UsuraVigente;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.AplicacionAbono;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Reglas que respondió Diego el 2026-10-20: A4 (abono extra con prelación), A5 (tasa del recálculo),
 * A6 (interés por días en el prepago) y T2/T4 (ajuste por usura). Ver docs/reglas/caso-2.md,
 * caso-5.md, caso-6.md y caso-7.md.
 */
class RespuestasDiego20OctTest {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    private static final ReglasNegocio DIEGO = ReglasNegocio.EXCEL.conAplicacionAbono(AplicacionAbono.PRIMERO_PENDIENTES)
            .conInteresPrepagoPorDias(true);
    private final MotorCartera motor = new MotorCartera();

    private static Escenario base() {
        return new Escenario(Operacion.ejemploExcel(), P);
    }

    /**
     * Deja pendientes de todo tipo antes del abono de la semana 44: en la 43 paga 30.000 (no alcanza el
     * interés: queda interés, fondo, asistencia, seguro y capital vencido) y en la 44 paga 5.000 con 3
     * días de atraso (cobranza 9.000 y mora; el pago no alcanza ni la cobranza).
     */
    private static Escenario conPendientes() {
        return base().en(43, new PagoValor(Calc.bd(30_000), 0)).en(44, new PagoValor(Calc.bd(5_000), 3));
    }

    private static BigDecimal pendienteTotal(Movimiento m) {
        return Calc.sumar(m.cxcCobranza(), m.cxcMora(), m.cxcInteresCorriente(), m.cxcServicios(), m.capitalVencido());
    }

    @Nested
    class A4AbonoExtraConPrelacion {

        @Test
        void elAbonoPagaPrimeroLosPendientesPorPrelacionYElRestoVaACapital() {
            Movimiento sinAbono = motor.ejecutar(conPendientes().conReglas(DIEGO)).get(44);
            assertThat(sinAbono.cxcMora()).isPositive();
            assertThat(sinAbono.cxcInteresCorriente()).isPositive();
            assertThat(sinAbono.cxcServiciosPorConcepto().fondoGarantias()).isPositive();
            assertThat(sinAbono.capitalVencido()).isPositive();

            BigDecimal abono = Calc.bd(500_000);
            List<Movimiento> m = motor.ejecutar(conPendientes().en(44, new AbonoExtra(abono)).conReglas(DIEGO));
            Movimiento semana = m.get(44);
            DeudaExtinguida pagado = semana.pendientesPagadosExtra();

            // Cada pendiente se paga completo y en el orden de Inputs ⑥.
            assertThat(pagado.cobranza()).isEqualByComparingTo(sinAbono.cxcCobranza());
            assertThat(pagado.mora()).isEqualByComparingTo(sinAbono.cxcMora());
            assertThat(pagado.interesCorriente()).isEqualByComparingTo(sinAbono.cxcInteresCorriente());
            assertThat(pagado.servicios()).isEqualTo(sinAbono.cxcServiciosPorConcepto());
            assertThat(pagado.capital()).isEqualByComparingTo(sinAbono.capitalVencido());
            // El resto va a capital vigente.
            assertThat(semana.abonoExtra()).isCloseTo(abono.subtract(pendienteTotal(sinAbono)), offset(UN_PESO));
            assertThat(semana.pagoExtraordinario()).isCloseTo(abono, offset(UN_PESO));
            assertThat(pendienteTotal(semana)).isZero();
            assertThat(semana.estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
            assertThat(semana.cuotasVencidas()).isZero();
            assertThat(semana.saldoFinal()).isCloseTo(sinAbono.saldoFinal().subtract(abono)
                    .add(Calc.sumar(pagado.cobranza(), pagado.mora(), pagado.interesCorriente(), pagado.servicios().total())),
                    offset(UN_PESO));
        }

        @Test
        void unAbonoMenorQueLosPendientesLosPagaEnOrdenSinLlegarACapital() {
            Movimiento sinAbono = motor.ejecutar(conPendientes().conReglas(DIEGO)).get(44);
            BigDecimal mitadFondo = Calc.dividir(sinAbono.cxcServiciosPorConcepto().fondoGarantias(), Calc.bd(2));
            BigDecimal abono = Calc.sumar(sinAbono.cxcCobranza(), sinAbono.cxcMora(), sinAbono.cxcInteresCorriente(), mitadFondo);

            Movimiento semana = motor.ejecutar(conPendientes().en(44, new AbonoExtra(abono)).conReglas(DIEGO)).get(44);
            DeudaExtinguida pagado = semana.pendientesPagadosExtra();
            assertThat(pagado.mora()).isEqualByComparingTo(sinAbono.cxcMora());
            assertThat(pagado.interesCorriente()).isEqualByComparingTo(sinAbono.cxcInteresCorriente());
            assertThat(pagado.servicios().fondoGarantias()).isCloseTo(mitadFondo, offset(UN_PESO));
            assertThat(pagado.servicios().asistencia()).isZero();
            assertThat(pagado.servicios().seguro()).isZero();
            assertThat(pagado.capital()).isZero();
            assertThat(semana.abonoExtra()).isZero();
            assertThat(semana.cxcMora()).isZero();
            assertThat(semana.cxcInteresCorriente()).isZero();
            assertThat(semana.capitalVencido()).isEqualByComparingTo(sinAbono.capitalVencido());
            assertThat(semana.estado()).isEqualTo(EstadoCredito.EN_MORA);
        }

        @Test
        void elAbonoNoAdelantaInteresesNiServiciosDeCuotasFuturas() {
            List<Movimiento> m = motor.ejecutar(conPendientes().en(44, new AbonoExtra(Calc.bd(500_000))).conReglas(DIEGO));
            // Lo pagado de interés en la semana del abono no supera lo causado hasta esa semana.
            BigDecimal causado = m.stream().filter(x -> x.periodo() <= 44).map(Movimiento::interesCorriente)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pagado = m.stream().filter(x -> x.periodo() <= 44)
                    .map(x -> x.pagoInteresCorriente().add(x.pendientesPagadosExtra().interesCorriente()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(pagado).isCloseTo(causado, offset(UN_PESO));
            // La semana siguiente causa y cobra su interés y sus servicios completos: nada quedó adelantado.
            Movimiento siguiente = m.get(45);
            assertThat(siguiente.interesCorriente()).isCloseTo(
                    Calc.multiplicar(siguiente.saldoInicial(), Tasas.de(P).periodica()), offset(UN_PESO));
            assertThat(siguiente.pagoInteresCorriente()).isCloseTo(siguiente.interesCorriente(), offset(UN_PESO));
            assertThat(siguiente.pagoServicios()).isCloseTo(siguiente.serviciosCausados().total(), offset(UN_PESO));
            assertThat(siguiente.serviciosCausados().total()).isPositive();
            assertThat(siguiente.pendientesPagadosExtra()).isEqualTo(DeudaExtinguida.NINGUNA);
        }

        @Test
        void conLaReglaDelExcelElAbonoVaTodoACapitalYLosPendientesSiguen() {
            Movimiento sinAbono = motor.ejecutar(conPendientes()).get(44);
            BigDecimal abono = Calc.bd(500_000);
            Movimiento semana = motor.ejecutar(conPendientes().en(44, new AbonoExtra(abono))).get(44);
            assertThat(semana.pendientesPagadosExtra()).isEqualTo(DeudaExtinguida.NINGUNA);
            assertThat(semana.abonoExtra()).isEqualByComparingTo(abono);
            assertThat(semana.cxcMora()).isEqualByComparingTo(sinAbono.cxcMora());
            assertThat(semana.capitalVencido()).isEqualByComparingTo(sinAbono.capitalVencido());
            assertThat(semana.estado()).isEqualTo(EstadoCredito.EN_MORA);
        }

        @Test
        void sinPendientesLaReglaDeDiegoDaLoMismoQueElExcel() {
            // Casos 6 y 7: el cliente está al día, el abono va completo a capital con las dos reglas.
            for (ModalidadAbono modalidad : ModalidadAbono.values()) {
                Escenario e = base().en(43, new AbonoExtra(Calc.bd(2_000_000), modalidad));
                assertThat(motor.ejecutar(e.conReglas(DIEGO))).isEqualTo(motor.ejecutar(e.conReglas(ReglasNegocio.EXCEL)));
            }
        }

        @Test
        void elAsientoDelAbonoAcreditaCadaPendienteYCuadra() {
            List<Movimiento> m = motor.ejecutar(conPendientes().en(44, new AbonoExtra(Calc.bd(500_000))).conReglas(DIEGO));
            Movimiento semana = m.get(44);
            Asiento asiento = new AbonoExtraordinario().contabilizar(semana).orElseThrow();
            assertThat(asiento.cuadra()).isTrue();
            assertThat(asiento.totalDebitos()).isCloseTo(Calc.bd(500_000), offset(UN_PESO));
            assertThat(credito(asiento, Cuenta.CXC_INTERES_MORA)).isEqualByComparingTo(semana.pendientesPagadosExtra().mora());
            assertThat(credito(asiento, Cuenta.CARTERA_VENCIDA)).isEqualByComparingTo(semana.pendientesPagadosExtra().capital());
            assertThat(credito(asiento, Cuenta.CARTERA_VIGENTE)).isEqualByComparingTo(semana.abonoExtra());
            Contabilizador.estandar().contabilizar(m);
        }

        @Test
        void elTopeDelAbonoEsElSaldoMasLosPendientes() {
            Movimiento sinAbono = motor.ejecutar(conPendientes().conReglas(DIEGO)).get(44);
            BigDecimal deudaTotal = Calc.sumar(sinAbono.saldoFinal(), sinAbono.cxcCobranza(), sinAbono.cxcMora(),
                    sinAbono.cxcInteresCorriente(), sinAbono.cxcServicios());
            List<Movimiento> m = motor.ejecutar(conPendientes().en(44, new AbonoExtra(deudaTotal)).conReglas(DIEGO));
            assertThat(m.getLast().periodo()).isEqualTo(44);
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);

            Escenario excedido = conPendientes().en(44, new AbonoExtra(deudaTotal.add(Calc.bd(1_000)))).conReglas(DIEGO);
            assertThatThrownBy(() -> motor.ejecutar(excedido)).isInstanceOf(AbonoMayorQueElSaldoException.class);
        }

        @Test
        void unPagoConExcedenteTambienPagaPrimeroLosPendientesYNoAdelantaNada() {
            // Pago inferior en la 43 y en la 44 un pago mayor que la cuota: el excedente respeta la prelación.
            Escenario e = base().en(43, new PagoParcial(new BigDecimal("0.4"), 0)).en(44, new PagoValor(Calc.bd(600_000), 0));
            for (ReglasNegocio reglas : List.of(ReglasNegocio.EXCEL, DIEGO)) {
                List<Movimiento> m = motor.ejecutar(e.conReglas(reglas));
                Movimiento semana = m.get(44);
                assertThat(pendienteTotal(semana)).isZero();
                BigDecimal repartido = Calc.sumar(semana.pagoCobranza(), semana.pagoMora(), semana.pagoInteresCorriente(),
                        semana.pagoServicios(), semana.pagoCapital());
                assertThat(repartido).isCloseTo(Calc.bd(600_000), offset(UN_PESO));
                Movimiento siguiente = m.get(45);
                assertThat(siguiente.pagoInteresCorriente()).isCloseTo(siguiente.interesCorriente(), offset(UN_PESO));
                assertThat(siguiente.pagoServicios()).isCloseTo(siguiente.serviciosCausados().total(), offset(UN_PESO));
            }
        }

        @Test
        void elPrepagoConPendientesLosPagaYCancelaElCredito() {
            List<Movimiento> diego = motor.ejecutar(conPendientes().en(44, new PrepagoTotal()).conReglas(DIEGO));
            Movimiento semana = diego.getLast();
            assertThat(semana.periodo()).isEqualTo(44);
            assertThat(semana.estado()).isEqualTo(EstadoCredito.CANCELADO);
            assertThat(semana.pendientesPagadosExtra().total()).isPositive();
            assertThat(pendienteTotal(semana)).isZero();
            Contabilizador.estandar().contabilizar(diego);

            // Con la regla del Excel el prepago solo toma el saldo de capital y lo demás queda pendiente.
            // Auditoría 2026-10-23 (H3): el capital vencido sí es capital y el prepago lo extingue; se reporta aparte
            // (pendientesPagadosExtra.capital) para acreditarlo a Cartera capital vencida. Cobranza, mora, interés y
            // servicios pendientes no se tocan.
            Movimiento excel = motor.ejecutar(conPendientes().en(44, new PrepagoTotal())).get(44);
            assertThat(excel.pendientesPagadosExtra().total()).isEqualByComparingTo(excel.pendientesPagadosExtra().capital());
            assertThat(excel.capitalVencido().signum()).isZero();
            assertThat(excel.saldoFinal().signum()).isZero();
            assertThat(excel.estado()).isEqualTo(EstadoCredito.EN_MORA);
        }
    }

    @Nested
    class A5TasaDelRecalculo {

        @Test
        void laCuotaNuevaUsaLaTasaVigenteYElAbonoNoCambiaLaTasa() {
            BigDecimal nuevaEA = new BigDecimal("0.80");
            int periodoCambio = Calendario.primerPeriodoDelMes(9, P);
            BigDecimal vigente = Tasas.desdeEA(nuevaEA, P.periodosPorAnio(), P.mesesPorAnio()).periodica();
            for (ReglasNegocio reglas : List.of(ReglasNegocio.EXCEL, DIEGO)) {
                List<Movimiento> m = motor.ejecutar(base().en(periodoCambio, new CambioTasa(nuevaEA))
                        .en(43, new AbonoExtra(Calc.bd(2_000_000), ModalidadAbono.MENOR_CUOTA)).conReglas(reglas));
                BigDecimal esperada = Amortizacion.cuotaFija(vigente, Calendario.numeroCuotas(P) - 43, m.get(43).saldoFinal());
                assertThat(m.get(44).cuotaFinanciera()).isCloseTo(esperada, offset(UN_PESO));
                assertThat(m.stream().filter(x -> x.periodo() >= periodoCambio))
                        .allSatisfy(x -> assertThat(x.tasaPeriodica()).isEqualByComparingTo(vigente));
                assertThat(m.getLast().periodo()).isEqualTo(Calendario.numeroCuotas(P));
                assertThat(m.getLast().saldoFinal()).isCloseTo(BigDecimal.ZERO, offset(UN_PESO));
            }
        }
    }

    @Nested
    class A6PrepagoConInteresPorDias {

        @Test
        void conLaReglaDeDiegoCobraElInteresDeLosDiasConLaTasaDiaria() {
            List<Movimiento> conDias = motor.ejecutar(base().en(43, new PrepagoTotal(3)).conReglas(DIEGO));
            List<Movimiento> mismoDia = motor.ejecutar(base().en(43, new PrepagoTotal()).conReglas(DIEGO));
            Movimiento semana = conDias.get(43);
            BigDecimal factor = Calc.restar(Calc.potencia(Calc.sumar(Calc.UNO, Tasas.de(P).diaria()), 3), Calc.UNO);
            BigDecimal esperado = Calc.multiplicar(semana.prepagoCapital(), factor);

            assertThat(semana.interesPrepago()).isPositive().isCloseTo(esperado, offset(new BigDecimal("0.000001")));
            assertThat(semana.prepagoCapital()).isEqualByComparingTo(mismoDia.get(43).prepagoCapital());
            assertThat(semana.pagoTotalCliente()).isCloseTo(mismoDia.get(43).pagoTotalCliente().add(esperado), offset(UN_PESO));
            assertThat(semana.estado()).isEqualTo(EstadoCredito.CANCELADO);
            assertThat(mismoDia.get(43).interesPrepago()).isZero();

            Asiento asiento = new AbonoExtraordinario().contabilizar(semana).orElseThrow();
            assertThat(asiento.cuadra()).isTrue();
            assertThat(credito(asiento, Cuenta.INGRESOS_INTERESES)).isCloseTo(esperado, offset(UN_PESO));
        }

        @Test
        void elExcelPrepagaSinInteresPorDias() {
            List<Movimiento> conDias = motor.ejecutar(base().en(43, new PrepagoTotal(3)));
            assertThat(conDias).isEqualTo(motor.ejecutar(base().en(43, new PrepagoTotal())));
            assertThat(conDias.get(43).interesPrepago()).isZero();
        }

        @Test
        void losDiasNoPuedenSerNegativos() {
            assertThatThrownBy(() -> new PrepagoTotal(-1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * T1/T2/T4 con la modalidad del Excel ({@code modalidadUsura = MISMA_FECHA_FINAL}, que es lo que hace
     * {@link ReglasNegocio#EXCEL}): la cuota se recalcula como en el caso 2. Diego eligió en la ronda 3
     * (USU-6) mantener la cuota y bajar el plazo; esa modalidad se prueba en
     * {@code RespuestasDiegoRonda3Test.Usu6UsuraConLaMismaCuota}. Estas pruebas fijan la modalidad
     * explícitamente para no depender del valor por defecto.
     */
    @Nested
    class T2T4AjustePorUsura {

        private final ReglasNegocio comoCaso2 = ReglasNegocio.EXCEL.conModalidadUsura(ModalidadCambioTasa.MISMA_FECHA_FINAL);
        private final BigDecimal tasa79 = Tasas.desdeEA(new BigDecimal("0.79"), 52, 12).periodica();

        @Test
        void siLaTasaSuperaLaUsuraBajaDesdeLaCuotaSiguienteYRecalculaComoElCaso2() {
            List<Movimiento> m = motor.ejecutar(base().en(43, new UsuraVigente(new BigDecimal("0.80"))).conReglas(comoCaso2));
            assertThat(m.get(43).tasaPeriodica()).isEqualByComparingTo(Tasas.de(P).periodica());
            Movimiento cambio = m.get(44);
            assertThat(cambio.evento()).isEqualTo("Cambio de tasa");
            assertThat(cambio.tasaPeriodica()).isCloseTo(tasa79, offset(ComparadorExcel.TOLERANCIA_TASA));
            assertThat(cambio.cuotaFinanciera()).isCloseTo(
                    Amortizacion.cuotaFija(tasa79, Calendario.numeroCuotas(P) - 44 + 1, cambio.saldoInicial()), offset(UN_PESO));
            assertThat(m.getLast().periodo()).isEqualTo(Calendario.numeroCuotas(P));
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
            assertThat(m.getLast().saldoFinal()).isCloseTo(BigDecimal.ZERO, offset(UN_PESO));
        }

        @Test
        void conUsuraDe81PublicadaEnLa43ReproduceElCaso2DelExcel() {
            int periodo44 = Calendario.primerPeriodoDelMes(11, P);
            List<Movimiento> m = motor.ejecutar(base().en(periodo44 - 1, new UsuraVigente(new BigDecimal("0.81"))).conReglas(comoCaso2));
            ComparadorExcel comparador = new ComparadorExcel()
                    .dinero("Saldo inicial", Movimiento::saldoInicial)
                    .tasa("Tasa periódica", Movimiento::tasaPeriodica)
                    .dinero("Interés", Movimiento::interesCorriente)
                    .dinero("Cuota financiera", Movimiento::cuotaFinanciera)
                    .dinero("Capital", Movimiento::pagoCapital)
                    .dinero("Saldo final", Movimiento::saldoFinal);
            assertThat(comparador.diferencias(HojaExcel.cargar("Caso2_CambioTasa"), m)).isEmpty();
        }

        @Test
        void siLaUsuraSubeLaTasaNoVuelveASubirYSiBajaOtraVezSeAjustaConElMismoMargen() {
            List<Movimiento> m = motor.ejecutar(base()
                    .en(43, new UsuraVigente(new BigDecimal("0.80")))
                    .en(60, new UsuraVigente(new BigDecimal("0.95")))
                    .en(70, new UsuraVigente(new BigDecimal("0.75"))).conReglas(comoCaso2));
            assertThat(m.get(61).tasaPeriodica()).isCloseTo(tasa79, offset(ComparadorExcel.TOLERANCIA_TASA));
            assertThat(m.get(61).evento()).isNotEqualTo("Cambio de tasa");
            assertThat(m.get(61).cuotaFinanciera()).isEqualByComparingTo(m.get(60).cuotaFinanciera());
            BigDecimal tasa74 = Tasas.desdeEA(new BigDecimal("0.74"), 52, 12).periodica();
            Movimiento segundo = m.get(71);
            assertThat(segundo.evento()).isEqualTo("Cambio de tasa");
            assertThat(segundo.tasaPeriodica()).isCloseTo(tasa74, offset(ComparadorExcel.TOLERANCIA_TASA));
            assertThat(segundo.cuotaFinanciera()).isCloseTo(
                    Amortizacion.cuotaFija(tasa74, Calendario.numeroCuotas(P) - 71 + 1, segundo.saldoInicial()), offset(UN_PESO));
            assertThat(m.getLast().saldoFinal()).isCloseTo(BigDecimal.ZERO, offset(UN_PESO));
        }

        @Test
        void unaUsuraPorEncimaDeLaTasaNoCambiaNada() {
            assertThat(motor.ejecutar(base().en(43, new UsuraVigente(new BigDecimal("0.95")))))
                    .isEqualTo(motor.ejecutar(base()));
        }

        @Test
        void elMargenSaleDeLasReglasDeNegocio() {
            ReglasNegocio medioPunto = ReglasNegocio.EXCEL.conMargenUsura(new BigDecimal("0.5"));
            List<Movimiento> m = motor.ejecutar(base().en(43, new UsuraVigente(new BigDecimal("0.80"))).conReglas(medioPunto));
            assertThat(m.get(44).tasaPeriodica()).isCloseTo(Tasas.desdeEA(new BigDecimal("0.795"), 52, 12).periodica(),
                    offset(ComparadorExcel.TOLERANCIA_TASA));
            assertThatThrownBy(() -> ReglasNegocio.EXCEL.conMargenUsura(new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new UsuraVigente(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void lasClavesNuevasSeLeenDeLaConfiguracionYPorDefectoSonLasDelExcel() {
        assertThat(ReglasNegocio.CLAVES).contains("aplicacionAbono", "interesPrepagoPorDias", "margenUsura");
        assertThat(ReglasNegocio.EXCEL.aplicacionAbono()).isEqualTo(AplicacionAbono.SOLO_CAPITAL);
        assertThat(ReglasNegocio.EXCEL.interesPrepagoPorDias()).isFalse();
        assertThat(ReglasNegocio.EXCEL.margenUsura()).isEqualByComparingTo("1");
        ReglasNegocio r = ReglasNegocio.desde(Map.of("aplicacionAbono", "PRIMERO_PENDIENTES", "interesPrepagoPorDias", "true",
                "margenUsura", "0.5"));
        assertThat(r.aplicacionAbono()).isEqualTo(AplicacionAbono.PRIMERO_PENDIENTES);
        assertThat(r.interesPrepagoPorDias()).isTrue();
        assertThat(r.margenUsura()).isEqualByComparingTo("0.5");
        assertThat(r.prelacion()).isEqualTo(ReglasNegocio.EXCEL.prelacion());
    }

    private static BigDecimal credito(Asiento asiento, Cuenta cuenta) {
        return asiento.lineas().stream().filter(l -> l.cuenta() == cuenta).map(Asiento.Linea::credito)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
