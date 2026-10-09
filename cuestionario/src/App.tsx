import { useState } from 'react'
import { FECHA_ESCENARIOS } from './escenarios'
import { descargarExcel } from './exportar'
import { PreguntaTarjeta } from './PreguntaTarjeta'
import { BLOQUES, PREGUNTAS } from './preguntas'
import { avance, estaCompleta, useEstado } from './respuestas'

export default function App() {
  const [estado, setEstado] = useEstado()
  const [bloque, setBloque] = useState<string>(BLOQUES[0])
  const [exportando, setExportando] = useState(false)
  const { completas, total } = avance(estado.respuestas)
  const delBloque = PREGUNTAS.filter((p) => p.bloque === bloque)
  const indice = BLOQUES.indexOf(bloque as (typeof BLOQUES)[number])

  const irA = (b: string) => {
    setBloque(b)
    window.scrollTo({ top: 0 })
  }

  const exportar = async () => {
    setExportando(true)
    try {
      await descargarExcel(estado)
    } finally {
      setExportando(false)
    }
  }

  const reiniciar = () => {
    if (window.confirm('¿Borrar todas las respuestas guardadas en este navegador?')) {
      setEstado({ respondente: { nombre: '', cargo: '' }, respuestas: {} })
    }
  }

  return (
    <div className="min-h-screen pb-space-2xl">
      <header className="sticky top-0 z-20 bg-surface-container-lowest/95 shadow-sm backdrop-blur">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-space-sm px-margin py-space-md md:px-margin-desktop">
          <div className="min-w-0">
            <p className="text-label-sm font-bold uppercase tracking-wider text-primary">Motor de cartera de motos</p>
            <h1 className="text-title-lg text-on-surface">Definición de reglas</h1>
          </div>
          <div className="flex items-center gap-space-md">
            <div className="text-right">
              <p className="cifra text-label-lg text-on-surface">
                {completas} de {total}
              </p>
              <div className="mt-1 h-1.5 w-28 overflow-hidden rounded-full bg-surface-container-high" aria-hidden="true">
                <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${(completas / total) * 100}%` }} />
              </div>
            </div>
            <button
              type="button"
              onClick={exportar}
              disabled={exportando}
              className="min-h-10 rounded-xl bg-primary px-space-lg text-label-lg font-bold text-on-primary shadow-sm transition hover:brightness-110 disabled:opacity-60"
            >
              {exportando ? 'Generando…' : 'Descargar Excel'}
            </button>
          </div>
        </div>
        <nav aria-label="Bloques de preguntas" className="mx-auto max-w-6xl overflow-x-auto px-margin pb-space-sm md:px-margin-desktop">
          <ol className="flex gap-space-xs">
            {BLOQUES.map((b) => {
              const preguntas = PREGUNTAS.filter((p) => p.bloque === b)
              const listas = preguntas.filter((p) => estaCompleta(p, estado.respuestas[p.id])).length
              const activo = b === bloque
              return (
                <li key={b} className="shrink-0">
                  <button
                    type="button"
                    aria-current={activo ? 'step' : undefined}
                    onClick={() => irA(b)}
                    className={`min-h-9 rounded-full px-space-md text-label-md font-semibold ${activo ? 'bg-primary text-on-primary' : 'bg-surface-container-low text-on-surface-variant hover:bg-surface-container'}`}
                  >
                    {b} · {listas}/{preguntas.length}
                  </button>
                </li>
              )
            })}
          </ol>
        </nav>
      </header>

      <main className="mx-auto flex max-w-6xl flex-col gap-space-lg px-margin pt-space-lg md:px-margin-desktop">
        {indice === 0 && (
          <section className="rounded-2xl bg-surface-container-lowest p-space-lg shadow-sm sm:p-space-xl">
            <h2 className="text-headline-md text-on-surface">Antes de empezar</h2>
            <p className="mt-space-xs text-body-lg text-on-surface-variant">
              Al replicar el Excel de casos operativos en el motor aparecieron decisiones que el Excel no resuelve o resuelve de dos formas. Para
              cada pregunta verá lo que hace hoy el Excel y, cuando cambia el cálculo, <strong className="text-on-surface">las cifras que da el motor con cada opción</strong>.
              Sus respuestas se guardan en este navegador; al terminar descargue el Excel y envíelo de vuelta.
            </p>
            <div className="mt-space-lg grid gap-space-md sm:grid-cols-2">
              {(
                [
                  ['nombre', 'Nombre de quien responde'],
                  ['cargo', 'Cargo'],
                ] as const
              ).map(([campo, etiqueta]) => (
                <label key={campo} className="flex flex-col gap-space-xs">
                  <span className="text-label-md font-semibold text-on-surface-variant">{etiqueta}</span>
                  <input
                    value={estado.respondente[campo]}
                    onChange={(e) => setEstado((s) => ({ ...s, respondente: { ...s.respondente, [campo]: e.target.value } }))}
                    className="rounded-xl bg-surface-container-low px-space-md py-space-md text-body-lg text-on-surface focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                </label>
              ))}
            </div>
            <p className="mt-space-md text-label-md text-on-surface-variant">Cifras calculadas con el motor el {FECHA_ESCENARIOS}, con los parámetros de la hoja Inputs.</p>
          </section>
        )}

        <h2 className="text-headline-md text-on-surface">{bloque}</h2>
        {delBloque.map((p) => (
          <PreguntaTarjeta
            key={p.id}
            pregunta={p}
            respuesta={estado.respuestas[p.id]}
            onCambio={(r) => setEstado((s) => ({ ...s, respuestas: { ...s.respuestas, [p.id]: r } }))}
          />
        ))}

        <nav aria-label="Cambiar de bloque" className="flex flex-col-reverse gap-space-sm sm:flex-row sm:justify-between">
          {indice > 0 ? (
            <button type="button" onClick={() => irA(BLOQUES[indice - 1])} className="min-h-11 rounded-xl bg-surface-container px-space-lg text-label-lg text-on-surface hover:bg-surface-container-high">
              ← {BLOQUES[indice - 1]}
            </button>
          ) : (
            <span />
          )}
          {indice < BLOQUES.length - 1 ? (
            <button type="button" onClick={() => irA(BLOQUES[indice + 1])} className="min-h-11 rounded-xl bg-primary px-space-lg text-label-lg font-bold text-on-primary hover:brightness-110">
              {BLOQUES[indice + 1]} →
            </button>
          ) : (
            <button type="button" onClick={exportar} disabled={exportando} className="min-h-11 rounded-xl bg-primary px-space-lg text-label-lg font-bold text-on-primary hover:brightness-110">
              {completas === total ? 'Terminé: descargar Excel' : `Descargar Excel (${completas} de ${total} respondidas)`}
            </button>
          )}
        </nav>

        <button type="button" onClick={reiniciar} className="self-center text-label-md text-on-surface-variant underline hover:text-on-surface">
          Borrar respuestas de este navegador
        </button>
      </main>
    </div>
  )
}
