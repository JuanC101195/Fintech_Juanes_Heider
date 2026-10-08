package co.financiera.cartera.nucleo;

import co.financiera.cartera.nucleo.motor.Instruccion;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Regla de usura (caso 2). Acordado con negocio: la tasa del crédito es fija y solo cambia por
 * ley. Si la tasa pactada queda por encima de la tasa de usura vigente, se ajusta a
 * <b>usura − 1 punto</b> y el ajuste se aplica como un {@link Instruccion.CambioTasa}: nueva
 * cuota sobre el saldo conservando la fecha final.
 *
 * <p>Todas las tasas son efectivas anuales en fracción (0,8732 = 87,32 % EA). "1 punto" se toma
 * como un punto porcentual de EA (0,01), pendiente de confirmar con Diego (ver
 * docs/reglas/caso-2.md).
 */
public final class Usura {

    /** Margen bajo la usura al que se lleva un crédito que la supera: 1 punto porcentual de EA. */
    public static final BigDecimal MARGEN = Calc.bd("0.01");

    private Usura() {
    }

    /**
     * Verdadero si la EA pactada supera la usura vigente. Cobrar exactamente la usura es legal, por
     * eso la comparación es estricta.
     */
    public static boolean superaUsura(BigDecimal eaPactada, BigDecimal usuraVigente) {
        validar(eaPactada, usuraVigente);
        return eaPactada.compareTo(usuraVigente) > 0;
    }

    /** EA a la que se ajusta el crédito: usura − 1 punto. */
    public static BigDecimal tasaAjustada(BigDecimal usuraVigente) {
        BigDecimal ajustada = Calc.restar(usuraVigente, MARGEN);
        if (ajustada.signum() <= 0) {
            throw new IllegalArgumentException("La usura " + usuraVigente.toPlainString() + " no deja una tasa positiva");
        }
        return ajustada;
    }

    /**
     * El cambio de tasa que exige la usura vigente, o vacío si la EA pactada no la supera. El
     * llamador ubica la instrucción en el periodo desde el que rige (por ejemplo
     * {@link Calendario#primerPeriodoDelMes}).
     */
    public static Optional<Instruccion.CambioTasa> ajustar(BigDecimal eaPactada, BigDecimal usuraVigente) {
        if (!superaUsura(eaPactada, usuraVigente)) {
            return Optional.empty();
        }
        return Optional.of(new Instruccion.CambioTasa(tasaAjustada(usuraVigente)));
    }

    private static void validar(BigDecimal eaPactada, BigDecimal usuraVigente) {
        Objects.requireNonNull(eaPactada, "eaPactada");
        Objects.requireNonNull(usuraVigente, "usuraVigente");
        if (eaPactada.signum() <= 0 || usuraVigente.signum() <= 0) {
            throw new IllegalArgumentException("Las tasas deben ser positivas");
        }
    }
}
