const test = require('node:test')
const assert = require('node:assert')
const { crearManejador, crearLimitador, variantes } = require('./contrato')

const CLAVE = 'clave-de-prueba'
const base = { metodo: 'GET', origen: 'https://juanc101195.github.io', clave: CLAVE, ip: '1.1.1.1', numero: '1001' }

const filaContrato = (id, inicio, extra = {}) => ({
  id, sede: 'X', contrato: '1001', cohorte: '2024-01', estado_real: 'activo', fecha_inicio: inicio, fecha_fin: null,
  cuotas: '52', cuota_real_miles: '100.5', inversion: '5000000', inversion_imputada: false, linea_usura: 'consumo',
  tasa_inicial_ea: '0.25', pagos_reales: 2, multas_reales_miles: '10', dias_justificados: 3, ...extra,
})
const filaResultado = (id, corrida) => ({
  corrida, id, sim_pagado: '100', sim_saldo_final: '50', sim_pendientes: '25', sim_mora: '1', sim_cobranza: '2', sim_interes: '3',
  sim_servicios: '4', sim_cuotas_vencidas_final: '0', sim_cuotas_vencidas_max: '2', sim_marca_recuperacion: true, sim_periodo_marca: 7,
  sim_estado_final: 'pagado', sim_periodos: 52, periodo_corte: 52, real_ea_con_multas: '0.31',
})
const filaPeriodo = (id, corrida, n) => ({
  corrida, id, periodo: n, fecha_vencimiento: '2024-02-01', instruccion: 'x', valor_real: '0', dias_atraso: 0, justificado: false,
  estado: 'sin_pago', cuotas_vencidas: '1.5', saldo_inicial: '1000', interes_corriente: '10', interes_mora: '0', cobranza: '0',
  servicios: '0', pago_total_cliente: '0', saldo_final: '1010', listo_recuperacion: false, tasa_ea: '0.2', usura_ea: '0.27', cuota_financiera: '100',
  pago_capital: '0', pago_interes: '0', pago_mora: '0', pago_cobranza: '0', pago_servicios: '0', capital_vencido: '100',
})

function falso(datos = {}) {
  const llamadas = []
  const consultar = async (sql, params) => {
    llamadas.push({ sql, params })
    if (/FROM contrato/.test(sql)) return datos.contratos ?? []
    if (/FROM pago_real/.test(sql)) return datos.pagos ?? []
    if (/FROM resultado/.test(sql)) return datos.resultados ?? []
    if (/FROM periodo/.test(sql)) return datos.periodos ?? []
    throw new Error('consulta inesperada')
  }
  return { consultar, llamadas }
}
const manejador = (datos, extra = {}) => {
  const f = falso(datos)
  return { m: crearManejador({ consultar: f.consultar, claveEsperada: CLAVE, ...extra }), f }
}

test('sin clave o con clave incorrecta responde 401 sin pistas y no toca la base', async () => {
  const { m, f } = manejador()
  for (const clave of [undefined, '', 'otra']) {
    const r = await m({ ...base, clave })
    assert.strictEqual(r.status, 401)
    assert.deepStrictEqual(r.cuerpo, { error: 'clave' })
  }
  assert.strictEqual(f.llamadas.length, 0)
})

test('si la variable CLAVE_CONSULTA no está configurada, nadie entra', async () => {
  const m = crearManejador({ consultar: falso().consultar, claveEsperada: undefined })
  assert.strictEqual((await m({ ...base, clave: '' })).status, 401)
  assert.strictEqual((await m({ ...base, clave: 'undefined' })).status, 401)
})

test('valida el número: solo dígitos, 1 a 10', async () => {
  const { m } = manejador()
  for (const numero of [undefined, '', 'abc', '12a', '1 2', "1' OR 1=1", '12345678901', '-1', '1.5']) {
    assert.strictEqual((await m({ ...base, numero })).status, 400, String(numero))
  }
  assert.strictEqual((await m({ ...base, numero: '1234567890' })).status, 404)
})

test('busca el número tal cual y sin ceros a la izquierda', async () => {
  assert.deepStrictEqual(variantes('0123'), ['0123', '123'])
  assert.deepStrictEqual(variantes('123'), ['123'])
  assert.deepStrictEqual(variantes('000'), ['000', '0'])
  const { m, f } = manejador()
  await m({ ...base, numero: '0123' })
  assert.deepStrictEqual(f.llamadas[0].params, [['0123', '123']])
  assert.match(f.llamadas[0].sql, /^SELECT/)
})

test('contrato sin episodios responde 404', async () => {
  const { m, f } = manejador()
  const r = await m(base)
  assert.strictEqual(r.status, 404)
  assert.deepStrictEqual(r.cuerpo, { error: 'no_encontrado' })
  assert.strictEqual(f.llamadas.length, 1)
})

test('forma de la respuesta: números convertidos, derivados y 4 consultas', async () => {
  const { m, f } = manejador({
    contratos: [filaContrato('A', '2024-01-01')],
    pagos: [{ id: 'A', fecha: '2024-01-08', valor_miles: '100', multa_miles: null }, { id: 'A', fecha: '2024-01-15', valor_miles: '50.5', multa_miles: '5' }],
    resultados: [filaResultado('A', 'sin_justificaciones'), filaResultado('A', 'con_justificaciones')],
    periodos: [filaPeriodo('A', 'sin_justificaciones', 1), filaPeriodo('A', 'con_justificaciones', 1), filaPeriodo('A', 'con_justificaciones', 2)],
  })
  const r = await m(base)
  assert.strictEqual(r.status, 200)
  assert.strictEqual(f.llamadas.length, 4)
  assert.strictEqual(r.cuerpo.numero, '1001')
  const e = r.cuerpo.episodios[0]
  assert.strictEqual(e.id, 'A')
  assert.strictEqual(e.fechaFin, null)
  assert.strictEqual(e.cuotas, 52)
  assert.strictEqual(e.cuotaRealMiles, 100.5)
  assert.strictEqual(e.tasaInicialEA, 0.25)
  assert.strictEqual(e.realPagadoCuotas, 150500)
  assert.strictEqual(e.realMultas, 10000)
  assert.deepStrictEqual(e.pagosReales[0], { fecha: '2024-01-08', valorMiles: 100, multaMiles: null })
  assert.deepStrictEqual(Object.keys(e.corridas).sort(), ['con_justificaciones', 'sin_justificaciones'])
  const c = e.corridas.sin_justificaciones
  assert.strictEqual(c.resumen.simTotalConDeuda, 175)
  assert.strictEqual(c.resumen.simMarcaRecuperacion, true)
  assert.strictEqual(c.resumen.realEaConMultas, 0.31)
  assert.strictEqual(c.periodos.length, 1)
  assert.strictEqual(e.corridas.con_justificaciones.periodos.length, 2)
  const p = c.periodos[0]
  assert.strictEqual(p.cuotasVencidas, 1.5)
  assert.strictEqual(p.saldoFinal, 1010)
  assert.strictEqual(p.listoRecuperacion, false)
  assert.strictEqual(p.fechaVencimiento, '2024-02-01')
  assert.strictEqual(typeof p.tasaEA, 'number')
  assert.strictEqual(p.usuraEA, 0.27)
  assert.strictEqual(c.resumen.realEA, 0.31)
})

test('varios episodios se devuelven todos, cada uno con lo suyo', async () => {
  const { m } = manejador({
    contratos: [filaContrato('A', '2023-01-01', { fecha_fin: '2023-12-01' }), filaContrato('B', '2024-06-01', { inversion_imputada: true })],
    pagos: [{ id: 'B', fecha: '2024-06-08', valor_miles: '10', multa_miles: '0' }],
    resultados: [filaResultado('A', 'sin_justificaciones'), filaResultado('B', 'sin_justificaciones')],
    periodos: [filaPeriodo('A', 'sin_justificaciones', 1), filaPeriodo('B', 'sin_justificaciones', 1), filaPeriodo('B', 'sin_justificaciones', 2)],
  })
  const r = await m(base)
  const [a, b] = r.cuerpo.episodios
  assert.deepStrictEqual([a.id, b.id], ['A', 'B'])
  assert.strictEqual(a.fechaFin, '2023-12-01')
  assert.strictEqual(b.inversionImputada, true)
  assert.strictEqual(a.pagosReales.length, 0)
  assert.strictEqual(a.realPagadoCuotas, 0)
  assert.strictEqual(a.corridas.sin_justificaciones.periodos.length, 1)
  assert.strictEqual(b.corridas.sin_justificaciones.periodos.length, 2)
})

test('un error de la base responde 500 genérico y no filtra el mensaje', async () => {
  const logs = []
  const m = crearManejador({
    consultar: async () => { throw Object.assign(new Error('password authentication failed for user secreto'), { code: '28P01' }) },
    claveEsperada: CLAVE,
    registrar: (t) => logs.push(t),
  })
  const r = await m(base)
  assert.strictEqual(r.status, 500)
  assert.deepStrictEqual(r.cuerpo, { error: 'interno' })
  assert.ok(logs.length === 1 && logs.every((l) => !l.includes('secreto') && !l.includes(CLAVE)))
})

test('limitador: la consulta 61 de la misma IP en un minuto da 429; otra IP no', async () => {
  let t = 1000
  const { m } = manejador({}, { limitador: crearLimitador(60, () => t) })
  for (let i = 0; i < 60; i++) assert.strictEqual((await m(base)).status, 404)
  const r = await m(base)
  assert.strictEqual(r.status, 429)
  assert.deepStrictEqual(r.cuerpo, { error: 'demasiadas_consultas' })
  assert.strictEqual((await m({ ...base, ip: '2.2.2.2' })).status, 404)
  t += 60000
  assert.strictEqual((await m(base)).status, 404)
})

test('CORS: orígenes permitidos reciben la cabecera, otros no; OPTIONS responde 204', async () => {
  const { m } = manejador()
  for (const origen of ['https://juanc101195.github.io', 'http://localhost:5173', 'http://127.0.0.1:5173']) {
    const r = await m({ ...base, origen, metodo: 'OPTIONS', clave: undefined })
    assert.strictEqual(r.status, 204)
    assert.strictEqual(r.headers['Access-Control-Allow-Origin'], origen)
    assert.match(r.headers['Access-Control-Allow-Headers'], /x-clave/)
  }
  const ajeno = await m({ ...base, origen: 'https://malo.example', metodo: 'OPTIONS' })
  assert.strictEqual(ajeno.headers['Access-Control-Allow-Origin'], undefined)
  const sinOrigen = await m({ ...base, origen: undefined })
  assert.strictEqual(sinOrigen.headers['Access-Control-Allow-Origin'], undefined)
  const err = await m({ ...base, clave: 'x' })
  assert.strictEqual(err.headers['Access-Control-Allow-Origin'], 'https://juanc101195.github.io')
})

test('las corridas con usura máxima aparecen por su nombre', async () => {
  const nombres = ['sin_justificaciones', 'con_justificaciones', 'max_sin_justificaciones', 'max_con_justificaciones']
  const { m } = manejador({
    contratos: [filaContrato('A', '2024-01-01')],
    resultados: nombres.map((n) => filaResultado('A', n)),
    periodos: nombres.map((n) => filaPeriodo('A', n, 1)),
  })
  const r = await m(base)
  assert.deepStrictEqual(Object.keys(r.cuerpo.episodios[0].corridas).sort(), [...nombres].sort())
  assert.strictEqual(r.cuerpo.episodios[0].corridas.max_con_justificaciones.periodos.length, 1)
})

test('si la base no tiene usura_ea, consulta sin esa columna y usuraEA queda en null', async () => {
  const llamadas = []
  const m = crearManejador({
    claveEsperada: CLAVE,
    consultar: async (sql) => {
      llamadas.push(sql)
      if (/FROM contrato/.test(sql)) return [filaContrato('A', '2024-01-01')]
      if (/FROM pago_real/.test(sql)) return []
      if (/FROM resultado/.test(sql)) return [filaResultado('A', 'sin_justificaciones'), { ...filaResultado('A', 'con_justificaciones'), real_ea_con_multas: null }]
      if (/usura_ea/.test(sql)) throw Object.assign(new Error('column "usura_ea" does not exist'), { code: '42703' })
      const { usura_ea, ...sinUsura } = filaPeriodo('A', 'sin_justificaciones', 1)
      return [sinUsura]
    },
  })
  const r = await m(base)
  assert.strictEqual(r.status, 200)
  const e = r.cuerpo.episodios[0]
  assert.strictEqual(e.corridas.sin_justificaciones.periodos[0].usuraEA, null)
  assert.strictEqual(e.corridas.con_justificaciones.resumen.realEA, null)
  assert.strictEqual(llamadas.filter((q) => /FROM periodo/.test(q)).length, 2)
})
