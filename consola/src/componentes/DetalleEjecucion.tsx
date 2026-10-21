import { categoria, describir, type CategoriaEvento } from '../api/instrucciones'
import { cop, esCero, sumar } from '../dinero'
import type { Asiento, Ejecucion, Instruccion, Movimiento } from '../tipos'
import { Tarjeta } from './comunes'

const ETIQUETA_ESTADO: Record<string, string> = {
  AL_DIA: 'Al día',
  EN_MORA: 'En mora',
  CANCELADO: 'Cancelado',
  RECUPERADO: 'Recuperado (dación)',
  RETOMADO: 'Retomado',
}

export function nombreEstado(estado: string): string {
  return ETIQUETA_ESTADO[estado] ?? estado.replaceAll('_', ' ').toLowerCase()
}

function Estado({ estado }: Readonly<{ estado: string }>) {
  const clase =
    estado === 'AL_DIA' ? 'bg-tertiary-container text-on-tertiary-container' : estado === 'EN_MORA' ? 'bg-secondary-fixed text-on-secondary-fixed' : 'bg-primary-container text-on-primary-container'
  const icono = estado === 'AL_DIA' ? '●' : estado === 'EN_MORA' ? '▲' : '■'
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${clase}`}>
      <span aria-hidden="true">{icono}</span>
      {nombreEstado(estado)}
    </span>
  )
}

// ---------- línea de tiempo ----------

export interface EventoLinea {
  desde: number
  hasta: number
  categoria: CategoriaEvento
  texto: string
}

/** Agrupa las instrucciones iguales y consecutivas (por ejemplo, tres semanas sin pago) en un solo evento. */
export function agruparEventos(instrucciones: Instruccion[]): EventoLinea[] {
  const ordenadas = [...instrucciones].sort((a, b) => a.periodo - b.periodo)
  const grupos: EventoLinea[] = []
  for (const i of ordenadas) {
    const texto = describir(i)
    const ultimo = grupos.at(-1)
    if (ultimo && ultimo.texto === texto && ultimo.hasta === i.periodo - 1) ultimo.hasta = i.periodo
    else grupos.push({ desde: i.periodo, hasta: i.periodo, categoria: categoria(i.tipo), texto })
  }
  return grupos
}

const ESTILO_EVENTO: Record<CategoriaEvento, { punto: string; nombre: string; icono: string }> = {
  'sin-pago': { punto: 'bg-secondary-fixed text-on-secondary-fixed', nombre: 'Sin pago', icono: '▲' },
  abono: { punto: 'bg-tertiary-container text-on-tertiary-container', nombre: 'Abono', icono: '＋' },
  tasa: { punto: 'bg-primary-container text-on-primary-container', nombre: 'Tasa', icono: '％' },
  dacion: { punto: 'bg-error-container text-on-error-container', nombre: 'Recuperación', icono: '■' },
  pago: { punto: 'bg-surface-high text-on-surface', nombre: 'Pago', icono: '●' },
}

export function LineaTiempo({ instrucciones }: Readonly<{ instrucciones: Instruccion[] }>) {
  const eventos = agruparEventos(instrucciones)
  if (eventos.length === 0) return <p className="text-on-surface-variant">Sin eventos: todos los periodos se pagan completos y a tiempo.</p>
  return (
    <ol className="grid gap-2" aria-label="Línea de tiempo de eventos">
      {eventos.map((e) => {
        const est = ESTILO_EVENTO[e.categoria]
        return (
          <li key={`${e.desde}-${e.texto}`} className="flex items-start gap-3">
            <span aria-hidden="true" className={`mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-bold ${est.punto}`}>
              {est.icono}
            </span>
            <div className="min-w-0">
              <p className="text-xs font-semibold text-on-surface-variant">
                {e.desde === e.hasta ? `Periodo ${e.desde}` : `Periodos ${e.desde} a ${e.hasta}`} · {est.nombre}
              </p>
              <p>{e.texto}</p>
            </div>
          </li>
        )
      })}
    </ol>
  )
}

// ---------- tabla de movimientos ----------

function pendientesPagados(m: Movimiento): string {
  const p = m.pendientesPagadosExtra
  return p && typeof p === 'object' ? (p.total ?? '0') : '0'
}

export function TablaMovimientos({ movimientos, instrucciones }: Readonly<{ movimientos: Movimiento[]; instrucciones: Instruccion[] }>) {
  const marcados = new Map<number, CategoriaEvento>()
  for (const i of instrucciones) marcados.set(i.periodo, categoria(i.tipo))
  const c = (v: string | undefined) => (esCero(v) ? '—' : cop(v))
  return (
    <div className="scroll-tabla dos" tabIndex={0} role="region" aria-label="Tabla periodo a periodo">
      <table>
        <thead>
          <tr className="grupos">
            <th scope="col" rowSpan={2}>Periodo</th>
            <th scope="colgroup" colSpan={4}>Pago</th>
            <th scope="colgroup" colSpan={5}>Aplicación por prelación</th>
            <th scope="colgroup" colSpan={7}>Saldos al cierre</th>
            <th scope="colgroup" colSpan={2} className="izq">Estado</th>
          </tr>
          <tr className="cols">
            <th scope="col">Pago recibido</th>
            <th scope="col">Abono extra</th>
            <th scope="col">Prepago</th>
            <th scope="col">Pendientes pagados</th>
            <th scope="col">Cobranza</th>
            <th scope="col">Mora</th>
            <th scope="col">Interés</th>
            <th scope="col">Servicios</th>
            <th scope="col">Capital</th>
            <th scope="col">CxC cobranza</th>
            <th scope="col">CxC mora</th>
            <th scope="col">CxC interés</th>
            <th scope="col">CxC servicios</th>
            <th scope="col">Capital vencido</th>
            <th scope="col">Capital vigente</th>
            <th scope="col">Saldo final</th>
            <th scope="col" className="izq">Estado</th>
            <th scope="col">Cuotas vencidas</th>
          </tr>
        </thead>
        <tbody>
          {movimientos.map((m) => {
            const marca = marcados.get(m.periodo)
            return (
              <tr key={m.periodo} className={marca && marca !== 'pago' ? 'bg-primary-container/15' : undefined}>
                <th scope="row" title={m.etiqueta}>
                  {m.periodo}
                </th>
                <td>{c(m.pagoRecibido)}</td>
                <td>{c(m.abonoExtra)}</td>
                <td>{c(m.prepagoCapital)}</td>
                <td>{c(pendientesPagados(m))}</td>
                <td>{c(m.pagoCobranza)}</td>
                <td>{c(m.pagoMora)}</td>
                <td>{c(m.pagoInteresCorriente)}</td>
                <td>{c(m.pagoServicios)}</td>
                <td>{c(m.pagoCapital)}</td>
                <td>{c(m.cxcCobranza)}</td>
                <td>{c(m.cxcMora)}</td>
                <td>{c(m.cxcInteresCorriente)}</td>
                <td>{c(m.cxcServicios)}</td>
                <td>{c(m.capitalVencido)}</td>
                <td>{c(m.capitalVigente)}</td>
                <td className="font-semibold">{cop(m.saldoFinal)}</td>
                <td className="izq">
                  <Estado estado={m.estado} />
                  {m.etiqueta && <span className="ml-2 text-xs text-on-surface-variant">{m.etiqueta}</span>}
                </td>
                <td>{String(m.cuotasVencidas ?? 0)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

// ---------- asientos ----------

export function ListaAsientos({ asientos }: Readonly<{ asientos: Asiento[] }>) {
  if (asientos.length === 0) return <p className="text-on-surface-variant">No hay asientos para mostrar.</p>
  const descuadrados = asientos.filter((a) => !a.cuadra).length
  return (
    <div>
      <p className="mb-2 text-sm text-on-surface-variant" role="status">
        {asientos.length} asientos · {descuadrados === 0 ? 'todos cuadran' : `${descuadrados} no cuadran`}
      </p>
      <div className="grid gap-2">
        {asientos.map((a) => {
          const debito = sumar(...a.lineas.map((l) => l.debito))
          const credito = sumar(...a.lineas.map((l) => l.credito))
          return (
            <details key={`${a.periodo}-${a.evento}`} className="rounded-xl border border-outline-variant bg-surface-lowest">
              <summary className="flex min-h-11 cursor-pointer flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2">
                <span className="font-semibold">Periodo {a.periodo}</span>
                <span className="text-sm text-on-surface-variant">{a.evento.replaceAll('_', ' ').toLowerCase()}</span>
                <span className={`ml-auto rounded-full px-2 py-0.5 text-xs font-semibold ${a.cuadra ? 'bg-tertiary-container text-on-tertiary-container' : 'bg-error-container text-on-error-container'}`}>
                  {a.cuadra ? '✓ Cuadra' : '✗ No cuadra'}
                </span>
              </summary>
              <div className="scroll-tabla m-3 mt-0">
                <table>
                  <thead>
                    <tr>
                      <th scope="col">Cuenta</th>
                      <th scope="col">Débito</th>
                      <th scope="col">Crédito</th>
                    </tr>
                  </thead>
                  <tbody>
                    {a.lineas.map((l, k) => (
                      <tr key={`${l.cuenta}-${k}`}>
                        <th scope="row">{l.cuenta.replaceAll('_', ' ')}</th>
                        <td>{c0(l.debito)}</td>
                        <td>{c0(l.credito)}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <td>Total</td>
                      <td>{cop(debito)}</td>
                      <td>{cop(credito)}</td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </details>
          )
        })}
      </div>
    </div>
  )
}

const c0 = (v: string) => (esCero(v) ? '—' : cop(v))

// ---------- conjunto ----------

export function DetalleEjecucion({ ejecucion, instrucciones }: Readonly<{ ejecucion: Ejecucion; instrucciones: Instruccion[] }>) {
  const r = ejecucion.resumen
  return (
    <div className="grid gap-6">
      <dl className="grid grid-cols-1 gap-3 min-[480px]:grid-cols-2 lg:grid-cols-4">
        <Tarjeta destacada etiqueta="Estado final" valor={nombreEstado(r.estadoFinal)} ayuda={`Termina en el periodo ${r.periodoFinal}`} />
        <Tarjeta etiqueta="Saldo final" valor={cop(r.saldoFinal)} />
        <Tarjeta etiqueta="Total pagado por el cliente" valor={cop(r.totalPagadoCliente)} />
        <Tarjeta etiqueta="Cuotas vencidas (máx.)" valor={String(r.cuotasVencidasMax)} />
      </dl>
      <section aria-labelledby="h-eventos">
        <h2 id="h-eventos" className="mb-2 text-lg font-bold">Eventos</h2>
        <LineaTiempo instrucciones={instrucciones} />
      </section>
      <section aria-labelledby="h-mov">
        <h2 id="h-mov" className="mb-2 text-lg font-bold">Periodo a periodo</h2>
        <TablaMovimientos movimientos={ejecucion.movimientos} instrucciones={instrucciones} />
      </section>
      {ejecucion.asientos && (
        <section aria-labelledby="h-asi">
          <h2 id="h-asi" className="mb-2 text-lg font-bold">Asientos contables</h2>
          <ListaAsientos asientos={ejecucion.asientos} />
        </section>
      )}
    </div>
  )
}
