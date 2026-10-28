package co.financiera.cartera.auditoria;

import static co.financiera.cartera.nucleo.Calc.bd;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.nucleo.Amortizacion;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Tasas;
import co.financiera.cartera.nucleo.motor.AbonoMayorQueElSaldoException;
import co.financiera.cartera.nucleo.motor.DeudaExtinguida;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.Instruccion;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadCambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
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
import java.io.FileReader;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Pruebas basadas en propiedades (auditoría). Genera escenarios aleatorios con semilla fija y verifica
 * invariantes financieros y contables en cada movimiento. Reporta todas las violaciones por invariante
 * (con el primer ejemplo reproducible) y falla si hay alguna.
 *
 * <p>Prueba permanente desde la auditoría del 2026-10-23 (hallazgos H1, H3, H6 en
 * {@code docs/pendientes-tecnicos.md}). Determinística: semilla fija, sin reloj; corre en pocos segundos. Ningún
 * invariante está excluido. Las instrucciones repetidas en un periodo no se generan porque el motor las rechaza (H5).
 */
class PropiedadesFinancierasAuditTest {

    // Por defecto 3.000 escenarios con semilla fija (rápido y determinista). Para cazar casos raros se puede subir la
    // escala sin tocar el código: ./mvnw test -Dtest=PropiedadesFinancierasAuditTest -Dpropiedades.escenarios=50000 -Dpropiedades.semilla=7
    private static final long SEMILLA = Long.getLong("propiedades.semilla", 20261023L);
    private static final int ESCENARIOS = Integer.getInteger("propiedades.escenarios", 3000);
    private static final BigDecimal TOL = new BigDecimal("0.01");
    private static final MotorCartera MOTOR = new MotorCartera();

    private final Map<String, Integer> conteo = new LinkedHashMap<>();
    private final Map<String, String> ejemplo = new LinkedHashMap<>();
    private final Map<String, Integer> excepciones = new LinkedHashMap<>();

    static ReglasNegocio negocio() throws Exception {
        Properties pr = new Properties();
        try (FileReader f = new FileReader("config/reglas-negocio.properties", java.nio.charset.StandardCharsets.UTF_8)) {
            pr.load(f);
        }
        Map<String, String> m = new HashMap<>();
        pr.stringPropertyNames().forEach(k -> m.put(k, pr.getProperty(k)));
        return ReglasNegocio.desde(m);
    }

    private static BigDecimal u(Random r, double a, double b) {
        return new BigDecimal(String.format(java.util.Locale.ROOT, "%.4f", a + (b - a) * r.nextDouble()));
    }

    private void violacion(String inv, String detalle, String repro) {
        conteo.merge(inv, 1, Integer::sum);
        ejemplo.putIfAbsent(inv, detalle + "\n      REPRO: " + repro);
    }

    /** Ranura de la instrucción en el periodo: el motor acepta una sola por ranura (H5); los cambios de tasa se apilan. */
    private static String tipo(Instruccion i) {
        if (i instanceof AbonoExtra) {
            return "abono";
        }
        if (i instanceof PrepagoTotal) {
            return "prepago";
        }
        if (i instanceof CierrePorRecuperacion) {
            return "cierre";
        }
        return i instanceof UsuraVigente ? "usura" : "pago";
    }

    private static boolean dif(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(TOL) > 0;
    }

    /**
     * Una combinación al azar de las reglas del negocio: parte del perfil EXCEL o del NEGOCIO y sortea cada clave de
     * {@link ReglasNegocio}. Los invariantes financieros y contables deben cumplirse con CUALQUIER combinación de
     * reglas, no solo con los dos perfiles que usamos hoy.
     */
    private static ReglasNegocio combinacion(Random r, ReglasNegocio neg) {
        ReglasNegocio x = r.nextBoolean() ? neg : ReglasNegocio.EXCEL;
        x = x.conPrelacion(ReglasNegocio.OrdenPrelacion.values()[r.nextInt(ReglasNegocio.OrdenPrelacion.values().length)])
                .conFormulaMora(ReglasNegocio.FormulaMora.values()[r.nextInt(ReglasNegocio.FormulaMora.values().length)])
                .conBaseInteres(ReglasNegocio.BaseInteres.values()[r.nextInt(ReglasNegocio.BaseInteres.values().length)])
                .conBaseServicios(ReglasNegocio.BaseServicios.values()[r.nextInt(ReglasNegocio.BaseServicios.values().length)])
                .conAplicacionAbono(ReglasNegocio.AplicacionAbono.values()[r.nextInt(ReglasNegocio.AplicacionAbono.values().length)])
                .conDiasMora(ReglasNegocio.DiasMora.values()[r.nextInt(ReglasNegocio.DiasMora.values().length)])
                .conServiciosDespuesDelPlazo(r.nextBoolean())
                .conMoraAntesDeRecuperacion(r.nextBoolean())
                .conInteresPrepagoPorDias(r.nextBoolean())
                .conCostoRecuperacionAlFondo(r.nextBoolean())
                .conCobranzaIngresoNoOperacional(r.nextBoolean())
                .conCambioTasaSobreCapitalVigente(r.nextBoolean())
                .conProrrateoPrimeraCuota(r.nextBoolean())
                .conModalidadUsura(r.nextBoolean() ? ModalidadCambioTasa.MISMA_CUOTA : ModalidadCambioTasa.MISMA_FECHA_FINAL)
                .conCuotasVencidasParaCobranza(r.nextBoolean() ? null : 1 + r.nextInt(3))
                .conCuotasVencidasParaRecuperacion(r.nextBoolean() ? null : 1 + r.nextInt(3));
        return x;
    }

    @Test
    void invariantesEnEscenariosAleatorios() throws Exception {
        corridaAleatoria(SEMILLA, ESCENARIOS, false);
    }

    @Test
    void invariantesConCombinacionesAleatoriasDeReglas() throws Exception {
        corridaAleatoria(SEMILLA + 1, ESCENARIOS, true);
    }

    private void corridaAleatoria(long semilla, int escenarios, boolean combinadas) throws Exception {
        ReglasNegocio neg = negocio();
        Random r = new Random(semilla);
        int corridos = 0;
        for (int k = 0; k < escenarios; k++) {
            StringBuilder repro = new StringBuilder();
            Periodicidad per = Periodicidad.values()[r.nextInt(3)];
            int max = per.cuotasEnMeses(24);
            int n = r.nextInt(8) == 0 ? 1 + r.nextInt(3) : 1 + r.nextInt(max);
            ParametrosProducto p = ParametrosProducto.de(LineaCredito.values()[r.nextInt(4)], per, n);
            boolean esNegocio = r.nextBoolean();
            ReglasNegocio reglas = combinadas ? combinacion(r, neg) : (esNegocio ? neg : ReglasNegocio.EXCEL);
            if (esNegocio || combinadas) {
                var lim = reglas.plazoLimitePeriodos(per, n);
                if (lim.isPresent()) {
                    p = p.conPlazoLimite(lim.getAsInt());
                }
            }
            BigDecimal moto = bd(200_000L + r.nextInt(40_000_000));
            Operacion o = new Operacion(moto, bd(171_100), bd(373_300), bd(120_000), bd(80_000), bd(60_000));
            if (r.nextInt(3) == 0) {
                o = o.conPagoInicial(Calc.multiplicar(o.valorTotal(), u(r, 0, 0.5)));
            }
            BigDecimal monto = o.montoFinanciado();
            boolean cobranza = r.nextBoolean();
            Escenario e = new Escenario(o, p).conReglas(reglas).conCobranzaActiva(cobranza);
            repro.append(String.format("per=%s n=%d tasa=%s reglas=%s moto=%s inicial=%s limite=%d cobranza=%s", per, n,
                    p.tasaEA(), combinadas ? reglas.toString() : (esNegocio ? "NEGOCIO" : "EXCEL"), moto, o.pagoInicial().toPlainString(),
                    p.plazoLimitePeriodos(), cobranza));
            if (r.nextBoolean()) {
                LocalDate des = LocalDate.of(2024, 1, 1).plusDays(r.nextInt(900));
                CalendarioPagos cal = switch (per) {
                    case SEMANAL -> {
                        LocalDate d = des.plusDays(3);
                        while (!DayOfWeek.TUESDAY.equals(d.getDayOfWeek())) {
                            d = d.plusDays(1);
                        }
                        yield CalendarioPagos.semanal(des, d);
                    }
                    case QUINCENAL -> {
                        LocalDate d = des.plusDays(1);
                        while (d.getDayOfMonth() != 5 && d.getDayOfMonth() != 20) {
                            d = d.plusDays(1);
                        }
                        yield CalendarioPagos.quincenal(des, d);
                    }
                    case MENSUAL -> {
                        LocalDate d = des.plusDays(1);
                        while (d.getDayOfMonth() != 5) {
                            d = d.plusDays(1);
                        }
                        yield CalendarioPagos.mensual(des, d);
                    }
                };
                e.conCalendario(cal);
                repro.append(" desembolso=").append(des);
            }
            BigDecimal cuota = Amortizacion.cuotaFija(Tasas.de(p).periodica(), n, monto);
            int eventos = r.nextInt(9);
            int horizonte = Math.min(p.plazoLimitePeriodos(), n + 6);
            java.util.Set<String> ocupados = new java.util.HashSet<>();
            for (int j = 0; j < eventos; j++) {
                int per1 = 1 + r.nextInt(horizonte);
                Instruccion ins = switch (r.nextInt(12)) {
                    case 0 -> new PagoContractual(r.nextInt(12));
                    case 1 -> new PagoParcial(u(r, 0.05, 0.95), r.nextInt(8));
                    case 2 -> new PagoValor(Calc.multiplicar(cuota, u(r, 0.1, 1.6)), r.nextInt(5));
                    case 3, 4 -> new SinPago(r.nextBoolean());
                    case 5 -> new PagoAlDia();
                    case 6 -> new AbonoExtra(Calc.multiplicar(monto, u(r, 0.01, 0.4)),
                            r.nextBoolean() ? ModalidadAbono.MENOR_PLAZO : ModalidadAbono.MENOR_CUOTA);
                    case 7 -> new PrepagoTotal(r.nextInt(7));
                    case 8 -> new CambioTasa(u(r, 0.15, 1.0),
                            r.nextBoolean() ? ModalidadCambioTasa.MISMA_FECHA_FINAL : ModalidadCambioTasa.MISMA_CUOTA);
                    case 9 -> new UsuraVigente(u(r, 0.3, 1.0));
                    case 10 -> new CierrePorRecuperacion(r.nextBoolean() ? TipoRecuperacion.DACION_EN_PAGO : TipoRecuperacion.RETOMA,
                            Calc.multiplicar(monto, u(r, 0.1, 1.5)), bd(r.nextInt(600_000)));
                    default -> new PagoParcial(u(r, 0.1, 0.9), 0);
                };
                boolean comoDesde = r.nextInt(10) == 0 && (ins instanceof PagoParcial || ins instanceof SinPago);
                if (comoDesde) {
                    e.desde(per1, ins);
                    repro.append(" | desde ").append(per1).append(": ").append(ins);
                } else if (ins instanceof CambioTasa || ocupados.add(per1 + ":" + tipo(ins))) {
                    // H5: el motor rechaza dos instrucciones del mismo tipo en un periodo; el generador no las arma
                    // (los sorteos no cambian, solo se omite la repetida). Los cambios de tasa sí se apilan.
                    e.en(per1, ins);
                    repro.append(" | en ").append(per1).append(": ").append(ins);
                }
            }
            List<Movimiento> movs;
            try {
                movs = MOTOR.ejecutar(e);
            } catch (AbonoMayorQueElSaldoException ex) {
                excepciones.merge("AbonoMayorQueElSaldo (rechazo legítimo D12)", 1, Integer::sum);
                continue;
            } catch (RuntimeException ex) {
                String clave = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()).replaceAll("[0-9.,]+", "#");
                excepciones.merge(clave, 1, Integer::sum);
                ejemplo.putIfAbsent("EXC " + clave, repro.toString());
                continue;
            }
            corridos++;
            verificar(movs, monto, e, repro.toString(), eventos == 0);
        }
        System.out.println("=== AUDITORIA" + (combinadas ? " (reglas combinadas)" : "") + ": escenarios corridos sin excepción = " + corridos + " / " + escenarios);
        excepciones.forEach((k, v) -> System.out.println("  excepción [" + v + "] " + k));
        conteo.forEach((k, v) -> System.out.println("  VIOLACIÓN [" + v + "] " + k + "\n    " + ejemplo.get(k)));
        ejemplo.forEach((k, v) -> {
            if (k.startsWith("EXC ")) {
                System.out.println("  " + k + "\n      REPRO: " + v);
            }
        });
        org.assertj.core.api.Assertions.assertThat(conteo).as("invariantes violados").isEmpty();
    }

    private void verificar(List<Movimiento> movs, BigDecimal monto, Escenario e, String repro, boolean contractual) {
        BigDecimal capitalPagado = Calc.CERO;
        BigDecimal sumaContractual = Calc.CERO;
        Movimiento prev = movs.get(0);
        for (int i = 1; i < movs.size(); i++) {
            Movimiento m = movs.get(i);
            String id = "periodo " + m.periodo() + " (" + m.evento() + ")";
            // I1 no negativos
            for (BigDecimal v : List.of(m.saldoFinal(), m.cxcCobranza(), m.cxcMora(), m.cxcInteresCorriente(), m.cxcServicios(),
                    m.capitalVencido(), m.pagoRecibido(), m.pagoCapital(), m.pagoInteresCorriente(), m.pagoMora(),
                    m.pagoCobranza(), m.pagoServicios(), m.abonoExtra(), m.prepagoCapital(), m.interesCorriente(),
                    m.interesMora(), m.capitalContractual(), m.cxcFondoGarantias(), m.cxpDeudor(), m.inventario())) {
                if (v.signum() < 0) {
                    violacion("I1 valor negativo", id + " valor " + v.toPlainString(), repro);
                }
            }
            // I8 (R7-COBRANZA-ACTIVA, Diego 2026-10-27): con umbral de cobranza, nunca se causa cobranza si el cliente
            // llegaba al periodo con menos cuotas vencidas que el umbral (con 2, empieza en la tercera cuota vencida).
            Integer umbral = e.reglas().cuotasVencidasParaCobranza();
            if (umbral != null && m.cobranzaCausada().signum() > 0 && prev.cuotasVencidas() < umbral) {
                violacion("I8 cobranza antes del umbral de cuotas vencidas", id + " cuotasVencidasPrevias=" + prev.cuotasVencidas()
                        + " cobranza=" + m.cobranzaCausada().toPlainString(), repro);
            }
            // I2 continuidad
            if (dif(m.saldoInicial(), prev.saldoFinal())) {
                violacion("I2 saldoInicial != saldoFinal anterior", id, repro);
            }
            // I3 pago recibido aplicado completo
            BigDecimal partes = Calc.sumar(m.pagoCobranza(), m.pagoMora(), m.pagoInteresCorriente(), m.pagoServicios(), m.pagoCapital());
            if (dif(m.pagoRecibido(), partes)) {
                violacion("I3 pagoRecibido != suma de la prelación", id + " recibido=" + m.pagoRecibido() + " partes=" + partes, repro);
            }
            DeudaExtinguida px = m.pendientesPagadosExtra();
            DeudaExtinguida ex = m.extinguido();
            // I4 conservación de capital del periodo
            BigDecimal bajaCapital = Calc.sumar(m.pagoCapital(), m.abonoExtra(), m.prepagoCapital(), px.capital(), ex.capital());
            if (dif(Calc.restar(m.saldoInicial(), bajaCapital), m.saldoFinal())) {
                violacion("I4 saldoInicial - capital aplicado != saldoFinal", id + " ini=" + m.saldoInicial().toPlainString()
                        + " baja=" + bajaCapital.toPlainString() + " fin=" + m.saldoFinal().toPlainString(), repro);
            }
            capitalPagado = Calc.sumar(capitalPagado, bajaCapital);
            sumaContractual = Calc.sumar(sumaContractual, m.capitalContractual());
            // I5 conservación por concepto (nada se cobra dos veces ni se pierde)
            chequeoConcepto("I5a cobranza", prev.cxcCobranza(), m.cobranzaCausada(), m.pagoCobranza(), px.cobranza(), ex.cobranza(), m.cxcCobranza(), id, repro);
            chequeoConcepto("I5b mora", prev.cxcMora(), m.interesMora(), m.pagoMora(), px.mora(), ex.mora(), m.cxcMora(), id, repro);
            chequeoConcepto("I5c interés corriente", prev.cxcInteresCorriente(), m.interesCorriente(), m.pagoInteresCorriente(),
                    px.interesCorriente(), ex.interesCorriente(), m.cxcInteresCorriente(), id, repro);
            chequeoConcepto("I5d servicios", prev.cxcServicios(), m.serviciosCausados().total(), m.pagoServicios(),
                    px.servicios().total(), ex.servicios().total(), m.cxcServicios(), id, repro);
            // I6 cierre: valor moto + fondo = deuda + costo - asumido + CxP deudor; lo extinguido = deuda
            if (m.estado() == EstadoCredito.CERRADO_POR_EVENTO) {
                BigDecimal izq = Calc.sumar(m.inventario(), m.cxcFondoGarantias());
                BigDecimal der = Calc.sumar(m.deudaAExtinguir(), m.costoRecuperacion(), m.cxpDeudor()).subtract(m.costoRecuperacionAsumido());
                if (dif(izq, der)) {
                    violacion("I6 ecuación del cierre", id + " moto+fondo=" + izq + " deuda+costo-asumido+cxp=" + der, repro);
                }
                if (dif(ex.total(), m.deudaAExtinguir())) {
                    violacion("I6b extinguido != deuda", id, repro);
                }
            }
            // I7 cancelado => todo en cero
            if (m.estado() == EstadoCredito.CANCELADO) {
                BigDecimal resto = Calc.sumar(m.saldoFinal(), m.cxcCobranza(), m.cxcMora(), m.cxcInteresCorriente(), m.cxcServicios());
                if (resto.compareTo(TOL) > 0) {
                    violacion("I7 CANCELADO con saldo/pendientes", id + " resto=" + resto, repro);
                }
            }
            // I8 capital vencido <= saldo
            if (m.capitalVencido().subtract(m.saldoInicial()).compareTo(TOL) > 0) {
                violacion("I8 capital vencido > saldo", id, repro);
            }
            prev = m;
        }
        Movimiento ultimo = movs.getLast();
        // I9 global: capital pagado + saldo final = monto financiado
        if (dif(Calc.sumar(capitalPagado, ultimo.saldoFinal()), monto)) {
            violacion("I9 capital pagado + saldo != monto", "pagado=" + capitalPagado + " saldo=" + ultimo.saldoFinal() + " monto=" + monto, repro);
        }
        // I10 crédito sin eventos: cierra en cero en la última cuota y la suma del capital contractual = monto
        if (contractual) {
            if (ultimo.estado() != EstadoCredito.CANCELADO || ultimo.periodo() != e.parametros().numeroCuotas()) {
                violacion("I10 plan contractual no cierra en la cuota n", "último periodo " + ultimo.periodo() + " estado "
                        + ultimo.estado() + " saldo " + ultimo.saldoFinal(), repro);
            }
            if (dif(sumaContractual, monto)) {
                violacion("I10b suma capital contractual != monto", sumaContractual + " vs " + monto, repro);
            }
        }
        // I11 contabilidad: cuadra al centavo y la caja de cada periodo = pago total del cliente
        List<Asiento> asientos;
        try {
            asientos = Contabilizador.para(e.reglas()).contabilizar(movs);
        } catch (RuntimeException ex) {
            violacion("I11 Contabilizador lanza excepción", ex.getMessage(), repro);
            return;
        }
        Map<Integer, BigDecimal> caja = new HashMap<>();
        for (Asiento a : asientos) {
            if (dif(a.totalDebitos(), a.totalCreditos())) {
                violacion("I11a asiento descuadrado > 0,01 (pero <= 1 COP)", "periodo " + a.periodo() + " " + a.evento()
                        + " D=" + a.totalDebitos() + " C=" + a.totalCreditos(), repro);
            }
            for (Asiento.Linea l : a.lineas()) {
                if (l.debito().signum() < 0 || l.credito().signum() < 0) {
                    violacion("I11c línea contable negativa", "periodo " + a.periodo() + " " + l.cuenta() + " D=" + l.debito()
                            + " C=" + l.credito(), repro);
                }
                if (l.cuenta() == Cuenta.CAJA) {
                    caja.merge(a.periodo(), l.debito().subtract(l.credito()), Calc::sumar);
                }
            }
        }
        for (Movimiento m : movs) {
            if (m.periodo() < 1) {
                continue;
            }
            BigDecimal c = caja.getOrDefault(m.periodo(), Calc.CERO);
            if (dif(c, m.pagoTotalCliente())) {
                violacion("I11b caja contable != pago total del cliente", "periodo " + m.periodo() + " (" + m.evento()
                        + ") caja=" + c.toPlainString() + " pagoTotal=" + m.pagoTotalCliente().toPlainString(), repro);
            }
        }
    }

    private void chequeoConcepto(String inv, BigDecimal antes, BigDecimal causado, BigDecimal pagado, BigDecimal pagadoExtra,
            BigDecimal extinguido, BigDecimal despues, String id, String repro) {
        BigDecimal esperado = Calc.restar(Calc.sumar(antes, causado), Calc.sumar(pagado, pagadoExtra, extinguido));
        if (dif(esperado, despues)) {
            violacion(inv + ": cxc anterior + causado - pagado != cxc final", id + " esperado=" + esperado.toPlainString()
                    + " real=" + despues.toPlainString(), repro);
        }
    }
}
