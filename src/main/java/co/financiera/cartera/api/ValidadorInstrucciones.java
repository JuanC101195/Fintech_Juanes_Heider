package co.financiera.cartera.api;

import co.financiera.cartera.api.Dtos.InstruccionDto;
import co.financiera.cartera.nucleo.motor.Escenario;
import java.util.List;

/** Request → núcleo: valida las instrucciones de un escenario y las carga en él, periodo a periodo. */
final class ValidadorInstrucciones {

    /** Máximo de instrucciones por petición. */
    static final int MAX_INSTRUCCIONES = 500;
    /** Máximo de días de atraso (o desde la última cuota) de una instrucción: 10 años. */
    static final int MAX_DIAS = 3650;

    private ValidadorInstrucciones() {
    }

    /** Valida cada instrucción (periodo dentro del plazo y topes numéricos) y la agrega al escenario. */
    static void cargar(Escenario e, List<InstruccionDto> instrucciones, int ultimoPeriodo) {
        if (instrucciones == null) {
            return;
        }
        if (instrucciones.size() > MAX_INSTRUCCIONES) {
            throw ApiException.datoInvalido("Una petición admite como máximo " + MAX_INSTRUCCIONES
                    + " instrucciones; llegaron " + instrucciones.size(), "instrucciones");
        }
        for (InstruccionDto i : instrucciones) {
            validarPeriodo(i, ultimoPeriodo);
            validarInstruccion(i);
            e.en(i.periodo(), Instrucciones.aMotor(i));
        }
    }

    private static void validarPeriodo(InstruccionDto i, int ultimoPeriodo) {
        if (i == null || i.periodo() == null || i.periodo() < 1 || i.periodo() > ultimoPeriodo) {
            throw ApiException.datoInvalido("Cada instrucción necesita un periodo entre 1 y "
                    + ultimoPeriodo + ": " + (i == null ? null : i.periodo()), "periodo");
        }
    }

    /** Topes de los campos numéricos de una instrucción, antes de convertirla para el motor. */
    static void validarInstruccion(InstruccionDto i) {
        diasAcotados(i.diasAtraso(), "diasAtraso");
        diasAcotados(i.diasDesdeUltimaCuota(), "diasDesdeUltimaCuota");
        if (tiene(i.fraccion())) {
            Decimales.fraccion(i.fraccion(), "fraccion");
        }
        if (tiene(i.valor())) {
            Decimales.dineroObligatorio(i.valor(), "valor", false);
        }
        if (tiene(i.valorMoto())) {
            Decimales.dineroObligatorio(i.valorMoto(), ValidadorCredito.CAMPO_VALOR_MOTO, true);
        }
        if (tiene(i.costoRecuperacion())) {
            Decimales.dineroObligatorio(i.costoRecuperacion(), "costoRecuperacion", false);
        }
        if (tiene(i.usuraEA())) {
            Decimales.tasa(i.usuraEA(), "usuraEA");
        }
        if (tiene(i.nuevaTasaEA())) {
            Decimales.tasa(i.nuevaTasaEA(), "nuevaTasaEA");
        }
    }

    private static boolean tiene(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static void diasAcotados(Integer dias, String campo) {
        if (dias != null && (dias < 0 || dias > MAX_DIAS)) {
            throw ApiException.datoInvalido("El campo " + campo + " debe estar entre 0 y " + MAX_DIAS, campo);
        }
    }
}
