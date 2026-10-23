package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * config/reglas-negocio.properties se escribe a mano en cada ronda: una clave mal escrita (p. ej. {@code diasmora})
 * se ignoraría en silencio y NEGOCIO quedaría como el Excel en esa regla. Aquí se lee en modo estricto.
 */
class ReglasNegocioArchivoEstrictoTest {

    @Test
    void elArchivoDeReglasNoTieneClavesMalEscritas() {
        Map<String, String> config = PerfilesReglas.leer(Path.of("config", "reglas-negocio.properties"));
        assertThat(config).isNotEmpty();
        assertThat(ReglasNegocio.clavesDesconocidas(config)).isEmpty();
        assertThat(ReglasNegocio.desdeEstricto(config)).isEqualTo(ReglasNegocio.desde(config));
    }

    @Test
    void laCopiaEmpaquetadaTampoco() {
        Map<String, String> config = PerfilesReglas.leerDelClasspath("config/reglas-negocio.properties");
        assertThat(config).isNotEmpty();
        assertThat(ReglasNegocio.desdeEstricto(config)).isEqualTo(ReglasNegocio.desde(config));
    }
}
