package co.financiera.cartera.nucleo.motor;

import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.Calc;
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

    /** NEGOCIO vigente, leído de {@link #ARCHIVO}. */
    static ReglasNegocio negocio() {
        try {
            return ReglasNegocio.desde(configuracion());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * NEGOCIO tal como quedó tras la ronda 9 (D60 a D64, ADR 0005), antes del rediseño de la ronda 10 (D70, D71, ADR
     * 0006): fondo y asistencia suspendidos desde la 2.ª cuota atrasada ({@code SUSPENDER_POR_UMBRAL} con umbral 2),
     * cobranza por cuotas vencidas que cuenta la cuota en curso (lectura A) y sin cociente de cobranza. Lo usan las
     * pruebas de las rondas 8 y 9 para seguir documentando esas respuestas.
     */
    static ReglasNegocio negocioRonda9() {
        return negocio().copia()
                .causacionServicios(ReglasNegocio.CausacionServicios.SUSPENDER_POR_UMBRAL)
                .cuotasVencidasParaSuspenderServicios(2)
                .cuotasVencidasParaCobranza(2)
                .umbralesCuentanCuotaEnCurso(true)
                .umbralCocienteCobranza(null)
                .construir();
    }

    @Test
    void lasReglasDeLaRondaNueveSeReconstruyenDesdeNegocio() {
        ReglasNegocio r9 = negocioRonda9();
        assertThat(r9.suspendeServicios(2)).isTrue();
        assertThat(r9.cobranzaPorCociente()).isFalse();
        assertThat(negocio().suspendeServicios(5)).as("NEGOCIO ya no suspende por atraso (D70)").isFalse();
        assertThat(negocio().cobranzaPorCociente()).isTrue();
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
                "caso 1 con pago inicial", EscenariosExcel.caso1ConInicial(p, Calc.bd(590_440)));
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
