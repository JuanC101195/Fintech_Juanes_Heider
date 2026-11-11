const test = require('node:test')
const assert = require('node:assert')
const { crearManejador: crearManejadorReal, crearLimitador, variantes } = require('./contrato')

// Los tests históricos simulan una base SIN migrar (sin vw_corrida_vigente: 42P01), que es el comportamiento de antes de P6/P10.
// Los tests de sede / run_id (al final) usan crearManejadorReal con su propia vista.
const sinVista = () => Object.assign(new Error('relation "vw_corrida_vigente" does not exist'), { code: '42P01' })
const crearManejador = (op) =>
  crearManejadorReal({ ...op, consultar: async (sql, params) => (/vw_corrida_vigente/.test(sql) ? Promise.reject(sinVista()) : op.consultar(sql, params)) })

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
  assert.strictEqual(llamadas.filter((q) => /FROM periodo/.test(q)).length, 4)
})

test('devuelve la lógica de cada periodo y, si la base aún no tiene esa columna, consulta sin ella y deja logica en null', async () => {
  const llamadas = []
  let sinColumna = false
  const m = crearManejador({
    claveEsperada: CLAVE,
    consultar: async (sql) => {
      llamadas.push(sql)
      if (/FROM contrato/.test(sql)) return [filaContrato('A', '2024-01-01')]
      if (/FROM pago_real/.test(sql)) return []
      if (/FROM resultado/.test(sql)) return [filaResultado('A', 'sin_justificaciones')]
      if (/logica/.test(sql)) {
        if (sinColumna) throw Object.assign(new Error('column "logica" does not exist'), { code: '42703' })
        return [{ ...filaPeriodo('A', 'sin_justificaciones', 1), logica: 'Pago a tiempo: amortización normal.' }]
      }
      return [filaPeriodo('A', 'sin_justificaciones', 1)]
    },
  })
  const conLogica = await m(base)
  assert.strictEqual(conLogica.cuerpo.episodios[0].corridas.sin_justificaciones.periodos[0].logica, 'Pago a tiempo: amortización normal.')
  sinColumna = true
  llamadas.length = 0
  const sinLogica = await m(base)
  assert.strictEqual(sinLogica.status, 200)
  assert.strictEqual(sinLogica.cuerpo.episodios[0].corridas.sin_justificaciones.periodos[0].logica, null)
  assert.strictEqual(llamadas.filter((q) => /FROM periodo/.test(q)).length, 3)
})

test('devuelve pago por servicio y saldo en mora y, si la base aún no tiene esas columnas, consulta sin ellas y los deja en null', async () => {
  const llamadas = []
  let sinColumnas = false
  const nuevas = { pago_fondo: '70', pago_seguro: '10', pago_asistencia: '20', saldo_en_mora: '1234.5', cxc_mora: '12' }
  const m = crearManejador({
    claveEsperada: CLAVE,
    consultar: async (sql) => {
      llamadas.push(sql)
      if (/FROM contrato/.test(sql)) return [filaContrato('A', '2024-01-01')]
      if (/FROM pago_real/.test(sql)) return []
      if (/FROM resultado/.test(sql)) return [filaResultado('A', 'sin_justificaciones')]
      if (/saldo_en_mora/.test(sql)) {
        if (sinColumnas) throw Object.assign(new Error('column "saldo_en_mora" does not exist'), { code: '42703' })
        return [{ ...filaPeriodo('A', 'sin_justificaciones', 1), ...nuevas }]
      }
      return [filaPeriodo('A', 'sin_justificaciones', 1)]
    },
  })
  const con = (await m(base)).cuerpo.episodios[0].corridas.sin_justificaciones.periodos[0]
  assert.deepStrictEqual([con.pagoFondo, con.pagoSeguro, con.pagoAsistencia, con.saldoEnMora, con.cxcMora], [70, 10, 20, 1234.5, 12])
  sinColumnas = true
  llamadas.length = 0
  const r = await m(base)
  assert.strictEqual(r.status, 200)
  const sin = r.cuerpo.episodios[0].corridas.sin_justificaciones.periodos[0]
  assert.deepStrictEqual([sin.pagoFondo, sin.pagoSeguro, sin.pagoAsistencia, sin.saldoEnMora, sin.cxcMora], [null, null, null, null, null])
  assert.strictEqual(llamadas.filter((q) => /FROM periodo/.test(q)).length, 2)
})

test('activos=1 lista los contratos vigentes con sus corridas de tasa fija y exige la clave', async () => {
  const llamadas = []
  const consultar = async (sql, params) => {
    llamadas.push({ sql, params })
    return [
      { id: 'S|7|2026-01-01', contrato: '7', fecha_inicio: '2026-01-01', cuotas: '52', cuota_real_miles: '90', inversion: '4000000', inversion_imputada: false,
        pagos_reales: 5, dias_justificados: 0, multas_reales_miles: '0', real_pagado: '450000', corrida: 'fija_con_justificaciones', sim_pagado: '500000',
        sim_saldo_final: '3000000', sim_pendientes: '10', sim_marca_recuperacion: false, sim_estado_final: 'ACTIVO', sim_periodos: 5, periodo_corte: 5 },
      { id: 'S|7|2026-01-01', contrato: '7', fecha_inicio: '2026-01-01', corrida: 'fija_sin_justificaciones', sim_pagado: '480000', sim_saldo_final: '3100000' },
    ]
  }
  const m = crearManejador({ consultar, claveEsperada: CLAVE })
  const sinClave = await m({ ...base, clave: 'mala', activos: '1', numero: undefined })
  assert.strictEqual(sinClave.status, 401)
  const r = await m({ ...base, activos: '1', numero: undefined })
  assert.strictEqual(r.status, 200)
  assert.strictEqual(r.cuerpo.activos, 1)
  const c = r.cuerpo.contratos[0]
  assert.strictEqual(c.contrato, '7')
  assert.strictEqual(c.realPagado, 450000)
  assert.deepStrictEqual(Object.keys(c.corridas).sort(), ['fija_con_justificaciones', 'fija_sin_justificaciones'])
  assert.strictEqual(c.corridas.fija_con_justificaciones.simPagado, 500000)
  assert.match(llamadas[0].sql, /estado_real = 'VIGENTE'/)
  assert.deepStrictEqual(llamadas[0].params, [['fija_con_justificaciones', 'fija_sin_justificaciones']])
})

test('flujo=1: meses cerrados con la flota activa de fin de mes, mes en curso y proyectados con la cartera de hoy', async () => {
  const consultar = async (sql) => {
    if (/FROM corte_activos/.test(sql)) return [{ corte: '2026-11-03' }]
    if (/FROM periodo p/.test(sql)) {
      return [{ mes: '2026-10', corrida: 'fija_sin_justificaciones', capital: '100', interes: '300', mora: '5', cobranza: '0', servicios: '50', total: '455', contratos: '10' }]
    }
    if (/FROM proyeccion p/.test(sql)) {
      return [
        { mes: '2026-11', corrida: 'fija_sin_justificaciones', capital: '120', interes: '280', mora: '0', cobranza: '0', servicios: '50', total: '450', contratos: '9' },
        { mes: '2026-12', corrida: 'fija_sin_justificaciones', capital: '130', interes: '270', mora: '0', cobranza: '0', servicios: '50', total: '450', contratos: '9' },
      ]
    }
    if (/FROM activas_fin_mes WHERE true GROUP BY mes/.test(sql)) return [{ mes: '2026-10', motos: '12' }]
    if (/JOIN activas_fin_mes a ON a.contrato = c.contrato AND a.mes = to_char/.test(sql)) return [{ mes: '2026-10', pagos: '480' }]
    if (/estado_real = 'VIGENTE'/.test(sql)) return [{ pagos: '90' }]
    if (/FROM carta_fin_mes/.test(sql)) return [{ mes: '2026-10', motivo: 'CARTA ADMINISTRACION', pagos: '3', valor_miles: '400' }, { mes: '2026-10', motivo: 'CARTA REGRESO DIARIO', pagos: '5', valor_miles: '500' }]
    return []
  }
  const m = crearManejador({ consultar, claveEsperada: CLAVE })
  assert.strictEqual((await m({ ...base, clave: 'mala', flujo: '1', numero: undefined })).status, 401)
  const r = await m({ ...base, flujo: '1', numero: undefined })
  assert.strictEqual(r.status, 200)
  assert.strictEqual(r.cuerpo.corte, '2026-11-03')
  assert.deepStrictEqual(r.cuerpo.meses.map((x) => [x.mes, x.estado]), [['2026-10', 'cerrado'], ['2026-11', 'en_curso'], ['2026-12', 'proyectado']])
  assert.strictEqual(r.cuerpo.meses[0].motosActivas, 12)
  assert.deepStrictEqual(r.cuerpo.meses[0].real, { pagos: 480 })
  assert.deepStrictEqual(r.cuerpo.meses[1].real, { pagos: 90 })
  assert.strictEqual(r.cuerpo.meses[2].real, null)
  assert.strictEqual(r.cuerpo.meses[0].carta.valor, 900000)
  assert.strictEqual(r.cuerpo.meses[0].carta.pagos, 8)
  assert.strictEqual(r.cuerpo.meses[0].carta.porMotivo['CARTA ADMINISTRACION'].valor, 400000)
  assert.strictEqual(r.cuerpo.meses[1].carta, null)
  assert.strictEqual(r.cuerpo.meses[0].corridas.fija_sin_justificaciones.interes, 300)
})

// ---- caché, ETag y paginación de activos / flujo ----
const filasActivos = (n) =>
  Array.from({ length: n }, (_, i) => ({
    id: `S|${i}|2026-01-01`, contrato: String(i), fecha_inicio: '2026-01-01', cuotas: '52', real_pagado: '1000', corrida: 'fija_sin_justificaciones', sim_pagado: '1',
  }))
function contado(filas) {
  const o = { n: 0 }
  o.consultar = async () => {
    o.n += 1
    return filas
  }
  return o
}

test('activos=1: dos llamadas seguidas ejecutan una sola consulta y la caché expira tras el TTL', async () => {
  let t = 1000
  const c = contado(filasActivos(2))
  const m = crearManejador({ consultar: c.consultar, claveEsperada: CLAVE, ttlMs: 600000, ahora: () => t })
  const pet = { ...base, activos: '1', numero: undefined }
  await m(pet)
  await m(pet)
  assert.strictEqual(c.n, 1)
  t += 599999
  await m(pet)
  assert.strictEqual(c.n, 1)
  t += 2
  await m(pet)
  assert.strictEqual(c.n, 2)
})

test('flujo=1: la caché evita repetir las consultas y TTL 0 la desactiva', async () => {
  let n = 0
  const consultar = async () => { n += 1; return [] }
  const m = crearManejador({ consultar, claveEsperada: CLAVE, ttlMs: 1000, ahora: () => 0 })
  await m({ ...base, flujo: '1', numero: undefined })
  const tras1 = n
  await m({ ...base, flujo: '1', numero: undefined })
  assert.strictEqual(n, tras1)
  const m0 = crearManejador({ consultar, claveEsperada: CLAVE, ttlMs: 0 })
  await m0({ ...base, flujo: '1', numero: undefined })
  await m0({ ...base, flujo: '1', numero: undefined })
  assert.strictEqual(n, tras1 + 2 * tras1)
})

test('una consulta fallida no se queda en caché', async () => {
  let n = 0
  const consultar = async () => { n += 1; if (n === 1) throw new Error('x'); return filasActivos(1) }
  const m = crearManejador({ consultar, claveEsperada: CLAVE, ttlMs: 1000, ahora: () => 0 })
  const pet = { ...base, activos: '1', numero: undefined }
  assert.strictEqual((await m(pet)).status, 500)
  assert.strictEqual((await m(pet)).status, 200)
})

test('activos y flujo llevan Cache-Control privado y ETag, y responden 304 si If-None-Match coincide (tras validar la clave)', async () => {
  const m = crearManejador({ consultar: contado(filasActivos(1)).consultar, claveEsperada: CLAVE, ttlMs: 1000, ahora: () => 0 })
  const pet = { ...base, activos: '1', numero: undefined }
  const r = await m(pet)
  assert.strictEqual(r.headers['Cache-Control'], 'private, max-age=300')
  assert.match(r.headers.ETag, /^"[0-9a-f]{32}"$/)
  const r304 = await m({ ...pet, ifNoneMatch: r.headers.ETag })
  assert.strictEqual(r304.status, 304)
  assert.strictEqual(r304.cuerpo, undefined)
  assert.strictEqual((await m({ ...pet, ifNoneMatch: '"otro"' })).status, 200)
  assert.strictEqual((await m({ ...pet, clave: 'mala', ifNoneMatch: r.headers.ETag })).status, 401)
})

test('la ruta ?numero= no cambia: no-store y sin ETag', async () => {
  const { m } = manejador({ contratos: [filaContrato('A', '2024-01-01')] })
  const r = await m({ ...base, ifNoneMatch: '*' })
  assert.strictEqual(r.status, 200)
  assert.strictEqual(r.headers['Cache-Control'], 'no-store')
  assert.strictEqual(r.headers.ETag, undefined)
})

test('activos=1 sin pagina/tamano devuelve todo (formato histórico); con ellos pagina con metadatos', async () => {
  const m = crearManejador({ consultar: contado(filasActivos(5)).consultar, claveEsperada: CLAVE, ttlMs: 1000, ahora: () => 0 })
  const pet = { ...base, activos: '1', numero: undefined }
  const todo = await m(pet)
  assert.deepStrictEqual(Object.keys(todo.cuerpo), ['sede', 'activos', 'contratos'])
  assert.strictEqual(todo.cuerpo.contratos.length, 5)
  const p2 = await m({ ...pet, pagina: '2', tamano: '2' })
  assert.deepStrictEqual([p2.cuerpo.total, p2.cuerpo.pagina, p2.cuerpo.tamano, p2.cuerpo.paginas], [5, 2, 2, 3])
  assert.deepStrictEqual(p2.cuerpo.contratos.map((c) => c.contrato), ['2', '3'])
  assert.strictEqual((await m({ ...pet, pagina: '9', tamano: '2' })).cuerpo.contratos.length, 0)
  assert.strictEqual((await m({ ...pet, pagina: '1' })).cuerpo.tamano, 200)
  for (const mala of [{ pagina: '0' }, { pagina: 'x' }, { tamano: '1001' }, { tamano: '0' }, { tamano: '-1' }]) {
    const r = await m({ ...pet, ...mala })
    assert.deepStrictEqual([r.status, r.cuerpo.error], [400, 'paginacion_invalida'])
  }
})

test('listarActivos usa un LEFT JOIN agregado en lugar de una subconsulta por fila, con SQL parametrizado', async () => {
  const c = { sqls: [] }
  const consultar = async (sql, params) => { c.sqls.push({ sql, params }); return [] }
  const m = crearManejador({ consultar, claveEsperada: CLAVE, ttlMs: 0 })
  await m({ ...base, activos: '1', numero: undefined })
  const { sql, params } = c.sqls[0]
  assert.match(sql, /LEFT JOIN \(SELECT id, SUM\(valor_miles\) \* 1000 AS real_pagado FROM pago_real GROUP BY id\) p ON p\.id = c\.id/)
  assert.doesNotMatch(sql, /\(SELECT COALESCE\(SUM/)
  assert.match(sql, /ANY\(\$1::text\[\]\)/)
  assert.deepStrictEqual(params, [['fija_con_justificaciones', 'fija_sin_justificaciones']])
})

// ---- sede y run_id: la llave es (sede, run_id, id); el número de contrato se repite entre sedes ----
const sinColumna = () => Object.assign(new Error('column "sede" does not exist'), { code: '42703' })

// Base migrada simulada: la vista devuelve `vigentes[sede]`; el resto de consultas se registra en `sqls`.
function migrada(vigentes, extra = () => []) {
  const sqls = []
  const consultar = async (sql, params) => {
    if (/vw_corrida_vigente/.test(sql)) return vigentes[params[0]] ? [{ run_id: vigentes[params[0]] }] : []
    sqls.push({ sql, params })
    return extra(sql, params)
  }
  return { sqls, consultar }
}

test('sede inválida responde 400 en los tres modos y en sedes=1, sin tocar la base', async () => {
  const { m, f } = manejador()
  for (const sede of ['', 'BOGOTA', 'x;DROP', ' ']) {
    for (const modo of [{}, { activos: '1', numero: undefined }, { flujo: '1', numero: undefined }, { sedes: '1', numero: undefined }]) {
      const r = await m({ ...base, ...modo, sede })
      assert.strictEqual(r.status, 400, `${sede} ${JSON.stringify(modo)}`)
      assert.deepStrictEqual(r.cuerpo, { error: 'sede_invalida' })
    }
  }
  assert.strictEqual(f.llamadas.length, 0)
})

test('numero con sede y run_id vigente: resuelve el run_id de la sede y filtra las 4 consultas por (sede, run_id)', async () => {
  const b = migrada({ MONTERIA: 'r2' }, (sql) => (/FROM contrato/.test(sql) ? [filaContrato('A', '2024-01-01')] : []))
  const m = crearManejadorReal({ consultar: b.consultar, claveEsperada: CLAVE, ttlMs: 0 })
  const r = await m({ ...base, sede: 'monteria' })
  assert.strictEqual(r.status, 200)
  assert.strictEqual(r.cuerpo.sede, 'MONTERIA')
  assert.strictEqual(r.cuerpo.runId, 'r2')
  assert.strictEqual(b.sqls.length, 4)
  for (const l of b.sqls) {
    assert.match(l.sql, /sede = \$2 AND run_id = \$3/)
    assert.deepStrictEqual(l.params.slice(-2), ['MONTERIA', 'r2'])
  }
  assert.deepStrictEqual(b.sqls[0].params, [['1001'], 'MONTERIA', 'r2'])
})

test('sin ?sede= se asume CARTAGENA: filtra por ella y su run_id, y la respuesta trae sede', async () => {
  const b = migrada({ CARTAGENA: 'legado', MONTERIA: 'r2' }, (sql) => (/FROM contrato/.test(sql) ? [filaContrato('A', '2024-01-01')] : []))
  const m = crearManejadorReal({ consultar: b.consultar, claveEsperada: CLAVE, ttlMs: 0 })
  const r = await m(base)
  assert.strictEqual(r.cuerpo.sede, 'CARTAGENA')
  assert.strictEqual(r.cuerpo.runId, 'legado')
  assert.deepStrictEqual(b.sqls[0].params, [['1001'], 'CARTAGENA', 'legado'])
})

test('vista vacía para la sede (migrada, sin corrida terminada): filtra solo por sede y no inventa runId', async () => {
  const b = migrada({}, (sql) => (/FROM contrato/.test(sql) ? [filaContrato('A', '2024-01-01')] : []))
  const m = crearManejadorReal({ consultar: b.consultar, claveEsperada: CLAVE, ttlMs: 0 })
  const r = await m({ ...base, sede: 'SINCELEJO' })
  assert.strictEqual(r.status, 200)
  assert.strictEqual('runId' in r.cuerpo, false)
  for (const l of b.sqls) {
    assert.match(l.sql, /sede = \$2/)
    assert.doesNotMatch(l.sql, /run_id/)
  }
})

test('activos=1: filtra c, r y pagos por (sede, run_id), cruza resultado por las tres columnas y la caché lleva sede y run_id', async () => {
  const b = migrada({ SINCELEJO: 'r1', BICIMOTOS: 'r9', CARTAGENA: 'legado' })
  let vigentes = 0
  const consultar = async (sql, params) => { if (/vw_corrida_vigente/.test(sql)) vigentes += 1; return b.consultar(sql, params) }
  const m = crearManejadorReal({ consultar, claveEsperada: CLAVE, ttlMs: 60000, ahora: () => 0 })
  const act = { ...base, activos: '1', numero: undefined }
  const a = await m({ ...act, sede: 'SINCELEJO' })
  assert.strictEqual(a.cuerpo.sede, 'SINCELEJO')
  assert.strictEqual(a.cuerpo.runId, 'r1')
  const q = b.sqls[0]
  assert.match(q.sql, /JOIN resultado r ON r\.id = c\.id AND r\.sede = c\.sede AND r\.run_id = c\.run_id/)
  assert.match(q.sql, /FROM pago_real WHERE sede = \$2 AND run_id = \$3 GROUP BY id/)
  assert.match(q.sql, /c\.sede = \$2 AND c\.run_id = \$3/)
  assert.deepStrictEqual(q.params, [['fija_con_justificaciones', 'fija_sin_justificaciones'], 'SINCELEJO', 'r1'])
  await m({ ...act, sede: 'SINCELEJO' })
  assert.strictEqual(b.sqls.length, 1, 'misma sede y run: caché')
  await m({ ...act, sede: 'BICIMOTOS' })
  assert.strictEqual(b.sqls.length, 2, 'otra sede: otra consulta')
  const sin = await m(act)
  assert.strictEqual(b.sqls.length, 3, 'sin sede = CARTAGENA: llave distinta')
  assert.strictEqual(sin.cuerpo.sede, 'CARTAGENA')
  assert.strictEqual(vigentes, 3, 'el run vigente también se cachea')
})

test('flujo=1: todas las consultas filtran por sede y run_id y los cruces usan (sede, run_id) y activas_fin_mes por contrato', async () => {
  const b = migrada({ MONTERIA: 'r2' }, (sql) => (/FROM corte_activos/.test(sql) ? [{ corte: '2026-11-03' }] : []))
  const m = crearManejadorReal({ consultar: b.consultar, claveEsperada: CLAVE, ttlMs: 0 })
  const r = await m({ ...base, flujo: '1', numero: undefined, sede: 'MONTERIA' })
  assert.strictEqual(r.status, 200)
  assert.strictEqual(r.cuerpo.sede, 'MONTERIA')
  assert.strictEqual(r.cuerpo.runId, 'r2')
  assert.strictEqual(b.sqls.length, 7)
  for (const q of b.sqls) assert.deepStrictEqual(q.params.slice(-2), ['MONTERIA', 'r2'], q.sql)
  const cerrados = b.sqls.find((q) => /FROM periodo p/.test(q.sql)).sql
  assert.match(cerrados, /c\.sede = p\.sede AND c\.run_id = p\.run_id/)
  assert.match(cerrados, /a\.sede = c\.sede AND a\.run_id = c\.run_id/)
  assert.match(cerrados, /p\.sede = \$3 AND p\.run_id = \$4/)
  assert.match(b.sqls.find((q) => /FROM proyeccion p/.test(q.sql)).sql, /p\.sede = \$3 AND p\.run_id = \$4/)
  assert.match(b.sqls.find((q) => /FROM carta_fin_mes/.test(q.sql)).sql, /sede = \$1 AND run_id = \$2/)
  assert.match(b.sqls.find((q) => /FROM corte_activos/.test(q.sql)).sql, /sede = \$1 AND run_id = \$2/)
  assert.match(b.sqls.find((q) => /FROM activas_fin_mes/.test(q.sql) && !/periodo|pago_real/.test(q.sql)).sql, /sede = \$1 AND run_id = \$2/)
})

test('base sin migrar (vista inexistente 42P01 o columna 42703): sin filtro, CARTAGENA con datos y otra sede sin datos', async () => {
  for (const error of [sinVista, sinColumna]) {
    const consultar = async (sql) => {
      if (/vw_corrida_vigente/.test(sql)) throw error()
      if (/sede = \$|\.sede/.test(sql)) throw sinColumna()
      if (/FROM contrato c JOIN resultado/.test(sql)) return [{ id: 'A', contrato: '1', fecha_inicio: '2026-01-01', corrida: 'fija_sin_justificaciones' }]
      if (/FROM contrato/.test(sql)) return [filaContrato('A', '2024-01-01')]
      if (/FROM corte_activos/.test(sql)) return [{ corte: '2026-11-03' }]
      return []
    }
    for (const [modo, vacio] of [[{}, (r) => r.status === 404], [{ activos: '1', numero: undefined }, (r) => r.cuerpo.activos === 0], [{ flujo: '1', numero: undefined }, (r) => r.cuerpo.meses.length === 0]]) {
      const m = crearManejadorReal({ consultar, claveEsperada: CLAVE, ttlMs: 0 })
      for (const pedida of [{ sede: 'CARTAGENA' }, {}]) {
        const cart = await m({ ...base, ...modo, ...pedida })
        assert.strictEqual(cart.status, 200, JSON.stringify(modo))
        assert.strictEqual(cart.cuerpo.sede, 'CARTAGENA')
        assert.strictEqual('runId' in cart.cuerpo, false)
        assert.ok(!vacio(cart) || modo.flujo, JSON.stringify(modo))
      }
      assert.ok(vacio(await m({ ...base, ...modo, sede: 'MONTERIA' })), `otra sede vacía ${JSON.stringify(modo)}`)
    }
  }
})

test('si la consulta del run vigente falla por otra causa, responde 500 sin filtrar el error', async () => {
  const m = crearManejadorReal({ consultar: async () => { throw new Error('boom') }, claveEsperada: CLAVE, ttlMs: 0 })
  for (const modo of [{}, { activos: '1', numero: undefined }, { flujo: '1', numero: undefined }]) {
    const r = await m({ ...base, ...modo })
    assert.deepStrictEqual([r.status, r.cuerpo], [500, { error: 'interno' }])
  }
})

test('sedes=1 lista las sedes con datos y, sin columna sede, solo CARTAGENA', async () => {
  const m1 = crearManejadorReal({ consultar: async () => [{ sede: 'MONTERIA' }, { sede: 'CARTAGENA' }], claveEsperada: CLAVE, ttlMs: 0 })
  const r = await m1({ ...base, sedes: '1', numero: undefined })
  assert.deepStrictEqual(r.cuerpo.sedes, ['CARTAGENA', 'MONTERIA'])
  assert.deepStrictEqual(r.cuerpo.todas, ['CARTAGENA', 'MONTERIA', 'SINCELEJO', 'BICIMOTOS'])
  const m2 = crearManejadorReal({ consultar: async () => { throw sinColumna() }, claveEsperada: CLAVE, ttlMs: 0 })
  assert.deepStrictEqual((await m2({ ...base, sedes: '1', numero: undefined })).cuerpo.sedes, ['CARTAGENA'])
})
