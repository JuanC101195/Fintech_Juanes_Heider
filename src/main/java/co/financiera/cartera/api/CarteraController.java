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
import java.util.List;
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

    private final ServicioCartera servicio;

    public CarteraController(ServicioCartera servicio) {
        this.servicio = servicio;
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

    @GetMapping("/casos")
    public List<CasoResumenDto> casos() {
        return servicio.casos();
    }

    /** Sin perfil corre con las reglas del Excel, que es de donde salen los casos 1 a 12. */
    @GetMapping("/casos/{numero}")
    public CasoResponse caso(@PathVariable int numero, @RequestParam(required = false) String perfilReglas) {
        return servicio.caso(numero, ServicioCartera.perfil(perfilReglas, Perfil.EXCEL));
    }

    @PostMapping("/pagos/clasificar")
    public ClasificacionResponse clasificar(@RequestBody ClasificarRequest request) {
        return servicio.clasificar(request);
    }
}
