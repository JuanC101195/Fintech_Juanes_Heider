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
        /** Saldo del plan vigente si el cliente pagara siempre a tiempo: de él sale el capital contractual. */
        private BigDecimal saldoPlan;
        private int cuotasVencidas;
        private Servicios serviciosDelMes = Servicios.NINGUNO;

        Corrida(Escenario escenario) {
            this.escenario = escenario;
            this.p = escenario.parametros();
            this.cuotasPactadas = Calendario.numeroCuotas(p);
            BigDecimal monto = escenario.operacion().montoFinanciado();
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
            Ordenes ordenes = leer(periodo, instrucciones);
            Instruccion pago = ordenes.pago;
            BigDecimal tasa = tasas.semanal();
            BigDecimal saldoInicial = saldo;
            // P7 / P-16: con moraAntesDeRecuperacion toda semana sin pago causa mora y servicios, aunque
            // el escenario la marque como en los casos 3 y 4 del Excel.
            boolean sinPagoSinMora = pago instanceof SinPago(boolean causaMora) && !causaMora
                    && !escenario.reglas().moraAntesDeRecuperacion();
            int dias = diasAtraso(pago);

            // 2. Causación de la semana.
            Causacion causado = causar(periodo, saldoInicial, sinPagoSinMora, dias);

            // 3. Pago y prelación.
            BigDecimal cuotaTotal = sumar(cuota, causado.servicios().total());
            BigDecimal recibido = recibido(pago, cuotaTotal, saldoInicial, causado);
            Reparto reparto = repartir(recibido, saldoInicial, causado);
            actualizarSaldos(saldoInicial, causado, reparto, sinPagoSinMora);
            boolean pagoCompleto = recibido.compareTo(restar(cuotaTotal, Calc.EPSILON_SALDO)) >= 0;

            // La fila reporta la cuota que se cobró esta semana: un abono a menor cuota (caso 7) cambia
            // la cuota solo desde la semana siguiente (Caso7 F46 = MIN(B20, D + E); F47 = PMT(...)).
            BigDecimal cuotaDeLaSemana = cuota;

            // 4. Abono extraordinario o prepago, después de la cuota de la semana.
            BigDecimal abonoAplicado = ordenes.abono == null ? CERO : aplicarAbono(periodo, ordenes.abono, tasa);
            BigDecimal prepagoCapital = ordenes.prepago ? prepagar() : CERO;

            // 5. Cierre por recuperación de la moto: se extingue capital + lo pendiente.
            Cierre cierre = ordenes.cierre == null ? Cierre.NINGUNO : cerrar(ordenes.cierre);

            // 6. Estado, cuotas vencidas y giro a terceros.
            BigDecimal pendiente = sumar(capitalVencido, cxcCobranza, cxcMora, cxcInteres, cxcServicios);
            EstadoCredito estado = estado(ordenes.cierre != null, pendiente);
            contarCuotasVencidas(pagoCompleto, pago, pendiente);
            Servicios giro = girar(periodo, causado.servicios(), estado);

            String evento = evento(ordenes, periodo, pagoCompleto, dias, recibido);
            return new Movimiento(periodo, Calendario.mes(periodo, p), evento, saldoInicial, tasa, cuotaDeLaSemana,
                    causado.interes(), causado.mora(), causado.capitalContractual(), causado.cobranza(), causado.servicios(),
                    recibido, reparto.cobranza(), reparto.mora(), reparto.interes(), reparto.servicios(), reparto.capital(),
                    abonoAplicado, prepagoCapital, cxcCobranza, cxcMora, cxcInteres, cxcServicios, capitalVencido,
                    max(CERO, restar(saldo, capitalVencido)), saldo, cuotasVencidas, cierre.deuda(), cierre.inventario(),
                    cierre.cxcFondoGarantias(), cierre.cxpDeudor(), giro, estado);
        }

        /** 1. Lee las instrucciones de la semana; el cambio de tasa se aplica al leerlo (caso 2). */
        private Ordenes leer(int periodo, List<Instruccion> instrucciones) {
            Ordenes ordenes = new Ordenes();
            for (Instruccion i : instrucciones) {
                switch (i) {
                    case CambioTasa(BigDecimal nuevaTasaEA) -> {
                        // Nueva cuota sobre el saldo, conservando la fecha final.
                        tasas = Tasas.desdeEA(nuevaTasaEA, p.semanasPorAnio(), p.mesesPorAnio());
                        cuota = Amortizacion.cuotaFija(tasas.semanal(), cuotasPactadas - periodo + 1, saldo);
                        saldoPlan = restar(saldo, capitalVencido);
                        ordenes.cambioTasa = true;
                    }
                    case AbonoExtra a -> ordenes.abono = a;
                    case PrepagoTotal t -> ordenes.prepago = true;
                    case CierrePorRecuperacion c -> ordenes.cierre = c;
                    default -> ordenes.pago = i;
                }
            }
            return ordenes;
        }

        /**
         * 2. Interés corriente, capital contractual, servicios, mora y cobranza de la semana. Las
         * variantes de ReglasNegocio son las opciones que se le preguntan a negocio; EXCEL reproduce
         * el Excel tal cual.
         */
        private Causacion causar(int periodo, BigDecimal saldoInicial, boolean sinPagoSinMora, int dias) {
            ReglasNegocio reglas = escenario.reglas();
            BigDecimal tasa = tasas.semanal();
            boolean dentroDelPlazo = periodo <= cuotasPactadas;
            BigDecimal baseInteres = reglas.baseInteres() == ReglasNegocio.BaseInteres.SALDO_VIGENTE
                    ? noNegativo(restar(saldoInicial, capitalVencido)) : saldoInicial;
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
            return new Causacion(multiplicar(baseInteres, tasa), capitalContractual, servicios, mora(capitalContractual, dias),
                    cobranza(saldoInicial, dias));
        }

        private BigDecimal mora(BigDecimal capitalContractual, int dias) {
            BigDecimal mora = multiplicar(capitalVencido, tasas.semanal());
            if (dias > 0 && escenario.reglas().formulaMora() == ReglasNegocio.FormulaMora.VENCIDO_MAS_DIAS) {
                BigDecimal factorDias = restar(Calc.potencia(sumar(Calc.UNO, tasas.diaria()), dias), Calc.UNO);
                mora = sumar(mora, multiplicar(capitalContractual, factorDias));
            }
            return mora;
        }

        private BigDecimal cobranza(BigDecimal saldoInicial, int dias) {
            BigDecimal cobranza = (dias > 0 && !esCero(saldoInicial)) ? multiplicar(Calc.bd(dias), p.cobranzaDiaria()) : CERO;
            BigDecimal tope = escenario.reglas().topeCobranzaPorPago();
            return tope == null ? cobranza : min(cobranza, tope);
        }

        /** 3. Lo que entra esta semana, limitado a lo exigible. */
        private BigDecimal recibido(Instruccion pago, BigDecimal cuotaTotal, BigDecimal saldoInicial, Causacion c) {
            BigDecimal exigible = sumar(saldoInicial, cxcCobranza, c.cobranza(), cxcMora, c.mora(), cxcInteres, c.interes(),
                    cxcServicios, c.servicios().total());
            return switch (pago) {
                case PagoContractual x -> min(cuotaTotal, exigible);
                case PagoParcial x -> min(multiplicar(x.fraccion(), cuotaTotal), exigible);
                case PagoValor x -> min(x.valor(), exigible);
                case SinPago x -> CERO;
                default -> throw new IllegalStateException("Instrucción de pago no soportada: " + pago);
            };
        }

        /** 3. Prelación: cobranza → mora → interés corriente → servicios y capital en el orden de ReglasNegocio. */
        private Reparto repartir(BigDecimal recibido, BigDecimal saldoInicial, Causacion c) {
            BigDecimal restante = recibido;
            BigDecimal pagoCobranza = min(restante, sumar(cxcCobranza, c.cobranza()));
            restante = restar(restante, pagoCobranza);
            BigDecimal pagoMora = min(restante, sumar(cxcMora, c.mora()));
            restante = restar(restante, pagoMora);
            BigDecimal pagoInteres = min(restante, sumar(cxcInteres, c.interes()));
            restante = restar(restante, pagoInteres);
            BigDecimal servicios = sumar(cxcServicios, c.servicios().total());
            BigDecimal pagoServicios;
            BigDecimal pagoCapital;
            if (escenario.reglas().prelacion() == ReglasNegocio.OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS) {
                // Solo se adelanta el capital de la cuota (contractual + vencido); el excedente pasa a servicios.
                pagoCapital = min(restante, min(saldoInicial, sumar(capitalVencido, c.capitalContractual())));
                restante = restar(restante, pagoCapital);
                pagoServicios = min(restante, servicios);
                restante = restar(restante, pagoServicios);
                pagoCapital = sumar(pagoCapital, min(restante, restar(saldoInicial, pagoCapital)));
            } else {
                pagoServicios = min(restante, servicios);
                restante = restar(restante, pagoServicios);
                pagoCapital = min(restante, saldoInicial);
            }
            return new Reparto(pagoCobranza, pagoMora, pagoInteres, pagoServicios, pagoCapital);
        }

        private void actualizarSaldos(BigDecimal saldoInicial, Causacion c, Reparto r, boolean sinPagoSinMora) {
            cxcCobranza = noNegativo(restar(sumar(cxcCobranza, c.cobranza()), r.cobranza()));
            cxcMora = noNegativo(restar(sumar(cxcMora, c.mora()), r.mora()));
            cxcInteres = noNegativo(restar(sumar(cxcInteres, c.interes()), r.interes()));
            cxcServicios = noNegativo(restar(sumar(cxcServicios, c.servicios().total()), r.servicios()));
            saldo = noNegativo(restar(saldoInicial, r.capital()));
            if (!sinPagoSinMora) {
                capitalVencido = noNegativo(restar(sumar(capitalVencido, c.capitalContractual()), r.capital()));
            }
            saldoPlan = noNegativo(restar(saldoPlan, c.capitalContractual()));
        }

        /** 4. Abono extraordinario 100 % a capital; devuelve lo aplicado. */
        private BigDecimal aplicarAbono(int periodo, AbonoExtra abono, BigDecimal tasa) {
            // P-14: Diego (2026-10-16) no acepta abonos mayores que el saldo; el Excel los recortaba.
            if (abono.valor().compareTo(sumar(saldo, Calc.EPSILON_SALDO)) > 0) {
                throw new AbonoMayorQueElSaldoException(periodo, abono.valor(), saldo);
            }
            BigDecimal aplicado = min(abono.valor(), saldo);
            saldo = restar(saldo, aplicado);
            if (abono.modalidad() == ModalidadAbono.MENOR_CUOTA && cuotasPactadas - periodo > 0 && !esCero(saldo)) {
                cuota = Amortizacion.cuotaFija(tasa, cuotasPactadas - periodo, saldo);
            }
            saldoPlan = restar(saldo, capitalVencido);
            return aplicado;
        }

        /** 4. Prepago total; devuelve el capital prepagado. */
        private BigDecimal prepagar() {
            BigDecimal prepagado = saldo;
            saldo = CERO;
            saldoPlan = CERO;
            return prepagado;
        }

        /** 5. Recibe la moto y extingue capital + lo pendiente contra su valor. */
        private Cierre cerrar(CierrePorRecuperacion orden) {
            BigDecimal deuda = sumar(saldo, cxcInteres, cxcMora, cxcServicios, cxcCobranza);
            BigDecimal inventario = orden.valorMoto();
            Cierre cierre = new Cierre(deuda, inventario, noNegativo(restar(deuda, inventario)),
                    noNegativo(restar(inventario, deuda)));
            saldo = CERO;
            saldoPlan = CERO;
            capitalVencido = CERO;
            cxcInteres = CERO;
            cxcMora = CERO;
            cxcServicios = CERO;
            cxcCobranza = CERO;
            return cierre;
        }

        private EstadoCredito estado(boolean cerrado, BigDecimal pendiente) {
            if (cerrado) {
                return EstadoCredito.CERRADO_POR_EVENTO;
            }
            if (esCero(sumar(saldo, pendiente))) {
                return EstadoCredito.CANCELADO;
            }
            return esCero(pendiente) ? EstadoCredito.VIGENTE_AL_DIA : EstadoCredito.EN_MORA;
        }

        private void contarCuotasVencidas(boolean pagoCompleto, Instruccion pago, BigDecimal pendiente) {
            if (!pagoCompleto && !(pago instanceof PagoContractual)) {
                cuotasVencidas++;
            } else if (esCero(pendiente)) {
                cuotasVencidas = 0;
            }
        }

        /** 6. Los servicios se giran a terceros al cerrar el mes, en la última cuota o al terminar el crédito. */
        private Servicios girar(int periodo, Servicios causados, EstadoCredito estado) {
            serviciosDelMes = serviciosDelMes.mas(causados);
            boolean terminaMes = Calendario.mes(periodo + 1, p) > Calendario.mes(periodo, p);
            boolean termina = estado == EstadoCredito.CANCELADO || estado == EstadoCredito.CERRADO_POR_EVENTO;
            if (periodo != cuotasPactadas && !terminaMes && !termina) {
                return Servicios.NINGUNO;
            }
            Servicios giro = serviciosDelMes;
            serviciosDelMes = Servicios.NINGUNO;
            return giro;
        }

        /** El evento de mayor jerarquía de la semana: cierre, prepago, abono, cambio de tasa o el pago. */
        private String evento(Ordenes o, int periodo, boolean pagoCompleto, int dias, BigDecimal recibido) {
            if (o.cierre != null) {
                return "Evento de cierre";
            }
            if (o.prepago) {
                return "Cuota + prepago total";
            }
            if (o.abono != null) {
                return "Cuota + abono extra";
            }
            return o.cambioTasa ? "Cambio de tasa" : describir(periodo, o.pago, pagoCompleto, dias, recibido);
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

        /** Lo que piden las instrucciones de una semana. */
        private static final class Ordenes {
            private Instruccion pago = PagoContractual.A_TIEMPO;
            private AbonoExtra abono;
            private boolean prepago;
            private boolean cambioTasa;
            private CierrePorRecuperacion cierre;
        }

        /** Lo causado en una semana. */
        private record Causacion(BigDecimal interes, BigDecimal capitalContractual, Servicios servicios, BigDecimal mora,
                BigDecimal cobranza) {
        }

        /** Cómo se repartió el pago por prelación. */
        private record Reparto(BigDecimal cobranza, BigDecimal mora, BigDecimal interes, BigDecimal servicios,
                BigDecimal capital) {
        }

        /** Resultado de un cierre por recuperación de la moto. */
        private record Cierre(BigDecimal deuda, BigDecimal inventario, BigDecimal cxcFondoGarantias, BigDecimal cxpDeudor) {
            private static final Cierre NINGUNO = new Cierre(CERO, CERO, CERO, CERO);
        }
    }
}
