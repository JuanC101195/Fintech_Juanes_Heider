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

/** API v1 del motor de cartera (docs/api.md). */
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

    @GetMapping("/catalogo")
    public CatalogoResponse catalogo() {
        return servicio.catalogo();
    }

    @GetMapping("/reglas")
    public ReglasResponse reglas() {
        return servicio.reglas();
    }

    @PostMapping("/simulaciones")
    public SimulacionResponse simular(@RequestBody CreditoRequest credito) {
        return servicio.simular(credito);
    }

    @PostMapping("/escenarios/ejecutar")
    public EjecucionResponse ejecutar(@RequestBody EjecutarRequest request) {
        return servicio.ejecutar(request);
    }

    /**
     * Hasta {@value ServicioCartera#LOTE_MAXIMO} escenarios en una petición; responde NDJSON (una línea por ítem, en
     * orden): el mismo resultado de {@code /escenarios/ejecutar} o {@code {indice, codigo, mensaje, detalle}} si ese
     * ítem falla. Un lote vacío o de más de 500 responde 400 antes de emitir nada.
     */
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

    @GetMapping("/casos")
    public List<CasoResumenDto> casos() {
        return servicio.casos();
    }

    /**
     * Sin perfil corre con las reglas del Excel, que es de donde salen los casos 1 a 12. Con {@code fechaDesembolso}
     * y {@code primerVencimiento} (opcionales, juntas) el caso corre con fechas (P5-PRORRATEO).
     */
    @GetMapping("/casos/{numero}")
    public CasoResponse caso(@PathVariable int numero, @RequestParam(required = false) String perfilReglas,
            @RequestParam(required = false) String fechaDesembolso,
            @RequestParam(required = false) String primerVencimiento) {
        return servicio.caso(numero, ServicioCartera.perfil(perfilReglas, Perfil.EXCEL), fechaDesembolso,
                primerVencimiento);
    }

    @PostMapping("/pagos/clasificar")
    public ClasificacionResponse clasificar(@RequestBody ClasificarRequest request) {
        return servicio.clasificar(request);
    }
}
