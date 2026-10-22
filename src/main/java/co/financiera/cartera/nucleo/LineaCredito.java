package co.financiera.cartera.nucleo;

import static co.financiera.cartera.nucleo.Calc.bd;

import java.math.BigDecimal;

/**
 * Catálogo de líneas de crédito (hoja Inputs, sección ⑤). Las tasas son las máximas legales que
 * publica la Superintendencia Financiera cada mes (P-01): se actualizan con aprobación de un
 * autorizador, y cada crédito conserva la tasa con que se originó (P-11).
 *
 * <p>El plazo máximo es un parámetro de cada línea, no una constante del motor (Diego, R4-PLAZO-LINEA,
 * ronda 4, 2026-10-21): en la primera versión es de 24 meses para todas, incluida ORDINARIO (que el
 * catálogo de Inputs traía en 36), y a futuro puede crecer cambiando el valor de la línea o pasando otro
 * tope a {@link ParametrosProducto#de(LineaCredito, Periodicidad, int, int)}.
 *
 * @param tasaEA          tasa efectiva anual de la línea
 * @param plazoMaximoMeses plazo máximo que permite la línea
 */
public enum LineaCredito {
    CPPU(bd("0.8762"), LineaCredito.PLAZO_MAXIMO_MESES_PRIMERA_VERSION),
    CPPR(bd("0.6545"), LineaCredito.PLAZO_MAXIMO_MESES_PRIMERA_VERSION),
    BAJO_MONTO(bd("0.6666"), LineaCredito.PLAZO_MAXIMO_MESES_PRIMERA_VERSION),
    // Inputs ⑤ traía 36 meses; R4-PLAZO-LINEA la deja en 24 por ahora.
    ORDINARIO(bd("0.2859"), LineaCredito.PLAZO_MAXIMO_MESES_PRIMERA_VERSION);

    /** Tope de plazo de todas las líneas en la primera versión (R4-PLAZO-LINEA; antes PPER-2). */
    public static final int PLAZO_MAXIMO_MESES_PRIMERA_VERSION = 24;

    private final BigDecimal tasaEA;
    private final int plazoMaximoMeses;

    LineaCredito(BigDecimal tasaEA, int plazoMaximoMeses) {
        this.tasaEA = tasaEA;
        this.plazoMaximoMeses = plazoMaximoMeses;
    }

    public BigDecimal tasaEA() {
        return tasaEA;
    }

    public int plazoMaximoMeses() {
        return plazoMaximoMeses;
    }
}
