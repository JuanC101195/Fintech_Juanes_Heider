package co.financiera.cartera.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Escribe un error con el formato del contrato ({@code {codigo, mensaje, detalle}}) desde un filtro. */
final class RespuestaFiltro {

    private RespuestaFiltro() {
    }

    /** Los mensajes son constantes del código (sin comillas ni datos del usuario). */
    static void error(HttpServletResponse respuesta, int estado, String codigo, String mensaje) throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType("application/json;charset=UTF-8");
        String cuerpo = "{\"codigo\":\"" + codigo + "\",\"mensaje\":\"" + mensaje + "\",\"detalle\":{}}";
        respuesta.getOutputStream().write(cuerpo.getBytes(StandardCharsets.UTF_8));
    }
}
