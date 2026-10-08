package co.financiera.cartera.contabilidad.reglas;

import static co.financiera.cartera.nucleo.Calc.restar;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Asiento.Linea;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.ReglaContable;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Caso 4 · Retoma con excedente: la moto vale más que la deuda y la diferencia queda como CxP a
 * favor del deudor. Reproduce el panel "Movimiento contable clave del evento" de la hoja
 * Caso4_Retoma (Z12:AC18):
 *
 * <pre>
 * Débito  Inventario de motos        = valor de la moto (M)
 * Crédito Cartera de créditos        = capital extinguido (D del periodo de retoma)
 * Crédito CxC intereses corrientes   = deuda a extinguir − capital (L − D)
 * Crédito CxP deudor                 = excedente (O)
 * </pre>
 *
 * <p>Solo aplica a cierres por recuperación con excedente (CxP deudor &gt; 0). El cierre con
 * faltante (dación, caso 3) lo contabiliza otra regla. Como en el Excel, todo lo que se extingue
 * aparte del capital se lleva a CxC intereses corrientes (en el caso 4 no hay mora, servicios ni
 * cobranza pendientes: D8 / P7).
 */
public final class Retoma implements ReglaContable {

    @Override
    public Optional<Asiento> contabilizar(Movimiento m) {
        if (m.estado() != EstadoCredito.CERRADO_POR_EVENTO || m.cxpDeudor().signum() <= 0) {
            return Optional.empty();
        }
        // Capital vivo en el momento del cierre (en el Excel no hay pago esa semana: es D17).
        BigDecimal capital = restar(restar(restar(m.saldoInicial(), m.pagoCapital()), m.abonoExtra()), m.prepagoCapital());
        BigDecimal otros = restar(m.deudaAExtinguir(), capital);
        return ReglaContable.asiento(m, List.of(
                Linea.debito(Cuenta.INVENTARIO_MOTOS, m.inventario(), "Ingreso de la moto por el valor definido para el evento"),
                Linea.credito(Cuenta.CARTERA_VIGENTE, capital, "Extinción del capital"),
                Linea.credito(Cuenta.CXC_INTERES_CORRIENTE, otros, "Extinción de intereses corrientes pendientes"),
                Linea.credito(Cuenta.CXP_DEUDOR, m.cxpDeudor(), "Excedente a favor del deudor")));
    }
}
