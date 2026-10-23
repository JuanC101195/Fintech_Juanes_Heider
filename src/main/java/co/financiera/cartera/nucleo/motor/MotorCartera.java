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
import co.financiera.cartera.nucleo.Periodicidad;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.Tasas;
import co.financiera.cartera.nucleo.Usura;
import co.financiera.cartera.nucleo.motor.Instruccion.AbonoExtra;
import co.financiera.cartera.nucleo.motor.Instruccion.CambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.CierrePorRecuperacion;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadAbono;
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadCambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.UsuraVigente;
import co.financiera.cartera.nucleo.motor.ReglasNegocio.AplicacionAbono;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor de reglas de cartera: recorre el crédito periodo a periodo y aplica las reglas de los 12
 * casos del Excel con un solo ciclo.
 *
 * <p>En cada periodo: (1) eventos de tasa, (2) causación de interés corriente, mora, cobranza,
 * capital contractual y servicios, (3) pago aplicado por prelación (Inputs ⑥): cobranza → mora →
 * interés corriente → fondo de garantías → asistencia → seguro → capital, (4) abonos
 * extraordinarios o prepago, (5) cierre por recuperación de la moto con la misma prelación, (6)
 * estado y giro a terceros de lo recaudado.
 */
public final class MotorCartera {

    public List<Movimiento> ejecutar(Escenario escenario) {
        return new Corrida(escenario).correr();
    }

    /** Estado mutable de una simulación. */
    private static final class Corrida {
        private final Escenario escenario;
        private final ParametrosProducto p;
        private final Servicios serviciosPorCuota;
        private final List<Movimiento> filas = new ArrayList<>();
        /** Saldos del crédito, compartidos con las piezas extraídas del motor (contexto explícito). */
        private final EstadoCorrida s;

        private int cuotasVencidas;
        private Servicios recaudadoDelMes = Servicios.NINGUNO;
        /** Usura publicada la semana anterior: rige desde esta cuota (T1, Diego 2026-10-20). */
        private BigDecimal usuraPorAplicar;
        /**
         * R6-RECUP-AUTO (Diego): una vez el crédito llega al umbral de recuperación queda marcado en los periodos
         * siguientes aunque el cliente se ponga al día; el motor no ejecuta nada, una persona decide.
         */
        private boolean marcadoParaRecuperacion;

        Corrida(Escenario escenario) {
            this.escenario = escenario;
            this.p = escenario.parametros();
            this.s = new EstadoCorrida(escenario, Calendario.numeroCuotas(p));
            this.serviciosPorCuota = s.serviciosPorCuota;
        }

        List<Movimiento> correr() {
            filas.add(desembolso());
            for (int periodo = 1; periodo <= p.plazoLimitePeriodos(); periodo++) {
                Movimiento fila = procesarPeriodo(periodo, escenario.instruccionesDe(periodo));
                filas.add(fila);
                if (fila.estado() == EstadoCredito.CANCELADO || fila.estado() == EstadoCredito.CERRADO_POR_EVENTO) {
                    break;
                }
            }
            return List.copyOf(filas);
        }

        private Movimiento desembolso() {
            return new Movimiento(0, 0, "Desembolso", s.saldo, CERO, CERO, CERO, CERO, CERO, CERO, Servicios.NINGUNO, CERO, CERO,
                    CERO, CERO, CERO, CERO, CERO, CERO, CERO, CERO, CERO, CERO, CERO, s.saldo, s.saldo, 0, CERO, CERO, CERO, CERO,
                    Servicios.NINGUNO, EstadoCredito.DESEMBOLSADO, Servicios.NINGUNO, Servicios.NINGUNO, DeudaExtinguida.NINGUNA,
                    CERO, DeudaExtinguida.NINGUNA, CERO, CERO, CERO, false);
        }

        // ADR 0002 paso 4: antes "semana"; el ciclo es por periodo de la periodicidad pactada.
        private Movimiento procesarPeriodo(int periodo, List<Instruccion> instrucciones) {
            Ordenes ordenes = leer(periodo, instrucciones);
            Instruccion pago = ordenes.pago;
            BigDecimal tasa = s.tasas.periodica();
            BigDecimal saldoInicial = s.saldo;
            BigDecimal vencidoInicial = s.capitalVencido;
            // P7 / P-16: con moraAntesDeRecuperacion (el Excel) toda semana sin pago causa mora y seguro.
            boolean sinPagoSinMora = pago instanceof SinPago(boolean causaMora) && !causaMora
                    && !escenario.reglas().moraAntesDeRecuperacion();
            int dias = diasAtraso(pago);

            // 2. Causación del periodo.
            Causacion causado = causar(periodo, saldoInicial, sinPagoSinMora, dias, alDia(pago, dias));

            // La fila reporta la cuota que se cobró este periodo: un abono a menor cuota (caso 7) cambia
            // la cuota solo desde el periodo siguiente (Caso7 F46 = MIN(B24, D + E); F47 = PMT(...)). Con el
            // prorrateo de la primera cuota (P5-PRORRATEO) es su capital completo más el interés de los días reales.
            BigDecimal cuotaDelPeriodo = causado.prorrateada() ? sumar(causado.capitalContractual(), causado.interes()) : s.cuota;

            // 3. Pago y prelación.
            BigDecimal cuotaTotal = sumar(cuotaDelPeriodo, causado.servicios().total());
            BigDecimal recibido = recibido(pago, cuotaTotal, cuotaDelPeriodo, saldoInicial, causado);
            Reparto reparto = repartir(recibido, saldoInicial, causado);
            actualizarSaldos(saldoInicial, causado, reparto, sinPagoSinMora);
            boolean pagoCompleto = recibido.compareTo(restar(cuotaTotal, Calc.EPSILON_SALDO)) >= 0;

            // 4. Abono extraordinario o prepago, después de la cuota del periodo. Con PRIMERO_PENDIENTES (A4)
            // pagan antes lo pendiente de semanas anteriores; nunca adelantan intereses ni servicios futuros.
            s.pendientesPagadosExtra = DeudaExtinguida.NINGUNA;
            s.interesPrepago = CERO;
            s.serviciosCorrientesExtra = Servicios.NINGUNO;
            BigDecimal abonoAplicado = ordenes.abono == null ? CERO : aplicarAbono(periodo, ordenes.abono, tasa, causado);
            BigDecimal prepagoCapital = ordenes.prepago == null ? CERO : prepagar(periodo, ordenes.prepago);

            // 5. Cierre por recuperación de la moto: su valor extingue lo pendiente por prelación. La fila
            // del cierre reporta el capital vencido que se extinguió (Caso3 Z del evento).
            BigDecimal vencidoReportado = s.capitalVencido;
            Cierre cierre = ordenes.cierre == null ? Cierre.NINGUNO : cerrar(ordenes.cierre);

            // 6. Estado, cuotas vencidas y giro a terceros de lo recaudado.
            BigDecimal pendiente = sumar(s.capitalVencido, s.cxcCobranza, s.cxcMora, s.cxcInteres, s.cxcServicios.total());
            EstadoCredito estado = estado(ordenes.cierre != null, pendiente);
            contarCuotasVencidas(pagoCompleto, pago, pendiente);
            // A4: un abono o prepago que pagó todo lo pendiente deja al cliente al día.
            if (s.pendientesPagadosExtra.total().signum() > 0 && esCero(pendiente)) {
                cuotasVencidas = 0;
            }
            Servicios recaudado = reparto.servicios().mas(cierre.servicios());
            Servicios giro = girar(periodo, recaudado.mas(s.pendientesPagadosExtra.servicios()), estado);

            String evento = DescriptorEventos.evento(ordenes, periodo, s.cuotasVigentes, pagoCompleto, dias, recibido);
            // R4-CASO13: los servicios del periodo que pagó el excedente del abono también quedan causados.
            Servicios serviciosDelPeriodo = causado.servicios().mas(s.serviciosCorrientesExtra);
            // PPER-9 (Diego 2026-10-22): con 2 cuotas atrasadas el crédito queda listo para recuperación. R6-RECUP-AUTO
            // (Diego): el motor solo marca, nunca ejecuta la dación ni la retoma, y la marca persiste aunque el
            // cliente se ponga al día: una persona decide. Solo se apaga cuando el crédito termina (cancelado o
            // cerrado por evento), porque ya no hay nada que recuperar.
            if (estado == EstadoCredito.EN_MORA && escenario.reglas().llegaAlUmbralDeRecuperacion(cuotasVencidas)) {
                marcadoParaRecuperacion = true;
            }
            boolean termino = estado == EstadoCredito.CANCELADO || estado == EstadoCredito.CERRADO_POR_EVENTO;
            boolean listoParaRecuperacion = marcadoParaRecuperacion && !termino;
            return new Movimiento(periodo, Calendario.mes(periodo, p), evento, saldoInicial, tasa, cuotaDelPeriodo,
                    causado.interes(), causado.mora(), causado.capitalContractual(), causado.cobranza(), serviciosDelPeriodo,
                    recibido, reparto.cobranza(), reparto.mora(), reparto.interes(), reparto.servicios().total(),
                    reparto.capital(), abonoAplicado, prepagoCapital, s.cxcCobranza, s.cxcMora, s.cxcInteres, s.cxcServicios.total(),
                    vencidoReportado, max(CERO, restar(s.saldo, s.capitalVencido)), s.saldo, cuotasVencidas, cierre.deuda(),
                    cierre.inventario(), cierre.cxcFondoGarantias(), cierre.cxpDeudor(), giro, estado, recaudado,
                    s.cxcServicios, cierre.extinguido(), vencidoInicial, s.pendientesPagadosExtra, s.interesPrepago,
                    cierre.costo(), cierre.costoAsumido(), listoParaRecuperacion);
        }

        /** 1. Lee las instrucciones del periodo; el cambio de tasa se aplica al leerlo (casos 2 y 10). */
        private Ordenes leer(int periodo, List<Instruccion> instrucciones) {
            Ordenes ordenes = new Ordenes();
            // T1/T2/T4 (Diego 2026-10-20): la usura publicada la semana anterior rige desde esta cuota.
            if (usuraPorAplicar != null) {
                ordenes.cambioTasa = ajustarPorUsura(periodo, usuraPorAplicar);
                usuraPorAplicar = null;
            }
            boolean pagoExplicito = false;
            boolean usuraLeida = false;
            for (Instruccion i : instrucciones) {
                switch (i) {
                    case CambioTasa(BigDecimal nuevaTasaEA, ModalidadCambioTasa modalidad) -> {
                        cambiarTasa(periodo, nuevaTasaEA, modalidad);
                        ordenes.cambioTasa = true;
                    }
                    case UsuraVigente(BigDecimal usuraEA) -> {
                        unaSola(periodo, usuraLeida, "publicación de usura");
                        usuraLeida = true;
                        usuraPorAplicar = usuraEA;
                    }
                    case AbonoExtra a -> {
                        unaSola(periodo, ordenes.abono != null, "abono extraordinario");
                        ordenes.abono = a;
                    }
                    case PrepagoTotal t -> {
                        unaSola(periodo, ordenes.prepago != null, "orden de prepago total");
                        ordenes.prepago = t;
                    }
                    case CierrePorRecuperacion c -> {
                        unaSola(periodo, ordenes.cierre != null, "orden de cierre por recuperación");
                        ordenes.cierre = c;
                    }
                    default -> {
                        unaSola(periodo, pagoExplicito, "instrucción de pago");
                        ordenes.pago = i;
                        pagoExplicito = true;
                    }
                }
            }
            // REC-8 (Diego, ronda 3, 2026-10-20): la semana de la dación o la retoma cuenta como vencida. Si el
            // escenario no dice cómo paga esa semana, no se supone un pago a tiempo: se trata como sin pago, y
            // moraAntesDeRecuperacion decide si causa mora y servicios (los casos 3, 4 y 11 ya lo hacían explícito).
            if (ordenes.cierre != null && !pagoExplicito) {
                ordenes.pago = new SinPago(false);
            }
            return ordenes;
        }

        /**
         * Auditoría 2026-10-23 (H5): antes la segunda instrucción del mismo tipo en un periodo reemplazaba a la
         * primera y el dinero de la primera se perdía sin aviso. Decisión conservadora: no se suman ni se elige
         * una; se rechaza el escenario y quien lo arma decide.
         */
        private static void unaSola(int periodo, boolean yaHabia, String que) {
            if (yaHabia) {
                throw new IllegalArgumentException("El periodo " + periodo + " tiene más de una " + que
                        + "; el motor acepta una sola por periodo (no las suma). Unifíquelas en una instrucción.");
            }
        }

        /**
         * Ajuste por usura como evento de tasa: la EA vigente baja a usura − margen solo si la supera
         * ({@link Usura#tasaAjustada(BigDecimal, BigDecimal, BigDecimal)}). Lo que se conserva lo decide
         * {@code ReglasNegocio.modalidadUsura}: con MISMA_CUOTA (Diego, USU-6, ronda 3) la cuota se mantiene y
         * baja el plazo como en el caso 10; con MISMA_FECHA_FINAL (EXCEL) la cuota se recalcula como en el
         * caso 2. Devuelve si la tasa cambió.
         */
        private boolean ajustarPorUsura(int periodo, BigDecimal usuraEA) {
            BigDecimal actual = s.tasas.efectivaAnual();
            BigDecimal nueva = Usura.tasaAjustada(actual, usuraEA, escenario.reglas().margenUsura());
            if (nueva.compareTo(actual) == 0) {
                return false;
            }
            cambiarTasa(periodo, nueva, escenario.reglas().modalidadUsura());
            return true;
        }

        /**
         * Caso 2: conserva la fecha final y recalcula la cuota. Caso 10: conserva la cuota y recalcula
         * el número de cuotas hasta saldo cero, NPER = −ln(1 − r·S/C) / ln(1 + r); la última se ajusta.
         */
        private void cambiarTasa(int periodo, BigDecimal nuevaTasaEA, ModalidadCambioTasa modalidad) {
            s.tasas = Tasas.desdeEA(nuevaTasaEA, p.periodosPorAnio(), p.mesesPorAnio());
            s.saldoPlan = restar(s.saldo, s.capitalVencido);
            if (periodo > s.cuotasVigentes) {
                // Auditoría 2026-10-23 (H6): después de la última cuota pactada no quedan cuotas por recalcular
                // (PMT con 0 o menos periodos lanzaba excepción y tumbaba, p. ej., un lote de usura con créditos en
                // mora fuera de plazo). Decisión conservadora, pendiente de Diego: la tasa nueva queda registrada
                // (rige el interés y la mora desde este periodo), pero ni la cuota ni el plazo cambian: lo que
                // queda es capital vencido y se sigue cobrando por prelación.
                return;
            }
            if (modalidad == ModalidadCambioTasa.MISMA_FECHA_FINAL) {
                // R4-T5 (Diego 2026-10-22): con el cliente en mora la cuota nueva va solo sobre el capital vigente;
                // el vencido se sigue cobrando aparte (prelación) y los intereses pendientes nunca entran a la base
                // (no hay anatocismo). Sin vencido las dos bases son iguales, como en el Excel.
                BigDecimal base = escenario.reglas().cambioTasaSobreCapitalVigente() && !esCero(s.capitalVencido)
                        ? s.saldoPlan : s.saldo;
                s.cuota = Amortizacion.cuotaFija(s.tasas.periodica(), s.cuotasVigentes - periodo + 1, base);
                return;
            }
            s.cuotasVigentes = periodo - 1 + s.cuotasHastaSaldoCero(s.saldoPlan);
        }

        /** FCC y asistencia solo se causan en periodos pagados al día (Inputs ⑥, P-07). */
        private boolean alDia(Instruccion pago, int dias) {
            return (pago instanceof PagoContractual || pago instanceof PagoAlDia || pago instanceof PagoValor) && dias == 0;
        }

        /**
         * 2. Interés corriente, capital contractual, servicios, mora y cobranza del periodo. Las
         * variantes de ReglasNegocio son las opciones que se le preguntan a negocio; EXCEL reproduce
         * el Excel tal cual.
         */
        private Causacion causar(int periodo, BigDecimal saldoInicial, boolean sinPagoSinMora, int dias, boolean alDia) {
            ReglasNegocio reglas = escenario.reglas();
            BigDecimal tasa = s.tasas.periodica();
            boolean dentroDelPlazo = periodo <= s.cuotasVigentes;
            BigDecimal baseInteres = reglas.baseInteres() == ReglasNegocio.BaseInteres.SALDO_VIGENTE
                    ? noNegativo(restar(saldoInicial, s.capitalVencido)) : saldoInicial;
            BigDecimal capitalContractual = CERO;
            if (dentroDelPlazo && !sinPagoSinMora && !esCero(s.saldoPlan)) {
                // Auditoría 2026-10-23 (H3): nunca negativo, aunque el saldo del plan lo fuera por un error aguas arriba.
                capitalContractual = noNegativo(min(s.saldoPlan, restar(s.cuota, multiplicar(s.saldoPlan, tasa))));
                // Auditoría 2026-10-23 (I8): el plan puede ir por encima del capital que de verdad queda vigente (un pago
                // mayor que la cuota baja el saldo pero no el plan). Sin este tope la cuota del plan vencía más capital
                // del que existe y el capital vencido superaba al saldo. Solo actúa si la diferencia supera el medio
                // centavo, así los residuos DECIMAL128 de los casos del Excel no cambian.
                BigDecimal vigente = noNegativo(restar(saldoInicial, s.capitalVencido));
                if (restar(capitalContractual, vigente).compareTo(Calc.EPSILON_SALDO) > 0) {
                    capitalContractual = vigente;
                }
            }
            boolean causaServicios = !sinPagoSinMora
                    && (dentroDelPlazo || (reglas.serviciosDespuesDelPlazo() && !esCero(saldoInicial)));
            Servicios servicios = Servicios.NINGUNO;
            Servicios serviciosAlDia = Servicios.NINGUNO;
            if (causaServicios) {
                Servicios base = reglas.baseServicios() == ReglasNegocio.BaseServicios.SALDO
                        ? Servicios.porCuota(saldoInicial, p) : serviciosPorCuota;
                servicios = alDia ? base : base.soloSeguro();
                serviciosAlDia = base;
            }
            // P5-PRORRATEO (Diego, ronda 5; ADR 0002 paso 7): en la primera cuota solo el interés se prorratea por
            // los días reales entre el desembolso y la primera fecha de pago, con la tasa diaria como el prepago por
            // días (A6); capital contractual y servicios van completos. Con 365/ppa días daría exactamente la tasa
            // periódica. Sin fechas en el escenario, o con el Excel, no cambia nada.
            boolean prorrateada = periodo == 1 && reglas.prorrateoPrimeraCuota() && escenario.diasPrimerPeriodo().isPresent();
            BigDecimal interes = multiplicar(baseInteres, tasa);
            if (prorrateada) {
                int diasReales = escenario.diasPrimerPeriodo().getAsInt();
                interes = multiplicar(baseInteres, restar(Calc.potencia(sumar(Calc.UNO, s.tasas.diaria()), diasReales), Calc.UNO));
            }
            return new Causacion(interes, capitalContractual, servicios, mora(periodo, capitalContractual, dias),
                    cobranza(saldoInicial, dias), serviciosAlDia, prorrateada);
        }

        private BigDecimal mora(int periodo, BigDecimal capitalContractual, int dias) {
            BigDecimal mora = multiplicar(s.capitalVencido, factorMoraDelPeriodo(periodo));
            if (dias > 0 && escenario.reglas().formulaMora() == ReglasNegocio.FormulaMora.VENCIDO_MAS_DIAS) {
                BigDecimal factorDias = restar(Calc.potencia(sumar(Calc.UNO, s.tasas.diaria()), dias), Calc.UNO);
                mora = sumar(mora, multiplicar(capitalContractual, factorDias));
            }
            return mora;
        }

        /**
         * Factor de mora del capital vencido en el periodo. Con el Excel (PERIODO_NOMINAL) es la tasa periódica:
         * el periodo dura 365/ppa días. PPER-5 / D27 (Diego, ADR 0002 paso 8): en mensual la mora cuenta los días
         * reales del mes; con DIAS_CALENDARIO_MENSUAL y fechas en el escenario el factor es (1 + diaria)^días − 1 con
         * los días del vencimiento anterior al de esta cuota (28 a 31). Quincenal y semanal siguen nominales: Diego
         * solo decidió la mensual.
         */
        private BigDecimal factorMoraDelPeriodo(int periodo) {
            if (escenario.reglas().diasMora() == ReglasNegocio.DiasMora.DIAS_CALENDARIO_MENSUAL
                    && p.periodicidad() == Periodicidad.MENSUAL) {
                java.util.OptionalInt diasReales = escenario.diasDelPeriodo(periodo);
                if (diasReales.isPresent()) {
                    return restar(Calc.potencia(sumar(Calc.UNO, s.tasas.diaria()), diasReales.getAsInt()), Calc.UNO);
                }
            }
            return s.tasas.periodica();
        }

        /**
         * Gastos de cobranza = días de atraso × valor diario del producto. COB-4 (Diego 2026-10-22): solo se
         * causan cuando el pago llega en mora (días de atraso &gt; 0); al día, cancelado o prepagado no se liquidan.
         * COB-6: solo si la cobranza está activa en el crédito ({@link Escenario#cobranzaActiva()}).
         */
        private BigDecimal cobranza(BigDecimal saldoInicial, int dias) {
            if (!escenario.cobranzaActiva()) {
                return CERO;
            }
            BigDecimal cobranza = (dias > 0 && !esCero(saldoInicial)) ? multiplicar(Calc.bd(dias), p.cobranzaDiaria()) : CERO;
            BigDecimal tope = escenario.reglas().topeCobranzaPorPago();
            return tope == null ? cobranza : min(cobranza, tope);
        }

        /** 3. Lo que entra este periodo, limitado a lo exigible. */
        private BigDecimal recibido(Instruccion pago, BigDecimal cuotaTotal, BigDecimal cuotaDelPeriodo,
                BigDecimal saldoInicial, Causacion c) {
            BigDecimal pendienteSinCapital = sumar(s.cxcCobranza, c.cobranza(), s.cxcMora, c.mora(), s.cxcInteres, c.interes(),
                    s.cxcServicios.total(), c.servicios().total());
            BigDecimal exigible = sumar(saldoInicial, pendienteSinCapital);
            return switch (pago) {
                case PagoContractual x -> min(cuotaTotal, exigible);
                // El pago parcial es una fracción de la cuota total pactada (Inputs B36: cuota + los tres servicios);
                // en la primera cuota prorrateada (P5-PRORRATEO), de la cuota de ese periodo.
                case PagoParcial x -> min(multiplicar(x.fraccion(), sumar(cuotaDelPeriodo, serviciosPorCuota.total())), exigible);
                case PagoValor x -> min(x.valor(), exigible);
                case PagoAlDia x -> min(sumar(pendienteSinCapital, s.capitalVencido, c.capitalContractual()), exigible);
                case SinPago x -> CERO;
                default -> throw new IllegalStateException("Instrucción de pago no soportada: " + pago);
            };
        }

        /**
         * 3. Prelación (Inputs ⑥): cobranza → mora → interés corriente → fondo de garantías →
         * asistencia → seguro → capital. Con CAPITAL_ANTES_DE_SERVICIOS el capital de la cuota va antes
         * de los servicios.
         */
        private Reparto repartir(BigDecimal recibido, BigDecimal saldoInicial, Causacion c) {
            BigDecimal restante = recibido;
            BigDecimal pagoCobranza = min(restante, sumar(s.cxcCobranza, c.cobranza()));
            restante = restar(restante, pagoCobranza);
            BigDecimal pagoMora = min(restante, sumar(s.cxcMora, c.mora()));
            restante = restar(restante, pagoMora);
            BigDecimal pagoInteres = min(restante, sumar(s.cxcInteres, c.interes()));
            restante = restar(restante, pagoInteres);
            Servicios porCobrar = s.cxcServicios.mas(c.servicios());
            BigDecimal pagoCapital = CERO;
            if (escenario.reglas().prelacion() == ReglasNegocio.OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS) {
                // Solo se adelanta el capital de la cuota (contractual + vencido); el excedente pasa a servicios.
                pagoCapital = min(restante, min(saldoInicial, sumar(s.capitalVencido, c.capitalContractual())));
                restante = restar(restante, pagoCapital);
            }
            BigDecimal pagoFondo = min(restante, porCobrar.fondoGarantias());
            restante = restar(restante, pagoFondo);
            BigDecimal pagoAsistencia = min(restante, porCobrar.asistencia());
            restante = restar(restante, pagoAsistencia);
            BigDecimal pagoSeguro = min(restante, porCobrar.seguro());
            restante = restar(restante, pagoSeguro);
            pagoCapital = sumar(pagoCapital, min(restante, restar(saldoInicial, pagoCapital)));
            return new Reparto(pagoCobranza, pagoMora, pagoInteres, new Servicios(pagoFondo, pagoAsistencia, pagoSeguro),
                    pagoCapital);
        }

        private void actualizarSaldos(BigDecimal saldoInicial, Causacion c, Reparto r, boolean sinPagoSinMora) {
            s.cxcCobranza = noNegativo(restar(sumar(s.cxcCobranza, c.cobranza()), r.cobranza()));
            s.cxcMora = noNegativo(restar(sumar(s.cxcMora, c.mora()), r.mora()));
            s.cxcInteres = noNegativo(restar(sumar(s.cxcInteres, c.interes()), r.interes()));
            s.cxcServicios = s.cxcServicios.mas(c.servicios()).menos(r.servicios()).noNegativo();
            s.saldo = noNegativo(restar(saldoInicial, r.capital()));
            if (!sinPagoSinMora) {
                s.capitalVencido = noNegativo(restar(sumar(s.capitalVencido, c.capitalContractual()), r.capital()));
                if (esCero(s.capitalVencido)) {
                    s.capitalVencido = CERO;
                }
            }
            s.saldoPlan = noNegativo(restar(s.saldoPlan, c.capitalContractual()));
        }

        /**
         * 4. Abono extraordinario; devuelve lo aplicado a capital vigente. Con SOLO_CAPITAL (Excel) va
         * 100 % a capital. Con PRIMERO_PENDIENTES (A4, Diego 2026-10-20) paga antes los pendientes de
         * semanas anteriores por prelación y el resto va a capital: como la cuota del periodo ya se
         * aplicó, solo quedan CxC ya causadas y nunca se adelantan intereses ni servicios futuros.
         */
        private BigDecimal aplicarAbono(int periodo, AbonoExtra abono, BigDecimal tasaVigente, Causacion causado) {
            AplicacionAbono aplicacion = escenario.reglas().aplicacionAbono();
            boolean primeroPendientes = aplicacion.pagaPendientes();
            // R4-CASO13 (Diego, ronda 4): con PRELACION_COMPLETA el excedente vuelve a pasar por la prelación
            // sobre lo causado del periodo corriente; solo faltan su fondo y su asistencia si no se pagó al día.
            Servicios corrientePorPagar = aplicacion == AplicacionAbono.PRELACION_COMPLETA
                    ? causado.serviciosNoCausados() : Servicios.NINGUNO;
            // P-14: Diego (2026-10-16) no acepta abonos mayores que el saldo; el Excel los recortaba. Con
            // pendientes, el saldo del crédito incluye lo que se debe de semanas anteriores.
            BigDecimal maximo = primeroPendientes ? sumar(s.saldo, s.pendientesSinCapital(), corrientePorPagar.total()) : s.saldo;
            if (abono.valor().compareTo(sumar(maximo, Calc.EPSILON_SALDO)) > 0) {
                throw new AbonoMayorQueElSaldoException(periodo, abono.valor(), maximo);
            }
            BigDecimal restante = abono.valor();
            if (primeroPendientes) {
                s.pendientesPagadosExtra = pagarPendientes(restante);
                restante = noNegativo(restar(restante, s.pendientesPagadosExtra.total()));
            }
            if (restante.signum() > 0 && corrientePorPagar.total().signum() > 0) {
                s.serviciosCorrientesExtra = pagarServiciosCorrientes(restante, corrientePorPagar);
                s.pendientesPagadosExtra = s.pendientesPagadosExtra.mas(
                        new DeudaExtinguida(CERO, CERO, CERO, s.serviciosCorrientesExtra, CERO));
                restante = noNegativo(restar(restante, s.serviciosCorrientesExtra.total()));
            }
            // Auditoría 2026-10-23 (H3): el abono va primero al capital vigente (con un abono menor que el vigente nada
            // cambia) y lo que lo supere extingue capital vencido. Antes se descontaba del saldo (que incluye el
            // vencido) sin reducir el vencido: el saldo del plan quedaba negativo, el capital contractual salía
            // negativo y el vencido seguía causando mora sobre capital ya pagado.
            BigDecimal aplicado = min(restante, noNegativo(restar(s.saldo, s.capitalVencido)));
            s.saldo = restar(s.saldo, aplicado);
            pagarVencidoConExtra(noNegativo(restar(restante, aplicado)));
            if (abono.modalidad() == ModalidadAbono.MENOR_CUOTA && s.cuotasVigentes - periodo > 0 && !esCero(s.saldo)) {
                // A5 (Diego 2026-10-20): la cuota nueva usa la tasa vigente; el abono no cambia la tasa (D-7.1).
                // Con PRIMERO_PENDIENTES la cuota nueva amortiza solo el capital vigente: el vencido que el
                // abono no alcanzó a cubrir sigue cobrándose aparte.
                BigDecimal base = primeroPendientes ? noNegativo(restar(s.saldo, s.capitalVencido)) : s.saldo;
                s.cuota = Amortizacion.cuotaFija(tasaVigente, s.cuotasVigentes - periodo, base);
            }
            s.saldoPlan = restar(s.saldo, s.capitalVencido);
            // SRV-2 (Diego, ronda 3, 2026-10-20): si el abono acorta el plazo, la cobertura de los servicios
            // termina con el nuevo plazo. Se recalcula el número de cuotas como en el caso 10 (NPER con la misma
            // cuota) para que causar() deje de causar servicios y capital contractual después de la última. En
            // el caso 6 del Excel el crédito ya terminaba en la cuota 71 porque el saldo llega a cero; esto solo
            // cambia algo si el cliente se atrasa después del abono.
            if (abono.modalidad() == ModalidadAbono.MENOR_PLAZO && periodo < s.cuotasVigentes && s.saldoPlan.signum() > 0
                    && !esCero(s.saldoPlan)) {
                s.cuotasVigentes = Math.min(s.cuotasVigentes, periodo + s.cuotasHastaSaldoCero(s.saldoPlan));
            }
            return aplicado;
        }

        /**
         * R4-CASO13: segunda pasada de la prelación con el excedente del abono, sobre lo causado del periodo
         * corriente. Cobranza, mora, interés corriente, seguro y capital de la cuota ya se causaron y los pagó la
         * primera pasada (como pendientes); lo único que falta son el fondo de garantías y la asistencia que el
         * periodo no causó por no pagarse al día (D17). Se pagan en el orden de Inputs ⑥ (fondo → asistencia →
         * seguro) y solo hasta donde alcance: lo que no se pague no queda como CxC, porque el periodo sigue
         * contando como no pagado al día. Nunca se cobra nada de periodos futuros (A4).
         */
        private Servicios pagarServiciosCorrientes(BigDecimal disponible, Servicios porPagar) {
            BigDecimal restante = disponible;
            BigDecimal fondo = min(restante, porPagar.fondoGarantias());
            restante = restar(restante, fondo);
            BigDecimal asistencia = min(restante, porPagar.asistencia());
            restante = restar(restante, asistencia);
            BigDecimal seguro = min(restante, porPagar.seguro());
            return new Servicios(fondo, asistencia, seguro);
        }

        /**
         * Auditoría 2026-10-23 (H3): capital vencido que paga un abono o un prepago aplicado solo a capital
         * (SOLO_CAPITAL, el Excel) cuando supera el capital vigente. Se reporta como capital de los pendientes
         * pagados por fuera de la cuota, para que la contabilidad lo acredite a Cartera capital vencida.
         */
        private void pagarVencidoConExtra(BigDecimal disponible) {
            BigDecimal vencido = min(disponible, s.capitalVencido);
            if (vencido.signum() <= 0) {
                return;
            }
            s.pendientesPagadosExtra = s.pendientesPagadosExtra.mas(
                    new DeudaExtinguida(CERO, CERO, CERO, Servicios.NINGUNO, vencido));
            s.capitalVencido = noNegativo(restar(s.capitalVencido, vencido));
            if (esCero(s.capitalVencido)) {
                s.capitalVencido = CERO;
            }
            s.saldo = noNegativo(restar(s.saldo, vencido));
        }

        /**
         * A4: aplica {@code disponible} a lo pendiente por la prelación de Inputs ⑥: cobranza → mora →
         * interés corriente → fondo → asistencia → seguro → capital vencido (con CAPITAL_ANTES_DE_SERVICIOS
         * el capital vencido va antes de los servicios, como en el reparto de la cuota). Devuelve lo pagado.
         */
        private DeudaExtinguida pagarPendientes(BigDecimal disponible) {
            BigDecimal restante = disponible;
            BigDecimal cobranza = min(restante, s.cxcCobranza);
            restante = restar(restante, cobranza);
            BigDecimal mora = min(restante, s.cxcMora);
            restante = restar(restante, mora);
            BigDecimal interes = min(restante, s.cxcInteres);
            restante = restar(restante, interes);
            BigDecimal vencido = CERO;
            boolean capitalAntes = escenario.reglas().prelacion() == ReglasNegocio.OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS;
            if (capitalAntes) {
                vencido = min(restante, s.capitalVencido);
                restante = restar(restante, vencido);
            }
            BigDecimal fondo = min(restante, s.cxcServicios.fondoGarantias());
            restante = restar(restante, fondo);
            BigDecimal asistencia = min(restante, s.cxcServicios.asistencia());
            restante = restar(restante, asistencia);
            BigDecimal seguro = min(restante, s.cxcServicios.seguro());
            restante = restar(restante, seguro);
            if (!capitalAntes) {
                vencido = min(restante, s.capitalVencido);
            }
            Servicios servicios = new Servicios(fondo, asistencia, seguro);
            s.cxcCobranza = noNegativo(restar(s.cxcCobranza, cobranza));
            s.cxcMora = noNegativo(restar(s.cxcMora, mora));
            s.cxcInteres = noNegativo(restar(s.cxcInteres, interes));
            s.cxcServicios = s.cxcServicios.menos(servicios).noNegativo();
            s.capitalVencido = noNegativo(restar(s.capitalVencido, vencido));
            if (esCero(s.capitalVencido)) {
                s.capitalVencido = CERO;
            }
            s.saldo = noNegativo(restar(s.saldo, vencido));
            return new DeudaExtinguida(cobranza, mora, interes, servicios, vencido);
        }

        /**
         * 4. Prepago total; devuelve el capital prepagado. A6: con interesPrepagoPorDias se cobra además
         * el interés corriente de los días desde la última cuota, saldo × ((1 + tasa diaria)^días − 1),
         * el mismo prorrateo diario de la mora; el Excel prepaga el día de la cuota y no lo cobra. Con
         * PRIMERO_PENDIENTES (A4) el prepago paga primero todo lo pendiente.
         */
        private BigDecimal prepagar(int periodo, PrepagoTotal prepago) {
            ReglasNegocio reglas = escenario.reglas();
            int dias = prepago.fecha() == null ? prepago.diasDesdeUltimaCuota() : diasHastaPrepago(periodo, prepago.fecha());
            if (reglas.interesPrepagoPorDias() && dias > 0) {
                BigDecimal base = reglas.baseInteres() == ReglasNegocio.BaseInteres.SALDO_VIGENTE
                        ? noNegativo(restar(s.saldo, s.capitalVencido)) : s.saldo;
                BigDecimal factorDias = restar(Calc.potencia(sumar(Calc.UNO, s.tasas.diaria()), dias), Calc.UNO);
                s.interesPrepago = multiplicar(base, factorDias);
            }
            if (reglas.aplicacionAbono().pagaPendientes()) {
                s.pendientesPagadosExtra = s.pendientesPagadosExtra.mas(pagarPendientes(sumar(s.saldo, s.pendientesSinCapital())));
            }
            // Auditoría 2026-10-23 (H3): el prepago total extingue el capital completo, también el vencido (con SOLO_CAPITAL
            // antes quedaba vivo con saldo 0 y seguía causando mora y servicios por más de 100 periodos).
            pagarVencidoConExtra(s.capitalVencido);
            BigDecimal prepagado = s.saldo;
            s.saldo = CERO;
            s.saldoPlan = CERO;
            return prepagado;
        }

        /**
         * PPER-7 / D27 (ADR 0002 paso 8): días reales de un prepago a mitad de periodo, desde el vencimiento de la cuota
         * del periodo (que ya se aplicó) hasta la fecha del prepago. Si la fecha es anterior a ese vencimiento son 0 (la
         * cuota ya cobró el periodo completo, como el Excel). Desde el vencimiento siguiente se rechaza: esa cuota ya es
         * exigible y el prepago va en el periodo siguiente.
         */
        private int diasHastaPrepago(int periodo, java.time.LocalDate fecha) {
            co.financiera.cartera.nucleo.CalendarioPagos calendario = escenario.calendario().orElseThrow(
                    () -> new IllegalStateException("El prepago con fecha necesita el calendario del crédito (Escenario.conCalendario)"));
            java.time.LocalDate siguiente = calendario.vencimiento(periodo + 1);
            if (!fecha.isBefore(siguiente)) {
                throw new IllegalArgumentException("El prepago del " + fecha + " cae en el periodo " + (periodo + 1)
                        + " o después (vence el " + siguiente + "); va en ese periodo");
            }
            return calendario.diasAtraso(periodo, fecha);
        }

        /**
         * 5. Recibe la moto: su valor se aplica por prelación a lo pendiente (cobranza, mora, interés,
         * fondo, asistencia, seguro, capital). Lo no cubierto es CxC al fondo de garantías; lo que
         * sobra, CxP al deudor (casos 3, 4 y 11).
         *
         * <p>R4-R2 (Diego, ronda 4): se descuenta el costo real de la recuperación con soporte. En la retoma
         * reduce el excedente que se devuelve. En la dación, con {@code costoRecuperacionAlFondo} (confirmado por
         * Diego en P5-COSTO-DACION) se suma al faltante que cubre el fondo; si no, lo asume la financiera.
         * Con costo 0 (el Excel) todo queda como antes.
         */
        private Cierre cerrar(CierrePorRecuperacion orden) {
            BigDecimal inventario = orden.valorMoto();
            BigDecimal costo = orden.costoRecuperacion();
            BigDecimal deuda = sumar(s.saldo, s.cxcInteres, s.cxcMora, s.cxcServicios.total(), s.cxcCobranza);
            boolean costoAlFondo = escenario.reglas().costoRecuperacionAlFondo();
            BigDecimal neto = restar(restar(inventario, deuda), costo);
            BigDecimal cxpDeudor = noNegativo(neto);
            BigDecimal cxcFondo;
            BigDecimal costoAsumido;
            BigDecimal disponible;
            if (costoAlFondo) {
                // La moto paga primero el costo y el resto va a la deuda; el faltante (con el costo) lo cubre el fondo.
                cxcFondo = noNegativo(restar(CERO, neto));
                costoAsumido = CERO;
                disponible = noNegativo(restar(inventario, costo));
            } else {
                // La moto paga primero la deuda; el costo solo se descuenta del excedente y el resto lo asume la financiera.
                cxcFondo = noNegativo(restar(deuda, inventario));
                costoAsumido = restar(costo, min(costo, noNegativo(restar(inventario, deuda))));
                disponible = inventario;
            }
            BigDecimal restante = restar(disponible, min(disponible, sumar(s.cxcCobranza, s.cxcMora, s.cxcInteres)));
            BigDecimal fondo = min(restante, s.cxcServicios.fondoGarantias());
            restante = restar(restante, fondo);
            BigDecimal asistencia = min(restante, s.cxcServicios.asistencia());
            restante = restar(restante, asistencia);
            BigDecimal seguro = min(restante, s.cxcServicios.seguro());
            Cierre cierre = new Cierre(deuda, inventario, cxcFondo, cxpDeudor, new Servicios(fondo, asistencia, seguro),
                    new DeudaExtinguida(s.cxcCobranza, s.cxcMora, s.cxcInteres, s.cxcServicios, s.saldo), costo, costoAsumido);
            s.saldo = CERO;
            s.saldoPlan = CERO;
            s.capitalVencido = CERO;
            s.cxcInteres = CERO;
            s.cxcMora = CERO;
            s.cxcServicios = Servicios.NINGUNO;
            s.cxcCobranza = CERO;
            return cierre;
        }

        private EstadoCredito estado(boolean cerrado, BigDecimal pendiente) {
            if (cerrado) {
                return EstadoCredito.CERRADO_POR_EVENTO;
            }
            if (esCero(sumar(s.saldo, pendiente))) {
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

        /**
         * 6. Lo recaudado de cada servicio se gira al tercero al cerrar el mes o en el último periodo
         * del crédito (Caso8 AD = SUMIF del mes de "Pago FCC+IVA (4)").
         */
        private Servicios girar(int periodo, Servicios recaudado, EstadoCredito estado) {
            recaudadoDelMes = recaudadoDelMes.mas(recaudado);
            boolean terminaMes = Calendario.mes(periodo + 1, p) > Calendario.mes(periodo, p);
            boolean termina = estado == EstadoCredito.CANCELADO || estado == EstadoCredito.CERRADO_POR_EVENTO
                    || periodo == p.plazoLimitePeriodos();
            if (!terminaMes && !termina) {
                return Servicios.NINGUNO;
            }
            Servicios giro = recaudadoDelMes;
            recaudadoDelMes = Servicios.NINGUNO;
            return giro;
        }

        private int diasAtraso(Instruccion pago) {
            return switch (pago) {
                case PagoContractual c -> c.diasAtraso();
                case PagoParcial c -> c.diasAtraso();
                case PagoValor c -> c.diasAtraso();
                default -> 0;
            };
        }

        /** Lo causado en un periodo. */
        private record Causacion(BigDecimal interes, BigDecimal capitalContractual, Servicios servicios, BigDecimal mora,
                BigDecimal cobranza, Servicios serviciosAlDia, boolean prorrateada) {

            /**
             * Servicios que el periodo no causó porque no se pagó al día (fondo y asistencia, D17) y que causaría
             * si la cuota corriente se pagara en su periodo.
             */
            Servicios serviciosNoCausados() {
                return serviciosAlDia.menos(servicios).noNegativo();
            }
        }

        /** Cómo se repartió el pago por prelación. */
        private record Reparto(BigDecimal cobranza, BigDecimal mora, BigDecimal interes, Servicios servicios,
                BigDecimal capital) {
        }

        /** Resultado de un cierre por recuperación de la moto. */
        private record Cierre(BigDecimal deuda, BigDecimal inventario, BigDecimal cxcFondoGarantias, BigDecimal cxpDeudor,
                Servicios servicios, DeudaExtinguida extinguido, BigDecimal costo, BigDecimal costoAsumido) {
            private static final Cierre NINGUNO = new Cierre(CERO, CERO, CERO, CERO, Servicios.NINGUNO, DeudaExtinguida.NINGUNA,
                    CERO, CERO);
        }
    }
}
