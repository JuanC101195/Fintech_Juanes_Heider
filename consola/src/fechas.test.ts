import { describe, expect, it } from 'vitest'
import { diasEntre, fechaLarga, fechasDelCredito, leerFecha, validarFechas } from './fechas'

const f = (fechaDesembolso: string, primerVencimiento: string) => ({ fechaDesembolso, primerVencimiento })

describe('fechas del crédito (P5-PRORRATEO), mismas reglas que el API', () => {
  it('sin fechas es válido y no manda nada', () => {
    expect(validarFechas('MENSUAL', f('', ''))).toEqual({})
    expect(fechasDelCredito(f('', ''))).toEqual({})
    expect(fechasDelCredito(f('2026-10-23', ''))).toEqual({})
  })

  it('van juntas', () => {
    expect(validarFechas('MENSUAL', f('2026-10-23', ''))).toHaveProperty('primerVencimiento')
    expect(validarFechas('MENSUAL', f('', '2026-12-05'))).toHaveProperty('fechaDesembolso')
  })

  it('rechaza fechas que no existen y un vencimiento que no es posterior al desembolso', () => {
    expect(leerFecha('2026-02-30')).toBeNull()
    expect(leerFecha('23/10/2026')).toBeNull()
    expect(validarFechas('MENSUAL', f('2026-02-30', '2026-12-05'))).toHaveProperty('fechaDesembolso')
    expect(validarFechas('MENSUAL', f('2026-12-05', '2026-12-05')).primerVencimiento).toMatch(/posterior/)
  })

  it('semanal: martes; quincenal: 5 o 20; mensual: 5', () => {
    expect(validarFechas('SEMANAL', f('2026-10-23', '2026-11-03'))).toEqual({})
    expect(validarFechas('SEMANAL', f('2026-10-23', '2026-11-04')).primerVencimiento).toMatch(/martes; esa fecha es miércoles/)
    expect(validarFechas('QUINCENAL', f('2026-10-23', '2026-11-05'))).toEqual({})
    expect(validarFechas('QUINCENAL', f('2026-10-23', '2026-11-20'))).toEqual({})
    expect(validarFechas('QUINCENAL', f('2026-10-23', '2026-11-15')).primerVencimiento).toMatch(/5 y el 20/)
    expect(validarFechas('MENSUAL', f('2026-10-23', '2026-12-05'))).toEqual({})
    expect(validarFechas('MENSUAL', f('2026-10-23', '2026-11-06')).primerVencimiento).toMatch(/día 5/)
  })

  it('cuenta los días reales y escribe la fecha en español sin depender de la zona horaria', () => {
    expect(diasEntre('2026-10-23', '2026-12-05')).toBe(43)
    expect(diasEntre('2026-10-23', '2026-11-03')).toBe(11)
    expect(fechaLarga('2026-12-05')).toBe('5 de diciembre de 2026')
    expect(fechasDelCredito(f(' 2026-10-23 ', '2026-12-05'))).toEqual({ fechaDesembolso: '2026-10-23', primerVencimiento: '2026-12-05' })
  })
})
