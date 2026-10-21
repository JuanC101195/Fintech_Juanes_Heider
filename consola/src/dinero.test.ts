import { describe, expect, it } from 'vitest'
import { aMicros, comparar, cop, copCentavos, decimalEs, deMicros, esCero, esDecimal, formatearMiles, fraccionAPorcentaje, leerMiles, porcentaje, porcentajeAFraccion, redondear, restar, sumar } from './dinero'

describe('entrada de dinero y porcentajes', () => {
  it('formatea con miles en vivo y vuelve al valor exacto', () => {
    expect(formatearMiles('1500000')).toBe('1.500.000')
    expect(formatearMiles('3000000.5')).toBe('3.000.000,5')
    expect(formatearMiles('')).toBe('')
    expect(leerMiles('$ 1.500.000')).toBe('1500000')
    expect(leerMiles('1.500.0001')).toBe('15000001')
    expect(leerMiles('1.500.000,50')).toBe('1500000.50')
    expect(leerMiles('1500000.50')).toBe('1500000.50')
    expect(leerMiles('007')).toBe('7')
    expect(leerMiles('abc')).toBe('')
    // ida y vuelta sin perder nada, también con decimales largos del API
    for (const v of ['5100000', '3169909.335342', '0']) expect(leerMiles(formatearMiles(v))).toBe(v)
  })
  it('convierte porcentajes y fracciones sin pasar por number', () => {
    expect(fraccionAPorcentaje('0.81')).toBe('81')
    expect(fraccionAPorcentaje('0.815')).toBe('81,5')
    expect(porcentajeAFraccion('81')).toBe('0.81')
    expect(porcentajeAFraccion('81,5 %')).toBe('0.815')
    expect(porcentajeAFraccion('40')).toBe('0.4')
    expect(porcentajeAFraccion('x')).toBeNull()
  })
})

const sinEspeciales = (s: string) => s.replaceAll(' ', ' ')

describe('formato de pesos', () => {
  it('formatea enteros con puntos de miles y sin decimales', () => {
    expect(sinEspeciales(cop('355667'))).toBe('$ 355.667')
    expect(sinEspeciales(cop('17072015.00'))).toBe('$ 17.072.015')
    expect(sinEspeciales(cop('5100000'))).toBe('$ 5.100.000')
  })

  it('agrupa también los números de cuatro cifras', () => {
    expect(sinEspeciales(cop('1500'))).toBe('$ 1.500')
  })

  it('cop redondea a pesos (P-05) sin importar cuántos decimales traiga el API', () => {
    expect(sinEspeciales(cop('355666.9755960436057213056188035084'))).toBe('$ 355.667')
    expect(sinEspeciales(cop('219092.9355960436057213056188035084'))).toBe('$ 219.093')
    expect(sinEspeciales(cop('3169909.34'))).toBe('$ 3.169.909')
    expect(sinEspeciales(cop('0.4999999999999999999999999999999999'))).toBe('$ 0')
    expect(sinEspeciales(cop('0.5'))).toBe('$ 1')
  })

  it('copCentavos muestra dos decimales con coma, redondeando solo al mostrar', () => {
    expect(sinEspeciales(copCentavos('3169909.335342596390987740224978012'))).toBe('$ 3.169.909,34')
    expect(sinEspeciales(copCentavos('1182645.20'))).toBe('$ 1.182.645,20')
    expect(sinEspeciales(copCentavos('219093'))).toBe('$ 219.093,00')
    expect(sinEspeciales(copCentavos('317354.8038047462184048812262164839'))).toBe('$ 317.354,80')
    expect(sinEspeciales(copCentavos('1499999.9999999999999999999999999999'))).toBe('$ 1.500.000,00')
  })

  it('redondea desde el string, sin pasar por number (317354.805 → ,81)', () => {
    expect(sinEspeciales(copCentavos('317354.805'))).toBe('$ 317.354,81')
    expect(sinEspeciales(copCentavos('1182645.195'))).toBe('$ 1.182.645,20')
    expect(redondear('2.675', 2)).toBe('2.68')
    expect(redondear('9.9999', 2)).toBe('10.00')
    expect(redondear('-0.001', 2)).toBe('0.00')
    expect(redondear('12', 0)).toBe('12')
  })

  it('no pierde precisión en cifras que un double no representa', () => {
    expect(sinEspeciales(cop('9007199254740993'))).toBe('$ 9.007.199.254.740.993')
    expect(sinEspeciales(copCentavos('12345678901234567.89'))).toBe('$ 12.345.678.901.234.567,89')
  })

  it('formatea negativos y valores vacíos', () => {
    expect(sinEspeciales(copCentavos('-1500.5'))).toContain('1.500,50')
    expect(cop(null)).toBe('—')
    expect(cop('abc')).toBe('—')
  })
})

describe('porcentajes y decimales', () => {
  it('convierte fracciones a porcentaje moviendo la coma', () => {
    expect(sinEspeciales(porcentaje('0.8762'))).toBe('87,62 %')
    expect(sinEspeciales(porcentaje('0.0265'))).toBe('2,65 %')
    expect(sinEspeciales(porcentaje('0.4', 0))).toBe('40 %')
    expect(porcentaje(undefined)).toBe('—')
  })
  it('usa coma decimal', () => {
    expect(decimalEs('0.4')).toBe('0,4')
  })
})

describe('aritmética decimal exacta', () => {
  it('suma sin errores de coma flotante', () => {
    expect(sumar('0.1', '0.2')).toBe('0.3')
    expect(sumar('317354.805', '1182645.195')).toBe('1500000')
    expect(sumar('219093', '136574')).toBe('355667')
  })
  it('suma con los 34 decimales del motor sin perder nada', () => {
    const a = '219092.9355960436057213056188035084'
    const b = '136574.04'
    expect(sumar(a, b)).toBe('355666.9755960436057213056188035084')
    expect(restar('355666.9755960436057213056188035084', a)).toBe(b)
    expect(sumar('0.0000000000000000000000000000000001', '0.0000000000000000000000000000000002')).toBe('0.0000000000000000000000000000000003')
    expect(comparar('1499999.9999999999999999999999999999', '1500000')).toBe(-1)
  })
  it('porcentaje y decimal aceptan cualquier cantidad de decimales', () => {
    expect(sinEspeciales(porcentaje('0.0265654190161528'))).toBe('2,66 %')
    expect(decimalEs('0.0265654190161528')).toBe('0,026565')
  })
  it('ignora vacíos al sumar', () => {
    expect(sumar('10', undefined, null, '5.5')).toBe('15.5')
  })
  it('resta y compara', () => {
    expect(restar('1500000', '317354.805')).toBe('1182645.195')
    expect(comparar('2', '10')).toBe(-1)
    expect(comparar('10.10', '10.1')).toBe(0)
    expect(comparar('11', '10.999999')).toBe(1)
  })
  it('convierte de ida y vuelta', () => {
    expect(deMicros(aMicros('0001234.500'))).toBe('1234.5')
    expect(deMicros(aMicros('-0.25'))).toBe('-0.25')
    expect(deMicros(0n)).toBe('0')
  })
  it('reconoce decimales válidos y ceros', () => {
    expect(esDecimal('12.50')).toBe(true)
    expect(esDecimal('1e5')).toBe(false)
    expect(esDecimal('1,5')).toBe(false)
    expect(esCero('0.00')).toBe(true)
    expect(esCero('0.01')).toBe(false)
    expect(() => aMicros('x')).toThrow()
  })
})
