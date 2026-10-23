package co.financiera.cartera.nucleo.versiones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RepositorioReglasEnMemoriaTest {

    private static final VersionReglas EXCEL = VersionReglas.de("excel-2026-10", LocalDate.of(2026, 10, 11),
            ReglasNegocio.EXCEL, "Excel");
    private static final VersionReglas NEGOCIO = VersionReglas.de("negocio-2026-10-23", LocalDate.of(2026, 10, 23),
            ReglasNegocio.EXCEL.conPlazoLimiteMeses(24), "config");

    @Test
    void laVigenteEsLaUltimaQueEmpezoEnOAntesDeLaFecha() {
        RepositorioReglas repo = RepositorioReglasEnMemoria.con(EXCEL, NEGOCIO);
        assertThat(repo.vigenteEn(LocalDate.of(2026, 10, 10))).isEmpty();
        assertThat(repo.vigenteEn(LocalDate.of(2026, 10, 11))).contains(EXCEL);
        assertThat(repo.vigenteEn(LocalDate.of(2026, 10, 22))).contains(EXCEL);
        assertThat(repo.vigenteEn(LocalDate.of(2026, 10, 23))).contains(NEGOCIO);
        assertThat(repo.vigenteEn(LocalDate.of(2030, 1, 1))).contains(NEGOCIO);
    }

    @Test
    void seBuscaPorIdYSeListanEnOrden() {
        RepositorioReglas repo = RepositorioReglasEnMemoria.con(EXCEL, NEGOCIO);
        assertThat(repo.porVersion("negocio-2026-10-23")).contains(NEGOCIO);
        assertThat(repo.porVersion("negocio-2099-01-01")).isEmpty();
        assertThat(repo.exigir("excel-2026-10")).isEqualTo(EXCEL);
        assertThatThrownBy(() -> repo.exigir("no-existe")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-existe");
        assertThat(repo.versiones()).containsExactly(EXCEL, NEGOCIO);
        assertThatThrownBy(() -> repo.versiones().add(EXCEL)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(RepositorioReglasEnMemoria.con().versiones()).isEmpty();
        assertThat(RepositorioReglasEnMemoria.con().ultima()).isEmpty();
        assertThat(RepositorioReglasEnMemoria.con(EXCEL, NEGOCIO).ultima()).contains(NEGOCIO);
    }

    @Test
    void soloHaciaAdelanteYSinIdsRepetidos() {
        assertThatThrownBy(() -> RepositorioReglasEnMemoria.con(NEGOCIO, EXCEL))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("después");
        VersionReglas mismaFecha = VersionReglas.de("otra", NEGOCIO.vigenteDesde(), ReglasNegocio.EXCEL, "o");
        assertThatThrownBy(() -> RepositorioReglasEnMemoria.con(EXCEL, NEGOCIO, mismaFecha))
                .isInstanceOf(IllegalArgumentException.class);
        VersionReglas mismoIdOtroContenido = VersionReglas.de(EXCEL.id(), LocalDate.of(2027, 1, 1),
                ReglasNegocio.EXCEL.conMargenUsura(BigDecimal.TEN), "o");
        assertThatThrownBy(() -> RepositorioReglasEnMemoria.con(EXCEL, mismoIdOtroContenido))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("otro contenido");
    }
}
