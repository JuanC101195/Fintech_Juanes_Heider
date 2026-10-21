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
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreditoRequest(String linea, String periodicidad, Integer cuotas, Integer meses, String valorMoto,
            String pagoInicial, String perfilReglas, String matriculaRunt, String soat, String prenda,
            String gpsPlataforma, String gastosComerciales, Integer plazoLimitePeriodos) {
    }

    /**
     * Una instrucción del escenario. Los campos son los del record de {@code Instruccion}; solo se usan los
     * del {@code tipo}. {@code modalidad} es la del abono (MENOR_PLAZO / MENOR_CUOTA) o la del cambio de tasa
     * (MISMA_FECHA_FINAL / MISMA_CUOTA). En {@code CIERRE_POR_RECUPERACION} el tipo de recuperación va en
     * {@code tipoRecuperacion} porque {@code tipo} ya es el de la instrucción.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InstruccionDto(Integer periodo, String tipo, Integer diasAtraso, String fraccion, String valor,
            Boolean causaMoraYServicios, String modalidad, Integer diasDesdeUltimaCuota, String usuraEA,
            String nuevaTasaEA, String tipoRecuperacion, String valorMoto) {
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

    public record ResumenSimulacion(String linea, String perfilReglas, String montoFinanciado, String tasaEA,
            String tasaPeriodo, int cuotas, String cuotaFinanciera, String serviciosPorCuota, String cuotaTotal,
            String totalPagado, String totalIntereses, String totalServicios, String periodicidad,
            List<String> porConfirmar) {
    }

    public record FilaPlan(int periodo, int mes, String saldoInicial, String interes, String capital,
            String cuotaFinanciera, String fondoGarantias, String asistencia, String seguro, String cuotaTotal,
            String saldo) {
    }

    // ---------------------------------------------------------------- Ejecución de escenarios

    public record EjecucionResponse(ResumenEjecucion resumen, List<MovimientoDto> movimientos,
            List<AsientoDto> asientos) {
    }

    public record ResumenEjecucion(int periodoFinal, String estadoFinal, String saldoFinal, String totalPagadoCliente,
            int cuotasVencidasMax, String montoFinanciado, String perfilReglas) {
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
            DeudaDto pendientesPagadosExtra, String interesPrepago, String pagoTotalCliente,
            String pagoExtraordinario) {
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
