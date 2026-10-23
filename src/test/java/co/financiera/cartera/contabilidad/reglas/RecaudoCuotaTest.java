package co.financiera.cartera.contabilidad.reglas;

import static co.financiera.cartera.nucleo.Calc.bd;
import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.contabilidad.Asiento;
import co.financiera.cartera.contabilidad.Cuenta;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Una prueba por cada condición de {@link RecaudoCuota#esRecaudoNormal}: parte de un recaudo normal real del caso 1
 * y le cambia UN solo campo, de modo que si la condición se quita o se invierte, solo esa prueba lo detecta.
 */
class RecaudoCuotaTest {

    private static final Movimiento NORMAL = new MotorCartera()
            .ejecutar(EscenariosExcel.caso1(ParametrosProducto.replicaExcel())).get(5);
    private static final BigDecimal CIEN = bd("100");

    /** Copia del movimiento con los campos indicados reemplazados (los records no tienen «with»). */
    private static Movimiento con(Movimiento base, Map<String, Object> cambios) {
        try {
            RecordComponent[] componentes = Movimiento.class.getRecordComponents();
            Class<?>[] tipos = new Class<?>[componentes.length];
            Object[] valores = new Object[componentes.length];
            for (int i = 0; i < componentes.length; i++) {
                tipos[i] = componentes[i].getType();
                valores[i] = cambios.containsKey(componentes[i].getName())
                        ? cambios.get(componentes[i].getName())
                        : componentes[i].getAccessor().invoke(base);
            }
            return Movimiento.class.getDeclaredConstructor(tipos).newInstance(valores);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Movimiento con(String campo, Object valor) {
        return con(NORMAL, Map.of(campo, valor));
    }

    @Test
    void elMovimientoDePartidaEsUnRecaudoNormalYGeneraElAsientoDeLaCuota() {
        assertThat(NORMAL.pagoRecibido()).isPositive();
        assertThat(RecaudoCuota.esRecaudoNormal(NORMAL)).isTrue();

        Optional<Asiento> asiento = new RecaudoCuota().contabilizar(NORMAL);

        assertThat(asiento).isPresent();
        assertThat(asiento.get().lineas()).extracting(Asiento.Linea::cuenta).containsExactly(Cuenta.CAJA,
                Cuenta.INGRESOS_INTERESES, Cuenta.CARTERA_VIGENTE, Cuenta.CXP_SERVICIOS_TERCEROS);
        assertThat(asiento.get().cuadra()).isTrue();
    }

    @Test
    void sinPagoNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("pagoRecibido", BigDecimal.ZERO))).isFalse();
        assertThat(new RecaudoCuota().contabilizar(con("pagoRecibido", BigDecimal.ZERO))).isEmpty();
    }

    @Test
    void conMoraCausadaNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("interesMora", CIEN))).isFalse();
    }

    @Test
    void conCobranzaCausadaNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("cobranzaCausada", CIEN))).isFalse();
    }

    @Test
    void siPagoMoraNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("pagoMora", CIEN))).isFalse();
    }

    @Test
    void siPagoCobranzaNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("pagoCobranza", CIEN))).isFalse();
    }

    @Test
    void siPagoMasInteresQueElDelPeriodoNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("pagoInteresCorriente", NORMAL.interesCorriente().add(CIEN)))).isFalse();
    }

    @Test
    void siPagoMenosInteresQueElDelPeriodoNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("pagoInteresCorriente", NORMAL.interesCorriente().subtract(CIEN)))).isFalse();
    }

    @Test
    void siPagoMasServiciosQueLosDelPeriodoNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("pagoServicios", NORMAL.pagoServicios().add(CIEN)))).isFalse();
    }

    @Test
    void siPagoMenosServiciosQueLosDelPeriodoNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("pagoServicios", NORMAL.pagoServicios().subtract(CIEN)))).isFalse();
    }

    @Test
    void siLosServiciosCausadosNoCoincidenConLosPagadosNoEsRecaudoNormal() {
        Servicios causados = NORMAL.serviciosCausados();
        Servicios masSeguro = new Servicios(causados.fondoGarantias(), causados.asistencia(), causados.seguro().add(CIEN));
        assertThat(RecaudoCuota.esRecaudoNormal(con("serviciosCausados", masSeguro))).isFalse();
    }

    @Test
    void conCxcCobranzaNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("cxcCobranza", CIEN))).isFalse();
    }

    @Test
    void conCxcMoraNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("cxcMora", CIEN))).isFalse();
    }

    @Test
    void conCxcInteresCorrienteNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("cxcInteresCorriente", CIEN))).isFalse();
    }

    @Test
    void conCxcServiciosNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("cxcServicios", CIEN))).isFalse();
    }

    @Test
    void conCapitalVencidoNoEsRecaudoNormal() {
        assertThat(RecaudoCuota.esRecaudoNormal(con("capitalVencido", CIEN))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"interesMora", "cobranzaCausada", "pagoMora", "pagoCobranza", "cxcCobranza", "cxcMora",
            "cxcInteresCorriente", "cxcServicios", "capitalVencido"})
    void unResiduoDeMedioCentavoSeConsideraCeroPeroUnCentavoNo(String campo) {
        assertThat(RecaudoCuota.esRecaudoNormal(con(campo, bd("0.005")))).as(campo + " = 0,005").isTrue();
        assertThat(RecaudoCuota.esRecaudoNormal(con(campo, bd("0.006")))).as(campo + " = 0,006").isFalse();
    }

    @Test
    void laDiferenciaDeInteresYDeServiciosTambienToleraMedioCentavo() {
        assertThat(RecaudoCuota.esRecaudoNormal(
                con("pagoInteresCorriente", NORMAL.pagoInteresCorriente().add(bd("0.005"))))).isTrue();
        assertThat(RecaudoCuota.esRecaudoNormal(
                con("pagoInteresCorriente", NORMAL.pagoInteresCorriente().add(bd("0.006"))))).isFalse();
        assertThat(RecaudoCuota.esRecaudoNormal(con("pagoServicios", NORMAL.pagoServicios().add(bd("0.005"))))).isTrue();
        assertThat(RecaudoCuota.esRecaudoNormal(con("pagoServicios", NORMAL.pagoServicios().add(bd("0.006"))))).isFalse();
    }

    @Test
    void elHelperNoTocaLosDemasCampos() throws InvocationTargetException, IllegalAccessException {
        Movimiento copia = con("cxcMora", CIEN);
        for (RecordComponent c : Movimiento.class.getRecordComponents()) {
            if (!c.getName().equals("cxcMora")) {
                assertThat(c.getAccessor().invoke(copia)).as(c.getName()).isEqualTo(c.getAccessor().invoke(NORMAL));
            }
        }
        assertThat(copia.cxcMora()).isEqualByComparingTo(CIEN);
    }
}
