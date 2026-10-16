package co.financiera.cartera.nucleo;

import java.math.BigDecimal;

/**
 * Servicios complementarios por cuota semanal (hoja Inputs, filas 28 a 31). Se calculan sobre el
 * monto financiado inicial, no sobre el saldo (Diego, P-07).
 *
 * <p>El fondo de garantías es hoy el Fondo de Coberturas Crediticias (FCC del Excel); se nombra
 * genérico porque el fondo puede cambiar (P-07).
 */
public record Servicios(BigDecimal fondoGarantias, BigDecimal asistencia, BigDecimal seguro) {

    public static final Servicios NINGUNO = new Servicios(Calc.CERO, Calc.CERO, Calc.CERO);

    public static Servicios porCuota(BigDecimal montoFinanciado, ParametrosProducto p) {
        BigDecimal semanasMes = p.periodosPorMes();
        BigDecimal conIva = Calc.sumar(Calc.UNO, p.iva());
        BigDecimal fondoGarantias = Calc.multiplicar(
                Calc.dividir(Calc.multiplicar(montoFinanciado, p.fondoGarantiasPorcentajeMensual()), semanasMes), conIva);
        BigDecimal asistencia = Calc.multiplicar(Calc.dividir(p.asistenciaMensual(), semanasMes), conIva);
        BigDecimal seguro = Calc.dividir(Calc.multiplicar(montoFinanciado, p.seguroPorcentajeMensual()), semanasMes);
        return new Servicios(fondoGarantias, asistencia, seguro);
    }

    public BigDecimal total() {
        return Calc.sumar(fondoGarantias, asistencia, seguro);
    }

    /** Solo el seguro de vida: en periodos de atraso o impago el fondo y la asistencia no se causan. */
    public Servicios soloSeguro() {
        return new Servicios(Calc.CERO, Calc.CERO, seguro);
    }

    public Servicios menos(Servicios otro) {
        return new Servicios(Calc.restar(fondoGarantias, otro.fondoGarantias), Calc.restar(asistencia, otro.asistencia),
                Calc.restar(seguro, otro.seguro));
    }

    public Servicios noNegativo() {
        return new Servicios(Calc.noNegativo(fondoGarantias), Calc.noNegativo(asistencia), Calc.noNegativo(seguro));
    }

    public Servicios mas(Servicios otro) {
        return new Servicios(Calc.sumar(fondoGarantias, otro.fondoGarantias), Calc.sumar(asistencia, otro.asistencia),
                Calc.sumar(seguro, otro.seguro));
    }
}
