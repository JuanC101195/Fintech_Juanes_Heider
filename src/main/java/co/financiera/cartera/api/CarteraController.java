package co.financiera.cartera.api;

import co.financiera.cartera.api.Dtos.CasoResponse;
import co.financiera.cartera.api.Dtos.CasoResumenDto;
import co.financiera.cartera.api.Dtos.CatalogoResponse;
import co.financiera.cartera.api.Dtos.ClasificacionResponse;
import co.financiera.cartera.api.Dtos.ClasificarRequest;
import co.financiera.cartera.api.Dtos.CreditoRequest;
import co.financiera.cartera.api.Dtos.EjecucionResponse;
import co.financiera.cartera.api.Dtos.EjecutarRequest;
import co.financiera.cartera.api.Dtos.ReglasResponse;
import co.financiera.cartera.api.Dtos.SimulacionResponse;
import co.financiera.cartera.api.PerfilesReglas.Perfil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API v1 del motor de cartera (docs/api.md; contrato OpenAPI en docs/openapi.yaml). */
@Tag(name = "Motor de cartera", description = "Simulación de créditos, escenarios de pago y clasificación de pagos")
@RestController
@RequestMapping("/api/v1")
public class CarteraController {

    static final String NDJSON = "application/x-ndjson";

    private final ServicioCartera servicio;
    private final ObjectMapper json;

    public CarteraController(ServicioCartera servicio, ObjectMapper json) {
        this.servicio = servicio;
        this.json = json;
    }

    @Operation(summary = "Catálogo del producto", description = "Líneas de crédito con su tasa y plazo, periodicidades, perfiles de reglas, tipos de instrucción de pago y las claves de reglas de negocio.")
    @GetMapping("/catalogo")
    public CatalogoResponse catalogo() {
        return servicio.catalogo();
    }

    @Operation(summary = "Reglas de negocio vigentes", description = "Los perfiles EXCEL y NEGOCIO con el valor de cada regla, su descripción y la pregunta o decisión de donde sale.")
    @GetMapping("/reglas")
    public ReglasResponse reglas() {
        return servicio.reglas();
    }

    @Operation(summary = "Simular un crédito", description = "Calcula la cuota, los servicios y el plan de pagos completo de un crédito nuevo, pagado al día. Con fechaDesembolso y primerVencimiento (juntas) la primera cuota se prorratea por los días reales.")
    @PostMapping("/simulaciones")
    public SimulacionResponse simular(@RequestBody CreditoRequest credito) {
        return servicio.simular(credito);
    }

    @Operation(summary = "Ejecutar un escenario de pagos", description = "Corre un crédito con instrucciones por periodo (pago tarde, parcial, por valor, sin pago, abono extra, prepago, cambio de tasa, usura, dación o retoma) y devuelve cada periodo con su causación, el reparto del pago por prelación, los saldos y los asientos contables.")
    @PostMapping("/escenarios/ejecutar")
    public EjecucionResponse ejecutar(@RequestBody EjecutarRequest request) {
        return servicio.ejecutar(request);
    }

    /**
     * Hasta {@value ServicioCartera#LOTE_MAXIMO} escenarios en una petición; responde NDJSON (una línea por ítem, en
     * orden): el mismo resultado de {@code /escenarios/ejecutar} o {@code {indice, codigo, mensaje, detalle}} si ese
     * ítem falla. Un lote vacío o de más de 500 responde 400 antes de emitir nada.
     */
    @Operation(summary = "Ejecutar un lote de escenarios", description = "Hasta 500 escenarios en una petición. Responde NDJSON (application/x-ndjson): una línea por escenario, en orden, con el mismo resultado de /escenarios/ejecutar o {indice, codigo, mensaje, detalle} si ese escenario falla.")
    @ApiResponse(responseCode = "200", description = "Una línea JSON por escenario", content = @Content(mediaType = NDJSON))
    @PostMapping("/escenarios/ejecutar-lote")
    public void ejecutarLote(@RequestBody List<EjecutarRequest> lote, HttpServletResponse respuesta) throws IOException {
        servicio.validarLote(lote);
        respuesta.setStatus(HttpServletResponse.SC_OK);
        respuesta.setContentType(NDJSON + ";charset=UTF-8");
        OutputStream salida = respuesta.getOutputStream();
        try {
            servicio.ejecutarLote(lote, linea -> {
                try {
                    salida.write(json.writeValueAsBytes(linea));
                    salida.write('\n');
                    salida.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause(); // el cliente cortó la conexión
        }
    }

    @Operation(summary = "Casos de negocio", description = "Los 13 casos de referencia (los 12 del Excel de casos y el abono extra con pendientes).")
    @GetMapping("/casos")
    public List<CasoResumenDto> casos() {
        return servicio.casos();
    }

    /**
     * Sin perfil corre con las reglas del Excel, que es de donde salen los casos 1 a 12. Con {@code fechaDesembolso}
     * y {@code primerVencimiento} (opcionales, juntas) el caso corre con fechas (P5-PRORRATEO).
     */
    @Operation(summary = "Ejecutar un caso de referencia", description = "Corre el caso con las reglas EXCEL (por defecto) o NEGOCIO; con fechaDesembolso y primerVencimiento, con la primera cuota prorrateada.")
    @GetMapping("/casos/{numero}")
    public CasoResponse caso(@Parameter(description = "Número del caso, de 1 a 13") @PathVariable int numero,
            @Parameter(description = "EXCEL (por defecto) o NEGOCIO") @RequestParam(required = false) String perfilReglas,
            @RequestParam(required = false) String fechaDesembolso,
            @RequestParam(required = false) String primerVencimiento) {
        return servicio.caso(numero, ServicioCartera.perfil(perfilReglas, Perfil.EXCEL), fechaDesembolso,
                primerVencimiento);
    }

    @Operation(summary = "Clasificar un pago", description = "Dice en qué caso de negocio cae un pago (al día, tarde, parcial, abono, prepago...) según lo exigible del crédito en ese periodo.")
    @PostMapping("/pagos/clasificar")
    public ClasificacionResponse clasificar(@RequestBody ClasificarRequest request) {
        return servicio.clasificar(request);
    }
}
