import { useId, useState } from 'react'
import { aNumeroGrafico, cop } from '../dinero'

export interface Serie {
  nombre: string
  /** Color como variable CSS, por ejemplo "var(--c-primary)". */
  color: string
  /** Dinero como string; solo se convierte a number para dibujar. */
  valores: string[]
}

const ANCHO = 640
const ALTO = 240
const M = { arriba: 12, derecha: 12, abajo: 28, izquierda: 52 }

function corto(valor: number): string {
  if (valor >= 1e6) return `${(valor / 1e6).toLocaleString('es-CO', { maximumFractionDigits: 1 })} M`
  if (valor >= 1e3) return `${Math.round(valor / 1e3).toLocaleString('es-CO')} mil`
  return String(Math.round(valor))
}

/** Valor "redondo" para el tope del eje. */
function tope(maximo: number): number {
  if (maximo <= 0) return 1
  const potencia = 10 ** Math.floor(Math.log10(maximo))
  const f = maximo / potencia
  const paso = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10
  return paso * potencia
}

/**
 * Gráfico de áreas en SVG propio, sin librerías: una serie (línea con relleno suave) o varias apiladas.
 * Una sola escala, cuadrícula tenue, leyenda siempre que haya 2 o más series y lectura al pasar el puntero o
 * moverse con el teclado (flechas). La tabla del plan hace de vista alternativa de los mismos datos.
 */
export function GraficoSeries({ titulo, descripcion, periodos, series, apilado }: Readonly<{ titulo: string; descripcion: string; periodos: number[]; series: Serie[]; apilado: boolean }>) {
  const id = useId()
  const [activo, setActivo] = useState<number | null>(null)
  const n = periodos.length
  if (n === 0) return null

  const numeros = series.map((s) => s.valores.map(aNumeroGrafico))
  const acumulado = apilado ? numeros.map((_, k) => numeros.slice(0, k + 1).reduce((a, fila) => a.map((v, i) => v + fila[i]), Array<number>(n).fill(0))) : numeros
  const maximo = tope(Math.max(...acumulado.flat(), 0))
  const w = ANCHO - M.izquierda - M.derecha
  const h = ALTO - M.arriba - M.abajo
  const x = (i: number) => M.izquierda + (n === 1 ? w / 2 : (i / (n - 1)) * w)
  const y = (v: number) => M.arriba + h - (v / maximo) * h

  const caminoSup = (k: number) => acumulado[k].map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ')
  const caminoInf = (k: number) => (apilado && k > 0 ? acumulado[k - 1].map((v, i) => `L${x(i).toFixed(1)},${y(v).toFixed(1)}`).reverse().join(' ') : `L${x(n - 1).toFixed(1)},${y(0)} L${x(0).toFixed(1)},${y(0)}`)
  const ticksY = [0, 0.25, 0.5, 0.75, 1].map((f) => f * maximo)
  const cadaX = Math.max(1, Math.ceil(n / 8))

  const desdePuntero = (e: React.PointerEvent<SVGSVGElement>) => {
    const r = e.currentTarget.getBoundingClientRect()
    const px = ((e.clientX - r.left) / r.width) * ANCHO
    setActivo(Math.min(n - 1, Math.max(0, Math.round(((px - M.izquierda) / w) * (n - 1)))))
  }
  const teclado = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowRight') setActivo((a) => Math.min(n - 1, (a ?? -1) + 1))
    else if (e.key === 'ArrowLeft') setActivo((a) => Math.max(0, (a ?? n) - 1))
    else if (e.key === 'Escape') setActivo(null)
    else return
    e.preventDefault()
  }

  return (
    <figure className="tarjeta min-w-0" aria-labelledby={`${id}-t`}>
      <figcaption id={`${id}-t`} className="mb-1 font-bold">
        {titulo}
      </figcaption>
      <p className="mb-2 text-xs text-on-surface-variant">{descripcion}</p>
      {series.length > 1 && (
        <ul className="mb-2 flex flex-wrap gap-x-4 gap-y-1 text-xs">
          {series.map((s) => (
            <li key={s.nombre} className="flex items-center gap-1.5">
              <span aria-hidden="true" className="inline-block size-3 rounded-sm" style={{ background: s.color }} />
              {s.nombre}
            </li>
          ))}
        </ul>
      )}
      <div className="relative">
        <svg
          viewBox={`0 0 ${ANCHO} ${ALTO}`}
          className="block h-auto w-full touch-pan-y"
          role="img"
          tabIndex={0}
          aria-label={`${titulo}. ${descripcion} Usa las flechas izquierda y derecha para leer cada periodo.`}
          onPointerMove={desdePuntero}
          onPointerDown={desdePuntero}
          onPointerLeave={() => setActivo(null)}
          onKeyDown={teclado}
          onBlur={() => setActivo(null)}
        >
          {ticksY.map((t) => (
            <g key={t}>
              <line x1={M.izquierda} x2={ANCHO - M.derecha} y1={y(t)} y2={y(t)} stroke="var(--c-outline-variant)" strokeWidth="1" opacity={t === 0 ? 1 : 0.5} />
              <text x={M.izquierda - 6} y={y(t) + 4} textAnchor="end" fontSize="11" fill="var(--c-on-surface-variant)">
                {corto(t)}
              </text>
            </g>
          ))}
          {periodos.map((p, i) =>
            i % cadaX === 0 || i === n - 1 ? (
              <text key={p} x={x(i)} y={ALTO - 8} textAnchor="middle" fontSize="11" fill="var(--c-on-surface-variant)">
                {p}
              </text>
            ) : null,
          )}
          {series.map((s, k) => (
            <g key={s.nombre}>
              <path d={`${caminoSup(k)} ${caminoInf(k)} Z`} fill={s.color} opacity={apilado ? 0.85 : 0.14} />
              {(!apilado || k === series.length - 1) && <path d={caminoSup(k)} fill="none" stroke={s.color} strokeWidth="2" strokeLinejoin="round" />}
            </g>
          ))}
          {activo !== null && (
            <g>
              <line x1={x(activo)} x2={x(activo)} y1={M.arriba} y2={M.arriba + h} stroke="var(--c-on-surface)" strokeWidth="1" strokeDasharray="3 3" />
              {series.map((s, k) => (
                <circle key={s.nombre} cx={x(activo)} cy={y(acumulado[k][activo])} r="4" fill={s.color} stroke="var(--c-surface-lowest)" strokeWidth="2" />
              ))}
            </g>
          )}
        </svg>
        <div aria-live="polite" className="cifra mt-1 min-h-[3.25rem] rounded-lg bg-surface-low px-3 py-1.5 text-xs">
          {activo === null ? (
            <span className="text-on-surface-variant">Pasa el puntero o usa las flechas para leer un periodo.</span>
          ) : (
            <>
              <strong>Periodo {periodos[activo]}</strong>
              {series.map((s) => (
                <span key={s.nombre} className="ml-3 inline-block">
                  <span aria-hidden="true" className="mr-1 inline-block size-2 rounded-full" style={{ background: s.color }} />
                  {s.nombre}: {cop(s.valores[activo])}
                </span>
              ))}
            </>
          )}
        </div>
      </div>
    </figure>
  )
}
