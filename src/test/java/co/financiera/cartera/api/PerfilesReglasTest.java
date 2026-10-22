package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;

import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import org.junit.jupiter.api.Test;

class PerfilesReglasTest {

    /** En Azure el directorio de trabajo no tiene config/: las reglas de Diego deben venir en el jar. */
    @Test
    void lasReglasDeNegocioVanEmpaquetadasEnElClasspath() {
        assertThat(PerfilesReglas.leerDelClasspath("config/reglas-negocio.properties"))
                .containsEntry("modalidadUsura", "MISMA_CUOTA")
                .containsEntry("aplicacionAbono", "PRELACION_COMPLETA")
                .containsEntry("plazoLimiteMeses", "24");
    }

    @Test
    void sinArchivoNiRecursoNegocioQuedaComoElExcelYLoAvisa() {
        PerfilesReglas perfiles = new PerfilesReglas("inexistente/reglas.properties");
        assertThat(perfiles.archivoEncontrado()).isFalse();
        assertThat(perfiles.reglas(PerfilesReglas.Perfil.NEGOCIO)).isEqualTo(ReglasNegocio.EXCEL);
    }
}
