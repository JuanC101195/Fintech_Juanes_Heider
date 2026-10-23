// Los tipos de src/tipos.ts contra las respuestas REALES del API (docs/api-ejemplos/*.json, que genera
// EjemplosApiTest en cada ./mvnw test) y contra los fixtures de la demostración (src/demo/descargar.mjs).
// Los Record de abajo obligan a declarar TODAS las claves del tipo (si se agrega una al tipo y no aquí, no compila),
// y las pruebas exigen que el JSON real tenga exactamente esas claves y esa forma.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { esDecimal } from './dinero'
import type { Asiento, Catalogo, CasoDetalle, CasoResumen, Credito, DeudaExtinguida, Ejecucion, FilaPlan, InfoPeriodicidad, Linea, LineaAsiento, Movimiento, ProrrateoPrimeraCuota, ResumenEjecucion, ResumenSimulacion, Servicios, Simulacion } from './tipos'

type Forma = 'dinero' | 'texto' | 'entero' | 'booleano' | 'servicios' | 'deuda' | 'fecha'

const MOVIMIENTO: Record<keyof Movimiento, Forma> = {
  periodo: 'entero', mes: 'entero', estado: 'texto', evento: 'texto', etiqueta: 'texto',
  saldoInicial: 'dinero', tasaPeriodica: 'dinero', cuotaFinanciera: 'dinero', interesCorriente: 'dinero', interesMora: 'dinero',
  capitalContractual: 'dinero', cobranzaCausada: 'dinero', serviciosCausados: 'servicios', pagoRecibido: 'dinero', pagoCobranza: 'dinero',
  pagoMora: 'dinero', pagoInteresCorriente: 'dinero', pagoServicios: 'dinero', pagoCapital: 'dinero', abonoExtra: 'dinero', prepagoCapital: 'dinero',
  cxcCobranza: 'dinero', cxcMora: 'dinero', cxcInteresCorriente: 'dinero', cxcServicios: 'dinero', capitalVencido: 'dinero', capitalVigente: 'dinero',
  saldoFinal: 'dinero', cuotasVencidas: 'entero', deudaAExtinguir: 'dinero', inventario: 'dinero', cxcFondoGarantias: 'dinero', cxpDeudor: 'dinero',
  pagoTerceros: 'servicios', pagosServicios: 'servicios', cxcServiciosPorConcepto: 'servicios', extinguido: 'deuda', capitalVencidoInicial: 'dinero',
  pendientesPagadosExtra: 'deuda', interesPrepago: 'dinero', pagoTotalCliente: 'dinero', pagoExtraordinario: 'dinero',
  costoRecuperacion: 'dinero', costoRecuperacionAsumido: 'dinero', listoParaRecuperacion: 'booleano',
}
const SERVICIOS: Record<keyof Servicios, Forma> = { fondoGarantias: 'dinero', asistencia: 'dinero', seguro: 'dinero', total: 'dinero' }
const DEUDA: Record<keyof DeudaExtinguida, Forma> = { cobranza: 'dinero', mora: 'dinero', interesCorriente: 'dinero', servicios: 'servicios', capital: 'dinero', total: 'dinero' }
const LINEA_ASIENTO: Record<keyof LineaAsiento, Forma> = { cuenta: 'texto', nombreCuenta: 'texto', debito: 'dinero', credito: 'dinero', explicacion: 'texto' }
const ASIENTO: Record<keyof Asiento, Forma> = { periodo: 'entero', evento: 'texto', lineas: 'texto', totalDebitos: 'dinero', totalCreditos: 'dinero', cuadra: 'booleano' }
const FILA_PLAN: Record<Exclude<keyof FilaPlan, never>, Forma> = {
  periodo: 'entero', mes: 'entero', saldoInicial: 'dinero', cuotaFinanciera: 'dinero', interes: 'dinero', capital: 'dinero',
  fondoGarantias: 'dinero', asistencia: 'dinero', seguro: 'dinero', cuotaTotal: 'dinero', saldo: 'dinero',
}

const PRORRATEO: Record<keyof ProrrateoPrimeraCuota, Forma> = {
  fechaDesembolso: 'fecha', primerVencimiento: 'fecha', diasPrimerPeriodo: 'entero', diasNominales: 'entero', aplicado: 'booleano',
  interesSinProrrateo: 'dinero', interesProrrateado: 'dinero', diferencia: 'dinero',
}

const raiz = path.resolve(__dirname, '..', '..')
const leer = <T>(...partes: string[]): T => JSON.parse(readFileSync(path.join(raiz, ...partes), 'utf8')) as T

/** Compara un objeto real contra su plantilla: mismas claves y misma forma de cada valor. */
function validar(objeto: unknown, plantilla: Record<string, Forma>, donde: string): void {
  expect(objeto, donde).toBeTypeOf('object')
  const real = objeto as Record<string, unknown>
  expect(Object.keys(real).sort(), `claves de ${donde}`).toEqual(Object.keys(plantilla).sort())
  for (const [clave, forma] of Object.entries(plantilla)) {
    const v = real[clave]
    const aqui = `${donde}.${clave}`
    switch (forma) {
      case 'dinero':
        expect(typeof v === 'string' && esDecimal(v), `${aqui} debe ser string decimal, llegó ${JSON.stringify(v)}`).toBe(true)
        break
      case 'texto':
        expect(typeof v, aqui).toBe('string')
        break
      case 'entero':
        expect(Number.isInteger(v), aqui).toBe(true)
        break
      case 'booleano':
        expect(typeof v, aqui).toBe('boolean')
        break
      case 'fecha':
        expect(typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v), `${aqui} debe ser fecha yyyy-MM-dd`).toBe(true)
        break
      case 'servicios':
        validar(v, SERVICIOS, aqui)
        break
      case 'deuda':
        validar(v, DEUDA, aqui)
        break
    }
  }
}

describe('caso 13 (docs/api-ejemplos/caso-13-negocio.json) contra los tipos', () => {
  const caso = leer<CasoDetalle>('docs', 'api-ejemplos', 'caso-13-negocio.json')

  it('cada movimiento tiene exactamente los campos de Movimiento, con pendientesPagadosExtra siempre como objeto', () => {
    expect(caso.movimientos.length).toBeGreaterThan(80)
    for (const m of caso.movimientos) validar(m, MOVIMIENTO, `movimiento ${m.periodo}`)
  })

  it('cada asiento trae nombreCuenta, explicacion, totales y cuadra al centavo', () => {
    for (const a of caso.asientos ?? []) {
      validar({ ...a, lineas: 'x' }, ASIENTO, `asiento ${a.periodo}`)
      for (const l of a.lineas) validar(l, LINEA_ASIENTO, `línea del asiento ${a.periodo}`)
      expect(a.cuadra).toBe(true)
    }
  })

  it('credito trae plazoLimitePeriodos y el resumen los campos agregados', () => {
    expect(caso.credito.plazoLimitePeriodos).toBe(120)
    expect(caso.perfilReglas).toBe('NEGOCIO')
    expect(Object.keys(caso.resumen).sort()).toEqual(['cuotasVencidasMax', 'estadoFinal', 'listoParaRecuperacion', 'montoFinanciado', 'perfilReglas', 'periodoFinal', 'periodoListoParaRecuperacion', 'saldoFinal', 'totalPagadoCliente'])
  })

  it('el dinero llega sin redondear: más de 2 decimales', () => {
    const m = caso.movimientos.find((x) => x.periodo === 42)!
    expect(m.saldoFinal.split('.')[1].length).toBeGreaterThan(10)
  })

  it('las instrucciones usan modalidad y tipoRecuperacion del API', () => {
    const tipos = caso.instrucciones.map((i) => i.tipo)
    expect(tipos).toContain('ABONO_EXTRA')
    expect(caso.instrucciones.find((i) => i.tipo === 'ABONO_EXTRA')).toMatchObject({ modalidad: 'MENOR_PLAZO' })
  })
})

describe('catálogo y simulación reales contra los tipos', () => {
  it('el catálogo declara DACION_EN_PAGO / RETOMA y las modalidades', () => {
    const c = leer<Catalogo>('docs', 'api-ejemplos', 'catalogo.json')
    expect(c.tiposRecuperacion).toEqual(['DACION_EN_PAGO', 'RETOMA'])
    expect(c.modalidadesAbono).toEqual(['MENOR_PLAZO', 'MENOR_CUOTA'])
    expect(c.modalidadesCambioTasa).toEqual(['MISMA_FECHA_FINAL', 'MISMA_CUOTA'])
    expect(c.perfilesReglas).toEqual(['EXCEL', 'NEGOCIO'])
    expect(Object.keys(c.periodicidades[0]).sort()).toEqual(['adjetivo', 'codigo', 'cuotasMaximas', 'diasNominales', 'periodosPorAnio', 'porConfirmar', 'unidadCuota'])
  })

  it('cada fila del plan tiene los campos de FilaPlan', () => {
    const s = leer<Simulacion>('docs', 'api-ejemplos', 'simulacion-quincenal.json')
    expect(s.plan).toHaveLength(48)
    for (const f of s.plan) validar(f, FILA_PLAN, `fila ${f.periodo}`)
  })
})

/** Claves exactas de cada objeto del contrato (los Record obligan a declarar todas las claves del tipo de tipos.ts). */
const CLAVES_LINEA: Record<keyof Linea, true> = { codigo: true, nombre: true, tasaEA: true, plazoMaximoMeses: true, periodicidades: true }
const CLAVES_PERIODICIDAD: Record<keyof InfoPeriodicidad, true> = { codigo: true, periodosPorAnio: true, adjetivo: true, porConfirmar: true, diasNominales: true, unidadCuota: true, cuotasMaximas: true }
const CLAVES_RESUMEN_SIMULACION: Record<Exclude<keyof ResumenSimulacion, 'prorrateoPrimeraCuota'>, true> = {
  linea: true, perfilReglas: true, serviciosPorCuota: true, montoFinanciado: true, tasaEA: true, tasaPeriodo: true, cuotas: true,
  cuotaFinanciera: true, cuotaTotal: true, totalPagado: true, totalIntereses: true, totalServicios: true, periodicidad: true, porConfirmar: true,
}
const CLAVES_RESUMEN_EJECUCION: Record<Exclude<keyof ResumenEjecucion, 'prorrateoPrimeraCuota'>, true> = {
  periodoFinal: true, estadoFinal: true, saldoFinal: true, totalPagadoCliente: true, cuotasVencidasMax: true, montoFinanciado: true,
  perfilReglas: true, listoParaRecuperacion: true, periodoListoParaRecuperacion: true,
}
const CLAVES_CASO_RESUMEN: Record<keyof CasoResumen, true> = { numero: true, nombre: true, descripcion: true, fuente: true }
// Las que el API omite cuando no aplican (@JsonInclude NON_NULL) son opcionales en el tipo; el ejemplo trae un subconjunto.
const CLAVES_CREDITO: Record<keyof Credito, true> = {
  linea: true, periodicidad: true, cuotas: true, meses: true, valorMoto: true, pagoInicial: true, perfilReglas: true, matriculaRunt: true, soat: true,
  prenda: true, gpsPlataforma: true, gastosComerciales: true, plazoLimitePeriodos: true, cobranzaActiva: true, fechaDesembolso: true, primerVencimiento: true,
}

const claves = (o: unknown) => Object.keys(o as object).sort()

describe('claves de los objetos del contrato (Java ↔ tipos.ts ↔ ejemplos reales)', () => {
  it('catálogo: líneas y periodicidades', () => {
    const c = leer<Catalogo>('docs', 'api-ejemplos', 'catalogo.json')
    expect(c.lineas.length).toBeGreaterThan(0)
    for (const l of c.lineas) expect(claves(l)).toEqual(claves(CLAVES_LINEA))
    for (const p of c.periodicidades) expect(claves(p)).toEqual(claves(CLAVES_PERIODICIDAD))
    expect(claves(c)).toEqual(['lineas', 'modalidadesAbono', 'modalidadesCambioTasa', 'perfilesReglas', 'periodicidades', 'tiposInstruccion', 'tiposRecuperacion', 'valoresPorDefecto'])
  })

  it('resúmenes de simulación y de ejecución', () => {
    const s = leer<Simulacion>('docs', 'api-ejemplos', 'simulacion-quincenal.json')
    expect(claves(s.resumen)).toEqual(claves(CLAVES_RESUMEN_SIMULACION))
    const conFechas = leer<Simulacion>('docs', 'api-ejemplos', 'simulacion-mensual-prorrateo.json')
    expect(claves(conFechas.resumen)).toEqual([...claves(CLAVES_RESUMEN_SIMULACION), 'prorrateoPrimeraCuota'].sort())
    const e = leer<Ejecucion>('docs', 'api-ejemplos', 'escenario-semanal-prorrateo.json')
    expect(claves(e.resumen)).toEqual([...claves(CLAVES_RESUMEN_EJECUCION), 'prorrateoPrimeraCuota'].sort())
    expect(claves(leer<CasoDetalle>('docs', 'api-ejemplos', 'caso-13-negocio.json').resumen)).toEqual(claves(CLAVES_RESUMEN_EJECUCION))
  })

  it('caso 13: resumen del caso, crédito e instrucciones solo con claves del tipo', () => {
    const c = leer<CasoDetalle>('docs', 'api-ejemplos', 'caso-13-negocio.json')
    expect(claves(c.caso)).toEqual(claves(CLAVES_CASO_RESUMEN))
    expect(Object.keys(c.credito).filter((k) => !(k in CLAVES_CREDITO))).toEqual([])
    const permitidas = ['periodo', 'tipo', 'valor', 'fraccion', 'diasAtraso', 'causaMoraYServicios', 'modalidad', 'diasDesdeUltimaCuota', 'usuraEA', 'nuevaTasaEA', 'tipoRecuperacion', 'valorMoto', 'costoRecuperacion']
    for (const i of c.instrucciones) expect(Object.keys(i).filter((k) => !permitidas.includes(k))).toEqual([])
  })
})

describe('prorrateo de la primera cuota (P5-PRORRATEO) contra los tipos', () => {
  it('sin fechas el resumen no trae prorrateoPrimeraCuota (compatibilidad total)', () => {
    expect(leer<Simulacion>('docs', 'api-ejemplos', 'simulacion-quincenal.json').resumen).not.toHaveProperty('prorrateoPrimeraCuota')
    const caso = leer<CasoDetalle>('docs', 'api-ejemplos', 'caso-13-negocio.json')
    expect(caso.resumen).not.toHaveProperty('prorrateoPrimeraCuota')
    expect(caso.credito).not.toHaveProperty('fechaDesembolso')
  })

  it('la simulación mensual con fechas trae el prorrateo y el interés de la fila 1 es el prorrateado', () => {
    const s = leer<Simulacion>('docs', 'api-ejemplos', 'simulacion-mensual-prorrateo.json')
    const p = s.resumen.prorrateoPrimeraCuota!
    validar(p, PRORRATEO, 'prorrateoPrimeraCuota')
    expect(p).toMatchObject({ fechaDesembolso: '2026-10-23', primerVencimiento: '2026-12-05', diasPrimerPeriodo: 43, diasNominales: 30, aplicado: true })
    expect(s.plan[0].interes).toBe(p.interesProrrateado)
    for (const f of s.plan) validar(f, FILA_PLAN, `fila ${f.periodo}`)
    // El resumen muestra la cuota regular (periodo 2), no la prorrateada.
    expect(s.resumen.cuotaFinanciera).toBe(s.plan[1].cuotaFinanciera)
  })

  it('el escenario semanal con fechas trae el prorrateo en el resumen y el interés en el movimiento 1', () => {
    const e = leer<Ejecucion>('docs', 'api-ejemplos', 'escenario-semanal-prorrateo.json')
    const p = e.resumen.prorrateoPrimeraCuota!
    validar(p, PRORRATEO, 'prorrateoPrimeraCuota')
    expect(p.diasPrimerPeriodo).toBe(11)
    expect(e.movimientos[1].interesCorriente).toBe(p.interesProrrateado)
    for (const m of e.movimientos.slice(0, 5)) validar(m, MOVIMIENTO, `movimiento ${m.periodo}`)
  })
})

describe('fixtures de la demostración (respuestas reales)', () => {
  it('todos los casos, en ambos perfiles, respetan la forma de Movimiento', () => {
    for (let n = 1; n <= 13; n++) {
      for (const perfil of ['EXCEL', 'NEGOCIO']) {
        const c = leer<CasoDetalle>('consola', 'src', 'demo', 'fixtures', `caso-${n}-${perfil}.json`)
        expect(c.perfilReglas).toBe(perfil)
        validar(c.movimientos[0], recortadoSiHaceFalta(c.movimientos[0]), `caso ${n} ${perfil}`)
        for (const a of c.asientos ?? []) expect(a.cuadra, `caso ${n} ${perfil} periodo ${a.periodo}`).toBe(true)
      }
    }
  })
})

/** Los fixtures recortados (descargar.mjs) conservan un subconjunto de Movimiento; se valida el que traen. */
function recortadoSiHaceFalta(m: unknown): Record<string, Forma> {
  const claves = Object.keys(m as object)
  const desconocidas = claves.filter((k) => !(k in MOVIMIENTO))
  expect(desconocidas, 'campos del API que Movimiento no declara').toEqual([])
  return Object.fromEntries(claves.map((k) => [k, MOVIMIENTO[k as keyof Movimiento]]))
}
