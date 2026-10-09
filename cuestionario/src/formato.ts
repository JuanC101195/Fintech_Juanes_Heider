export function cop(valor: number): string {
  const entero = Math.round(valor)
  const texto = Math.abs(entero).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  return (entero < 0 ? '-$' : '$') + texto
}

export function numero(valor: number): string {
  return valor.toLocaleString('es-CO', { maximumFractionDigits: 2 })
}
