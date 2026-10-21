import { sinJerga } from './componentes/comunes'
import type { CasoResumen } from './tipos'

/** Una línea de qué pasa en cada caso, para las tarjetas y el selector. Si el API trae un caso nuevo, se usa su descripción. */
const LINEAS: Record<number, string> = {
  1: 'Paga todas las cuotas a tiempo',
  2: 'La tasa sube al 80 % y se conserva la fecha final',
  3: 'Deja de pagar y entrega la moto por menos de lo que debe',
  4: 'Deja de pagar y se retoma la moto por más de lo que debe',
  5: 'En el mes 10 paga todo el saldo',
  6: 'Abona $ 2.000.000 y termina antes',
  7: 'Abona $ 2.000.000 y le baja la cuota',
  8: 'Un mes paga solo el 40 % de la cuota',
  9: 'Paga con 14 días de atraso: mora y cobranza',
  10: 'La tasa sube al 80 % y se conserva la cuota',
  11: 'Paga el 40 %, no paga 3 semanas y entrega la moto',
  12: 'Paga el 40 % y luego se pone al día',
  13: 'No paga, paga poco y luego abona $ 1.500.000',
}

export function lineaCaso(c: CasoResumen): string {
  return LINEAS[c.numero] ?? sinJerga(c.descripcion)
}
