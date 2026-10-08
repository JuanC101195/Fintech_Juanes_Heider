package co.financiera.cartera.nucleo;

import static co.financiera.cartera.nucleo.Calc.bd;

import java.math.BigDecimal;

/**
 * Parámetros del producto de crédito de moto (hoja Inputs del Excel). Se versionan: cada crédito
 * guarda la versión con que se originó (HT-04).
 *
 * @param plazoMeses              plazo pactado en meses (20 en el Excel)
 * @param semanasPorAnio          52
 * @param mesesPorAnio            12
 * @param tasaEA                  tasa efectiva anual pactada (0,8732)
 * @param porcentajeInicial       cuota inicial sobre el valor total de la operación. Regla de
 *                                negocio: 10 %. El Excel de Diego no tiene inicial, así que sus
 *                                réplicas usan 0.
 * @param fccPorcentajeMensual    fondo de garantías: % mensual sobre el monto financiado (0,03)
 * @param asistenciaMensual       valor mensual de la asistencia (40.000)
 * @param seguroPorcentajeMensual seguro de vida: % mensual sobre el monto financiado (0,0025)
 * @param iva                     IVA de servicios gravados: FCC y asistencia (0,19)
 * @param cobranzaDiaria          gasto de cobranza por día de atraso (3.000)
 * @param plazoLimiteSemanas      semanas máximas que se proyecta un crédito con saldo pendiente
 */
public record ParametrosProducto(
        int plazoMeses,
        int semanasPorAnio,
        int mesesPorAnio,
        BigDecimal tasaEA,
        BigDecimal porcentajeInicial,
        BigDecimal fccPorcentajeMensual,
        BigDecimal asistenciaMensual,
        BigDecimal seguroPorcentajeMensual,
        BigDecimal iva,
        BigDecimal cobranzaDiaria,
        int plazoLimiteSemanas) {

    public static final BigDecimal INICIAL_REGLA_NEGOCIO = bd("0.10");

    /** Valores de la hoja Inputs, con la cuota inicial del 10 % que exige el negocio. */
    public static ParametrosProducto motoEstandar() {
        return new ParametrosProducto(20, 52, 12, bd("0.8732"), INICIAL_REGLA_NEGOCIO, bd("0.03"), bd("40000"),
                bd("0.0025"), bd("0.19"), bd("3000"), 120);
    }

    /** Los mismos parámetros sin cuota inicial, para reproducir el Excel al centavo. */
    public static ParametrosProducto replicaExcel() {
        return motoEstandar().conInicial(Calc.CERO);
    }

    public ParametrosProducto conInicial(BigDecimal porcentaje) {
        return new ParametrosProducto(plazoMeses, semanasPorAnio, mesesPorAnio, tasaEA, porcentaje, fccPorcentajeMensual,
                asistenciaMensual, seguroPorcentajeMensual, iva, cobranzaDiaria, plazoLimiteSemanas);
    }

    public ParametrosProducto conTasaEA(BigDecimal ea) {
        return new ParametrosProducto(plazoMeses, semanasPorAnio, mesesPorAnio, ea, porcentajeInicial, fccPorcentajeMensual,
                asistenciaMensual, seguroPorcentajeMensual, iva, cobranzaDiaria, plazoLimiteSemanas);
    }

    public ParametrosProducto conPlazoLimite(int semanas) {
        return new ParametrosProducto(plazoMeses, semanasPorAnio, mesesPorAnio, tasaEA, porcentajeInicial, fccPorcentajeMensual,
                asistenciaMensual, seguroPorcentajeMensual, iva, cobranzaDiaria, semanas);
    }

    /** Semanas por mes = 52 / 12 = 4,333… */
    public BigDecimal semanasPorMes() {
        return Calc.dividir(bd(semanasPorAnio), bd(mesesPorAnio));
    }
}
