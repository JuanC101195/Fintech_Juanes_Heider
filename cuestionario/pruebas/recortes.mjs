// Recortes de pantalla del cuestionario en celular para revisión visual.
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const { chromium } = createRequire('C:/Users/LeNoVo/Desktop/Fintech/app/package.json')('@playwright/test')
const b = await chromium.launch()
const pg = await b.newPage({ viewport: { width: 360, height: 780 }, deviceScaleFactor: 2 })
await pg.route((u) => !u.protocol.startsWith('file'), (r) => r.abort())
await pg.goto(pathToFileURL(path.join(raiz, 'dist', 'index.html')).href)
await pg.getByRole('radio', { name: /capital de la cuota → servicios/ }).check()
const salida = path.join(raiz, 'pruebas', 'capturas')
await pg.screenshot({ path: path.join(salida, 'r1-inicio.png') })
const p1 = pg.locator('#pregunta-P1')
await p1.screenshot({ path: path.join(salida, 'r2-p1.png') })
await pg.locator('#pregunta-PTOPE').screenshot({ path: path.join(salida, 'r3-ptope.png') })
await b.close()
