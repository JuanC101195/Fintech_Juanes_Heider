package co.financiera.cartera.nucleo;

/**
 * Punto de extensión para la bitácora de cambios de parámetros sensibles (D26: la cobranza la cambia solo un
 * administrador; D45: queda registro de quién, cuándo y por qué). El núcleo no persiste nada: la implementación real
 * (base de datos, auditoría) vivirá en otro módulo. {@link #NINGUNA} descarta los registros, para simulaciones.
 */
@FunctionalInterface
public interface BitacoraParametros {

    /** No guarda nada (simulaciones y pruebas que no miran la bitácora). */
    BitacoraParametros NINGUNA = cambio -> { };

    void registrar(CambioParametro cambio);
}
