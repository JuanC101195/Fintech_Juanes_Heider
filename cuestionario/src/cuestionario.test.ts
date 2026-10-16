import ExcelJS from 'exceljs'
import { describe, expect, it } from 'vitest'
import { columnasDe } from './Comparador'
import { previsualizacion } from './escenarios'
import { ENCABEZADOS, construirLibro } from './exportar'
import { cop } from './formato'
import { BLOQUES, PREGUNTAS, RESPONDIDAS, TODAS } from './preguntas'
import { ajustesDe, avance, estaCompleta, type Estado } from './respuestas'

describe('preguntas', () => {
  it('tienen identificadores únicos y bloques válidos', () => {
    const ids = TODAS.map((p) => p.id)
    expect(new Set(ids).size).toBe(ids.length)
    for (const p of TODAS) expect(BLOQUES).toContain(p.bloque)
  })

  it('las respondidas por negocio salen del cuestionario y ningún bloque queda vacío', () => {
    expect(RESPONDIDAS.length + PREGUNTAS.length).toBe(TODAS.length)
    expect(RESPONDIDAS.map((p) => p.id)).toEqual(expect.arrayContaining(['P1', 'P7', 'A1', 'A2', 'C4']))
    for (const p of PREGUNTAS) expect(p.respondida, p.id).toBeUndefined()
    for (const p of RESPONDIDAS) expect(p.respondida!.referencia, p.id).toMatch(/^P-\d{2}/)
    for (const b of BLOQUES) expect(PREGUNTAS.some((p) => p.bloque === b), b).toBe(true)
  })

  it('cada pregunta tiene opciones con códigos únicos', () => {
    for (const p of PREGUNTAS) {
      const codigos = p.opciones.map((o) => o.codigo)
      expect(new Set(codigos).size, p.id).toBe(codigos.length)
      expect(codigos.length, p.id).toBeGreaterThanOrEqual(2)
    }
  })

  it('las opciones que piden valor tienen campo de valor', () => {
    for (const p of PREGUNTAS) {
      if (p.opciones.some((o) => o.pideValor)) expect(p.valor, p.id).toBeDefined()
    }
  })

  it('toda previsualización existe en los escenarios que calculó el motor', () => {
    for (const p of TODAS.filter((x) => x.previsualizacion)) {
      const prev = previsualizacion(p.previsualizacion)
      expect(prev, p.id).toBeDefined()
      for (const o of p.opciones.filter((x) => x.escenario)) expect(prev!.opciones[o.escenario!], `${p.id}/${o.codigo}`).toBeDefined()
      for (const s of p.valor?.sugeridos ?? []) if (s.escenario) expect(prev!.opciones[s.escenario], `${p.id}/${s.valor}`).toBeDefined()
    }
  })

  it('la opción del Excel aparece en el comparador de cada pregunta con previsualización', () => {
    for (const p of TODAS.filter((x) => x.previsualizacion && x.opciones.some((o) => o.esExcel))) {
      const columnas = columnasDe(p, previsualizacion(p.previsualizacion)!)
      expect(columnas.some((c) => c.esExcel), p.id).toBe(true)
    }
  })
})

describe('respuestas', () => {
  const tope = PREGUNTAS.find((p) => p.id === 'PTOPE')!

  it('una respuesta con valor pendiente no está completa', () => {
    expect(estaCompleta(tope, { codigo: 'con-tope' })).toBe(false)
    expect(estaCompleta(tope, { codigo: 'con-tope', valor: 20000 })).toBe(true)
    expect(estaCompleta(tope, { codigo: 'otra' })).toBe(false)
  })

  it('los ajustes incluyen el valor digitado', () => {
    expect(ajustesDe(tope, { codigo: 'con-tope', valor: 15000 })).toEqual({ topeCobranzaPorPago: '15000' })
    expect(ajustesDe(TODAS.find((p) => p.id === 'P1')!, { codigo: 'capital-antes' })).toEqual({ prelacion: 'CAPITAL_ANTES_DE_SERVICIOS' })
  })

  it('cuenta el avance', () => {
    expect(avance({ PCOB: { codigo: 'siete-por-semana' }, PTOPE: { codigo: 'con-tope' } }).completas).toBe(1)
    // Lo que ya respondió negocio no cuenta en el avance.
    expect(avance({ P1: { codigo: 'servicios-antes' } }).completas).toBe(0)
  })
})

describe('formato', () => {
  it('muestra pesos con separador de miles y sin decimales', () => {
    expect(cop(0)).toBe('$0')
    expect(cop(999)).toBe('$999')
    expect(cop(1000)).toBe('$1.000')
    expect(cop(111014.82)).toBe('$111.015')
    expect(cop(-5904400)).toBe('-$5.904.400')
  })
})

describe('exportación a Excel', () => {
  it('genera la hoja Respuestas que se puede volver a leer', async () => {
    const estado: Estado = {
      respondente: { nombre: 'Diego González', cargo: 'Dueño de producto' },
      respuestas: {
        PDIAS: { codigo: 'consolidado', comentario: 'Así lo hacemos hoy' },
        PTOPE: { codigo: 'con-tope', valor: 20000 },
        R1: { codigo: 'tramite', texto: 'La dación es voluntaria' },
      },
    }
    const libro = await construirLibro(estado, new Date('2026-10-08T10:00:00'))
    const buffer = await libro.xlsx.writeBuffer()
    const leido = new ExcelJS.Workbook()
    await leido.xlsx.load(buffer as ArrayBuffer)

    const hoja = leido.getWorksheet('Respuestas')!
    expect(hoja.getRow(1).values).toEqual([undefined, ...ENCABEZADOS])
    expect(hoja.rowCount).toBe(PREGUNTAS.length + 1)

    const fila = (id: string) => {
      let encontrada: ExcelJS.Row | undefined
      hoja.eachRow((r) => {
        if (r.getCell(1).value === id) encontrada = r
      })
      return encontrada!
    }
    expect(fila('PDIAS').getCell(4).value).toBe('consolidado')
    expect(fila('PDIAS').getCell(9).value).toBe('Así lo hacemos hoy')
    // Las ya respondidas no van en la hoja.
    expect(fila('P1')).toBeUndefined()
    expect(fila('PTOPE').getCell(6).value).toBe(20000)
    expect(fila('PTOPE').getCell(10).value).toBe('topeCobranzaPorPago=20000')
    expect(fila('R1').getCell(8).value).toBe('La dación es voluntaria')
    expect(fila('T1').getCell(11).value).toBe('No')

    const efecto = leido.getWorksheet('Efecto de lo elegido')!
    expect(efecto.rowCount).toBe(2)
  })
})
