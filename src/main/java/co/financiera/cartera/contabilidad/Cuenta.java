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
    INVENTARIO_MOTOS("Inventario de motos"),
    CXC_FCC("CxC FCC"),
    CXP_DEUDOR("CxP deudor");

    private final String nombreExcel;

    Cuenta(String nombreExcel) {
        this.nombreExcel = nombreExcel;
    }

    /** Nombre tal como aparece en el Excel, para comparar los paneles contables. */
    public String nombreExcel() {
        return nombreExcel;
    }
}
