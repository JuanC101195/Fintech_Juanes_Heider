import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'

export interface OpcionSelector {
  valor: string
  titulo: string
  descripcion?: string
}

/** Más de estas opciones y el selector trae un buscador. */
export const UMBRAL_BUSQUEDA = 7

const normalizar = (t: string) => t.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()

/** Opciones cuyo título o descripción contienen todas las palabras buscadas (sin tildes ni mayúsculas). */
export function filtrarOpciones(opciones: OpcionSelector[], busqueda: string): OpcionSelector[] {
  const palabras = normalizar(busqueda).split(/\s+/).filter(Boolean)
  if (palabras.length === 0) return opciones
  return opciones.filter((o) => {
    const t = normalizar(`${o.titulo} ${o.descripcion ?? ''}`)
    return palabras.every((p) => t.includes(p))
  })
}

function useCelular(): boolean {
  const consulta = '(max-width: 639px)'
  const [es, setEs] = useState(() => typeof window !== 'undefined' && typeof window.matchMedia === 'function' && window.matchMedia(consulta).matches)
  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return
    const m = window.matchMedia(consulta)
    const alCambiar = () => setEs(m.matches)
    m.addEventListener('change', alCambiar)
    return () => m.removeEventListener('change', alCambiar)
  }, [])
  return es
}

/**
 * Selector propio para 5 opciones o más, en lugar del <select> nativo (en Windows su lista sale con letra diminuta
 * y no se arregla con CSS). Patrón ARIA: botón que abre un listbox; con buscador (combobox con lista) cuando hay más
 * de 7 opciones. Teclado: flechas, Inicio/Fin, Enter para elegir, Esc para cerrar y escribir para buscar o saltar.
 * Opciones de 48 px o más y letra de 16 px; en un celular se abre como hoja inferior.
 */
export function Selector({
  etiqueta,
  valor,
  opciones,
  alCambiar,
  ayuda,
  marcador = 'Elige una opción',
}: Readonly<{ etiqueta: string; valor: string; opciones: OpcionSelector[]; alCambiar: (v: string) => void; ayuda?: string; marcador?: string }>) {
  const id = useId()
  const celular = useCelular()
  const [abierto, setAbierto] = useState(false)
  const [busqueda, setBusqueda] = useState('')
  const [activo, setActivo] = useState(0)
  const contenedor = useRef<HTMLDivElement>(null)
  const boton = useRef<HTMLButtonElement>(null)
  const buscador = useRef<HTMLInputElement>(null)
  const lista = useRef<HTMLUListElement>(null)
  const tecleo = useRef({ texto: '', hasta: 0 })

  const conBusqueda = opciones.length > UMBRAL_BUSQUEDA
  const visibles = useMemo(() => filtrarOpciones(opciones, busqueda), [opciones, busqueda])
  const elegida = opciones.find((o) => o.valor === valor)
  const idLista = `${id}-lista`
  const idOpcion = (i: number) => `${id}-op-${i}`

  function abrir(inicial = '') {
    setBusqueda(conBusqueda ? inicial : '')
    const base = conBusqueda && inicial ? filtrarOpciones(opciones, inicial) : opciones
    setActivo(Math.max(0, base.findIndex((o) => o.valor === valor)))
    setAbierto(true)
  }

  function cerrar(devolverFoco = true) {
    setAbierto(false)
    setBusqueda('')
    if (devolverFoco) boton.current?.focus()
  }

  function elegir(o: OpcionSelector | undefined) {
    if (!o) return
    alCambiar(o.valor)
    cerrar()
  }

  // Al abrir, el foco va al buscador o a la lista.
  useEffect(() => {
    if (!abierto) return
    ;(conBusqueda ? buscador.current : lista.current)?.focus()
  }, [abierto, conBusqueda])

  // La opción activa siempre a la vista.
  useEffect(() => {
    if (abierto) document.getElementById(idOpcion(activo))?.scrollIntoView?.({ block: 'nearest' })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activo, abierto])

  // Clic o toque afuera cierra (sin robar el foco a lo que se tocó).
  useEffect(() => {
    if (!abierto) return
    const afuera = (e: PointerEvent) => {
      if (contenedor.current && !contenedor.current.contains(e.target as Node)) cerrar(false)
    }
    document.addEventListener('pointerdown', afuera)
    return () => document.removeEventListener('pointerdown', afuera)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [abierto])

  function saltarPorTexto(letra: string) {
    const ahora = Date.now()
    const t = ahora < tecleo.current.hasta ? tecleo.current.texto + letra : letra
    tecleo.current = { texto: t, hasta: ahora + 700 }
    const buscado = normalizar(t)
    const i = visibles.findIndex((o) => normalizar(o.titulo).startsWith(buscado))
    const j = i >= 0 ? i : visibles.findIndex((o) => normalizar(o.titulo).includes(buscado))
    if (j >= 0) setActivo(j)
  }

  function tecladoBoton(e: KeyboardEvent<HTMLButtonElement>) {
    if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(e.key)) {
      e.preventDefault()
      abrir()
    } else if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
      e.preventDefault()
      abrir(e.key)
      if (!conBusqueda) setTimeout(() => saltarPorTexto(e.key))
    }
  }

  function tecladoLista(e: KeyboardEvent<HTMLElement>) {
    const ultimo = visibles.length - 1
    switch (e.key) {
      case 'ArrowDown':
        setActivo((a) => Math.min(ultimo, a + 1))
        break
      case 'ArrowUp':
        setActivo((a) => Math.max(0, a - 1))
        break
      case 'Home':
        if (conBusqueda && e.currentTarget === buscador.current) return
        setActivo(0)
        break
      case 'End':
        if (conBusqueda && e.currentTarget === buscador.current) return
        setActivo(ultimo)
        break
      case 'PageDown':
        setActivo((a) => Math.min(ultimo, a + 5))
        break
      case 'PageUp':
        setActivo((a) => Math.max(0, a - 5))
        break
      case 'Enter':
        elegir(visibles[activo])
        break
      case 'Escape':
        cerrar()
        break
      case 'Tab':
        cerrar(false)
        return
      default:
        if (!conBusqueda && e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) saltarPorTexto(e.key)
        else return
    }
    e.preventDefault()
  }

  const listbox = (
    <ul
      ref={lista}
      id={idLista}
      role="listbox"
      aria-labelledby={`${id}-et`}
      tabIndex={conBusqueda ? -1 : 0}
      aria-activedescendant={!conBusqueda && visibles.length ? idOpcion(activo) : undefined}
      onKeyDown={conBusqueda ? undefined : tecladoLista}
      className={`flex flex-col gap-1 overflow-y-auto overscroll-contain p-1.5 outline-none ${celular ? 'min-h-0 flex-1' : 'max-h-[min(24rem,60vh)]'}`}
    >
      {visibles.map((o, i) => {
        const sel = o.valor === valor
        return (
          <li
            key={o.valor}
            id={idOpcion(i)}
            role="option"
            aria-selected={sel}
            onPointerMove={() => setActivo(i)}
            onClick={() => elegir(o)}
            className={`flex min-h-12 shrink-0 cursor-pointer items-start gap-3 rounded-lg px-3 py-2.5 text-base ${i === activo ? 'bg-surface-high outline-2 -outline-offset-2 outline-primary' : ''} ${sel ? 'text-primary' : 'text-on-surface'}`}
          >
            <span aria-hidden="true" className="w-4 shrink-0 font-bold">
              {sel ? '✓' : ''}
            </span>
            <span className="min-w-0">
              <span className="block font-semibold">{o.titulo}</span>
              {o.descripcion && <span className="block text-sm text-on-surface-variant">{o.descripcion}</span>}
            </span>
          </li>
        )
      })}
      {visibles.length === 0 && <li className="px-3 py-3 text-on-surface-variant">No hay opciones con «{busqueda}». Borra la búsqueda para ver todas.</li>}
    </ul>
  )

  const panel = (
    <div
      className={
        celular
          ? 'fixed inset-x-0 bottom-0 z-50 flex max-h-[85vh] flex-col rounded-t-2xl border-t border-outline-variant bg-surface-lowest pb-[env(safe-area-inset-bottom)] shadow-2xl'
          : 'absolute inset-x-0 top-full z-30 mt-1 flex flex-col rounded-xl border border-outline bg-surface-lowest shadow-xl'
      }
      role={celular ? 'dialog' : undefined}
      aria-modal={celular ? true : undefined}
      aria-label={celular ? etiqueta : undefined}
    >
      {celular && (
        <div className="flex items-center justify-between gap-2 border-b border-outline-variant px-4 py-2">
          <p className="font-bold">{etiqueta}</p>
          <button type="button" className="boton-suave" onClick={() => cerrar()}>
            Cerrar
          </button>
        </div>
      )}
      {conBusqueda && (
        <div className="border-b border-outline-variant p-2">
          <input
            ref={buscador}
            type="text"
            role="combobox"
            aria-expanded="true"
            aria-controls={idLista}
            aria-autocomplete="list"
            aria-activedescendant={visibles.length ? idOpcion(activo) : undefined}
            aria-label={`Buscar en ${etiqueta.toLowerCase()}`}
            placeholder="Escribe para buscar…"
            className="campo"
            value={busqueda}
            onChange={(e) => {
              setBusqueda(e.target.value)
              setActivo(0)
            }}
            onKeyDown={tecladoLista}
          />
        </div>
      )}
      {listbox}
    </div>
  )

  return (
    <div ref={contenedor} className="relative min-w-0">
      <span id={`${id}-et`} className="campo-etiqueta">
        {etiqueta}
      </span>
      <button
        ref={boton}
        type="button"
        id={`${id}-boton`}
        aria-haspopup="listbox"
        aria-expanded={abierto}
        aria-controls={abierto ? idLista : undefined}
        aria-labelledby={`${id}-et ${id}-boton`}
        aria-describedby={ayuda ? `${id}-ay` : undefined}
        onClick={() => (abierto ? cerrar() : abrir())}
        onKeyDown={tecladoBoton}
        className="campo flex items-center justify-between gap-3 text-left"
      >
        <span className="min-w-0 py-0.5">
          <span className="block font-semibold">{elegida?.titulo ?? marcador}</span>
          {elegida?.descripcion && <span className="block truncate text-sm text-on-surface-variant">{elegida.descripcion}</span>}
        </span>
        <span aria-hidden="true" className={`shrink-0 text-on-surface-variant transition-transform ${abierto ? 'rotate-180' : ''}`}>
          ▾
        </span>
      </button>
      {ayuda && (
        <p id={`${id}-ay`} className="mt-1 text-sm text-on-surface-variant">
          {ayuda}
        </p>
      )}
      {abierto && celular && <div aria-hidden="true" className="fixed inset-0 z-40 bg-black/50" onClick={() => cerrar()} />}
      {abierto && panel}
    </div>
  )
}
