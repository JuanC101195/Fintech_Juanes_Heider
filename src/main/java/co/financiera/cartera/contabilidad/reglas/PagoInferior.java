package co.financiera.cartera.contabilidad.reglas;

import static co.financiera.cartera.contabilidad.Asiento.Linea.credito;
import static co.financiera.cartera.contabilidad.Asiento.Linea.debito;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.ReglaContable;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Caso 8 · Pago inferior: panel "Movimiento contable clave del evento" de la hoja
 * Caso8_PagoInferior (AB12:AE20). Ver docs/reglas/caso-8.md (R-8.10 y R-8.11).
 *
 * <p>Un solo asiento por la semana del pago inferior, con dos partes:
 * <ol>
 *   <li>Recaudo: débito Caja por el pago recibido; crédito, en orden de prelación, CxC gastos de
 *       cobranza, CxC interés mora, CxC interés corriente, servicios complementarios y cartera
 *       (capital).</li>
 *   <li>Reclasificación del capital contractual no pagado: débito Cartera capital vencida, crédito
 *       Cartera capital vigente.</li>
 * </ol>
 *
 * <p><b>Frontera con el recaudo normal ({@code RecaudoCuota}, caso 1):</b> esta regla es dueña de
 * todo el asiento de la semana del pago inferior, incluido el débito a Caja. La regla de recaudo
 * normal debe excluir los movimientos para los que {@link #aplica(Movimiento)} es verdadero; si no,
 * la Caja se contabiliza dos veces.
 */
public final class PagoInferior implements ReglaContable {

    /** Evento con que el motor marca la semana de un pago inferior (columna C de la hoja). */
    public static final String EVENTO = "Pago inferior";

    /** Verdadero si el movimiento es una semana de pago inferior y lo contabiliza esta regla. */
    public static boolean aplica(Movimiento m) {
        return EVENTO.equals(m.evento()) && m.pagoRecibido().signum() > 0;
    }

    @Override
    public Optional<Asiento> contabilizar(Movimiento m) {
        if (!aplica(m)) {
            return Optional.empty();
        }
        // Capital contractual de la semana que el pago no alcanzó a cubrir (AC18 = AD19 = N43 en el Excel,
        // donde el capital vencido anterior es 0; en general es el aumento del capital vencido).
        BigDecimal reclasificacion = sinResiduo(Calc.noNegativo(Calc.restar(m.capitalContractual(), m.pagoCapital())));
        return ReglaContable.asiento(m, List.of(
                debito(Cuenta.CAJA, sinResiduo(m.pagoRecibido()), "Recaudo efectivamente recibido (incluye servicios)"),
                credito(Cuenta.CXC_GASTOS_COBRANZA, sinResiduo(m.pagoCobranza()), "Aplicación del pago a gastos de cobranza"),
                credito(Cuenta.CXC_INTERES_MORA, sinResiduo(m.pagoMora()), "Aplicación del pago a interés de mora"),
                credito(Cuenta.CXC_INTERES_CORRIENTE, sinResiduo(m.pagoInteresCorriente()),
                        "Aplicación del pago a interés corriente"),
                credito(Cuenta.CXP_SERVICIOS_TERCEROS, sinResiduo(m.pagoServicios()),
                        "Aplicación del pago a servicios, después de intereses (Excel: Servicios complementarios)"),
                credito(Cuenta.CARTERA_VIGENTE, sinResiduo(m.pagoCapital()), "Aplicación del remanente a capital"),
                debito(Cuenta.CARTERA_VENCIDA, reclasificacion, "Reclasificación del capital contractual no pagado"),
                credito(Cuenta.CARTERA_VIGENTE, reclasificacion, "Contrapartida de la reclasificación")));
    }

    /**
     * Los residuos de cálculo DECIMAL128 (del orden de 1e-29, p. ej. la mora con capital vencido 0)
     * no generan línea contable: por debajo de {@link Calc#EPSILON_SALDO} el valor se toma como 0.
     */
    private static BigDecimal sinResiduo(BigDecimal valor) {
        return Calc.esCero(valor) ? Calc.CERO : valor;
    }
}
