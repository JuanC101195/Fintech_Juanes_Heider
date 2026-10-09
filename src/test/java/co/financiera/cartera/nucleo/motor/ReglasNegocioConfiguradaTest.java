package co.financiera.cartera.nucleo.motor;

import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Corre los casos del Excel con las reglas que respondió negocio (config/reglas-negocio.properties,
 * generado por herramientas/importar_respuestas.py) y verifica que el motor siga consistente:
 * sin saldos negativos, pagos repartidos completos y asientos cuadrados. Sin archivo, usa las
 * reglas del Excel.
 */
class ReglasNegocioConfiguradaTest {

    static final Path ARCHIVO = Path.of("config", "reglas-negocio.properties");

    static Map<String, String> configuracion() throws IOException {
        Map<String, String> mapa = new HashMap<>();
        if (Files.exists(ARCHIVO)) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(ARCHIVO, StandardCharsets.UTF_8)) {
                p.load(r);
            }
            p.forEach((k, v) -> mapa.put(String.valueOf(k), String.valueOf(v)));
        }
        return mapa;
    }

    @Test
    void leeLasClavesDelArchivoYDejaLoDemasComoElExcel() {
        ReglasNegocio r = ReglasNegocio.desde(Map.of("prelacion", "CAPITAL_ANTES_DE_SERVICIOS", "topeCobranzaPorPago", "20000"));
        assertThat(r.prelacion()).isEqualTo(ReglasNegocio.OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS);
        assertThat(r.topeCobranzaPorPago()).isEqualByComparingTo("20000");
        assertThat(r.formulaMora()).isEqualTo(ReglasNegocio.EXCEL.formulaMora());
        assertThat(ReglasNegocio.desde(Map.of())).isEqualTo(ReglasNegocio.EXCEL);
    }

    @TestFactory
    Stream<DynamicTest> losCasosSiguenConsistentesConLasReglasRespondidas() throws IOException {
        ReglasNegocio reglas = ReglasNegocio.desde(configuracion());
        ParametrosProducto p = ParametrosProducto.replicaExcel();
        Map<String, Escenario> casos = Map.of(
                "caso 1", EscenariosExcel.caso1(p),
                "caso 3", EscenariosExcel.caso3(p, false),
                "caso 6", EscenariosExcel.caso6(p),
                "caso 8", EscenariosExcel.caso8(p),
                "caso 9", EscenariosExcel.caso9(p),
                "caso 1 con inicial del 10 %", EscenariosExcel.caso1(ParametrosProducto.motoEstandar()));
        return casos.entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), () -> {
            List<Movimiento> m = new MotorCartera().ejecutar(e.getValue().conReglas(reglas));
            for (Movimiento x : m) {
                assertThat(x.saldoFinal()).isNotNegative();
                assertThat(x.capitalVencido()).isNotNegative();
                BigDecimal repartido = x.pagoCobranza().add(x.pagoMora()).add(x.pagoInteresCorriente()).add(x.pagoServicios())
                        .add(x.pagoCapital());
                assertThat(repartido.subtract(x.pagoRecibido()).abs()).isLessThanOrEqualTo(BigDecimal.ONE);
            }
            // El Contabilizador lanza excepción si algún asiento no cuadra.
            Contabilizador.estandar().contabilizar(m);
        }));
    }
}
