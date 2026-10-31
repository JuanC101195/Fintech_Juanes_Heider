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
 * Casos 8 y 12 · Recaudo con vencidos: las semanas que siguen a un pago inferior, en que el cliente
 * paga y el pago recupera mora, interés o capital vencido (incluida la normalización del caso 12).
 * El Excel no trae panel para estas semanas; el asiento sigue el esquema del pago tardío (caso 9) y
 * del pago inferior (caso 8):
 *
 * <ol>
 *   <li>Causación de la mora: CxC interés mora contra Ingresos por mora.
 *   <li>Aplicación del recaudo por prelación: CxC gastos de cobranza pendientes → CxC interés mora → CxC interés corriente → servicios →
 *       Cartera de créditos.
 *   <li>Reclasificación del cambio de capital vencido de la semana: si crece, de cartera vigente a
 *       vencida; si se recupera, de vencida a vigente.
 * </ol>
 *
 * <p><b>Frontera:</b> aplica a toda semana con pago que no sea recaudo normal ({@link RecaudoCuota}),
 * pago inferior ({@link PagoInferior}) ni pago tardío con cobranza ({@link CobranzaYMora}), para que la
 * caja de cada semana se debite una sola vez.
 */
public final class RecaudoConVencidos implements ReglaContable {

    /**
     * Cuenta de los servicios recaudados por cuenta de terceros: "CxP servicios a terceros" del Excel o, con
     * {@code causacionServicios = SIEMPRE} (R10-CONTADOR-VENCIDAS, D72), "Ingresos recibidos para terceros" (PUC 2815,
     * provisional). La resuelve {@code Contabilizador.para}.
     */
    private final Cuenta terceros;

    /** Con la cuenta del Excel ("CxP servicios a terceros"). */
    public RecaudoConVencidos() {
        this(Cuenta.CXP_SERVICIOS_TERCEROS);
    }

    public RecaudoConVencidos(Cuenta terceros) {
        this.terceros = terceros;
    }

    /** Verdadero si la semana tiene pago y ninguna otra regla de recaudo la contabiliza. */
    public static boolean aplica(Movimiento m) {
        return m.pagoRecibido().signum() > 0 && !RecaudoCuota.esRecaudoNormal(m) && !PagoInferior.aplica(m)
                && !CobranzaYMora.aplica(m);
    }

    @Override
    public Optional<Asiento> contabilizar(Movimiento m) {
        if (!aplica(m)) {
            return Optional.empty();
        }
        BigDecimal cambioVencido = Calc.restar(m.capitalVencido(), m.capitalVencidoInicial());
        BigDecimal aVencida = sinResiduo(Calc.noNegativo(cambioVencido));
        BigDecimal aVigente = sinResiduo(Calc.noNegativo(cambioVencido.negate()));
        BigDecimal servicios = sinResiduo(m.pagoServicios());
        return ReglaContable.asiento(m, ReglaContable.con(List.of(
                debito(Cuenta.CXC_INTERES_MORA, sinResiduo(m.interesMora()), "Causación de mora"),
                credito(Cuenta.INGRESOS_MORA, sinResiduo(m.interesMora()), "Contrapartida del interés de mora"),
                debito(Cuenta.CAJA, m.pagoRecibido(), "Recaudo de la cuota más lo atrasado"),
                // Cobranza causada en una semana anterior (pago tardío) que esta cuota paga primero.
                credito(Cuenta.CXC_GASTOS_COBRANZA, sinResiduo(m.pagoCobranza()), "Primera prioridad"),
                credito(Cuenta.CXC_INTERES_MORA, sinResiduo(m.pagoMora()), "Segunda prioridad"),
                credito(Cuenta.CXC_INTERES_CORRIENTE, sinResiduo(m.pagoInteresCorriente()), "Tercera prioridad"),
                credito(terceros, ReglaContable.serviciosSinTodoRiesgo(servicios, m.pagosServicios()),
                        "Fondo, asistencia y seguro"),
                credito(Cuenta.CARTERA_VIGENTE, sinResiduo(m.pagoCapital()), "Remanente aplicado a capital"),
                debito(Cuenta.CARTERA_VENCIDA, aVencida, "Capital contractual de la semana que quedó vencido"),
                credito(Cuenta.CARTERA_VIGENTE, aVencida, "Contrapartida de la reclasificación a vencida"),
                debito(Cuenta.CARTERA_VIGENTE, aVigente, "Capital vencido recuperado"),
                credito(Cuenta.CARTERA_VENCIDA, aVigente, "Contrapartida de la recuperación del vencido")),
                ReglaContable.seguroTodoRiesgo(servicios, m.pagosServicios())));
    }

    /** Los residuos DECIMAL128 por debajo de {@link Calc#EPSILON_SALDO} no generan línea. */
    private static BigDecimal sinResiduo(BigDecimal valor) {
        return Calc.esCero(valor) ? Calc.CERO : valor;
    }
}
