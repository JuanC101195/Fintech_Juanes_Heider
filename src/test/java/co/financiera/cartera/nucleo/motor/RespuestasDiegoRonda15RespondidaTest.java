package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.AlcanceCobranzaPorCuota;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Respuestas de Diego a la ronda 15 y a R14-MARCA-SEMANAS (2026-11-18): D91 (cobranza de 25.000 también con pendientes,
 * permanente) y D92 (una sola medida de cuotas vencidas: saldo en mora ÷ cuota total).
 */
class RespuestasDiegoRonda15RespondidaTest {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final BigDecimal CARGO = bd(25_000);
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

    /** D91 (R15-COBRANZA-CUOTA = por-cuota-con-pendientes). */
    @Nested
    class CobranzaConPendientes {

        @Test
        void negocioCobraTambienCuandoLaCuotaSePagaATiempoYQuedaPendienteDeAntes() {
            assertThat(negocio().alcanceCobranzaPorCuota()).isEqualTo(AlcanceCobranzaPorCuota.CON_PENDIENTES);
            // Semana 2 sin pago; la 3 paga solo su cuota a tiempo: lo de la 2 sigue pendiente, así que la 3 también causa.
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().en(2, new SinPago(true))
                    .en(3, new PagoContractual(0)).conReglas(negocio()));
            assertThat(fila(m, 2).cobranzaCausada()).isEqualByComparingTo(CARGO);
            assertThat(fila(m, 3).cobranzaCausada()).isEqualByComparingTo(CARGO);
        }

        @Test
        void conElSupuestoAnteriorLaCuotaPagadaATiempoNoCausa() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().en(2, new SinPago(true))
                    .en(3, new PagoContractual(0))
                    .conReglas(negocio().conAlcanceCobranzaPorCuota(AlcanceCobranzaPorCuota.CUOTA_DEL_PERIODO)));
            assertThat(fila(m, 2).cobranzaCausada()).isEqualByComparingTo(CARGO);
            assertThat(fila(m, 3).cobranzaCausada()).isZero();
        }

        @Test
        void siElPagoCubreLoPendienteYLaCuotaNoCausaYUnaSolaVezPorPeriodo() {
            // La 3 paga 1.000.000 (más que lo pendiente de la 2 + su cuota): al día, sin cargo.
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().en(2, new SinPago(true))
                    .en(3, new PagoValor(bd(1_000_000), 0)).conReglas(negocio()));
            assertThat(fila(m, 3).cobranzaCausada()).isZero();
            assertThat(fila(m, 3).estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);
            // Pago tarde y además con pendiente de antes: un solo cargo en el periodo.
            List<Movimiento> t = motor.ejecutar(conOperacionDelExcel().en(2, new SinPago(true))
                    .en(3, new PagoContractual(3)).conReglas(negocio()));
            assertThat(fila(t, 3).cobranzaCausada()).isEqualByComparingTo(CARGO);
        }

        @Test
        void unClienteAlDiaNoCausaNada() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().conReglas(negocio()));
            assertThat(m.stream().map(Movimiento::cobranzaCausada).reduce(Calc.CERO, Calc::sumar)).isZero();
        }
    }

    /** D92 (R14-MARCA-SEMANAS): una sola medida, saldo en mora ÷ cuota total. */
    @Nested
    class ContadorPorCociente {

        @Test
        void elEjemploDeLaPreguntaYaNoQuedaConCuatroCuotasSinMarca() {
            assertThat(negocio().contadorVencidasPorCociente()).isTrue();
            // No paga las semanas 1 a 4 y en la 5 paga 800.000 (ejemplo de R14-MARCA-SEMANAS).
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 4, new SinPago(true))
                    .en(5, new PagoValor(bd(800_000), 0)).conReglas(negocio()));
            assertThat(fila(m, 4).cuotasVencidas()).isGreaterThanOrEqualTo(2);
            assertThat(fila(m, 4).listoParaRecuperacion()).isTrue();
            // Tras el abono el cociente baja de 1: el contador y la marca dicen lo mismo.
            assertThat(fila(m, 5).cuotasVencidas()).isLessThan(2);
            assertThat(fila(m, 5).listoParaRecuperacion()).isFalse();
        }

        @Test
        void elContadorYLaMarcaCoincidenEnCadaPeriodo() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 4, new SinPago(true))
                    .en(5, new PagoValor(bd(800_000), 0)).entre(6, 9, new SinPago(true)).conReglas(negocio()));
            for (Movimiento x : m) {
                assertThat(x.listoParaRecuperacion()).as("periodo %d", x.periodo()).isEqualTo(x.cuotasVencidas() >= 2);
            }
        }

        @Test
        void sinLaClaveSeSiguenContandoPeriodos() {
            List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 4, new SinPago(true))
                    .en(5, new PagoValor(bd(800_000), 0)).conReglas(negocio().conContadorVencidasPorCociente(false)));
            assertThat(fila(m, 5).cuotasVencidas()).isEqualTo(4);
        }
    }
}
