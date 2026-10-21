import { readdirSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

// El <select> nativo abre en Windows una lista con letra diminuta que no se arregla con CSS. En la consola se usan
// botones segmentados o tarjetas (hasta 4 opciones) y el Selector propio (5 o más). Esta prueba evita que vuelva.
const src = path.dirname(fileURLToPath(import.meta.url))
const PATRON = new RegExp('<' + 'select[\\s>/]|createElement\\([\'"]' + 'select', 'i')

function archivos(dir: string): string[] {
  return readdirSync(dir).flatMap((n) => {
    const ruta = path.join(dir, n)
    if (statSync(ruta).isDirectory()) return n === 'fixtures' ? [] : archivos(ruta)
    return /\.(tsx?|jsx?|html)$/.test(n) ? [ruta] : []
  })
}

describe('sin <select> nativo', () => {
  it('ningún archivo de consola/src usa <select>', () => {
    const lista = archivos(src).filter((f) => f !== fileURLToPath(import.meta.url))
    expect(PATRON.test('<' + 'select className="campo" value={x}>')).toBe(true)
    expect(lista.length).toBeGreaterThan(10)
    // Los comentarios pueden nombrarlo (para explicar por qué no se usa); el código no.
    const sinComentarios = (t: string) => t.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')
    const conSelect = lista.filter((f) => PATRON.test(sinComentarios(readFileSync(f, 'utf8')))).map((f) => path.relative(src, f))
    expect(conSelect).toEqual([])
  })
})
