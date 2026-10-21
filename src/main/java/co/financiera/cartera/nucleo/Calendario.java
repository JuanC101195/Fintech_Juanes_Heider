package co.financiera.cartera.nucleo;

/** Conversión entre meses y periodos con aritmética entera exacta (sin errores de punto flotante). */
public final class Calendario {

    private Calendario() {
    }

    /** Cuotas pactadas (Inputs B19: 104). */
    public static int numeroCuotas(ParametrosProducto p) {
        return p.numeroCuotas();
    }

    /** ROUNDUP(periodo × 12 / ppa): mes al que pertenece una cuota. Semanal: la cuota 4 es del mes 1 y la 5 del
     * mes 2; quincenal: ⌈periodo / 2⌉; mensual: el mismo periodo. */
    public static int mes(int periodo, ParametrosProducto p) {
        int numerador = periodo * p.mesesPorAnio();
        return (numerador + p.periodosPorAnio() - 1) / p.periodosPorAnio();
    }

    /**
     * ROUNDDOWN((mes − 1) × ppa / 12) + 1: primera cuota de un mes. Es la regla del Excel para
     * ubicar eventos por mes (cambio de tasa del mes 11 → cuota 44 en semanal).
     */
    public static int primerPeriodoDelMes(int mes, ParametrosProducto p) {
        return (mes - 1) * p.periodosPorAnio() / p.mesesPorAnio() + 1;
    }

    /** ROUNDDOWN(mes × ppa / 12): periodo que el Excel asocia al cierre de un mes (mes 10 → cuota 43 en semanal). */
    public static int periodoDelMes(int mes, ParametrosProducto p) {
        return mes * p.periodosPorAnio() / p.mesesPorAnio();
    }
}
