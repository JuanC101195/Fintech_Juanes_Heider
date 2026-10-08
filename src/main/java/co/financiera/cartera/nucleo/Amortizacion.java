package co.financiera.cartera.nucleo;

import java.math.BigDecimal;

/** Amortización francesa (cuota fija). */
public final class Amortizacion {

    private Amortizacion() {
    }

    /** PMT de Excel: valor × i / (1 − (1 + i)^−n). Con tasa 0 reparte el valor en partes iguales. */
    public static BigDecimal cuotaFija(BigDecimal tasaPeriodica, int periodos, BigDecimal valor) {
        if (periodos <= 0) {
            throw new IllegalArgumentException("El número de periodos debe ser positivo");
        }
        if (tasaPeriodica.signum() == 0) {
            return Calc.dividir(valor, Calc.bd(periodos));
        }
        BigDecimal factor = Calc.sumar(Calc.UNO, tasaPeriodica).pow(periodos, Calc.MC);
        BigDecimal descuento = Calc.restar(Calc.UNO, Calc.dividir(Calc.UNO, factor));
        return Calc.dividir(Calc.multiplicar(valor, tasaPeriodica), descuento);
    }
}
