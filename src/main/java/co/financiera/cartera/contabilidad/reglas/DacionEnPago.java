package co.financiera.cartera.contabilidad.reglas;

import static co.financiera.cartera.nucleo.Calc.noNegativo;
import static co.financiera.cartera.nucleo.Calc.restar;
import static co.financiera.cartera.nucleo.Calc.sumar;

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
 * Caso 3 · Dación en pago: el cliente entrega la moto por un valor menor que la deuda y el
 * faltante queda como cuenta por cobrar al fondo de garantías (CxC FCC). Panel Z8:AC14 de la hoja
 * Caso3_Dacion:
 *
 * <pre>
 *   Débito  Inventario de motos        = valor de la moto              (AA10 = M34)
 *   Débito  CxC FCC                    = deuda − valor de la moto      (AA11 = N34)
 *   Crédito Cartera de créditos        = capital extinguido            (AB12 = D34)
 *   Crédito CxC intereses corrientes   = deuda − capital               (AB13 = L34 − D34)
 * </pre>
 *
 * <p>Aplica solo a cierres con faltante (CxC FCC &gt; 0). El cierre con excedente (CxP deudor) es
 * la retoma del caso 4 y tiene su propia regla. El movimiento no trae el tipo de recuperación:
 * si algún día hay retomas con faltante o daciones con excedente, hay que agregarlo (ver
 * docs/reglas/caso-3.md, P3.3).
 *
 * <p>Como en el Excel, todo lo que no es capital se acredita a CxC intereses corrientes: en el
 * caso 3 no hay mora, servicios ni cobranza pendientes (D8 / P7). Si se causan, el movimiento debe
 * traer su desglose antes del cierre para repartir el crédito (P3.2).
 */
public final class DacionEnPago implements ReglaContable {

    @Override
    public Optional<Asiento> contabilizar(Movimiento m) {
        if (m.estado() != EstadoCredito.CERRADO_POR_EVENTO || m.cxcFcc().signum() <= 0) {
            return Optional.empty();
        }
        // Capital que se extingue: el saldo que quedaba después del pago de la semana (en el Excel,
        // el saldo inicial del periodo del siniestro, porque esa semana no hay pago).
        BigDecimal capital = noNegativo(restar(m.saldoInicial(), sumar(m.pagoCapital(), m.abonoExtra(), m.prepagoCapital())));
        BigDecimal intereses = noNegativo(restar(m.deudaAExtinguir(), capital));
        return ReglaContable.asiento(m, List.of(
                Linea.debito(Cuenta.INVENTARIO_MOTOS, m.inventario(),
                        "Ingreso de la moto por el valor definido para el evento"),
                Linea.debito(Cuenta.CXC_FCC, m.cxcFcc(), "Residual de la deuda no cubierto por la moto"),
                Linea.credito(Cuenta.CARTERA_VIGENTE, capital, "Extinción del capital"),
                Linea.credito(Cuenta.CXC_INTERES_CORRIENTE, intereses,
                        "Extinción de intereses corrientes pendientes")));
    }
}
