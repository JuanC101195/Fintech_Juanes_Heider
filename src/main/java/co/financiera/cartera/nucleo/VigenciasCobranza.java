package co.financiera.cartera.nucleo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Valor diario de los gastos de cobranza con vigencias en el tiempo (Diego, D26 y D44: "parámetro con vigencia,
 * cambia en el tiempo; cada crédito guarda el vigente cuando se origina"; solo lo cambia un administrador y queda en
 * bitácora). Modelo puro, sin base de datos: una tabla inmutable {@code fecha desde → valor diario}.
 *
 * <ul>
 *   <li>El valor vigente en una fecha es el de la vigencia más reciente que empezó en o antes de esa fecha.</li>
 *   <li>El crédito toma el valor vigente en su fecha de originación y lo guarda en sus parámetros
 *       ({@link ParametrosProducto#conCobranzaVigente}); cambiar la tabla después no lo afecta, porque cada cambio
 *       devuelve una tabla nueva y el crédito ya tiene el valor copiado.</li>
 *   <li>Sin tabla, o con una fecha anterior a la primera vigencia, se usa el valor por defecto (hoy
 *       {@code ParametrosProducto.cobranzaDiaria} o {@code ReglasNegocio.cobranzaDiaria}, 3.000).</li>
 *   <li>Supuestos conservadores, pendientes de confirmar con Diego: las vigencias solo se agregan hacia adelante
 *       (no se corrige ni se borra una vigencia ya registrada, ni se crea una que empiece antes del día del
 *       cambio), para que la tabla siempre diga lo que de verdad estaba vigente cuando se originó cada crédito.</li>
 * </ul>
 */
public final class VigenciasCobranza {

    /** Nombre del parámetro en la bitácora. */
    public static final String PARAMETRO = "cobranzaDiaria";

    private static final VigenciasCobranza VACIA = new VigenciasCobranza(new TreeMap<>());

    private final NavigableMap<LocalDate, BigDecimal> vigencias;

    private VigenciasCobranza(NavigableMap<LocalDate, BigDecimal> vigencias) {
        this.vigencias = vigencias;
    }

    /** Tabla sin vigencias: todo crédito toma el valor por defecto. */
    public static VigenciasCobranza vacia() {
        return VACIA;
    }

    /**
     * Tabla con una vigencia más que empieza en {@code desde}. Solo hacia adelante: {@code desde} debe ser posterior a
     * la última vigencia registrada. El valor no puede ser negativo.
     */
    public VigenciasCobranza conVigencia(LocalDate desde, BigDecimal valorDiario) {
        Objects.requireNonNull(desde, "La fecha desde la que rige es obligatoria");
        if (valorDiario == null || valorDiario.signum() < 0) {
            throw new IllegalArgumentException("La cobranza diaria debe ser cero o positiva: " + valorDiario);
        }
        if (!vigencias.isEmpty() && !desde.isAfter(vigencias.lastKey())) {
            throw new IllegalArgumentException("La vigencia nueva (" + desde + ") debe empezar después de la última ("
                    + vigencias.lastKey() + "); las vigencias registradas no se cambian");
        }
        TreeMap<LocalDate, BigDecimal> nuevas = new TreeMap<>(vigencias);
        nuevas.put(desde, valorDiario);
        return new VigenciasCobranza(nuevas);
    }

    /**
     * Cambio hecho por un administrador (D26): agrega la vigencia y la registra en la bitácora con quién, cuándo y por
     * qué (D45). El permiso de administrador lo valida quien llama (el API); aquí solo se exige que venga quién y por
     * qué. {@code registradoEn} lo da quien llama (el núcleo no lee el reloj). La vigencia no puede empezar antes del
     * día del cambio: un cambio retroactivo diría que regía un valor que nadie aplicó.
     */
    public VigenciasCobranza cambiar(LocalDate desde, BigDecimal valorDiario, String administrador,
            LocalDateTime registradoEn, String justificacion, BitacoraParametros bitacora) {
        Objects.requireNonNull(bitacora, "La bitácora es obligatoria");
        Objects.requireNonNull(registradoEn, "La fecha y hora del cambio son obligatorias");
        Objects.requireNonNull(desde, "La fecha desde la que rige es obligatoria");
        if (desde.isBefore(registradoEn.toLocalDate())) {
            throw new IllegalArgumentException("La vigencia no puede empezar antes del día del cambio: " + desde + " < "
                    + registradoEn.toLocalDate());
        }
        VigenciasCobranza nueva = conVigencia(desde, valorDiario);
        bitacora.registrar(new CambioParametro(PARAMETRO, desde, valorEn(desde).orElse(null), valorDiario,
                administrador, registradoEn, justificacion));
        return nueva;
    }

    /**
     * Como {@link #cambiar(LocalDate, BigDecimal, String, LocalDateTime, String, BitacoraParametros)}, pero con el rol
     * de quien cambia: solo el administrador o su suplente (D26, D33, D41); si no, {@link PermisoInsuficienteException}
     * y no se cambia ni se registra nada.
     */
    public VigenciasCobranza cambiar(LocalDate desde, BigDecimal valorDiario, Responsable responsable,
            LocalDateTime registradoEn, String justificacion, BitacoraParametros bitacora) {
        Objects.requireNonNull(responsable, "responsable")
                .exigirAdministrador("cambiar la cobranza diaria");
        return cambiar(desde, valorDiario, responsable.nombre(), registradoEn, justificacion, bitacora);
    }

    /** Valor vigente en {@code fecha}; vacío si no hay ninguna vigencia que haya empezado para entonces. */
    public Optional<BigDecimal> valorEn(LocalDate fecha) {
        Objects.requireNonNull(fecha, "fecha");
        Map.Entry<LocalDate, BigDecimal> vigente = vigencias.floorEntry(fecha);
        return vigente == null ? Optional.empty() : Optional.of(vigente.getValue());
    }

    /** Valor vigente en {@code fecha} o {@code porDefecto} si la tabla no tiene uno para esa fecha. */
    public BigDecimal valorEn(LocalDate fecha, BigDecimal porDefecto) {
        return valorEn(fecha).orElse(porDefecto);
    }

    /** Vigencias registradas, en orden de fecha (solo lectura). */
    public NavigableMap<LocalDate, BigDecimal> vigencias() {
        return Collections.unmodifiableNavigableMap(vigencias);
    }

    public boolean estaVacia() {
        return vigencias.isEmpty();
    }
}
