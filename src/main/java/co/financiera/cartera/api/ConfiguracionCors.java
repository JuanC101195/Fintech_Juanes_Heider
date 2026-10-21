package co.financiera.cartera.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS del API: el front en desarrollo (Vite) y el publicado en GitHub Pages. */
@Configuration
public class ConfiguracionCors implements WebMvcConfigurer {

    private final String[] origenes;

    public ConfiguracionCors(
            @Value("${cartera.api.cors.origenes:http://localhost:5173,https://juanc101195.github.io}") String[] origenes) {
        this.origenes = origenes;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origenes).allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*").maxAge(3600);
    }
}
