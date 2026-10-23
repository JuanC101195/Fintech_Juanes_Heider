// Prueba de humo del cuestionario compilado (dist/index.html) en Chromium:
// celular y escritorio sin desbordes, respuestas y descarga del Excel leído de vuelta.
// Usa el Playwright del front de Prestaya o, en CI, el que indique PLAYWRIGHT_PACKAGE_JSON (package.json de la carpeta donde se instaló @playwright/test). Uso: node pruebas/humo.mjs <carpeta-capturas>
import { mkdirSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const aqui = path.dirname(fileURLToPath(import.meta.url))
const raiz = path.resolve(aqui, '..')
const { chromium } = createRequire(process.env.PLAYWRIGHT_PACKAGE_JSON ?? 'C:/Users/LeNoVo/Desktop/Fintech/app/package.json')('@playwright/test')

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
  // La función de la nube se simula: se guarda lo que la página manda en vez de llamar a Azure.
  const enviados = []
  await contexto.route(/fn-motor-cartera-respuestas\.azurewebsites\.net/, (r) => {
    enviados.push(JSON.parse(r.request().postData()))
    r.fulfill({ status: 204, headers: { 'Access-Control-Allow-Origin': '*' } })
  })
  await contexto.route((url) => !url.protocol.startsWith('file') && !url.hostname.endsWith('azurewebsites.net'), (r) => r.abort())
  const page = await contexto.newPage()
  const errores = []
  page.on('pageerror', (e) => errores.push(String(e)))
  await page.goto(archivo)
  await page.getByLabel('Nombre de quien responde').fill('Diego González')
  await page.getByRole('radio', { name: /Respondo en el comentario/ }).first().check()
  await page.getByLabel('Comentario de la pregunta P5-PRORRATEO', { exact: true }).fill('Solo el interés por días reales')
  await page.getByRole('button', { name: /Abonos y prepago →/ }).click()
  await page.getByRole('radio', { name: /Es abono extra a capital/ }).check()
  const p = await problemas(page)
  console.log(`${nombre}: problemas de diseño ${JSON.stringify(p)} · errores JS ${errores.length}`)
  fallas += p.length + errores.length
  await page.screenshot({ path: path.join(salida, `cuestionario-${nombre}.png`), fullPage: true })

  if (nombre === 'escritorio') {
    // Una pregunta con solo comentario, sin escoger opción, también se guarda como completa.
    await page.getByRole('button', { name: /Cambio de tasa y usura →/ }).click()
    await page.getByText('Agregar un comentario').first().click()
    await page.getByLabel('Comentario de la pregunta P5-USURA-DIA').fill('Un día hábil, como dije en la ronda 4')
    await page.screenshot({ path: path.join(salida, 'cuestionario-comentario.png'), fullPage: true })
    // La ronda 6 (R6-*) aparece en su bloque y se puede responder.
    await page.getByRole('button', { name: /Dación y retoma →/ }).click()
    await page.getByRole('radio', { name: /el caso 11 se ajusta a 2 cuotas atrasadas/ }).check()
    await page.getByRole('button', { name: 'Enviar respuestas', exact: true }).click()
    await page.getByText(/¡Recibidas!/).waitFor({ timeout: 10000 })
    const ultimo = enviados.at(-1)
    const filas = JSON.parse(ultimo.filas).filter((f) => f[3]).map((f) => [f[0], f[3], f[5], f[9]].join(' | '))
    console.log(`Guardados en la nube: ${enviados.length} (último final=${ultimo.final}, ${ultimo.completas} de ${ultimo.total})`)
    console.log(filas.join('\n'))
    if (filas.length !== 3 || ultimo.completas !== 4 || !ultimo.final) fallas++
  }
  await contexto.close()
}
await navegador.close()
process.exit(fallas ? 1 : 0)
