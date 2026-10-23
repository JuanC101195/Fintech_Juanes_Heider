package co.financiera.cartera.nucleo.motor;

import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;

/** Lo que piden las instrucciones de un periodo (lo arma {@code MotorCartera.Corrida.leer}). */
final class Ordenes {
    Instruccion pago = PagoContractual.A_TIEMPO;
    AbonoExtra abono;
    PrepagoTotal prepago;
    boolean cambioTasa;
    CierrePorRecuperacion cierre;
}
