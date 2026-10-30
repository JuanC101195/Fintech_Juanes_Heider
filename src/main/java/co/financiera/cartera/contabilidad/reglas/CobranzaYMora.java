package co.financiera.cartera.contabilidad.reglas;

import static co.financiera.cartera.contabilidad.Asiento.Linea.credito;
import static co.financiera.cartera.contabilidad.Asiento.Linea.debito;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.ReglaContable;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.util.List;
import java.util.Optional;

/**
 * Caso 9 · Pago tardío con gastos de cobranza. Reproduce el panel "Movimiento contable clave del
 * evento" de la hoja Caso9_Cobranza (AE13:AH25):
 *
 * <ol>
 *   <li>Causación: CxC gastos de cobranza contra Ingresos por cobranza (H) y CxC interés mora
 *       contra Ingresos por mora (F).
 *   <li>Aplicación del recaudo (I) por prelación: CxC cobranza (J) → CxC mora (K) → CxC interés
 *       corriente (L) → servicios (Y) → Cartera de créditos (M).
 * </ol>
 *
 * <p><b>Frontera</b> (docs/reglas/caso-9.md, R-9.10): esta regla es dueña del asiento completo de
 * los movimientos con cobranza causada ({@link #aplica}). {@code RecaudoCuota} (caso 1) y
 * {@code PagoInferior} (caso 8) deben ignorar esos movimientos para no debitar la caja dos veces.
 * Abonos extraordinarios y prepagos no se tocan aquí ({@code AbonoExtraordinario}).
 */
public final class CobranzaYMora implements ReglaContable {

    /** Cuenta de ingreso de la cobranza causada: la del Excel o la no operacional de Diego (R4-COBRANZA-CONTABLE). */
    private final Cuenta ingresoCobranza;

    /** Como el Excel (Caso9_Cobranza AE15:AG16): crédito a "Ingresos por cobranza". */
    public CobranzaYMora() {
        this(Cuenta.INGRESOS_COBRANZA);
    }

    public CobranzaYMora(Cuenta ingresoCobranza) {
        this.ingresoCobranza = java.util.Objects.requireNonNull(ingresoCobranza, "ingresoCobranza");
    }

    /** Verdadero si el movimiento es un pago tardío con gastos de cobranza (columna H &gt; 0). */
    public static boolean aplica(Movimiento m) {
        return m.cobranzaCausada().signum() > 0;
    }

    @Override
    public Optional<Asiento> contabilizar(Movimiento m) {
        if (!aplica(m)) {
            return Optional.empty();
        }
        return ReglaContable.asiento(m, ReglaContable.con(List.of(
                // Causación (AE15:AG18)
                debito(Cuenta.CXC_GASTOS_COBRANZA, m.cobranzaCausada(), "Causación de cobranza por días de atraso"),
                credito(ingresoCobranza, m.cobranzaCausada(), "Contrapartida de la cobranza causada"),
                debito(Cuenta.CXC_INTERES_MORA, m.interesMora(), "Causación de mora"),
                credito(Cuenta.INGRESOS_MORA, m.interesMora(), "Contrapartida del interés de mora"),
                // Aplicación del recaudo (AE19:AG24)
                debito(Cuenta.CAJA, m.pagoRecibido(), "Recaudo de la cuota total (financiera + servicios)"),
                credito(Cuenta.CXC_GASTOS_COBRANZA, m.pagoCobranza(), "Primera prioridad"),
                credito(Cuenta.CXC_INTERES_MORA, m.pagoMora(), "Segunda prioridad"),
                credito(Cuenta.CXC_INTERES_CORRIENTE, m.pagoInteresCorriente(), "Tercera prioridad"),
                // El Excel la llama "Servicios complementarios (FCC, asistencia, seguro)"; el caso 1 usa
                // "CxP servicios a terceros" para el mismo recaudo por cuenta de terceros (R-9.11).
                credito(Cuenta.CXP_SERVICIOS_TERCEROS,
                        ReglaContable.serviciosSinTodoRiesgo(m.pagoServicios(), m.pagosServicios()), "Cuarta prioridad"),
                credito(Cuenta.CARTERA_VIGENTE, m.pagoCapital(), "Remanente aplicado a capital")),
                ReglaContable.seguroTodoRiesgo(m.pagoServicios(), m.pagosServicios())));
    }
}
