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
 *                                 (R4-R2, ronda 4) pide descontarlo en dación y retoma y confirmó en la ronda 5
 *                                 (P5-COSTO-DACION) que en la dación el costo se suma al faltante que cubre el
 *                                 fondo de garantías ({@code true}). En la retoma el costo siempre reduce el
 *                                 excedente que se devuelve.
 * @param cobranzaIngresoNoOperacional si los gastos de cobranza se contabilizan como ingreso no
 *                                 operacional (PUC clase 42) en lugar de la cuenta "Ingresos por cobranza"
 *                                 del Excel (Diego, R4-COBRANZA-CONTABLE). Lo aplica la contabilidad
 *                                 ({@code Contabilizador.para(reglas)}); el motor no lo usa.
 * @param plazoLimiteMeses         tope del plazo límite de recuperación en meses, que se convierte a periodos del
 *                                 crédito con ROUNDDOWN(meses × ppa / 12) (Diego, R4-PLAZO-LIMITE: 24 meses). El
 *                                 límite de cada crédito es min(plazo propio del crédito, este tope) en periodos
 *                                 (Diego, R6-LIMITE-CORTO: un crédito de 12 meses tiene límite de 12 meses).
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
 *                                 motor NO ejecuta la recuperación: solo marca y una persona decide (Diego,
 *                                 R6-RECUP-AUTO). La marca persiste aunque el cliente se ponga al día, hasta que
 *                                 el crédito termina. {@code null} = no se marca (el Excel).
 * @param cuotasVencidasParaCobranza cuotas atrasadas (en periodos de la periodicidad pactada) que deben haber
 *                                 pasado antes de que se causen gastos de cobranza: con 2, la cobranza empieza en la
 *                                 tercera cuota vencida (Diego, R7-COBRANZA-ACTIVA: regla fija, sin activar crédito por
 *                                 crédito; el director financiero puede exonerar). {@code null} = sin umbral (el Excel).
 * @param prorrateoPrimeraCuota    si el interés corriente de la primera cuota se prorratea por los días reales
 *                                 entre el desembolso y la primera fecha de pago (Diego, P5-PRORRATEO; ADR 0002
 *                                 paso 7): interés = saldo × ((1 + tasa diaria)^días − 1), con los días de
 *                                 {@code CalendarioPagos.diasDelPeriodo(1)} que trae el escenario
 *                                 ({@link Escenario#conCalendario}). Capital y servicios complementarios van
 *                                 completos. Aplica en semanal (primer martes), quincenal (5 y 20) y mensual
 *                                 (día 5). Sin fechas en el escenario no cambia nada. {@code false} en el Excel.
 * @param diasMora                 cuántos días causa de mora el capital vencido en un periodo (Diego, PPER-5 / D27:
 *                                 "en mensual la mora cuenta los días reales del mes"; ADR 0002 paso 8). Con
 *                                 {@link DiasMora#PERIODO_NOMINAL} (el Excel) es vencido × tasa periódica, es decir
 *                                 365/ppa días; con {@link DiasMora#DIAS_CALENDARIO_MENSUAL} un crédito mensual con
 *                                 fechas ({@link Escenario#conCalendario}) causa vencido × ((1 + tasa diaria)^días − 1)
 *                                 con los días reales del periodo (28 a 31). Sin fechas no cambia nada.
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
        Integer cuotasVencidasParaRecuperacion,
        Integer cuotasVencidasParaCobranza,
        boolean prorrateoPrimeraCuota,
        DiasMora diasMora) {

    /** Clave de {@link #diasMora}; se repite en la validación y en {@link #desde}. */
    private static final String DIAS_MORA = "diasMora";
    /** Clave de {@link #modalidadUsura}; se repite en la validación y en {@link #desde}. */
    private static final String MODALIDAD_USURA = "modalidadUsura";

    /**
     * Único punto de validación: todo pasa por aquí (el {@link Constructor}, los métodos {@code conX} y
     * {@link #desde}). Los chequeos van en el orden de los componentes para que, con varios valores inválidos, falle
     * primero el mismo que fallaba cuando cada {@code conX} validaba por su cuenta.
     */
    public ReglasNegocio {
        java.util.Objects.requireNonNull(diasMora, DIAS_MORA);
        java.util.Objects.requireNonNull(modalidadUsura, MODALIDAD_USURA);
        if (margenUsura == null || margenUsura.signum() < 0) {
            throw new IllegalArgumentException("El margen bajo la usura debe ser cero o positivo: " + margenUsura);
        }
        if (plazoLimiteMeses != null && plazoLimiteMeses < 1) {
            throw new IllegalArgumentException("El plazo límite debe ser de al menos 1 mes: " + plazoLimiteMeses);
        }
        if (cobranzaDiaria != null && cobranzaDiaria.signum() < 0) {
            throw new IllegalArgumentException("La cobranza diaria no puede ser negativa: " + cobranzaDiaria);
        }
        if (cuotasVencidasParaCobranza != null && cuotasVencidasParaCobranza < 0) {
            throw new IllegalArgumentException("El umbral de cobranza no puede ser negativo: " + cuotasVencidasParaCobranza);
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
    public static final ReglasNegocio EXCEL = new Constructor()
            .prelacion(OrdenPrelacion.SERVICIOS_ANTES_DE_CAPITAL)
            .formulaMora(FormulaMora.VENCIDO_MAS_DIAS)
            .baseInteres(BaseInteres.SALDO_VIGENTE)
            .topeCobranzaPorPago(null)
            .baseServicios(BaseServicios.MONTO_INICIAL)
            .serviciosDespuesDelPlazo(false)
            .moraAntesDeRecuperacion(true)
            .aplicacionAbono(AplicacionAbono.SOLO_CAPITAL)
            .interesPrepagoPorDias(false)
            .margenUsura(MARGEN_USURA_POR_DEFECTO)
            .modalidadUsura(Instruccion.ModalidadCambioTasa.MISMA_FECHA_FINAL)
            .costoRecuperacionAlFondo(true)
            .cobranzaIngresoNoOperacional(false)
            .plazoLimiteMeses(null)
            .cambioTasaSobreCapitalVigente(false)
            .cobranzaDiaria(null)
            .cuotasVencidasParaRecuperacion(null)
            .cuotasVencidasParaCobranza(null)
            .prorrateoPrimeraCuota(false)
            .diasMora(DiasMora.PERIODO_NOMINAL)
            .construir();

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

    /**
     * Días de mora del capital vencido en un periodo (PPER-5, D27; ADR 0002 paso 8). Diego solo decidió la mensual:
     * la quincenal (13 a 16 días reales) y la semanal (7) siguen con el periodo nominal hasta que lo diga.
     */
    public enum DiasMora {
        /** Vencido × tasa periódica: el periodo dura 365/ppa días (30,42 en mensual), como el Excel. */
        PERIODO_NOMINAL,
        /**
         * En un crédito mensual con fechas, vencido × ((1 + tasa diaria)^días − 1) con los días reales del mes
         * ({@code CalendarioPagos.diasDelPeriodo}: del 5 al 5, de 28 a 31). Quincenal, semanal o sin fechas: nominal.
         */
        DIAS_CALENDARIO_MENSUAL
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
     * Periodos del plazo límite según {@link #plazoLimiteMeses}: min(cuotas pactadas, ROUNDDOWN(meses × ppa /
     * 12)). R4-PLAZO-LIMITE fija el tope en 24 meses y R6-LIMITE-CORTO (Diego) dice que un crédito más corto
     * tiene como límite su propio plazo (12 meses → 12 meses en periodos). Antes era el máximo de los dos: un
     * crédito mensual de 12 cuotas proyectaba 24. Vacío si el perfil no lo fija (queda el del producto).
     */
    public java.util.OptionalInt plazoLimitePeriodos(co.financiera.cartera.nucleo.Periodicidad periodicidad,
            int cuotasPactadas) {
        if (plazoLimiteMeses == null) {
            return java.util.OptionalInt.empty();
        }
        return java.util.OptionalInt.of(Math.min(cuotasPactadas, periodicidad.cuotasEnMeses(plazoLimiteMeses)));
    }

    /**
     * Claves que el motor ya sabe aplicar; las demás respuestas del cuestionario esperan desarrollo. Son los nombres
     * de los componentes del record, en su orden (el mismo de {@code /reglas}), así que una regla nueva entra sola.
     */
    public static final java.util.Set<String> CLAVES = nombresDeComponentes();

    private static java.util.Set<String> nombresDeComponentes() {
        java.util.Set<String> nombres = new java.util.LinkedHashSet<>();
        for (java.lang.reflect.RecordComponent c : ReglasNegocio.class.getRecordComponents()) {
            nombres.add(c.getName());
        }
        return java.util.Collections.unmodifiableSet(nombres);
    }

    /**
     * Reglas a partir de la configuración que genera herramientas/importar_respuestas.py
     * (config/reglas-negocio.properties). Lo que no venga queda como en el Excel. Las claves que el motor no conoce
     * se ignoran (son respuestas que esperan desarrollo); {@link #clavesDesconocidas} las lista y
     * {@link #desdeEstricto} las rechaza.
     */
    public static ReglasNegocio desde(java.util.Map<String, String> config) {
        Constructor c = EXCEL.copia();
        for (Clave clave : TABLA) {
            String valor = config.get(clave.nombre());
            if (valor != null) {
                clave.aplicar().accept(c, valor);
            }
        }
        return c.construir();
    }

    /** Como {@link #desde}, pero una clave que el motor no conoce (p. ej. mal escrita) es un error. */
    public static ReglasNegocio desdeEstricto(java.util.Map<String, String> config) {
        java.util.List<String> desconocidas = clavesDesconocidas(config);
        if (!desconocidas.isEmpty()) {
            throw new IllegalArgumentException("Claves de reglas desconocidas: " + desconocidas
                    + ". Las conocidas son " + CLAVES);
        }
        return desde(config);
    }

    /** Claves de {@code config} que no son reglas del motor, en orden alfabético. */
    public static java.util.List<String> clavesDesconocidas(java.util.Map<String, String> config) {
        return config.keySet().stream().filter(k -> !CLAVES.contains(k)).sorted().toList();
    }

    /** Una clave de la configuración: su nombre y cómo se lee el texto y se aplica al {@link Constructor}. */
    private record Clave(String nombre, java.util.function.BiConsumer<Constructor, String> aplicar) {
    }

    /** Enumeración: {@code valueOf} del texto sin espacios (vacío es un error, como antes). */
    private static <E extends Enum<E>> Clave enumerada(String nombre, Class<E> tipo,
            java.util.function.BiConsumer<Constructor, E> setter) {
        return new Clave(nombre, (c, v) -> setter.accept(c, Enum.valueOf(tipo, v.trim())));
    }

    /** Booleano: {@code Boolean.parseBoolean} (vacío o cualquier otro texto = {@code false}, como antes). */
    private static Clave booleana(String nombre, java.util.function.BiConsumer<Constructor, Boolean> setter) {
        return new Clave(nombre, (c, v) -> setter.accept(c, Boolean.parseBoolean(v.trim())));
    }

    /** Decimal: vacío se ignora (queda el valor del Excel). */
    private static Clave decimal(String nombre, java.util.function.BiConsumer<Constructor, BigDecimal> setter) {
        return new Clave(nombre, (c, v) -> {
            if (!v.isBlank()) {
                setter.accept(c, new BigDecimal(v.trim()));
            }
        });
    }

    /** Entero: vacío se ignora (queda el valor del Excel). */
    private static Clave entera(String nombre, java.util.function.ObjIntConsumer<Constructor> setter) {
        return new Clave(nombre, (c, v) -> {
            if (!v.isBlank()) {
                setter.accept(c, Integer.parseInt(v.trim()));
            }
        });
    }

    /**
     * Cómo se lee cada clave, en el orden de los componentes. Una regla nueva agrega aquí su línea; si falta, la
     * clase no carga ({@link #verificarTabla}) y fallan todas las pruebas con el nombre de la clave.
     */
    private static final java.util.List<Clave> TABLA = java.util.List.of(
            enumerada("prelacion", OrdenPrelacion.class, Constructor::prelacion),
            enumerada("formulaMora", FormulaMora.class, Constructor::formulaMora),
            enumerada("baseInteres", BaseInteres.class, Constructor::baseInteres),
            decimal("topeCobranzaPorPago", Constructor::topeCobranzaPorPago),
            enumerada("baseServicios", BaseServicios.class, Constructor::baseServicios),
            booleana("serviciosDespuesDelPlazo", Constructor::serviciosDespuesDelPlazo),
            booleana("moraAntesDeRecuperacion", Constructor::moraAntesDeRecuperacion),
            enumerada("aplicacionAbono", AplicacionAbono.class, Constructor::aplicacionAbono),
            booleana("interesPrepagoPorDias", Constructor::interesPrepagoPorDias),
            decimal("margenUsura", Constructor::margenUsura),
            enumerada(MODALIDAD_USURA, Instruccion.ModalidadCambioTasa.class, Constructor::modalidadUsura),
            booleana("costoRecuperacionAlFondo", Constructor::costoRecuperacionAlFondo),
            booleana("cobranzaIngresoNoOperacional", Constructor::cobranzaIngresoNoOperacional),
            entera("plazoLimiteMeses", Constructor::plazoLimiteMeses),
            booleana("cambioTasaSobreCapitalVigente", Constructor::cambioTasaSobreCapitalVigente),
            decimal("cobranzaDiaria", Constructor::cobranzaDiaria),
            entera("cuotasVencidasParaRecuperacion", Constructor::cuotasVencidasParaRecuperacion),
            entera("cuotasVencidasParaCobranza", Constructor::cuotasVencidasParaCobranza),
            booleana("prorrateoPrimeraCuota", Constructor::prorrateoPrimeraCuota),
            enumerada(DIAS_MORA, DiasMora.class, Constructor::diasMora));

    static {
        verificarTabla();
    }

    /** La tabla debe tener exactamente las claves de {@link #CLAVES}, en el mismo orden. */
    private static void verificarTabla() {
        java.util.List<String> nombres = TABLA.stream().map(Clave::nombre).toList();
        if (!nombres.equals(java.util.List.copyOf(CLAVES))) {
            throw new IllegalStateException("La tabla de claves de ReglasNegocio no coincide con sus componentes: tabla "
                    + nombres + ", componentes " + CLAVES);
        }
    }

    /** Constructor con todos los valores de estas reglas, para cambiar los que haga falta con su nombre. */
    public Constructor copia() {
        return new Constructor(this);
    }

    public ReglasNegocio conPrelacion(OrdenPrelacion valor) {
        return copia().prelacion(valor).construir();
    }

    public ReglasNegocio conFormulaMora(FormulaMora valor) {
        return copia().formulaMora(valor).construir();
    }

    public ReglasNegocio conBaseInteres(BaseInteres valor) {
        return copia().baseInteres(valor).construir();
    }

    public ReglasNegocio conTopeCobranza(BigDecimal valor) {
        return copia().topeCobranzaPorPago(valor).construir();
    }

    public ReglasNegocio conBaseServicios(BaseServicios valor) {
        return copia().baseServicios(valor).construir();
    }

    public ReglasNegocio conServiciosDespuesDelPlazo(boolean valor) {
        return copia().serviciosDespuesDelPlazo(valor).construir();
    }

    public ReglasNegocio conMoraAntesDeRecuperacion(boolean valor) {
        return copia().moraAntesDeRecuperacion(valor).construir();
    }

    public ReglasNegocio conAplicacionAbono(AplicacionAbono valor) {
        return copia().aplicacionAbono(valor).construir();
    }

    public ReglasNegocio conInteresPrepagoPorDias(boolean valor) {
        return copia().interesPrepagoPorDias(valor).construir();
    }

    /** Margen bajo la usura en puntos porcentuales de EA (1 = 100 pb); no puede ser negativo. */
    public ReglasNegocio conMargenUsura(BigDecimal valor) {
        return copia().margenUsura(valor).construir();
    }

    /** Qué se conserva cuando la usura obliga a bajar la tasa (USU-6): la cuota o la fecha final. */
    public ReglasNegocio conModalidadUsura(Instruccion.ModalidadCambioTasa valor) {
        return copia().modalidadUsura(valor).construir();
    }

    /** R4-R2 / P5-COSTO-DACION: en la dación, si el costo de la recuperación se suma al faltante que cubre el fondo. */
    public ReglasNegocio conCostoRecuperacionAlFondo(boolean valor) {
        return copia().costoRecuperacionAlFondo(valor).construir();
    }

    /** R4-COBRANZA-CONTABLE: gastos de cobranza como ingreso no operacional (PUC clase 42). */
    public ReglasNegocio conCobranzaIngresoNoOperacional(boolean valor) {
        return copia().cobranzaIngresoNoOperacional(valor).construir();
    }

    /** R4-PLAZO-LIMITE: plazo límite de recuperación en meses; {@code null} = el del producto. */
    public ReglasNegocio conPlazoLimiteMeses(Integer valor) {
        return copia().plazoLimiteMeses(valor).construir();
    }

    /** R4-T5: con el cliente en mora, la cuota nueva de un cambio de tasa va solo sobre el capital vigente. */
    public ReglasNegocio conCambioTasaSobreCapitalVigente(boolean valor) {
        return copia().cambioTasaSobreCapitalVigente(valor).construir();
    }

    /** COB-6: valor diario de la cobranza para los créditos nuevos; {@code null} = el del producto. */
    public ReglasNegocio conCobranzaDiaria(BigDecimal valor) {
        return copia().cobranzaDiaria(valor).construir();
    }

    /** PPER-9: cuotas atrasadas desde las que el crédito queda listo para recuperación; {@code null} = no se marca. */
    public ReglasNegocio conCuotasVencidasParaRecuperacion(Integer valor) {
        return copia().cuotasVencidasParaRecuperacion(valor).construir();
    }

    /** R7-COBRANZA-ACTIVA: cuotas atrasadas que deben haber pasado antes de causar cobranza; {@code null} = sin umbral. */
    public ReglasNegocio conCuotasVencidasParaCobranza(Integer valor) {
        return copia().cuotasVencidasParaCobranza(valor).construir();
    }

    /** P5-PRORRATEO: si el interés de la primera cuota se prorratea por los días reales desde el desembolso. */
    public ReglasNegocio conProrrateoPrimeraCuota(boolean valor) {
        return copia().prorrateoPrimeraCuota(valor).construir();
    }

    /** PPER-5 / D27: días de mora del capital vencido (nominales o reales del mes en mensual). */
    public ReglasNegocio conDiasMora(DiasMora valor) {
        return copia().diasMora(valor).construir();
    }

    /**
     * Arma unas reglas por nombre de componente, sin depender del orden de los argumentos (dos booleanos cruzados
     * compilan). Es el único sitio, aparte de la cabecera del record, que lista los componentes en orden.
     */
    public static final class Constructor {

        private OrdenPrelacion prelacion;
        private FormulaMora formulaMora;
        private BaseInteres baseInteres;
        private BigDecimal topeCobranzaPorPago;
        private BaseServicios baseServicios;
        private boolean serviciosDespuesDelPlazo;
        private boolean moraAntesDeRecuperacion;
        private AplicacionAbono aplicacionAbono;
        private boolean interesPrepagoPorDias;
        private BigDecimal margenUsura;
        private Instruccion.ModalidadCambioTasa modalidadUsura;
        private boolean costoRecuperacionAlFondo;
        private boolean cobranzaIngresoNoOperacional;
        private Integer plazoLimiteMeses;
        private boolean cambioTasaSobreCapitalVigente;
        private BigDecimal cobranzaDiaria;
        private Integer cuotasVencidasParaRecuperacion;
        private Integer cuotasVencidasParaCobranza;
        private boolean prorrateoPrimeraCuota;
        private DiasMora diasMora;

        /** Vacío: hay que fijar todo (los booleanos quedan en {@code false}); para partir de algo, {@link #copia()}. */
        public Constructor() {
            // sin valores
        }

        private Constructor(ReglasNegocio r) {
            prelacion = r.prelacion;
            formulaMora = r.formulaMora;
            baseInteres = r.baseInteres;
            topeCobranzaPorPago = r.topeCobranzaPorPago;
            baseServicios = r.baseServicios;
            serviciosDespuesDelPlazo = r.serviciosDespuesDelPlazo;
            moraAntesDeRecuperacion = r.moraAntesDeRecuperacion;
            aplicacionAbono = r.aplicacionAbono;
            interesPrepagoPorDias = r.interesPrepagoPorDias;
            margenUsura = r.margenUsura;
            modalidadUsura = r.modalidadUsura;
            costoRecuperacionAlFondo = r.costoRecuperacionAlFondo;
            cobranzaIngresoNoOperacional = r.cobranzaIngresoNoOperacional;
            plazoLimiteMeses = r.plazoLimiteMeses;
            cambioTasaSobreCapitalVigente = r.cambioTasaSobreCapitalVigente;
            cobranzaDiaria = r.cobranzaDiaria;
            cuotasVencidasParaRecuperacion = r.cuotasVencidasParaRecuperacion;
            cuotasVencidasParaCobranza = r.cuotasVencidasParaCobranza;
            prorrateoPrimeraCuota = r.prorrateoPrimeraCuota;
            diasMora = r.diasMora;
        }

        public Constructor prelacion(OrdenPrelacion valor) {
            prelacion = valor;
            return this;
        }

        public Constructor formulaMora(FormulaMora valor) {
            formulaMora = valor;
            return this;
        }

        public Constructor baseInteres(BaseInteres valor) {
            baseInteres = valor;
            return this;
        }

        public Constructor topeCobranzaPorPago(BigDecimal valor) {
            topeCobranzaPorPago = valor;
            return this;
        }

        public Constructor baseServicios(BaseServicios valor) {
            baseServicios = valor;
            return this;
        }

        public Constructor serviciosDespuesDelPlazo(boolean valor) {
            serviciosDespuesDelPlazo = valor;
            return this;
        }

        public Constructor moraAntesDeRecuperacion(boolean valor) {
            moraAntesDeRecuperacion = valor;
            return this;
        }

        public Constructor aplicacionAbono(AplicacionAbono valor) {
            aplicacionAbono = valor;
            return this;
        }

        public Constructor interesPrepagoPorDias(boolean valor) {
            interesPrepagoPorDias = valor;
            return this;
        }

        public Constructor margenUsura(BigDecimal valor) {
            margenUsura = valor;
            return this;
        }

        public Constructor modalidadUsura(Instruccion.ModalidadCambioTasa valor) {
            modalidadUsura = valor;
            return this;
        }

        public Constructor costoRecuperacionAlFondo(boolean valor) {
            costoRecuperacionAlFondo = valor;
            return this;
        }

        public Constructor cobranzaIngresoNoOperacional(boolean valor) {
            cobranzaIngresoNoOperacional = valor;
            return this;
        }

        public Constructor plazoLimiteMeses(Integer valor) {
            plazoLimiteMeses = valor;
            return this;
        }

        public Constructor cambioTasaSobreCapitalVigente(boolean valor) {
            cambioTasaSobreCapitalVigente = valor;
            return this;
        }

        public Constructor cobranzaDiaria(BigDecimal valor) {
            cobranzaDiaria = valor;
            return this;
        }

        public Constructor cuotasVencidasParaRecuperacion(Integer valor) {
            cuotasVencidasParaRecuperacion = valor;
            return this;
        }

        public Constructor cuotasVencidasParaCobranza(Integer valor) {
            cuotasVencidasParaCobranza = valor;
            return this;
        }

        public Constructor prorrateoPrimeraCuota(boolean valor) {
            prorrateoPrimeraCuota = valor;
            return this;
        }

        public Constructor diasMora(DiasMora valor) {
            diasMora = valor;
            return this;
        }

        public ReglasNegocio construir() {
            return new ReglasNegocio(
                    prelacion, formulaMora, baseInteres, topeCobranzaPorPago, baseServicios,
                    serviciosDespuesDelPlazo, moraAntesDeRecuperacion, aplicacionAbono,
                    interesPrepagoPorDias, margenUsura, modalidadUsura, costoRecuperacionAlFondo,
                    cobranzaIngresoNoOperacional, plazoLimiteMeses, cambioTasaSobreCapitalVigente,
                    cobranzaDiaria, cuotasVencidasParaRecuperacion, cuotasVencidasParaCobranza, prorrateoPrimeraCuota, diasMora);
        }
    }

    /** PPER-9: si con {@code cuotasVencidas} el crédito llega al umbral de recuperación. */
    /** Si, con estas cuotas atrasadas de periodos anteriores, el periodo en curso ya debe causar cobranza. */
    public boolean llegaAlUmbralDeCobranza(int cuotasVencidasPrevias) {
        return cuotasVencidasParaCobranza == null || cuotasVencidasPrevias >= cuotasVencidasParaCobranza;
    }

    public boolean llegaAlUmbralDeRecuperacion(int cuotasVencidas) {
        return cuotasVencidasParaRecuperacion != null && cuotasVencidas >= cuotasVencidasParaRecuperacion;
    }
}
