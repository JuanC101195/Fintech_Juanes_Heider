package co.financiera.cartera.api;

import static co.financiera.cartera.api.Decimales.texto;

import co.financiera.cartera.api.Dtos.AsientoDto;
import co.financiera.cartera.api.Dtos.CasoResponse;
import co.financiera.cartera.api.Dtos.CasoResumenDto;
import co.financiera.cartera.api.Dtos.CatalogoResponse;
import co.financiera.cartera.api.Dtos.ClasificacionResponse;
import co.financiera.cartera.api.Dtos.ClasificarRequest;
import co.financiera.cartera.api.Dtos.ClaveDto;
import co.financiera.cartera.api.Dtos.CreditoRequest;
import co.financiera.cartera.api.Dtos.DeudaDto;
import co.financiera.cartera.api.Dtos.EjecucionResponse;
import co.financiera.cartera.api.Dtos.EjecutarRequest;
import co.financiera.cartera.api.Dtos.FilaPlan;
import co.financiera.cartera.api.Dtos.InstruccionDto;
import co.financiera.cartera.api.Dtos.LineaAsientoDto;
import co.financiera.cartera.api.Dtos.LineaDto;
import co.financiera.cartera.api.Dtos.MovimientoDto;
import co.financiera.cartera.api.Dtos.PeriodicidadDto;
import co.financiera.cartera.api.Dtos.ProrrateoDto;
import co.financiera.cartera.api.Dtos.ReglasResponse;
import co.financiera.cartera.api.Dtos.ResumenEjecucion;
import co.financiera.cartera.api.Dtos.ResumenSimulacion;
import co.financiera.cartera.api.Dtos.ServiciosDto;
import co.financiera.cartera.api.Dtos.SimulacionResponse;
import co.financiera.cartera.api.PerfilesReglas.Perfil;
import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.ClasificacionPago;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.Tasas;
import co.financiera.cartera.nucleo.motor.DeudaExtinguida;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Lo que hace el API: arma escenarios desde los DTOs, corre el motor y traduce el resultado. */
@Service
public class ServicioCartera {

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
            new ClaveDto("prorrateoPrimeraCuota",
                    "Si el interés de la primera cuota se prorratea por los días reales entre el desembolso y la primera fecha de pago",
                    "P5-PRORRATEO"),
            new ClaveDto("diasMora",
                    "Días de mora del capital vencido: los nominales del periodo o, en mensual con fechas, los días reales del mes",
                    "PPER-5 / D27"));

    private final PerfilesReglas perfiles;
    private final MotorCartera motor = new MotorCartera();

    public ServicioCartera(PerfilesReglas perfiles) {
        this.perfiles = perfiles;
    }

    // ---------------------------------------------------------------- Catálogo y reglas

    public CatalogoResponse catalogo() {
        List<String> periodicidades = Arrays.stream(Periodicidad.values()).map(Enum::name).toList();
        List<LineaDto> lineas = Arrays.stream(LineaCredito.values())
                .map(l -> new LineaDto(l.name(), NOMBRES_LINEAS.get(l), texto(l.tasaEA()), l.plazoMaximoMeses(), periodicidades))
                .toList();
        List<PeriodicidadDto> pers = Arrays.stream(Periodicidad.values())
                .map(p -> new PeriodicidadDto(p.name(), p.periodosPorAnio(), p.adjetivo(), POR_CONFIRMAR.contains(p),
                        p.diasNominales(), p.unidadCuota(), p.cuotasEnMeses(plazoMaximoCatalogo())))
                .toList();
        Operacion o = Operacion.ejemploExcel();
        ParametrosProducto p = ParametrosProducto.replicaExcel();
        Map<String, String> defecto = new LinkedHashMap<>();
        defecto.put("linea", LineaCredito.CPPU.name());
        defecto.put("periodicidad", p.periodicidad().name());
        defecto.put("cuotas", String.valueOf(p.numeroCuotas()));
        defecto.put("plazoMaximoMeses", String.valueOf(LineaCredito.CPPU.plazoMaximoMeses()));
        defecto.put("valorMoto", texto(o.valorMoto()));
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

    /** El mayor plazo máximo del catálogo de líneas (R4-PLAZO-LINEA: hoy 24 en todas). */
    private static int plazoMaximoCatalogo() {
        return Arrays.stream(LineaCredito.values()).mapToInt(LineaCredito::plazoMaximoMeses).max().orElseThrow();
    }

    private static List<String> nombres(Enum<?>[] valores) {
        return Arrays.stream(valores).map(Enum::name).toList();
    }

    public ReglasResponse reglas() {
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

    // ---------------------------------------------------------------- Simulación

    public SimulacionResponse simular(CreditoRequest c) {
        if (c == null) {
            throw ApiException.datoInvalido("Falta el crédito");
        }
        Perfil perfil = perfil(c.perfilReglas(), Perfil.NEGOCIO);
        ParametrosProducto p = parametros(c, perfiles.reglas(perfil));
        Operacion o = operacion(c);
        Fechas fechas = fechas(c, p.periodicidad());
        Escenario escenario = new Escenario(o, p).conReglas(perfiles.reglas(perfil)).conCobranzaActiva(cobranzaActiva(c));
        if (fechas != null) {
            escenario.conCalendario(fechas.calendario());
        }
        List<Movimiento> movs = motor.ejecutar(escenario);
        List<FilaPlan> plan = new ArrayList<>();
        BigDecimal totalPagado = Calc.CERO;
        BigDecimal totalIntereses = Calc.CERO;
        BigDecimal totalServicios = Calc.CERO;
        for (Movimiento m : movs) {
            if (m.periodo() < 1) {
                continue;
            }
            Servicios s = m.serviciosCausados();
            plan.add(new FilaPlan(m.periodo(), m.mes(), texto(m.saldoInicial()), texto(m.interesCorriente()),
                    texto(m.capitalContractual()), texto(m.cuotaFinanciera()), texto(s.fondoGarantias()),
                    texto(s.asistencia()), texto(s.seguro()), texto(Calc.sumar(m.cuotaFinanciera(), s.total())),
                    texto(m.saldoFinal())));
            totalPagado = Calc.sumar(totalPagado, m.pagoTotalCliente());
            totalIntereses = Calc.sumar(totalIntereses, m.pagoInteresCorriente());
            totalServicios = Calc.sumar(totalServicios, m.pagoServicios());
        }
        Movimiento primero = periodo(movs, 1);
        ProrrateoDto prorrateo = fechas == null ? null : prorrateo(fechas, escenario, primero);
        // Con fechas la cuota del resumen es la regular (periodo 2): la primera puede venir prorrateada (P5-PRORRATEO).
        Movimiento regular = fechas != null && p.numeroCuotas() > 1 ? periodo(movs, 2) : primero;
        List<String> porConfirmar = POR_CONFIRMAR.contains(p.periodicidad())
                ? List.of(p.periodicidad().name() + ": " + p.periodosPorAnio() + " periodos/año por confirmar con Diego")
                : List.of();
        ResumenSimulacion resumen = new ResumenSimulacion(linea(c).name(), perfil.name(), texto(o.montoFinanciado()),
                texto(p.tasaEA()), texto(Tasas.de(p).periodica()), p.numeroCuotas(), texto(regular.cuotaFinanciera()),
                texto(regular.serviciosCausados().total()),
                texto(Calc.sumar(regular.cuotaFinanciera(), regular.serviciosCausados().total())), texto(totalPagado),
                texto(totalIntereses), texto(totalServicios), p.periodicidad().name(), porConfirmar, prorrateo);
        return new SimulacionResponse(resumen, plan);
    }

    // ---------------------------------------------------------------- Escenarios y casos

    public EjecucionResponse ejecutar(EjecutarRequest r) {
        if (r == null || r.credito() == null) {
            throw ApiException.datoInvalido("Falta el crédito", "credito");
        }
        CreditoRequest c = r.credito();
        Perfil perfil = perfil(r.perfilReglas() != null ? r.perfilReglas() : c.perfilReglas(), Perfil.NEGOCIO);
        ParametrosProducto p = parametros(c, perfiles.reglas(perfil));
        Escenario e = new Escenario(operacion(c), p).conReglas(perfiles.reglas(perfil))
                .conCobranzaActiva(cobranzaActiva(c));
        Fechas fechas = fechas(c, p.periodicidad());
        if (fechas != null) {
            e.conCalendario(fechas.calendario());
        }
        int ultimoPeriodo = Math.max(p.plazoLimitePeriodos(), p.numeroCuotas());
        if (r.instrucciones() != null) {
            if (r.instrucciones().size() > MAX_INSTRUCCIONES) {
                throw ApiException.datoInvalido("Una petición admite como máximo " + MAX_INSTRUCCIONES
                        + " instrucciones; llegaron " + r.instrucciones().size(), "instrucciones");
            }
            for (InstruccionDto i : r.instrucciones()) {
                if (i == null || i.periodo() == null || i.periodo() < 1 || i.periodo() > ultimoPeriodo) {
                    throw ApiException.datoInvalido("Cada instrucción necesita un periodo entre 1 y "
                            + ultimoPeriodo + ": " + (i == null ? null : i.periodo()), "periodo");
                }
                validarInstruccion(i);
                e.en(i.periodo(), Instrucciones.aMotor(i));
            }
        }
        return correr(e, perfil, r.incluirAsientos() == null || r.incluirAsientos(), fechas);
    }

    /** Topes de los campos numéricos de una instrucción, antes de convertirla para el motor. */
    static void validarInstruccion(InstruccionDto i) {
        diasAcotados(i.diasAtraso(), "diasAtraso");
        diasAcotados(i.diasDesdeUltimaCuota(), "diasDesdeUltimaCuota");
        if (tiene(i.fraccion())) {
            Decimales.fraccion(i.fraccion(), "fraccion");
        }
        if (tiene(i.valor())) {
            Decimales.dineroObligatorio(i.valor(), "valor", false);
        }
        if (tiene(i.valorMoto())) {
            Decimales.dineroObligatorio(i.valorMoto(), "valorMoto", true);
        }
        if (tiene(i.costoRecuperacion())) {
            Decimales.dineroObligatorio(i.costoRecuperacion(), "costoRecuperacion", false);
        }
        if (tiene(i.usuraEA())) {
            Decimales.tasa(i.usuraEA(), "usuraEA");
        }
        if (tiene(i.nuevaTasaEA())) {
            Decimales.tasa(i.nuevaTasaEA(), "nuevaTasaEA");
        }
    }

    private static boolean tiene(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static void diasAcotados(Integer dias, String campo) {
        if (dias != null && (dias < 0 || dias > MAX_DIAS)) {
            throw ApiException.datoInvalido("El campo " + campo + " debe estar entre 0 y " + MAX_DIAS, campo);
        }
    }

    public List<CasoResumenDto> casos() {
        return CatalogoCasos.CASOS.stream().map(CatalogoCasos.Caso::resumen).toList();
    }

    public CasoResponse caso(int numero, Perfil perfil) {
        return caso(numero, perfil, null, null);
    }

    /**
     * El caso con fechas opcionales (P5-PRORRATEO). Los casos son semanales: el primer vencimiento debe ser martes.
     * Sin fechas es el caso del Excel tal cual.
     */
    public CasoResponse caso(int numero, Perfil perfil, String fechaDesembolso, String primerVencimiento) {
        CatalogoCasos.Caso caso = CatalogoCasos.buscar(numero).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                ApiException.CASO_INEXISTENTE, "No existe el caso " + numero + "; los casos van del 1 al "
                        + CatalogoCasos.CASOS.size(),
                Map.of("numero", numero)));
        ParametrosProducto p = ParametrosProducto.replicaExcel();
        ReglasNegocio reglas = perfiles.reglas(perfil);
        Escenario e = caso.escenario().apply(p, reglas).conReglas(reglas);
        Fechas fechas = fechas(fechaDesembolso, primerVencimiento, p.periodicidad());
        if (fechas != null) {
            e.conCalendario(fechas.calendario());
        }
        EjecucionResponse r = correr(e, perfil, true, fechas);
        List<InstruccionDto> instrucciones = new ArrayList<>();
        for (int periodo = 1; periodo <= r.resumen().periodoFinal(); periodo++) {
            for (Instruccion i : e.instruccionesDe(periodo)) {
                instrucciones.add(Instrucciones.aDto(periodo, i));
            }
        }
        Operacion o = e.operacion();
        CreditoRequest credito = new CreditoRequest(LineaCredito.CPPU.name(), p.periodicidad().name(), p.numeroCuotas(),
                null, texto(o.valorMoto()), texto(o.pagoInicial()), perfil.name(), texto(o.matriculaRunt()),
                texto(o.soat()), texto(o.prenda()), texto(o.gpsPlataforma()), texto(o.gastosComerciales()),
                e.parametros().plazoLimitePeriodos(), e.cobranzaActiva(),
                fechas == null ? null : fechas.desembolso().toString(),
                fechas == null ? null : fechas.primerVencimiento().toString());
        return new CasoResponse(caso.resumen(), perfil.name(), credito, instrucciones, r.resumen(), r.movimientos(),
                r.asientos());
    }

    /** Corre el escenario y lo traduce. Público en el paquete para comparar con el motor directo. */
    EjecucionResponse correr(Escenario e, Perfil perfil, boolean incluirAsientos) {
        return correr(e, perfil, incluirAsientos, null);
    }

    private EjecucionResponse correr(Escenario e, Perfil perfil, boolean incluirAsientos, Fechas fechas) {
        List<Movimiento> movs = motor.ejecutar(e);
        Movimiento ultimo = movs.getLast();
        BigDecimal totalPagado = Calc.CERO;
        int vencidasMax = 0;
        Integer periodoListo = null;
        for (Movimiento m : movs) {
            totalPagado = Calc.sumar(totalPagado, m.pagoTotalCliente());
            vencidasMax = Math.max(vencidasMax, m.cuotasVencidas());
            if (periodoListo == null && m.listoParaRecuperacion()) {
                periodoListo = m.periodo();
            }
        }
        ResumenEjecucion resumen = new ResumenEjecucion(ultimo.periodo(), ultimo.estado().name(), texto(ultimo.saldoFinal()),
                texto(totalPagado), vencidasMax, texto(e.operacion().montoFinanciado()), perfil.name(),
                ultimo.listoParaRecuperacion(), periodoListo,
                fechas == null ? null : prorrateo(fechas, e, periodo(movs, 1)));
        List<AsientoDto> asientos = incluirAsientos
                ? Contabilizador.para(e.reglas()).contabilizar(movs).stream().map(MapeadorDto::asiento).toList()
                : List.of();
        return new EjecucionResponse(resumen, movs.stream().map(MapeadorDto::movimiento).toList(), asientos);
    }

    // ---------------------------------------------------------------- Pagos

    public ClasificacionResponse clasificar(ClasificarRequest r) {
        if (r == null) {
            throw ApiException.datoInvalido("Faltan valorRecibido y valorAdeudado");
        }
        ClasificacionPago c = ClasificacionPago.clasificar(Decimales.dineroObligatorio(r.valorRecibido(), "valorRecibido", false),
                Decimales.dineroObligatorio(r.valorAdeudado(), "valorAdeudado", false));
        return new ClasificacionResponse(texto(c.pagoCorriente()), texto(c.abonoExtra()));
    }

    // ---------------------------------------------------------------- Request → núcleo

    static Perfil perfil(String valor, Perfil porDefecto) {
        return Enumeraciones.leer(Perfil.class, valor, porDefecto, "perfilReglas");
    }

    private static LineaCredito linea(CreditoRequest c) {
        return Enumeraciones.leer(LineaCredito.class, c.linea(), LineaCredito.CPPU, "linea");
    }

    /** Máximo de instrucciones por petición. */
    static final int MAX_INSTRUCCIONES = 500;
    /** Máximo de días de atraso (o desde la última cuota) de una instrucción: 10 años. */
    static final int MAX_DIAS = 3650;
    /** El doble de las cuotas máximas del catálogo (hoy 2 × 104 semanas). */
    static final int MAX_PLAZO_LIMITE_PERIODOS = 2 * Arrays.stream(Periodicidad.values())
            .mapToInt(per -> per.cuotasEnMeses(plazoMaximoCatalogo())).max().orElseThrow();
    private static final LocalDate FECHA_MINIMA = LocalDate.of(2000, 1, 1);
    private static final LocalDate FECHA_MAXIMA = LocalDate.of(2100, 12, 31);

    /**
     * Parámetros del crédito del request. Si el request no trae {@code plazoLimitePeriodos} y el perfil fija
     * {@code plazoLimiteMeses} (R4-PLAZO-LIMITE: 24 meses en NEGOCIO), el plazo límite son esos meses en
     * periodos del crédito; con EXCEL queda el del producto (120 semanas convertidas).
     */
    static ParametrosProducto parametros(CreditoRequest c, ReglasNegocio reglas) {
        LineaCredito linea = linea(c);
        Periodicidad per = Enumeraciones.leer(Periodicidad.class, c.periodicidad(), Periodicidad.SEMANAL, "periodicidad");
        int maxMeses = linea.plazoMaximoMeses();
        int maxCuotas = per.cuotasEnMeses(maxMeses);
        if (c.cuotas() == null && c.meses() == null) {
            throw ApiException.datoInvalido("Mande cuotas o meses", "cuotas");
        }
        Integer cuotasMeses = null;
        if (c.meses() != null) {
            if (c.meses() < 1) {
                throw ApiException.datoInvalido("Los meses deben ser al menos 1", "meses");
            }
            if (c.meses() > maxMeses) {
                throw plazoMaximo(maxMeses, maxCuotas, per, "Plazo de " + c.meses() + " meses");
            }
            cuotasMeses = per.cuotasEnMeses(c.meses());
            if (cuotasMeses < 1) {
                throw ApiException.datoInvalido(c.meses() + " meses no alcanzan para una cuota " + per.adjetivo(), "meses");
            }
        }
        if (c.cuotas() != null) {
            if (c.cuotas() < 1) {
                throw ApiException.datoInvalido("Las cuotas deben ser al menos 1", "cuotas");
            }
            if (c.cuotas() > maxCuotas) {
                throw plazoMaximo(maxMeses, maxCuotas, per, c.cuotas() + " " + per.unidadCuota());
            }
            if (cuotasMeses != null && !cuotasMeses.equals(c.cuotas())) {
                throw ApiException.datoInvalido(c.meses() + " meses son " + cuotasMeses + " " + per.unidadCuota()
                        + ", no " + c.cuotas() + "; mande solo cuotas o solo meses", "cuotas");
            }
        }
        int n = c.cuotas() != null ? c.cuotas() : cuotasMeses;
        ParametrosProducto p = ParametrosProducto.de(linea, per, n);
        if (reglas.cobranzaDiaria() != null) {
            // COB-6: el valor diario vigente de la cobranza queda fijo en los parámetros del crédito nuevo.
            p = p.conCobranzaDiaria(reglas.cobranzaDiaria());
        }
        if (c.plazoLimitePeriodos() != null) {
            // Puede ser menor que las cuotas: el caso 8 proyecta 100 semanas de un crédito de 104.
            if (c.plazoLimitePeriodos() < 1) {
                throw ApiException.datoInvalido("El plazo límite debe ser al menos 1 periodo", "plazoLimitePeriodos");
            }
            if (c.plazoLimitePeriodos() > MAX_PLAZO_LIMITE_PERIODOS) {
                throw ApiException.datoInvalido("El plazo límite no puede superar " + MAX_PLAZO_LIMITE_PERIODOS
                        + " periodos (el doble de las cuotas máximas del catálogo)", "plazoLimitePeriodos");
            }
            p = p.conPlazoLimite(c.plazoLimitePeriodos());
        } else {
            java.util.OptionalInt limite = reglas.plazoLimitePeriodos(per, n);
            if (limite.isPresent()) {
                p = p.conPlazoLimite(limite.getAsInt());
            }
        }
        return p;
    }

    private static ApiException plazoMaximo(int maxMeses, int maxCuotas, Periodicidad per, String pedido) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiException.PLAZO_MAXIMO,
                pedido + " supera el máximo de " + maxMeses + " meses (" + maxCuotas + " " + per.unidadCuota() + ")",
                Map.of("maximoMeses", maxMeses, "maximoCuotas", maxCuotas, "periodicidad", per.name()));
    }

    /** Fechas del crédito validadas y su calendario (ADR 0002 paso 7). */
    record Fechas(LocalDate desembolso, LocalDate primerVencimiento, CalendarioPagos calendario) {
    }

    private static final String FECHA_DESEMBOLSO = "fechaDesembolso";
    private static final String PRIMER_VENCIMIENTO = "primerVencimiento";

    static Fechas fechas(CreditoRequest c, Periodicidad per) {
        return fechas(c.fechaDesembolso(), c.primerVencimiento(), per);
    }

    /**
     * P5-PRORRATEO: valida las fechas opcionales del crédito contra la periodicidad y arma su {@link CalendarioPagos}.
     * Sin ninguna de las dos devuelve {@code null} (el comportamiento de siempre, sin fechas). Van juntas: con una sola
     * es un 400. El primer vencimiento cae martes en semanal (D28), el 5 o el 20 en quincenal (D27) y el 5 en mensual
     * (D35), y es posterior al desembolso. No hay tope a los días del primer periodo: Diego no lo ha fijado.
     */
    static Fechas fechas(String fechaDesembolso, String primerVencimiento, Periodicidad per) {
        boolean sinDesembolso = fechaDesembolso == null || fechaDesembolso.isBlank();
        boolean sinVencimiento = primerVencimiento == null || primerVencimiento.isBlank();
        if (sinDesembolso && sinVencimiento) {
            return null;
        }
        if (sinDesembolso || sinVencimiento) {
            String falta = sinDesembolso ? FECHA_DESEMBOLSO : PRIMER_VENCIMIENTO;
            throw ApiException.datoInvalido("Las fechas van juntas: falta " + falta
                    + " (mande fechaDesembolso y primerVencimiento, o ninguna de las dos)", falta);
        }
        LocalDate desembolso = fecha(fechaDesembolso, FECHA_DESEMBOLSO);
        LocalDate vencimiento = fecha(primerVencimiento, PRIMER_VENCIMIENTO);
        if (!vencimiento.isAfter(desembolso)) {
            throw ApiException.datoInvalido("El primer vencimiento (" + vencimiento + ") debe ser posterior a la fecha de "
                    + "desembolso (" + desembolso + ")", PRIMER_VENCIMIENTO);
        }
        CalendarioPagos calendario = switch (per) {
            case SEMANAL -> {
                if (!DayOfWeek.TUESDAY.equals(vencimiento.getDayOfWeek())) {
                    throw ApiException.datoInvalido("En un crédito semanal la cuota vence los martes; el " + vencimiento
                            + " es " + vencimiento.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.of("es", "CO")),
                            PRIMER_VENCIMIENTO);
                }
                yield CalendarioPagos.semanal(desembolso, vencimiento);
            }
            case QUINCENAL -> {
                if (vencimiento.getDayOfMonth() != 5 && vencimiento.getDayOfMonth() != 20) {
                    throw ApiException.datoInvalido("En un crédito quincenal la cuota vence el 5 y el 20 de cada mes; el "
                            + vencimiento + " no es ninguno de los dos", PRIMER_VENCIMIENTO);
                }
                yield CalendarioPagos.quincenal(desembolso, vencimiento);
            }
            case MENSUAL -> {
                if (vencimiento.getDayOfMonth() != CalendarioPagos.DIA_PAGO_MENSUAL) {
                    throw ApiException.datoInvalido("En un crédito mensual la cuota vence el día "
                            + CalendarioPagos.DIA_PAGO_MENSUAL + " de cada mes; el " + vencimiento + " no lo es",
                            PRIMER_VENCIMIENTO);
                }
                yield CalendarioPagos.mensual(desembolso, vencimiento);
            }
        };
        return new Fechas(desembolso, vencimiento, calendario);
    }

    private static LocalDate fecha(String valor, String campo) {
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.datoInvalido(campo + " debe ser una fecha con formato yyyy-MM-dd (por ejemplo 2026-11-03)",
                    campo);
        }
        if (fecha.isBefore(FECHA_MINIMA) || fecha.isAfter(FECHA_MAXIMA)) {
            throw ApiException.datoInvalido(campo + " debe estar entre el año 2000 y el 2100", campo);
        }
        return fecha;
    }

    private static Movimiento periodo(List<Movimiento> movs, int periodo) {
        return movs.stream().filter(m -> m.periodo() == periodo).findFirst().orElseThrow();
    }

    /**
     * P5-PRORRATEO visible en la respuesta: el interés que causó el motor en el periodo 1 contra el de un periodo
     * completo (saldo inicial × tasa del periodo, como lo causa el motor sin fechas).
     */
    static ProrrateoDto prorrateo(Fechas fechas, Escenario e, Movimiento primero) {
        boolean aplicado = e.reglas().prorrateoPrimeraCuota();
        BigDecimal causado = Objects.requireNonNull(primero.interesCorriente(), "interesCorriente");
        BigDecimal completo = aplicado ? Calc.multiplicar(primero.saldoInicial(), primero.tasaPeriodica()) : causado;
        return new ProrrateoDto(fechas.desembolso().toString(), fechas.primerVencimiento().toString(),
                fechas.calendario().diasDelPeriodo(1), e.parametros().periodicidad().diasNominales(), aplicado,
                texto(completo), texto(causado), texto(Calc.restar(causado, completo)));
    }

    /** COB-6: la cobranza del crédito está activa salvo que el request diga {@code false}. */
    static boolean cobranzaActiva(CreditoRequest c) {
        return c.cobranzaActiva() == null || c.cobranzaActiva();
    }

    static Operacion operacion(CreditoRequest c) {
        Operacion base = Operacion.ejemploExcel();
        return new Operacion(Decimales.dinero(c.valorMoto(), "valorMoto", base.valorMoto(), true),
                Decimales.dinero(c.matriculaRunt(), "matriculaRunt", base.matriculaRunt(), false),
                Decimales.dinero(c.soat(), "soat", base.soat(), false),
                Decimales.dinero(c.prenda(), "prenda", base.prenda(), false),
                Decimales.dinero(c.gpsPlataforma(), "gpsPlataforma", base.gpsPlataforma(), false),
                Decimales.dinero(c.gastosComerciales(), "gastosComerciales", base.gastosComerciales(), false),
                Decimales.dinero(c.pagoInicial(), "pagoInicial", Calc.CERO, false));
    }

}
