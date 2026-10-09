import datos from './datos/escenarios.json'

// Resultados del motor real (Java) para cada opción, generados con `npm run escenarios`.
// El front no calcula nada: solo muestra lo que devolvió el motor.

export interface Kpis {
  cuotaInicial: number
  montoFinanciado: number
  cuotaSemanal: number
  semanaFinal: number
  estadoFinal: string
  saldoPendiente: number
  totalPagado: number
  interesPagado: number
  moraPagada: number
  cobranzaPagada: number
  serviciosPagados: number
  capitalVencidoMaximo: number
  semanasEnMora: number
  deudaAExtinguir: number
  faltanteFcc: number
  excedenteDeudor: number
}

export interface Semana {
  periodo: number
  pagoRecibido: number
  cobranza: number
  mora: number
  interes: number
  servicios: number
  capital: number
  interesCausado: number
  moraCausada: number
  serviciosCausados: number
  capitalVencido: number
  saldoFinal: number
}

export interface ResultadoOpcion {
  kpis: Kpis
  semanas: Semana[]
  serieSaldo: number[]
  serieVencido: number[]
}

export interface Previsualizacion {
  caso: string
  semanasClave: number[]
  opciones: Record<string, ResultadoOpcion>
}

interface ArchivoEscenarios {
  generado: string
  preguntas: Record<string, Previsualizacion>
}

const archivo = datos as ArchivoEscenarios

export const FECHA_ESCENARIOS = archivo.generado

export function previsualizacion(id: string | undefined): Previsualizacion | undefined {
  return id ? archivo.preguntas[id] : undefined
}

export const ESTADOS: Record<string, string> = {
  CANCELADO: 'Pagado por completo',
  EN_MORA: 'Sigue en mora',
  CERRADO_POR_EVENTO: 'Cerrado con la moto',
  VIGENTE_AL_DIA: 'Al día',
}
