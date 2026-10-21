// Piezas compartidas por humo.mjs (modo demostración) y e2e.mjs (API real): servidor de dist/, Chromium y revisión de desbordes.
// Usa el Playwright instalado en el front de Prestaya.
import { existsSync, readFileSync } from 'node:fs'
import { createServer } from 'node:http'
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const aqui = path.dirname(fileURLToPath(import.meta.url))
export const raiz = path.resolve(aqui, '..')
const dist = path.join(raiz, 'dist')

export const { chromium } = createRequire('C:/Users/LeNoVo/Desktop/Fintech/app/package.json')('@playwright/test')

const TIPOS = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml' }

/** Sirve dist/ en 127.0.0.1:puerto (0 = libre). El API solo acepta CORS desde localhost:5173, así que e2e usa ese puerto. */
export async function servirDist(puerto = 0, host = '127.0.0.1') {
  if (!existsSync(path.join(dist, 'index.html'))) throw new Error('Falta dist/: corre "npm run build" primero')
  const servidor = createServer((req, res) => {
    const ruta = new URL(req.url, 'http://x').pathname
    const archivo = path.join(dist, ruta === '/' ? 'index.html' : ruta)
    if (!archivo.startsWith(dist) || !existsSync(archivo)) {
      res.writeHead(404).end()
      return
    }
    res.writeHead(200, { 'Content-Type': TIPOS[path.extname(archivo)] ?? 'application/octet-stream' }).end(readFileSync(archivo))
  })
  await new Promise((ok, mal) => {
    servidor.once('error', mal)
    servidor.listen(puerto, host, ok)
  })
  return { servidor, origen: `http://${host}:${servidor.address().port}` }
}

/** Desbordes: scroll horizontal de la página o elementos fuera de pantalla que no estén dentro de un contenedor con scroll. */
export const problemas = (page) =>
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
