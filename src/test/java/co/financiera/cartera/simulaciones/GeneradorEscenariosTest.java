package co.financiera.cartera.simulaciones;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * ADR 0002 paso 4 · Contrato del JSON que lee {@code cuestionario/}: las claves no se renombran
 * (cuotaSemanal, semanaFinal, semanasEnMora, semanasClave) aunque el motor ya hable de periodos, y
 * las cuotas clave calculadas con el Calendario siguen siendo las del Excel semanal.
 */
class GeneradorEscenariosTest {

    private final String json = GeneradorEscenarios.generar();

    @Test
    void conservaLasClavesQueLeeElCuestionario() {
        assertThat(json).contains("\"semanasClave\"", "\"cuotaSemanal\"", "\"semanaFinal\"", "\"semanasEnMora\"",
                "\"serieSaldo\"", "\"serieVencido\"", "\"kpis\"", "\"semanas\"");
    }

    @Test
    void lasCuotasClaveSonLasDelExcelSemanal() {
        assertThat(json).contains("\"P1\": {\"caso\": \"Caso 8 · el cliente paga el 40 % de la cuota 43 y luego vuelve a pagar"
                + " la cuota pactada\", \"semanasClave\": [43, 44]");
        assertThat(json).contains("\"P7\": {\"caso\": \"Caso 3 · el cliente deja de pagar en la cuota 31 y entrega la moto"
                + " (vale 3.000.000) en la 34\", \"semanasClave\": [31, 34]");
        assertThat(json).contains("\"semanasClave\": [44, 86]", "\"semanasClave\": [43, 86]", "\"semanasClave\": [1, 61]",
                "\"semanasClave\": [86, 87]");
    }
}
