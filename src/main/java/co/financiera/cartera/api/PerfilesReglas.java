package co.financiera.cartera.api;

import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Los dos perfiles de reglas del API: EXCEL = {@link ReglasNegocio#EXCEL}; NEGOCIO = las respuestas de
 * negocio de {@code config/reglas-negocio.properties} (como {@code ReglasNegocioConfiguradaTest}). Si el
 * archivo no existe, NEGOCIO queda igual a EXCEL.
 */
@Component
public class PerfilesReglas {

    /** Perfil de reglas con que corre el motor. */
    public enum Perfil {
        EXCEL,
        NEGOCIO
    }

    private static final System.Logger LOG = System.getLogger(PerfilesReglas.class.getName());

    private final ReglasNegocio negocio;
    private final Path archivo;
    private final boolean archivoEncontrado;

    public PerfilesReglas(@Value("${cartera.reglas.archivo:config/reglas-negocio.properties}") String archivo) {
        this.archivo = Path.of(archivo);
        Map<String, String> valores = leer(this.archivo);
        boolean enDisco = Files.exists(this.archivo);
        if (!enDisco) {
            // Desplegado (Azure) no existe config/ en disco: se usa la copia empaquetada en el jar.
            valores = leerDelClasspath(this.archivo.toString().replace('\\', '/'));
        }
        this.archivoEncontrado = enDisco || !valores.isEmpty();
        if (!archivoEncontrado) {
            LOG.log(System.Logger.Level.WARNING, "No existe {0}: el perfil NEGOCIO usa las reglas del Excel",
                    this.archivo.toAbsolutePath());
        }
        var desconocidas = ReglasNegocio.clavesDesconocidas(valores);
        if (!desconocidas.isEmpty()) {
            // Se ignoran como siempre (pueden ser respuestas que esperan desarrollo), pero quedan a la vista.
            LOG.log(System.Logger.Level.WARNING, "{0} trae claves que el motor no aplica: {1}",
                    this.archivo, desconocidas);
        }
        this.negocio = ReglasNegocio.desde(valores);
    }

    static Map<String, String> leerDelClasspath(String recurso) {
        Map<String, String> mapa = new HashMap<>();
        var entrada = PerfilesReglas.class.getClassLoader().getResourceAsStream(recurso);
        if (entrada == null) {
            return mapa;
        }
        java.util.Properties p = new java.util.Properties();
        try (Reader r = new java.io.InputStreamReader(entrada, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + recurso + " del classpath", e);
        }
        p.forEach((k, v) -> mapa.put(String.valueOf(k), String.valueOf(v)));
        return mapa;
    }

    static Map<String, String> leer(Path archivo) {
        Map<String, String> mapa = new HashMap<>();
        if (!Files.exists(archivo)) {
            return mapa;
        }
        java.util.Properties p = new java.util.Properties();
        try (Reader r = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        }
        p.forEach((k, v) -> mapa.put(String.valueOf(k), String.valueOf(v)));
        return mapa;
    }

    public ReglasNegocio reglas(Perfil perfil) {
        return perfil == Perfil.NEGOCIO ? negocio : ReglasNegocio.EXCEL;
    }

    /** Ruta del archivo de NEGOCIO tal como se configuró, con "/" como separador. */
    public String archivo() {
        return archivo.toString().replace('\\', '/');
    }

    public boolean archivoEncontrado() {
        return archivoEncontrado;
    }
}
