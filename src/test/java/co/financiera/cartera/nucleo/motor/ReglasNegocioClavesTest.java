package co.financiera.cartera.nucleo.motor;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Las claves de {@link ReglasNegocio}: las que lee la configuración, las que muestra el API y las del importador. */
class ReglasNegocioClavesTest {

    /** Un valor distinto del actual para el componente, según su tipo. */
    static Object otroValor(RecordComponent c, Object actual) {
        Class<?> tipo = c.getType();
        if (tipo == boolean.class) {
            return !(Boolean) actual;
        }
        if (tipo == BigDecimal.class) {
            return new BigDecimal("7").equals(actual) ? new BigDecimal("8") : new BigDecimal("7");
        }
        if (tipo == Integer.class) {
            return Integer.valueOf(5).equals(actual) ? 6 : 5;
        }
        if (tipo.isEnum()) {
            for (Object constante : tipo.getEnumConstants()) {
                if (constante != actual) {
                    return constante;
                }
            }
        }
        throw new AssertionError("Tipo sin valor alternativo en la prueba: " + tipo);
    }

    static List<Object> valores(ReglasNegocio r) throws ReflectiveOperationException {
        List<Object> lista = new ArrayList<>();
        for (RecordComponent c : ReglasNegocio.class.getRecordComponents()) {
            lista.add(c.getAccessor().invoke(r));
        }
        return lista;
    }

    /** Si el Constructor cruzara dos argumentos (dos booleanos compilan igual), cambiaría otro componente. */
    @Test
    void cadaSetterDelConstructorCambiaSoloSuComponente() throws ReflectiveOperationException {
        RecordComponent[] componentes = ReglasNegocio.class.getRecordComponents();
        List<Object> base = valores(ReglasNegocio.EXCEL);
        for (int i = 0; i < componentes.length; i++) {
            RecordComponent c = componentes[i];
            Object nuevo = otroValor(c, base.get(i));
            ReglasNegocio.Constructor constructor = ReglasNegocio.EXCEL.copia();
            ReglasNegocio.Constructor.class.getMethod(c.getName(), c.getType()).invoke(constructor, nuevo);
            List<Object> esperado = new ArrayList<>(base);
            esperado.set(i, nuevo);
            assertThat(valores(constructor.construir())).as(c.getName()).isEqualTo(esperado);
        }
    }

    @Test
    void laCopiaSinCambiosEsIgualAlOriginal() {
        assertThat(ReglasNegocio.EXCEL.copia().construir()).isEqualTo(ReglasNegocio.EXCEL);
    }

    @Test
    void lasClavesSonLosComponentesDelRecordEnSuOrden() {
        // Las mismas 19 que estaban escritas a mano (y en CLAVES_MOTOR de herramientas/importar_respuestas.py).
        assertThat(ReglasNegocio.CLAVES).containsExactly("prelacion", "formulaMora", "baseInteres",
                "topeCobranzaPorPago", "baseServicios", "serviciosDespuesDelPlazo", "moraAntesDeRecuperacion",
                "aplicacionAbono", "interesPrepagoPorDias", "margenUsura", "modalidadUsura",
                "costoRecuperacionAlFondo", "cobranzaIngresoNoOperacional", "plazoLimiteMeses",
                "cambioTasaSobreCapitalVigente", "cobranzaDiaria", "cuotasVencidasParaRecuperacion",
                "prorrateoPrimeraCuota", "diasMora");
    }
}
