package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Tasas;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * P5-PRORRATEO (D48) en el API: con {@code fechaDesembolso} y {@code primerVencimiento} el motor recibe el calendario y,
 * con NEGOCIO ({@code prorrateoPrimeraCuota=true}), cobra el interés del periodo 1 por los días reales. Sin fechas el
 * API responde exactamente lo de antes.
 */
@WebMvcTest(CarteraController.class)
@Import({ServicioCartera.class, PerfilesReglas.class})
class ProrrateoApiTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final BigDecimal MONTO = new BigDecimal("5904400");
    /** 2026-10-23 (viernes) → 2026-12-05: 31 + 12 = 43 días. */
    static final String MENSUAL_43_DIAS = """
            "fechaDesembolso":"2026-10-23","primerVencimiento":"2026-12-05"
            """.strip();

    @Autowired
    MockMvc mvc;

    static String credito(String periodicidad, int cuotas, String perfil, String extra) {
        return """
                {"linea":"CPPU","periodicidad":"%s","cuotas":%d,"valorMoto":"5100000","pagoInicial":"0","perfilReglas":"%s"%s}
                """.formatted(periodicidad, cuotas, perfil, extra.isEmpty() ? "" : "," + extra);
    }

    JsonNode postJson(String url, String cuerpo) throws Exception {
        MvcResult r = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    JsonNode getJson(String url) throws Exception {
        MvcResult r = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        return JSON.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    static BigDecimal bd(JsonNode n) {
        return new BigDecimal(n.asString());
    }

    /** Interés de {@code dias} días reales sobre el monto: monto × ((1 + tasa diaria)^días − 1). */
    static BigDecimal interesDias(Periodicidad per, int dias) {
        BigDecimal diaria = Tasas.de(ParametrosProducto.de(LineaCredito.CPPU, per, 24)).diaria();
        return Calc.multiplicar(MONTO, Calc.restar(Calc.potencia(Calc.sumar(Calc.UNO, diaria), dias), Calc.UNO));
    }

    static void igualAlPeso(BigDecimal real, BigDecimal esperado) {
        assertThat(real.subtract(esperado).abs()).isLessThan(BigDecimal.ONE);
    }

    @Nested
    class Simulaciones {

        @Test
        void conNegocioSoloElInteresDelPeriodoUnoSeProrrateaPorLosDiasReales() throws Exception {
            JsonNode con = postJson("/api/v1/simulaciones", credito("MENSUAL", 24, "NEGOCIO", MENSUAL_43_DIAS));
            JsonNode sin = postJson("/api/v1/simulaciones", credito("MENSUAL", 24, "NEGOCIO", ""));
            JsonNode pr = con.get("resumen").get("prorrateoPrimeraCuota");

            assertThat(pr.get("fechaDesembolso").asString()).isEqualTo("2026-10-23");
            assertThat(pr.get("primerVencimiento").asString()).isEqualTo("2026-12-05");
            assertThat(pr.get("diasPrimerPeriodo").asInt()).isEqualTo(43);
            assertThat(pr.get("diasNominales").asInt()).isEqualTo(30);
            assertThat(pr.get("aplicado").asBoolean()).isTrue();
            igualAlPeso(bd(pr.get("interesProrrateado")), interesDias(Periodicidad.MENSUAL, 43));
            // Sin prorrateo es el interés del periodo 1 sin fechas: saldo × tasa mensual.
            // R8-REDONDEO-PESO: con NEGOCIO el interés del plan absorbe el residuo (hasta medio peso) del redondeo de la cuota.
            igualAlPeso(bd(pr.get("interesSinProrrateo")), bd(sin.get("plan").get(0).get("interes")));
            assertThat(bd(pr.get("diferencia"))).isEqualByComparingTo(
                    bd(pr.get("interesProrrateado")).subtract(bd(pr.get("interesSinProrrateo"))));
            // 43 días cuestan más que un mes.
            assertThat(bd(pr.get("diferencia"))).isPositive();

            JsonNode fila1 = con.get("plan").get(0);
            JsonNode fila1Sin = sin.get("plan").get(0);
            assertThat(bd(fila1.get("interes"))).isEqualByComparingTo(bd(pr.get("interesProrrateado")));
            // Capital y servicios completos (D48).
            assertThat(bd(fila1.get("capital"))).isEqualByComparingTo(bd(fila1Sin.get("capital")));
            for (String s : new String[] {"fondoGarantias", "asistencia", "seguro"}) {
                assertThat(bd(fila1.get(s))).isEqualByComparingTo(bd(fila1Sin.get(s)));
            }
            assertThat(bd(fila1.get("cuotaFinanciera")))
                    .isEqualByComparingTo(bd(fila1.get("capital")).add(bd(fila1.get("interes"))));
            // El resumen muestra la cuota regular, no la prorrateada.
            assertThat(bd(con.get("resumen").get("cuotaFinanciera")))
                    .isEqualByComparingTo(bd(sin.get("resumen").get("cuotaFinanciera")));
            assertThat(bd(con.get("resumen").get("cuotaTotal")))
                    .isEqualByComparingTo(bd(sin.get("resumen").get("cuotaTotal")));
            // Desde el periodo 2 el plan es el mismo.
            assertThat(con.get("plan").get(5)).isEqualTo(sin.get("plan").get(5));
        }

        @Test
        void quincenalConPeriodoCortoCobraMenosInteres() throws Exception {
            JsonNode con = postJson("/api/v1/simulaciones", credito("QUINCENAL", 48, "NEGOCIO",
                    "\"fechaDesembolso\":\"2026-10-23\",\"primerVencimiento\":\"2026-11-05\""));
            JsonNode pr = con.get("resumen").get("prorrateoPrimeraCuota");
            assertThat(pr.get("diasPrimerPeriodo").asInt()).isEqualTo(13);
            assertThat(pr.get("diasNominales").asInt()).isEqualTo(15);
            igualAlPeso(bd(pr.get("interesProrrateado")), interesDias(Periodicidad.QUINCENAL, 13));
            assertThat(bd(pr.get("diferencia"))).isNegative();
        }

        @Test
        void conExcelLasFechasSeAceptanPeroNoSeProrratea() throws Exception {
            JsonNode con = postJson("/api/v1/simulaciones", credito("MENSUAL", 24, "EXCEL", MENSUAL_43_DIAS));
            JsonNode sin = postJson("/api/v1/simulaciones", credito("MENSUAL", 24, "EXCEL", ""));
            JsonNode pr = con.get("resumen").get("prorrateoPrimeraCuota");
            assertThat(pr.get("aplicado").asBoolean()).isFalse();
            assertThat(pr.get("diasPrimerPeriodo").asInt()).isEqualTo(43);
            assertThat(bd(pr.get("diferencia"))).isZero();
            assertThat(con.get("plan")).isEqualTo(sin.get("plan"));
        }

        @Test
        void sinFechasLaRespuestaNoCambiaNiTraeElCampoNuevo() throws Exception {
            JsonNode sin = postJson("/api/v1/simulaciones", credito("QUINCENAL", 48, "NEGOCIO", ""));
            assertThat(sin.get("resumen").has("prorrateoPrimeraCuota")).isFalse();
            JsonNode vacias = postJson("/api/v1/simulaciones", credito("QUINCENAL", 48, "NEGOCIO",
                    "\"fechaDesembolso\":null,\"primerVencimiento\":\"\""));
            assertThat(vacias).isEqualTo(sin);
        }
    }

    @Nested
    class Escenarios {

        @Test
        void ejecutarConFechasMuestraElProrrateoYLosAsientosCuadran() throws Exception {
            String cuerpo = """
                    {"credito": %s, "instrucciones": [{"periodo": 3, "tipo": "SIN_PAGO"}]}
                    """.formatted(credito("SEMANAL", 104, "NEGOCIO",
                    "\"fechaDesembolso\":\"2026-10-23\",\"primerVencimiento\":\"2026-11-03\""));
            JsonNode r = postJson("/api/v1/escenarios/ejecutar", cuerpo);
            JsonNode pr = r.get("resumen").get("prorrateoPrimeraCuota");
            assertThat(pr.get("diasPrimerPeriodo").asInt()).isEqualTo(11);
            assertThat(pr.get("aplicado").asBoolean()).isTrue();
            igualAlPeso(bd(pr.get("interesProrrateado")), interesDias(Periodicidad.SEMANAL, 11));
            assertThat(bd(r.get("movimientos").get(1).get("interesCorriente")))
                    .isEqualByComparingTo(bd(pr.get("interesProrrateado")));
            for (JsonNode a : r.get("asientos")) {
                assertThat(a.get("cuadra").asBoolean()).as("asiento del periodo %s", a.get("periodo")).isTrue();
            }
        }

        @Test
        void sinFechasElResumenNoTraeElCampoNuevo() throws Exception {
            JsonNode r = postJson("/api/v1/escenarios/ejecutar",
                    "{\"credito\": " + credito("SEMANAL", 104, "NEGOCIO", "") + "}");
            assertThat(r.get("resumen").has("prorrateoPrimeraCuota")).isFalse();
            assertThat(r.get("resumen").has("periodoListoParaRecuperacion")).isTrue();
        }
    }

    @Nested
    class Casos {

        static final String FECHAS = "&fechaDesembolso=2026-10-23&primerVencimiento=2026-11-03";

        @Test
        void elCasoConFechasDevuelveElCreditoConFechasYSeReejecutaIgual() throws Exception {
            JsonNode caso = getJson("/api/v1/casos/13?perfilReglas=NEGOCIO" + FECHAS);
            assertThat(caso.get("credito").get("fechaDesembolso").asString()).isEqualTo("2026-10-23");
            assertThat(caso.get("credito").get("primerVencimiento").asString()).isEqualTo("2026-11-03");
            assertThat(caso.get("resumen").get("prorrateoPrimeraCuota").get("diasPrimerPeriodo").asInt()).isEqualTo(11);

            ObjectNode peticion = JSON.createObjectNode();
            peticion.set("credito", caso.get("credito"));
            peticion.put("perfilReglas", "NEGOCIO");
            peticion.set("instrucciones", caso.get("instrucciones"));
            JsonNode otra = postJson("/api/v1/escenarios/ejecutar", JSON.writeValueAsString(peticion));
            assertThat(otra.get("resumen")).isEqualTo(caso.get("resumen"));
            assertThat(otra.get("movimientos")).isEqualTo(caso.get("movimientos"));
        }

        @Test
        void elCasoSinFechasNoLasDevuelve() throws Exception {
            JsonNode caso = getJson("/api/v1/casos/1?perfilReglas=NEGOCIO");
            assertThat(caso.get("credito").has("fechaDesembolso")).isFalse();
            assertThat(caso.get("credito").has("primerVencimiento")).isFalse();
            assertThat(caso.get("resumen").has("prorrateoPrimeraCuota")).isFalse();
        }

        @Test
        void losCasosSonSemanalesYElVencimientoDebeSerMartes() throws Exception {
            mvc.perform(get("/api/v1/casos/1?perfilReglas=NEGOCIO&fechaDesembolso=2026-10-23&primerVencimiento=2026-11-05"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalle.campo").value("primerVencimiento"))
                    .andExpect(jsonPath("$.mensaje", containsString("martes")));
        }
    }

    @Nested
    class Validaciones {

        void error(String periodicidad, String fechas, String campo, String fragmento) throws Exception {
            mvc.perform(post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON)
                    .content(credito(periodicidad, "MENSUAL".equals(periodicidad) ? 24 : 48, "NEGOCIO", fechas)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalle.campo").value(campo))
                    .andExpect(jsonPath("$.mensaje", containsString(fragmento)));
        }

        @Test
        void semanalFueraDeMartes() throws Exception {
            error("SEMANAL", "\"fechaDesembolso\":\"2026-10-23\",\"primerVencimiento\":\"2026-11-04\"",
                    "primerVencimiento", "vence los martes; el 2026-11-04 es miércoles");
        }

        @Test
        void quincenalFueraDel5YEl20() throws Exception {
            error("QUINCENAL", "\"fechaDesembolso\":\"2026-10-23\",\"primerVencimiento\":\"2026-11-15\"",
                    "primerVencimiento", "el 5 y el 20");
        }

        @Test
        void mensualFueraDelDia5() throws Exception {
            error("MENSUAL", "\"fechaDesembolso\":\"2026-10-23\",\"primerVencimiento\":\"2026-11-06\"",
                    "primerVencimiento", "vence el día 5");
        }

        @Test
        void lasFechasVanJuntas() throws Exception {
            error("MENSUAL", "\"fechaDesembolso\":\"2026-10-23\"", "primerVencimiento", "Las fechas van juntas");
            error("MENSUAL", "\"primerVencimiento\":\"2026-12-05\"", "fechaDesembolso", "falta fechaDesembolso");
        }

        @Test
        void fechaMalEscrita() throws Exception {
            error("MENSUAL", "\"fechaDesembolso\":\"23/10/2026\",\"primerVencimiento\":\"2026-12-05\"",
                    "fechaDesembolso", "yyyy-MM-dd");
            error("MENSUAL", "\"fechaDesembolso\":\"2026-10-23\",\"primerVencimiento\":\"2026-02-30\"",
                    "primerVencimiento", "yyyy-MM-dd");
        }

        @Test
        void elVencimientoDebeSerPosteriorAlDesembolso() throws Exception {
            error("MENSUAL", "\"fechaDesembolso\":\"2026-12-05\",\"primerVencimiento\":\"2026-12-05\"",
                    "primerVencimiento", "posterior");
            error("MENSUAL", "\"fechaDesembolso\":\"2026-12-10\",\"primerVencimiento\":\"2026-12-05\"",
                    "primerVencimiento", "posterior");
        }
    }
}
