package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Endurecimiento del API: topes de números, escenarios y cuerpo, cabeceras, CORS y propiedades de despliegue. */
@WebMvcTest(CarteraController.class)
@Import({ServicioCartera.class, PerfilesReglas.class, ConfiguracionFiltros.class})
class EndurecimientoApiTest {

    @Autowired
    MockMvc mvc;

    private ResultActions enviar(String url, String cuerpo) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private void rechazaCampo(String url, String cuerpo, String campo) throws Exception {
        enviar(url, cuerpo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalle.campo").value(campo));
    }

    private static String simulacion(String extra) {
        return "{\"cuotas\":104,\"valorMoto\":\"5100000\"" + extra + "}";
    }

    // ---------------------------------------------------------------- 1. Números

    @Test
    void valorMotoConExponenteGiganteSeRechazaSinCalcular() throws Exception {
        rechazaCampo("/api/v1/simulaciones", "{\"cuotas\":104,\"valorMoto\":\"1e999999999\"}", "valorMoto");
    }

    @Test
    void clasificarConExponenteGiganteSeRechaza() throws Exception {
        rechazaCampo("/api/v1/pagos/clasificar", "{\"valorRecibido\":\"1e2147483647\",\"valorAdeudado\":\"1\"}",
                "valorRecibido");
    }

    @Test
    void masDe30DigitosOEscalaFueraDeRangoSeRechaza() throws Exception {
        rechazaCampo("/api/v1/simulaciones", simulacion("").replace("5100000", "1234567890123456789012345678901"),
                "valorMoto");
        rechazaCampo("/api/v1/simulaciones", simulacion("").replace("5100000", "0.000000000000000000001"),
                "valorMoto");
        rechazaCampo("/api/v1/simulaciones", simulacion("").replace("5100000", "1e-25"), "valorMoto");
        rechazaCampo("/api/v1/simulaciones", simulacion("").replace("5100000", "1e25"), "valorMoto");
    }

    @Test
    void dineroSobreElTopeNegativoOCeroSeRechaza() throws Exception {
        rechazaCampo("/api/v1/simulaciones", simulacion("").replace("5100000", "10000000001"), "valorMoto");
        rechazaCampo("/api/v1/simulaciones", simulacion("").replace("5100000", "-5100000"), "valorMoto");
        rechazaCampo("/api/v1/simulaciones", simulacion("").replace("5100000", "0"), "valorMoto");
        rechazaCampo("/api/v1/simulaciones", simulacion(",\"pagoInicial\":\"-1\""), "pagoInicial");
        rechazaCampo("/api/v1/pagos/clasificar", "{\"valorRecibido\":\"-1\",\"valorAdeudado\":\"1\"}", "valorRecibido");
        rechazaCampo("/api/v1/pagos/clasificar", "{\"valorRecibido\":\"1\",\"valorAdeudado\":\"99999999999\"}",
                "valorAdeudado");
    }

    @Test
    void tasasFueraDeCeroACincoSeRechazan() throws Exception {
        String credito = "{\"cuotas\":104}";
        for (String tasa : new String[] {"0", "-0.1", "5.01", "1e20"}) {
            rechazaCampo("/api/v1/escenarios/ejecutar", "{\"credito\":" + credito + ",\"instrucciones\":["
                    + "{\"periodo\":3,\"tipo\":\"CAMBIO_TASA\",\"nuevaTasaEA\":\"" + tasa + "\"}]}", "nuevaTasaEA");
        }
        rechazaCampo("/api/v1/escenarios/ejecutar", "{\"credito\":" + credito + ",\"instrucciones\":["
                + "{\"periodo\":3,\"tipo\":\"USURA_VIGENTE\",\"usuraEA\":\"6\"}]}", "usuraEA");
    }

    @Test
    void valoresDeInstruccionConExponenteGiganteSeRechazan() throws Exception {
        rechazaCampo("/api/v1/escenarios/ejecutar", "{\"credito\":{\"cuotas\":104},\"instrucciones\":["
                + "{\"periodo\":3,\"tipo\":\"PAGO_VALOR\",\"valor\":\"1e999999999\"}]}", "valor");
        rechazaCampo("/api/v1/escenarios/ejecutar", "{\"credito\":{\"cuotas\":104},\"instrucciones\":["
                + "{\"periodo\":3,\"tipo\":\"PAGO_PARCIAL\",\"fraccion\":\"1e999999999\"}]}", "fraccion");
    }

    @Test
    void fechasFueraDe2000A2100SeRechazan() throws Exception {
        rechazaCampo("/api/v1/simulaciones", simulacion(",\"periodicidad\":\"SEMANAL\","
                + "\"fechaDesembolso\":\"1999-12-28\",\"primerVencimiento\":\"2000-01-04\""), "fechaDesembolso");
        rechazaCampo("/api/v1/simulaciones", simulacion(",\"periodicidad\":\"SEMANAL\","
                + "\"fechaDesembolso\":\"2101-01-01\",\"primerVencimiento\":\"2101-01-08\""), "fechaDesembolso");
        mvc.perform(get("/api/v1/casos/1?fechaDesembolso=+999999999-01-01&primerVencimiento=2026-11-03"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- 2. Escenarios y cuerpo

    @Test
    void masDe500InstruccionesSeRechazan() throws Exception {
        String una = "{\"periodo\":1,\"tipo\":\"SIN_PAGO\"}";
        String lista = String.join(",", java.util.Collections.nCopies(501, una));
        rechazaCampo("/api/v1/escenarios/ejecutar",
                "{\"credito\":{\"cuotas\":104},\"instrucciones\":[" + lista + "]}", "instrucciones");
    }

    @Test
    void plazoLimiteSobreElDobleDeLasCuotasMaximasSeRechaza() throws Exception {
        rechazaCampo("/api/v1/simulaciones", simulacion(",\"plazoLimitePeriodos\":209"), "plazoLimitePeriodos");
        enviar("/api/v1/simulaciones", simulacion(",\"plazoLimitePeriodos\":208")).andExpect(status().isOk());
    }

    @Test
    void diasDeAtrasoFueraDeRangoSeRechazan() throws Exception {
        for (String dias : new String[] {"3651", "-1"}) {
            rechazaCampo("/api/v1/escenarios/ejecutar", "{\"credito\":{\"cuotas\":104},\"instrucciones\":["
                    + "{\"periodo\":3,\"tipo\":\"PAGO_CONTRACTUAL\",\"diasAtraso\":" + dias + "}]}", "diasAtraso");
        }
        rechazaCampo("/api/v1/escenarios/ejecutar", "{\"credito\":{\"cuotas\":104},\"instrucciones\":["
                + "{\"periodo\":3,\"tipo\":\"PREPAGO_TOTAL\",\"diasDesdeUltimaCuota\":3651}]}", "diasDesdeUltimaCuota");
    }

    @Test
    void cuerpoSobre256KbResponde413EnJsonDelContrato() throws Exception {
        String relleno = "x".repeat(300 * 1024);
        enviar("/api/v1/escenarios/ejecutar", "{\"credito\":{\"cuotas\":104},\"nota\":\"" + relleno + "\"}")
                .andExpect(status().isContentTooLarge()).andExpect(jsonPath("$.codigo").value("CUERPO_DEMASIADO_GRANDE"))
                .andExpect(jsonPath("$.mensaje").exists()).andExpect(jsonPath("$.detalle").exists())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void cuerpoChunkedSobreElTopeTambienResponde413() throws Exception {
        FiltroTamanoCuerpo filtro = new FiltroTamanoCuerpo(1024);
        MockHttpServletRequest base = new MockHttpServletRequest("POST", "/api/v1/simulaciones");
        base.setContent(new byte[2048]);
        HttpServletRequest sinLargo = new HttpServletRequestWrapper(base) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();
        filtro.doFilter(sinLargo, respuesta, cadena);
        assertThat(respuesta.getStatus()).isEqualTo(413);
        assertThat(cadena.getRequest()).isNull();

        MockHttpServletRequest chico = new MockHttpServletRequest("POST", "/api/v1/simulaciones");
        chico.setContent("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        HttpServletRequest chunked = new HttpServletRequestWrapper(chico) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        MockFilterChain pasa = new MockFilterChain();
        filtro.doFilter(chunked, new MockHttpServletResponse(), pasa);
        assertThat(pasa.getRequest()).isNotNull();
        assertThat(new String(pasa.getRequest().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("{\"a\":1}");
    }

    // ---------------------------------------------------------------- 3. Errores

    @Test
    void tipoInvalidoNoDevuelveLoQueMandoElUsuario() throws Exception {
        mvc.perform(get("/api/v1/casos/abc%3Cscript%3E")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION")).andExpect(jsonPath("$.detalle.campo").value("numero"))
                .andExpect(content().string(not(containsString("script"))));
    }

    @Test
    void valorDeEnumInvalidoNoSeEcha() throws Exception {
        enviar("/api/v1/simulaciones", simulacion(",\"periodicidad\":\"ZZZ_RARO\""))
                .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("ZZZ_RARO"))));
    }

    // ---------------------------------------------------------------- 4. Cabeceras y CORS

    @Test
    void lasRespuestasDeApiLlevanCabecerasDeSeguridad() throws Exception {
        mvc.perform(get("/api/v1/catalogo")).andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void corsSoloPermiteContentTypeYApiKey() throws Exception {
        mvc.perform(options("/api/v1/simulaciones").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type, X-API-Key")).andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/v1/simulaciones").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-Otra-Cabecera")).andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/simulaciones").header("Origin", "https://malo.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- 4 y 5. Propiedades de despliegue

    @Test
    void propiedadesDeEndurecimientoYCompresion() throws IOException {
        Properties p = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertThat(p.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        for (String k : new String[] {"message", "binding-errors", "stacktrace", "exception"}) {
            assertThat(p.getProperty("server.error.include-" + k)).as(k).isEqualTo("never");
        }
        assertThat(p.getProperty("server.compression.enabled")).isEqualTo("true");
        assertThat(p.getProperty("server.compression.mime-types")).isEqualTo("application/json");
        assertThat(p.getProperty("server.compression.min-response-size")).isEqualTo("1024");
    }

    // ---------------------------------------------------------------- 6. Sin claves configuradas no cambia nada

    @Test
    void sinClavesConfiguradasElFiltroNoHaceNada() throws Exception {
        mvc.perform(get("/api/v1/catalogo")).andExpect(status().isOk());
        enviar("/api/v1/pagos/clasificar", "{\"valorRecibido\":\"400000\",\"valorAdeudado\":\"355667\"}")
                .andExpect(status().isOk());
    }
}
