import { useEffect, useMemo, useRef, useState } from 'react'
import { Campo, Cargando, MensajeError, Segmentado, SelectorPerfil, sinJerga, Tarjeta, Titulo } from '../componentes/comunes'
import { EntradaDinero, EntradaPasos } from '../componentes/entradas'
import { AvisoProrrateo, CamposFechas } from '../componentes/FechasCredito'
import { GraficoSeries } from '../componentes/GraficoSeries'
import { useCarga, useDatos } from '../datos'
import { aMicros, cop, esDecimal, porcentaje, sumar } from '../dinero'
import { fechasDelCredito, validarFechas } from '../fechas'
import type { Catalogo, Credito, FilaPlan, Perfil, Periodicidad, Simulacion } from '../tipos'

const PERIODOS_POR_ANIO: Record<Periodicidad, number> = { SEMANAL: 52, QUINCENAL: 24, MENSUAL: 12 }
const NOMBRES: Record<Periodicidad, string> = { SEMANAL: 'Semanal', QUINCENAL: 'Quincenal', MENSUAL: 'Mensual' }

interface Formulario {
  linea: string
  periodicidad: Periodicidad
  unidad: 'cuotas' | 'meses'
  plazo: string
  valorMoto: string
  pagoInicial: string
  perfil: Perfil
  /** Opcionales (P5-PRORRATEO): vacías = sin fechas. */
  fechaDesembolso?: string
  primerVencimiento?: string
}

type Errores = Partial<Record<'plazo' | 'valorMoto' | 'pagoInicial' | 'fechaDesembolso' | 'primerVencimiento', string>>

const fechasDe = (f: Formulario) => ({ fechaDesembolso: f.fechaDesembolso ?? '', primerVencimiento: f.primerVencimiento ?? '' })

/** Valida el formulario del simulador. Máximo de meses de la línea (24). */
export function validarSimulador(f: Formulario, maxMeses: number): Errores {
  const e: Errores = {}
  if (!/^\d+$/.test(f.plazo.trim()) || Number(f.plazo) < 1) e.plazo = 'Escribe un número entero de 1 en adelante'
  else {
    const meses = f.unidad === 'meses' ? Number(f.plazo) : (Number(f.plazo) * 12) / PERIODOS_POR_ANIO[f.periodicidad]
    if (meses > maxMeses) e.plazo = `El plazo máximo es de ${maxMeses} meses (${Math.floor((maxMeses * PERIODOS_POR_ANIO[f.periodicidad]) / 12)} cuotas ${NOMBRES[f.periodicidad].toLowerCase()}s)`
  }
  if (!esDecimal(f.valorMoto) || aMicros(f.valorMoto) <= 0n) e.valorMoto = 'Escribe un valor mayor que cero, por ejemplo 5.100.000'
  if (!esDecimal(f.pagoInicial)) e.pagoInicial = 'Escribe un valor, o 0 si no hay pago inicial'
  else if (!e.valorMoto && aMicros(f.pagoInicial) >= aMicros(f.valorMoto)) e.pagoInicial = 'El pago inicial debe ser menor que el valor de la moto'
  return { ...e, ...validarFechas(f.periodicidad, fechasDe(f)) }
}

export function aCredito(f: Formulario): Credito {
  return {
    linea: f.linea,
    periodicidad: f.periodicidad,
    cuotas: f.unidad === 'cuotas' ? Number(f.plazo) : null,
    meses: f.unidad === 'meses' ? Number(f.plazo) : null,
    valorMoto: f.valorMoto.trim(),
    pagoInicial: f.pagoInicial.trim(),
    perfilReglas: f.perfil,
    ...fechasDelCredito(fechasDe(f)),
  }
}

export default function Simulador() {
  const { fuente } = useDatos()
  const cat = useCarga<Catalogo>(() => fuente.catalogo(), [fuente])
  const [f, setF] = useState<Formulario>({ linea: '', periodicidad: 'QUINCENAL', unidad: 'cuotas', plazo: '48', valorMoto: '5100000', pagoInicial: '0', perfil: 'NEGOCIO' })
  const [sim, setSim] = useState<Simulacion | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [calculando, setCalculando] = useState(false)
  const [intentado, setIntentado] = useState(false)
  const inicial = useRef(false)

  const catalogo = cat.datos
  const linea = catalogo?.lineas.find((l) => l.codigo === f.linea) ?? catalogo?.lineas[0]
  const maxMeses = linea?.plazoMaximoMeses ?? 24
  const errores = useMemo(() => validarSimulador(f, maxMeses), [f, maxMeses])
  const hayErrores = Object.keys(errores).length > 0

  async function simular(formulario: Formulario) {
    setCalculando(true)
    setError(null)
    try {
      setSim(await fuente.simular(aCredito(formulario)))
    } catch (e) {
      setSim(null)
      setError(e)
    } finally {
      setCalculando(false)
    }
  }

  // Al cargar el catálogo se fija la línea y se corre una primera simulación con los valores por defecto.
  useEffect(() => {
    if (!catalogo || inicial.current) return
    inicial.current = true
    const siguiente = { ...f, linea: catalogo.lineas[0]?.codigo ?? '', valorMoto: catalogo.valoresPorDefecto.valorMoto ?? f.valorMoto }
    setF(siguiente)
    void simular(siguiente)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [catalogo])

  function cambiarPeriodicidad(p: Periodicidad) {
    // Mantiene el plazo equivalente en meses al cambiar de periodicidad.
    const meses = f.unidad === 'meses' ? Number(f.plazo) : (Number(f.plazo) * 12) / PERIODOS_POR_ANIO[f.periodicidad]
    const plazo = f.unidad === 'meses' ? f.plazo : String(Math.max(1, Math.floor((meses * PERIODOS_POR_ANIO[p]) / 12)))
    setF({ ...f, periodicidad: p, plazo })
  }

  function enviar(e: React.FormEvent) {
    e.preventDefault()
    setIntentado(true)
    if (!hayErrores) void simular(f)
  }

  const verErrores = intentado ? errores : {}
  const infoPer = (p: Periodicidad) => catalogo?.periodicidades.find((x) => x.codigo === p)
  const periodicidadesLinea = (linea?.periodicidades ?? (['SEMANAL', 'QUINCENAL', 'MENSUAL'] as Periodicidad[]))
  const maxCuotas = Math.floor((maxMeses * PERIODOS_POR_ANIO[f.periodicidad]) / 12)

  return (
    <div>
      <Titulo detalle="Calcula la cuota y el plan de pagos de un crédito nuevo. Cambia los datos y pulsa «Simular».">Simulador</Titulo>
      {cat.cargando && <Cargando texto="Cargando las líneas de crédito…" />}
      {cat.error && <MensajeError error={cat.error} alReintentar={cat.recargar} />}
      {catalogo && (
        <form onSubmit={enviar} noValidate className="tarjeta mb-4 grid gap-5 md:grid-cols-2 xl:grid-cols-3" aria-label="Datos del crédito">
          <div className="md:col-span-2 xl:col-span-3">
            <Segmentado
              nombre="linea"
              etiqueta="Línea de crédito"
              diseno="tarjetas"
              columnas="grid-cols-1 min-[480px]:grid-cols-2 xl:grid-cols-4"
              valor={f.linea}
              alCambiar={(linea) => setF({ ...f, linea })}
              opciones={catalogo.lineas.map((l) => ({ valor: l.codigo, etiqueta: l.nombre, nota: `Tasa ${porcentaje(l.tasaEA)} efectiva anual · hasta ${l.plazoMaximoMeses} meses` }))}
            />
          </div>

          <div className="md:col-span-2 xl:col-span-3">
            <Segmentado
              nombre="periodicidad"
              etiqueta="¿Cada cuánto paga el cliente?"
              valor={f.periodicidad}
              alCambiar={cambiarPeriodicidad}
              opciones={periodicidadesLinea.map((p) => ({ valor: p, etiqueta: NOMBRES[p], nota: infoPer(p)?.porConfirmar ? `${infoPer(p)?.periodosPorAnio} periodos/año, por confirmar` : undefined }))}
            />
          </div>

          <div>
            <Campo etiqueta={f.unidad === 'cuotas' ? 'Plazo en cuotas' : 'Plazo en meses'} error={verErrores.plazo} ayuda={`Máximo ${maxMeses} meses (${maxCuotas} cuotas ${NOMBRES[f.periodicidad].toLowerCase()}s)`}>
              {(p) => <EntradaPasos {...p} nombre={f.unidad === 'cuotas' ? 'Plazo en cuotas' : 'Plazo en meses'} valor={f.plazo} maximo={f.unidad === 'cuotas' ? maxCuotas : maxMeses} alCambiar={(plazo) => setF({ ...f, plazo })} />}
            </Campo>
            <button type="button" className="boton-suave mt-2 w-full" aria-label={`Cambiar a plazo en ${f.unidad === 'cuotas' ? 'meses' : 'cuotas'}`} onClick={() => setF({ ...f, unidad: f.unidad === 'cuotas' ? 'meses' : 'cuotas', plazo: f.unidad === 'cuotas' ? String(maxMeses) : String(maxCuotas) })}>
              {f.unidad === 'cuotas' ? 'Prefiero escribirlo en meses' : 'Prefiero escribirlo en cuotas'}
            </button>
          </div>

          <Campo etiqueta="Valor de la moto" error={verErrores.valorMoto} ayuda="En pesos, sin centavos">
            {(p) => <EntradaDinero {...p} valor={f.valorMoto} alCambiar={(valorMoto) => setF({ ...f, valorMoto })} />}
          </Campo>
          <Campo etiqueta="Pago inicial" error={verErrores.pagoInicial} ayuda="Opcional: lo que el cliente da de entrada. 0 si no da nada">
            {(p) => <EntradaDinero {...p} valor={f.pagoInicial} alCambiar={(pagoInicial) => setF({ ...f, pagoInicial })} />}
          </Campo>
          <div className="md:col-span-2 xl:col-span-3">
            <CamposFechas
              valores={fechasDe(f)}
              alCambiar={(fechas) => setF({ ...f, ...fechas })}
              periodicidad={f.periodicidad}
              errores={{ fechaDesembolso: verErrores.fechaDesembolso, primerVencimiento: verErrores.primerVencimiento }}
              modoDemo={fuente.modo === 'demo'}
            />
          </div>
          <div className="md:col-span-2 xl:col-span-3">
            <SelectorPerfil valor={f.perfil} alCambiar={(perfil) => setF({ ...f, perfil })} />
          </div>

          <div className="md:col-span-2 xl:col-span-3">
            <button type="submit" className="boton w-full sm:w-auto sm:min-w-48" disabled={calculando}>
              {calculando ? 'Calculando…' : 'Simular'}
            </button>
            {intentado && hayErrores && <p className="mt-2 text-sm font-semibold text-error">Revisa los datos marcados en rojo.</p>}
          </div>
        </form>
      )}

      {calculando && <Cargando texto="Calculando…" />}
      {error != null && <MensajeError error={error} alReintentar={() => void simular(f)} />}
      {sim && <ResultadoSimulacion sim={sim} />}
    </div>
  )
}

/** R9-SEGURO-TODO-RIESGO: seguro todo riesgo más su IVA de una fila del plan (0 si el perfil no lo causa). */
function todoRiesgoConIva(p: FilaPlan): string {
  return sumar(p.seguroTodoRiesgo ?? '0', p.ivaSeguroTodoRiesgo ?? '0')
}

export function ResultadoSimulacion({ sim }: Readonly<{ sim: Simulacion }>) {
  const { resumen: r, plan } = sim
  const totales = useMemo(
    () => ({
      interes: sumar(...plan.map((p) => p.interes)),
      capital: sumar(...plan.map((p) => p.capital)),
      fondo: sumar(...plan.map((p) => p.fondoGarantias)),
      asistencia: sumar(...plan.map((p) => p.asistencia)),
      seguro: sumar(...plan.map((p) => p.seguro)),
      todoRiesgo: sumar(...plan.map(todoRiesgoConIva)),
      cuota: sumar(...plan.map((p) => p.cuotaTotal)),
    }),
    [plan],
  )
  // R9-SEGURO-TODO-RIESGO: la columna solo aparece si el perfil lo causa (con EXCEL no viaja).
  const conTodoRiesgo = plan.some((p) => p.seguroTodoRiesgo !== undefined)
  const periodos = plan.map((p) => p.periodo)
  return (
    <section aria-label="Resultado de la simulación" className="grid gap-4">
      {r.porConfirmar.length > 0 && (
        <ul className="rounded-xl bg-aviso p-3 text-sm font-semibold text-on-aviso">
          {r.porConfirmar.map((t) => (
            <li key={t}>Por confirmar: {sinJerga(t)}</li>
          ))}
        </ul>
      )}
      <dl className="grid grid-cols-1 gap-3 min-[480px]:grid-cols-2 lg:grid-cols-4">
        <Tarjeta destacada etiqueta="Cuota total" valor={cop(r.cuotaTotal)} ayuda={`${r.cuotas} cuotas ${NOMBRES[r.periodicidad].toLowerCase()}s${r.prorrateoPrimeraCuota?.aplicado ? '; la primera va prorrateada' : ''}`} />
        <Tarjeta etiqueta="Cuota financiera" valor={cop(r.cuotaFinanciera)} ayuda="Capital más interés, sin servicios" />
        <Tarjeta etiqueta="Tasa del periodo" valor={porcentaje(r.tasaPeriodo)} ayuda={`Equivale a ${porcentaje(r.tasaEA)} efectivo anual`} />
        <Tarjeta etiqueta="Total pagado" valor={cop(r.totalPagado)} ayuda={`Sobre ${cop(r.montoFinanciado)} financiados`} />
        <Tarjeta etiqueta="Intereses" valor={cop(r.totalIntereses)} />
        <Tarjeta etiqueta="Servicios" valor={cop(r.totalServicios)} ayuda={conTodoRiesgo ? 'Fondo de garantías, asistencia, seguro de vida y seguro todo riesgo con IVA' : 'Fondo de garantías, asistencia y seguro'} />
      </dl>
      {r.prorrateoPrimeraCuota && <AvisoProrrateo prorrateo={r.prorrateoPrimeraCuota} />}

      <div className="grid gap-4 lg:grid-cols-2">
        <GraficoSeries titulo="Saldo de capital" descripcion="Cuánto capital queda por pagar al cierre de cada periodo." periodos={periodos} apilado={false} series={[{ nombre: 'Saldo', color: 'var(--c-primary)', valores: plan.map((p) => p.saldo) }]} />
        <GraficoSeries
          titulo="Composición de la cuota"
          descripcion="Cuánto de cada cuota es capital, interés y servicios."
          periodos={periodos}
          apilado
          series={[
            { nombre: 'Capital', color: 'var(--c-primary)', valores: plan.map((p) => p.capital) },
            { nombre: 'Interés', color: 'var(--serie-2)', valores: plan.map((p) => p.interes) },
            { nombre: 'Servicios', color: 'var(--c-outline)', valores: plan.map((p) => sumar(p.fondoGarantias, p.asistencia, p.seguro, todoRiesgoConIva(p))) },
          ]}
        />
      </div>

      <div>
        <h2 className="mb-2 text-lg font-bold">Plan de pagos</h2>
        <div className="scroll-tabla" tabIndex={0} role="region" aria-label="Tabla del plan de pagos">
          <table>
            <thead>
              <tr>
                <th scope="col">Periodo</th>
                <th scope="col">Interés</th>
                <th scope="col">Capital</th>
                <th scope="col">Fondo garantías</th>
                <th scope="col">Asistencia</th>
                <th scope="col">Seguro</th>
                {conTodoRiesgo && <th scope="col">Todo riesgo + IVA</th>}
                <th scope="col">Cuota total</th>
                <th scope="col">Saldo</th>
              </tr>
            </thead>
            <tbody>
              {plan.map((p) => (
                <tr key={p.periodo}>
                  <th scope="row">{p.periodo}</th>
                  <td>{cop(p.interes)}</td>
                  <td>{cop(p.capital)}</td>
                  <td>{cop(p.fondoGarantias)}</td>
                  <td>{cop(p.asistencia)}</td>
                  <td>{cop(p.seguro)}</td>
                  {conTodoRiesgo && <td>{cop(todoRiesgoConIva(p))}</td>}
                  <td className="font-semibold">{cop(p.cuotaTotal)}</td>
                  <td>{cop(p.saldo)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="font-bold">
                <td>Totales</td>
                <td>{cop(totales.interes)}</td>
                <td>{cop(totales.capital)}</td>
                <td>{cop(totales.fondo)}</td>
                <td>{cop(totales.asistencia)}</td>
                <td>{cop(totales.seguro)}</td>
                {conTodoRiesgo && <td>{cop(totales.todoRiesgo)}</td>}
                <td>{cop(totales.cuota)}</td>
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    </section>
  )
}
