package co.financiera.cartera.nucleo.versiones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.financiera.cartera.nucleo.AltaVersionReglas;
import co.financiera.cartera.nucleo.BitacoraEnMemoria;
import co.financiera.cartera.nucleo.CambioParametro;
import co.financiera.cartera.nucleo.PermisoInsuficienteException;
import co.financiera.cartera.nucleo.Responsable;
import co.financiera.cartera.nucleo.Responsable.Rol;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Alta de versiones de reglas con bitácora y créditos atados a la versión con que se originaron (D33). */
class AltaVersionesYOrigenTest {

    private static final VersionReglas EXCEL = VersionReglas.de("excel-2026-10", LocalDate.of(2026, 10, 11),
            ReglasNegocio.EXCEL, "Excel");
    private static final VersionReglas NEGOCIO = VersionReglas.de("negocio-2026-10-23", LocalDate.of(2026, 10, 23),
            ReglasNegocio.EXCEL.conPlazoLimiteMeses(24), "config");
    private static final VersionReglas NOVIEMBRE = VersionReglas.de("negocio-2026-11-01", LocalDate.of(2026, 11, 1),
            NEGOCIO.reglas().conCuotasVencidasParaRecuperacion(3), "Ronda 7 de Diego");
    private static final LocalDateTime CUANDO = LocalDateTime.of(2026, 10, 28, 16, 0);
    private static final Responsable ADMIN = new Responsable("ana.admin", Rol.ADMINISTRADOR);

    @Test
    void elAdministradorRegistraUnaVersionNuevaYQuedaEnLaBitacora() {
        RepositorioReglasEnMemoria repo = RepositorioReglasEnMemoria.con(EXCEL, NEGOCIO);
        BitacoraEnMemoria bitacora = new BitacoraEnMemoria();
        assertThat(repo.registrar(NOVIEMBRE, ADMIN, CUANDO, "Ronda 7: recuperación a las 3 cuotas", bitacora)).isTrue();
        assertThat(repo.versiones()).containsExactly(EXCEL, NEGOCIO, NOVIEMBRE);
        assertThat(repo.vigenteEn(LocalDate.of(2026, 10, 31))).contains(NEGOCIO);
        assertThat(repo.vigenteEn(LocalDate.of(2026, 11, 1))).contains(NOVIEMBRE);
        assertThat(bitacora.altasDeReglas()).containsExactly(new AltaVersionReglas("negocio-2026-11-01",
                LocalDate.of(2026, 11, 1), NOVIEMBRE.huella(), "negocio-2026-10-23", ADMIN, CUANDO,
                "Ronda 7: recuperación a las 3 cuotas"));
        // Reintento idéntico: no duplica ni falla.
        assertThat(repo.registrar(NOVIEMBRE, ADMIN, CUANDO.plusMinutes(1), "reintento", bitacora)).isFalse();
        assertThat(bitacora.eventos()).hasSize(1);
    }

    @Test
    void unAltaInvalidaNoAgregaNiRegistraNada() {
        RepositorioReglasEnMemoria repo = RepositorioReglasEnMemoria.con(EXCEL, NEGOCIO);
        BitacoraEnMemoria bitacora = new BitacoraEnMemoria();
        Responsable gerente = new Responsable("german", Rol.GERENTE);
        assertThatThrownBy(() -> repo.registrar(NOVIEMBRE, gerente, CUANDO, "motivo", bitacora))
                .isInstanceOf(PermisoInsuficienteException.class);
        assertThatThrownBy(() -> repo.registrar(NOVIEMBRE, ADMIN, CUANDO, " ", bitacora))
                .isInstanceOf(IllegalArgumentException.class);
        // Registrada el 2-nov con vigencia desde el 1-nov: retroactiva.
        assertThatThrownBy(() -> repo.registrar(NOVIEMBRE, ADMIN, CUANDO.plusDays(5), "motivo", bitacora))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("antes del día");
        // Antes de la última versión.
        VersionReglas vieja = VersionReglas.de("negocio-2026-10-20", LocalDate.of(2026, 10, 20), ReglasNegocio.EXCEL,
                "o");
        assertThatThrownBy(() -> repo.registrar(vieja, ADMIN, LocalDateTime.of(2026, 10, 19, 8, 0), "motivo",
                bitacora)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("después");
        // Mismo id, otro contenido.
        VersionReglas alterada = VersionReglas.de(NEGOCIO.id(), LocalDate.of(2026, 12, 1),
                ReglasNegocio.EXCEL.conMargenUsura(BigDecimal.TEN), "o");
        assertThatThrownBy(() -> repo.registrar(alterada, ADMIN, CUANDO, "motivo", bitacora))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("otro contenido");
        // Una bitácora que no sabe guardar el alta: falla y no se agrega la versión.
        List<CambioParametro> soloCambios = new ArrayList<>();
        assertThatThrownBy(() -> repo.registrar(NOVIEMBRE, ADMIN, CUANDO, "motivo", soloCambios::add))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(repo.versiones()).containsExactly(EXCEL, NEGOCIO);
        assertThat(bitacora.eventos()).isEmpty();
    }

    @Test
    void unCreditoConservaLaVersionConQueSeOriginoAunqueLlegueOtra() {
        RepositorioReglasEnMemoria repo = RepositorioReglasEnMemoria.con(EXCEL, NEGOCIO);
        OrigenCredito octubre = OrigenCredito.originar("CR-001", LocalDate.of(2026, 10, 25), repo);
        assertThat(octubre.idVersionReglas()).isEqualTo("negocio-2026-10-23");
        repo.registrar(NOVIEMBRE, ADMIN, CUANDO, "Ronda 7", new BitacoraEnMemoria());
        OrigenCredito noviembre = OrigenCredito.originar("CR-002", LocalDate.of(2026, 11, 3), repo);
        assertThat(octubre.version(repo).reglas().cuotasVencidasParaRecuperacion()).isNull();
        assertThat(noviembre.version(repo)).isEqualTo(NOVIEMBRE);
        assertThat(noviembre.version(repo).reglas().cuotasVencidasParaRecuperacion()).isEqualTo(3);
        assertThatThrownBy(() -> OrigenCredito.originar("CR-003", LocalDate.of(2026, 1, 1), repo))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void siLaVersionDelCreditoCambioSeDetecta() {
        OrigenCredito origen = OrigenCredito.originar("CR-001", LocalDate.of(2026, 10, 25),
                RepositorioReglasEnMemoria.con(EXCEL, NEGOCIO));
        // Otro almacenamiento donde el mismo id tiene otro contenido (p. ej. alguien editó la fila).
        RepositorioReglas otro = RepositorioReglasEnMemoria.con(EXCEL, VersionReglas.de(NEGOCIO.id(),
                NEGOCIO.vigenteDesde(), ReglasNegocio.EXCEL, "editada"));
        assertThatThrownBy(() -> origen.version(otro)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cambió");
        assertThatThrownBy(() -> origen.version(RepositorioReglasEnMemoria.con(EXCEL)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrigenCredito(" ", LocalDate.of(2026, 1, 1), "v", "h"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
