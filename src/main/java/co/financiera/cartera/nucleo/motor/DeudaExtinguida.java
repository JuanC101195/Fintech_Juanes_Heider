package co.financiera.cartera.nucleo.motor;

import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Servicios;
import java.math.BigDecimal;

/**
 * Lo que se extingue con la moto en una dación o retoma, por concepto (casos 3, 4 y 11, panel
 * "Prelación · aplicación del valor recibido"). La contabilidad acredita cada concepto por separado.
 * También describe lo que un abono extra o un prepago pagó de pendientes (A4,
 * {@link Movimiento#pendientesPagadosExtra()}).
 */
public record DeudaExtinguida(BigDecimal cobranza, BigDecimal mora, BigDecimal interesCorriente, Servicios servicios,
        BigDecimal capital) {

    public static final DeudaExtinguida NINGUNA = new DeudaExtinguida(Calc.CERO, Calc.CERO, Calc.CERO, Servicios.NINGUNO,
            Calc.CERO);

    public DeudaExtinguida mas(DeudaExtinguida otra) {
        return new DeudaExtinguida(Calc.sumar(cobranza, otra.cobranza), Calc.sumar(mora, otra.mora),
                Calc.sumar(interesCorriente, otra.interesCorriente), servicios.mas(otra.servicios),
                Calc.sumar(capital, otra.capital));
    }

    public BigDecimal total() {
        return Calc.sumar(cobranza, mora, interesCorriente, servicios.total(), capital);
    }
}
