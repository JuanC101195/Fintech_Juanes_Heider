package co.financiera.cartera.nucleo.motor;

import co.financiera.cartera.nucleo.Servicios;
import java.math.BigDecimal;

/**
 * Una fila del cronograma: lo que se causó, lo que se pagó (en orden de prelación) y cómo quedó
 * cada saldo al cierre del periodo. Los nombres siguen las columnas de las hojas Caso1 a Caso12.
 */
public record Movimiento(
        int periodo,
        int mes,
        String evento,
        BigDecimal saldoInicial,
        BigDecimal tasaPeriodica,
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
        BigDecimal cxcFondoGarantias,
        BigDecimal cxpDeudor,
        // Giro a terceros al cerrar cada mes: lo recaudado de cada servicio
        Servicios pagoTerceros,
        EstadoCredito estado,
        // Detalle por servicio (prelación 4 a 6: fondo de garantías → asistencia → seguro)
        Servicios pagosServicios,
        Servicios cxcServiciosPorConcepto,
        // Lo extinguido con la moto en una dación o retoma, por concepto
        DeudaExtinguida extinguido) {

    /** Pago total del cliente en la semana: pago recibido + abonos extraordinarios. */
    public BigDecimal pagoTotalCliente() {
        return pagoRecibido.add(abonoExtra).add(prepagoCapital);
    }
}
