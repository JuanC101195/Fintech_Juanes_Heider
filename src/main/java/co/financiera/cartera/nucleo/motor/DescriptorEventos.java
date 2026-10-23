package co.financiera.cartera.nucleo.motor;

import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import java.math.BigDecimal;

/**
 * Texto del evento de cada fila del cronograma (columna "Evento" de las hojas del Excel). Puro: no cambia nada
 * del crédito; lo usa {@code MotorCartera} al armar el {@link Movimiento} del periodo.
 */
final class DescriptorEventos {

    private DescriptorEventos() {
    }

    /** El evento de mayor jerarquía del periodo: cierre, prepago, abono, cambio de tasa o el pago. */
    static String evento(Ordenes o, int periodo, int cuotasVigentes, boolean pagoCompleto, int dias, BigDecimal recibido) {
        if (o.cierre != null) {
            return "Evento de cierre";
        }
        if (o.prepago != null) {
            return "Cuota + prepago total";
        }
        if (o.abono != null) {
            return "Cuota + abono extra";
        }
        return o.cambioTasa ? "Cambio de tasa" : describir(periodo, cuotasVigentes, o.pago, pagoCompleto, dias, recibido);
    }

    private static String describir(int periodo, int cuotasVigentes, Instruccion pago, boolean completo, int dias,
            BigDecimal recibido) {
        if (pago instanceof SinPago) {
            return "Mora - sin pago";
        }
        if (pago instanceof PagoAlDia) {
            return "Normalización - paga mes + atrasado";
        }
        if (periodo > cuotasVigentes) {
            return "Periodo adicional";
        }
        if (dias > 0) {
            return "Pago tardío + cobranza";
        }
        // La última cuota contractual puede ser menor porque liquida el saldo: eso no es un pago inferior.
        if (!completo && recibido.signum() > 0 && !(pago instanceof PagoContractual)) {
            return "Pago inferior";
        }
        return periodo == cuotasVigentes ? "Pago final" : "Pago normal";
    }
}
