package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * El API arranca de verdad (Tomcat real, en un puerto aleatorio) con todos sus filtros, y los filtros actúan.
 *
 * <p>Las pruebas con {@code @WebMvcTest} usan un servidor simulado y no ven los fallos de arranque: así pasó un API
 * cuyos filtros, siendo beans proxificados por Spring Modulith, impedían iniciar el jar ("this.logger is null").
 * Esta prueba habría fallado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArranqueRealTest {

    @Value("${local.server.port}")
    private int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private HttpResponse<String> enviar(HttpRequest peticion) throws Exception {
        return cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String ruta) {
        return URI.create("http://localhost:" + puerto + ruta);
    }

    @Test
    void elServidorArrancaYResponde() throws Exception {
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(uri("/api/v1/catalogo")).GET().build());

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("lineas");
    }

    @Test
    void elFiltroDeCabecerasActuaEnElServidorReal() throws Exception {
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(uri("/api/v1/catalogo")).GET().build());

        assertThat(r.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(r.headers().firstValue("Cache-Control")).hasValue("no-store");
    }

    @Test
    void elFiltroDeTamanoRechazaUnCuerpoGigante() throws Exception {
        String cuerpo = "{\"x\":\"" + "a".repeat(300_000) + "\"}";
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(uri("/api/v1/simulaciones"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build());

        assertThat(r.statusCode()).isEqualTo(413);
        assertThat(r.body()).contains("CUERPO_DEMASIADO_GRANDE");
    }

    @Test
    void unNumeroConExponenteGiganteSeRechazaSinTumbarElServidor() throws Exception {
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(uri("/api/v1/simulaciones"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"cuotas\":104,\"valorMoto\":\"1e999999999\"}")).build());

        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).contains("VALIDACION");

        // Y el servidor sigue vivo.
        assertThat(enviar(HttpRequest.newBuilder(uri("/api/v1/catalogo")).GET().build()).statusCode()).isEqualTo(200);
    }
}
