package co.financiera.cartera.nucleo;

import java.math.BigDecimal;

/**
 * Periodicidad de pago del crédito (hoja Inputs, sección ⑤; ADR 0002). Se pacta en la negociación
 * y queda fija desde el desembolso (Diego, PPER-10): si hace falta cambiarla se liquida el crédito y
 * se abre uno nuevo.
 *
 * <p>La tasa del periodo es la conversión efectiva de la EA: (1 + EA)^(1/periodosPorAnio) − 1
 * (Diego, PPER). Los días nominales solo sirven para el calendario, tolerancias y textos: no entran
 * en la tasa.
 *
 * <p>La diaria (Inputs A105, 365) se retiró del catálogo (Diego, PPER-4): no la usaba ninguna prueba
 * ni simulación, por eso se eliminó en lugar de marcarla obsoleta.
 */
public enum Periodicidad {
    SEMANAL(52, 7, "semanal", "cuotas semanales"),
    // 24 periodos por año (2 por mes). Confirmado por Diego (R4-QUIN, ronda 4, 2026-10-21).
    QUINCENAL(24, 15, "quincenal", "cuotas quincenales"),
    MENSUAL(12, 30, "mensual", "cuotas mensuales");

    public static final int MESES_POR_ANIO = 12;
    /** Semanas por año con que el Excel convierte plazos en semanas a periodos (Inputs C66, C73). */
    private static final int SEMANAS_POR_ANIO = 52;

    private final int periodosPorAnio;
    private final int diasNominales;
    private final String adjetivo;
    private final String unidadCuota;

    Periodicidad(int periodosPorAnio, int diasNominales, String adjetivo, String unidadCuota) {
        this.periodosPorAnio = periodosPorAnio;
        this.diasNominales = diasNominales;
        this.adjetivo = adjetivo;
        this.unidadCuota = unidadCuota;
    }

    /** Inputs B17: 52, 24 o 12. */
    public int periodosPorAnio() {
        return periodosPorAnio;
    }

    /** Días aproximados de un periodo (7, 15, 30): solo para calendario, tolerancias y textos. */
    public int diasNominales() {
        return diasNominales;
    }

    /** "semanal", "quincenal", "mensual" (Inputs C105:C108). */
    public String adjetivo() {
        return adjetivo;
    }

    /** "cuotas semanales"… (Inputs D105:D108). */
    public String unidadCuota() {
        return unidadCuota;
    }

    /** Inputs B18 = periodos por año / 12 (semanal 4,333…, quincenal 2, mensual 1). */
    public BigDecimal periodosPorMes() {
        return Calc.dividir(Calc.bd(periodosPorAnio), Calc.bd(MESES_POR_ANIO));
    }

    /**
     * Cuotas de un plazo en meses: ROUNDDOWN(meses × periodos por año / 12), como Inputs B15 (Diego,
     * PPER-3). Semanal 24 meses = 104, quincenal = 48, mensual = 24.
     */
    public int cuotasEnMeses(int meses) {
        return meses * periodosPorAnio / MESES_POR_ANIO;
    }

    /**
     * Periodos de un plazo en semanas: ROUNDDOWN(semanas × periodos por año / 52), como Inputs C66 y
     * C73. Solo para reproducir los plazos límite del Excel (100 y 120 semanas): el producto los
     * expresa en periodos del crédito (Diego, PPER-6).
     */
    public int periodosDesdeSemanas(int semanas) {
        return semanas * periodosPorAnio / SEMANAS_POR_ANIO;
    }
}
