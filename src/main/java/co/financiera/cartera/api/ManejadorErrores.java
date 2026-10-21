package co.financiera.cartera.api;

import co.financiera.cartera.api.Dtos.ErrorDto;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.motor.AbonoMayorQueElSaldoException;
import java.util.LinkedHashMap;
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
                "Valor no válido para " + e.getName() + ": " + e.getValue(), Map.of("campo", e.getName())));
    }
}
