package co.financiera.cartera.casos;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.TipoRecuperacion;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ADR 0002, paso 0 · Prueba de caracterización: los 12 casos del Excel con {@code replicaExcel()}
 * (semanal, 104 cuotas) producen exactamente el mismo cronograma y los mismos asientos que antes de
 * volver configurable la periodicidad. Compara un SHA-256 de cada fila completa ({@code toString}
 * del registro {@link Movimiento}, con todos sus decimales) y de cada asiento contra
 * {@code src/test/resources/caracterizacion/replica-semanal.sha256}.
 *
 * <p>Si un cambio de reglas (no de periodicidad) cambia a propósito una cifra, regenerar con
 * {@code ./mvnw -q -B test -Dtest=ReplicaSemanalNoCambiaTest -Dcaracterizacion.regenerar=true} y
 * revisar en el diff qué casos cambiaron. También cambia si se agregan campos a {@code Movimiento}.
 */
class ReplicaSemanalNoCambiaTest {

    private static final Path ARCHIVO = Path.of("src/test/resources/caracterizacion/replica-semanal.sha256");

    private static Map<String, Escenario> escenarios() {
        ParametrosProducto p = ParametrosProducto.replicaExcel();
        Map<String, Escenario> e = new LinkedHashMap<>();
        e.put("caso1", EscenariosExcel.caso1(p));
        e.put("caso1-inicial", EscenariosExcel.caso1ConInicial(p, bd(590_440)));
        e.put("caso2", new Escenario(Operacion.ejemploExcel(), p).en(44, new CambioTasa(bd("0.80"))));
        e.put("caso3", EscenariosExcel.caso3(p, false));
        e.put("caso4", new Escenario(Operacion.ejemploExcel(), p).entre(14, 17, new SinPago(false))
                .en(17, new CierrePorRecuperacion(TipoRecuperacion.RETOMA, bd(6_800_000))));
        e.put("caso5", new Escenario(Operacion.ejemploExcel(), p).en(43, new PrepagoTotal()));
        e.put("caso6", EscenariosExcel.caso6(p));
        e.put("caso7", new Escenario(Operacion.ejemploExcel(), p).en(43, new AbonoExtra(bd(2_000_000), ModalidadAbono.MENOR_CUOTA)));
        e.put("caso8", EscenariosExcel.caso8(p));
        e.put("caso9", EscenariosExcel.caso9(p));
        e.put("caso10", EscenariosExcel.caso10(p));
        e.put("caso11", EscenariosExcel.caso11(p));
        e.put("caso12", EscenariosExcel.caso12(p));
        return e;
    }

    private static String huella(Escenario escenario) {
        List<Movimiento> movimientos = new MotorCartera().ejecutar(escenario);
        StringBuilder texto = new StringBuilder();
        movimientos.forEach(m -> texto.append(m).append('\n'));
        Contabilizador.estandar().contabilizar(movimientos).forEach(a -> texto.append(a).append('\n'));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.toString().getBytes(StandardCharsets.UTF_8));
            return movimientos.size() + " " + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void losDoceCasosSemanalesNoCambianNiUnPeso() throws IOException {
        Map<String, String> actual = new LinkedHashMap<>();
        escenarios().forEach((caso, escenario) -> actual.put(caso, huella(escenario)));

        if (Boolean.getBoolean("caracterizacion.regenerar") || !Files.exists(ARCHIVO)) {
            StringBuilder salida = new StringBuilder();
            actual.forEach((caso, h) -> salida.append(caso).append('=').append(h).append('\n'));
            Files.createDirectories(ARCHIVO.getParent());
            Files.writeString(ARCHIVO, salida.toString(), StandardCharsets.UTF_8);
        }

        Map<String, String> guardado = new LinkedHashMap<>();
        for (String linea : Files.readAllLines(ARCHIVO, StandardCharsets.UTF_8)) {
            if (!linea.isBlank()) {
                String[] partes = linea.split("=", 2);
                guardado.put(partes[0], partes[1]);
            }
        }
        assertThat(actual).containsExactlyEntriesOf(guardado);
    }
}
