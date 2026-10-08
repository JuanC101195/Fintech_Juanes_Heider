package co.financiera.cartera.contabilidad;

import co.financiera.cartera.nucleo.motor.Movimiento;
import java.util.ArrayList;
import java.util.List;

/**
 * Aplica todas las reglas contables a cada movimiento y rechaza asientos descuadrados (HT-06).
 */
public final class Contabilizador {

    private final List<ReglaContable> reglas;

    public Contabilizador(List<ReglaContable> reglas) {
        this.reglas = List.copyOf(reglas);
    }

    /**
     * Reglas vigentes. Cada caso agrega la suya aquí (una línea por regla, para que los cambios en
     * paralelo no choquen).
     */
    public static Contabilizador estandar() {
        List<ReglaContable> reglas = new ArrayList<>();
        reglas.add(new co.financiera.cartera.contabilidad.reglas.RecaudoCuota()); // caso 1
        // reglas.add(new AbonoExtraordinario()); // casos 5, 6 y 7
        // reglas.add(new RecaudoCuota());        // caso 1
        reglas.add(new co.financiera.cartera.contabilidad.reglas.AbonoExtraordinario()); // casos 5, 6 y 7
        // reglas.add(new PagoInferior());        // caso 8
        // reglas.add(new CobranzaYMora());       // caso 9
        reglas.add(new co.financiera.cartera.contabilidad.reglas.DacionEnPago()); // caso 3
        // reglas.add(new Retoma());              // caso 4
        // reglas.add(new DacionEnPago());        // caso 3
        reglas.add(new co.financiera.cartera.contabilidad.reglas.Retoma()); // caso 4
        return new Contabilizador(reglas);
    }

    public List<Asiento> contabilizar(Movimiento movimiento) {
        List<Asiento> asientos = new ArrayList<>();
        for (ReglaContable regla : reglas) {
            regla.contabilizar(movimiento).ifPresent(a -> {
                if (!a.cuadra()) {
                    throw new AsientoDescuadradoException(a);
                }
                asientos.add(a);
            });
        }
        return asientos;
    }

    public List<Asiento> contabilizar(List<Movimiento> movimientos) {
        List<Asiento> todos = new ArrayList<>();
        movimientos.forEach(m -> todos.addAll(contabilizar(m)));
        return todos;
    }

    public static final class AsientoDescuadradoException extends RuntimeException {
        public AsientoDescuadradoException(Asiento a) {
            super("Asiento descuadrado en el periodo " + a.periodo() + " (" + a.evento() + "): débitos "
                    + a.totalDebitos().toPlainString() + " · créditos " + a.totalCreditos().toPlainString());
        }
    }
}
