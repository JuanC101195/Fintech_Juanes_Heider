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
        private final int cuotasPactadas;
        private final Servicios serviciosPorCuota;
        private final List<Movimiento> filas = new ArrayList<>();

        private Tasas tasas;
        private BigDecimal cuota;
        private BigDecimal saldo;
        /** Cuotas del plan vigente: cambian cuando un cambio de tasa conserva la cuota (caso 10). */
        private int cuotasVigentes;
        private BigDecimal capitalVencido = CERO;
        private BigDecimal cxcInteres = CERO;
        private BigDecimal cxcMora = CERO;
        private Servicios cxcServicios = Servicios.NINGUNO;
        private BigDecimal cxcCobranza = CERO;
        /** Saldo del plan vigente si el cliente pagara siempre a tiempo: de él sale el capital contractual. */
        private BigDecimal saldoPlan;
        private int cuotasVencidas;
        private Servicios recaudadoDelMes = Servicios.NINGUNO;
        /** Usura publicada la semana anterior: rige desde esta cuota (T1, Diego 2026-10-20). */
        private BigDecimal usuraPorAplicar;
        /** Lo que el abono extra o el prepago de la semana pagó de pendientes (A4). */
        private DeudaExtinguida pendientesPagadosExtra = DeudaExtinguida.NINGUNA;
        private BigDecimal interesPrepago = CERO;

        Corrida(Escenario escenario) {
            this.escenario = escenario;
            this.p = escenario.parametros();
            this.cuotasPactadas = Calendario.numeroCuotas(p);
            this.cuotasVigentes = cuotasPactadas;
            BigDecimal monto = escenario.operacion().montoFinanciado();
            this.serviciosPorCuota = Servicios.porCuota(monto, p);
            this.tasas = Tasas.de(p);
            this.cuota = Amortizacion.cuotaFija(tasas.periodica(), cuotasPactadas, monto);
            this.saldo = monto;
            this.saldoPlan = monto;
        }

        List<Movimiento> correr() {
            filas.add(desembolso());
            for (int periodo = 1; periodo <= p.plazoLimitePeriodos(); periodo++) {
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
                    Servicios.NINGUNO, EstadoCredito.DESEMBOLSADO, Servicios.NINGUNO, Servicios.NINGUNO, DeudaExtinguida.NINGUNA,
                    CERO, DeudaExtinguida.NINGUNA, CERO);
        }

        private Movimiento semana(int periodo, List<Instruccion> instrucciones) {
            Ordenes ordenes = leer(periodo, instrucciones);
            Instruccion pago = ordenes.pago;
            BigDecimal tasa = tasas.periodica();
            BigDecimal saldoInicial = saldo;
            BigDecimal vencidoInicial = capitalVencido;
            // P7 / P-16: con moraAntesDeRecuperacion (el Excel) toda semana sin pago causa mora y seguro.
            boolean sinPagoSinMora = pago instanceof SinPago(boolean causaMora) && !causaMora
                    && !escenario.reglas().moraAntesDeRecuperacion();
            int dias = diasAtraso(pago);

            // 2. Causación del periodo.
            Causacion causado = causar(periodo, saldoInicial, sinPagoSinMora, dias, alDia(pago, dias));

            // 3. Pago y prelación.
            BigDecimal cuotaTotal = sumar(cuota, causado.servicios().total());
            BigDecimal recibido = recibido(pago, cuotaTotal, saldoInicial, causado);
            Reparto reparto = repartir(recibido, saldoInicial, causado);
            actualizarSaldos(saldoInicial, causado, reparto, sinPagoSinMora);
            boolean pagoCompleto = recibido.compareTo(restar(cuotaTotal, Calc.EPSILON_SALDO)) >= 0;

            // La fila reporta la cuota que se cobró este periodo: un abono a menor cuota (caso 7) cambia
            // la cuota solo desde el periodo siguiente (Caso7 F46 = MIN(B24, D + E); F47 = PMT(...)).
            BigDecimal cuotaDelPeriodo = cuota;

            // 4. Abono extraordinario o prepago, después de la cuota del periodo. Con PRIMERO_PENDIENTES (A4)
            // pagan antes lo pendiente de semanas anteriores; nunca adelantan intereses ni servicios futuros.
            pendientesPagadosExtra = DeudaExtinguida.NINGUNA;
            interesPrepago = CERO;
            BigDecimal abonoAplicado = ordenes.abono == null ? CERO : aplicarAbono(periodo, ordenes.abono, tasa);
            BigDecimal prepagoCapital = ordenes.prepago == null ? CERO : prepagar(ordenes.prepago);

            // 5. Cierre por recuperación de la moto: su valor extingue lo pendiente por prelación. La fila
            // del cierre reporta el capital vencido que se extinguió (Caso3 Z del evento).
            BigDecimal vencidoReportado = capitalVencido;
            Cierre cierre = ordenes.cierre == null ? Cierre.NINGUNO : cerrar(ordenes.cierre);

            // 6. Estado, cuotas vencidas y giro a terceros de lo recaudado.
            BigDecimal pendiente = sumar(capitalVencido, cxcCobranza, cxcMora, cxcInteres, cxcServicios.total());
            EstadoCredito estado = estado(ordenes.cierre != null, pendiente);
            contarCuotasVencidas(pagoCompleto, pago, pendiente);
            // A4: un abono o prepago que pagó todo lo pendiente deja al cliente al día.
            if (pendientesPagadosExtra.total().signum() > 0 && esCero(pendiente)) {
                cuotasVencidas = 0;
            }
            Servicios recaudado = reparto.servicios().mas(cierre.servicios());
            Servicios giro = girar(periodo, recaudado.mas(pendientesPagadosExtra.servicios()), estado);

            String evento = evento(ordenes, periodo, pagoCompleto, dias, recibido);
            return new Movimiento(periodo, Calendario.mes(periodo, p), evento, saldoInicial, tasa, cuotaDelPeriodo,
                    causado.interes(), causado.mora(), causado.capitalContractual(), causado.cobranza(), causado.servicios(),
                    recibido, reparto.cobranza(), reparto.mora(), reparto.interes(), reparto.servicios().total(),
                    reparto.capital(), abonoAplicado, prepagoCapital, cxcCobranza, cxcMora, cxcInteres, cxcServicios.total(),
                    vencidoReportado, max(CERO, restar(saldo, capitalVencido)), saldo, cuotasVencidas, cierre.deuda(),
                    cierre.inventario(), cierre.cxcFondoGarantias(), cierre.cxpDeudor(), giro, estado, recaudado,
                    cxcServicios, cierre.extinguido(), vencidoInicial, pendientesPagadosExtra, interesPrepago);
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
            for (Instruccion i : instrucciones) {
                switch (i) {
                    case CambioTasa(BigDecimal nuevaTasaEA, ModalidadCambioTasa modalidad) -> {
                        cambiarTasa(periodo, nuevaTasaEA, modalidad);
                        ordenes.cambioTasa = true;
                    }
                    case UsuraVigente(BigDecimal usuraEA) -> usuraPorAplicar = usuraEA;
                    case AbonoExtra a -> ordenes.abono = a;
                    case PrepagoTotal t -> ordenes.prepago = t;
                    case CierrePorRecuperacion c -> ordenes.cierre = c;
                    default -> {
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
         * Ajuste por usura como evento de tasa: la EA vigente baja a usura − margen solo si la supera
         * ({@link Usura#tasaAjustada(BigDecimal, BigDecimal, BigDecimal)}). Lo que se conserva lo decide
         * {@code ReglasNegocio.modalidadUsura}: con MISMA_CUOTA (Diego, USU-6, ronda 3) la cuota se mantiene y
         * baja el plazo como en el caso 10; con MISMA_FECHA_FINAL (EXCEL) la cuota se recalcula como en el
         * caso 2. Devuelve si la tasa cambió.
         */
        private boolean ajustarPorUsura(int periodo, BigDecimal usuraEA) {
            BigDecimal actual = tasas.efectivaAnual();
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
            tasas = Tasas.desdeEA(nuevaTasaEA, p.periodosPorAnio(), p.mesesPorAnio());
            saldoPlan = restar(saldo, capitalVencido);
            if (modalidad == ModalidadCambioTasa.MISMA_FECHA_FINAL) {
                cuota = Amortizacion.cuotaFija(tasas.periodica(), cuotasVigentes - periodo + 1, saldo);
                return;
            }
            cuotasVigentes = periodo - 1 + cuotasHastaSaldoCero(saldoPlan);
        }

        /**
         * Cuotas de valor {@code cuota} que hacen falta para llevar {@code base} a cero con la tasa vigente,
         * NPER = −ln(1 − r·S/C) / ln(1 + r) redondeado hacia arriba (la última se ajusta). Lo usan el caso 10
         * y el abono a menor plazo (SRV-2).
         */
        private int cuotasHastaSaldoCero(BigDecimal base) {
            double r = tasas.periodica().doubleValue();
            double proporcion = r * base.doubleValue() / cuota.doubleValue();
            if (proporcion >= 1) {
                throw new IllegalArgumentException("Con la tasa nueva la cuota no alcanza para cubrir el interés: no amortiza");
            }
            return (int) Math.ceil(-Math.log(1 - proporcion) / Math.log(1 + r) - 1e-9);
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
            BigDecimal tasa = tasas.periodica();
            boolean dentroDelPlazo = periodo <= cuotasVigentes;
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
                Servicios base = reglas.baseServicios() == ReglasNegocio.BaseServicios.SALDO
                        ? Servicios.porCuota(saldoInicial, p) : serviciosPorCuota;
                servicios = alDia ? base : base.soloSeguro();
            }
            return new Causacion(multiplicar(baseInteres, tasa), capitalContractual, servicios, mora(capitalContractual, dias),
                    cobranza(saldoInicial, dias));
        }

        private BigDecimal mora(BigDecimal capitalContractual, int dias) {
            BigDecimal mora = multiplicar(capitalVencido, tasas.periodica());
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

        /** 3. Lo que entra este periodo, limitado a lo exigible. */
        private BigDecimal recibido(Instruccion pago, BigDecimal cuotaTotal, BigDecimal saldoInicial, Causacion c) {
            BigDecimal pendienteSinCapital = sumar(cxcCobranza, c.cobranza(), cxcMora, c.mora(), cxcInteres, c.interes(),
                    cxcServicios.total(), c.servicios().total());
            BigDecimal exigible = sumar(saldoInicial, pendienteSinCapital);
            return switch (pago) {
                case PagoContractual x -> min(cuotaTotal, exigible);
                // El pago parcial es una fracción de la cuota total pactada (Inputs B36: cuota + los tres servicios).
                case PagoParcial x -> min(multiplicar(x.fraccion(), sumar(cuota, serviciosPorCuota.total())), exigible);
                case PagoValor x -> min(x.valor(), exigible);
                case PagoAlDia x -> min(sumar(pendienteSinCapital, capitalVencido, c.capitalContractual()), exigible);
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
            BigDecimal pagoCobranza = min(restante, sumar(cxcCobranza, c.cobranza()));
            restante = restar(restante, pagoCobranza);
            BigDecimal pagoMora = min(restante, sumar(cxcMora, c.mora()));
            restante = restar(restante, pagoMora);
            BigDecimal pagoInteres = min(restante, sumar(cxcInteres, c.interes()));
            restante = restar(restante, pagoInteres);
            Servicios porCobrar = cxcServicios.mas(c.servicios());
            BigDecimal pagoCapital = CERO;
            if (escenario.reglas().prelacion() == ReglasNegocio.OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS) {
                // Solo se adelanta el capital de la cuota (contractual + vencido); el excedente pasa a servicios.
                pagoCapital = min(restante, min(saldoInicial, sumar(capitalVencido, c.capitalContractual())));
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
            cxcCobranza = noNegativo(restar(sumar(cxcCobranza, c.cobranza()), r.cobranza()));
            cxcMora = noNegativo(restar(sumar(cxcMora, c.mora()), r.mora()));
            cxcInteres = noNegativo(restar(sumar(cxcInteres, c.interes()), r.interes()));
            cxcServicios = cxcServicios.mas(c.servicios()).menos(r.servicios()).noNegativo();
            saldo = noNegativo(restar(saldoInicial, r.capital()));
            if (!sinPagoSinMora) {
                capitalVencido = noNegativo(restar(sumar(capitalVencido, c.capitalContractual()), r.capital()));
                if (esCero(capitalVencido)) {
                    capitalVencido = CERO;
                }
            }
            saldoPlan = noNegativo(restar(saldoPlan, c.capitalContractual()));
        }

        /**
         * 4. Abono extraordinario; devuelve lo aplicado a capital vigente. Con SOLO_CAPITAL (Excel) va
         * 100 % a capital. Con PRIMERO_PENDIENTES (A4, Diego 2026-10-20) paga antes los pendientes de
         * semanas anteriores por prelación y el resto va a capital: como la cuota del periodo ya se
         * aplicó, solo quedan CxC ya causadas y nunca se adelantan intereses ni servicios futuros.
         */
        private BigDecimal aplicarAbono(int periodo, AbonoExtra abono, BigDecimal tasaVigente) {
            boolean primeroPendientes = escenario.reglas().aplicacionAbono() == AplicacionAbono.PRIMERO_PENDIENTES;
            // P-14: Diego (2026-10-16) no acepta abonos mayores que el saldo; el Excel los recortaba. Con
            // pendientes, el saldo del crédito incluye lo que se debe de semanas anteriores.
            BigDecimal maximo = primeroPendientes ? sumar(saldo, pendientesSinCapital()) : saldo;
            if (abono.valor().compareTo(sumar(maximo, Calc.EPSILON_SALDO)) > 0) {
                throw new AbonoMayorQueElSaldoException(periodo, abono.valor(), maximo);
            }
            BigDecimal restante = abono.valor();
            if (primeroPendientes) {
                pendientesPagadosExtra = pagarPendientes(restante);
                restante = noNegativo(restar(restante, pendientesPagadosExtra.total()));
            }
            BigDecimal aplicado = min(restante, saldo);
            saldo = restar(saldo, aplicado);
            if (abono.modalidad() == ModalidadAbono.MENOR_CUOTA && cuotasVigentes - periodo > 0 && !esCero(saldo)) {
                // A5 (Diego 2026-10-20): la cuota nueva usa la tasa vigente; el abono no cambia la tasa (D-7.1).
                // Con PRIMERO_PENDIENTES la cuota nueva amortiza solo el capital vigente: el vencido que el
                // abono no alcanzó a cubrir sigue cobrándose aparte.
                BigDecimal base = primeroPendientes ? noNegativo(restar(saldo, capitalVencido)) : saldo;
                cuota = Amortizacion.cuotaFija(tasaVigente, cuotasVigentes - periodo, base);
            }
            saldoPlan = restar(saldo, capitalVencido);
            // SRV-2 (Diego, ronda 3, 2026-10-20): si el abono acorta el plazo, la cobertura de los servicios
            // termina con el nuevo plazo. Se recalcula el número de cuotas como en el caso 10 (NPER con la misma
            // cuota) para que causar() deje de causar servicios y capital contractual después de la última. En
            // el caso 6 del Excel el crédito ya terminaba en la cuota 71 porque el saldo llega a cero; esto solo
            // cambia algo si el cliente se atrasa después del abono.
            if (abono.modalidad() == ModalidadAbono.MENOR_PLAZO && periodo < cuotasVigentes && saldoPlan.signum() > 0
                    && !esCero(saldoPlan)) {
                cuotasVigentes = Math.min(cuotasVigentes, periodo + cuotasHastaSaldoCero(saldoPlan));
            }
            return aplicado;
        }

        /** Pendientes de semanas anteriores distintos del capital: cobranza, mora, interés y servicios. */
        private BigDecimal pendientesSinCapital() {
            return sumar(cxcCobranza, cxcMora, cxcInteres, cxcServicios.total());
        }

        /**
         * A4: aplica {@code disponible} a lo pendiente por la prelación de Inputs ⑥: cobranza → mora →
         * interés corriente → fondo → asistencia → seguro → capital vencido (con CAPITAL_ANTES_DE_SERVICIOS
         * el capital vencido va antes de los servicios, como en el reparto de la cuota). Devuelve lo pagado.
         */
        private DeudaExtinguida pagarPendientes(BigDecimal disponible) {
            BigDecimal restante = disponible;
            BigDecimal cobranza = min(restante, cxcCobranza);
            restante = restar(restante, cobranza);
            BigDecimal mora = min(restante, cxcMora);
            restante = restar(restante, mora);
            BigDecimal interes = min(restante, cxcInteres);
            restante = restar(restante, interes);
            BigDecimal vencido = CERO;
            boolean capitalAntes = escenario.reglas().prelacion() == ReglasNegocio.OrdenPrelacion.CAPITAL_ANTES_DE_SERVICIOS;
            if (capitalAntes) {
                vencido = min(restante, capitalVencido);
                restante = restar(restante, vencido);
            }
            BigDecimal fondo = min(restante, cxcServicios.fondoGarantias());
            restante = restar(restante, fondo);
            BigDecimal asistencia = min(restante, cxcServicios.asistencia());
            restante = restar(restante, asistencia);
            BigDecimal seguro = min(restante, cxcServicios.seguro());
            restante = restar(restante, seguro);
            if (!capitalAntes) {
                vencido = min(restante, capitalVencido);
            }
            Servicios servicios = new Servicios(fondo, asistencia, seguro);
            cxcCobranza = noNegativo(restar(cxcCobranza, cobranza));
            cxcMora = noNegativo(restar(cxcMora, mora));
            cxcInteres = noNegativo(restar(cxcInteres, interes));
            cxcServicios = cxcServicios.menos(servicios).noNegativo();
            capitalVencido = noNegativo(restar(capitalVencido, vencido));
            if (esCero(capitalVencido)) {
                capitalVencido = CERO;
            }
            saldo = noNegativo(restar(saldo, vencido));
            return new DeudaExtinguida(cobranza, mora, interes, servicios, vencido);
        }

        /**
         * 4. Prepago total; devuelve el capital prepagado. A6: con interesPrepagoPorDias se cobra además
         * el interés corriente de los días desde la última cuota, saldo × ((1 + tasa diaria)^días − 1),
         * el mismo prorrateo diario de la mora; el Excel prepaga el día de la cuota y no lo cobra. Con
         * PRIMERO_PENDIENTES (A4) el prepago paga primero todo lo pendiente.
         */
        private BigDecimal prepagar(PrepagoTotal prepago) {
            ReglasNegocio reglas = escenario.reglas();
            int dias = prepago.diasDesdeUltimaCuota();
            if (reglas.interesPrepagoPorDias() && dias > 0) {
                BigDecimal base = reglas.baseInteres() == ReglasNegocio.BaseInteres.SALDO_VIGENTE
                        ? noNegativo(restar(saldo, capitalVencido)) : saldo;
                BigDecimal factorDias = restar(Calc.potencia(sumar(Calc.UNO, tasas.diaria()), dias), Calc.UNO);
                interesPrepago = multiplicar(base, factorDias);
            }
            if (reglas.aplicacionAbono() == AplicacionAbono.PRIMERO_PENDIENTES) {
                pendientesPagadosExtra = pendientesPagadosExtra.mas(pagarPendientes(sumar(saldo, pendientesSinCapital())));
            }
            BigDecimal prepagado = saldo;
            saldo = CERO;
            saldoPlan = CERO;
            return prepagado;
        }

        /**
         * 5. Recibe la moto: su valor se aplica por prelación a lo pendiente (cobranza, mora, interés,
         * fondo, asistencia, seguro, capital). Lo no cubierto es CxC al fondo de garantías; lo que
         * sobra, CxP al deudor (casos 3, 4 y 11).
         */
        private Cierre cerrar(CierrePorRecuperacion orden) {
            BigDecimal inventario = orden.valorMoto();
            BigDecimal deuda = sumar(saldo, cxcInteres, cxcMora, cxcServicios.total(), cxcCobranza);
            BigDecimal restante = restar(inventario, min(inventario, sumar(cxcCobranza, cxcMora, cxcInteres)));
            BigDecimal fondo = min(restante, cxcServicios.fondoGarantias());
            restante = restar(restante, fondo);
            BigDecimal asistencia = min(restante, cxcServicios.asistencia());
            restante = restar(restante, asistencia);
            BigDecimal seguro = min(restante, cxcServicios.seguro());
            Cierre cierre = new Cierre(deuda, inventario, noNegativo(restar(deuda, inventario)),
                    noNegativo(restar(inventario, deuda)), new Servicios(fondo, asistencia, seguro),
                    new DeudaExtinguida(cxcCobranza, cxcMora, cxcInteres, cxcServicios, saldo));
            saldo = CERO;
            saldoPlan = CERO;
            capitalVencido = CERO;
            cxcInteres = CERO;
            cxcMora = CERO;
            cxcServicios = Servicios.NINGUNO;
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

        /** El evento de mayor jerarquía del periodo: cierre, prepago, abono, cambio de tasa o el pago. */
        private String evento(Ordenes o, int periodo, boolean pagoCompleto, int dias, BigDecimal recibido) {
            if (o.cierre != null) {
                return "Evento de cierre";
            }
            if (o.prepago != null) {
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
            if (pago instanceof PagoAlDia) {
                return "Normalización - paga mes + atrasado";
            }
            if (periodo > cuotasVigentes) {
                return "Periodo adicional";
            }
            if (dias > 0) {
                return "Pago tardío + cobranza";
            }
            // La última cuota contractual puede ser menor porque liquida el saldo: eso no es un pago inferior.
            if (!completo && recibido.signum() > 0 && !(pago instanceof PagoContractual)) {
                return "Pago inferior";
            }
            return periodo == cuotasVigentes ? "Pago final" : "Pago normal";
        }

        /** Lo que piden las instrucciones de un periodo. */
        private static final class Ordenes {
            private Instruccion pago = PagoContractual.A_TIEMPO;
            private AbonoExtra abono;
            private PrepagoTotal prepago;
            private boolean cambioTasa;
            private CierrePorRecuperacion cierre;
        }

        /** Lo causado en un periodo. */
        private record Causacion(BigDecimal interes, BigDecimal capitalContractual, Servicios servicios, BigDecimal mora,
                BigDecimal cobranza) {
        }

        /** Cómo se repartió el pago por prelación. */
        private record Reparto(BigDecimal cobranza, BigDecimal mora, BigDecimal interes, Servicios servicios,
                BigDecimal capital) {
        }

        /** Resultado de un cierre por recuperación de la moto. */
        private record Cierre(BigDecimal deuda, BigDecimal inventario, BigDecimal cxcFondoGarantias, BigDecimal cxpDeudor,
                Servicios servicios, DeudaExtinguida extinguido) {
            private static final Cierre NINGUNO = new Cierre(CERO, CERO, CERO, CERO, Servicios.NINGUNO, DeudaExtinguida.NINGUNA);
        }
    }
}
