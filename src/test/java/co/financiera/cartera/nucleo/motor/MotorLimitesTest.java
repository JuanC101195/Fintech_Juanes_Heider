package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import co.financiera.cartera.nucleo.Amortizacion;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadCambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.AplicacionAbono;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.BaseInteres;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.OrdenPrelacion;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Límites exactos del motor, escritos contra los mutantes que sobrevivieron en la auditoría con PIT: el abono que
 * iguala el saldo y el que lo pasa por un centavo, la tolerancia de «pago completo», la cobranza con tope, el interés
 * de prepago por días, la prelación con capital antes de servicios y el recálculo de cuota o plazo de un abono.
 * Cada valor esperado sale de una corrida del mismo escenario sin el evento (o de una fórmula cerrada).
 */
class MotorLimitesTest {

    private static final BigDecimal UN_PESO = BigDecimal.ONE;
    private static final BigDecimal CENTAVO = new BigDecimal("0.01");

    private static Escenario base() {
        return new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel());
    }

    private static List<Movimiento> correr(Escenario escenario) {
        return new MotorCartera().ejecutar(escenario);
    }

    /** Deuda pendiente al cierre del periodo: capital vencido más todas las cuentas por cobrar. */
    private static BigDecimal pendiente(Movimiento m) {
        return m.capitalVencido().add(pendienteSinCapital(m));
    }

    private static BigDecimal pendienteSinCapital(Movimiento m) {
        return m.cxcCobranza().add(m.cxcMora()).add(m.cxcInteresCorriente()).add(m.cxcServicios());
    }

    private static Escenario conPendientes(ReglasNegocio reglas) {
        return base().conReglas(reglas).en(5, new SinPago(true));
    }

    // ------------------------------------------------------------------ abono contra el saldo (líneas ~446, 454)

    @Test
    void unAbonoIgualAlSaldoCancelaElCreditoYUnCentavoMasLoRechaza() {
        BigDecimal saldoTrasLaCuota = correr(base()).get(10).saldoFinal();

        List<Movimiento> exacto = correr(base().en(10, new AbonoExtra(saldoTrasLaCuota, ModalidadAbono.MENOR_PLAZO)));
        assertThat(exacto).hasSize(11);
        assertThat(exacto.get(10).estado()).isEqualTo(EstadoCredito.CANCELADO);
        assertThat(exacto.get(10).saldoFinal()).isZero();
        assertThat(exacto.get(10).abonoExtra()).isEqualByComparingTo(saldoTrasLaCuota);

        assertThatThrownBy(() -> correr(base().en(10, new AbonoExtra(saldoTrasLaCuota.add(CENTAVO), ModalidadAbono.MENOR_PLAZO))))
                .isInstanceOf(AbonoMayorQueElSaldoException.class)
                .satisfies(e -> {
                    AbonoMayorQueElSaldoException a = (AbonoMayorQueElSaldoException) e;
                    assertThat(a.periodo()).isEqualTo(10);
                    assertThat(a.saldo()).isEqualByComparingTo(saldoTrasLaCuota);
                    assertThat(a.abono()).isEqualByComparingTo(saldoTrasLaCuota.add(CENTAVO));
                });
    }

    @Test
    void elAbonoTieneUnaHolguraDeMedioCentavoSobreElSaldo() {
        BigDecimal saldoTrasLaCuota = correr(base()).get(10).saldoFinal();
        List<Movimiento> conHolgura = correr(
                base().en(10, new AbonoExtra(saldoTrasLaCuota.add(new BigDecimal("0.005")), ModalidadAbono.MENOR_CUOTA)));
        // Solo se aplica lo que hay de saldo: el crédito queda cancelado sin saldo negativo.
        assertThat(conHolgura.get(10).abonoExtra()).isEqualByComparingTo(saldoTrasLaCuota);
        assertThat(conHolgura.get(10).saldoFinal()).isZero();
        assertThat(conHolgura.get(10).estado()).isEqualTo(EstadoCredito.CANCELADO);
    }

    @Test
    void conPrimeroPendientesElMaximoDelAbonoIncluyeLoQueSeDebeDeSemanasAnteriores() {
        ReglasNegocio reglas = ReglasNegocio.EXCEL.conAplicacionAbono(AplicacionAbono.PRIMERO_PENDIENTES);
        Movimiento sinAbono = correr(conPendientes(reglas)).get(6);
        BigDecimal maximo = sinAbono.saldoFinal().add(pendienteSinCapital(sinAbono));
        assertThat(pendienteSinCapital(sinAbono)).isPositive();

        List<Movimiento> exacto = correr(conPendientes(reglas).en(6, new AbonoExtra(maximo, ModalidadAbono.MENOR_PLAZO)));
        assertThat(exacto.get(6).estado()).isEqualTo(EstadoCredito.CANCELADO);
        assertThat(exacto.get(6).saldoFinal()).isZero();
        assertThat(exacto.get(6).cuotasVencidas()).isZero();
        assertThat(exacto.get(6).pendientesPagadosExtra().total()).isCloseTo(pendiente(sinAbono), offset(CENTAVO));

        assertThatThrownBy(() -> correr(conPendientes(reglas).en(6, new AbonoExtra(maximo.add(CENTAVO), ModalidadAbono.MENOR_PLAZO))))
                .isInstanceOf(AbonoMayorQueElSaldoException.class);
    }

    @Test
    void conPrelacionCompletaUnAbonoQueSoloCubreLoPendienteNoToca_LosServiciosDelPeriodoCorriente() {
        ReglasNegocio reglas = ReglasNegocio.EXCEL.conAplicacionAbono(AplicacionAbono.PRELACION_COMPLETA);
        Escenario sinAbono = conPendientes(reglas).en(6, new PagoParcial(bd("0.4"), 0));
        BigDecimal debido = pendiente(correr(sinAbono).get(6));

        // Abono exacto a lo pendiente: no queda nada para el fondo ni la asistencia del periodo corriente.
        Movimiento exacto = correr(conPendientes(reglas).en(6, new PagoParcial(bd("0.4"), 0))
                .en(6, new AbonoExtra(debido, ModalidadAbono.MENOR_PLAZO))).get(6);
        assertThat(exacto.pendientesPagadosExtra().total()).isCloseTo(debido, offset(CENTAVO));
        assertThat(exacto.pendientesPagadosExtra().servicios().total()).isCloseTo(
                correr(sinAbono).get(6).cxcServicios(), offset(CENTAVO));
        assertThat(exacto.abonoExtra()).isZero();

        // Un peso de más va al fondo de garantías del periodo corriente (primero en la prelación), no a capital.
        Movimiento unPesoMas = correr(conPendientes(reglas).en(6, new PagoParcial(bd("0.4"), 0))
                .en(6, new AbonoExtra(debido.add(UN_PESO), ModalidadAbono.MENOR_PLAZO))).get(6);
        assertThat(unPesoMas.pendientesPagadosExtra().total()).isCloseTo(debido.add(UN_PESO), offset(CENTAVO));
        assertThat(unPesoMas.abonoExtra()).isZero();

        // Pagados el fondo y la asistencia del periodo, el sobrante sí es capital vigente.
        Servicios porCuota = Servicios.porCuota(Operacion.ejemploExcel().montoFinanciado(), ParametrosProducto.replicaExcel());
        BigDecimal corriente = porCuota.fondoGarantias().add(porCuota.asistencia());
        Movimiento conSobrante = correr(conPendientes(reglas).en(6, new PagoParcial(bd("0.4"), 0))
                .en(6, new AbonoExtra(debido.add(corriente).add(CENTAVO), ModalidadAbono.MENOR_PLAZO))).get(6);
        assertThat(conSobrante.abonoExtra()).isCloseTo(CENTAVO, offset(new BigDecimal("0.0001")));
    }

    // ------------------------------------------------------------------ pago completo (línea ~148) y su evento (708)

    @Test
    void unPagoAMedioCentavoDeLaCuotaTotalSigueSiendoCompletoYUnCentavoMenosYaEsInferior() {
        Movimiento normal = correr(base()).get(5);
        BigDecimal cuotaTotal = normal.cuotaFinanciera().add(normal.serviciosCausados().total());

        // Un faltante de 0,004 es ruido (esCero tolera hasta 0,005): el periodo cuenta como pagado.
        Movimiento justo = correr(base().en(5, new PagoValor(cuotaTotal.subtract(new BigDecimal("0.004")), 0))).get(5);
        assertThat(justo.evento()).isEqualTo("Pago normal");
        assertThat(justo.cuotasVencidas()).isZero();
        assertThat(justo.estado()).isEqualTo(EstadoCredito.VIGENTE_AL_DIA);

        Movimiento corto = correr(base().en(5, new PagoValor(cuotaTotal.subtract(CENTAVO), 0))).get(5);
        assertThat(corto.evento()).isEqualTo("Pago inferior");
        assertThat(corto.cuotasVencidas()).isEqualTo(1);
        assertThat(corto.estado()).isEqualTo(EstadoCredito.EN_MORA);
    }

    @Test
    void laFraccionUnoEsUnPagoCompletoYLaCuarentaPorCientoUnPagoInferior() {
        assertThat(correr(base().en(5, new PagoParcial(BigDecimal.ONE, 0))).get(5).evento()).isEqualTo("Pago normal");
        Movimiento inferior = correr(base().en(5, new PagoParcial(bd("0.4"), 0))).get(5);
        assertThat(inferior.evento()).isEqualTo("Pago inferior");
        assertThat(inferior.cuotasVencidas()).isEqualTo(1);
    }

    @Test
    void elEventoDeCadaPagoTienePrioridadYNombreEstables() {
        List<Movimiento> m = correr(base().en(5, new SinPago(true)).en(6, new PagoAlDia()).en(7, new PagoContractual(3)));
        assertThat(m.get(4).evento()).isEqualTo("Pago normal");
        assertThat(m.get(5).evento()).isEqualTo("Mora - sin pago");
        assertThat(m.get(6).evento()).isEqualTo("Normalización - paga mes + atrasado");
        assertThat(m.get(7).evento()).isEqualTo("Pago tardío + cobranza");
        // La última cuota liquida el saldo y no se confunde con un pago inferior.
        List<Movimiento> limpio = correr(base());
        assertThat(limpio.get(103).evento()).isEqualTo("Pago normal");
        assertThat(limpio.get(104).evento()).isEqualTo("Pago final");
        assertThat(limpio.get(104).estado()).isEqualTo(EstadoCredito.CANCELADO);
    }

    // ------------------------------------------------------------------ cobranza (línea ~361)

    @Test
    void laCobranzaEsDiasPorTresMilSoloConAtrasoYRespetaElTopeExacto() {
        assertThat(correr(base().en(10, new PagoContractual(0))).get(10).cobranzaCausada()).isZero();
        assertThat(correr(base().en(10, new PagoContractual(1))).get(10).cobranzaCausada()).isEqualByComparingTo("3000");
        assertThat(correr(base().en(10, new PagoContractual(14))).get(10).cobranzaCausada()).isEqualByComparingTo("42000");

        Escenario catorceDias = base().en(10, new PagoContractual(14));
        assertThat(correr(catorceDias.conReglas(ReglasNegocio.EXCEL.conTopeCobranza(bd("42000")))).get(10).cobranzaCausada())
                .isEqualByComparingTo("42000");
        assertThat(correr(base().en(10, new PagoContractual(14)).conReglas(ReglasNegocio.EXCEL.conTopeCobranza(bd("41999.99"))))
                .get(10).cobranzaCausada()).isEqualByComparingTo("41999.99");
        assertThat(correr(base().en(10, new PagoContractual(14)).conReglas(ReglasNegocio.EXCEL.conTopeCobranza(bd("50000"))))
                .get(10).cobranzaCausada()).isEqualByComparingTo("42000");
    }

    @Test
    void sinCobranzaActivaElAtrasoNoCausaCobranza() {
        assertThat(correr(base().conCobranzaActiva(false).en(10, new PagoContractual(14))).get(10).cobranzaCausada()).isZero();
    }

    // ------------------------------------------------------------------ cambio de tasa (líneas ~274, 278)

    @Test
    void siLaCuotaNoAlcanzaParaElInteresDeLaTasaNuevaNoAmortizaYSeRechaza() {
        Escenario imposible = base().en(11, new CambioTasa(bd("1000000"), ModalidadCambioTasa.MISMA_CUOTA));
        assertThatThrownBy(() -> correr(imposible))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no amortiza");
    }

    @Test
    void conservarLaCuotaConLaMismaTasaNoAgregaNiQuitaUnaCuotaPorRuidoDeDecimales() {
        // NPER da 94 en exacto; el ajuste de 1e-9 evita que 94,0000000001 se redondee hacia arriba a 95.
        BigDecimal tasaActual = ParametrosProducto.replicaExcel().tasaEA();
        List<Movimiento> m = correr(base().en(11, new CambioTasa(tasaActual, ModalidadCambioTasa.MISMA_CUOTA)));
        assertThat(m.getLast().periodo()).isEqualTo(104);
        assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
    }

    // ------------------------------------------------------------------ abono: cuota o plazo nuevos (líneas ~462, 475)

    @Test
    void conMenorCuotaLaCuotaNuevaAmortizaElSaldoRestanteEnLasCuotasQueQuedan() {
        List<Movimiento> m = correr(base().en(10, new AbonoExtra(bd(2_000_000), ModalidadAbono.MENOR_CUOTA)));
        BigDecimal esperada = Amortizacion.cuotaFija(m.get(10).tasaPeriodica(), 104 - 10, m.get(10).saldoFinal());
        assertThat(m.get(11).cuotaFinanciera()).isCloseTo(esperada, offset(UN_PESO));
        assertThat(m.get(10).cuotaFinanciera()).isGreaterThan(m.get(11).cuotaFinanciera());
        assertThat(m.getLast().periodo()).isEqualTo(104);
    }

    @Test
    void unAbonoAMenorCuotaEnLaPenultimaCuotaDejaUnaSolaCuotaQueCancelaElSaldo() {
        List<Movimiento> m = correr(base().en(103, new AbonoExtra(bd(1000), ModalidadAbono.MENOR_CUOTA)));
        BigDecimal esperada = m.get(103).saldoFinal().multiply(BigDecimal.ONE.add(m.get(103).tasaPeriodica()));
        assertThat(m.get(104).cuotaFinanciera()).isCloseTo(esperada, offset(UN_PESO));
        assertThat(m.get(104).estado()).isEqualTo(EstadoCredito.CANCELADO);
    }

    @Test
    void unAbonoQueCancelaTodoNoRecalculaLaCuota() {
        BigDecimal saldoTrasLaCuota = correr(base()).get(10).saldoFinal();
        List<Movimiento> m = correr(base().en(10, new AbonoExtra(saldoTrasLaCuota, ModalidadAbono.MENOR_CUOTA)));
        assertThat(m.getLast().periodo()).isEqualTo(10);
        assertThat(m.getLast().estado()).isEqualTo(EstadoCredito.CANCELADO);
    }

    @Test
    void conPrimeroPendientesLaCuotaNuevaSoloAmortizaElCapitalVigenteYSinEllaTodoElSaldo() {
        // El abono (mora e interés pendientes + 1.000) no alcanza a tocar el capital vencido.
        ReglasNegocio primero = ReglasNegocio.EXCEL.conAplicacionAbono(AplicacionAbono.PRIMERO_PENDIENTES);
        Movimiento sinAbono = correr(conPendientes(primero)).get(6);
        BigDecimal soloIntereses = sinAbono.cxcCobranza().add(sinAbono.cxcMora()).add(sinAbono.cxcInteresCorriente());
        assertThat(sinAbono.capitalVencido()).isPositive();

        List<Movimiento> m = correr(conPendientes(primero).en(6, new AbonoExtra(soloIntereses.add(bd(1000)), ModalidadAbono.MENOR_CUOTA)));
        BigDecimal vigente = m.get(6).saldoFinal().subtract(m.get(6).capitalVencido());
        assertThat(m.get(6).capitalVencido()).isPositive();
        assertThat(m.get(7).cuotaFinanciera())
                .isCloseTo(Amortizacion.cuotaFija(m.get(6).tasaPeriodica(), 104 - 6, vigente), offset(UN_PESO));

        List<Movimiento> todo = correr(conPendientes(ReglasNegocio.EXCEL).en(6, new AbonoExtra(bd(1000), ModalidadAbono.MENOR_CUOTA)));
        assertThat(todo.get(7).cuotaFinanciera())
                .isCloseTo(Amortizacion.cuotaFija(todo.get(6).tasaPeriodica(), 104 - 6, todo.get(6).saldoFinal()), offset(UN_PESO));
    }

    @Test
    void conMenorPlazoLosServiciosTerminanConElNuevoPlazoAunqueElClienteSeAtrase() {
        // SRV-2: el abono acorta el plazo y el nuevo último periodo cubre los servicios; el siguiente ya no los causa.
        int nuevoPlazo = correr(base().en(10, new AbonoExtra(bd(2_000_000), ModalidadAbono.MENOR_PLAZO))).getLast().periodo();
        assertThat(nuevoPlazo).isLessThan(104);

        List<Movimiento> m = correr(base().en(10, new AbonoExtra(bd(2_000_000), ModalidadAbono.MENOR_PLAZO))
                .desde(11, new SinPago(true)));
        assertThat(m.get(nuevoPlazo).serviciosCausados().total()).isPositive();
        assertThat(m.get(nuevoPlazo + 1).serviciosCausados().total()).isZero();
        assertThat(m.get(nuevoPlazo + 1).evento()).isEqualTo("Mora - sin pago");
        assertThat(m.get(nuevoPlazo + 1).capitalContractual()).isZero();
    }

    @Test
    void sinAbonoElPlazoNoSeAcortaYLosServiciosSiguenHastaLaUltimaCuota() {
        List<Movimiento> m = correr(base().en(10, new SinPago(true)).desde(11, new SinPago(true)));
        assertThat(m.get(104).serviciosCausados().total()).isPositive();
        assertThat(m.get(105).serviciosCausados().total()).isZero();
    }

    // ------------------------------------------------------------------ interés del prepago por días (líneas ~555, 556)

    private static double interesEsperado(BigDecimal saldo, int dias) {
        // (1 + tasa diaria)^días − 1 con la tasa diaria de (1 + EA)^(1/365).
        double factor = Math.pow(1 + ParametrosProducto.replicaExcel().tasaEA().doubleValue(), dias / 365.0) - 1;
        return saldo.doubleValue() * factor;
    }

    @Test
    void elPrepagoCobraElInteresDeLosDiasSoloSiLaReglaLoPideYHayDias() {
        BigDecimal saldo = correr(base()).get(10).saldoFinal();
        ReglasNegocio porDias = ReglasNegocio.EXCEL.conInteresPrepagoPorDias(true);

        assertThat(correr(base().conReglas(porDias).en(10, new PrepagoTotal(15))).get(10).interesPrepago().doubleValue())
                .isCloseTo(interesEsperado(saldo, 15), offset(0.01));
        assertThat(correr(base().conReglas(porDias).en(10, new PrepagoTotal(1))).get(10).interesPrepago().doubleValue())
                .isCloseTo(interesEsperado(saldo, 1), offset(0.01));
        assertThat(correr(base().conReglas(porDias).en(10, new PrepagoTotal(1))).get(10).interesPrepago()).isPositive();
        assertThat(correr(base().conReglas(porDias).en(10, new PrepagoTotal(0))).get(10).interesPrepago()).isZero();
        // Con el Excel (regla apagada) los días no cobran nada.
        assertThat(correr(base().en(10, new PrepagoTotal(15))).get(10).interesPrepago()).isZero();
    }

    @Test
    void elInteresDelPrepagoSobreSaldoVigenteExcluyeElCapitalVencidoYSobreSaldoTotalLoIncluye() {
        ReglasNegocio vigente = ReglasNegocio.EXCEL.conInteresPrepagoPorDias(true).conBaseInteres(BaseInteres.SALDO_VIGENTE);
        ReglasNegocio total = ReglasNegocio.EXCEL.conInteresPrepagoPorDias(true).conBaseInteres(BaseInteres.SALDO_TOTAL);
        Movimiento antes = correr(conPendientes(vigente)).get(6);
        assertThat(antes.capitalVencido()).isPositive();

        double sobreVigente = interesEsperado(antes.saldoFinal().subtract(antes.capitalVencido()), 10);
        double sobreTotal = interesEsperado(antes.saldoFinal(), 10);
        assertThat(sobreTotal).isGreaterThan(sobreVigente);

        assertThat(correr(conPendientes(vigente).en(6, new PrepagoTotal(10))).get(6).interesPrepago().doubleValue())
                .isCloseTo(sobreVigente, offset(0.01));
        assertThat(correr(conPendientes(total).en(6, new PrepagoTotal(10))).get(6).interesPrepago().doubleValue())
                .isCloseTo(sobreTotal, offset(0.01));
    }

    // ------------------------------------------------------------------ prelación con capital antes de servicios

    @Test
    void conCapitalAntesDeServiciosElAbonoPagaElCapitalVencidoAntesQueLosServiciosPendientes() {
        ReglasNegocio servicios = ReglasNegocio.EXCEL.conAplicacionAbono(AplicacionAbono.PRIMERO_PENDIENTES);
        ReglasNegocio capital = servicios.conPrelacion(OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS);
        Movimiento antes = correr(conPendientes(servicios)).get(6);
        // Lo que pagan mora e interés pendientes, más 1.000 que decide entre servicios y capital vencido.
        BigDecimal hastaInteres = antes.cxcCobranza().add(antes.cxcMora()).add(antes.cxcInteresCorriente());
        assertThat(antes.cxcServicios()).isGreaterThan(bd(1000));
        assertThat(antes.capitalVencido()).isGreaterThan(bd(1000));
        BigDecimal abono = hastaInteres.add(bd(1000));

        Movimiento conServicios = correr(conPendientes(servicios).en(6, new AbonoExtra(abono, ModalidadAbono.MENOR_PLAZO))).get(6);
        assertThat(conServicios.pendientesPagadosExtra().capital()).isZero();
        assertThat(conServicios.pendientesPagadosExtra().servicios().total()).isEqualByComparingTo("1000");

        Movimiento conCapital = correr(conPendientes(capital).en(6, new AbonoExtra(abono, ModalidadAbono.MENOR_PLAZO))).get(6);
        assertThat(conCapital.pendientesPagadosExtra().capital()).isEqualByComparingTo("1000");
        assertThat(conCapital.pendientesPagadosExtra().servicios().total()).isZero();
    }

    @Test
    void conCapitalAntesDeServiciosUnPagoParcialAdelantaElCapitalDeLaCuotaYSinEllaVanPrimeroLosServicios() {
        // Con 115.000 (la cuota total son 163.440), lo que sobra después del interés decide entre servicios y capital.
        Movimiento servicios = correr(base().en(5, new PagoValor(bd(115_000), 0))).get(5);
        Movimiento capital = correr(base().conReglas(ReglasNegocio.EXCEL.conPrelacion(OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS))
                .en(5, new PagoValor(bd(115_000), 0))).get(5);
        assertThat(servicios.pagoRecibido()).isEqualByComparingTo(capital.pagoRecibido());
        assertThat(servicios.pagoCapital()).isZero();
        assertThat(capital.pagoCapital()).isCloseTo(capital.capitalContractual(), offset(CENTAVO));
        assertThat(capital.pagoServicios()).isLessThan(servicios.pagoServicios());
    }
}
