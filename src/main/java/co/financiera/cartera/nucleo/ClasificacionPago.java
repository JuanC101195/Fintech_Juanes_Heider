package co.financiera.cartera.nucleo;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * ABN-4 (Diego, ronda 3, 2026-10-20): pagar antes del martes el valor <b>exacto</b> de una cuota ya
 * causada es pago corriente, no anticipo; lo que supere ese valor es abono extra. Función pura: no sabe
 * de fechas. Quien la llame (el módulo futuro {@code abonos}) decide cuál es el periodo cuya cuota ya está
 * causada en la fecha del pago y le pasa lo exigible de ese periodo. Ver docs/reglas/caso-13.md.
 *
 * @param pagoCorriente lo que se aplica como pago de la cuota (por prelación, incluidos los pendientes)
 * @param abonoExtra    lo que supera lo exigible: va como abono extraordinario (menor plazo o menor cuota)
 */
public record ClasificacionPago(BigDecimal pagoCorriente, BigDecimal abonoExtra) {

    public ClasificacionPago {
        Objects.requireNonNull(pagoCorriente, "pagoCorriente");
        Objects.requireNonNull(abonoExtra, "abonoExtra");
    }

    /**
     * Divide un valor recibido en pago corriente y abono extra.
     *
     * @param recibido         lo que entregó el cliente
     * @param exigibleCausado  lo exigible de la cuota ya causada: cuota financiera + servicios del periodo +
     *                         lo pendiente de periodos anteriores. Cero si no hay cuota causada: entonces todo
     *                         el valor supera lo exigible y es abono extra.
     */
    public static ClasificacionPago clasificar(BigDecimal recibido, BigDecimal exigibleCausado) {
        Objects.requireNonNull(recibido, "recibido");
        Objects.requireNonNull(exigibleCausado, "exigibleCausado");
        if (recibido.signum() < 0 || exigibleCausado.signum() < 0) {
            throw new IllegalArgumentException("El valor recibido y lo exigible no pueden ser negativos");
        }
        BigDecimal corriente = Calc.min(recibido, exigibleCausado);
        BigDecimal extra = Calc.restar(recibido, corriente);
        // Diferencias de redondeo por debajo de medio centavo no son un abono extra.
        if (Calc.esCero(extra)) {
            return new ClasificacionPago(recibido, Calc.CERO);
        }
        return new ClasificacionPago(corriente, extra);
    }

    public boolean tieneAbonoExtra() {
        return abonoExtra.signum() > 0;
    }
}
