package co.financiera.cartera.casos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.data.Offset.offset;

import co.financiera.cartera.contabilidad.Contabilizador;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.LineaCredito;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.motor.Escenario;
import co.financiera.cartera.nucleo.motor.EstadoCredito;
import co.financiera.cartera.nucleo.motor.MotorCartera;
import co.financiera.cartera.nucleo.motor.Movimiento;
import co.financiera.cartera.simulaciones.EscenariosExcel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * ADR 0002, paso 5 · Los casos 1 a 12 del Excel en quincenal y mensual (24 meses: 48 y 24 cuotas),
 * con los eventos ubicados por mes con el {@link Calendario} igual que en Inputs. No hay Excel de
 * referencia para estas periodicidades: se verifican controles de coherencia (saldos no negativos,
 * prelación, asientos cuadrados, cierre en cero o por evento), que los servicios de un mes suman el
 * valor mensual y que el giro a terceros es mensual.
 */
class PeriodicidadEscenariosTest {

    private static final BigDecimal UN_PESO = BigDecimal.ONE;

    private static ParametrosProducto parametros(Periodicidad per) {
        return ParametrosProducto.deMeses(LineaCredito.CPPU, per, 24);
    }

    /** Los 12 casos con los eventos de Inputs: meses 3-4 (caso 4), 7-8 (caso 3), 10 (casos 5 a 9, 11, 12), 11 (casos 2 y 10). */
    private static Map<String, Escenario> escenarios(Periodicidad per) {
        ParametrosProducto p = parametros(per);
        Map<String, Escenario> e = new LinkedHashMap<>();
        e.put("caso1", EscenariosExcel.caso1(p));
        e.put("caso2", EscenariosExcel.caso2(p));
        e.put("caso3", EscenariosExcel.caso3(p, false));
        e.put("caso4", EscenariosExcel.caso4(p));
        e.put("caso5", EscenariosExcel.caso5(p));
        e.put("caso6", EscenariosExcel.caso6(p));
        e.put("caso7", EscenariosExcel.caso7(p));
        e.put("caso8", EscenariosExcel.caso8(p));
        e.put("caso9", EscenariosExcel.caso9(p));
        e.put("caso10", EscenariosExcel.caso10(p));
        e.put("caso11", EscenariosExcel.caso11(p));
        e.put("caso12", EscenariosExcel.caso12(p));
        return e;
    }

    private static final List<String> TERMINAN_CANCELADOS = List.of("caso1", "caso2", "caso5", "caso6", "caso7", "caso10", "caso12");
    private static final List<String> TERMINAN_POR_EVENTO = List.of("caso3", "caso4", "caso11");

    private static final Map<String, Function<Movimiento, BigDecimal>> SALDOS = Map.of(
            "saldo final", Movimiento::saldoFinal,
            "capital vencido", Movimiento::capitalVencido,
            "CxC mora", Movimiento::cxcMora,
            "CxC interés corriente", Movimiento::cxcInteresCorriente,
            "CxC servicios", Movimiento::cxcServicios);

    @ParameterizedTest
    @EnumSource(value = Periodicidad.class, names = {"QUINCENAL", "MENSUAL"})
    void losDoceCasosSonCoherentes(Periodicidad per) {
        List<String> errores = new ArrayList<>();
        escenarios(per).forEach((caso, escenario) -> {
            List<Movimiento> movs = new MotorCartera().ejecutar(escenario);
            for (Movimiento m : movs) {
                SALDOS.forEach((nombre, saldo) -> {
                    if (saldo.apply(m).signum() < 0) {
                        errores.add(caso + " · periodo " + m.periodo() + " · " + nombre + " negativo");
                    }
                });
                BigDecimal aplicado = Calc.sumar(m.pagoCobranza(), m.pagoMora(), m.pagoInteresCorriente(), m.pagoServicios(),
                        m.pagoCapital());
                if (Calc.restar(m.pagoRecibido(), aplicado).abs().compareTo(UN_PESO) > 0) {
                    errores.add(caso + " · periodo " + m.periodo() + " · la prelación no reparte el pago recibido");
                }
            }
            Movimiento ultimo = movs.getLast();
            if (TERMINAN_CANCELADOS.contains(caso)
                    && (ultimo.estado() != EstadoCredito.CANCELADO || ultimo.saldoFinal().abs().compareTo(UN_PESO) > 0)) {
                errores.add(caso + " · termina " + ultimo.estado() + " con saldo " + ultimo.saldoFinal().toPlainString());
            }
            if (TERMINAN_POR_EVENTO.contains(caso) && ultimo.estado() != EstadoCredito.CERRADO_POR_EVENTO) {
                errores.add(caso + " · debía cerrar por la recuperación de la moto y termina " + ultimo.estado());
            }
            assertThatCode(() -> Contabilizador.estandar().contabilizar(movs)).as(caso).doesNotThrowAnyException();
        });
        assertThat(errores).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Periodicidad.class, names = {"QUINCENAL", "MENSUAL"})
    void losCasosOchoYNueveUsanElLimiteDelExcelEnPeriodos(Periodicidad per) {
        Map<String, Escenario> e = escenarios(per);
        // Inputs C66 y C73: ROUNDDOWN(100 y 120 semanas × ppa / 52).
        assertThat(e.get("caso8").parametros().plazoLimitePeriodos()).isEqualTo(per.periodosDesdeSemanas(100));
        assertThat(e.get("caso9").parametros().plazoLimitePeriodos()).isEqualTo(per.periodosDesdeSemanas(120));
        assertThat(new MotorCartera().ejecutar(e.get("caso9")).getLast().periodo()).isEqualTo(per.periodosDesdeSemanas(120));
    }

    @ParameterizedTest
    @EnumSource(value = Periodicidad.class, names = {"SEMANAL", "QUINCENAL", "MENSUAL"})
    void losServiciosDeUnMesSumanElValorMensualYSeGiranCadaMes(Periodicidad per) {
        ParametrosProducto p = parametros(per);
        BigDecimal monto = Operacion.ejemploExcel().montoFinanciado();
        BigDecimal conIva = Calc.sumar(Calc.UNO, p.iva());
        Servicios mensual = new Servicios(Calc.multiplicar(Calc.multiplicar(monto, p.fondoGarantiasPorcentajeMensual()), conIva),
                Calc.multiplicar(p.asistenciaMensual(), conIva), Calc.multiplicar(monto, p.seguroPorcentajeMensual()));
        Servicios porCuota = Servicios.porCuota(monto, p);
        // Valor mensual × 12 / ppa por cuota: mensual completo, quincenal la mitad, semanal ÷ 4,333.
        assertThat(Calc.multiplicar(porCuota.total(), p.periodosPorMes())).isCloseTo(mensual.total(),
                offset(new BigDecimal("0.000001")));

        List<Movimiento> movs = new MotorCartera().ejecutar(EscenariosExcel.caso1(p));
        BigDecimal girado = Calc.CERO;
        for (Movimiento m : movs.subList(1, movs.size())) {
            boolean cierraMes = Calendario.mes(m.periodo() + 1, p) > Calendario.mes(m.periodo(), p);
            if (!cierraMes) {
                assertThat(m.pagoTerceros().total()).as("periodo %d sin cierre de mes", m.periodo()).isZero();
            }
            girado = Calc.sumar(girado, m.pagoTerceros().total());
        }
        BigDecimal pagado = movs.stream().map(Movimiento::pagoServicios).reduce(Calc.CERO, BigDecimal::add);
        assertThat(girado).isCloseTo(pagado, offset(UN_PESO));
        if (per == Periodicidad.MENSUAL) {
            // En mensual cada cuota cierra un mes: se gira en todas, y cada giro es el valor mensual completo.
            assertThat(movs.subList(1, movs.size())).allSatisfy(m -> assertThat(m.pagoTerceros().total())
                    .isCloseTo(mensual.total(), offset(UN_PESO)));
        }
    }
}
