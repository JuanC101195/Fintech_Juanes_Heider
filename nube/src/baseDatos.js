const { Pool, types } = require('pg')

// Las fechas (date) se leen como texto 'aaaa-mm-dd' para evitar corrimientos por zona horaria.
types.setTypeParser(1082, (v) => v)

let pool
function obtenerPool() {
  if (!pool) {
    pool = new Pool({
      host: process.env.PG_HOST,
      database: process.env.PG_DB,
      user: process.env.PG_USER,
      password: process.env.PG_PASSWORD,
      port: 5432,
      ssl: { rejectUnauthorized: false },
      min: 1,
      max: 3,
      connectionTimeoutMillis: 8000,
      idleTimeoutMillis: 30000,
      statement_timeout: 15000,
    })
    pool.on('error', () => {})
  }
  return pool
}

// Solo SELECT parametrizados: el usuario de la base debe ser de solo lectura (docs/despliegue.md).
async function consultar(sql, parametros) {
  const { rows } = await obtenerPool().query(sql, parametros)
  return rows
}

module.exports = { consultar }
