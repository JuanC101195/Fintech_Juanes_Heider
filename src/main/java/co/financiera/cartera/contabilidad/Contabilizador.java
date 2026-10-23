package co.financiera.cartera.contabilidad;

import co.financiera.cartera.contabilidad.reglas.AbonoExtraordinario;
import co.financiera.cartera.contabilidad.reglas.CobranzaYMora;
import co.financiera.cartera.contabilidad.reglas.DacionEnPago;
import co.financiera.cartera.contabilidad.reglas.PagoInferior;
import co.financiera.cartera.contabilidad.reglas.RecaudoConVencidos;
import co.financiera.cartera.contabilidad.reglas.RecaudoCuota;
import co.financiera.cartera.contabilidad.reglas.Retoma;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Aplica todas las reglas contables a cada movimiento y rechaza asientos descuadrados (HT-06).
 */
public final class Contabilizador {

    /** Diferencia máxima entre la Caja de los asientos y lo que entregó el cliente en un periodo (H1). */
    private static final BigDecimal TOLERANCIA_CAJA = new BigDecimal("0.01");

    private final List<ReglaContable> reglas;

    public Contabilizador(List<ReglaContable> reglas) {
        this.reglas = List.copyOf(reglas);
    }

    /**
     * Reglas vigentes. Cada caso agrega la suya aquí (una línea por regla, para que los cambios en
     * paralelo no choquen).
     */
    public static Contabilizador estandar() {
        return new Contabilizador(reglas(Cuenta.INGRESOS_COBRANZA));
    }

    /**
     * Las reglas contables según el perfil de negocio. Hoy cambia una cosa: con
     * {@code cobranzaIngresoNoOperacional} (Diego, R4-COBRANZA-CONTABLE, ronda 4) la cobranza causada va a
     * {@link Cuenta#INGRESOS_NO_OPERACIONALES_COBRANZA} (PUC clase 42) en lugar de "Ingresos por cobranza"
     * del Excel. Con {@code ReglasNegocio.EXCEL} es igual a {@link #estandar()}.
     */
    public static Contabilizador para(ReglasNegocio reglasNegocio) {
        return new Contabilizador(reglas(reglasNegocio.cobranzaIngresoNoOperacional()
                ? Cuenta.INGRESOS_NO_OPERACIONALES_COBRANZA : Cuenta.INGRESOS_COBRANZA));
    }

    private static List<ReglaContable> reglas(Cuenta ingresoCobranza) {
        List<ReglaContable> reglas = new ArrayList<>();
        reglas.add(new RecaudoCuota());        // caso 1: cuota pagada completa y a tiempo
        reglas.add(new AbonoExtraordinario()); // casos 5, 6 y 7: prepago y abono extra
        reglas.add(new PagoInferior());        // caso 8: pago incompleto y reclasificación a vencida
        reglas.add(new CobranzaYMora(ingresoCobranza)); // caso 9: pago tardío con cobranza y mora
        reglas.add(new RecaudoConVencidos());  // casos 8 y 12: semanas que recuperan vencidos
        reglas.add(new DacionEnPago());        // caso 3: moto recibida por menos que la deuda
        reglas.add(new Retoma());              // caso 4: moto recibida por más que la deuda
        return reglas;
    }

    public List<Asiento> contabilizar(Movimiento movimiento) {
        List<Asiento> asientos = new ArrayList<>();
        for (ReglaContable regla : reglas) {
            regla.contabilizar(movimiento).ifPresent(a -> {
                if (!a.cuadra()) {
                    throw new AsientoDescuadradoException(a);
                }
                asientos.add(a);
            });
        }
        verificarCaja(movimiento, asientos);
        return asientos;
    }

    /**
     * Auditoría 2026-10-23 (H1): invariante de caja. Lo que los asientos de un periodo mueven en Caja tiene que ser
     * exactamente lo que el cliente entregó ese periodo ({@link Movimiento#pagoTotalCliente()}): ni más (una regla
     * de recaudo de más, la Caja contabilizada dos veces) ni menos (un recaudo que ninguna regla tomó). Tolerancia
     * de un centavo por los residuos DECIMAL128 que las reglas no llevan a línea contable.
     */
    private static void verificarCaja(Movimiento m, List<Asiento> asientos) {
        if (m.periodo() < 1) {
            return;
        }
        BigDecimal caja = BigDecimal.ZERO;
        for (Asiento a : asientos) {
            for (Asiento.Linea l : a.lineas()) {
                if (l.cuenta() == Cuenta.CAJA) {
                    caja = caja.add(l.debito()).subtract(l.credito());
                }
            }
        }
        if (caja.subtract(m.pagoTotalCliente()).abs().compareTo(TOLERANCIA_CAJA) > 0) {
            throw new CajaDescuadradaException(m, caja);
        }
    }

    public static final class CajaDescuadradaException extends RuntimeException {
        public CajaDescuadradaException(Movimiento m, BigDecimal caja) {
            super("Caja descuadrada en el periodo " + m.periodo() + " (" + m.evento() + "): los asientos mueven "
                    + caja.toPlainString() + " y el cliente entregó " + m.pagoTotalCliente().toPlainString());
        }
    }

    public List<Asiento> contabilizar(List<Movimiento> movimientos) {
        List<Asiento> todos = new ArrayList<>();
        movimientos.forEach(m -> todos.addAll(contabilizar(m)));
        return todos;
    }

    public static final class AsientoDescuadradoException extends RuntimeException {
        public AsientoDescuadradoException(Asiento a) {
            super("Asiento descuadrado en el periodo " + a.periodo() + " (" + a.evento() + "): débitos "
                    + a.totalDebitos().toPlainString() + " · créditos " + a.totalCreditos().toPlainString());
        }
    }
}
