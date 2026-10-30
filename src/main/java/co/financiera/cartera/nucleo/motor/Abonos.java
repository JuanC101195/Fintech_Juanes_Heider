package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.CERO;
import static co.financiera.cartera.nucleo.Calc.esCero;
import static co.financiera.cartera.nucleo.Calc.min;
import static co.financiera.cartera.nucleo.Calc.multiplicar;
import static co.financiera.cartera.nucleo.Calc.noNegativo;
import static co.financiera.cartera.nucleo.Calc.restar;
import static co.financiera.cartera.nucleo.Calc.sumar;

import co.financiera.cartera.nucleo.Amortizacion;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.AplicacionAbono;
import java.math.BigDecimal;

/**
 * Paso 4 del periodo: abono extraordinario (menor plazo / menor cuota) y prepago total, después de la cuota.
 * Extraído de {@code MotorCartera.Corrida} sin cambiar la matemática: lee y escribe los saldos en el
 * {@link EstadoCorrida} compartido, en el mismo orden que antes.
 */
final class Abonos {

    private final EstadoCorrida s;

    Abonos(EstadoCorrida s) {
        this.s = s;
    }

    /**
     * 4. Abono extraordinario; devuelve lo aplicado a capital vigente. Con SOLO_CAPITAL (Excel) va
     * 100 % a capital. Con PRIMERO_PENDIENTES (A4, Diego 2026-10-20) paga antes los pendientes de
     * semanas anteriores por prelación y el resto va a capital: como la cuota del periodo ya se
     * aplicó, solo quedan CxC ya causadas y nunca se adelantan intereses ni servicios futuros.
     */
    BigDecimal aplicarAbono(int periodo, AbonoExtra abono, BigDecimal tasaVigente, Causacion causado) {
        AplicacionAbono aplicacion = s.reglas().aplicacionAbono();
        boolean primeroPendientes = aplicacion.pagaPendientes();
        // R4-CASO13 (Diego, ronda 4): con PRELACION_COMPLETA el excedente vuelve a pasar por la prelación
        // sobre lo causado del periodo corriente; solo faltan su fondo y su asistencia si no se pagó al día.
        Servicios corrientePorPagar = aplicacion == AplicacionAbono.PRELACION_COMPLETA
                ? causado.serviciosNoCausados() : Servicios.NINGUNO;
        // P-14: Diego (2026-10-16) no acepta abonos mayores que el saldo; el Excel los recortaba. Con
        // pendientes, el saldo del crédito incluye lo que se debe de semanas anteriores.
        BigDecimal maximo = primeroPendientes ? sumar(s.saldo, s.pendientesSinCapital(), corrientePorPagar.total()) : s.saldo;
        if (abono.valor().compareTo(sumar(maximo, Calc.EPSILON_SALDO)) > 0) {
            throw new AbonoMayorQueElSaldoException(periodo, abono.valor(), maximo);
        }
        BigDecimal restante = abono.valor();
        if (primeroPendientes) {
            s.pendientesPagadosExtra = pagarPendientes(restante);
            restante = noNegativo(restar(restante, s.pendientesPagadosExtra.total()));
        }
        if (restante.signum() > 0 && corrientePorPagar.total().signum() > 0) {
            s.serviciosCorrientesExtra = pagarServiciosCorrientes(restante, corrientePorPagar);
            s.pendientesPagadosExtra = s.pendientesPagadosExtra.mas(
                    new DeudaExtinguida(CERO, CERO, CERO, s.serviciosCorrientesExtra, CERO));
            restante = noNegativo(restar(restante, s.serviciosCorrientesExtra.total()));
        }
        // Auditoría 2026-10-23 (H3): el abono va primero al capital vigente (con un abono menor que el vigente nada
        // cambia) y lo que lo supere extingue capital vencido. Antes se descontaba del saldo (que incluye el
        // vencido) sin reducir el vencido: el saldo del plan quedaba negativo, el capital contractual salía
        // negativo y el vencido seguía causando mora sobre capital ya pagado.
        BigDecimal aplicado = min(restante, noNegativo(restar(s.saldo, s.capitalVencido)));
        s.saldo = restar(s.saldo, aplicado);
        pagarVencidoConExtra(noNegativo(restar(restante, aplicado)));
        recalcularPlan(periodo, abono.modalidad(), tasaVigente, primeroPendientes);
        return aplicado;
    }

    /** Después del abono: la cuota nueva (menor cuota), el saldo del plan y el plazo nuevo (menor plazo). */
    private void recalcularPlan(int periodo, ModalidadAbono modalidad, BigDecimal tasaVigente, boolean primeroPendientes) {
        if (modalidad == ModalidadAbono.MENOR_CUOTA && s.cuotasVigentes - periodo > 0 && !esCero(s.saldo)) {
            // A5 (Diego 2026-10-20): la cuota nueva usa la tasa vigente; el abono no cambia la tasa (D-7.1).
            // Con PRIMERO_PENDIENTES la cuota nueva amortiza solo el capital vigente: el vencido que el
            // abono no alcanzó a cubrir sigue cobrándose aparte.
            BigDecimal base = primeroPendientes ? noNegativo(restar(s.saldo, s.capitalVencido)) : s.saldo;
            s.cuota = Amortizacion.cuotaFija(tasaVigente, s.cuotasVigentes - periodo, base);
        }
        s.saldoPlan = restar(s.saldo, s.capitalVencido);
        // SRV-2 (Diego, ronda 3, 2026-10-20): si el abono acorta el plazo, la cobertura de los servicios
        // termina con el nuevo plazo. Se recalcula el número de cuotas como en el caso 10 (NPER con la misma
        // cuota) para que causar() deje de causar servicios y capital contractual después de la última. En
        // el caso 6 del Excel el crédito ya terminaba en la cuota 71 porque el saldo llega a cero; esto solo
        // cambia algo si el cliente se atrasa después del abono.
        if (modalidad == ModalidadAbono.MENOR_PLAZO && periodo < s.cuotasVigentes && s.saldoPlan.signum() > 0
                && !esCero(s.saldoPlan)) {
            s.cuotasVigentes = Math.min(s.cuotasVigentes, periodo + s.cuotasHastaSaldoCero(s.saldoPlan));
        }
    }

    /**
     * R4-CASO13: segunda pasada de la prelación con el excedente del abono, sobre lo causado del periodo
     * corriente. Cobranza, mora, interés corriente, seguro y capital de la cuota ya se causaron y los pagó la
     * primera pasada (como pendientes); lo único que falta son el fondo de garantías y la asistencia que el
     * periodo no causó por no pagarse al día (D17). Se pagan en el orden de Inputs ⑥ (fondo → asistencia →
     * seguro) y solo hasta donde alcance: lo que no se pague no queda como CxC, porque el periodo sigue
     * contando como no pagado al día. Nunca se cobra nada de periodos futuros (A4).
     */
    private static Servicios pagarServiciosCorrientes(BigDecimal disponible, Servicios porPagar) {
        return porPagar.pagarHasta(disponible);
    }

    /**
     * Auditoría 2026-10-23 (H3): capital vencido que paga un abono o un prepago aplicado solo a capital
     * (SOLO_CAPITAL, el Excel) cuando supera el capital vigente. Se reporta como capital de los pendientes
     * pagados por fuera de la cuota, para que la contabilidad lo acredite a Cartera capital vencida.
     */
    private void pagarVencidoConExtra(BigDecimal disponible) {
        BigDecimal vencido = min(disponible, s.capitalVencido);
        if (vencido.signum() <= 0) {
            return;
        }
        s.pendientesPagadosExtra = s.pendientesPagadosExtra.mas(
                new DeudaExtinguida(CERO, CERO, CERO, Servicios.NINGUNO, vencido));
        s.capitalVencido = noNegativo(restar(s.capitalVencido, vencido));
        if (esCero(s.capitalVencido)) {
            s.capitalVencido = CERO;
        }
        s.saldo = noNegativo(restar(s.saldo, vencido));
    }

    /**
     * A4: aplica {@code disponible} a lo pendiente por la prelación de Inputs ⑥: cobranza → mora →
     * interés corriente → fondo → asistencia → seguro → capital vencido (con CAPITAL_ANTES_DE_SERVICIOS
     * el capital vencido va antes de los servicios, como en el reparto de la cuota). Devuelve lo pagado.
     */
    private DeudaExtinguida pagarPendientes(BigDecimal disponible) {
        BigDecimal restante = disponible;
        BigDecimal cobranza = min(restante, s.cxcCobranza);
        restante = restar(restante, cobranza);
        BigDecimal mora = min(restante, s.cxcMora);
        restante = restar(restante, mora);
        BigDecimal interes = min(restante, s.cxcInteres);
        restante = restar(restante, interes);
        BigDecimal vencido = CERO;
        boolean capitalAntes = s.reglas().prelacion() == ReglasNegocio.OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS;
        if (capitalAntes) {
            vencido = min(restante, s.capitalVencido);
            restante = restar(restante, vencido);
        }
        // Fondo → asistencia → seguro de vida → seguro todo riesgo → su IVA (R9-SEGURO-TODO-RIESGO).
        Servicios servicios = s.cxcServicios.pagarHasta(restante);
        restante = servicios.descontarDe(restante);
        if (!capitalAntes) {
            vencido = min(restante, s.capitalVencido);
        }
        s.cxcCobranza = noNegativo(restar(s.cxcCobranza, cobranza));
        s.cxcMora = noNegativo(restar(s.cxcMora, mora));
        s.cxcInteres = noNegativo(restar(s.cxcInteres, interes));
        s.cxcServicios = s.cxcServicios.menos(servicios).noNegativo();
        s.capitalVencido = noNegativo(restar(s.capitalVencido, vencido));
        if (esCero(s.capitalVencido)) {
            s.capitalVencido = CERO;
        }
        s.saldo = noNegativo(restar(s.saldo, vencido));
        return new DeudaExtinguida(cobranza, mora, interes, servicios, vencido);
    }

    /**
     * 4. Prepago total; devuelve el capital prepagado. A6: con interesPrepagoPorDias se cobra además
     * el interés corriente de los días desde la última cuota, saldo × ((1 + tasa diaria)^días − 1),
     * el mismo prorrateo diario de la mora; el Excel prepaga el día de la cuota y no lo cobra. Con
     * PRIMERO_PENDIENTES (A4) el prepago paga primero todo lo pendiente.
     */
    BigDecimal prepagar(int periodo, PrepagoTotal prepago) {
        ReglasNegocio reglas = s.reglas();
        int dias = prepago.fecha() == null ? prepago.diasDesdeUltimaCuota() : diasHastaPrepago(periodo, prepago.fecha());
        if (reglas.interesPrepagoPorDias() && dias > 0) {
            BigDecimal base = reglas.baseInteres() == ReglasNegocio.BaseInteres.SALDO_VIGENTE
                    ? noNegativo(restar(s.saldo, s.capitalVencido)) : s.saldo;
            BigDecimal factorDias = restar(Calc.potencia(sumar(Calc.UNO, s.tasas.diaria()), dias), Calc.UNO);
            s.interesPrepago = multiplicar(base, factorDias);
        }
        if (reglas.aplicacionAbono().pagaPendientes()) {
            s.pendientesPagadosExtra = s.pendientesPagadosExtra.mas(pagarPendientes(sumar(s.saldo, s.pendientesSinCapital())));
        }
        // Auditoría 2026-10-23 (H3): el prepago total extingue el capital completo, también el vencido (con SOLO_CAPITAL
        // antes quedaba vivo con saldo 0 y seguía causando mora y servicios por más de 100 periodos).
        pagarVencidoConExtra(s.capitalVencido);
        BigDecimal prepagado = s.saldo;
        s.saldo = CERO;
        s.saldoPlan = CERO;
        return prepagado;
    }

    /**
     * PPER-7 / D27 (ADR 0002 paso 8): días reales de un prepago a mitad de periodo, desde el vencimiento de la cuota
     * del periodo (que ya se aplicó) hasta la fecha del prepago. Si la fecha es anterior a ese vencimiento son 0 (la
     * cuota ya cobró el periodo completo, como el Excel). Desde el vencimiento siguiente se rechaza: esa cuota ya es
     * exigible y el prepago va en el periodo siguiente.
     */
    private int diasHastaPrepago(int periodo, java.time.LocalDate fecha) {
        co.financiera.cartera.nucleo.CalendarioPagos calendario = s.escenario.calendario().orElseThrow(
                () -> new IllegalStateException("El prepago con fecha necesita el calendario del crédito (Escenario.conCalendario)"));
        java.time.LocalDate siguiente = calendario.vencimiento(periodo + 1);
        if (!fecha.isBefore(siguiente)) {
            throw new IllegalArgumentException("El prepago del " + fecha + " cae en el periodo " + (periodo + 1)
                    + " o después (vence el " + siguiente + "); va en ese periodo");
        }
        return calendario.diasAtraso(periodo, fecha);
    }
}
