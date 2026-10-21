import { describe, expect, it, vi } from 'vitest'
import { crearCliente, ErrorDeApi, ErrorDeRed, urlDelApi } from './cliente'
import { conRespaldo, demo } from '../demo'
import type { Credito } from '../tipos'

const respuesta = (cuerpo: unknown, estado = 200) => Promise.resolve(new Response(JSON.stringify(cuerpo), { status: estado, headers: { 'Content-Type': 'application/json' } }))

const credito: Credito = { linea: 'CPPU', periodicidad: 'QUINCENAL', cuotas: 48, meses: null, valorMoto: '5100000', pagoInicial: '0', perfilReglas: 'NEGOCIO' }

describe('cliente HTTP', () => {
  it('pide el catálogo a /api/v1/catalogo sin barras dobles', async () => {
    const f = vi.fn().mockReturnValue(respuesta({ lineas: [] }))
    const cliente = crearCliente('http://localhost:8080/', f)
    await cliente.catalogo()
    expect(f.mock.calls[0][0]).toBe('http://localhost:8080/api/v1/catalogo')
  })

  it('manda la simulación como JSON y devuelve el dinero como string', async () => {
    const f = vi.fn().mockReturnValue(respuesta({ resumen: { cuotaTotal: '355667.00' }, plan: [] }))
    const sim = await crearCliente('http://api', f).simular(credito)
    const [url, init] = f.mock.calls[0]
    expect(url).toBe('http://api/api/v1/simulaciones')
    expect(init.method).toBe('POST')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(init.body)).toEqual(credito)
    expect(sim.resumen.cuotaTotal).toBe('355667.00')
  })

  it('pide un caso con el perfil de reglas', async () => {
    const f = vi.fn().mockReturnValue(respuesta({ caso: { numero: 13 } }))
    await crearCliente('http://api', f).caso(13, 'EXCEL')
    expect(f.mock.calls[0][0]).toBe('http://api/api/v1/casos/13?perfilReglas=EXCEL')
  })

  it('ejecuta escenarios con las instrucciones', async () => {
    const f = vi.fn().mockReturnValue(respuesta({ resumen: {}, movimientos: [] }))
    const peticion = { credito, perfilReglas: 'NEGOCIO' as const, instrucciones: [{ periodo: 42, tipo: 'ABONO_EXTRA' as const, valor: '1500000', modalidad: 'MENOR_PLAZO' }], incluirAsientos: true }
    await crearCliente('http://api', f).ejecutar(peticion)
    expect(f.mock.calls[0][0]).toBe('http://api/api/v1/escenarios/ejecutar')
    expect(JSON.parse(f.mock.calls[0][1].body)).toEqual(peticion)
  })

  it('convierte un 400 del API en ErrorDeApi con código y detalle', async () => {
    const f = vi.fn().mockReturnValue(respuesta({ codigo: 'ABONO_MAYOR_QUE_SALDO', mensaje: 'El abono supera el saldo', detalle: { saldo: '100' } }, 400))
    const error = await crearCliente('http://api', f).ejecutar({ credito, perfilReglas: 'NEGOCIO', instrucciones: [], incluirAsientos: false }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ErrorDeApi)
    expect(error).toMatchObject({ codigo: 'ABONO_MAYOR_QUE_SALDO', estado: 400, message: 'El abono supera el saldo', detalle: { saldo: '100' } })
  })

  it('trata un fallo de red como ErrorDeRed', async () => {
    const f = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    await expect(crearCliente('http://api', f).reglas()).rejects.toBeInstanceOf(ErrorDeRed)
  })

  it('trata una respuesta que no es JSON, o un error sin código, como ErrorDeRed', async () => {
    const html = vi.fn().mockResolvedValue(new Response('<html>', { status: 502 }))
    await expect(crearCliente('http://api', html).casos()).rejects.toBeInstanceOf(ErrorDeRed)
    const sinCodigo = vi.fn().mockReturnValue(respuesta({ error: 'x' }, 500))
    await expect(crearCliente('http://api', sinCodigo).casos()).rejects.toBeInstanceOf(ErrorDeRed)
  })

  it('urlDelApi: sin definir usa localhost:8080; vacío es modo demostración', () => {
    expect(urlDelApi(undefined)).toBe('http://localhost:8080')
    expect(urlDelApi('')).toBe('')
    expect(urlDelApi(' https://api.x ')).toBe('https://api.x')
  })
})

describe('respaldo en la demostración', () => {
  it('cae a los fixtures cuando el API no responde y avisa una sola vez', async () => {
    const f = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    const alCaer = vi.fn()
    const fuente = conRespaldo(crearCliente('http://api', f), demo, alCaer, (e) => e instanceof ErrorDeRed)
    expect(fuente.modo).toBe('api')
    const casos = await fuente.casos()
    expect(casos).toHaveLength(13)
    expect(fuente.modo).toBe('demo')
    await fuente.reglas()
    expect(alCaer).toHaveBeenCalledTimes(1)
    expect(f).toHaveBeenCalledTimes(1)
  })

  it('un error de negocio del API no activa la demostración', async () => {
    const f = vi.fn().mockReturnValue(respuesta({ codigo: 'PLAZO_MAXIMO', mensaje: 'x' }, 400))
    const alCaer = vi.fn()
    const fuente = conRespaldo(crearCliente('http://api', f), demo, alCaer, (e) => e instanceof ErrorDeRed)
    await expect(fuente.simular(credito)).rejects.toBeInstanceOf(ErrorDeApi)
    expect(alCaer).not.toHaveBeenCalled()
  })
})
