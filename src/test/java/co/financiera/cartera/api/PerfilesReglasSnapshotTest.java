package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Red de seguridad del refactor de {@link ReglasNegocio}: los perfiles EXCEL y NEGOCIO deben dar exactamente las
 * mismas reglas, con las mismas claves en el mismo orden (el de {@code /reglas}). El snapshot está escrito a mano y se
 * tomó antes del refactor; si cambia, es porque cambió una regla (y entonces se actualiza a propósito) o porque el
 * refactor rompió algo. Se lee por reflexión, igual que el API, para no depender de los métodos que se refactorizan.
 */
class PerfilesReglasSnapshotTest {

    private static final List<String> EXCEL = List.of(
            "prelacion=SERVICIOS_ANTES_DE_CAPITAL",
            "formulaMora=VENCIDO_MAS_DIAS",
            "baseInteres=SALDO_VIGENTE",
            "topeCobranzaPorPago=null",
            "baseServicios=MONTO_INICIAL",
            "serviciosDespuesDelPlazo=false",
            "moraAntesDeRecuperacion=true",
            "aplicacionAbono=SOLO_CAPITAL",
            "interesPrepagoPorDias=false",
            "margenUsura=1",
            "modalidadUsura=MISMA_FECHA_FINAL",
            "costoRecuperacionAlFondo=true",
            "cobranzaIngresoNoOperacional=false",
            "plazoLimiteMeses=null",
            "cambioTasaSobreCapitalVigente=false",
            "cobranzaDiaria=null",
            "cuotasVencidasParaRecuperacion=null",
            "cuotasVencidasParaCobranza=null",
            "prorrateoPrimeraCuota=false",
            "diasMora=PERIODO_NOMINAL",
            "cobranzaPorPeriodo=null",
            "cuotasVencidasParaSuspenderServicios=null",
            "redondeoCuotaAlPeso=false",
            "sobranteComoCuentaPorPagar=false",
            "umbralesCuentanCuotaEnCurso=false",
            "seguroTodoRiesgoPorcentajeMensual=0",
            "causacionServicios=SOLO_AL_DIA",
            "umbralCocienteCobranza=null",
            "baseMoraCobranza=TODO_PENDIENTE",
            "comparacionCobranza=MAYOR");

    private static final List<String> NEGOCIO = List.of(
            "prelacion=SERVICIOS_ANTES_DE_CAPITAL",
            "formulaMora=VENCIDO_MAS_DIAS",
            "baseInteres=SALDO_VIGENTE",
            "topeCobranzaPorPago=null",
            "baseServicios=MONTO_INICIAL",
            "serviciosDespuesDelPlazo=false",
            "moraAntesDeRecuperacion=true",
            "aplicacionAbono=PRELACION_COMPLETA",
            "interesPrepagoPorDias=true",
            "margenUsura=1",
            "modalidadUsura=MISMA_CUOTA",
            "costoRecuperacionAlFondo=true",
            "cobranzaIngresoNoOperacional=true",
            "plazoLimiteMeses=24",
            "cambioTasaSobreCapitalVigente=true",
            "cobranzaDiaria=3000",
            "cuotasVencidasParaRecuperacion=2",
            "cuotasVencidasParaCobranza=2",
            "prorrateoPrimeraCuota=true",
            "diasMora=DIAS_CALENDARIO_MENSUAL",
            "cobranzaPorPeriodo=40000",
            "cuotasVencidasParaSuspenderServicios=null",
            "redondeoCuotaAlPeso=true",
            "sobranteComoCuentaPorPagar=true",
            "umbralesCuentanCuotaEnCurso=false",
            "seguroTodoRiesgoPorcentajeMensual=0.003",
            "causacionServicios=SIEMPRE",
            "umbralCocienteCobranza=2",
            "baseMoraCobranza=TODO_PENDIENTE",
            "comparacionCobranza=MAYOR");

    /** clave=valor en el orden de los componentes del record; BigDecimal con su escala (toPlainString). */
    private static List<String> valores(ReglasNegocio r) throws ReflectiveOperationException {
        List<String> lista = new ArrayList<>();
        for (RecordComponent c : ReglasNegocio.class.getRecordComponents()) {
            Object v = c.getAccessor().invoke(r);
            String texto = v instanceof BigDecimal b ? b.toPlainString() : String.valueOf(v);
            lista.add(c.getName() + "=" + texto);
        }
        return lista;
    }

    @Test
    void elPerfilExcelNoCambia() throws ReflectiveOperationException {
        PerfilesReglas perfiles = new PerfilesReglas("config/reglas-negocio.properties");
        assertThat(valores(perfiles.reglas(PerfilesReglas.Perfil.EXCEL))).containsExactlyElementsOf(EXCEL);
        assertThat(valores(ReglasNegocio.EXCEL)).containsExactlyElementsOf(EXCEL);
    }

    @Test
    void elPerfilNegocioNoCambia() throws ReflectiveOperationException {
        PerfilesReglas perfiles = new PerfilesReglas("config/reglas-negocio.properties");
        assertThat(perfiles.archivoEncontrado()).isTrue();
        assertThat(valores(perfiles.reglas(PerfilesReglas.Perfil.NEGOCIO))).containsExactlyElementsOf(NEGOCIO);
    }

    @Test
    void elPerfilNegocioEmpaquetadoEnElJarEsElMismo() throws ReflectiveOperationException {
        // Azure: sin config/ en disco se lee la copia del classpath.
        ReglasNegocio delJar = ReglasNegocio.desde(PerfilesReglas.leerDelClasspath("config/reglas-negocio.properties"));
        assertThat(valores(delJar)).containsExactlyElementsOf(NEGOCIO);
    }
}
