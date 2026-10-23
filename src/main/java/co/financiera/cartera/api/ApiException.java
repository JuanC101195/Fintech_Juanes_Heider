package co.financiera.cartera.api;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Error del API con su código de negocio y el estado HTTP con que se responde. */
public class ApiException extends RuntimeException {

    public static final String VALIDACION = "VALIDACION";
    public static final String PLAZO_MAXIMO = "PLAZO_MAXIMO";
    public static final String CASO_INEXISTENTE = "CASO_INEXISTENTE";

    private final HttpStatus estado;
    private final String codigo;
    private final transient Map<String, Object> detalle;

    public ApiException(HttpStatus estado, String codigo, String mensaje, Map<String, Object> detalle) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
        this.detalle = detalle == null ? Map.of() : detalle;
    }

    public static ApiException datoInvalido(String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, VALIDACION, mensaje, Map.of());
    }

    public static ApiException datoInvalido(String mensaje, String campo) {
        return new ApiException(HttpStatus.BAD_REQUEST, VALIDACION, mensaje, Map.of("campo", campo));
    }

    public HttpStatus estado() {
        return estado;
    }

    public String codigo() {
        return codigo;
    }

    public Map<String, Object> detalle() {
        return detalle;
    }
}
