import { comparar, cop, copCentavos, esCero, restar } from '../dinero'
import { fechaLarga, reglaVencimiento, type ErroresFechas, type Fechas } from '../fechas'
import type { Periodicidad, ProrrateoPrimeraCuota } from '../tipos'
import { Campo } from './comunes'

/**
 * Fecha de desembolso y primer vencimiento, opcionales (P5-PRORRATEO). Sin ellas el motor no prorratea: la primera
 * cuota es como todas. `modoDemo` avisa que los ejemplos guardados no cambian con las fechas.
 */
export function CamposFechas({
  valores,
  alCambiar,
  periodicidad,
  errores,
  modoDemo,
}: Readonly<{ valores: Fechas; alCambiar: (f: Fechas) => void; periodicidad: Periodicidad; errores: ErroresFechas; modoDemo: boolean }>) {
  const hayFechas = Boolean(valores.fechaDesembolso || valores.primerVencimiento)
  return (
    <fieldset className="grid gap-4 rounded-xl border border-outline-variant p-4 min-[480px]:grid-cols-2" aria-describedby="nota-fechas">
      <legend className="px-1 font-semibold">Fechas (opcional)</legend>
      <p id="nota-fechas" className="text-sm text-on-surface-variant min-[480px]:col-span-2">
        Con las dos fechas, el interés de la primera cuota se cobra por los días reales entre el desembolso y el primer pago; el capital y los servicios van completos. Sin fechas, la primera cuota es como todas.
      </p>
      <Campo etiqueta="Fecha de desembolso" error={errores.fechaDesembolso}>
        {(p) => <input {...p} type="date" className="campo cifra" value={valores.fechaDesembolso} onChange={(e) => alCambiar({ ...valores, fechaDesembolso: e.target.value })} />}
      </Campo>
      <Campo etiqueta="Primer vencimiento" error={errores.primerVencimiento} ayuda={reglaVencimiento(periodicidad)}>
        {(p) => <input {...p} type="date" className="campo cifra" value={valores.primerVencimiento} onChange={(e) => alCambiar({ ...valores, primerVencimiento: e.target.value })} />}
      </Campo>
      {hayFechas && (
        <div className="flex flex-wrap items-center gap-2 min-[480px]:col-span-2">
          <button type="button" className="boton-suave" onClick={() => alCambiar({ fechaDesembolso: '', primerVencimiento: '' })}>
            Quitar las fechas
          </button>
          {modoDemo && <span className="text-sm text-on-surface-variant">En modo demostración las cifras son de ejemplo y no incluyen el prorrateo.</span>}
        </div>
      )}
    </fieldset>
  )
}

/** Lo que hizo el prorrateo con la primera cuota, en palabras y cifras. */
export function AvisoProrrateo({ prorrateo: p }: Readonly<{ prorrateo: ProrrateoPrimeraCuota }>) {
  const signo = comparar(p.diferencia, '0')
  let efecto = 'igual que un periodo normal'
  if (signo > 0) efecto = `${cop(p.diferencia)} más que un periodo normal`
  if (signo < 0) efecto = `${cop(restar('0', p.diferencia))} menos que un periodo normal`
  return (
    <section aria-label="Prorrateo de la primera cuota" className="rounded-xl border border-outline-variant bg-surface-lowest p-4">
      <h2 className="text-lg font-bold">Primera cuota prorrateada</h2>
      <p className="text-on-surface-variant">
        Desembolso el {fechaLarga(p.fechaDesembolso)} y primer pago el {fechaLarga(p.primerVencimiento)}: <strong data-prueba="dias-prorrateo">{p.diasPrimerPeriodo} días</strong> (un periodo normal son {p.diasNominales}).
      </p>
      {p.aplicado ? (
        <dl className="mt-3 grid grid-cols-1 gap-3 min-[480px]:grid-cols-3">
          <div>
            <dt className="text-sm font-semibold text-on-surface-variant">Interés de la primera cuota</dt>
            <dd className="cifra text-xl font-bold" data-prueba="interes-prorrateado">{copCentavos(p.interesProrrateado)}</dd>
          </div>
          <div>
            <dt className="text-sm font-semibold text-on-surface-variant">Interés de un periodo completo</dt>
            <dd className="cifra text-xl font-bold">{copCentavos(p.interesSinProrrateo)}</dd>
          </div>
          <div>
            <dt className="text-sm font-semibold text-on-surface-variant">Diferencia</dt>
            <dd className="cifra text-xl font-bold">{esCero(p.diferencia) ? '—' : efecto}</dd>
          </div>
        </dl>
      ) : (
        <p className="mt-2 font-semibold">Con las reglas «como el Excel» no se prorratea: la primera cuota cobra el interés de un periodo completo.</p>
      )}
    </section>
  )
}
