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
        assertThat(caso9(ReglasNegocio.EXCEL).get(43).interesMora()).isCloseTo(new BigDecimal("1590.46"), offset(UN_PESO));
        assertThat(caso9(ReglasNegocio.EXCEL.conFormulaMora(FormulaMora.SOLO_VENCIDO)).get(43).interesMora()).isZero();
    }

    @Test
    void elInteresSobreSaldoVigenteNoCobraInteresAlCapitalVencido() {
        Movimiento excel = caso8(ReglasNegocio.EXCEL).get(44);
        Movimiento vigente = caso8(ReglasNegocio.EXCEL.conBaseInteres(BaseInteres.SALDO_VIGENTE)).get(44);
        BigDecimal tasa = excel.tasaSemanal();
        assertThat(vigente.interesCorriente())
                .isCloseTo(excel.saldoInicial().subtract(new BigDecimal("65272.89")).multiply(tasa), offset(UN_PESO));
        assertThat(vigente.interesCorriente()).isLessThan(excel.interesCorriente());
    }

    @Test
    void conCapitalAntesDeServiciosElPagoInferiorAbonaACapital() {
        Movimiento excel = caso8(ReglasNegocio.EXCEL).get(43);
        Movimiento capitalPrimero = caso8(ReglasNegocio.EXCEL.conPrelacion(OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS)).get(43);
        assertThat(excel.pagoCapital()).isZero();
        assertThat(capitalPrimero.pagoInteresCorriente()).isCloseTo(new BigDecimal("45741.93"), offset(UN_PESO));
        assertThat(capitalPrimero.pagoCapital()).isCloseTo(new BigDecimal("23877.67"), offset(UN_PESO));
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
        assertThat(m.get(86).serviciosCausados().fcc()).isLessThan(m.get(1).serviciosCausados().fcc());
        assertThat(m.get(86).serviciosCausados().asistencia()).isEqualByComparingTo(m.get(1).serviciosCausados().asistencia());
    }

    @Test
    void losServiciosDespuesDelPlazoSeCausanMientrasHayaSaldo() {
        assertThat(caso8(ReglasNegocio.EXCEL).get(87).serviciosCausados().total()).isZero();
        assertThat(caso8(ReglasNegocio.EXCEL.conServiciosDespuesDelPlazo(true)).get(87).serviciosCausados().total()).isPositive();
    }
}
