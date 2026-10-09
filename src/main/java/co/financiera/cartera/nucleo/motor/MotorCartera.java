package co.financiera.cartera.nucleo.motor;

import static co.financiera.cartera.nucleo.Calc.CERO;
import static co.financiera.cartera.nucleo.Calc.esCero;
import static co.financiera.cartera.nucleo.Calc.max;
import static co.financiera.cartera.nucleo.Calc.min;
import static co.financiera.cartera.nucleo.Calc.multiplicar;
import static co.financiera.cartera.nucleo.Calc.noNegativo;
import static co.financiera.cartera.nucleo.Calc.restar;
import static co.financiera.cartera.nucleo.Calc.sumar;

import co.financiera.cartera.nucleo.Amortizacion;
import co.financiera.cartera.nucleo.Calc;
import co.financiera.cartera.nucleo.Calendario;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.Tasas;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor de reglas de cartera: recorre el crédito semana a semana y aplica las reglas de los 9
 * casos del Excel con un solo ciclo.
 *
 * <p>En cada semana: (1) eventos de tasa, (2) causación de interés corriente, mora, cobranza,
 * capital contractual y servicios, (3) pago aplicado por prelación: cobranza → mora → interés
 * corriente → servicios → capital (P1, según las fórmulas de los casos 8 y 9), (4) abonos
 * extraordinarios o prepago, (5) cierre por recuperación de la moto, (6) estado y giro a terceros.
 */
public final class MotorCartera {

    public List<Movimiento> ejecutar(Escenario escenario) {
        return new Corrida(escenario).correr();
    }

    /** Estado mutable de una simulación. */
    private static final class Corrida {
        private final Escenario escenario;
        private final ParametrosProducto p;
        private final int cuotasPactadas;
        private final Servicios serviciosPorCuota;
        private final List<Movimiento> filas = new ArrayList<>();

        private Tasas tasas;
        private BigDecimal cuota;
        private BigDecimal saldo;
        private BigDecimal capitalVencido = CERO;
        private BigDecimal cxcInteres = CERO;
        private BigDecimal cxcMora = CERO;
        private BigDecimal cxcServicios = CERO;
        private BigDecimal cxcCobranza = CERO;
        /** Saldo del plan vigente si el cliente pagara todo a tiempo: de él sale el capital contractual. */
        private BigDecimal saldoPlan;
        private int cuotasVencidas;
        private Servicios serviciosDelMes = Servicios.NINGUNO;

        Corrida(Escenario escenario) {
            this.escenario = escenario;
            this.p = escenario.parametros();
            this.cuotasPactadas = Calendario.numeroCuotas(p);
            BigDecimal monto = escenario.operacion().montoFinanciado(p);
            this.serviciosPorCuota = Servicios.porCuota(monto, p);
            this.tasas = Tasas.de(p);
            this.cuota = Amortizacion.cuotaFija(tasas.semanal(), cuotasPactadas, monto);
            this.saldo = monto;
            this.saldoPlan = monto;
        }

        List<Movimiento> correr() {
            filas.add(desembolso());
            for (int periodo = 1; periodo <= p.plazoLimiteSemanas(); periodo++) {
                Movimiento fila = semana(periodo, escenario.instruccionesDe(periodo));
                filas.add(fila);
                if (fila.estado() == EstadoCredito.CANCELADO || fila.estado() == EstadoCredito.CERRADO_POR_EVENTO) {
                    break;
                }
            }
            return List.copyOf(filas);
        }

        private Movimiento desembolso() {
            return new Movimiento(0, 0, "Desembolso", saldo, CERO, CERO, CERO, CERO, CERO, CERO, Servicios.NINGUNO, CERO, CERO,
                    CERO, CERO, CERO, CERO, CERO, CERO, CERO, CERO, CERO, CERO, CERO, saldo, saldo, 0, CERO, CERO, CERO, CERO,
                    Servicios.NINGUNO, EstadoCredito.DESEMBOLSADO);
        }

        private Movimiento semana(int periodo, List<Instruccion> instrucciones) {
            Instruccion pago = PagoContractual.A_TIEMPO;
            AbonoExtra abono = null;
            boolean prepago = false;
            CierrePorRecuperacion cierre = null;
            String evento = null;

            // 1. Cambio de tasa: nueva cuota sobre el saldo, conservando la fecha final (caso 2).
            for (Instruccion i : instrucciones) {
                switch (i) {
                    case CambioTasa c -> {
                        tasas = Tasas.desdeEA(c.nuevaTasaEA(), p.semanasPorAnio(), p.mesesPorAnio());
                        cuota = Amortizacion.cuotaFija(tasas.semanal(), cuotasPactadas - periodo + 1, saldo);
                        saldoPlan = restar(saldo, capitalVencido);
                        evento = "Cambio de tasa";
                    }
                    case AbonoExtra a -> abono = a;
                    case PrepagoTotal t -> prepago = true;
                    case CierrePorRecuperacion c -> cierre = c;
                    default -> pago = i;
                }
            }

            BigDecimal tasa = tasas.semanal();
            BigDecimal saldoInicial = saldo;
            boolean sinPagoSinMora = pago instanceof SinPago s && !s.causaMoraYServicios();
            int dias = diasAtraso(pago);
            boolean dentroDelPlazo = periodo <= cuotasPactadas;

            // 2. Causación de la semana. Las variantes de ReglasNegocio son las opciones que se le
            // preguntan a negocio; EXCEL reproduce el Excel tal cual.
            ReglasNegocio reglas = escenario.reglas();
            BigDecimal baseInteres = reglas.baseInteres() == ReglasNegocio.BaseInteres.SALDO_VIGENTE
                    ? noNegativo(restar(saldoInicial, capitalVencido)) : saldoInicial;
            BigDecimal interes = multiplicar(baseInteres, tasa);
            BigDecimal capitalContractual = CERO;
            if (dentroDelPlazo && !sinPagoSinMora && !esCero(saldoPlan)) {
                capitalContractual = min(saldoPlan, restar(cuota, multiplicar(saldoPlan, tasa)));
            }
            boolean causaServicios = !sinPagoSinMora
                    && (dentroDelPlazo || (reglas.serviciosDespuesDelPlazo() && !esCero(saldoInicial)));
            Servicios servicios = Servicios.NINGUNO;
            if (causaServicios) {
                servicios = reglas.baseServicios() == ReglasNegocio.BaseServicios.SALDO
                        ? Servicios.porCuota(saldoInicial, p) : serviciosPorCuota;
            }
            BigDecimal mora = multiplicar(capitalVencido, tasa);
            if (dias > 0 && reglas.formulaMora() == ReglasNegocio.FormulaMora.VENCIDO_MAS_DIAS) {
                BigDecimal factorDias = restar(Calc.potencia(sumar(Calc.UNO, tasas.diaria()), dias), Calc.UNO);
                mora = sumar(mora, multiplicar(capitalContractual, factorDias));
            }
            BigDecimal cobranza = (dias > 0 && !esCero(saldoInicial)) ? multiplicar(Calc.bd(dias), p.cobranzaDiaria()) : CERO;
            if (reglas.topeCobranzaPorPago() != null) {
                cobranza = min(cobranza, reglas.topeCobranzaPorPago());
            }

            // 3. Pago y prelación.
            BigDecimal exigible = sumar(saldoInicial, cxcCobranza, cobranza, cxcMora, mora, cxcInteres, interes, cxcServicios,
                    servicios.total());
            BigDecimal cuotaTotal = sumar(cuota, servicios.total());
            BigDecimal recibido = switch (pago) {
                case PagoContractual c -> min(cuotaTotal, exigible);
                case PagoParcial c -> min(multiplicar(c.fraccion(), cuotaTotal), exigible);
                case PagoValor c -> min(c.valor(), exigible);
                case SinPago s -> CERO;
                default -> throw new IllegalStateException("Instrucción de pago no soportada: " + pago);
            };

            BigDecimal restante = recibido;
            BigDecimal pagoCobranza = min(restante, sumar(cxcCobranza, cobranza));
            restante = restar(restante, pagoCobranza);
            BigDecimal pagoMora = min(restante, sumar(cxcMora, mora));
            restante = restar(restante, pagoMora);
            BigDecimal pagoInteres = min(restante, sumar(cxcInteres, interes));
            restante = restar(restante, pagoInteres);
            BigDecimal pagoServicios;
            BigDecimal pagoCapital;
            if (reglas.prelacion() == ReglasNegocio.OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS) {
                // Solo se adelanta el capital de la cuota (contractual + vencido); el excedente pasa a servicios.
                pagoCapital = min(restante, min(saldoInicial, sumar(capitalVencido, capitalContractual)));
                restante = restar(restante, pagoCapital);
                pagoServicios = min(restante, sumar(cxcServicios, servicios.total()));
                restante = restar(restante, pagoServicios);
                pagoCapital = sumar(pagoCapital, min(restante, restar(saldoInicial, pagoCapital)));
            } else {
                pagoServicios = min(restante, sumar(cxcServicios, servicios.total()));
                restante = restar(restante, pagoServicios);
                pagoCapital = min(restante, saldoInicial);
            }

            cxcCobranza = noNegativo(restar(sumar(cxcCobranza, cobranza), pagoCobranza));
            cxcMora = noNegativo(restar(sumar(cxcMora, mora), pagoMora));
            cxcInteres = noNegativo(restar(sumar(cxcInteres, interes), pagoInteres));
            cxcServicios = noNegativo(restar(sumar(cxcServicios, servicios.total()), pagoServicios));
            saldo = noNegativo(restar(saldoInicial, pagoCapital));
            if (!sinPagoSinMora) {
                capitalVencido = noNegativo(restar(sumar(capitalVencido, capitalContractual), pagoCapital));
            }
            saldoPlan = noNegativo(restar(saldoPlan, capitalContractual));
            boolean pagoCompleto = recibido.compareTo(restar(cuotaTotal, Calc.EPSILON_SALDO)) >= 0;

            // La fila reporta la cuota que se cobró esta semana: un abono a menor cuota (caso 7) cambia
            // la cuota solo desde la semana siguiente (Caso7 F46 = MIN(B20, D + E); F47 = PMT(...)).
            BigDecimal cuotaDeLaSemana = cuota;

            // 4. Abono extraordinario o prepago, después de la cuota de la semana.
            BigDecimal abonoAplicado = CERO;
            if (abono != null) {
                abonoAplicado = min(abono.valor(), saldo);
                saldo = restar(saldo, abonoAplicado);
                if (abono.modalidad() == ModalidadAbono.MENOR_CUOTA && cuotasPactadas - periodo > 0 && !esCero(saldo)) {
                    cuota = Amortizacion.cuotaFija(tasa, cuotasPactadas - periodo, saldo);
                }
                saldoPlan = restar(saldo, capitalVencido);
                evento = "Cuota + abono extra";
            }
            BigDecimal prepagoCapital = CERO;
            if (prepago) {
                prepagoCapital = saldo;
                saldo = CERO;
                saldoPlan = CERO;
                evento = "Cuota + prepago total";
            }

            // 5. Cierre por recuperación de la moto: se extingue capital + todo lo pendiente.
            BigDecimal deuda = CERO;
            BigDecimal inventario = CERO;
            BigDecimal cxcFcc = CERO;
            BigDecimal cxpDeudor = CERO;
            if (cierre != null) {
                deuda = sumar(saldo, cxcInteres, cxcMora, cxcServicios, cxcCobranza);
                inventario = cierre.valorMoto();
                cxcFcc = noNegativo(restar(deuda, inventario));
                cxpDeudor = noNegativo(restar(inventario, deuda));
                saldo = CERO;
                saldoPlan = CERO;
                capitalVencido = CERO;
                cxcInteres = CERO;
                cxcMora = CERO;
                cxcServicios = CERO;
                cxcCobranza = CERO;
                evento = "Evento de cierre";
            }

            // 6. Estado, cuotas vencidas y giro a terceros.
            BigDecimal pendiente = sumar(capitalVencido, cxcCobranza, cxcMora, cxcInteres, cxcServicios);
            EstadoCredito estado;
            if (cierre != null) {
                estado = EstadoCredito.CERRADO_POR_EVENTO;
            } else if (esCero(sumar(saldo, pendiente))) {
                estado = EstadoCredito.CANCELADO;
            } else if (!esCero(pendiente)) {
                estado = EstadoCredito.EN_MORA;
            } else {
                estado = EstadoCredito.VIGENTE_AL_DIA;
            }
            if (!pagoCompleto && !(pago instanceof PagoContractual)) {
                cuotasVencidas++;
            } else if (esCero(pendiente)) {
                cuotasVencidas = 0;
            }

            serviciosDelMes = serviciosDelMes.mas(servicios);
            Servicios giro = Servicios.NINGUNO;
            boolean terminaMes = Calendario.mes(periodo + 1, p) > Calendario.mes(periodo, p);
            boolean termina = estado == EstadoCredito.CANCELADO || estado == EstadoCredito.CERRADO_POR_EVENTO;
            if (periodo == cuotasPactadas || terminaMes || termina) {
                giro = serviciosDelMes;
                serviciosDelMes = Servicios.NINGUNO;
            }

            if (evento == null) {
                evento = describir(periodo, pago, pagoCompleto, dias, recibido);
            }
            return new Movimiento(periodo, Calendario.mes(periodo, p), evento, saldoInicial, tasa, cuotaDeLaSemana, interes, mora,
                    capitalContractual, cobranza, servicios, recibido, pagoCobranza, pagoMora, pagoInteres, pagoServicios,
                    pagoCapital, abonoAplicado, prepagoCapital, cxcCobranza, cxcMora, cxcInteres, cxcServicios, capitalVencido,
                    max(CERO, restar(saldo, capitalVencido)), saldo, cuotasVencidas, deuda, inventario, cxcFcc, cxpDeudor, giro,
                    estado);
        }

        private int diasAtraso(Instruccion pago) {
            return switch (pago) {
                case PagoContractual c -> c.diasAtraso();
                case PagoParcial c -> c.diasAtraso();
                case PagoValor c -> c.diasAtraso();
                default -> 0;
            };
        }

        private String describir(int periodo, Instruccion pago, boolean completo, int dias, BigDecimal recibido) {
            if (pago instanceof SinPago) {
                return "Mora - sin pago";
            }
            if (periodo > cuotasPactadas) {
                return "Periodo adicional";
            }
            if (dias > 0) {
                return "Pago tardío + cobranza";
            }
            if (!completo && recibido.signum() > 0) {
                return "Pago inferior";
            }
            return periodo == cuotasPactadas ? "Pago final" : "Pago normal";
        }
    }
}
