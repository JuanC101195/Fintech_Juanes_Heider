package co.financiera.cartera.nucleo;

import static co.financiera.cartera.nucleo.Calc.bd;

import java.math.BigDecimal;

/**
 * Parámetros del producto de crédito de moto (hoja Inputs del Excel). Se versionan: cada crédito
 * guarda la versión con que se originó (HT-04).
 *
 * @param numeroCuotas                    cuotas pactadas (Inputs B19: 104)
 * @param periodosPorAnio                 365 diaria, 52 semanal o 12 mensual (Inputs B17)
 * @param mesesPorAnio                    12
 * @param tasaEA                          tasa efectiva anual pactada (la de la línea: 0,8762 en CPPU)
 * @param fondoGarantiasPorcentajeMensual fondo de garantías: % mensual sobre el monto financiado (0,03)
 * @param asistenciaMensual               valor mensual de la asistencia (40.000)
 * @param seguroPorcentajeMensual         seguro de vida: % mensual sobre el monto financiado (0,0025)
 * @param iva                             IVA de servicios gravados: fondo de garantías y asistencia (0,19); el seguro es exento (P-08)
 * @param cobranzaDiaria                  gasto de cobranza por día de atraso (3.000)
 * @param plazoLimitePeriodos             periodos máximos que se proyecta un crédito con saldo pendiente
 */
public record ParametrosProducto(
        int numeroCuotas,
        int periodosPorAnio,
        int mesesPorAnio,
        BigDecimal tasaEA,
        BigDecimal fondoGarantiasPorcentajeMensual,
        BigDecimal asistenciaMensual,
        BigDecimal seguroPorcentajeMensual,
        BigDecimal iva,
        BigDecimal cobranzaDiaria,
        int plazoLimitePeriodos) {

    /** Valores de la hoja Inputs: línea CPPU, semanal, 104 cuotas. */
    public static ParametrosProducto replicaExcel() {
        return de(LineaCredito.CPPU, Periodicidad.SEMANAL, 104);
    }

    /**
     * Producto de una línea de crédito con la periodicidad y el número de cuotas que se pacten. Las
     * cuotas no pueden pasar el plazo máximo de la línea: ROUNDDOWN(plazo máximo × periodos por mes).
     */
    public static ParametrosProducto de(LineaCredito linea, Periodicidad periodicidad, int numeroCuotas) {
        int maximo = linea.plazoMaximoMeses() * periodicidad.periodosPorAnio() / 12;
        if (numeroCuotas < 1 || numeroCuotas > maximo) {
            throw new IllegalArgumentException("La línea " + linea + " admite de 1 a " + maximo + " "
                    + periodicidad.unidadCuota() + "; se pidieron " + numeroCuotas);
        }
        return new ParametrosProducto(numeroCuotas, periodicidad.periodosPorAnio(), 12, linea.tasaEA(), bd("0.03"), bd("40000"),
                bd("0.0025"), bd("0.19"), bd("3000"), Math.max(120, numeroCuotas));
    }

    public ParametrosProducto conTasaEA(BigDecimal ea) {
        return new ParametrosProducto(numeroCuotas, periodosPorAnio, mesesPorAnio, ea, fondoGarantiasPorcentajeMensual,
                asistenciaMensual, seguroPorcentajeMensual, iva, cobranzaDiaria, plazoLimitePeriodos);
    }

    public ParametrosProducto conNumeroCuotas(int cuotas) {
        return new ParametrosProducto(cuotas, periodosPorAnio, mesesPorAnio, tasaEA, fondoGarantiasPorcentajeMensual,
                asistenciaMensual, seguroPorcentajeMensual, iva, cobranzaDiaria, Math.max(plazoLimitePeriodos, cuotas));
    }

    public ParametrosProducto conPlazoLimite(int periodos) {
        return new ParametrosProducto(numeroCuotas, periodosPorAnio, mesesPorAnio, tasaEA, fondoGarantiasPorcentajeMensual,
                asistenciaMensual, seguroPorcentajeMensual, iva, cobranzaDiaria, periodos);
    }

    /** Periodos por mes = periodos por año / 12 (semanal: 4,333…). */
    public BigDecimal periodosPorMes() {
        return Calc.dividir(bd(periodosPorAnio), bd(mesesPorAnio));
    }
}
