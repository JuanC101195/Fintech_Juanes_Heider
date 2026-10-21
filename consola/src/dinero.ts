// Dinero como string decimal (así viaja en el API). Nunca se pasa por number/parseFloat:
// las sumas se hacen con BigInt en micro-pesos (6 decimales) y el formato sale del string.

const ESCALA = 6
const FACTOR = 10n ** BigInt(ESCALA)
const PATRON = /^(-)?(\d+)(?:\.(\d+))?$/

/** Valor decimal exacto en micro-pesos. */
export type Micros = bigint

export function esDecimal(texto: unknown): texto is string {
  return typeof texto === 'string' && PATRON.test(texto.trim())
}

/** "1182645.20" → 1182645200000n. Lo que exceda 6 decimales se redondea hacia arriba (mitad). */
export function aMicros(texto: string): Micros {
  const m = PATRON.exec(texto.trim())
  if (!m) throw new Error(`No es un decimal: "${texto}"`)
  const [, signo, entera, fraccion = ''] = m
  const base = BigInt(entera) * FACTOR + BigInt(fraccion.slice(0, ESCALA).padEnd(ESCALA, '0'))
  const sobra = fraccion.length > ESCALA && fraccion.charCodeAt(ESCALA) >= 53 ? 1n : 0n
  const valor = base + sobra
  return signo ? -valor : valor
}

/** Vuelve a string decimal sin ceros sobrantes: 1500000000000n → "1500000". */
export function deMicros(valor: Micros): string {
  const negativo = valor < 0n
  const abs = negativo ? -valor : valor
  const entera = (abs / FACTOR).toString()
  const fraccion = (abs % FACTOR).toString().padStart(ESCALA, '0').replace(/0+$/, '')
  return (negativo ? '-' : '') + entera + (fraccion ? '.' + fraccion : '')
}

export function sumar(...valores: (string | null | undefined)[]): string {
  return deMicros(valores.reduce<Micros>((acc, v) => acc + (v && esDecimal(v) ? aMicros(v) : 0n), 0n))
}

export function restar(a: string, b: string): string {
  return deMicros(aMicros(a) - aMicros(b))
}

export function comparar(a: string, b: string): -1 | 0 | 1 {
  const x = aMicros(a)
  const y = aMicros(b)
  return x < y ? -1 : x > y ? 1 : 0
}

export function esCero(valor: string | null | undefined): boolean {
  return !valor || !esDecimal(valor) || aMicros(valor) === 0n
}

const entero = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', minimumFractionDigits: 0, maximumFractionDigits: 0, useGrouping: 'always' })
const conCentavos = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', minimumFractionDigits: 2, maximumFractionDigits: 2, useGrouping: 'always' })
const numeroPlano = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 0, maximumFractionDigits: 6, useGrouping: 'always' })

/** Cuántos decimales distintos de cero hay hasta el centavo. */
function tieneCentavos(texto: string): boolean {
  const [, fraccion = ''] = texto.split('.')
  return /[1-9]/.test(fraccion.slice(0, 2))
}

/**
 * Pesos colombianos desde el string, sin pasar por number: "1182645.20" → "$ 1.182.645,20".
 * Enteros sin decimales; con centavos, dos decimales. Intl.NumberFormat acepta strings decimales exactos.
 */
export function cop(valor: string | null | undefined): string {
  if (valor == null || valor === '' || !esDecimal(valor)) return '—'
  const texto = valor.trim()
  return (tieneCentavos(texto) ? conCentavos : entero).format(texto as unknown as number)
}

/** Pesos siempre con 2 decimales (para el detalle de un abono). */
export function copCentavos(valor: string | null | undefined): string {
  if (valor == null || !esDecimal(valor)) return '—'
  return conCentavos.format(valor.trim() as unknown as number)
}

/** Fracción a porcentaje desde el string: "0.8762" → "87,62 %"; "0.0265" → "2,65 %". Mueve la coma, sin number. */
export function porcentaje(fraccion: string | null | undefined, decimales = 2): string {
  if (fraccion == null || !esDecimal(fraccion)) return '—'
  const x = aMicros(fraccion) * 100n
  const factor = 10n ** BigInt(ESCALA - decimales)
  const redondeado = (x + (x < 0n ? -factor / 2n : factor / 2n)) / factor
  const texto = deMicros(redondeado * factor)
  const conComa = new Intl.NumberFormat('es-CO', { minimumFractionDigits: decimales, maximumFractionDigits: decimales }).format(texto as unknown as number)
  return `${conComa} %`
}

/** Número decimal plano (tasas, fracciones) con coma: "0.4" → "0,4". */
export function decimalEs(valor: string | null | undefined): string {
  if (valor == null || !esDecimal(valor)) return '—'
  return numeroPlano.format(valor.trim() as unknown as number)
}

/** Valor para dibujar (solo gráficos, nunca para mostrar ni sumar dinero). */
export function aNumeroGrafico(valor: string): number {
  return Number(deMicros(aMicros(valor)))
}
