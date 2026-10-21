import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { crearCliente, ErrorDeRed, urlDelApi, type FuenteDatos } from './api/cliente'
import { conRespaldo, demo } from './demo'
import type { Credito, Instruccion, Perfil } from './tipos'

/** Lo que Casos le pasa al Laboratorio al pulsar "Abrir en el laboratorio". */
export interface SemillaLaboratorio {
  credito?: Credito
  instrucciones: Instruccion[]
  perfil: Perfil
  origen: string
}

interface ContextoDatos {
  fuente: FuenteDatos
  modo: 'api' | 'demo'
  semilla: SemillaLaboratorio | null
  poner(semilla: SemillaLaboratorio | null): void
}

const Contexto = createContext<ContextoDatos | null>(null)

export function useDatos(): ContextoDatos {
  const c = useContext(Contexto)
  if (!c) throw new Error('useDatos fuera de ProveedorDatos')
  return c
}

/**
 * Entrega la fuente de datos. Con `fuente` explícita (pruebas) se usa tal cual. Si no: `VITE_API_URL` vacío →
 * demostración; con URL → API real, con respaldo en la demostración si no responde.
 */
export function ProveedorDatos({ children, fuente }: Readonly<{ children: ReactNode; fuente?: FuenteDatos }>) {
  const [cayo, setCayo] = useState(false)
  const [semilla, poner] = useState<SemillaLaboratorio | null>(null)
  const real = useMemo<FuenteDatos>(() => {
    if (fuente) return fuente
    const url = urlDelApi(import.meta.env.VITE_API_URL)
    if (!url) return demo
    return conRespaldo(crearCliente(url), demo, () => setCayo(true), (e) => e instanceof ErrorDeRed)
  }, [fuente])
  // Al montar se sondea el catálogo para decidir el modo antes de que el usuario pida otra cosa.
  useEffect(() => {
    void real.catalogo().catch(() => undefined)
  }, [real])
  const modo = cayo ? 'demo' : real.modo
  const valor = useMemo(() => ({ fuente: real, modo, semilla, poner }), [real, modo, semilla])
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>
}

/** Carga asíncrona simple con estado de carga y de error. */
export interface Carga<T> {
  datos: T | null
  error: Error | null
  cargando: boolean
}

export function useCarga<T>(cargar: () => Promise<T>, dependencias: unknown[]): Carga<T> {
  const [estado, setEstado] = useState<Carga<T>>({ datos: null, error: null, cargando: true })
  useEffect(() => {
    let vigente = true
    setEstado((e) => ({ ...e, cargando: true, error: null }))
    cargar().then(
      (datos) => vigente && setEstado({ datos, error: null, cargando: false }),
      (error: Error) => vigente && setEstado({ datos: null, error, cargando: false }),
    )
    return () => {
      vigente = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, dependencias)
  return estado
}
