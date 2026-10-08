package co.financiera.cartera.contabilidad;

import co.financiera.cartera.nucleo.Calc;
import java.math.BigDecimal;
import java.util.List;

/** Asiento en partida doble generado por un movimiento del motor. */
public record Asiento(int periodo, String evento, List<Linea> lineas) {

    /** Una línea del asiento: débito o crédito (el otro en cero) y su explicación. */
    public record Linea(Cuenta cuenta, BigDecimal debito, BigDecimal credito, String explicacion) {

        public static Linea debito(Cuenta cuenta, BigDecimal valor, String explicacion) {
            return new Linea(cuenta, valor, Calc.CERO, explicacion);
        }

        public static Linea credito(Cuenta cuenta, BigDecimal valor, String explicacion) {
            return new Linea(cuenta, Calc.CERO, valor, explicacion);
        }
    }

    public BigDecimal totalDebitos() {
        return lineas.stream().map(Linea::debito).reduce(Calc.CERO, (a, b) -> a.add(b, Calc.MC));
    }

    public BigDecimal totalCreditos() {
        return lineas.stream().map(Linea::credito).reduce(Calc.CERO, (a, b) -> a.add(b, Calc.MC));
    }

    /** Invariante: débitos = créditos con tolerancia de 1 COP (HT-06). */
    public boolean cuadra() {
        return totalDebitos().subtract(totalCreditos()).abs().compareTo(BigDecimal.ONE) <= 0;
    }
}
