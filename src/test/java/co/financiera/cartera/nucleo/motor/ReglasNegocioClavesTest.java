package co.financiera.cartera.nucleo.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            return new BigDecimal(actual != null && new BigDecimal("7").compareTo((BigDecimal) actual) == 0 ? "8" : "7");
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

    /** Cada clave de la tabla de {@code desde} llega a su propio componente, con el parser de su tipo. */
    @Test
    void cadaClaveDeLaConfiguracionCambiaSoloSuComponente() throws ReflectiveOperationException {
        RecordComponent[] componentes = ReglasNegocio.class.getRecordComponents();
        List<Object> base = valores(ReglasNegocio.EXCEL);
        for (int i = 0; i < componentes.length; i++) {
            RecordComponent c = componentes[i];
            Object nuevo = otroValor(c, base.get(i));
            String texto = nuevo instanceof BigDecimal b ? b.toPlainString()
                    : nuevo instanceof Enum<?> e ? e.name() : String.valueOf(nuevo);
            List<Object> esperado = new ArrayList<>(base);
            esperado.set(i, nuevo);
            assertThat(valores(ReglasNegocio.desde(Map.of(c.getName(), " " + texto + " ")))).as(c.getName())
                    .isEqualTo(esperado);
        }
    }

    @Test
    void lasClavesDesconocidasSeIgnoranSalvoEnModoEstricto() {
        Map<String, String> config = Map.of("margenUsura", "2", "modalidadusura", "MISMA_CUOTA", "abonoMinimo", "0");
        assertThat(ReglasNegocio.desde(config)).isEqualTo(ReglasNegocio.EXCEL.conMargenUsura(new BigDecimal("2")));
        assertThat(ReglasNegocio.clavesDesconocidas(config)).containsExactly("abonoMinimo", "modalidadusura");
        assertThatThrownBy(() -> ReglasNegocio.desdeEstricto(config)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[abonoMinimo, modalidadusura]");
        assertThat(ReglasNegocio.desdeEstricto(Map.of("margenUsura", "2"))).isEqualTo(ReglasNegocio.desde(config));
    }

    @Test
    void losVaciosSeComportanComoAntes() {
        // Decimales y enteros vacíos se ignoran; un booleano vacío es false; una enumeración vacía es un error.
        assertThat(ReglasNegocio.desde(Map.of("margenUsura", " ", "plazoLimiteMeses", "", "cobranzaDiaria", "",
                "moraAntesDeRecuperacion", ""))).isEqualTo(ReglasNegocio.EXCEL.conMoraAntesDeRecuperacion(false));
        assertThatThrownBy(() -> ReglasNegocio.desde(Map.of("diasMora", " ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReglasNegocio.desde(Map.of("margenUsura", "-1")))
                .hasMessageContaining("El margen bajo la usura debe ser cero o positivo");
    }

    @Test
    void laCopiaSinCambiosEsIgualAlOriginal() {
        assertThat(ReglasNegocio.EXCEL.copia().construir()).isEqualTo(ReglasNegocio.EXCEL);
    }

    @Test
    void lasClavesSonLosComponentesDelRecordEnSuOrden() {
        // Las 26 (las 20 de antes, las 4 de la ronda 8 y las 2 de la ronda 9), también en CLAVES_MOTOR de herramientas/importar_respuestas.py.
        assertThat(ReglasNegocio.CLAVES).containsExactly("prelacion", "formulaMora", "baseInteres",
                "topeCobranzaPorPago", "baseServicios", "serviciosDespuesDelPlazo", "moraAntesDeRecuperacion",
                "aplicacionAbono", "interesPrepagoPorDias", "margenUsura", "modalidadUsura",
                "costoRecuperacionAlFondo", "cobranzaIngresoNoOperacional", "plazoLimiteMeses",
                "cambioTasaSobreCapitalVigente", "cobranzaDiaria", "cuotasVencidasParaRecuperacion", "cuotasVencidasParaCobranza",
                "prorrateoPrimeraCuota", "diasMora", "cobranzaPorPeriodo", "cuotasVencidasParaSuspenderServicios",
                "redondeoCuotaAlPeso", "sobranteComoCuentaPorPagar", "umbralesCuentanCuotaEnCurso",
                "seguroTodoRiesgoPorcentajeMensual");
    }
}
