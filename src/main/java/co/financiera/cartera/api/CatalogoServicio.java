package co.financiera.cartera.api;

import static co.financiera.cartera.api.Decimales.texto;
import static co.financiera.cartera.api.ValidadorCredito.CAMPO_CUOTAS;
import static co.financiera.cartera.api.ValidadorCredito.CAMPO_PERIODICIDAD;
import static co.financiera.cartera.api.ValidadorCredito.CAMPO_VALOR_MOTO;

import co.financiera.cartera.api.Dtos.CatalogoResponse;
import co.financiera.cartera.api.Dtos.ClaveDto;
import co.financiera.cartera.api.Dtos.LineaDto;
import co.financiera.cartera.api.Dtos.PeriodicidadDto;
import co.financiera.cartera.api.Dtos.ReglasResponse;
import co.financiera.cartera.api.PerfilesReglas.Perfil;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.motor.Instruccion;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Catálogo de líneas y periodicidades, y las reglas de negocio vigentes por perfil. */
final class CatalogoServicio {

    /**
     * Periodicidades cuyo número de periodos por año falta confirmar con Diego. Vacío desde la ronda 4: la
     * quincenal son 24 periodos por año (R4-QUIN, 2026-10-21). Los campos {@code porConfirmar} del API se
     * conservan (en {@code false} y vacío) para no romper a quien ya los lee.
     */
    static final Set<Periodicidad> POR_CONFIRMAR = Set.of();

    private static final Map<LineaCredito, String> NOMBRES_LINEAS = Map.of(
            LineaCredito.CPPU, "Crédito popular productivo urbano",
            LineaCredito.CPPR, "Crédito popular productivo rural",
            LineaCredito.BAJO_MONTO, "Consumo de bajo monto",
            LineaCredito.ORDINARIO, "Consumo y ordinario");

    /** Descripción y pregunta de origen de cada clave de {@link ReglasNegocio#CLAVES}. */
    private static final List<ClaveDto> CLAVES = List.of(
            new ClaveDto("prelacion", "Orden de aplicación de un pago", "P1 / D16"),
            new ClaveDto("formulaMora", "Cómo se causa el interés de mora", "P3"),
            new ClaveDto("baseInteres", "Sobre qué saldo se causa el interés corriente", "P-03"),
            new ClaveDto("topeCobranzaPorPago", "Máximo de gastos de cobranza por pago; vacío = sin tope", "PTOPE"),
            new ClaveDto("baseServicios", "Base del fondo de garantías y del seguro", "P-07"),
            new ClaveDto("serviciosDespuesDelPlazo", "Si se causan servicios después de la última cuota pactada", "P6"),
            new ClaveDto("moraAntesDeRecuperacion", "Si las semanas sin pago antes de una dación o retoma causan mora y servicios", "P-16"),
            new ClaveDto("aplicacionAbono", "Cómo se aplica un abono extra o un prepago cuando hay pendientes", "A4"),
            new ClaveDto("interesPrepagoPorDias", "Si el prepago cobra el interés de los días desde la última cuota", "A6"),
            new ClaveDto("margenUsura", "Puntos de EA por debajo de la usura a los que se lleva un crédito que la supera", "T2"),
            new ClaveDto("modalidadUsura", "Qué se conserva cuando la usura obliga a bajar la tasa", "USU-6"),
            new ClaveDto("costoRecuperacionAlFondo",
                    "En la dación, si el costo real de la recuperación se suma al faltante que cubre el fondo",
                    "R4-R2 / P5-COSTO-DACION"),
            new ClaveDto("cobranzaIngresoNoOperacional",
                    "Si los gastos de cobranza se contabilizan como ingreso no operacional (PUC clase 42)",
                    "R4-COBRANZA-CONTABLE"),
            new ClaveDto("plazoLimiteMeses",
                    "Tope del plazo límite de recuperación en meses para créditos nuevos y simulaciones: el límite es min(plazo del crédito, tope); vacío = el del producto",
                    "R4-PLAZO-LIMITE / R6-LIMITE-CORTO"),
            new ClaveDto("cambioTasaSobreCapitalVigente",
                    "Con el cliente en mora, si la cuota nueva de un cambio de tasa va solo sobre el capital vigente", "R4-T5"),
            new ClaveDto("cobranzaDiaria",
                    "Valor diario de la cobranza para créditos nuevos y simulaciones; vacío = el del producto (3.000)",
                    "COB-6"),
            new ClaveDto("cuotasVencidasParaRecuperacion",
                    "Cuotas atrasadas desde las que el crédito queda marcado listo para recuperación (solo se marca y la marca persiste); vacío = no se marca",
                    "PPER-9 / R6-RECUP-AUTO"),
            new ClaveDto("cuotasVencidasParaCobranza",
                    "Cuotas atrasadas de periodos anteriores desde las que se causan gastos de cobranza (2 = desde la tercera cuota vencida); vacío = sin umbral",
                    "R7-COBRANZA-ACTIVA"),
            new ClaveDto("prorrateoPrimeraCuota",
                    "Si el interés de la primera cuota se prorratea por los días reales entre el desembolso y la primera fecha de pago",
                    "P5-PRORRATEO"),
            new ClaveDto("diasMora",
                    "Días de mora del capital vencido: los nominales del periodo o, en mensual con fechas, los días reales del mes",
                    "PPER-5 / D27"),
            new ClaveDto("cobranzaPorPeriodo",
                    "Cobro fijo de cobranza por periodo en mora (no se prorratea por días): se causa una vez en cada periodo que causaría cobranza; vacío = días × valor diario",
                    "R8-COBRANZA-PAGO-TARDIO"),
            new ClaveDto("cuotasVencidasParaSuspenderServicios",
                    "Con la causación de servicios con suspensión (rondas 8 y 9), desde estas cuotas atrasadas solo se causan los seguros (fondo y asistencia suspendidos); vacío = sin suspensión",
                    "R8-PAGO-INCOMPLETO-EN-FECHA"),
            new ClaveDto("redondeoCuotaAlPeso",
                    "Si la cuota exigible del periodo (cuota + servicios) se redondea al peso más cercano; el residuo va al interés corriente del periodo",
                    "R8-REDONDEO-PESO"),
            new ClaveDto("sobranteComoCuentaPorPagar",
                    "Si el pago mayor que lo exigible se recibe completo y el sobrante queda como cuenta por pagar al cliente (en vez de recortarse)",
                    "R8-PAGO-MAYOR-EXIGIBLE"),
            new ClaveDto("umbralesCuentanCuotaEnCurso",
                    "Si los umbrales de cobranza y de suspensión de servicios cuentan también la cuota atrasada del periodo en curso: con 2, la 2.ª cuota atrasada ya cobra la cobranza y suspende fondo y asistencia (lectura A); si no, desde la 3.ª",
                    "R9-COBRO-MOMENTO"),
            new ClaveDto("seguroTodoRiesgoPorcentajeMensual",
                    "Seguro todo riesgo (se suma al seguro de vida): % mensual sobre el monto financiado inicial, periodizado como el seguro de vida y con IVA encima, segmentado en la contabilidad; 0 = no existe",
                    "R9-SEGURO-TODO-RIESGO"),
            new ClaveDto("causacionServicios",
                    "Cómo se causan fondo, asistencia y seguros: solo al día (D17), con suspensión tras las cuotas atrasadas del umbral (rondas 8 y 9) o siempre (el cliente siempre los paga en la cuota; solo la dación o la retoma los suspenden)",
                    "R10-CONTADOR-VENCIDAS / R11-SEGUROS-TRAS-SUSPENSION"),
            new ClaveDto("umbralCocienteCobranza",
                    "La cobranza se causa cuando saldo en mora / valor total de la cuota supera este cociente (se evalúa al iniciar el periodo); con valor no rige el umbral por cuotas vencidas; vacío = umbral por cuotas vencidas",
                    "R10-CONTADOR-VENCIDAS"),
            new ClaveDto("baseMoraCobranza",
                    "Qué entra en el saldo en mora del cociente de cobranza: solo lo que fue cuota (capital, interés y servicios pendientes) o todo lo pendiente (además mora y cobranza); supuesto provisional",
                    "R10-CONTADOR-VENCIDAS / R12-COBRANZA-SALDO-MORA"),
            new ClaveDto("comparacionCobranza",
                    "Si el cociente de cobranza debe ser estrictamente mayor que el umbral o basta igualarlo; supuesto provisional",
                    "R10-CONTADOR-VENCIDAS / R12-COBRANZA-SALDO-MORA"));

    private final PerfilesReglas perfiles;

    CatalogoServicio(PerfilesReglas perfiles) {
        this.perfiles = perfiles;
    }

    CatalogoResponse catalogo() {
        List<String> periodicidades = Arrays.stream(Periodicidad.values()).map(Enum::name).toList();
        List<LineaDto> lineas = Arrays.stream(LineaCredito.values())
                .map(l -> new LineaDto(l.name(), NOMBRES_LINEAS.get(l), texto(l.tasaEA()), l.plazoMaximoMeses(), periodicidades))
                .toList();
        List<PeriodicidadDto> pers = Arrays.stream(Periodicidad.values())
                .map(p -> new PeriodicidadDto(p.name(), p.periodosPorAnio(), p.adjetivo(), POR_CONFIRMAR.contains(p),
                        p.diasNominales(), p.unidadCuota(), p.cuotasEnMeses(ValidadorCredito.plazoMaximoCatalogo())))
                .toList();
        Operacion o = Operacion.ejemploExcel();
        ParametrosProducto p = ParametrosProducto.replicaExcel();
        Map<String, String> defecto = new LinkedHashMap<>();
        defecto.put("linea", LineaCredito.CPPU.name());
        defecto.put(CAMPO_PERIODICIDAD, p.periodicidad().name());
        defecto.put(CAMPO_CUOTAS, String.valueOf(p.numeroCuotas()));
        defecto.put("plazoMaximoMeses", String.valueOf(LineaCredito.CPPU.plazoMaximoMeses()));
        defecto.put(CAMPO_VALOR_MOTO, texto(o.valorMoto()));
        defecto.put("matriculaRunt", texto(o.matriculaRunt()));
        defecto.put("soat", texto(o.soat()));
        defecto.put("prenda", texto(o.prenda()));
        defecto.put("alistamiento", texto(o.alistamiento()));
        defecto.put("gpsPlataforma", texto(o.gpsPlataforma()));
        defecto.put("gastosComerciales", texto(o.gastosComerciales()));
        defecto.put("pagoInicial", texto(o.pagoInicial()));
        defecto.put("valorTotal", texto(o.valorTotal()));
        defecto.put("montoFinanciado", texto(o.montoFinanciado()));
        defecto.put("fondoGarantiasPorcentajeMensual", texto(p.fondoGarantiasPorcentajeMensual()));
        defecto.put("asistenciaMensual", texto(p.asistenciaMensual()));
        defecto.put("seguroPorcentajeMensual", texto(p.seguroPorcentajeMensual()));
        defecto.put("iva", texto(p.iva()));
        defecto.put("cobranzaDiaria", texto(p.cobranzaDiaria()));
        defecto.put("perfilReglas", Perfil.NEGOCIO.name());
        return new CatalogoResponse(lineas, pers, Instrucciones.tipos(), nombres(Instruccion.ModalidadAbono.values()),
                nombres(Instruccion.ModalidadCambioTasa.values()), nombres(Instruccion.TipoRecuperacion.values()),
                nombres(Perfil.values()), defecto);
    }

    private static List<String> nombres(Enum<?>[] valores) {
        return Arrays.stream(valores).map(Enum::name).toList();
    }

    ReglasResponse reglas() {
        Map<String, Map<String, String>> mapa = new LinkedHashMap<>();
        for (Perfil perfil : Perfil.values()) {
            mapa.put(perfil.name(), valores(perfiles.reglas(perfil)));
        }
        return new ReglasResponse(mapa, CLAVES, perfiles.archivo());
    }

    private static Map<String, String> valores(ReglasNegocio r) {
        Map<String, String> m = new LinkedHashMap<>();
        for (RecordComponent c : ReglasNegocio.class.getRecordComponents()) {
            Object v;
            try {
                v = c.getAccessor().invoke(r);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            m.put(c.getName(), switch (v) {
                case null -> null;
                case BigDecimal b -> texto(b);
                case Enum<?> e -> e.name();
                default -> String.valueOf(v);
            });
        }
        return m;
    }
}
