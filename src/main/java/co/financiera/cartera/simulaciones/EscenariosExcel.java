package co.financiera.cartera.simulaciones;

import static co.financiera.cartera.nucleo.Calc.bd;

import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.TipoRecuperacion;

/**
 * Los escenarios de las hojas Caso1 a Caso9 del Excel (parámetros de la hoja Inputs), para
 * reutilizarlos fuera de las pruebas: previsualizaciones del cuestionario y demos.
 */
public final class EscenariosExcel {

    private EscenariosExcel() {
    }

    private static Escenario base(ParametrosProducto p) {
        return new Escenario(Operacion.ejemploExcel(), p);
    }

    public static Escenario caso1(ParametrosProducto p) {
        return base(p);
    }

    /** Pago normal hasta el mes 7, sin pago hasta el siniestro del mes 8 y dación por 3.000.000. */
    public static Escenario caso3(ParametrosProducto p, boolean causaMoraYServicios) {
        int ultimoPagado = Calendario.periodoDelMes(7, p);
        int siniestro = Calendario.periodoDelMes(8, p);
        return base(p).entre(ultimoPagado + 1, siniestro, new SinPago(causaMoraYServicios))
                .en(siniestro, new CierrePorRecuperacion(TipoRecuperacion.DACION_EN_PAGO, bd(3_000_000)));
    }

    /** Abono extra de 2.000.000 en el mes 10 aplicado como menor plazo. */
    public static Escenario caso6(ParametrosProducto p) {
        return base(p).en(Calendario.periodoDelMes(10, p), new AbonoExtra(bd(2_000_000), ModalidadAbono.MENOR_PLAZO));
    }

    /** Pago del 40 % de la cuota en el mes 10; luego vuelve a pagar la cuota pactada. Límite 100 semanas. */
    public static Escenario caso8(ParametrosProducto p) {
        return new Escenario(Operacion.ejemploExcel(), p.conPlazoLimite(100))
                .en(Calendario.periodoDelMes(10, p), new PagoParcial(bd("0.4"), 0));
    }

    /** Desde el mes 10 todas las cuotas se pagan con 14 días de atraso. Límite 120 semanas. */
    public static Escenario caso9(ParametrosProducto p) {
        return new Escenario(Operacion.ejemploExcel(), p.conPlazoLimite(120))
                .desde(Calendario.periodoDelMes(10, p), new PagoContractual(14));
    }
}
