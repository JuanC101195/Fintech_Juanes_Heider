package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.AlcanceProrrateo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Ronda 13 de Diego (2026-11-02): una prueba por decisión, D84 y D85 (ver {@code docs/respuestas-diego-2026-11-02-ronda13.md}).
 * Congelar el crédito (D86) y los servicios no recaudados al cierre (pendiente) no tienen código en el motor.
 */
class RespuestasDiegoRonda13Test {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
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

    /** Desembolso sábado 10-oct-2026 y primer martes 13-oct-2026: 3 días de 7. */
    private static final CalendarioPagos TRES_DIAS = CalendarioPagos.semanal(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 13));

    /** D85 (R13-COBRANZA-TRAS-MARCA): la marca se activa con 2 o más cuotas de mora y se desactiva cuando baja de ahí. */
    @Nested
    class MarcaSeDesactiva {

        @Test
        void conMarcaPorCocienteLaMarcaNoPersiste() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 3, new SinPago(true))
                    .en(4, new PagoValor(bd(600_000), 0)).conReglas(negocio()));
            assertThat(fila(m, 2).listoParaRecuperacion()).isTrue();
            assertThat(fila(m, 3).listoParaRecuperacion()).isTrue();
            // En la 4 el cociente baja de 2: se quita la marca y la cobranza de la 5 es cero.
            assertThat(fila(m, 4).listoParaRecuperacion()).isFalse();
            assertThat(fila(m, 5).cobranzaCausada()).isZero();
        }

        @Test
        void sinMarcaPorCocienteSigueLaReglaAnteriorQuePersiste() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 3, new SinPago(true))
                    .en(4, new PagoValor(bd(600_000), 0)).conReglas(negocio().conMarcaPorCociente(false)));
            assertThat(fila(m, 4).listoParaRecuperacion()).isTrue();
        }

        @Test
        void laMarcaVuelveSiElSaldoEnMoraVuelveADosCuotas() {
            // Sin pago en la 1 a 3, paga mucho en la 4 (se quita) y vuelve a quedar sin pago de la 5 a la 8.
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 3, new SinPago(true))
                    .en(4, new PagoValor(bd(900_000), 0)).entre(5, 8, new SinPago(true)).conReglas(negocio()));
            assertThat(fila(m, 4).listoParaRecuperacion()).isFalse();
            assertThat(m.stream().filter(x -> x.periodo() >= 5).anyMatch(Movimiento::listoParaRecuperacion)).isTrue();
        }
    }

    /** D84 (R13-PRIMERA-CUOTA-INTERES): el capital que la primera cuota no cobra no causa interés. */
    @Nested
    class PrimeraCuotaSinInteres {

        private List<Movimiento> correr(ReglasNegocio reglas) {
            return motor.ejecutar(conOperacionDelExcel().conReglas(reglas).conCalendario(TRES_DIAS));
        }

        @Test
        void laUltimaCuotaSoloSumaElCapitalNoCobrado() {
            List<Movimiento> sin = correr(negocio());
            assertThat(negocio().alcanceProrrateoPrimeraCuota()).isEqualTo(AlcanceProrrateo.CUOTA_COMPLETA_SIN_INTERES);
            // 100.406,51 de la cuota fija + 16.299,13 de capital no cobrado = 116.705,64 (el ejemplo de la pregunta).
            assertThat(fila(sin, 104).cuotaFinanciera()).isCloseTo(new BigDecimal("116705.64"), within(new BigDecimal("1")));
            // Con interés (D79) subía a 157.091,51.
            List<Movimiento> con = correr(negocio().conAlcanceProrrateoPrimeraCuota(AlcanceProrrateo.CUOTA_COMPLETA));
            assertThat(fila(con, 104).cuotaFinanciera()).isCloseTo(new BigDecimal("157091.51"), within(new BigDecimal("1")));
        }

        @Test
        void laCuotaFijaYElPlanSiguenIgualYElCapitalCierraEnLaCuotaN() {
            List<Movimiento> m = correr(negocio());
            assertThat(m.getLast().periodo()).isEqualTo(104);
            assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
            BigDecimal cuotaFija = fila(m, 2).cuotaFinanciera();
            assertThat(fila(m, 103).cuotaFinanciera()).isEqualByComparingTo(cuotaFija);
            BigDecimal capital = m.stream().map(Movimiento::capitalContractual).reduce(Calc.CERO, Calc::sumar);
            assertThat(capital).isCloseTo(Operacion.ejemploExcel().montoFinanciado(), within(CENTAVO));
        }

        @Test
        void sinFechasNoCambiaNada() {
            List<Movimiento> a = motor.ejecutar(conOperacionDelExcel().conReglas(negocio()));
            List<Movimiento> b = motor.ejecutar(conOperacionDelExcel().conReglas(negocio().conAlcanceProrrateoPrimeraCuota(AlcanceProrrateo.CUOTA_COMPLETA)));
            assertThat(fila(a, 104).cuotaFinanciera()).isEqualByComparingTo(fila(b, 104).cuotaFinanciera());
        }
    }
}
