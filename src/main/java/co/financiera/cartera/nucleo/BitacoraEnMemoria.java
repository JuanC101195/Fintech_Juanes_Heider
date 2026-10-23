package co.financiera.cartera.nucleo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bitácora en memoria, para pruebas y simulaciones: guarda los registros en el orden en que llegan y solo deja
 * agregar. La de producción guardará lo mismo en una tabla de solo inserción (ADR 0004). No es segura para varios
 * hilos.
 */
public final class BitacoraEnMemoria implements BitacoraParametros {

    private final List<EventoBitacora> eventos = new ArrayList<>();

    @Override
    public void registrar(CambioParametro cambio) {
        registrar((EventoBitacora) cambio);
    }

    @Override
    public void registrar(EventoBitacora evento) {
        eventos.add(Objects.requireNonNull(evento, "evento"));
    }

    /** Todos los registros, en orden de llegada (solo lectura). */
    public List<EventoBitacora> eventos() {
        return List.copyOf(eventos);
    }

    /** Los cambios de un parámetro, en orden de llegada. */
    public List<CambioParametro> cambiosDe(String parametro) {
        return eventos.stream().filter(CambioParametro.class::isInstance).map(CambioParametro.class::cast)
                .filter(c -> c.parametro().equals(parametro)).toList();
    }

    /** Las altas de versiones de reglas, en orden de llegada. */
    public List<AltaVersionReglas> altasDeReglas() {
        return eventos.stream().filter(AltaVersionReglas.class::isInstance).map(AltaVersionReglas.class::cast).toList();
    }

    /** Las exoneraciones de un crédito, en orden de llegada. */
    public List<Exoneracion> exoneracionesDe(String credito) {
        return eventos.stream().filter(Exoneracion.class::isInstance).map(Exoneracion.class::cast)
                .filter(e -> e.credito().equals(credito)).toList();
    }
}
