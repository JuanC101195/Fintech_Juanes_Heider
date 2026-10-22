import { esDecimal, aMicros, comparar, cop, porcentaje, decimalEs } from '../dinero'
import type { Catalogo, Instruccion, Periodicidad, TipoInstruccion } from '../tipos'

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

/**
 * Cada tipo de instrucción del API dicho en lenguaje de negocio: nombre corto, qué hace y un ícono. El orden es el
 * de las tarjetas del laboratorio (primero lo que más se prueba).
 */
export const INFO_TIPO: Record<TipoInstruccion, { nombre: string; frase: string; icono: string }> = {
  SIN_PAGO: { nombre: 'Sin pago', frase: 'El cliente no paga ese periodo', icono: '⛔' },
  ABONO_EXTRA: { nombre: 'Abono extra', frase: 'Paga de más para bajar plazo o cuota', icono: '➕' },
  CAMBIO_TASA: { nombre: 'Cambio de tasa', frase: 'La tasa del crédito cambia desde ese periodo', icono: '📈' },
  USURA_VIGENTE: { nombre: 'Usura vigente', frase: 'Cambia la tasa máxima legal; si el crédito la supera, se baja', icono: '⚖️' },
  CIERRE_POR_RECUPERACION: { nombre: 'Dación o retoma', frase: 'Se recibe la moto y se cierra el crédito', icono: '🏍️' },
  PAGO_PARCIAL: { nombre: 'Pago parcial', frase: 'Paga solo una parte de la cuota', icono: '◐' },
  PAGO_VALOR: { nombre: 'Pago de un valor', frase: 'Paga una suma exacta, más o menos que la cuota', icono: '💵' },
  PAGO_CONTRACTUAL: { nombre: 'Pago de la cuota', frase: 'Paga la cuota pactada, a tiempo o con días de atraso', icono: '✅' },
  PAGO_AL_DIA: { nombre: 'Se pone al día', frase: 'Paga la cuota y todo lo atrasado', icono: '🔄' },
  PREPAGO_TOTAL: { nombre: 'Paga todo', frase: 'Cancela el saldo completo del crédito', icono: '🏁' },
}

export const ORDEN_TIPOS = Object.keys(INFO_TIPO) as TipoInstruccion[]

export const ETIQUETAS_TIPO: Record<TipoInstruccion, string> = Object.fromEntries(ORDEN_TIPOS.map((t) => [t, INFO_TIPO[t].nombre])) as Record<TipoInstruccion, string>

/** Nombre corto de cada opción (va en las descripciones) y una frase de qué significa (va en los botones). */
export const ETIQUETAS_VALOR: Record<string, string> = {
  MENOR_PLAZO: 'Menor plazo',
  MENOR_CUOTA: 'Menor cuota',
  MISMA_FECHA_FINAL: 'Misma fecha final',
  MISMA_CUOTA: 'Misma cuota',
  DACION_EN_PAGO: 'Dación en pago',
  RETOMA: 'Retoma',
}

export const NOTAS_VALOR: Record<string, string> = {
  MENOR_PLAZO: 'Misma cuota, termina antes',
  MENOR_CUOTA: 'Mismo plazo, cuota más baja',
  MISMA_FECHA_FINAL: 'Termina el mismo día; cambia la cuota',
  MISMA_CUOTA: 'Misma cuota; cambia el número de cuotas',
  DACION_EN_PAGO: 'El cliente entrega la moto',
  RETOMA: 'La empresa recoge la moto',
}

/** Campos de cada tipo: son los de su record en `Instruccion` (Java), según el contrato. */
export const CAMPOS_POR_TIPO: Record<TipoInstruccion, Campo[]> = {
  PAGO_CONTRACTUAL: [{ nombre: 'diasAtraso', etiqueta: 'Días de atraso', ayuda: '0 si paga a tiempo', tipo: 'entero', obligatorio: false, defecto: '0' }],
  PAGO_PARCIAL: [
    { nombre: 'fraccion', etiqueta: 'Qué parte de la cuota paga', ayuda: 'Por ejemplo 40 % de la cuota', tipo: 'fraccion', obligatorio: true },
    { nombre: 'diasAtraso', etiqueta: 'Días de atraso', ayuda: '0 si paga a tiempo', tipo: 'entero', obligatorio: false, defecto: '0' },
  ],
  PAGO_VALOR: [
    { nombre: 'valor', etiqueta: 'Valor que paga', tipo: 'dinero', obligatorio: true },
    { nombre: 'diasAtraso', etiqueta: 'Días de atraso', ayuda: '0 si paga a tiempo', tipo: 'entero', obligatorio: false, defecto: '0' },
  ],
  SIN_PAGO: [{ nombre: 'causaMoraYServicios', etiqueta: 'Cobrar mora y servicios por ese periodo', ayuda: 'Si lo desmarcas, solo se acumula el interés corriente', tipo: 'booleano', obligatorio: false, defecto: true }],
  ABONO_EXTRA: [
    { nombre: 'valor', etiqueta: 'Valor del abono', tipo: 'dinero', obligatorio: true },
    { nombre: 'modalidad', etiqueta: '¿Qué baja con el abono?', tipo: 'opcion', opciones: 'modalidadesAbono', obligatorio: true, defecto: 'MENOR_PLAZO' },
  ],
  PREPAGO_TOTAL: [{ nombre: 'diasDesdeUltimaCuota', etiqueta: 'Días desde la última cuota pagada', ayuda: 'Para cobrar el interés de esos días', tipo: 'entero', obligatorio: false, defecto: '0' }],
  USURA_VIGENTE: [{ nombre: 'usuraEA', etiqueta: 'Nueva tasa de usura (efectiva anual)', ayuda: 'La tasa máxima legal, por ejemplo 81 %', tipo: 'tasa', obligatorio: true }],
  CAMBIO_TASA: [
    { nombre: 'nuevaTasaEA', etiqueta: 'Nueva tasa (efectiva anual)', ayuda: 'Por ejemplo 80 %', tipo: 'tasa', obligatorio: true },
    { nombre: 'modalidad', etiqueta: '¿Qué se conserva?', tipo: 'opcion', opciones: 'modalidadesCambioTasa', obligatorio: true, defecto: 'MISMA_FECHA_FINAL' },
  ],
  PAGO_AL_DIA: [],
  CIERRE_POR_RECUPERACION: [
    { nombre: 'tipoRecuperacion', etiqueta: '¿Cómo se recibe la moto?', tipo: 'opcion', opciones: 'tiposRecuperacion', obligatorio: true, defecto: 'DACION_EN_PAGO' },
    { nombre: 'valorMoto', etiqueta: 'Valor en que se recibe la moto', tipo: 'dinero', obligatorio: true },
    { nombre: 'costoRecuperacion', etiqueta: 'Costo de la recuperación', ayuda: 'Costo real de la recuperación (grúa, bodegaje, avalúo, trámites), con soporte', tipo: 'dinero', obligatorio: false },
  ],
}

/** Tipos que tiene sentido repetir en periodos seguidos ("no paga 2 semanas"). */
export const TIPOS_REPETIBLES: TipoInstruccion[] = ['SIN_PAGO', 'PAGO_PARCIAL', 'PAGO_VALOR', 'PAGO_CONTRACTUAL']

export type ValoresFormulario = Record<string, string | boolean>

export function valoresPorDefecto(tipo: TipoInstruccion): ValoresFormulario {
  const v: ValoresFormulario = {}
  for (const c of CAMPOS_POR_TIPO[tipo]) v[c.nombre] = c.defecto ?? (c.tipo === 'booleano' ? false : '')
  return v
}

/** Nombre del periodo según la periodicidad: semana, quincena o mes. */
export function unidadPeriodo(p: Periodicidad): { una: string; varias: string } {
  if (p === 'QUINCENAL') return { una: 'quincena', varias: 'quincenas' }
  if (p === 'MENSUAL') return { una: 'mes', varias: 'meses' }
  return { una: 'semana', varias: 'semanas' }
}

export interface Atajo {
  id: string
  texto: string
  icono: string
  tipo: TipoInstruccion
  valores: ValoresFormulario
  /** Periodos seguidos que crea. */
  veces: number
}

/** Instrucciones típicas a un clic: llenan el formulario y la persona solo ajusta el periodo y el valor. */
export function atajos(p: Periodicidad): Atajo[] {
  const u = unidadPeriodo(p)
  return [
    { id: 'no-paga-2', texto: `El cliente no paga 2 ${u.varias}`, icono: INFO_TIPO.SIN_PAGO.icono, tipo: 'SIN_PAGO', valores: { causaMoraYServicios: true }, veces: 2 },
    { id: 'abono-500', texto: 'Abono extra de $ 500.000 a menor plazo', icono: INFO_TIPO.ABONO_EXTRA.icono, tipo: 'ABONO_EXTRA', valores: { valor: '500000', modalidad: 'MENOR_PLAZO' }, veces: 1 },
    { id: 'usura-81', texto: 'Baja la usura al 81 %', icono: INFO_TIPO.USURA_VIGENTE.icono, tipo: 'USURA_VIGENTE', valores: { usuraEA: '0.81' }, veces: 1 },
    { id: 'dacion', texto: 'Dación de la moto', icono: INFO_TIPO.CIERRE_POR_RECUPERACION.icono, tipo: 'CIERRE_POR_RECUPERACION', valores: { tipoRecuperacion: 'DACION_EN_PAGO', valorMoto: '3000000' }, veces: 1 },
  ]
}

export type ErroresFormulario = Record<string, string>

const entero = /^\d+$/

export const OBLIGATORIO = 'Este dato es obligatorio'

/** Valida el formulario de un tipo; devuelve los errores por campo (vacío si está bien). */
export function validarFormulario(tipo: TipoInstruccion, periodo: number, valores: ValoresFormulario): ErroresFormulario {
  const errores: ErroresFormulario = {}
  if (!Number.isInteger(periodo) || periodo < 1) errores.periodo = 'Escribe el número del periodo, desde 1'
  for (const c of CAMPOS_POR_TIPO[tipo]) {
    const crudo = valores[c.nombre]
    const texto = typeof crudo === 'string' ? crudo.trim() : ''
    if (c.tipo === 'booleano') continue
    if (texto === '') {
      if (c.obligatorio) errores[c.nombre] = OBLIGATORIO
      continue
    }
    if (c.tipo === 'entero' && !entero.test(texto)) errores[c.nombre] = 'Escribe un número entero, 0 o más'
    else if (c.tipo === 'dinero' && (!esDecimal(texto) || aMicros(texto) <= 0n)) errores[c.nombre] = 'Escribe un valor mayor que cero, por ejemplo 1.500.000'
    else if (c.tipo === 'fraccion' && (!esDecimal(texto) || aMicros(texto) <= 0n || comparar(texto, '1') > 0)) errores[c.nombre] = 'Escribe un porcentaje entre 1 y 100'
    else if (c.tipo === 'tasa' && (!esDecimal(texto) || aMicros(texto) <= 0n || comparar(texto, '10') >= 0)) errores[c.nombre] = 'Escribe un porcentaje mayor que 0, por ejemplo 81'
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

/** Ordena por periodo (estable) sin mutar la lista. */
export function ordenarInstrucciones(lista: Instruccion[]): Instruccion[] {
  return [...lista].sort((a, b) => a.periodo - b.periodo)
}

/**
 * Agrega instrucciones nuevas a la lista: una del mismo tipo en el mismo periodo se reemplaza (no se duplica) y el
 * resultado queda ordenado por periodo.
 */
export function agregarInstrucciones(lista: Instruccion[], nuevas: Instruccion[]): Instruccion[] {
  const choca = (a: Instruccion) => nuevas.some((n) => n.periodo === a.periodo && n.tipo === a.tipo)
  return ordenarInstrucciones([...lista.filter((a) => !choca(a)), ...nuevas])
}

/** Frase corta en español de una instrucción, para la lista del laboratorio y la línea de tiempo. */
export function describir(i: Instruccion): string {
  const etiqueta = ETIQUETAS_TIPO[i.tipo] ?? i.tipo
  const o = (v?: string) => (v ? (ETIQUETAS_VALOR[v] ?? v) : '')
  switch (i.tipo) {
    case 'PAGO_VALOR':
      return `${etiqueta}: ${cop(i.valor)}${i.diasAtraso ? `, ${i.diasAtraso} días de atraso` : ''}`
    case 'PAGO_PARCIAL':
      return `${etiqueta}: ${porcentaje(i.fraccion, 0)} de la cuota${i.diasAtraso ? `, ${i.diasAtraso} días de atraso` : ''}`
    case 'PAGO_CONTRACTUAL':
      return i.diasAtraso ? `${etiqueta} con ${i.diasAtraso} días de atraso` : etiqueta
    case 'SIN_PAGO':
      return i.causaMoraYServicios ? `${etiqueta} (se cobran mora y servicios)` : `${etiqueta} (solo interés corriente)`
    case 'ABONO_EXTRA':
      return `${etiqueta} de ${cop(i.valor)} · ${o(i.modalidad)}`
    case 'PREPAGO_TOTAL':
      return i.diasDesdeUltimaCuota ? `${etiqueta} a ${i.diasDesdeUltimaCuota} días de la última cuota` : etiqueta
    case 'USURA_VIGENTE':
      return `${etiqueta}: ${porcentaje(i.usuraEA)} EA`
    case 'CAMBIO_TASA':
      return `${etiqueta} a ${porcentaje(i.nuevaTasaEA)} EA · ${o(i.modalidad)}`
    case 'CIERRE_POR_RECUPERACION':
      return `${o(i.tipoRecuperacion) || etiqueta} por ${cop(i.valorMoto)}${i.costoRecuperacion && Number(i.costoRecuperacion) > 0 ? ` · costo de recuperación ${cop(i.costoRecuperacion)}` : ''}`
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

/** Opciones de un campo `opcion` según el catálogo del API. */
export function opcionesDe(catalogo: Catalogo | null, clave: NonNullable<Campo['opciones']>): string[] {
  return catalogo?.[clave] ?? []
}

export { decimalEs }
