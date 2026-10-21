// Dinero como string decimal (así viaja en el API: sin redondear, con hasta 34 cifras decimales del DECIMAL128).
// Nunca se pasa por number/parseFloat: las sumas se hacen con BigInt a escala fija de 34 decimales (sin pérdida) y
// el redondeo ocurre solo al mostrar (`cop`, `copCentavos`, `porcentaje`).

const ESCALA = 34
const FACTOR = 10n ** BigInt(ESCALA)
const PATRON = /^(-)?(\d+)(?:\.(\d+))?$/

/** Valor decimal exacto escalado a 34 decimales (el nombre "micros" es histórico). */
export type Micros = bigint

export function esDecimal(texto: unknown): texto is string {
  return typeof texto === 'string' && PATRON.test(texto.trim())
}

/** "1182645.20" → 1182645.20 × 10^34. Lo que exceda 34 decimales se redondea hacia arriba (mitad). */
export function aMicros(texto: string): Micros {
  const m = PATRON.exec(texto.trim())
  if (!m) throw new Error(`No es un decimal: "${texto}"`)
  const [, signo, entera, fraccion = ''] = m
  const base = BigInt(entera) * FACTOR + BigInt(fraccion.slice(0, ESCALA).padEnd(ESCALA, '0'))
  const sobra = fraccion.length > ESCALA && fraccion.charCodeAt(ESCALA) >= 53 ? 1n : 0n
  const valor = base + sobra
  return signo ? -valor : valor
}

/** Vuelve a string decimal sin ceros sobrantes. */
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

const numeroPlano = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 0, maximumFractionDigits: 6, useGrouping: 'always' })

/**
 * Redondea un decimal (string, con los decimales que traiga) a `decimales` posiciones, mitad hacia arriba en valor absoluto,
 * y devuelve un string con exactamente esas posiciones. Todo con BigInt: no hay number de por medio.
 */
export function redondear(texto: string, decimales: number): string {
  const m = PATRON.exec(texto.trim())
  if (!m) throw new Error(`No es un decimal: "${texto}"`)
  const [, signo, entera, fraccion = ''] = m
  const digitos = BigInt(entera + fraccion.slice(0, decimales).padEnd(decimales, '0'))
  const sube = fraccion.length > decimales && fraccion.charCodeAt(decimales) >= 53 ? 1n : 0n
  const total = (digitos + sube).toString().padStart(decimales + 1, '0')
  const cuerpo = decimales === 0 ? total : `${total.slice(0, -decimales)}.${total.slice(-decimales)}`
  return signo && /[1-9]/.test(cuerpo) ? `-${cuerpo}` : cuerpo
}

const moneda = (decimales: number) => new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', minimumFractionDigits: decimales, maximumFractionDigits: decimales, useGrouping: 'always' })
const entero = moneda(0)
const conCentavos = moneda(2)

/**
 * Pesos sin decimales (Diego, P-05) desde el string del API, que llega sin redondear con decenas de decimales:
 * "355666.9755960436057…" → "$ 355.667". Se redondea solo al mostrar; no pasa por number.
 */
export function cop(valor: string | null | undefined): string {
  if (valor == null || valor === '' || !esDecimal(valor)) return '—'
  return entero.format(redondear(valor, 0) as unknown as number)
}

/** Pesos con centavos al mostrar: "3169909.335342…" → "$ 3.169.909,34" (tablas periodo a periodo y asientos). */
export function copCentavos(valor: string | null | undefined): string {
  if (valor == null || !esDecimal(valor)) return '—'
  return conCentavos.format(redondear(valor, 2) as unknown as number)
}

/** Fracción a porcentaje desde el string: "0.8762" → "87,62 %"; "0.0265" → "2,65 %". Mueve la coma, sin number. */
export function porcentaje(fraccion: string | null | undefined, decimales = 2): string {
  if (fraccion == null || !esDecimal(fraccion)) return '—'
  const texto = redondear(deMicros(aMicros(fraccion) * 100n), decimales)
  const conComa = new Intl.NumberFormat('es-CO', { minimumFractionDigits: decimales, maximumFractionDigits: decimales }).format(texto as unknown as number)
  return `${conComa} %`
}

/** Número decimal plano (tasas, fracciones) con coma: "0.4" → "0,4". */
export function decimalEs(valor: string | null | undefined): string {
  if (valor == null || !esDecimal(valor)) return '—'
  return numeroPlano.format(redondear(valor, 6) as unknown as number)
}

/** Valor para dibujar (solo gráficos, nunca para mostrar ni sumar dinero). */
export function aNumeroGrafico(valor: string): number {
  return Number(deMicros(aMicros(valor)))
}
