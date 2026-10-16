package co.financiera.cartera.nucleo;

import static co.financiera.cartera.nucleo.Calc.bd;

import java.math.BigDecimal;

/**
 * Lo que compra el cliente: la moto más el alistamiento, la tecnología y los gastos comerciales
 * (hoja Inputs, filas 4 a 11), menos el pago inicial que haga.
 *
 * <p>Regla de negocio (Diego, 2026-10-16, P-09): el pago inicial es <b>opcional</b>, lo digita el
 * asesor y se resta del valor a financiar; todo el crédito parte de ese monto. Ejemplo de Diego:
 * operación de 6.500.000 con inicial de 700.000 → se financian 5.800.000. Reemplaza la regla
 * anterior del 10 % fijo.
 *
 * @param pagoInicial lo que el cliente paga de contado; 0 si no da inicial
 */
public record Operacion(
        BigDecimal valorMoto,
        BigDecimal matriculaRunt,
        BigDecimal soat,
        BigDecimal prenda,
        BigDecimal gpsPlataforma,
        BigDecimal gastosComerciales,
        BigDecimal pagoInicial) {

    public Operacion {
        if (pagoInicial.signum() < 0) {
            throw new IllegalArgumentException("El pago inicial no puede ser negativo: " + pagoInicial.toPlainString());
        }
        if (pagoInicial.compareTo(Calc.sumar(valorMoto, matriculaRunt, soat, prenda, gpsPlataforma, gastosComerciales)) >= 0) {
            throw new IllegalArgumentException("El pago inicial debe ser menor que el valor total de la operación");
        }
    }

    /** Operación sin pago inicial. */
    public Operacion(BigDecimal valorMoto, BigDecimal matriculaRunt, BigDecimal soat, BigDecimal prenda,
            BigDecimal gpsPlataforma, BigDecimal gastosComerciales) {
        this(valorMoto, matriculaRunt, soat, prenda, gpsPlataforma, gastosComerciales, Calc.CERO);
    }

    /** Operación de ejemplo del Excel: moto de 5.100.000, total 5.904.400, sin inicial. */
    public static Operacion ejemploExcel() {
        return new Operacion(bd(5_100_000), bd(171_100), bd(373_300), bd(120_000), bd(80_000), bd(60_000));
    }

    public Operacion conPagoInicial(BigDecimal valor) {
        return new Operacion(valorMoto, matriculaRunt, soat, prenda, gpsPlataforma, gastosComerciales, valor);
    }

    public BigDecimal alistamiento() {
        return Calc.sumar(matriculaRunt, soat, prenda);
    }

    public BigDecimal valorTotal() {
        return Calc.sumar(valorMoto, alistamiento(), gpsPlataforma, gastosComerciales);
    }

    public BigDecimal montoFinanciado() {
        return Calc.restar(valorTotal(), pagoInicial);
    }

    /** Porcentaje de financiación sobre la moto (LTV). */
    public BigDecimal ltv() {
        return Calc.dividir(montoFinanciado(), valorMoto);
    }
}
