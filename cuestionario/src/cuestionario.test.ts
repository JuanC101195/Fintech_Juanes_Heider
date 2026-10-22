import { readFileSync } from 'node:fs'
import ExcelJS from 'exceljs'
import { describe, expect, it, vi } from 'vitest'
import { columnasDe } from './Comparador'
import { previsualizacion } from './escenarios'
import { ENCABEZADOS, construirLibro } from './exportar'
import { cop } from './formato'
import { envioDe, guardarEnLaNube } from './nube'
import { BLOQUES, BLOQUES_ABIERTOS, CODIGO_PENDIENTE, completitudPorBloque, MARCADOR_CIFRAS_CASO13, PREGUNTAS, RESPONDIDAS, TODAS } from './preguntas'
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
    for (const p of RESPONDIDAS) expect(p.respondida!.referencia, p.id).toMatch(/^(P-\d{2}|Excel|Respuestas 20 oct|Ronda 3|Ronda 4)/)
    for (const b of BLOQUES_ABIERTOS) expect(PREGUNTAS.some((p) => p.bloque === b), b).toBe(true)
    expect(BLOQUES_ABIERTOS.every((b) => (BLOQUES as readonly string[]).includes(b))).toBe(true)
  })

  it('la ronda 5 deja abiertas 6 confirmaciones y ninguna otra', () => {
    const ronda5 = PREGUNTAS.filter((p) => p.id.startsWith('P5-'))
    expect(ronda5.map((p) => p.id).sort()).toEqual(
      ['P5-CASO13', 'P5-COSTO-DACION', 'P5-LIMITE-DESDE', 'P5-PAGO-ANTES-PRIMERA', 'P5-PRORRATEO', 'P5-USURA-DIA'].sort(),
    )
    expect(PREGUNTAS).toHaveLength(6)
    // Todas ofrecen al menos 2 opciones reales, sin relleno.
    for (const p of ronda5) expect(p.opciones.filter((o) => o.codigo !== CODIGO_PENDIENTE).length, p.id).toBeGreaterThanOrEqual(2)
  })

  it('la ronda 4 quedó respondida el 21 de octubre, salvo T5 que sigue pendiente de Diego', () => {
    const ronda4 = TODAS.filter((p) => p.id.startsWith('R4-'))
    expect(ronda4).toHaveLength(16)
    for (const p of ronda4) {
      expect(p.respondida, p.id).toBeDefined()
      expect(p.respondida!.fecha, p.id).toBe('21 oct 2026')
      expect(p.respondida!.referencia, p.id).toBe('Ronda 4')
    }
    expect(ronda4.find((p) => p.id === 'R4-T5')!.respondida!.nota).toBe('Pendiente: Diego aún no responde')
    expect(ronda4.filter((p) => p.respondida!.nota).map((p) => p.id).sort()).toEqual(['R4-CASO13', 'R4-T5'])
    // R4-CASO13 cambió la regla: la cuenta la pregunta nueva de la ronda 5.
    expect(ronda4.find((p) => p.id === 'R4-CASO13')!.respondida!.reemplazadaPor).toBe('P5-CASO13')
  })

  it('P5-CASO13 compara el antes y el ahora y deja el marcador de las cifras del motor', () => {
    const p = PREGUNTAS.find((x) => x.id === 'P5-CASO13')!
    expect(p.contexto).toContain('1.182.645,20')
    expect(p.contexto).toContain('Antes')
    expect(p.contexto).toContain('Ahora')
    expect(p.contexto).toContain(MARCADOR_CIFRAS_CASO13)
  })

  it('las reemplazadas apuntan a una pregunta que existe y las que esperan un documento no se vuelven a preguntar', () => {
    const reemplazadas = RESPONDIDAS.filter((p) => p.respondida!.reemplazadaPor)
    expect(reemplazadas.map((p) => p.id).sort()).toEqual(['A3', 'C5', 'CON-4', 'PPER-1', 'R2', 'R4-CASO13', 'REC-6', 'ROL-1', 'T5', 'USU-3', 'USU-8'])
    for (const p of reemplazadas) expect(TODAS.map((x) => x.id), p.id).toContain(p.respondida!.reemplazadaPor)
    for (const id of [...Array.from({ length: 6 }, (_, i) => `COB-${i + 1}`), 'PPER-8', 'PPER-9', 'CON-1']) {
      expect(RESPONDIDAS.find((p) => p.id === id)!.respondida!.nota, id).toMatch(/^Pendiente: /)
    }
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
    const cerradas = filas.reduce((s, f) => s + f.cerradas, 0)
    expect(cerradas).toBe(RESPONDIDAS.filter((p) => !p.respondida!.nota).length)
    expect(cerradas).toBeLessThan(RESPONDIDAS.length)
    // Las reemplazadas por la ronda 4 o 5 no cuentan en el total: las cuenta la pregunta nueva.
    expect(filas.reduce((s, f) => s + f.total, 0)).toBe(TODAS.length - RESPONDIDAS.filter((p) => p.respondida!.reemplazadaPor).length)
  })

  it('docs/completitud-reglas.md menciona cada pregunta del cuestionario', () => {
    const doc = readFileSync(new URL('../../docs/completitud-reglas.md', import.meta.url), 'utf8')
    for (const p of TODAS) expect(doc, p.id).toMatch(new RegExp(String.raw`\b${p.id}\b`))
  })
})

describe('respuestas', () => {
  const minimo = PREGUNTAS.find((p) => p.id === 'P5-USURA-DIA')!
  const inicial = PREGUNTAS.find((p) => p.id === 'P5-PAGO-ANTES-PRIMERA')!

  it('una respuesta con valor pendiente no está completa', () => {
    expect(estaCompleta(minimo, { codigo: 'otro-plazo' })).toBe(false)
    expect(estaCompleta(minimo, { codigo: 'otro-plazo', valor: 2 })).toBe(true)
    expect(estaCompleta(inicial, undefined)).toBe(false)
    expect(estaCompleta(inicial, { codigo: 'abono-extra' })).toBe(true)
  })

  it('PENDIENTE exige comentario o detalle; un comentario solo, sin opción, también cuenta', () => {
    expect(estaCompleta(minimo, { codigo: CODIGO_PENDIENTE })).toBe(false)
    expect(estaCompleta(minimo, { codigo: CODIGO_PENDIENTE, comentario: '   ' })).toBe(false)
    expect(estaCompleta(minimo, { codigo: CODIGO_PENDIENTE, comentario: 'Dos días' })).toBe(true)
    expect(estaCompleta(minimo, { codigo: CODIGO_PENDIENTE, texto: 'Dos días' })).toBe(true)
    expect(estaCompleta(minimo, { comentario: 'Dos días' })).toBe(true)
    expect(estaCompleta(minimo, { comentario: '' })).toBe(false)
    expect(estaCompleta(minimo, undefined)).toBe(false)
    expect(ajustesDe(minimo, { codigo: CODIGO_PENDIENTE, comentario: 'x' })).toEqual({})
  })

  it('los ajustes incluyen el valor digitado cuando el campo lo declara', () => {
    expect(ajustesDe(minimo, { codigo: 'otro-plazo', valor: 3 })).toEqual({})
    expect(ajustesDe({ ...minimo, valor: { ...minimo.valor!, ajuste: 'diasUsura' } }, { codigo: 'otro-plazo', valor: 3 })).toEqual({ diasUsura: '3' })
    expect(ajustesDe(TODAS.find((p) => p.id === 'P1')!, { codigo: 'capital-antes' })).toEqual({ prelacion: 'CAPITAL_ANTES_DE_SERVICIOS' })
  })

  it('cuenta el avance', () => {
    expect(avance({ 'P5-USURA-DIA': { codigo: 'un-dia-habil' }, 'P5-COSTO-DACION': { codigo: 'financiera' }, 'P5-CASO13': { comentario: 'Depende de la cifra' }, 'R4-T5': { comentario: 'No cuenta' } }).completas).toBe(3)
    expect(avance({}).total).toBe(PREGUNTAS.length)
    // Lo que ya respondió negocio no cuenta en el avance.
    expect(avance({ P1: { codigo: 'servicios-antes' } }).completas).toBe(0)
  })
})

describe('guardado en la nube', () => {
  const estado: Estado = {
    respondente: { nombre: 'Diego González', cargo: 'Dueño de producto' },
    respuestas: { 'P5-USURA-DIA': { codigo: 'otro-plazo', valor: 3 }, 'P5-CASO13': { comentario: 'Así' } },
  }

  it('guarda las mismas filas que lee el importador', () => {
    const e = envioDe(estado, true)
    expect(e.sesion).toMatch(/^[A-Za-z0-9-]{8,64}$/)
    expect(e).toMatchObject({ nombre: 'Diego González', final: true, completas: 2 })
    expect(JSON.parse(e.encabezados)).toEqual([...ENCABEZADOS])
    const filas: (string | number)[][] = JSON.parse(e.filas)
    expect(filas).toHaveLength(PREGUNTAS.length)
    expect(filas.find((f) => f[0] === 'P5-USURA-DIA')).toContain(3)
    expect(filas.find((f) => f[0] === 'P5-CASO13')).toContain('Así')
    expect(filas.find((f) => f[0] === 'P5-CASO13')![10]).toBe('Sí')
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
        'P5-CASO13': { codigo: 'correctos', comentario: 'Así lo hacemos hoy' },
        'P5-USURA-DIA': { codigo: 'otro-plazo', valor: 3 },
        'P5-PRORRATEO': { codigo: 'otro', texto: 'Otra forma' },
        'P5-COSTO-DACION': { codigo: CODIGO_PENDIENTE, comentario: 'Va en la política de cobranza' },
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
    expect(fila('P5-CASO13').getCell(4).value).toBe('correctos')
    expect(fila('P5-CASO13').getCell(9).value).toBe('Así lo hacemos hoy')
    // Las ya respondidas no van en la hoja.
    expect(fila('P1')).toBeUndefined()
    expect(fila('R4-QUIN')).toBeUndefined()
    expect(fila('P5-USURA-DIA').getCell(6).value).toBe(3)
    expect(fila('P5-PRORRATEO').getCell(8).value).toBe('Otra forma')
    expect(fila('P5-COSTO-DACION').getCell(4).value).toBe(CODIGO_PENDIENTE)
    expect(fila('P5-COSTO-DACION').getCell(11).value).toBe('Sí')
    expect(fila('P5-LIMITE-DESDE').getCell(11).value).toBe('No')

    const efecto = leido.getWorksheet('Efecto de lo elegido')!
    expect(efecto.rowCount).toBe(1)
  })
})
