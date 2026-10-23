package co.financiera.cartera.nucleo.motor;

import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Un crédito y lo que le pasa periodo a periodo (semana, quincena o mes, según la periodicidad).
 * Los periodos sin instrucción son pagos contractuales a tiempo.
 */
public final class Escenario {

    private final Operacion operacion;
    private final ParametrosProducto parametros;
    private final Map<Integer, List<Instruccion>> instrucciones = new TreeMap<>();
    /** Desde este periodo, todo periodo sin instrucción propia usa esta (pagos tardíos recurrentes, caso 9). */
    private final Map<Integer, Instruccion> porDefectoDesde = new TreeMap<>();
    private ReglasNegocio reglas = ReglasNegocio.EXCEL;
    /** COB-6: si a este crédito se le causan gastos de cobranza; el Excel los causa siempre. */
    private boolean cobranzaActiva = true;
    /**
     * Fechas del crédito (ADR 0002 pasos 7 y 8): días reales de la primera cuota (P5-PRORRATEO), de cada mes para la
     * mora mensual (PPER-5) y de un prepago a mitad de periodo (PPER-7). {@code null} si el escenario no tiene fechas.
     */
    private CalendarioPagos calendario;

    public Escenario(Operacion operacion, ParametrosProducto parametros) {
        this.operacion = operacion;
        this.parametros = parametros;
    }

    /** Reglas de negocio con que corre el motor; por defecto las del Excel. */
    public Escenario conReglas(ReglasNegocio reglas) {
        this.reglas = reglas;
        return this;
    }

    public ReglasNegocio reglas() {
        return reglas;
    }

    /**
     * COB-6 (Diego 2026-10-22): la cobranza se activa o no crédito por crédito (lo decide un responsable; quién,
     * en R6-COBRANZA-ACTIVA). Por defecto activa, que es lo que hace el Excel (caso 9).
     */
    public Escenario conCobranzaActiva(boolean activa) {
        this.cobranzaActiva = activa;
        return this;
    }

    public boolean cobranzaActiva() {
        return cobranzaActiva;
    }

    /**
     * Fechas del crédito (ADR 0002 paso 7): de su calendario sale cuántos días reales tiene la primera cuota, que
     * es lo que usa el prorrateo del interés (P5-PRORRATEO) cuando las reglas lo activan. La periodicidad del
     * calendario debe ser la del producto.
     */
    public Escenario conCalendario(CalendarioPagos calendario) {
        if (calendario.periodicidad() != parametros.periodicidad()) {
            throw new IllegalArgumentException("El calendario es " + calendario.periodicidad().adjetivo()
                    + " y el crédito " + parametros.periodicidad().adjetivo());
        }
        this.calendario = calendario;
        return this;
    }

    /** Días reales de la primera cuota, si el escenario tiene fechas ({@link #conCalendario}). */
    public java.util.OptionalInt diasPrimerPeriodo() {
        return diasDelPeriodo(1);
    }

    /**
     * Días reales del periodo (del vencimiento anterior, o del desembolso, al de esta cuota), si el escenario tiene
     * fechas ({@link #conCalendario}).
     */
    public java.util.OptionalInt diasDelPeriodo(int periodo) {
        return calendario == null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(calendario.diasDelPeriodo(periodo));
    }

    /** Calendario de vencimientos del crédito, si el escenario tiene fechas ({@link #conCalendario}). */
    public java.util.Optional<CalendarioPagos> calendario() {
        return java.util.Optional.ofNullable(calendario);
    }

    public Escenario en(int periodo, Instruccion instruccion) {
        instrucciones.computeIfAbsent(periodo, k -> new ArrayList<>()).add(instruccion);
        return this;
    }

    /** Aplica la instrucción a cada periodo desde {@code desde} hasta {@code hasta} inclusive. */
    public Escenario entre(int desde, int hasta, Instruccion instruccion) {
        for (int p = desde; p <= hasta; p++) {
            en(p, instruccion);
        }
        return this;
    }

    public Escenario desde(int periodo, Instruccion instruccion) {
        porDefectoDesde.put(periodo, instruccion);
        return this;
    }

    public Operacion operacion() {
        return operacion;
    }

    public ParametrosProducto parametros() {
        return parametros;
    }

    public List<Instruccion> instruccionesDe(int periodo) {
        List<Instruccion> propias = instrucciones.get(periodo);
        if (propias != null) {
            return Collections.unmodifiableList(propias);
        }
        Instruccion defecto = null;
        for (Map.Entry<Integer, Instruccion> e : porDefectoDesde.entrySet()) {
            if (e.getKey() <= periodo) {
                defecto = e.getValue();
            }
        }
        return defecto == null ? List.of() : List.of(defecto);
    }
}
