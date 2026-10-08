package co.financiera.cartera.excel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Una hoja del Excel exportada a CSV por herramientas/exportar_excel.py. Cada fila es un mapa
 * columna → texto. Las columnas se buscan por su encabezado exacto del Excel.
 */
public final class HojaExcel {

    private final String nombre;
    private final List<String> encabezados;
    private final List<Map<String, String>> filas;

    private HojaExcel(String nombre, List<String> encabezados, List<Map<String, String>> filas) {
        this.nombre = nombre;
        this.encabezados = encabezados;
        this.filas = filas;
    }

    public static HojaExcel cargar(String hoja) {
        String recurso = "/excel/" + hoja + ".csv";
        try (InputStream in = HojaExcel.class.getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalArgumentException("No existe el recurso " + recurso + ": corre herramientas/exportar_excel.py");
            }
            BufferedReader lector = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> encabezados = partir(lector.readLine());
            List<Map<String, String>> filas = new ArrayList<>();
            String linea;
            while ((linea = lector.readLine()) != null) {
                List<String> celdas = partir(linea);
                Map<String, String> fila = new LinkedHashMap<>();
                for (int i = 0; i < encabezados.size(); i++) {
                    fila.put(encabezados.get(i), i < celdas.size() ? celdas.get(i) : "");
                }
                filas.add(fila);
            }
            return new HojaExcel(hoja, encabezados, filas);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** CSV simple: comas, con comillas dobles cuando el texto trae comas. */
    static List<String> partir(String linea) {
        List<String> celdas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                if (comillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else {
                    comillas = !comillas;
                }
            } else if (c == ',' && !comillas) {
                celdas.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        celdas.add(actual.toString());
        return celdas;
    }

    public String nombre() {
        return nombre;
    }

    public List<String> encabezados() {
        return encabezados;
    }

    public List<Map<String, String>> filas() {
        return filas;
    }

    public Map<String, String> filaDelPeriodo(int periodo) {
        return filas.stream()
                .filter(f -> f.get(encabezados.get(0)).equals(String.valueOf(periodo)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(nombre + ": no hay periodo " + periodo));
    }

    public static Optional<BigDecimal> numero(Map<String, String> fila, String columna) {
        if (!fila.containsKey(columna)) {
            throw new IllegalArgumentException("Columna inexistente: " + columna + " en " + fila.keySet());
        }
        String texto = fila.get(columna);
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(texto));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
