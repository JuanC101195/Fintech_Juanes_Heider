// Prueba de humo de la consola compilada (dist/) en Chromium: celular (360 px) y escritorio.
// Recorre las 4 pantallas en modo demostración (el API en localhost:8080 se bloquea a propósito, así se
// prueba también el respaldo), y falla si hay errores de JS o desbordes horizontales.
// Usa el Playwright instalado en el front de Prestaya.
// Uso: npm run build && node pruebas/humo.mjs [carpeta-capturas]
import { mkdirSync, readFileSync, existsSync } from 'node:fs'
import { createServer } from 'node:http'
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const aqui = path.dirname(fileURLToPath(import.meta.url))
const raiz = path.resolve(aqui, '..')
const dist = path.join(raiz, 'dist')
if (!existsSync(path.join(dist, 'index.html'))) throw new Error('Falta dist/: corre "npm run build" primero')
const { chromium } = createRequire('C:/Users/LeNoVo/Desktop/Fintech/app/package.json')('@playwright/test')

const salida = path.resolve(process.argv[2] ?? path.join(raiz, 'pruebas', 'capturas'))
mkdirSync(salida, { recursive: true })

const TIPOS = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml' }
const servidor = createServer((req, res) => {
  const ruta = new URL(req.url, 'http://x').pathname
  const archivo = path.join(dist, ruta === '/' ? 'index.html' : ruta)
  if (!archivo.startsWith(dist) || !existsSync(archivo)) {
    res.writeHead(404).end()
    return
  }
  res.writeHead(200, { 'Content-Type': TIPOS[path.extname(archivo)] ?? 'application/octet-stream' }).end(readFileSync(archivo))
})
await new Promise((ok) => servidor.listen(0, '127.0.0.1', ok))
const origen = `http://127.0.0.1:${servidor.address().port}`

/** Desbordes: scroll horizontal de la página o elementos fuera de pantalla que no estén dentro de un contenedor con scroll. */
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
      if (!enScroll && (r.right > ancho + 1 || r.left < -1) && !el.closest('.sr-only')) lista.push(`se sale: <${el.tagName}> ${(el.textContent || '').slice(0, 40)}`)
    }
    return lista.slice(0, 10)
  })

let fallas = 0
const navegador = await chromium.launch()
for (const [nombre, ancho, alto] of [['celular', 360, 780], ['escritorio', 1280, 900]]) {
  for (const esquema of ['light', 'dark']) {
    if (nombre === 'celular' && esquema === 'dark') continue
    const contexto = await navegador.newContext({ viewport: { width: ancho, height: alto }, colorScheme: esquema })
    await contexto.route(/localhost:8080/, (r) => r.abort())
    const page = await contexto.newPage()
    const errores = []
    page.on('pageerror', (e) => errores.push(String(e)))
    page.on('console', (m) => m.type() === 'error' && !/Failed to load resource|ERR_FAILED|net::/.test(m.text()) && errores.push(m.text()))
    const etiqueta = `${nombre}-${esquema}`
    const revisar = async (pantalla) => {
      const p = await problemas(page)
      console.log(`${etiqueta} · ${pantalla}: desbordes ${JSON.stringify(p)}`)
      fallas += p.length
      await page.screenshot({ path: path.join(salida, `consola-${etiqueta}-${pantalla}.png`), fullPage: true })
    }
    const ir = (nombreEnlace) => page.locator('nav:visible').getByRole('link', { name: nombreEnlace }).click()

    await page.goto(origen)
    await page.getByText('Datos de demostración').first().waitFor({ timeout: 10000 })

    // 1. Simulador (quincenal por defecto, luego mensual)
    await page.getByRole('region', { name: 'Resultado de la simulación' }).waitFor()
    if (!(await page.getByText('$ 355.667').first().isVisible())) { console.log('FALTA cuota total 355.667'); fallas++ }
    await revisar('simulador')
    await page.getByRole('radio', { name: /Mensual/ }).check({ force: true })
    await page.getByRole('button', { name: 'Simular' }).click()
    await page.getByText('$ 717.154').first().waitFor()

    // 2. Casos: lista y detalle del 13
    await ir('Casos')
    await page.getByRole('link', { name: /Caso 13/ }).waitFor()
    await revisar('casos')
    await page.getByRole('link', { name: /Caso 13/ }).click()
    await page.getByRole('region', { name: 'Tabla periodo a periodo' }).waitFor()
    await page.getByText('Termina en el periodo 83').waitFor()
    await page.locator('details').first().locator('summary').click()
    await revisar('caso-13')

    // 3. Laboratorio: abrir el caso y ejecutar; luego un abono imposible
    await page.getByRole('button', { name: 'Abrir en el laboratorio' }).click()
    await page.getByRole('button', { name: /Editar la instrucción del periodo 42/ }).waitFor()
    await page.getByRole('button', { name: 'Ejecutar' }).click()
    await page.getByRole('region', { name: 'Tabla periodo a periodo' }).waitFor()
    await revisar('laboratorio')
    await page.getByLabel('Periodo', { exact: true }).fill('10')
    await page.getByLabel('Tipo de instrucción').selectOption('ABONO_EXTRA')
    await page.getByLabel('Valor del abono (COP)').fill('99000000')
    await page.getByRole('button', { name: 'Agregar instrucción' }).click()
    await page.getByRole('button', { name: 'Ejecutar' }).click()
    await page.getByRole('alert').waitFor()
    await revisar('laboratorio-error')

    // 4. Reglas
    await ir('Reglas')
    await page.getByRole('region', { name: 'Tabla de reglas por perfil' }).waitFor()
    await revisar('reglas')

    console.log(`${etiqueta}: errores JS ${errores.length} ${errores.length ? JSON.stringify(errores.slice(0, 3)) : ''}`)
    fallas += errores.length
    await contexto.close()
  }
}
await navegador.close()
servidor.close()
if (fallas) {
  console.error(`HUMO FALLÓ: ${fallas} problema(s)`)
  process.exit(1)
}
console.log('HUMO OK: 4 pantallas en celular y escritorio, sin errores JS ni desbordes')
