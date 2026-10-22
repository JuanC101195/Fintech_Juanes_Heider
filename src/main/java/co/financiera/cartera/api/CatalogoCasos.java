package co.financiera.cartera.api;

import co.financiera.cartera.api.Dtos.CasoResumenDto;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Los 12 casos del Excel de Diego (versión del 11 de octubre) y el caso 13 del equipo, todos sobre la
 * réplica del Excel: CPPU, semanal, 104 cuotas, operación de ejemplo de la hoja Inputs.
 */
final class CatalogoCasos {

    record Caso(CasoResumenDto resumen, Function<ParametrosProducto, Escenario> escenario) {
    }

    private static final String EXCEL = "Excel 11-oct, hoja ";

    static final List<Caso> CASOS = List.of(
            caso(1, "Operación normal", "Paga las 104 cuotas semanales a tiempo.", EXCEL + "Caso1_Normal",
                    EscenariosExcel::caso1),
            caso(2, "Cambio de tasa", "Desde el mes 11 la tasa pasa a 80 % EA; se conserva la fecha final y se recalcula la cuota.",
                    EXCEL + "Caso2_CambioTasa", EscenariosExcel::caso2),
            caso(3, "Dación en pago", "Paga hasta el mes 7, deja de pagar y en el mes 8 entrega la moto por 3.000.000, menos que la deuda.",
                    EXCEL + "Caso3_Dacion", p -> EscenariosExcel.caso3(p, false)),
            caso(4, "Retoma con excedente", "Paga hasta el mes 3, deja de pagar y en el mes 4 se retoma la moto por 6.800.000, más que la deuda.",
                    EXCEL + "Caso4_Retoma", EscenariosExcel::caso4),
            caso(5, "Pago anticipado total (prepago)", "En el mes 10 paga la cuota y el saldo completo.",
                    EXCEL + "Caso5_Prepago", EscenariosExcel::caso5),
            caso(6, "Abono extra como menor plazo", "Abono de 2.000.000 en el mes 10: se mantiene la cuota y baja el plazo.",
                    EXCEL + "Caso6_MenorPlazo", EscenariosExcel::caso6),
            caso(7, "Abono extra como menor cuota", "Abono de 2.000.000 en el mes 10: se mantiene el plazo y baja la cuota.",
                    EXCEL + "Caso7_MenorCuota", EscenariosExcel::caso7),
            caso(8, "Pago inferior", "En el mes 10 paga el 40 % de la cuota y luego vuelve a la cuota pactada; plazo límite de 100 semanas.",
                    EXCEL + "Caso8_PagoInferior", EscenariosExcel::caso8),
            caso(9, "Pago tardío y gastos de cobranza", "Desde el mes 10 paga todas las cuotas con 14 días de atraso; plazo límite de 120 semanas.",
                    EXCEL + "Caso9_Cobranza", EscenariosExcel::caso9),
            caso(10, "Cambio de tasa con cuota fija", "Desde el mes 11 la tasa pasa a 80 % EA; se conserva la cuota y cambia el número de cuotas.",
                    EXCEL + "Caso10_TasaCuotaFija", EscenariosExcel::caso10),
            caso(11, "Pago inferior y dación", "Paga el 40 % de la cuota en el mes 10, no paga tres semanas y en la cuarta entrega la moto por 3.000.000.",
                    EXCEL + "Caso11_PagoInfDacion", EscenariosExcel::caso11),
            caso(12, "Pago inferior y normalización", "Paga el 40 % de la cuota en el mes 10 y en el mes 14 paga la cuota más lo atrasado y queda al día.",
                    EXCEL + "Caso12_PagoInfNormaliza", EscenariosExcel::caso12),
            caso(13, "Abono extra con pendientes",
                    "La semana 40 no paga, la 41 paga 5.000 con 3 días de atraso y la 42 abona 1.500.000 a menor plazo. "
                            + "Con NEGOCIO el abono paga primero lo vencido (A4) y el sobrante vuelve a pasar por la prelación "
                            + "sobre lo causado de la semana 42 (R4-CASO13); con EXCEL va todo a capital.",
                    "Propuesto por el equipo; regla de la ronda 4 (R4-CASO13); Diego debe validar las cifras nuevas "
                            + "(pregunta P5-CASO13). No está en el Excel",
                    EscenariosExcel::caso13));

    private CatalogoCasos() {
    }

    private static Caso caso(int numero, String nombre, String descripcion, String fuente,
            Function<ParametrosProducto, Escenario> escenario) {
        return new Caso(new CasoResumenDto(numero, nombre, descripcion, fuente), escenario);
    }

    static Optional<Caso> buscar(int numero) {
        return CASOS.stream().filter(c -> c.resumen().numero() == numero).findFirst();
    }
}
