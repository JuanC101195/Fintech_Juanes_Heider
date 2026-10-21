// Tipos del contrato del API (contrato-api-cartera.md, v1). El dinero y las tasas son strings decimales.

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
  tiposRecuperacion: string[]
  valoresPorDefecto: Record<string, string>
}

export interface ClaveRegla {
  clave: string
  descripcion: string
  fuente: string
}

export interface Reglas {
  perfiles: Record<Perfil, Record<string, string>>
  claves: ClaveRegla[]
}

export interface Credito {
  linea: string
  periodicidad: Periodicidad
  cuotas: number | null
  meses: number | null
  valorMoto: string
  pagoInicial: string
  perfilReglas: Perfil
}

export interface ResumenSimulacion {
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
}

export interface FilaPlan {
  periodo: number
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

/** Campos de una instrucción: los del record de `Instruccion`, en camelCase; el resto es opcional por tipo. */
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
}

export interface PeticionEjecucion {
  credito: Credito
  perfilReglas: Perfil
  instrucciones: Instruccion[]
  incluirAsientos: boolean
}

export interface ServiciosPorConcepto {
  fondoGarantias?: string
  asistencia?: string
  seguro?: string
}

/** Movimiento: todos sus campos viajan como string; aquí van los que la consola muestra, el resto se conserva. */
export interface Movimiento {
  periodo: number
  estado: string
  etiqueta?: string
  evento?: string
  mes?: number
  saldoInicial?: string
  cuotaFinanciera?: string
  interesCorriente?: string
  interesMora?: string
  capitalContractual?: string
  cobranzaCausada?: string
  pagoRecibido?: string
  pagoCobranza?: string
  pagoMora?: string
  pagoInteresCorriente?: string
  pagoServicios?: string
  pagoCapital?: string
  abonoExtra?: string
  prepagoCapital?: string
  cxcCobranza?: string
  cxcMora?: string
  cxcInteresCorriente?: string
  cxcServicios?: string
  capitalVencido?: string
  capitalVigente?: string
  saldoFinal?: string
  cuotasVencidas?: number | string
  pendientesPagadosExtra?: Record<string, string> | string
  [campo: string]: unknown
}

export interface LineaAsiento {
  cuenta: string
  debito: string
  credito: string
}

export interface Asiento {
  periodo: number
  evento: string
  lineas: LineaAsiento[]
  cuadra: boolean
}

export interface ResumenEjecucion {
  periodoFinal: number
  estadoFinal: string
  saldoFinal: string
  totalPagadoCliente: string
  cuotasVencidasMax: number
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
  /** Supuesto: el API puede mandar el crédito del caso; si no, el laboratorio usa uno por defecto. */
  credito?: Credito
}

export interface ErrorApi {
  codigo: string
  mensaje: string
  detalle?: Record<string, unknown>
}
