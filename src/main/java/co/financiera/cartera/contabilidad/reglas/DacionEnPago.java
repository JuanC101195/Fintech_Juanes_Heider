package co.financiera.cartera.contabilidad.reglas;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Asiento.Linea;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.ReglaContable;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dación en pago (casos 3 y 11): el cliente entrega la moto por un valor menor que la deuda y el
 * faltante queda como cuenta por cobrar al fondo de garantías. Panel "Movimiento contable clave del
 * evento" de Caso3_Dacion (AJ9:AM18):
 *
 * <pre>
 *   Débito  Inventario de motos           = valor de la moto
 *   Débito  CxC FCC (fondo de garantías)  = deuda − valor de la moto
 *   Crédito cada concepto extinguido      = capital, interés corriente, mora, fondo, asistencia, seguro
 * </pre>
 *
 * <p>Aplica solo a cierres con faltante (CxC FCC &gt; 0). El cierre con excedente (CxP deudor) es la
 * retoma del caso 4 y tiene su propia regla.
 */
public final class DacionEnPago implements ReglaContable {

    @Override
    public Optional<Asiento> contabilizar(Movimiento m) {
        if (m.estado() != EstadoCredito.CERRADO_POR_EVENTO || m.cxcFondoGarantias().signum() <= 0) {
            return Optional.empty();
        }
        List<Linea> lineas = new ArrayList<>();
        lineas.add(Linea.debito(Cuenta.INVENTARIO_MOTOS, m.inventario(), "Ingreso de la moto por el valor definido para el evento"));
        lineas.add(Linea.debito(Cuenta.CXC_FONDO_GARANTIAS, m.cxcFondoGarantias(), "Residual de la deuda no cubierto por la moto"));
        lineas.addAll(ReglaContable.extincion(m.extinguido()));
        return ReglaContable.asiento(m, lineas);
    }
}
