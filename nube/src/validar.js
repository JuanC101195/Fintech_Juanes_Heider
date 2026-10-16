// Valida lo que manda el cuestionario antes de guardarlo. Devuelve la entidad lista para la
// tabla o un mensaje de error. Sin dependencias de Azure, para probarla con node --test.
const LIMITE_FILAS = 60000
const SESION = /^[A-Za-z0-9-]{8,64}$/

function texto(valor, maximo) {
  return typeof valor === 'string' && valor.length <= maximo
}

function entero(valor) {
  return Number.isInteger(valor) && valor >= 0 && valor <= 1000
}

function validar(datos, ahora = Date.now()) {
  if (!datos || typeof datos !== 'object') return { error: 'Cuerpo vacío' }
  const { sesion, nombre, cargo, completas, total, final, escenarios, encabezados, filas } = datos
  if (!texto(sesion, 64) || !SESION.test(sesion)) return { error: 'Sesión inválida' }
  if (!texto(nombre, 200) || !texto(cargo, 200)) return { error: 'Nombre o cargo inválido' }
  if (!entero(completas) || !entero(total) || completas > total) return { error: 'Avance inválido' }
  if (typeof final !== 'boolean') return { error: 'Campo final inválido' }
  if (!texto(escenarios, 40) || !texto(encabezados, 2000)) return { error: 'Metadatos inválidos' }
  if (!texto(filas, LIMITE_FILAS)) return { error: 'Respuestas demasiado largas' }
  let lista
  try {
    lista = JSON.parse(filas)
  } catch {
    return { error: 'Respuestas inválidas' }
  }
  if (!Array.isArray(lista) || !lista.every(Array.isArray)) return { error: 'Respuestas inválidas' }
  return {
    entidad: {
      partitionKey: 'respuestas',
      rowKey: `${ahora}-${sesion}`,
      sesion,
      nombre,
      cargo,
      completas,
      total,
      final,
      escenarios,
      encabezados,
      filas,
    },
  }
}

module.exports = { validar }
