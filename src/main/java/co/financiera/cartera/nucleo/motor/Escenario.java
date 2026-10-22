package co.financiera.cartera.nucleo.motor;

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
