package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.financiera.cartera.caracterizacion.Huellas;
import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Instantánea del motor (escala, D1): se extrae al cierre de un periodo, se guarda en el formato explícito
 * {@link FormatoInstantanea} y se reanuda con {@link MotorCartera#ejecutarDesde}. La equivalencia masiva está en
 * {@code ReanudacionEquivalenteTest}; aquí van el formato y los bordes.
 */
class InstantaneaTest {

    private static final MotorCartera MOTOR = new MotorCartera();

    private static Instantanea hasta(Escenario e, int periodo) {
        return MOTOR.ejecutarHasta(e, periodo, m -> { });
    }

    @Test
    void elTextoSeLeeYSeVuelveAEscribirIgual() {
        Escenario e = EscenariosExcel.caso8(ParametrosProducto.replicaExcel());
        Instantanea i = hasta(e, 20).conVersionReglas("EXCEL@2026-10-23");
        String texto = i.aTexto();
        Instantanea leida = Instantanea.leer(texto);
        assertThat(leida.aTexto()).isEqualTo(texto);
        assertThat(leida).isEqualTo(i); // sin calendario el record completo es igual, escalas incluidas
        assertThat(texto).startsWith("formato=motor-cartera.instantanea\nversion=1\nperiodo=20\n");
        assertThat(leida.versionReglas()).isEqualTo("EXCEL@2026-10-23");
    }

    @Test
    void conservaElCalendarioYLaUsuraPublicada() {
        ParametrosProducto p = ParametrosProducto.deMeses(LineaCredito.CPPU, Periodicidad.MENSUAL, 24);
        CalendarioPagos cal = CalendarioPagos.mensual(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 12, 5));
        Escenario e = new Escenario(co.financiera.cartera.nucleo.Operacion.ejemploExcel(), p).conCalendario(cal)
                .en(6, new Instruccion.UsuraVigente(bd("0.40")));
        Instantanea i = hasta(e, 6);
        assertThat(i.usuraPorAplicar()).isEqualByComparingTo("0.40");
        Instantanea leida = Instantanea.leer(i.aTexto());
        assertThat(leida.calendario().desembolso()).isEqualTo(cal.desembolso());
        assertThat(leida.calendario().primerVencimiento()).isEqualTo(cal.primerVencimiento());
        assertThat(leida.aTexto()).isEqualTo(i.aTexto());
    }

    @Test
    void elDineroConservaSuEscalaAunNegativa() {
        for (String v : List.of("0", "0.00", "1.0E+10", "123.4500", "0E-30", "5904400")) {
            BigDecimal original = new BigDecimal(v);
            BigDecimal leido = new BigDecimal(FormatoInstantanea.texto(original));
            assertThat(leido).as(v).isEqualTo(original); // equals de BigDecimal compara también la escala
        }
        assertThat(FormatoInstantanea.texto(new BigDecimal("0.00"))).isEqualTo("0.00");
    }

    @Test
    void laLecturaEsEstricta() {
        String texto = hasta(EscenariosExcel.caso1(ParametrosProducto.replicaExcel()), 3).aTexto();
        assertThatThrownBy(() -> Instantanea.leer(texto.replace("version=1", "version=2")))
                .hasMessageContaining("Versión de instantánea no soportada");
        assertThatThrownBy(() -> Instantanea.leer(texto.replace("formato=motor-cartera.instantanea", "formato=otro")))
                .hasMessageContaining("No es una instantánea");
        assertThatThrownBy(() -> Instantanea.leer(texto + "campoNuevo=1\n")).hasMessageContaining("Claves desconocidas");
        assertThatThrownBy(() -> Instantanea.leer(texto.replaceAll("(?m)^saldoPlan=.*\n", "")))
                .hasMessageContaining("Falta la clave saldoPlan");
        assertThatThrownBy(() -> Instantanea.leer(texto + "saldo=1\n")).hasMessageContaining("Clave repetida");
        // Con CRLF (un archivo editado en Windows) se lee igual.
        assertThat(Instantanea.leer(texto.replace("\n", "\r\n")).aTexto()).isEqualTo(texto);
    }

    @Test
    void reanudarDaLasMismasFilasQueLaCorridaCompleta() {
        Escenario e = EscenariosExcel.caso9(ParametrosProducto.replicaExcel());
        List<Movimiento> completa = MOTOR.ejecutar(e);
        Instantanea i = Instantanea.leer(hasta(e, 30).aTexto());
        List<Movimiento> reanudada = MOTOR.ejecutarDesde(i, e.reglas(), e::instruccionesDe);
        assertThat(reanudada).isEqualTo(completa.subList(31, completa.size()));
        assertThat(reanudada.stream().map(Huellas::texto).toList())
                .isEqualTo(completa.subList(31, completa.size()).stream().map(Huellas::texto).toList());
    }

    @Test
    void elDesembolsoSoloSaleEnLaCorridaNuevaYUnCreditoTerminadoNoProduceFilas() {
        Escenario e = EscenariosExcel.caso5(ParametrosProducto.replicaExcel());
        List<Movimiento> filas = new ArrayList<>();
        Instantanea cero = MOTOR.ejecutarHasta(e, 0, filas::add);
        assertThat(filas).extracting(Movimiento::periodo).containsExactly(0);
        assertThat(cero.periodo()).isZero();
        assertThat(MOTOR.ejecutarDesde(cero, e.reglas(), e::instruccionesDe).get(0).periodo()).isEqualTo(1);

        Instantanea fin = MOTOR.ejecutar(e, m -> { });
        assertThat(fin.terminado()).isTrue();
        assertThat(MOTOR.ejecutarDesde(fin, e.reglas(), e::instruccionesDe)).isEmpty();
    }

    @Test
    void laUsuraPublicadaEsUnaSolaPorPeriodo() {
        Escenario e = EscenariosExcel.caso1(ParametrosProducto.replicaExcel()).en(4, new Instruccion.UsuraVigente(bd("0.5")));
        Instantanea i = hasta(e, 4);
        assertThatThrownBy(() -> i.conUsuraPublicada(bd("0.4"))).hasMessageContaining("ya tiene una usura publicada");
        assertThatThrownBy(() -> hasta(e, 3).conUsuraPublicada(BigDecimal.ZERO)).hasMessageContaining("positiva");
    }
}
