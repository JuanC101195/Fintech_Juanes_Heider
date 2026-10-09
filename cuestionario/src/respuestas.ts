import { useEffect, useState } from 'react'
import { PREGUNTAS } from './preguntas'
import type { Pregunta, Respondente, Respuesta } from './tipos'

// El avance se guarda en el navegador para poder responder en varias sesiones.
const CLAVE = 'cuestionario-reglas.v1'

export interface Estado {
  respondente: Respondente
  respuestas: Record<string, Respuesta>
}

const VACIO: Estado = { respondente: { nombre: '', cargo: '' }, respuestas: {} }

function leer(): Estado {
  try {
    const guardado = localStorage.getItem(CLAVE)
    return guardado ? { ...VACIO, ...JSON.parse(guardado) } : VACIO
  } catch {
    return VACIO
  }
}

export function useEstado() {
  const [estado, setEstado] = useState<Estado>(leer)
  useEffect(() => {
    try {
      localStorage.setItem(CLAVE, JSON.stringify(estado))
    } catch {
      // Sin almacenamiento (modo privado): se responde en una sola sesión y se exporta al final.
    }
  }, [estado])
  return [estado, setEstado] as const
}

/** Una pregunta está completa si tiene opción y, cuando la opción lo pide, valor o texto. */
export function estaCompleta(p: Pregunta, r: Respuesta | undefined): boolean {
  if (!r?.codigo) return false
  const opcion = p.opciones.find((o) => o.codigo === r.codigo)
  if (!opcion) return false
  if (opcion.pideValor && (r.valor === undefined || Number.isNaN(r.valor))) return false
  if (opcion.pideTexto && !r.texto?.trim()) return false
  return true
}

export function avance(respuestas: Record<string, Respuesta>) {
  const completas = PREGUNTAS.filter((p) => estaCompleta(p, respuestas[p.id])).length
  return { completas, total: PREGUNTAS.length }
}

/** Ajustes al motor que salen de una respuesta (claves de ReglasNegocio). */
export function ajustesDe(p: Pregunta, r: Respuesta | undefined): Record<string, string> {
  if (!r?.codigo) return {}
  const opcion = p.opciones.find((o) => o.codigo === r.codigo)
  if (!opcion) return {}
  const ajustes = { ...(opcion.ajustes ?? {}) }
  if (opcion.pideValor && p.valor?.ajuste && r.valor !== undefined) ajustes[p.valor.ajuste] = String(r.valor)
  return ajustes
}
