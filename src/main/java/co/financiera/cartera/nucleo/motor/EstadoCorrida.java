package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.CERO;
import static co.financiera.cartera.nucleo.Calc.esCero;

import co.financiera.cartera.nucleo.Amortizacion;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.Tasas;
import java.math.BigDecimal;

/**
 * Contexto explícito de una corrida del motor: el escenario y los saldos del crédito que cambian periodo a periodo.
 * Lo comparten {@code MotorCartera.Corrida} y las piezas extraídas de ella (causación, reparto por prelación, abonos,
 * recuperación), que leen y escriben estos campos en el mismo orden en que antes lo hacía la Corrida. Es del paquete:
 * no sale del motor.
 */
final class EstadoCorrida {
    final Escenario escenario;
    final ParametrosProducto p;
    final Servicios serviciosPorCuota;

    Tasas tasas;
    BigDecimal cuota;
    BigDecimal saldo;
    /** Cuotas del plan vigente: cambian cuando un cambio de tasa conserva la cuota (caso 10). */
    int cuotasVigentes;
    BigDecimal capitalVencido = CERO;
    BigDecimal cxcInteres = CERO;
    BigDecimal cxcMora = CERO;
    Servicios cxcServicios = Servicios.NINGUNO;
    BigDecimal cxcCobranza = CERO;
    /** Saldo del plan vigente si el cliente pagara siempre a tiempo: de él sale el capital contractual. */
    BigDecimal saldoPlan;
    /** Lo que el abono extra o el prepago de la semana pagó de pendientes (A4). */
    DeudaExtinguida pendientesPagadosExtra = DeudaExtinguida.NINGUNA;
    BigDecimal interesPrepago = CERO;
    /**
     * R4-CASO13 (PRELACION_COMPLETA): fondo y asistencia del periodo corriente que no se causaron porque
     * el periodo no se pagó al día y que el excedente del abono pagó por prelación.
     */
    Servicios serviciosCorrientesExtra = Servicios.NINGUNO;
    /**
     * R8-PAGO-MAYOR-EXIGIBLE: lo que un {@code PagoValor} del periodo trajo por encima de lo exigible y queda como cuenta
     * por pagar al cliente (con {@code sobranteComoCuentaPorPagar}). Transitorio: el reparto lo reinicia cada periodo.
     */
    BigDecimal sobranteCliente = CERO;

    EstadoCorrida(Escenario escenario, int cuotasPactadas) {
        this.escenario = escenario;
        this.p = escenario.parametros();
        this.cuotasVigentes = cuotasPactadas;
        BigDecimal monto = escenario.operacion().montoFinanciado();
        this.serviciosPorCuota = Servicios.porCuota(monto, p);
        this.tasas = Tasas.de(p);
        this.cuota = Amortizacion.cuotaFija(tasas.periodica(), cuotasPactadas, monto);
        this.saldo = monto;
        this.saldoPlan = monto;
    }

    /**
     * Saldos restaurados de una {@link Instantanea} (escala: reanudar sin volver a correr desde el desembolso). Lo
     * transitorio de un periodo (pendientes pagados con el abono, interés del prepago, servicios corrientes del
     * excedente) no se guarda: el periodo lo reinicia antes de usarlo.
     */
    EstadoCorrida(Escenario escenario, Instantanea i) {
        this.escenario = escenario;
        this.p = escenario.parametros();
        this.serviciosPorCuota = Servicios.porCuota(escenario.operacion().montoFinanciado(), p);
        this.tasas = i.tasas();
        this.cuota = i.cuota();
        this.saldo = i.saldo();
        this.saldoPlan = i.saldoPlan();
        this.cuotasVigentes = i.cuotasVigentes();
        this.capitalVencido = i.capitalVencido();
        this.cxcInteres = i.cxcInteres();
        this.cxcMora = i.cxcMora();
        this.cxcServicios = i.cxcServicios();
        this.cxcCobranza = i.cxcCobranza();
    }

    ReglasNegocio reglas() {
        return escenario.reglas();
    }

    /** Pendientes de semanas anteriores distintos del capital: cobranza, mora, interés y servicios. */
    BigDecimal pendientesSinCapital() {
        return Calc.sumar(cxcCobranza, cxcMora, cxcInteres, cxcServicios.total());
    }

    /**
     * Cuotas de valor {@code cuota} que hacen falta para llevar {@code base} a cero con la tasa vigente,
     * NPER = −ln(1 − r·S/C) / ln(1 + r) redondeado hacia arriba (la última se ajusta). Lo usan el caso 10
     * y el abono a menor plazo (SRV-2).
     */
    int cuotasHastaSaldoCero(BigDecimal base) {
        if (base.signum() <= 0 || esCero(base)) {
            return 0;
        }
        if (tasas.periodica().signum() == 0) {
            // Auditoría 2026-10-23 (H9): con tasa 0 la fórmula da 0/0 = NaN y (int) NaN = 0 cuotas, lo que
            // cortaba el plan. Sin interés el número de cuotas es saldo / cuota redondeado hacia arriba.
            return Calc.dividir(base, cuota).setScale(0, java.math.RoundingMode.CEILING).intValueExact();
        }
        double r = tasas.periodica().doubleValue();
        double proporcion = r * base.doubleValue() / cuota.doubleValue();
        if (proporcion >= 1) {
            throw new IllegalArgumentException("Con la tasa nueva la cuota no alcanza para cubrir el interés: no amortiza");
        }
        // StrictMath: mismo resultado bit a bit en cualquier máquina (ver Calc.potencia).
        return (int) Math.ceil(-StrictMath.log(1 - proporcion) / StrictMath.log(1 + r) - 1e-9);
    }
}
