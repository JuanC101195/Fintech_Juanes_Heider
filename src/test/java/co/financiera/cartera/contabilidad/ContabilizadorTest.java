package co.financiera.cartera.contabilidad;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.financiera.cartera.api.PerfilesReglas;
import co.financiera.cartera.api.PerfilesReglas.Perfil;
import co.financiera.cartera.contabilidad.Contabilizador.AsientoDescuadradoException;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** HT-06: el Contabilizador rechaza asientos descuadrados y todo lo que contabiliza el motor cuadra. */
class ContabilizadorTest {

    private static final Movimiento DESEMBOLSO = new MotorCartera()
            .ejecutar(EscenariosExcel.caso1(ParametrosProducto.replicaExcel())).getFirst();

    /** Regla de prueba que siempre devuelve un asiento con la diferencia débitos − créditos indicada. */
    private static ReglaContable conDiferencia(String diferencia) {
        return m -> Optional.of(new Asiento(m.periodo(), "Prueba", List.of(
                Asiento.Linea.debito(Cuenta.CAJA, bd("100").add(bd(diferencia)), "Débito"),
                Asiento.Linea.credito(Cuenta.CARTERA_VIGENTE, bd("100"), "Crédito"))));
    }

    @Test
    void unAsientoDescuadradoLanzaAsientoDescuadradoException() {
        Contabilizador contabilizador = new Contabilizador(List.of(conDiferencia("2")));

        assertThatThrownBy(() -> contabilizador.contabilizar(DESEMBOLSO))
                .isInstanceOf(AsientoDescuadradoException.class)
                .hasMessageContaining("periodo 0")
                .hasMessageContaining("Prueba")
                .hasMessageContaining("débitos 102")
                .hasMessageContaining("créditos 100");
    }

    @Test
    void laToleranciaEsUnPesoExactoYUnCentavoMasYaDescuadra() {
        assertThatCode(() -> new Contabilizador(List.of(conDiferencia("1"))).contabilizar(DESEMBOLSO))
                .doesNotThrowAnyException();
        assertThatCode(() -> new Contabilizador(List.of(conDiferencia("-1"))).contabilizar(DESEMBOLSO))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new Contabilizador(List.of(conDiferencia("1.01"))).contabilizar(DESEMBOLSO))
                .isInstanceOf(AsientoDescuadradoException.class);
        assertThatThrownBy(() -> new Contabilizador(List.of(conDiferencia("-1.01"))).contabilizar(DESEMBOLSO))
                .isInstanceOf(AsientoDescuadradoException.class);
    }

    @Test
    void unaReglaQueNoAplicaNoGeneraAsiento() {
        assertThat(new Contabilizador(List.of(m -> Optional.empty())).contabilizar(DESEMBOLSO)).isEmpty();
    }

    @Test
    void lasReglasSeCopianAlCrearElContabilizador() {
        List<ReglaContable> reglas = new ArrayList<>(List.of(conDiferencia("0")));
        Contabilizador contabilizador = new Contabilizador(reglas);
        reglas.add(conDiferencia("5")); // no debe afectar al contabilizador ya creado
        assertThat(contabilizador.contabilizar(List.of(DESEMBOLSO, DESEMBOLSO))).hasSize(2);
    }

    // ------------------------------------------------------------------ invariante en los 13 casos

    private static List<Function<ParametrosProducto, Escenario>> casos() {
        return List.of(EscenariosExcel::caso1, EscenariosExcel::caso2, p -> EscenariosExcel.caso3(p, false),
                EscenariosExcel::caso4, EscenariosExcel::caso5, EscenariosExcel::caso6, EscenariosExcel::caso7,
                EscenariosExcel::caso8, EscenariosExcel::caso9, EscenariosExcel::caso10, EscenariosExcel::caso11,
                EscenariosExcel::caso12, EscenariosExcel::caso13);
    }

    @ParameterizedTest
    @EnumSource(Periodicidad.class)
    void losDebitosIgualanLosCreditosEnLosTreceCasosConLasReglasDelExcelYDelNegocio(Periodicidad periodicidad) {
        ParametrosProducto parametros = ParametrosProducto.deMeses(LineaCredito.CPPU, periodicidad, 24);
        PerfilesReglas perfiles = new PerfilesReglas("config/reglas-negocio.properties");
        int asientos = 0;
        for (Perfil perfil : Perfil.values()) {
            ReglasNegocio reglas = perfiles.reglas(perfil);
            Contabilizador contabilizador = Contabilizador.para(reglas);
            for (int n = 0; n < casos().size(); n++) {
                Escenario escenario = casos().get(n).apply(parametros).conReglas(reglas);
                for (Movimiento m : new MotorCartera().ejecutar(escenario)) {
                    for (Asiento a : contabilizador.contabilizar(m)) {
                        String donde = periodicidad + " " + perfil + " caso " + (n + 1) + " periodo " + m.periodo();
                        assertThat(a.cuadra()).as(donde).isTrue();
                        assertThat(a.totalDebitos().subtract(a.totalCreditos()).abs()).as(donde)
                                .isLessThan(bd("0.000001"));
                        assertThat(a.lineas()).as(donde).allSatisfy(l -> {
                            assertThat(l.debito()).isNotNegative();
                            assertThat(l.credito()).isNotNegative();
                        });
                        asientos++;
                    }
                }
            }
        }
        assertThat(asientos).isPositive();
    }
}
