package co.financiera.cartera.nucleo;

import java.math.BigDecimal;

/**
 * Servicios complementarios por cuota semanal (hoja Inputs, filas 28 a 31). Se calculan sobre el
 * monto financiado inicial, no sobre el saldo (pendiente de confirmar, P6).
 */
public record Servicios(BigDecimal fcc, BigDecimal asistencia, BigDecimal seguro) {

    public static final Servicios NINGUNO = new Servicios(Calc.CERO, Calc.CERO, Calc.CERO);

    public static Servicios porCuota(BigDecimal montoFinanciado, ParametrosProducto p) {
        BigDecimal semanasMes = p.semanasPorMes();
        BigDecimal conIva = Calc.sumar(Calc.UNO, p.iva());
        BigDecimal fcc = Calc.multiplicar(Calc.dividir(Calc.multiplicar(montoFinanciado, p.fccPorcentajeMensual()), semanasMes), conIva);
        BigDecimal asistencia = Calc.multiplicar(Calc.dividir(p.asistenciaMensual(), semanasMes), conIva);
        BigDecimal seguro = Calc.dividir(Calc.multiplicar(montoFinanciado, p.seguroPorcentajeMensual()), semanasMes);
        return new Servicios(fcc, asistencia, seguro);
    }

    public BigDecimal total() {
        return Calc.sumar(fcc, asistencia, seguro);
    }

    public Servicios mas(Servicios otro) {
        return new Servicios(Calc.sumar(fcc, otro.fcc), Calc.sumar(asistencia, otro.asistencia), Calc.sumar(seguro, otro.seguro));
    }
}
