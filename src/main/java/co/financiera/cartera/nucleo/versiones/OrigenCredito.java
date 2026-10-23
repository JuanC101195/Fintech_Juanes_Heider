package co.financiera.cartera.nucleo.versiones;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Con qué versión de reglas se originó un crédito (D33: "un cambio rige solo para créditos nuevos, salvo la usura").
 * Se fija una vez, al originar, con la versión vigente ese día, y no cambia aunque después se registren versiones
 * nuevas. La usura no es una regla de la versión sino un evento ({@code UsuraVigente}) que sí alcanza a los créditos
 * vigentes. Los parámetros del producto (tasa, servicios, cobranza diaria) ya quedan copiados en los
 * {@code ParametrosProducto} del crédito al originarlo; la persistencia los guarda con él (ADR 0004).
 *
 * @param credito         id del crédito
 * @param originacion     fecha de originación
 * @param idVersionReglas versión de reglas con que se originó
 * @param huellaReglas    huella de esa versión al originar (para detectar si la versión se alteró después)
 */
public record OrigenCredito(String credito, LocalDate originacion, String idVersionReglas, String huellaReglas) {

    public OrigenCredito {
        if (credito == null || credito.isBlank()) {
            throw new IllegalArgumentException("El origen debe decir de qué crédito es");
        }
        Objects.requireNonNull(originacion, "originacion");
        Objects.requireNonNull(idVersionReglas, "idVersionReglas");
        Objects.requireNonNull(huellaReglas, "huellaReglas");
    }

    /** Ata el crédito a la versión vigente en su fecha de originación; falla si ninguna regía entonces. */
    public static OrigenCredito originar(String credito, LocalDate originacion, RepositorioReglas repositorio) {
        Objects.requireNonNull(originacion, "originacion");
        VersionReglas v = repositorio.vigenteEn(originacion).orElseThrow(() -> new IllegalArgumentException(
                "No hay versión de reglas vigente el " + originacion + " para originar el crédito " + credito));
        return new OrigenCredito(credito, originacion, v.id(), v.huella());
    }

    /**
     * La versión de reglas del crédito, la misma con que se originó. Falla si ya no existe o si su contenido cambió
     * (la huella guardada en el crédito no coincide): un crédito nunca corre con reglas distintas de las suyas.
     */
    public VersionReglas version(RepositorioReglas repositorio) {
        VersionReglas v = repositorio.exigir(idVersionReglas);
        if (!v.huella().equals(huellaReglas)) {
            throw new IllegalStateException("La versión de reglas " + idVersionReglas + " del crédito " + credito
                    + " cambió después de su originación (huella " + v.huella() + ", esperada " + huellaReglas + ")");
        }
        return v;
    }
}
