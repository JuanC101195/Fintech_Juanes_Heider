import { useEffect, useLayoutEffect, useRef, useState, type InputHTMLAttributes } from 'react'
import { formatearMiles, fraccionAPorcentaje, leerMiles, porcentajeAFraccion } from '../dinero'

type PropsBase = Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type' | 'className'>

/**
 * Dinero con separador de miles en vivo ("$ 1.500.000") y teclado numérico en el celular. Lo que entrega es el valor
 * exacto como string decimal ("1500000"), el mismo formato del API. El cursor se queda donde iba aunque se agreguen puntos.
 */
export function EntradaDinero({ valor, alCambiar, ...resto }: Readonly<PropsBase & { valor: string; alCambiar: (v: string) => void }>) {
  const ref = useRef<HTMLInputElement>(null)
  /** Cuántos dígitos (o coma) había antes del cursor al escribir. */
  const cursor = useRef<number | null>(null)
  const mostrado = formatearMiles(valor)

  useLayoutEffect(() => {
    const el = ref.current
    if (cursor.current === null || !el || document.activeElement !== el) return
    let vistos = 0
    let pos = 0
    while (pos < el.value.length && vistos < cursor.current) {
      if (/[\d,]/.test(el.value[pos])) vistos++
      pos++
    }
    el.setSelectionRange(pos, pos)
    cursor.current = null
  })

  return (
    <div className="relative">
      <span aria-hidden="true" className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 font-semibold text-on-surface-variant">
        $
      </span>
      <input
        {...resto}
        ref={ref}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        className="campo cifra pl-8 text-lg"
        value={mostrado}
        onChange={(e) => {
          const t = e.target.value
          const sel = e.target.selectionStart ?? t.length
          cursor.current = [...t.slice(0, sel)].filter((c) => /[\d,]/.test(c)).length
          alCambiar(leerMiles(t))
        }}
      />
    </div>
  )
}

/** Entero con botones − y + de 48 px (periodos, cuotas, días). Escribir también funciona. */
export function EntradaPasos({ valor, alCambiar, minimo = 1, maximo, nombre, ...resto }: Readonly<PropsBase & { valor: string; alCambiar: (v: string) => void; minimo?: number; maximo?: number; nombre: string }>) {
  const n = /^\d+$/.test(valor) ? Number(valor) : null
  const mover = (paso: number) => {
    let siguiente = (n ?? minimo) + paso
    if (n === null) siguiente = minimo
    siguiente = Math.max(minimo, maximo === undefined ? siguiente : Math.min(maximo, siguiente))
    alCambiar(String(siguiente))
  }
  return (
    <div className="flex items-stretch gap-1.5">
      <button type="button" className="boton-suave w-12 shrink-0 !px-0 text-xl" aria-label={`${nombre}: restar 1`} onClick={() => mover(-1)} disabled={n !== null && n <= minimo}>
        −
      </button>
      <input
        {...resto}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        className="campo cifra min-w-0 flex-1 text-center text-lg"
        value={valor}
        onChange={(e) => alCambiar(e.target.value.replace(/\D/g, ''))}
      />
      <button type="button" className="boton-suave w-12 shrink-0 !px-0 text-xl" aria-label={`${nombre}: sumar 1`} onClick={() => mover(1)} disabled={maximo !== undefined && n !== null && n >= maximo}>
        +
      </button>
    </div>
  )
}

/**
 * Tasa o fracción escrita como porcentaje ("81" o "81,5") que entrega la fracción exacta del API ("0.81"). Si lo
 * escrito no es un número, entrega el texto tal cual para que la validación lo marque.
 */
export function EntradaPorcentaje({ valor, alCambiar, ...resto }: Readonly<PropsBase & { valor: string; alCambiar: (v: string) => void }>) {
  const [texto, setTexto] = useState(() => fraccionAPorcentaje(valor) || valor)
  useEffect(() => {
    // Si el valor cambia desde afuera (un atajo, editar otro evento), se muestra; si es lo mismo que se escribe, no se toca.
    if (porcentajeAFraccion(texto) !== valor && texto !== valor) setTexto(fraccionAPorcentaje(valor) || valor)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [valor])
  return (
    <div className="relative">
      <input
        {...resto}
        type="text"
        inputMode="decimal"
        autoComplete="off"
        className="campo cifra pr-10 text-lg"
        value={texto}
        onChange={(e) => {
          const t = e.target.value.replace(/[^\d,.]/g, '')
          setTexto(t)
          alCambiar(t === '' ? '' : (porcentajeAFraccion(t) ?? t))
        }}
      />
      <span aria-hidden="true" className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 font-semibold text-on-surface-variant">
        %
      </span>
    </div>
  )
}
