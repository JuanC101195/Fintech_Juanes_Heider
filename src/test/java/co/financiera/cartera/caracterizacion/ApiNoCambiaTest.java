package co.financiera.cartera.caracterizacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.financiera.cartera.api.CarteraController;
import co.financiera.cartera.api.PerfilesReglas;
import co.financiera.cartera.api.ServicioCartera;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Caracterización del API: huella del cuerpo JSON de {@code /simulaciones} y {@code /escenarios/ejecutar} para
 * peticiones representativas, en {@code caracterizacion/api.sha256}. El cuerpo se normaliza (llaves ordenadas,
 * una línea {@code ruta=valor} por hoja, sin el campo volátil {@code idCorrelacion}) antes de la huella, así que
 * un cambio de formato del serializador no mueve nada pero sí un campo nuevo, uno renombrado o una cifra.
 * Regenerar: {@code ./mvnw -q -B test -Dtest=ApiNoCambiaTest -Dcaracterizacion.regenerar=true}.
 */
@WebMvcTest(CarteraController.class)
@Import({ServicioCartera.class, PerfilesReglas.class})
class ApiNoCambiaTest {

    private static final java.util.Set<String> VOLATILES = java.util.Set.of("idCorrelacion");

    @Autowired
    MockMvc mvc;

    private static Map<String, String> peticiones() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("POST /simulaciones · semanal NEGOCIO 104 cuotas", """
                /simulaciones
                {"linea":"CPPU","periodicidad":"SEMANAL","cuotas":104,"valorMoto":"5100000","pagoInicial":"0","perfilReglas":"NEGOCIO"}""");
        p.put("POST /simulaciones · mensual NEGOCIO con fechas", """
                /simulaciones
                {"linea":"CPPU","periodicidad":"MENSUAL","cuotas":24,"valorMoto":"5100000","pagoInicial":"0","perfilReglas":"NEGOCIO","fechaDesembolso":"2026-10-23","primerVencimiento":"2026-12-05"}""");
        p.put("POST /simulaciones · quincenal EXCEL 48 cuotas", """
                /simulaciones
                {"linea":"CPPU","periodicidad":"QUINCENAL","cuotas":48,"valorMoto":"5100000","pagoInicial":"500000","perfilReglas":"EXCEL"}""");
        p.put("POST /escenarios/ejecutar · semanal NEGOCIO con instrucciones", """
                /escenarios/ejecutar
                {"credito":{"linea":"CPPU","periodicidad":"SEMANAL","cuotas":104,"valorMoto":"5100000","pagoInicial":"0"},"perfilReglas":"NEGOCIO","incluirAsientos":true,"instrucciones":[{"periodo":40,"tipo":"SIN_PAGO","causaMoraYServicios":true},{"periodo":41,"tipo":"PAGO_VALOR","valor":"5000","diasAtraso":3},{"periodo":42,"tipo":"ABONO_EXTRA","valor":"1500000","modalidad":"MENOR_PLAZO"}]}""");
        p.put("POST /escenarios/ejecutar · quincenal NEGOCIO con fechas y cambio de tasa", """
                /escenarios/ejecutar
                {"credito":{"linea":"CPPU","periodicidad":"QUINCENAL","cuotas":48,"valorMoto":"5100000","pagoInicial":"0","fechaDesembolso":"2026-10-23","primerVencimiento":"2026-11-05"},"perfilReglas":"NEGOCIO","incluirAsientos":true,"instrucciones":[{"periodo":6,"tipo":"PAGO_PARCIAL","fraccion":"0.4"},{"periodo":7,"tipo":"PAGO_AL_DIA"},{"periodo":16,"tipo":"CAMBIO_TASA","nuevaTasaEA":"0.60","modalidad":"MISMA_CUOTA"}]}""");
        p.put("POST /escenarios/ejecutar · mensual NEGOCIO con fechas y prepago", """
                /escenarios/ejecutar
                {"credito":{"linea":"CPPU","periodicidad":"MENSUAL","cuotas":24,"valorMoto":"5100000","pagoInicial":"0","fechaDesembolso":"2026-10-23","primerVencimiento":"2026-12-05"},"perfilReglas":"NEGOCIO","incluirAsientos":true,"instrucciones":[{"periodo":8,"tipo":"ABONO_EXTRA","valor":"1000000","modalidad":"MENOR_CUOTA"},{"periodo":10,"tipo":"PREPAGO_TOTAL","diasDesdeUltimaCuota":3}]}""");
        p.put("POST /escenarios/ejecutar · semanal EXCEL con dación", """
                /escenarios/ejecutar
                {"credito":{"linea":"CPPU","periodicidad":"SEMANAL","cuotas":104,"valorMoto":"5100000","pagoInicial":"0"},"perfilReglas":"EXCEL","incluirAsientos":true,"instrucciones":[{"periodo":31,"tipo":"SIN_PAGO","causaMoraYServicios":false},{"periodo":32,"tipo":"SIN_PAGO","causaMoraYServicios":false},{"periodo":33,"tipo":"CIERRE_POR_RECUPERACION","tipoRecuperacion":"DACION_EN_PAGO","valorMoto":"3000000"}]}""");
        return p;
    }

    /** Llaves ordenadas, una línea {@code ruta=valor} por hoja y sin campos volátiles: estable ante el formato del JSON. */
    static String normalizar(JsonNode nodo) {
        StringBuilder t = new StringBuilder();
        recorrer(t, "$", nodo);
        return t.toString();
    }

    private static void recorrer(StringBuilder t, String ruta, JsonNode nodo) {
        if (nodo.isObject()) {
            Map<String, JsonNode> ordenado = new TreeMap<>();
            for (Entry<String, JsonNode> campo : nodo.properties()) {
                if (!VOLATILES.contains(campo.getKey())) {
                    ordenado.put(campo.getKey(), campo.getValue());
                }
            }
            if (ordenado.isEmpty()) {
                t.append(ruta).append("={}\n");
            }
            ordenado.forEach((k, v) -> recorrer(t, ruta + "." + k, v));
        } else if (nodo.isArray()) {
            if (nodo.isEmpty()) {
                t.append(ruta).append("=[]\n");
            }
            for (int i = 0; i < nodo.size(); i++) {
                recorrer(t, ruta + "[" + i + "]", nodo.get(i));
            }
        } else {
            // toString del nodo escalar = su JSON (las cadenas con comillas, null explícito).
            t.append(ruta).append('=').append(nodo).append('\n');
        }
    }

    @Test
    void lasRespuestasDelApiNoCambian() throws Exception {
        ObjectMapper json = new ObjectMapper();
        Map<String, String> actual = new LinkedHashMap<>();
        for (Entry<String, String> p : peticiones().entrySet()) {
            String[] partes = p.getValue().split("\n", 2);
            String cuerpo = mvc.perform(post("/api/v1" + partes[0]).contentType(MediaType.APPLICATION_JSON)
                    .content(partes[1])).andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            String texto = normalizar(json.readTree(cuerpo));
            long lineas = texto.lines().count();
            actual.put(p.getKey().replace(' ', '_').replace('=', '-'), lineas + " " + Huellas.sha256(texto));
        }
        Huellas.verificar("api.sha256", actual);
    }
}
