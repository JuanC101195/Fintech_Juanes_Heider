package co.financiera.cartera.nucleo.versiones;

import co.financiera.cartera.nucleo.AltaVersionReglas;
import co.financiera.cartera.nucleo.BitacoraParametros;
import co.financiera.cartera.nucleo.Responsable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Versiones de reglas en memoria, para pruebas, simulaciones y para exponer los perfiles del archivo de configuración.
 * Solo se agregan versiones (nunca se cambia ni se borra una), y cada una debe empezar después de la última, como
 * {@code VigenciasCobranza}: así la tabla siempre dice qué regía cuando se originó cada crédito. Las versiones que ya
 * existían (archivo de configuración) se cargan con {@link #con}; las nuevas entran con {@link #registrar}, que exige
 * administrador y deja bitácora. No es seguro para varios hilos (el núcleo no los usa).
 */
public final class RepositorioReglasEnMemoria implements RepositorioReglas {

    private final Map<String, VersionReglas> porId = new LinkedHashMap<>();
    private final NavigableMap<LocalDate, VersionReglas> porFecha = new TreeMap<>();

    /** Repositorio con las versiones dadas, que deben venir en orden de {@code vigenteDesde} estrictamente creciente. */
    public static RepositorioReglasEnMemoria con(VersionReglas... versiones) {
        RepositorioReglasEnMemoria repo = new RepositorioReglasEnMemoria();
        for (VersionReglas v : versiones) {
            repo.agregar(v);
        }
        return repo;
    }

    /**
     * Agrega una versión ya existente (cargada de un archivo o de la base de datos), sin bitácora. Para el alta de
     * una versión nueva por un administrador está {@link #registrar}. Reglas: el id no se repite y la vigencia
     * empieza después de la última.
     */
    void agregar(VersionReglas version) {
        validarAgregar(version);
        porId.put(version.id(), version);
        porFecha.put(version.vigenteDesde(), version);
    }

    /**
     * Alta de una versión nueva por el administrador o su suplente (D33, D41): rige desde su fecha solo para los
     * créditos que se originen desde entonces (los anteriores conservan la suya) y queda en la bitácora con quién,
     * cuándo, qué (id, huella, versión anterior) y por qué. {@code registradoEn} lo da quien llama (el núcleo no lee el
     * reloj); la versión no puede regir antes de ese día ni antes de la última.
     *
     * <p>Idempotente: registrar otra vez la misma versión (mismo id y mismo contenido) no hace nada y devuelve
     * {@code false}, para que un reintento no duplique la bitácora. Si algo falla, no se agrega ni se registra nada.
     *
     * @return {@code true} si la versión es nueva
     */
    public boolean registrar(VersionReglas version, Responsable responsable, LocalDateTime registradoEn,
            String justificacion, BitacoraParametros bitacora) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(bitacora, "La bitácora es obligatoria");
        VersionReglas existente = porId.get(version.id());
        if (existente != null && existente.equals(version)) {
            return false;
        }
        AltaVersionReglas alta = new AltaVersionReglas(version.id(), version.vigenteDesde(), version.huella(),
                ultima().map(VersionReglas::id).orElse(null), responsable, registradoEn, justificacion);
        validarAgregar(version);
        bitacora.registrar(alta);
        porId.put(version.id(), version);
        porFecha.put(version.vigenteDesde(), version);
        return true;
    }

    private void validarAgregar(VersionReglas version) {
        Objects.requireNonNull(version, "version");
        VersionReglas existente = porId.get(version.id());
        if (existente != null) {
            throw new IllegalArgumentException("Ya existe la versión de reglas " + version.id()
                    + (existente.mismoContenidoQue(version) ? "" : " con otro contenido: una versión no se cambia"));
        }
        if (!porFecha.isEmpty() && !version.vigenteDesde().isAfter(porFecha.lastKey())) {
            throw new IllegalArgumentException("La versión " + version.id() + " (desde " + version.vigenteDesde()
                    + ") debe empezar después de la última (" + porFecha.lastEntry().getValue().id() + ", desde "
                    + porFecha.lastKey() + ")");
        }
    }

    @Override
    public Optional<VersionReglas> porVersion(String id) {
        return Optional.ofNullable(porId.get(id));
    }

    @Override
    public Optional<VersionReglas> vigenteEn(LocalDate fecha) {
        Objects.requireNonNull(fecha, "fecha");
        var entrada = porFecha.floorEntry(fecha);
        return entrada == null ? Optional.empty() : Optional.of(entrada.getValue());
    }

    @Override
    public List<VersionReglas> versiones() {
        return List.copyOf(new ArrayList<>(porFecha.values()));
    }

    /** La versión más reciente, si hay alguna. */
    public Optional<VersionReglas> ultima() {
        return porFecha.isEmpty() ? Optional.empty() : Optional.of(porFecha.lastEntry().getValue());
    }
}
