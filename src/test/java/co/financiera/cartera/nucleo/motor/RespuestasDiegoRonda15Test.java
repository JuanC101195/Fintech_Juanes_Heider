package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.nucleo.Amortizacion;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Tasas;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadCambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Instrucciones nuevas de Diego (2026-11-05, sesión de la ronda 14): D88 (cobranza temporal de 25.000 por cuota atrasada)
 * y D89 (base 360), con los supuestos de la ADR 0009 que se preguntan en la ronda 15. Ver
 * {@code docs/respuestas-diego-2026-11-05-ronda14-y-nuevas.md}.
 */
class RespuestasDiegoRonda15Test {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final BigDecimal CARGO = bd(25_000);
    private static final BigDecimal CENTAVO = new BigDecimal("0.01");
    private final MotorCartera motor = new MotorCartera();

    private static ReglasNegocio negocio() {
        return ReglasNegocioConfiguradaTest.negocio();
    }

    private static Movimiento fila(List<Movimiento> m, int periodo) {
        return m.stream().filter(x -> x.periodo() == periodo).findFirst().orElseThrow();
    }

    private static BigDecimal cobranzaTotal(List<Movimiento> m) {
        return m.stream().map(Movimiento::cobranzaCausada).reduce(Calc.CERO, Calc::sumar);
    }

    private static Escenario conOperacionDelExcel() {
        return new Escenario(Operacion.ejemploExcel(), P);
    }

    /** D88 (temporal): «si el cliente paga atrasado así sea 1 día, se le cobra un cargo de cobranza de $25.000». */
    @Nested
    class CobranzaPorCuotaAtrasada {

        @Test
        void negocioTraeLaPoliticaTemporalYElExcelNo() {
            assertThat(negocio().cobranzaPorCuotaAtrasada()).isEqualByComparingTo(CARGO);
            assertThat(negocio().cobranzaPorCuotaAtrasadaActiva()).isTrue();
            assertThat(ReglasNegocio.EXCEL.cobranzaPorCuotaAtrasada()).isZero();
            assertThat(ReglasNegocio.EXCEL.cobranzaPorCuotaAtrasadaActiva()).isFalse();
            assertThat(negocio().conCobranzaPorCuotaAtrasada(null).cobranzaPorCuotaAtrasadaActiva()).isFalse();
        }

        @Test
        void cuotaPagadaATiempoNoCausaCobranza() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().conReglas(negocio()));
            assertThat(cobranzaTotal(m)).isZero();
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
        }

        @Test
        void unDiaTardeCausa25000UnaSolaVezYVaPrimeroEnLaPrelacion() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().en(2, new PagoContractual(1)).conReglas(negocio()));
            Movimiento tarde = fila(m, 2);
            assertThat(tarde.cobranzaCausada()).isEqualByComparingTo(CARGO);
            // Primero en la prelación: de lo que entra, lo primero que se paga es el cargo.
            assertThat(tarde.pagoCobranza()).isEqualByComparingTo(CARGO);
            assertThat(cobranzaTotal(m)).isEqualByComparingTo(CARGO);
            // La cuota siguiente se paga completa a tiempo: no vuelve a causar el cargo aunque quedara algo pendiente.
            assertThat(fila(m, 3).cobranzaCausada()).isZero();
        }

        @Test
        void elCargoNoDependeDeLosDiasDeAtraso() {
            for (int dias : new int[] {1, 3, 6, 20}) {
                List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().en(2, new PagoContractual(dias)).conReglas(negocio()));
                assertThat(fila(m, 2).cobranzaCausada()).as("%d días", dias).isEqualByComparingTo(CARGO);
            }
        }

        @Test
        void variasSemanasSinPagoCausan25000PorCadaCuotaYNoPorDia() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 3, new SinPago(true)).conReglas(negocio()));
            for (int periodo = 1; periodo <= 3; periodo++) {
                assertThat(fila(m, periodo).cobranzaCausada()).as("cuota %d", periodo).isEqualByComparingTo(CARGO);
            }
            assertThat(fila(m, 3).cxcCobranza()).isEqualByComparingTo(bd(75_000));
            // La semana 4 paga a tiempo: no causa cargo y lo primero que paga son los 75.000 pendientes.
            assertThat(fila(m, 4).cobranzaCausada()).isZero();
            assertThat(fila(m, 4).pagoCobranza()).isEqualByComparingTo(bd(75_000));
            assertThat(cobranzaTotal(m)).isEqualByComparingTo(bd(75_000));
        }

        @Test
        void pagoParcialOMenorQueLaCuotaCausaElCargoYPagoCompletoNo() {
            BigDecimal cuotaTotal = fila(motor.ejecutar(conOperacionDelExcel().conReglas(negocio())), 2).pagoRecibido();
            assertThat(fila(motor.ejecutar(conOperacionDelExcel().en(2, new PagoParcial(new BigDecimal("0.4"), 0))
                    .conReglas(negocio())), 2).cobranzaCausada()).isEqualByComparingTo(CARGO);
            assertThat(fila(motor.ejecutar(conOperacionDelExcel().en(2, new PagoValor(bd(100_000), 0))
                    .conReglas(negocio())), 2).cobranzaCausada()).isEqualByComparingTo(CARGO);
            assertThat(fila(motor.ejecutar(conOperacionDelExcel().en(2, new PagoValor(cuotaTotal, 0))
                    .conReglas(negocio())), 2).cobranzaCausada()).isZero();
            // Un pago grande pero tardío también causa el cargo.
            assertThat(fila(motor.ejecutar(conOperacionDelExcel().en(2, new PagoValor(bd(500_000), 2))
                    .conReglas(negocio())), 2).cobranzaCausada()).isEqualByComparingTo(CARGO);
        }

        @Test
        void unPagoPequenoTardioSeVaTodoAlCargo() {
            Movimiento f = fila(motor.ejecutar(conOperacionDelExcel().en(2, new PagoValor(bd(10_000), 3))
                    .conReglas(negocio())), 2);
            assertThat(f.pagoCobranza()).isEqualByComparingTo(bd(10_000));
            assertThat(f.pagoMora()).isZero();
            assertThat(f.pagoInteresCorriente()).isZero();
            assertThat(f.cxcCobranza()).isEqualByComparingTo(bd(15_000));
        }

        @Test
        void reemplazaLaCobranzaAnteriorSinDuplicarla() {
            Escenario e = conOperacionDelExcel().entre(1, 6, new SinPago(true));
            List<Movimiento> temporal = motor.ejecutar(e.conReglas(negocio()));
            List<Movimiento> anterior = motor.ejecutar(conOperacionDelExcel().entre(1, 6, new SinPago(true))
                    .conReglas(negocio().conCobranzaPorCuotaAtrasada(BigDecimal.ZERO)));
            // La anterior (cociente >= 2, 40.000 por periodo) vuelve con 0; la temporal nunca causa 40.000 ni 65.000.
            assertThat(anterior.stream().map(Movimiento::cobranzaCausada)).contains(bd(40_000)).doesNotContain(CARGO);
            for (Movimiento x : temporal) {
                if (x.periodo() >= 1) {
                    assertThat(x.cobranzaCausada()).as("periodo %d", x.periodo())
                            .isIn(Calc.CERO, CARGO);
                }
            }
            assertThat(cobranzaTotal(temporal)).isEqualByComparingTo(bd(150_000));
        }

        @Test
        void conLaCobranzaInactivaDelCreditoNoSeCausa() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 3, new SinPago(true))
                    .conCobranzaActiva(false).conReglas(negocio()));
            assertThat(cobranzaTotal(m)).isZero();
        }

        @Test
        void despuesDelPlazoNoVenceNingunaCuotaYNoSeCausa() {
            // 4 cuotas mensuales; sin pago en la última: después de la cuota 4 ya no vence ninguna cuota.
            ParametrosProducto corto = ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.MENSUAL, 4).conPlazoLimite(8);
            List<Movimiento> m = motor.ejecutar(new Escenario(Operacion.ejemploExcel(), corto).en(4, new SinPago(true))
                    .desde(5, new PagoValor(bd(100_000), 0)).conReglas(negocio()));
            assertThat(fila(m, 4).cobranzaCausada()).isEqualByComparingTo(CARGO);
            assertThat(m.stream().filter(x -> x.periodo() > 4).map(Movimiento::cobranzaCausada)).allMatch(c -> c.signum() == 0);
        }

        @Test
        void laMarcaListoParaRecuperarNoCambia() {
            // El escenario de la ronda 13 (D85): se marca en la 2 y la 3 y se quita en la 4, con la política temporal o sin ella.
            Escenario e = conOperacionDelExcel().entre(1, 3, new SinPago(true)).en(4, new PagoValor(bd(600_000), 0));
            List<Movimiento> temporal = motor.ejecutar(e.conReglas(negocio()));
            List<Movimiento> anterior = motor.ejecutar(conOperacionDelExcel().entre(1, 3, new SinPago(true))
                    .en(4, new PagoValor(bd(600_000), 0)).conReglas(negocio().conCobranzaPorCuotaAtrasada(BigDecimal.ZERO)));
            // La regla es la misma (cociente >= 2 al cerrar el periodo); el saldo en mora incluye la cobranza pendiente, así
            // que en la cola del crédito, donde la anterior seguía cobrando 40.000 por periodo, las marcas pueden diferir.
            assertThat(temporal.subList(0, 20).stream().map(Movimiento::listoParaRecuperacion).toList())
                    .isEqualTo(anterior.subList(0, 20).stream().map(Movimiento::listoParaRecuperacion).toList());
            assertThat(fila(temporal, 2).listoParaRecuperacion()).isTrue();
            assertThat(fila(temporal, 4).listoParaRecuperacion()).isFalse();
        }

        @Test
        void seContabilizaComoLaCobranzaDeSiempre() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().en(2, new PagoContractual(1)).conReglas(negocio()));
            List<Asiento> asientos = Contabilizador.para(negocio()).contabilizar(m);
            BigDecimal ingreso = asientos.stream().filter(a -> a.periodo() == 2).flatMap(a -> a.lineas().stream())
                    .filter(l -> l.cuenta() == Cuenta.INGRESOS_NO_OPERACIONALES_COBRANZA)
                    .map(Asiento.Linea::credito).reduce(Calc.CERO, Calc::sumar);
            assertThat(ingreso).isEqualByComparingTo(CARGO);
            assertThat(asientos).allMatch(Asiento::cuadra);
        }

        @Test
        void elExcelSigueCobrandoPorDias() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().en(2, new PagoContractual(3)));
            assertThat(fila(m, 2).cobranzaCausada()).isEqualByComparingTo(bd(9_000));
        }
    }

    /** D89: «cambiaremos la base 365 a base 360 para el cálculo de las cuotas». */
    @Nested
    class Base360 {

        private static final BigDecimal EA_EJEMPLO = new BigDecimal("0.8844");
        private static final BigDecimal MONTO_EJEMPLO = bd(5_533_760);

        @Test
        void negocioUsaBase360YElExcel365() {
            assertThat(negocio().baseDiasAnio()).isEqualTo(360);
            assertThat(ReglasNegocio.EXCEL.baseDiasAnio()).isEqualTo(365);
            assertThat(Tasas.de(P, 365)).isEqualTo(Tasas.de(P));
        }

        @Test
        void soloCambiaLaSemanalYSieteDiasDeLaDiariaDanLaSemanal() {
            for (Periodicidad per : List.of(Periodicidad.QUINCENAL, Periodicidad.MENSUAL)) {
                assertThat(Tasas.desdeEA(P.tasaEA(), per, 360).periodica()).as(per.name())
                        .isEqualByComparingTo(Tasas.desdeEA(P.tasaEA(), per, 365).periodica());
            }
            Tasas t360 = Tasas.desdeEA(P.tasaEA(), Periodicidad.SEMANAL, 360);
            BigDecimal esperada = Calc.restar(Calc.potencia(Calc.sumar(Calc.UNO, P.tasaEA()), 7.0 / 360), Calc.UNO);
            assertThat(t360.periodica()).isEqualByComparingTo(esperada);
            assertThat(t360.periodica()).isGreaterThan(Tasas.desdeEA(P.tasaEA(), Periodicidad.SEMANAL, 365).periodica());
            BigDecimal sieteDias = Calc.restar(Calc.potencia(Calc.sumar(Calc.UNO, t360.diaria()), 7), Calc.UNO);
            assertThat(sieteDias).isCloseTo(t360.periodica(), within(new BigDecimal("1E-12")));
            assertThat(t360.mensual()).isEqualByComparingTo(Tasas.desdeEA(P.tasaEA(), Periodicidad.SEMANAL, 365).mensual());
        }

        @Test
        void cuotaSemanalConLaOperacionDelExcelAntesYDespues() {
            BigDecimal monto = Operacion.ejemploExcel().montoFinanciado();
            BigDecimal antes = Amortizacion.cuotaFija(Tasas.de(P, 365).periodica(), 104, monto);
            BigDecimal despues = Amortizacion.cuotaFija(Tasas.de(P, 360).periodica(), 104, monto);
            assertThat(antes).isCloseTo(new BigDecimal("100406.27"), within(CENTAVO));
            assertThat(despues).isCloseTo(new BigDecimal("100972.38"), within(CENTAVO));
            // En el motor con NEGOCIO (redondeo al peso) la cuota total pasa de 168.305 a 168.871.
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().conReglas(negocio()));
            assertThat(fila(m, 2).pagoRecibido()).isEqualByComparingTo(bd(168_871));
            List<Movimiento> m365 = motor.ejecutar(conOperacionDelExcel().conReglas(negocio().conBaseDiasAnio(365)));
            assertThat(fila(m365, 2).pagoRecibido()).isEqualByComparingTo(bd(168_305));
        }

        @Test
        void creditoDeEjemploDe88CuotasCierraEnCeroEnLaCuotaN() {
            ParametrosProducto p = ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.SEMANAL, 88).conTasaEA(EA_EJEMPLO);
            Operacion o = new Operacion(MONTO_EJEMPLO, Calc.CERO, Calc.CERO, Calc.CERO, Calc.CERO, Calc.CERO);
            assertThat(Amortizacion.cuotaFija(Tasas.de(p, 365).periodica(), 88, MONTO_EJEMPLO))
                    .isCloseTo(new BigDecimal("103137.22"), within(CENTAVO));
            assertThat(Amortizacion.cuotaFija(Tasas.de(p, 360).periodica(), 88, MONTO_EJEMPLO))
                    .isCloseTo(new BigDecimal("103651.56"), within(CENTAVO));
            for (int base : new int[] {365, 360}) {
                List<Movimiento> m = motor.ejecutar(new Escenario(o, p).conReglas(negocio().conBaseDiasAnio(base)));
                assertThat(m.getLast().periodo()).as("base %d", base).isEqualTo(88);
                assertThat(m.getLast().estado()).as("base %d", base).isEqualTo(EstadoCredito.CANCELADO);
                assertThat(m.getLast().saldoFinal()).as("base %d", base).isZero();
                BigDecimal capital = m.stream().map(Movimiento::capitalContractual).reduce(Calc.CERO, Calc::sumar);
                assertThat(capital).as("base %d", base).isCloseTo(MONTO_EJEMPLO, within(CENTAVO));
            }
        }

        @Test
        void mensualYQuincenalNoCambian() {
            for (Periodicidad per : List.of(Periodicidad.QUINCENAL, Periodicidad.MENSUAL)) {
                ParametrosProducto p = ParametrosProducto.de(LineaCredito.CPPU, per, 12);
                List<Movimiento> a = motor.ejecutar(new Escenario(Operacion.ejemploExcel(), p).en(3, new SinPago(true))
                        .conReglas(negocio()));
                List<Movimiento> b = motor.ejecutar(new Escenario(Operacion.ejemploExcel(), p).en(3, new SinPago(true))
                        .conReglas(negocio().conBaseDiasAnio(365)));
                assertThat(a.stream().map(Movimiento::cuotaFinanciera).toList()).as(per.name())
                        .isEqualTo(b.stream().map(Movimiento::cuotaFinanciera).toList());
                assertThat(a.stream().map(Movimiento::interesCorriente).toList()).as(per.name())
                        .isEqualTo(b.stream().map(Movimiento::interesCorriente).toList());
            }
        }

        @Test
        void unCambioDeTasaUsaLaMismaBase() {
            BigDecimal nueva = new BigDecimal("0.60");
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel()
                    .en(10, new CambioTasa(nueva, ModalidadCambioTasa.MISMA_FECHA_FINAL)).conReglas(negocio()));
            assertThat(fila(m, 10).tasaPeriodica())
                    .isEqualByComparingTo(Tasas.desdeEA(nueva, Periodicidad.SEMANAL, 360).periodica());
        }

        @Test
        void soloAceptaBase365O360() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> negocio().conBaseDiasAnio(366))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("365 o 360");
        }
    }

    /**
     * Ejemplo que Diego pidió para R14-MARCA-SEMANAS (las cifras del contexto de la pregunta): crédito semanal con la
     * operación del Excel que no paga las semanas 1 a 4 y en la 5 paga 800.000. El contador de cuotas vencidas queda en 4,
     * pero el saldo en mora baja a 147.880,29 (0,88 cuotas de 168.871) y la marca se quita.
     */
    @Test
    void ejemploDeLaMarcaConCuatroPeriodosAtrasadosSinMarca() {
        List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 4, new SinPago(true))
                .en(5, new PagoValor(bd(800_000), 0)).conReglas(negocio()));
        Movimiento semana4 = fila(m, 4);
        assertThat(semana4.listoParaRecuperacion()).isTrue();
        assertThat(semana4.cuotasVencidas()).isEqualTo(4);
        assertThat(saldoEnMora(semana4)).isCloseTo(new BigDecimal("777590.49"), within(CENTAVO));
        Movimiento semana5 = fila(m, 5);
        assertThat(semana5.cuotasVencidas()).isEqualTo(4);
        assertThat(semana5.listoParaRecuperacion()).isFalse();
        assertThat(saldoEnMora(semana5)).isCloseTo(new BigDecimal("147880.29"), within(CENTAVO));
        assertThat(semana5.pagoCobranza()).isEqualByComparingTo(bd(100_000));
    }

    private static BigDecimal saldoEnMora(Movimiento x) {
        return Calc.sumar(x.capitalVencido(), x.cxcInteresCorriente(), x.cxcServicios(), x.cxcMora(), x.cxcCobranza());
    }
}
