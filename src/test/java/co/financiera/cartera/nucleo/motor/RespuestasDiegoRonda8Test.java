package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Ronda 8 de Diego (2026-10-28): cobranza fija por periodo (R8-COBRANZA-PAGO-TARDIO) y servicios que se causan siempre
 * salvo con 2 periodos atrasados previos (R8-PAGO-INCOMPLETO-EN-FECHA). El redondeo al peso (H2) y el sobrante como
 * cuenta por pagar (H8) se prueban en {@code HallazgosAuditoriaTest}; los cuatro, con reglas al azar, en
 * {@code PropiedadesFinancierasAuditTest} (I12 a I14).
 */
class RespuestasDiegoRonda8Test {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final BigDecimal CUARENTA_MIL = bd(40_000);
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

    @Test
    void lasClavesDeLaRondaOchoEstanEnNegocioYNoEnElExcel() {
        ReglasNegocio n = negocio();
        assertThat(n.cobranzaPorPeriodo()).isEqualByComparingTo(CUARENTA_MIL);
        assertThat(n.cuotasVencidasParaSuspenderServicios()).isEqualTo(2);
        assertThat(n.redondeoCuotaAlPeso()).isTrue();
        assertThat(n.sobranteComoCuentaPorPagar()).isTrue();
        assertThat(ReglasNegocio.EXCEL.cobranzaPorPeriodo()).isNull();
        assertThat(ReglasNegocio.EXCEL.cuotasVencidasParaSuspenderServicios()).isNull();
        assertThat(ReglasNegocio.EXCEL.redondeoCuotaAlPeso()).isFalse();
        assertThat(ReglasNegocio.EXCEL.sobranteComoCuentaPorPagar()).isFalse();
        // También se leen de texto (config/reglas-negocio.properties) y validan su signo.
        ReglasNegocio leidas = ReglasNegocio.desdeEstricto(Map.of("cobranzaPorPeriodo", "40000",
                "cuotasVencidasParaSuspenderServicios", "2", "redondeoCuotaAlPeso", "true", "sobranteComoCuentaPorPagar", "true"));
        assertThat(leidas.cobranzaPorPeriodo()).isEqualByComparingTo(CUARENTA_MIL);
        assertThat(leidas.cuotasVencidasParaSuspenderServicios()).isEqualTo(2);
        assertThatThrownBy(() -> ReglasNegocio.EXCEL.conCobranzaPorPeriodo(bd(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReglasNegocio.EXCEL.conCuotasVencidasParaSuspenderServicios(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** R8-COBRANZA-PAGO-TARDIO: la cobranza no se prorratea por días; es un cobro único y fijo por periodo. */
    @Nested
    class CobranzaPorPeriodo {

        @Test
        void elCaso11CobraLosCuarentaMilEnCadaPeriodoAtrasadoDesdeLaTerceraCuotaVencida() {
            List<Movimiento> fija = motor.ejecutar(EscenariosExcel.caso11(P).conReglas(negocio()));
            List<Movimiento> diaria = motor.ejecutar(EscenariosExcel.caso11(P).conReglas(negocio().conCobranzaPorPeriodo(null)));
            assertThat(fila(fija, 43).cobranzaCausada()).isZero();
            assertThat(fila(fija, 44).cobranzaCausada()).isZero();
            for (int periodo = 45; periodo <= 47; periodo++) {
                assertThat(fila(fija, periodo).cobranzaCausada()).as("periodo %d", periodo).isEqualByComparingTo(CUARENTA_MIL);
                // Antes: 7 días × 3.000.
                assertThat(fila(diaria, periodo).cobranzaCausada()).as("periodo %d", periodo).isEqualByComparingTo("21000");
            }
            // Concepto aparte: la cobranza no entra en la cuota financiera ni en los servicios.
            Movimiento m = fila(fija, 45);
            assertThat(m.cuotaFinanciera()).isEqualByComparingTo(fila(diaria, 45).cuotaFinanciera());
            assertThat(m.serviciosCausados()).isEqualTo(fila(diaria, 45).serviciosCausados());
        }

        @Test
        void unPagoTardioDespuesDelUmbralCobraElValorFijoSinImportarLosDias() {
            for (int dias : new int[] {1, 4, 13}) {
                Escenario e = new Escenario(Operacion.ejemploExcel(), P).entre(3, 4, new SinPago(true))
                        .en(5, new PagoContractual(dias)).conReglas(negocio());
                assertThat(fila(motor.ejecutar(e), 5).cobranzaCausada()).as("%d días", dias).isEqualByComparingTo(CUARENTA_MIL);
            }
        }

        @Test
        void elCaso9PagaCompletoPeroTardeYNoLlegaAlUmbralNoGeneraCobranza() {
            assertThat(motor.ejecutar(EscenariosExcel.caso9(P).conReglas(negocio())))
                    .allMatch(m -> m.cobranzaCausada().signum() == 0);
        }

        @Test
        void sinCobranzaActivaOConElExcelNoCambiaNada() {
            assertThat(motor.ejecutar(EscenariosExcel.caso11(P).conReglas(negocio()).conCobranzaActiva(false)))
                    .allMatch(m -> m.cobranzaCausada().signum() == 0);
            // EXCEL con solo el cobro fijo y sin umbral: el pago tardío del caso 9 cobra 40.000 por periodo en vez de días × 3.000.
            List<Movimiento> excelFijo = motor.ejecutar(EscenariosExcel.caso9(P).conReglas(
                    ReglasNegocio.EXCEL.conCobranzaPorPeriodo(CUARENTA_MIL)));
            assertThat(excelFijo).filteredOn(m -> m.cobranzaCausada().signum() > 0).isNotEmpty()
                    .allMatch(m -> m.cobranzaCausada().compareTo(CUARENTA_MIL) == 0);
        }
    }

    /**
     * R8-PAGO-INCOMPLETO-EN-FECHA: la causación genera siempre fondo, asistencia y seguro, haya o no pago; con 2 periodos
     * atrasados previos (desde la tercera cuota vencida) solo se causa el seguro.
     */
    @Nested
    class SuspensionDeServicios {

        @Test
        void sinPagoCausaLosTresHastaDosAtrasosPreviosYDespuesSoloElSeguro() {
            Escenario e = new Escenario(Operacion.ejemploExcel(), P).entre(10, 14, new SinPago(true))
                    .en(15, new Instruccion.PagoAlDia()).conReglas(negocio());
            List<Movimiento> m = motor.ejecutar(e);
            for (int periodo = 10; periodo <= 11; periodo++) {
                Movimiento x = fila(m, periodo);
                assertThat(x.serviciosCausados().fondoGarantias()).as("periodo %d", periodo).isPositive();
                assertThat(x.serviciosCausados().asistencia()).as("periodo %d", periodo).isPositive();
            }
            for (int periodo = 12; periodo <= 14; periodo++) {
                Movimiento x = fila(m, periodo);
                assertThat(fila(m, periodo - 1).cuotasVencidas()).isGreaterThanOrEqualTo(2);
                assertThat(x.serviciosCausados().fondoGarantias()).as("periodo %d", periodo).isZero();
                assertThat(x.serviciosCausados().asistencia()).as("periodo %d", periodo).isZero();
                assertThat(x.serviciosCausados().seguro()).as("periodo %d", periodo).isPositive();
            }
            // Al ponerse al día (ya sin cuotas vencidas previas) vuelven los tres.
            Movimiento despues = fila(m, 16);
            assertThat(fila(m, 15).cuotasVencidas()).isZero();
            assertThat(despues.serviciosCausados().fondoGarantias()).isPositive();
        }

        @Test
        void conD17ElPeriodoSinPagoSoloCausabaElSeguro() {
            Escenario e = new Escenario(Operacion.ejemploExcel(), P).entre(10, 11, new SinPago(true))
                    .conReglas(negocio().conCuotasVencidasParaSuspenderServicios(null));
            Movimiento diez = fila(motor.ejecutar(e), 10);
            assertThat(diez.serviciosCausados().fondoGarantias()).isZero();
            assertThat(diez.serviciosCausados().seguro()).isPositive();
        }

        @Test
        void unPagoTardioCompletoCausaLosTresServicios() {
            // Caso 9: paga completo con días de atraso; antes (D17) solo causaba el seguro esas semanas.
            List<Movimiento> m = motor.ejecutar(EscenariosExcel.caso9(P).conReglas(negocio()));
            assertThat(m.subList(1, m.size())).filteredOn(x -> x.serviciosCausados().seguro().signum() > 0)
                    .allMatch(x -> x.serviciosCausados().fondoGarantias().signum() > 0);
        }
    }
}
