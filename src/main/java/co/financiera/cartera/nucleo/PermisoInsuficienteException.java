package co.financiera.cartera.nucleo;

/**
 * El rol de quien pide una operación sensible no alcanza para hacerla (D33, D41, D45). El API la traducirá a 403 cuando
 * tenga usuarios; hoy solo la lanza el modelo.
 */
public class PermisoInsuficienteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PermisoInsuficienteException(String mensaje) {
        super(mensaje);
    }
}
