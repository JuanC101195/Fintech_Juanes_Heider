package co.financiera.cartera.contabilidad.reglas;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Asiento.Linea;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.contabilidad.ReglaContable;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.util.List;
import java.util.Optional;

/**
 * R8-PAGO-MAYOR-EXIGIBLE (Diego, ronda 8, 2026-10-28) · Sobrante de un pago mayor que lo exigible. El pago se recibe
 * completo: lo aplicado lo contabilizan las demás reglas (con {@code pagoRecibido}) y esta solo registra lo que sobró
 * como cuenta por pagar al cliente, para devolverlo:
 *
 * <pre>
 *   Caja                              D  sobrante recibido
 *   CxP sobrante por devolver         C  sobrante recibido
 * </pre>
 *
 * <p>Con sobrante 0 (siempre con el perfil EXCEL) no genera asiento. La cuenta es provisional hasta que contabilidad
 * entregue el PUC.
 */
public final class SobranteCliente implements ReglaContable {

    @Override
    public Optional<Asiento> contabilizar(Movimiento m) {
        if (m.sobranteCliente().signum() <= 0) {
            return Optional.empty();
        }
        return ReglaContable.asiento(m, List.of(
                Linea.debito(Cuenta.CAJA, m.sobranteCliente(), "Pago recibido por encima de lo exigible"),
                Linea.credito(Cuenta.CXP_SOBRANTE_CLIENTE, m.sobranteCliente(),
                        "Sobrante por devolver al cliente (R8-PAGO-MAYOR-EXIGIBLE)")));
    }
}
