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
import co.financiera.cartera.api.Dtos.ReglasResponse;
import co.financiera.cartera.api.Dtos.ResumenEjecucion;
import co.financiera.cartera.api.Dtos.ResumenSimulacion;
import co.financiera.cartera.api.Dtos.ServiciosDto;
import co.financiera.cartera.api.Dtos.SimulacionResponse;
import co.financiera.cartera.api.PerfilesReglas.Perfil;
import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.Calc;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                    "En la dación, si el costo real de la recuperación se suma al faltante que cubre el fondo (provisional)",
                    "R4-R2 / P5-COSTO-DACION"),
            new ClaveDto("cobranzaIngresoNoOperacional",
                    "Si los gastos de cobranza se contabilizan como ingreso no operacional (PUC clase 42)",
                    "R4-COBRANZA-CONTABLE"),
            new ClaveDto("plazoLimiteMeses",
                    "Plazo límite de recuperación en meses para créditos nuevos y simulaciones; vacío = el del producto",
                    "R4-PLAZO-LIMITE"),
            new ClaveDto("cambioTasaSobreCapitalVigente",
                    "Con el cliente en mora, si la cuota nueva de un cambio de tasa va solo sobre el capital vigente", "R4-T5"),
            new ClaveDto("cobranzaDiaria",
                    "Valor diario de la cobranza para créditos nuevos y simulaciones; vacío = el del producto (3.000)",
                    "COB-6"),
            new ClaveDto("cuotasVencidasParaRecuperacion",
                    "Cuotas atrasadas desde las que el crédito queda listo para recuperación; vacío = no se marca",
                    "PPER-9"));

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
            throw ApiException.validacion("Falta el crédito");
        }
        Perfil perfil = perfil(c.perfilReglas(), Perfil.NEGOCIO);
        ParametrosProducto p = parametros(c, perfiles.reglas(perfil));
        Operacion o = operacion(c);
        List<Movimiento> movs = motor.ejecutar(new Escenario(o, p).conReglas(perfiles.reglas(perfil))
                .conCobranzaActiva(cobranzaActiva(c)));
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
        Movimiento primero = movs.stream().filter(m -> m.periodo() == 1).findFirst().orElseThrow();
        List<String> porConfirmar = POR_CONFIRMAR.contains(p.periodicidad())
                ? List.of(p.periodicidad().name() + ": " + p.periodosPorAnio() + " periodos/año por confirmar con Diego")
                : List.of();
        ResumenSimulacion resumen = new ResumenSimulacion(linea(c).name(), perfil.name(), texto(o.montoFinanciado()),
                texto(p.tasaEA()), texto(Tasas.de(p).periodica()), p.numeroCuotas(), texto(primero.cuotaFinanciera()),
                texto(primero.serviciosCausados().total()),
                texto(Calc.sumar(primero.cuotaFinanciera(), primero.serviciosCausados().total())), texto(totalPagado),
                texto(totalIntereses), texto(totalServicios), p.periodicidad().name(), porConfirmar);
        return new SimulacionResponse(resumen, plan);
    }

    // ---------------------------------------------------------------- Escenarios y casos

    public EjecucionResponse ejecutar(EjecutarRequest r) {
        if (r == null || r.credito() == null) {
            throw ApiException.validacion("Falta el crédito", "credito");
        }
        CreditoRequest c = r.credito();
        Perfil perfil = perfil(r.perfilReglas() != null ? r.perfilReglas() : c.perfilReglas(), Perfil.NEGOCIO);
        ParametrosProducto p = parametros(c, perfiles.reglas(perfil));
        Escenario e = new Escenario(operacion(c), p).conReglas(perfiles.reglas(perfil))
                .conCobranzaActiva(cobranzaActiva(c));
        int ultimoPeriodo = Math.max(p.plazoLimitePeriodos(), p.numeroCuotas());
        if (r.instrucciones() != null) {
            for (InstruccionDto i : r.instrucciones()) {
                if (i == null || i.periodo() == null || i.periodo() < 1 || i.periodo() > ultimoPeriodo) {
                    throw ApiException.validacion("Cada instrucción necesita un periodo entre 1 y "
                            + ultimoPeriodo + ": " + (i == null ? null : i.periodo()), "periodo");
                }
                e.en(i.periodo(), Instrucciones.aMotor(i));
            }
        }
        return correr(e, perfil, r.incluirAsientos() == null || r.incluirAsientos());
    }

    public List<CasoResumenDto> casos() {
        return CatalogoCasos.CASOS.stream().map(CatalogoCasos.Caso::resumen).toList();
    }

    public CasoResponse caso(int numero, Perfil perfil) {
        CatalogoCasos.Caso caso = CatalogoCasos.buscar(numero).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                ApiException.CASO_INEXISTENTE, "No existe el caso " + numero + "; los casos van del 1 al "
                        + CatalogoCasos.CASOS.size(),
                Map.of("numero", numero)));
        ParametrosProducto p = ParametrosProducto.replicaExcel();
        ReglasNegocio reglas = perfiles.reglas(perfil);
        Escenario e = caso.escenario().apply(p, reglas).conReglas(reglas);
        EjecucionResponse r = correr(e, perfil, true);
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
                e.parametros().plazoLimitePeriodos(), e.cobranzaActiva());
        return new CasoResponse(caso.resumen(), perfil.name(), credito, instrucciones, r.resumen(), r.movimientos(),
                r.asientos());
    }

    /** Corre el escenario y lo traduce. Público en el paquete para comparar con el motor directo. */
    EjecucionResponse correr(Escenario e, Perfil perfil, boolean incluirAsientos) {
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
                ultimo.listoParaRecuperacion(), periodoListo);
        List<AsientoDto> asientos = incluirAsientos
                ? Contabilizador.para(e.reglas()).contabilizar(movs).stream().map(ServicioCartera::asiento).toList()
                : List.of();
        return new EjecucionResponse(resumen, movs.stream().map(ServicioCartera::movimiento).toList(), asientos);
    }

    // ---------------------------------------------------------------- Pagos

    public ClasificacionResponse clasificar(ClasificarRequest r) {
        if (r == null) {
            throw ApiException.validacion("Faltan valorRecibido y valorAdeudado");
        }
        ClasificacionPago c = ClasificacionPago.clasificar(Decimales.obligatorio(r.valorRecibido(), "valorRecibido"),
                Decimales.obligatorio(r.valorAdeudado(), "valorAdeudado"));
        return new ClasificacionResponse(texto(c.pagoCorriente()), texto(c.abonoExtra()));
    }

    // ---------------------------------------------------------------- Request → núcleo

    static Perfil perfil(String valor, Perfil porDefecto) {
        return Enumeraciones.leer(Perfil.class, valor, porDefecto, "perfilReglas");
    }

    private static LineaCredito linea(CreditoRequest c) {
        return Enumeraciones.leer(LineaCredito.class, c.linea(), LineaCredito.CPPU, "linea");
    }

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
            throw ApiException.validacion("Mande cuotas o meses", "cuotas");
        }
        Integer cuotasMeses = null;
        if (c.meses() != null) {
            if (c.meses() < 1) {
                throw ApiException.validacion("Los meses deben ser al menos 1", "meses");
            }
            if (c.meses() > maxMeses) {
                throw plazoMaximo(maxMeses, maxCuotas, per, "Plazo de " + c.meses() + " meses");
            }
            cuotasMeses = per.cuotasEnMeses(c.meses());
            if (cuotasMeses < 1) {
                throw ApiException.validacion(c.meses() + " meses no alcanzan para una cuota " + per.adjetivo(), "meses");
            }
        }
        if (c.cuotas() != null) {
            if (c.cuotas() < 1) {
                throw ApiException.validacion("Las cuotas deben ser al menos 1", "cuotas");
            }
            if (c.cuotas() > maxCuotas) {
                throw plazoMaximo(maxMeses, maxCuotas, per, c.cuotas() + " " + per.unidadCuota());
            }
            if (cuotasMeses != null && !cuotasMeses.equals(c.cuotas())) {
                throw ApiException.validacion(c.meses() + " meses son " + cuotasMeses + " " + per.unidadCuota()
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
                throw ApiException.validacion("El plazo límite debe ser al menos 1 periodo", "plazoLimitePeriodos");
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

    /** COB-6: la cobranza del crédito está activa salvo que el request diga {@code false}. */
    static boolean cobranzaActiva(CreditoRequest c) {
        return c.cobranzaActiva() == null || c.cobranzaActiva();
    }

    static Operacion operacion(CreditoRequest c) {
        Operacion base = Operacion.ejemploExcel();
        return new Operacion(Decimales.leer(c.valorMoto(), "valorMoto", base.valorMoto()),
                Decimales.leer(c.matriculaRunt(), "matriculaRunt", base.matriculaRunt()),
                Decimales.leer(c.soat(), "soat", base.soat()),
                Decimales.leer(c.prenda(), "prenda", base.prenda()),
                Decimales.leer(c.gpsPlataforma(), "gpsPlataforma", base.gpsPlataforma()),
                Decimales.leer(c.gastosComerciales(), "gastosComerciales", base.gastosComerciales()),
                Decimales.leer(c.pagoInicial(), "pagoInicial", Calc.CERO));
    }

    // ---------------------------------------------------------------- Núcleo → DTO

    static ServiciosDto servicios(Servicios s) {
        return s == null ? null
                : new ServiciosDto(texto(s.fondoGarantias()), texto(s.asistencia()), texto(s.seguro()), texto(s.total()));
    }

    static DeudaDto deuda(DeudaExtinguida d) {
        return d == null ? null
                : new DeudaDto(texto(d.cobranza()), texto(d.mora()), texto(d.interesCorriente()), servicios(d.servicios()),
                        texto(d.capital()), texto(d.total()));
    }

    static MovimientoDto movimiento(Movimiento m) {
        return new MovimientoDto(m.periodo(), m.mes(), m.estado() == null ? null : m.estado().name(), m.evento(),
                m.evento(), texto(m.saldoInicial()), texto(m.tasaPeriodica()), texto(m.cuotaFinanciera()),
                texto(m.interesCorriente()), texto(m.interesMora()), texto(m.capitalContractual()),
                texto(m.cobranzaCausada()), servicios(m.serviciosCausados()), texto(m.pagoRecibido()),
                texto(m.pagoCobranza()), texto(m.pagoMora()), texto(m.pagoInteresCorriente()), texto(m.pagoServicios()),
                texto(m.pagoCapital()), texto(m.abonoExtra()), texto(m.prepagoCapital()), texto(m.cxcCobranza()),
                texto(m.cxcMora()), texto(m.cxcInteresCorriente()), texto(m.cxcServicios()), texto(m.capitalVencido()),
                texto(m.capitalVigente()), texto(m.saldoFinal()), m.cuotasVencidas(), texto(m.deudaAExtinguir()),
                texto(m.inventario()), texto(m.cxcFondoGarantias()), texto(m.cxpDeudor()), servicios(m.pagoTerceros()),
                servicios(m.pagosServicios()), servicios(m.cxcServiciosPorConcepto()), deuda(m.extinguido()),
                texto(m.capitalVencidoInicial()), deuda(m.pendientesPagadosExtra()), texto(m.interesPrepago()),
                texto(m.costoRecuperacion()), texto(m.costoRecuperacionAsumido()), texto(m.pagoTotalCliente()),
                texto(m.pagoExtraordinario()), m.listoParaRecuperacion());
    }

    static AsientoDto asiento(Asiento a) {
        List<LineaAsientoDto> lineas = a.lineas().stream()
                .map(l -> new LineaAsientoDto(l.cuenta().name(), l.cuenta().nombreExcel(), texto(l.debito()),
                        texto(l.credito()), l.explicacion()))
                .toList();
        BigDecimal debitos = a.totalDebitos();
        BigDecimal creditos = a.totalCreditos();
        // Débitos = créditos al centavo: con DECIMAL128 las sumas difieren en residuos de 1e-28.
        boolean cuadra = alCentavo(debitos).compareTo(alCentavo(creditos)) == 0;
        return new AsientoDto(a.periodo(), a.evento(), lineas, texto(debitos), texto(creditos), cuadra);
    }

    private static BigDecimal alCentavo(BigDecimal valor) {
        return valor.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
