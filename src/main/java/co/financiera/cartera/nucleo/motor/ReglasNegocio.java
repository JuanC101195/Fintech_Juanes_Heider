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
 * @param modalidadUsura           qué se conserva cuando la usura obliga a bajar la tasa. Diego (USU-6,
 *                                 ronda 3, 2026-10-20): siempre la misma cuota y baja el plazo
 *                                 ({@code MISMA_CUOTA}, caso 10). {@link #EXCEL} conserva la fecha final y
 *                                 recalcula la cuota ({@code MISMA_FECHA_FINAL}), como el cambio de tasa
 *                                 del caso 2, que es lo que hacía el motor desde el PR #8.
 * @param costoRecuperacionAlFondo en una dación, si el costo real de la recuperación (grúa, bodegaje,
 *                                 avalúo, trámites, con soporte) se suma al faltante que cubre el fondo de
 *                                 garantías ({@code true}) o lo asume la financiera ({@code false}). Diego
 *                                 (R4-R2, ronda 4) pide descontarlo en dación y retoma; cómo se trata frente
 *                                 al fondo en la dación es pregunta de la ronda 5 (P5-COSTO-DACION). Por
 *                                 ahora va la opción más conservadora, {@code true}: es <b>provisional</b>.
 *                                 En la retoma el costo siempre reduce el excedente que se devuelve.
 * @param cobranzaIngresoNoOperacional si los gastos de cobranza se contabilizan como ingreso no
 *                                 operacional (PUC clase 42) en lugar de la cuenta "Ingresos por cobranza"
 *                                 del Excel (Diego, R4-COBRANZA-CONTABLE). Lo aplica la contabilidad
 *                                 ({@code Contabilizador.para(reglas)}); el motor no lo usa.
 * @param plazoLimiteMeses         plazo límite de recuperación en meses, que se convierte a periodos del
 *                                 crédito con ROUNDDOWN(meses × ppa / 12) (Diego, R4-PLAZO-LIMITE: 24 meses).
 *                                 {@code null} = el que traiga el producto (el Excel: 100 y 120 semanas en
 *                                 los casos 8 y 9). Lo aplica quien crea el crédito (el API, para créditos
 *                                 nuevos y simulaciones, con {@link #plazoLimitePeriodos}), no el motor, para
 *                                 no cambiar los casos del Excel.
 * @param cambioTasaSobreCapitalVigente si, cuando cambia la tasa (CambioTasa o UsuraVigente) con el cliente
 *                                 en mora, la cuota nueva se recalcula solo sobre el capital vigente
 *                                 ({@code true}: lo vencido se sigue cobrando aparte y nunca se capitalizan
 *                                 intereses, prohibido el anatocismo; Diego, R4-T5) o sobre el saldo total con
 *                                 el vencido ({@code false}, el motor desde el PR #8; el Excel solo modela
 *                                 clientes al día, así que da igual en sus casos). Solo afecta la modalidad
 *                                 MISMA_FECHA_FINAL: MISMA_CUOTA ya recalcula el plazo sobre el capital vigente.
 * @param cobranzaDiaria           valor diario de los gastos de cobranza para los créditos nuevos y las
 *                                 simulaciones del API (Diego, COB-6: los 3.000 del Excel son un ejemplo y el
 *                                 valor real puede cambiar en el tiempo). {@code null} = el del producto (3.000,
 *                                 Inputs). Como {@link #plazoLimiteMeses}, lo aplica quien crea el crédito y
 *                                 queda fijo en sus parámetros (la versión del producto con que se originó).
 * @param cuotasVencidasParaRecuperacion cuotas atrasadas (en periodos de la periodicidad pactada) desde las
 *                                 que el crédito queda marcado "listo para recuperación" (dación o retoma) en
 *                                 {@link Movimiento#listoParaRecuperacion()} (Diego, PPER-9: 2 cuotas). El
 *                                 motor NO ejecuta la recuperación sola (pregunta R6-RECUP-AUTO). {@code null}
 *                                 = no se marca (el Excel).
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
        BigDecimal margenUsura,
        Instruccion.ModalidadCambioTasa modalidadUsura,
        boolean costoRecuperacionAlFondo,
        boolean cobranzaIngresoNoOperacional,
        Integer plazoLimiteMeses,
        boolean cambioTasaSobreCapitalVigente,
        BigDecimal cobranzaDiaria,
        Integer cuotasVencidasParaRecuperacion) {

    public ReglasNegocio {
        if (plazoLimiteMeses != null && plazoLimiteMeses < 1) {
            throw new IllegalArgumentException("El plazo límite debe ser de al menos 1 mes: " + plazoLimiteMeses);
        }
        if (cobranzaDiaria != null && cobranzaDiaria.signum() < 0) {
            throw new IllegalArgumentException("La cobranza diaria no puede ser negativa: " + cobranzaDiaria);
        }
        if (cuotasVencidasParaRecuperacion != null && cuotasVencidasParaRecuperacion < 1) {
            throw new IllegalArgumentException("El umbral de recuperación debe ser de al menos 1 cuota: "
                    + cuotasVencidasParaRecuperacion);
        }
    }

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
            AplicacionAbono.SOLO_CAPITAL, false, MARGEN_USURA_POR_DEFECTO,
            Instruccion.ModalidadCambioTasa.MISMA_FECHA_FINAL, true, false, null, false, null, null);

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
        PRIMERO_PENDIENTES,
        /**
         * Diego (R4-CASO13, ronda 4, 2026-10-21): como {@link #PRIMERO_PENDIENTES}, y además el excedente
         * que queda después de pagar todo lo vencido vuelve a pasar por la prelación de 7 conceptos sobre lo
         * causado del periodo corriente; solo el resto va a capital vigente. En un periodo que no se pagó al
         * día solo se causó el seguro (Inputs ⑥, D17): con el abono la cuota corriente queda pagada en su
         * periodo, así que el excedente cubre también su fondo de garantías y su asistencia. Nunca se cobran
         * conceptos de periodos futuros (A4).
         */
        PRELACION_COMPLETA;

        /** Si el abono paga primero lo pendiente (A4): todas menos {@link #SOLO_CAPITAL}. */
        public boolean pagaPendientes() {
            return this != SOLO_CAPITAL;
        }
    }

    /**
     * Periodos del plazo límite según {@link #plazoLimiteMeses} (R4-PLAZO-LIMITE): ROUNDDOWN(meses × ppa /
     * 12), nunca menos que las cuotas pactadas. Vacío si el perfil no lo fija (queda el del producto).
     */
    public java.util.OptionalInt plazoLimitePeriodos(co.financiera.cartera.nucleo.Periodicidad periodicidad,
            int cuotasPactadas) {
        if (plazoLimiteMeses == null) {
            return java.util.OptionalInt.empty();
        }
        return java.util.OptionalInt.of(Math.max(cuotasPactadas, periodicidad.cuotasEnMeses(plazoLimiteMeses)));
    }

    /** Claves que el motor ya sabe aplicar; las demás respuestas del cuestionario esperan desarrollo. */
    public static final java.util.Set<String> CLAVES = java.util.Set.of("prelacion", "formulaMora", "baseInteres",
            "topeCobranzaPorPago", "baseServicios", "serviciosDespuesDelPlazo", "moraAntesDeRecuperacion",
            "aplicacionAbono", "interesPrepagoPorDias", "margenUsura", "modalidadUsura", "costoRecuperacionAlFondo",
            "cobranzaIngresoNoOperacional", "plazoLimiteMeses", "cambioTasaSobreCapitalVigente", "cobranzaDiaria",
            "cuotasVencidasParaRecuperacion");

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
        if ((v = config.get("modalidadUsura")) != null) {
            r = r.conModalidadUsura(Instruccion.ModalidadCambioTasa.valueOf(v.trim()));
        }
        if ((v = config.get("costoRecuperacionAlFondo")) != null) {
            r = r.conCostoRecuperacionAlFondo(Boolean.parseBoolean(v.trim()));
        }
        if ((v = config.get("cobranzaIngresoNoOperacional")) != null) {
            r = r.conCobranzaIngresoNoOperacional(Boolean.parseBoolean(v.trim()));
        }
        if ((v = config.get("plazoLimiteMeses")) != null && !v.isBlank()) {
            r = r.conPlazoLimiteMeses(Integer.valueOf(v.trim()));
        }
        if ((v = config.get("cambioTasaSobreCapitalVigente")) != null) {
            r = r.conCambioTasaSobreCapitalVigente(Boolean.parseBoolean(v.trim()));
        }
        if ((v = config.get("cobranzaDiaria")) != null && !v.isBlank()) {
            r = r.conCobranzaDiaria(new BigDecimal(v.trim()));
        }
        if ((v = config.get("cuotasVencidasParaRecuperacion")) != null && !v.isBlank()) {
            r = r.conCuotasVencidasParaRecuperacion(Integer.valueOf(v.trim()));
        }
        return r;
    }

    public ReglasNegocio conPrelacion(OrdenPrelacion valor) {
        return new ReglasNegocio(valor, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura, modalidadUsura, costoRecuperacionAlFondo,
                cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    public ReglasNegocio conFormulaMora(FormulaMora valor) {
        return new ReglasNegocio(prelacion, valor, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura, modalidadUsura, costoRecuperacionAlFondo,
                cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    public ReglasNegocio conBaseInteres(BaseInteres valor) {
        return new ReglasNegocio(prelacion, formulaMora, valor, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura, modalidadUsura, costoRecuperacionAlFondo,
                cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    public ReglasNegocio conTopeCobranza(BigDecimal valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, valor, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura, modalidadUsura, costoRecuperacionAlFondo,
                cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    public ReglasNegocio conBaseServicios(BaseServicios valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, valor, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura, modalidadUsura, costoRecuperacionAlFondo,
                cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    public ReglasNegocio conServiciosDespuesDelPlazo(boolean valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, valor,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura, modalidadUsura, costoRecuperacionAlFondo,
                cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    public ReglasNegocio conMoraAntesDeRecuperacion(boolean valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                valor, aplicacionAbono, interesPrepagoPorDias, margenUsura, modalidadUsura, costoRecuperacionAlFondo,
                cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    public ReglasNegocio conAplicacionAbono(AplicacionAbono valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, valor, interesPrepagoPorDias, margenUsura, modalidadUsura, costoRecuperacionAlFondo,
                cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    public ReglasNegocio conInteresPrepagoPorDias(boolean valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, valor, margenUsura, modalidadUsura, costoRecuperacionAlFondo,
                cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    /** Margen bajo la usura en puntos porcentuales de EA (1 = 100 pb); no puede ser negativo. */
    public ReglasNegocio conMargenUsura(BigDecimal valor) {
        if (valor == null || valor.signum() < 0) {
            throw new IllegalArgumentException("El margen bajo la usura debe ser cero o positivo: " + valor);
        }
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, valor, modalidadUsura, costoRecuperacionAlFondo,
                cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    /** Qué se conserva cuando la usura obliga a bajar la tasa (USU-6): la cuota o la fecha final. */
    public ReglasNegocio conModalidadUsura(Instruccion.ModalidadCambioTasa valor) {
        java.util.Objects.requireNonNull(valor, "modalidadUsura");
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura, valor, costoRecuperacionAlFondo,
                cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    /** R4-R2: en la dación, si el costo de la recuperación se suma al faltante que cubre el fondo (provisional). */
    public ReglasNegocio conCostoRecuperacionAlFondo(boolean valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura, modalidadUsura, valor,
                cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    /** R4-COBRANZA-CONTABLE: gastos de cobranza como ingreso no operacional (PUC clase 42). */
    public ReglasNegocio conCobranzaIngresoNoOperacional(boolean valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura, modalidadUsura,
                costoRecuperacionAlFondo, valor, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    /** R4-PLAZO-LIMITE: plazo límite de recuperación en meses; {@code null} = el del producto. */
    public ReglasNegocio conPlazoLimiteMeses(Integer valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura, modalidadUsura,
                costoRecuperacionAlFondo, cobranzaIngresoNoOperacional, valor, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, cuotasVencidasParaRecuperacion);
    }

    /** R4-T5: con el cliente en mora, la cuota nueva de un cambio de tasa va solo sobre el capital vigente. */
    public ReglasNegocio conCambioTasaSobreCapitalVigente(boolean valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura, modalidadUsura,
                costoRecuperacionAlFondo, cobranzaIngresoNoOperacional, plazoLimiteMeses, valor, cobranzaDiaria,
                cuotasVencidasParaRecuperacion);
    }

    /** COB-6: valor diario de la cobranza para los créditos nuevos; {@code null} = el del producto. */
    public ReglasNegocio conCobranzaDiaria(BigDecimal valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura, modalidadUsura,
                costoRecuperacionAlFondo, cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente, valor,
                cuotasVencidasParaRecuperacion);
    }

    /** PPER-9: cuotas atrasadas desde las que el crédito queda listo para recuperación; {@code null} = no se marca. */
    public ReglasNegocio conCuotasVencidasParaRecuperacion(Integer valor) {
        return new ReglasNegocio(prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios, serviciosDespuesDelPlazo,
                moraAntesDeRecuperacion, aplicacionAbono, interesPrepagoPorDias, margenUsura, modalidadUsura,
                costoRecuperacionAlFondo, cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                cobranzaDiaria, valor);
    }

    /** PPER-9: si con {@code cuotasVencidas} el crédito llega al umbral de recuperación. */
    public boolean llegaAlUmbralDeRecuperacion(int cuotasVencidas) {
        return cuotasVencidasParaRecuperacion != null && cuotasVencidas >= cuotasVencidasParaRecuperacion;
    }
}
