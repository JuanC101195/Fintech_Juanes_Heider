import { ESTADOS, type Kpis, type Previsualizacion, type ResultadoOpcion } from './escenarios'
import { cop, numero } from './formato'
import type { Pregunta } from './tipos'

// Muestra lo que calculó el motor para cada opción de una pregunta: cifras clave, gráfico del
// saldo de capital semana a semana y cómo se repartió el pago en las semanas clave.

type ClaveKpi = keyof Kpis

const ETIQUETAS: Record<ClaveKpi, string> = {
  cuotaInicial: 'Cuota inicial',
  montoFinanciado: 'Monto financiado',
  cuotaSemanal: 'Cuota semanal total',
  semanaFinal: 'Última semana',
  estadoFinal: 'Cómo termina',
  saldoPendiente: 'Queda debiendo',
  totalPagado: 'Total que paga el cliente',
  interesPagado: 'Interés corriente pagado',
  moraPagada: 'Mora pagada',
  cobranzaPagada: 'Cobranza pagada',
  serviciosPagados: 'Servicios pagados',
  capitalVencidoMaximo: 'Capital vencido máximo',
  semanasEnMora: 'Semanas en mora',
  deudaAExtinguir: 'Deuda que se extingue',
  faltanteFcc: 'Faltante que cubre el FCC',
  excedenteDeudor: 'Excedente para el cliente',
}

const KPIS_POR_PREGUNTA: Record<string, ClaveKpi[]> = {
  P1: ['semanaFinal', 'totalPagado', 'moraPagada', 'capitalVencidoMaximo', 'semanasEnMora'],
  P3: ['estadoFinal', 'semanaFinal', 'saldoPendiente', 'moraPagada', 'totalPagado'],
  PDOBLE: ['semanaFinal', 'totalPagado', 'interesPagado', 'moraPagada'],
  PTOPE: ['estadoFinal', 'semanaFinal', 'saldoPendiente', 'totalPagado', 'cobranzaPagada', 'moraPagada'],
  P7: ['deudaAExtinguir', 'faltanteFcc', 'excedenteDeudor'],
  S1: ['serviciosPagados', 'totalPagado', 'semanaFinal'],
  S4: ['serviciosPagados', 'totalPagado', 'semanaFinal'],
  INICIAL: ['cuotaInicial', 'montoFinanciado', 'cuotaSemanal', 'serviciosPagados', 'totalPagado'],
}

const COLORES = ['#006b5e', '#b60f3b', '#7c5800', '#4a5a8a']

function valorKpi(k: Kpis, clave: ClaveKpi): string {
  const v = k[clave]
  if (clave === 'estadoFinal') return ESTADOS[String(v)] ?? String(v)
  if (clave === 'semanaFinal' || clave === 'semanasEnMora') return numero(Number(v))
  return cop(Number(v))
}

interface Columna {
  clave: string
  titulo: string
  esExcel: boolean
  elegida: boolean
  resultado: ResultadoOpcion
  color: string
}

/** Columnas del comparador: una por opción con escenario y, si la pregunta pide un valor, una por valor sugerido. */
export function columnasDe(p: Pregunta, prev: Previsualizacion, codigo?: string, valor?: number): Omit<Columna, 'color'>[] {
  const columnas: Omit<Columna, 'color'>[] = []
  for (const o of p.opciones) {
    if (o.escenario && prev.opciones[o.escenario]) {
      columnas.push({ clave: o.escenario, titulo: o.texto, esExcel: Boolean(o.esExcel), elegida: o.codigo === codigo, resultado: prev.opciones[o.escenario] })
    }
  }
  for (const s of p.valor?.sugeridos ?? []) {
    if (s.escenario && prev.opciones[s.escenario]) {
      const opcionValor = p.opciones.find((o) => o.pideValor)
      columnas.push({
        clave: s.escenario,
        titulo: `${p.valor?.etiqueta ?? 'Valor'}: ${p.valor?.unidad === 'COP' ? cop(s.valor) : numero(s.valor)}`,
        esExcel: false,
        elegida: opcionValor?.codigo === codigo && valor === s.valor,
        resultado: prev.opciones[s.escenario],
      })
    }
  }
  return columnas
}

function Grafico({ columnas }: { columnas: Columna[] }) {
  const ancho = 600
  const alto = 170
  const largo = Math.max(...columnas.map((c) => c.resultado.serieSaldo.length))
  const maximo = Math.max(1, ...columnas.flatMap((c) => c.resultado.serieSaldo))
  const x = (i: number) => (i / Math.max(1, largo - 1)) * ancho
  const y = (v: number) => alto - (v / maximo) * alto
  return (
    <figure className="rounded-xl bg-surface-container-low p-space-md">
      <figcaption className="mb-space-sm text-label-md font-semibold text-on-surface-variant">Saldo de capital semana a semana</figcaption>
      <svg viewBox={`0 0 ${ancho} ${alto}`} preserveAspectRatio="none" className="h-36 w-full" role="img" aria-label="Gráfico del saldo de capital por opción">
        {columnas.map((c) => (
          <polyline
            key={c.clave}
            fill="none"
            stroke={c.color}
            strokeWidth={c.elegida ? 3 : 1.6}
            strokeDasharray={c.esExcel ? undefined : c.elegida ? undefined : '6 4'}
            vectorEffect="non-scaling-stroke"
            points={c.resultado.serieSaldo.map((v, i) => `${x(i)},${y(v)}`).join(' ')}
          />
        ))}
      </svg>
      <div className="mt-space-xs flex justify-between text-label-sm text-on-surface-variant">
        <span>Semana 0</span>
        <span>Semana {largo - 1}</span>
      </div>
      <ul className="mt-space-sm flex flex-wrap gap-x-space-lg gap-y-space-xs text-label-md">
        {columnas.map((c) => (
          <li key={c.clave} className="flex items-center gap-space-xs">
            <span className="inline-block h-1 w-5 rounded-full" style={{ background: c.color }} />
            <span className="text-on-surface">{c.titulo}</span>
          </li>
        ))}
      </ul>
    </figure>
  )
}

export function Comparador({ pregunta, prev, codigo, valor }: { pregunta: Pregunta; prev: Previsualizacion; codigo?: string; valor?: number }) {
  const columnas: Columna[] = columnasDe(pregunta, prev, codigo, valor).map((c, i) => ({ ...c, color: COLORES[i % COLORES.length] }))
  const kpis = KPIS_POR_PREGUNTA[pregunta.previsualizacion ?? ''] ?? ['totalPagado', 'semanaFinal']
  if (columnas.length === 0) return null

  return (
    <section aria-label="Qué pasa con cada opción" className="flex flex-col gap-space-md">
      <div>
        <h4 className="text-title-md text-on-surface">Qué pasa con cada opción</h4>
        <p className="text-body-md text-on-surface-variant">Calculado con el motor sobre el {prev.caso}.</p>
      </div>

      <div className="grid grid-cols-1 gap-space-sm sm:grid-cols-2 xl:grid-cols-4">
        {columnas.map((c) => (
          <article
            key={c.clave}
            className={`flex flex-col gap-space-sm rounded-xl p-space-md ${c.elegida ? 'bg-primary-container/15 ring-2 ring-primary' : 'bg-surface-container-low'}`}
          >
            <header className="flex items-start gap-space-sm">
              <span className="mt-1.5 inline-block h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: c.color }} />
              <div className="min-w-0">
                <p className="text-label-lg text-on-surface">{c.titulo}</p>
                <div className="mt-1 flex flex-wrap gap-space-xs">
                  {c.esExcel && <span className="rounded-full bg-surface-container-high px-space-sm py-0.5 text-label-sm text-on-surface-variant">Así está hoy en el Excel</span>}
                  {c.elegida && <span className="rounded-full bg-primary px-space-sm py-0.5 text-label-sm text-on-primary">Su respuesta</span>}
                </div>
              </div>
            </header>
            <dl className="flex flex-col gap-1 text-body-md">
              {kpis.map((k) => (
                <div key={k} className="flex flex-wrap justify-between gap-x-space-sm">
                  <dt className="text-on-surface-variant">{ETIQUETAS[k]}</dt>
                  <dd className="cifra font-semibold text-on-surface">{valorKpi(c.resultado.kpis, k)}</dd>
                </div>
              ))}
            </dl>
            {c.resultado.semanas.length > 0 && (
              <details className="text-body-md">
                <summary className="cursor-pointer text-label-md font-semibold text-primary">Cómo se reparte el pago</summary>
                <div className="mt-space-sm flex flex-col gap-space-sm">
                  {c.resultado.semanas.map((s) => (
                    <dl key={s.periodo} className="rounded-lg bg-surface-container-lowest p-space-sm">
                      <dt className="mb-1 text-label-md font-semibold text-on-surface">Semana {s.periodo} · paga {cop(s.pagoRecibido)}</dt>
                      {[
                        ['Cobranza', s.cobranza],
                        ['Mora', s.mora],
                        ['Interés corriente', s.interes],
                        ['Servicios', s.servicios],
                        ['Capital', s.capital],
                        ['Capital vencido al cierre', s.capitalVencido],
                      ].map(([etiqueta, v]) => (
                        <dd key={etiqueta} className="flex justify-between gap-space-sm text-label-md text-on-surface-variant">
                          <span>{etiqueta}</span>
                          <span className="cifra text-on-surface">{cop(Number(v))}</span>
                        </dd>
                      ))}
                    </dl>
                  ))}
                </div>
              </details>
            )}
          </article>
        ))}
      </div>

      <Grafico columnas={columnas} />
    </section>
  )
}
