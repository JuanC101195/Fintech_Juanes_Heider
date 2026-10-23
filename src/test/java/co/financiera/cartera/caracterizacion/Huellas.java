package co.financiera.cartera.caracterizacion;

import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mecanismo común de las pruebas de caracterización (huellas SHA-256 de lo que produce el motor).
 *
 * <p>La huella de {@link #huella(Escenario)} NO usa {@code Movimiento.toString}: escribe cada campo como
 * {@code campo=valor}, uno por línea, en el orden declarado, con el dinero en {@code toPlainString} (todos
 * los decimales, sin notación científica). Como los campos se leen por reflexión, {@link #CAMPOS_ESPERADOS}
 * fija la lista de campos que la huella "conoce": si alguien agrega, quita o renombra un campo de
 * {@code Movimiento}, {@code Servicios} o {@code DeudaExtinguida}, la prueba falla pidiendo decidir
 * conscientemente (actualizar la lista y regenerar), en vez de cambiar todas las huellas por ruido.
 *
 * <p>Regenerar: {@code -Dcaracterizacion.regenerar=true}. Si el archivo no existe también se crea.
 */
public final class Huellas {

    /** Bandera para reescribir los archivos de huellas tras un cambio intencional. */
    public static final String BANDERA = "caracterizacion.regenerar";

    private static final Path CARPETA = Path.of("src/test/resources/caracterizacion");

    /** Campos que la huella incluye, en orden de declaración, por cada record que se recorre. */
    private static final Map<String, List<String>> CAMPOS_ESPERADOS = Map.of(
            "Movimiento", List.of("periodo", "mes", "evento", "saldoInicial", "tasaPeriodica", "cuotaFinanciera",
                    "interesCorriente", "interesMora", "capitalContractual", "cobranzaCausada", "serviciosCausados",
                    "pagoRecibido", "pagoCobranza", "pagoMora", "pagoInteresCorriente", "pagoServicios", "pagoCapital",
                    "abonoExtra", "prepagoCapital", "cxcCobranza", "cxcMora", "cxcInteresCorriente", "cxcServicios",
                    "capitalVencido", "capitalVigente", "saldoFinal", "cuotasVencidas", "deudaAExtinguir", "inventario",
                    "cxcFondoGarantias", "cxpDeudor", "pagoTerceros", "estado", "pagosServicios",
                    "cxcServiciosPorConcepto", "extinguido", "capitalVencidoInicial", "pendientesPagadosExtra",
                    "interesPrepago", "costoRecuperacion", "costoRecuperacionAsumido", "listoParaRecuperacion"),
            "Servicios", List.of("fondoGarantias", "asistencia", "seguro"),
            "DeudaExtinguida", List.of("cobranza", "mora", "interesCorriente", "servicios", "capital"));

    private Huellas() {
    }

    // ------------------------------------------------------------------ Formato estable

    /** El cronograma y los asientos de un escenario en el formato estable {@code campo=valor}. */
    public static String texto(Escenario escenario) {
        List<Movimiento> movimientos = new MotorCartera().ejecutar(escenario);
        StringBuilder t = new StringBuilder();
        for (Movimiento m : movimientos) {
            t.append("[movimiento]\n");
            registro(t, "", m);
        }
        for (Asiento a : Contabilizador.para(escenario.reglas()).contabilizar(movimientos)) {
            t.append("[asiento] periodo=").append(a.periodo()).append(" evento=").append(a.evento()).append('\n');
            for (Asiento.Linea l : a.lineas()) {
                t.append("  cuenta=").append(l.cuenta().name()).append(" debito=").append(l.debito().toPlainString())
                        .append(" credito=").append(l.credito().toPlainString()).append(" explicacion=")
                        .append(l.explicacion()).append('\n');
            }
        }
        return t.toString();
    }

    private static void registro(StringBuilder t, String prefijo, Record r) {
        String nombre = r.getClass().getSimpleName();
        RecordComponent[] componentes = r.getClass().getRecordComponents();
        List<String> reales = Arrays.stream(componentes).map(RecordComponent::getName).toList();
        assertThat(reales)
                .as("%s cambió de campos. Decide si el campo nuevo entra a las huellas de caracterización: actualiza "
                        + "Huellas.CAMPOS_ESPERADOS y regenera con -D%s=true", nombre, BANDERA)
                .isEqualTo(CAMPOS_ESPERADOS.get(nombre));
        for (RecordComponent c : componentes) {
            Object v;
            try {
                v = c.getAccessor().invoke(r);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            if (v instanceof Record anidado) {
                registro(t, prefijo + c.getName() + ".", anidado);
            } else {
                t.append(prefijo).append(c.getName()).append('=').append(valor(v)).append('\n');
            }
        }
    }

    private static String valor(Object v) {
        return switch (v) {
            case null -> "null";
            case BigDecimal b -> b.toPlainString();
            case Enum<?> e -> e.name();
            default -> String.valueOf(v);
        };
    }

    // ------------------------------------------------------------------ Huellas

    /** "n_movimientos sha256" del formato estable de un escenario. */
    public static String huella(Escenario escenario) {
        String texto = texto(escenario);
        long movimientos = texto.lines().filter("[movimiento]"::equals).count();
        return movimientos + " " + sha256(texto);
    }

    public static String sha256(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ Archivo

    /**
     * Compara {@code actual} (nombre → huella) con {@code src/test/resources/caracterizacion/<archivo>}. Con la
     * bandera (o sin archivo) lo reescribe. Si difiere, el mensaje dice qué escenarios cambiaron.
     */
    public static void verificar(String archivo, Map<String, String> actual) throws IOException {
        Path ruta = CARPETA.resolve(archivo);
        if (Boolean.getBoolean(BANDERA) || !Files.exists(ruta)) {
            StringBuilder salida = new StringBuilder();
            actual.forEach((nombre, h) -> salida.append(nombre).append('=').append(h).append('\n'));
            Files.createDirectories(ruta.getParent());
            Files.writeString(ruta, salida.toString(), StandardCharsets.UTF_8);
        }
        Map<String, String> guardado = new LinkedHashMap<>();
        for (String linea : Files.readAllLines(ruta, StandardCharsets.UTF_8)) {
            if (!linea.isBlank()) {
                String[] partes = linea.split("=", 2);
                guardado.put(partes[0], partes[1]);
            }
        }
        List<String> diferencias = new ArrayList<>();
        actual.forEach((nombre, h) -> {
            if (!guardado.containsKey(nombre)) {
                diferencias.add("NUEVO " + nombre);
            } else if (!guardado.get(nombre).equals(h)) {
                diferencias.add("CAMBIÓ " + nombre);
            }
        });
        guardado.keySet().stream().filter(k -> !actual.containsKey(k)).forEach(k -> diferencias.add("FALTA " + k));
        assertThat(diferencias)
                .as("El comportamiento congelado en %s cambió (%d de %d escenarios). Si el cambio es intencional, "
                        + "regenere con -D%s=true y revise en el diff qué escenarios cambiaron", ruta, diferencias.size(),
                        actual.size(), BANDERA)
                .isEmpty();
        assertThat(actual).containsExactlyEntriesOf(guardado);
    }
}
