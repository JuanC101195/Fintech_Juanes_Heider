import { useState } from 'react'
import { lineaCaso } from '../casosResumen'
import { Cargando, MensajeError, PERFILES, SelectorPerfil, sinJerga, Titulo } from '../componentes/comunes'
import { DetalleEjecucion } from '../componentes/DetalleEjecucion'
import { Selector } from '../componentes/Selector'
import { useCarga, useDatos } from '../datos'
import type { CasoDetalle, CasoResumen, Perfil } from '../tipos'

export default function Casos({ numero }: Readonly<{ numero?: number }>) {
  return numero ? <DetalleCaso numero={numero} /> : <ListaCasos />
}

function ListaCasos() {
  const { fuente } = useDatos()
  const { datos, error, cargando, recargar } = useCarga<CasoResumen[]>(() => fuente.casos(), [fuente])
  return (
    <div>
      <Titulo detalle="Los casos del Excel de operaciones (más el caso 13) calculados con el motor. Entra a uno para ver periodo a periodo qué pasó.">Casos</Titulo>
      {cargando && <Cargando texto="Cargando los casos…" />}
      {error && <MensajeError error={error} alReintentar={recargar} />}
      {datos?.length === 0 && <p className="text-on-surface-variant">No hay casos para mostrar todavía.</p>}
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
                  <span className="mt-0.5 block text-on-surface-variant">{sinJerga(c.descripcion)}</span>
                  <span className="mt-1 block text-sm text-on-surface-variant">Origen: {c.fuente}</span>
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
  const { datos, error, cargando, recargar } = useCarga<CasoDetalle>(() => fuente.caso(numero, perfil), [fuente, numero, perfil])
  const lista = useCarga<CasoResumen[]>(() => fuente.casos(), [fuente])

  function abrirEnLaboratorio(c: CasoDetalle) {
    poner({ credito: c.credito, instrucciones: c.instrucciones, perfil, caso: { numero: c.caso.numero, nombre: c.caso.nombre } })
    window.location.hash = '#/laboratorio'
  }

  return (
    <div>
      <p className="mb-2">
        <a href="#/casos" className="inline-flex min-h-11 items-center font-semibold text-primary underline">
          ← Todos los casos
        </a>
      </p>
      <Titulo detalle={datos && sinJerga(datos.caso.descripcion)}>{datos ? `Caso ${datos.caso.numero} · ${datos.caso.nombre}` : `Caso ${numero}`}</Titulo>
      {datos && <p className="-mt-3 mb-4 text-sm text-on-surface-variant">Origen: {datos.caso.fuente}</p>}
      <div className="tarjeta mb-6 grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] lg:items-end">
        {lista.datos && (
          <Selector
            etiqueta="Ver otro caso"
            valor={String(numero)}
            opciones={lista.datos.map((c) => ({ valor: String(c.numero), titulo: `Caso ${c.numero} · ${c.nombre}`, descripcion: lineaCaso(c) }))}
            alCambiar={(v) => {
              window.location.hash = `#/casos/${v}`
            }}
          />
        )}
        <SelectorPerfil valor={perfil} alCambiar={setPerfil} />
        {datos && (
          <button type="button" className="boton" onClick={() => abrirEnLaboratorio(datos)}>
            🧪 Probar variaciones en el laboratorio
          </button>
        )}
      </div>
      <p className="sr-only" role="status">
        Calculado con {PERFILES[perfil].nombre.toLowerCase()}
      </p>
      {cargando && <Cargando texto={`Calculando el caso ${numero}…`} />}
      {error && <MensajeError error={error} alReintentar={recargar} />}
      {datos && !cargando && <DetalleEjecucion ejecucion={datos} instrucciones={datos.instrucciones} />}
    </div>
  )
}
