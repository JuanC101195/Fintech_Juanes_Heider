package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * HT-02 · Invariantes del motor con pagos contractuales a tiempo, sobre créditos aleatorios
 * (semilla fija, reproducible): monto 1–40 millones, plazo 6–36 meses, EA 20 %–120 %, en cada
 * periodicidad del catálogo: semanal, quincenal y mensual (ADR 0002, paso 5).
 */
class Caso1InvariantesTest {

    private static final long SEMILLA = 20261008L;
    private static final int CREDITOS = 300;
    private static final BigDecimal UN_PESO = BigDecimal.ONE;

    private record Credito(BigDecimal monto, int plazoMeses, BigDecimal ea) {
        @Override
        public String toString() {
            return "monto " + monto.toPlainString() + ", " + plazoMeses + " meses, EA " + ea.toPlainString();
        }
    }

    private static List<Credito> creditosAleatorios() {
        Random azar = new Random(SEMILLA);
        List<Credito> creditos = new ArrayList<>();
        // Los extremos de los rangos siempre se prueban.
        creditos.add(new Credito(Calc.bd(1_000_000), 6, new BigDecimal("0.20")));
        creditos.add(new Credito(Calc.bd(40_000_000), 36, new BigDecimal("1.20")));
        creditos.add(new Credito(Calc.bd(1_000_000), 36, new BigDecimal("1.20")));
        creditos.add(new Credito(Calc.bd(40_000_000), 6, new BigDecimal("0.20")));
        while (creditos.size() < CREDITOS) {
            BigDecimal monto = Calc.bd(1_000_000L + azar.nextLong(39_000_000L));
            int plazo = 6 + azar.nextInt(31);
            BigDecimal ea = Calc.dividir(Calc.bd(2000 + azar.nextInt(10_001)), Calc.bd(10_000)); // 0,2000 a 1,2000
            creditos.add(new Credito(monto, plazo, ea));
        }
        return creditos;
    }

    /**
     * ADR 0002 paso 5: los mismos créditos en cada periodicidad. Las cuotas = ROUNDDOWN(meses × ppa / 12)
     * (PPER-3). Se arma con conNumeroCuotas para probar plazos de hasta 36 meses, más allá del máximo
     * comercial de 24 (PPER-2): son invariantes del cálculo, no del producto.
     */
    private static ParametrosProducto parametros(Credito c, Periodicidad per) {
        ParametrosProducto base = ParametrosProducto.de(LineaCredito.CPPU, per, 1).conTasaEA(c.ea());
        int cuotas = per.cuotasEnMeses(c.plazoMeses());
        // El horizonte debe alcanzar la última cuota (36 meses = 156 semanas > 120 por defecto).
        return base.conNumeroCuotas(cuotas).conPlazoLimite(cuotas + 10);
    }

    private static List<Movimiento> correr(Credito c, Periodicidad per) {
        Operacion op = new Operacion(c.monto(), Calc.CERO, Calc.CERO, Calc.CERO, Calc.CERO, Calc.CERO);
        return new MotorCartera().ejecutar(new Escenario(op, parametros(c, per)));
    }

    private static final Map<String, Function<Movimiento, BigDecimal>> SALDOS = Map.of(
            "saldo inicial", Movimiento::saldoInicial,
            "saldo final", Movimiento::saldoFinal,
            "capital vigente", Movimiento::capitalVigente,
            "capital vencido", Movimiento::capitalVencido,
            "CxC cobranza", Movimiento::cxcCobranza,
            "CxC mora", Movimiento::cxcMora,
            "CxC interés corriente", Movimiento::cxcInteresCorriente,
            "CxC servicios", Movimiento::cxcServicios);

    @ParameterizedTest
    @EnumSource(Periodicidad.class)
    void ningunSaldoEsNegativo(Periodicidad per) {
        List<String> errores = new ArrayList<>();
        for (Credito c : creditosAleatorios()) {
            for (Movimiento m : correr(c, per)) {
                SALDOS.forEach((nombre, saldo) -> {
                    if (saldo.apply(m).signum() < 0) {
                        errores.add(per + " · " + c + " · periodo " + m.periodo() + " · " + nombre + " = " + saldo.apply(m));
                    }
                });
            }
        }
        assertThat(errores).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Periodicidad.class)
    void elCronogramaCierraEnCeroEnLaCuotaPactada(Periodicidad per) {
        List<String> errores = new ArrayList<>();
        for (Credito c : creditosAleatorios()) {
            List<Movimiento> movs = correr(c, per);
            Movimiento ultimo = movs.getLast();
            int pactadas = Calendario.numeroCuotas(parametros(c, per));
            if (ultimo.periodo() != pactadas || ultimo.estado() != EstadoCredito.CANCELADO
                    || ultimo.saldoFinal().abs().compareTo(UN_PESO) > 0) {
                errores.add(per + " · " + c + " · termina en el periodo " + ultimo.periodo() + " de " + pactadas + " con saldo "
                        + ultimo.saldoFinal().toPlainString() + " y estado " + ultimo.estado());
            }
            // Antes de la última cuota el crédito no se cancela.
            if (movs.get(movs.size() - 2).estado() == EstadoCredito.CANCELADO) {
                errores.add(per + " · " + c + " · se canceló antes de la cuota pactada");
            }
        }
        assertThat(errores).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Periodicidad.class)
    void elPagoRecibidoEsLaSumaDeSuAplicacionPorPrelacion(Periodicidad per) {
        List<String> errores = new ArrayList<>();
        for (Credito c : creditosAleatorios()) {
            for (Movimiento m : correr(c, per)) {
                BigDecimal aplicado = Calc.sumar(m.pagoCobranza(), m.pagoMora(), m.pagoInteresCorriente(), m.pagoServicios(),
                        m.pagoCapital());
                if (Calc.restar(m.pagoRecibido(), aplicado).abs().compareTo(UN_PESO) > 0) {
                    errores.add(per + " · " + c + " · periodo " + m.periodo() + ": recibido " + m.pagoRecibido().toPlainString()
                            + ", aplicado " + aplicado.toPlainString());
                }
            }
        }
        assertThat(errores).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Periodicidad.class)
    void cadaCuotaContractualSeContabilizaComoRecaudoNormalYLaCarteraQuedaEnCero(Periodicidad per) {
        Contabilizador contabilizador = Contabilizador.estandar();
        List<String> errores = new ArrayList<>();
        for (Credito c : creditosAleatorios()) {
            List<Movimiento> movs = correr(c, per);
            List<Asiento> asientos = contabilizador.contabilizar(movs); // lanza si alguno descuadra (HT-06)
            BigDecimal caja = Calc.CERO;
            BigDecimal cartera = Calc.CERO;
            for (Asiento a : asientos) {
                for (Asiento.Linea l : a.lineas()) {
                    if (l.cuenta() == Cuenta.CAJA) {
                        caja = Calc.sumar(caja, l.debito());
                    }
                    if (l.cuenta() == Cuenta.CARTERA_VIGENTE) {
                        cartera = Calc.sumar(cartera, l.credito());
                    }
                }
            }
            BigDecimal recaudado = movs.stream().map(Movimiento::pagoRecibido).reduce(Calc.CERO, (x, y) -> Calc.sumar(x, y));
            if (asientos.size() != movs.size() - 1
                    || Calc.restar(caja, recaudado).abs().compareTo(UN_PESO) > 0
                    || Calc.restar(cartera, c.monto()).abs().compareTo(UN_PESO) > 0) {
                errores.add(per + " · " + c + " · asientos " + asientos.size() + ", caja " + caja.toPlainString() + " vs "
                        + recaudado.toPlainString() + ", cartera " + cartera.toPlainString());
            }
        }
        assertThat(errores).isEmpty();
    }
}
