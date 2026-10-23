package co.financiera.cartera.api;

import co.financiera.cartera.api.Dtos.ErrorDto;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.motor.AbonoMayorQueElSaldoException;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.ErrorResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Errores del API con el formato del contrato: {@code {codigo, mensaje, detalle}}. */
@RestControllerAdvice(assignableTypes = CarteraController.class)
public class ManejadorErrores {

    static final String ERROR_INTERNO = "ERROR_INTERNO";
    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);
    static final String ABONO_MAYOR_QUE_SALDO = "ABONO_MAYOR_QUE_SALDO";
    static final String ASIENTO_DESCUADRADO = "ASIENTO_DESCUADRADO";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorDto> api(ApiException e) {
        return ResponseEntity.status(e.estado()).body(new ErrorDto(e.codigo(), e.getMessage(), e.detalle()));
    }

    /** P-14: el abono no puede superar el saldo de capital (D12). */
    @ExceptionHandler(AbonoMayorQueElSaldoException.class)
    public ResponseEntity<ErrorDto> abonoMayorQueSaldo(AbonoMayorQueElSaldoException e) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("periodo", e.periodo());
        detalle.put("abono", Decimales.texto(e.abono()));
        detalle.put("saldo", Decimales.texto(e.saldo()));
        return ResponseEntity.badRequest().body(new ErrorDto(ABONO_MAYOR_QUE_SALDO, e.getMessage(), detalle));
    }

    /** Validaciones del núcleo (operación, parámetros, instrucciones). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDto> validacion(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorDto(ApiException.VALIDACION, e.getMessage(), Map.of()));
    }

    /** Error interno del motor o de la contabilidad (HT-06): no es culpa del request. */
    @ExceptionHandler(Contabilizador.AsientoDescuadradoException.class)
    public ResponseEntity<ErrorDto> asientoDescuadrado(Contabilizador.AsientoDescuadradoException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorDto(ASIENTO_DESCUADRADO, e.getMessage(), Map.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> jsonInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorDto(ApiException.VALIDACION,
                "El cuerpo de la petición no es un JSON válido para este endpoint", Map.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDto> tipoInvalido(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorDto(ApiException.VALIDACION,
                "Valor no válido para " + e.getName(), Map.of("campo", e.getName())));
    }

    /**
     * Cualquier otra excepción: 500 con un id de correlación que también queda en el log. No se vuelca el cuerpo de
     * la petición ni se devuelve el mensaje interno. Los {@link Error} (OutOfMemoryError, StackOverflowError) no son
     * {@code Exception} y no se atrapan. Los errores HTTP de Spring (405, 415, parámetro faltante) conservan su estado.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> interno(Exception e) throws Exception {
        // DispatcherServlet envuelve los Error en una ServletException: se deja pasar para que no se atrapen.
        for (Throwable causa = e; causa != null; causa = causa.getCause() == causa ? null : causa.getCause()) {
            if (causa instanceof Error) {
                throw e;
            }
        }
        if (e instanceof ErrorResponse er && !er.getStatusCode().is5xxServerError()) {
            return ResponseEntity.status(er.getStatusCode()).body(new ErrorDto(ApiException.VALIDACION,
                    "Petición no válida para este endpoint", Map.of()));
        }
        String id = UUID.randomUUID().toString();
        LOG.error("ERROR_INTERNO idCorrelacion={} tipo={}", id, e.getClass().getName(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorDto(ERROR_INTERNO,
                "Error interno del servidor. Cite el identificador al reportarlo.", Map.of("idCorrelacion", id)));
    }
}
