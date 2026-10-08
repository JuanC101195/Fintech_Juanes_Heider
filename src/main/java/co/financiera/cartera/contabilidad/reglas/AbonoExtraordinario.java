package co.financiera.cartera.contabilidad.reglas;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Asiento.Linea;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.ReglaContable;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.util.List;
import java.util.Optional;

/**
 * Recaudo extraordinario aplicado 100 % a capital, después de la cuota ordinaria de la semana
 * (la cuota va por su propio asiento de recaudo).
 *
 * <ul>
 *   <li>Caso 5 · prepago total (Caso5_Prepago!T13:W14): débito Caja / crédito Cartera de créditos
 *       por el prepago de capital ({@link Movimiento#prepagoCapital()}).</li>
 *   <li>Casos 6 y 7 · abono extra menor plazo / menor cuota (Caso6_MenorPlazo!U11:X12,
 *       Caso7_MenorCuota!U14:X15): débito Caja / crédito Cartera de créditos por el abono
 *       ({@link Movimiento#abonoExtra()}).</li>
 * </ul>
 *
 * Los dos conceptos van en líneas separadas para conservar la explicación del Excel; si una semana
 * no tiene ninguno, la regla no genera asiento.
 */
public final class AbonoExtraordinario implements ReglaContable {

    @Override
    public Optional<Asiento> contabilizar(Movimiento m) {
        return ReglaContable.asiento(m, List.of(
                Linea.debito(Cuenta.CAJA, m.prepagoCapital(), "Recaudo extraordinario por prepago de capital"),
                Linea.credito(Cuenta.CARTERA_VIGENTE, m.prepagoCapital(),
                        "Extinción del saldo de capital después de la cuota ordinaria"),
                Linea.debito(Cuenta.CAJA, m.abonoExtra(), "Abono extraordinario recibido"),
                Linea.credito(Cuenta.CARTERA_VIGENTE, m.abonoExtra(), "Aplicación 100% a capital")));
    }
}
