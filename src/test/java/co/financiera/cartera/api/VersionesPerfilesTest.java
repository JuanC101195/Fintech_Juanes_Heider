package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.financiera.cartera.api.PerfilesReglas.Perfil;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import co.financiera.cartera.nucleo.versiones.VersionReglas;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Los perfiles del archivo como versiones de reglas (ADR 0004). Fija la huella de cada id: si
 * {@code config/reglas-negocio.properties} cambia, esta prueba falla hasta que se le dé un id nuevo (subir
 * {@code PerfilesReglas.NEGOCIO_VIGENTE_DESDE}) y se actualice la huella aquí. Un mismo id nunca debe significar dos
 * reglas distintas: los créditos guardan el id con que se originaron.
 */
class VersionesPerfilesTest {

    private static final String HUELLA_EXCEL = "34588b778cb8a478c3206f87a97f21bf5d147d998d4dbf1952b616080e165930";
    private static final String ID_NEGOCIO = "negocio-2026-10-31";
    private static final String HUELLA_NEGOCIO = "30b820ae2c38169dd5db52f7cf8b5399557399f7fc5830dc77fbaa5e060aff27";

    private final PerfilesReglas perfiles = new PerfilesReglas("config/reglas-negocio.properties");

    @Test
    void cadaPerfilEsUnaVersionConIdYHuellaFijos() {
        VersionReglas excel = perfiles.version(Perfil.EXCEL);
        assertThat(excel.id()).isEqualTo("excel-2026-10");
        assertThat(excel.vigenteDesde()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(excel.reglas()).isSameAs(perfiles.reglas(Perfil.EXCEL));
        assertThat(excel.huella()).isEqualTo(HUELLA_EXCEL);

        VersionReglas negocio = perfiles.version(Perfil.NEGOCIO);
        assertThat(negocio.id())
                .as("¿Cambió config/reglas-negocio.properties? Es una versión nueva: suba NEGOCIO_VIGENTE_DESDE")
                .isEqualTo(ID_NEGOCIO);
        assertThat(negocio.vigenteDesde()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(negocio.reglas()).isSameAs(perfiles.reglas(Perfil.NEGOCIO));
        assertThat(negocio.huella())
                .as("El contenido de " + ID_NEGOCIO + " cambió sin cambiar el id: suba NEGOCIO_VIGENTE_DESDE y "
                        + "actualice esta huella")
                .isEqualTo(HUELLA_NEGOCIO);
        assertThat(negocio.origen()).contains("config/reglas-negocio.properties");
    }

    @Test
    void elRepositorioDelArchivoTieneLasDosVersionesEnOrden() {
        var repo = perfiles.repositorio();
        assertThat(repo.versiones()).containsExactly(perfiles.version(Perfil.EXCEL), perfiles.version(Perfil.NEGOCIO));
        assertThat(repo.porVersion("excel-2026-10")).contains(perfiles.version(Perfil.EXCEL));
        assertThat(repo.porVersion(ID_NEGOCIO)).contains(perfiles.version(Perfil.NEGOCIO));
        assertThat(repo.vigenteEn(LocalDate.of(2026, 10, 1))).isEmpty();
        assertThat(repo.vigenteEn(LocalDate.of(2026, 10, 20))).contains(perfiles.version(Perfil.EXCEL));
        assertThat(repo.vigenteEn(LocalDate.of(2026, 11, 1))).contains(perfiles.version(Perfil.NEGOCIO));
    }

    @Test
    void laFechaDeNegocioSeConfiguraYDebeSerPosteriorAlExcel() {
        PerfilesReglas otra = new PerfilesReglas("config/reglas-negocio.properties", "2026-11-02");
        assertThat(otra.version(Perfil.NEGOCIO).id()).isEqualTo("negocio-2026-11-02");
        assertThat(otra.version(Perfil.NEGOCIO).huella()).isEqualTo(HUELLA_NEGOCIO);
        assertThatThrownBy(() -> new PerfilesReglas("config/reglas-negocio.properties", "2026-10-11"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sinArchivoNegocioEsUnaVersionConLasReglasDelExcel() {
        PerfilesReglas sinArchivo = new PerfilesReglas("inexistente/reglas.properties");
        assertThat(sinArchivo.version(Perfil.NEGOCIO).reglas()).isEqualTo(ReglasNegocio.EXCEL);
        assertThat(sinArchivo.version(Perfil.NEGOCIO).huella()).isEqualTo(HUELLA_EXCEL);
        assertThat(sinArchivo.version(Perfil.NEGOCIO).origen()).contains("no encontrado");
    }
}
