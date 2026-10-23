package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.CERO;
import static co.financiera.cartera.nucleo.Calc.esCero;
import static co.financiera.cartera.nucleo.Calc.min;
import static co.financiera.cartera.nucleo.Calc.multiplicar;
import static co.financiera.cartera.nucleo.Calc.noNegativo;
import static co.financiera.cartera.nucleo.Calc.restar;
import static co.financiera.cartera.nucleo.Calc.sumar;

import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import java.math.BigDecimal;

/**
 * Paso 3 del periodo: lo que entra, su reparto por prelación (Inputs ⑥) y los saldos que quedan. Extraído de
 * {@code MotorCartera.Corrida} sin cambiar la matemática: lee y escribe los saldos en el {@link EstadoCorrida}
 * compartido, en el mismo orden que antes.
 */
final class RepartoPrelacion {

    private final EstadoCorrida s;

    RepartoPrelacion(EstadoCorrida s) {
        this.s = s;
    }

    /** 3. Lo que entra este periodo, limitado a lo exigible. */
    BigDecimal recibido(Instruccion pago, BigDecimal cuotaTotal, BigDecimal cuotaDelPeriodo,
            BigDecimal saldoInicial, Causacion c) {
        BigDecimal pendienteSinCapital = sumar(s.cxcCobranza, c.cobranza(), s.cxcMora, c.mora(), s.cxcInteres, c.interes(),
                s.cxcServicios.total(), c.servicios().total());
        BigDecimal exigible = sumar(saldoInicial, pendienteSinCapital);
        return switch (pago) {
            case PagoContractual x -> min(cuotaTotal, exigible);
            // El pago parcial es una fracción de la cuota total pactada (Inputs B36: cuota + los tres servicios);
            // en la primera cuota prorrateada (P5-PRORRATEO), de la cuota de ese periodo.
            case PagoParcial x -> min(multiplicar(x.fraccion(), sumar(cuotaDelPeriodo, s.serviciosPorCuota.total())), exigible);
            case PagoValor x -> min(x.valor(), exigible);
            case PagoAlDia x -> min(sumar(pendienteSinCapital, s.capitalVencido, c.capitalContractual()), exigible);
            case SinPago x -> CERO;
            default -> throw new IllegalStateException("Instrucción de pago no soportada: " + pago);
        };
    }

    /**
     * 3. Prelación (Inputs ⑥): cobranza → mora → interés corriente → fondo de garantías →
     * asistencia → seguro → capital. Con CAPITAL_ANTES_DE_SERVICIOS el capital de la cuota va antes
     * de los servicios.
     */
    Reparto repartir(BigDecimal recibido, BigDecimal saldoInicial, Causacion c) {
        BigDecimal restante = recibido;
        BigDecimal pagoCobranza = min(restante, sumar(s.cxcCobranza, c.cobranza()));
        restante = restar(restante, pagoCobranza);
        BigDecimal pagoMora = min(restante, sumar(s.cxcMora, c.mora()));
        restante = restar(restante, pagoMora);
        BigDecimal pagoInteres = min(restante, sumar(s.cxcInteres, c.interes()));
        restante = restar(restante, pagoInteres);
        Servicios porCobrar = s.cxcServicios.mas(c.servicios());
        BigDecimal pagoCapital = CERO;
        if (s.reglas().prelacion() == ReglasNegocio.OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS) {
            // Solo se adelanta el capital de la cuota (contractual + vencido); el excedente pasa a servicios.
            pagoCapital = min(restante, min(saldoInicial, sumar(s.capitalVencido, c.capitalContractual())));
            restante = restar(restante, pagoCapital);
        }
        BigDecimal pagoFondo = min(restante, porCobrar.fondoGarantias());
        restante = restar(restante, pagoFondo);
        BigDecimal pagoAsistencia = min(restante, porCobrar.asistencia());
        restante = restar(restante, pagoAsistencia);
        BigDecimal pagoSeguro = min(restante, porCobrar.seguro());
        restante = restar(restante, pagoSeguro);
        pagoCapital = sumar(pagoCapital, min(restante, restar(saldoInicial, pagoCapital)));
        return new Reparto(pagoCobranza, pagoMora, pagoInteres, new Servicios(pagoFondo, pagoAsistencia, pagoSeguro),
                pagoCapital);
    }

    void actualizarSaldos(BigDecimal saldoInicial, Causacion c, Reparto r, boolean sinPagoSinMora) {
        s.cxcCobranza = noNegativo(restar(sumar(s.cxcCobranza, c.cobranza()), r.cobranza()));
        s.cxcMora = noNegativo(restar(sumar(s.cxcMora, c.mora()), r.mora()));
        s.cxcInteres = noNegativo(restar(sumar(s.cxcInteres, c.interes()), r.interes()));
        s.cxcServicios = s.cxcServicios.mas(c.servicios()).menos(r.servicios()).noNegativo();
        s.saldo = noNegativo(restar(saldoInicial, r.capital()));
        if (!sinPagoSinMora) {
            s.capitalVencido = noNegativo(restar(sumar(s.capitalVencido, c.capitalContractual()), r.capital()));
            if (esCero(s.capitalVencido)) {
                s.capitalVencido = CERO;
            }
        }
        s.saldoPlan = noNegativo(restar(s.saldoPlan, c.capitalContractual()));
    }

    /** Cómo se repartió el pago por prelación. */
    record Reparto(BigDecimal cobranza, BigDecimal mora, BigDecimal interes, Servicios servicios,
            BigDecimal capital) {
    }
}
