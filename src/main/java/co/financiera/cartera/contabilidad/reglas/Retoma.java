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
 * Caso 4 · Retoma con excedente: la moto vale más que la deuda y la diferencia queda como CxP a
 * favor del deudor. Panel "Movimiento contable clave del evento" de Caso4_Retoma (AJ13:AM22):
 *
 * <pre>
 * Débito  Inventario de motos            = valor de la moto
 * Crédito cada concepto extinguido       = capital, interés corriente, mora, fondo, asistencia, seguro
 * Crédito CxP deudor                     = excedente
 * </pre>
 *
 * <p>Solo aplica a cierres por recuperación con excedente (CxP deudor &gt; 0). El cierre con faltante
 * (dación) lo contabiliza otra regla.
 */
public final class Retoma implements ReglaContable {

    @Override
    public Optional<Asiento> contabilizar(Movimiento m) {
        if (m.estado() != EstadoCredito.CERRADO_POR_EVENTO || m.cxpDeudor().signum() <= 0) {
            return Optional.empty();
        }
        List<Linea> lineas = new ArrayList<>();
        lineas.add(Linea.debito(Cuenta.INVENTARIO_MOTOS, m.inventario(), "Ingreso de la moto por el valor definido para el evento"));
        lineas.addAll(ReglaContable.extincion(m.extinguido()));
        lineas.add(Linea.credito(Cuenta.CXP_DEUDOR, m.cxpDeudor(), "Excedente a favor del deudor"));
        return ReglaContable.asiento(m, lineas);
    }
}
