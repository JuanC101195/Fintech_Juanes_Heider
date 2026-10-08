package co.financiera.cartera.contabilidad.reglas;

import static co.financiera.cartera.nucleo.Calc.esCero;
import static co.financiera.cartera.nucleo.Calc.restar;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Asiento.Linea;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.ReglaContable;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.util.List;
import java.util.Optional;

/**
 * Caso 1 · Recaudo de una cuota normal (R-1.23). Reproduce el panel "MOVIMIENTO CONTABLE CLAVE DEL
 * EVENTO" de la hoja Caso1_Normal (V12:Y17):
 *
 * <pre>
 *   Caja                        D  pago total de la cuota       (W13 = M4)
 *   Ingresos por intereses      C  interés corriente causado    (X14 = F4)
 *   Cartera de créditos         C  amortización de capital      (X15 = H4)
 *   CxP servicios a terceros    C  servicios de la cuota        (X16 = L4)
 * </pre>
 *
 * <p><b>Frontera con los casos 8 y 9 (para no contabilizar dos veces).</b> Esta regla solo aplica a
 * un <i>recaudo normal</i> ({@link #esRecaudoNormal}): pago a tiempo y completo de la cuota de la
 * semana, sin nada vencido antes ni después. En cuanto el movimiento tenga mora, cobranza, pago
 * parcial o pague cuentas por cobrar de semanas anteriores, el <b>recaudo completo</b> de esa semana
 * lo contabilizan las reglas de los casos 8 y 9 (que van por CxC interés corriente, como sus
 * paneles), y esta regla no genera nada. Esas reglas deben aplicar exactamente cuando
 * {@code !RecaudoCuota.esRecaudoNormal(m)} y {@code m.pagoRecibido() > 0}.
 *
 * <p>Abonos extraordinarios y prepagos (casos 5, 6 y 7) no van en {@code pagoRecibido} sino en
 * {@code abonoExtra} y {@code prepagoCapital}: esta regla contabiliza la cuota ordinaria de esa
 * semana y la regla de abonos solo la parte extraordinaria, igual que los paneles de esas hojas.
 */
public final class RecaudoCuota implements ReglaContable {

    @Override
    public Optional<Asiento> contabilizar(Movimiento m) {
        if (!esRecaudoNormal(m)) {
            return Optional.empty();
        }
        return ReglaContable.asiento(m, List.of(
                Linea.debito(Cuenta.CAJA, m.pagoRecibido(), "Recaudo de la cuota total"),
                Linea.credito(Cuenta.INGRESOS_INTERESES, m.pagoInteresCorriente(), "Interés corriente causado"),
                Linea.credito(Cuenta.CARTERA_VIGENTE, m.pagoCapital(), "Amortización de capital"),
                Linea.credito(Cuenta.CXP_SERVICIOS_TERCEROS, m.pagoServicios(),
                        "Servicios recaudados por cuenta de terceros")));
    }

    /**
     * Verdadero si el pago de la semana es un recaudo normal: hay pago, no hay mora ni cobranza
     * (causada o pagada), el interés y los servicios pagados son exactamente los de la semana (no
     * había CxC de semanas anteriores) y al cierre no queda nada pendiente. Sin mora causada no hay
     * capital vencido anterior, porque la mora = capital vencido × tasa semanal (D6).
     */
    public static boolean esRecaudoNormal(Movimiento m) {
        return m.pagoRecibido().signum() > 0
                && esCero(m.interesMora()) && esCero(m.cobranzaCausada())
                && esCero(m.pagoMora()) && esCero(m.pagoCobranza())
                && esCero(restar(m.pagoInteresCorriente(), m.interesCorriente()))
                && esCero(restar(m.pagoServicios(), m.serviciosCausados().total()))
                && esCero(m.cxcCobranza()) && esCero(m.cxcMora()) && esCero(m.cxcInteresCorriente())
                && esCero(m.cxcServicios()) && esCero(m.capitalVencido());
    }
}
