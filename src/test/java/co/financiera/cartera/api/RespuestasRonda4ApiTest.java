package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Lo que la ronda 4 de Diego (2026-10-21) cambia en el API: R4-R2, R4-PLAZO-LIMITE y R4-COBRANZA-CONTABLE. */
@WebMvcTest(CarteraController.class)
@Import({ServicioCartera.class, PerfilesReglas.class})
class RespuestasRonda4ApiTest {

    static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    private JsonNode ejecutar(String cuerpo) throws Exception {
        String r = mvc.perform(post("/api/v1/escenarios/ejecutar").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JSON.readTree(r);
    }

    private static String retoma(String costo) {
        return """
                {"credito":{"cuotas":104},"perfilReglas":"NEGOCIO","instrucciones":[
                  {"periodo":17,"tipo":"CIERRE_POR_RECUPERACION","tipoRecuperacion":"RETOMA","valorMoto":"6800000"%s}]}
                """.formatted(costo == null ? "" : ",\"costoRecuperacion\":\"" + costo + "\"");
    }

    /** R4-R2: el costo real de la recuperación es un campo opcional de la instrucción y reduce el excedente. */
    @Test
    void elCostoDeLaRecuperacionEsOpcionalYReduceElExcedenteDeLaRetoma() throws Exception {
        JsonNode sin = ejecutar(retoma(null)).get("movimientos").get(17);
        JsonNode con = ejecutar(retoma("320000")).get("movimientos").get(17);
        assertThat(sin.get("costoRecuperacion").asString()).isEqualTo("0");
        assertThat(con.get("costoRecuperacion").asString()).isEqualTo("320000");
        BigDecimal diferencia = new BigDecimal(sin.get("cxpDeudor").asString())
                .subtract(new BigDecimal(con.get("cxpDeudor").asString()));
        assertThat(diferencia).isEqualByComparingTo("320000");
    }

    /** R4-PLAZO-LIMITE: en NEGOCIO un crédito semanal nuevo se proyecta 24 meses = 104 semanas; en EXCEL, 120. */
    @Test
    void elPlazoLimiteDeUnCreditoNuevoSonVeinticuatroMeses() throws Exception {
        String fuera = """
                {"credito":{"cuotas":104},"perfilReglas":"%s","instrucciones":[{"periodo":110,"tipo":"SIN_PAGO"}]}
                """;
        mvc.perform(post("/api/v1/escenarios/ejecutar").contentType(MediaType.APPLICATION_JSON)
                        .content(fuera.formatted("NEGOCIO")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalle.campo").value("periodo"));
        mvc.perform(post("/api/v1/escenarios/ejecutar").contentType(MediaType.APPLICATION_JSON)
                .content(fuera.formatted("EXCEL"))).andExpect(status().isOk());
        // Mandarlo explícito en el request sigue mandando.
        ejecutar("""
                {"credito":{"cuotas":104,"plazoLimitePeriodos":120},"perfilReglas":"NEGOCIO",
                 "instrucciones":[{"periodo":110,"tipo":"SIN_PAGO"}]}
                """);
    }

    /** R4-COBRANZA-CONTABLE: en NEGOCIO la cobranza causada va a ingresos no operacionales (PUC 42). */
    @Test
    void enNegocioLaCobranzaVaAIngresosNoOperacionales() throws Exception {
        String tardio = """
                {"credito":{"cuotas":104},"perfilReglas":"%s","instrucciones":[{"periodo":5,"tipo":"PAGO_CONTRACTUAL","diasAtraso":4}]}
                """;
        for (String perfil : new String[] {"NEGOCIO", "EXCEL"}) {
            JsonNode asientos = ejecutar(tardio.formatted(perfil)).get("asientos");
            String esperado = perfil.equals("NEGOCIO") ? "INGRESOS_NO_OPERACIONALES_COBRANZA" : "INGRESOS_COBRANZA";
            boolean hay = false;
            for (JsonNode a : asientos) {
                for (JsonNode l : a.get("lineas")) {
                    hay |= l.get("cuenta").asString().equals(esperado) && new BigDecimal(l.get("credito").asString())
                            .compareTo(new BigDecimal("12000")) == 0;
                }
            }
            assertThat(hay).as(perfil).isTrue();
        }
    }
}
