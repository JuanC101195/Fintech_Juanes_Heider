package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.caracterizacion.Huellas;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.UsuraVigente;
import co.financiera.cartera.nucleo.motor.RecalculoUsura.Credito;
import co.financiera.cartera.nucleo.motor.RecalculoUsura.Estado;
import co.financiera.cartera.nucleo.motor.RecalculoUsura.Resultado;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Recálculo masivo por usura (escala, D4): reanudar cada crédito desde su instantánea con la usura de su línea da lo
 * mismo que correrlo completo con {@link UsuraVigente} en ese periodo, y respeta margen, modalidad MISMA_CUOTA (NEGOCIO,
 * USU-6) y créditos fuera de plazo (H6). Mide el tiempo de 10.000 créditos.
 */
class RecalculoUsuraTest {

    private static final MotorCartera MOTOR = new MotorCartera();
    private static ReglasNegocio negocio;

    /** Una usura por línea: CPPU, CPPR y ORDINARIO quedan por encima (se ajustan); BAJO_MONTO no (0,6666 < 0,70). */
    private static final Map<LineaCredito, BigDecimal> USURA = new EnumMap<>(Map.of(LineaCredito.CPPU, bd("0.60"),
            LineaCredito.CPPR, bd("0.55"), LineaCredito.BAJO_MONTO, bd("0.70"), LineaCredito.ORDINARIO, bd("0.25")));

    @BeforeAll
    static void cargar() throws Exception {
        negocio = EscenariosAleatorios.negocio();
    }

    /** Copia del escenario con todas sus instrucciones explícitas por periodo (para agregar la usura sin perder un {@code desde}). */
    private static Escenario explicito(Escenario e) {
        Escenario copia = new Escenario(e.operacion(), e.parametros()).conReglas(e.reglas()).conCobranzaActiva(e.cobranzaActiva());
        e.calendario().ifPresent(copia::conCalendario);
        for (int p = 1; p <= e.parametros().plazoLimitePeriodos(); p++) {
            for (Instruccion i : e.instruccionesDe(p)) {
                copia.en(p, i);
            }
        }
        return copia;
    }

    @Test
    void reanudarConLaUsuraEsLoMismoQueCorrerCompletoConUsuraVigente() {
        Random r = new Random(31);
        List<String> diferencias = new ArrayList<>();
        Map<Estado, Integer> estados = new EnumMap<>(Estado.class);
        int comparados = 0;
        for (EscenariosAleatorios.Generado g : EscenariosAleatorios.generar(400, negocio)) {
            Escenario e = g.escenario();
            List<Movimiento> base = new ArrayList<>();
            try {
                MOTOR.ejecutar(e, base::add);
            } catch (RuntimeException ex) {
                continue; // escenario rechazado por sí mismo (D12, H5): no es un crédito vigente
            }
            int ultimo = base.get(base.size() - 1).periodo();
            for (int k : new int[] {1 + r.nextInt(Math.max(1, ultimo)), Math.max(1, ultimo - 1), ultimo}) {
                Instantanea i = Instantanea.leer(MOTOR.ejecutarHasta(e, k, m -> { }).aTexto());
                RecalculoUsura recalculo = new RecalculoUsura(e.reglas(), Integer.MAX_VALUE);
                Resultado res = recalculo.recalcular(List.of(new Credito("c" + g.indice(), g.linea(), i, e::instruccionesDe)), USURA)
                        .get(0);
                estados.merge(res.estado(), 1, Integer::sum);
                String nombre = "aleatorio#" + g.indice() + " k=" + k + " [" + g.repro() + "]";
                if (i.terminado()) {
                    if (res.estado() != Estado.TERMINADO) {
                        diferencias.add(nombre + ": terminado y quedó " + res.estado());
                    }
                    continue;
                }
                List<Movimiento> esperada = new ArrayList<>();
                RuntimeException falla = null;
                try {
                    MOTOR.ejecutar(explicito(e).en(k, new UsuraVigente(USURA.get(g.linea()))), esperada::add);
                } catch (RuntimeException ex) {
                    falla = ex;
                }
                comparados++;
                if (falla != null) {
                    if (res.estado() != Estado.RECHAZADO) {
                        diferencias.add(nombre + ": completa lanzó " + falla + " y el recálculo quedó " + res.estado());
                    }
                    continue;
                }
                List<Movimiento> despues = esperada.subList(k + 1, esperada.size());
                if (!res.movimientosNuevos().equals(despues)
                        || !res.movimientosNuevos().stream().map(Huellas::texto).toList()
                                .equals(despues.stream().map(Huellas::texto).toList())) {
                    diferencias.add(nombre + ": los movimientos nuevos no son los de la corrida completa (" + res.estado() + ")");
                }
                boolean ajusta = i.tasas().efectivaAnual().compareTo(USURA.get(g.linea())) > 0;
                if ((res.estado() == Estado.AJUSTADO) != ajusta) {
                    diferencias.add(nombre + ": estado " + res.estado() + " con tasa " + i.tasas().efectivaAnual());
                }
                if (ajusta && res.tasaNuevaEA().compareTo(USURA.get(g.linea()).subtract(e.reglas().margenUsura().movePointLeft(2))) != 0) {
                    diferencias.add(nombre + ": tasa nueva " + res.tasaNuevaEA() + " no es usura − margen");
                }
            }
        }
        System.out.println("=== RECÁLCULO USURA equivalencia: " + comparados + " comparaciones, estados " + estados);
        assertThat(diferencias).isEmpty();
        assertThat(estados).containsKeys(Estado.AJUSTADO, Estado.SIN_CAMBIO, Estado.TERMINADO);
        assertThat(comparados).isGreaterThan(500);
    }

    @Test
    void conNegocioMantieneLaCuotaYBajaElPlazo() {
        Escenario e = new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel()).conReglas(negocio);
        Instantanea i = MOTOR.ejecutarHasta(e, 30, m -> { });
        Resultado res = new RecalculoUsura(negocio, 1).recalcular(new Credito("a", LineaCredito.CPPU, i), bd("0.60"));
        assertThat(negocio.modalidadUsura()).isEqualTo(Instruccion.ModalidadCambioTasa.MISMA_CUOTA);
        assertThat(res.estado()).isEqualTo(Estado.AJUSTADO);
        assertThat(res.tasaNuevaEA()).isEqualByComparingTo("0.59"); // usura − 1 punto (T2)
        assertThat(res.cuotaNueva()).isEqualTo(res.cuotaAnterior());
        assertThat(res.cuotasNuevas()).isLessThan(res.cuotasAnteriores());
        assertThat(res.movimientosNuevos()).singleElement().satisfies(m -> {
            assertThat(m.periodo()).isEqualTo(31); // rige desde la cuota siguiente (T1)
            assertThat(m.evento()).isEqualTo("Cambio de tasa");
        });
        assertThat(res.instantaneaConUsura().usuraPorAplicar()).isEqualByComparingTo("0.60");
        assertThat(res.instantaneaConUsura().periodo()).isEqualTo(30);
    }

    @Test
    void unCreditoFueraDePlazoNoExplota() {
        // H6: mensual de 11 cuotas sin pagar las dos últimas: la usura rige en el periodo 12, después de la última cuota.
        ParametrosProducto p = ParametrosProducto.de(LineaCredito.CPPU, Periodicidad.MENSUAL, 11);
        Escenario e = new Escenario(Operacion.ejemploExcel(), p).entre(10, 11, new SinPago(true));
        Instantanea i = MOTOR.ejecutarHasta(e, 11, m -> { });
        assertThat(i.terminado()).isFalse();
        assertThat(i.periodo()).isEqualTo(i.cuotasVigentes());
        assertThat(i.capitalVencido().signum()).isPositive();
        Resultado res = new RecalculoUsura(ReglasNegocio.EXCEL, Integer.MAX_VALUE)
                .recalcular(new Credito("h6", LineaCredito.CPPU, i), bd("0.60"));
        assertThat(res.estado()).isEqualTo(Estado.AJUSTADO);
        assertThat(res.cuotaNueva()).isEqualTo(res.cuotaAnterior());
        assertThat(res.cuotasNuevas()).isEqualTo(res.cuotasAnteriores());
        assertThat(res.movimientosNuevos()).isNotEmpty();
    }

    @Test
    void estadosQueNoRecalculan() {
        Escenario e = new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel()).en(5, new UsuraVigente(bd("0.9")));
        RecalculoUsura recalculo = new RecalculoUsura(ReglasNegocio.EXCEL, 1);
        Instantanea fin = MOTOR.ejecutar(e, m -> { });
        Instantanea cinco = MOTOR.ejecutarHasta(e, 5, m -> { });
        List<Resultado> r = recalculo.recalcular(List.of(new Credito("fin", LineaCredito.CPPU, fin),
                new Credito("sin-usura", LineaCredito.CPPR, cinco), new Credito("doble", LineaCredito.CPPU, cinco)),
                Map.of(LineaCredito.CPPU, bd("0.5")));
        assertThat(r).extracting(Resultado::estado).containsExactly(Estado.TERMINADO, Estado.SIN_USURA_PARA_LA_LINEA,
                Estado.RECHAZADO);
        assertThat(r.get(2).motivo()).contains("ya tiene una usura publicada");
        assertThat(r).allSatisfy(x -> assertThat(x.movimientosNuevos()).isEmpty());
    }

    /** Medición: 10.000 créditos vigentes semanales recalculados desde su instantánea vs. desde el desembolso. */
    @Test
    void diezMilCreditos() {
        int n = 10_000;
        List<Credito> lote = new ArrayList<>(n);
        List<Escenario> escenarios = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Escenario e = LoteStreaming.credito(i, negocio);
            int k = 11 + i % 85; // después de la usura que traen algunos créditos en el periodo 10
            Instantanea inst = MOTOR.ejecutarHasta(e, k, m -> { });
            lote.add(new Credito("c" + i, LineaCredito.CPPU, inst, e::instruccionesDe));
            escenarios.add(e);
        }
        Map<LineaCredito, BigDecimal> usura = Map.of(LineaCredito.CPPU, bd("0.60"));
        Map<Estado, Integer> estados = new EnumMap<>(Estado.class);

        long t0 = System.nanoTime();
        new RecalculoUsura(negocio, 1).recalcular(lote, usura, res -> estados.merge(res.estado(), 1, Integer::sum));
        long cuotaSiguiente = (System.nanoTime() - t0) / 1_000_000;

        long[] filas = {0};
        t0 = System.nanoTime();
        new RecalculoUsura(negocio, Integer.MAX_VALUE).recalcular(lote, usura, res -> filas[0] += res.movimientosNuevos().size());
        long proyeccion = (System.nanoTime() - t0) / 1_000_000;

        // Lo de antes: correr cada crédito desde el desembolso con la usura en su periodo (solo se mide; no se guarda nada).
        long[] filasDesdeCero = {0};
        t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            Escenario e = explicito(escenarios.get(i)).en(lote.get(i).instantanea().periodo(), new UsuraVigente(bd("0.60")));
            MOTOR.ejecutar(e, m -> filasDesdeCero[0]++);
        }
        long desdeCero = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("=== RECÁLCULO USURA 10.000 créditos: cuota siguiente " + cuotaSiguiente + " ms (" + estados
                + "); proyección completa " + proyeccion + " ms (" + filas[0] + " filas); desde el desembolso " + desdeCero
                + " ms (" + filasDesdeCero[0] + " filas)");
        assertThat(estados.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(n);
        assertThat(estados.get(Estado.RECHAZADO)).isNull();
    }
}
