package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.CERO;
import static co.financiera.cartera.nucleo.Calc.esCero;
import static co.financiera.cartera.nucleo.Calc.min;
import static co.financiera.cartera.nucleo.Calc.multiplicar;
import static co.financiera.cartera.nucleo.Calc.noNegativo;
import static co.financiera.cartera.nucleo.Calc.restar;
import static co.financiera.cartera.nucleo.Calc.sumar;

import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import java.math.BigDecimal;

/**
 * Paso 2 del periodo: causación de interés corriente, capital contractual, servicios, mora y cobranza. Extraído de
 * {@code MotorCartera.Corrida} sin cambiar la matemática. Solo lee el {@link EstadoCorrida} compartido: causar no
 * cambia ningún saldo (eso lo hace el reparto por prelación).
 */
final class Causador {

    private final EstadoCorrida s;

    Causador(EstadoCorrida s) {
        this.s = s;
    }

    /** FCC y asistencia solo se causan en periodos pagados al día (Inputs ⑥, P-07). */
    static boolean alDia(Instruccion pago, int dias) {
        return (pago instanceof PagoContractual || pago instanceof PagoAlDia || pago instanceof PagoValor) && dias == 0;
    }

    /**
     * 2. Interés corriente, capital contractual, servicios, mora y cobranza del periodo. Las
     * variantes de ReglasNegocio son las opciones que se le preguntan a negocio; EXCEL reproduce
     * el Excel tal cual.
     */
    Causacion causar(int periodo, BigDecimal saldoInicial, boolean sinPagoSinMora, int dias, boolean alDia,
            int cuotasVencidasPrevias, int diasCobranza) {
        ReglasNegocio reglas = s.reglas();
        BigDecimal tasa = s.tasas.periodica();
        boolean dentroDelPlazo = periodo <= s.cuotasVigentes;
        BigDecimal baseInteres = reglas.baseInteres() == ReglasNegocio.BaseInteres.SALDO_VIGENTE
                ? noNegativo(restar(saldoInicial, s.capitalVencido)) : saldoInicial;
        BigDecimal capitalContractual = CERO;
        if (dentroDelPlazo && !sinPagoSinMora && !esCero(s.saldoPlan)) {
            capitalContractual = capitalContractual(saldoInicial, tasa);
        }
        boolean causaServicios = !sinPagoSinMora
                && (dentroDelPlazo || (reglas.serviciosDespuesDelPlazo() && !esCero(saldoInicial)));
        Servicios servicios = Servicios.NINGUNO;
        Servicios serviciosAlDia = Servicios.NINGUNO;
        if (causaServicios) {
            Servicios base = reglas.baseServicios() == ReglasNegocio.BaseServicios.SALDO
                    ? Servicios.porCuota(saldoInicial, s.p) : s.serviciosPorCuota;
            if (reglas.cuotasVencidasParaSuspenderServicios() == null) {
                servicios = alDia ? base : base.soloSeguro();
                serviciosAlDia = base;
            } else {
                // R8-PAGO-INCOMPLETO-EN-FECHA (Diego 2026-10-28): la causación genera siempre los tres servicios, haya
                // o no pago en la fecha (D17 deja de aplicar); con previas >= clave se suspenden fondo y asistencia y
                // solo se causa el seguro. Lo suspendido no es "no causado por falta de pago": un abono no lo revive
                // (R4-CASO13), por eso serviciosAlDia = servicios.
                servicios = reglas.suspendeServicios(cuotasVencidasPrevias) ? base.soloSeguro() : base;
                serviciosAlDia = servicios;
            }
        }
        // P5-PRORRATEO (Diego, ronda 5; ADR 0002 paso 7): en la primera cuota solo el interés se prorratea por
        // los días reales entre el desembolso y la primera fecha de pago, con la tasa diaria como el prepago por
        // días (A6); capital contractual y servicios van completos. Con 365/ppa días daría exactamente la tasa
        // periódica. Sin fechas en el escenario, o con el Excel, no cambia nada.
        boolean prorrateada = periodo == 1 && reglas.prorrateoPrimeraCuota() && s.escenario.diasPrimerPeriodo().isPresent();
        BigDecimal interes = multiplicar(baseInteres, tasa);
        if (prorrateada) {
            int diasReales = s.escenario.diasPrimerPeriodo().getAsInt();
            interes = multiplicar(baseInteres, restar(Calc.potencia(sumar(Calc.UNO, s.tasas.diaria()), diasReales), Calc.UNO));
        }
        return new Causacion(interes, capitalContractual, servicios, mora(periodo, capitalContractual, dias),
                cobranza(saldoInicial, diasCobranza, cuotasVencidasPrevias), serviciosAlDia, prorrateada);
    }

    /**
     * R8-REDONDEO-PESO (Diego 2026-10-28): lo que hay que sumar a la cuota exigible del periodo (cuota financiera +
     * servicios causados) para que quede en pesos enteros, redondeando HALF_UP (supuesto: Diego dijo "redondear al
     * peso" sin el modo). El residuo, de −0,5 a +0,5, se absorbe en el interés corriente del periodo (ingreso de la
     * financiera): el capital contractual sigue el plan exacto, así el plan cierra en la cuota n, la suma del capital
     * contractual es el monto financiado y la última cuota no arrastra residuos acumulados; los servicios son de terceros
     * y tampoco se tocan. Si el interés del periodo no alcanza para absorber un residuo negativo (tasa 0 o interés menor
     * que medio peso) esa cuota no se redondea. Sin la clave (el Excel) devuelve 0.
     */
    BigDecimal residuoAlPeso(BigDecimal cuotaDelPeriodo, Causacion c) {
        if (!s.reglas().redondeoCuotaAlPeso()) {
            return CERO;
        }
        BigDecimal exigible = sumar(cuotaDelPeriodo, c.servicios().total());
        BigDecimal residuo = restar(exigible.setScale(0, java.math.RoundingMode.HALF_UP), exigible);
        if (residuo.signum() == 0 || sumar(c.interes(), residuo).signum() < 0) {
            return CERO;
        }
        return residuo;
    }

    /** Capital contractual del plan vigente en un periodo dentro del plazo, sin pasar del capital vigente. */
    private BigDecimal capitalContractual(BigDecimal saldoInicial, BigDecimal tasa) {
        // Auditoría 2026-10-23 (H3): nunca negativo, aunque el saldo del plan lo fuera por un error aguas arriba.
        BigDecimal capitalContractual = noNegativo(min(s.saldoPlan, restar(s.cuota, multiplicar(s.saldoPlan, tasa))));
        // Auditoría 2026-10-23 (I8): el plan puede ir por encima del capital que de verdad queda vigente (un pago
        // mayor que la cuota baja el saldo pero no el plan). Sin este tope la cuota del plan vencía más capital
        // del que existe y el capital vencido superaba al saldo. Solo actúa si la diferencia supera el medio
        // centavo, así los residuos DECIMAL128 de los casos del Excel no cambian.
        BigDecimal vigente = noNegativo(restar(saldoInicial, s.capitalVencido));
        if (restar(capitalContractual, vigente).compareTo(Calc.EPSILON_SALDO) > 0) {
            capitalContractual = vigente;
        }
        return capitalContractual;
    }

    private BigDecimal mora(int periodo, BigDecimal capitalContractual, int dias) {
        BigDecimal mora = multiplicar(s.capitalVencido, factorMoraDelPeriodo(periodo));
        if (dias > 0 && s.reglas().formulaMora() == ReglasNegocio.FormulaMora.VENCIDO_MAS_DIAS) {
            BigDecimal factorDias = restar(Calc.potencia(sumar(Calc.UNO, s.tasas.diaria()), dias), Calc.UNO);
            mora = sumar(mora, multiplicar(capitalContractual, factorDias));
        }
        return mora;
    }

    /**
     * Factor de mora del capital vencido en el periodo. Con el Excel (PERIODO_NOMINAL) es la tasa periódica:
     * el periodo dura 365/ppa días. PPER-5 / D27 (Diego, ADR 0002 paso 8): en mensual la mora cuenta los días
     * reales del mes; con DIAS_CALENDARIO_MENSUAL y fechas en el escenario el factor es (1 + diaria)^días − 1 con
     * los días del vencimiento anterior al de esta cuota (28 a 31). Quincenal y semanal siguen nominales: Diego
     * solo decidió la mensual.
     */
    private BigDecimal factorMoraDelPeriodo(int periodo) {
        if (s.reglas().diasMora() == ReglasNegocio.DiasMora.DIAS_CALENDARIO_MENSUAL
                && s.p.periodicidad() == Periodicidad.MENSUAL) {
            java.util.OptionalInt diasReales = s.escenario.diasDelPeriodo(periodo);
            if (diasReales.isPresent()) {
                return restar(Calc.potencia(sumar(Calc.UNO, s.tasas.diaria()), diasReales.getAsInt()), Calc.UNO);
            }
        }
        return s.tasas.periodica();
    }

    /**
     * Gastos de cobranza = días de atraso × valor diario del producto. COB-4 (Diego 2026-10-22): solo se
     * causan cuando el pago llega en mora (días de atraso &gt; 0); al día, cancelado o prepagado no se liquidan.
     * COB-6: solo si la cobranza está activa en el crédito ({@link Escenario#cobranzaActiva()}).
     * R7-COBRANZA-ACTIVA: y solo desde que hay {@code cuotasVencidasParaCobranza} cuotas atrasadas de periodos anteriores
     * (con 2, la cobranza empieza en la tercera cuota vencida).
     */
    private BigDecimal cobranza(BigDecimal saldoInicial, int dias, int cuotasVencidasPrevias) {
        // R7-COBRANZA-ACTIVA (Diego): la cobranza empieza tras N periodos en mora (regla fija; con el Excel no hay umbral).
        if (!s.escenario.cobranzaActiva() || !s.reglas().llegaAlUmbralDeCobranza(cuotasVencidasPrevias)) {
            return CERO;
        }
        BigDecimal fija = s.reglas().cobranzaPorPeriodo();
        if (fija != null) {
            // R8-COBRANZA-PAGO-TARDIO (Diego 2026-10-28): no se prorratea por días; el periodo que causaría cobranza
            // causa exactamente el cobro fijo, una sola vez (sin tope por pago: el valor ya es el del periodo).
            return dias > 0 && !esCero(saldoInicial) ? fija : CERO;
        }
        BigDecimal cobranza = (dias > 0 && !esCero(saldoInicial)) ? multiplicar(Calc.bd(dias), s.p.cobranzaDiaria()) : CERO;
        BigDecimal tope = s.reglas().topeCobranzaPorPago();
        return tope == null ? cobranza : min(cobranza, tope);
    }
}
