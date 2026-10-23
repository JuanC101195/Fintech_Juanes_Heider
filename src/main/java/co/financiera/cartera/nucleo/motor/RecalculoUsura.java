package co.financiera.cartera.nucleo.motor;

import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Usura;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Recálculo masivo por usura (D31 / USU-5, D33): cuando se publica una usura nueva, todos los créditos vigentes de la
 * línea se recalculan desde la cuota SIGUIENTE, reanudando cada uno desde su última {@link Instantanea} en lugar de
 * correrlo desde el desembolso.
 *
 * <p>Equivale exactamente a una {@link Instruccion.UsuraVigente} en el periodo de la instantánea: la usura queda
 * publicada y rige desde la cuota siguiente (T1); si la tasa vigente la supera baja a usura − margen
 * ({@code ReglasNegocio.margenUsura}, 1 punto en NEGOCIO, T2) y la tasa nunca sube (T4). Lo que se conserva lo decide
 * {@code ReglasNegocio.modalidadUsura}: con NEGOCIO, MISMA_CUOTA (USU-6: la cuota se mantiene y baja el plazo). Un
 * crédito ya fuera de plazo no explota (H6): la tasa nueva rige el interés y la mora, sin recalcular cuota ni plazo.
 *
 * <p>Una usura por línea de crédito: el lote recibe la usura de cada línea y cada crédito dice su línea (la instantánea
 * guarda la tasa, no la línea). Un crédito que falla no tumba el lote: queda {@link Estado#RECHAZADO} con el motivo.
 *
 * <p>Qué devuelve por crédito ({@link Resultado}): la instantánea con la usura publicada (lo que se guarda: el cierre
 * diario la reanuda con el pago real de la cuota siguiente), y la proyección de {@code periodosAProyectar} periodos con
 * las instrucciones que se den (por defecto pagos a tiempo): sus movimientos y la tasa, la cuota y el número de cuotas
 * antes y después.
 */
public final class RecalculoUsura {

    /** Un crédito vigente del lote: su línea, su última instantánea y, si se quiere, las instrucciones siguientes. */
    public record Credito(String id, LineaCredito linea, Instantanea instantanea,
            IntFunction<List<Instruccion>> instrucciones) {

        public Credito {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(linea, "linea");
            Objects.requireNonNull(instantanea, "instantanea");
            Objects.requireNonNull(instrucciones, "instrucciones");
        }

        /** Proyección con pagos contractuales a tiempo. */
        public Credito(String id, LineaCredito linea, Instantanea instantanea) {
            this(id, linea, instantanea, periodo -> List.of());
        }
    }

    public enum Estado {
        /** La tasa superaba la usura y bajó a usura − margen. */
        AJUSTADO,
        /** La tasa no supera la usura: nada cambia (la tasa nunca sube). */
        SIN_CAMBIO,
        /** No se publicó usura para la línea del crédito. */
        SIN_USURA_PARA_LA_LINEA,
        /** El crédito ya terminó (cancelado, cerrado por evento o en el plazo límite). */
        TERMINADO,
        /** El crédito no se pudo recalcular; ver {@link Resultado#motivo()}. */
        RECHAZADO
    }

    /**
     * Resultado por crédito. Lo "anterior" sale de la instantánea; lo "nuevo", del estado al cierre del último periodo
     * proyectado (con un periodo, la cuota siguiente, que es donde rige la usura).
     *
     * @param instantaneaConUsura la instantánea del crédito con la usura publicada; {@code null} si no aplica
     * @param movimientosNuevos   movimientos de los periodos proyectados (vacío si no aplica)
     */
    public record Resultado(String id, Estado estado, BigDecimal tasaAnteriorEA, BigDecimal tasaNuevaEA,
            BigDecimal cuotaAnterior, BigDecimal cuotaNueva, int cuotasAnteriores, int cuotasNuevas,
            Instantanea instantaneaConUsura, List<Movimiento> movimientosNuevos, String motivo) {
    }

    private final MotorCartera motor = new MotorCartera();
    private final ReglasNegocio reglas;
    private final int periodosAProyectar;

    /**
     * @param reglas             perfil con que se corrieron las instantáneas (el margen y la modalidad salen de aquí)
     * @param periodosAProyectar periodos a proyectar después de la instantánea: 1 = la cuota siguiente,
     *                           {@link Integer#MAX_VALUE} = hasta que el crédito termine
     */
    public RecalculoUsura(ReglasNegocio reglas, int periodosAProyectar) {
        this.reglas = Objects.requireNonNull(reglas, "reglas");
        if (periodosAProyectar < 1) {
            throw new IllegalArgumentException("Hay que proyectar al menos la cuota siguiente: " + periodosAProyectar);
        }
        this.periodosAProyectar = periodosAProyectar;
    }

    /** Recalcula el lote y devuelve un resultado por crédito, en el mismo orden. */
    public List<Resultado> recalcular(Iterable<Credito> creditos, Map<LineaCredito, BigDecimal> usuraPorLinea) {
        List<Resultado> resultados = new ArrayList<>();
        recalcular(creditos, usuraPorLinea, resultados::add);
        return resultados;
    }

    /** Como {@link #recalcular(Iterable, Map)}, entregando cada resultado sin acumular el lote. */
    public void recalcular(Iterable<Credito> creditos, Map<LineaCredito, BigDecimal> usuraPorLinea,
            Consumer<Resultado> consumidor) {
        Map<LineaCredito, BigDecimal> usuras = new EnumMap<>(LineaCredito.class);
        usuras.putAll(usuraPorLinea);
        for (Credito c : creditos) {
            consumidor.accept(recalcular(c, usuras.get(c.linea())));
        }
    }

    /** Un crédito con la usura de su línea ({@code null} si no se publicó). */
    public Resultado recalcular(Credito c, BigDecimal usuraEA) {
        Instantanea antes = c.instantanea();
        BigDecimal tasaAntes = antes.tasas().efectivaAnual();
        if (antes.terminado()) {
            return sinCambio(c, Estado.TERMINADO, "El crédito ya terminó en el periodo " + antes.periodo());
        }
        if (usuraEA == null) {
            return sinCambio(c, Estado.SIN_USURA_PARA_LA_LINEA, "No hay usura publicada para la línea " + c.linea());
        }
        Instantanea conUsura;
        Instantanea despues;
        List<Movimiento> nuevos = new ArrayList<>();
        try {
            conUsura = antes.conUsuraPublicada(usuraEA);
            int hasta = periodosAProyectar >= Integer.MAX_VALUE - antes.periodo() ? Integer.MAX_VALUE
                    : antes.periodo() + periodosAProyectar;
            despues = motor.ejecutarDesde(conUsura, reglas, c.instrucciones(), hasta, nuevos::add);
        } catch (RuntimeException e) {
            return sinCambio(c, Estado.RECHAZADO, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        // La tasa que rige desde la cuota siguiente es la de la regla pura (la misma que aplica el motor al leer la
        // usura); la cuota y el número de cuotas, los del estado al cierre del último periodo proyectado.
        BigDecimal tasaNueva = Usura.tasaAjustada(tasaAntes, usuraEA, reglas.margenUsura());
        Estado estado = tasaNueva.compareTo(tasaAntes) == 0 ? Estado.SIN_CAMBIO : Estado.AJUSTADO;
        return new Resultado(c.id(), estado, tasaAntes, tasaNueva, antes.cuota(), despues.cuota(), antes.cuotasVigentes(),
                despues.cuotasVigentes(), conUsura, List.copyOf(nuevos), null);
    }

    private static Resultado sinCambio(Credito c, Estado estado, String motivo) {
        Instantanea i = c.instantanea();
        BigDecimal tasa = i.tasas().efectivaAnual();
        return new Resultado(c.id(), estado, tasa, tasa, i.cuota(), i.cuota(), i.cuotasVigentes(), i.cuotasVigentes(),
                null, List.of(), motivo);
    }
}
