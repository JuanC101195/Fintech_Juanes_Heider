import { esDecimal, aMicros, comparar, cop, porcentaje, decimalEs } from '../dinero'
import type { Catalogo, Instruccion, TipoInstruccion } from '../tipos'

export type TipoCampo = 'dinero' | 'entero' | 'fraccion' | 'tasa' | 'booleano' | 'opcion'

export interface Campo {
  nombre: keyof Instruccion
  etiqueta: string
  ayuda?: string
  tipo: TipoCampo
  obligatorio: boolean
  /** Para `opcion`: clave del catálogo de donde salen las opciones. */
  opciones?: 'modalidadesAbono' | 'modalidadesCambioTasa' | 'tiposRecuperacion'
  defecto?: string | boolean
}

export const ETIQUETAS_TIPO: Record<TipoInstruccion, string> = {
  PAGO_CONTRACTUAL: 'Pago de la cuota',
  PAGO_PARCIAL: 'Pago parcial (fracción de la cuota)',
  PAGO_VALOR: 'Pago de un valor exacto',
  SIN_PAGO: 'Sin pago',
  ABONO_EXTRA: 'Abono extraordinario',
  PREPAGO_TOTAL: 'Prepago total',
  USURA_VIGENTE: 'Nueva tasa de usura',
  CAMBIO_TASA: 'Cambio de tasa',
  PAGO_AL_DIA: 'Pago al día (normalización)',
  CIERRE_POR_RECUPERACION: 'Cierre por recuperación de la moto',
}

export const ETIQUETAS_VALOR: Record<string, string> = {
  MENOR_PLAZO: 'Menor plazo',
  MENOR_CUOTA: 'Menor cuota',
  MISMA_FECHA_FINAL: 'Misma fecha final',
  MISMA_CUOTA: 'Misma cuota',
  DACION_EN_PAGO: 'Dación en pago',
  RETOMA: 'Retoma',
}

/** Campos de cada tipo: son los de su record en `Instruccion` (Java), según el contrato. */
export const CAMPOS_POR_TIPO: Record<TipoInstruccion, Campo[]> = {
  PAGO_CONTRACTUAL: [{ nombre: 'diasAtraso', etiqueta: 'Días de atraso', tipo: 'entero', obligatorio: false, defecto: '0' }],
  PAGO_PARCIAL: [
    { nombre: 'fraccion', etiqueta: 'Fracción de la cuota', ayuda: 'Entre 0 y 1; por ejemplo 0.4 es el 40 %', tipo: 'fraccion', obligatorio: true },
    { nombre: 'diasAtraso', etiqueta: 'Días de atraso', tipo: 'entero', obligatorio: false, defecto: '0' },
  ],
  PAGO_VALOR: [
    { nombre: 'valor', etiqueta: 'Valor pagado (COP)', tipo: 'dinero', obligatorio: true },
    { nombre: 'diasAtraso', etiqueta: 'Días de atraso', tipo: 'entero', obligatorio: false, defecto: '0' },
  ],
  SIN_PAGO: [{ nombre: 'causaMoraYServicios', etiqueta: 'Causar mora y servicios', ayuda: 'Si no, solo se acumula el interés corriente', tipo: 'booleano', obligatorio: false, defecto: true }],
  ABONO_EXTRA: [
    { nombre: 'valor', etiqueta: 'Valor del abono (COP)', tipo: 'dinero', obligatorio: true },
    { nombre: 'modalidad', etiqueta: 'Modalidad', tipo: 'opcion', opciones: 'modalidadesAbono', obligatorio: true, defecto: 'MENOR_PLAZO' },
  ],
  PREPAGO_TOTAL: [{ nombre: 'diasDesdeUltimaCuota', etiqueta: 'Días desde la última cuota', tipo: 'entero', obligatorio: false, defecto: '0' }],
  USURA_VIGENTE: [{ nombre: 'usuraEA', etiqueta: 'Usura EA (fracción)', ayuda: 'Por ejemplo 0.81 es 81 % EA', tipo: 'tasa', obligatorio: true }],
  CAMBIO_TASA: [
    { nombre: 'nuevaTasaEA', etiqueta: 'Nueva tasa EA (fracción)', ayuda: 'Por ejemplo 0.7 es 70 % EA', tipo: 'tasa', obligatorio: true },
    { nombre: 'modalidad', etiqueta: 'Qué se conserva', tipo: 'opcion', opciones: 'modalidadesCambioTasa', obligatorio: true, defecto: 'MISMA_FECHA_FINAL' },
  ],
  PAGO_AL_DIA: [],
  CIERRE_POR_RECUPERACION: [
    { nombre: 'tipoRecuperacion', etiqueta: 'Tipo de recuperación', tipo: 'opcion', opciones: 'tiposRecuperacion', obligatorio: true, defecto: 'DACION_EN_PAGO' },
    { nombre: 'valorMoto', etiqueta: 'Valor de la moto (COP)', tipo: 'dinero', obligatorio: true },
  ],
}

export type ValoresFormulario = Record<string, string | boolean>

export function valoresPorDefecto(tipo: TipoInstruccion): ValoresFormulario {
  const v: ValoresFormulario = {}
  for (const c of CAMPOS_POR_TIPO[tipo]) v[c.nombre] = c.defecto ?? (c.tipo === 'booleano' ? false : '')
  return v
}

export type ErroresFormulario = Record<string, string>

const entero = /^\d+$/

/** Valida el formulario de un tipo; devuelve los errores por campo (vacío si está bien). */
export function validarFormulario(tipo: TipoInstruccion, periodo: number, valores: ValoresFormulario): ErroresFormulario {
  const errores: ErroresFormulario = {}
  if (!Number.isInteger(periodo) || periodo < 1) errores.periodo = 'El periodo debe ser un entero desde 1'
  for (const c of CAMPOS_POR_TIPO[tipo]) {
    const crudo = valores[c.nombre]
    const texto = typeof crudo === 'string' ? crudo.trim() : ''
    if (c.tipo === 'booleano') continue
    if (texto === '') {
      if (c.obligatorio) errores[c.nombre] = 'Obligatorio'
      continue
    }
    if (c.tipo === 'entero' && !entero.test(texto)) errores[c.nombre] = 'Debe ser un entero sin signo'
    else if (c.tipo === 'dinero' && (!esDecimal(texto) || aMicros(texto) <= 0n)) errores[c.nombre] = 'Debe ser un valor positivo, por ejemplo 1500000'
    else if (c.tipo === 'fraccion' && (!esDecimal(texto) || aMicros(texto) <= 0n || comparar(texto, '1') > 0)) errores[c.nombre] = 'Debe estar entre 0 y 1'
    else if (c.tipo === 'tasa' && (!esDecimal(texto) || aMicros(texto) <= 0n || comparar(texto, '10') >= 0)) errores[c.nombre] = 'Debe ser una fracción positiva, por ejemplo 0.81'
  }
  return errores
}

/** Formulario → instrucción del API. Enteros como number, dinero/tasas como string, lo vacío se omite. */
export function aInstruccion(tipo: TipoInstruccion, periodo: number, valores: ValoresFormulario): Instruccion {
  const resultado: Instruccion = { periodo, tipo }
  const destino = resultado as unknown as Record<string, unknown>
  for (const c of CAMPOS_POR_TIPO[tipo]) {
    const crudo = valores[c.nombre]
    if (c.tipo === 'booleano') destino[c.nombre] = crudo === true
    else {
      const texto = typeof crudo === 'string' ? crudo.trim() : ''
      if (texto === '') continue
      destino[c.nombre] = c.tipo === 'entero' ? Number(texto) : texto
    }
  }
  return resultado
}

/** Instrucción del API → valores del formulario (para editar las instrucciones de un caso). */
export function aFormulario(instruccion: Instruccion): ValoresFormulario {
  const base = valoresPorDefecto(instruccion.tipo)
  const origen = instruccion as unknown as Record<string, unknown>
  for (const c of CAMPOS_POR_TIPO[instruccion.tipo]) {
    const v = origen[c.nombre]
    if (v === undefined || v === null) continue
    base[c.nombre] = c.tipo === 'booleano' ? v === true : String(v)
  }
  return base
}

/** Ordena por periodo (estable) y deja una sola instrucción por periodo y tipo. */
export function ordenarInstrucciones(lista: Instruccion[]): Instruccion[] {
  return [...lista].sort((a, b) => a.periodo - b.periodo)
}

/** Frase corta en español de una instrucción, para la lista del laboratorio y la línea de tiempo. */
export function describir(i: Instruccion): string {
  const etiqueta = ETIQUETAS_TIPO[i.tipo] ?? i.tipo
  const o = (v?: string) => (v ? (ETIQUETAS_VALOR[v] ?? v) : '')
  switch (i.tipo) {
    case 'PAGO_VALOR':
      return `${etiqueta}: ${cop(i.valor)}${i.diasAtraso ? `, ${i.diasAtraso} días de atraso` : ''}`
    case 'PAGO_PARCIAL':
      return `${etiqueta}: ${porcentaje(i.fraccion, 0)}${i.diasAtraso ? `, ${i.diasAtraso} días de atraso` : ''}`
    case 'PAGO_CONTRACTUAL':
      return i.diasAtraso ? `${etiqueta} con ${i.diasAtraso} días de atraso` : etiqueta
    case 'SIN_PAGO':
      return i.causaMoraYServicios ? `${etiqueta} (causa mora y servicios)` : `${etiqueta} (solo interés corriente)`
    case 'ABONO_EXTRA':
      return `${etiqueta} de ${cop(i.valor)} · ${o(i.modalidad)}`
    case 'PREPAGO_TOTAL':
      return i.diasDesdeUltimaCuota ? `${etiqueta} a ${i.diasDesdeUltimaCuota} días de la última cuota` : etiqueta
    case 'USURA_VIGENTE':
      return `${etiqueta}: ${porcentaje(i.usuraEA)} EA`
    case 'CAMBIO_TASA':
      return `${etiqueta} a ${porcentaje(i.nuevaTasaEA)} EA · ${o(i.modalidad)}`
    case 'CIERRE_POR_RECUPERACION':
      return `${o(i.tipoRecuperacion) || etiqueta} por ${cop(i.valorMoto)}`
    default:
      return etiqueta
  }
}

/** Categoría de la línea de tiempo: agrupa tipos y da un color/ícono coherente. */
export type CategoriaEvento = 'sin-pago' | 'abono' | 'tasa' | 'dacion' | 'pago'

export function categoria(tipo: TipoInstruccion): CategoriaEvento {
  switch (tipo) {
    case 'SIN_PAGO':
      return 'sin-pago'
    case 'ABONO_EXTRA':
    case 'PREPAGO_TOTAL':
      return 'abono'
    case 'CAMBIO_TASA':
    case 'USURA_VIGENTE':
      return 'tasa'
    case 'CIERRE_POR_RECUPERACION':
      return 'dacion'
    default:
      return 'pago'
  }
}

/** Crédito por defecto del laboratorio cuando se parte de uno nuevo. */
export function opcionesDe(catalogo: Catalogo | null, clave: NonNullable<Campo['opciones']>): string[] {
  return catalogo?.[clave] ?? []
}

export { decimalEs }
