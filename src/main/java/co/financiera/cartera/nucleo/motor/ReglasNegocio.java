package co.financiera.cartera.nucleo.motor;

import java.math.BigDecimal;

/**
 * Reglas que dependen de las respuestas de negocio (docs/preguntas-diego.md). {@link #EXCEL}
 * reproduce el Excel de Diego tal cual; cada opción distinta es la alternativa que se le pregunta.
 * Cuando Diego responda el cuestionario, el importador genera la combinación elegida.
 *
 * @param prelacion                orden de aplicación de un pago (P1)
 * @param formulaMora              cómo se causa el interés de mora (P3)
 * @param baseInteres              sobre qué saldo se causa el interés corriente (doble cobro con la mora)
 * @param topeCobranzaPorPago      máximo de gastos de cobranza por pago; {@code null} = sin tope
 * @param baseServicios            sobre qué se calculan FCC y seguro (P6)
 * @param serviciosDespuesDelPlazo si se siguen causando servicios después de la última cuota pactada
 *                                 mientras haya saldo
 */
public record ReglasNegocio(
        OrdenPrelacion prelacion,
        FormulaMora formulaMora,
        BaseInteres baseInteres,
        BigDecimal topeCobranzaPorPago,
        BaseServicios baseServicios,
        boolean serviciosDespuesDelPlazo) {

    public static final ReglasNegocio EXCEL = new ReglasNegocio(OrdenPrelacion.SERVICIOS_ANTES_DE_CAPITAL,
            FormulaMora.VENCIDO_MAS_DIAS, BaseInteres.SALDO_TOTAL, null, BaseServicios.MONTO_INICIAL, false);

    public enum OrdenPrelacion {
        /** Cobranza → mora → interés corriente → servicios → capital (fórmulas de los casos 8 y 9). */
        SERVICIOS_ANTES_DE_CAPITAL,
        /** Cobranza → mora → interés corriente → capital → servicios (texto de la hoja Casos, con servicios al final). */
        CAPITAL_ANTES_DE_SERVICIOS
    }

    public enum FormulaMora {
        /** Capital vencido × tasa semanal + capital de la cuota × ((1 + tasa diaria)^días − 1) (caso 9). */
        VENCIDO_MAS_DIAS,
        /** Solo capital vencido × tasa semanal (caso 8). */
        SOLO_VENCIDO
    }

    public enum BaseInteres {
        /** Saldo total, incluido el capital vencido (el vencido paga interés corriente y mora). */
        SALDO_TOTAL,
        /** Solo el capital vigente: el vencido paga únicamente mora. */
        SALDO_VIGENTE
    }

    public enum BaseServicios {
        /** FCC y seguro fijos sobre el monto financiado inicial. */
        MONTO_INICIAL,
        /** FCC y seguro sobre el saldo de capital de cada semana. */
        SALDO
    }

    /** Claves que el motor ya sabe aplicar; las demás respuestas del cuestionario esperan desarrollo. */
    public static final java.util.Set<String> CLAVES = java.util.Set.of("prelacion", "formulaMora", "baseInteres",
            "topeCobranzaPorPago", "baseServicios", "serviciosDespuesDelPlazo");

    /**
     * Reglas a partir de la configuración que genera herramientas/importar_respuestas.py
     * (config/reglas-negocio.properties). Lo que no venga queda como en el Excel.
     */
    public static ReglasNegocio desde(java.util.Map<String, String> config) {
        ReglasNegocio r = EXCEL;
        String v;
        if ((v = config.get("prelacion")) != null) {
            r = r.conPrelacion(OrdenPrelacion.valueOf(v.trim()));
        }
        if ((v = config.get("formulaMora")) != null) {
            r = r.conFormulaMora(FormulaMora.valueOf(v.trim()));
        }
        if ((v = config.get("baseInteres")) != null) {
            r = r.conBaseInteres(BaseInteres.valueOf(v.trim()));
        }
        if ((v = config.get("topeCobranzaPorPago")) != null && !v.isBlank()) {
            r = r.conTopeCobranza(new BigDecimal(v.trim()));
        }
        if ((v = config.get("baseServicios")) != null) {
            r = r.conBaseServicios(BaseServicios.valueOf(v.trim()));
        }
        if ((v = config.get("serviciosDespuesDelPlazo")) != null) {
            r = r.conServiciosDespuesDelPlazo(Boolean.parseBoolean(v.trim()));
        }
        return r;
    }

    public ReglasNegocio conPrelacion(OrdenPrelacion valor) {
        return new ReglasNegocio(valor, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo);
    }

    public ReglasNegocio conFormulaMora(FormulaMora valor) {
        return new ReglasNegocio(prelacion, valor, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo);
    }

    public ReglasNegocio conBaseInteres(BaseInteres valor) {
        return new ReglasNegocio(prelacion, formulaMora, valor, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo);
    }

    public ReglasNegocio conTopeCobranza(BigDecimal valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, valor, baseServicios, serviciosDespuesDelPlazo);
    }

    public ReglasNegocio conBaseServicios(BaseServicios valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, valor, serviciosDespuesDelPlazo);
    }

    public ReglasNegocio conServiciosDespuesDelPlazo(boolean valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, valor);
    }
}
