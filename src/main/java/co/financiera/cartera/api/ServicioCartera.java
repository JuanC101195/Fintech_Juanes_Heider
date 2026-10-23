package co.financiera.cartera.api;

import static co.financiera.cartera.api.Decimales.texto;

import co.financiera.cartera.api.Dtos.AsientoDto;
import co.financiera.cartera.api.Dtos.CasoResponse;
import co.financiera.cartera.api.Dtos.CasoResumenDto;
import co.financiera.cartera.api.Dtos.CatalogoResponse;
import co.financiera.cartera.api.Dtos.ClasificacionResponse;
import co.financiera.cartera.api.Dtos.ClasificarRequest;
import co.financiera.cartera.api.Dtos.CreditoRequest;
import co.financiera.cartera.api.Dtos.EjecucionResponse;
import co.financiera.cartera.api.Dtos.EjecutarRequest;
import co.financiera.cartera.api.Dtos.FilaPlan;
import co.financiera.cartera.api.Dtos.InstruccionDto;
import co.financiera.cartera.api.Dtos.ProrrateoDto;
import co.financiera.cartera.api.Dtos.ReglasResponse;
import co.financiera.cartera.api.Dtos.ResumenEjecucion;
import co.financiera.cartera.api.Dtos.ResumenSimulacion;
import co.financiera.cartera.api.Dtos.SimulacionResponse;
import co.financiera.cartera.api.PerfilesReglas.Perfil;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.ClasificacionPago;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.Tasas;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Lo que hace el API: arma escenarios desde los DTOs, corre el motor y traduce el resultado. */
@Service
public class ServicioCartera {

    private final PerfilesReglas perfiles;
    private final CatalogoServicio catalogo;
    private final MotorCartera motor = new MotorCartera();

    public ServicioCartera(PerfilesReglas perfiles) {
        this.perfiles = perfiles;
        this.catalogo = new CatalogoServicio(perfiles);
    }

    public CatalogoResponse catalogo() {
        return catalogo.catalogo();
    }

    public ReglasResponse reglas() {
        return catalogo.reglas();
    }

    // ---------------------------------------------------------------- Simulación

    public SimulacionResponse simular(CreditoRequest c) {
        if (c == null) {
            throw ApiException.datoInvalido("Falta el crédito");
        }
        Perfil perfil = perfil(c.perfilReglas(), Perfil.NEGOCIO);
        ParametrosProducto p = ValidadorCredito.parametros(c, perfiles.reglas(perfil));
        Operacion o = ValidadorCredito.operacion(c);
        FechasCredito fechas = FechasCredito.de(c, p.periodicidad());
        Escenario escenario = new Escenario(o, p).conReglas(perfiles.reglas(perfil)).conCobranzaActiva(ValidadorCredito.cobranzaActiva(c));
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
        ProrrateoDto prorrateo = fechas == null ? null : fechas.prorrateo(escenario, primero);
        // Con fechas la cuota del resumen es la regular (periodo 2): la primera puede venir prorrateada (P5-PRORRATEO).
        Movimiento regular = fechas != null && p.numeroCuotas() > 1 ? periodo(movs, 2) : primero;
        List<String> porConfirmar = CatalogoServicio.POR_CONFIRMAR.contains(p.periodicidad())
                ? List.of(p.periodicidad().name() + ": " + p.periodosPorAnio() + " periodos/año por confirmar con Diego")
                : List.of();
        ResumenSimulacion resumen = new ResumenSimulacion(ValidadorCredito.linea(c).name(), perfil.name(), texto(o.montoFinanciado()),
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
        ParametrosProducto p = ValidadorCredito.parametros(c, perfiles.reglas(perfil));
        Escenario e = new Escenario(ValidadorCredito.operacion(c), p).conReglas(perfiles.reglas(perfil))
                .conCobranzaActiva(ValidadorCredito.cobranzaActiva(c));
        FechasCredito fechas = FechasCredito.de(c, p.periodicidad());
        if (fechas != null) {
            e.conCalendario(fechas.calendario());
        }
        int ultimoPeriodo = Math.max(p.plazoLimitePeriodos(), p.numeroCuotas());
        ValidadorInstrucciones.cargar(e, r.instrucciones(), ultimoPeriodo);
        return correr(e, perfil, r.incluirAsientos() == null || r.incluirAsientos(), fechas);
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
        FechasCredito fechas = FechasCredito.de(fechaDesembolso, primerVencimiento, p.periodicidad());
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

    private EjecucionResponse correr(Escenario e, Perfil perfil, boolean incluirAsientos, FechasCredito fechas) {
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
                fechas == null ? null : fechas.prorrateo(e, periodo(movs, 1)));
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

    private static Movimiento periodo(List<Movimiento> movs, int periodo) {
        return movs.stream().filter(m -> m.periodo() == periodo).findFirst().orElseThrow();
    }
}
