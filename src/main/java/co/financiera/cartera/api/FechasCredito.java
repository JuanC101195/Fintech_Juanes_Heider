package co.financiera.cartera.api;

import static co.financiera.cartera.api.Decimales.texto;

import co.financiera.cartera.api.Dtos.CreditoRequest;
import co.financiera.cartera.api.Dtos.ProrrateoDto;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;

/** Fechas del crédito validadas y su calendario (ADR 0002 paso 7), con el prorrateo de la primera cuota. */
record FechasCredito(LocalDate desembolso, LocalDate primerVencimiento, CalendarioPagos calendario) {

    private static final String FECHA_DESEMBOLSO = "fechaDesembolso";
    private static final String PRIMER_VENCIMIENTO = "primerVencimiento";
    private static final LocalDate FECHA_MINIMA = LocalDate.of(2000, 1, 1);
    private static final LocalDate FECHA_MAXIMA = LocalDate.of(2100, 12, 31);

    static FechasCredito de(CreditoRequest c, Periodicidad per) {
        return de(c.fechaDesembolso(), c.primerVencimiento(), per);
    }

    /**
     * P5-PRORRATEO: valida las fechas opcionales del crédito contra la periodicidad y arma su {@link CalendarioPagos}.
     * Sin ninguna de las dos devuelve {@code null} (el comportamiento de siempre, sin fechas). Van juntas: con una sola
     * es un 400. El primer vencimiento cae martes en semanal (D28), el 5 o el 20 en quincenal (D27) y el 5 en mensual
     * (D35), y es posterior al desembolso. No hay tope a los días del primer periodo: Diego no lo ha fijado.
     */
    static FechasCredito de(String fechaDesembolso, String primerVencimiento, Periodicidad per) {
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
        return new FechasCredito(desembolso, vencimiento, calendario(per, desembolso, vencimiento));
    }

    private static CalendarioPagos calendario(Periodicidad per, LocalDate desembolso, LocalDate vencimiento) {
        return switch (per) {
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

    /**
     * P5-PRORRATEO visible en la respuesta: el interés que causó el motor en el periodo 1 contra el de un periodo
     * completo (saldo inicial × tasa del periodo, como lo causa el motor sin fechas).
     */
    ProrrateoDto prorrateo(Escenario e, Movimiento primero) {
        boolean aplicado = e.reglas().prorrateoPrimeraCuota();
        BigDecimal causado = Objects.requireNonNull(primero.interesCorriente(), "interesCorriente");
        BigDecimal completo = aplicado ? Calc.multiplicar(primero.saldoInicial(), primero.tasaPeriodica()) : causado;
        return new ProrrateoDto(desembolso.toString(), primerVencimiento.toString(), calendario.diasDelPeriodo(1),
                e.parametros().periodicidad().diasNominales(), aplicado, texto(completo), texto(causado),
                texto(Calc.restar(causado, completo)));
    }
}
