package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

/** Un grupo de pruebas por endpoint del API v1 (docs/api.md). */
@WebMvcTest(CarteraController.class)
@Import({ServicioCartera.class, PerfilesReglas.class})
class CarteraControllerTest {

    static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    static String quincenal(String plazo) {
        return """
                {"linea":"CPPU","periodicidad":"QUINCENAL",%s,"valorMoto":"5100000","pagoInicial":"0","perfilReglas":"NEGOCIO"}
                """.formatted(plazo);
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

    static BigDecimal alPeso(JsonNode n) {
        return new BigDecimal(n.asString()).setScale(0, RoundingMode.HALF_UP);
    }

    @Nested
    class Catalogo {

        @Test
        void tieneLineasPeriodicidadesYTiposConLosNombresDelMotor() throws Exception {
            mvc.perform(get("/api/v1/catalogo")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.lineas[0].codigo").value("CPPU"))
                    .andExpect(jsonPath("$.lineas[0].tasaEA").value("0.8762"))
                    .andExpect(jsonPath("$.lineas[0].plazoMaximoMeses").value(24))
                    .andExpect(jsonPath("$.lineas[0].periodicidades", hasItem("QUINCENAL")))
                    .andExpect(jsonPath("$.periodicidades[1].codigo").value("QUINCENAL"))
                    .andExpect(jsonPath("$.periodicidades[1].periodosPorAnio").value(24))
                    // R4-QUIN (Diego, ronda 4): la quincena son 24 periodos por año, ya no está por confirmar.
                    .andExpect(jsonPath("$.periodicidades[1].porConfirmar").value(false))
                    .andExpect(jsonPath("$.lineas[3].codigo").value("ORDINARIO"))
                    .andExpect(jsonPath("$.lineas[3].plazoMaximoMeses").value(24))
                    .andExpect(jsonPath("$.periodicidades[0].porConfirmar").value(false))
                    .andExpect(jsonPath("$.tiposInstruccion", hasSize(10)))
                    .andExpect(jsonPath("$.tiposInstruccion", hasItem("CIERRE_POR_RECUPERACION")))
                    .andExpect(jsonPath("$.modalidadesAbono", hasItem("MENOR_CUOTA")))
                    .andExpect(jsonPath("$.modalidadesCambioTasa", hasItem("MISMA_FECHA_FINAL")))
                    .andExpect(jsonPath("$.tiposRecuperacion", hasItem("DACION_EN_PAGO")))
                    .andExpect(jsonPath("$.valoresPorDefecto.valorMoto").value("5100000"))
                    .andExpect(jsonPath("$.valoresPorDefecto.montoFinanciado").value("5904400"));
        }
    }

    @Nested
    class Reglas {

        @Test
        void muestraLosDosPerfilesYLasClaves() throws Exception {
            mvc.perform(get("/api/v1/reglas")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.perfiles.EXCEL.aplicacionAbono").value("SOLO_CAPITAL"))
                    .andExpect(jsonPath("$.perfiles.EXCEL.modalidadUsura").value("MISMA_FECHA_FINAL"))
                    .andExpect(jsonPath("$.perfiles.NEGOCIO.aplicacionAbono").value("PRELACION_COMPLETA"))
                    .andExpect(jsonPath("$.perfiles.NEGOCIO.cobranzaIngresoNoOperacional").value("true"))
                    .andExpect(jsonPath("$.perfiles.NEGOCIO.plazoLimiteMeses").value("24"))
                    .andExpect(jsonPath("$.perfiles.EXCEL.plazoLimiteMeses").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.perfiles.NEGOCIO.modalidadUsura").value("MISMA_CUOTA"))
                    .andExpect(jsonPath("$.perfiles.NEGOCIO.margenUsura").value("1"))
                    .andExpect(jsonPath("$.claves", hasSize(19)))
                    .andExpect(jsonPath("$.claves[0].clave").value("prelacion"))
                    .andExpect(jsonPath("$.archivoNegocio").value("config/reglas-negocio.properties"));
        }
    }

    @Nested
    class Simulaciones {

        /** docs/reglas/planes-ejemplo-periodicidad.md: quincenal, 48 cuotas, 219.093 financiera y 355.667 total. */
        @Test
        void laQuincenalDeLaLineaEstandarDaLaCuotaDelPlanDeEjemplo() throws Exception {
            JsonNode r = postJson("/api/v1/simulaciones", quincenal("\"cuotas\":48,\"meses\":null"));
            JsonNode resumen = r.get("resumen");
            assertThat(resumen.get("cuotas").asInt()).isEqualTo(48);
            assertThat(alPeso(resumen.get("cuotaFinanciera"))).isEqualByComparingTo("219093");
            assertThat(alPeso(resumen.get("cuotaTotal"))).isEqualByComparingTo("355667");
            assertThat(alPeso(resumen.get("totalPagado"))).isEqualByComparingTo("17072015");
            assertThat(alPeso(resumen.get("totalIntereses"))).isEqualByComparingTo("4612061");
            assertThat(alPeso(resumen.get("totalServicios"))).isEqualByComparingTo("6555554");
            assertThat(resumen.get("montoFinanciado").asString()).isEqualTo("5904400");
            assertThat(resumen.get("tasaEA").asString()).isEqualTo("0.8762");
            assertThat(new BigDecimal(resumen.get("tasaPeriodo").asString())).isCloseTo(new BigDecimal("0.026565419"),
                    org.assertj.core.data.Offset.offset(new BigDecimal("0.000000001")));
            assertThat(resumen.get("periodicidad").asString()).isEqualTo("QUINCENAL");
            assertThat(resumen.get("porConfirmar").size()).isZero(); // R4-QUIN
            JsonNode plan = r.get("plan");
            assertThat(plan.size()).isEqualTo(48);
            assertThat(alPeso(plan.get(0).get("interes"))).isEqualByComparingTo("156853");
            assertThat(alPeso(plan.get(0).get("capital"))).isEqualByComparingTo("62240");
            assertThat(alPeso(plan.get(0).get("fondoGarantias"))).isEqualByComparingTo("105394");
            assertThat(alPeso(plan.get(47).get("saldo"))).isEqualByComparingTo("0");
        }

        @Test
        void conMesesCalculaLasCuotasComoInputsB15() throws Exception {
            JsonNode r = postJson("/api/v1/simulaciones", quincenal("\"meses\":24"));
            assertThat(r.get("resumen").get("cuotas").asInt()).isEqualTo(48);
            JsonNode semanal = postJson("/api/v1/simulaciones", "{\"periodicidad\":\"SEMANAL\",\"meses\":7}");
            assertThat(semanal.get("resumen").get("cuotas").asInt()).isEqualTo(30);
            assertThat(semanal.get("resumen").get("porConfirmar").size()).isZero();
        }

        @Test
        void masDe24MesesEsPlazoMaximo() throws Exception {
            mvc.perform(post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON).content(quincenal("\"meses\":25")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PLAZO_MAXIMO"))
                    .andExpect(jsonPath("$.detalle.maximoCuotas").value(48));
            mvc.perform(post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON).content(quincenal("\"cuotas\":49")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PLAZO_MAXIMO"));
        }

        @Test
        void sinPlazoOConValoresMalosEsValidacion() throws Exception {
            mvc.perform(post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON).content("{\"linea\":\"CPPU\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
            mvc.perform(post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"linea\":\"NO_EXISTE\",\"cuotas\":10}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalle.valoresValidos", hasItem("CPPU")));
            mvc.perform(post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cuotas\":10,\"valorMoto\":\"abc\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalle.campo").value("valorMoto"));
            mvc.perform(post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cuotas\":10,\"pagoInicial\":\"-1\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
            mvc.perform(post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
    }

    @Nested
    class Escenarios {

        static final String EJEMPLO_CONTRATO = """
                {"credito":{"linea":"CPPU","periodicidad":"SEMANAL","cuotas":104,"valorMoto":"5100000","pagoInicial":"0"},
                 "perfilReglas":"NEGOCIO",
                 "instrucciones":[{"periodo":40,"tipo":"SIN_PAGO","causaMoraYServicios":true},
                                  {"periodo":41,"tipo":"PAGO_VALOR","valor":"5000","diasAtraso":3},
                                  {"periodo":42,"tipo":"ABONO_EXTRA","valor":"1500000","modalidad":"MENOR_PLAZO"},
                                  {"periodo":43,"tipo":"USURA_VIGENTE","usuraEA":"0.81"}],
                 "incluirAsientos":true}
                """;

        @Test
        void correElEjemploDelContratoConAsientosCuadrados() throws Exception {
            mvc.perform(post("/api/v1/escenarios/ejecutar").contentType(MediaType.APPLICATION_JSON).content(EJEMPLO_CONTRATO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resumen.estadoFinal").value("CANCELADO"))
                    .andExpect(jsonPath("$.resumen.saldoFinal").value("0"))
                    .andExpect(jsonPath("$.resumen.perfilReglas").value("NEGOCIO"))
                    .andExpect(jsonPath("$.movimientos[0].estado").value("DESEMBOLSADO"))
                    .andExpect(jsonPath("$.movimientos[42].periodo").value(42))
                    .andExpect(jsonPath("$.movimientos[42].estado").value("VIGENTE_AL_DIA"))
                    .andExpect(jsonPath("$.movimientos[42].etiqueta").value("Cuota + abono extra"))
                    .andExpect(jsonPath("$.asientos", not(hasSize(0))))
                    .andExpect(jsonPath("$.asientos[*].cuadra", everyItem(is(true))))
                    .andExpect(jsonPath("$.asientos[0].lineas[0].cuenta").exists());
        }

        @Test
        void sinAsientosSiNoSePiden() throws Exception {
            mvc.perform(post("/api/v1/escenarios/ejecutar").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"credito\":{\"cuotas\":24,\"periodicidad\":\"MENSUAL\"},\"incluirAsientos\":false}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.asientos", hasSize(0)))
                    .andExpect(jsonPath("$.resumen.periodoFinal").value(24));
        }

        @Test
        void unAbonoMayorQueElSaldoEsAbonoMayorQueSaldo() throws Exception {
            mvc.perform(post("/api/v1/escenarios/ejecutar").contentType(MediaType.APPLICATION_JSON).content("""
                    {"credito":{"cuotas":104},"instrucciones":[{"periodo":10,"tipo":"ABONO_EXTRA","valor":"99000000"}]}
                    """)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("ABONO_MAYOR_QUE_SALDO"))
                    .andExpect(jsonPath("$.detalle.periodo").value(10))
                    .andExpect(jsonPath("$.detalle.abono").value("99000000"))
                    .andExpect(jsonPath("$.mensaje", startsWith("Semana 10")));
        }

        @Test
        void instruccionesInvalidasSonValidacion() throws Exception {
            mvc.perform(post("/api/v1/escenarios/ejecutar").contentType(MediaType.APPLICATION_JSON).content("""
                    {"credito":{"cuotas":104},"instrucciones":[{"periodo":10,"tipo":"BAILAR"}]}
                    """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalle.campo").value("tipo"));
            mvc.perform(post("/api/v1/escenarios/ejecutar").contentType(MediaType.APPLICATION_JSON).content("""
                    {"credito":{"cuotas":104},"instrucciones":[{"periodo":10,"tipo":"PAGO_VALOR"}]}
                    """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalle.campo").value("valor"));
            mvc.perform(post("/api/v1/escenarios/ejecutar").contentType(MediaType.APPLICATION_JSON).content("""
                    {"credito":{"cuotas":104},"instrucciones":[{"periodo":999,"tipo":"PAGO_AL_DIA"}]}
                    """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalle.campo").value("periodo"));
            mvc.perform(post("/api/v1/escenarios/ejecutar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalle.campo").value("credito"));
        }
    }

    @Nested
    class Casos {

        @Test
        void listaLosDoceDelExcelYElTrece() throws Exception {
            mvc.perform(get("/api/v1/casos")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(13)))
                    .andExpect(jsonPath("$[0].numero").value(1)).andExpect(jsonPath("$[0].nombre").value("Operación normal"))
                    .andExpect(jsonPath("$[0].fuente").value("Excel 11-oct, hoja Caso1_Normal"))
                    .andExpect(jsonPath("$[12].numero").value(13));
        }

        @Test
        void unCasoInexistenteEs404() throws Exception {
            mvc.perform(get("/api/v1/casos/99")).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("CASO_INEXISTENTE"));
        }

        @Test
        void unPerfilInexistenteEs400() throws Exception {
            mvc.perform(get("/api/v1/casos/1?perfilReglas=OTRO")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }

        /** Caso13AbonoConPendientesTest: con NEGOCIO el abono paga 317.354,81 de pendientes y termina en la 83. */
        @Test
        void elCaso13ConNegocioPagaPrimeroLosPendientes() throws Exception {
            JsonNode r = getJson("/api/v1/casos/13?perfilReglas=NEGOCIO");
            assertThat(r.get("caso").get("numero").asInt()).isEqualTo(13);
            assertThat(r.get("perfilReglas").asString()).isEqualTo("NEGOCIO");
            JsonNode semana = r.get("movimientos").get(42);
            // R4-CASO13 (PRELACION_COMPLETA): lo vencido (317.354,81) más el fondo y la asistencia de la semana 42.
            assertThat(new BigDecimal(semana.get("pendientesPagadosExtra").get("total").asString()))
                    .isCloseTo(new BigDecimal("376982.59"), org.assertj.core.data.Offset.offset(BigDecimal.ONE));
            assertThat(semana.get("estado").asString()).isEqualTo("VIGENTE_AL_DIA");
            assertThat(r.get("resumen").get("periodoFinal").asInt()).isEqualTo(84);
            assertThat(r.get("instrucciones").size()).isEqualTo(4);
            assertThat(r.get("instrucciones").get(3).get("tipo").asString()).isEqualTo("ABONO_EXTRA");
            assertThat(r.get("instrucciones").get(3).get("valor").asString()).isEqualTo("1500000");
        }

        /** Las instrucciones y el crédito del caso, mandados a /escenarios/ejecutar, dan el mismo resultado. */
        @Test
        void lasInstruccionesDelCasoSeReejecutanIgual() throws Exception {
            for (int numero : new int[] {3, 4, 8, 9, 11, 13}) {
                JsonNode caso = getJson("/api/v1/casos/" + numero + "?perfilReglas=NEGOCIO");
                ObjectNode cuerpo = JSON.createObjectNode();
                cuerpo.set("credito", caso.get("credito"));
                cuerpo.put("perfilReglas", "NEGOCIO");
                cuerpo.set("instrucciones", caso.get("instrucciones"));
                String request = JSON.writeValueAsString(cuerpo);
                JsonNode ejecucion = postJson("/api/v1/escenarios/ejecutar", request);
                assertThat(ejecucion.get("resumen")).as("caso %d", numero).isEqualTo(caso.get("resumen"));
                assertThat(ejecucion.get("movimientos")).as("caso %d", numero).isEqualTo(caso.get("movimientos"));
            }
        }
    }

    @Nested
    class Pagos {

        @Test
        void separaElPagoCorrienteDelAbonoExtra() throws Exception {
            mvc.perform(post("/api/v1/pagos/clasificar").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"valorRecibido\":\"400000\",\"valorAdeudado\":\"355667\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.pagoCorriente").value("355667"))
                    .andExpect(jsonPath("$.abonoExtra").value("44333"));
        }

        @Test
        void valoresNegativosOFaltantesSonValidacion() throws Exception {
            mvc.perform(post("/api/v1/pagos/clasificar").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"valorRecibido\":\"-1\",\"valorAdeudado\":\"355667\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
            mvc.perform(post("/api/v1/pagos/clasificar").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"valorRecibido\":\"1\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalle.campo").value("valorAdeudado"));
        }
    }

    @Nested
    class Cors {

        @Test
        void permiteElFrontLocalYGitHubPages() throws Exception {
            for (String origen : new String[] {"http://localhost:5173", "https://juanc101195.github.io"}) {
                mvc.perform(options("/api/v1/simulaciones").header("Origin", origen)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                        .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origen));
            }
        }

        @Test
        void rechazaOtrosOrigenes() throws Exception {
            mvc.perform(options("/api/v1/catalogo").header("Origin", "https://otro.example")
                    .header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
        }
    }
}
