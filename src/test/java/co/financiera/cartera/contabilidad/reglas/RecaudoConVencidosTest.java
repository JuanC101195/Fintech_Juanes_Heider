package co.financiera.cartera.contabilidad.reglas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Recaudo con vencidos (casos 8 y 12): las semanas después de un pago inferior ya tienen asiento. */
class RecaudoConVencidosTest {

    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final Map<String, Escenario> CASOS = Map.ofEntries(
            Map.entry("caso 1", EscenariosExcel.caso1(P)),
            Map.entry("caso 3", EscenariosExcel.caso3(P, true)),
            Map.entry("caso 6", EscenariosExcel.caso6(P)),
            Map.entry("caso 8", EscenariosExcel.caso8(P)),
            Map.entry("caso 9", EscenariosExcel.caso9(P)),
            Map.entry("caso 10", EscenariosExcel.caso10(P)),
            Map.entry("caso 11", EscenariosExcel.caso11(P)),
            Map.entry("caso 12", EscenariosExcel.caso12(P)));

    private static List<Movimiento> correr(Escenario e) {
        return new MotorCartera().ejecutar(e);
    }

    private static BigDecimal suma(List<Asiento> asientos, Cuenta cuenta, Function<Asiento.Linea, BigDecimal> lado) {
        return asientos.stream().flatMap(a -> a.lineas().stream()).filter(l -> l.cuenta() == cuenta).map(lado)
                .reduce(BigDecimal.ZERO, Calc::sumar);
    }

    /** Cada semana con pago debita la caja una sola vez: cuota recibida más abonos o prepago. */
    @TestFactory
    List<DynamicTest> cadaSemanaConPagoDebitaLaCajaUnaSolaVez() {
        return CASOS.entrySet().stream().map(caso -> DynamicTest.dynamicTest(caso.getKey(), () -> {
            for (Movimiento m : correr(caso.getValue())) {
                List<Asiento> asientos = Contabilizador.estandar().contabilizar(m);
                BigDecimal caja = suma(asientos, Cuenta.CAJA, Asiento.Linea::debito);
                BigDecimal esperado = Calc.sumar(m.pagoRecibido(), m.abonoExtra(), m.prepagoCapital());
                assertThat(caja).as("%s, periodo %d", caso.getKey(), m.periodo()).isCloseTo(esperado, within(UN_PESO));
            }
        })).toList();
    }

    /** La cuenta Cartera capital vencida termina con el capital vencido del motor. */
    @Test
    void laCarteraVencidaContableEsElCapitalVencidoDelMotor() {
        for (String caso : List.of("caso 8", "caso 12")) {
            List<Movimiento> motor = correr(CASOS.get(caso));
            List<Asiento> asientos = Contabilizador.estandar().contabilizar(motor);
            BigDecimal saldo = Calc.restar(suma(asientos, Cuenta.CARTERA_VENCIDA, Asiento.Linea::debito),
                    suma(asientos, Cuenta.CARTERA_VENCIDA, Asiento.Linea::credito));
            assertThat(saldo).as(caso).isCloseTo(motor.getLast().capitalVencido(), within(UN_PESO));
            assertThat(asientos).isNotEmpty().allSatisfy(a -> assertThat(a.cuadra()).isTrue());
        }
    }

    @Test
    void aplicaALasSemanasQueRecuperanVencidosYNoAOtras() {
        List<Movimiento> caso8 = correr(CASOS.get("caso 8"));
        assertThat(caso8).filteredOn(RecaudoConVencidos::aplica).extracting(Movimiento::periodo)
                .first().isEqualTo(44);
        assertThat(caso8).filteredOn(RecaudoConVencidos::aplica).hasSize(57);

        List<Movimiento> caso12 = correr(CASOS.get("caso 12"));
        assertThat(caso12).filteredOn(RecaudoConVencidos::aplica).extracting(Movimiento::periodo)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(44, 60).boxed().toList());

        assertThat(correr(CASOS.get("caso 1"))).noneMatch(RecaudoConVencidos::aplica);
        assertThat(correr(CASOS.get("caso 9"))).noneMatch(RecaudoConVencidos::aplica);
    }

    /** La normalización del caso 12 devuelve a vigente todo el capital vencido. */
    @Test
    void laNormalizacionRecuperaElVencidoAVigente() {
        Movimiento normalizacion = correr(CASOS.get("caso 12")).get(60);
        Asiento a = new RecaudoConVencidos().contabilizar(normalizacion).orElseThrow();
        assertThat(suma(List.of(a), Cuenta.CARTERA_VENCIDA, Asiento.Linea::credito))
                .isCloseTo(normalizacion.capitalVencidoInicial(), within(UN_PESO));
        assertThat(normalizacion.capitalVencido()).isCloseTo(BigDecimal.ZERO, within(UN_PESO));
    }

    /**
     * Una cuota pagada a tiempo después de un pago tardío paga primero la cobranza pendiente; el asiento la
     * acredita a CxC gastos de cobranza y cuadra (antes faltaba esa línea y el asiento se descuadraba).
     */
    @Test
    void laCobranzaPendienteQuePagaLaCuotaSeAcreditaYElAsientoCuadra() {
        Escenario e = new Escenario(co.financiera.cartera.nucleo.Operacion.ejemploExcel(), P)
                .en(40, new co.financiera.cartera.nucleo.motor.Instruccion.SinPago(true))
                .en(41, new co.financiera.cartera.nucleo.motor.Instruccion.PagoValor(Calc.bd(5_000), 3));
        Movimiento semana = correr(e).get(42);
        assertThat(semana.pagoCobranza()).isPositive();
        Asiento asiento = new RecaudoConVencidos().contabilizar(semana).orElseThrow();
        assertThat(asiento.cuadra()).isTrue();
        assertThat(suma(List.of(asiento), Cuenta.CXC_GASTOS_COBRANZA, Asiento.Linea::credito))
                .isEqualByComparingTo(semana.pagoCobranza());
    }
}
