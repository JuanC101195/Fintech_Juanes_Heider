package co.financiera.cartera.excel;

import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Compara el cronograma del motor contra una hoja del Excel, celda por celda. Cada caso declara
 * qué columna del Excel corresponde a qué dato del {@link Movimiento}. Reporta todas las
 * diferencias juntas (no se detiene en la primera) para que sea fácil ver el patrón.
 */
public final class ComparadorExcel {

    /** Tolerancia de dinero de la hoja Inputs (fila 71): 1 COP. */
    public static final BigDecimal TOLERANCIA_DINERO = BigDecimal.ONE;
    /** Tolerancia de tasas (fila 72). */
    public static final BigDecimal TOLERANCIA_TASA = new BigDecimal("0.000001");

    private final Map<String, Function<Movimiento, BigDecimal>> columnas = new LinkedHashMap<>();
    private final Map<String, BigDecimal> tolerancias = new LinkedHashMap<>();

    public ComparadorExcel dinero(String columnaExcel, Function<Movimiento, BigDecimal> dato) {
        columnas.put(columnaExcel, dato);
        tolerancias.put(columnaExcel, TOLERANCIA_DINERO);
        return this;
    }

    public ComparadorExcel tasa(String columnaExcel, Function<Movimiento, BigDecimal> dato) {
        columnas.put(columnaExcel, dato);
        tolerancias.put(columnaExcel, TOLERANCIA_TASA);
        return this;
    }

    /** Devuelve una línea por cada celda que se sale de la tolerancia, más las filas que sobran o faltan. */
    public List<String> diferencias(HojaExcel hoja, List<Movimiento> motor) {
        List<String> errores = new ArrayList<>();
        String colPeriodo = hoja.encabezados().get(0);
        for (Map<String, String> fila : hoja.filas()) {
            int periodo = Integer.parseInt(fila.get(colPeriodo));
            Optional<Movimiento> mov = motor.stream().filter(m -> m.periodo() == periodo).findFirst();
            if (mov.isEmpty()) {
                errores.add("periodo " + periodo + ": el Excel lo tiene y el motor no");
                continue;
            }
            for (var c : columnas.entrySet()) {
                Optional<BigDecimal> esperado = HojaExcel.numero(fila, c.getKey());
                if (esperado.isEmpty()) {
                    continue;
                }
                BigDecimal obtenido = c.getValue().apply(mov.get());
                BigDecimal diferencia = obtenido.subtract(esperado.get()).abs();
                if (diferencia.compareTo(tolerancias.get(c.getKey())) > 0) {
                    errores.add(String.format("periodo %d · %s: Excel %s, motor %s", periodo, c.getKey(),
                            esperado.get().toPlainString(), obtenido.stripTrailingZeros().toPlainString()));
                }
            }
        }
        int ultimoExcel = hoja.filas().stream().mapToInt(f -> Integer.parseInt(f.get(colPeriodo))).max().orElse(0);
        motor.stream().filter(m -> m.periodo() > ultimoExcel)
                .forEach(m -> errores.add("periodo " + m.periodo() + ": el motor lo tiene y el Excel no"));
        return errores;
    }
}
