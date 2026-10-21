import { useId, type ReactNode } from 'react'
import { ErrorDeApi, ErrorDeRed } from '../api/cliente'
import { useNotaEspera } from '../datos'
import { cop } from '../dinero'

export function AvisoDemo() {
  return (
    <div role="status" className="flex items-start gap-2 bg-aviso px-4 py-2 text-sm font-semibold text-on-aviso">
      <span aria-hidden="true">⚠</span>
      <span>
        Datos de demostración
        <span className="font-normal"> — no hay conexión con el servidor del motor. Las cifras son de ejemplo y no cambian con lo que escribas.</span>
      </span>
    </div>
  )
}

/** Indicador de espera; mientras el servidor no haya respondido nunca, explica que puede estar despertando. */
export function Cargando({ texto = 'Cargando…' }: Readonly<{ texto?: string }>) {
  const nota = useNotaEspera()
  return (
    <div role="status" className="flex items-start gap-3 py-4 text-on-surface-variant">
      <span aria-hidden="true" className="mt-0.5 inline-block size-5 shrink-0 animate-spin rounded-full border-[3px] border-outline-variant border-t-primary motion-reduce:animate-none" />
      <p>
        <span className="font-semibold text-on-surface">{texto}</span>
        {nota && <span className="block text-sm">{nota}</span>}
      </p>
    </div>
  )
}

const EXPLICACIONES: Record<string, string> = {
  ABONO_MAYOR_QUE_SALDO: 'El abono extraordinario es mayor que el saldo de capital de ese periodo. Baja el valor o usa «Paga todo» para cancelar el crédito.',
  PLAZO_MAXIMO: 'El plazo supera el máximo permitido para la línea de crédito.',
  VALOR_MOTO_INVALIDO: 'El valor de la moto debe ser mayor que cero.',
}

function detalleLegible(detalle: Record<string, unknown>): string[] {
  const etiquetas: Record<string, string> = { periodo: 'Periodo', abono: 'Abono', valorAbono: 'Abono', saldo: 'Saldo de capital', meses: 'Meses pedidos', maximoMeses: 'Máximo de meses', maximoCuotas: 'Máximo de cuotas', periodicidad: 'Periodicidad', campo: 'Campo' }
  return Object.entries(detalle).map(([k, v]) => {
    const texto = typeof v === 'string' && /^-?\d+(\.\d+)?$/.test(v) && !['periodo', 'maximoMeses', 'maximoCuotas', 'meses'].includes(k) ? cop(v) : String(v)
    return `${etiquetas[k] ?? k}: ${texto}`
  })
}

/** Errores en español claro: qué pasó, qué hacer y, para soporte, el código técnico. */
export function MensajeError({ error, alReintentar }: Readonly<{ error: unknown; alReintentar?: () => void }>) {
  if (!error) return null
  let titulo = 'Algo falló'
  let cuerpo = error instanceof Error ? error.message : String(error)
  let detalle: string[] = []
  let codigo: string | undefined
  if (error instanceof ErrorDeApi) {
    codigo = error.codigo
    titulo = 'El motor no aceptó estos datos'
    cuerpo = EXPLICACIONES[error.codigo] ?? error.message
    detalle = error.detalle ? detalleLegible(error.detalle) : []
  } else if (error instanceof ErrorDeRed) {
    titulo = 'No pudimos conectar con el servidor del motor'
    cuerpo = 'Revisa tu conexión a internet y vuelve a intentar. Si acabas de abrir la consola, espera unos segundos: el servidor puede estar despertando.'
  }
  return (
    <div role="alert" className="rounded-xl border border-error bg-error-container p-4 text-on-error-container">
      <p className="font-bold">
        <span aria-hidden="true">✗ </span>
        {titulo}
      </p>
      <p className="mt-1">{cuerpo}</p>
      {detalle.length > 0 && (
        <ul className="mt-2 list-disc pl-5">
          {detalle.map((d) => (
            <li key={d}>{d}</li>
          ))}
        </ul>
      )}
      {codigo && <p className="mt-2 text-sm">Código para soporte: <code className="rounded bg-surface-lowest/60 px-1.5 py-0.5">{codigo}</code></p>}
      {alReintentar && (
        <button type="button" className="boton-suave mt-3" onClick={alReintentar}>
          Volver a intentar
        </button>
      )}
    </div>
  )
}

export function Titulo({ children, detalle }: Readonly<{ children: ReactNode; detalle?: ReactNode }>) {
  return (
    <header className="mb-5">
      <h1 className="text-2xl font-bold tracking-tight md:text-3xl">{children}</h1>
      {detalle && <p className="mt-1 max-w-3xl text-on-surface-variant">{detalle}</p>}
    </header>
  )
}

export function Tarjeta({ etiqueta, valor, ayuda, destacada }: Readonly<{ etiqueta: string; valor: string; ayuda?: string; destacada?: boolean }>) {
  return (
    <div className={`rounded-xl border p-4 ${destacada ? 'border-primary bg-primary-container text-on-primary-container' : 'border-outline-variant bg-surface-lowest'}`}>
      <dt className={`text-sm font-semibold ${destacada ? '' : 'text-on-surface-variant'}`}>{etiqueta}</dt>
      <dd className="cifra mt-1 text-2xl font-bold">{valor}</dd>
      {ayuda && <p className={`mt-0.5 text-sm ${destacada ? '' : 'text-on-surface-variant'}`}>{ayuda}</p>}
    </div>
  )
}

export interface OpcionSegmento<T extends string> {
  valor: T
  etiqueta: string
  nota?: string
  icono?: string
}

/**
 * Opciones pocas y a la vista (radiogroup): `fila` son botones segmentados; `tarjetas` son tarjetas grandes con
 * ícono y una frase. Reemplaza al <select> nativo, cuya lista en Windows sale con letra diminuta.
 */
export function Segmentado<T extends string>({
  nombre,
  valor,
  opciones,
  alCambiar,
  etiqueta,
  ayuda,
  diseno = 'fila',
  columnas = 'grid-cols-1 min-[480px]:grid-cols-2',
}: Readonly<{ nombre: string; valor: T; opciones: OpcionSegmento<T>[]; alCambiar: (v: T) => void; etiqueta: string; ayuda?: string; diseno?: 'fila' | 'tarjetas'; columnas?: string }>) {
  const id = useId()
  return (
    <fieldset className="min-w-0">
      <legend className="campo-etiqueta" id={id}>
        {etiqueta}
      </legend>
      {ayuda && <p className="-mt-1 mb-2 text-sm text-on-surface-variant">{ayuda}</p>}
      <div role="radiogroup" aria-labelledby={id} className={diseno === 'fila' ? 'flex flex-wrap gap-2' : `grid gap-2 ${columnas}`}>
        {opciones.map((o) => (
          <label key={o.valor} className={diseno === 'fila' ? 'opcion-tarjeta min-w-[7.5rem] flex-col justify-center gap-0 py-2' : 'opcion-tarjeta items-start'}>
            <input type="radio" className="sr-only" name={nombre} value={o.valor} checked={valor === o.valor} onChange={() => alCambiar(o.valor)} />
            {o.icono && (
              <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface-high text-lg">
                {o.icono}
              </span>
            )}
            <span className="min-w-0">
              <span className="block font-semibold">
                {valor === o.valor && <span aria-hidden="true" className="mr-1 text-primary">✓</span>}
                {o.etiqueta}
              </span>
              {o.nota && <span className="block text-sm text-on-surface-variant">{o.nota}</span>}
            </span>
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
        <p id={`${id}-n`} className={`mt-1 text-sm ${error ? 'font-semibold text-error' : 'text-on-surface-variant'}`}>
          {error && <span aria-hidden="true">✗ </span>}
          {nota}
        </p>
      )}
    </div>
  )
}

const PALABRAS_INTERNAS: Record<string, string> = { SEMANAL: 'semanal', QUINCENAL: 'quincenal', MENSUAL: 'mensual', NEGOCIO: '«reglas de negocio»', EXCEL: '«como el Excel»' }

/** Textos que manda el API ("Con NEGOCIO…", "QUINCENAL: 24 periodos") sin nombres internos en mayúsculas. */
export function sinJerga(texto: string): string {
  return texto.replace(/\b(SEMANAL|QUINCENAL|MENSUAL|NEGOCIO|EXCEL)\b/g, (m) => PALABRAS_INTERNAS[m])
}

/** Los dos juegos de reglas del motor, dichos en lenguaje de negocio. */
export const PERFILES = {
  NEGOCIO: { nombre: 'Reglas de negocio', nota: 'Con las respuestas de Diego al cuestionario' },
  EXCEL: { nombre: 'Como el Excel', nota: 'Reproduce el Excel de casos tal cual' },
} as const

export function SelectorPerfil({ valor, alCambiar }: Readonly<{ valor: 'EXCEL' | 'NEGOCIO'; alCambiar: (p: 'EXCEL' | 'NEGOCIO') => void }>) {
  return (
    <Segmentado
      nombre="perfil"
      etiqueta="¿Con qué reglas calcular?"
      valor={valor}
      alCambiar={alCambiar}
      opciones={[
        { valor: 'NEGOCIO', etiqueta: PERFILES.NEGOCIO.nombre, nota: PERFILES.NEGOCIO.nota },
        { valor: 'EXCEL', etiqueta: PERFILES.EXCEL.nombre, nota: PERFILES.EXCEL.nota },
      ]}
    />
  )
}
