package co.financiera.cartera.nucleo;

/** Periodicidad de pago (hoja Inputs, sección ⑤). La operación actual paga los martes, semanal (P-06). */
public enum Periodicidad {
    DIARIA(365),
    SEMANAL(52),
    MENSUAL(12);

    private final int periodosPorAnio;

    Periodicidad(int periodosPorAnio) {
        this.periodosPorAnio = periodosPorAnio;
    }

    public int periodosPorAnio() {
        return periodosPorAnio;
    }
}
