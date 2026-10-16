package co.financiera.cartera.nucleo.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.BaseInteres;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.BaseServicios;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.FormulaMora;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.OrdenPrelacion;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cada opción de {@link ReglasNegocio} cambia solo lo que dice; {@code EXCEL} es el comportamiento probado contra el Excel. */
class ReglasNegocioTest {

    private static final BigDecimal UN_PESO = BigDecimal.ONE;

    private static List<Movimiento> caso8(ReglasNegocio reglas) {
        return new MotorCartera().ejecutar(new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel().conPlazoLimite(100))
                .en(43, new PagoParcial(new BigDecimal("0.4"), 0)).conReglas(reglas));
    }

    private static List<Movimiento> caso9(ReglasNegocio reglas) {
        return new MotorCartera().ejecutar(new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel())
                .desde(43, new PagoContractual(14)).conReglas(reglas));
    }

    private static BigDecimal total(List<Movimiento> movimientos, java.util.function.Function<Movimiento, BigDecimal> dato) {
        return movimientos.stream().map(dato).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void conTopeCeroNoSeCausaCobranza() {
        List<Movimiento> m = caso9(ReglasNegocio.EXCEL.conTopeCobranza(BigDecimal.ZERO));
        assertThat(total(m, Movimiento::cobranzaCausada)).isZero();
        assertThat(total(caso9(ReglasNegocio.EXCEL), Movimiento::cobranzaCausada)).isPositive();
    }

    @Test
    void conTopeLaCobranzaPorPagoNoLoSupera() {
        List<Movimiento> m = caso9(ReglasNegocio.EXCEL.conTopeCobranza(new BigDecimal("10000")));
        assertThat(m).allSatisfy(x -> assertThat(x.cobranzaCausada()).isLessThanOrEqualTo(new BigDecimal("10000")));
        assertThat(m.get(43).cobranzaCausada()).isEqualByComparingTo("10000");
    }

    @Test
    void laMoraSoloSobreVencidoNoCobraLosDiasDeLaCuota() {
        assertThat(caso9(ReglasNegocio.EXCEL).get(43).interesMora()).isCloseTo(new BigDecimal("1158.34"), offset(UN_PESO));
        assertThat(caso9(ReglasNegocio.EXCEL.conFormulaMora(FormulaMora.SOLO_VENCIDO)).get(43).interesMora()).isZero();
    }

    @Test
    void elExcelNoCobraInteresCorrienteAlCapitalVencidoYLaVarianteSi() {
        // P-03: el Excel causa el interés corriente solo sobre el capital vigente (Caso8 E = (D − N anterior) × tasa).
        Movimiento excel = caso8(ReglasNegocio.EXCEL).get(44);
        Movimiento total = caso8(ReglasNegocio.EXCEL.conBaseInteres(BaseInteres.SALDO_TOTAL)).get(44);
        BigDecimal tasa = excel.tasaPeriodica();
        assertThat(excel.interesCorriente())
                .isCloseTo(excel.saldoInicial().subtract(new BigDecimal("38436.48")).multiply(tasa), offset(UN_PESO));
        assertThat(total.interesCorriente()).isGreaterThan(excel.interesCorriente());
    }

    @Test
    void conCapitalAntesDeServiciosElPagoInferiorAbonaACapital() {
        Movimiento excel = caso8(ReglasNegocio.EXCEL).get(43);
        Movimiento capitalPrimero = caso8(ReglasNegocio.EXCEL.conPrelacion(OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS)).get(43);
        // Excel (Inputs ⑥): del 65.376,18 van 52.989,93 a interés, 3.406,38 al seguro y 8.979,86 a capital.
        assertThat(excel.pagoServicios()).isCloseTo(new BigDecimal("3406.38"), offset(UN_PESO));
        assertThat(excel.pagoCapital()).isCloseTo(new BigDecimal("8979.86"), offset(UN_PESO));
        assertThat(capitalPrimero.pagoInteresCorriente()).isCloseTo(new BigDecimal("52989.93"), offset(UN_PESO));
        assertThat(capitalPrimero.pagoCapital()).isCloseTo(new BigDecimal("12386.25"), offset(UN_PESO));
        assertThat(capitalPrimero.pagoServicios()).isZero();
    }

    @Test
    void conCapitalAntesDeServiciosUnPagoCompletoEsIgualAlDelExcel() {
        List<Movimiento> excel = new MotorCartera().ejecutar(new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel()));
        List<Movimiento> otra = new MotorCartera().ejecutar(new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel())
                .conReglas(ReglasNegocio.EXCEL.conPrelacion(OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS)));
        assertThat(otra).hasSameSizeAs(excel);
        for (int i = 0; i < excel.size(); i++) {
            assertThat(otra.get(i).pagoCapital()).isCloseTo(excel.get(i).pagoCapital(), offset(UN_PESO));
            assertThat(otra.get(i).pagoServicios()).isCloseTo(excel.get(i).pagoServicios(), offset(UN_PESO));
        }
    }

    @Test
    void losServiciosSobreSaldoBajanConElCapitalYLaAsistenciaNo() {
        List<Movimiento> m = new MotorCartera().ejecutar(new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel())
                .conReglas(ReglasNegocio.EXCEL.conBaseServicios(BaseServicios.SALDO)));
        assertThat(m.get(86).serviciosCausados().fondoGarantias()).isLessThan(m.get(1).serviciosCausados().fondoGarantias());
        assertThat(m.get(86).serviciosCausados().asistencia()).isEqualByComparingTo(m.get(1).serviciosCausados().asistencia());
    }

    @Test
    void losServiciosDespuesDelPlazoSeCausanMientrasHayaSaldo() {
        // El caso 9 sigue con saldo después de la cuota 104 (horizonte de 120 periodos).
        assertThat(caso9(ReglasNegocio.EXCEL).get(105).serviciosCausados().total()).isZero();
        assertThat(caso9(ReglasNegocio.EXCEL.conServiciosDespuesDelPlazo(true)).get(105).serviciosCausados().total()).isPositive();
    }
}
