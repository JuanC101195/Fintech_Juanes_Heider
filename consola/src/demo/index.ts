import { ErrorDeApi, type FuenteDatos } from '../api/cliente'
import { aMicros, comparar } from '../dinero'
import type { Catalogo, CasoDetalle, CasoResumen, Credito, Ejecucion, Perfil, PeticionEjecucion, Reglas, Simulacion } from '../tipos'

// Los fixtures viven en ./fixtures/*.json con el mismo formato que el API. Para usar los JSON reales de
// docs/api-ejemplos/ basta con copiarlos ahí con estos nombres:
//   catalogo.json, reglas.json, casos.json, simulacion-{semanal|quincenal|mensual}.json, caso-{N}-{EXCEL|NEGOCIO}.json
const archivos = import.meta.glob('./fixtures/*.json', { import: 'default' }) as Record<string, () => Promise<unknown>>

export async function cargarFixture<T>(nombre: string): Promise<T> {
  const cargador = archivos[`./fixtures/${nombre}.json`]
  if (!cargador) throw new ErrorDeApi(404, { codigo: 'FIXTURE_NO_ENCONTRADO', mensaje: `No hay fixture de demostración "${nombre}"` })
  return (await cargador()) as T
}

const PERIODOS_POR_ANIO = { SEMANAL: 52, QUINCENAL: 24, MENSUAL: 12 } as const
const CASOS = 13

/** Instrucciones iguales (mismo orden de campos no importa): sirve para reconocer que el laboratorio ejecuta un caso. */
function mismasInstrucciones(a: unknown[], b: unknown[]): boolean {
  const canon = (x: unknown) => JSON.stringify(x, (_k, v) => (v && typeof v === 'object' && !Array.isArray(v) ? Object.fromEntries(Object.entries(v).sort()) : v))
  return a.length === b.length && a.every((x, i) => canon(x) === canon(b[i]))
}

async function elegirCaso(peticion: PeticionEjecucion): Promise<CasoDetalle> {
  const perfil = peticion.perfilReglas
  const tipos = new Set(peticion.instrucciones.map((i) => i.tipo))
  let mejor = 1
  let puntaje = -1
  for (let n = 1; n <= CASOS; n++) {
    const c = await demo.caso(n, perfil)
    if (mismasInstrucciones(c.instrucciones, peticion.instrucciones)) return c
    const t = new Set(c.instrucciones.map((i) => i.tipo))
    const comunes = [...tipos].filter((x) => t.has(x)).length
    const p = comunes / (tipos.size + t.size - comunes || 1)
    if (p > puntaje) {
      puntaje = p
      mejor = n
    }
  }
  return demo.caso(mejor, perfil)
}

/** Regla ABONO_MAYOR_QUE_SALDO (P-14): el abono no puede superar el saldo de capital en ese periodo. */
function validarAbonos(peticion: PeticionEjecucion, base: CasoDetalle): void {
  for (const i of peticion.instrucciones) {
    if (i.tipo !== 'ABONO_EXTRA' || !i.valor) continue
    const anterior = base.movimientos.filter((m) => m.periodo < i.periodo).at(-1)
    const saldo = anterior?.saldoFinal ?? base.movimientos[0]?.saldoInicial
    if (saldo && comparar(i.valor, saldo) > 0) {
      throw new ErrorDeApi(400, {
        codigo: 'ABONO_MAYOR_QUE_SALDO',
        mensaje: `El abono de la semana ${i.periodo} supera el saldo de capital`,
        detalle: { periodo: i.periodo, valorAbono: i.valor, saldo },
      })
    }
  }
}

function validarPlazo(credito: Credito, maxMeses: number): void {
  const ppa = PERIODOS_POR_ANIO[credito.periodicidad]
  const cuotas = credito.cuotas ?? Math.floor(((credito.meses ?? 0) * ppa) / 12)
  const meses = credito.meses ?? (cuotas * 12) / ppa
  if (!(cuotas >= 1) || meses > maxMeses) {
    throw new ErrorDeApi(400, { codigo: 'PLAZO_MAXIMO', mensaje: `El plazo máximo es de ${maxMeses} meses`, detalle: { meses, maximoMeses: maxMeses } })
  }
  if (aMicros(credito.valorMoto) <= 0n) throw new ErrorDeApi(400, { codigo: 'VALOR_MOTO_INVALIDO', mensaje: 'El valor de la moto debe ser positivo' })
}

/** Fuente de demostración: sirve los fixtures; las cifras no cambian con los parámetros (solo se validan). */
export const demo: FuenteDatos = {
  modo: 'demo',
  catalogo: () => cargarFixture<Catalogo>('catalogo'),
  reglas: () => cargarFixture<Reglas>('reglas'),
  casos: () => cargarFixture<CasoResumen[]>('casos'),
  async simular(credito) {
    const cat = await cargarFixture<Catalogo>('catalogo')
    validarPlazo(credito, cat.lineas[0]?.plazoMaximoMeses ?? 24)
    return cargarFixture<Simulacion>(`simulacion-${credito.periodicidad.toLowerCase()}`)
  },
  async caso(numero, perfil: Perfil) {
    try {
      return await cargarFixture<CasoDetalle>(`caso-${numero}-${perfil}`)
    } catch (e) {
      if (perfil === 'NEGOCIO') throw e
      return cargarFixture<CasoDetalle>(`caso-${numero}-NEGOCIO`)
    }
  },
  async ejecutar(peticion): Promise<Ejecucion> {
    const base = await elegirCaso(peticion)
    validarAbonos(peticion, base)
    const { resumen, movimientos, asientos } = base
    return { resumen, movimientos, ...(peticion.incluirAsientos ? { asientos } : {}) }
  },
}

/** Intenta el API real y, si no hay red, cae a la demostración avisando con `alCaer`. */
export function conRespaldo(api: FuenteDatos, alternativa: FuenteDatos, alCaer: () => void, esFalloDeRed: (e: unknown) => boolean): FuenteDatos {
  let caido = false
  const llamar = async <T>(f: (d: FuenteDatos) => Promise<T>): Promise<T> => {
    if (caido) return f(alternativa)
    try {
      return await f(api)
    } catch (e) {
      if (!esFalloDeRed(e)) throw e
      caido = true
      alCaer()
      return f(alternativa)
    }
  }
  return {
    get modo() {
      return caido ? 'demo' : 'api'
    },
    catalogo: () => llamar((d) => d.catalogo()),
    reglas: () => llamar((d) => d.reglas()),
    casos: () => llamar((d) => d.casos()),
    simular: (c) => llamar((d) => d.simular(c)),
    ejecutar: (p) => llamar((d) => d.ejecutar(p)),
    caso: (n, perfil) => llamar((d) => d.caso(n, perfil)),
  }
}
