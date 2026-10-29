package co.financiera.cartera.nucleo.motor;

import co.financiera.cartera.nucleo.Servicios;
import java.math.BigDecimal;

/** Lo causado en un periodo. */
record Causacion(BigDecimal interes, BigDecimal capitalContractual, Servicios servicios, BigDecimal mora,
        BigDecimal cobranza, Servicios serviciosAlDia, boolean prorrateada) {

    /**
     * Servicios que el periodo no causó porque no se pagó al día (fondo y asistencia, D17) y que causaría
     * si la cuota corriente se pagara en su periodo.
     */
    Servicios serviciosNoCausados() {
        return serviciosAlDia.menos(servicios).noNegativo();
    }

    /** La misma causación con otro interés corriente (R8-REDONDEO-PESO: el residuo del redondeo va al interés). */
    Causacion conInteres(BigDecimal nuevo) {
        return new Causacion(nuevo, capitalContractual, servicios, mora, cobranza, serviciosAlDia, prorrateada);
    }
}
