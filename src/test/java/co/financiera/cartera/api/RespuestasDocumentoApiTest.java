package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Lo que las respuestas de Diego por documento ("rptas software", 2026-10-22) cambian en el API: COB-6
 * ({@code cobranzaActiva} en el crédito y {@code cobranzaDiaria} en las reglas), PPER-9 ({@code listoParaRecuperacion})
 * y X4 (plazo límite del caso 8 con NEGOCIO).
 */
@WebMvcTest(CarteraController.class)
@Import({ServicioCartera.class, PerfilesReglas.class})
class RespuestasDocumentoApiTest {

    static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    private JsonNode leer(RequestBuilder peticion) throws Exception {
        String r = mvc.perform(peticion).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JSON.readTree(r);
    }

    private JsonNode ejecutar(String cuerpo) throws Exception {
        return leer(post("/api/v1/escenarios/ejecutar").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static BigDecimal dinero(JsonNode n) {
        return new BigDecimal(n.asString());
    }

    /**
     * COB-6. Fuente: respuesta de Diego 2026-10-22 (documento 'rptas software'), pregunta COB-6. La cobranza se
     * activa por crédito: campo opcional {@code cobranzaActiva} (por defecto activa, como el Excel).
     */
    @Test
    void cob6LaCobranzaSeActivaODesactivaPorCredito() throws Exception {
        String tardio = """
                {"credito":{"cuotas":104%s},"perfilReglas":"NEGOCIO","instrucciones":[{"periodo":5,"tipo":"PAGO_CONTRACTUAL","diasAtraso":4}]}
                """;
        JsonNode porDefecto = ejecutar(tardio.formatted("")).get("movimientos").get(5);
        JsonNode activa = ejecutar(tardio.formatted(",\"cobranzaActiva\":true")).get("movimientos").get(5);
        JsonNode inactiva = ejecutar(tardio.formatted(",\"cobranzaActiva\":false")).get("movimientos").get(5);
        assertThat(dinero(porDefecto.get("cobranzaCausada"))).isEqualByComparingTo("12000");
        assertThat(dinero(activa.get("cobranzaCausada"))).isEqualByComparingTo("12000");
        assertThat(dinero(inactiva.get("cobranzaCausada"))).isZero();
        assertThat(dinero(inactiva.get("interesMora"))).isEqualByComparingTo(dinero(porDefecto.get("interesMora")));
    }

    /** COB-6 y R4-T5 y PPER-9: las claves nuevas salen en /reglas con el valor de Diego en NEGOCIO. */
    @Test
    void lasClavesNuevasSalenEnLasReglas() throws Exception {
        JsonNode r = leer(get("/api/v1/reglas"));
        JsonNode negocio = r.get("perfiles").get("NEGOCIO");
        JsonNode excel = r.get("perfiles").get("EXCEL");
        assertThat(negocio.get("cambioTasaSobreCapitalVigente").asString()).isEqualTo("true");
        assertThat(negocio.get("cobranzaDiaria").asString()).isEqualTo("3000");
        assertThat(negocio.get("cuotasVencidasParaRecuperacion").asString()).isEqualTo("2");
        assertThat(excel.get("cambioTasaSobreCapitalVigente").asString()).isEqualTo("false");
        assertThat(excel.get("cobranzaDiaria").isNull()).isTrue();
        assertThat(excel.get("cuotasVencidasParaRecuperacion").isNull()).isTrue();
        assertThat(r.get("claves")).hasSize(18);
    }

    /**
     * PPER-9. Fuente: respuesta de Diego 2026-10-22 (documento 'rptas software'), pregunta PPER-9. Con 2 cuotas
     * atrasadas el crédito queda listo para recuperación, en cada movimiento y en el resumen.
     */
    @Test
    void pper9ElCreditoQuedaListoParaRecuperacionConDosCuotasAtrasadas() throws Exception {
        String sinPago = """
                {"credito":{"cuotas":104},"perfilReglas":"%s","instrucciones":[
                  {"periodo":30,"tipo":"SIN_PAGO"},{"periodo":31,"tipo":"SIN_PAGO"},{"periodo":32,"tipo":"SIN_PAGO"}]}
                """;
        JsonNode negocio = ejecutar(sinPago.formatted("NEGOCIO"));
        assertThat(negocio.get("movimientos").get(30).get("listoParaRecuperacion").asBoolean()).isFalse();
        assertThat(negocio.get("movimientos").get(31).get("listoParaRecuperacion").asBoolean()).isTrue();
        assertThat(negocio.get("resumen").get("periodoListoParaRecuperacion").asInt()).isEqualTo(31);
        // Después sigue pagando la cuota pactada sin ponerse al día: termina en mora y listo para recuperación.
        assertThat(negocio.get("resumen").get("listoParaRecuperacion").asBoolean()).isTrue();

        JsonNode excel = ejecutar(sinPago.formatted("EXCEL"));
        assertThat(excel.get("resumen").get("periodoListoParaRecuperacion").isNull()).isTrue();
        assertThat(excel.get("resumen").get("listoParaRecuperacion").asBoolean()).isFalse();
        for (JsonNode m : excel.get("movimientos")) {
            assertThat(m.get("listoParaRecuperacion").asBoolean()).isFalse();
        }
    }

    /**
     * X4. Fuente: respuesta de Diego 2026-10-22 (documento 'rptas software'), pregunta X4. Con NEGOCIO el caso 8
     * tiene plazo límite de 104 cuotas (24 meses); con EXCEL conserva las 100 semanas de la hoja.
     */
    @Test
    void x4ElCaso8ConNegocioTienePlazoLimiteDe104Cuotas() throws Exception {
        JsonNode negocio = leer(get("/api/v1/casos/8?perfilReglas=NEGOCIO"));
        JsonNode excel = leer(get("/api/v1/casos/8?perfilReglas=EXCEL"));
        assertThat(negocio.get("credito").get("plazoLimitePeriodos").asInt()).isEqualTo(104);
        assertThat(excel.get("credito").get("plazoLimitePeriodos").asInt()).isEqualTo(100);
        assertThat(excel.get("resumen").get("periodoFinal").asInt()).isEqualTo(100);
        assertThat(negocio.get("resumen").get("periodoFinal").asInt()).isGreaterThan(100).isLessThanOrEqualTo(104);
        assertThat(negocio.get("credito").get("cobranzaActiva").asBoolean()).isTrue();
        // El caso 9 no cambia con el perfil: sus 120 semanas son las de la hoja.
        assertThat(leer(get("/api/v1/casos/9?perfilReglas=NEGOCIO")).get("credito").get("plazoLimitePeriodos").asInt())
                .isEqualTo(120);
    }
}
