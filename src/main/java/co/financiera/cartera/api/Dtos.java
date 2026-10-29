package co.financiera.cartera.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * DTOs del API v1 (contrato en docs/api.md). Son propios del API: no exponen los records del núcleo. El
 * dinero y las tasas van como string decimal.
 */
public final class Dtos {

    private Dtos() {
    }

    // ---------------------------------------------------------------- Requests

    /**
     * Crédito a simular o ejecutar. Se manda {@code cuotas} o {@code meses}. Los valores de la operación
     * que no vengan toman los de la hoja Inputs ({@code valoresPorDefecto} del catálogo).
     *
     * @param plazoLimitePeriodos periodos que se proyecta un crédito con saldo pendiente (opcional; los casos
     *                            8 y 9 usan 100 y 120)
     * @param cobranzaActiva      si al crédito se le causan gastos de cobranza (COB-6, opcional; {@code true}
     *                            por defecto, como el Excel). En {@code false} los pagos tardíos no causan
     *                            cobranza
     * @param fechaDesembolso     fecha del desembolso, ISO {@code yyyy-MM-dd} (opcional; va junto con
     *                            {@code primerVencimiento}). Con las dos fechas el motor recibe el
     *                            {@code CalendarioPagos} y, si el perfil tiene {@code prorrateoPrimeraCuota}, prorratea
     *                            el interés de la primera cuota por los días reales (P5-PRORRATEO, D48)
     * @param primerVencimiento   primera fecha de pago, ISO {@code yyyy-MM-dd} (opcional): martes en semanal (D28), 5 o
     *                            20 en quincenal (D27), día 5 en mensual (D35); posterior al desembolso
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreditoRequest(String linea, String periodicidad, Integer cuotas, Integer meses, String valorMoto,
            String pagoInicial, String perfilReglas, String matriculaRunt, String soat, String prenda,
            String gpsPlataforma, String gastosComerciales, Integer plazoLimitePeriodos, Boolean cobranzaActiva,
            String fechaDesembolso, String primerVencimiento) {
    }

    /**
     * Una instrucción del escenario. Los campos son los del record de {@code Instruccion}; solo se usan los
     * del {@code tipo}. {@code modalidad} es la del abono (MENOR_PLAZO / MENOR_CUOTA) o la del cambio de tasa
     * (MISMA_FECHA_FINAL / MISMA_CUOTA). En {@code CIERRE_POR_RECUPERACION} el tipo de recuperación va en
     * {@code tipoRecuperacion} porque {@code tipo} ya es el de la instrucción. {@code costoRecuperacion} es
     * opcional (0 por defecto): el costo real de la recuperación con soporte (R4-R2).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InstruccionDto(Integer periodo, String tipo, Integer diasAtraso, String fraccion, String valor,
            Boolean causaMoraYServicios, String modalidad, Integer diasDesdeUltimaCuota, String usuraEA,
            String nuevaTasaEA, String tipoRecuperacion, String valorMoto, String costoRecuperacion) {
    }

    public record EjecutarRequest(CreditoRequest credito, String perfilReglas, List<InstruccionDto> instrucciones,
            Boolean incluirAsientos) {
    }

    public record ClasificarRequest(String valorRecibido, String valorAdeudado) {
    }

    // ---------------------------------------------------------------- Catálogo y reglas

    public record CatalogoResponse(List<LineaDto> lineas, List<PeriodicidadDto> periodicidades,
            List<String> tiposInstruccion, List<String> modalidadesAbono, List<String> modalidadesCambioTasa,
            List<String> tiposRecuperacion, List<String> perfilesReglas, Map<String, String> valoresPorDefecto) {
    }

    public record LineaDto(String codigo, String nombre, String tasaEA, int plazoMaximoMeses,
            List<String> periodicidades) {
    }

    public record PeriodicidadDto(String codigo, int periodosPorAnio, String adjetivo, boolean porConfirmar,
            int diasNominales, String unidadCuota, int cuotasMaximas) {
    }

    public record ReglasResponse(Map<String, Map<String, String>> perfiles, List<ClaveDto> claves,
            String archivoNegocio) {
    }

    public record ClaveDto(String clave, String descripcion, String fuente) {
    }

    // ---------------------------------------------------------------- Simulación

    public record SimulacionResponse(ResumenSimulacion resumen, List<FilaPlan> plan) {
    }

    /**
     * Con fechas en el crédito, {@code cuotaFinanciera} y {@code cuotaTotal} son las de una cuota regular (la del
     * periodo 2); la primera, prorrateada, está en {@code plan[0]} y en {@code prorrateoPrimeraCuota}.
     *
     * @param prorrateoPrimeraCuota solo si el crédito trae fechas (P5-PRORRATEO); sin fechas no aparece en el JSON
     */
    public record ResumenSimulacion(String linea, String perfilReglas, String montoFinanciado, String tasaEA,
            String tasaPeriodo, int cuotas, String cuotaFinanciera, String serviciosPorCuota, String cuotaTotal,
            String totalPagado, String totalIntereses, String totalServicios, String periodicidad,
            List<String> porConfirmar,
            @JsonInclude(JsonInclude.Include.NON_NULL) ProrrateoDto prorrateoPrimeraCuota) {
    }

    /**
     * Prorrateo de la primera cuota (P5-PRORRATEO, D48): solo el interés del periodo 1 se cobra por los días reales
     * entre el desembolso y la primera fecha de pago, con la tasa diaria; capital y servicios van completos.
     *
     * @param diasPrimerPeriodo     días reales del periodo 1 ({@code CalendarioPagos.diasDelPeriodo(1)})
     * @param diasNominales         días nominales de la periodicidad (7, 15 o 30), como referencia
     * @param aplicado              si el perfil tiene {@code prorrateoPrimeraCuota} (NEGOCIO sí, EXCEL no); en
     *                              {@code false} el interés del periodo 1 es el de un periodo completo
     * @param interesSinProrrateo   interés de un periodo completo: saldo inicial × tasa del periodo
     * @param interesProrrateado    interés que causó el motor en el periodo 1
     * @param diferencia            interesProrrateado − interesSinProrrateo (negativo si el periodo es corto)
     */
    public record ProrrateoDto(String fechaDesembolso, String primerVencimiento, int diasPrimerPeriodo,
            int diasNominales, boolean aplicado, String interesSinProrrateo, String interesProrrateado,
            String diferencia) {
    }

    public record FilaPlan(int periodo, int mes, String saldoInicial, String interes, String capital,
            String cuotaFinanciera, String fondoGarantias, String asistencia, String seguro, String cuotaTotal,
            String saldo) {
    }

    // ---------------------------------------------------------------- Ejecución de escenarios

    public record EjecucionResponse(ResumenEjecucion resumen, List<MovimientoDto> movimientos,
            List<AsientoDto> asientos) {
    }

    /**
     * @param listoParaRecuperacion         PPER-9: el último periodo quedó marcado listo para dación o retoma
     * @param periodoListoParaRecuperacion  PPER-9: primer periodo en que el crédito quedó listo para recuperación;
     *                                      {@code null} si nunca llegó al umbral (o el perfil no lo fija)
     * @param prorrateoPrimeraCuota         solo si el crédito trae fechas (P5-PRORRATEO); sin fechas no aparece
     */
    public record ResumenEjecucion(int periodoFinal, String estadoFinal, String saldoFinal, String totalPagadoCliente,
            int cuotasVencidasMax, String montoFinanciado, String perfilReglas, boolean listoParaRecuperacion,
            Integer periodoListoParaRecuperacion,
            @JsonInclude(JsonInclude.Include.NON_NULL) ProrrateoDto prorrateoPrimeraCuota) {
    }

    public record ServiciosDto(String fondoGarantias, String asistencia, String seguro, String total) {
    }

    public record DeudaDto(String cobranza, String mora, String interesCorriente, ServiciosDto servicios,
            String capital, String total) {
    }

    /** Una fila del cronograma: todos los campos de {@code Movimiento}, con el dinero como string. */
    public record MovimientoDto(int periodo, int mes, String estado, String evento, String etiqueta,
            String saldoInicial, String tasaPeriodica, String cuotaFinanciera, String interesCorriente,
            String interesMora, String capitalContractual, String cobranzaCausada, ServiciosDto serviciosCausados,
            String pagoRecibido, String pagoCobranza, String pagoMora, String pagoInteresCorriente,
            String pagoServicios, String pagoCapital, String abonoExtra, String prepagoCapital, String cxcCobranza,
            String cxcMora, String cxcInteresCorriente, String cxcServicios, String capitalVencido,
            String capitalVigente, String saldoFinal, int cuotasVencidas, String deudaAExtinguir, String inventario,
            String cxcFondoGarantias, String cxpDeudor, ServiciosDto pagoTerceros, ServiciosDto pagosServicios,
            ServiciosDto cxcServiciosPorConcepto, DeudaDto extinguido, String capitalVencidoInicial,
            DeudaDto pendientesPagadosExtra, String interesPrepago, String costoRecuperacion,
            String costoRecuperacionAsumido, String pagoTotalCliente, String pagoExtraordinario,
            boolean listoParaRecuperacion,
            // R8-PAGO-MAYOR-EXIGIBLE: sobrante por devolver al cliente; solo viaja cuando lo hay (nunca con EXCEL).
            @JsonInclude(JsonInclude.Include.NON_NULL) String sobranteCliente) {
    }

    public record AsientoDto(int periodo, String evento, List<LineaAsientoDto> lineas, String totalDebitos,
            String totalCreditos, boolean cuadra) {
    }

    public record LineaAsientoDto(String cuenta, String nombreCuenta, String debito, String credito,
            String explicacion) {
    }

    // ---------------------------------------------------------------- Casos

    public record CasoResumenDto(int numero, String nombre, String descripcion, String fuente) {
    }

    /** La respuesta de /escenarios/ejecutar más el caso, su crédito y sus instrucciones en formato de request. */
    public record CasoResponse(CasoResumenDto caso, String perfilReglas, CreditoRequest credito,
            List<InstruccionDto> instrucciones, ResumenEjecucion resumen, List<MovimientoDto> movimientos,
            List<AsientoDto> asientos) {
    }

    // ---------------------------------------------------------------- Pagos y errores

    public record ClasificacionResponse(String pagoCorriente, String abonoExtra) {
    }

    public record ErrorDto(String codigo, String mensaje, Map<String, Object> detalle) {
    }
}
