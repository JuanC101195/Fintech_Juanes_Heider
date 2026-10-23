package co.financiera.cartera.nucleo;

/**
 * Punto de extensión para la bitácora de operaciones sensibles (D26: la cobranza la cambia solo un administrador;
 * D33: el cambio de parámetros y de reglas queda con quién, qué y cuándo; D45: la exoneración, con quién, a qué
 * crédito y por qué). El núcleo no persiste nada: la implementación real (tabla de solo inserción, ADR 0004) vivirá en
 * otro módulo; {@link BitacoraEnMemoria} sirve para pruebas y simulaciones y {@link #NINGUNA} descarta los registros.
 *
 * <p>Una lambda {@code cambio -> ...} solo recibe cambios de parámetros; los demás registros ({@link EventoBitacora})
 * exigen una bitácora que los sepa guardar y, si no, fallan en vez de perderse en silencio.
 */
@FunctionalInterface
public interface BitacoraParametros {

    /** No guarda nada (simulaciones y pruebas que no miran la bitácora). */
    BitacoraParametros NINGUNA = new BitacoraParametros() {
        @Override
        public void registrar(CambioParametro cambio) {
            // descarta
        }

        @Override
        public void registrar(EventoBitacora evento) {
            // descarta
        }
    };

    void registrar(CambioParametro cambio);

    /**
     * Registra cualquier operación sensible. Por defecto solo sabe guardar cambios de parámetros; una implementación
     * que guarde otros registros (altas de reglas, exoneraciones) debe sobrescribirlo.
     */
    default void registrar(EventoBitacora evento) {
        if (evento instanceof CambioParametro cambio) {
            registrar(cambio);
        } else {
            throw new UnsupportedOperationException("Esta bitácora solo guarda cambios de parámetros; no puede guardar "
                    + evento.getClass().getSimpleName());
        }
    }
}
