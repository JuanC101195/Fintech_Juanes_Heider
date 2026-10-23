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
    }

    /** Paga una fracción de la cuota total exigible (caso 8: 40 %). */
    record PagoParcial(BigDecimal fraccion, int diasAtraso) implements Instruccion {
    }

    /** Paga un valor exacto. */
    record PagoValor(BigDecimal valor, int diasAtraso) implements Instruccion {
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
     */
    record PrepagoTotal(int diasDesdeUltimaCuota) implements Instruccion {

        public PrepagoTotal {
            if (diasDesdeUltimaCuota < 0) {
                throw new IllegalArgumentException("Los días desde la última cuota no pueden ser negativos");
            }
        }

        /** Prepago el mismo día de la cuota, como el Excel. */
        public PrepagoTotal() {
            this(0);
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
