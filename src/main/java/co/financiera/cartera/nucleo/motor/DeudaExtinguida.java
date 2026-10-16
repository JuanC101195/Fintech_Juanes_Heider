package co.financiera.cartera.nucleo.motor;

import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Servicios;
import java.math.BigDecimal;

/**
 * Lo que se extingue con la moto en una dación o retoma, por concepto (casos 3, 4 y 11, panel
 * "Prelación · aplicación del valor recibido"). La contabilidad acredita cada concepto por separado.
 */
public record DeudaExtinguida(BigDecimal cobranza, BigDecimal mora, BigDecimal interesCorriente, Servicios servicios,
        BigDecimal capital) {

    public static final DeudaExtinguida NINGUNA = new DeudaExtinguida(Calc.CERO, Calc.CERO, Calc.CERO, Servicios.NINGUNO,
            Calc.CERO);

    public BigDecimal total() {
        return Calc.sumar(cobranza, mora, interesCorriente, servicios.total(), capital);
    }
}
