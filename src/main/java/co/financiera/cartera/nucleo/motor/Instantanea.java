package co.financiera.cartera.nucleo.motor;

import co.financiera.cartera.nucleo.CalendarioPagos;
import co.financiera.cartera.nucleo.Operacion;
import co.financiera.cartera.nucleo.ParametrosProducto;
import co.financiera.cartera.nucleo.Servicios;
import co.financiera.cartera.nucleo.Tasas;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Estado completo de un crédito al cierre de un periodo: cuanto el motor necesita para seguir con el periodo
 * siguiente sin volver a correr el crédito desde el desembolso ({@link MotorCartera#ejecutarDesde}). Es un valor
 * inmutable: se extrae con {@link MotorCartera#ejecutarHasta}, se guarda con {@link #aTexto()} (formato explícito y
 * versionado de {@link FormatoInstantanea}, sin serialización de Java) y se lee con {@link #leer(String)}.
 *
 * <p>Escala (auditoría de escala, D31 / USU-5): el recálculo masivo por usura y el cierre diario reanudan cada crédito
 * desde su última instantánea en vez de reprocesar todos sus periodos e instrucciones.
 *
 * <p>El perfil de reglas NO va dentro: se pasa aparte al reanudar. {@link #versionReglas()} es solo una referencia
 * (por ejemplo, el identificador de una versión de reglas guardada) que pone quien guarda la instantánea con
 * {@link #conVersionReglas(String)}; el motor no la interpreta.
 *
 * <p>Qué guarda y por qué:
 * <ul>
 *   <li>El contrato del crédito, que no cambia: la operación, los parámetros del producto, si la cobranza está activa
 *       (COB-6) y el calendario de fechas, si lo tiene (sin él, {@code null}).</li>
 *   <li>Los saldos de {@code EstadoCorrida}: tasas vigentes (cambian con la usura y los cambios de tasa), cuota,
 *       saldo, saldo del plan, capital vencido, las CxC por concepto y las cuotas del plan vigente.</li>
 *   <li>Lo que la corrida arrastra de periodos anteriores y no está en los saldos: las cuotas vencidas, lo recaudado
 *       del mes que aún no se ha girado a terceros, la usura publicada que rige desde la cuota siguiente (T1) y la
 *       marca de recuperación, que persiste (R6-RECUP-AUTO).</li>
 * </ul>
 *
 * @param periodo                 último periodo procesado (0 = solo el desembolso)
 * @param terminado               el crédito ya no tiene periodos (cancelado, cerrado por evento o en el plazo límite)
 * @param versionReglas           referencia opcional al perfil de reglas con que se corrió; {@code null} si no se fijó
 * @param calendario              fechas del crédito, o {@code null} si el escenario no tiene fechas
 * @param usuraPorAplicar         usura publicada en este periodo que rige desde la cuota siguiente, o {@code null}
 */
public record Instantanea(
        int periodo,
        boolean terminado,
        String versionReglas,
        Operacion operacion,
        ParametrosProducto parametros,
        boolean cobranzaActiva,
        CalendarioPagos calendario,
        Tasas tasas,
        BigDecimal cuota,
        BigDecimal saldo,
        BigDecimal saldoPlan,
        BigDecimal capitalVencido,
        BigDecimal cxcInteres,
        BigDecimal cxcMora,
        BigDecimal cxcCobranza,
        Servicios cxcServicios,
        int cuotasVigentes,
        int cuotasVencidas,
        Servicios recaudadoDelMes,
        BigDecimal usuraPorAplicar,
        boolean marcadoParaRecuperacion) {

    public Instantanea {
        if (periodo < 0) {
            throw new IllegalArgumentException("El periodo de la instantánea no puede ser negativo: " + periodo);
        }
        if (versionReglas != null && (versionReglas.indexOf('\n') >= 0 || versionReglas.indexOf('\r') >= 0)) {
            throw new IllegalArgumentException("La versión de reglas no puede tener saltos de línea");
        }
        Objects.requireNonNull(operacion, "operacion");
        Objects.requireNonNull(parametros, "parametros");
        Objects.requireNonNull(tasas, "tasas");
        Objects.requireNonNull(cuota, "cuota");
        Objects.requireNonNull(saldo, "saldo");
        Objects.requireNonNull(saldoPlan, "saldoPlan");
        Objects.requireNonNull(capitalVencido, "capitalVencido");
        Objects.requireNonNull(cxcInteres, "cxcInteres");
        Objects.requireNonNull(cxcMora, "cxcMora");
        Objects.requireNonNull(cxcCobranza, "cxcCobranza");
        Objects.requireNonNull(cxcServicios, "cxcServicios");
        Objects.requireNonNull(recaudadoDelMes, "recaudadoDelMes");
        if (calendario != null && calendario.periodicidad() != parametros.periodicidad()) {
            throw new IllegalArgumentException("El calendario es " + calendario.periodicidad().adjetivo()
                    + " y el crédito " + parametros.periodicidad().adjetivo());
        }
    }

    /** La misma instantánea con la referencia al perfil de reglas con que se corrió (la pone quien la guarda). */
    public Instantanea conVersionReglas(String version) {
        return new Instantanea(periodo, terminado, version, operacion, parametros, cobranzaActiva, calendario, tasas, cuota,
                saldo, saldoPlan, capitalVencido, cxcInteres, cxcMora, cxcCobranza, cxcServicios, cuotasVigentes,
                cuotasVencidas, recaudadoDelMes, usuraPorAplicar, marcadoParaRecuperacion);
    }

    /**
     * La misma instantánea con una usura publicada en su periodo: rige desde la cuota siguiente, exactamente como una
     * {@link Instruccion.UsuraVigente} en ese periodo (T1). Lo usa el recálculo masivo ({@link RecalculoUsura}). Si
     * ya hay una usura publicada en el periodo se rechaza, como el motor rechaza dos publicaciones en un periodo (H5).
     */
    public Instantanea conUsuraPublicada(BigDecimal usuraEA) {
        Objects.requireNonNull(usuraEA, "usuraEA");
        if (usuraEA.signum() <= 0) {
            throw new IllegalArgumentException("La usura debe ser positiva");
        }
        if (usuraPorAplicar != null) {
            throw new IllegalArgumentException("El periodo " + periodo + " ya tiene una usura publicada ("
                    + usuraPorAplicar.toPlainString() + "); el motor acepta una sola por periodo");
        }
        return new Instantanea(periodo, terminado, versionReglas, operacion, parametros, cobranzaActiva, calendario, tasas,
                cuota, saldo, saldoPlan, capitalVencido, cxcInteres, cxcMora, cxcCobranza, cxcServicios, cuotasVigentes,
                cuotasVencidas, recaudadoDelMes, usuraEA, marcadoParaRecuperacion);
    }

    /** El escenario base del crédito (sin instrucciones) con el perfil de reglas que se pasa aparte. */
    Escenario escenario(ReglasNegocio reglas) {
        Escenario e = new Escenario(operacion, parametros).conReglas(Objects.requireNonNull(reglas, "reglas"))
                .conCobranzaActiva(cobranzaActiva);
        return calendario == null ? e : e.conCalendario(calendario);
    }

    /** Texto en el formato explícito {@code clave=valor} versionado ({@link FormatoInstantanea}). */
    public String aTexto() {
        return FormatoInstantanea.escribir(this);
    }

    /** Lee una instantánea escrita con {@link #aTexto()}; falla si el formato o la versión no son los esperados. */
    public static Instantanea leer(String texto) {
        return FormatoInstantanea.leer(texto);
    }
}
