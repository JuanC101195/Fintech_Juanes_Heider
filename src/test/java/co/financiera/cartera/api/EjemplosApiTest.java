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
        // P5-PRORRATEO: con fechas el interés del periodo 1 va por los días reales (43 días del 23-oct al 5-dic).
        guardar("simulacion-mensual-prorrateo.json", post("/api/v1/simulaciones").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"linea":"CPPU","periodicidad":"MENSUAL","cuotas":24,"valorMoto":"5100000","pagoInicial":"0","perfilReglas":"NEGOCIO","fechaDesembolso":"2026-10-23","primerVencimiento":"2026-12-05"}
                        """));
        guardar("escenario-semanal-prorrateo.json", post("/api/v1/escenarios/ejecutar").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"credito":{"linea":"CPPU","periodicidad":"SEMANAL","cuotas":104,"valorMoto":"5100000","pagoInicial":"0","fechaDesembolso":"2026-10-23","primerVencimiento":"2026-11-03"},"perfilReglas":"NEGOCIO","instrucciones":[],"incluirAsientos":false}
                        """));
    }
}
