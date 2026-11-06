package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/** La clave es obligatoria en el perfil prod y opcional en el resto; sin levantar servidor. */
class AutenticacionObligatoriaProdTest {

    private static final String CLAVE_VALIDA =
            "6a20cc797f0c4a348e3144e82b14eb8d0d5995e9ca57284c3182533f9a8e848c:CONSUMIDOR";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracionFiltros.class);

    @Test
    void filtroObligatorioSinClaveFallaConMensajeClaro() {
        assertThatThrownBy(() -> new FiltroApiKey("", true)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CARTERA_API_CLAVES").hasMessageContaining("obligatoria");
        assertThatThrownBy(() -> new FiltroApiKey(null, true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FiltroApiKey(" , ", true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void filtroObligatorioConClaveArranca() {
        assertThatCode(() -> new FiltroApiKey(CLAVE_VALIDA, true)).doesNotThrowAnyException();
    }

    @Test
    void filtroPorDefectoSinClaveSigueAbierto() {
        assertThatCode(() -> new FiltroApiKey("")).doesNotThrowAnyException();
        assertThatCode(() -> new FiltroApiKey("", false)).doesNotThrowAnyException();
    }

    @Test
    void contextoConPropiedadObligatoriaYSinClaveNoArranca() {
        runner.withPropertyValues("cartera.api.claves-obligatorias=true", "cartera.api.claves=").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("CARTERA_API_CLAVES");
        });
    }

    @Test
    void contextoConPropiedadObligatoriaYClaveArranca() {
        runner.withPropertyValues("cartera.api.claves-obligatorias=true", "cartera.api.claves=" + CLAVE_VALIDA)
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void contextoPorDefectoSinClaveArranca() {
        runner.run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void elPerfilProdActivaLaObligatoriedadYElJson() throws Exception {
        var prod = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));
        var base = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        assertThat(prod.getProperty("cartera.api.claves-obligatorias")).isEqualTo("true");
        assertThat(prod.getProperty("logging.structured.format.console")).isEqualTo("ecs");
        assertThat(prod.getProperty("management.endpoints.web.exposure.include"))
                .contains("health", "info", "metrics");
        // El perfil por defecto no cambia: abierto y sin JSON.
        assertThat(base.getProperty("cartera.api.claves-obligatorias")).isEqualTo("false");
        assertThat(base.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(base.getProperty("logging.structured.format.console")).isNull();
    }
}
