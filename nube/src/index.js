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
