package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.reglas.RecaudoCuota;
import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** Caso 1 · Regla contable del recaudo de una cuota normal (R-1.23, hoja Caso1_Normal V12:Y17). */
class Caso1RecaudoCuotaTest {

    private static final Offset<BigDecimal> UN_PESO = Offset.offset(BigDecimal.ONE);

    private final RecaudoCuota regla = new RecaudoCuota();
    private final HojaExcel hoja = HojaExcel.cargar("Caso1_Normal");
    private final List<Movimiento> caso1 = correr(new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel()));

    private static List<Movimiento> correr(Escenario e) {
        return new MotorCartera().ejecutar(e);
    }

    private static BigDecimal debito(Asiento a, Cuenta c) {
        return a.lineas().stream().filter(l -> l.cuenta() == c).map(Asiento.Linea::debito)
                .reduce(Calc.CERO, (x, y) -> Calc.sumar(x, y));
    }

    private static BigDecimal credito(Asiento a, Cuenta c) {
        return a.lineas().stream().filter(l -> l.cuenta() == c).map(Asiento.Linea::credito)
                .reduce(Calc.CERO, (x, y) -> Calc.sumar(x, y));
    }

    private static BigDecimal celda(Map<String, String> fila, String columna) {
        return HojaExcel.numero(fila, columna).orElseThrow();
    }

    @Test
    void reproduceElPanelContableDelPeriodo1() {
        Asiento a = regla.contabilizar(caso1.get(1)).orElseThrow();

        assertThat(a.periodo()).isEqualTo(1);
        assertThat(a.lineas()).hasSize(4);
        // Valores del panel V12:Y17 (W13 = M4, X14 = F4, X15 = H4, X16 = L4).
        assertThat(debito(a, Cuenta.CAJA)).isCloseTo(new BigDecimal("174048.9941844608"), UN_PESO);
        assertThat(credito(a, Cuenta.INGRESOS_INTERESES)).isCloseTo(new BigDecimal("71698.8767877002"), UN_PESO);
        assertThat(credito(a, Cuenta.CARTERA_VIGENTE)).isCloseTo(new BigDecimal("39315.94508906829"), UN_PESO);
        assertThat(credito(a, Cuenta.CXP_SERVICIOS_TERCEROS)).isCloseTo(new BigDecimal("63034.172307692315"), UN_PESO);
        assertThat(a.cuadra()).isTrue();
        assertThat(a.lineas()).extracting(l -> l.cuenta().nombreExcel())
                .containsExactly("Caja", "Ingresos por intereses", "Cartera de créditos", "CxP servicios a terceros");
    }

    @Test
    void cadaCuotaDelCaso1GeneraElMismoAsientoConLasColumnasDeSuFila() {
        for (Movimiento m : caso1.subList(1, caso1.size())) {
            Map<String, String> fila = hoja.filaDelPeriodo(m.periodo());
            Asiento a = regla.contabilizar(m).orElseThrow(() -> new AssertionError("sin asiento en " + m.periodo()));
            assertThat(debito(a, Cuenta.CAJA)).as("Caja %d", m.periodo()).isCloseTo(celda(fila, "Pago total cliente"), UN_PESO);
            assertThat(credito(a, Cuenta.INGRESOS_INTERESES)).isCloseTo(celda(fila, "Interés corriente"), UN_PESO);
            assertThat(credito(a, Cuenta.CARTERA_VIGENTE)).isCloseTo(celda(fila, "Capital pagado"), UN_PESO);
            assertThat(credito(a, Cuenta.CXP_SERVICIOS_TERCEROS)).isCloseTo(celda(fila, "Servicios"), UN_PESO);
        }
    }

    @Test
    void elDesembolsoNoEsUnRecaudo() {
        assertThat(regla.contabilizar(caso1.getFirst())).isEmpty();
    }

    @Test
    void elContabilizadorEstandarLaTieneRegistradaYLaCarteraSeExtingueCompleta() {
        List<Asiento> asientos = Contabilizador.estandar().contabilizar(caso1);
        List<Asiento> recaudos = asientos.stream().filter(a -> debito(a, Cuenta.CAJA).signum() > 0).toList();
        assertThat(recaudos).hasSize(86);
        BigDecimal carteraAbonada = recaudos.stream().map(a -> credito(a, Cuenta.CARTERA_VIGENTE)).reduce(Calc.CERO, (x, y) -> Calc.sumar(x, y));
        assertThat(carteraAbonada).isCloseTo(new BigDecimal("5904400"), UN_PESO);
    }

    // ---- Frontera con los casos 5 a 9: lo que no es un recaudo normal no se contabiliza aquí. ----

    @Test
    void unPagoParcialYLaSemanaQuePagaLoVencidoQuedanParaLaReglaDelCaso8() {
        List<Movimiento> movs = correr(new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel())
                .en(43, new Instruccion.PagoParcial(new BigDecimal("0.4"), 0)));

        assertThat(regla.contabilizar(movs.get(42))).isPresent();
        assertThat(regla.contabilizar(movs.get(43))).as("periodo 43, pago del 40 %").isEmpty();
        assertThat(regla.contabilizar(movs.get(44))).as("periodo 44, paga mora y vencidos").isEmpty();
        assertThat(movs.get(43).pagoRecibido()).isPositive();
    }

    @Test
    void unPagoTardioConCobranzaQuedaParaLaReglaDelCaso9() {
        List<Movimiento> movs = correr(new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel())
                .en(43, new Instruccion.PagoContractual(14)));

        assertThat(movs.get(43).cobranzaCausada()).isPositive();
        assertThat(regla.contabilizar(movs.get(43))).isEmpty();
    }

    @Test
    void unaSemanaSinPagoNoGeneraRecaudo() {
        List<Movimiento> movs = correr(new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel())
                .en(10, new Instruccion.SinPago(true)));
        assertThat(regla.contabilizar(movs.get(10))).isEmpty();
    }

    @Test
    void conAbonoExtraOPrepagoSoloContabilizaLaCuotaOrdinaria() {
        List<Movimiento> abono = correr(new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel())
                .en(43, new Instruccion.AbonoExtra(new BigDecimal("2000000"), Instruccion.ModalidadAbono.MENOR_PLAZO)));
        Movimiento m = abono.get(43);
        Optional<Asiento> a = regla.contabilizar(m);
        assertThat(a).isPresent();
        assertThat(debito(a.get(), Cuenta.CAJA)).isCloseTo(m.pagoRecibido(), UN_PESO);
        assertThat(debito(a.get(), Cuenta.CAJA)).isCloseTo(m.pagoTotalCliente().subtract(new BigDecimal("2000000")), UN_PESO);

        List<Movimiento> prepago = correr(new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel())
                .en(43, new Instruccion.PrepagoTotal()));
        Movimiento p = prepago.get(43);
        Asiento ap = regla.contabilizar(p).orElseThrow();
        assertThat(credito(ap, Cuenta.CARTERA_VIGENTE)).isCloseTo(p.pagoCapital(), UN_PESO);
        assertThat(p.prepagoCapital()).isPositive();
        assertThat(debito(ap, Cuenta.CAJA)).isCloseTo(p.pagoRecibido(), UN_PESO);
    }
}
