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

    /** Tope de negocio del dinero en una petición: 10.000 millones. */
    static final BigDecimal TOPE_DINERO = new BigDecimal("10000000000");
    /** Tope de una tasa efectiva anual como fracción: 500 %. */
    static final BigDecimal TOPE_TASA = new BigDecimal("5");
    /** Tope de la fracción de un pago parcial. */
    static final BigDecimal TOPE_FRACCION = new BigDecimal("100");

    private static final int MAX_PRECISION = 30;
    private static final int MAX_ESCALA = 20;
    private static final int MAX_LARGO_TEXTO = 64;

    /**
     * Lee un decimal del request; nulo o vacío devuelve {@code porDefecto}. Rechaza (400) lo que no es un número
     * razonable antes de cualquier cálculo: más de 30 dígitos, escala fuera de ±20 (así "1e999999999" no llega a
     * {@code toPlainString}) o texto demasiado largo.
     */
    static BigDecimal leer(String valor, String campo, BigDecimal porDefecto) {
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        String limpio = valor.trim();
        if (limpio.length() > MAX_LARGO_TEXTO) {
            throw ApiException.datoInvalido("El campo " + campo + " es demasiado largo para ser un número", campo);
        }
        BigDecimal numero;
        try {
            numero = new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            throw ApiException.datoInvalido("El campo " + campo + " no es un número decimal", campo);
        }
        if (numero.precision() > MAX_PRECISION || Math.abs((long) numero.scale()) > MAX_ESCALA) {
            throw ApiException.datoInvalido("El campo " + campo + " tiene demasiados dígitos o una escala fuera de rango"
                    + " (máximo " + MAX_PRECISION + " dígitos y escala de " + MAX_ESCALA + ")", campo);
        }
        return numero;
    }

    /** Lee un decimal obligatorio del request. */
    static BigDecimal obligatorio(String valor, String campo) {
        BigDecimal v = leer(valor, campo, null);
        if (v == null) {
            throw ApiException.datoInvalido("Falta el campo " + campo, campo);
        }
        return v;
    }

    /** Dinero opcional: entre 0 (o más de 0 si {@code positivo}) y {@link #TOPE_DINERO}. */
    static BigDecimal dinero(String valor, String campo, BigDecimal porDefecto, boolean positivo) {
        return acotar(leer(valor, campo, porDefecto), campo, positivo, TOPE_DINERO);
    }

    /** Dinero obligatorio, con los mismos topes de {@link #dinero}. */
    static BigDecimal dineroObligatorio(String valor, String campo, boolean positivo) {
        return acotar(obligatorio(valor, campo), campo, positivo, TOPE_DINERO);
    }

    /** Tasa efectiva anual como fracción, en (0, 5]. */
    static BigDecimal tasa(String valor, String campo) {
        return acotar(obligatorio(valor, campo), campo, true, TOPE_TASA);
    }

    /** Fracción de un pago parcial, entre 0 y {@link #TOPE_FRACCION}. */
    static BigDecimal fraccion(String valor, String campo) {
        return acotar(obligatorio(valor, campo), campo, false, TOPE_FRACCION);
    }

    private static BigDecimal acotar(BigDecimal v, String campo, boolean positivo, BigDecimal tope) {
        if (v == null) {
            return null;
        }
        if (v.signum() < 0 || positivo && v.signum() == 0) {
            throw ApiException.datoInvalido("El campo " + campo + (positivo ? " debe ser mayor que cero"
                    : " no puede ser negativo"), campo);
        }
        if (v.compareTo(tope) > 0) {
            throw ApiException.datoInvalido("El campo " + campo + " supera el máximo permitido de "
                    + tope.toPlainString(), campo);
        }
        return v;
    }
}
