package co.financiera.cartera.api;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrato OpenAPI del API v1 para quienes lo consumen (p. ej. la sede de Cartagena): {@code /v3/api-docs} (JSON),
 * {@code /v3/api-docs.yaml} y la página {@code /swagger-ui.html}. Las rutas de la documentación no piden clave (solo
 * describen el contrato); los datos de {@code /api/v1/**} sí la piden cuando {@code cartera.api.claves} tiene valor.
 * La copia comprometida está en {@code docs/openapi.yaml} ({@code OpenApiTest}).
 */
@Configuration
class ConfiguracionOpenApi {

    static final String ESQUEMA_CLAVE = "claveApi";

    private static final String DESCRIPCION = """
            Motor de reglas de cartera de la financiera: simula créditos de moto (cuotas semanales, quincenales o \
            mensuales), ejecuta escenarios de pagos (pagos tarde, parciales, abonos, prepagos, cambios de tasa, dación y \
            retoma) y clasifica un pago en el caso de negocio que le corresponde.

            **Autenticación.** Cada petición a `/api/v1/**` lleva la cabecera `X-API-Key` con la clave que entrega la \
            financiera. Sin clave válida responde 401; con una clave de solo lectura, los POST responden 403.

            **Dinero y tasas.** Viajan como texto decimal sin redondear (por ejemplo `"5904400"` o `"0.8762"`), para no \
            perder precisión. Las fechas van en formato `yyyy-MM-dd`.

            **Perfiles de reglas.** `perfilReglas` = `NEGOCIO` (las reglas vigentes de la financiera, el valor por \
            defecto en simulaciones y escenarios) o `EXCEL` (reproduce el Excel de casos tal cual).

            **Errores.** Todos responden `{codigo, mensaje, detalle}` con un código estable: 400 `VALIDACION` \
            (dato inválido; `detalle.campo` dice cuál), `PLAZO_MAXIMO`, `ABONO_MAYOR_QUE_SALDO` y \
            `LOTE_DEMASIADO_GRANDE`; 401 `NO_AUTORIZADO`; 403 `PROHIBIDO`; 404 `CASO_INEXISTENTE`; 413 \
            `CUERPO_DEMASIADO_GRANDE`; 500 `ASIENTO_DESCUADRADO` o `ERROR_INTERNO`.

            **Disponibilidad.** El servicio corre en un plan gratuito: si estuvo inactivo, la primera petición puede \
            tardar unos 30 segundos.""";

    @Bean
    OpenAPI contratoOpenApi(@Value("${cartera.api.url-publica:https://motor-cartera-api-jc.azurewebsites.net}") String url,
            @Value("${cartera.api.version-contrato:1.0.0}") String version) {
        return new OpenAPI()
                .info(new Info().title("API del motor de cartera").version(version).description(DESCRIPCION))
                .servers(List.of(new Server().url(url).description("Azure App Service")))
                .components(new Components().addSecuritySchemes(ESQUEMA_CLAVE, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-Key")
                        .description("Clave que entrega la financiera (rol CONSUMIDOR: todo; LECTURA: solo GET de "
                                + "catálogo, reglas y casos)")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_CLAVE));
    }

    /**
     * Las respuestas de error ({@link Dtos.ErrorDto}) en cada operación. {@code ManejadorErrores} fija el estado en tiempo de
     * ejecución, así que springdoc no las descubre solo: 400, 401 y 500 en todas, 403 y 413 en los POST y 404 en los casos.
     */
    @Bean
    OpenApiCustomizer respuestasDeError() {
        return api -> {
            ModelConverters.getInstance().readAll(Dtos.ErrorDto.class).forEach(api.getComponents()::addSchemas);
            Content error = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorDto")));
            api.getPaths().forEach((ruta, item) -> item.readOperationsMap().forEach((metodo, op) -> {
                agregar(op, "400", "Dato inválido o regla del crédito (VALIDACION, PLAZO_MAXIMO, ABONO_MAYOR_QUE_SALDO, "
                        + "LOTE_DEMASIADO_GRANDE)", error);
                agregar(op, "401", "Falta una clave válida en X-API-Key (NO_AUTORIZADO)", error);
                agregar(op, "500", "Error interno (ASIENTO_DESCUADRADO, ERROR_INTERNO)", error);
                if ("POST".equals(metodo.name())) {
                    agregar(op, "403", "La clave es de solo lectura (PROHIBIDO)", error);
                    agregar(op, "413", "El cuerpo supera el tamaño máximo (CUERPO_DEMASIADO_GRANDE)", error);
                }
                if (ruta.contains("{numero}")) {
                    agregar(op, "404", "No existe el caso (CASO_INEXISTENTE)", error);
                }
            }));
        };
    }

    private static void agregar(Operation op, String codigo, String descripcion, Content error) {
        op.getResponses().putIfAbsent(codigo, new ApiResponse().description(descripcion).content(error));
    }
}
