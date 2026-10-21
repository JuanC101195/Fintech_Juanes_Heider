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
        this.archivoEncontrado = Files.exists(this.archivo);
        if (!archivoEncontrado) {
            LOG.log(System.Logger.Level.WARNING, "No existe {0}: el perfil NEGOCIO usa las reglas del Excel",
                    this.archivo.toAbsolutePath());
        }
        this.negocio = ReglasNegocio.desde(leer(this.archivo));
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
