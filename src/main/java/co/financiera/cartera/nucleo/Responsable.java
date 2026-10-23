package co.financiera.cartera.nucleo;

import java.util.Objects;

/**
 * Quién hace una operación sensible y con qué rol (D33, D41, D45). Modela la regla de negocio de quién puede hacer qué;
 * los permisos reales (autenticación, quién tiene qué rol) los pone el API cuando exista el módulo de usuarios. El
 * núcleo solo exige que el rol declarado alcance para la operación.
 *
 * @param nombre usuario que hace la operación (como quedará en la bitácora)
 * @param rol    rol con que la hace
 */
public record Responsable(String nombre, Rol rol) {

    /** Roles que hoy nombran las decisiones de Diego. */
    public enum Rol {
        /** Cambia los parámetros del producto y las reglas, de inmediato y con bitácora, sin segundo aprobador (D33). */
        ADMINISTRADOR,
        /** Suplente del administrador: los mismos permisos y la misma bitácora (D41, ROL-1). */
        SUPLENTE_ADMINISTRADOR,
        /** Autoriza exoneraciones y condonaciones de un cobro, con justificación (D45). */
        DIRECTOR_FINANCIERO,
        /** Autoriza exoneraciones y condonaciones de un cobro, con justificación (D45). */
        GERENTE,
        /** Consulta y opera; no cambia parámetros ni exonera. */
        ANALISTA;

        /** D33 y D41: solo el administrador o su suplente cambian parámetros y versiones de reglas. */
        public boolean administraParametros() {
            return this == ADMINISTRADOR || this == SUPLENTE_ADMINISTRADOR;
        }

        /** D45: solo el director financiero o el gerente exoneran o condonan un cobro. */
        public boolean autorizaExoneraciones() {
            return this == DIRECTOR_FINANCIERO || this == GERENTE;
        }
    }

    public Responsable {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Toda operación sensible debe decir quién la hace");
        }
        Objects.requireNonNull(rol, "rol");
    }

    /** Falla con {@link PermisoInsuficienteException} si el rol no administra parámetros (D33, D41). */
    public Responsable exigirAdministrador(String operacion) {
        if (!rol.administraParametros()) {
            throw new PermisoInsuficienteException(nombre + " (" + rol + ") no puede " + operacion
                    + ": solo el administrador o su suplente (D33, D41)");
        }
        return this;
    }

    /** Falla con {@link PermisoInsuficienteException} si el rol no autoriza exoneraciones (D45). */
    public Responsable exigirAutorizaExoneraciones() {
        if (!rol.autorizaExoneraciones()) {
            throw new PermisoInsuficienteException(nombre + " (" + rol + ") no puede exonerar ni condonar un cobro: "
                    + "solo el director financiero o el gerente (D45)");
        }
        return this;
    }
}
