package co.financiera.cartera.nucleo;

import static co.financiera.cartera.nucleo.Calc.bd;

import java.math.BigDecimal;

/**
 * Catálogo de líneas de crédito (hoja Inputs, sección ⑤). Las tasas son las máximas legales que
 * publica la Superintendencia Financiera cada mes (P-01): se actualizan con aprobación de un
 * autorizador, y cada crédito conserva la tasa con que se originó (P-11).
 *
 * @param tasaEA          tasa efectiva anual de la línea
 * @param plazoMaximoMeses plazo máximo que permite la línea
 */
public enum LineaCredito {
    CPPU(bd("0.8762"), 24),
    CPPR(bd("0.6545"), 24),
    BAJO_MONTO(bd("0.6666"), 24),
    ORDINARIO(bd("0.2859"), 36);

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
