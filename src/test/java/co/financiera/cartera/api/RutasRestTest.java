package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Rutas REST con sustantivos (revisión de Heider, 2026-11-19): {@code POST /escenarios}, {@code POST /escenarios/lotes}
 * y {@code POST /clasificaciones}. Las rutas con verbo de antes siguen respondiendo lo mismo, pero avisan que son
 * obsoletas con las cabeceras {@code Deprecation} y {@code Link} (RFC 9745) y no salen en el contrato OpenAPI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RutasRestTest {

    private static final String ESCENARIO = """
            {"credito":{"linea":"CPPU","periodicidad":"SEMANAL","cuotas":104,"valorMoto":"5100000","pagoInicial":"0"},
             "perfilReglas":"NEGOCIO","incluirAsientos":false,
             "instrucciones":[{"periodo":3,"tipo":"SIN_PAGO"},{"periodo":4,"tipo":"PAGO_VALOR","valor":"400000","diasAtraso":2}]}""";
    private static final String PAGO = """
            {"valorRecibido":"150000","valorAdeudado":"169008"}""";

    @Value("${local.server.port}")
    private int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private HttpResponse<String> post(String ruta, String cuerpo) throws Exception {
        return cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void mismaRespuestaYObsoleta(String nueva, String vieja, String cuerpo) throws Exception {
        HttpResponse<String> n = post(nueva, cuerpo);
        HttpResponse<String> v = post(vieja, cuerpo);
        assertThat(n.statusCode()).as(nueva).isEqualTo(200);
        assertThat(v.statusCode()).as(vieja).isEqualTo(200);
        assertThat(v.body()).as("%s responde igual que %s", vieja, nueva).isEqualTo(n.body());
        assertThat(n.headers().firstValue("Deprecation")).as(nueva).isEmpty();
        assertThat(v.headers().firstValue("Deprecation")).as(vieja).hasValue("@1795046400");
        assertThat(v.headers().firstValue("Link")).as(vieja).hasValue("<" + nueva + ">; rel=\"successor-version\"");
    }

    @Test
    void escenario() throws Exception {
        mismaRespuestaYObsoleta("/api/v1/escenarios", "/api/v1/escenarios/ejecutar", ESCENARIO);
    }

    @Test
    void loteDeEscenarios() throws Exception {
        mismaRespuestaYObsoleta("/api/v1/escenarios/lotes", "/api/v1/escenarios/ejecutar-lote",
                "[" + ESCENARIO + "," + ESCENARIO + "]");
    }

    @Test
    void clasificacionDeUnPago() throws Exception {
        mismaRespuestaYObsoleta("/api/v1/clasificaciones", "/api/v1/pagos/clasificar", PAGO);
    }
}
