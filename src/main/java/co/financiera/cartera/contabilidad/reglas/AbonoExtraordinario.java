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
 *
 * <p>Fuera del Excel (Diego, 2026-10-20):
 * <ul>
 *   <li>A4 · con {@code aplicacionAbono = PRIMERO_PENDIENTES} el abono o el prepago paga antes lo
 *       pendiente de semanas anteriores ({@link Movimiento#pendientesPagadosExtra()}): débito Caja
 *       contra cada CxC por prelación (cobranza, mora, interés corriente), CxP servicios a terceros por
 *       los servicios recaudados (como el recaudo con vencidos) y Cartera capital vencida por el capital
 *       vencido recuperado. Con {@code PRELACION_COMPLETA} (R4-CASO13, ronda 4) el fondo y la asistencia del
 *       periodo corriente que paga el excedente van a la misma CxP servicios a terceros.</li>
 *   <li>A6 · el interés corriente de los días del prepago ({@link Movimiento#interesPrepago()}): débito
 *       Caja / crédito Ingresos por intereses.</li>
 * </ul>
 */
public final class AbonoExtraordinario implements ReglaContable {

    /**
     * Cuenta de los servicios recaudados por cuenta de terceros: "CxP servicios a terceros" del Excel o, con
     * {@code causacionServicios = SIEMPRE} (R10-CONTADOR-VENCIDAS, D72), "Ingresos recibidos para terceros" (PUC 2815,
     * provisional). La resuelve {@code Contabilizador.para}.
     */
    private final Cuenta terceros;

    /** Con la cuenta del Excel ("CxP servicios a terceros"). */
    public AbonoExtraordinario() {
        this(Cuenta.CXP_SERVICIOS_TERCEROS);
    }

    public AbonoExtraordinario(Cuenta terceros) {
        this.terceros = terceros;
    }

    @Override
    public Optional<Asiento> contabilizar(Movimiento m) {
        co.financiera.cartera.nucleo.Servicios pendientes = m.pendientesPagadosExtra().servicios();
        return ReglaContable.asiento(m, ReglaContable.con(List.of(
                Linea.debito(Cuenta.CAJA, m.prepagoCapital(), "Recaudo extraordinario por prepago de capital"),
                Linea.credito(Cuenta.CARTERA_VIGENTE, m.prepagoCapital(),
                        "Extinción del saldo de capital después de la cuota ordinaria"),
                Linea.debito(Cuenta.CAJA, m.abonoExtra(), "Abono extraordinario recibido"),
                Linea.credito(Cuenta.CARTERA_VIGENTE, m.abonoExtra(), "Aplicación 100% a capital"),
                Linea.debito(Cuenta.CAJA, m.pendientesPagadosExtra().total(), "Recaudo extraordinario aplicado a pendientes"),
                Linea.credito(Cuenta.CXC_GASTOS_COBRANZA, m.pendientesPagadosExtra().cobranza(), "Pendiente de cobranza (prelación 1)"),
                Linea.credito(Cuenta.CXC_INTERES_MORA, m.pendientesPagadosExtra().mora(), "Pendiente de mora (prelación 2)"),
                Linea.credito(Cuenta.CXC_INTERES_CORRIENTE, m.pendientesPagadosExtra().interesCorriente(),
                        "Pendiente de interés corriente (prelación 3)"),
                Linea.credito(terceros,
                        ReglaContable.serviciosSinTodoRiesgo(pendientes.total(), pendientes),
                        "Servicios pendientes: fondo, asistencia y seguro (prelación 4 a 6)"),
                Linea.credito(Cuenta.CARTERA_VENCIDA, m.pendientesPagadosExtra().capital(), "Capital vencido recuperado (prelación 7)"),
                Linea.debito(Cuenta.CAJA, m.interesPrepago(), "Interés corriente de los días hasta el prepago"),
                Linea.credito(Cuenta.INGRESOS_INTERESES, m.interesPrepago(), "Interés corriente causado por días")),
                ReglaContable.seguroTodoRiesgo(pendientes.total(), pendientes)));
    }
}
