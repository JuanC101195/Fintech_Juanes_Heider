package co.financiera.cartera.nucleo;

/** Conversión entre meses y semanas con aritmética entera exacta (sin errores de punto flotante). */
public final class Calendario {

    private Calendario() {
    }

    /** ROUNDDOWN(plazoMeses × 52 / 12): 20 meses → 86 cuotas. */
    public static int numeroCuotas(ParametrosProducto p) {
        return p.plazoMeses() * p.semanasPorAnio() / p.mesesPorAnio();
    }

    /** ROUNDUP(periodo / (52/12)): mes al que pertenece una cuota semanal. La cuota 4 es del mes 1 y la 5 del mes 2. */
    public static int mes(int periodo, ParametrosProducto p) {
        int numerador = periodo * p.mesesPorAnio();
        return (numerador + p.semanasPorAnio() - 1) / p.semanasPorAnio();
    }

    /**
     * ROUNDDOWN((mes − 1) × 52/12) + 1: primera cuota semanal de un mes. Es la regla del Excel para
     * ubicar eventos por mes (cambio de tasa del mes 11 → cuota 44).
     */
    public static int primerPeriodoDelMes(int mes, ParametrosProducto p) {
        return (mes - 1) * p.semanasPorAnio() / p.mesesPorAnio() + 1;
    }

    /** ROUNDDOWN(mes × 52/12): periodo que el Excel asocia al cierre de un mes (mes 10 → cuota 43). */
    public static int periodoDelMes(int mes, ParametrosProducto p) {
        return mes * p.semanasPorAnio() / p.mesesPorAnio();
    }
}
