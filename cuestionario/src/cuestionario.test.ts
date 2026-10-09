import ExcelJS from 'exceljs'
import { describe, expect, it } from 'vitest'
import { columnasDe } from './Comparador'
import { previsualizacion } from './escenarios'
import { ENCABEZADOS, construirLibro } from './exportar'
import { BLOQUES, PREGUNTAS } from './preguntas'
import { ajustesDe, avance, estaCompleta, type Estado } from './respuestas'

describe('preguntas', () => {
  it('tienen identificadores únicos y bloques válidos', () => {
    const ids = PREGUNTAS.map((p) => p.id)
    expect(new Set(ids).size).toBe(ids.length)
    for (const p of PREGUNTAS) expect(BLOQUES).toContain(p.bloque)
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
    for (const p of PREGUNTAS.filter((x) => x.previsualizacion)) {
      const prev = previsualizacion(p.previsualizacion)
      expect(prev, p.id).toBeDefined()
      for (const o of p.opciones.filter((x) => x.escenario)) expect(prev!.opciones[o.escenario!], `${p.id}/${o.codigo}`).toBeDefined()
      for (const s of p.valor?.sugeridos ?? []) if (s.escenario) expect(prev!.opciones[s.escenario], `${p.id}/${s.valor}`).toBeDefined()
    }
  })

  it('la opción del Excel aparece en el comparador de cada pregunta con previsualización', () => {
    for (const p of PREGUNTAS.filter((x) => x.previsualizacion && x.opciones.some((o) => o.esExcel))) {
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
    expect(ajustesDe(PREGUNTAS.find((p) => p.id === 'P1')!, { codigo: 'capital-antes' })).toEqual({ prelacion: 'CAPITAL_ANTES_DE_SERVICIOS' })
  })

  it('cuenta el avance', () => {
    expect(avance({ P1: { codigo: 'servicios-antes' }, PTOPE: { codigo: 'con-tope' } }).completas).toBe(1)
  })
})

describe('exportación a Excel', () => {
  it('genera la hoja Respuestas que se puede volver a leer', async () => {
    const estado: Estado = {
      respondente: { nombre: 'Diego González', cargo: 'Dueño de producto' },
      respuestas: {
        P1: { codigo: 'capital-antes', comentario: 'Así lo hacemos hoy' },
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
    expect(fila('P1').getCell(4).value).toBe('capital-antes')
    expect(fila('P1').getCell(9).value).toBe('Así lo hacemos hoy')
    expect(fila('P1').getCell(10).value).toBe('prelacion=CAPITAL_ANTES_DE_SERVICIOS')
    expect(fila('PTOPE').getCell(6).value).toBe(20000)
    expect(fila('PTOPE').getCell(10).value).toBe('topeCobranzaPorPago=20000')
    expect(fila('R1').getCell(8).value).toBe('La dación es voluntaria')
    expect(fila('P3').getCell(11).value).toBe('No')

    const efecto = leido.getWorksheet('Efecto de lo elegido')!
    expect(efecto.rowCount).toBe(3)
  })
})
