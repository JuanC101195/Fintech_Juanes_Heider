package co.financiera.cartera.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra los filtros del API con {@code new}, no como beans {@code @Component}.
 *
 * <p>Spring Modulith (observabilidad) envuelve en un proxy CGLIB los beans de los módulos. Un filtro que sea bean
 * queda proxificado sin pasar por su constructor, y {@code GenericFilterBean.init} (final) corre sobre el proxy con
 * los campos vacíos: Tomcat no arranca ("this.logger is null"). Las pruebas con {@code @WebMvcTest} no lo ven;
 * {@code ArranqueRealTest} sí, porque levanta el servidor de verdad.
 */
@Configuration
public class ConfiguracionFiltros {

    @Bean
    FilterRegistrationBean<FiltroCabecerasSeguridad> filtroCabecerasSeguridad() {
        return registrar(new FiltroCabecerasSeguridad(), Ordered.HIGHEST_PRECEDENCE);
    }

    @Bean
    FilterRegistrationBean<FiltroApiKey> filtroApiKey(@Value("${cartera.api.claves:}") String configuracion) {
        return registrar(new FiltroApiKey(configuracion), Ordered.HIGHEST_PRECEDENCE + 10);
    }

    @Bean
    FilterRegistrationBean<FiltroTamanoCuerpo> filtroTamanoCuerpo(
            @Value("${cartera.api.cuerpo-maximo-bytes:262144}") int maximo) {
        return registrar(new FiltroTamanoCuerpo(maximo), Ordered.HIGHEST_PRECEDENCE + 20);
    }

    private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> registrar(T filtro, int orden) {
        FilterRegistrationBean<T> registro = new FilterRegistrationBean<>(filtro);
        registro.setOrder(orden);
        return registro;
    }
}
