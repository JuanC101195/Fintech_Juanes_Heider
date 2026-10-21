import { useId, type ReactNode } from 'react'
import { ErrorDeApi, ErrorDeRed } from '../api/cliente'
import { cop } from '../dinero'

export function AvisoDemo() {
  return (
    <div role="status" className="flex items-start gap-2 bg-aviso px-4 py-2 text-sm font-semibold text-on-aviso">
      <span aria-hidden="true">⚠</span>
      <span>
        Datos de demostración
        <span className="font-normal"> — el API no está conectado. Las cifras son de ejemplo y no cambian con los parámetros que escribas.</span>
      </span>
    </div>
  )
}

export function Cargando({ texto = 'Cargando…' }: Readonly<{ texto?: string }>) {
  return (
    <p role="status" className="py-6 text-on-surface-variant">
      {texto}
    </p>
  )
}

const EXPLICACIONES: Record<string, string> = {
  ABONO_MAYOR_QUE_SALDO: 'El abono extraordinario es mayor que el saldo de capital de ese periodo. Baja el valor o usa un prepago total.',
  PLAZO_MAXIMO: 'El plazo supera el máximo permitido de la línea (24 meses).',
  VALOR_MOTO_INVALIDO: 'El valor de la moto debe ser mayor que cero.',
}

function detalleLegible(detalle: Record<string, unknown>): string[] {
  const etiquetas: Record<string, string> = { periodo: 'Periodo', abono: 'Abono', valorAbono: 'Abono', saldo: 'Saldo de capital', meses: 'Meses pedidos', maximoMeses: 'Máximo de meses', maximoCuotas: 'Máximo de cuotas', periodicidad: 'Periodicidad', campo: 'Campo' }
  return Object.entries(detalle).map(([k, v]) => {
    const texto = typeof v === 'string' && /^-?\d+(\.\d+)?$/.test(v) && !['periodo', 'maximoMeses', 'maximoCuotas', 'meses'].includes(k) ? cop(v) : String(v)
    return `${etiquetas[k] ?? k}: ${texto}`
  })
}

/** Errores del API en español claro: el código de negocio, qué significa y el detalle. */
export function MensajeError({ error }: Readonly<{ error: unknown }>) {
  if (!error) return null
  let titulo = 'Algo falló'
  let cuerpo = error instanceof Error ? error.message : String(error)
  let detalle: string[] = []
  let codigo: string | undefined
  if (error instanceof ErrorDeApi) {
    codigo = error.codigo
    titulo = 'El motor rechazó la operación'
    cuerpo = EXPLICACIONES[error.codigo] ?? error.message
    detalle = error.detalle ? detalleLegible(error.detalle) : []
  } else if (error instanceof ErrorDeRed) {
    titulo = 'No se pudo hablar con el API'
  }
  return (
    <div role="alert" className="rounded-xl border border-error bg-error-container p-4 text-on-error-container">
      <p className="font-bold">
        {titulo}
        {codigo && <code className="ml-2 rounded bg-surface-lowest/60 px-1.5 py-0.5 text-xs">{codigo}</code>}
      </p>
      <p className="mt-1">{cuerpo}</p>
      {detalle.length > 0 && (
        <ul className="mt-2 list-disc pl-5 text-sm">
          {detalle.map((d) => (
            <li key={d}>{d}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function Titulo({ children, detalle }: Readonly<{ children: ReactNode; detalle?: ReactNode }>) {
  return (
    <header className="mb-4">
      <h1 className="text-2xl font-bold tracking-tight">{children}</h1>
      {detalle && <p className="mt-1 text-on-surface-variant">{detalle}</p>}
    </header>
  )
}

export function Tarjeta({ etiqueta, valor, ayuda, destacada }: Readonly<{ etiqueta: string; valor: string; ayuda?: string; destacada?: boolean }>) {
  return (
    <div className={`rounded-xl border p-4 ${destacada ? 'border-primary bg-primary-container text-on-primary-container' : 'border-outline-variant bg-surface-lowest'}`}>
      <dt className={`text-xs font-semibold ${destacada ? '' : 'text-on-surface-variant'}`}>{etiqueta}</dt>
      <dd className="cifra mt-1 text-xl font-bold">{valor}</dd>
      {ayuda && <p className={`mt-0.5 text-xs ${destacada ? '' : 'text-on-surface-variant'}`}>{ayuda}</p>}
    </div>
  )
}

interface OpcionSegmento<T extends string> {
  valor: T
  etiqueta: string
  nota?: string
}

/** Control segmentado accesible (radiogroup). */
export function Segmentado<T extends string>({ nombre, valor, opciones, alCambiar, etiqueta }: Readonly<{ nombre: string; valor: T; opciones: OpcionSegmento<T>[]; alCambiar: (v: T) => void; etiqueta: string }>) {
  const id = useId()
  return (
    <fieldset className="min-w-0">
      <legend className="campo-etiqueta" id={id}>
        {etiqueta}
      </legend>
      <div role="radiogroup" aria-labelledby={id} className="flex flex-wrap gap-2">
        {opciones.map((o) => (
          <label key={o.valor} className={`flex min-h-11 cursor-pointer flex-col justify-center rounded-lg border px-3 py-1.5 has-[:focus-visible]:outline has-[:focus-visible]:outline-[3px] has-[:focus-visible]:outline-primary ${valor === o.valor ? 'border-primary bg-primary-container text-on-primary-container' : 'border-outline bg-surface-lowest'}`}>
            <input type="radio" className="sr-only" name={nombre} value={o.valor} checked={valor === o.valor} onChange={() => alCambiar(o.valor)} />
            <span className="text-sm font-semibold">{o.etiqueta}</span>
            {o.nota && <span className="text-xs">{o.nota}</span>}
          </label>
        ))}
      </div>
    </fieldset>
  )
}

export function Campo({ etiqueta, error, ayuda, children }: Readonly<{ etiqueta: string; error?: string; ayuda?: string; children: (props: { id: string; 'aria-invalid': boolean; 'aria-describedby'?: string }) => ReactNode }>) {
  const id = useId()
  const nota = error ?? ayuda
  return (
    <div className="min-w-0">
      <label htmlFor={id} className="campo-etiqueta">
        {etiqueta}
      </label>
      {children({ id, 'aria-invalid': Boolean(error), 'aria-describedby': nota ? `${id}-n` : undefined })}
      {nota && (
        <p id={`${id}-n`} className={`mt-1 text-xs ${error ? 'text-error' : 'text-on-surface-variant'}`}>
          {nota}
        </p>
      )}
    </div>
  )
}

export function SelectorPerfil({ valor, alCambiar }: Readonly<{ valor: 'EXCEL' | 'NEGOCIO'; alCambiar: (p: 'EXCEL' | 'NEGOCIO') => void }>) {
  return (
    <Segmentado
      nombre="perfil"
      etiqueta="Perfil de reglas"
      valor={valor}
      alCambiar={alCambiar}
      opciones={[
        { valor: 'NEGOCIO', etiqueta: 'Negocio', nota: 'Respuestas de Diego' },
        { valor: 'EXCEL', etiqueta: 'Excel', nota: 'Como el Excel' },
      ]}
    />
  )
}
