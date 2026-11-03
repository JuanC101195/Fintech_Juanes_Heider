package co.financiera.cartera.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.api.PerfilesReglas.Perfil;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import org.junit.jupiter.api.Test;

/** D84 (R13-PRIMERA-CUOTA-LARGA, Diego 2026-11-02): el primer vencimiento siempre cae dentro del primer periodo. */
class PrimeraCuotaLargaTest {

    private final ReglasNegocio negocio = new PerfilesReglas("config/reglas-negocio.properties").reglas(Perfil.NEGOCIO);

    @Test
    void negocioRechazaUnaEntregaAMasDeUnPeriodoDelPrimerVencimiento() {
        // Sábado 3-oct y primer martes 13-oct: 10 días para un periodo de 7.
        FechasCredito diez = FechasCredito.de("2026-10-03", "2026-10-13", Periodicidad.SEMANAL);
        assertThatThrownBy(() -> diez.validarContra(negocio, Periodicidad.SEMANAL))
                .isInstanceOf(ApiException.class).hasMessageContaining("dentro del primer periodo");
    }

    @Test
    void unPeriodoExactoOMenosSePermite() {
        assertThatCode(() -> FechasCredito.de("2026-10-06", "2026-10-13", Periodicidad.SEMANAL)
                .validarContra(negocio, Periodicidad.SEMANAL)).doesNotThrowAnyException();
        assertThatCode(() -> FechasCredito.de("2026-10-10", "2026-10-13", Periodicidad.SEMANAL)
                .validarContra(negocio, Periodicidad.SEMANAL)).doesNotThrowAnyException();
    }

    @Test
    void conElExcelNoHayTope() {
        FechasCredito diez = FechasCredito.de("2026-10-03", "2026-10-13", Periodicidad.SEMANAL);
        assertThat(diez.validarContra(ReglasNegocio.EXCEL, Periodicidad.SEMANAL)).isSameAs(diez);
    }
}
