package co.financiera.cartera.nucleo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Calendario de vencimientos de un crédito (ADR 0002, decisión 7): función pura que da la fecha de
 * vencimiento de cada periodo, los días de atraso de un pago y los días reales de cada periodo. El
 * motor sigue indexado por número de periodo y recibe los días de atraso como entero; este
 * calendario es el que los calcula a partir de fechas (lo usará el módulo de abonos).
 *
 * <ul>
 *   <li><b>Semanal</b>: todos los martes, sin festivos (CAL-2, CAL-3). La primera fecha la fija la
 *       oferta y debe ser martes.</li>
 *   <li><b>Quincenal</b>: el 5 y el 20 de cada mes (Diego, PPER-1).</li>
 *   <li><b>Mensual</b>: el día 5 de cada mes, fijo para todos (Diego, R4-MENSUAL-DIA, ronda 4,
 *       2026-10-21). La opción del día 1 se retiró. La primera cuota se prorratea según los días entre el
 *       desembolso y el primer 5; cómo, es pregunta de la ronda 5 (P5-PRORRATEO) y no está implementado.</li>
 * </ul>
 *
 * <p>Una cuota es a tiempo hasta el fin del día de vencimiento; el atraso empieza el día siguiente
 * como día 1 (CAL-1). En mensual la mora se cuenta por los días reales del mes (PPER-5): para eso
 * está {@link #diasDelPeriodo(int)}; el motor todavía causa la mora de un periodo sin pago con la
 * tasa periódica (paso 8 del ADR).
 */
public final class CalendarioPagos {

    /** Día de pago de los créditos mensuales: el 5, fijo para todos (Diego, R4-MENSUAL-DIA). */
    public static final int DIA_PAGO_MENSUAL = 5;

    /** Días de pago de la quincenal (PPER-1). */
    private static final int[] DIAS_QUINCENA = {5, 20};

    private final Periodicidad periodicidad;
    private final LocalDate desembolso;
    private final LocalDate primerVencimiento;

    private CalendarioPagos(Periodicidad periodicidad, LocalDate desembolso, LocalDate primerVencimiento) {
        this.periodicidad = periodicidad;
        this.desembolso = Objects.requireNonNull(desembolso, "La fecha de desembolso es obligatoria");
        this.primerVencimiento = Objects.requireNonNull(primerVencimiento, "La primera fecha de pago es obligatoria");
        if (!primerVencimiento.isAfter(desembolso)) {
            throw new IllegalArgumentException("La primera fecha de pago (" + primerVencimiento
                    + ") debe ser posterior al desembolso (" + desembolso + ")");
        }
    }

    /** Semanal: la primera fecha la fija la oferta (CAL-3) y debe ser martes. */
    public static CalendarioPagos semanal(LocalDate desembolso, LocalDate primerMartes) {
        if (primerMartes.getDayOfWeek() != DayOfWeek.TUESDAY) {
            throw new IllegalArgumentException("La cuota semanal vence los martes; " + primerMartes + " es "
                    + primerMartes.getDayOfWeek());
        }
        return new CalendarioPagos(Periodicidad.SEMANAL, desembolso, primerMartes);
    }

    /** Quincenal: la primera fecha debe ser un 5 o un 20. */
    public static CalendarioPagos quincenal(LocalDate desembolso, LocalDate primerVencimiento) {
        indiceQuincena(primerVencimiento);
        return new CalendarioPagos(Periodicidad.QUINCENAL, desembolso, primerVencimiento);
    }

    /** Mensual: vence el día 5 de cada mes (R4-MENSUAL-DIA); la primera fecha debe ser un 5. */
    public static CalendarioPagos mensual(LocalDate desembolso, LocalDate primerVencimiento) {
        Objects.requireNonNull(primerVencimiento, "La primera fecha de pago es obligatoria");
        if (primerVencimiento.getDayOfMonth() != DIA_PAGO_MENSUAL) {
            throw new IllegalArgumentException("La cuota mensual vence el día " + DIA_PAGO_MENSUAL + "; "
                    + primerVencimiento + " no cae ese día");
        }
        return new CalendarioPagos(Periodicidad.MENSUAL, desembolso, primerVencimiento);
    }

    public Periodicidad periodicidad() {
        return periodicidad;
    }

    /** Fecha de vencimiento de la cuota {@code periodo} (1, 2, …). */
    public LocalDate vencimiento(int periodo) {
        if (periodo < 1) {
            throw new IllegalArgumentException("El periodo empieza en 1; se pidió " + periodo);
        }
        int adelante = periodo - 1;
        return switch (periodicidad) {
            case SEMANAL -> primerVencimiento.plusWeeks(adelante);
            case MENSUAL -> primerVencimiento.plusMonths(adelante);
            case QUINCENAL -> {
                int n = indiceQuincena(primerVencimiento) + adelante;
                yield primerVencimiento.withDayOfMonth(1).plusMonths(n / 2L).withDayOfMonth(DIAS_QUINCENA[n % 2]);
            }
        };
    }

    /**
     * Días de atraso de un pago de la cuota {@code periodo} hecho en {@code fechaPago}: 0 si paga hasta
     * el día de vencimiento inclusive; el día siguiente es el día 1 (CAL-1).
     */
    public int diasAtraso(int periodo, LocalDate fechaPago) {
        long dias = ChronoUnit.DAYS.between(vencimiento(periodo), fechaPago);
        return (int) Math.max(0, dias);
    }

    /**
     * Días reales del periodo: desde el vencimiento anterior (o el desembolso, en la primera cuota)
     * hasta el vencimiento de esta cuota. En mensual va de 28 a 31 (PPER-5); en quincenal 15 o el
     * resto del mes (del 20 al 5 siguiente); en semanal siempre 7, salvo la primera cuota.
     */
    public int diasDelPeriodo(int periodo) {
        LocalDate inicio = periodo == 1 ? desembolso : vencimiento(periodo - 1);
        return (int) ChronoUnit.DAYS.between(inicio, vencimiento(periodo));
    }

    private static int indiceQuincena(LocalDate fecha) {
        for (int i = 0; i < DIAS_QUINCENA.length; i++) {
            if (fecha.getDayOfMonth() == DIAS_QUINCENA[i]) {
                return i;
            }
        }
        throw new IllegalArgumentException("La cuota quincenal vence el 5 y el 20; " + fecha + " no es ninguno");
    }
}
