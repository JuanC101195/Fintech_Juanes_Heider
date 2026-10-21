package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code GET /casos/1?perfilReglas=EXCEL} da exactamente las cifras del motor corrido directo con
 * {@link EscenariosExcel#caso1} y {@link ReglasNegocio#EXCEL}: cada periodo, cada columna y cada asiento.
 */
@WebMvcTest(CarteraController.class)
@Import({ServicioCartera.class, PerfilesReglas.class})
class CasoUnoContraMotorTest {

    private static final Map<String, Function<Movimiento, BigDecimal>> COLUMNAS = Map.ofEntries(
            Map.entry("saldoInicial", Movimiento::saldoInicial),
            Map.entry("tasaPeriodica", Movimiento::tasaPeriodica),
            Map.entry("cuotaFinanciera", Movimiento::cuotaFinanciera),
            Map.entry("interesCorriente", Movimiento::interesCorriente),
            Map.entry("capitalContractual", Movimiento::capitalContractual),
            Map.entry("pagoRecibido", Movimiento::pagoRecibido),
            Map.entry("pagoInteresCorriente", Movimiento::pagoInteresCorriente),
            Map.entry("pagoServicios", Movimiento::pagoServicios),
            Map.entry("pagoCapital", Movimiento::pagoCapital),
            Map.entry("capitalVigente", Movimiento::capitalVigente),
            Map.entry("saldoFinal", Movimiento::saldoFinal),
            Map.entry("pagoTotalCliente", Movimiento::pagoTotalCliente));

    @Autowired
    MockMvc mvc;

    @Test
    void elCaso1DelApiEsElMotorDirecto() throws Exception {
        String cuerpo = mvc.perform(get("/api/v1/casos/1?perfilReglas=EXCEL")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode api = new ObjectMapper().readTree(cuerpo);

        List<Movimiento> motor = new MotorCartera()
                .ejecutar(EscenariosExcel.caso1(ParametrosProducto.replicaExcel()).conReglas(ReglasNegocio.EXCEL));
        JsonNode movs = api.get("movimientos");
        assertThat(movs.size()).isEqualTo(motor.size());
        for (int i = 0; i < motor.size(); i++) {
            Movimiento m = motor.get(i);
            JsonNode fila = movs.get(i);
            assertThat(fila.get("periodo").asInt()).isEqualTo(m.periodo());
            assertThat(fila.get("estado").asString()).isEqualTo(m.estado().name());
            assertThat(fila.get("evento").asString()).isEqualTo(m.evento());
            assertThat(fila.get("cuotasVencidas").asInt()).isEqualTo(m.cuotasVencidas());
            for (Map.Entry<String, Function<Movimiento, BigDecimal>> c : COLUMNAS.entrySet()) {
                assertThat(new BigDecimal(fila.get(c.getKey()).asString())).as("periodo %d, %s", m.periodo(), c.getKey())
                        .isEqualByComparingTo(c.getValue().apply(m));
            }
            assertThat(new BigDecimal(fila.get("serviciosCausados").get("total").asString()))
                    .isEqualByComparingTo(m.serviciosCausados().total());
        }

        Movimiento ultimo = motor.getLast();
        assertThat(api.get("resumen").get("periodoFinal").asInt()).isEqualTo(104).isEqualTo(ultimo.periodo());
        assertThat(api.get("resumen").get("estadoFinal").asString()).isEqualTo(ultimo.estado().name());
        assertThat(new BigDecimal(api.get("resumen").get("saldoFinal").asString())).isEqualByComparingTo(ultimo.saldoFinal());
        BigDecimal pagado = motor.stream().map(Movimiento::pagoTotalCliente).reduce(Calc.CERO, (a, b) -> Calc.sumar(a, b));
        assertThat(new BigDecimal(api.get("resumen").get("totalPagadoCliente").asString())).isEqualByComparingTo(pagado);

        List<Asiento> asientos = Contabilizador.estandar().contabilizar(motor);
        JsonNode asientosApi = api.get("asientos");
        assertThat(asientosApi.size()).isEqualTo(asientos.size());
        for (int i = 0; i < asientos.size(); i++) {
            Asiento a = asientos.get(i);
            JsonNode x = asientosApi.get(i);
            assertThat(x.get("periodo").asInt()).isEqualTo(a.periodo());
            assertThat(x.get("evento").asString()).isEqualTo(a.evento());
            assertThat(x.get("lineas").size()).isEqualTo(a.lineas().size());
            assertThat(new BigDecimal(x.get("totalDebitos").asString())).isEqualByComparingTo(a.totalDebitos());
            assertThat(x.get("cuadra").asBoolean()).isTrue();
        }
        assertThat(api.get("instrucciones").size()).isZero();
    }
}
