package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.api.Dtos.CreditoRequest;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import org.junit.jupiter.api.Test;

/**
 * R6-LIMITE-CORTO en el API. Fuente: respuesta de Diego, ronda 6. Para créditos nuevos y simulaciones el plazo
 * límite es min(plazo del crédito, 24 meses) en periodos: un crédito mensual de 12 cuotas tiene límite 12.
 */
class RespuestasRonda5ApiTest {

    private static final ReglasNegocio NEGOCIO = ReglasNegocio.EXCEL.conPlazoLimiteMeses(24);

    private static CreditoRequest credito(String periodicidad, Integer cuotas, Integer meses) {
        return new CreditoRequest(null, periodicidad, cuotas, meses, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    @Test
    void elPlazoLimiteDeUnCreditoCortoEsSuPropioPlazo() {
        assertThat(ValidadorCredito.parametros(credito("MENSUAL", null, 12), NEGOCIO).plazoLimitePeriodos()).isEqualTo(12);
        assertThat(ValidadorCredito.parametros(credito("SEMANAL", null, 12), NEGOCIO).plazoLimitePeriodos()).isEqualTo(52);
        assertThat(ValidadorCredito.parametros(credito("QUINCENAL", 24, null), NEGOCIO).plazoLimitePeriodos()).isEqualTo(24);
        assertThat(ValidadorCredito.parametros(credito("SEMANAL", 104, null), NEGOCIO).plazoLimitePeriodos()).isEqualTo(104);
        // Con el Excel queda el defecto del producto (120 semanas convertidas, nunca menos que las cuotas).
        assertThat(ValidadorCredito.parametros(credito("MENSUAL", 12, null), ReglasNegocio.EXCEL).plazoLimitePeriodos())
                .isEqualTo(27);
    }
}
