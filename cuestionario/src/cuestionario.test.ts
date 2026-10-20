import { readFileSync } from 'node:fs'
import ExcelJS from 'exceljs'
import { describe, expect, it, vi } from 'vitest'
import { columnasDe } from './Comparador'
import { previsualizacion } from './escenarios'
import { ENCABEZADOS, construirLibro } from './exportar'
import { cop } from './formato'
import { envioDe, guardarEnLaNube } from './nube'
import { BLOQUES, BLOQUES_ABIERTOS, CODIGO_PENDIENTE, completitudPorBloque, PREGUNTAS, RESPONDIDAS, TODAS } from './preguntas'
import { ajustesDe, avance, estaCompleta, type Estado } from './respuestas'

describe('preguntas', () => {
  it('tienen identificadores únicos y bloques válidos', () => {
    const ids = TODAS.map((p) => p.id)
    expect(new Set(ids).size).toBe(ids.length)
    for (const p of TODAS) expect(BLOQUES).toContain(p.bloque)
  })

  it('las respondidas por negocio salen del cuestionario y ningún bloque queda vacío', () => {
    expect(RESPONDIDAS.length + PREGUNTAS.length).toBe(TODAS.length)
    expect(RESPONDIDAS.map((p) => p.id)).toEqual(expect.arrayContaining(['P1', 'P7', 'A1', 'A2', 'C4', 'PTOPE', 'S3', 'X4']))
    for (const p of PREGUNTAS) expect(p.respondida, p.id).toBeUndefined()
    for (const p of RESPONDIDAS) expect(p.respondida!.referencia, p.id).toMatch(/^(P-\d{2}|Excel|Respuestas 20 oct)/)
    for (const b of BLOQUES_ABIERTOS) expect(PREGUNTAS.some((p) => p.bloque === b), b).toBe(true)
    expect(BLOQUES_ABIERTOS.every((b) => (BLOQUES as readonly string[]).includes(b))).toBe(true)
  })

  it('quedan abiertas las que Diego dejó indefinidas el 20 de octubre y las de la ronda 3', () => {
    const ronda3 = PREGUNTAS.filter((p) => /^(COB|CAL|SRV|ABN|USU|REC|CON|ROL)-\d+$/.test(p.id))
    expect(ronda3).toHaveLength(44)
    expect(PREGUNTAS.filter((p) => !ronda3.includes(p)).map((p) => p.id).sort()).toEqual(['A3', 'C5', 'PPER', ...Array.from({ length: 10 }, (_, i) => `PPER-${i + 1}`), 'R2', 'T5'].sort())
    // Ninguna pregunta nueva obliga a escoger una opción de relleno: todas ofrecen al menos 2 opciones reales.
    for (const p of ronda3) expect(p.opciones.filter((o) => o.codigo !== CODIGO_PENDIENTE).length, p.id).toBeGreaterThanOrEqual(2)
    for (const id of ['PTOPE', 'PCOB', 'PDIAS']) {
      expect(RESPONDIDAS.find((p) => p.id === id)!.respondida!.nota).toMatch(/política de gastos de cobranza/)
    }
  })

  it('todas llevan la opción PENDIENTE, sin ajustes', () => {
    for (const p of TODAS) {
      const o = p.opciones.find((x) => x.codigo === CODIGO_PENDIENTE)
      expect(o, p.id).toBeDefined()
      expect(o!.ajustes, p.id).toBeUndefined()
      expect(o!.pideValor, p.id).toBeUndefined()
    }
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

describe('completitud', () => {
  it('cuenta las cerradas sobre el total inventariado por bloque, sin las respondidas con salvedad', () => {
    const filas = completitudPorBloque()
    expect(filas.map((f) => f.bloque)).toEqual([...BLOQUES])
    expect(filas.reduce((s, f) => s + f.total, 0)).toBe(TODAS.length)
    const cerradas = filas.reduce((s, f) => s + f.cerradas, 0)
    expect(cerradas).toBe(RESPONDIDAS.filter((p) => !p.respondida!.nota).length)
    expect(cerradas).toBeLessThan(RESPONDIDAS.length)
  })

  it('docs/completitud-reglas.md menciona cada pregunta del cuestionario', () => {
    const doc = readFileSync(new URL('../../docs/completitud-reglas.md', import.meta.url), 'utf8')
    for (const p of TODAS) expect(doc, p.id).toMatch(new RegExp(String.raw`\b${p.id}\b`))
  })
})

describe('respuestas', () => {
  const minimo = PREGUNTAS.find((p) => p.id === 'A3')!
  const inicial = PREGUNTAS.find((p) => p.id === 'C5')!

  it('una respuesta con valor pendiente no está completa', () => {
    expect(estaCompleta(minimo, { codigo: 'con-minimo' })).toBe(false)
    expect(estaCompleta(minimo, { codigo: 'con-minimo', valor: 50000 })).toBe(true)
    expect(estaCompleta(inicial, { codigo: 'otra' })).toBe(false)
  })

  it('PENDIENTE exige comentario o detalle; un comentario solo, sin opción, también cuenta', () => {
    expect(estaCompleta(minimo, { codigo: CODIGO_PENDIENTE })).toBe(false)
    expect(estaCompleta(minimo, { codigo: CODIGO_PENDIENTE, comentario: '   ' })).toBe(false)
    expect(estaCompleta(minimo, { codigo: CODIGO_PENDIENTE, comentario: 'Mínimo de 50.000' })).toBe(true)
    expect(estaCompleta(minimo, { codigo: CODIGO_PENDIENTE, texto: 'Mínimo de 50.000' })).toBe(true)
    expect(estaCompleta(minimo, { comentario: 'Mínimo de 50.000' })).toBe(true)
    expect(estaCompleta(minimo, { comentario: '' })).toBe(false)
    expect(estaCompleta(minimo, undefined)).toBe(false)
    expect(ajustesDe(minimo, { codigo: CODIGO_PENDIENTE, comentario: 'x' })).toEqual({})
  })

  it('los ajustes incluyen el valor digitado', () => {
    expect(ajustesDe(minimo, { codigo: 'con-minimo', valor: 15000 })).toEqual({ abonoMinimo: '15000' })
    expect(ajustesDe(TODAS.find((p) => p.id === 'P1')!, { codigo: 'capital-antes' })).toEqual({ prelacion: 'CAPITAL_ANTES_DE_SERVICIOS' })
  })

  it('cuenta el avance', () => {
    expect(avance({ A3: { codigo: 'sin-minimo' }, R2: { codigo: 'si' }, T5: { comentario: 'Depende de la cláusula' } }).completas).toBe(2)
    expect(avance({}).total).toBe(PREGUNTAS.length)
    // Lo que ya respondió negocio no cuenta en el avance.
    expect(avance({ P1: { codigo: 'servicios-antes' } }).completas).toBe(0)
  })
})

describe('guardado en la nube', () => {
  const estado: Estado = {
    respondente: { nombre: 'Diego González', cargo: 'Dueño de producto' },
    respuestas: { A3: { codigo: 'con-minimo', valor: 20000 }, T5: { comentario: 'Así' } },
  }

  it('guarda las mismas filas que lee el importador', () => {
    const e = envioDe(estado, true)
    expect(e.sesion).toMatch(/^[A-Za-z0-9-]{8,64}$/)
    expect(e).toMatchObject({ nombre: 'Diego González', final: true, completas: 2 })
    expect(JSON.parse(e.encabezados)).toEqual([...ENCABEZADOS])
    const filas: (string | number)[][] = JSON.parse(e.filas)
    expect(filas).toHaveLength(PREGUNTAS.length)
    expect(filas.find((f) => f[0] === 'A3')).toContain('abonoMinimo=20000')
    expect(filas.find((f) => f[0] === 'T5')).toContain('Así')
    expect(filas.find((f) => f[0] === 'T5')![10]).toBe('Sí')
  })

  it('manda la fila a la tabla y avisa si falla', async () => {
    const llamadas: [string, RequestInit][] = []
    vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
      llamadas.push([url, init])
      return new Response(null, { status: llamadas.length === 1 ? 204 : 403 })
    })
    await guardarEnLaNube(estado)
    expect(llamadas[0][0]).toBe('https://fn-motor-cartera-respuestas.azurewebsites.net/api/respuestas')
    expect(llamadas[0][1].method).toBe('POST')
    await expect(guardarEnLaNube(estado)).rejects.toThrow('403')
    vi.unstubAllGlobals()
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
        T5: { codigo: 'saldo-total', comentario: 'Así lo hacemos hoy' },
        A3: { codigo: 'con-minimo', valor: 20000 },
        C5: { codigo: 'otra', texto: 'Solo la moto' },
        R2: { codigo: CODIGO_PENDIENTE, comentario: 'Va en la política de cobranza' },
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
    expect(fila('T5').getCell(4).value).toBe('saldo-total')
    expect(fila('T5').getCell(9).value).toBe('Así lo hacemos hoy')
    // Las ya respondidas no van en la hoja.
    expect(fila('P1')).toBeUndefined()
    expect(fila('A3').getCell(6).value).toBe(20000)
    expect(fila('A3').getCell(10).value).toBe('abonoMinimo=20000')
    expect(fila('C5').getCell(8).value).toBe('Solo la moto')
    expect(fila('R2').getCell(4).value).toBe(CODIGO_PENDIENTE)
    expect(fila('R2').getCell(11).value).toBe('Sí')
    expect(fila('PPER').getCell(11).value).toBe('No')

    const efecto = leido.getWorksheet('Efecto de lo elegido')!
    expect(efecto.rowCount).toBe(1)
  })
})
