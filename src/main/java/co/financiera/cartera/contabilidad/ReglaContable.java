package co.financiera.cartera.contabilidad;

import co.financiera.cartera.nucleo.motor.Movimiento;
import java.util.List;
import java.util.Optional;

/**
 * Convierte un movimiento del motor en un asiento. Cada evento del Excel tiene su propia regla
 * (una clase en {@code contabilidad.reglas}) y se registra en {@link Contabilizador#estandar()}.
 */
public interface ReglaContable {

    /** El asiento de este movimiento, o vacío si la regla no aplica. */
    Optional<Asiento> contabilizar(Movimiento movimiento);

    /** Ayuda para reglas que generan varias líneas. */
    static Optional<Asiento> asiento(Movimiento m, List<Asiento.Linea> lineas) {
        List<Asiento.Linea> conValor = lineas.stream()
                .filter(l -> l.debito().signum() != 0 || l.credito().signum() != 0)
                .toList();
        return conValor.isEmpty() ? Optional.empty() : Optional.of(new Asiento(m.periodo(), m.evento(), conValor));
    }
}
