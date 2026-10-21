package co.financiera.cartera.nucleo;

import java.math.BigDecimal;

/**
 * Tasas equivalentes a la efectiva anual pactada (hoja Inputs, filas 16 a 19).
 * Conversión efectiva, no nominal: (1 + EA)^(1/n) − 1. Ojo: Fineract divide la nominal entre 52.
 */
public record Tasas(BigDecimal efectivaAnual, BigDecimal periodica, BigDecimal diaria, BigDecimal mensual) {

    /** Tasas de una EA para la periodicidad del crédito: periódica (1 + EA)^(1/ppa) − 1, diaria con 365, mensual con 12. */
    public static Tasas desdeEA(BigDecimal ea, Periodicidad periodicidad) {
        return desdeEA(ea, periodicidad.periodosPorAnio(), Periodicidad.MESES_POR_ANIO);
    }

    /**
     * Versión con enteros sueltos. Se conserva porque la usan el cambio de tasa del motor y pruebas de
     * usura que trabajan otros agentes; el código nuevo usa {@link #desdeEA(BigDecimal, Periodicidad)}.
     */
    public static Tasas desdeEA(BigDecimal ea, int periodosPorAnio, int mesesPorAnio) {
        BigDecimal base = Calc.sumar(Calc.UNO, ea);
        return new Tasas(ea,
                Calc.restar(Calc.potencia(base, 1.0 / periodosPorAnio), Calc.UNO),
                Calc.restar(Calc.potencia(base, 1.0 / 365), Calc.UNO),
                Calc.restar(Calc.potencia(base, 1.0 / mesesPorAnio), Calc.UNO));
    }

    public static Tasas de(ParametrosProducto p) {
        return desdeEA(p.tasaEA(), p.periodicidad());
    }
}
