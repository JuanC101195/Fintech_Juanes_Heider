package co.financiera.cartera.nucleo.motor;

import java.math.BigDecimal;

/** Un abono extraordinario no puede superar el saldo de capital después de la cuota (P-14). */
public final class AbonoMayorQueElSaldoException extends IllegalArgumentException {

    private final int periodo;
    private final BigDecimal abono;
    private final BigDecimal saldo;

    public AbonoMayorQueElSaldoException(int periodo, BigDecimal abono, BigDecimal saldo) {
        super("Semana " + periodo + ": el abono de " + abono.toPlainString() + " supera el saldo de capital de "
                + saldo.toPlainString() + "; para cancelar el crédito se usa el prepago total");
        this.periodo = periodo;
        this.abono = abono;
        this.saldo = saldo;
    }

    public int periodo() {
        return periodo;
    }

    public BigDecimal abono() {
        return abono;
    }

    public BigDecimal saldo() {
        return saldo;
    }
}
