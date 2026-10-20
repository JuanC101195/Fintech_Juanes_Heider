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
 * @param baseServicios            sobre qué se calculan fondo de garantías y seguro (P6)
 * @param serviciosDespuesDelPlazo si se siguen causando servicios después de la última cuota pactada
 *                                 mientras haya saldo
 * @param moraAntesDeRecuperacion  si las semanas sin pago antes de una dación o retoma causan mora
 *                                 y servicios como cualquier atraso (P7). Diego (2026-10-16, P-16):
 *                                 sí. El Excel (casos 3 y 4) solo acumula interés corriente.
 * @param aplicacionAbono          cómo se aplica un abono extra o un prepago cuando hay pendientes de
 *                                 semanas anteriores (A4). El Excel solo modela clientes al día.
 * @param interesPrepagoPorDias    si el prepago total cobra el interés corriente de los días desde la
 *                                 última cuota (A6). El Excel prepaga el mismo día de la cuota.
 * @param margenUsura              puntos porcentuales de EA por debajo de la usura a los que se lleva
 *                                 un crédito que la supera (T2): 1 = 100 pb = 0,01 de EA
 */
public record ReglasNegocio(
        OrdenPrelacion prelacion,
        FormulaMora formulaMora,
        BaseInteres baseInteres,
        BigDecimal topeCobranzaPorPago,
        BaseServicios baseServicios,
        boolean serviciosDespuesDelPlazo,
        boolean moraAntesDeRecuperacion,
        AplicacionAbono aplicacionAbono,
        boolean interesPrepagoPorDias,
        BigDecimal margenUsura) {

    /** Margen bajo la usura acordado con negocio y confirmado por Diego (T2, 2026-10-20): 1 punto de EA. */
    public static final BigDecimal MARGEN_USURA_POR_DEFECTO = BigDecimal.ONE;

    /**
     * Comportamiento del Excel de Diego (versión del 11 de octubre): interés corriente sobre el capital
     * vigente (P-03) y mora y servicios en las semanas sin pago antes de una dación o retoma (P-16). El
     * abono extra va 100 % a capital (en el Excel nadie abona con pendientes) y el prepago se hace el día
     * de la cuota (sin días).
     */
    public static final ReglasNegocio EXCEL = new ReglasNegocio(OrdenPrelacion.SERVICIOS_ANTES_DE_CAPITAL,
            FormulaMora.VENCIDO_MAS_DIAS, BaseInteres.SALDO_VIGENTE, null, BaseServicios.MONTO_INICIAL, false, true,
            AplicacionAbono.SOLO_CAPITAL, false, MARGEN_USURA_POR_DEFECTO);

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
        /** Fondo de garantías y seguro fijos sobre el monto financiado inicial (Diego, P-07). */
        MONTO_INICIAL,
        /** Fondo de garantías y seguro sobre el saldo de capital de cada semana. */
        SALDO
    }

    public enum AplicacionAbono {
        /** Todo el abono va a capital; lo pendiente sigue pendiente (el motor con el Excel, que no tiene el caso). */
        SOLO_CAPITAL,
        /**
         * Diego (A4, 2026-10-20): primero los pendientes de semanas anteriores por la prelación de Inputs ⑥
         * (cobranza → mora → interés corriente → fondo → asistencia → seguro → capital vencido) y el resto a
         * capital vigente. Nunca se adelantan intereses ni servicios de cuotas futuras.
         */
        PRIMERO_PENDIENTES
    }

    /** Claves que el motor ya sabe aplicar; las demás respuestas del cuestionario esperan desarrollo. */
    public static final java.util.Set<String> CLAVES = java.util.Set.of("prelacion", "formulaMora", "baseInteres",
            "topeCobranzaPorPago", "baseServicios", "serviciosDespuesDelPlazo", "moraAntesDeRecuperacion",
            "aplicacionAbono", "interesPrepagoPorDias", "margenUsura");

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
        if ((v = config.get("moraAntesDeRecuperacion")) != null) {
            r = r.conMoraAntesDeRecuperacion(Boolean.parseBoolean(v.trim()));
        }
        if ((v = config.get("aplicacionAbono")) != null) {
            r = r.conAplicacionAbono(AplicacionAbono.valueOf(v.trim()));
        }
        if ((v = config.get("interesPrepagoPorDias")) != null) {
            r = r.conInteresPrepagoPorDias(Boolean.parseBoolean(v.trim()));
        }
        if ((v = config.get("margenUsura")) != null && !v.isBlank()) {
            r = r.conMargenUsura(new BigDecimal(v.trim()));
        }
        return r;
    }

    public ReglasNegocio conPrelacion(OrdenPrelacion valor) {
        return new ReglasNegocio(valor, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura);
    }

    public ReglasNegocio conFormulaMora(FormulaMora valor) {
        return new ReglasNegocio(prelacion, valor, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura);
    }

    public ReglasNegocio conBaseInteres(BaseInteres valor) {
        return new ReglasNegocio(prelacion, formulaMora, valor, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura);
    }

    public ReglasNegocio conTopeCobranza(BigDecimal valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, valor, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura);
    }

    public ReglasNegocio conBaseServicios(BaseServicios valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, valor, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura);
    }

    public ReglasNegocio conServiciosDespuesDelPlazo(boolean valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, valor,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura);
    }

    public ReglasNegocio conMoraAntesDeRecuperacion(boolean valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                valor, aplicacionAbono, interesPrepagoPorDias, margenUsura);
    }

    public ReglasNegocio conAplicacionAbono(AplicacionAbono valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, valor, interesPrepagoPorDias, margenUsura);
    }

    public ReglasNegocio conInteresPrepagoPorDias(boolean valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, valor, margenUsura);
    }

    /** Margen bajo la usura en puntos porcentuales de EA (1 = 100 pb); no puede ser negativo. */
    public ReglasNegocio conMargenUsura(BigDecimal valor) {
        if (valor == null || valor.signum() < 0) {
            throw new IllegalArgumentException("El margen bajo la usura debe ser cero o positivo: " + valor);
        }
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, valor);
    }
}
