import { useEffect, useRef, useState } from 'react'
import {
  aFormulario,
  aInstruccion,
  CAMPOS_POR_TIPO,
  describir,
  ETIQUETAS_TIPO,
  ETIQUETAS_VALOR,
  opcionesDe,
  ordenarInstrucciones,
  validarFormulario,
  valoresPorDefecto,
  type ErroresFormulario,
  type ValoresFormulario,
} from '../api/instrucciones'
import { Campo, Cargando, MensajeError, SelectorPerfil, Titulo } from '../componentes/comunes'
import { DetalleEjecucion } from '../componentes/DetalleEjecucion'
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

export default function Laboratorio() {
  const { fuente, semilla, poner } = useDatos()
  const cat = useCarga<Catalogo>(() => fuente.catalogo(), [fuente])
  const casos = useCarga<CasoResumen[]>(() => fuente.casos(), [fuente])
  const catalogo = cat.datos

  const [credito, setCredito] = useState<Credito>(creditoInicial())
  const [perfil, setPerfil] = useState<Perfil>('NEGOCIO')
  const [instrucciones, setInstrucciones] = useState<Instruccion[]>([])
  const [origen, setOrigen] = useState('Crédito nuevo sin instrucciones')
  const [resultado, setResultado] = useState<Ejecucion | null>(null)
  const [ejecutadas, setEjecutadas] = useState<Instruccion[]>([])
  const [error, setError] = useState<unknown>(null)
  const [corriendo, setCorriendo] = useState(false)
  const [casoElegido, setCasoElegido] = useState('')
  const sembrado = useRef(false)

  // Si llegas desde Casos, arranca con ese caso cargado.
  useEffect(() => {
    if (!semilla || sembrado.current) return
    sembrado.current = true
    if (semilla.credito) setCredito({ ...semilla.credito, perfilReglas: semilla.perfil })
    setInstrucciones(ordenarInstrucciones(semilla.instrucciones))
    setPerfil(semilla.perfil)
    setOrigen(semilla.origen)
    poner(null)
  }, [semilla, poner])

  async function cargarCaso(numero: string) {
    setCasoElegido(numero)
    if (!numero) return
    setError(null)
    try {
      const c = await fuente.caso(Number(numero), perfil)
      if (c.credito) setCredito({ ...c.credito, perfilReglas: perfil })
      setInstrucciones(ordenarInstrucciones(c.instrucciones))
      setOrigen(`Caso ${c.caso.numero}: ${c.caso.nombre}`)
      setResultado(null)
    } catch (e) {
      setError(e)
    }
  }

  async function ejecutar() {
    setCorriendo(true)
    setError(null)
    try {
      const enviadas = ordenarInstrucciones(instrucciones)
      setResultado(await fuente.ejecutar({ credito: { ...credito, perfilReglas: perfil }, perfilReglas: perfil, instrucciones: enviadas, incluirAsientos: true }))
      setEjecutadas(enviadas)
    } catch (e) {
      setResultado(null)
      setError(e)
    } finally {
      setCorriendo(false)
    }
  }

  const errCredito: Partial<Record<'valorMoto' | 'pagoInicial' | 'cuotas', string>> = {}
  if (!esDecimal(credito.valorMoto) || aMicros(credito.valorMoto) <= 0n) errCredito.valorMoto = 'Valor mayor que cero'
  if (!esDecimal(credito.pagoInicial)) errCredito.pagoInicial = 'Valor numérico'
  if (!credito.cuotas || credito.cuotas < 1) errCredito.cuotas = 'Entero desde 1'

  return (
    <div>
      <Titulo detalle="Arma una historia periodo a periodo (sin pago, abonos, cambios de tasa, dación…) y corre el motor para ver el resultado.">Laboratorio</Titulo>
      {cat.cargando && <Cargando />}
      {cat.error && <MensajeError error={cat.error} />}

      <section className="tarjeta mb-4 grid gap-4 md:grid-cols-2 xl:grid-cols-4" aria-label="Punto de partida">
        <Campo etiqueta="Partir de un caso" ayuda={`Ahora: ${origen}`}>
          {(p) => (
            <select {...p} className="campo" value={casoElegido} onChange={(e) => void cargarCaso(e.target.value)}>
              <option value="">Crédito nuevo (vacío)</option>
              {casos.datos?.map((c) => (
                <option key={c.numero} value={c.numero}>
                  Caso {c.numero} · {c.nombre}
                </option>
              ))}
            </select>
          )}
        </Campo>
        <Campo etiqueta="Periodicidad">
          {(p) => (
            <select {...p} className="campo" value={credito.periodicidad} onChange={(e) => setCredito({ ...credito, periodicidad: e.target.value as Periodicidad })}>
              {(catalogo?.periodicidades.map((x) => x.codigo) ?? (['SEMANAL', 'QUINCENAL', 'MENSUAL'] as Periodicidad[])).map((x) => (
                <option key={x} value={x}>
                  {NOMBRES[x]}
                </option>
              ))}
            </select>
          )}
        </Campo>
        <Campo etiqueta="Cuotas" error={errCredito.cuotas}>
          {(p) => <input {...p} className="campo cifra" inputMode="numeric" value={credito.cuotas ?? ''} onChange={(e) => setCredito({ ...credito, cuotas: /^\d*$/.test(e.target.value) && e.target.value ? Number(e.target.value) : null, meses: null })} />}
        </Campo>
        <Campo etiqueta="Valor de la moto (COP)" error={errCredito.valorMoto}>
          {(p) => <input {...p} className="campo cifra" inputMode="decimal" value={credito.valorMoto} onChange={(e) => setCredito({ ...credito, valorMoto: e.target.value })} />}
        </Campo>
        <Campo etiqueta="Pago inicial (COP)" error={errCredito.pagoInicial}>
          {(p) => <input {...p} className="campo cifra" inputMode="decimal" value={credito.pagoInicial} onChange={(e) => setCredito({ ...credito, pagoInicial: e.target.value })} />}
        </Campo>
        <SelectorPerfil valor={perfil} alCambiar={setPerfil} />
      </section>

      <div className="grid items-start gap-4 lg:grid-cols-2">
        <ListaInstrucciones instrucciones={instrucciones} alCambiar={setInstrucciones} catalogo={catalogo} />
        <div className="tarjeta">
          <h2 className="mb-1 text-lg font-bold">Correr el motor</h2>
          <p className="mb-3 text-sm text-on-surface-variant">
            {instrucciones.length === 0 ? 'Sin instrucciones: todos los periodos se pagan completos y a tiempo.' : `${instrucciones.length} instrucción(es). Los periodos sin instrucción son pagos a tiempo.`}
          </p>
          <button type="button" className="boton" onClick={() => void ejecutar()} disabled={corriendo || Object.keys(errCredito).length > 0}>
            {corriendo ? 'Ejecutando…' : 'Ejecutar'}
          </button>
        </div>
      </div>

      <div className="mt-6 grid gap-4" aria-live="polite">
        {error != null && <MensajeError error={error} />}
        {resultado && <DetalleEjecucion ejecucion={resultado} instrucciones={ejecutadas} />}
      </div>
    </div>
  )
}

// ---------- lista y formulario de instrucciones ----------

function ListaInstrucciones({ instrucciones, alCambiar, catalogo }: Readonly<{ instrucciones: Instruccion[]; alCambiar: (l: Instruccion[]) => void; catalogo: Catalogo | null }>) {
  const tipos = catalogo?.tiposInstruccion ?? (Object.keys(ETIQUETAS_TIPO) as TipoInstruccion[])
  const [periodo, setPeriodo] = useState('1')
  const [tipo, setTipo] = useState<TipoInstruccion>('SIN_PAGO')
  const [valores, setValores] = useState<ValoresFormulario>(valoresPorDefecto('SIN_PAGO'))
  const [errores, setErrores] = useState<ErroresFormulario>({})
  const [editando, setEditando] = useState<number | null>(null)

  function cambiarTipo(t: TipoInstruccion) {
    setTipo(t)
    setValores(valoresPorDefecto(t))
    setErrores({})
  }

  function guardar(e: React.FormEvent) {
    e.preventDefault()
    const n = /^\d+$/.test(periodo.trim()) ? Number(periodo) : Number.NaN
    const errs = validarFormulario(tipo, n, valores)
    setErrores(errs)
    if (Object.keys(errs).length > 0) return
    const nueva = aInstruccion(tipo, n, valores)
    const resto = editando === null ? instrucciones : instrucciones.filter((_, i) => i !== editando)
    alCambiar(ordenarInstrucciones([...resto, nueva]))
    setEditando(null)
    setPeriodo(String(n + 1))
    setValores(valoresPorDefecto(tipo))
  }

  function editar(i: number) {
    const ins = instrucciones[i]
    setEditando(i)
    setTipo(ins.tipo)
    setPeriodo(String(ins.periodo))
    setValores(aFormulario(ins))
    setErrores({})
  }

  return (
    <section className="tarjeta" aria-labelledby="h-ins">
      <h2 id="h-ins" className="mb-3 text-lg font-bold">Instrucciones por periodo</h2>
      {instrucciones.length === 0 ? (
        <p className="mb-3 text-on-surface-variant">Todavía no hay instrucciones.</p>
      ) : (
        <ol className="mb-4 grid gap-2">
          {instrucciones.map((ins, i) => (
            <li key={`${ins.periodo}-${ins.tipo}-${i}`} className="flex flex-wrap items-center gap-2 rounded-lg bg-surface-low px-3 py-2">
              <span className="font-semibold">Periodo {ins.periodo}</span>
              <span className="min-w-0 flex-1 text-sm">{describir(ins)}</span>
              <button type="button" className="boton-suave !min-h-9 !px-2 text-sm" onClick={() => editar(i)} aria-label={`Editar la instrucción del periodo ${ins.periodo}`}>
                Editar
              </button>
              <button type="button" className="boton-suave !min-h-9 !px-2 text-sm" onClick={() => alCambiar(instrucciones.filter((_, k) => k !== i))} aria-label={`Quitar la instrucción del periodo ${ins.periodo}`}>
                Quitar
              </button>
            </li>
          ))}
        </ol>
      )}

      <form onSubmit={guardar} noValidate className="grid gap-3 border-t border-outline-variant pt-3" aria-label={editando === null ? 'Agregar instrucción' : 'Editar instrucción'}>
        <div className="grid grid-cols-[6rem_1fr] gap-3">
          <Campo etiqueta="Periodo" error={errores.periodo}>
            {(p) => <input {...p} className="campo cifra" inputMode="numeric" value={periodo} onChange={(e) => setPeriodo(e.target.value)} />}
          </Campo>
          <Campo etiqueta="Tipo de instrucción">
            {(p) => (
              <select {...p} className="campo" value={tipo} onChange={(e) => cambiarTipo(e.target.value as TipoInstruccion)}>
                {tipos.map((t) => (
                  <option key={t} value={t}>
                    {ETIQUETAS_TIPO[t] ?? t}
                  </option>
                ))}
              </select>
            )}
          </Campo>
        </div>
        {CAMPOS_POR_TIPO[tipo].map((c) =>
          c.tipo === 'booleano' ? (
            <label key={c.nombre} className="flex min-h-11 items-start gap-2">
              <input type="checkbox" className="mt-1 size-5" checked={valores[c.nombre] === true} onChange={(e) => setValores({ ...valores, [c.nombre]: e.target.checked })} />
              <span>
                <span className="font-semibold">{c.etiqueta}</span>
                {c.ayuda && <span className="block text-xs text-on-surface-variant">{c.ayuda}</span>}
              </span>
            </label>
          ) : (
            <Campo key={c.nombre} etiqueta={c.etiqueta} error={errores[c.nombre]} ayuda={c.ayuda}>
              {(p) =>
                c.tipo === 'opcion' ? (
                  <select {...p} className="campo" value={String(valores[c.nombre] ?? '')} onChange={(e) => setValores({ ...valores, [c.nombre]: e.target.value })}>
                    {opcionesDe(catalogo, c.opciones!).length === 0 && <option value={String(c.defecto ?? '')}>{ETIQUETAS_VALOR[String(c.defecto)] ?? String(c.defecto)}</option>}
                    {opcionesDe(catalogo, c.opciones!).map((o) => (
                      <option key={o} value={o}>
                        {ETIQUETAS_VALOR[o] ?? o}
                      </option>
                    ))}
                  </select>
                ) : (
                  <input {...p} className="campo cifra" inputMode={c.tipo === 'entero' ? 'numeric' : 'decimal'} value={String(valores[c.nombre] ?? '')} onChange={(e) => setValores({ ...valores, [c.nombre]: e.target.value })} />
                )
              }
            </Campo>
          ),
        )}
        <div className="flex flex-wrap gap-2">
          <button type="submit" className="boton">
            {editando === null ? 'Agregar instrucción' : 'Guardar cambios'}
          </button>
          {editando !== null && (
            <button type="button" className="boton-suave" onClick={() => setEditando(null)}>
              Cancelar
            </button>
          )}
          {instrucciones.length > 0 && editando === null && (
            <button type="button" className="boton-suave" onClick={() => alCambiar([])}>
              Quitar todas
            </button>
          )}
        </div>
      </form>
    </section>
  )
}
