// Prueba de integración contra PostgreSQL REAL (contenedor Docker local `pg-p10`, puerto 55433, imagen postgres:16).
// Ejecuta la función real (crearManejador de src/contrato.js) con consultas reales, no simuladas.
//   cd nube && npm run test:pg
// Variables: PG_KEEP=1 deja el contenedor encendido al terminar (por defecto se borra).
// Solo toca un contenedor local desechable; no usa credenciales ni bases remotas.
const test = require('node:test')
const assert = require('node:assert')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { execFileSync } = require('node:child_process')
const { Pool, types } = require('pg')
const { crearManejador } = require('../src/contrato')

types.setTypeParser(1082, (v) => v) // igual que src/baseDatos.js

const CONTENEDOR = 'pg-p10'
const PUERTO = 55433
const CLAVE = 'clave-de-prueba'
const DB_DIR = path.join(__dirname, '..', '..', 'db')
const sqlDe = (f) => fs.readFileSync(path.join(DB_DIR, f), 'utf8')
const docker = (...args) => execFileSync('docker', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
const dormir = (ms) => new Promise((r) => setTimeout(r, ms))

const pools = {}
const pool = (db) => (pools[db] ??= new Pool({ host: '127.0.0.1', port: PUERTO, user: 'postgres', password: 'p10', database: db, max: 4 }))
const consultarDe = (db) => async (sql, params) => (await pool(db).query(sql, params)).rows

async function esperarPostgres() {
  for (let i = 0; i < 90; i++) {
    try {
      await pool('postgres').query('SELECT 1')
      await dormir(1500) // initdb reinicia el servidor una vez
      await pool('postgres').query('SELECT 1')
      return
    } catch {
      await dormir(1000)
    }
  }
  throw new Error('PostgreSQL local no respondió')
}

// ---- datos inventados -------------------------------------------------------------------------------------------
// El contrato 1428 existe en CARTAGENA y en MONTERIA (contratos distintos) y con el MISMO id interno 'K1' en todas las
// corridas. `marca` identifica de qué (sede, corrida) viene cada dato: sim_pagado = marca, pago real = marca*100 miles,
// capital de periodo/proyeccion = marca*10.
const CORRIDAS = ['fija_con_justificaciones', 'fija_sin_justificaciones']
const MARCA = { 'CARTAGENA/legado': 1, 'CARTAGENA/c-nueva': 2, 'MONTERIA/m-vieja': 3, 'MONTERIA/m-nueva': 4 }

async function cargarCorrida(db, sede, run, { migrada, extra = false }) {
  const marca = MARCA[`${sede}/${run}`]
  const sr = migrada ? ', sede, run_id' : ''
  const vr = migrada ? `, '${sede}', '${run}'` : ''
  const contratos = [['K1', '1428']]
  if (extra) contratos.push(['K2', '2000'])
  for (const [id, num] of contratos) {
    await pool(db).query(
      `INSERT INTO contrato (id, contrato, cohorte, estado_real, fecha_inicio, cuotas, cuota_real_miles, inversion, pagos_reales, multas_reales_miles${migrada ? '' : ', sede'}${sr})
       VALUES ($1, $2, '2026-01', 'VIGENTE', '2026-01-05', 52, 100, 5000000, 1, 0${migrada ? '' : `, '${sede}'`}${vr})`,
      [id, num],
    )
    await pool(db).query(`INSERT INTO pago_real (id, fecha, valor_miles, multa_miles${sr}) VALUES ($1, '2026-10-10', $2, 0${vr})`, [id, marca * 100])
    for (const corrida of CORRIDAS) {
      await pool(db).query(
        `INSERT INTO resultado (corrida, id, sim_pagado, sim_saldo_final, sim_pendientes, sim_estado_final${sr}) VALUES ($1, $2, $3, 0, 0, 'vigente'${vr})`,
        [corrida, id, marca],
      )
      await pool(db).query(
        `INSERT INTO periodo (corrida, id, periodo, fecha_vencimiento, pago_capital, pago_interes, pago_mora, pago_cobranza, pago_servicios${sr})
         VALUES ($1, $2, 1, '2026-10-15', $3, 0, 0, 0, 0${vr})`,
        [corrida, id, marca * 10],
      )
      await pool(db).query(
        `INSERT INTO proyeccion (corrida, id, periodo, fecha_vencimiento, pago_capital, pago_interes, pago_mora, pago_cobranza, pago_servicios${sr})
         VALUES ($1, $2, 2, '2026-11-20', $3, 0, 0, 0, 0${vr})`,
        [corrida, id, marca * 10],
      )
    }
  }
  await pool(db).query(`INSERT INTO activas_fin_mes (mes, fecha_foto, contrato${sr}) VALUES ('2026-10', '2026-10-31', '1428'${vr})`)
  await pool(db).query(`INSERT INTO carta_fin_mes (mes, motivo, pagos, valor_miles${sr}) VALUES ('2026-10', 'CARTA X', 1, $1${vr})`, [marca])
  await pool(db).query(`INSERT INTO corte_activos (corte${sr}) VALUES ('2026-11-03'${vr})`)
}

let migrada // manejador sobre la base con 001+002+003 y 2 corridas por sede
let legada // manejador sobre la base solo con 001+002 (sin migrar)
let viejo // manejador del contrato.js anterior a P10 (git), sobre la base sin migrar

test.before(async () => {
  try { docker('rm', '-f', CONTENEDOR) } catch { /* no existía */ }
  docker('run', '-d', '--name', CONTENEDOR, '-e', 'POSTGRES_PASSWORD=p10', '-p', `${PUERTO}:5432`, 'postgres:16')
  await esperarPostgres()
  await pool('postgres').query('CREATE DATABASE migrada')
  await pool('postgres').query('CREATE DATABASE legada')

  // --- base SIN migrar: solo 001 + 002, datos de Cartagena
  await pool('legada').query(sqlDe('001_esquema.sql'))
  await pool('legada').query(sqlDe('002_indices.sql'))
  await cargarCorrida('legada', 'CARTAGENA', 'legado', { migrada: false, extra: true })

  // --- base migrada: 001 + 002, datos históricos de Cartagena, 003 (los marca 'legado') y corridas nuevas
  await pool('migrada').query(sqlDe('001_esquema.sql'))
  await pool('migrada').query(sqlDe('002_indices.sql'))
  await cargarCorrida('migrada', 'CARTAGENA', 'legado', { migrada: false })
  await pool('migrada').query(sqlDe('003_sede.sql'))
  await pool('migrada').query(sqlDe('003_sede.sql')) // idempotente
  const meta = (sede, run, fin, estado = 'terminada') =>
    pool('migrada').query('INSERT INTO corrida_meta (sede, run_id, fecha_corte, inicio, fin, estado) VALUES ($1, $2, $3, $4, $4, $5)', [sede, run, '2026-11-03', fin, estado])
  await cargarCorrida('migrada', 'CARTAGENA', 'c-nueva', { migrada: true })
  await meta('CARTAGENA', 'c-nueva', '2026-11-01T10:00:00Z')
  await cargarCorrida('migrada', 'MONTERIA', 'm-vieja', { migrada: true })
  await meta('MONTERIA', 'm-vieja', '2026-10-01T10:00:00Z')
  await cargarCorrida('migrada', 'MONTERIA', 'm-nueva', { migrada: true, extra: true })
  await meta('MONTERIA', 'm-nueva', '2026-11-01T10:00:00Z')
  // Una corrida posterior pero fallida (con datos distintos) no debe ser la vigente.
  await pool('migrada').query("INSERT INTO corrida_meta (sede, run_id, estado, fin) VALUES ('MONTERIA', 'm-fallida', 'fallida', '2026-11-05T10:00:00Z')")
  const vig = (await consultarDe('migrada')('SELECT sede, run_id FROM vw_corrida_vigente ORDER BY sede'))
  assert.deepStrictEqual(vig.map((v) => `${v.sede}/${v.run_id}`), ['CARTAGENA/c-nueva', 'MONTERIA/m-nueva'])

  const nuevo = (db) => crearManejador({ consultar: consultarDe(db), claveEsperada: CLAVE, ttlMs: 0 })
  migrada = nuevo('migrada')
  legada = nuevo('legada')

  // contrato.js anterior a P10 (el de main antes de este cambio), para comparar la base sin migrar.
  const anterior = execFileSync('git', ['show', 'HEAD:nube/src/contrato.js'], { cwd: path.join(__dirname, '..', '..'), encoding: 'utf8' })
  const archivo = path.join(os.tmpdir(), `contrato-anterior-p10-${process.pid}.js`)
  fs.writeFileSync(archivo, anterior)
  viejo = require(archivo).crearManejador({ consultar: consultarDe('legada'), claveEsperada: CLAVE, ttlMs: 0 })
})

test.after(async () => {
  for (const p of Object.values(pools)) await p.end().catch(() => {})
  if (!process.env.PG_KEEP) { try { docker('rm', '-f', CONTENEDOR) } catch { /* ya no está */ } }
})

const pet = (extra) => ({ metodo: 'GET', origen: 'https://juanc101195.github.io', clave: CLAVE, ip: '2.2.2.2', ...extra })
const marcasDe = (cuerpo) => cuerpo.episodios.map((e) => e.corridas.fija_con_justificaciones.resumen.simPagado)

// ---- base migrada con 2 sedes y 2 corridas por sede ----------------------------------------------------------------
test('numero=1428&sede=MONTERIA: solo Montería y solo la corrida vigente (ni m-vieja ni la fallida ni Cartagena)', async () => {
  const r = await migrada(pet({ numero: '1428', sede: 'MONTERIA' }))
  assert.strictEqual(r.status, 200)
  assert.strictEqual(r.cuerpo.sede, 'MONTERIA')
  assert.strictEqual(r.cuerpo.runId, 'm-nueva')
  assert.strictEqual(r.cuerpo.episodios.length, 1)
  const e = r.cuerpo.episodios[0]
  assert.deepStrictEqual(marcasDe(r.cuerpo), [4])
  assert.strictEqual(e.realPagadoCuotas, 400 * 1000)
  assert.strictEqual(e.pagosReales.length, 1)
  assert.strictEqual(e.pagosReales[0].valorMiles, 400)
  assert.deepStrictEqual(e.corridas.fija_con_justificaciones.periodos.map((p) => p.pagoCapital), [40])
})

test('minúsculas en sede: igual que en mayúsculas', async () => {
  const r = await migrada(pet({ numero: '1428', sede: 'monteria' }))
  assert.deepStrictEqual([r.cuerpo.sede, r.cuerpo.runId, ...marcasDe(r.cuerpo)], ['MONTERIA', 'm-nueva', 4])
})

test('numero=1428 sin ?sede= asume CARTAGENA y su corrida vigente (no la legada ni Montería)', async () => {
  const r = await migrada(pet({ numero: '1428' }))
  assert.strictEqual(r.status, 200)
  assert.strictEqual(r.cuerpo.sede, 'CARTAGENA')
  assert.strictEqual(r.cuerpo.runId, 'c-nueva')
  assert.deepStrictEqual(marcasDe(r.cuerpo), [2])
  const explicita = await migrada(pet({ numero: '1428', sede: 'CARTAGENA' }))
  assert.deepStrictEqual(explicita.cuerpo, r.cuerpo)
})

test('un número que solo existe en otra corrida/sede no se cuela (contrato 2000 solo está en Montería)', async () => {
  assert.strictEqual((await migrada(pet({ numero: '2000' }))).status, 404) // Cartagena no lo tiene
  assert.strictEqual((await migrada(pet({ numero: '2000', sede: 'MONTERIA' }))).status, 200)
  assert.strictEqual((await migrada(pet({ numero: '1428', sede: 'SINCELEJO' }))).status, 404) // sede sin datos
})

test('activos=1: no mezcla sedes ni corridas', async () => {
  const mon = (await migrada(pet({ activos: '1', sede: 'MONTERIA' }))).cuerpo
  assert.strictEqual(mon.sede, 'MONTERIA')
  assert.strictEqual(mon.runId, 'm-nueva')
  assert.strictEqual(mon.activos, 2)
  assert.deepStrictEqual(mon.contratos.map((c) => c.contrato).sort(), ['1428', '2000'])
  for (const c of mon.contratos) {
    assert.deepStrictEqual(Object.keys(c.corridas).sort(), CORRIDAS)
    assert.strictEqual(c.corridas.fija_con_justificaciones.simPagado, 4)
    assert.strictEqual(c.realPagado, 400 * 1000)
  }
  const cart = (await migrada(pet({ activos: '1' }))).cuerpo
  assert.strictEqual(cart.sede, 'CARTAGENA')
  assert.strictEqual(cart.runId, 'c-nueva')
  assert.strictEqual(cart.activos, 1)
  assert.strictEqual(cart.contratos[0].corridas.fija_con_justificaciones.simPagado, 2)
  assert.strictEqual(cart.contratos[0].realPagado, 200 * 1000)
})

test('flujo=1: no mezcla sedes ni corridas (capital, motos, pagos reales y cartas de la corrida vigente)', async () => {
  const mon = (await migrada(pet({ flujo: '1', sede: 'MONTERIA' }))).cuerpo
  assert.strictEqual(mon.sede, 'MONTERIA')
  assert.strictEqual(mon.runId, 'm-nueva')
  assert.strictEqual(mon.corte, '2026-11-03')
  assert.deepStrictEqual(mon.meses.map((m) => [m.mes, m.estado]), [['2026-10', 'cerrado'], ['2026-11', 'en_curso']])
  const [oct, nov] = mon.meses
  // Octubre: 2 contratos con periodo en octubre y foto de octubre; solo '1428' está en activas_fin_mes -> 1 contrato, capital 40.
  assert.strictEqual(oct.corridas.fija_con_justificaciones.capital, 40)
  assert.strictEqual(oct.corridas.fija_con_justificaciones.contratos, 1)
  assert.strictEqual(oct.motosActivas, 1)
  assert.strictEqual(oct.real.pagos, 400 * 1000)
  assert.deepStrictEqual(oct.carta, { pagos: 1, valor: 4000, porMotivo: { 'CARTA X': { pagos: 1, valor: 4000 } } })
  // Noviembre (proyección, 2 contratos de la corrida vigente): 40 + 40.
  assert.strictEqual(nov.corridas.fija_con_justificaciones.capital, 80)
  assert.strictEqual(nov.corridas.fija_con_justificaciones.contratos, 2)
  assert.strictEqual(nov.real.pagos, 0) // el único pago real inventado es del 10-oct: noviembre no tiene pagos reales

  const cart = (await migrada(pet({ flujo: '1' }))).cuerpo
  assert.strictEqual(cart.sede, 'CARTAGENA')
  assert.strictEqual(cart.runId, 'c-nueva')
  assert.strictEqual(cart.meses[0].corridas.fija_con_justificaciones.capital, 20)
  assert.strictEqual(cart.meses[0].real.pagos, 200 * 1000)
  assert.strictEqual(cart.meses[1].corridas.fija_con_justificaciones.capital, 20)
  assert.strictEqual(cart.meses[1].corridas.fija_con_justificaciones.contratos, 1)
})

test('sedes=1 lista ambas sedes con datos', async () => {
  const r = await migrada(pet({ sedes: '1' }))
  assert.deepStrictEqual(r.cuerpo.sedes, ['CARTAGENA', 'MONTERIA'])
  assert.strictEqual(r.cuerpo.defecto, 'CARTAGENA')
})

test('al cambiar la corrida vigente la API la toma (sin caché): Montería pasa a una corrida más nueva', async () => {
  await pool('migrada').query("INSERT INTO corrida_meta (sede, run_id, estado, fin) VALUES ('MONTERIA', 'm-sin-datos', 'terminada', '2026-11-09T10:00:00Z')")
  try {
    const r = await migrada(pet({ numero: '1428', sede: 'MONTERIA' }))
    // La nueva corrida es la vigente pero no tiene datos: no se mezcla con m-nueva.
    assert.strictEqual(r.status, 404)
  } finally {
    await pool('migrada').query("DELETE FROM corrida_meta WHERE run_id = 'm-sin-datos'")
  }
  assert.strictEqual((await migrada(pet({ numero: '1428', sede: 'MONTERIA' }))).cuerpo.runId, 'm-nueva')
})

test('sede sin fila en vw_corrida_vigente (migrada): responde filtrando solo por sede, sin runId', async () => {
  await pool('migrada').query("DELETE FROM corrida_meta WHERE sede = 'MONTERIA'")
  try {
    const r = await migrada(pet({ activos: '1', sede: 'MONTERIA' }))
    assert.strictEqual(r.status, 200)
    assert.strictEqual('runId' in r.cuerpo, false)
    assert.strictEqual(r.cuerpo.sede, 'MONTERIA')
  } finally {
    await pool('migrada').query(
      "INSERT INTO corrida_meta (sede, run_id, estado, fin) VALUES ('MONTERIA', 'm-vieja', 'terminada', '2026-10-01T10:00:00Z'), ('MONTERIA', 'm-nueva', 'terminada', '2026-11-01T10:00:00Z')",
    )
  }
})

// ---- base SIN migrar (solo 001 + 002) ------------------------------------------------------------------------------
test('base sin migrar: numero, activos y flujo funcionan y coinciden con el contrato.js anterior (salvo el campo sede)', async () => {
  for (const modo of [{ numero: '1428' }, { activos: '1' }, { flujo: '1' }]) {
    const nuevo = await legada(pet(modo))
    const antes = await viejo(pet(modo))
    assert.strictEqual(nuevo.status, 200, JSON.stringify(modo))
    assert.strictEqual(antes.status, 200)
    // El contrato.js anterior (main, con P7) ya devuelve `sede`; lo único que P10 agrega es `runId`, y solo en bases migradas.
    assert.strictEqual(nuevo.cuerpo.sede, 'CARTAGENA')
    assert.strictEqual('runId' in nuevo.cuerpo, false)
    assert.deepStrictEqual(nuevo.cuerpo, antes.cuerpo, JSON.stringify(modo))
  }
  const num = (await legada(pet({ numero: '1428' }))).cuerpo
  assert.deepStrictEqual(marcasDe(num), [1])
  assert.strictEqual((await legada(pet({ activos: '1' }))).cuerpo.activos, 2)
  const fl = (await legada(pet({ flujo: '1' }))).cuerpo
  assert.strictEqual(fl.meses[0].corridas.fija_con_justificaciones.capital, 10) // marca 1 × 10; solo '1428' está en activas_fin_mes
})

test('base sin migrar: ?sede=CARTAGENA igual que sin sede; otra sede sin datos; sedes=1 solo CARTAGENA', async () => {
  const sin = await legada(pet({ numero: '1428' }))
  const cart = await legada(pet({ numero: '1428', sede: 'CARTAGENA' }))
  assert.deepStrictEqual(cart.cuerpo, sin.cuerpo)
  assert.strictEqual((await legada(pet({ numero: '1428', sede: 'MONTERIA' }))).status, 404)
  assert.strictEqual((await legada(pet({ activos: '1', sede: 'MONTERIA' }))).cuerpo.activos, 0)
  assert.deepStrictEqual((await legada(pet({ flujo: '1', sede: 'MONTERIA' }))).cuerpo.meses, [])
  assert.deepStrictEqual((await legada(pet({ sedes: '1' }))).cuerpo.sedes, ['CARTAGENA'])
})
