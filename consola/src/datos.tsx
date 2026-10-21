import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { crearCliente, ErrorDeApi, ErrorDeRed, urlDelApi, type FuenteDatos } from './api/cliente'
import { conRespaldo, demo } from './demo'
import type { Credito, Instruccion, Perfil } from './tipos'

/** Lo que Casos le pasa al Laboratorio al pulsar "Probar variaciones en el laboratorio". */
export interface SemillaLaboratorio {
  credito?: Credito
  instrucciones: Instruccion[]
  perfil: Perfil
  /** El caso de donde sale: el laboratorio lo marca como punto de partida (una sola fuente de verdad). */
  caso: { numero: number; nombre: string }
}

interface ContextoDatos {
  fuente: FuenteDatos
  modo: 'api' | 'demo'
  /** false mientras el API real no haya respondido nada: el plan gratuito se duerme y la primera vez tarda. */
  despierto: boolean
  semilla: SemillaLaboratorio | null
  poner(semilla: SemillaLaboratorio | null): void
}

const Contexto = createContext<ContextoDatos | null>(null)

export function useDatos(): ContextoDatos {
  const c = useContext(Contexto)
  if (!c) throw new Error('useDatos fuera de ProveedorDatos')
  return c
}

/** Texto de espera para cuando el servidor aún no ha respondido (null si ya respondió o si es la demostración). */
export function useNotaEspera(): string | null {
  const { modo, despierto } = useDatos()
  return modo === 'api' && !despierto ? 'El servidor puede tardar unos segundos en despertar (hasta 30 s la primera vez).' : null
}

/** Envuelve la fuente para saber cuándo respondió el servidor por primera vez (una respuesta de error también cuenta). */
function observar(fuente: FuenteDatos, alResponder: () => void): FuenteDatos {
  const marcar = <T,>(p: Promise<T>): Promise<T> =>
    p.then(
      (v) => {
        alResponder()
        return v
      },
      (e: unknown) => {
        if (e instanceof ErrorDeApi) alResponder()
        throw e
      },
    )
  return {
    get modo() {
      return fuente.modo
    },
    catalogo: () => marcar(fuente.catalogo()),
    reglas: () => marcar(fuente.reglas()),
    casos: () => marcar(fuente.casos()),
    simular: (c) => marcar(fuente.simular(c)),
    ejecutar: (p) => marcar(fuente.ejecutar(p)),
    caso: (n, perfil) => marcar(fuente.caso(n, perfil)),
  }
}

/**
 * Entrega la fuente de datos. Con `fuente` explícita (pruebas) se usa tal cual. Si no: `VITE_API_URL` vacío →
 * demostración; con URL → API real, con respaldo en la demostración si no responde.
 */
export function ProveedorDatos({ children, fuente }: Readonly<{ children: ReactNode; fuente?: FuenteDatos }>) {
  const [cayo, setCayo] = useState(false)
  const [despierto, setDespierto] = useState(false)
  const [semilla, poner] = useState<SemillaLaboratorio | null>(null)
  const real = useMemo<FuenteDatos>(() => {
    const base = (() => {
      if (fuente) return fuente
      const url = urlDelApi(import.meta.env.VITE_API_URL)
      if (!url) return demo
      return conRespaldo(crearCliente(url), demo, () => setCayo(true), (e) => e instanceof ErrorDeRed)
    })()
    return observar(base, () => setDespierto(true))
  }, [fuente])
  // Al montar se sondea el catálogo para decidir el modo (y despertar el servidor) antes de que el usuario pida otra cosa.
  useEffect(() => {
    void real.catalogo().catch(() => undefined)
  }, [real])
  const modo = cayo ? 'demo' : real.modo
  const valor = useMemo(() => ({ fuente: real, modo, despierto, semilla, poner }), [real, modo, despierto, semilla])
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>
}

/** Carga asíncrona simple con estado de carga y de error; `recargar` vuelve a intentar. */
export interface Carga<T> {
  datos: T | null
  error: Error | null
  cargando: boolean
  recargar(): void
}

export function useCarga<T>(cargar: () => Promise<T>, dependencias: unknown[]): Carga<T> {
  const [intento, setIntento] = useState(0)
  const [estado, setEstado] = useState<Omit<Carga<T>, 'recargar'>>({ datos: null, error: null, cargando: true })
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
  }, [...dependencias, intento])
  return { ...estado, recargar: () => setIntento((i) => i + 1) }
}
