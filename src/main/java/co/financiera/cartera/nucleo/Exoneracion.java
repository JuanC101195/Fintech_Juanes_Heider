package co.financiera.cartera.nucleo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Exoneración o condonación de un cobro de un crédito (D45, COB-5): la autoriza el director financiero o el gerente,
 * con justificación, directamente en el software, y queda quién, cuándo, a qué crédito y por qué. Un objeto de este
 * tipo solo existe si cumple esas reglas. Es el evento y su validación: el motor todavía no lo aplica a los saldos
 * (será una instrucción del crédito, ADR 0004) y los permisos reales los pondrá el API.
 *
 * @param credito       id del crédito
 * @param concepto      qué cobro se exonera
 * @param valor         cuánto (mayor que cero)
 * @param fechaEfecto   fecha del crédito en que aplica
 * @param autorizadoPor director financiero o gerente
 * @param registradoEn  cuándo se registró (lo da quien llama)
 * @param justificacion por qué (obligatoria)
 */
public record Exoneracion(
        String credito,
        Concepto concepto,
        BigDecimal valor,
        LocalDate fechaEfecto,
        Responsable autorizadoPor,
        LocalDateTime registradoEn,
        String justificacion) implements EventoBitacora {

    /**
     * Cobros que se pueden exonerar. Supuesto conservador, pendiente de confirmar con Diego: D45 habla de "un cobro"
     * (en el contexto de la cobranza); el capital no está, porque condonar capital es otra decisión (castigo de
     * cartera) que Diego no ha tomado.
     */
    public enum Concepto {
        GASTOS_COBRANZA,
        INTERES_MORA,
        INTERES_CORRIENTE,
        SERVICIOS
    }

    public Exoneracion {
        EventoBitacora.exigirTexto(credito, "La exoneración debe decir a qué crédito aplica");
        Objects.requireNonNull(concepto, "concepto");
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("El valor exonerado debe ser mayor que cero: " + valor);
        }
        Objects.requireNonNull(fechaEfecto, "fechaEfecto");
        Objects.requireNonNull(autorizadoPor, "autorizadoPor").exigirAutorizaExoneraciones();
        Objects.requireNonNull(registradoEn, "registradoEn");
        EventoBitacora.exigirTexto(justificacion, "La exoneración debe llevar justificación (D45)");
    }

    /** Valida la exoneración, la registra en la bitácora y la devuelve. */
    public static Exoneracion autorizar(String credito, Concepto concepto, BigDecimal valor, LocalDate fechaEfecto,
            Responsable autorizadoPor, LocalDateTime registradoEn, String justificacion, BitacoraParametros bitacora) {
        Objects.requireNonNull(bitacora, "La bitácora es obligatoria");
        Exoneracion e = new Exoneracion(credito, concepto, valor, fechaEfecto, autorizadoPor, registradoEn,
                justificacion);
        bitacora.registrar(e);
        return e;
    }

    @Override
    public String quien() {
        return autorizadoPor.nombre();
    }
}
