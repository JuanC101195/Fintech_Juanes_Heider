package co.financiera.cartera.nucleo;

import static co.financiera.cartera.nucleo.Calc.bd;

import java.math.BigDecimal;

/**
 * Parámetros del producto de crédito de moto (hoja Inputs del Excel). Se versionan: cada crédito
 * guarda la versión con que se originó (HT-04) y la periodicidad pactada, que no cambia después del
 * desembolso (Diego, PPER-10).
 *
 * @param numeroCuotas                    cuotas pactadas (Inputs B19: 104 semanales)
 * @param periodicidad                    semanal, quincenal o mensual (Inputs B13; B17 = periodos por año)
 * @param tasaEA                          tasa efectiva anual pactada (la de la línea: 0,8762 en CPPU)
 * @param fondoGarantiasPorcentajeMensual fondo de garantías: % mensual sobre el monto financiado (0,03)
 * @param asistenciaMensual               valor mensual de la asistencia (40.000)
 * @param seguroPorcentajeMensual         seguro de vida: % mensual sobre el monto financiado (0,0025)
 * @param iva                             IVA de servicios gravados: fondo de garantías y asistencia (0,19); el seguro es exento (P-08)
 * @param cobranzaDiaria                  gasto de cobranza por día de atraso (3.000 en Inputs, solo un ejemplo: el
 *                                        valor real está pendiente y puede cambiar en el tiempo, Diego COB-6). Queda
 *                                        fijo en los parámetros con que se origina el crédito; el API lo toma de
 *                                        {@code ReglasNegocio.cobranzaDiaria} para los créditos nuevos
 * @param plazoLimitePeriodos             periodos del crédito (no semanas) que se proyecta un crédito con saldo pendiente
 */
public record ParametrosProducto(
        int numeroCuotas,
        Periodicidad periodicidad,
        BigDecimal tasaEA,
        BigDecimal fondoGarantiasPorcentajeMensual,
        BigDecimal asistenciaMensual,
        BigDecimal seguroPorcentajeMensual,
        BigDecimal iva,
        BigDecimal cobranzaDiaria,
        int plazoLimitePeriodos) {

    /**
     * Horizonte de proyección por defecto de un crédito con saldo pendiente: las 120 semanas del Excel
     * (Inputs C72) convertidas a periodos del crédito con ROUNDDOWN(semanas × ppa / 52), como hace el
     * Excel en C73. Semanal 120, quincenal 55, mensual 27. Diego (PPER-6) fijó que el plazo límite se
     * expresa en periodos del crédito, pero no dio el valor: este es provisional.
     */
    static final int PLAZO_LIMITE_EXCEL_SEMANAS = 120;

    public ParametrosProducto {
        if (periodicidad == null) {
            throw new IllegalArgumentException("La periodicidad del crédito es obligatoria");
        }
    }

    /** Valores de la hoja Inputs: línea CPPU, semanal, 104 cuotas. */
    public static ParametrosProducto replicaExcel() {
        return de(LineaCredito.CPPU, Periodicidad.SEMANAL, 104);
    }

    /**
     * Producto de una línea de crédito con la periodicidad y el número de cuotas que se pacten (Diego,
     * PPER-2: el plazo se pacta en cuotas). Las cuotas no pueden pasar ROUNDDOWN(plazo máximo × ppa / 12),
     * con el plazo máximo de la línea (R4-PLAZO-LINEA: 24 meses en todas): semanal 104, quincenal 48,
     * mensual 24.
     */
    public static ParametrosProducto de(LineaCredito linea, Periodicidad periodicidad, int numeroCuotas) {
        return de(linea, periodicidad, numeroCuotas, linea.plazoMaximoMeses());
    }

    /**
     * Como {@link #de(LineaCredito, Periodicidad, int)} con un tope de plazo en meses que no es el del catálogo
     * de líneas (R4-PLAZO-LINEA: el tope es parametrizable y puede crecer a futuro).
     */
    public static ParametrosProducto de(LineaCredito linea, Periodicidad periodicidad, int numeroCuotas,
            int plazoMaximoMeses) {
        if (plazoMaximoMeses < 1) {
            throw new IllegalArgumentException("El plazo máximo debe ser de al menos 1 mes: " + plazoMaximoMeses);
        }
        int maximo = periodicidad.cuotasEnMeses(plazoMaximoMeses);
        if (numeroCuotas < 1 || numeroCuotas > maximo) {
            throw new IllegalArgumentException("La línea " + linea + " admite de 1 a " + maximo + " "
                    + periodicidad.unidadCuota() + "; se pidieron " + numeroCuotas);
        }
        return new ParametrosProducto(numeroCuotas, periodicidad, linea.tasaEA(), bd("0.03"), bd("40000"),
                bd("0.0025"), bd("0.19"), bd("3000"),
                Math.max(periodicidad.periodosDesdeSemanas(PLAZO_LIMITE_EXCEL_SEMANAS), numeroCuotas));
    }

    /**
     * Producto con el plazo pactado en meses: cuotas = ROUNDDOWN(meses × ppa / 12) (Diego, PPER-3;
     * Inputs B15). 7 meses semanales = 30 cuotas.
     */
    public static ParametrosProducto deMeses(LineaCredito linea, Periodicidad periodicidad, int meses) {
        return de(linea, periodicidad, periodicidad.cuotasEnMeses(meses));
    }

    public ParametrosProducto conTasaEA(BigDecimal ea) {
        return new ParametrosProducto(numeroCuotas, periodicidad, ea, fondoGarantiasPorcentajeMensual,
                asistenciaMensual, seguroPorcentajeMensual, iva, cobranzaDiaria, plazoLimitePeriodos);
    }

    public ParametrosProducto conNumeroCuotas(int cuotas) {
        return new ParametrosProducto(cuotas, periodicidad, tasaEA, fondoGarantiasPorcentajeMensual,
                asistenciaMensual, seguroPorcentajeMensual, iva, cobranzaDiaria, Math.max(plazoLimitePeriodos, cuotas));
    }

    /** COB-6: valor diario de la cobranza vigente cuando se origina el crédito; no puede ser negativo. */
    public ParametrosProducto conCobranzaDiaria(BigDecimal valor) {
        if (valor == null || valor.signum() < 0) {
            throw new IllegalArgumentException("La cobranza diaria debe ser cero o positiva: " + valor);
        }
        return new ParametrosProducto(numeroCuotas, periodicidad, tasaEA, fondoGarantiasPorcentajeMensual,
                asistenciaMensual, seguroPorcentajeMensual, iva, valor, plazoLimitePeriodos);
    }

    public ParametrosProducto conPlazoLimite(int periodos) {
        return new ParametrosProducto(numeroCuotas, periodicidad, tasaEA, fondoGarantiasPorcentajeMensual,
                asistenciaMensual, seguroPorcentajeMensual, iva, cobranzaDiaria, periodos);
    }

    /** Inputs B17: 52, 24 o 12, derivado de la periodicidad. */
    public int periodosPorAnio() {
        return periodicidad.periodosPorAnio();
    }

    /** Siempre 12; se conserva para no cambiar a quienes ya lo usan. */
    public int mesesPorAnio() {
        return Periodicidad.MESES_POR_ANIO;
    }

    /** Periodos por mes = periodos por año / 12 (semanal: 4,333…). */
    public BigDecimal periodosPorMes() {
        return periodicidad.periodosPorMes();
    }
}
