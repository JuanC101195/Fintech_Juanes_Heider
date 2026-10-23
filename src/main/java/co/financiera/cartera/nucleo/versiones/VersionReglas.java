package co.financiera.cartera.nucleo.versiones;

import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Una versión inmutable de las reglas de negocio (D33: "un cambio rige solo para créditos nuevos, salvo la usura").
 * Cada crédito queda atado al {@link #id} de la versión con que se originó; la {@link #huella} permite detectar que el
 * contenido guardado de una versión se alteró después (la base de datos guarda el {@link #contenidoCanonico} y la
 * huella; al leerlos se vuelve a calcular y se compara, ver ADR 0004).
 *
 * <p>Convención de ids (ADR 0004): minúsculas, dígitos y guiones, legible y estable; nunca se reutiliza un id con
 * otro contenido. Los perfiles actuales son {@code excel-2026-10} (el Excel de Diego del 11 de octubre) y
 * {@code negocio-<aaaa-mm-dd>} (las respuestas de negocio de {@code config/reglas-negocio.properties}, con la fecha
 * desde la que rige esa configuración).
 *
 * @param id           identificador estable y legible (p. ej. {@code negocio-2026-10-23})
 * @param vigenteDesde primer día en que la versión rige para los créditos que se originan
 * @param reglas       las reglas de la versión
 * @param huella       SHA-256 (hex, minúsculas) del {@link #contenidoCanonico(ReglasNegocio)}; debe coincidir
 * @param origen       de dónde salió la versión y por qué (archivo, ronda de Diego, alta de un administrador)
 */
public record VersionReglas(String id, LocalDate vigenteDesde, ReglasNegocio reglas, String huella, String origen) {

    /** Versión del formato del contenido canónico; si cambia el formato, cambia este número (y todas las huellas). */
    public static final String FORMATO = "reglas-negocio/1";

    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int LARGO_MAXIMO_ID = 64;

    public VersionReglas {
        if (id == null || id.length() > LARGO_MAXIMO_ID || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Id de versión inválido (minúsculas, dígitos y guiones, hasta "
                    + LARGO_MAXIMO_ID + " caracteres): " + id);
        }
        Objects.requireNonNull(vigenteDesde, "vigenteDesde");
        Objects.requireNonNull(reglas, "reglas");
        if (origen == null || origen.isBlank()) {
            throw new IllegalArgumentException("La versión " + id + " debe decir su origen");
        }
        String calculada = huella(reglas);
        if (!calculada.equals(huella)) {
            throw new IllegalArgumentException("La versión " + id + " está alterada: su huella guardada (" + huella
                    + ") no coincide con la de su contenido (" + calculada + ")");
        }
    }

    /** Versión nueva con la huella calculada de las reglas. */
    public static VersionReglas de(String id, LocalDate vigenteDesde, ReglasNegocio reglas, String origen) {
        Objects.requireNonNull(reglas, "reglas");
        return new VersionReglas(id, vigenteDesde, reglas, huella(reglas), origen);
    }

    /**
     * Versión reconstruida desde lo guardado (contenido canónico + huella): falla si el contenido no es válido o si
     * la huella no coincide, es decir, si alguien lo cambió después de registrarlo.
     */
    public static VersionReglas desdeGuardado(String id, LocalDate vigenteDesde, String contenidoCanonico,
            String huella, String origen) {
        return new VersionReglas(id, vigenteDesde, reglasDesdeContenido(contenidoCanonico), huella, origen);
    }

    /** Si dos versiones tienen las mismas reglas (misma huella), aunque sus ids o fechas sean distintos. */
    public boolean mismoContenidoQue(VersionReglas otra) {
        return huella.equals(otra.huella);
    }

    public String contenidoCanonico() {
        return contenidoCanonico(reglas);
    }

    /**
     * Texto determinista de las reglas: una primera línea {@code formato=}{@value #FORMATO} y luego
     * {@code clave=valor}, una por línea ({@code \n}), en el orden de los componentes de {@link ReglasNegocio} (el de
     * {@code ReglasNegocio.CLAVES} y {@code /reglas}). Enumeraciones por nombre, booleanos y enteros en texto,
     * decimales sin ceros a la derecha ({@code 3000.00} y {@code 3000} son la misma regla) y {@code null} vacío.
     * Se puede volver a leer con {@link #reglasDesdeContenido}.
     */
    public static String contenidoCanonico(ReglasNegocio reglas) {
        StringBuilder texto = new StringBuilder("formato=").append(FORMATO).append('\n');
        valores(reglas).forEach((clave, valor) -> texto.append(clave).append('=').append(valor).append('\n'));
        return texto.toString();
    }

    /** SHA-256 en hexadecimal (minúsculas) del {@link #contenidoCanonico(ReglasNegocio)} en UTF-8. */
    public static String huella(ReglasNegocio reglas) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(contenidoCanonico(reglas).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no trae SHA-256", e);
        }
    }

    /**
     * Reglas a partir de un {@link #contenidoCanonico}: exige el formato y rechaza claves desconocidas
     * ({@code ReglasNegocio.desdeEstricto}). Una clave que falte queda como en el Excel: una regla nueva siempre entra
     * con el comportamiento anterior como valor de {@code EXCEL}, así que una versión vieja sigue significando lo
     * mismo (ADR 0004).
     */
    public static ReglasNegocio reglasDesdeContenido(String contenido) {
        Objects.requireNonNull(contenido, "contenido");
        Map<String, String> valores = new LinkedHashMap<>();
        String formato = null;
        for (String linea : contenido.split("\n")) {
            if (linea.isBlank()) {
                continue;
            }
            int igual = linea.indexOf('=');
            if (igual < 1) {
                throw new IllegalArgumentException("Línea inválida en el contenido de una versión de reglas: " + linea);
            }
            String clave = linea.substring(0, igual);
            String valor = linea.substring(igual + 1);
            if ("formato".equals(clave)) {
                formato = valor;
            } else if (valores.put(clave, valor) != null) {
                throw new IllegalArgumentException("Clave repetida en el contenido de una versión de reglas: " + clave);
            }
        }
        if (!FORMATO.equals(formato)) {
            throw new IllegalArgumentException("Formato de reglas no soportado: " + formato + " (se espera " + FORMATO
                    + ")");
        }
        return ReglasNegocio.desdeEstricto(valores);
    }

    private static Map<String, String> valores(ReglasNegocio reglas) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (RecordComponent c : ReglasNegocio.class.getRecordComponents()) {
            Object valor;
            try {
                valor = c.getAccessor().invoke(reglas);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("No se pudo leer la regla " + c.getName(), e);
            }
            mapa.put(c.getName(), switch (valor) {
                case null -> "";
                case BigDecimal b -> b.signum() == 0 ? "0" : b.stripTrailingZeros().toPlainString();
                case Enum<?> e -> e.name();
                default -> String.valueOf(valor);
            });
        }
        return mapa;
    }
}
