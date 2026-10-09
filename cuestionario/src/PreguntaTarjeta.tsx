import { useId } from 'react'
import { Comparador } from './Comparador'
import { previsualizacion } from './escenarios'
import { cop, numero } from './formato'
import { estaCompleta } from './respuestas'
import type { Pregunta, Respuesta } from './tipos'

interface Props {
  pregunta: Pregunta
  respuesta: Respuesta | undefined
  onCambio: (r: Respuesta) => void
}

export function PreguntaTarjeta({ pregunta: p, respuesta, onCambio }: Props) {
  const id = useId()
  const r = respuesta ?? {}
  const elegida = p.opciones.find((o) => o.codigo === r.codigo)
  const prev = previsualizacion(p.previsualizacion)
  const completa = estaCompleta(p, respuesta)
  const poner = (cambios: Partial<Respuesta>) => onCambio({ ...r, ...cambios })

  return (
    <article id={`pregunta-${p.id}`} className="scroll-mt-28 rounded-2xl bg-surface-container-lowest p-space-lg shadow-sm sm:p-space-xl">
      <header className="mb-space-md flex items-start justify-between gap-space-md">
        <div className="min-w-0">
          <span className="text-label-sm font-bold uppercase tracking-wider text-primary">{p.id}</span>
          <h3 className="text-title-lg text-on-surface">{p.titulo}</h3>
        </div>
        <span
          className={`shrink-0 rounded-full px-space-sm py-0.5 text-label-sm font-semibold ${completa ? 'bg-tertiary-container/40 text-on-tertiary-container' : 'bg-surface-container text-on-surface-variant'}`}
        >
          {completa ? '✓ Respondida' : 'Pendiente'}
        </span>
      </header>

      <div className="mb-space-lg grid gap-space-sm md:grid-cols-2">
        <p className="rounded-xl bg-surface-container-low p-space-md text-body-md text-on-surface-variant">
          <strong className="block text-label-md text-on-surface">Por qué importa</strong>
          {p.contexto}
        </p>
        <p className="rounded-xl bg-surface-container-low p-space-md text-body-md text-on-surface-variant">
          <strong className="block text-label-md text-on-surface">Hoy en el Excel</strong>
          {p.excelHoy}
        </p>
      </div>

      <fieldset className="mb-space-lg">
        <legend className="mb-space-sm text-label-md font-semibold text-on-surface-variant">Su respuesta</legend>
        <div className="flex flex-col gap-space-sm">
          {p.opciones.map((o) => {
            const marcada = r.codigo === o.codigo
            return (
              <label
                key={o.codigo}
                className={`flex cursor-pointer items-start gap-space-md rounded-xl p-space-md transition-colors ${marcada ? 'bg-primary-container/15 ring-2 ring-primary' : 'bg-surface-container-low hover:bg-surface-container'}`}
              >
                <input
                  type="radio"
                  name={`${id}-opcion`}
                  checked={marcada}
                  onChange={() => poner({ codigo: o.codigo })}
                  className="mt-1 h-5 w-5 shrink-0 accent-primary"
                />
                <span className="min-w-0">
                  <span className="block text-title-md text-on-surface">{o.texto}</span>
                  {o.detalle && <span className="block text-body-md text-on-surface-variant">{o.detalle}</span>}
                  {o.esExcel && <span className="mt-1 inline-block rounded-full bg-surface-container-high px-space-sm py-0.5 text-label-sm text-on-surface-variant">Así está hoy en el Excel</span>}
                </span>
              </label>
            )
          })}
        </div>
      </fieldset>

      {elegida?.pideValor && p.valor && (
        <div className="mb-space-lg">
          <label htmlFor={`${id}-valor`} className="mb-space-xs block text-label-md font-semibold text-on-surface-variant">
            {p.valor.etiqueta} ({p.valor.unidad})
          </label>
          <input
            id={`${id}-valor`}
            type="number"
            inputMode="decimal"
            min={0}
            value={r.valor ?? ''}
            onChange={(e) => poner({ valor: e.target.value === '' ? undefined : Number(e.target.value) })}
            className="w-full rounded-xl bg-surface-container-low px-space-md py-space-md text-body-lg text-on-surface focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/30 sm:w-64"
          />
          {p.valor.sugeridos && (
            <div className="mt-space-sm flex flex-wrap gap-space-xs">
              {p.valor.sugeridos.map((s) => (
                <button
                  key={s.valor}
                  type="button"
                  onClick={() => poner({ valor: s.valor })}
                  className={`min-h-9 rounded-full px-space-md text-label-md font-semibold ${r.valor === s.valor ? 'bg-primary text-on-primary' : 'bg-surface-container text-on-surface hover:bg-surface-container-high'}`}
                >
                  {p.valor?.unidad === 'COP' ? cop(s.valor) : `${numero(s.valor)} ${p.valor?.unidad}`}
                </button>
              ))}
            </div>
          )}
          {p.valor.sugeridos?.some((s) => s.escenario) && r.valor !== undefined && !p.valor.sugeridos.some((s) => s.valor === r.valor && s.escenario) && (
            <p className="mt-space-xs text-label-md text-on-surface-variant">Este valor no tiene vista previa; el efecto exacto se calcula al cargar sus respuestas en el motor.</p>
          )}
        </div>
      )}

      {elegida?.pideTexto && (
        <div className="mb-space-lg">
          <label htmlFor={`${id}-texto`} className="mb-space-xs block text-label-md font-semibold text-on-surface-variant">
            Explique su respuesta
          </label>
          <textarea
            id={`${id}-texto`}
            rows={3}
            value={r.texto ?? ''}
            onChange={(e) => poner({ texto: e.target.value })}
            className="w-full rounded-xl bg-surface-container-low px-space-md py-space-sm text-body-lg text-on-surface focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
        </div>
      )}

      {prev && (
        <div className="mb-space-lg">
          <Comparador pregunta={p} prev={prev} codigo={r.codigo} valor={r.valor} />
        </div>
      )}

      <details open={Boolean(r.comentario)}>
        <summary className="cursor-pointer text-label-md font-semibold text-primary">Agregar un comentario</summary>
        <textarea
          aria-label={`Comentario de la pregunta ${p.id}`}
          rows={2}
          value={r.comentario ?? ''}
          onChange={(e) => poner({ comentario: e.target.value })}
          className="mt-space-sm w-full rounded-xl bg-surface-container-low px-space-md py-space-sm text-body-lg text-on-surface focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/30"
        />
      </details>
    </article>
  )
}
