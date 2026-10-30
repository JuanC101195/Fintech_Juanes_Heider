package co.financiera.cartera.contabilidad;

import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.DeudaExtinguida;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.ArrayList;
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
                // R9-SEGURO-TODO-RIESGO: el seguro todo riesgo y su IVA, por separado (0 sin la regla: no hay línea).
                Asiento.Linea.credito(Cuenta.CXC_SEGUROS, d.servicios().seguroTodoRiesgo(),
                        "Extinción del seguro todo riesgo, sin IVA (R9)"),
                Asiento.Linea.credito(Cuenta.CXC_SEGUROS, d.servicios().ivaSeguroTodoRiesgo(),
                        "Extinción del IVA del seguro todo riesgo (R9)"),
                Asiento.Linea.credito(Cuenta.CXC_GASTOS_COBRANZA, d.cobranza(), "Extinción de gastos de cobranza (prelación 1)"));
    }

    /**
     * R9-SEGURO-TODO-RIESGO (Diego, ronda 9): de los servicios recaudados {@code total} que van a "CxP servicios a
     * terceros", la parte que NO es seguro todo riesgo ni su IVA. Esas dos partes van en líneas aparte
     * ({@link #seguroTodoRiesgo}). Sin seguro todo riesgo devuelve {@code total} tal cual (las cifras del Excel no
     * cambian).
     */
    static BigDecimal serviciosSinTodoRiesgo(BigDecimal total, Servicios detalle) {
        Servicios tr = todoRiesgoDe(total, detalle);
        if (tr.todoRiesgoConIva().signum() == 0) {
            return total;
        }
        return Calc.restar(Calc.restar(total, tr.seguroTodoRiesgo()), tr.ivaSeguroTodoRiesgo());
    }

    /**
     * R9-SEGURO-TODO-RIESGO: las líneas del seguro todo riesgo recaudado, segmentado en el seguro (CxP a terceros, el
     * tratamiento del seguro de vida: se recauda por cuenta de la aseguradora) y su IVA ({@link Cuenta#IVA_POR_PAGAR}).
     * {@code detalle} es el reparto por concepto del movimiento; lo que se toma nunca pasa de {@code total}.
     */
    static List<Asiento.Linea> seguroTodoRiesgo(BigDecimal total, Servicios detalle) {
        Servicios tr = todoRiesgoDe(total, detalle);
        return List.of(
                Asiento.Linea.credito(Cuenta.CXP_SERVICIOS_TERCEROS, tr.seguroTodoRiesgo(),
                        "Seguro todo riesgo recaudado, sin IVA (R9-SEGURO-TODO-RIESGO)"),
                Asiento.Linea.credito(Cuenta.IVA_POR_PAGAR, tr.ivaSeguroTodoRiesgo(),
                        "IVA del seguro todo riesgo (R9-SEGURO-TODO-RIESGO)"));
    }

    /** Las dos listas de líneas, una tras otra. */
    static List<Asiento.Linea> con(List<Asiento.Linea> primeras, List<Asiento.Linea> despues) {
        List<Asiento.Linea> todas = new ArrayList<>(primeras);
        todas.addAll(despues);
        return todas;
    }

    private static Servicios todoRiesgoDe(BigDecimal total, Servicios detalle) {
        BigDecimal disponible = Calc.noNegativo(total);
        BigDecimal seguro = Calc.min(disponible, detalle.seguroTodoRiesgo());
        BigDecimal iva = Calc.min(Calc.restar(disponible, seguro), detalle.ivaSeguroTodoRiesgo());
        return new Servicios(Calc.CERO, Calc.CERO, Calc.CERO, seguro, iva);
    }
}
