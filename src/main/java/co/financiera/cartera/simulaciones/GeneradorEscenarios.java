package co.financiera.cartera.simulaciones;

import static co.financiera.cartera.nucleo.Calc.bd;

import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.BaseInteres;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.BaseServicios;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.FormulaMora;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.OrdenPrelacion;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Genera las previsualizaciones del cuestionario de negocio: para cada pregunta que cambia el
 * cálculo, corre el motor real con cada opción sobre el caso del Excel que mejor la muestra y
 * escribe los resultados en JSON.
 *
 * <p>Uso: {@code java -cp target/classes co.financiera.cartera.simulaciones.GeneradorEscenarios <salida.json>}
 */
public final class GeneradorEscenarios {

    private record Opcion(String id, Escenario escenario) {
    }

    private record Pregunta(String id, String caso, List<Integer> semanasClave, List<Opcion> opciones) {
    }

    public static void main(String[] args) throws IOException {
        Path salida = Path.of(args.length > 0 ? args[0] : "cuestionario/src/datos/escenarios.json");
        Files.createDirectories(salida.toAbsolutePath().getParent());
        Files.writeString(salida, generar(), StandardCharsets.UTF_8);
        System.out.println("Escenarios escritos en " + salida.toAbsolutePath());
    }

    static String generar() {
        ParametrosProducto excel = ParametrosProducto.replicaExcel();
        ReglasNegocio r = ReglasNegocio.EXCEL;
        List<Pregunta> preguntas = List.of(
                new Pregunta("P1", "Caso 8 · el cliente paga el 40 % de la cuota 43 y luego vuelve a pagar la cuota pactada", List.of(43, 44),
                        List.of(new Opcion("servicios-antes", EscenariosExcel.caso8(excel).conReglas(r)),
                                new Opcion("capital-antes", EscenariosExcel.caso8(excel).conReglas(r.conPrelacion(OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS))))),
                new Pregunta("P3", "Caso 9 · desde la cuota 43 todas se pagan con 14 días de atraso", List.of(43, 44),
                        List.of(new Opcion("vencido-mas-dias", EscenariosExcel.caso9(excel).conReglas(r)),
                                new Opcion("solo-vencido", EscenariosExcel.caso9(excel).conReglas(r.conFormulaMora(FormulaMora.SOLO_VENCIDO))))),
                new Pregunta("P7", "Caso 3 · el cliente deja de pagar en la cuota 31 y entrega la moto (vale 3.000.000) en la 34", List.of(31, 34),
                        List.of(new Opcion("sin-mora", EscenariosExcel.caso3(excel, false).conReglas(r)),
                                new Opcion("con-mora", EscenariosExcel.caso3(excel, true).conReglas(r)))),
                new Pregunta("PDOBLE", "Caso 8 · pago del 40 % en la cuota 43", List.of(44, 86),
                        List.of(new Opcion("saldo-total", EscenariosExcel.caso8(excel).conReglas(r)),
                                new Opcion("saldo-vigente", EscenariosExcel.caso8(excel).conReglas(r.conBaseInteres(BaseInteres.SALDO_VIGENTE))))),
                new Pregunta("PTOPE", "Caso 9 · desde la cuota 43 todas se pagan con 14 días de atraso", List.of(43, 86),
                        List.of(new Opcion("sin-tope", EscenariosExcel.caso9(excel).conReglas(r)),
                                new Opcion("tope-20000", EscenariosExcel.caso9(excel).conReglas(r.conTopeCobranza(bd(20_000)))),
                                new Opcion("tope-10000", EscenariosExcel.caso9(excel).conReglas(r.conTopeCobranza(bd(10_000)))),
                                new Opcion("tope-0", EscenariosExcel.caso9(excel).conReglas(r.conTopeCobranza(BigDecimal.ZERO))))),
                new Pregunta("S1", "Caso 6 · abono extra de 2.000.000 en la cuota 43 como menor plazo", List.of(1, 61),
                        List.of(new Opcion("monto-inicial", EscenariosExcel.caso6(excel).conReglas(r)),
                                new Opcion("saldo", EscenariosExcel.caso6(excel).conReglas(r.conBaseServicios(BaseServicios.SALDO))))),
                new Pregunta("S4", "Caso 8 · el crédito sigue con saldo después de la cuota 86", List.of(86, 87),
                        List.of(new Opcion("no", EscenariosExcel.caso8(excel).conReglas(r)),
                                new Opcion("si", EscenariosExcel.caso8(excel).conReglas(r.conServiciosDespuesDelPlazo(true))))),
                new Pregunta("INICIAL", "Caso 1 · operación de 5.904.400 pagada a tiempo", List.of(1),
                        List.of(new Opcion("sin-inicial", EscenariosExcel.caso1(excel)),
                                new Opcion("inicial-10", EscenariosExcel.caso1(ParametrosProducto.motoEstandar())))));

        MotorCartera motor = new MotorCartera();
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"generado\": \"").append(LocalDate.now()).append("\",\n  \"preguntas\": {");
        for (int i = 0; i < preguntas.size(); i++) {
            Pregunta pr = preguntas.get(i);
            json.append(i == 0 ? "\n" : ",\n").append("    \"").append(pr.id()).append("\": {\"caso\": ").append(texto(pr.caso()))
                    .append(", \"semanasClave\": ").append(pr.semanasClave()).append(", \"opciones\": {");
            for (int j = 0; j < pr.opciones().size(); j++) {
                Opcion op = pr.opciones().get(j);
                Escenario e = op.escenario();
                List<Movimiento> m = motor.ejecutar(e);
                json.append(j == 0 ? "\n" : ",\n").append("      \"").append(op.id()).append("\": ")
                        .append(resultado(m, pr.semanasClave(), e.operacion().cuotaInicial(e.parametros()),
                                e.operacion().montoFinanciado(e.parametros())));
            }
            json.append("\n    }}");
        }
        json.append("\n  }\n}\n");
        return json.toString();
    }

    private static String resultado(List<Movimiento> m, List<Integer> semanasClave, BigDecimal inicial, BigDecimal financiado) {
        Movimiento ultimo = m.getLast();
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("cuotaInicial", inicial);
        kpis.put("montoFinanciado", financiado);
        kpis.put("cuotaSemanal", m.get(1).cuotaFinanciera().add(m.get(1).serviciosCausados().total()));
        kpis.put("semanaFinal", ultimo.periodo());
        kpis.put("estadoFinal", ultimo.estado().name());
        kpis.put("saldoPendiente", ultimo.saldoFinal().add(ultimo.cxcInteresCorriente()).add(ultimo.cxcMora())
                .add(ultimo.cxcServicios()).add(ultimo.cxcCobranza()));
        kpis.put("totalPagado", suma(m, Movimiento::pagoTotalCliente));
        kpis.put("interesPagado", suma(m, Movimiento::pagoInteresCorriente));
        kpis.put("moraPagada", suma(m, Movimiento::pagoMora));
        kpis.put("cobranzaPagada", suma(m, Movimiento::pagoCobranza));
        kpis.put("serviciosPagados", suma(m, Movimiento::pagoServicios));
        kpis.put("capitalVencidoMaximo", m.stream().map(Movimiento::capitalVencido).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
        kpis.put("semanasEnMora", m.stream().filter(x -> x.estado().name().equals("EN_MORA")).count());
        kpis.put("deudaAExtinguir", ultimo.deudaAExtinguir());
        kpis.put("faltanteFcc", ultimo.cxcFcc());
        kpis.put("excedenteDeudor", ultimo.cxpDeudor());

        List<String> semanas = new ArrayList<>();
        for (int s : semanasClave) {
            m.stream().filter(x -> x.periodo() == s).findFirst().ifPresent(x -> {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("periodo", x.periodo());
                f.put("pagoRecibido", x.pagoRecibido());
                f.put("cobranza", x.pagoCobranza());
                f.put("mora", x.pagoMora());
                f.put("interes", x.pagoInteresCorriente());
                f.put("servicios", x.pagoServicios());
                f.put("capital", x.pagoCapital());
                f.put("interesCausado", x.interesCorriente());
                f.put("moraCausada", x.interesMora());
                f.put("serviciosCausados", x.serviciosCausados().total());
                f.put("capitalVencido", x.capitalVencido());
                f.put("saldoFinal", x.saldoFinal());
                semanas.add(objeto(f));
            });
        }
        String saldo = serie(m, Movimiento::saldoFinal);
        String vencido = serie(m, Movimiento::capitalVencido);
        return "{\"kpis\": " + objeto(kpis) + ", \"semanas\": [" + String.join(", ", semanas) + "], \"serieSaldo\": " + saldo
                + ", \"serieVencido\": " + vencido + "}";
    }

    private static BigDecimal suma(List<Movimiento> m, Function<Movimiento, BigDecimal> dato) {
        return m.stream().map(dato).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String serie(List<Movimiento> m, Function<Movimiento, BigDecimal> dato) {
        return "[" + String.join(",", m.stream().map(x -> numero(dato.apply(x))).toList()) + "]";
    }

    private static String objeto(Map<String, Object> mapa) {
        List<String> partes = new ArrayList<>();
        mapa.forEach((k, v) -> partes.add("\"" + k + "\": " + valor(v)));
        return "{" + String.join(", ", partes) + "}";
    }

    private static String valor(Object v) {
        if (v instanceof BigDecimal b) {
            return numero(b);
        }
        if (v instanceof Number n) {
            return n.toString();
        }
        return texto(String.valueOf(v));
    }

    private static String numero(BigDecimal b) {
        return b.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String texto(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
