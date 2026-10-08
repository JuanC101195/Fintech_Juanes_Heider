package co.financiera.cartera.nucleo.motor;

/** Estados del crédito (HU-19). El Excel dice "Amortizado" en los casos 1, 2 y 7 y "Cancelado" en los demás. */
public enum EstadoCredito {
    DESEMBOLSADO,
    VIGENTE_AL_DIA,
    EN_MORA,
    CANCELADO,
    CERRADO_POR_EVENTO
}
