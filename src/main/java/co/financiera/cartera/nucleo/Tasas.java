package co.financiera.cartera.nucleo;

import java.math.BigDecimal;

/**
 * Tasas equivalentes a la efectiva anual pactada (hoja Inputs, filas 16 a 19).
 * Conversión efectiva, no nominal: (1 + EA)^(1/n) − 1. Ojo: Fineract divide la nominal entre 52.
 *
 * <p>Base de días del año (D89, ADR 0009): con {@link #BASE_365} (el Excel) la periódica es (1 + EA)^(1/ppa) − 1 y la
 * diaria (1 + EA)^(1/365) − 1; con {@link #BASE_360} (NEGOCIO, «cambiaremos la base 365 a base 360 para el cálculo de
 * las cuotas») la periódica es (1 + EA)^(días/360) − 1 con los días nominales del periodo (7, 15 o 30) y la diaria
 * (1 + EA)^(1/360) − 1. Quincenal y mensual dan lo mismo en las dos bases (15/360 = 1/24 y 30/360 = 1/12 son el mismo
 * {@code double}); solo cambia la semanal, de ^(1/52) a ^(7/360). La mensual equivalente siempre es ^(1/12).
 */
public record Tasas(BigDecimal efectivaAnual, BigDecimal periodica, BigDecimal diaria, BigDecimal mensual) {

    /** Base 365 días (el Excel): año de 52 semanas, 24 quincenas o 12 meses y diaria con 365. */
    public static final int BASE_365 = 365;
    /** Base 360 días (D89): año comercial de meses de 30 días; la semana pesa 7/360 del año. */
    public static final int BASE_360 = 360;

    /** Tasas de una EA para la periodicidad del crédito con base 365: periódica (1 + EA)^(1/ppa) − 1, diaria con 365. */
    public static Tasas desdeEA(BigDecimal ea, Periodicidad periodicidad) {
        return desdeEA(ea, periodicidad, BASE_365);
    }

    /**
     * Tasas de una EA para la periodicidad del crédito con la base de días del año de las reglas (D89): 365 = como el
     * Excel; 360 = periódica (1 + EA)^(díasNominales/360) − 1 y diaria (1 + EA)^(1/360) − 1.
     */
    public static Tasas desdeEA(BigDecimal ea, Periodicidad periodicidad, int baseDiasAnio) {
        if (baseDiasAnio == BASE_365) {
            return desdeEA(ea, periodicidad.periodosPorAnio(), Periodicidad.MESES_POR_ANIO);
        }
        if (baseDiasAnio != BASE_360) {
            throw new IllegalArgumentException("La base de días del año debe ser 365 o 360: " + baseDiasAnio);
        }
        BigDecimal base = Calc.sumar(Calc.UNO, ea);
        return new Tasas(ea,
                Calc.restar(Calc.potencia(base, (double) periodicidad.diasNominales() / BASE_360), Calc.UNO),
                Calc.restar(Calc.potencia(base, 1.0 / BASE_360), Calc.UNO),
                Calc.restar(Calc.potencia(base, 1.0 / Periodicidad.MESES_POR_ANIO), Calc.UNO));
    }

    /**
     * Versión con enteros sueltos y base 365. Se conserva porque la usan pruebas de usura que trabajan otros agentes;
     * el código nuevo usa {@link #desdeEA(BigDecimal, Periodicidad, int)}.
     */
    public static Tasas desdeEA(BigDecimal ea, int periodosPorAnio, int mesesPorAnio) {
        BigDecimal base = Calc.sumar(Calc.UNO, ea);
        return new Tasas(ea,
                Calc.restar(Calc.potencia(base, 1.0 / periodosPorAnio), Calc.UNO),
                Calc.restar(Calc.potencia(base, 1.0 / BASE_365), Calc.UNO),
                Calc.restar(Calc.potencia(base, 1.0 / mesesPorAnio), Calc.UNO));
    }

    /** Tasas del producto con base 365 (el Excel). */
    public static Tasas de(ParametrosProducto p) {
        return desdeEA(p.tasaEA(), p.periodicidad());
    }

    /** Tasas del producto con la base de días del año de las reglas (D89). */
    public static Tasas de(ParametrosProducto p, int baseDiasAnio) {
        return desdeEA(p.tasaEA(), p.periodicidad(), baseDiasAnio);
    }
}
