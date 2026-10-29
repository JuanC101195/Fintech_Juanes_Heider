const { app } = require('@azure/functions')
const { TableClient } = require('@azure/data-tables')
const { validar } = require('./validar')

// La conexión sale de la configuración de la Function App (AzureWebJobsStorage), no del código.
let tabla
function cliente() {
  tabla ??= TableClient.fromConnectionString(process.env.AzureWebJobsStorage, 'respuestas')
  return tabla
}

app.http('respuestas', {
  methods: ['POST'],
  authLevel: 'anonymous',
  handler: async (solicitud, contexto) => {
    let datos
    try {
      datos = await solicitud.json()
    } catch {
      return { status: 400, body: 'JSON inválido' }
    }
    const { entidad, error } = validar(datos)
    if (error) return { status: 400, body: error }
    await cliente().createEntity(entidad)
    contexto.log(`Respuestas guardadas: ${entidad.rowKey} (${entidad.completas}/${entidad.total}, final=${entidad.final})`)
    return { status: 204 }
  },
})

// ---- Consulta de contratos del backtesting (GET /api/contrato?numero=1001) ----
// Lee la base PostgreSQL (solo SELECT). La lógica está en contrato.js; aquí solo se adapta la petición de Azure.
const { crearManejador } = require('./contrato')
let manejador
function obtenerManejador() {
  manejador ??= crearManejador({
    consultar: (sql, parametros) => require('./baseDatos').consultar(sql, parametros),
    claveEsperada: process.env.CLAVE_CONSULTA,
    registrar: (t) => console.error(t),
  })
  return manejador
}

app.http('contrato', {
  methods: ['GET', 'OPTIONS'],
  authLevel: 'anonymous',
  route: 'contrato',
  handler: async (solicitud) => {
    const reenviada = solicitud.headers.get('x-forwarded-for') || ''
    const ip = reenviada.split(',')[0].trim().replace(/:\d+$/, '')
    const r = await obtenerManejador()({
      metodo: solicitud.method,
      origen: solicitud.headers.get('origin') || undefined,
      clave: solicitud.headers.get('x-clave') || undefined,
      ip,
      numero: solicitud.query.get('numero') ?? undefined,
    })
    return {
      status: r.status,
      headers: { ...r.headers, ...(r.cuerpo ? { 'Content-Type': 'application/json; charset=utf-8' } : {}) },
      body: r.cuerpo ? JSON.stringify(r.cuerpo) : undefined,
    }
  },
})
