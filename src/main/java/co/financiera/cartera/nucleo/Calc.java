package co.financiera.cartera.nucleo;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Aritmética del motor. Todo el dinero va en {@link BigDecimal} con 34 dígitos significativos
 * (DECIMAL128) y sin redondear: el Excel de Diego tampoco redondea y la tolerancia de las
 * validaciones es 1 COP. El redondeo a pesos se hará en un solo punto cuando se decida P2.
 */
public final class Calc {

    public static final MathContext MC = MathContext.DECIMAL128;
    public static final BigDecimal CERO = BigDecimal.ZERO;
    public static final BigDecimal UNO = BigDecimal.ONE;
    /** Debajo de esto un saldo se considera cero (el Excel usa 0,005). */
    public static final BigDecimal EPSILON_SALDO = new BigDecimal("0.005");

    private Calc() {
    }

    public static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    public static BigDecimal bd(long valor) {
        return BigDecimal.valueOf(valor);
    }

    public static BigDecimal sumar(BigDecimal... valores) {
        BigDecimal total = CERO;
        for (BigDecimal v : valores) {
            total = total.add(v, MC);
        }
        return total;
    }

    public static BigDecimal restar(BigDecimal a, BigDecimal b) {
        return a.subtract(b, MC);
    }

    public static BigDecimal multiplicar(BigDecimal a, BigDecimal b) {
        return a.multiply(b, MC);
    }

    public static BigDecimal dividir(BigDecimal a, BigDecimal b) {
        return a.divide(b, MC);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** Por debajo de esto un saldo es ruido de DECIMAL128 (del orden de 1e-27), no dinero. */
    private static final BigDecimal RUIDO = new BigDecimal("1e-12");

    /**
     * max(0, valor): ningún saldo ni pago puede ser negativo. También lleva a cero el ruido de
     * las restas en DECIMAL128 para que no aparezcan capital vencido o mora de 1e-27.
     */
    public static BigDecimal noNegativo(BigDecimal valor) {
        return valor.compareTo(RUIDO) < 0 ? CERO : valor;
    }

    public static boolean esCero(BigDecimal valor) {
        return valor.abs().compareTo(EPSILON_SALDO) <= 0;
    }

    /**
     * Potencia con exponente fraccionario, por ejemplo (1 + EA)^(1/52). BigDecimal no la tiene;
     * se calcula en doble precisión, igual que Excel, y el resultado se pasa a BigDecimal exacto.
     */
    public static BigDecimal potencia(BigDecimal base, double exponente) {
        return new BigDecimal(Math.pow(base.doubleValue(), exponente), MC);
    }
}
