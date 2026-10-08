package co.financiera.cartera.nucleo.motor;

import co.financiera.cartera.nucleo.Servicios;
import java.math.BigDecimal;

/**
 * Una fila del cronograma: lo que se causó, lo que se pagó (en orden de prelación) y cómo quedó
 * cada saldo al cierre de la semana. Los nombres siguen las columnas de las hojas Caso1 a Caso9.
 */
public record Movimiento(
        int periodo,
        int mes,
        String evento,
        BigDecimal saldoInicial,
        BigDecimal tasaSemanal,
        BigDecimal cuotaFinanciera,
        // Causaciones de la semana
        BigDecimal interesCorriente,
        BigDecimal interesMora,
        BigDecimal capitalContractual,
        BigDecimal cobranzaCausada,
        Servicios serviciosCausados,
        // Pago y su aplicación por prelación: cobranza, mora, interés corriente, servicios, capital
        BigDecimal pagoRecibido,
        BigDecimal pagoCobranza,
        BigDecimal pagoMora,
        BigDecimal pagoInteresCorriente,
        BigDecimal pagoServicios,
        BigDecimal pagoCapital,
        BigDecimal abonoExtra,
        BigDecimal prepagoCapital,
        // Saldos al cierre
        BigDecimal cxcCobranza,
        BigDecimal cxcMora,
        BigDecimal cxcInteresCorriente,
        BigDecimal cxcServicios,
        BigDecimal capitalVencido,
        BigDecimal capitalVigente,
        BigDecimal saldoFinal,
        int cuotasVencidas,
        // Cierre por recuperación de la moto (casos 3 y 4)
        BigDecimal deudaAExtinguir,
        BigDecimal inventario,
        BigDecimal cxcFcc,
        BigDecimal cxpDeudor,
        // Giro a terceros al cerrar cada mes
        Servicios pagoTerceros,
        EstadoCredito estado) {

    /** Pago total del cliente en la semana: pago recibido + abonos extraordinarios. */
    public BigDecimal pagoTotalCliente() {
        return pagoRecibido.add(abonoExtra).add(prepagoCapital);
    }
}
