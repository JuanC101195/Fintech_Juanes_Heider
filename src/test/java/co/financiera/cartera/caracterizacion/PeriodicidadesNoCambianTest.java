package co.financiera.cartera.caracterizacion;

import static co.financiera.cartera.nucleo.Calc.bd;

import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadCambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.UsuraVigente;
import co.financiera.cartera.nucleo.motor.ReglasNegocio;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Caracterización de quincenal y mensual (24 meses: 48 y 24 cuotas) con perfiles EXCEL y NEGOCIO, sin fechas y con
 * fechas (calendario): una huella por escenario en {@code caracterizacion/periodicidades.sha256}, con el nombre
 * {@code PERIODICIDAD/sin-fechas|con-fechas/PERFIL/escenario}. No hay azar: todo es fijo. Regenerar:
 * {@code ./mvnw -q -B test -Dtest=PeriodicidadesNoCambianTest -Dcaracterizacion.regenerar=true}.
 */
class PeriodicidadesNoCambianTest {

    private static final LocalDate DESEMBOLSO = LocalDate.of(2026, 10, 23);

    private static ParametrosProducto parametros(Periodicidad per) {
        return ParametrosProducto.deMeses(LineaCredito.CPPU, per, 24);
    }

    private static CalendarioPagos calendario(Periodicidad per, LocalDate desembolso, LocalDate primerVencimiento) {
        return per == Periodicidad.QUINCENAL ? CalendarioPagos.quincenal(desembolso, primerVencimiento)
                : CalendarioPagos.mensual(desembolso, primerVencimiento);
    }

    private static Escenario base(ParametrosProducto p, CalendarioPagos cal, ReglasNegocio reglas) {
        Escenario e = new Escenario(Operacion.ejemploExcel(), p).conReglas(reglas);
        return cal == null ? e : e.conCalendario(cal);
    }

    private static Map<String, Escenario> escenarios(Periodicidad per, boolean conFechas, ReglasNegocio reglas) {
        ParametrosProducto p = parametros(per);
        // Quincenal: 23-oct → 5-nov (13 días). Mensual: 23-oct → 5-dic (43 días, como el ejemplo de P5-PRORRATEO).
        LocalDate primero = per == Periodicidad.QUINCENAL ? LocalDate.of(2026, 11, 5) : LocalDate.of(2026, 12, 5);
        // Primera cuota "casi completa": quincenal 6-oct → 20-oct (14 días); mensual 6-oct → 5-nov (30 días).
        LocalDate desembolsoLargo = LocalDate.of(2026, 10, 6);
        LocalDate primeroLargo = per == Periodicidad.QUINCENAL ? LocalDate.of(2026, 10, 20) : LocalDate.of(2026, 11, 5);
        CalendarioPagos cal = conFechas ? calendario(per, DESEMBOLSO, primero) : null;
        int mes10 = Calendario.periodoDelMes(10, p);
        int mes8 = Calendario.periodoDelMes(8, p);

        Map<String, Escenario> e = new LinkedHashMap<>();
        e.put("al-dia", base(p, cal, reglas));
        e.put("mora-dos-periodos-y-normaliza",
                base(p, cal, reglas).entre(mes8, mes8 + 1, new SinPago(true)).en(mes8 + 2, new PagoAlDia()));
        e.put("pago-tardio-14-dias-cobranza", sobre(EscenariosExcel.caso9(p), cal, reglas));
        e.put("abono-menor-plazo",
                base(p, cal, reglas).en(mes10, new AbonoExtra(bd(2_000_000), ModalidadAbono.MENOR_PLAZO)));
        e.put("abono-menor-cuota",
                base(p, cal, reglas).en(mes10, new AbonoExtra(bd(2_000_000), ModalidadAbono.MENOR_CUOTA)));
        e.put("prepago-total", base(p, cal, reglas).en(mes10, new PrepagoTotal()));
        if (conFechas) {
            e.put("prepago-total-a-mitad-de-periodo", base(p, cal, reglas).en(mes10,
                    PrepagoTotal.enFecha(cal.vencimiento(mes10).plusDays(3))));
        } else {
            e.put("prepago-total-a-mitad-de-periodo", base(p, cal, reglas).en(mes10, new PrepagoTotal(3)));
        }
        e.put("usura-modalidad-del-perfil", base(p, cal, reglas).en(mes8, new UsuraVigente(bd("0.50"))));
        e.put("cambio-tasa-misma-cuota",
                base(p, cal, reglas).en(mes8, new CambioTasa(bd("0.60"), ModalidadCambioTasa.MISMA_CUOTA)));
        e.put("cambio-tasa-misma-fecha-final",
                base(p, cal, reglas).en(mes8, new CambioTasa(bd("0.60"), ModalidadCambioTasa.MISMA_FECHA_FINAL)));
        e.put("dacion", sobre(EscenariosExcel.caso3(p, false), cal, reglas));
        e.put("retoma", sobre(EscenariosExcel.caso4(p), cal, reglas));
        e.put("pago-parcial-y-sigue", base(p, cal, reglas).en(mes10, new PagoParcial(bd("0.4"), 0)));
        e.put("pago-parcial-y-normaliza", base(p, cal, reglas).en(mes10, new PagoParcial(bd("0.4"), 0))
                .en(Calendario.periodoDelMes(14, p), new PagoAlDia()));
        if (conFechas) {
            e.put("primera-cuota-prorrateada", base(p, calendario(per, desembolsoLargo, primeroLargo), reglas));
        }
        return e;
    }

    private static Escenario sobre(Escenario e, CalendarioPagos cal, ReglasNegocio reglas) {
        e.conReglas(reglas);
        return cal == null ? e : e.conCalendario(cal);
    }

    @Test
    void quincenalYMensualNoCambianNiUnPeso() throws IOException {
        Map<String, ReglasNegocio> perfiles = new LinkedHashMap<>();
        perfiles.put("EXCEL", ReglasNegocio.EXCEL);
        perfiles.put("NEGOCIO", NegocioNoCambiaTest.reglasNegocio());

        Map<String, String> actual = new LinkedHashMap<>();
        for (Periodicidad per : new Periodicidad[] {Periodicidad.QUINCENAL, Periodicidad.MENSUAL}) {
            for (boolean conFechas : new boolean[] {false, true}) {
                for (Map.Entry<String, ReglasNegocio> perfil : perfiles.entrySet()) {
                    escenarios(per, conFechas, perfil.getValue()).forEach((nombre, escenario) -> actual.put(
                            per.name() + "/" + (conFechas ? "con-fechas" : "sin-fechas") + "/" + perfil.getKey() + "/" + nombre,
                            Huellas.huella(escenario)));
                }
            }
        }
        Huellas.verificar("periodicidades.sha256", actual);
    }
}
