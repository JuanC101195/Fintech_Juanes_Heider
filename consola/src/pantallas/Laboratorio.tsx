import { useEffect, useRef, useState } from 'react'
import {
  aFormulario,
  agregarInstrucciones,
  aInstruccion,
  atajos,
  CAMPOS_POR_TIPO,
  describir,
  INFO_TIPO,
  NOTAS_VALOR,
  ETIQUETAS_VALOR,
  opcionesDe,
  ORDEN_TIPOS,
  ordenarInstrucciones,
  TIPOS_REPETIBLES,
  unidadPeriodo,
  validarFormulario,
  valoresPorDefecto,
  type Atajo,
  type Campo as CampoInstruccion,
  type ErroresFormulario,
  type ValoresFormulario,
} from '../api/instrucciones'
import { lineaCaso } from '../casosResumen'
import { Campo, Cargando, MensajeError, Segmentado, SelectorPerfil, Titulo } from '../componentes/comunes'
import { DetalleEjecucion } from '../componentes/DetalleEjecucion'
import { EntradaDinero, EntradaPasos, EntradaPorcentaje } from '../componentes/entradas'
import { useCarga, useDatos } from '../datos'
import { aMicros, esDecimal } from '../dinero'
import type { Catalogo, CasoResumen, Credito, Ejecucion, Instruccion, Perfil, Periodicidad, TipoInstruccion } from '../tipos'

const NOMBRES: Record<Periodicidad, string> = { SEMANAL: 'Semanal', QUINCENAL: 'Quincenal', MENSUAL: 'Mensual' }

function creditoInicial(): Credito {
  return {
    linea: 'CPPU',
    periodicidad: 'SEMANAL',
    cuotas: 104,
    meses: null,
    valorMoto: '5100000',
    pagoInicial: '0',
    perfilReglas: 'NEGOCIO',
  }
}

/**
 * De dónde parte el laboratorio. Es el ÚNICO estado de "qué está elegido": las tarjetas marcadas y el texto «Ahora: …»
 * salen de aquí, así no pueden contradecirse (antes había dos estados y al llegar desde Casos solo se llenaba uno).
 */
export type Partida = { tipo: 'nuevo' } | { tipo: 'caso'; numero: number; nombre: string; base: Instruccion[] }

const firma = (l: Instruccion[]) => JSON.stringify(ordenarInstrucciones(l))

export function describirPartida(p: Partida, instrucciones: Instruccion[]): string {
  if (p.tipo === 'nuevo') return instrucciones.length === 0 ? 'Crédito nuevo, sin eventos' : 'Crédito nuevo con tus eventos'
  const cambios = firma(instrucciones) !== firma(p.base)
  return `Caso ${p.numero} · ${p.nombre}${cambios ? ' (con cambios tuyos)' : ''}`
}

export default function Laboratorio() {
  const { fuente, semilla, poner } = useDatos()
  const cat = useCarga<Catalogo>(() => fuente.catalogo(), [fuente])
  const casos = useCarga<CasoResumen[]>(() => fuente.casos(), [fuente])
  const catalogo = cat.datos

  // Si llegas desde Casos ("Probar variaciones…"), el caso ya viene: todo arranca con él, sin pasar por "crédito nuevo".
  const [inicio] = useState(() => semilla)
  const [credito, setCredito] = useState<Credito>(() => (inicio?.credito ? { ...inicio.credito, perfilReglas: inicio.perfil } : creditoInicial()))
  const [perfil, setPerfil] = useState<Perfil>(() => inicio?.perfil ?? 'NEGOCIO')
  const [instrucciones, setInstrucciones] = useState<Instruccion[]>(() => ordenarInstrucciones(inicio?.instrucciones ?? []))
  const [partida, setPartida] = useState<Partida>(() => (inicio ? { tipo: 'caso', numero: inicio.caso.numero, nombre: inicio.caso.nombre, base: ordenarInstrucciones(inicio.instrucciones) } : { tipo: 'nuevo' }))
  const [eligiendo, setEligiendo] = useState(!inicio)
  const [cargandoCaso, setCargandoCaso] = useState<number | null>(null)
  const [resultado, setResultado] = useState<Ejecucion | null>(null)
  const [ejecutado, setEjecutado] = useState<{ instrucciones: Instruccion[]; firma: string } | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [corriendo, setCorriendo] = useState(false)

  useEffect(() => {
    if (semilla) poner(null)
  }, [semilla, poner])

  const valorPartida = partida.tipo === 'nuevo' ? 'nuevo' : String(partida.numero)

  async function elegirPartida(valor: string) {
    setError(null)
    if (valor === 'nuevo') {
      setCredito(creditoInicial())
      setInstrucciones([])
      setPartida({ tipo: 'nuevo' })
      setResultado(null)
      setEligiendo(false)
      return
    }
    const numero = Number(valor)
    setCargandoCaso(numero)
    try {
      const c = await fuente.caso(numero, perfil)
      const base = ordenarInstrucciones(c.instrucciones)
      setCredito(c.credito ? { ...c.credito, perfilReglas: perfil } : creditoInicial())
      setInstrucciones(base)
      setPartida({ tipo: 'caso', numero: c.caso.numero, nombre: c.caso.nombre, base })
      setResultado(null)
      setEligiendo(false)
    } catch (e) {
      setError(e)
    } finally {
      setCargandoCaso(null)
    }
  }

  const firmaActual = JSON.stringify({ credito, perfil, i: ordenarInstrucciones(instrucciones) })

  async function ejecutar() {
    setCorriendo(true)
    setError(null)
    try {
      const enviadas = ordenarInstrucciones(instrucciones)
      setResultado(await fuente.ejecutar({ credito: { ...credito, perfilReglas: perfil }, perfilReglas: perfil, instrucciones: enviadas, incluirAsientos: true }))
      setEjecutado({ instrucciones: enviadas, firma: firmaActual })
    } catch (e) {
      setResultado(null)
      setError(e)
    } finally {
      setCorriendo(false)
    }
  }

  const errCredito: Partial<Record<'valorMoto' | 'pagoInicial' | 'cuotas', string>> = {}
  if (!esDecimal(credito.valorMoto) || aMicros(credito.valorMoto) <= 0n) errCredito.valorMoto = 'Escribe un valor mayor que cero'
  if (!esDecimal(credito.pagoInicial)) errCredito.pagoInicial = 'Escribe un valor, o 0 si no hay pago inicial'
  if (!credito.cuotas || credito.cuotas < 1) errCredito.cuotas = 'Escribe cuántas cuotas, desde 1'
  const hayErrCredito = Object.keys(errCredito).length > 0
  const desactualizado = resultado !== null && ejecutado !== null && ejecutado.firma !== firmaActual

  const tarjetasPartida = [
    { valor: 'nuevo', insignia: '＋', titulo: 'Crédito nuevo', linea: 'Empieza sin eventos: el cliente paga todo a tiempo', nombre: 'Crédito nuevo' },
    ...(casos.datos ?? []).map((c) => ({ valor: String(c.numero), insignia: String(c.numero), titulo: c.nombre, linea: lineaCaso(c), nombre: `Caso ${c.numero}` })),
  ]

  return (
    <div>
      <Titulo detalle="Arma la historia de un crédito (semanas sin pago, abonos, cambios de tasa, dación…) y mira qué hace el motor periodo a periodo.">Laboratorio</Titulo>
      {cat.error && <MensajeError error={cat.error} alReintentar={cat.recargar} />}

      <section className="tarjeta mb-4" aria-labelledby="h-partida">
        <h2 id="h-partida" className="text-xl font-bold">
          1. ¿De dónde partes?
        </h2>
        <p className="text-on-surface-variant">Elige un caso para cargar lo que pasa en él y variarlo, o un crédito nuevo para armarlo desde cero.</p>
        <p role="status" className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg bg-surface-low px-3 py-2">
          <span className="text-on-surface-variant">Ahora:</span>
          <strong data-prueba="partida-actual">{describirPartida(partida, instrucciones)}</strong>
          {cargandoCaso !== null && <span className="text-on-surface-variant">· cargando el caso {cargandoCaso}…</span>}
        </p>

        {eligiendo ? (
          <>
            {casos.cargando && <Cargando texto="Cargando los casos…" />}
            {casos.error && <MensajeError error={casos.error} alReintentar={casos.recargar} />}
            <div role="radiogroup" aria-label="Partir de un caso" className="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
              {tarjetasPartida.map((t) => (
                <label key={t.valor} className="opcion-tarjeta items-start">
                  <input
                    type="radio"
                    className="sr-only"
                    name="partida"
                    value={t.valor}
                    checked={valorPartida === t.valor}
                    disabled={cargandoCaso !== null}
                    onChange={() => void elegirPartida(t.valor)}
                    onClick={() => valorPartida === t.valor && setEligiendo(false)}
                  />
                  <span aria-hidden="true" className={`flex size-10 shrink-0 items-center justify-center rounded-full font-bold ${valorPartida === t.valor ? 'bg-primary text-on-primary' : 'bg-primary-container text-on-primary-container'}`}>
                    {valorPartida === t.valor ? '✓' : t.insignia}
                  </span>
                  <span className="min-w-0">
                    <span className="block font-semibold">
                      <span className="sr-only">{t.nombre}: </span>
                      {t.valor !== 'nuevo' && <span aria-hidden="true" className="text-on-surface-variant">Caso {t.valor} · </span>}
                      {t.titulo}
                    </span>
                    <span className="block text-sm text-on-surface-variant">{cargandoCaso === Number(t.valor) ? 'Cargando…' : t.linea}</span>
                  </span>
                </label>
              ))}
            </div>
          </>
        ) : (
          <div className="mt-3 flex flex-wrap gap-2">
            <button type="button" className="boton-suave" onClick={() => setEligiendo(true)}>
              Elegir otro caso
            </button>
            {partida.tipo === 'caso' && firma(instrucciones) !== firma(partida.base) && (
              <button type="button" className="boton-suave" onClick={() => void elegirPartida(String(partida.numero))}>
                Deshacer mis cambios al caso {partida.numero}
              </button>
            )}
          </div>
        )}

        <h3 className="mt-6 mb-3 text-lg font-bold">Datos del crédito</h3>
        <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">
          <div className="md:col-span-2 xl:col-span-2">
            <Segmentado
              nombre="periodicidad-lab"
              etiqueta="¿Cada cuánto paga el cliente?"
              valor={credito.periodicidad}
              alCambiar={(p) => setCredito({ ...credito, periodicidad: p })}
              opciones={(catalogo?.periodicidades.map((x) => x.codigo) ?? (['SEMANAL', 'QUINCENAL', 'MENSUAL'] as Periodicidad[])).map((p) => ({ valor: p, etiqueta: NOMBRES[p] }))}
            />
          </div>
          <Campo etiqueta="Número de cuotas" error={errCredito.cuotas}>
            {(p) => <EntradaPasos {...p} nombre="Número de cuotas" valor={credito.cuotas == null ? '' : String(credito.cuotas)} alCambiar={(v) => setCredito({ ...credito, cuotas: v ? Number(v) : null, meses: null })} />}
          </Campo>
          <div className="hidden xl:block" />
          <Campo etiqueta="Valor de la moto" error={errCredito.valorMoto}>
            {(p) => <EntradaDinero {...p} valor={credito.valorMoto} alCambiar={(valorMoto) => setCredito({ ...credito, valorMoto })} />}
          </Campo>
          <Campo etiqueta="Pago inicial" error={errCredito.pagoInicial} ayuda="0 si no da nada de entrada">
            {(p) => <EntradaDinero {...p} valor={credito.pagoInicial} alCambiar={(pagoInicial) => setCredito({ ...credito, pagoInicial })} />}
          </Campo>
          <div className="md:col-span-2">
            <SelectorPerfil valor={perfil} alCambiar={setPerfil} />
          </div>
        </div>
      </section>

      <EditorEventos instrucciones={instrucciones} alCambiar={setInstrucciones} catalogo={catalogo} periodicidad={credito.periodicidad} cuotas={credito.cuotas} />

      <section className="tarjeta mt-4" aria-labelledby="h-correr">
        <h2 id="h-correr" className="text-xl font-bold">
          3. Resultado
        </h2>
        <p className="mb-3 text-on-surface-variant">
          {instrucciones.length === 0 ? 'Sin eventos: el motor supone que el cliente paga todas las cuotas completas y a tiempo.' : `${instrucciones.length} ${instrucciones.length === 1 ? 'evento' : 'eventos'}; los periodos sin evento se pagan completos y a tiempo.`}
        </p>
        <button type="button" className="boton w-full sm:w-auto sm:min-w-56" onClick={() => void ejecutar()} disabled={corriendo || hayErrCredito}>
          {corriendo ? 'Calculando…' : 'Calcular resultado'}
        </button>
        {hayErrCredito && <p className="mt-2 text-sm font-semibold text-error">Corrige los datos del crédito marcados en rojo para poder calcular.</p>}
        {desactualizado && !corriendo && (
          <p className="mt-3 rounded-lg bg-aviso px-3 py-2 font-semibold text-on-aviso" role="status">
            Cambiaste algo después de calcular: pulsa «Calcular resultado» otra vez para ver el efecto.
          </p>
        )}
      </section>

      <div className="mt-6 grid gap-4" aria-live="polite">
        {corriendo && <Cargando texto="Calculando…" />}
        {error != null && <MensajeError error={error} />}
        {resultado && ejecutado && <DetalleEjecucion ejecucion={resultado} instrucciones={ejecutado.instrucciones} />}
        {!resultado && !corriendo && error == null && (
          <p className="rounded-xl border border-dashed border-outline-variant p-6 text-center text-on-surface-variant">Aquí verás el resultado periodo a periodo. Pulsa «Calcular resultado».</p>
        )}
      </div>
    </div>
  )
}

// ---------- eventos por periodo: atajos, tipos y formulario ----------

function EditorEventos({
  instrucciones,
  alCambiar,
  catalogo,
  periodicidad,
  cuotas,
}: Readonly<{ instrucciones: Instruccion[]; alCambiar: (l: Instruccion[]) => void; catalogo: Catalogo | null; periodicidad: Periodicidad; cuotas: number | null }>) {
  const disponibles = ORDEN_TIPOS.filter((t) => !catalogo || catalogo.tiposInstruccion.includes(t))
  const u = unidadPeriodo(periodicidad)
  const [periodo, setPeriodo] = useState('1')
  const [tipo, setTipo] = useState<TipoInstruccion>('SIN_PAGO')
  const [valores, setValores] = useState<ValoresFormulario>(valoresPorDefecto('SIN_PAGO'))
  const [veces, setVeces] = useState('1')
  const [errores, setErrores] = useState<ErroresFormulario>({})
  const [editando, setEditando] = useState<number | null>(null)
  const [atajo, setAtajo] = useState<string | null>(null)
  const [aviso, setAviso] = useState('')
  const formulario = useRef<HTMLFormElement>(null)
  const repetible = TIPOS_REPETIBLES.includes(tipo) && editando === null

  function irAlPeriodo() {
    requestAnimationFrame(() => {
      const el = formulario.current?.querySelector<HTMLInputElement>('[data-periodo] input')
      el?.scrollIntoView?.({ block: 'center', behavior: 'smooth' })
      el?.focus({ preventScroll: true })
      el?.select()
    })
  }

  function cambiarTipo(t: TipoInstruccion) {
    setTipo(t)
    setValores(valoresPorDefecto(t))
    setErrores({})
    setAtajo(null)
    if (!TIPOS_REPETIBLES.includes(t)) setVeces('1')
  }

  function usarAtajo(a: Atajo) {
    setEditando(null)
    setTipo(a.tipo)
    setValores({ ...valoresPorDefecto(a.tipo), ...a.valores })
    setVeces(String(a.veces))
    setErrores({})
    setAtajo(a.texto)
    setAviso('')
    irAlPeriodo()
  }

  function guardar(e: { preventDefault(): void }) {
    e.preventDefault()
    const n = /^\d+$/.test(periodo.trim()) ? Number(periodo) : Number.NaN
    const errs = validarFormulario(tipo, n, valores)
    const k = repetible ? Number(veces) : 1
    if (!Number.isInteger(k) || k < 1) errs.veces = 'Escribe cuántos periodos seguidos, desde 1'
    setErrores(errs)
    if (Object.keys(errs).length > 0) return
    const nuevas = Array.from({ length: k }, (_, i) => aInstruccion(tipo, n + i, valores))
    const resto = editando === null ? instrucciones : instrucciones.filter((_, i) => i !== editando)
    alCambiar(agregarInstrucciones(resto, nuevas))
    setAviso(k === 1 ? `Agregado en el periodo ${n}: ${describir(nuevas[0])}.` : `Agregado en los periodos ${n} a ${n + k - 1}: ${describir(nuevas[0])}.`)
    setEditando(null)
    setAtajo(null)
    setPeriodo(String(n + k))
    setValores(valoresPorDefecto(tipo))
    setVeces('1')
  }

  function editar(i: number) {
    const ins = instrucciones[i]
    setEditando(i)
    setTipo(ins.tipo)
    setPeriodo(String(ins.periodo))
    setValores(aFormulario(ins))
    setVeces('1')
    setErrores({})
    setAtajo(null)
    setAviso('')
    irAlPeriodo()
  }

  return (
    <section className="tarjeta" aria-labelledby="h-ins">
      <h2 id="h-ins" className="text-xl font-bold">
        2. ¿Qué pasa en el crédito?
      </h2>
      <p className="mb-4 text-on-surface-variant">
        Cada evento es algo que pasa en un periodo de pago ({u.una} 1, 2, 3…): el cliente no paga, abona, cambia la tasa… Los periodos sin evento se pagan completos y a tiempo.
      </p>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        <div>
          <h3 className="mb-2 text-lg font-bold">Eventos ({instrucciones.length})</h3>
          {instrucciones.length === 0 ? (
            <p className="rounded-xl border border-dashed border-outline-variant p-4 text-on-surface-variant">
              Todavía no hay eventos: el cliente paga todo a tiempo. Usa un atajo o elige qué pasa y pulsa «Agregar evento».
            </p>
          ) : (
            <>
              <ol className="grid max-h-[32rem] gap-2 overflow-y-auto pr-1" aria-label="Eventos agregados">
                {instrucciones.map((ins, i) => (
                  <li key={`${ins.periodo}-${ins.tipo}-${i}`} className={`flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg px-3 py-2 ${editando === i ? 'bg-primary-container/40 ring-2 ring-primary' : 'bg-surface-low'}`}>
                    <span aria-hidden="true" className="text-lg">
                      {INFO_TIPO[ins.tipo]?.icono}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-semibold text-on-surface-variant">Periodo {ins.periodo}</span>
                      <span className="block">{describir(ins)}</span>
                    </span>
                    <span className="flex gap-1">
                      <button type="button" className="boton-suave !px-3" onClick={() => editar(i)} aria-label={`Editar el evento del periodo ${ins.periodo}`}>
                        Editar
                      </button>
                      <button type="button" className="boton-suave !px-3" onClick={() => alCambiar(instrucciones.filter((_, k) => k !== i))} aria-label={`Quitar el evento del periodo ${ins.periodo}`}>
                        Quitar
                      </button>
                    </span>
                  </li>
                ))}
              </ol>
              {editando === null && (
                <button type="button" className="boton-suave mt-3" onClick={() => alCambiar([])}>
                  Quitar todos los eventos
                </button>
              )}
            </>
          )}
          <p role="status" className="mt-2 text-sm font-semibold text-tertiary">
            {aviso}
          </p>
        </div>

        <div>
          {editando === null && (
            <>
              <h3 className="mb-2 text-lg font-bold">Atajos</h3>
              <p className="mb-2 text-sm text-on-surface-variant">Un clic llena el formulario; tú solo ajustas el periodo y el valor.</p>
              <div className="mb-5 grid gap-2 sm:grid-cols-2">
                {atajos(periodicidad).map((a) => (
                  <button key={a.id} type="button" className="opcion-tarjeta items-center font-semibold" aria-pressed={atajo === a.texto} onClick={() => usarAtajo(a)}>
                    <span aria-hidden="true" className="text-lg">
                      {a.icono}
                    </span>
                    {a.texto}
                  </button>
                ))}
              </div>
            </>
          )}

          <form ref={formulario} onSubmit={guardar} noValidate className="grid gap-4" aria-label={editando === null ? 'Agregar evento' : 'Editar evento'}>
            <h3 className="text-lg font-bold">{editando === null ? 'Agregar un evento' : `Editar el evento del periodo ${instrucciones[editando]?.periodo}`}</h3>
            {atajo && (
              <p className="rounded-lg bg-primary-container/40 px-3 py-2" role="status">
                <strong>Atajo listo: {atajo}.</strong> Ajusta el periodo y el valor, y pulsa «Agregar evento».
              </p>
            )}
            <Segmentado nombre="tipo" etiqueta="¿Qué pasa?" diseno="tarjetas" valor={tipo} alCambiar={cambiarTipo} opciones={disponibles.map((t) => ({ valor: t, etiqueta: INFO_TIPO[t].nombre, nota: INFO_TIPO[t].frase, icono: INFO_TIPO[t].icono }))} />

            <div className="grid gap-4 min-[480px]:grid-cols-2">
              <div data-periodo>
                <Campo etiqueta="Periodo" error={errores.periodo} ayuda={`El número de la ${u.una === 'mes' ? 'cuota' : u.una}: 1 es la primera${cuotas ? `; el crédito tiene ${cuotas}` : ''}`}>
                  {(p) => <EntradaPasos {...p} nombre="Periodo" valor={periodo} alCambiar={setPeriodo} />}
                </Campo>
              </div>
              {repetible && (
                <Campo etiqueta="¿Cuántos periodos seguidos?" error={errores.veces} ayuda={`1 = solo ese periodo; 2 = ese y el siguiente…`}>
                  {(p) => <EntradaPasos {...p} nombre="Periodos seguidos" valor={veces} maximo={104} alCambiar={setVeces} />}
                </Campo>
              )}
            </div>

            {CAMPOS_POR_TIPO[tipo].map((c) => (
              <CampoDeTipo key={`${tipo}-${c.nombre}`} campo={c} valores={valores} alCambiar={setValores} error={errores[c.nombre]} catalogo={catalogo} />
            ))}

            <div className="flex flex-wrap gap-2">
              <button type="submit" className="boton w-full sm:w-auto">
                {editando === null ? 'Agregar evento' : 'Guardar cambios'}
              </button>
              {editando !== null && (
                <button type="button" className="boton-suave" onClick={() => setEditando(null)}>
                  Cancelar
                </button>
              )}
            </div>
          </form>
        </div>
      </div>
    </section>
  )
}

function CampoDeTipo({ campo: c, valores, alCambiar, error, catalogo }: Readonly<{ campo: CampoInstruccion; valores: ValoresFormulario; alCambiar: (v: ValoresFormulario) => void; error?: string; catalogo: Catalogo | null }>) {
  const poner = (v: string | boolean) => alCambiar({ ...valores, [c.nombre]: v })
  const texto = String(valores[c.nombre] ?? '')
  if (c.tipo === 'booleano') {
    return (
      <label className="flex min-h-12 cursor-pointer items-start gap-3 rounded-lg border border-outline-variant px-3 py-2.5">
        <input type="checkbox" className="mt-0.5 size-6 shrink-0 accent-[var(--c-primary)]" checked={valores[c.nombre] === true} onChange={(e) => poner(e.target.checked)} />
        <span>
          <span className="font-semibold">{c.etiqueta}</span>
          {c.ayuda && <span className="block text-sm text-on-surface-variant">{c.ayuda}</span>}
        </span>
      </label>
    )
  }
  if (c.tipo === 'opcion') {
    const lista = opcionesDe(catalogo, c.opciones!)
    const opciones = (lista.length ? lista : [String(c.defecto ?? '')]).map((o) => ({ valor: o, etiqueta: ETIQUETAS_VALOR[o] ?? o.replaceAll('_', ' ').toLowerCase(), nota: NOTAS_VALOR[o] }))
    return <Segmentado nombre={`op-${c.nombre}`} etiqueta={c.etiqueta} valor={texto} alCambiar={poner} opciones={opciones} />
  }
  return (
    <Campo etiqueta={c.etiqueta} error={error} ayuda={c.ayuda}>
      {(p) => {
        if (c.tipo === 'dinero') return <EntradaDinero {...p} valor={texto} alCambiar={poner} />
        if (c.tipo === 'tasa' || c.tipo === 'fraccion') return <EntradaPorcentaje {...p} valor={texto} alCambiar={poner} />
        return <EntradaPasos {...p} nombre={c.etiqueta} minimo={0} valor={texto} alCambiar={poner} />
      }}
    </Campo>
  )
}
