package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Escribe en docs/api-ejemplos/ respuestas reales del API para que el front las use como fixtures. Se
 * regeneran en cada {@code ./mvnw test}; si el motor cambia, el diff de git muestra qué cambió.
 */
@WebMvcTest(CarteraController.class)
@Import({ServicioCartera.class, PerfilesReglas.class})
class EjemplosApiTest {

    static final Path CARPETA = Path.of("docs", "api-ejemplos");

    @Autowired
    MockMvc mvc;

    private void guardar(String archivo, RequestBuilder peticion) throws Exception {
        String cuerpo = mvc.perform(peticion).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ObjectMapper json = new ObjectMapper();
        String bonito = json.writerWithDefaultPrettyPrinter().writeValueAsString(json.readTree(cuerpo))
                .replace("\r\n", "\n") + "\n";
        Files.createDirectories(CARPETA);
        Path destino = CARPETA.resolve(archivo);
        Files.writeString(destino, bonito, StandardCharsets.UTF_8);
        assertThat(destino).isNotEmptyFile();
    }

    @Test
    void generaLosEjemplos() throws Exception {
        guardar("catalogo.json", get("/api/v1/catalogo"));
        guardar("simulacion-quincenal.json", post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"linea":"CPPU","periodicidad":"QUINCENAL","cuotas":48,"meses":null,"valorMoto":"5100000","pagoInicial":"0","perfilReglas":"NEGOCIO"}
                        """));
        guardar("caso-13-negocio.json", get("/api/v1/casos/13?perfilReglas=NEGOCIO"));
    }
}
