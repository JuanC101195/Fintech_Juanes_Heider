package co.financiera.cartera.nucleo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Un cambio de un parámetro con vigencia, para la bitácora (D26, D45): qué parámetro, desde cuándo rige, el valor que
 * habría regido ese día sin el cambio ({@code null} si no había ninguno en la tabla), el nuevo, quién lo hizo, cuándo
 * y por qué.
 */
public record CambioParametro(
        String parametro,
        LocalDate vigenteDesde,
        BigDecimal valorAnterior,
        BigDecimal valorNuevo,
        String administrador,
        LocalDateTime registradoEn,
        String justificacion) implements EventoBitacora {

    public CambioParametro {
        Objects.requireNonNull(parametro, "parametro");
        Objects.requireNonNull(vigenteDesde, "vigenteDesde");
        Objects.requireNonNull(valorNuevo, "valorNuevo");
        Objects.requireNonNull(registradoEn, "registradoEn");
        if (administrador == null || administrador.isBlank()) {
            throw new IllegalArgumentException("El cambio de un parámetro debe decir qué administrador lo hizo");
        }
        if (justificacion == null || justificacion.isBlank()) {
            throw new IllegalArgumentException("El cambio de un parámetro debe llevar justificación");
        }
    }

    @Override
    public String quien() {
        return administrador;
    }
}
