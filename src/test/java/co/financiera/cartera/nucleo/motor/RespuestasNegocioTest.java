package co.financiera.cartera.nucleo.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Reglas que salen de las respuestas de Diego del 2026-10-16 (docs/respuestas-diego-2026-10-16.md). */
class RespuestasNegocioTest {

    private final ParametrosProducto p = ParametrosProducto.replicaExcel();
    private final MotorCartera motor = new MotorCartera();

    @Test
    void p13SiElClienteNoEligeElAbonoReduceElPlazo() {
        assertThat(new AbonoExtra(Calc.bd(2_000_000)).modalidad()).isEqualTo(ModalidadAbono.MENOR_PLAZO);
        List<Movimiento> sinElegir = motor.ejecutar(new Escenario(Operacion.ejemploExcel(), p).en(43, new AbonoExtra(Calc.bd(2_000_000))));
        List<Movimiento> menorPlazo = motor.ejecutar(new Escenario(Operacion.ejemploExcel(), p)
                .en(43, new AbonoExtra(Calc.bd(2_000_000), ModalidadAbono.MENOR_PLAZO)));
        assertThat(sinElegir).isEqualTo(menorPlazo);
        assertThat(sinElegir.getLast().periodo()).isLessThan(86);
    }

    @Test
    void p14UnAbonoMayorQueElSaldoSeRechaza() {
        Escenario e = new Escenario(Operacion.ejemploExcel(), p).en(80, new AbonoExtra(Calc.bd(5_000_000)));
        assertThatThrownBy(() -> motor.ejecutar(e))
                .isInstanceOf(AbonoMayorQueElSaldoException.class)
                .hasMessageContaining("Semana 80");
    }

    @Test
    void p14UnAbonoIgualAlSaldoSiSeAcepta() {
        List<Movimiento> base = motor.ejecutar(new Escenario(Operacion.ejemploExcel(), p));
        BigDecimal saldo = base.get(80).saldoFinal();
        List<Movimiento> m = motor.ejecutar(new Escenario(Operacion.ejemploExcel(), p).en(80, new AbonoExtra(saldo)));
        assertThat(m.get(80).saldoFinal()).isCloseTo(BigDecimal.ZERO, org.assertj.core.data.Offset.offset(BigDecimal.ONE));
    }

    @Test
    void p16ConMoraAntesDeRecuperacionLaDacionCausaMoraComoLosCasos8y9() {
        ReglasNegocio reglas = ReglasNegocio.EXCEL.conMoraAntesDeRecuperacion(true);
        List<Movimiento> conRegla = motor.ejecutar(EscenariosExcel.caso3(p, false).conReglas(reglas));
        List<Movimiento> excel = motor.ejecutar(EscenariosExcel.caso3(p, false));
        List<Movimiento> causaMora = motor.ejecutar(EscenariosExcel.caso3(p, true));

        assertThat(conRegla).isEqualTo(causaMora);
        BigDecimal moraConRegla = conRegla.stream().map(Movimiento::interesMora).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal moraExcel = excel.stream().map(Movimiento::interesMora).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(moraExcel).isZero();
        assertThat(moraConRegla).isPositive();
        // La deuda que extingue la moto es mayor: incluye la mora y los servicios no pagados.
        assertThat(conRegla.getLast().deudaAExtinguir()).isGreaterThan(excel.getLast().deudaAExtinguir());
        Contabilizador.estandar().contabilizar(conRegla);
    }

    @Test
    void p16LaClaveSeLeeDeLaConfiguracion() {
        assertThat(ReglasNegocio.desde(Map.of("moraAntesDeRecuperacion", "true")).moraAntesDeRecuperacion()).isTrue();
        assertThat(ReglasNegocio.EXCEL.moraAntesDeRecuperacion()).isFalse();
        assertThat(ReglasNegocio.CLAVES).contains("moraAntesDeRecuperacion");
    }
}
