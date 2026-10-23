package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Los errores del API con el contrato de {@code docs/api.md}: estado HTTP, {@code codigo}, {@code mensaje} y
 * {@code detalle.campo} estables, para que la consola pueda mostrarlos junto al campo que falló. Solo lo que el contrato
 * ya garantiza hoy.
 */
@WebMvcTest(CarteraController.class)
@Import({ServicioCartera.class, PerfilesReglas.class})
class ErroresApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    /** Respuesta de error ya leída. */
    private record Error(int estado, JsonNode cuerpo) {

        String codigo() {
            return cuerpo.get("codigo").asString();
        }

        String mensaje() {
            return cuerpo.get("mensaje").asString();
        }

        JsonNode detalle() {
            return cuerpo.get("detalle");
        }

        String campo() {
            return detalle().get("campo").asString();
        }
    }

    private Error error(MockHttpServletRequestBuilder peticion) throws Exception {
        var respuesta = mvc.perform(peticion).andReturn().getResponse();
        JsonNode cuerpo = JSON.readTree(respuesta.getContentAsString(StandardCharsets.UTF_8));
        // El contrato de todo error: exactamente estas tres claves.
        List<String> claves = new ArrayList<>();
        cuerpo.propertyNames().forEach(claves::add);
        assertThat(claves).containsExactlyInAnyOrder("codigo", "mensaje", "detalle");
        assertThat(cuerpo.get("detalle").isObject()).isTrue();
        return new Error(respuesta.getStatus(), cuerpo);
    }

    private Error simular(String cuerpo) throws Exception {
        return error(post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private Error ejecutar(String cuerpo) throws Exception {
        return error(post("/api/v1/escenarios/ejecutar").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static void esValidacion(Error e, String campo) {
        assertThat(e.estado()).isEqualTo(400);
        assertThat(e.codigo()).isEqualTo("VALIDACION");
        assertThat(e.mensaje()).isNotBlank();
        assertThat(e.campo()).isEqualTo(campo);
    }

    // ------------------------------------------------------------------ 404 y cuerpo ilegible

    @Test
    void unCasoInexistenteEs404ConElNumeroEnElDetalle() throws Exception {
        Error e = error(get("/api/v1/casos/99"));
        assertThat(e.estado()).isEqualTo(404);
        assertThat(e.codigo()).isEqualTo("CASO_INEXISTENTE");
        assertThat(e.mensaje()).isEqualTo("No existe el caso 99; los casos van del 1 al 13");
        assertThat(e.detalle().get("numero").asInt()).isEqualTo(99);
    }

    @Test
    void unCuerpoJsonMalformadoEs400ConUnMensajeGenerico() throws Exception {
        Error e = simular("{no es json");
        assertThat(e.estado()).isEqualTo(400);
        assertThat(e.codigo()).isEqualTo("VALIDACION");
        assertThat(e.mensaje()).isEqualTo("El cuerpo de la petición no es un JSON válido para este endpoint");
        assertThat(e.detalle().size()).isZero();
        // El mismo error en los demás endpoints con cuerpo.
        assertThat(ejecutar("[1,2").codigo()).isEqualTo("VALIDACION");
        assertThat(error(post("/api/v1/pagos/clasificar").contentType(MediaType.APPLICATION_JSON).content("{")).estado())
                .isEqualTo(400);
    }

    @Test
    void unTipoDeDatoQueNoCorrespondeAlCampoTambienEs400() throws Exception {
        Error e = simular("{\"cuotas\":\"muchas\"}");
        assertThat(e.estado()).isEqualTo(400);
        assertThat(e.codigo()).isEqualTo("VALIDACION");
    }

    @Test
    void unNumeroDeCasoQueNoEsEnteroEs400ConElCampo() throws Exception {
        Error e = error(get("/api/v1/casos/uno"));
        assertThat(e.estado()).isEqualTo(400);
        assertThat(e.codigo()).isEqualTo("VALIDACION");
        assertThat(e.campo()).isEqualTo("numero");
        assertThat(e.mensaje()).isEqualTo("Valor no válido para numero").doesNotContain("uno");
    }

    // ------------------------------------------------------------------ enums inválidos

    @Test
    void unaPeriodicidadInvalidaListaLasValidas() throws Exception {
        Error e = simular("{\"periodicidad\":\"TRIMESTRAL\",\"cuotas\":10}");
        esValidacion(e, "periodicidad");
        assertThat(e.mensaje()).isEqualTo("Valor no válido para periodicidad").doesNotContain("TRIMESTRAL");
        List<String> validos = new ArrayList<>();
        e.detalle().get("valoresValidos").forEach(v -> validos.add(v.asString()));
        assertThat(validos).containsExactly("SEMANAL", "QUINCENAL", "MENSUAL");
    }

    @Test
    void unaLineaInvalidaListaLasValidas() throws Exception {
        Error e = simular("{\"linea\":\"NO_EXISTE\",\"cuotas\":10}");
        esValidacion(e, "linea");
        assertThat(e.mensaje()).contains("linea").doesNotContain("NO_EXISTE");
        assertThat(e.detalle().get("valoresValidos").toString()).contains("CPPU");
    }

    @Test
    void unPerfilDeReglasInvalidoEsValidacion() throws Exception {
        Error e = simular("{\"cuotas\":10,\"perfilReglas\":\"OTRO\"}");
        esValidacion(e, "perfilReglas");
        assertThat(e.detalle().get("valoresValidos").toString()).contains("EXCEL").contains("NEGOCIO");
    }

    @Test
    void unTipoDeInstruccionInvalidoEsValidacionSobreElCampoTipo() throws Exception {
        Error e = ejecutar("{\"credito\":{\"cuotas\":104},\"instrucciones\":[{\"periodo\":10,\"tipo\":\"BAILAR\"}]}");
        esValidacion(e, "tipo");
        assertThat(e.mensaje()).contains("tipo").doesNotContain("BAILAR");
    }

    // ------------------------------------------------------------------ periodo fuera de rango

    @Test
    void unPeriodoDeInstruccionFueraDeRangoEsValidacionSobreElCampoPeriodo() throws Exception {
        for (String periodo : List.of("999", "0", "-3")) {
            Error e = ejecutar("{\"credito\":{\"cuotas\":104},\"instrucciones\":[{\"periodo\":" + periodo
                    + ",\"tipo\":\"PAGO_AL_DIA\"}]}");
            esValidacion(e, "periodo");
            assertThat(e.mensaje()).startsWith("Cada instrucción necesita un periodo entre 1 y ").endsWith(": " + periodo);
        }
        Error sinPeriodo = ejecutar("{\"credito\":{\"cuotas\":104},\"instrucciones\":[{\"tipo\":\"PAGO_AL_DIA\"}]}");
        esValidacion(sinPeriodo, "periodo");
        assertThat(sinPeriodo.mensaje()).endsWith(": null");
    }

    // ------------------------------------------------------------------ plazos

    @Test
    void sinCuotasNiMesesEsValidacionSobreElCampoCuotas() throws Exception {
        Error e = simular("{\"linea\":\"CPPU\"}");
        esValidacion(e, "cuotas");
        assertThat(e.mensaje()).isEqualTo("Mande cuotas o meses");
    }

    @Test
    void mesesYCuotasContradictoriosEsValidacionYPideSoloUnoDeLos2() throws Exception {
        // 7 meses semanales son 30 cuotas, no 31.
        Error e = simular("{\"periodicidad\":\"SEMANAL\",\"meses\":7,\"cuotas\":31}");
        esValidacion(e, "cuotas");
        assertThat(e.mensaje()).isEqualTo("7 meses son 30 cuotas semanales, no 31; mande solo cuotas o solo meses");
        // Los coherentes sí pasan.
        assertThat(mvc.perform(post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"periodicidad\":\"SEMANAL\",\"meses\":7,\"cuotas\":30}")).andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void mesesOCuotasMenoresQueUnoSonValidacion() throws Exception {
        Error meses = simular("{\"meses\":0}");
        esValidacion(meses, "meses");
        assertThat(meses.mensaje()).isEqualTo("Los meses deben ser al menos 1");
        Error cuotas = simular("{\"cuotas\":0}");
        esValidacion(cuotas, "cuotas");
        assertThat(cuotas.mensaje()).isEqualTo("Las cuotas deben ser al menos 1");
    }

    @Test
    void pasarseDelPlazoMaximoEsPlazoMaximoConLosTopesEnElDetalle() throws Exception {
        Error e = simular("{\"periodicidad\":\"QUINCENAL\",\"meses\":25}");
        assertThat(e.estado()).isEqualTo(400);
        assertThat(e.codigo()).isEqualTo("PLAZO_MAXIMO");
        assertThat(e.detalle().get("maximoMeses").asInt()).isEqualTo(24);
        assertThat(e.detalle().get("maximoCuotas").asInt()).isEqualTo(48);
        assertThat(e.detalle().get("periodicidad").asString()).isEqualTo("QUINCENAL");
    }

    // ------------------------------------------------------------------ fechas

    private static String conFechas(String periodicidad, String desembolso, String vencimiento) {
        return "{\"periodicidad\":\"" + periodicidad + "\",\"cuotas\":24,\"fechaDesembolso\":" + desembolso
                + ",\"primerVencimiento\":" + vencimiento + "}";
    }

    @Test
    void unaQuincenaQueNoVenceElCincoOElVeinteEsValidacionSobreElPrimerVencimiento() throws Exception {
        Error e = simular(conFechas("QUINCENAL", "\"2026-10-23\"", "\"2026-11-10\""));
        esValidacion(e, "primerVencimiento");
        assertThat(e.mensaje()).isEqualTo("En un crédito quincenal la cuota vence el 5 y el 20 de cada mes; el 2026-11-10 no es ninguno de los dos");
        // El 5 y el 20 sí son válidos.
        assertThat(mvc.perform(post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON)
                .content(conFechas("QUINCENAL", "\"2026-10-23\"", "\"2026-11-05\""))).andReturn().getResponse().getStatus())
                .isEqualTo(200);
        assertThat(mvc.perform(post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON)
                .content(conFechas("QUINCENAL", "\"2026-10-23\"", "\"2026-11-20\""))).andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void unaSemanaQueNoVenceUnMartesYUnMesQueNoVenceElCincoSonValidacion() throws Exception {
        Error semanal = simular(conFechas("SEMANAL", "\"2026-10-23\"", "\"2026-11-04\"")); // miércoles
        esValidacion(semanal, "primerVencimiento");
        assertThat(semanal.mensaje()).startsWith("En un crédito semanal la cuota vence los martes; el 2026-11-04 es ");
        Error mensual = simular(conFechas("MENSUAL", "\"2026-10-23\"", "\"2026-12-06\""));
        esValidacion(mensual, "primerVencimiento");
        assertThat(mensual.mensaje()).isEqualTo("En un crédito mensual la cuota vence el día 5 de cada mes; el 2026-12-06 no lo es");
    }

    @Test
    void lasFechasVanJuntasConFormatoIsoYElVencimientoDespuesDelDesembolso() throws Exception {
        Error sinVencimiento = simular("{\"cuotas\":24,\"fechaDesembolso\":\"2026-10-23\"}");
        esValidacion(sinVencimiento, "primerVencimiento");
        assertThat(sinVencimiento.mensaje()).startsWith("Las fechas van juntas: falta primerVencimiento");

        Error sinDesembolso = simular("{\"cuotas\":24,\"primerVencimiento\":\"2026-11-03\"}");
        esValidacion(sinDesembolso, "fechaDesembolso");

        Error formato = simular(conFechas("SEMANAL", "\"23/10/2026\"", "\"2026-11-03\""));
        esValidacion(formato, "fechaDesembolso");
        assertThat(formato.mensaje()).contains("yyyy-MM-dd").doesNotContain("23/10/2026");

        Error anterior = simular(conFechas("SEMANAL", "\"2026-11-03\"", "\"2026-10-27\""));
        esValidacion(anterior, "primerVencimiento");
        assertThat(anterior.mensaje()).startsWith("El primer vencimiento (2026-10-27) debe ser posterior");
    }

    // ------------------------------------------------------------------ crédito faltante

    @Test
    void unCreditoNuloOFaltanteEnEjecutarEsValidacionSobreElCampoCredito() throws Exception {
        for (String cuerpo : List.of("{}", "{\"credito\":null}", "{\"credito\":null,\"instrucciones\":[]}")) {
            Error e = ejecutar(cuerpo);
            esValidacion(e, "credito");
            assertThat(e.mensaje()).isEqualTo("Falta el crédito");
        }
    }

    @Test
    void unCuerpoNuloEnSimulacionesEsValidacion() throws Exception {
        Error vacio = simular("null");
        assertThat(vacio.estado()).isEqualTo(400);
        assertThat(vacio.codigo()).isEqualTo("VALIDACION");
        Error sinCuerpo = error(post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON));
        assertThat(sinCuerpo.estado()).isEqualTo(400);
        assertThat(sinCuerpo.codigo()).isEqualTo("VALIDACION");
    }

    @Test
    void unAbonoMayorQueElSaldoTieneSuPropioCodigoConPeriodoAbonoYSaldo() throws Exception {
        Error e = ejecutar("""
                {"credito":{"cuotas":104},"instrucciones":[{"periodo":10,"tipo":"ABONO_EXTRA","valor":"99000000"}]}
                """);
        assertThat(e.estado()).isEqualTo(400);
        assertThat(e.codigo()).isEqualTo("ABONO_MAYOR_QUE_SALDO");
        assertThat(e.detalle().get("periodo").asInt()).isEqualTo(10);
        assertThat(e.detalle().get("abono").asString()).isEqualTo("99000000");
        assertThat(new java.math.BigDecimal(e.detalle().get("saldo").asString())).isPositive();
        assertThat(e.mensaje()).isNotBlank();
    }
}
