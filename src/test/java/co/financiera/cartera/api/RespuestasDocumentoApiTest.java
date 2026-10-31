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
                {"credito":{"cuotas":104%s},"perfilReglas":"NEGOCIO","instrucciones":[{"periodo":3,"tipo":"SIN_PAGO"},{"periodo":4,"tipo":"SIN_PAGO"},{"periodo":5,"tipo":"PAGO_CONTRACTUAL","diasAtraso":4}]}
                """;
        JsonNode porDefecto = ejecutar(tardio.formatted("")).get("movimientos").get(5);
        JsonNode activa = ejecutar(tardio.formatted(",\"cobranzaActiva\":true")).get("movimientos").get(5);
        JsonNode inactiva = ejecutar(tardio.formatted(",\"cobranzaActiva\":false")).get("movimientos").get(5);
        // R8-COBRANZA-PAGO-TARDIO: con NEGOCIO la cobranza es el cobro fijo del periodo (40.000), no 4 días × 3.000.
        assertThat(dinero(porDefecto.get("cobranzaCausada"))).isEqualByComparingTo("40000");
        assertThat(dinero(activa.get("cobranzaCausada"))).isEqualByComparingTo("40000");
        assertThat(dinero(inactiva.get("cobranzaCausada"))).isZero();
        assertThat(dinero(inactiva.get("interesMora"))).isEqualByComparingTo(dinero(porDefecto.get("interesMora")));
    }

    /**
     * R8-PAGO-MAYOR-EXIGIBLE: con NEGOCIO el movimiento trae {@code sobranteCliente} y el asiento a la CxP provisional;
     * con EXCEL el campo no viaja (se recorta como antes, H8).
     */
    @Test
    void r8ElSobranteDeUnPagoMayorQueLoExigibleSaleEnElMovimientoYEnElAsiento() throws Exception {
        String pago = """
                {"credito":{"cuotas":104},"perfilReglas":"%s","instrucciones":[{"periodo":5,"tipo":"PAGO_VALOR","valor":"99000000"}]}
                """;
        JsonNode negocio = ejecutar(pago.formatted("NEGOCIO"));
        JsonNode cinco = negocio.get("movimientos").get(5);
        BigDecimal sobrante = dinero(cinco.get("sobranteCliente"));
        assertThat(sobrante).isPositive();
        assertThat(sobrante.add(dinero(cinco.get("pagoRecibido")))).isCloseTo(new BigDecimal("99000000"),
                org.assertj.core.data.Offset.offset(new BigDecimal("0.000001")));
        boolean cxp = false;
        for (JsonNode a : negocio.get("asientos")) {
            assertThat(a.get("cuadra").asBoolean()).isTrue();
            for (JsonNode l : a.get("lineas")) {
                cxp |= l.get("cuenta").asString().equals("CXP_SOBRANTE_CLIENTE")
                        && dinero(l.get("credito")).compareTo(sobrante) == 0;
            }
        }
        assertThat(cxp).isTrue();
        JsonNode excel = ejecutar(pago.formatted("EXCEL")).get("movimientos").get(5);
        assertThat(excel.has("sobranteCliente")).isFalse();
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
        assertThat(r.get("claves")).hasSize(30);
        // Ronda 8 (Diego 2026-10-28).
        assertThat(negocio.get("cobranzaPorPeriodo").asString()).isEqualTo("40000");
        assertThat(excel.get("cobranzaPorPeriodo").isNull()).isTrue();
        // D70 (ronda 10): NEGOCIO ya no suspende por umbral; la clave queda vacía.
        assertThat(negocio.get("cuotasVencidasParaSuspenderServicios").isNull()).isTrue();
        assertThat(excel.get("cuotasVencidasParaSuspenderServicios").isNull()).isTrue();
        assertThat(negocio.get("redondeoCuotaAlPeso").asString()).isEqualTo("true");
        assertThat(excel.get("redondeoCuotaAlPeso").asString()).isEqualTo("false");
        assertThat(negocio.get("sobranteComoCuentaPorPagar").asString()).isEqualTo("true");
        assertThat(excel.get("sobranteComoCuentaPorPagar").asString()).isEqualTo("false");
    }

    @Test
    void lasClavesDeLasRondas9y10SalenEnLasReglas() throws Exception {
        JsonNode r = leer(get("/api/v1/reglas"));
        JsonNode negocio = r.get("perfiles").get("NEGOCIO");
        JsonNode excel = r.get("perfiles").get("EXCEL");
        // Ronda 9 (Diego 2026-10-30).
        // D71 (ronda 10): la reemplaza el cociente de cobranza.
        assertThat(negocio.get("umbralesCuentanCuotaEnCurso").asString()).isEqualTo("false");
        assertThat(excel.get("umbralesCuentanCuotaEnCurso").asString()).isEqualTo("false");
        assertThat(negocio.get("seguroTodoRiesgoPorcentajeMensual").asString()).isEqualTo("0.003");
        assertThat(excel.get("seguroTodoRiesgoPorcentajeMensual").asString()).isEqualTo("0");
        // Ronda 10 (Diego 2026-10-31).
        assertThat(negocio.get("causacionServicios").asString()).isEqualTo("SIEMPRE");
        assertThat(excel.get("causacionServicios").asString()).isEqualTo("SOLO_AL_DIA");
        assertThat(negocio.get("umbralCocienteCobranza").asString()).isEqualTo("2");
        assertThat(excel.get("umbralCocienteCobranza").isNull()).isTrue();
        assertThat(negocio.get("baseMoraCobranza").asString()).isEqualTo("TOTAL_PENDIENTE");
        assertThat(negocio.get("comparacionCobranza").asString()).isEqualTo("MAYOR");
        assertThat(negocio.get("cuotasVencidasParaCobranza").asString()).isEqualTo("2");
        assertThat(excel.get("cuotasVencidasParaCobranza").isNull()).isTrue();
        assertThat(negocio.get("diasMora").asString()).isEqualTo("DIAS_CALENDARIO_MENSUAL");
        assertThat(excel.get("diasMora").asString()).isEqualTo("PERIODO_NOMINAL");
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
