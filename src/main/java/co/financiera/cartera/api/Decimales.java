package co.financiera.cartera.api;

import java.math.BigDecimal;

/**
 * Conversión entre BigDecimal y el string decimal del contrato. El motor no redondea (D4): se manda el
 * valor completo, sin ceros sobrantes a la derecha y sin notación científica.
 */
final class Decimales {

    private Decimales() {
    }

    /** "537782.1234…", "0", "5100000". Nulo se queda nulo. */
    static String texto(BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        if (valor.signum() == 0) {
            return "0";
        }
        return valor.stripTrailingZeros().toPlainString();
    }

    /** Lee un decimal del request; nulo o vacío devuelve {@code porDefecto}. */
    static BigDecimal leer(String valor, String campo, BigDecimal porDefecto) {
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        try {
            return new BigDecimal(valor.trim());
        } catch (NumberFormatException e) {
            throw ApiException.datoInvalido("El campo " + campo + " no es un número decimal: " + valor, campo);
        }
    }

    /** Lee un decimal obligatorio del request. */
    static BigDecimal obligatorio(String valor, String campo) {
        BigDecimal v = leer(valor, campo, null);
        if (v == null) {
            throw ApiException.datoInvalido("Falta el campo " + campo, campo);
        }
        return v;
    }
}
