import { useState } from 'react'
import { Cargando, MensajeError, SelectorPerfil, Titulo } from '../componentes/comunes'
import { DetalleEjecucion } from '../componentes/DetalleEjecucion'
import { useCarga, useDatos } from '../datos'
import type { CasoDetalle, CasoResumen, Perfil } from '../tipos'

export default function Casos({ numero }: Readonly<{ numero?: number }>) {
  return numero ? <DetalleCaso numero={numero} /> : <ListaCasos />
}

function ListaCasos() {
  const { fuente } = useDatos()
  const { datos, error, cargando } = useCarga<CasoResumen[]>(() => fuente.casos(), [fuente])
  return (
    <div>
      <Titulo detalle="Los casos del Excel de operaciones (más el caso 13) corridos con el motor real. Entra a uno para ver periodo a periodo qué pasó.">Casos</Titulo>
      {cargando && <Cargando />}
      {error && <MensajeError error={error} />}
      {datos && (
        <ul className="grid gap-3 md:grid-cols-2 xl:grid-cols-3" aria-label="Lista de casos">
          {datos.map((c) => (
            <li key={c.numero}>
              <a href={`#/casos/${c.numero}`} className="tarjeta flex h-full gap-3 no-underline hover:border-primary">
                <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary-container font-bold text-on-primary-container">
                  {c.numero}
                </span>
                <span className="min-w-0">
                  <span className="block font-bold">
                    <span className="sr-only">Caso {c.numero}: </span>
                    {c.nombre}
                  </span>
                  <span className="mt-0.5 block text-sm text-on-surface-variant">{c.descripcion}</span>
                  <span className="mt-1 block text-xs text-outline">{c.fuente}</span>
                </span>
              </a>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function DetalleCaso({ numero }: Readonly<{ numero: number }>) {
  const { fuente, poner } = useDatos()
  const [perfil, setPerfil] = useState<Perfil>('NEGOCIO')
  const { datos, error, cargando } = useCarga<CasoDetalle>(() => fuente.caso(numero, perfil), [fuente, numero, perfil])

  function abrirEnLaboratorio(c: CasoDetalle) {
    poner({ credito: c.credito, instrucciones: c.instrucciones, perfil, origen: `Caso ${c.caso.numero}: ${c.caso.nombre}` })
    window.location.hash = '#/laboratorio'
  }

  return (
    <div>
      <p className="mb-2">
        <a href="#/casos" className="font-semibold text-primary underline">← Todos los casos</a>
      </p>
      <Titulo detalle={datos?.caso.descripcion}>{datos ? `Caso ${datos.caso.numero} · ${datos.caso.nombre}` : `Caso ${numero}`}</Titulo>
      {datos && <p className="-mt-2 mb-4 text-xs text-outline">Fuente: {datos.caso.fuente}</p>}
      <div className="mb-4 flex flex-wrap items-end gap-4">
        <SelectorPerfil valor={perfil} alCambiar={setPerfil} />
        {datos && (
          <button type="button" className="boton-suave" onClick={() => abrirEnLaboratorio(datos)}>
            Abrir en el laboratorio
          </button>
        )}
      </div>
      {cargando && <Cargando />}
      {error && <MensajeError error={error} />}
      {datos && <DetalleEjecucion ejecucion={datos} instrucciones={datos.instrucciones} />}
    </div>
  )
}
