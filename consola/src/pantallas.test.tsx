import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App, { leerRuta } from './App'
import { demo } from './demo'
import { comparar, cop, copCentavos, sumar } from './dinero'
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
    expect(s.resumen).toMatchObject({ cuotas: 48, cuotaFinanciera: '219092.9355960436057213056188035084', cuotaTotal: '355666.9755960436057213056188035084' })
    expect(cop(s.resumen.cuotaTotal).replaceAll(' ', ' ')).toBe('$ 355.667')
    expect(cop(s.resumen.totalPagado).replaceAll(' ', ' ')).toBe('$ 17.072.015')
    expect(s.plan).toHaveLength(48)
    expect(comparar(s.plan.at(-1)!.saldo, '0.000001')).toBeLessThanOrEqual(0)
    expect(s.resumen.porConfirmar).toEqual([])
  })
  it('la mensual da 24 cuotas', async () => {
    const s = await demo.simular({ linea: 'CPPU', periodicidad: 'MENSUAL', cuotas: 24, meses: null, valorMoto: '5100000', pagoInicial: '0', perfilReglas: 'NEGOCIO' })
    expect(s.resumen.cuotas).toBe(24)
    expect(cop(s.resumen.cuotaFinanciera).replaceAll(' ', ' ')).toBe('$ 444.006')
    expect(cop(s.resumen.cuotaTotal).replaceAll(' ', ' ')).toBe('$ 717.154')
    expect(cop(s.resumen.totalPagado).replaceAll(' ', ' ')).toBe('$ 17.211.702')
  })
  it('el caso 13 en la semana 42 trae las cifras acordadas y termina en la 84', async () => {
    const c: CasoDetalle = await demo.caso(13, 'NEGOCIO')
    const m = c.movimientos.find((x) => x.periodo === 42)!
    expect(sp(copCentavos(m.saldoFinal))).toBe('$ 3.229.537,12')
    expect(sp(copCentavos(m.abonoExtra))).toBe('$ 1.123.017,41')
    expect(sp(copCentavos(m.pendientesPagadosExtra.total))).toBe('$ 376.982,59')
    expect(sp(copCentavos(sumar(m.abonoExtra, m.pendientesPagadosExtra.total)))).toBe('$ 1.500.000,00')
    expect(m.evento).toBe('Cuota + abono extra')
    expect(c.resumen.periodoFinal).toBe(84)
    expect(c.asientos?.every((a) => a.cuadra)).toBe(true)
  })
  it('el perfil EXCEL del caso 13 no paga los pendientes con el abono', async () => {
    const c = await demo.caso(13, 'EXCEL')
    const m = c.movimientos.find((x) => x.periodo === 42)!
    expect(sp(copCentavos(m.abonoExtra))).toBe('$ 1.500.000,00')
    expect(sp(copCentavos(m.pendientesPagadosExtra.total))).toBe('$ 0,00')
    expect(sp(copCentavos(m.saldoFinal))).toBe('$ 2.991.408,97')
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
    expect(screen.queryByText(/por confirmar/)).toBeNull()
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

  it('elige la línea con tarjetas, formatea el dinero en vivo y mueve el plazo con − y +', async () => {
    abrir('#/simulador')
    await waitFor(() => expect(screen.getByRole('region', { name: 'Resultado de la simulación' })).toBeTruthy())
    expect(screen.getAllByRole('radio', { name: /^Crédito popular|^Consumo/ })).toHaveLength(4)
    const moto = screen.getByLabelText('Valor de la moto') as HTMLInputElement
    expect(moto.value).toBe('5.100.000')
    expect(moto.inputMode).toBe('numeric')
    fireEvent.change(moto, { target: { value: '6.200.0005' } })
    expect(moto.value).toBe('62.000.005')
    const plazo = screen.getByLabelText('Plazo en cuotas') as HTMLInputElement
    fireEvent.click(screen.getByRole('button', { name: 'Plazo en cuotas: restar 1' }))
    expect(plazo.value).toBe('47')
    fireEvent.click(screen.getByRole('button', { name: 'Plazo en cuotas: sumar 1' }))
    fireEvent.click(screen.getByRole('button', { name: 'Plazo en cuotas: sumar 1' }))
    expect(plazo.value).toBe('48')
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
    expect(t).toContain('$ 376.982,59')
    expect(t).toContain('$ 1.123.017,41')
    expect(t).toContain('Recaudo extraordinario aplicado a pendientes')
    expect(t).toContain('$ 3.229.537,12')
    expect(t).toContain('Termina en el periodo 84')
    expect(screen.getByRole('list', { name: 'Línea de tiempo de eventos' }).textContent).toContain('Periodo 40')
    // Por defecto solo los asientos de periodos con eventos; el botón despliega todos.
    const destacados = screen.getAllByText('✓ Cuadra').length
    expect(destacados).toBeLessThan(10)
    fireEvent.click(screen.getByRole('button', { name: /Ver los \d+ asientos/ }))
    expect(screen.getAllByText('✓ Cuadra').length).toBeGreaterThan(40)
    expect(screen.getByRole('columnheader', { name: 'Pago' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'Cómo se repartió el pago (prelación)' })).toBeTruthy()
  })

  it('cambiar el perfil a EXCEL recarga el caso con otro resultado', async () => {
    abrir('#/casos/13')
    await waitFor(() => expect(texto()).toContain('Termina en el periodo 84'))
    expect(texto()).toContain('$ 3.229.537,12')
    fireEvent.click(screen.getByRole('radio', { name: /Excel/ }))
    await waitFor(() => expect(texto()).not.toContain('$ 3.229.537,12'))
    await waitFor(() => expect(texto()).toContain('$ 2.991.408,97'))
  })

  it('"Ver otro caso" lleva a otro caso con el selector propio', async () => {
    abrir('#/casos/13')
    const boton = await screen.findByRole('button', { name: /Ver otro caso/ })
    fireEvent.click(boton)
    fireEvent.change(screen.getByRole('combobox', { name: /Buscar/ }), { target: { value: 'retoma' } })
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual([expect.stringContaining('Caso 4')])
    fireEvent.keyDown(screen.getByRole('combobox', { name: /Buscar/ }), { key: 'Enter' })
    await waitFor(() => expect(window.location.hash).toBe('#/casos/4'))
  })
})

/** Encuentra la tarjeta de punto de partida (radio) de un caso: "Caso 1:" no debe confundirse con "Caso 10:". */
const radioCaso = (n: number) => screen.getByRole('radio', { name: new RegExp(`^Caso ${n}:`) })
const partidaActual = () => document.querySelector('[data-prueba="partida-actual"]')?.textContent

describe('Laboratorio', () => {
  it('muestra el error legible del API (ABONO_MAYOR_QUE_SALDO)', async () => {
    abrir('#/laboratorio')
    await waitFor(() => expect(screen.getByRole('radio', { name: /^Abono extra/ })).toBeTruthy())
    fireEvent.change(screen.getByLabelText('Periodo'), { target: { value: '10' } })
    fireEvent.click(screen.getByRole('radio', { name: /^Abono extra/ }))
    fireEvent.change(screen.getByLabelText('Valor del abono'), { target: { value: '99000000' } })
    expect((screen.getByLabelText('Valor del abono') as HTMLInputElement).value).toBe('99.000.000')
    fireEvent.click(screen.getByRole('button', { name: 'Agregar evento' }))
    expect(within(screen.getByRole('list', { name: 'Eventos agregados' })).getByText(/Abono extra de \$/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Calcular resultado' }))
    const alerta = await screen.findByRole('alert')
    expect(alerta.textContent).toContain('ABONO_MAYOR_QUE_SALDO')
    expect(alerta.textContent).toContain('mayor que el saldo de capital')
    expect(sp(alerta.textContent ?? '')).toContain('Saldo de capital: $')
  })

  it('valida el formulario antes de agregar', async () => {
    abrir('#/laboratorio')
    await waitFor(() => expect(screen.getByRole('radio', { name: /^Pago de un valor/ })).toBeTruthy())
    fireEvent.click(screen.getByRole('radio', { name: /^Pago de un valor/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Agregar evento' }))
    expect(screen.getByText(/Este dato es obligatorio/)).toBeTruthy()
  })

  it('parte de un caso desde las tarjetas y calcula', async () => {
    abrir('#/laboratorio')
    await waitFor(() => expect(radioCaso(13)).toBeTruthy())
    expect(screen.getAllByRole('radio', { name: /^(Caso \d+|Crédito nuevo):/ })).toHaveLength(14)
    fireEvent.click(radioCaso(13))
    await waitFor(() => expect(screen.getAllByRole('button', { name: /Editar el evento del periodo 42/ }).length).toBeGreaterThan(0))
    expect(partidaActual()).toBe('Caso 13 · Abono extra con pendientes')
    fireEvent.click(screen.getByRole('button', { name: 'Calcular resultado' }))
    await waitFor(() => expect(texto()).toContain('Termina en el periodo 84'))
    expect(screen.getByRole('region', { name: 'Tabla periodo a periodo' })).toBeTruthy()
  })

  it('"Probar variaciones en el laboratorio" carga el caso y lo deja marcado como punto de partida (bug: decía «Crédito nuevo»)', async () => {
    abrir('#/casos/1')
    fireEvent.click(await screen.findByRole('button', { name: /Probar variaciones en el laboratorio/ }))
    await waitFor(() => expect(partidaActual()).toBe('Caso 1 · Operación normal'))
    fireEvent.click(screen.getByRole('button', { name: 'Elegir otro caso' }))
    await waitFor(() => expect(radioCaso(1)).toBeTruthy())
    // Un solo estado: la tarjeta marcada y el texto «Ahora» dicen lo mismo.
    expect((radioCaso(1) as HTMLInputElement).checked).toBe(true)
    expect((screen.getByRole('radio', { name: /^Crédito nuevo:/ }) as HTMLInputElement).checked).toBe(false)
    expect(screen.getAllByRole('radio', { checked: true, name: /^(Caso \d+|Crédito nuevo):/ })).toHaveLength(1)
    // Volver a crédito nuevo también deja todo coherente.
    fireEvent.click(screen.getByRole('radio', { name: /^Crédito nuevo:/ }))
    expect(partidaActual()).toBe('Crédito nuevo, sin eventos')
  })

  it('desde el caso 13 trae sus eventos y al cambiarlos lo dice', async () => {
    abrir('#/casos/13')
    fireEvent.click(await screen.findByRole('button', { name: /Probar variaciones en el laboratorio/ }))
    await waitFor(() => expect(screen.getAllByRole('button', { name: /Editar el evento del periodo 42/ }).length).toBeGreaterThan(0))
    expect(partidaActual()).toBe('Caso 13 · Abono extra con pendientes')
    fireEvent.click(screen.getAllByRole('button', { name: 'Quitar el evento del periodo 42' })[0])
    expect(partidaActual()).toBe('Caso 13 · Abono extra con pendientes (con cambios tuyos)')
  })

  it('el atajo "no paga 2 semanas" llena el formulario y crea dos periodos seguidos', async () => {
    abrir('#/laboratorio')
    const atajo = await screen.findByRole('button', { name: /El cliente no paga 2 semanas/ })
    fireEvent.click(atajo)
    expect((screen.getByRole('radio', { name: /^Sin pago/ }) as HTMLInputElement).checked).toBe(true)
    expect((screen.getByLabelText('¿Cuántos periodos seguidos?') as HTMLInputElement).value).toBe('2')
    fireEvent.click(screen.getByRole('button', { name: 'Periodo: sumar 1' }))
    expect((screen.getByLabelText('Periodo') as HTMLInputElement).value).toBe('2')
    fireEvent.change(screen.getByLabelText('Periodo'), { target: { value: '40' } })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar evento' }))
    expect(screen.getByRole('button', { name: 'Editar el evento del periodo 40' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Editar el evento del periodo 41' })).toBeTruthy()
  })

  it('los atajos de abono, usura y dación traen el valor ya escrito y con formato', async () => {
    abrir('#/laboratorio')
    fireEvent.click(await screen.findByRole('button', { name: /Abono extra de \$ 500\.000 a menor plazo/ }))
    expect((screen.getByLabelText('Valor del abono') as HTMLInputElement).value).toBe('500.000')
    fireEvent.click(screen.getByRole('button', { name: /Baja la usura al 81 %/ }))
    expect((screen.getByLabelText('Nueva tasa de usura (efectiva anual)') as HTMLInputElement).value).toBe('81')
    fireEvent.click(screen.getByRole('button', { name: /Dación de la moto/ }))
    expect((screen.getByLabelText('Valor en que se recibe la moto') as HTMLInputElement).value).toBe('3.000.000')
    fireEvent.click(screen.getByRole('button', { name: 'Agregar evento' }))
    expect(sp(texto())).toContain('Dación en pago por $ 3.000.000')
  })
})

describe('Reglas', () => {
  it('muestra cada regla en lenguaje de negocio, sin nombres internos, con su referencia y lo por confirmar', async () => {
    abrir('#/reglas')
    const lista = await screen.findByRole('list', { name: 'Reglas por perfil' })
    expect(within(lista).getAllByRole('listitem')).toHaveLength(14)
    expect(within(lista).getAllByText('Paga lo pendiente y el sobrante vuelve a pasar por la prelación (cobranza, mora, interés, servicios y capital)').length).toBeGreaterThan(0)
    expect(within(lista).getByText(/A4/)).toBeTruthy()
    expect(lista.textContent).not.toMatch(/[A-Z]{3,}_[A-Z]/)
    expect(screen.getByRole('heading', { name: 'Por confirmar' })).toBeTruthy()
  })
})

describe('servidor dormido', () => {
  it('mientras el API no responde la primera vez, avisa que puede tardar en despertar; luego ya no', async () => {
    let soltar: () => void = () => undefined
    const espera = new Promise<void>((r) => (soltar = r))
    const lenta = { ...demo, modo: 'api' as const, catalogo: async () => (await espera, demo.catalogo()) }
    window.location.hash = '#/simulador'
    render(<App fuente={lenta} />)
    expect(screen.getByText(/puede tardar unos segundos en despertar/)).toBeTruthy()
    soltar()
    await waitFor(() => expect(screen.getByRole('region', { name: 'Resultado de la simulación' })).toBeTruthy())
    expect(screen.queryByText(/puede tardar unos segundos en despertar/)).toBeNull()
  })
})

describe('sin jerga técnica a la vista', () => {
  it('el laboratorio no muestra nombres internos (MISMA_CUOTA, DACION_EN_PAGO…)', async () => {
    abrir('#/laboratorio')
    await waitFor(() => expect(radioCaso(13)).toBeTruthy())
    fireEvent.click(screen.getByRole('radio', { name: /^Cambio de tasa/ }))
    expect(document.body.textContent).not.toMatch(/[A-Z]{3,}_[A-Z]/)
    fireEvent.click(screen.getByRole('radio', { name: /^Dación o retoma/ }))
    expect(document.body.textContent).not.toMatch(/[A-Z]{3,}_[A-Z]/)
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
