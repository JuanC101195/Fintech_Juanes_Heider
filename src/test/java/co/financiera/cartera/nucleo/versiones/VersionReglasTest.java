package co.financiera.cartera.nucleo.versiones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class VersionReglasTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 10, 11);

    /**
     * Huella fija del perfil EXCEL. Si cambia, cambió el formato canónico o una regla del Excel: las dos cosas son
     * versiones nuevas y deben decidirse a propósito (ADR 0004), nunca regenerarse en silencio.
     */
    private static final String HUELLA_EXCEL = "580dd2b46a53e31b9b8b7ae1e732264050b3d0021acf69742b2d7bd87281e9a8";

    @Test
    void elContenidoCanonicoTieneElFormatoYTodasLasClavesEnOrden() {
        String texto = VersionReglas.contenidoCanonico(ReglasNegocio.EXCEL);
        String[] lineas = texto.split("\n");
        assertThat(lineas[0]).isEqualTo("formato=" + VersionReglas.FORMATO);
        assertThat(lineas).hasSize(ReglasNegocio.CLAVES.size() + 1);
        int i = 1;
        for (String clave : ReglasNegocio.CLAVES) {
            assertThat(lineas[i++]).startsWith(clave + "=");
        }
        assertThat(texto).contains("\ntopeCobranzaPorPago=\n", "\nmargenUsura=1\n", "\nprelacion=SERVICIOS_ANTES_DE_CAPITAL\n")
                .endsWith("\ndiasMora=PERIODO_NOMINAL\n");
    }

    @Test
    void laHuellaEsDeterministaYFija() {
        assertThat(VersionReglas.huella(ReglasNegocio.EXCEL)).isEqualTo(VersionReglas.huella(ReglasNegocio.EXCEL))
                .isEqualTo(VersionReglas.huella(ReglasNegocio.EXCEL.copia().construir()))
                .hasSize(64).matches("[0-9a-f]{64}")
                .isEqualTo(HUELLA_EXCEL);
    }

    @Test
    void laEscalaDeUnDecimalNoCambiaLaHuellaPeroElValorSi() {
        ReglasNegocio tres = ReglasNegocio.EXCEL.conCobranzaDiaria(new BigDecimal("3000"));
        ReglasNegocio tresConDecimales = ReglasNegocio.EXCEL.conCobranzaDiaria(new BigDecimal("3000.00"));
        ReglasNegocio tresMil1 = ReglasNegocio.EXCEL.conCobranzaDiaria(new BigDecimal("3000.01"));
        assertThat(VersionReglas.huella(tres)).isEqualTo(VersionReglas.huella(tresConDecimales))
                .isNotEqualTo(VersionReglas.huella(tresMil1))
                .isNotEqualTo(VersionReglas.huella(ReglasNegocio.EXCEL));
        assertThat(VersionReglas.contenidoCanonico(ReglasNegocio.EXCEL.conMargenUsura(new BigDecimal("0.000"))))
                .contains("\nmargenUsura=0\n");
    }

    @Test
    void cadaReglaCambiaLaHuella() {
        String base = VersionReglas.huella(ReglasNegocio.EXCEL);
        assertThat(VersionReglas.huella(ReglasNegocio.EXCEL.conProrrateoPrimeraCuota(true))).isNotEqualTo(base);
        assertThat(VersionReglas.huella(ReglasNegocio.EXCEL.conDiasMora(ReglasNegocio.DiasMora.DIAS_CALENDARIO_MENSUAL)))
                .isNotEqualTo(base);
        assertThat(VersionReglas.huella(ReglasNegocio.EXCEL.conPlazoLimiteMeses(24))).isNotEqualTo(base);
        // Dos booleanos distintos en true no dan la misma huella (la clave va en el texto).
        assertThat(VersionReglas.huella(ReglasNegocio.EXCEL.conInteresPrepagoPorDias(true)))
                .isNotEqualTo(VersionReglas.huella(ReglasNegocio.EXCEL.conServiciosDespuesDelPlazo(true)));
    }

    @Test
    void elContenidoSeVuelveALeerIgual() {
        ReglasNegocio negocio = ReglasNegocio.EXCEL.conAplicacionAbono(ReglasNegocio.AplicacionAbono.PRELACION_COMPLETA)
                .conPlazoLimiteMeses(24).conCobranzaDiaria(new BigDecimal("3000")).conCuotasVencidasParaRecuperacion(2)
                .conTopeCobranza(new BigDecimal("50000.5"));
        for (ReglasNegocio r : new ReglasNegocio[] {ReglasNegocio.EXCEL, negocio}) {
            ReglasNegocio leidas = VersionReglas.reglasDesdeContenido(VersionReglas.contenidoCanonico(r));
            assertThat(leidas).isEqualTo(r);
            assertThat(VersionReglas.huella(leidas)).isEqualTo(VersionReglas.huella(r));
        }
    }

    @Test
    void unContenidoMalFormadoSeRechaza() {
        String bueno = VersionReglas.contenidoCanonico(ReglasNegocio.EXCEL);
        assertThatThrownBy(() -> VersionReglas.reglasDesdeContenido(bueno.replace("reglas-negocio/1", "reglas-negocio/9")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Formato");
        assertThatThrownBy(() -> VersionReglas.reglasDesdeContenido(bueno + "claveInventada=1\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("claveInventada");
        assertThatThrownBy(() -> VersionReglas.reglasDesdeContenido(bueno + "margenUsura=2\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repetida");
        assertThatThrownBy(() -> VersionReglas.reglasDesdeContenido(bueno + "sinIgual\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaVersionGuardadaYAlteradaSeDetecta() {
        VersionReglas v = VersionReglas.de("excel-2026-10", DESDE, ReglasNegocio.EXCEL, "Excel de Diego");
        assertThat(VersionReglas.desdeGuardado(v.id(), v.vigenteDesde(), v.contenidoCanonico(), v.huella(), v.origen()))
                .isEqualTo(v);
        String alterado = v.contenidoCanonico().replace("margenUsura=1", "margenUsura=5");
        assertThatThrownBy(() -> VersionReglas.desdeGuardado(v.id(), DESDE, alterado, v.huella(), v.origen()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("alterada");
        assertThatThrownBy(() -> new VersionReglas("x", DESDE, ReglasNegocio.EXCEL, "0".repeat(64), "o"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("alterada");
    }

    @Test
    void elIdEsLegibleYLaVersionDiceSuOrigen() {
        assertThat(VersionReglas.de("negocio-2026-10-23", DESDE, ReglasNegocio.EXCEL, "config").id())
                .isEqualTo("negocio-2026-10-23");
        for (String malo : new String[] {null, "", "Negocio", "negocio_1", "-negocio", "negocio-", "a--b", "a b",
                "a".repeat(65)}) {
            assertThatThrownBy(() -> VersionReglas.de(malo, DESDE, ReglasNegocio.EXCEL, "o"))
                    .as(String.valueOf(malo)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> VersionReglas.de("v1", DESDE, ReglasNegocio.EXCEL, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VersionReglas.de("v1", null, ReglasNegocio.EXCEL, "o"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void mismoContenidoCompararLaHuella() {
        VersionReglas a = VersionReglas.de("a", DESDE, ReglasNegocio.EXCEL, "o");
        VersionReglas b = VersionReglas.de("b", DESDE.plusDays(1), ReglasNegocio.EXCEL.copia().construir(), "otro");
        assertThat(a.mismoContenidoQue(b)).isTrue();
        assertThat(a.mismoContenidoQue(VersionReglas.de("c", DESDE, ReglasNegocio.EXCEL.conMargenUsura(BigDecimal.TEN),
                "o"))).isFalse();
    }
}
