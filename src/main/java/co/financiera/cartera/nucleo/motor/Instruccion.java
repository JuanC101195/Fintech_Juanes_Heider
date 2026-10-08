package co.financiera.cartera.nucleo.motor;

import java.math.BigDecimal;

/**
 * Lo que pasa en una semana del crédito. El motor arranca cada semana con un pago contractual a
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

    /** Después de la cuota de la semana, abono extraordinario 100 % a capital (casos 6 y 7). */
    record AbonoExtra(BigDecimal valor, ModalidadAbono modalidad) implements Instruccion {
    }

    /** Después de la cuota de la semana, paga todo el saldo pendiente (caso 5). */
    record PrepagoTotal() implements Instruccion {
    }

    /**
     * Cambio de tasa EA desde esta semana: se recalcula la cuota conservando la fecha final
     * (caso 2).
     */
    record CambioTasa(BigDecimal nuevaTasaEA) implements Instruccion {
    }

    /** Recibe la moto y extingue la deuda (caso 3: dación con faltante; caso 4: retoma con excedente). */
    record CierrePorRecuperacion(TipoRecuperacion tipo, BigDecimal valorMoto) implements Instruccion {
    }

    enum ModalidadAbono {
        /** Mantiene la cuota y reduce el número de cuotas (caso 6). */
        MENOR_PLAZO,
        /** Mantiene el plazo y recalcula una cuota menor (caso 7). */
        MENOR_CUOTA
    }

    enum TipoRecuperacion {
        DACION_EN_PAGO,
        RETOMA
    }
}
