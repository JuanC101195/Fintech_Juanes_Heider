import { useEffect, useState } from 'react'
import { AvisoDemo } from './componentes/comunes'
import { ProveedorDatos, useDatos } from './datos'
import Casos from './pantallas/Casos'
import Laboratorio from './pantallas/Laboratorio'
import Reglas from './pantallas/Reglas'
import Simulador from './pantallas/Simulador'
import type { FuenteDatos } from './api/cliente'

export type Ruta = { pantalla: 'simulador' | 'casos' | 'laboratorio' | 'reglas'; numero?: number }

/** "#/casos/13" → { pantalla: 'casos', numero: 13 }. Cualquier otra cosa cae en el simulador. */
export function leerRuta(hash: string): Ruta {
  const partes = hash.replace(/^#\/?/, '').split('/').filter(Boolean)
  const [pantalla, resto] = partes
  if (pantalla === 'casos') {
    const numero = Number(resto)
    return Number.isInteger(numero) && numero > 0 ? { pantalla, numero } : { pantalla }
  }
  if (pantalla === 'laboratorio' || pantalla === 'reglas') return { pantalla }
  return { pantalla: 'simulador' }
}

const SECCIONES: { id: Ruta['pantalla']; etiqueta: string; icono: string }[] = [
  { id: 'simulador', etiqueta: 'Simulador', icono: '🧮' },
  { id: 'casos', etiqueta: 'Casos', icono: '📋' },
  { id: 'laboratorio', etiqueta: 'Laboratorio', icono: '🧪' },
  { id: 'reglas', etiqueta: 'Reglas', icono: '📜' },
]

function useRuta(): Ruta {
  const [ruta, setRuta] = useState(() => leerRuta(window.location.hash))
  useEffect(() => {
    const alCambiar = () => {
      setRuta(leerRuta(window.location.hash))
      window.scrollTo(0, 0)
    }
    window.addEventListener('hashchange', alCambiar)
    return () => window.removeEventListener('hashchange', alCambiar)
  }, [])
  return ruta
}

type Tema = 'auto' | 'claro' | 'oscuro'

function useTema(): [Tema, () => void] {
  const [tema, setTema] = useState<Tema>(() => {
    try {
      const guardado = localStorage.getItem('consola-tema')
      return guardado === 'claro' || guardado === 'oscuro' ? guardado : 'auto'
    } catch {
      return 'auto'
    }
  })
  useEffect(() => {
    if (tema === 'auto') document.documentElement.removeAttribute('data-tema')
    else document.documentElement.dataset.tema = tema
    try {
      localStorage.setItem('consola-tema', tema)
    } catch {
      /* sin almacenamiento: el tema solo dura la sesión */
    }
  }, [tema])
  return [tema, () => setTema((t) => (t === 'auto' ? 'claro' : t === 'claro' ? 'oscuro' : 'auto'))]
}

function BotonTema() {
  const [tema, alternar] = useTema()
  const nombre = { auto: 'del sistema', claro: 'claro', oscuro: 'oscuro' }[tema]
  return (
    <button type="button" className="boton-suave w-full text-sm" onClick={alternar} aria-label={`Cambiar tema; ahora es ${nombre}`}>
      <span aria-hidden="true">◐</span> Tema: {nombre}
    </button>
  )
}

function Cascaron() {
  const ruta = useRuta()
  const { modo } = useDatos()
  return (
    <div className="min-h-screen md:grid md:grid-cols-[15rem_minmax(0,1fr)]">
      <a href="#contenido" onClick={(e) => { e.preventDefault(); document.getElementById('contenido')?.focus() }} className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:bg-primary focus:p-3 focus:text-on-primary">
        Saltar al contenido
      </a>
      <aside className="sticky top-0 hidden h-screen flex-col gap-4 border-r border-outline-variant bg-surface-low p-4 md:flex">
        <div>
          <p className="text-lg font-bold text-primary">Motor de cartera</p>
          <p className="text-xs text-on-surface-variant">Consola interna</p>
        </div>
        <nav aria-label="Secciones" className="grid gap-1">
          {SECCIONES.map((s) => (
            <a key={s.id} href={`#/${s.id}`} aria-current={ruta.pantalla === s.id ? 'page' : undefined} className={`flex min-h-11 items-center gap-3 rounded-lg px-3 font-semibold no-underline ${ruta.pantalla === s.id ? 'bg-primary-container text-on-primary-container' : 'hover:bg-surface-high'}`}>
              <span aria-hidden="true">{s.icono}</span>
              {s.etiqueta}
            </a>
          ))}
        </nav>
        <div className="mt-auto">
          <BotonTema />
        </div>
      </aside>

      <div className="min-w-0">
        {modo === 'demo' && <AvisoDemo />}
        <header className="flex items-center justify-between border-b border-outline-variant bg-surface-low px-4 py-2 md:hidden">
          <p className="font-bold text-primary">Motor de cartera</p>
          <div className="w-40">
            <BotonTema />
          </div>
        </header>
        <main id="contenido" tabIndex={-1} className="mx-auto max-w-[88rem] px-4 pt-4 pb-24 outline-none md:px-8 md:pt-6 md:pb-10">
          {ruta.pantalla === 'simulador' && <Simulador />}
          {ruta.pantalla === 'casos' && <Casos numero={ruta.numero} />}
          {ruta.pantalla === 'laboratorio' && <Laboratorio />}
          {ruta.pantalla === 'reglas' && <Reglas />}
        </main>
      </div>

      <nav aria-label="Secciones" className="fixed inset-x-0 bottom-0 z-40 grid grid-cols-4 border-t border-outline-variant bg-surface-low md:hidden">
        {SECCIONES.map((s) => (
          <a key={s.id} href={`#/${s.id}`} aria-current={ruta.pantalla === s.id ? 'page' : undefined} className={`flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs font-semibold no-underline ${ruta.pantalla === s.id ? 'bg-primary-container text-on-primary-container' : ''}`}>
            <span aria-hidden="true" className="text-lg leading-none">{s.icono}</span>
            {s.etiqueta}
          </a>
        ))}
      </nav>
    </div>
  )
}

export default function App({ fuente }: Readonly<{ fuente?: FuenteDatos }>) {
  return (
    <ProveedorDatos fuente={fuente}>
      <Cascaron />
    </ProveedorDatos>
  )
}
