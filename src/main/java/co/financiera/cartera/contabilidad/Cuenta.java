package co.financiera.cartera.contabilidad;

/**
 * Cuentas que usan los paneles "Movimiento contable clave del evento" del Excel. Los códigos del
 * PUC se asignan cuando contabilidad entregue el plan de cuentas (P9).
 */
public enum Cuenta {
    CAJA("Caja"),
    CARTERA_VIGENTE("Cartera de créditos"),
    CARTERA_VENCIDA("Cartera capital vencida"),
    INGRESOS_INTERESES("Ingresos por intereses"),
    CXC_INTERES_CORRIENTE("CxC interés corriente"),
    CXC_INTERES_MORA("CxC interés mora"),
    INGRESOS_MORA("Ingresos por mora"),
    CXC_GASTOS_COBRANZA("CxC gastos de cobranza"),
    INGRESOS_COBRANZA("Ingresos por cobranza"),
    CXP_SERVICIOS_TERCEROS("CxP servicios a terceros"),
    CXC_SERVICIOS("CxC servicios complementarios"),
    CXC_FONDO_CLIENTE("CxC FCC cliente (IVA incl.)"),
    CXC_ASISTENCIAS("CxC asistencias (IVA incl.)"),
    CXC_SEGUROS("CxC seguros"),
    INVENTARIO_MOTOS("Inventario de motos"),
    CXC_FONDO_GARANTIAS("CxC Fondo de garantías"),
    CXP_DEUDOR("CxP deudor"),
    /**
     * R4-COBRANZA-CONTABLE (Diego, ronda 4, 2026-10-21): los gastos de cobranza son ingreso no operacional
     * (PUC clase 42), para que no se imputen como mayor interés de financiación. La subcuenta exacta la fija
     * contabilidad con el PUC (C2). Solo la usa el perfil de negocio ({@code Contabilizador.para}).
     */
    INGRESOS_NO_OPERACIONALES_COBRANZA("Ingresos no operacionales por gastos de cobranza (PUC 42)"),
    /**
     * R4-R2: costo real de la recuperación con soporte (grúa, bodegaje, avalúo, trámites) que se paga a los
     * proveedores y se descuenta en la dación o la retoma. Provisional hasta que contabilidad defina el PUC.
     */
    CXP_COSTOS_RECUPERACION("CxP costos de recuperación (grúa, bodegaje, avalúo, trámites)"),
    /** R4-R2: la parte del costo de recuperación que asume la financiera (dación sin cargo al fondo). */
    GASTO_COSTOS_RECUPERACION("Gasto por costos de recuperación asumidos"),
    /**
     * R8-PAGO-MAYOR-EXIGIBLE (Diego, ronda 8, 2026-10-28): lo que el cliente pagó por encima de lo exigible y se le
     * debe devolver. PROVISIONAL: la subcuenta del PUC está pendiente con contabilidad (C2/P9).
     */
    CXP_SOBRANTE_CLIENTE("CxP sobrante por devolver al cliente (provisional, PUC pendiente)");

    private final String nombreExcel;

    Cuenta(String nombreExcel) {
        this.nombreExcel = nombreExcel;
    }

    /** Nombre tal como aparece en el Excel, para comparar los paneles contables. */
    public String nombreExcel() {
        return nombreExcel;
    }
}
