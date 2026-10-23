package co.financiera.cartera.nucleo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.financiera.cartera.nucleo.Responsable.Rol;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Bitácora en memoria, roles (D33, D41) y exoneraciones (D45), sin base de datos ni permisos reales. */
class BitacoraYPermisosTest {

    private static final LocalDateTime CUANDO = LocalDateTime.of(2026, 10, 23, 9, 15);
    private static final LocalDate NOVIEMBRE = LocalDate.of(2026, 11, 1);
    private static final Responsable ADMIN = new Responsable("ana.admin", Rol.ADMINISTRADOR);
    private static final Responsable SUPLENTE = new Responsable("sergio.suplente", Rol.SUPLENTE_ADMINISTRADOR);
    private static final Responsable DIRECTOR = new Responsable("diana.directora", Rol.DIRECTOR_FINANCIERO);
    private static final Responsable GERENTE = new Responsable("german.gerente", Rol.GERENTE);
    private static final Responsable ANALISTA = new Responsable("andres.analista", Rol.ANALISTA);

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void losRolesDicenQuienAdministraYQuienExonera() {
        assertThat(Rol.ADMINISTRADOR.administraParametros()).isTrue();
        assertThat(Rol.SUPLENTE_ADMINISTRADOR.administraParametros()).isTrue();
        assertThat(Rol.DIRECTOR_FINANCIERO.administraParametros()).isFalse();
        assertThat(Rol.GERENTE.administraParametros()).isFalse();
        assertThat(Rol.ANALISTA.administraParametros()).isFalse();
        assertThat(Rol.DIRECTOR_FINANCIERO.autorizaExoneraciones()).isTrue();
        assertThat(Rol.GERENTE.autorizaExoneraciones()).isTrue();
        assertThat(Rol.ADMINISTRADOR.autorizaExoneraciones()).isFalse();
        assertThat(Rol.SUPLENTE_ADMINISTRADOR.autorizaExoneraciones()).isFalse();
        assertThat(Rol.ANALISTA.autorizaExoneraciones()).isFalse();
        assertThatThrownBy(() -> new Responsable(" ", Rol.GERENTE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Responsable("x", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void elCambioDeCobranzaConRolQuedaEnLaBitacoraEnMemoria() {
        BitacoraEnMemoria bitacora = new BitacoraEnMemoria();
        VigenciasCobranza tabla = VigenciasCobranza.vacia()
                .cambiar(NOVIEMBRE, bd("3000"), ADMIN, CUANDO, "Valor inicial de la política", bitacora)
                .cambiar(LocalDate.of(2027, 1, 1), bd("3500"), SUPLENTE, CUANDO.plusDays(1), "Ajuste de enero",
                        bitacora);
        assertThat(tabla.valorEn(LocalDate.of(2027, 1, 1))).contains(bd("3500"));
        assertThat(bitacora.cambiosDe(VigenciasCobranza.PARAMETRO)).containsExactly(
                new CambioParametro("cobranzaDiaria", NOVIEMBRE, null, bd("3000"), "ana.admin", CUANDO,
                        "Valor inicial de la política"),
                new CambioParametro("cobranzaDiaria", LocalDate.of(2027, 1, 1), bd("3000"), bd("3500"),
                        "sergio.suplente", CUANDO.plusDays(1), "Ajuste de enero"));
        assertThat(bitacora.eventos()).hasSize(2);
        assertThat(bitacora.eventos().get(1).quien()).isEqualTo("sergio.suplente");
        assertThat(bitacora.cambiosDe("otro")).isEmpty();
    }

    @Test
    void quienNoAdministraNoCambiaLaCobranzaNiQuedaRegistro() {
        BitacoraEnMemoria bitacora = new BitacoraEnMemoria();
        for (Responsable r : new Responsable[] {DIRECTOR, GERENTE, ANALISTA}) {
            assertThatThrownBy(() -> VigenciasCobranza.vacia().cambiar(NOVIEMBRE, bd("3000"), r, CUANDO, "motivo",
                    bitacora)).isInstanceOf(PermisoInsuficienteException.class).hasMessageContaining(r.nombre());
        }
        assertThat(bitacora.eventos()).isEmpty();
    }

    @Test
    void laExoneracionExigeDirectorOGerenteYJustificacion() {
        BitacoraEnMemoria bitacora = new BitacoraEnMemoria();
        Exoneracion e = Exoneracion.autorizar("CR-001", Exoneracion.Concepto.GASTOS_COBRANZA, bd("21000"),
                LocalDate.of(2026, 10, 20), DIRECTOR, CUANDO, "Cliente hospitalizado, soporte médico", bitacora);
        Exoneracion.autorizar("CR-001", Exoneracion.Concepto.INTERES_MORA, bd("5000.5"), LocalDate.of(2026, 10, 20),
                GERENTE, CUANDO.plusHours(1), "Acuerdo de pago", bitacora);
        assertThat(bitacora.exoneracionesDe("CR-001")).hasSize(2).first().isEqualTo(e);
        assertThat(bitacora.exoneracionesDe("CR-002")).isEmpty();
        assertThat(e.quien()).isEqualTo("diana.directora");

        for (Responsable r : new Responsable[] {ADMIN, SUPLENTE, ANALISTA}) {
            assertThatThrownBy(() -> Exoneracion.autorizar("CR-001", Exoneracion.Concepto.GASTOS_COBRANZA,
                    bd("1000"), NOVIEMBRE, r, CUANDO, "motivo", bitacora))
                    .isInstanceOf(PermisoInsuficienteException.class).hasMessageContaining("D45");
        }
        assertThatThrownBy(() -> Exoneracion.autorizar("CR-001", Exoneracion.Concepto.GASTOS_COBRANZA, bd("1000"),
                NOVIEMBRE, GERENTE, CUANDO, "  ", bitacora)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificación");
        assertThatThrownBy(() -> Exoneracion.autorizar(" ", Exoneracion.Concepto.GASTOS_COBRANZA, bd("1000"),
                NOVIEMBRE, GERENTE, CUANDO, "motivo", bitacora)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Exoneracion.autorizar("CR-001", Exoneracion.Concepto.GASTOS_COBRANZA, BigDecimal.ZERO,
                NOVIEMBRE, GERENTE, CUANDO, "motivo", bitacora)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Exoneracion.autorizar("CR-001", Exoneracion.Concepto.GASTOS_COBRANZA, bd("1"),
                NOVIEMBRE, GERENTE, null, "motivo", bitacora)).isInstanceOf(NullPointerException.class);
        assertThat(bitacora.eventos()).hasSize(2);
    }

    @Test
    void unaBitacoraQueSoloGuardaCambiosNoPierdeOtrosRegistrosEnSilencio() {
        java.util.List<CambioParametro> cambios = new java.util.ArrayList<>();
        BitacoraParametros soloCambios = cambios::add;
        assertThatThrownBy(() -> Exoneracion.autorizar("CR-001", Exoneracion.Concepto.SERVICIOS, bd("1"), NOVIEMBRE,
                GERENTE, CUANDO, "motivo", soloCambios)).isInstanceOf(UnsupportedOperationException.class);
        // El cambio de un parámetro sí llega, por cualquiera de los dos métodos.
        EventoBitacora cambio = new CambioParametro("x", NOVIEMBRE, null, BigDecimal.ONE, "a", CUANDO, "j");
        soloCambios.registrar(cambio);
        assertThat(cambios).hasSize(1);
        // NINGUNA descarta todo, también las exoneraciones.
        Exoneracion.autorizar("CR-001", Exoneracion.Concepto.SERVICIOS, bd("1"), NOVIEMBRE, GERENTE, CUANDO, "motivo",
                BitacoraParametros.NINGUNA);
        BitacoraParametros.NINGUNA.registrar((CambioParametro) cambio);
    }

    @Test
    void elAltaDeReglasExigeAdministradorJustificacionYNoEsRetroactiva() {
        assertThat(new AltaVersionReglas("v2", NOVIEMBRE, "h", "v1", SUPLENTE, CUANDO, "motivo").quien())
                .isEqualTo("sergio.suplente");
        assertThatThrownBy(() -> new AltaVersionReglas("v2", NOVIEMBRE, "h", "v1", GERENTE, CUANDO, "motivo"))
                .isInstanceOf(PermisoInsuficienteException.class);
        assertThatThrownBy(() -> new AltaVersionReglas("v2", NOVIEMBRE, "h", "v1", ADMIN, CUANDO, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AltaVersionReglas("v2", LocalDate.of(2026, 10, 22), "h", "v1", ADMIN, CUANDO,
                "motivo")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("antes del día");
        // El mismo día del registro sí.
        assertThat(new AltaVersionReglas("v2", CUANDO.toLocalDate(), "h", null, ADMIN, CUANDO, "m").idVersionAnterior())
                .isNull();
    }
}
