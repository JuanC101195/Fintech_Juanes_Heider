export function cop(valor: number): string {
  const entero = Math.round(valor)
  return (entero < 0 ? '-$' : '$') + conMiles(Math.abs(entero).toString())
}

/** Separa los miles con punto ("1234567" → "1.234.567") sin expresiones regulares con retroceso. */
function conMiles(digitos: string): string {
  const grupos: string[] = []
  for (let fin = digitos.length; fin > 0; fin -= 3) grupos.unshift(digitos.slice(Math.max(0, fin - 3), fin))
  return grupos.join('.')
}

export function numero(valor: number): string {
  return valor.toLocaleString('es-CO', { maximumFractionDigits: 2 })
}
