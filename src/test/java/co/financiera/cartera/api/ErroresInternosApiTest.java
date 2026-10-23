package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Manejador genérico: 500 con id de correlación y sin filtrar el mensaje interno; los Error no se atrapan. */
@WebMvcTest(CarteraController.class)
@Import(ManejadorErrores.class)
class ErroresInternosApiTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ServicioCartera servicio;

    @Test
    void excepcionInesperadaDa500ConIdDeCorrelacionSinMensajeInterno() throws Exception {
        when(servicio.catalogo()).thenThrow(new IllegalStateException("detalle-secreto-interno"));
        mvc.perform(get("/api/v1/catalogo")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO")).andExpect(jsonPath("$.mensaje").isString())
                .andExpect(jsonPath("$.detalle.idCorrelacion").isNotEmpty())
                .andExpect(content().string(not(containsString("detalle-secreto-interno"))));
    }

    @Test
    void cadaErrorTieneUnIdDistinto() throws Exception {
        when(servicio.catalogo()).thenThrow(new IllegalStateException("x"));
        String a = mvc.perform(get("/api/v1/catalogo")).andReturn().getResponse().getContentAsString();
        String b = mvc.perform(get("/api/v1/catalogo")).andReturn().getResponse().getContentAsString();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void outOfMemoryErrorNoSeAtrapa() {
        when(servicio.catalogo()).thenThrow(new OutOfMemoryError("prueba"));
        assertThatThrownBy(() -> mvc.perform(get("/api/v1/catalogo")))
                .hasRootCauseInstanceOf(OutOfMemoryError.class);
    }

    @Test
    void erroresHttpDeSpringConservanSuEstado() throws Exception {
        mvc.perform(post("/api/v1/pagos/clasificar").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
