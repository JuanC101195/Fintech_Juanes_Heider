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
import co.financiera.cartera.nucleo.motor.Instruccion.ModalidadCambioTasa;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoAlDia;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoContractual;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoParcial;
import co.financiera.cartera.nucleo.motor.Instruccion.PagoValor;
import co.financiera.cartera.nucleo.motor.Instruccion.PrepagoTotal;
import co.financiera.cartera.nucleo.motor.Instruccion.SinPago;
import co.financiera.cartera.nucleo.motor.Instruccion.UsuraVigente;
import co.financiera.cartera.nucleo.motor.Recuperacion.Cierre;
import co.financiera.cartera.nucleo.motor.RepartoPrelacion.Reparto;
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
        /** Paso 5 (dación / retoma) y la marca de listo para recuperación (R6-RECUP-AUTO). */
        private final Recuperacion recuperacion;
        /** Paso 4: abono extraordinario y prepago total. */
        private final Abonos abonos;
        /** Paso 3: pago recibido, reparto por prelación y saldos al cierre de la cuota. */
        private final RepartoPrelacion prelacion;

        Corrida(Escenario escenario) {
            this.escenario = escenario;
            this.p = escenario.parametros();
            this.s = new EstadoCorrida(escenario, Calendario.numeroCuotas(p));
            this.serviciosPorCuota = s.serviciosPorCuota;
            this.recuperacion = new Recuperacion(s);
            this.abonos = new Abonos(s);
            this.prelacion = new RepartoPrelacion(s);
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
            BigDecimal recibido = prelacion.recibido(pago, cuotaTotal, cuotaDelPeriodo, saldoInicial, causado);
            Reparto reparto = prelacion.repartir(recibido, saldoInicial, causado);
            prelacion.actualizarSaldos(saldoInicial, causado, reparto, sinPagoSinMora);
            boolean pagoCompleto = recibido.compareTo(restar(cuotaTotal, Calc.EPSILON_SALDO)) >= 0;

            // 4. Abono extraordinario o prepago, después de la cuota del periodo. Con PRIMERO_PENDIENTES (A4)
            // pagan antes lo pendiente de semanas anteriores; nunca adelantan intereses ni servicios futuros.
            s.pendientesPagadosExtra = DeudaExtinguida.NINGUNA;
            s.interesPrepago = CERO;
            s.serviciosCorrientesExtra = Servicios.NINGUNO;
            BigDecimal abonoAplicado = ordenes.abono == null ? CERO : abonos.aplicarAbono(periodo, ordenes.abono, tasa, causado);
            BigDecimal prepagoCapital = ordenes.prepago == null ? CERO : abonos.prepagar(periodo, ordenes.prepago);

            // 5. Cierre por recuperación de la moto: su valor extingue lo pendiente por prelación. La fila
            // del cierre reporta el capital vencido que se extinguió (Caso3 Z del evento).
            BigDecimal vencidoReportado = s.capitalVencido;
            Cierre cierre = ordenes.cierre == null ? Cierre.NINGUNO : recuperacion.cerrar(ordenes.cierre);

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
            // PPER-9 / R6-RECUP-AUTO: el motor solo marca el crédito; la marca persiste hasta que termina.
            boolean listoParaRecuperacion = recuperacion.listoParaRecuperacion(estado, cuotasVencidas);
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

    }
}
