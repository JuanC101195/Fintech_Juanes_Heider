// Fechas opcionales del crédito (P5-PRORRATEO, D48): fecha de desembolso y primer vencimiento en ISO yyyy-MM-dd.
// Las mismas reglas que valida el API (ServicioCartera.fechas): van juntas, el vencimiento es posterior al desembolso y
// cae martes en semanal (D28), el 5 o el 20 en quincenal (D27) y el 5 en mensual (D35). Nunca se usa la fecha de hoy.
import type { Credito, Periodicidad } from './tipos'

export interface Fechas {
  fechaDesembolso: string
  primerVencimiento: string
}

export type ErroresFechas = Partial<Record<keyof Fechas, string>>

const DIAS_SEMANA = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado']
const MS_DIA = 86_400_000

/** La fecha ISO como milisegundos UTC, o null si no es una fecha real con formato yyyy-MM-dd. */
export function leerFecha(texto: string): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(texto.trim())
  if (!m) return null
  const [a, mes, d] = [Number(m[1]), Number(m[2]), Number(m[3])]
  const ms = Date.UTC(a, mes - 1, d)
  const f = new Date(ms)
  return f.getUTCFullYear() === a && f.getUTCMonth() === mes - 1 && f.getUTCDate() === d ? ms : null
}

/** Días entre dos fechas ISO válidas (fin − inicio). */
export function diasEntre(inicio: string, fin: string): number {
  return Math.round(((leerFecha(fin) ?? 0) - (leerFecha(inicio) ?? 0)) / MS_DIA)
}

/** Ayuda corta de qué día vence la cuota según la periodicidad. */
export function reglaVencimiento(p: Periodicidad): string {
  if (p === 'SEMANAL') return 'Debe ser martes'
  if (p === 'QUINCENAL') return 'Debe ser un 5 o un 20'
  return 'Debe ser un día 5'
}

/** Valida las dos fechas como el API. Vacías las dos = sin fechas (válido). */
export function validarFechas(periodicidad: Periodicidad, f: Fechas): ErroresFechas {
  const desembolso = f.fechaDesembolso.trim()
  const vencimiento = f.primerVencimiento.trim()
  if (!desembolso && !vencimiento) return {}
  if (!desembolso) return { fechaDesembolso: 'Falta la fecha de desembolso (o borra también el primer vencimiento)' }
  if (!vencimiento) return { primerVencimiento: 'Falta el primer vencimiento (o borra también la fecha de desembolso)' }
  const d = leerFecha(desembolso)
  const v = leerFecha(vencimiento)
  const e: ErroresFechas = {}
  if (d === null) e.fechaDesembolso = 'Escribe una fecha válida'
  if (v === null) e.primerVencimiento = 'Escribe una fecha válida'
  if (d === null || v === null) return e
  if (v <= d) return { primerVencimiento: 'Debe ser posterior a la fecha de desembolso' }
  const fecha = new Date(v)
  if (periodicidad === 'SEMANAL' && fecha.getUTCDay() !== 2) return { primerVencimiento: `La cuota semanal vence los martes; esa fecha es ${DIAS_SEMANA[fecha.getUTCDay()]}` }
  if (periodicidad === 'QUINCENAL' && fecha.getUTCDate() !== 5 && fecha.getUTCDate() !== 20) return { primerVencimiento: 'La cuota quincenal vence el 5 y el 20 de cada mes' }
  if (periodicidad === 'MENSUAL' && fecha.getUTCDate() !== 5) return { primerVencimiento: 'La cuota mensual vence el día 5 de cada mes' }
  return {}
}

/** Las fechas para el request: solo si vienen las dos; si no, ninguna (el API responde como siempre). */
export function fechasDelCredito(f: Fechas): Pick<Credito, 'fechaDesembolso' | 'primerVencimiento'> {
  const fechaDesembolso = f.fechaDesembolso.trim()
  const primerVencimiento = f.primerVencimiento.trim()
  return fechaDesembolso && primerVencimiento ? { fechaDesembolso, primerVencimiento } : {}
}

/** "5 de diciembre de 2026" sin depender de la zona horaria del navegador. */
export function fechaLarga(iso: string): string {
  const ms = leerFecha(iso)
  if (ms === null) return iso
  return new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(ms))
}
