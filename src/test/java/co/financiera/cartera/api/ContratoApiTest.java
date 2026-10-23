package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El contrato del API no puede divergir en silencio entre Java y la consola:
 * <ol>
 *   <li>Las claves de {@code docs/api-ejemplos/*.json} (respuestas reales) coincide, por reflexión, con los
 *       componentes de los records de {@link Dtos}. Solo pueden faltar los campos que el DTO omite cuando son
 *       nulos ({@code @JsonInclude(NON_NULL)}).</li>
 *   <li>Cada interfaz de {@code consola/src/tipos.ts} tiene exactamente los campos de su record de {@link Dtos}
 *       (los opcionales de TypeScript pueden ser obligatorios en Java, pero ninguno puede sobrar ni faltar).</li>
 * </ol>
 */
class ContratoApiTest {

    private static final Path EJEMPLOS = Path.of("docs", "api-ejemplos");
    private static final Path TIPOS_TS = Path.of("consola", "src", "tipos.ts");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Ejemplo → tipo de la respuesta. */
    private static final Map<String, Class<?>> EJEMPLOS_Y_TIPOS = Map.of(
            "catalogo.json", Dtos.CatalogoResponse.class,
            "simulacion-quincenal.json", Dtos.SimulacionResponse.class,
            "simulacion-mensual-prorrateo.json", Dtos.SimulacionResponse.class,
            "caso-13-negocio.json", Dtos.CasoResponse.class,
            "escenario-semanal-prorrateo.json", Dtos.EjecucionResponse.class);

    /** Interfaz de tipos.ts → record de Dtos. */
    private static final Map<String, Class<?>> TS_Y_JAVA = new LinkedHashMap<>();

    static {
        TS_Y_JAVA.put("Linea", Dtos.LineaDto.class);
        TS_Y_JAVA.put("InfoPeriodicidad", Dtos.PeriodicidadDto.class);
        TS_Y_JAVA.put("Catalogo", Dtos.CatalogoResponse.class);
        TS_Y_JAVA.put("ClaveRegla", Dtos.ClaveDto.class);
        TS_Y_JAVA.put("Reglas", Dtos.ReglasResponse.class);
        TS_Y_JAVA.put("Credito", Dtos.CreditoRequest.class);
        TS_Y_JAVA.put("ProrrateoPrimeraCuota", Dtos.ProrrateoDto.class);
        TS_Y_JAVA.put("ResumenSimulacion", Dtos.ResumenSimulacion.class);
        TS_Y_JAVA.put("FilaPlan", Dtos.FilaPlan.class);
        TS_Y_JAVA.put("Simulacion", Dtos.SimulacionResponse.class);
        TS_Y_JAVA.put("Instruccion", Dtos.InstruccionDto.class);
        TS_Y_JAVA.put("PeticionEjecucion", Dtos.EjecutarRequest.class);
        TS_Y_JAVA.put("Servicios", Dtos.ServiciosDto.class);
        TS_Y_JAVA.put("DeudaExtinguida", Dtos.DeudaDto.class);
        TS_Y_JAVA.put("Movimiento", Dtos.MovimientoDto.class);
        TS_Y_JAVA.put("LineaAsiento", Dtos.LineaAsientoDto.class);
        TS_Y_JAVA.put("Asiento", Dtos.AsientoDto.class);
        TS_Y_JAVA.put("ResumenEjecucion", Dtos.ResumenEjecucion.class);
        TS_Y_JAVA.put("Ejecucion", Dtos.EjecucionResponse.class);
        TS_Y_JAVA.put("CasoResumen", Dtos.CasoResumenDto.class);
        TS_Y_JAVA.put("CasoDetalle", Dtos.CasoResponse.class);
        TS_Y_JAVA.put("ErrorApi", Dtos.ErrorDto.class);
    }

    // ------------------------------------------------------------------ ejemplos JSON ↔ Dtos

    @TestFactory
    List<DynamicTest> losEjemplosRealesTienenLasClavesDeLosDtos() {
        List<DynamicTest> pruebas = new ArrayList<>();
        EJEMPLOS_Y_TIPOS.forEach((archivo, tipo) -> pruebas.add(DynamicTest.dynamicTest(archivo, () -> {
            JsonNode raiz = JSON.readTree(Files.readString(EJEMPLOS.resolve(archivo), StandardCharsets.UTF_8));
            List<String> diferencias = new ArrayList<>();
            comparar(raiz, tipo, archivo, diferencias);
            assertThat(diferencias).as("Claves del JSON frente a los DTOs de Java").isEmpty();
        })));
        return pruebas;
    }

    private static void comparar(JsonNode nodo, Class<?> tipo, String ruta, List<String> diferencias) {
        if (!tipo.isRecord() || nodo == null || !nodo.isObject()) {
            return;
        }
        compararClaves(nodo, tipo, ruta, diferencias);
        for (RecordComponent c : tipo.getRecordComponents()) {
            JsonNode hijo = nodo.get(c.getName());
            if (hijo == null) {
                continue;
            }
            Class<?> elemento = tipoDeElemento(c.getGenericType());
            if (hijo.isArray()) {
                int i = 0;
                for (JsonNode e : hijo) {
                    comparar(e, elemento, ruta + "." + c.getName() + "[" + i++ + "]", diferencias);
                }
            } else {
                comparar(hijo, elemento, ruta + "." + c.getName(), diferencias);
            }
        }
    }

    private static void compararClaves(JsonNode nodo, Class<?> tipo, String ruta, List<String> diferencias) {
        boolean omiteNulos = tipo.isAnnotationPresent(JsonInclude.class);
        Set<String> reales = new TreeSet<>();
        nodo.properties().forEach(e -> reales.add(e.getKey()));
        Set<String> declaradas = new TreeSet<>();
        for (RecordComponent c : tipo.getRecordComponents()) {
            declaradas.add(c.getName());
            boolean opcional = omiteNulos || c.getAccessor().isAnnotationPresent(JsonInclude.class);
            if (!reales.contains(c.getName()) && !opcional) {
                diferencias.add(ruta + ": el JSON no trae '" + c.getName() + "' que declara " + tipo.getSimpleName());
            }
        }
        for (String clave : reales) {
            if (!declaradas.contains(clave)) {
                diferencias.add(ruta + ": el JSON trae '" + clave + "' que " + tipo.getSimpleName() + " no declara");
            }
        }
    }

    /** {@code List<X>} → X; cualquier otro tipo, él mismo. */
    private static Class<?> tipoDeElemento(Type tipo) {
        if (tipo instanceof ParameterizedType p && p.getRawType() == List.class
                && p.getActualTypeArguments()[0] instanceof Class<?> c) {
            return c;
        }
        return tipo instanceof Class<?> c ? c : Object.class;
    }

    // ------------------------------------------------------------------ tipos.ts ↔ Dtos

    @Test
    void lasInterfacesDeTiposTsTienenLosCamposDeLosDtos() throws IOException {
        Map<String, Set<String>> ts = camposDeTipos(Files.readString(TIPOS_TS, StandardCharsets.UTF_8));
        List<String> diferencias = new ArrayList<>();
        TS_Y_JAVA.forEach((nombreTs, dto) -> {
            Set<String> camposTs = ts.get(nombreTs);
            if (camposTs == null) {
                diferencias.add("consola/src/tipos.ts ya no declara la interfaz " + nombreTs + " (" + dto.getSimpleName() + ")");
                return;
            }
            Set<String> camposJava = new TreeSet<>();
            for (RecordComponent c : dto.getRecordComponents()) {
                camposJava.add(c.getName());
            }
            Set<String> soloJava = new TreeSet<>(camposJava);
            soloJava.removeAll(camposTs);
            Set<String> soloTs = new TreeSet<>(camposTs);
            soloTs.removeAll(camposJava);
            if (!soloJava.isEmpty()) {
                diferencias.add(nombreTs + ": " + dto.getSimpleName() + " tiene " + soloJava + " y tipos.ts no");
            }
            if (!soloTs.isEmpty()) {
                diferencias.add(nombreTs + ": tipos.ts tiene " + soloTs + " y " + dto.getSimpleName() + " no");
            }
        });
        assertThat(diferencias).as("Contrato Java ↔ consola/src/tipos.ts").isEmpty();
    }

    @Test
    void elAnalizadorDeTiposTsEntiendeHerenciaYOpcionales() {
        Map<String, Set<String>> ts = camposDeTipos("""
                /** doc { con llave } */
                export interface Base {
                  a: string // comentario
                  b?: number
                }
                export interface Hija extends Base {
                  c: Record<string, string>
                }
                """);
        assertThat(ts.get("Base")).containsExactlyInAnyOrder("a", "b");
        assertThat(ts.get("Hija")).containsExactlyInAnyOrder("a", "b", "c");
    }

    /**
     * Campos de cada {@code export interface} de un archivo TypeScript (incluye los heredados con
     * {@code extends}). Los campos van en una línea, como en tipos.ts.
     */
    static Map<String, Set<String>> camposDeTipos(String fuente) {
        // Primero los comentarios de línea completa (uno puede nombrar una ruta con "/*"), luego los de bloque.
        String limpio = fuente.replaceAll("(?m)^\\s*//.*$", "").replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)\\s//.*$", "");
        Matcher interfaz = Pattern.compile("export interface (\\w+)(?:\\s+extends\\s+(\\w+))?\\s*\\{(.*?)\\n\\}",
                Pattern.DOTALL).matcher(limpio);
        Map<String, Set<String>> propios = new LinkedHashMap<>();
        Map<String, String> padres = new LinkedHashMap<>();
        Pattern campo = Pattern.compile("^\\s{2}(\\w+)\\??\\s*:", Pattern.MULTILINE);
        while (interfaz.find()) {
            Set<String> campos = new LinkedHashSet<>();
            Matcher m = campo.matcher(interfaz.group(3));
            while (m.find()) {
                campos.add(m.group(1));
            }
            propios.put(interfaz.group(1), campos);
            if (interfaz.group(2) != null) {
                padres.put(interfaz.group(1), interfaz.group(2));
            }
        }
        Map<String, Set<String>> todos = new LinkedHashMap<>();
        propios.forEach((nombre, campos) -> {
            Set<String> completos = new TreeSet<>(campos);
            for (String p = padres.get(nombre); p != null; p = padres.get(p)) {
                completos.addAll(propios.getOrDefault(p, Set.of()));
            }
            todos.put(nombre, completos);
        });
        return todos;
    }
}
