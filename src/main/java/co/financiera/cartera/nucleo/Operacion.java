package co.financiera.cartera.nucleo;

import static co.financiera.cartera.nucleo.Calc.bd;

import java.math.BigDecimal;

/**
 * Lo que compra el cliente: la moto más el alistamiento, la tecnología y los gastos comerciales
 * (hoja Inputs, filas 4 a 11).
 *
 * <p>Regla de negocio (2026-10-08): la cuota inicial es el 10 % del <b>valor total</b> de la
 * operación y se financia el resto.
 */
public record Operacion(
        BigDecimal valorMoto,
        BigDecimal matriculaRunt,
        BigDecimal soat,
        BigDecimal prenda,
        BigDecimal gpsPlataforma,
        BigDecimal gastosComerciales) {

    /** Operación de ejemplo del Excel: moto de 5.100.000, total 5.904.400. */
    public static Operacion ejemploExcel() {
        return new Operacion(bd(5_100_000), bd(171_100), bd(373_300), bd(120_000), bd(80_000), bd(60_000));
    }

    public BigDecimal alistamiento() {
        return Calc.sumar(matriculaRunt, soat, prenda);
    }

    public BigDecimal valorTotal() {
        return Calc.sumar(valorMoto, alistamiento(), gpsPlataforma, gastosComerciales);
    }

    public BigDecimal cuotaInicial(ParametrosProducto p) {
        return Calc.multiplicar(valorTotal(), p.porcentajeInicial());
    }

    public BigDecimal montoFinanciado(ParametrosProducto p) {
        return Calc.restar(valorTotal(), cuotaInicial(p));
    }

    /** Porcentaje de financiación sobre la moto (LTV). */
    public BigDecimal ltv(ParametrosProducto p) {
        return Calc.dividir(montoFinanciado(p), valorMoto);
    }
}
