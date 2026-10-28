package co.financiera.cartera.simulaciones;

import static co.financiera.cartera.nucleo.Calc.bd;

import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadCambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.TipoRecuperacion;

/**
 * Los escenarios de las hojas Caso1 a Caso12 del Excel (parámetros de la hoja Inputs) y el caso 13 del
 * equipo, para reutilizarlos fuera de las pruebas: previsualizaciones del cuestionario, demos y el API.
 */
public final class EscenariosExcel {

    private EscenariosExcel() {
    }

    private static Escenario base(ParametrosProducto p) {
        return new Escenario(Operacion.ejemploExcel(), p);
    }

    public static Escenario caso1(ParametrosProducto p) {
        return base(p);
    }

    /** Caso 1 con un pago inicial digitado por el asesor (P-09). */
    public static Escenario caso1ConInicial(ParametrosProducto p, java.math.BigDecimal pagoInicial) {
        return new Escenario(Operacion.ejemploExcel().conPagoInicial(pagoInicial), p);
    }

    /** Caso 2: desde el mes 11 (Inputs C40) la tasa pasa a 80 % EA (C41); se conserva la fecha final. */
    public static Escenario caso2(ParametrosProducto p) {
        return base(p).en(Calendario.primerPeriodoDelMes(11, p), new CambioTasa(bd("0.80")));
    }

    /** Pago normal hasta el mes 7, sin pago hasta el siniestro del mes 8 y dación por 3.000.000. */
    public static Escenario caso3(ParametrosProducto p, boolean causaMoraYServicios) {
        int ultimoPagado = Calendario.periodoDelMes(7, p);
        int siniestro = Calendario.periodoDelMes(8, p);
        return base(p).entre(ultimoPagado + 1, siniestro, new SinPago(causaMoraYServicios))
                .en(siniestro, new CierrePorRecuperacion(TipoRecuperacion.DACION_EN_PAGO, bd(3_000_000)));
    }

    /**
     * Caso 4: paga hasta el mes 3 (Inputs C48), no paga hasta el mes 4 (C49) y se retoma la moto por un
     * avalúo de 6.800.000 (C47), mayor que la deuda. Como el Excel, sin mora en las semanas sin pago (D8).
     */
    public static Escenario caso4(ParametrosProducto p) {
        int retoma = Calendario.periodoDelMes(4, p);
        return base(p).entre(Calendario.periodoDelMes(3, p) + 1, retoma, new SinPago(false))
                .en(retoma, new CierrePorRecuperacion(TipoRecuperacion.RETOMA, bd(6_800_000)));
    }

    /** Caso 5: prepago total el mismo día de la cuota del mes 10. */
    public static Escenario caso5(ParametrosProducto p) {
        return base(p).en(Calendario.periodoDelMes(10, p), new PrepagoTotal());
    }

    /** Abono extra de 2.000.000 en el mes 10 aplicado como menor plazo. */
    public static Escenario caso6(ParametrosProducto p) {
        return base(p).en(Calendario.periodoDelMes(10, p), new AbonoExtra(bd(2_000_000), ModalidadAbono.MENOR_PLAZO));
    }

    /** Caso 7: abono extra de 2.000.000 en el mes 10 aplicado como menor cuota. */
    public static Escenario caso7(ParametrosProducto p) {
        return base(p).en(Calendario.periodoDelMes(10, p), new AbonoExtra(bd(2_000_000), ModalidadAbono.MENOR_CUOTA));
    }

    /**
     * Pago del 40 % de la cuota en el mes 10; luego vuelve a pagar la cuota pactada. Límite: 100 semanas
     * en periodos del crédito, ROUNDDOWN(100 × ppa / 52) como Inputs C66 (semanal 100).
     */
    public static Escenario caso8(ParametrosProducto p) {
        return caso8(p, java.util.OptionalInt.empty());
    }

    /**
     * Caso 8 con el plazo límite del perfil de reglas. X4 (Diego 2026-10-22, documento "rptas software"): el
     * plazo límite del caso 8 se amplía a 104 cuotas, que es {@code plazoLimiteMeses = 24} (R4-PLAZO-LIMITE) en
     * periodos del crédito. Vacío = las 100 semanas del Excel (Inputs C61), como {@link #caso8(ParametrosProducto)}.
     */
    public static Escenario caso8(ParametrosProducto p, java.util.OptionalInt plazoLimitePeriodos) {
        int limite = plazoLimitePeriodos.orElse(p.periodicidad().periodosDesdeSemanas(100));
        return new Escenario(Operacion.ejemploExcel(), p.conPlazoLimite(limite))
                .en(Calendario.periodoDelMes(10, p), new PagoParcial(bd("0.4"), 0));
    }

    /**
     * Desde el mes 10 todas las cuotas se pagan con 14 días de atraso. Límite: 120 semanas en periodos
     * del crédito, ROUNDDOWN(120 × ppa / 52) como Inputs C73 (semanal 120).
     */
    public static Escenario caso9(ParametrosProducto p) {
        return new Escenario(Operacion.ejemploExcel(), p.conPlazoLimite(p.periodicidad().periodosDesdeSemanas(120)))
                .desde(Calendario.periodoDelMes(10, p), new PagoContractual(14));
    }

    /** Caso 10: desde el mes 11 la tasa pasa a 80 % EA; se conserva la cuota y cambia el número de cuotas. */
    public static Escenario caso10(ParametrosProducto p) {
        return base(p).en(Calendario.primerPeriodoDelMes(11, p),
                new CambioTasa(bd("0.80"), ModalidadCambioTasa.MISMA_CUOTA));
    }

    /**
     * Caso 11: pago del 40 % de la cuota en el mes 10, tres periodos sin pago y dación por 3.000.000 en
     * el cuarto (Inputs C80: 4 periodos de espera, incluido el cierre).
     */
    public static Escenario caso11(ParametrosProducto p) {
        return caso11(p, 4);
    }

    /**
     * Caso 11 con los periodos de espera de la dación: 4 en el Excel; con NEGOCIO, 2 (R7-RECUP-VS-CASO11, Diego
     * 2026-10-27: se corrige a 2 periodos, los de las cuotas vencidas que habilitan la recuperación).
     */
    public static Escenario caso11(ParametrosProducto p, int periodosEspera) {
        int inferior = Calendario.periodoDelMes(10, p);
        int cierre = inferior + periodosEspera;
        return base(p).en(inferior, new PagoParcial(bd("0.4"), 0))
                .entre(inferior + 1, cierre, new SinPago(true))
                .en(cierre, new CierrePorRecuperacion(TipoRecuperacion.DACION_EN_PAGO, bd(3_000_000)));
    }

    /**
     * Caso 12: pago del 40 % de la cuota en el mes 10; en el mes 14 paga la cuota del periodo más lo atrasado
     * y vuelve a estar al día (Inputs C89 = MIN(cuotas, MAX(inferior + 1, ROUNDDOWN(14 × 52/12)))).
     */
    public static Escenario caso12(ParametrosProducto p) {
        int inferior = Calendario.periodoDelMes(10, p);
        int normalizacion = Math.clamp(Calendario.periodoDelMes(14, p), inferior + 1, p.numeroCuotas());
        return base(p).en(inferior, new PagoParcial(bd("0.4"), 0)).en(normalizacion, new PagoAlDia());
    }

    /** Caso 13: semana sin pago. */
    public static final int CASO13_SIN_PAGO = 40;
    /** Caso 13: semana con un pago de 5.000 y 3 días de atraso. */
    public static final int CASO13_PAGO_TARDIO = 41;
    /** Caso 13: semana sin pago de la cuota pero con el abono extra. */
    public static final int CASO13_ABONO = 42;
    /** Caso 13: valor del abono extra. */
    public static final java.math.BigDecimal CASO13_VALOR_ABONO = bd(1_500_000);

    /**
     * Caso 13 sin el abono: la semana 40 no paga, la 41 paga 5.000 con 3 días de atraso y la 42 no paga la
     * cuota (ABN-1, docs/reglas/caso-13.md). Periodos semanales de la réplica del Excel.
     */
    public static Escenario caso13ConPendientes(ParametrosProducto p) {
        return base(p).en(CASO13_SIN_PAGO, new SinPago(true))
                .en(CASO13_PAGO_TARDIO, new PagoValor(bd(5_000), 3))
                .en(CASO13_ABONO, new SinPago(true));
    }

    /**
     * Caso 13 · Abono extra con pendientes: el de {@link #caso13ConPendientes} más un abono extra en la
     * semana 42. Con {@code aplicacionAbono = PRIMERO_PENDIENTES} paga primero los pendientes (A4/D21).
     */
    public static Escenario caso13(ParametrosProducto p, java.math.BigDecimal abono, ModalidadAbono modalidad) {
        return caso13ConPendientes(p).en(CASO13_ABONO, new AbonoExtra(abono, modalidad));
    }

    /** Caso 13 con los valores de docs/reglas/caso-13.md: abono de 1.500.000 a menor plazo. */
    public static Escenario caso13(ParametrosProducto p) {
        return caso13(p, CASO13_VALOR_ABONO, ModalidadAbono.MENOR_PLAZO);
    }
}
