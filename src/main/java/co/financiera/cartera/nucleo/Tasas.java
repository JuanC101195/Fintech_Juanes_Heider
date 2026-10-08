package co.financiera.cartera.nucleo;

import java.math.BigDecimal;

/**
 * Tasas equivalentes a la efectiva anual pactada (hoja Inputs, filas 16 a 19).
 * Conversión efectiva, no nominal: (1 + EA)^(1/n) − 1. Ojo: Fineract divide la nominal entre 52.
 */
public record Tasas(BigDecimal efectivaAnual, BigDecimal semanal, BigDecimal diaria, BigDecimal mensual) {

    public static Tasas desdeEA(BigDecimal ea, int semanasPorAnio, int mesesPorAnio) {
        BigDecimal base = Calc.sumar(Calc.UNO, ea);
        return new Tasas(ea,
                Calc.restar(Calc.potencia(base, 1.0 / semanasPorAnio), Calc.UNO),
                Calc.restar(Calc.potencia(base, 1.0 / 365), Calc.UNO),
                Calc.restar(Calc.potencia(base, 1.0 / mesesPorAnio), Calc.UNO));
    }

    public static Tasas de(ParametrosProducto p) {
        return desdeEA(p.tasaEA(), p.semanasPorAnio(), p.mesesPorAnio());
    }
}
