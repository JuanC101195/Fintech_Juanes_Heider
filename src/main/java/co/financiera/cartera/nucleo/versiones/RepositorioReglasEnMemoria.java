package co.financiera.cartera.nucleo.versiones;

import java.time.LocalDate;
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
 * {@code VigenciasCobranza}: así la tabla siempre dice qué regía cuando se originó cada crédito. No es seguro para
 * varios hilos (el núcleo no los usa).
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
        porId.put(version.id(), version);
        porFecha.put(version.vigenteDesde(), version);
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
