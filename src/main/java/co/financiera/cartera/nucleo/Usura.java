package co.financiera.cartera.nucleo;

import co.financiera.cartera.nucleo.motor.Instruccion;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Regla de usura (caso 2). Acordado con negocio: la tasa del crédito es fija y solo cambia por
 * ley. Si la tasa del crédito queda por encima de la tasa de usura vigente, se ajusta a
 * <b>usura − margen</b> y el ajuste se aplica como un {@link Instruccion.CambioTasa}: nueva
 * cuota sobre el saldo conservando la fecha final.
 *
 * <p>Todas las tasas son efectivas anuales en fracción (0,8732 = 87,32 % EA). El margen se expresa
 * en puntos porcentuales de EA (1 = 100 pb = 0,01 de EA): Diego lo confirmó el 2026-10-20 (T2) y
 * queda configurable en {@code ReglasNegocio.margenUsura}.
 *
 * <p>Diego, 2026-10-20 (T4): la tasa nunca vuelve a subir si la usura sube; si la usura baja y la tasa
 * ya ajustada vuelve a quedar por encima, se baja otra vez con el mismo margen
 * ({@link #tasaAjustada(BigDecimal, BigDecimal, BigDecimal)}).
 */
public final class Usura {

    /** Margen bajo la usura al que se lleva un crédito que la supera: 1 punto porcentual de EA. */
    public static final BigDecimal MARGEN = Calc.bd("0.01");

    private static final BigDecimal CIEN = Calc.bd(100);

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
        return usuraMenosMargen(usuraVigente, MARGEN);
    }

    /**
     * Función pura de la regla de usura (T2/T4, Diego 2026-10-20): la EA que debe regir con la usura
     * vigente.
     *
     * <ul>
     *   <li>Si {@code tasaActualEA} supera la usura: usura − margen.</li>
     *   <li>Si no la supera (incluida la usura que sube después de un ajuste): la tasa actual, sin
     *       cambio. La tasa nunca vuelve a subir.</li>
     * </ul>
     *
     * Aplicada cada vez que se publica una usura nueva sobre la tasa vigente del crédito, una usura que
     * baja por debajo de la tasa ya ajustada la vuelve a bajar con el mismo margen.
     *
     * @param tasaActualEA  EA vigente del crédito (la pactada o la de un ajuste anterior), en fracción
     * @param usuraEA       usura vigente, en fracción de EA
     * @param margenPuntos  puntos porcentuales de EA bajo la usura (1 = 100 pb = 0,01)
     */
    public static BigDecimal tasaAjustada(BigDecimal tasaActualEA, BigDecimal usuraEA, BigDecimal margenPuntos) {
        Objects.requireNonNull(margenPuntos, "margenPuntos");
        if (margenPuntos.signum() < 0) {
            throw new IllegalArgumentException("El margen bajo la usura no puede ser negativo");
        }
        if (!superaUsura(tasaActualEA, usuraEA)) {
            return tasaActualEA;
        }
        return usuraMenosMargen(usuraEA, Calc.dividir(margenPuntos, CIEN));
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

    private static BigDecimal usuraMenosMargen(BigDecimal usuraVigente, BigDecimal margenFraccion) {
        BigDecimal ajustada = Calc.restar(usuraVigente, margenFraccion);
        if (ajustada.signum() <= 0) {
            throw new IllegalArgumentException("La usura " + usuraVigente.toPlainString() + " no deja una tasa positiva");
        }
        return ajustada;
    }

    private static void validar(BigDecimal eaPactada, BigDecimal usuraVigente) {
        Objects.requireNonNull(eaPactada, "eaPactada");
        Objects.requireNonNull(usuraVigente, "usuraVigente");
        if (eaPactada.signum() <= 0 || usuraVigente.signum() <= 0) {
            throw new IllegalArgumentException("Las tasas deben ser positivas");
        }
    }
}
