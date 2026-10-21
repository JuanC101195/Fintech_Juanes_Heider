import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App, { leerRuta } from './App'
import { demo } from './demo'
import type { Ejecucion, Simulacion, CasoDetalle } from './tipos'

const sp = (s: string) => s.replaceAll(' ', ' ')
/** Texto de todo el documento con espacios normales, para buscar cifras formateadas. */
const texto = () => sp(document.body.textContent ?? '')

function abrir(hash: string) {
  window.location.hash = hash
  return render(<App fuente={demo} />)
}

beforeEach(() => {
  window.scrollTo = vi.fn()
})
afterEach(() => {
  cleanup()
  window.location.hash = ''
})

describe('rutas', () => {
  it('interpreta el hash', () => {
    expect(leerRuta('')).toEqual({ pantalla: 'simulador' })
    expect(leerRuta('#/casos')).toEqual({ pantalla: 'casos' })
    expect(leerRuta('#/casos/13')).toEqual({ pantalla: 'casos', numero: 13 })
    expect(leerRuta('#/casos/x')).toEqual({ pantalla: 'casos' })
    expect(leerRuta('#/laboratorio')).toEqual({ pantalla: 'laboratorio' })
    expect(leerRuta('#/reglas')).toEqual({ pantalla: 'reglas' })
    expect(leerRuta('#/otra')).toEqual({ pantalla: 'simulador' })
  })
})

describe('fixtures de demostración', () => {
  it('la simulación quincenal de la línea estándar da 48 cuotas y las cifras acordadas', async () => {
    const s: Simulacion = await demo.simular({ linea: 'CPPU', periodicidad: 'QUINCENAL', cuotas: 48, meses: null, valorMoto: '5100000', pagoInicial: '0', perfilReglas: 'NEGOCIO' })
    expect(s.resumen).toMatchObject({ cuotas: 48, cuotaFinanciera: '219093.00', cuotaTotal: '355667.00', totalPagado: '17072015.00' })
    expect(s.plan).toHaveLength(48)
    expect(s.plan.at(-1)?.saldo).toBe('0.00')
    expect(s.resumen.porConfirmar[0]).toContain('24 periodos')
  })
  it('la mensual da 24 cuotas', async () => {
    const s = await demo.simular({ linea: 'CPPU', periodicidad: 'MENSUAL', cuotas: 24, meses: null, valorMoto: '5100000', pagoInicial: '0', perfilReglas: 'NEGOCIO' })
    expect(s.resumen).toMatchObject({ cuotas: 24, cuotaFinanciera: '444006.00', cuotaTotal: '717154.00', totalPagado: '17211702.00' })
  })
  it('el caso 13 en la semana 42 trae las cifras acordadas y termina en la 83', async () => {
    const c: CasoDetalle = await demo.caso(13, 'NEGOCIO')
    const m = c.movimientos.find((x) => x.periodo === 42)!
    expect(sp(m.saldoFinal!)).toBe('3169909.34')
    expect(m.abonoExtra).toBe('1182645.195')
    expect((m.pendientesPagadosExtra as Record<string, string>).total).toBe('317354.805')
    expect(c.resumen.periodoFinal).toBe(83)
    expect(c.asientos?.every((a) => a.cuadra)).toBe(true)
  })
  it('el perfil EXCEL del caso 13 no paga los pendientes con el abono', async () => {
    const c = await demo.caso(13, 'EXCEL')
    const m = c.movimientos.find((x) => x.periodo === 42)!
    expect(m.abonoExtra).toBe('1500000.000')
    expect(c.resumen.periodoFinal).not.toBe(83)
  })
  it('rechaza más de 24 meses y un abono mayor que el saldo', async () => {
    await expect(demo.simular({ linea: 'CPPU', periodicidad: 'MENSUAL', cuotas: null, meses: 25, valorMoto: '5100000', pagoInicial: '0', perfilReglas: 'NEGOCIO' })).rejects.toMatchObject({ codigo: 'PLAZO_MAXIMO' })
    const credito = { linea: 'CPPU', periodicidad: 'SEMANAL' as const, cuotas: 104, meses: null, valorMoto: '5100000', pagoInicial: '0', perfilReglas: 'NEGOCIO' as const }
    await expect(demo.ejecutar({ credito, perfilReglas: 'NEGOCIO', incluirAsientos: false, instrucciones: [{ periodo: 10, tipo: 'ABONO_EXTRA', valor: '99000000', modalidad: 'MENOR_PLAZO' }] })).rejects.toMatchObject({ codigo: 'ABONO_MAYOR_QUE_SALDO' })
    const ok: Ejecucion = await demo.ejecutar({ credito, perfilReglas: 'NEGOCIO', incluirAsientos: false, instrucciones: [] })
    expect(ok.asientos).toBeUndefined()
  })
})

describe('Simulador', () => {
  it('corre con los valores por defecto y muestra tarjetas, gráficos y tabla', async () => {
    abrir('#/simulador')
    await waitFor(() => expect(screen.getByRole('region', { name: 'Resultado de la simulación' })).toBeTruthy())
    const t = texto()
    expect(t).toContain('$ 355.667')
    expect(t).toContain('$ 219.093')
    expect(t).toContain('$ 17.072.015')
    expect(t).toContain('Datos de demostración')
    expect(screen.getByRole('img', { name: /Saldo de capital/ })).toBeTruthy()
    expect(screen.getByRole('img', { name: /Composición de la cuota/ })).toBeTruthy()
    const tabla = screen.getByRole('region', { name: 'Tabla del plan de pagos' })
    expect(within(tabla).getAllByRole('row').length).toBeGreaterThan(48)
    expect(screen.getByText(/24 periodos\/año, por confirmar/)).toBeTruthy()
  })

  it('cambia a mensual y muestra sus cifras', async () => {
    abrir('#/simulador')
    await waitFor(() => expect(screen.getByRole('region', { name: 'Resultado de la simulación' })).toBeTruthy())
    fireEvent.click(screen.getByRole('radio', { name: /Mensual/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }))
    await waitFor(() => expect(texto()).toContain('$ 717.154'))
    expect(texto()).toContain('$ 444.006')
    expect(texto()).toContain('$ 17.211.702')
  })

  it('valida el máximo de 24 meses sin llamar al API', async () => {
    abrir('#/simulador')
    await waitFor(() => expect(screen.getByRole('region', { name: 'Resultado de la simulación' })).toBeTruthy())
    fireEvent.click(screen.getByRole('button', { name: /Cambiar a plazo en meses/ }))
    fireEvent.change(screen.getByLabelText('Plazo en meses'), { target: { value: '30' } })
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }))
    await waitFor(() => expect(screen.getByText(/El plazo máximo es de 24 meses/, { selector: 'p' })).toBeTruthy())
  })
})

describe('Casos', () => {
  it('lista los 13 casos', async () => {
    abrir('#/casos')
    await waitFor(() => expect(screen.getAllByRole('link', { name: /Caso \d+/ })).toHaveLength(13))
  })

  it('el detalle del caso 13 muestra la tabla, la línea de tiempo y los asientos', async () => {
    abrir('#/casos/13')
    await waitFor(() => expect(screen.getByRole('heading', { level: 1 }).textContent).toContain('Caso 13'))
    await waitFor(() => expect(screen.getByRole('region', { name: 'Tabla periodo a periodo' })).toBeTruthy())
    const t = texto()
    expect(t).toContain('$ 317.354,81')
    expect(t).toContain('$ 1.182.645,20')
    expect(t).toContain('$ 3.169.909,34')
    expect(t).toContain('Termina en el periodo 83')
    expect(screen.getByRole('list', { name: 'Línea de tiempo de eventos' }).textContent).toContain('Periodos 40 a 41')
    expect(screen.getAllByText('✓ Cuadra').length).toBeGreaterThan(40)
    expect(screen.getByRole('columnheader', { name: 'Pago' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'Aplicación por prelación' })).toBeTruthy()
  })

  it('cambiar el perfil a EXCEL recarga el caso con otro resultado', async () => {
    abrir('#/casos/13')
    await waitFor(() => expect(texto()).toContain('Termina en el periodo 83'))
    fireEvent.click(screen.getByRole('radio', { name: /Excel/ }))
    await waitFor(() => expect(texto()).not.toContain('Termina en el periodo 83'))
  })
})

describe('Laboratorio', () => {
  it('muestra el error legible del API (ABONO_MAYOR_QUE_SALDO)', async () => {
    abrir('#/laboratorio')
    await waitFor(() => expect(screen.getByLabelText('Tipo de instrucción')).toBeTruthy())
    fireEvent.change(screen.getByLabelText('Periodo'), { target: { value: '10' } })
    fireEvent.change(screen.getByLabelText('Tipo de instrucción'), { target: { value: 'ABONO_EXTRA' } })
    fireEvent.change(screen.getByLabelText('Valor del abono (COP)'), { target: { value: '99000000' } })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar instrucción' }))
    expect(screen.getByText(/Abono extraordinario de \$/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Ejecutar' }))
    const alerta = await screen.findByRole('alert')
    expect(alerta.textContent).toContain('ABONO_MAYOR_QUE_SALDO')
    expect(alerta.textContent).toContain('mayor que el saldo de capital')
    expect(sp(alerta.textContent ?? '')).toContain('Saldo de capital: $')
  })

  it('valida el formulario antes de agregar', async () => {
    abrir('#/laboratorio')
    await waitFor(() => expect(screen.getByLabelText('Tipo de instrucción')).toBeTruthy())
    fireEvent.change(screen.getByLabelText('Tipo de instrucción'), { target: { value: 'PAGO_VALOR' } })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar instrucción' }))
    expect(screen.getByText('Obligatorio')).toBeTruthy()
  })

  it('parte de un caso y ejecuta', async () => {
    abrir('#/laboratorio')
    await waitFor(() => expect(screen.getAllByRole('option', { name: /Caso 13/ }).length).toBe(1))
    fireEvent.change(screen.getByLabelText('Partir de un caso'), { target: { value: '13' } })
    await waitFor(() => expect(screen.getByRole('button', { name: /Editar la instrucción del periodo 42/ })).toBeTruthy())
    fireEvent.click(screen.getByRole('button', { name: 'Ejecutar' }))
    await waitFor(() => expect(texto()).toContain('Termina en el periodo 83'))
    expect(screen.getByRole('region', { name: 'Tabla periodo a periodo' })).toBeTruthy()
  })

  it('"Abrir en el laboratorio" desde un caso carga sus instrucciones', async () => {
    abrir('#/casos/13')
    const boton = await screen.findByRole('button', { name: 'Abrir en el laboratorio' })
    fireEvent.click(boton)
    await waitFor(() => expect(screen.getByRole('button', { name: /Editar la instrucción del periodo 42/ })).toBeTruthy())
    expect(texto()).toContain('Caso 13: Abono extra con pendientes')
  })
})

describe('Reglas', () => {
  it('muestra las claves con su valor por perfil, la fuente y lo por confirmar', async () => {
    abrir('#/reglas')
    const tabla = await screen.findByRole('region', { name: 'Tabla de reglas por perfil' })
    expect(within(tabla).getAllByRole('row')).toHaveLength(12)
    expect(within(tabla).getByText('PRIMERO_PENDIENTES')).toBeTruthy()
    expect(within(tabla).getByText('A4')).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Por confirmar' })).toBeTruthy()
  })
})

describe('navegación', () => {
  it('tiene navegación lateral y inferior con las 4 pantallas', () => {
    abrir('')
    const navs = screen.getAllByRole('navigation', { name: 'Secciones' })
    expect(navs).toHaveLength(2)
    for (const nav of navs) expect(within(nav).getAllByRole('link').map((a) => a.textContent?.replace(/\P{L}/gu, ''))).toEqual(['Simulador', 'Casos', 'Laboratorio', 'Reglas'])
  })
})
