import { describe, expect, it } from 'vitest'
import { aMicros, comparar, cop, copCentavos, decimalEs, deMicros, esCero, esDecimal, porcentaje, restar, sumar } from './dinero'

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

  it('muestra centavos con coma cuando existen', () => {
    expect(sinEspeciales(cop('3169909.34'))).toBe('$ 3.169.909,34')
    expect(sinEspeciales(cop('1182645.20'))).toBe('$ 1.182.645,20')
    expect(sinEspeciales(copCentavos('219093'))).toBe('$ 219.093,00')
  })

  it('redondea desde el string, sin pasar por number (317354.805 → ,81)', () => {
    expect(sinEspeciales(cop('317354.805'))).toBe('$ 317.354,81')
    expect(sinEspeciales(cop('1182645.195'))).toBe('$ 1.182.645,20')
  })

  it('no pierde precisión en cifras que un double no representa', () => {
    expect(sinEspeciales(cop('9007199254740993'))).toBe('$ 9.007.199.254.740.993')
    expect(sinEspeciales(cop('12345678901234567.89'))).toBe('$ 12.345.678.901.234.567,89')
  })

  it('formatea negativos y valores vacíos', () => {
    expect(sinEspeciales(cop('-1500.5'))).toContain('1.500,50')
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
