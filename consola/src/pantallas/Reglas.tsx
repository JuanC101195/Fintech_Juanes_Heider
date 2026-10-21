import { Cargando, MensajeError, Titulo } from '../componentes/comunes'
import { useCarga, useDatos } from '../datos'
import type { Reglas as TipoReglas } from '../tipos'

/** Lo que sigue por confirmar (además de las claves cuya fuente dice "por confirmar" en el API). */
export const POR_CONFIRMAR = [
  'Periodicidad quincenal: 24 periodos al año, por confirmar con Diego.',
  'Reglas que el cuestionario ya respondió pero que el motor aún no aplica: esperan desarrollo (ver docs/respuestas-negocio.md).',
]

export function claveSinConfirmar(fuente: string): boolean {
  return /confirmar/i.test(fuente)
}

export default function Reglas() {
  const { fuente } = useDatos()
  const { datos, error, cargando } = useCarga<TipoReglas>(() => fuente.reglas(), [fuente])
  return (
    <div>
      <Titulo detalle="Las reglas de negocio con las que corre el motor en cada perfil. EXCEL reproduce el Excel de Diego; NEGOCIO aplica sus respuestas.">Reglas</Titulo>
      {cargando && <Cargando />}
      {error && <MensajeError error={error} />}
      {datos && <TablaReglas reglas={datos} />}
      <section className="mt-6 rounded-xl bg-aviso p-4 text-on-aviso" aria-labelledby="h-conf">
        <h2 id="h-conf" className="mb-1 font-bold">Por confirmar</h2>
        <ul className="list-disc pl-5 text-sm">
          {POR_CONFIRMAR.map((t) => (
            <li key={t}>{t}</li>
          ))}
        </ul>
      </section>
    </div>
  )
}

export function TablaReglas({ reglas }: Readonly<{ reglas: TipoReglas }>) {
  return (
    <div className="scroll-tabla" tabIndex={0} role="region" aria-label="Tabla de reglas por perfil" style={{ maxHeight: 'none' }}>
      <table>
        <thead>
          <tr>
            <th scope="col">Clave</th>
            <th scope="col" className="izq">Descripción</th>
            <th scope="col" className="izq">EXCEL</th>
            <th scope="col" className="izq">NEGOCIO</th>
            <th scope="col" className="izq">Fuente</th>
          </tr>
        </thead>
        <tbody>
          {reglas.claves.map((k) => {
            const e = reglas.perfiles.EXCEL[k.clave] ?? 'sin tope'
            const n = reglas.perfiles.NEGOCIO[k.clave] ?? 'sin tope'
            return (
              <tr key={k.clave}>
                <th scope="row">{k.clave}</th>
                <td className="izq min-w-64 !whitespace-normal">{k.descripcion}</td>
                <td className="izq">
                  <code>{e}</code>
                </td>
                <td className="izq">
                  <code className={e === n ? '' : 'rounded bg-primary-container px-1 text-on-primary-container'}>{n}</code>
                  {e !== n && <span className="sr-only"> (distinto del perfil EXCEL)</span>}
                </td>
                <td className="izq min-w-36 !whitespace-normal">
                  {claveSinConfirmar(k.fuente) ? <span className="rounded bg-aviso px-1.5 py-0.5 text-xs font-semibold text-on-aviso">{k.fuente}</span> : k.fuente}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
