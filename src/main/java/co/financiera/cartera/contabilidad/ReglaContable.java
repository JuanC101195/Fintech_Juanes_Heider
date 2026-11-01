package co.financiera.cartera.contabilidad;

import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.DeudaExtinguida;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Convierte un movimiento del motor en un asiento. Cada evento del Excel tiene su propia regla
 * (una clase en {@code contabilidad.reglas}) y se registra en {@link Contabilizador#estandar()}.
 */
public interface ReglaContable {

    /** El asiento de este movimiento, o vacío si la regla no aplica. */
    Optional<Asiento> contabilizar(Movimiento movimiento);

    /** Ayuda para reglas que generan varias líneas. */
    static Optional<Asiento> asiento(Movimiento m, List<Asiento.Linea> lineas) {
        List<Asiento.Linea> conValor = lineas.stream()
                .filter(l -> l.debito().signum() != 0 || l.credito().signum() != 0)
                .toList();
        return conValor.isEmpty() ? Optional.empty() : Optional.of(new Asiento(m.periodo(), m.evento(), conValor));
    }

    /**
     * R4-R2 (Diego, ronda 4): costo real de la recuperación con soporte (grúa, bodegaje, avalúo, trámites)
     * descontado en una dación o una retoma. Crédito a la CxP de los proveedores por el costo completo; la
     * parte que asume la financiera (dación sin cargo al fondo) se debita al gasto. Con costo 0 no hay líneas.
     * Provisional: las cuentas las define contabilidad con el PUC.
     */
    static List<Asiento.Linea> costosRecuperacion(Movimiento m) {
        return List.of(
                Asiento.Linea.debito(Cuenta.GASTO_COSTOS_RECUPERACION, m.costoRecuperacionAsumido(),
                        "Costo de recuperación que asume la financiera"),
                Asiento.Linea.credito(Cuenta.CXP_COSTOS_RECUPERACION, m.costoRecuperacion(),
                        "Costo real de la recuperación con soporte (R4-R2)"));
    }

    /**
     * Créditos que extinguen la deuda en una dación o retoma, uno por concepto y en el orden del panel
     * del Excel (casos 3, 4 y 11): capital (prelación 7), interés corriente (3), mora (2), fondo de
     * garantías (4), asistencias (5), seguros (6) y cobranza (1). Los conceptos en cero no generan línea.
     */
    static List<Asiento.Linea> extincion(DeudaExtinguida d) {
        return List.of(
                Asiento.Linea.credito(Cuenta.CARTERA_VIGENTE, d.capital(), "Extinción del capital (prelación 7)"),
                Asiento.Linea.credito(Cuenta.CXC_INTERES_CORRIENTE, d.interesCorriente(),
                        "Extinción de intereses corrientes (prelación 3)"),
                Asiento.Linea.credito(Cuenta.CXC_INTERES_MORA, d.mora(), "Extinción de intereses de mora (prelación 2)"),
                Asiento.Linea.credito(Cuenta.CXC_FONDO_CLIENTE, d.servicios().fondoGarantias(),
                        "Extinción de fondo de garantías (prelación 4)"),
                Asiento.Linea.credito(Cuenta.CXC_ASISTENCIAS, d.servicios().asistencia(),
                        "Extinción de asistencias (prelación 5)"),
                Asiento.Linea.credito(Cuenta.CXC_SEGUROS, d.servicios().seguro(), "Extinción de seguros (prelación 6)"),
                // R9 (seguro riesgo total) con R12-IVA-FONDO-ASISTENCIAS (D78): el seguro riesgo total con su IVA en un
                // solo valor, como los demás servicios (0 sin la regla: no hay línea).
                Asiento.Linea.credito(Cuenta.CXC_SEGUROS, d.servicios().todoRiesgoConIva(),
                        "Extinción del seguro riesgo total, IVA incluido (R9, D78)"),
                Asiento.Linea.credito(Cuenta.CXC_GASTOS_COBRANZA, d.cobranza(), "Extinción de gastos de cobranza (prelación 1)"));
    }

    /**
     * R10-CONTADOR-VENCIDAS (Diego, ronda 10, D72): la extinción de una dación o una retoma cuando la cuenta con terceros
     * solo nace con el recaudo efectivo. La parte de los servicios pendientes que cubre la moto es recaudo efectivo: va
     * completa a {@code terceros} ("Ingresos recibidos para terceros"), el seguro riesgo total con su IVA incluido (un gran
     * total sin segmentar, D78). La parte que no cubre la moto (la cubre el faltante a cargo del fondo de garantías) se
     * extingue contra las cuentas por cobrar de cada servicio, como el Excel; cómo se le paga al tercero lo no recaudado
     * es la pregunta R13-SERVICIOS-CIERRE-ASIENTO. Los demás conceptos se extinguen como en
     * {@link #extincion(DeudaExtinguida)}.
     *
     * <p>Lo que cubrió la moto sale del movimiento: {@code pagosServicios} (lo recaudado en el periodo) menos
     * {@code pagoServicios} (lo que pagó la cuota). La moto paga los servicios por su prelación (fondo, asistencia, seguro,
     * riesgo total, su IVA), la misma de {@link Servicios#pagarHasta}, así que el detalle por concepto se reconstruye igual.
     */
    static List<Asiento.Linea> extincionConRecaudo(Movimiento m, Cuenta terceros) {
        DeudaExtinguida d = m.extinguido();
        BigDecimal cubierto = Calc.noNegativo(Calc.restar(m.pagosServicios().total(), m.pagoServicios()));
        Servicios cubiertos = d.servicios().pagarHasta(cubierto);
        Servicios noCubiertos = d.servicios().menos(cubiertos).noNegativo();
        return List.of(
                Asiento.Linea.credito(Cuenta.CARTERA_VIGENTE, d.capital(), "Extinción del capital (prelación 7)"),
                Asiento.Linea.credito(Cuenta.CXC_INTERES_CORRIENTE, d.interesCorriente(),
                        "Extinción de intereses corrientes (prelación 3)"),
                Asiento.Linea.credito(Cuenta.CXC_INTERES_MORA, d.mora(), "Extinción de intereses de mora (prelación 2)"),
                Asiento.Linea.credito(terceros, cubiertos.total(),
                        "Servicios cubiertos con la moto: recaudo efectivo para terceros (R10, D72)"),
                Asiento.Linea.credito(Cuenta.CXC_FONDO_CLIENTE, noCubiertos.fondoGarantias(),
                        "Extinción de fondo de garantías no cubierto por la moto (prelación 4)"),
                Asiento.Linea.credito(Cuenta.CXC_ASISTENCIAS, noCubiertos.asistencia(),
                        "Extinción de asistencias no cubiertas por la moto (prelación 5)"),
                Asiento.Linea.credito(Cuenta.CXC_SEGUROS, Calc.sumar(noCubiertos.seguro(), noCubiertos.todoRiesgoConIva()),
                        "Extinción de seguros no cubiertos por la moto (prelación 6)"),
                Asiento.Linea.credito(Cuenta.CXC_GASTOS_COBRANZA, d.cobranza(), "Extinción de gastos de cobranza (prelación 1)"));
    }
}
