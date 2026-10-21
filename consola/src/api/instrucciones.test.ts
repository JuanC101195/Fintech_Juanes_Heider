import { describe, expect, it } from 'vitest'
import { aFormulario, agregarInstrucciones, aInstruccion, atajos, categoria, describir, ordenarInstrucciones, validarFormulario, valoresPorDefecto } from './instrucciones'

describe('agregar y atajos', () => {
  it('una instrucción del mismo tipo y periodo se reemplaza, no se duplica', () => {
    const lista: Instruccion[] = [{ periodo: 5, tipo: 'SIN_PAGO', causaMoraYServicios: true }, { periodo: 2, tipo: 'PAGO_AL_DIA' }]
    const r = agregarInstrucciones(lista, [{ periodo: 5, tipo: 'SIN_PAGO', causaMoraYServicios: false }, { periodo: 6, tipo: 'SIN_PAGO', causaMoraYServicios: false }])
    expect(r.map((i) => `${i.periodo}-${i.tipo}-${String(i.causaMoraYServicios)}`)).toEqual(['2-PAGO_AL_DIA-undefined', '5-SIN_PAGO-false', '6-SIN_PAGO-false'])
  })
  it('los atajos hablan del periodo según la periodicidad y traen valores válidos', () => {
    expect(atajos('SEMANAL')[0].texto).toBe('El cliente no paga 2 semanas')
    expect(atajos('MENSUAL')[0].texto).toBe('El cliente no paga 2 meses')
    for (const a of atajos('SEMANAL')) expect(validarFormulario(a.tipo, 10, { ...valoresPorDefecto(a.tipo), ...a.valores })).toEqual({})
  })
})
import { agruparEventos } from '../componentes/DetalleEjecucion'
import type { Instruccion } from '../tipos'

describe('formulario → instrucción', () => {
  it('abono extra: dinero como string, modalidad del catálogo', () => {
    const v = { ...valoresPorDefecto('ABONO_EXTRA'), valor: '1500000' }
    expect(aInstruccion('ABONO_EXTRA', 42, v)).toEqual({ periodo: 42, tipo: 'ABONO_EXTRA', valor: '1500000', modalidad: 'MENOR_PLAZO' })
  })

  it('pago por valor: días de atraso como número y se omite lo vacío', () => {
    expect(aInstruccion('PAGO_VALOR', 41, { valor: '5000', diasAtraso: '3' })).toEqual({ periodo: 41, tipo: 'PAGO_VALOR', valor: '5000', diasAtraso: 3 })
    expect(aInstruccion('PAGO_VALOR', 41, { valor: '5000', diasAtraso: '' })).toEqual({ periodo: 41, tipo: 'PAGO_VALOR', valor: '5000' })
  })

  it('sin pago envía la bandera booleana; pago al día no lleva campos', () => {
    expect(aInstruccion('SIN_PAGO', 40, valoresPorDefecto('SIN_PAGO'))).toEqual({ periodo: 40, tipo: 'SIN_PAGO', causaMoraYServicios: true })
    expect(aInstruccion('PAGO_AL_DIA', 23, {})).toEqual({ periodo: 23, tipo: 'PAGO_AL_DIA' })
  })

  it('usura, cambio de tasa y cierre por recuperación', () => {
    expect(aInstruccion('USURA_VIGENTE', 43, { usuraEA: '0.81' })).toEqual({ periodo: 43, tipo: 'USURA_VIGENTE', usuraEA: '0.81' })
    expect(aInstruccion('CAMBIO_TASA', 30, { nuevaTasaEA: '0.7', modalidad: 'MISMA_CUOTA' })).toEqual({ periodo: 30, tipo: 'CAMBIO_TASA', nuevaTasaEA: '0.7', modalidad: 'MISMA_CUOTA' })
    expect(aInstruccion('CIERRE_POR_RECUPERACION', 25, { tipoRecuperacion: 'DACION_EN_PAGO', valorMoto: '3200000' })).toEqual({ periodo: 25, tipo: 'CIERRE_POR_RECUPERACION', tipoRecuperacion: 'DACION_EN_PAGO', valorMoto: '3200000' })
  })

  it('ida y vuelta: instrucción → formulario → instrucción', () => {
    const originales: Instruccion[] = [
      { periodo: 42, tipo: 'ABONO_EXTRA', valor: '1500000', modalidad: 'MENOR_CUOTA' },
      { periodo: 15, tipo: 'PAGO_PARCIAL', fraccion: '0.4', diasAtraso: 2 },
      { periodo: 40, tipo: 'SIN_PAGO', causaMoraYServicios: false },
      { periodo: 26, tipo: 'PREPAGO_TOTAL', diasDesdeUltimaCuota: 3 },
    ]
    for (const i of originales) expect(aInstruccion(i.tipo, i.periodo, aFormulario(i))).toEqual(i)
  })
})

describe('validación', () => {
  it('exige los campos obligatorios y un periodo entero', () => {
    expect(validarFormulario('ABONO_EXTRA', 42, { valor: '', modalidad: 'MENOR_PLAZO' })).toEqual({ valor: 'Este dato es obligatorio' })
    expect(validarFormulario('SIN_PAGO', 0, valoresPorDefecto('SIN_PAGO')).periodo).toBeDefined()
  })
  it('rechaza dinero no positivo o mal escrito, y fracciones fuera de 0 a 1', () => {
    expect(validarFormulario('PAGO_VALOR', 1, { valor: '-5', diasAtraso: '0' }).valor).toBeDefined()
    expect(validarFormulario('PAGO_VALOR', 1, { valor: '1,5', diasAtraso: '0' }).valor).toBeDefined()
    expect(validarFormulario('PAGO_PARCIAL', 1, { fraccion: '1.5', diasAtraso: '0' }).fraccion).toBeDefined()
    expect(validarFormulario('PAGO_PARCIAL', 1, { fraccion: '0.4', diasAtraso: '0' })).toEqual({})
    expect(validarFormulario('PAGO_VALOR', 1, { valor: '10', diasAtraso: '-1' }).diasAtraso).toBeDefined()
  })
})

describe('descripciones y orden', () => {
  it('describe en español con pesos y porcentajes formateados', () => {
    expect(describir({ periodo: 42, tipo: 'ABONO_EXTRA', valor: '1500000', modalidad: 'MENOR_PLAZO' }).replaceAll(' ', ' ')).toBe('Abono extra de $ 1.500.000 · Menor plazo')
    expect(describir({ periodo: 30, tipo: 'CAMBIO_TASA', nuevaTasaEA: '0.7', modalidad: 'MISMA_CUOTA' }).replaceAll(' ', ' ')).toBe('Cambio de tasa a 70,00 % EA · Misma cuota')
    expect(describir({ periodo: 40, tipo: 'SIN_PAGO', causaMoraYServicios: true })).toContain('se cobran mora')
  })
  it('ordena por periodo sin mutar la lista original', () => {
    const lista: Instruccion[] = [{ periodo: 5, tipo: 'SIN_PAGO' }, { periodo: 2, tipo: 'PAGO_AL_DIA' }]
    expect(ordenarInstrucciones(lista).map((i) => i.periodo)).toEqual([2, 5])
    expect(lista[0].periodo).toBe(5)
  })
  it('clasifica los eventos de la línea de tiempo', () => {
    expect(categoria('SIN_PAGO')).toBe('sin-pago')
    expect(categoria('ABONO_EXTRA')).toBe('abono')
    expect(categoria('USURA_VIGENTE')).toBe('tasa')
    expect(categoria('CIERRE_POR_RECUPERACION')).toBe('dacion')
  })
  it('agrupa semanas consecutivas iguales en un solo evento', () => {
    const e = agruparEventos([
      { periodo: 41, tipo: 'SIN_PAGO', causaMoraYServicios: true },
      { periodo: 40, tipo: 'SIN_PAGO', causaMoraYServicios: true },
      { periodo: 42, tipo: 'ABONO_EXTRA', valor: '1500000', modalidad: 'MENOR_PLAZO' },
    ])
    expect(e).toHaveLength(2)
    expect(e[0]).toMatchObject({ desde: 40, hasta: 41, categoria: 'sin-pago' })
    expect(e[1]).toMatchObject({ desde: 42, hasta: 42, categoria: 'abono' })
  })
})
