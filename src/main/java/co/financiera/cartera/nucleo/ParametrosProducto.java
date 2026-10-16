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
 * @param fondoGarantiasPorcentajeMensual fondo de garantías: % mensual sobre el monto financiado (0,03)
 * @param asistenciaMensual       valor mensual de la asistencia (40.000)
 * @param seguroPorcentajeMensual seguro de vida: % mensual sobre el monto financiado (0,0025)
 * @param iva                     IVA de servicios gravados: fondo de garantías y asistencia (0,19); el seguro es exento (P-08)
 * @param cobranzaDiaria          gasto de cobranza por día de atraso (3.000)
 * @param plazoLimiteSemanas      semanas máximas que se proyecta un crédito con saldo pendiente
 */
public record ParametrosProducto(
        int plazoMeses,
        int semanasPorAnio,
        int mesesPorAnio,
        BigDecimal tasaEA,
        BigDecimal fondoGarantiasPorcentajeMensual,
        BigDecimal asistenciaMensual,
        BigDecimal seguroPorcentajeMensual,
        BigDecimal iva,
        BigDecimal cobranzaDiaria,
        int plazoLimiteSemanas) {

    /**
     * Valores de la hoja Inputs. El pago inicial ya no es un parámetro del producto sino un dato de
     * cada operación ({@link Operacion#pagoInicial()}, P-09).
     */
    public static ParametrosProducto replicaExcel() {
        return new ParametrosProducto(20, 52, 12, bd("0.8732"), bd("0.03"), bd("40000"), bd("0.0025"), bd("0.19"), bd("3000"),
                120);
    }

    public ParametrosProducto conTasaEA(BigDecimal ea) {
        return new ParametrosProducto(plazoMeses, semanasPorAnio, mesesPorAnio, ea, fondoGarantiasPorcentajeMensual,
                asistenciaMensual, seguroPorcentajeMensual, iva, cobranzaDiaria, plazoLimiteSemanas);
    }

    public ParametrosProducto conPlazoLimite(int semanas) {
        return new ParametrosProducto(plazoMeses, semanasPorAnio, mesesPorAnio, tasaEA, fondoGarantiasPorcentajeMensual,
                asistenciaMensual, seguroPorcentajeMensual, iva, cobranzaDiaria, semanas);
    }

    /** Semanas por mes = 52 / 12 = 4,333… */
    public BigDecimal semanasPorMes() {
        return Calc.dividir(bd(semanasPorAnio), bd(mesesPorAnio));
    }
}
