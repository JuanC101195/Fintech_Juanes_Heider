package co.financiera.cartera.nucleo.versiones;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Dónde se consultan las versiones de las reglas de negocio (D33). Solo lectura: el alta de una versión nueva la hace
 * quien implementa el almacenamiento ({@link RepositorioReglasEnMemoria#registrar}; luego la base de datos, ADR 0004).
 * Sin Spring ni base de datos: el núcleo solo conoce esta interfaz.
 */
public interface RepositorioReglas {

    /** La versión con ese id, si existe. */
    Optional<VersionReglas> porVersion(String id);

    /**
     * La versión que rige para un crédito originado en {@code fecha}: la de {@code vigenteDesde} más reciente en o
     * antes de esa fecha. Vacío si ninguna había empezado.
     */
    Optional<VersionReglas> vigenteEn(LocalDate fecha);

    /** Todas las versiones, de la más antigua a la más reciente (por {@code vigenteDesde}). */
    List<VersionReglas> versiones();

    /** Como {@link #porVersion}, pero una versión que no existe es un error (un crédito apunta a una versión real). */
    default VersionReglas exigir(String id) {
        return porVersion(id).orElseThrow(() -> new IllegalArgumentException("No existe la versión de reglas " + id));
    }
}
