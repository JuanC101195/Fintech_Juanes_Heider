package co.financiera.cartera.api;

import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import co.financiera.cartera.nucleo.versiones.RepositorioReglas;
import co.financiera.cartera.nucleo.versiones.RepositorioReglasEnMemoria;
import co.financiera.cartera.nucleo.versiones.VersionReglas;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Los dos perfiles de reglas del API: EXCEL = {@link ReglasNegocio#EXCEL}; NEGOCIO = las respuestas de
 * negocio de {@code config/reglas-negocio.properties} (como {@code ReglasNegocioConfiguradaTest}). Si el
 * archivo no existe, NEGOCIO queda igual a EXCEL.
 *
 * <p>También es el adaptador de archivo de {@link RepositorioReglas} (ADR 0004): expone los dos perfiles como versiones
 * con id fijo, {@value #ID_EXCEL} (desde el {@value #EXCEL_VIGENTE_DESDE}, el Excel de Diego) y
 * {@code negocio-<aaaa-mm-dd>}, con la fecha desde la que rige la configuración de NEGOCIO
 * ({@code cartera.reglas.vigente-desde}, por defecto {@value #NEGOCIO_VIGENTE_DESDE}). Cambiar
 * {@code config/reglas-negocio.properties} es una versión nueva: hay que subir esa fecha (la prueba
 * {@code VersionesPerfilesTest} fija la huella de cada id y falla si el contenido cambia sin cambiar el id). El API no
 * publica la versión: agregar el campo cambiaría los ejemplos de {@code docs/api-ejemplos}.
 */
@Component
public class PerfilesReglas {

    /** Perfil de reglas con que corre el motor. */
    public enum Perfil {
        EXCEL,
        NEGOCIO
    }

    /** Id de la versión del perfil EXCEL: el Excel "Casos Operaciones V2" de Diego, versión de octubre de 2026. */
    public static final String ID_EXCEL = "excel-2026-10";
    /** Fecha de la versión del Excel de Diego con que se construyó el perfil EXCEL. */
    public static final String EXCEL_VIGENTE_DESDE = "2026-10-11";
    /** Prefijo del id de la versión del perfil NEGOCIO; le sigue la fecha desde la que rige (ISO). */
    public static final String PREFIJO_NEGOCIO = "negocio-";
    /** Desde cuándo rige la configuración actual de NEGOCIO (último cambio de config/reglas-negocio.properties). */
    public static final String NEGOCIO_VIGENTE_DESDE = "2026-10-27";

    private static final System.Logger LOG = System.getLogger(PerfilesReglas.class.getName());

    private final ReglasNegocio negocio;
    private final Path archivo;
    private final boolean archivoEncontrado;
    private final VersionReglas versionExcel;
    private final VersionReglas versionNegocio;
    private final RepositorioReglasEnMemoria repositorio;

    /** Con la fecha de vigencia por defecto de NEGOCIO ({@value #NEGOCIO_VIGENTE_DESDE}). */
    public PerfilesReglas(String archivo) {
        this(archivo, NEGOCIO_VIGENTE_DESDE);
    }

    @Autowired
    public PerfilesReglas(@Value("${cartera.reglas.archivo:config/reglas-negocio.properties}") String archivo,
            @Value("${cartera.reglas.vigente-desde:" + NEGOCIO_VIGENTE_DESDE + "}") String negocioVigenteDesde) {
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
        LocalDate desdeNegocio = LocalDate.parse(negocioVigenteDesde.trim());
        this.versionExcel = VersionReglas.de(ID_EXCEL, LocalDate.parse(EXCEL_VIGENTE_DESDE), ReglasNegocio.EXCEL,
                "Perfil EXCEL: Excel Casos Operaciones V2 de Diego González, tal cual");
        this.versionNegocio = VersionReglas.de(PREFIJO_NEGOCIO + desdeNegocio, desdeNegocio, negocio,
                "Perfil NEGOCIO: respuestas de Diego en " + archivo()
                        + (archivoEncontrado ? "" : " (archivo no encontrado: reglas del Excel)"));
        // Falla al arrancar si la fecha de NEGOCIO no es posterior a la del Excel.
        this.repositorio = RepositorioReglasEnMemoria.con(versionExcel, versionNegocio);
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

    /** La versión de reglas de un perfil (id, vigencia, huella y origen). */
    public VersionReglas version(Perfil perfil) {
        return perfil == Perfil.NEGOCIO ? versionNegocio : versionExcel;
    }

    /** Las versiones de los dos perfiles como repositorio de solo lectura (excel antes que negocio). */
    public RepositorioReglas repositorio() {
        return repositorio;
    }

    /** Ruta del archivo de NEGOCIO tal como se configuró, con "/" como separador. */
    public String archivo() {
        return archivo.toString().replace('\\', '/');
    }

    public boolean archivoEncontrado() {
        return archivoEncontrado;
    }
}
