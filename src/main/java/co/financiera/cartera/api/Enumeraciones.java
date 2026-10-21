package co.financiera.cartera.api;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/** Lee enums del request y responde 400 con los valores válidos cuando no existen. */
final class Enumeraciones {

    private Enumeraciones() {
    }

    static <E extends Enum<E>> E leer(Class<E> tipo, String valor, E porDefecto, String campo) {
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, ApiException.VALIDACION,
                    "Valor no válido para " + campo + ": " + valor,
                    Map.of("campo", campo, "valoresValidos", Arrays.stream(tipo.getEnumConstants()).map(Enum::name).toList()));
        }
    }
}
