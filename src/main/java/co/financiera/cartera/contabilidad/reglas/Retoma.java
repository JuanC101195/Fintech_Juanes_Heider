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
 * <p>R4-R2 (Diego, ronda 4): el costo real de la recuperación con soporte reduce el excedente que se
 * devuelve: crédito a CxP costos de recuperación por el costo y, si el excedente no lo alcanza a cubrir y no
 * se carga al fondo, débito al gasto por la diferencia.
 *
 * <p>Solo aplica a cierres por recuperación con excedente (CxP deudor &gt; 0). El cierre con faltante
 * (dación) lo contabiliza otra regla.
 */
public final class Retoma implements ReglaContable {

    /**
     * {@code null} = extinción del Excel (todos los servicios contra sus cuentas por cobrar). Con cuenta (R10, D72) la
     * parte de los servicios que cubre la moto es recaudo efectivo y va a esa cuenta ({@link ReglaContable#extincionConRecaudo}).
     */
    private final Cuenta terceros;

    /** Como el Excel (Caso4_Retoma AJ13:AM22). */
    public Retoma() {
        this(null);
    }

    public Retoma(Cuenta terceros) {
        this.terceros = terceros;
    }

    /**
     * Retoma con excedente, o sin excedente ni faltante porque el costo de la recuperación se comió el
     * excedente y lo asumió la financiera (R4-R2, sin cargo al fondo).
     */
    private static boolean aplica(Movimiento m) {
        return m.cxpDeudor().signum() > 0
                || (m.cxcFondoGarantias().signum() <= 0 && m.costoRecuperacion().signum() > 0);
    }

    @Override
    public Optional<Asiento> contabilizar(Movimiento m) {
        if (m.estado() != EstadoCredito.CERRADO_POR_EVENTO || !aplica(m)) {
            return Optional.empty();
        }
        List<Linea> lineas = new ArrayList<>();
        lineas.add(Linea.debito(Cuenta.INVENTARIO_MOTOS, m.inventario(), "Ingreso de la moto por el valor definido para el evento"));
        lineas.addAll(terceros == null ? ReglaContable.extincion(m.extinguido()) : ReglaContable.extincionConRecaudo(m, terceros));
        lineas.addAll(ReglaContable.costosRecuperacion(m));
        lineas.add(Linea.credito(Cuenta.CXP_DEUDOR, m.cxpDeudor(), "Excedente a favor del deudor"));
        return ReglaContable.asiento(m, lineas);
    }
}
