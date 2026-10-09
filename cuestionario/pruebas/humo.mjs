// Prueba de humo del cuestionario compilado (dist/index.html) en Chromium:
// celular y escritorio sin desbordes, respuestas y descarga del Excel leído de vuelta.
// Usa el Playwright instalado en el front de Prestaya. Uso: node pruebas/humo.mjs <carpeta-capturas>
import { mkdirSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const aqui = path.dirname(fileURLToPath(import.meta.url))
const raiz = path.resolve(aqui, '..')
const { chromium } = createRequire('C:/Users/LeNoVo/Desktop/Fintech/app/package.json')('@playwright/test')
const ExcelJS = createRequire(path.join(raiz, 'package.json'))('exceljs')

const archivo = pathToFileURL(path.join(raiz, 'dist', 'index.html')).href
const salida = path.resolve(process.argv[2] ?? path.join(raiz, 'pruebas', 'capturas'))
mkdirSync(salida, { recursive: true })

const problemas = (page) =>
  page.evaluate(() => {
    const ancho = document.documentElement.clientWidth
    const lista = []
    if (document.documentElement.scrollWidth > ancho + 1) lista.push(`scroll horizontal ${document.documentElement.scrollWidth} > ${ancho}`)
    for (const el of document.body.querySelectorAll('*')) {
      const r = el.getBoundingClientRect()
      if (!r.width || !r.height) continue
      let enScroll = false
      for (let p = el.parentElement; p; p = p.parentElement) {
        if (['auto', 'scroll'].includes(getComputedStyle(p).overflowX)) enScroll = true
      }
      if (!enScroll && (r.right > ancho + 1 || r.left < -1)) lista.push(`se sale: <${el.tagName}> ${(el.textContent || '').slice(0, 40)}`)
    }
    return lista.slice(0, 10)
  })

let fallas = 0
const navegador = await chromium.launch()
for (const [nombre, ancho, alto] of [['celular', 360, 780], ['escritorio', 1280, 900]]) {
  const contexto = await navegador.newContext({ viewport: { width: ancho, height: alto }, acceptDownloads: true })
  await contexto.route((url) => !url.protocol.startsWith('file'), (r) => r.abort())
  const page = await contexto.newPage()
  const errores = []
  page.on('pageerror', (e) => errores.push(String(e)))
  await page.goto(archivo)
  await page.getByLabel('Nombre de quien responde').fill('Diego González')
  await page.getByRole('radio', { name: /capital de la cuota → servicios/ }).check()
  await page.getByRole('radio', { name: /Con tope por pago/ }).check()
  await page.getByRole('button', { name: '$20.000' }).click()
  const p = await problemas(page)
  console.log(`${nombre}: problemas de diseño ${JSON.stringify(p)} · errores JS ${errores.length}`)
  fallas += p.length + errores.length
  await page.screenshot({ path: path.join(salida, `cuestionario-${nombre}.png`), fullPage: true })

  if (nombre === 'escritorio') {
    await page.getByRole('button', { name: /Servicios complementarios →/ }).click()
    await page.getByRole('radio', { name: /Sobre el saldo de capital/ }).check()
    await page.screenshot({ path: path.join(salida, 'cuestionario-servicios.png'), fullPage: true })
    const [descarga] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Descargar Excel' }).click()])
    const destino = path.join(salida, descarga.suggestedFilename())
    await descarga.saveAs(destino)
    const libro = new ExcelJS.Workbook()
    await libro.xlsx.readFile(destino)
    const filas = []
    libro.getWorksheet('Respuestas').eachRow((r, n) => {
      if (n > 1 && r.getCell(4).value) filas.push([r.getCell(1).value, r.getCell(4).value, r.getCell(6).value, r.getCell(10).value].join(' | '))
    })
    console.log(`Excel: ${descarga.suggestedFilename()}\n${filas.join('\n')}`)
    console.log(`Efecto de lo elegido: ${libro.getWorksheet('Efecto de lo elegido').rowCount - 1} filas`)
    if (filas.length !== 3) fallas++
  }
  await contexto.close()
}
await navegador.close()
process.exit(fallas ? 1 : 0)
