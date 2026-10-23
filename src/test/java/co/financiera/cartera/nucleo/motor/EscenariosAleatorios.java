package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;

import co.financiera.cartera.nucleo.Amortizacion;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Tasas;
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
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

/**
 * Los escenarios aleatorios de {@code auditoria/PropiedadesFinancierasAuditTest} (misma semilla 20261023 y mismos
 * sorteos, en el mismo orden), para las pruebas de escala: el generador se copia aquí sin cambios para no tocar la
 * prueba de auditoría protegida. Un escenario repetido con la misma semilla da los mismos créditos.
 */
final class EscenariosAleatorios {

    static final long SEMILLA = 20261023L;

    /** Un escenario y la línea de crédito con que se armó (el recálculo de usura va por línea). */
    record Generado(int indice, Escenario escenario, LineaCredito linea, String repro) {
    }

    private EscenariosAleatorios() {
    }

    static ReglasNegocio negocio() throws IOException {
        Properties pr = new Properties();
        try (FileReader f = new FileReader("config/reglas-negocio.properties", StandardCharsets.UTF_8)) {
            pr.load(f);
        }
        Map<String, String> m = new HashMap<>();
        pr.stringPropertyNames().forEach(k -> m.put(k, pr.getProperty(k)));
        return ReglasNegocio.desde(m);
    }

    private static BigDecimal u(Random r, double a, double b) {
        return new BigDecimal(String.format(java.util.Locale.ROOT, "%.4f", a + (b - a) * r.nextDouble()));
    }

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

    /** Los primeros {@code cuantos} escenarios del generador de la auditoría. */
    static List<Generado> generar(int cuantos, ReglasNegocio neg) {
        return generar(cuantos, neg, SEMILLA);
    }

    static List<Generado> generar(int cuantos, ReglasNegocio neg, long semilla) {
        Random r = new Random(semilla);
        List<Generado> salida = new ArrayList<>();
        for (int k = 0; k < cuantos; k++) {
            StringBuilder repro = new StringBuilder();
            Periodicidad per = Periodicidad.values()[r.nextInt(3)];
            int max = per.cuotasEnMeses(24);
            int n = r.nextInt(8) == 0 ? 1 + r.nextInt(3) : 1 + r.nextInt(max);
            LineaCredito linea = LineaCredito.values()[r.nextInt(4)];
            ParametrosProducto p = ParametrosProducto.de(linea, per, n);
            boolean esNegocio = r.nextBoolean();
            ReglasNegocio reglas = esNegocio ? neg : ReglasNegocio.EXCEL;
            if (esNegocio) {
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
                    p.tasaEA(), esNegocio ? "NEGOCIO" : "EXCEL", moto, o.pagoInicial().toPlainString(),
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
            Set<String> ocupados = new HashSet<>();
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
                    e.en(per1, ins);
                    repro.append(" | en ").append(per1).append(": ").append(ins);
                }
            }
            salida.add(new Generado(k, e, linea, repro.toString()));
        }
        return salida;
    }
}
