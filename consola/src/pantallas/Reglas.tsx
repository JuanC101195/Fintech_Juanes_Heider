import { Cargando, MensajeError, PERFILES, Titulo } from '../componentes/comunes'
import { useCarga, useDatos } from '../datos'
import { cop } from '../dinero'
import type { Reglas as TipoReglas } from '../tipos'

/** Lo que sigue por confirmar (además de las reglas cuya referencia dice "por confirmar" en el API). */
export const POR_CONFIRMAR = [
  'Reglas que Diego ya respondió en el cuestionario pero que el motor todavía no aplica: están en desarrollo.',
]

export function claveSinConfirmar(fuente: string): boolean {
  return /confirmar/i.test(fuente)
}

/** Valores de las reglas (nombres internos del motor) dichos en lenguaje de negocio. */
const VALORES: Record<string, string> = {
  SERVICIOS_ANTES_DE_CAPITAL: 'Cobranza → mora → interés → servicios → capital',
  CAPITAL_ANTES_DE_SERVICIOS: 'Cobranza → mora → interés → capital → servicios',
  VENCIDO_MAS_DIAS: 'Sobre el capital vencido más los días de atraso de la cuota',
  SOLO_VENCIDO: 'Solo sobre el capital vencido',
  SALDO_TOTAL: 'Sobre todo el saldo, incluido lo vencido',
  SALDO_VIGENTE: 'Solo sobre el capital al día (lo vencido paga mora)',
  MONTO_INICIAL: 'Fijos, sobre el monto financiado al inicio',
  SALDO: 'Sobre el saldo de capital de cada periodo',
  SOLO_CAPITAL: 'Todo el abono va a capital; lo pendiente sigue pendiente',
  PRIMERO_PENDIENTES: 'Primero paga lo pendiente y el resto va a capital',
  PRELACION_COMPLETA: 'Paga lo pendiente y el sobrante vuelve a pasar por la prelación (cobranza, mora, interés, servicios y capital)',
  MISMA_FECHA_FINAL: 'Se conserva la fecha final y cambia la cuota',
  MISMA_CUOTA: 'Se conserva la cuota y cambia el número de cuotas',
  true: 'Sí',
  false: 'No',
}

/** Valor de una regla para mostrar. Lo que no esté traducido se muestra en minúsculas y sin guiones bajos. */
export function valorRegla(clave: string, valor: string | null): string {
  if (valor === null || valor === '') return clave === 'topeCobranzaPorPago' ? 'Sin tope' : 'Sin definir'
  if (VALORES[valor]) return VALORES[valor]
  if (clave === 'margenUsura') return `${valor} ${valor === '1' ? 'punto' : 'puntos'} por debajo de la usura`
  if (clave === 'topeCobranzaPorPago' && /^\d+(\.\d+)?$/.test(valor)) return `Hasta ${cop(valor)} por pago`
  const t = valor.replaceAll('_', ' ').toLowerCase()
  return t.charAt(0).toUpperCase() + t.slice(1)
}

export default function Reglas() {
  const { fuente } = useDatos()
  const { datos, error, cargando, recargar } = useCarga<TipoReglas>(() => fuente.reglas(), [fuente])
  return (
    <div>
      <Titulo
        detalle={
          <>
            Las reglas con las que calcula el motor. Hay dos juegos: <strong>{PERFILES.EXCEL.nombre}</strong> reproduce el Excel de casos de Diego tal cual, y <strong>{PERFILES.NEGOCIO.nombre}</strong> aplica sus respuestas al cuestionario. Donde cambian, se resalta.
          </>
        }
      >
        Reglas
      </Titulo>
      {cargando && <Cargando texto="Cargando las reglas…" />}
      {error && <MensajeError error={error} alReintentar={recargar} />}
      {datos && <ListaReglas reglas={datos} />}
      <section className="mt-6 rounded-xl bg-aviso p-4 text-on-aviso" aria-labelledby="h-conf">
        <h2 id="h-conf" className="mb-1 text-lg font-bold">
          Por confirmar
        </h2>
        <ul className="list-disc pl-5">
          {POR_CONFIRMAR.map((t) => (
            <li key={t}>{t}</li>
          ))}
        </ul>
      </section>
    </div>
  )
}

export function ListaReglas({ reglas }: Readonly<{ reglas: TipoReglas }>) {
  if (reglas.claves.length === 0) return <p className="text-on-surface-variant">El servidor no envió reglas. Vuelve a cargar la página en un momento.</p>
  return (
    <ul className="grid gap-3 xl:grid-cols-2" aria-label="Reglas por perfil">
      {reglas.claves.map((k) => {
        const e = valorRegla(k.clave, reglas.perfiles.EXCEL[k.clave] ?? null)
        const n = valorRegla(k.clave, reglas.perfiles.NEGOCIO[k.clave] ?? null)
        const cambia = e !== n
        return (
          <li key={k.clave} className={`tarjeta ${cambia ? 'border-primary' : ''}`}>
            <div className="mb-2 flex flex-wrap items-start justify-between gap-2">
              <h3 className="font-bold">{k.descripcion}</h3>
              {cambia && <span className="rounded-full bg-primary-container px-2.5 py-0.5 text-sm font-semibold text-on-primary-container">Cambia según las reglas</span>}
            </div>
            <dl className="grid gap-2 sm:grid-cols-2">
              <div className="rounded-lg bg-surface-low p-3">
                <dt className="text-sm font-semibold text-on-surface-variant">{PERFILES.EXCEL.nombre}</dt>
                <dd>{e}</dd>
              </div>
              <div className={`rounded-lg p-3 ${cambia ? 'bg-primary-container/40' : 'bg-surface-low'}`}>
                <dt className="text-sm font-semibold text-on-surface-variant">{PERFILES.NEGOCIO.nombre}</dt>
                <dd className={cambia ? 'font-semibold' : ''}>{n}</dd>
              </div>
            </dl>
            <p className="mt-2 text-sm text-on-surface-variant">
              Pregunta del cuestionario: {claveSinConfirmar(k.fuente) ? <span className="rounded bg-aviso px-1.5 py-0.5 font-semibold text-on-aviso">{k.fuente}</span> : k.fuente}
            </p>
          </li>
        )
      })}
    </ul>
  )
}
