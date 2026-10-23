package co.financiera.cartera.nucleo.motor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Medición de memoria del streaming (escala, D3): un lote de 10.000 créditos semanales en una JVM aparte con
 * {@code -Xmx256m} termina sin {@code OutOfMemoryError} porque {@link MotorCartera#ejecutar(Escenario,
 * java.util.function.Consumer)} no acumula los movimientos y de cada crédito solo queda la instantánea final. Con la
 * lista completa ({@link MotorCartera#ejecutar(Escenario)}) el mismo lote retendría ~1 millón de movimientos (~1,1 KB
 * cada uno según la auditoría de escala): más de 1 GB.
 *
 * <p>No es frágil: el margen es grande (el pico medido queda muy por debajo de 256 MB) y no depende del tiempo; el
 * tiempo solo se imprime. Se puede saltar con {@code -Dlote.memoria.omitir=true}.
 */
class LoteStreamingMemoriaTest {

    private static final int CREDITOS = 10_000;

    @Test
    void unLoteDeDiezMilCreditosCabeEn256MegasConStreaming() throws Exception {
        if (Boolean.getBoolean("lote.memoria.omitir")) {
            return;
        }
        Process proceso = lanzar("streaming");
        String salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(proceso.waitFor(10, TimeUnit.MINUTES)).as("el lote terminó").isTrue();
        System.out.println("=== LOTE STREAMING -Xmx256m: " + salida.strip());
        assertThat(salida).doesNotContain("OutOfMemoryError");
        assertThat(proceso.exitValue()).as(salida).isZero();
        assertThat(salida).contains("RESULTADO creditos=" + CREDITOS);
    }

    /**
     * Contraste: el mismo lote guardando la lista completa de cada crédito se queda sin memoria (medido: hacia el
     * crédito 2.000 de 10.000, en ~2,5 s). Determinístico: necesitaría más de 1 GB.
     */
    @Test
    void elMismoLoteAcumulandoLasListasNoCabe() throws Exception {
        if (Boolean.getBoolean("lote.memoria.omitir")) {
            return;
        }
        Process proceso = lanzar("acumulando");
        String salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(proceso.waitFor(10, TimeUnit.MINUTES)).as("el lote terminó").isTrue();
        String ultimo = salida.lines().filter(l -> l.startsWith("acumulados")).reduce((a, b) -> b).orElse("ninguno");
        System.out.println("=== LOTE ACUMULANDO -Xmx256m: último avance '" + ultimo + "' y luego OutOfMemoryError");
        assertThat(salida).contains("OutOfMemoryError");
        assertThat(proceso.exitValue()).isNotZero();
    }

    /** El streaming entrega exactamente las filas de la lista (ejecutar(escenario) delega en él). */
    @Test
    void elStreamingEntregaLasMismasFilasQueLaLista() throws Exception {
        MotorCartera motor = new MotorCartera();
        ReglasNegocio negocio = EscenariosAleatorios.negocio();
        for (int i = 0; i < 60; i++) {
            List<Movimiento> recibidas = new ArrayList<>();
            Instantanea fin = motor.ejecutar(LoteStreaming.credito(i, negocio), recibidas::add);
            List<Movimiento> lista = motor.ejecutar(LoteStreaming.credito(i, negocio));
            assertThat(recibidas).isEqualTo(lista);
            assertThat(fin.periodo()).isEqualTo(lista.get(lista.size() - 1).periodo());
            assertThat(fin.terminado()).isTrue();
        }
    }

    private static Process lanzar(String modo) throws Exception {
        // Solo hacen falta las clases del proyecto (el núcleo no usa dependencias externas): classpath corto en Windows.
        String classpath = ruta(MotorCartera.class) + File.pathSeparator + ruta(LoteStreaming.class);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> comando = new ArrayList<>(List.of(java, "-Xmx256m", "-cp", classpath, LoteStreaming.class.getName(),
                String.valueOf(CREDITOS), modo));
        return new ProcessBuilder(comando).redirectErrorStream(true).start();
    }

    private static String ruta(Class<?> clase) throws Exception {
        return Path.of(clase.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }
}
