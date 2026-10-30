package co.financiera.cartera.nucleo;

import java.math.BigDecimal;

/**
 * Servicios complementarios por cuota (hoja Inputs, filas 28 a 31): valor mensual × 12 / ppa, así que
 * en mensual cada cuota lleva el valor mensual completo y en quincenal la mitad. Se calculan sobre el
 * monto financiado inicial, no sobre el saldo (Diego, P-07).
 *
 * <p>El fondo de garantías es hoy el Fondo de Coberturas Crediticias (FCC del Excel); se nombra
 * genérico porque el fondo puede cambiar (P-07).
 *
 * <p>R9-SEGURO-TODO-RIESGO (Diego 2026-10-30): el seguro todo riesgo es un servicio aparte que se suma al seguro de
 * vida. {@link #seguroTodoRiesgo} es el seguro sin IVA y {@link #ivaSeguroTodoRiesgo} su IVA (19 %), separados para
 * que la contabilidad los registre por separado ("si el seguro da $10.000, el cobro al cliente es de $11.900"). En la
 * prelación van después del seguro de vida: seguro todo riesgo y luego su IVA. Sin la regla los dos son 0 y todo queda
 * como antes (el Excel no los tiene).
 */
public record Servicios(BigDecimal fondoGarantias, BigDecimal asistencia, BigDecimal seguro,
        BigDecimal seguroTodoRiesgo, BigDecimal ivaSeguroTodoRiesgo) {

    public static final Servicios NINGUNO = new Servicios(Calc.CERO, Calc.CERO, Calc.CERO);

    /** Fondo, asistencia y seguro de vida, sin seguro todo riesgo (el Excel y lo anterior a la ronda 9). */
    public Servicios(BigDecimal fondoGarantias, BigDecimal asistencia, BigDecimal seguro) {
        this(fondoGarantias, asistencia, seguro, Calc.CERO, Calc.CERO);
    }

    public static Servicios porCuota(BigDecimal montoFinanciado, ParametrosProducto p) {
        return porCuota(montoFinanciado, p, Calc.CERO);
    }

    /**
     * Servicios por cuota con el seguro todo riesgo de {@code porcentajeTodoRiesgoMensual} (R9-SEGURO-TODO-RIESGO):
     * se periodiza exactamente como el seguro de vida (monto × % mensual / periodos por mes) y lleva encima el IVA de
     * servicios del producto, como el fondo y la asistencia. Con 0 no hay seguro todo riesgo.
     */
    public static Servicios porCuota(BigDecimal montoFinanciado, ParametrosProducto p, BigDecimal porcentajeTodoRiesgoMensual) {
        BigDecimal periodosMes = p.periodosPorMes();
        BigDecimal conIva = Calc.sumar(Calc.UNO, p.iva());
        BigDecimal fondoGarantias = Calc.multiplicar(
                Calc.dividir(Calc.multiplicar(montoFinanciado, p.fondoGarantiasPorcentajeMensual()), periodosMes), conIva);
        BigDecimal asistencia = Calc.multiplicar(Calc.dividir(p.asistenciaMensual(), periodosMes), conIva);
        BigDecimal seguro = Calc.dividir(Calc.multiplicar(montoFinanciado, p.seguroPorcentajeMensual()), periodosMes);
        if (porcentajeTodoRiesgoMensual == null || porcentajeTodoRiesgoMensual.signum() == 0) {
            return new Servicios(fondoGarantias, asistencia, seguro);
        }
        BigDecimal todoRiesgo = Calc.dividir(Calc.multiplicar(montoFinanciado, porcentajeTodoRiesgoMensual), periodosMes);
        return new Servicios(fondoGarantias, asistencia, seguro, todoRiesgo, Calc.multiplicar(todoRiesgo, p.iva()));
    }

    public BigDecimal total() {
        return Calc.sumar(fondoGarantias, asistencia, seguro, seguroTodoRiesgo, ivaSeguroTodoRiesgo);
    }

    /** Seguro todo riesgo con su IVA: lo que se le cobra al cliente por ese servicio. */
    public BigDecimal todoRiesgoConIva() {
        return Calc.sumar(seguroTodoRiesgo, ivaSeguroTodoRiesgo);
    }

    /**
     * Solo los seguros: en periodos de atraso o impago el fondo y la asistencia no se causan. R9-SEGURO-TODO-RIESGO
     * (decisión provisional, pregunta de la ronda 11): el seguro todo riesgo y su IVA siguen causándose con el de vida
     * (Diego dijo "solo se causa el seguro" sin precisar cuál).
     */
    public Servicios soloSeguro() {
        return new Servicios(Calc.CERO, Calc.CERO, seguro, seguroTodoRiesgo, ivaSeguroTodoRiesgo);
    }

    public Servicios menos(Servicios otro) {
        return new Servicios(Calc.restar(fondoGarantias, otro.fondoGarantias), Calc.restar(asistencia, otro.asistencia),
                Calc.restar(seguro, otro.seguro), Calc.restar(seguroTodoRiesgo, otro.seguroTodoRiesgo),
                Calc.restar(ivaSeguroTodoRiesgo, otro.ivaSeguroTodoRiesgo));
    }

    public Servicios noNegativo() {
        return new Servicios(Calc.noNegativo(fondoGarantias), Calc.noNegativo(asistencia), Calc.noNegativo(seguro),
                Calc.noNegativo(seguroTodoRiesgo), Calc.noNegativo(ivaSeguroTodoRiesgo));
    }

    public Servicios mas(Servicios otro) {
        return new Servicios(Calc.sumar(fondoGarantias, otro.fondoGarantias), Calc.sumar(asistencia, otro.asistencia),
                Calc.sumar(seguro, otro.seguro), Calc.sumar(seguroTodoRiesgo, otro.seguroTodoRiesgo),
                Calc.sumar(ivaSeguroTodoRiesgo, otro.ivaSeguroTodoRiesgo));
    }

    /**
     * Reparte {@code disponible} por la prelación de los servicios (Inputs ⑥): fondo → asistencia → seguro de vida →
     * seguro todo riesgo → su IVA, cada uno hasta lo que hay en este objeto. Devuelve lo pagado de cada uno. Las restas
     * son sucesivas, en el mismo orden que antes de la ronda 9, así las cifras sin seguro todo riesgo no cambian.
     */
    public Servicios pagarHasta(BigDecimal disponible) {
        BigDecimal restante = disponible;
        BigDecimal fondo = Calc.min(restante, fondoGarantias);
        restante = Calc.restar(restante, fondo);
        BigDecimal pagoAsistencia = Calc.min(restante, asistencia);
        restante = Calc.restar(restante, pagoAsistencia);
        BigDecimal pagoSeguro = Calc.min(restante, seguro);
        restante = Calc.restar(restante, pagoSeguro);
        // Sin seguro todo riesgo se paga el 0 exacto (escala 0), no el restante en cero con la escala que traiga: así las
        // corridas sin la regla (el Excel) no arrastran ceros con escala y la instantánea, que omite esos ceros, reanuda
        // con los mismos BigDecimal.
        BigDecimal todoRiesgo = seguroTodoRiesgo.signum() == 0 ? Calc.CERO : Calc.min(restante, seguroTodoRiesgo);
        restante = Calc.restar(restante, todoRiesgo);
        BigDecimal iva = ivaSeguroTodoRiesgo.signum() == 0 ? Calc.CERO : Calc.min(restante, ivaSeguroTodoRiesgo);
        return new Servicios(fondo, pagoAsistencia, pagoSeguro, todoRiesgo, iva);
    }

    /**
     * {@code disponible} menos cada componente, restado uno a uno en el orden de la prelación (lo que queda después de
     * pagar estos servicios con {@link #pagarHasta}). Mismas restas sucesivas que antes de la ronda 9.
     */
    public BigDecimal descontarDe(BigDecimal disponible) {
        BigDecimal restante = Calc.restar(disponible, fondoGarantias);
        restante = Calc.restar(restante, asistencia);
        restante = Calc.restar(restante, seguro);
        restante = Calc.restar(restante, seguroTodoRiesgo);
        return Calc.restar(restante, ivaSeguroTodoRiesgo);
    }

    /**
     * El formato de un record, pero sin el seguro todo riesgo ni su IVA cuando los dos son cero: llegaron con la ronda 9
     * y los textos que se calculan sobre {@code toString} (huellas, comparaciones) no deben cambiar sin ellos.
     */
    @Override
    public String toString() {
        String base = "Servicios[fondoGarantias=" + fondoGarantias + ", asistencia=" + asistencia + ", seguro=" + seguro;
        if (seguroTodoRiesgo.signum() == 0 && ivaSeguroTodoRiesgo.signum() == 0) {
            return base + "]";
        }
        return base + ", seguroTodoRiesgo=" + seguroTodoRiesgo + ", ivaSeguroTodoRiesgo=" + ivaSeguroTodoRiesgo + "]";
    }
}
