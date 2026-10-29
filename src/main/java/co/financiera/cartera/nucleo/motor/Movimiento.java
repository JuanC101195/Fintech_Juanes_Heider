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
        // Causaciones del periodo
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
        DeudaExtinguida extinguido,
        // Capital vencido al inicio del periodo: con capitalVencido da la reclasificación contable
        BigDecimal capitalVencidoInicial,
        // Lo que un abono extra o un prepago pagó de pendientes de semanas anteriores, por prelación
        // (A4, aplicacionAbono = PRIMERO_PENDIENTES); el capital es el capital vencido recuperado. No
        // está incluido en abonoExtra ni en prepagoCapital, que son solo capital vigente. Con
        // PRELACION_COMPLETA (R4-CASO13) incluye también el fondo y la asistencia del periodo corriente que
        // pagó el excedente; esos servicios quedan además en serviciosCausados.
        DeudaExtinguida pendientesPagadosExtra,
        // Interés corriente de los días entre la última cuota y el prepago (A6, interesPrepagoPorDias)
        BigDecimal interesPrepago,
        // Costo real de la recuperación con soporte que se descontó en la dación o la retoma (R4-R2)
        BigDecimal costoRecuperacion,
        // La parte de ese costo que asume la financiera (dación con costoRecuperacionAlFondo = false)
        BigDecimal costoRecuperacionAsumido,
        // PPER-9 (Diego 2026-10-22): el crédito llegó a ReglasNegocio.cuotasVencidasParaRecuperacion cuotas
        // atrasadas y sigue en mora: queda listo para dación o retoma. Solo es una marca: el motor no ejecuta
        // la recuperación (R6-RECUP-AUTO). Siempre false con ReglasNegocio.EXCEL.
        boolean listoParaRecuperacion,
        // R8-PAGO-MAYOR-EXIGIBLE (Diego 2026-10-28): lo que un PagoValor trajo por encima de lo exigible; no se aplica a
        // la deuda y queda como cuenta por pagar al cliente para devolverlo. Siempre 0 con ReglasNegocio.EXCEL (allí se
        // recorta sin registrarlo, H8). No está incluido en pagoRecibido.
        BigDecimal sobranteCliente) {

    /** Campo que {@link #toString()} solo escribe cuando no es cero (así las filas sin sobrante no cambian). */
    private static final String SOBRANTE = "sobranteCliente";

    /**
     * Pago total del cliente en el periodo: pago recibido + abonos extraordinarios (lo que fue a capital
     * vigente, a pendientes y al interés por días del prepago) + el sobrante que queda por devolverle (lo que entró a
     * caja).
     */
    public BigDecimal pagoTotalCliente() {
        return pagoRecibido.add(abonoExtra).add(prepagoCapital).add(pendientesPagadosExtra.total()).add(interesPrepago)
                .add(sobranteCliente);
    }

    /**
     * El formato de un record ({@code Movimiento[campo=valor, ...]}), pero sin {@link #sobranteCliente} cuando es cero:
     * el campo llegó con la ronda 8 y las filas que no tienen sobrante (todas las del Excel) deben escribirse igual que
     * antes, porque las huellas de caracterización ({@code ReplicaSemanalNoCambiaTest}) se calculan sobre este texto.
     */
    @Override
    public String toString() {
        StringBuilder t = new StringBuilder("Movimiento[");
        String separador = "";
        for (java.lang.reflect.RecordComponent c : Movimiento.class.getRecordComponents()) {
            if (SOBRANTE.equals(c.getName()) && sobranteCliente.signum() == 0) {
                continue;
            }
            Object valor;
            try {
                valor = c.getAccessor().invoke(this);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            t.append(separador).append(c.getName()).append('=').append(valor);
            separador = ", ";
        }
        return t.append(']').toString();
    }

    /** Lo que el cliente entregó por fuera de la cuota: abono extra o prepago, con lo que pagó de pendientes. */
    public BigDecimal pagoExtraordinario() {
        return abonoExtra.add(prepagoCapital).add(pendientesPagadosExtra.total()).add(interesPrepago);
    }
}
