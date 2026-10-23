package co.financiera.cartera.nucleo.motor;

import java.math.BigDecimal;

/**
 * Lo que pasa en un periodo del crédito. El motor arranca cada periodo con un pago contractual a
 * tiempo, salvo que una instrucción diga otra cosa.
 */
public sealed interface Instruccion {

    /** Paga la cuota total exigible (financiera + servicios), con los días de atraso indicados. */
    record PagoContractual(int diasAtraso) implements Instruccion {
        public static final PagoContractual A_TIEMPO = new PagoContractual(0);

        public PagoContractual {
            Validar.dias(diasAtraso);
        }
    }

    /** Paga una fracción de la cuota total exigible (caso 8: 40 %). La fracción va en (0, 1]. */
    record PagoParcial(BigDecimal fraccion, int diasAtraso) implements Instruccion {

        public PagoParcial {
            java.util.Objects.requireNonNull(fraccion, "fraccion");
            // Auditoría 2026-10-23 (H4): una fracción negativa o mayor que 1 no es un pago parcial.
            if (fraccion.signum() <= 0 || fraccion.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("La fracción del pago parcial debe estar entre 0 (sin incluir) y 1: "
                        + fraccion.toPlainString());
            }
            Validar.dias(diasAtraso);
        }
    }

    /** Paga un valor exacto (0 o más). */
    record PagoValor(BigDecimal valor, int diasAtraso) implements Instruccion {

        public PagoValor {
            java.util.Objects.requireNonNull(valor, "valor");
            // Auditoría 2026-10-23 (H4): un pago negativo creaba deuda de la nada (CxC de cobranza negativa).
            if (valor.signum() < 0) {
                throw new IllegalArgumentException("El valor del pago no puede ser negativo: " + valor.toPlainString());
            }
            Validar.dias(diasAtraso);
        }
    }

    /**
     * No paga. Con {@code causaMoraYServicios} en verdadero el capital de la cuota pasa a vencido,
     * se causa mora y los servicios quedan por cobrar (casos 8 y 9). En falso solo se acumula el
     * interés corriente, como hacen los casos 3 y 4 del Excel (pendiente P7).
     */
    record SinPago(boolean causaMoraYServicios) implements Instruccion {
    }

    /**
     * Después de la cuota de la semana, abono extraordinario 100 % a capital (casos 6 y 7). No puede
     * superar el saldo (P-14).
     */
    record AbonoExtra(BigDecimal valor, ModalidadAbono modalidad) implements Instruccion {

        public AbonoExtra {
            java.util.Objects.requireNonNull(valor, "valor");
            java.util.Objects.requireNonNull(modalidad, "modalidad");
            // Auditoría 2026-10-23 (H4): un abono negativo aumentaba el saldo sin desembolso.
            if (valor.signum() <= 0) {
                throw new IllegalArgumentException("El valor del abono extraordinario debe ser positivo: " + valor.toPlainString());
            }
        }

        /** Si el cliente no elige, se reduce el plazo (P-13, Diego 2026-10-16). */
        public AbonoExtra(BigDecimal valor) {
            this(valor, ModalidadAbono.POR_DEFECTO);
        }
    }

    /**
     * Después de la cuota de la semana, paga el saldo pendiente completo (caso 5). En el Excel el
     * prepago se hace el mismo día de la cuota; {@code diasDesdeUltimaCuota} son los días transcurridos
     * desde esa cuota hasta el prepago, por los que se cobra interés corriente si la regla
     * {@code interesPrepagoPorDias} lo pide (Diego, A6, 2026-10-20).
     *
     * <p>{@code fecha} (ADR 0002 paso 8, PPER-7 / D27: "el prepago a mitad de periodo cobra los días transcurridos"):
     * si el escenario trae calendario, los días salen de las fechas reales, desde el vencimiento de la cuota del
     * periodo hasta esta fecha, en lugar de digitarse. Se usa uno u otro, no los dos.
     */
    record PrepagoTotal(int diasDesdeUltimaCuota, java.time.LocalDate fecha) implements Instruccion {

        public PrepagoTotal {
            if (diasDesdeUltimaCuota < 0) {
                throw new IllegalArgumentException("Los días desde la última cuota no pueden ser negativos");
            }
            if (fecha != null && diasDesdeUltimaCuota != 0) {
                throw new IllegalArgumentException("El prepago lleva fecha o días digitados, no los dos");
            }
        }

        /** Prepago con los días desde la última cuota digitados (sin fechas). */
        public PrepagoTotal(int diasDesdeUltimaCuota) {
            this(diasDesdeUltimaCuota, null);
        }

        /** Prepago en una fecha: los días salen del calendario del escenario. */
        public static PrepagoTotal enFecha(java.time.LocalDate fecha) {
            return new PrepagoTotal(0, java.util.Objects.requireNonNull(fecha, "fecha"));
        }

        /** Prepago el mismo día de la cuota, como el Excel. */
        public PrepagoTotal() {
            this(0, null);
        }
    }

    /**
     * Se publica una tasa de usura nueva (EA en fracción) durante esta semana (T2/T4, Diego
     * 2026-10-20). Si la EA vigente del crédito la supera, baja a usura − {@code margenUsura} desde la
     * cuota siguiente (T1); si no la supera, nada cambia (la tasa nunca sube). Qué se conserva lo decide
     * {@code ReglasNegocio.modalidadUsura}: Diego (USU-6, ronda 3) mantiene la cuota y baja el plazo como
     * el caso 10; {@code ReglasNegocio.EXCEL} recalcula la cuota como el caso 2. Ver
     * {@link co.financiera.cartera.nucleo.Usura#tasaAjustada}.
     */
    record UsuraVigente(BigDecimal usuraEA) implements Instruccion {

        public UsuraVigente {
            java.util.Objects.requireNonNull(usuraEA, "usuraEA");
            if (usuraEA.signum() <= 0) {
                throw new IllegalArgumentException("La usura debe ser positiva");
            }
        }
    }

    /**
     * Cambio de tasa EA desde esta semana (P-11): se conserva la fecha final y se recalcula la cuota
     * (caso 2) o se conserva la cuota y cambia el número de cuotas (caso 10).
     */
    record CambioTasa(BigDecimal nuevaTasaEA, ModalidadCambioTasa modalidad) implements Instruccion {

        public CambioTasa {
            java.util.Objects.requireNonNull(nuevaTasaEA, "nuevaTasaEA");
            java.util.Objects.requireNonNull(modalidad, "modalidad");
            // Auditoría 2026-10-23 (H4): una tasa nueva negativa o cero no es un cambio de tasa válido.
            if (nuevaTasaEA.signum() <= 0) {
                throw new IllegalArgumentException("La nueva tasa EA debe ser positiva: " + nuevaTasaEA.toPlainString());
            }
        }

        /** Conserva la fecha final y recalcula la cuota (caso 2). */
        public CambioTasa(BigDecimal nuevaTasaEA) {
            this(nuevaTasaEA, ModalidadCambioTasa.MISMA_FECHA_FINAL);
        }
    }

    /**
     * Paga la cuota del periodo más lo atrasado (mora, interés, servicios pendientes y capital
     * vencido) y deja el crédito al día (caso 12, normalización).
     */
    record PagoAlDia() implements Instruccion {
    }

    /**
     * Recibe la moto y extingue la deuda (caso 3: dación con faltante; caso 4: retoma con excedente).
     *
     * <p>{@code costoRecuperacion} es el costo real de la recuperación con soporte (grúa, bodegaje, avalúo,
     * trámites), 0 por defecto (el Excel no lo tiene). Diego (R4-R2, ronda 4, 2026-10-21) pide descontarlo en
     * la dación y en la retoma: en la retoma reduce el excedente que se devuelve; en la dación lo decide
     * {@code ReglasNegocio.costoRecuperacionAlFondo} (Diego confirmó en P5-COSTO-DACION que se suma al faltante
     * que cubre el fondo).
     */
    record CierrePorRecuperacion(TipoRecuperacion tipo, BigDecimal valorMoto, BigDecimal costoRecuperacion)
            implements Instruccion {

        public CierrePorRecuperacion {
            java.util.Objects.requireNonNull(valorMoto, "valorMoto");
            java.util.Objects.requireNonNull(tipo, "tipo");
            // Auditoría 2026-10-23 (H4): la moto recibida tiene que tener un valor positivo.
            if (valorMoto.signum() <= 0) {
                throw new IllegalArgumentException("El valor de la moto recibida debe ser positivo: " + valorMoto.toPlainString());
            }
            costoRecuperacion = costoRecuperacion == null ? BigDecimal.ZERO : costoRecuperacion;
            if (costoRecuperacion.signum() < 0) {
                throw new IllegalArgumentException("El costo de la recuperación no puede ser negativo: " + costoRecuperacion);
            }
        }

        /** Sin costo de recuperación, como el Excel. */
        public CierrePorRecuperacion(TipoRecuperacion tipo, BigDecimal valorMoto) {
            this(tipo, valorMoto, BigDecimal.ZERO);
        }
    }

    /** Validaciones comunes de las instrucciones (auditoría 2026-10-23, H4). */
    final class Validar {
        private Validar() {
        }

        static void dias(int diasAtraso) {
            if (diasAtraso < 0) {
                throw new IllegalArgumentException("Los días de atraso no pueden ser negativos: " + diasAtraso);
            }
        }
    }

    enum ModalidadAbono {
        /** Mantiene la cuota y reduce el número de cuotas (caso 6). */
        MENOR_PLAZO,
        /** Mantiene el plazo y recalcula una cuota menor (caso 7). */
        MENOR_CUOTA;

        /** Por ley el cliente elige; si no responde se aplica la reducción de plazo (P-13). */
        public static final ModalidadAbono POR_DEFECTO = MENOR_PLAZO;
    }

    enum ModalidadCambioTasa {
        /** Se conserva la fecha final: nueva cuota = PMT(tasa nueva, cuotas restantes, saldo) (caso 2). */
        MISMA_FECHA_FINAL,
        /** Se conserva la cuota y se recalcula el número de cuotas hasta saldo cero (caso 10). */
        MISMA_CUOTA
    }

    enum TipoRecuperacion {
        DACION_EN_PAGO,
        RETOMA
    }
}
