import type { Catalogo, CasoDetalle, CasoResumen, Credito, Ejecucion, ErrorApi, Instruccion, Perfil, PeticionEjecucion, Reglas, Simulacion } from '../tipos'

/** Respuesta de error del API (HTTP 400/404) con su código de negocio, por ejemplo ABONO_MAYOR_QUE_SALDO. */
export class ErrorDeApi extends Error {
  readonly codigo: string
  readonly detalle?: Record<string, unknown>
  readonly estado: number
  constructor(estado: number, cuerpo: ErrorApi) {
    super(cuerpo.mensaje)
    this.name = 'ErrorDeApi'
    this.estado = estado
    this.codigo = cuerpo.codigo
    this.detalle = cuerpo.detalle
  }
}

/** No hubo respuesta del servidor (caído, CORS, sin red) o llegó algo que no es del API. */
export class ErrorDeRed extends Error {
  constructor(mensaje: string, readonly causa?: unknown) {
    super(mensaje)
    this.name = 'ErrorDeRed'
  }
}

export type Fetch = (entrada: string, init?: RequestInit) => Promise<Response>

/** Contrato que cumplen el cliente real y el modo demostración. */
export interface FuenteDatos {
  readonly modo: 'api' | 'demo'
  catalogo(): Promise<Catalogo>
  reglas(): Promise<Reglas>
  simular(credito: Credito): Promise<Simulacion>
  ejecutar(peticion: PeticionEjecucion): Promise<Ejecucion>
  casos(): Promise<CasoResumen[]>
  caso(numero: number, perfil: Perfil): Promise<CasoDetalle>
}

export function crearCliente(urlBase: string, fetchImpl: Fetch = (e, i) => fetch(e, i)): FuenteDatos {
  const base = urlBase.replace(/\/+$/, '') + '/api/v1'

  async function pedir<T>(ruta: string, init?: RequestInit): Promise<T> {
    let respuesta: Response
    try {
      respuesta = await fetchImpl(base + ruta, {
        ...init,
        headers: { Accept: 'application/json', ...(init?.body ? { 'Content-Type': 'application/json' } : {}), ...init?.headers },
      })
    } catch (e) {
      throw new ErrorDeRed('No se pudo contactar al API', e)
    }
    let cuerpo: unknown
    try {
      cuerpo = await respuesta.json()
    } catch (e) {
      throw new ErrorDeRed(`Respuesta no válida del API (HTTP ${respuesta.status})`, e)
    }
    if (!respuesta.ok) {
      const c = cuerpo as Partial<ErrorApi> | null
      if (c && typeof c.codigo === 'string') throw new ErrorDeApi(respuesta.status, { codigo: c.codigo, mensaje: c.mensaje ?? c.codigo, detalle: c.detalle })
      throw new ErrorDeRed(`El API respondió HTTP ${respuesta.status}`)
    }
    return cuerpo as T
  }

  const post = <T>(ruta: string, datos: unknown) => pedir<T>(ruta, { method: 'POST', body: JSON.stringify(datos) })

  return {
    modo: 'api',
    catalogo: () => pedir<Catalogo>('/catalogo'),
    reglas: () => pedir<Reglas>('/reglas'),
    simular: (credito) => post<Simulacion>('/simulaciones', credito),
    ejecutar: (peticion) => post<Ejecucion>('/escenarios/ejecutar', peticion),
    casos: () => pedir<CasoResumen[]>('/casos'),
    caso: (numero, perfil) => pedir<CasoDetalle>(`/casos/${numero}?perfilReglas=${perfil}`),
  }
}

/** URL del API: `VITE_API_URL` sin definir → http://localhost:8080; definida y vacía → modo demostración. */
export function urlDelApi(valor: string | undefined): string {
  return valor === undefined ? 'http://localhost:8080' : valor.trim()
}

export type { Instruccion }
