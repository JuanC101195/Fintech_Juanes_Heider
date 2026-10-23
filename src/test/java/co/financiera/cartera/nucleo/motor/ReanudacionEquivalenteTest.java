package co.financiera.cartera.nucleo.motor;

import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.caracterizacion.Huellas;
import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * El corazón de la instantánea (escala, D2): correr un crédito completo y correrlo hasta el periodo k, guardar la
 * instantánea en texto, leerla y reanudar deben dar EXACTAMENTE los mismos movimientos en los periodos &gt; k. Se compara
 * cada movimiento con el formato estable de las huellas ({@link Huellas#texto(Movimiento)}, dinero en toPlainString) y
 * además con {@code equals} del record (BigDecimal con escala). Si una regla dependiera de algo de periodos anteriores
 * que la instantánea no guarda, esta prueba lo detecta: se arregla la instantánea, no la prueba.
 *
 * <p>Escenarios: los 400 primeros del generador de la auditoría (semilla fija; semanal, quincenal y mensual, EXCEL y
 * NEGOCIO, con y sin fechas) con varios k cada uno, comparados también en texto, y los 13 casos del Excel en semanal,
 * quincenal y mensual (estas dos también con fechas) con los dos perfiles y todos los k.
 */
class ReanudacionEquivalenteTest {

    private static final MotorCartera MOTOR = new MotorCartera();
    private static final int ALEATORIOS = 400;

    private final List<String> diferencias = new ArrayList<>();
    private int comparaciones;

    /**
     * Corre completo y, para cada k, hasta k + texto + lectura + reanudación; anota toda diferencia. Con
     * {@code siempreTexto} compara cada fila en el formato de las huellas además de {@code equals}; sin él compara con
     * {@code equals} (que implica el mismo texto: el texto es función de los valores) y solo arma el texto para
     * diagnosticar una diferencia. Así los 13 casos con todos los k no cuestan un minuto de reflexión.
     */
    private void comparar(String nombre, Escenario e, Iterable<Integer> ks, boolean siempreTexto) {
        RuntimeException falla = null;
        List<Movimiento> completa = new ArrayList<>();
        try {
            MOTOR.ejecutar(e, completa::add);
        } catch (RuntimeException ex) {
            falla = ex;
        }
        List<String> textosCompleta = siempreTexto ? textos(completa) : null;
        for (int k : ks) {
            comparaciones++;
            List<Movimiento> prefijo = new ArrayList<>();
            Instantanea i;
            try {
                i = MOTOR.ejecutarHasta(e, k, prefijo::add);
            } catch (RuntimeException ex) {
                // La falla de la corrida completa ocurre en un periodo <= k: hasta k también falla, igual.
                if (falla == null || !falla.getClass().equals(ex.getClass())) {
                    diferencias.add(nombre + " k=" + k + ": falla al correr hasta k: " + ex);
                }
                continue;
            }
            int corte = Math.min(prefijo.size(), completa.size());
            if (!prefijo.equals(completa.subList(0, corte))) {
                diferencias.add(nombre + " k=" + k + ": el prefijo hasta k no es el de la corrida completa");
            }
            String texto = i.aTexto();
            Instantanea leida = Instantanea.leer(texto);
            if (!leida.aTexto().equals(texto)) {
                diferencias.add(nombre + " k=" + k + ": el texto leído no se vuelve a escribir igual");
            }
            List<Movimiento> reanudada = new ArrayList<>();
            RuntimeException fallaReanudada = null;
            try {
                MOTOR.ejecutarDesde(leida, e.reglas(), e::instruccionesDe, Integer.MAX_VALUE, reanudada::add);
            } catch (RuntimeException ex) {
                fallaReanudada = ex;
            }
            if ((falla == null) != (fallaReanudada == null)
                    || (falla != null && !falla.getClass().equals(fallaReanudada.getClass()))) {
                diferencias.add(nombre + " k=" + k + ": completa lanzó " + falla + " y reanudada " + fallaReanudada);
                continue;
            }
            List<Movimiento> esperada = completa.subList(corte, completa.size());
            boolean textoIgual = !siempreTexto || textos(reanudada).equals(textosCompleta.subList(corte, completa.size()));
            if (!textoIgual || !reanudada.equals(esperada)) {
                diferencias.add(nombre + " k=" + k + ": " + primeraDiferencia(esperada, reanudada));
            }
        }
    }

    private static List<String> textos(List<Movimiento> movs) {
        return movs.stream().map(Huellas::texto).toList();
    }

    private static String primeraDiferencia(List<Movimiento> esperada, List<Movimiento> real) {
        for (int j = 0; j < Math.min(esperada.size(), real.size()); j++) {
            String a = Huellas.texto(esperada.get(j));
            String b = Huellas.texto(real.get(j));
            if (!a.equals(b)) {
                List<String> la = a.lines().toList();
                List<String> lb = b.lines().toList();
                for (int x = 0; x < la.size(); x++) {
                    if (!la.get(x).equals(lb.get(x))) {
                        return "periodo " + esperada.get(j).periodo() + ": esperado " + la.get(x) + " / reanudado " + lb.get(x);
                    }
                }
            }
        }
        if (esperada.size() == real.size()) {
            return "mismo texto pero distinta escala de algún BigDecimal";
        }
        return "filas: esperadas " + esperada.size() + ", reanudadas " + real.size();
    }

    /** Todos los k, del desembolso a uno después del plazo límite. */
    private static List<Integer> ks(ParametrosProducto p) {
        List<Integer> ks = new ArrayList<>();
        for (int k = 0; k <= p.plazoLimitePeriodos() + 1; k++) {
            ks.add(k);
        }
        return ks;
    }

    @Test
    void escenariosAleatoriosDeLaAuditoria() throws Exception {
        Random r = new Random(7);
        int conFalla = 0;
        for (EscenariosAleatorios.Generado g : EscenariosAleatorios.generar(ALEATORIOS, EscenariosAleatorios.negocio())) {
            List<Movimiento> completa = new ArrayList<>();
            try {
                MOTOR.ejecutar(g.escenario(), completa::add);
            } catch (RuntimeException ex) {
                conFalla++;
            }
            int ultimo = completa.isEmpty() ? 0 : completa.get(completa.size() - 1).periodo();
            TreeSet<Integer> ks = new TreeSet<>(List.of(0, 1, Math.max(0, ultimo - 1), ultimo, ultimo + 3));
            for (int j = 0; j < 4 && ultimo > 2; j++) {
                ks.add(1 + r.nextInt(ultimo - 1));
            }
            comparar("aleatorio#" + g.indice() + " [" + g.repro() + "]", g.escenario(), ks, true);
        }
        System.out.println("=== REANUDACIÓN: " + ALEATORIOS + " escenarios aleatorios (" + conFalla + " con excepción legítima), "
                + comparaciones + " comparaciones");
        assertThat(diferencias).as("reanudar desde la instantánea cambió los movimientos").isEmpty();
        assertThat(comparaciones).isGreaterThan(ALEATORIOS * 5);
    }

    @Test
    void losTreceCasosEnLasTresPeriodicidadesYLosDosPerfiles() throws Exception {
        Map<String, ReglasNegocio> perfiles = new LinkedHashMap<>();
        perfiles.put("EXCEL", ReglasNegocio.EXCEL);
        perfiles.put("NEGOCIO", EscenariosAleatorios.negocio());
        int escenarios = 0;
        for (Periodicidad per : Periodicidad.values()) {
            ParametrosProducto p = per == Periodicidad.SEMANAL ? ParametrosProducto.replicaExcel()
                    : ParametrosProducto.deMeses(LineaCredito.CPPU, per, 24);
            List<CalendarioPagos> calendarios = new ArrayList<>();
            calendarios.add(null);
            if (per == Periodicidad.QUINCENAL) {
                calendarios.add(CalendarioPagos.quincenal(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 11, 5)));
            } else if (per == Periodicidad.MENSUAL) {
                calendarios.add(CalendarioPagos.mensual(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 12, 5)));
            }
            for (CalendarioPagos cal : calendarios) {
                for (Map.Entry<String, ReglasNegocio> perfil : perfiles.entrySet()) {
                    for (int n = 1; n <= 13; n++) {
                        Escenario e = construir(n, p);
                        e.conReglas(perfil.getValue());
                        if (cal != null) {
                            e.conCalendario(cal);
                        }
                        escenarios++;
                        comparar(per + (cal == null ? "/sin-fechas/" : "/con-fechas/") + perfil.getKey() + "/caso" + n,
                                e, ks(p), false);
                    }
                }
            }
        }
        System.out.println("=== REANUDACIÓN: " + escenarios + " escenarios de los 13 casos, " + comparaciones + " comparaciones");
        assertThat(diferencias).as("reanudar desde la instantánea cambió los movimientos").isEmpty();
    }

    /** Encadenar instantáneas (k1 → k2 → k3 → fin, con texto en cada paso) da lo mismo que la corrida completa. */
    @Test
    void reanudacionesEncadenadas() throws Exception {
        for (EscenariosAleatorios.Generado g : EscenariosAleatorios.generar(150, EscenariosAleatorios.negocio())) {
            Escenario e = g.escenario();
            List<Movimiento> completa = new ArrayList<>();
            try {
                MOTOR.ejecutar(e, completa::add);
            } catch (RuntimeException ex) {
                continue;
            }
            List<Movimiento> encadenada = new ArrayList<>();
            Instantanea i = MOTOR.ejecutarHasta(e, 2, encadenada::add);
            for (int k = 7; !i.terminado(); k += 5) {
                i = Instantanea.leer(MOTOR.ejecutarDesde(Instantanea.leer(i.aTexto()), e.reglas(), e::instruccionesDe, k,
                        encadenada::add).aTexto());
            }
            assertThat(textos(encadenada)).as("aleatorio#%d [%s]", g.indice(), g.repro()).isEqualTo(textos(completa));
            assertThat(encadenada).isEqualTo(completa);
        }
    }

    private static Escenario construir(int caso, ParametrosProducto p) {
        return switch (caso) {
            case 1 -> EscenariosExcel.caso1(p);
            case 2 -> EscenariosExcel.caso2(p);
            case 3 -> EscenariosExcel.caso3(p, false);
            case 4 -> EscenariosExcel.caso4(p);
            case 5 -> EscenariosExcel.caso5(p);
            case 6 -> EscenariosExcel.caso6(p);
            case 7 -> EscenariosExcel.caso7(p);
            case 8 -> EscenariosExcel.caso8(p);
            case 9 -> EscenariosExcel.caso9(p);
            case 10 -> EscenariosExcel.caso10(p);
            case 11 -> EscenariosExcel.caso11(p);
            case 12 -> EscenariosExcel.caso12(p);
            case 13 -> EscenariosExcel.caso13(p);
            default -> throw new IllegalArgumentException("caso " + caso);
        };
    }
}
