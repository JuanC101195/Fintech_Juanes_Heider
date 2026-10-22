package co.financiera.cartera.nucleo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * El motor debe ser determinístico por construcción, no solo en las pruebas: el código que calcula
 * (nucleo y contabilidad) no puede usar azar, el reloj del sistema, paralelismo ni funciones de punto
 * flotante cuyo resultado cambie según la máquina (Math.pow/log/exp; se usa StrictMath).
 */
class DeterminismoTest {

    private static final Pattern PROHIBIDO = Pattern.compile(
            "(?<!Strict)Math\\.(pow|log|log10|exp|sqrt|cbrt|sin|cos|tan)\\("
                    + "|\\bRandom\\b|ThreadLocalRandom|UUID\\.randomUUID"
                    + "|LocalDate\\.now|LocalDateTime\\.now|Instant\\.now|System\\.currentTimeMillis|System\\.nanoTime"
                    + "|parallelStream|\\.parallel\\(|CompletableFuture|ExecutorService");

    @Test
    void elCodigoQueCalculaNoTieneFuentesDeNoDeterminismo() throws IOException {
        List<String> hallazgos = new ArrayList<>();
        for (String modulo : List.of("nucleo", "contabilidad")) {
            Path raiz = Path.of("src/main/java/co/financiera/cartera", modulo);
            try (Stream<Path> archivos = Files.walk(raiz)) {
                for (Path archivo : archivos.filter(a -> a.toString().endsWith(".java")).toList()) {
                    List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
                    for (int i = 0; i < lineas.size(); i++) {
                        String linea = lineas.get(i).strip();
                        if (!linea.startsWith("*") && !linea.startsWith("//") && PROHIBIDO.matcher(linea).find()) {
                            hallazgos.add(archivo + ":" + (i + 1) + "  " + linea);
                        }
                    }
                }
            }
        }
        assertThat(hallazgos).as("fuentes de no determinismo en el cálculo").isEmpty();
    }
}
