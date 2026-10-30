package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.CERO;
import static co.financiera.cartera.nucleo.Calc.min;
import static co.financiera.cartera.nucleo.Calc.noNegativo;
import static co.financiera.cartera.nucleo.Calc.restar;
import static co.financiera.cartera.nucleo.Calc.sumar;

import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import java.math.BigDecimal;

/**
 * Paso 5 del periodo (cierre por recuperación de la moto: dación o retoma) y la marca de "listo para
 * recuperación" del paso 6. Extraído de {@code MotorCartera.Corrida} sin cambiar la matemática: lee y escribe los
 * saldos en el {@link EstadoCorrida} compartido.
 */
final class Recuperacion {

    private final EstadoCorrida s;
    /**
     * R6-RECUP-AUTO (Diego): una vez el crédito llega al umbral de recuperación queda marcado en los periodos
     * siguientes aunque el cliente se ponga al día; el motor no ejecuta nada, una persona decide.
     */
    private boolean marcadoParaRecuperacion;

    Recuperacion(EstadoCorrida s) {
        this(s, false);
    }

    /** Con la marca que traía el crédito (reanudación desde una {@link Instantanea}). */
    Recuperacion(EstadoCorrida s, boolean marcadoParaRecuperacion) {
        this.s = s;
        this.marcadoParaRecuperacion = marcadoParaRecuperacion;
    }

    boolean marcadoParaRecuperacion() {
        return marcadoParaRecuperacion;
    }

    /**
     * 5. Recibe la moto: su valor se aplica por prelación a lo pendiente (cobranza, mora, interés,
     * fondo, asistencia, seguro, capital). Lo no cubierto es CxC al fondo de garantías; lo que
     * sobra, CxP al deudor (casos 3, 4 y 11).
     *
     * <p>R4-R2 (Diego, ronda 4): se descuenta el costo real de la recuperación con soporte. En la retoma
     * reduce el excedente que se devuelve. En la dación, con {@code costoRecuperacionAlFondo} (confirmado por
     * Diego en P5-COSTO-DACION) se suma al faltante que cubre el fondo; si no, lo asume la financiera.
     * Con costo 0 (el Excel) todo queda como antes.
     */
    Cierre cerrar(CierrePorRecuperacion orden) {
        BigDecimal inventario = orden.valorMoto();
        BigDecimal costo = orden.costoRecuperacion();
        BigDecimal deuda = sumar(s.saldo, s.cxcInteres, s.cxcMora, s.cxcServicios.total(), s.cxcCobranza);
        boolean costoAlFondo = s.reglas().costoRecuperacionAlFondo();
        BigDecimal neto = restar(restar(inventario, deuda), costo);
        BigDecimal cxpDeudor = noNegativo(neto);
        BigDecimal cxcFondo;
        BigDecimal costoAsumido;
        BigDecimal disponible;
        if (costoAlFondo) {
            // La moto paga primero el costo y el resto va a la deuda; el faltante (con el costo) lo cubre el fondo.
            cxcFondo = noNegativo(restar(CERO, neto));
            costoAsumido = CERO;
            disponible = noNegativo(restar(inventario, costo));
        } else {
            // La moto paga primero la deuda; el costo solo se descuenta del excedente y el resto lo asume la financiera.
            cxcFondo = noNegativo(restar(deuda, inventario));
            costoAsumido = restar(costo, min(costo, noNegativo(restar(inventario, deuda))));
            disponible = inventario;
        }
        BigDecimal restante = restar(disponible, min(disponible, sumar(s.cxcCobranza, s.cxcMora, s.cxcInteres)));
        // Fondo → asistencia → seguro de vida → seguro todo riesgo → su IVA (R9-SEGURO-TODO-RIESGO).
        Cierre cierre = new Cierre(deuda, inventario, cxcFondo, cxpDeudor, s.cxcServicios.pagarHasta(restante),
                new DeudaExtinguida(s.cxcCobranza, s.cxcMora, s.cxcInteres, s.cxcServicios, s.saldo), costo, costoAsumido);
        s.saldo = CERO;
        s.saldoPlan = CERO;
        s.capitalVencido = CERO;
        s.cxcInteres = CERO;
        s.cxcMora = CERO;
        s.cxcServicios = Servicios.NINGUNO;
        s.cxcCobranza = CERO;
        return cierre;
    }

    /**
     * PPER-9 (Diego 2026-10-22): con 2 cuotas atrasadas el crédito queda listo para recuperación. R6-RECUP-AUTO
     * (Diego): el motor solo marca, nunca ejecuta la dación ni la retoma, y la marca persiste aunque el
     * cliente se ponga al día: una persona decide. Solo se apaga cuando el crédito termina (cancelado o
     * cerrado por evento), porque ya no hay nada que recuperar.
     */
    boolean listoParaRecuperacion(EstadoCredito estado, int cuotasVencidas) {
        if (estado == EstadoCredito.EN_MORA && s.reglas().llegaAlUmbralDeRecuperacion(cuotasVencidas)) {
            marcadoParaRecuperacion = true;
        }
        boolean termino = estado == EstadoCredito.CANCELADO || estado == EstadoCredito.CERRADO_POR_EVENTO;
        return marcadoParaRecuperacion && !termino;
    }

    /** Resultado de un cierre por recuperación de la moto. */
    record Cierre(BigDecimal deuda, BigDecimal inventario, BigDecimal cxcFondoGarantias, BigDecimal cxpDeudor,
            Servicios servicios, DeudaExtinguida extinguido, BigDecimal costo, BigDecimal costoAsumido) {
        static final Cierre NINGUNO = new Cierre(CERO, CERO, CERO, CERO, Servicios.NINGUNO, DeudaExtinguida.NINGUNA,
                CERO, CERO);
    }
}
