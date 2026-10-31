package co.financiera.cartera.auditoria;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadCambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.TipoRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.UsuraVigente;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regresión de los hallazgos de la auditoría financiera del 2026-10-23 (H1, H3, H4, H5, H6, H9). Cada prueba
 * reproduce el caso que la auditoría encontró; antes de los arreglos fallaban.
 */
class HallazgosAuditoriaTest {

    private static final MotorCartera MOTOR = new MotorCartera();
    private static final ParametrosProducto P = ParametrosProducto.replicaExcel();

    private static Escenario excel() {
        return new Escenario(Operacion.ejemploExcel(), P).conReglas(ReglasNegocio.EXCEL);
    }

    /** Débito neto a Caja de cada periodo según los asientos. */
    static Map<Integer, BigDecimal> cajaPorPeriodo(List<Asiento> asientos) {
        Map<Integer, BigDecimal> caja = new HashMap<>();
        for (Asiento a : asientos) {
            for (Asiento.Linea l : a.lineas()) {
                if (l.cuenta() == Cuenta.CAJA) {
                    caja.merge(a.periodo(), l.debito().subtract(l.credito()), Calc::sumar);
                }
            }
        }
        return caja;
    }

    // ---------------------------------------------------------------- H1

    @Test
    void h1UltimaCuotaResidualPagadaConPagoValorSeContabilizaUnaSolaVez() {
        Escenario e = excel().en(10, new AbonoExtra(bd(5_300_000))).en(14, new PagoValor(bd(72_470), 0));
        List<Movimiento> movs = MOTOR.ejecutar(e);
        Movimiento ultimo = movs.getLast();
        assertThat(ultimo.periodo()).isEqualTo(14);
        assertThat(ultimo.pagoRecibido()).isCloseTo(new BigDecimal("72469.65"), within(new BigDecimal("0.01")));

        Map<Integer, BigDecimal> caja = cajaPorPeriodo(Contabilizador.para(ReglasNegocio.EXCEL).contabilizar(movs));
        // Antes: la Caja del periodo 14 quedaba debitada 144.939,30 (RecaudoCuota y PagoInferior a la vez).
        for (Movimiento m : movs.subList(1, movs.size())) {
            assertThat(caja.getOrDefault(m.periodo(), Calc.CERO))
                    .as("caja del periodo %d", m.periodo())
                    .isCloseTo(m.pagoTotalCliente(), within(new BigDecimal("0.01")));
        }
    }

    // ---------------------------------------------------------------- H3

    @Test
    void h3PrepagoConCapitalVencidoExtingueElVencidoYNoSigueCausandoMora() {
        List<Movimiento> movs = MOTOR.ejecutar(excel().en(5, new SinPago(true)).en(10, new PrepagoTotal()));
        Movimiento ultimo = movs.getLast();
        // Antes: el crédito seguía 100+ periodos causando mora y servicios sobre un vencido sin saldo.
        assertThat(ultimo.periodo()).isLessThanOrEqualTo(11);
        assertThat(ultimo.estado()).isEqualTo(EstadoCredito.CANCELADO);
        assertThat(movs.get(10).capitalVencido().signum()).isZero();
        sinNegativosYVencidoAcotado(movs);
        assertThat(Contabilizador.para(ReglasNegocio.EXCEL).contabilizar(movs)).isNotEmpty();
    }

    @Test
    void h3AbonoSoloCapitalMayorQueElVigenteReduceElVencido() {
        List<Movimiento> movs = MOTOR.ejecutar(excel().entre(3, 6, new SinPago(true)).en(7, new AbonoExtra(bd(5_000_000))));
        sinNegativosYVencidoAcotado(movs);
        assertThat(movs.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
        Map<Integer, BigDecimal> caja = cajaPorPeriodo(Contabilizador.para(ReglasNegocio.EXCEL).contabilizar(movs));
        assertThat(caja.get(7)).isCloseTo(movs.get(7).pagoTotalCliente(), within(new BigDecimal("0.01")));

        // Repro de la prueba de propiedades: el abono (SOLO_CAPITAL) supera el capital vigente y, antes, el saldo
        // del plan quedaba negativo y el capital contractual de la última cuota salía en −2.621.031.
        Operacion o = new Operacion(bd(31_289_111), bd(171_100), bd(373_300), bd(120_000), bd(80_000), bd(60_000))
                .conPagoInicial(new BigDecimal("8520827.1705"));
        List<Movimiento> grande = MOTOR.ejecutar(new Escenario(o, ParametrosProducto.de(LineaCredito.CPPR, Periodicidad.SEMANAL, 14))
                .conReglas(ReglasNegocio.EXCEL).en(4, new SinPago(true)).en(10, new SinPago(false))
                .en(13, new AbonoExtra(new BigDecimal("4412806.41288240"))));
        sinNegativosYVencidoAcotado(grande);
        Map<Integer, BigDecimal> cajaGrande = cajaPorPeriodo(Contabilizador.para(ReglasNegocio.EXCEL).contabilizar(grande));
        for (Movimiento m : grande.subList(1, grande.size())) {
            assertThat(cajaGrande.getOrDefault(m.periodo(), Calc.CERO)).isCloseTo(m.pagoTotalCliente(),
                    within(new BigDecimal("0.01")));
        }
    }

    private static void sinNegativosYVencidoAcotado(List<Movimiento> movs) {
        for (Movimiento m : movs) {
            assertThat(m.capitalContractual().signum()).as("capital contractual del periodo %d", m.periodo()).isNotNegative();
            assertThat(m.saldoFinal().signum()).as("saldo del periodo %d", m.periodo()).isNotNegative();
            assertThat(m.capitalVencido().subtract(m.saldoFinal()))
                    .as("capital vencido ≤ saldo en el periodo %d", m.periodo())
                    .isLessThanOrEqualTo(new BigDecimal("0.01"));
        }
    }

    // ---------------------------------------------------------------- H4

    @Test
    void h4InstruccionesImposiblesSeRechazanEnElNucleo() {
        assertThatThrownBy(() -> new PagoValor(bd(-1_000_000), 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
        assertThatThrownBy(() -> new AbonoExtra(bd(-1_000_000))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("abono");
        assertThatThrownBy(() -> new AbonoExtra(bd(0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagoParcial(bd("-0.5"), 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fracción");
        assertThatThrownBy(() -> new PagoParcial(bd(0), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagoParcial(bd("1.01"), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CambioTasa(bd("-0.5"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tasa");
        assertThatThrownBy(() -> new CambioTasa(bd(0), ModalidadCambioTasa.MISMA_CUOTA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CierrePorRecuperacion(TipoRecuperacion.DACION_EN_PAGO, bd(-1_000_000)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("moto");
        assertThatThrownBy(() -> new CierrePorRecuperacion(TipoRecuperacion.RETOMA, bd(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagoContractual(-1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("días");
        assertThatThrownBy(() -> new PagoParcial(bd("0.4"), -2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagoValor(bd(100), -3)).isInstanceOf(IllegalArgumentException.class);
        // Los límites válidos siguen aceptándose.
        assertThatCode(() -> new PagoParcial(bd(1), 0)).doesNotThrowAnyException();
        assertThatCode(() -> new PagoValor(bd(0), 0)).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- H5

    @Test
    void h5DosPagosEnElMismoPeriodoSeRechazanNombrandoElPeriodo() {
        Escenario e = excel().en(5, new PagoValor(bd(50_000), 0)).en(5, new PagoValor(bd(80_000), 0));
        assertThatThrownBy(() -> MOTOR.ejecutar(e)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodo 5");
    }

    @Test
    void h5DosAbonosEnElMismoPeriodoSeRechazan() {
        Escenario e = excel().en(5, new AbonoExtra(bd(100_000))).en(5, new AbonoExtra(bd(200_000)));
        assertThatThrownBy(() -> MOTOR.ejecutar(e)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodo 5");
    }

    @Test
    void h5DosUsurasEnElMismoPeriodoSeRechazan() {
        Escenario e = excel().en(5, new UsuraVigente(bd("0.5"))).en(5, new UsuraVigente(bd("0.4")));
        assertThatThrownBy(() -> MOTOR.ejecutar(e)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodo 5");
    }

    // ---------------------------------------------------------------- H6

    @Test
    void h6CambioDeTasaDespuesDeLaUltimaCuotaNoExplota() {
        ParametrosProducto mensual = ParametrosProducto.de(LineaCredito.BAJO_MONTO, Periodicidad.MENSUAL, 11);
        Escenario e = new Escenario(Operacion.ejemploExcel(), mensual).conReglas(ReglasNegocio.EXCEL)
                .en(6, new PagoParcial(bd("0.654"), 0)).en(12, new CambioTasa(bd("0.4178")));
        List<Movimiento> movs = MOTOR.ejecutar(e);
        Movimiento p12 = movs.get(12);
        // La tasa nueva queda registrada (la periódica baja), pero la cuota no se recalcula: no quedan cuotas.
        assertThat(p12.tasaPeriodica()).isLessThan(movs.get(11).tasaPeriodica());
        assertThat(p12.cuotaFinanciera()).isEqualByComparingTo(movs.get(11).cuotaFinanciera());
        sinNegativosYVencidoAcotado(movs);
    }

    @Test
    void h6CambioDeTasaMismaCuotaDespuesDeLaUltimaCuotaNoReabreElPlazo() {
        ParametrosProducto mensual = ParametrosProducto.de(LineaCredito.BAJO_MONTO, Periodicidad.MENSUAL, 11);
        Escenario e = new Escenario(Operacion.ejemploExcel(), mensual).conReglas(ReglasNegocio.EXCEL)
                .en(6, new PagoParcial(bd("0.654"), 0)).en(12, new CambioTasa(bd("0.4178"), ModalidadCambioTasa.MISMA_CUOTA));
        assertThatCode(() -> MOTOR.ejecutar(e)).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- H9

    @Test
    void h9AbonoMenorPlazoConTasaCeroNoCortaElPlan() {
        ParametrosProducto tasaCero = P.conTasaEA(bd(0));
        List<Movimiento> movs = MOTOR.ejecutar(new Escenario(Operacion.ejemploExcel(), tasaCero)
                .conReglas(ReglasNegocio.EXCEL).en(10, new AbonoExtra(bd(1_000_000))));
        Movimiento ultimo = movs.getLast();
        assertThat(ultimo.estado()).isEqualTo(EstadoCredito.CANCELADO);
        // Antes: NPER daba NaN → 0 cuotas y el plan terminaba en el periodo del abono sin cobrar el capital.
        BigDecimal capitalContractual = movs.stream().map(Movimiento::capitalContractual).reduce(Calc.CERO, Calc::sumar);
        BigDecimal abonos = movs.stream().map(Movimiento::abonoExtra).reduce(Calc.CERO, Calc::sumar);
        assertThat(Calc.sumar(capitalContractual, abonos))
                .isCloseTo(Operacion.ejemploExcel().montoFinanciado(), within(new BigDecimal("0.01")));
        for (Movimiento m : movs.subList(11, movs.size())) {
            assertThat(m.capitalVencido().signum()).as("periodo %d al día", m.periodo()).isZero();
        }
    }

    // ---------------------------------------------------------------- H2, H7 y H8 (respuestas de la ronda 8, 2026-10-28)

    /** El perfil NEGOCIO de config/reglas-negocio.properties. */
    private static ReglasNegocio negocio() {
        try {
            return PropiedadesFinancierasAuditTest.negocio();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Cliente que paga cada periodo la cuota del ejemplo (163.440,4465) en pesos enteros: 163.440. */
    /** La cuota exigible de 163.440,4465 es sin el seguro todo riesgo de la ronda 9 (R9-SEGURO-TODO-RIESGO). */
    private static ReglasNegocio negocioSinTodoRiesgo() {
        return negocio().conSeguroTodoRiesgoPorcentajeMensual(BigDecimal.ZERO);
    }

    private static Escenario pagaEnPesosEnteros(ReglasNegocio reglas) {
        return new Escenario(Operacion.ejemploExcel(), P).conReglas(reglas).desde(1, new PagoValor(bd(163_440), 0));
    }

    @Test
    void h2SinRedondeoElPagoEnPesosEnterosQuedaComoPagoInferiorYLlegaARecuperacion() {
        List<Movimiento> movs = MOTOR.ejecutar(pagaEnPesosEnteros(negocioSinTodoRiesgo().conRedondeoCuotaAlPeso(false)));
        assertThat(movs.get(1).evento()).isEqualTo("Pago inferior");
        assertThat(movs.stream().anyMatch(Movimiento::listoParaRecuperacion)).isTrue();
    }

    @Test
    void h2ConRedondeoAlPesoElPagoEnPesosEnterosEsUnPagoNormalYElCreditoCierraEnLaCuotaN() {
        // R8-REDONDEO-PESO (Diego): la cuota exigible se redondea al peso (HALF_UP) y el residuo va al interés del periodo.
        List<Movimiento> movs = MOTOR.ejecutar(pagaEnPesosEnteros(negocioSinTodoRiesgo()));
        List<Movimiento> exacto = MOTOR.ejecutar(new Escenario(Operacion.ejemploExcel(), P)
                .conReglas(negocioSinTodoRiesgo().conRedondeoCuotaAlPeso(false)));
        assertThat(movs).hasSameSizeAs(exacto);
        for (int i = 1; i < movs.size(); i++) {
            Movimiento m = movs.get(i);
            assertThat(m.evento()).as("periodo %d", m.periodo()).isNotEqualTo("Pago inferior");
            assertThat(m.cuotasVencidas()).as("periodo %d", m.periodo()).isZero();
            assertThat(m.interesMora().signum()).as("periodo %d", m.periodo()).isZero();
            assertThat(m.listoParaRecuperacion()).isFalse();
            // La cuota exigible queda en pesos enteros.
            BigDecimal exigible = Calc.sumar(m.cuotaFinanciera(), m.serviciosCausados().total());
            assertThat(exigible.stripTrailingZeros().scale()).as("periodo %d", m.periodo()).isLessThanOrEqualTo(0);
            // El plan de capital y los servicios no cambian; el residuo (hasta medio peso) es del interés.
            assertThat(m.capitalContractual()).isEqualByComparingTo(exacto.get(i).capitalContractual());
            assertThat(m.serviciosCausados()).isEqualTo(exacto.get(i).serviciosCausados());
            assertThat(m.interesCorriente().subtract(exacto.get(i).interesCorriente()).abs())
                    .isLessThanOrEqualTo(new BigDecimal("0.5"));
        }
        assertThat(movs.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
        assertThat(movs.getLast().periodo()).isEqualTo(P.numeroCuotas());
    }

    @Test
    void h7ConSuspensionUnPagoIncompletoEnLaFechaCausaLosTresServicios() {
        // R8-PAGO-INCOMPLETO-EN-FECHA (Diego): la causación genera siempre fondo, asistencia y seguro, haya o no pago.
        BigDecimal valor = Calc.multiplicar(bd("0.4"), bd("163440.4465"));
        for (var pago : List.of(new PagoParcial(bd("0.4"), 0), new PagoValor(valor, 0))) {
            Movimiento tres = MOTOR.ejecutar(new Escenario(Operacion.ejemploExcel(), P).conReglas(negocio()).en(3, pago)).get(3);
            assertThat(tres.serviciosCausados().fondoGarantias()).as("%s", pago).isPositive();
            assertThat(tres.serviciosCausados().asistencia()).as("%s", pago).isPositive();
            assertThat(tres.serviciosCausados().seguro()).as("%s", pago).isPositive();
        }
        // Con la causación del Excel (SOLO_AL_DIA, D17) el pago parcial declarado solo causa el seguro.
        Movimiento d17 = MOTOR.ejecutar(new Escenario(Operacion.ejemploExcel(), P)
                .conReglas(negocio().conCausacionServicios(ReglasNegocio.CausacionServicios.SOLO_AL_DIA))
                .en(3, new PagoParcial(bd("0.4"), 0))).get(3);
        assertThat(d17.serviciosCausados().fondoGarantias()).isZero();
        assertThat(d17.serviciosCausados().asistencia()).isZero();
    }

    @Test
    void h8ConSobranteElPagoMayorQueLoExigibleSeRecibeCompletoYQuedaComoCuentaPorPagar() {
        // R8-PAGO-MAYOR-EXIGIBLE (Diego): el sobrante queda como CxP al cliente para devolverlo.
        BigDecimal valor = bd(99_000_000);
        List<Movimiento> movs = MOTOR.ejecutar(new Escenario(Operacion.ejemploExcel(), P).conReglas(negocio())
                .en(5, new PagoValor(valor, 0)));
        Movimiento cinco = movs.getLast();
        assertThat(cinco.periodo()).isEqualTo(5);
        assertThat(cinco.estado()).isEqualTo(EstadoCredito.CANCELADO);
        assertThat(cinco.sobranteCliente()).isPositive();
        assertThat(Calc.sumar(cinco.pagoRecibido(), cinco.sobranteCliente())).isCloseTo(valor, within(new BigDecimal("0.000001")));
        assertThat(cinco.pagoTotalCliente()).isCloseTo(valor, within(new BigDecimal("0.000001")));
        List<Asiento> asientos = Contabilizador.para(negocio()).contabilizar(movs);
        BigDecimal cxp = asientos.stream().filter(a -> a.periodo() == 5).flatMap(a -> a.lineas().stream())
                .filter(l -> l.cuenta() == Cuenta.CXP_SOBRANTE_CLIENTE).map(Asiento.Linea::credito).reduce(Calc.CERO, Calc::sumar);
        assertThat(cxp).isEqualByComparingTo(cinco.sobranteCliente());
        assertThat(asientos).allMatch(Asiento::cuadra);
        assertThat(cajaPorPeriodo(asientos).get(5)).isCloseTo(valor, within(new BigDecimal("0.000001")));

        // Sin la clave (el Excel, H8): se recorta a lo exigible y no queda registro del sobrante.
        Movimiento recortado = MOTOR.ejecutar(new Escenario(Operacion.ejemploExcel(), P)
                .conReglas(negocio().conSobranteComoCuentaPorPagar(false)).en(5, new PagoValor(valor, 0))).getLast();
        assertThat(recortado.sobranteCliente()).isZero();
        assertThat(recortado.pagoRecibido()).isEqualByComparingTo(cinco.pagoRecibido()).isLessThan(valor);
        List<Movimiento> conExcel = MOTOR.ejecutar(excel().en(5, new PagoValor(valor, 0)));
        assertThat(conExcel.getLast().sobranteCliente()).isZero();
        assertThat(Contabilizador.para(ReglasNegocio.EXCEL).contabilizar(conExcel).stream().flatMap(a -> a.lineas().stream())
                .noneMatch(l -> l.cuenta() == Cuenta.CXP_SOBRANTE_CLIENTE)).isTrue();
    }
}
