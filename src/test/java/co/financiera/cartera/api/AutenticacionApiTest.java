package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Autenticación opcional por X-API-Key con dos claves: una CONSUMIDOR y una LECTURA. */
@WebMvcTest(CarteraController.class)
@Import({ServicioCartera.class, PerfilesReglas.class, ConfiguracionFiltros.class})
@TestPropertySource(properties = "cartera.api.claves=" + AutenticacionApiTest.CONFIG)
class AutenticacionApiTest {

    static final String CLAVE_CONSUMIDOR = "clave-consumidor-de-prueba";
    static final String CLAVE_LECTURA = "clave-lectura-de-prueba";
    // sha256 de las dos claves de arriba (printf '<clave>' | sha256sum).
    static final String CONFIG = "6a20cc797f0c4a348e3144e82b14eb8d0d5995e9ca57284c3182533f9a8e848c:CONSUMIDOR,"
            + "5ef06e8a3c599a800d8156e58b8f4200ace8addae29034c5b23cfe6addeb45d7:LECTURA";

    @Autowired
    MockMvc mvc;

    private ResultActions postClasificar(String clave) throws Exception {
        var req = post("/api/v1/pagos/clasificar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorRecibido\":\"400000\",\"valorAdeudado\":\"355667\"}");
        if (clave != null) {
            req.header("X-API-Key", clave);
        }
        return mvc.perform(req);
    }

    @Test
    void loshashesDeLaConfiguracionSonLosDeLasClaves() throws Exception {
        assertThat(CONFIG).startsWith(sha256(CLAVE_CONSUMIDOR) + ":CONSUMIDOR").contains(sha256(CLAVE_LECTURA) + ":LECTURA");
    }

    @Test
    void sinClaveResponde401() throws Exception {
        mvc.perform(get("/api/v1/catalogo")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        postClasificar(null).andExpect(status().isUnauthorized());
    }

    @Test
    void claveInvalidaResponde401() throws Exception {
        mvc.perform(get("/api/v1/catalogo").header("X-API-Key", "otra")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
        // El hash configurado no sirve como clave.
        mvc.perform(get("/api/v1/catalogo").header("X-API-Key", sha256(CLAVE_CONSUMIDOR)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void claveConsumidorValidaResponde200EnTodo() throws Exception {
        mvc.perform(get("/api/v1/catalogo").header("X-API-Key", CLAVE_CONSUMIDOR)).andExpect(status().isOk());
        postClasificar(CLAVE_CONSUMIDOR).andExpect(status().isOk());
    }

    @Test
    void claveDeLecturaSoloLeeCatalogoReglasYCasos() throws Exception {
        for (String ruta : new String[] {"/api/v1/catalogo", "/api/v1/reglas", "/api/v1/casos", "/api/v1/casos/1"}) {
            mvc.perform(get(ruta).header("X-API-Key", CLAVE_LECTURA)).andExpect(status().isOk());
        }
        postClasificar(CLAVE_LECTURA).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mvc.perform(post("/api/v1/simulaciones").header("X-API-Key", CLAVE_LECTURA)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cuotas\":104}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/casos/../pagos/clasificar").header("X-API-Key", CLAVE_LECTURA))
                .andExpect(status().isForbidden());
    }

    @Test
    void optionsNoPideClave() throws Exception {
        mvc.perform(options("/api/v1/simulaciones").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type, X-API-Key")).andExpect(status().isOk());
    }

    @Test
    void saludNoPideClave() throws Exception {
        // Sin la capa de actuator en WebMvcTest responde 404, pero nunca 401: el filtro la deja pasar.
        mvc.perform(get("/actuator/health")).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    @Test
    void unaConfiguracionMalFormadaImpideArrancarSinFiltrarLaClave() {
        assertThatThrownBy(() -> new FiltroApiKey("mi-clave-en-claro:CONSUMIDOR"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("mi-clave-en-claro");
        assertThatThrownBy(() -> new FiltroApiKey(sha256("x") + ":ADMIN")).isInstanceOf(IllegalStateException.class);
        // Vacía o ausente: filtro inactivo, no falla.
        new FiltroApiKey("");
        new FiltroApiKey(null);
    }

    static String sha256(String clave) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(clave.getBytes(StandardCharsets.UTF_8)));
    }
}
