package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.VigenciasCobranza;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Respuestas de Diego del 2026-10-31 a las rondas 10 y 11 que se implementan sin ambigüedad (D65 a D69 de
 * decisiones.md): valor de cobranza definido por el director financiero (R10-VALOR-COBRANZA), marca de recuperación
 * la primera vez (R10-MARCA-RECUPERACION), base del 0,3 % = monto financiado a secas (R11-BASE-FINANCIACION-INICIAL),
 * periodización igual que el seguro de vida (R11-PERIODIZACION-SEGURO) y contabilidad del seguro con su IVA
 * (R11-CONTABILIDAD-SEGURO-IVA; el ejemplo 10.000 / 1.900 / 11.900 está en {@code SeguroIvaContabilidadTest}).
 */
class RespuestasDiegoRonda10y11Test {

    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.0001");
    private final MotorCartera motor = new MotorCartera();

    private static ReglasNegocio negocio() {
        try {
            return ReglasNegocio.desde(ReglasNegocioConfiguradaTest.configuracion());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Movimiento periodoUno(List<Movimiento> m) {
        return m.stream().filter(x -> x.periodo() == 1).findFirst().orElseThrow();
    }

    private static Escenario conOperacionDelExcel() {
        return new Escenario(Operacion.ejemploExcel(), P);
    }

    /** R10-VALOR-COBRANZA: el valor lo define el director financiero con vigencias; los 3.000 y 40.000 son provisionales. */
    @Test
    void elValorDeCobranzaLoDefineElDirectorFinancieroPorVigencias() {
        assertThat(negocio().cobranzaDiaria()).isEqualByComparingTo(bd(3_000));
        assertThat(negocio().cobranzaPorPeriodo()).isEqualByComparingTo(bd(40_000));
        VigenciasCobranza tabla = VigenciasCobranza.vacia()
                .conVigencia(LocalDate.of(2026, 11, 1), bd(5_000))
                .conVigencia(LocalDate.of(2027, 1, 1), bd(6_500));
        // Un crédito toma el valor vigente al originarse; antes de la primera vigencia, el provisional.
        assertThat(tabla.valorEn(LocalDate.of(2026, 10, 31), bd(3_000))).isEqualByComparingTo(bd(3_000));
        assertThat(tabla.valorEn(LocalDate.of(2026, 12, 15), bd(3_000))).isEqualByComparingTo(bd(5_000));
        assertThat(tabla.valorEn(LocalDate.of(2027, 1, 1), bd(3_000))).isEqualByComparingTo(bd(6_500));
    }

    /** R10-MARCA-RECUPERACION = primera-vez: la marca no se quita aunque el cliente se ponga al día. */
    @Test
    void laMarcaDeRecuperacionSeDaLaPrimeraVezYNoSeQuitaAlPonerseAlDia() {
        assertThat(negocio().cuotasVencidasParaRecuperacion()).isEqualTo(2);
        List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().entre(1, 2, new SinPago(true))
                .desde(3, new PagoAlDia()).conReglas(negocio()));
        int primera = m.stream().filter(Movimiento::listoParaRecuperacion).mapToInt(Movimiento::periodo).min().orElseThrow();
        assertThat(primera).isLessThanOrEqualTo(3);
        // La marca dura hasta que el crédito cierra (el último periodo la apaga: D54).
        int ultimo = m.stream().mapToInt(Movimiento::periodo).max().orElseThrow();
        assertThat(m.stream().filter(x -> x.periodo() >= primera && x.periodo() < ultimo)).isNotEmpty()
                .allMatch(Movimiento::listoParaRecuperacion);
        assertThat(m.stream().filter(x -> x.periodo() < primera)).noneMatch(Movimiento::listoParaRecuperacion);
        // Un cliente que siempre paga al día nunca queda marcado.
        assertThat(motor.ejecutar(conOperacionDelExcel().conReglas(negocio()))).noneMatch(Movimiento::listoParaRecuperacion);
    }

    /** R11-BASE-FINANCIACION-INICIAL = monto-financiado: la base del 0,3 % es el monto financiado a secas. */
    @Test
    void laBaseDelSeguroTodoRiesgoEsElMontoFinanciadoASecas() {
        // Ejemplo de la pregunta: moto 6.000.000, inicial 1.000.000: financiado 5.000.000; 0,3 % = 15.000 al mes sin IVA.
        ParametrosProducto mensual = ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.MENSUAL, 24);
        Servicios s = Servicios.porCuota(bd(5_000_000), mensual, new BigDecimal("0.003"));
        assertThat(s.seguroTodoRiesgo()).isEqualByComparingTo(bd(15_000));
        assertThat(s.ivaSeguroTodoRiesgo()).isEqualByComparingTo(bd(2_850));
        // Con la operación del Excel, la base es su monto financiado (5.904.400) y no se le suma nada más.
        BigDecimal monto = Operacion.ejemploExcel().montoFinanciado();
        assertThat(monto).isEqualByComparingTo(bd(5_904_400));
        Movimiento uno = periodoUno(motor.ejecutar(conOperacionDelExcel().conReglas(negocio())));
        BigDecimal esperado = monto.multiply(new BigDecimal("0.003")).multiply(bd(12)).divide(bd(52), 10, RoundingMode.HALF_UP);
        assertThat(uno.serviciosCausados().seguroTodoRiesgo()).isCloseTo(esperado, within(TOLERANCIA));
    }

    /** R11-PERIODIZACION-SEGURO = igual-que-vida: mismo factor de conversión mensual a semanal, quincenal y mensual. */
    @Test
    void elSeguroTodoRiesgoSePeriodizaIgualQueElDeVida() {
        BigDecimal monto = bd(5_904_400);
        for (Periodicidad per : Periodicidad.values()) {
            ParametrosProducto p = ParametrosProducto.de(LineaCredito.CPPU, per, per == Periodicidad.SEMANAL ? 52 : 24);
            Servicios s = Servicios.porCuota(monto, p, new BigDecimal("0.0025"));
            // Con el mismo porcentaje que el de vida (0,25 %), los dos valen igual en cada periodicidad.
            assertThat(s.seguroTodoRiesgo()).as("%s", per).isCloseTo(s.seguro(), within(TOLERANCIA));
        }
        assertThat(Servicios.porCuota(monto, P, new BigDecimal("0.003")).seguroTodoRiesgo())
                .isCloseTo(new BigDecimal("4087.6615"), within(TOLERANCIA));
    }

    /** R11-CONTABILIDAD-SEGURO-IVA con la corrida del motor: segmenta seguro e IVA, cuadra, IVA = 19 % del seguro. */
    @Test
    void laCuotaDeNegocioSegmentaElSeguroYElIvaYCuadra() {
        List<Movimiento> m = motor.ejecutar(conOperacionDelExcel().conReglas(negocio()));
        Asiento cuota = Contabilizador.para(negocio()).contabilizar(periodoUno(m)).getFirst();
        assertThat(cuota.cuadra()).isTrue();
        BigDecimal seguro = periodoUno(m).serviciosCausados().seguroTodoRiesgo();
        BigDecimal iva = periodoUno(m).serviciosCausados().ivaSeguroTodoRiesgo();
        assertThat(cuota.lineas()).filteredOn(l -> l.cuenta() == Cuenta.CXP_SEGUROS).singleElement()
                .satisfies(l -> assertThat(l.credito()).isCloseTo(seguro, within(TOLERANCIA)));
        assertThat(cuota.lineas()).filteredOn(l -> l.cuenta() == Cuenta.IVA_SEGUROS_POR_PAGAR).singleElement()
                .satisfies(l -> assertThat(l.credito()).isCloseTo(iva, within(TOLERANCIA)));
        assertThat(iva).isCloseTo(seguro.multiply(new BigDecimal("0.19")), within(TOLERANCIA));
        assertThat(Cuenta.IVA_SEGUROS_POR_PAGAR.nombreExcel()).contains("no a la DIAN");
    }
}
