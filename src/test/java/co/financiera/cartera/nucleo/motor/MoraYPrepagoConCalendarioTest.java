package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.DiasMora;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ADR 0002 paso 8: el motor usa el {@link CalendarioPagos} del escenario para (1) la mora de un crédito mensual por los
 * días reales del mes (PPER-5, D27) y (2) el interés de un prepago a mitad de periodo por los días transcurridos entre
 * el vencimiento de la cuota y la fecha del prepago (PPER-7, D27).
 *
 * <p>Cifras a mano (monto 5.904.400, EA 87,62 %, mensual de 24 cuotas): tasa mensual 1,8762^(1/12) − 1 =
 * 0,0538365595; diaria 1,8762^(1/365) − 1 = 0,0017254553; cuota PAGO = 444.006,17; capital de la cuota 1 =
 * 444.006,17 − 5.904.400 × 0,0538365595 = 126.133,58. Si la cuota 1 no se paga, ese capital queda vencido y en el
 * periodo 2 causa mora = 126.133,58 × ((1 + diaria)^días − 1): 28 días 6.237,95; 30 días 6.695,15; 31 días 6.924,34.
 * Con la tasa mensual (periodo nominal, 30,42 días) son 6.790,60.
 */
class MoraYPrepagoConCalendarioTest {

    private static final ParametrosProducto MENSUAL = ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.MENSUAL, 24);
    private static final ReglasNegocio DIAS_REALES = ReglasNegocio.EXCEL.conDiasMora(DiasMora.DIAS_CALENDARIO_MENSUAL);
    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    private final MotorCartera motor = new MotorCartera();

    private static Movimiento fila(List<Movimiento> m, int periodo) {
        return m.stream().filter(x -> x.periodo() == periodo).findFirst().orElseThrow();
    }

    /** Cuota 1 sin pago (su capital queda vencido) y cuota 2 pagada a tiempo. */
    private static Escenario cuotaUnoSinPago(ParametrosProducto p) {
        return new Escenario(Operacion.ejemploExcel(), p).en(1, new SinPago(true));
    }

    /** Mora del periodo 2 con un calendario mensual que empieza el desembolso {@code desembolso}. */
    private BigDecimal moraDelPeriodo2(LocalDate desembolso, LocalDate primerVencimiento) {
        CalendarioPagos cal = CalendarioPagos.mensual(desembolso, primerVencimiento);
        return fila(motor.ejecutar(cuotaUnoSinPago(MENSUAL).conReglas(DIAS_REALES).conCalendario(cal)), 2).interesMora();
    }

    @Nested
    class MoraMensualPorDiasReales {

        @Test
        void febreroDe28Dias() {
            // 5-feb-2026 → 5-mar-2026: 28 días.
            assertThat(CalendarioPagos.mensual(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 5)).diasDelPeriodo(2))
                    .isEqualTo(28);
            assertThat(moraDelPeriodo2(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 5)))
                    .isCloseTo(bd("6237.95"), within(UN_PESO));
        }

        @Test
        void abrilDe30Dias() {
            // 5-abr-2026 → 5-may-2026: 30 días.
            assertThat(moraDelPeriodo2(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 5)))
                    .isCloseTo(bd("6695.15"), within(UN_PESO));
        }

        @Test
        void marzoDe31Dias() {
            // 5-mar-2026 → 5-abr-2026: 31 días.
            assertThat(moraDelPeriodo2(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 5)))
                    .isCloseTo(bd("6924.34"), within(UN_PESO));
        }

        @Test
        void sinFechasOConElExcelSigueLaTasaMensual() {
            CalendarioPagos cal = CalendarioPagos.mensual(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 5));
            BigDecimal sinFechas = fila(motor.ejecutar(cuotaUnoSinPago(MENSUAL).conReglas(DIAS_REALES)), 2).interesMora();
            BigDecimal excel = fila(motor.ejecutar(cuotaUnoSinPago(MENSUAL).conCalendario(cal)), 2).interesMora();
            assertThat(sinFechas).isCloseTo(bd("6790.60"), within(UN_PESO));
            assertThat(excel).isEqualByComparingTo(sinFechas);
        }

        /** Diego solo decidió la mensual (D27): la quincenal sigue con la tasa del periodo aunque haya fechas. */
        @Test
        void quincenalNoCambia() {
            ParametrosProducto q = ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.QUINCENAL, 48);
            CalendarioPagos cal = CalendarioPagos.quincenal(LocalDate.of(2026, 1, 25), LocalDate.of(2026, 2, 5));
            BigDecimal conDias = fila(motor.ejecutar(cuotaUnoSinPago(q).conReglas(DIAS_REALES).conCalendario(cal)), 2)
                    .interesMora();
            BigDecimal nominal = fila(motor.ejecutar(cuotaUnoSinPago(q)), 2).interesMora();
            // A mano: capital de la cuota 1 = 62.240,08 × tasa quincenal 0,0265656 = 1.653,43.
            assertThat(nominal).isCloseTo(bd("1653.43"), within(UN_PESO));
            assertThat(conDias).isEqualByComparingTo(nominal);
        }

        @Test
        void laClaveVieneDelPerfilDeNegocio() throws IOException {
            assertThat(ReglasNegocio.EXCEL.diasMora()).isEqualTo(DiasMora.PERIODO_NOMINAL);
            assertThat(ReglasNegocio.desde(ReglasNegocioConfiguradaTest.configuracion()).diasMora())
                    .isEqualTo(DiasMora.DIAS_CALENDARIO_MENSUAL);
            assertThat(ReglasNegocio.CLAVES).contains("diasMora");
            assertThat(ReglasNegocio.desde(Map.of("diasMora", "PERIODO_NOMINAL")).diasMora())
                    .isEqualTo(DiasMora.PERIODO_NOMINAL);
        }
    }

    /**
     * Prepago a mitad de periodo con fechas (PPER-7). Crédito mensual desembolsado el 1-ene-2026, primera cuota el
     * 5-feb. Después de pagar la cuota 1 el saldo es 5.904.400 − 126.133,58 = 5.778.266,42; el interés de d días es
     * saldo × ((1 + diaria)^d − 1): 10 días (15-feb) 100.479,11; 17 días (22-feb) 171.852,30.
     */
    @Nested
    class PrepagoConFecha {

        private final CalendarioPagos cal = CalendarioPagos.mensual(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 5));
        private final ReglasNegocio reglas = ReglasNegocio.EXCEL.conInteresPrepagoPorDias(true);

        private Movimiento prepagoEn(Instruccion prepago, CalendarioPagos calendario) {
            Escenario e = new Escenario(Operacion.ejemploExcel(), MENSUAL).en(1, prepago).conReglas(reglas);
            return fila(motor.ejecutar(calendario == null ? e : e.conCalendario(calendario)), 1);
        }

        @Test
        void cobraLosDiasRealesDesdeElVencimiento() {
            assertThat(prepagoEn(PrepagoTotal.enFecha(LocalDate.of(2026, 2, 15)), cal).interesPrepago())
                    .isCloseTo(bd("100479.11"), within(UN_PESO));
            assertThat(prepagoEn(PrepagoTotal.enFecha(LocalDate.of(2026, 2, 22)), cal).interesPrepago())
                    .isCloseTo(bd("171852.30"), within(UN_PESO));
        }

        @Test
        void daLoMismoQueDigitarLosDias() {
            Movimiento conFecha = prepagoEn(PrepagoTotal.enFecha(LocalDate.of(2026, 2, 22)), cal);
            Movimiento digitado = prepagoEn(new PrepagoTotal(17), null);
            assertThat(conFecha).isEqualTo(digitado);
        }

        @Test
        void elMismoDiaOAntesDelVencimientoNoCobraDias() {
            assertThat(prepagoEn(PrepagoTotal.enFecha(LocalDate.of(2026, 2, 5)), cal).interesPrepago()).isZero();
            assertThat(prepagoEn(PrepagoTotal.enFecha(LocalDate.of(2026, 2, 3)), cal).interesPrepago()).isZero();
        }

        @Test
        void desdeElVencimientoSiguienteVaEnOtroPeriodo() {
            // Febrero de 2026 tiene 28 días: el 5-mar ya vence la cuota 2.
            assertThatThrownBy(() -> prepagoEn(PrepagoTotal.enFecha(LocalDate.of(2026, 3, 5)), cal))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("periodo 2");
            assertThat(prepagoEn(PrepagoTotal.enFecha(LocalDate.of(2026, 3, 4)), cal).interesPrepago())
                    .isPositive();
        }

        @Test
        void conFechaNecesitaCalendarioYNoSeMezclaConDiasDigitados() {
            assertThatThrownBy(() -> prepagoEn(PrepagoTotal.enFecha(LocalDate.of(2026, 2, 15)), null))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> new PrepagoTotal(3, LocalDate.of(2026, 2, 15)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void conElExcelNoCobraDias() {
            Escenario e = new Escenario(Operacion.ejemploExcel(), MENSUAL)
                    .en(1, PrepagoTotal.enFecha(LocalDate.of(2026, 2, 22))).conCalendario(cal);
            assertThat(fila(motor.ejecutar(e), 1).interesPrepago()).isZero();
        }
    }
}
