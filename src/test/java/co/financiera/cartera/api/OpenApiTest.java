package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El contrato OpenAPI que se entrega a quienes consumen el API (p. ej. la sede de Cartagena). Arranca el servidor real con
 * una clave configurada: la documentación se lee sin clave y los datos la siguen pidiendo. La copia comprometida en
 * {@code docs/openapi.yaml} debe ser igual a la que sirve el API; tras un cambio intencional del contrato:
 * {@code ./mvnw test -Dtest=OpenApiTest -Dregenerar=true} y revisar el diff.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "cartera.api.claves=1c9395741bff745f301ef2cee9732f0d0fa4ffd19ae78a5add4fc5dc1c380c04:LECTURA")
class OpenApiTest {

    static final Path CONTRATO = Path.of("docs", "openapi.yaml");

    @Value("${local.server.port}")
    private int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private HttpResponse<String> get(String ruta, String clave) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET();
        if (clave != null) {
            b.header("X-API-Key", clave);
        }
        return cliente.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    void elContratoDescribeLosOchoEndpointsYLaClave() throws Exception {
        HttpResponse<String> r = get("/v3/api-docs", null);
        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode api = new ObjectMapper().readTree(r.body());
        assertThat(api.get("openapi").asString()).startsWith("3.");
        assertThat(api.get("info").get("title").asString()).isEqualTo("API del motor de cartera");
        // Solo sustantivos: la acción la da el método HTTP. Las rutas con verbo obsoletas no salen en el contrato.
        assertThat(api.get("paths").propertyNames()).containsExactlyInAnyOrder("/api/v1/catalogo", "/api/v1/reglas",
                "/api/v1/simulaciones", "/api/v1/escenarios", "/api/v1/escenarios/lotes",
                "/api/v1/casos", "/api/v1/casos/{numero}", "/api/v1/clasificaciones");
        for (String ruta : api.get("paths").propertyNames()) {
            assertThat(ruta).as("ruta sin verbos").doesNotContain("ejecutar", "clasificar", "calcular", "simular");
        }
        JsonNode esquema = api.get("components").get("securitySchemes").get(ConfiguracionOpenApi.ESQUEMA_CLAVE);
        assertThat(esquema.get("type").asString()).isEqualTo("apiKey");
        assertThat(esquema.get("in").asString()).isEqualTo("header");
        assertThat(esquema.get("name").asString()).isEqualTo("X-API-Key");
        // Los records del API salen como esquemas (lo que necesita quien genera un cliente).
        assertThat(api.get("components").get("schemas").propertyNames()).contains("CreditoRequest",
                "SimulacionResponse", "EjecutarRequest", "ErrorDto");
    }

    @Test
    void laDocumentacionNoPideClavePeroLosDatosSi() throws Exception {
        assertThat(get("/v3/api-docs.yaml", null).statusCode()).isEqualTo(200);
        assertThat(get("/swagger-ui.html", null).statusCode()).isIn(200, 302);
        assertThat(get("/api/v1/catalogo", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/catalogo", "clave-de-prueba").statusCode()).isEqualTo(200);
    }

    @Test
    void laCopiaComprometidaEsLaQueSirveElApi() throws Exception {
        String servido = get("/v3/api-docs.yaml", null).body().replace("\r\n", "\n");
        if (Boolean.getBoolean("regenerar")) {
            Files.writeString(CONTRATO, servido, StandardCharsets.UTF_8);
            return;
        }
        assertThat(CONTRATO).as("Falta %s: ./mvnw test -Dtest=OpenApiTest -Dregenerar=true", CONTRATO).exists();
        assertThat(servido)
                .as("%s ya no coincide con el contrato del API. Si el cambio es intencional: "
                        + "./mvnw test -Dtest=OpenApiTest -Dregenerar=true y revise el diff", CONTRATO)
                .isEqualTo(Files.readString(CONTRATO, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }
}
