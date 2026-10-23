package co.financiera.cartera.nucleo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Alta de una versión nueva de las reglas de negocio, para la bitácora (D33: el administrador cambia de inmediato,
 * con bitácora de quién, qué y cuándo, sin segundo aprobador). Guarda el id y la huella, no las reglas: el contenido
 * vive en la versión (ADR 0004).
 *
 * @param idVersion         id de la versión nueva
 * @param vigenteDesde      desde cuándo rige para créditos nuevos
 * @param huella            SHA-256 del contenido canónico de la versión
 * @param idVersionAnterior la versión que regía hasta entonces ({@code null} si es la primera)
 * @param responsable       el administrador o su suplente
 * @param registradoEn      cuándo se registró
 * @param justificacion     por qué
 */
public record AltaVersionReglas(
        String idVersion,
        LocalDate vigenteDesde,
        String huella,
        String idVersionAnterior,
        Responsable responsable,
        LocalDateTime registradoEn,
        String justificacion) implements EventoBitacora {

    public AltaVersionReglas {
        Objects.requireNonNull(idVersion, "idVersion");
        Objects.requireNonNull(vigenteDesde, "vigenteDesde");
        Objects.requireNonNull(huella, "huella");
        Objects.requireNonNull(responsable, "responsable").exigirAdministrador("registrar una versión de reglas");
        Objects.requireNonNull(registradoEn, "registradoEn");
        EventoBitacora.exigirTexto(justificacion, "Una versión nueva de reglas debe llevar justificación");
        if (vigenteDesde.isBefore(registradoEn.toLocalDate())) {
            throw new IllegalArgumentException("La versión " + idVersion + " no puede regir antes del día en que se "
                    + "registra: " + vigenteDesde + " < " + registradoEn.toLocalDate());
        }
    }

    @Override
    public String quien() {
        return responsable.nombre();
    }
}
