// Tipos del API real (docs/api.md, v1), verificados contra docs/api-ejemplos/*.json en contrato.test.ts.
// El dinero y las tasas son strings decimales SIN redondear (hasta 34 decimales); se redondea solo al mostrar.

export type Perfil = 'EXCEL' | 'NEGOCIO'
export type Periodicidad = 'SEMANAL' | 'QUINCENAL' | 'MENSUAL'

export interface Linea {
  codigo: string
  nombre: string
  tasaEA: string
  plazoMaximoMeses: number
  periodicidades: Periodicidad[]
}

export interface InfoPeriodicidad {
  codigo: Periodicidad
  periodosPorAnio: number
  adjetivo: string
  porConfirmar: boolean
  diasNominales: number
  unidadCuota: string
  cuotasMaximas: number
}

export type TipoInstruccion =
  | 'PAGO_CONTRACTUAL'
  | 'PAGO_PARCIAL'
  | 'PAGO_VALOR'
  | 'SIN_PAGO'
  | 'ABONO_EXTRA'
  | 'PREPAGO_TOTAL'
  | 'USURA_VIGENTE'
  | 'CAMBIO_TASA'
  | 'PAGO_AL_DIA'
  | 'CIERRE_POR_RECUPERACION'

export interface Catalogo {
  lineas: Linea[]
  periodicidades: InfoPeriodicidad[]
  tiposInstruccion: TipoInstruccion[]
  modalidadesAbono: string[]
  modalidadesCambioTasa: string[]
  /** Son los nombres reales de `Instruccion.TipoRecuperacion`: DACION_EN_PAGO y RETOMA. */
  tiposRecuperacion: string[]
  perfilesReglas: Perfil[]
  valoresPorDefecto: Record<string, string>
}

export interface ClaveRegla {
  clave: string
  descripcion: string
  fuente: string
}

export interface Reglas {
  /** Valor de cada clave por perfil; `topeCobranzaPorPago` es null cuando no hay tope. */
  perfiles: Record<Perfil, Record<string, string | null>>
  claves: ClaveRegla[]
  archivoNegocio?: string
}

export interface Credito {
  linea: string
  periodicidad: Periodicidad
  cuotas: number | null
  meses: number | null
  valorMoto: string
  pagoInicial: string
  perfilReglas: Perfil
  matriculaRunt?: string
  soat?: string
  prenda?: string
  gpsPlataforma?: string
  gastosComerciales?: string
  /** Horizonte de proyección con saldo pendiente (casos 8 y 9: 100 y 120). */
  plazoLimitePeriodos?: number | null
  /** Si al crédito se le cobran gastos de cobranza (los activa un responsable, D44). */
  cobranzaActiva?: boolean
  /** Fecha de desembolso ISO yyyy-MM-dd (opcional, va junto con `primerVencimiento`; P5-PRORRATEO). */
  fechaDesembolso?: string
  /** Primera fecha de pago ISO yyyy-MM-dd: martes en semanal, 5 o 20 en quincenal, 5 en mensual. */
  primerVencimiento?: string
}

/**
 * Prorrateo de la primera cuota (P5-PRORRATEO, D48). Solo llega si el crédito trae las dos fechas: el interés del
 * periodo 1 se cobra por los días reales; capital y servicios van completos.
 */
export interface ProrrateoPrimeraCuota {
  fechaDesembolso: string
  primerVencimiento: string
  diasPrimerPeriodo: number
  /** 7, 15 o 30, como referencia. */
  diasNominales: number
  /** `false` con el perfil EXCEL: las fechas no cambian el interés. */
  aplicado: boolean
  /** Interés de un periodo completo (saldo × tasa del periodo). */
  interesSinProrrateo: string
  /** Interés que cobró el motor en el periodo 1. */
  interesProrrateado: string
  /** interesProrrateado − interesSinProrrateo. */
  diferencia: string
}

export interface ResumenSimulacion {
  linea?: string
  perfilReglas?: Perfil
  serviciosPorCuota?: string
  montoFinanciado: string
  tasaEA: string
  tasaPeriodo: string
  cuotas: number
  cuotaFinanciera: string
  cuotaTotal: string
  totalPagado: string
  totalIntereses: string
  totalServicios: string
  periodicidad: Periodicidad
  porConfirmar: string[]
  /** Solo con fechas. Entonces `cuotaFinanciera` y `cuotaTotal` son los de una cuota regular (periodo 2). */
  prorrateoPrimeraCuota?: ProrrateoPrimeraCuota
}

export interface FilaPlan {
  periodo: number
  mes?: number
  saldoInicial?: string
  cuotaFinanciera?: string
  interes: string
  capital: string
  fondoGarantias: string
  asistencia: string
  seguro: string
  cuotaTotal: string
  saldo: string
}

export interface Simulacion {
  resumen: ResumenSimulacion
  plan: FilaPlan[]
}

/**
 * Campos de una instrucción: los del record de `Instruccion`, en camelCase; el resto es opcional por tipo.
 * `modalidad` es un solo campo compartido por ABONO_EXTRA (MENOR_PLAZO/MENOR_CUOTA) y CAMBIO_TASA (MISMA_FECHA_FINAL/MISMA_CUOTA).
 * `tipoRecuperacion` (DACION_EN_PAGO/RETOMA) es el de CIERRE_POR_RECUPERACION.
 */
export interface Instruccion {
  periodo: number
  tipo: TipoInstruccion
  valor?: string
  fraccion?: string
  diasAtraso?: number
  causaMoraYServicios?: boolean
  modalidad?: string
  diasDesdeUltimaCuota?: number
  usuraEA?: string
  nuevaTasaEA?: string
  tipoRecuperacion?: string
  valorMoto?: string
  costoRecuperacion?: string
}

export interface PeticionEjecucion {
  credito: Credito
  perfilReglas: Perfil
  instrucciones: Instruccion[]
  incluirAsientos: boolean
}

/** Servicios por concepto; `total` es la suma que calcula el API. */
export interface Servicios {
  fondoGarantias: string
  asistencia: string
  seguro: string
  total: string
}

/** Lo que un pago extraordinario cancela de la deuda pendiente (cobranza, mora, interés, servicios y capital). */
export interface DeudaExtinguida {
  cobranza: string
  mora: string
  interesCorriente: string
  servicios: Servicios
  capital: string
  total: string
}

export type EstadoCredito = 'DESEMBOLSADO' | 'VIGENTE_AL_DIA' | 'EN_MORA' | 'CANCELADO' | 'CERRADO_POR_EVENTO'

/** Movimiento del motor: todos los campos de dinero son strings decimales sin redondear. */
export interface Movimiento {
  periodo: number
  mes: number
  estado: EstadoCredito | (string & {})
  /** Nombre del evento del motor ("Pago normal", "Cuota + abono extra"…); `etiqueta` trae el mismo texto. */
  evento: string
  etiqueta: string
  saldoInicial: string
  tasaPeriodica: string
  cuotaFinanciera: string
  interesCorriente: string
  interesMora: string
  capitalContractual: string
  cobranzaCausada: string
  serviciosCausados: Servicios
  pagoRecibido: string
  pagoCobranza: string
  pagoMora: string
  pagoInteresCorriente: string
  pagoServicios: string
  pagoCapital: string
  abonoExtra: string
  prepagoCapital: string
  cxcCobranza: string
  cxcMora: string
  cxcInteresCorriente: string
  cxcServicios: string
  capitalVencido: string
  capitalVigente: string
  saldoFinal: string
  cuotasVencidas: number
  deudaAExtinguir: string
  inventario: string
  cxcFondoGarantias: string
  cxpDeudor: string
  pagoTerceros: Servicios
  pagosServicios: Servicios
  cxcServiciosPorConcepto: Servicios
  extinguido: DeudaExtinguida
  capitalVencidoInicial: string
  /** Pendientes (cobranza, mora, interés, servicios, capital) que un pago extra cubrió antes de abonar a capital. Siempre es un objeto. */
  pendientesPagadosExtra: DeudaExtinguida
  interesPrepago: string
  /** Todo lo que pagó el cliente en el periodo (cuota + extraordinario). */
  pagoTotalCliente: string
  pagoExtraordinario: string
  /** Costo real de la recuperación (grúa, bodegaje, avalúo, trámites) descontado en la dación o la retoma. */
  costoRecuperacion: string
  /** Parte de ese costo que asume la financiera (0 si se suma al faltante que cubre el fondo). */
  costoRecuperacionAsumido: string
  /** El crédito llegó al umbral de cuotas vencidas para dación o retoma (D46); el motor no la ejecuta solo. */
  listoParaRecuperacion: boolean
  /** R8-PAGO-MAYOR-EXIGIBLE: sobrante por devolver al cliente; solo viene cuando lo hay (nunca con EXCEL). */
  sobranteCliente?: string
}

export interface LineaAsiento {
  cuenta: string
  nombreCuenta: string
  debito: string
  credito: string
  /** Por qué se mueve la cuenta, en español ("Aplicación 100% a capital"). */
  explicacion: string
}

export interface Asiento {
  periodo: number
  evento: string
  lineas: LineaAsiento[]
  totalDebitos: string
  totalCreditos: string
  /** Calculado por el API al centavo. */
  cuadra: boolean
}

export interface ResumenEjecucion {
  periodoFinal: number
  estadoFinal: string
  saldoFinal: string
  totalPagadoCliente: string
  cuotasVencidasMax: number
  montoFinanciado: string
  perfilReglas: Perfil
  /** Si en algún periodo llegó al umbral de recuperación (D46) y desde cuál. */
  listoParaRecuperacion: boolean
  periodoListoParaRecuperacion: number | null
  /** Solo si el crédito trae fechas (P5-PRORRATEO). */
  prorrateoPrimeraCuota?: ProrrateoPrimeraCuota
}

export interface Ejecucion {
  resumen: ResumenEjecucion
  movimientos: Movimiento[]
  asientos?: Asiento[]
}

export interface CasoResumen {
  numero: number
  nombre: string
  descripcion: string
  fuente: string
}

export interface CasoDetalle extends Ejecucion {
  caso: CasoResumen
  instrucciones: Instruccion[]
  perfilReglas: Perfil
  /** Crédito del caso en formato de request (con `plazoLimitePeriodos`); el laboratorio lo reejecuta tal cual. */
  credito: Credito
}

export interface ErrorApi {
  codigo: string
  mensaje: string
  detalle?: Record<string, unknown>
}
