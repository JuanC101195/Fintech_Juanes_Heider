package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** POST /escenarios/ejecutar-lote: NDJSON, mismo resultado que /escenarios/ejecutar, errores por ítem, tope de 500. */
@WebMvcTest(CarteraController.class)
@Import({ServicioCartera.class, PerfilesReglas.class})
class LoteApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String LOTE = "/api/v1/escenarios/ejecutar-lote";
    private static final String UNO = "/api/v1/escenarios/ejecutar";

    @Autowired
    MockMvc mvc;

    private String texto(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** El EjecutarRequest equivalente al caso del Excel: su crédito, sus instrucciones y el perfil EXCEL. */
    private JsonNode requestDelCaso(int n) throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/casos/" + n + "?perfilReglas=EXCEL")).andExpect(status().isOk()).andReturn();
        JsonNode caso = JSON.readTree(texto(r));
        var req = JSON.createObjectNode();
        req.set("credito", caso.get("credito"));
        req.put("perfilReglas", "EXCEL");
        req.set("instrucciones", caso.get("instrucciones"));
        req.put("incluirAsientos", true);
        return req;
    }

    private JsonNode uno(JsonNode request) throws Exception {
        MvcResult r = mvc.perform(post(UNO).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(texto(r));
    }

    private List<String> lote(List<JsonNode> requests) throws Exception {
        MvcResult r = mvc.perform(post(LOTE).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(requests))).andExpect(status().isOk()).andReturn();
        assertThat(r.getResponse().getContentType()).startsWith("application/x-ndjson");
        return texto(r).lines().toList();
    }

    @Test
    void los13CasosDelExcelPorLoteDanLoMismoQueUnoPorUno() throws Exception {
        List<JsonNode> requests = new ArrayList<>();
        for (int n = 1; n <= 13; n++) {
            requests.add(requestDelCaso(n));
        }
        List<String> lineas = lote(requests);
        assertThat(lineas).hasSize(13);
        for (int i = 0; i < 13; i++) {
            JsonNode esperado = uno(requests.get(i));
            JsonNode recibido = JSON.readTree(lineas.get(i));
            assertThat(esperado.get("movimientos").size()).as("caso %d tiene movimientos", i + 1).isPositive();
            // Comparación estructural completa (resumen, cada movimiento, cada asiento): campo a campo.
            assertThat(recibido).as("caso %d", i + 1).isEqualTo(esperado);
            assertThat(recibido.propertyNames()).containsExactlyInAnyOrderElementsOf(esperado.propertyNames());
        }
    }

    @Test
    void unItemInvalidoDaUnaLineaDeErrorConSuIndiceYNoTumbaElLote() throws Exception {
        JsonNode bueno = requestDelCaso(1);
        JsonNode malo = JSON.readTree("{\"credito\":{\"cuotas\":104,\"valorMoto\":\"-5\"}}");
        JsonNode sinCredito = JSON.readTree("{\"perfilReglas\":\"NEGOCIO\"}");
        JsonNode abonoGigante = JSON.readTree("{\"credito\":{\"linea\":\"CPPU\",\"periodicidad\":\"SEMANAL\",\"cuotas\":104,"
                + "\"valorMoto\":\"5100000\",\"pagoInicial\":\"0\"},\"instrucciones\":[{\"periodo\":3,"
                + "\"tipo\":\"ABONO_EXTRA\",\"valor\":\"900000000\",\"modalidad\":\"MENOR_PLAZO\"}]}");
        List<String> lineas = lote(List.of(bueno, malo, JSON.nullNode(), sinCredito, abonoGigante, bueno));
        assertThat(lineas).hasSize(6);
        assertThat(JSON.readTree(lineas.get(0))).isEqualTo(uno(bueno));
        for (int i : new int[] {1, 2, 3, 4}) {
            JsonNode e = JSON.readTree(lineas.get(i));
            assertThat(e.get("indice").asInt()).isEqualTo(i);
            assertThat(e.get("codigo").asString()).isNotBlank();
            assertThat(e.get("mensaje").asString()).isNotBlank();
            assertThat(e.has("detalle")).isTrue();
            assertThat(e.has("movimientos")).isFalse();
        }
        assertThat(JSON.readTree(lineas.get(4)).get("codigo").asString()).isEqualTo("ABONO_MAYOR_QUE_SALDO");
        assertThat(JSON.readTree(lineas.get(5))).isEqualTo(uno(bueno));
    }

    @Test
    void masDe500RespondeUn400ClaroSinEmitirNada() throws Exception {
        JsonNode item = requestDelCaso(1);
        List<JsonNode> lote = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            lote.add(item);
        }
        mvc.perform(post(LOTE).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(lote)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("LOTE_DEMASIADO_GRANDE"))
                .andExpect(jsonPath("$.mensaje").value("El lote trae 501 escenarios; el máximo es 500"))
                .andExpect(jsonPath("$.detalle.maximo").value(500));
    }

    @Test
    void loteVacioOQueNoEsUnaListaResponde400() throws Exception {
        mvc.perform(post(LOTE).contentType(MediaType.APPLICATION_JSON).content("[]")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mvc.perform(post(LOTE).contentType(MediaType.APPLICATION_JSON).content("{\"credito\":{}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void elTopeDeCuerpoMayorAplicaSoloAlLote() throws Exception {
        FiltroTamanoCuerpo filtro = new FiltroTamanoCuerpo(1024);
        assertThat(pasa(filtro, LOTE, 2048)).isTrue();
        assertThat(pasa(filtro, LOTE, FiltroTamanoCuerpo.MAXIMO_LOTE_POR_DEFECTO + 1)).isFalse();
        assertThat(pasa(filtro, "/api/v1/escenarios/ejecutar", 2048)).isFalse();
        assertThat(pasa(filtro, "/api/v1/simulaciones", 2048)).isFalse();
    }

    private static boolean pasa(FiltroTamanoCuerpo filtro, String uri, int bytes) throws Exception {
        MockHttpServletRequest base = new MockHttpServletRequest("POST", uri);
        base.setRequestURI(uri);
        base.setContent(new byte[bytes]);
        HttpServletRequest chunked = new HttpServletRequestWrapper(base) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();
        filtro.doFilter(chunked, respuesta, cadena);
        return cadena.getRequest() != null && respuesta.getStatus() == 200;
    }

    @Test
    void medirUnLoteDe500() throws Exception {
        JsonNode item = JSON.readTree("{\"credito\":{\"linea\":\"CPPU\",\"periodicidad\":\"SEMANAL\",\"cuotas\":104,"
                + "\"valorMoto\":\"5100000\",\"pagoInicial\":\"0\"},\"perfilReglas\":\"NEGOCIO\",\"instrucciones\":["
                + "{\"periodo\":40,\"tipo\":\"SIN_PAGO\",\"causaMoraYServicios\":true},"
                + "{\"periodo\":41,\"tipo\":\"PAGO_VALOR\",\"valor\":\"5000\",\"diasAtraso\":3},"
                + "{\"periodo\":42,\"tipo\":\"ABONO_EXTRA\",\"valor\":\"1500000\",\"modalidad\":\"MENOR_PLAZO\"}]}");
        List<JsonNode> requests = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            requests.add(item);
        }
        lote(requests.subList(0, 50)); // calentamiento de la JVM
        long ini = System.nanoTime();
        List<String> lineas = lote(requests);
        double ms = (System.nanoTime() - ini) / 1e6;
        assertThat(lineas).hasSize(500);
        System.out.printf("MEDICION_LOTE_500: %.0f ms en total, %.2f ms/credito (semanal 104 cuotas, 3 instrucciones, con asientos)%n",
                ms, ms / 500);
    }
}
