const test = require('node:test')
const assert = require('node:assert')
const { validar } = require('./validar')

const valido = {
  sesion: '123e4567-e89b-12d3-a456-426614174000',
  nombre: 'Diego González',
  cargo: 'Dueño de producto',
  completas: 2,
  total: 25,
  final: false,
  escenarios: '2026-10-16',
  encabezados: '["ID"]',
  filas: '[["PTOPE","Pagos"]]',
}

test('acepta un envío del cuestionario y arma la clave de la fila', () => {
  const { entidad, error } = validar(valido, 1000)
  assert.strictEqual(error, undefined)
  assert.strictEqual(entidad.partitionKey, 'respuestas')
  assert.strictEqual(entidad.rowKey, `1000-${valido.sesion}`)
  assert.strictEqual(entidad.nombre, 'Diego González')
})

test('rechaza sesiones con caracteres raros, avances imposibles y respuestas que no son tabla', () => {
  assert.ok(validar({ ...valido, sesion: '../x' }).error)
  assert.ok(validar({ ...valido, completas: 30 }).error)
  assert.ok(validar({ ...valido, final: 'si' }).error)
  assert.ok(validar({ ...valido, filas: '{"a":1}' }).error)
  assert.ok(validar({ ...valido, filas: 'x'.repeat(60001) }).error)
  assert.ok(validar(null).error)
})
