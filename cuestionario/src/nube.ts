import { FECHA_ESCENARIOS } from './escenarios'
import { ENCABEZADOS, filasRespuestas } from './exportar'
import { avance, type Estado } from './respuestas'

// Las respuestas se guardan solas mientras se responde: la página las manda a una Azure Function
// (nube/) que las escribe en una tabla de Azure Storage. La página no lleva ninguna clave.
// Cada guardado es una fila nueva; herramientas/importar_respuestas.py --nube lee la más reciente.
const FUNCION = 'https://fn-motor-cartera-respuestas.azurewebsites.net/api/respuestas'
const CLAVE_SESION = 'cuestionario-reglas.sesion'

let sesionActual: string | undefined

/** Identifica el navegador de quien responde, para agrupar sus guardados. */
function sesion(): string {
  if (sesionActual) return sesionActual
  try {
    sesionActual = localStorage.getItem(CLAVE_SESION) ?? crypto.randomUUID()
    localStorage.setItem(CLAVE_SESION, sesionActual)
  } catch {
    sesionActual = crypto.randomUUID()
  }
  return sesionActual
}

/** Lo que se manda a la función; ella le pone la fecha y la clave de la fila. */
export function envioDe(estado: Estado, final: boolean) {
  const { completas, total } = avance(estado.respuestas)
  return {
    sesion: sesion(),
    nombre: estado.respondente.nombre,
    cargo: estado.respondente.cargo,
    completas,
    total,
    final,
    escenarios: FECHA_ESCENARIOS,
    encabezados: JSON.stringify(ENCABEZADOS),
    filas: JSON.stringify(filasRespuestas(estado)),
  }
}

/** Guarda una foto de las respuestas. Con final en verdadero, quien responde dio por terminado. */
export async function guardarEnLaNube(estado: Estado, final = false): Promise<void> {
  const respuesta = await fetch(FUNCION, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(envioDe(estado, final)),
  })
  if (!respuesta.ok) throw new Error(`No se pudieron guardar las respuestas (${respuesta.status})`)
}
