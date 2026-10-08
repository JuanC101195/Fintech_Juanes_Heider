package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.excel.HojaExcel;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Hoja Validaciones, pruebas 1 a 5 (caso 1). El periodo, el tipo y la tolerancia salen de la hoja
 * Validaciones; el valor esperado sale de la hoja Inputs o de Caso1_Normal (la columna "Resultado
 * software" está vacía: es la que este software llena).
 */
class Caso1ValidacionesTest {

    private final List<Movimiento> motor = new MotorCartera()
            .ejecutar(new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel()));
    private final HojaExcel validaciones = HojaExcel.cargar("validaciones");
    private final HojaExcel inputs = HojaExcel.cargar("inputs");
    private final HojaExcel caso1 = HojaExcel.cargar("Caso1_Normal");

    private Map<String, String> validacion(int numero) {
        return validaciones.filaDelPeriodo(numero); // la primera columna de la hoja es "#"
    }

    private BigDecimal input(String celda) {
        Map<String, String> fila = inputs.filas().stream().filter(f -> f.get("celda").equals(celda)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Inputs no tiene la celda " + celda));
        return HojaExcel.numero(fila, "valor").orElseThrow();
    }

    /** Compara el motor con el valor esperado en el periodo y con la tolerancia que dice la validación. */
    private void validar(int numero, String variable, BigDecimal esperado, Function<Movimiento, BigDecimal> dato) {
        Map<String, String> v = validacion(numero);
        assertThat(v.get("Caso")).isEqualTo("Caso 1");
        assertThat(v.get("Variable")).isEqualTo(variable);
        int periodo = Integer.parseInt(v.get("Periodo"));
        BigDecimal tolerancia = new BigDecimal(v.get("Tolerancia"));
        BigDecimal esperadaPorTipo = switch (v.get("Tipo")) {
            case "Dinero" -> input("B71");
            case "Tasa" -> input("B72");
            default -> throw new IllegalStateException(v.get("Tipo"));
        };
        assertThat(tolerancia).isEqualByComparingTo(esperadaPorTipo);

        Movimiento m = motor.stream().filter(x -> x.periodo() == periodo).findFirst().orElseThrow();
        assertThat(dato.apply(m)).as("Validación %d · %s (periodo %d)", numero, variable, periodo)
                .isCloseTo(esperado, Offset.offset(tolerancia));
    }

    private BigDecimal enCaso1(int periodo, String columna) {
        return HojaExcel.numero(caso1.filaDelPeriodo(periodo), columna).orElseThrow();
    }

    @Test
    void validacion1MontoDesembolsado() {
        // Inputs!B3 = Caso1_Normal!D3 = N3.
        assertThat(enCaso1(0, "Saldo final")).isEqualByComparingTo(input("B3"));
        validar(1, "Monto desembolsado", input("B3"), Movimiento::saldoFinal);
        validar(1, "Monto desembolsado", input("B3"), Movimiento::saldoInicial);
    }

    @Test
    void validacion2TasaSemanal() {
        // Inputs!B17 = Caso1_Normal!E4.
        assertThat(enCaso1(1, "Tasa semanal")).isEqualByComparingTo(input("B17"));
        validar(2, "Tasa semanal", input("B17"), Movimiento::tasaSemanal);
    }

    @Test
    void validacion3CuotaFinanciera() {
        // Inputs!B20 = PMT(B17, B15, −B3) = Caso1_Normal!G4.
        assertThat(enCaso1(1, "Cuota financiera")).isEqualByComparingTo(input("B20"));
        validar(3, "Cuota financiera", input("B20"), Movimiento::cuotaFinanciera);
        validar(3, "Cuota financiera", input("B20"), m -> Calc.sumar(m.pagoInteresCorriente(), m.pagoCapital()));
    }

    @Test
    void validacion4SaldoFinal() {
        // Caso1_Normal!N89 (periodo 86) = W8 ≈ 4,29e-9.
        validar(4, "Saldo final", enCaso1(86, "Saldo final"), Movimiento::saldoFinal);
        assertThat(motor.getLast().periodo()).isEqualTo(86);
    }

    @Test
    void validacion5PagoFccDelMes1() {
        // Caso1_Normal!O7 (periodo 4 = ROUNDDOWN(Inputs!B14)) = W9; es 4 × Inputs!B28.
        BigDecimal esperado = enCaso1(4, "Pago FCC");
        assertThat(esperado).isCloseTo(Calc.multiplicar(Calc.bd(4), input("B28")), Offset.offset(BigDecimal.ONE));
        validar(5, "Pago FCC", esperado, m -> m.pagoTerceros().fcc());
    }
}
