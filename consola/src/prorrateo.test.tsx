// P5-PRORRATEO en la consola: el Simulador y el Laboratorio dejan elegir fecha de desembolso y primer vencimiento
// (opcionales), los validan como el API, los mandan solo si vienen las dos y muestran el interés prorrateado.
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { FuenteDatos } from './api/cliente'
import { demo } from './demo'
import type { Credito, Ejecucion, PeticionEjecucion, Simulacion } from './tipos'

const raiz = path.resolve(__dirname, '..', '..')
const ejemplo = <T,>(archivo: string): T => JSON.parse(readFileSync(path.join(raiz, 'docs', 'api-ejemplos', archivo), 'utf8')) as T
/** Espacios normales en lugar del espacio duro que pone el formato de pesos. */
const sp = (s: string | null | undefined) => (s ?? '').replace(/ /g, ' ')
const texto = () => sp(document.body.textContent)

/** Fuente "API" que responde con los ejemplos reales con prorrateo y guarda lo que se le pidió. */
function fuenteConProrrateo() {
  const pedidos: { simular: Credito[]; ejecutar: PeticionEjecucion[] } = { simular: [], ejecutar: [] }
  const fuente: FuenteDatos = {
    ...demo,
    modo: 'api',
    async simular(c) {
      pedidos.simular.push(c)
      return c.fechaDesembolso ? ejemplo<Simulacion>('simulacion-mensual-prorrateo.json') : demo.simular(c)
    },
    async ejecutar(p) {
      pedidos.ejecutar.push(p)
      return p.credito.fechaDesembolso ? ejemplo<Ejecucion>('escenario-semanal-prorrateo.json') : demo.ejecutar(p)
    },
  }
  return { fuente, pedidos }
}

function abrir(hash: string, fuente: FuenteDatos = demo) {
  window.location.hash = hash
  return render(<App fuente={fuente} />)
}

const fecha = (etiqueta: string, valor: string) => fireEvent.change(screen.getByLabelText(etiqueta), { target: { value: valor } })

beforeEach(() => {
  window.scrollTo = vi.fn()
})
afterEach(() => {
  cleanup()
  window.location.hash = ''
})

describe('Simulador con fechas', () => {
  it('valida las fechas como el API antes de simular y sin fechas no manda nada nuevo', async () => {
    const { fuente, pedidos } = fuenteConProrrateo()
    abrir('#/simulador', fuente)
    await waitFor(() => expect(screen.getByRole('region', { name: 'Resultado de la simulación' })).toBeTruthy())
    expect(pedidos.simular[0]).not.toHaveProperty('fechaDesembolso')
    expect(pedidos.simular[0]).not.toHaveProperty('primerVencimiento')
    expect(screen.queryByRole('region', { name: 'Prorrateo de la primera cuota' })).toBeNull()

    fireEvent.click(screen.getByRole('radio', { name: /Mensual/ }))
    fecha('Fecha de desembolso', '2026-10-23')
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }))
    expect(await screen.findByText(/Falta el primer vencimiento/)).toBeTruthy()
    fecha('Primer vencimiento', '2026-11-06')
    expect(screen.getByText(/La cuota mensual vence el día 5/)).toBeTruthy()
    expect(pedidos.simular).toHaveLength(1)
  })

  it('manda las dos fechas y muestra los días y el interés prorrateado', async () => {
    const { fuente, pedidos } = fuenteConProrrateo()
    abrir('#/simulador', fuente)
    await waitFor(() => expect(screen.getByRole('region', { name: 'Resultado de la simulación' })).toBeTruthy())
    fireEvent.click(screen.getByRole('radio', { name: /Mensual/ }))
    fecha('Fecha de desembolso', '2026-10-23')
    fecha('Primer vencimiento', '2026-12-05')
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }))
    const aviso = await screen.findByRole('region', { name: 'Prorrateo de la primera cuota' })
    expect(pedidos.simular.at(-1)).toMatchObject({ periodicidad: 'MENSUAL', fechaDesembolso: '2026-10-23', primerVencimiento: '2026-12-05' })
    const t = sp(aviso.textContent)
    expect(t).toContain('43 días')
    expect(t).toContain('23 de octubre de 2026')
    expect(t).toContain('$ 454.328,63')
    expect(t).toContain('$ 317.872,58')
    expect(t).toContain('$ 136.456 más que un periodo normal')
    // La tarjeta muestra la cuota regular y avisa que la primera va prorrateada.
    // R9-SEGURO-TODO-RIESGO: 717.154 + 5.904.400 × 0,3 % × 1,19 (21.078,71) = 738.233.
    expect(texto()).toContain('$ 738.233')
    expect(texto()).toContain('la primera va prorrateada')

    fireEvent.click(screen.getByRole('button', { name: 'Quitar las fechas' }))
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }))
    await waitFor(() => expect(pedidos.simular.at(-1)).not.toHaveProperty('fechaDesembolso'))
  })

  it('en modo demostración acepta fechas válidas pero avisa que las cifras no incluyen el prorrateo', async () => {
    abrir('#/simulador')
    await waitFor(() => expect(screen.getByRole('region', { name: 'Resultado de la simulación' })).toBeTruthy())
    fecha('Fecha de desembolso', '2026-10-23')
    fecha('Primer vencimiento', '2026-11-05')
    expect(screen.getByText(/no incluyen el prorrateo/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }))
    await waitFor(() => expect(texto()).toContain('$ 355.667'))
    expect(screen.queryByRole('alert')).toBeNull()
  })
})

describe('Laboratorio con fechas', () => {
  it('no deja calcular con una sola fecha y con las dos muestra el prorrateo del resultado', async () => {
    const { fuente, pedidos } = fuenteConProrrateo()
    abrir('#/laboratorio', fuente)
    await waitFor(() => expect(screen.getByLabelText('Fecha de desembolso')).toBeTruthy())
    fecha('Fecha de desembolso', '2026-10-23')
    expect((screen.getByRole('button', { name: 'Calcular resultado' }) as HTMLButtonElement).disabled).toBe(true)
    fecha('Primer vencimiento', '2026-11-04')
    expect(screen.getByText(/martes; esa fecha es miércoles/)).toBeTruthy()
    fecha('Primer vencimiento', '2026-11-03')
    fireEvent.click(screen.getByRole('button', { name: 'Calcular resultado' }))
    const aviso = await screen.findByRole('region', { name: 'Prorrateo de la primera cuota' })
    expect(aviso.textContent).toContain('11 días')
    expect(sp(aviso.textContent)).toContain('$ 41.154 más que un periodo normal')
    expect(pedidos.ejecutar[0].credito).toMatchObject({ periodicidad: 'SEMANAL', fechaDesembolso: '2026-10-23', primerVencimiento: '2026-11-03' })
  })

  it('el modo demostración rechaza fechas incoherentes con el mismo error del API', async () => {
    const credito: Credito = { linea: 'CPPU', periodicidad: 'QUINCENAL', cuotas: 48, meses: null, valorMoto: '5100000', pagoInicial: '0', perfilReglas: 'NEGOCIO', fechaDesembolso: '2026-10-23', primerVencimiento: '2026-11-15' }
    await expect(demo.simular(credito)).rejects.toMatchObject({ codigo: 'VALIDACION', detalle: { campo: 'primerVencimiento' } })
    await expect(demo.ejecutar({ credito: { ...credito, primerVencimiento: undefined }, perfilReglas: 'NEGOCIO', instrucciones: [], incluirAsientos: false })).rejects.toMatchObject({ codigo: 'VALIDACION' })
  })
})
