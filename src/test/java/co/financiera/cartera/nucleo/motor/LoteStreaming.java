package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;

import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.UsuraVigente;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Lote de medición (escala, D3): corre {@code N} créditos semanales (el peor caso: hasta 120 periodos) en streaming,
 * sin acumular sus movimientos, y guarda solo la instantánea final de cada uno en texto. Lo lanza
 * {@code LoteStreamingMemoriaTest} en una JVM aparte con {@code -Xmx256m}; imprime una línea {@code RESULTADO ...} con
 * las filas procesadas, el tiempo y el pico de heap.
 *
 * <p>Créditos determinísticos (sin azar): montos distintos, perfiles EXCEL y NEGOCIO alternados, y cada tanto mora con
 * normalización, pago parcial recurrente hasta el plazo límite, abono extraordinario y usura.
 */
public final class LoteStreaming {

    private LoteStreaming() {
    }

    static Escenario credito(int i, ReglasNegocio negocio) {
        BigDecimal moto = bd(3_000_000L + (i * 7_919L) % 20_000_000L);
        Operacion o = new Operacion(moto, bd(171_100), bd(373_300), bd(120_000), bd(80_000), bd(60_000));
        Escenario e = new Escenario(o, ParametrosProducto.replicaExcel()).conReglas(i % 2 == 0 ? ReglasNegocio.EXCEL : negocio);
        if (i % 5 == 0) {
            e.entre(30, 33, new SinPago(true)).en(34, new PagoAlDia());
        }
        if (i % 7 == 0) {
            e.desde(50, new PagoParcial(bd("0.5"), 0));
        }
        if (i % 11 == 0) {
            e.en(20, new AbonoExtra(Calc.multiplicar(o.montoFinanciado(), bd("0.1"))));
        }
        if (i % 13 == 0) {
            e.en(10, new UsuraVigente(bd("0.40")));
        }
        return e;
    }

    public static void main(String[] args) throws Exception {
        int n = Integer.parseInt(args[0]);
        // Modo de contraste: guarda la lista completa de cada crédito, como haría un lote con MotorCartera.ejecutar(escenario).
        boolean acumulando = args.length > 1 && "acumulando".equals(args[1]);
        List<List<Movimiento>> retenidas = new ArrayList<>();
        ReglasNegocio negocio = EscenariosAleatorios.negocio();
        MotorCartera motor = new MotorCartera();
        List<String> instantaneas = new ArrayList<>(n);
        long[] filas = {0};
        BigDecimal[] recaudo = {Calc.CERO};
        long inicio = System.nanoTime();
        for (int i = 0; i < n; i++) {
            if (acumulando) {
                retenidas.add(motor.ejecutar(credito(i, negocio)));
                if (i % 500 == 0) {
                    System.out.println("acumulados " + i + " créditos, " + (System.nanoTime() - inicio) / 1_000_000 + " ms");
                }
                continue;
            }
            Instantanea fin = motor.ejecutar(credito(i, negocio), m -> {
                filas[0]++;
                recaudo[0] = Calc.sumar(recaudo[0], m.pagoRecibido());
            });
            instantaneas.add(fin.aTexto());
        }
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        long pico = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                pico += pool.getPeakUsage().getUsed();
            }
        }
        long caracteres = instantaneas.stream().mapToLong(String::length).sum();
        System.out.println("RESULTADO creditos=" + n + " filas=" + filas[0] + " ms=" + ms + " picoHeapMB=" + pico / (1024 * 1024)
                + " maxHeapMB=" + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " instantaneasKB=" + caracteres / 1024
                + " recaudo=" + recaudo[0].toPlainString() + (acumulando ? " retenidas=" + retenidas.size() : ""));
    }
}
