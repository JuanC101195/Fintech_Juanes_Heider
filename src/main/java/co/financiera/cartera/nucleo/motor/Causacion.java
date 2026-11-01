package co.financiera.cartera.nucleo.motor;

import co.financiera.cartera.nucleo.Servicios;
import java.math.BigDecimal;

/**
 * Lo causado en un periodo.
 *
 * @param cuotaPorComponentes si la cuota financiera del periodo es su capital contractual más su interés en lugar de la
 *                            cuota fija: la primera cuota prorrateada (P5-PRORRATEO, D48, D79) o la última cuota que
 *                            recoge el capital que la primera no cobró (R12-PRIMERA-CUOTA-DETALLE, D79)
 */
record Causacion(BigDecimal interes, BigDecimal capitalContractual, Servicios servicios, BigDecimal mora,
        BigDecimal cobranza, Servicios serviciosAlDia, boolean cuotaPorComponentes) {

    /**
     * Servicios que el periodo no causó porque no se pagó al día (fondo y asistencia, D17) y que causaría
     * si la cuota corriente se pagara en su periodo.
     */
    Servicios serviciosNoCausados() {
        return serviciosAlDia.menos(servicios).noNegativo();
    }

    /** La misma causación con otro interés corriente (R8-REDONDEO-PESO: el residuo del redondeo va al interés). */
    Causacion conInteres(BigDecimal nuevo) {
        return new Causacion(nuevo, capitalContractual, servicios, mora, cobranza, serviciosAlDia, cuotaPorComponentes);
    }
}
