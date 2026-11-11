// Prueba de humo de docs/sitio/contratos.html en Chromium:
//  1) modo demostración (?demo=1) en celular (360 px) y escritorio: sin desbordes de página, 0 errores JS, la tabla se descarga a CSV;
//  2) consulta con la función simulada (page.route): 200, clave incorrecta (401), contrato no encontrado (404), 429;
//  1b) «Contratos activos» (demo): filtros, resumen con las dos lecturas lado a lado y tabla sin preselección sesgada;
//  1c) «Recaudo de la cartera activa» (demo): tabla mensual del año (12 filas, subtotal y total, clic en fila, filtro de año), tarjetas del mes y proyección con deslizador.
// Usa el Playwright del front de Prestaya o, en CI, el que indique PLAYWRIGHT_PACKAGE_JSON. Uso: node docs/sitio/pruebas/contratos-humo.mjs [carpeta-capturas]
import { existsSync, mkdirSync, readFileSync, statSync } from 'node:fs'
import http from 'node:http'
import { createRequire } from 'node:module'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const aqui = path.dirname(fileURLToPath(import.meta.url))
const sitio = path.resolve(aqui, '..')
// Texto esperado de la nota de multas (único lugar donde se escribe).
const NOTA_MULTAS = 'Las multas del sistema actual no siguen una regla fija; el motor aplica una cobranza de $25.000 por cada cuota atrasada (política vigente en perfil NEGOCIO).'
// package.json desde el que se resuelve @playwright/test: PLAYWRIGHT_PACKAGE_JSON, o el front de Prestaya si existe (PC de Juan Esteban),
// o el package.json de la carpeta actual (donde se instaló @playwright/test).
const localPrestaya = 'C:/Users/LeNoVo/Desktop/Fintech/app/package.json'
const paquetePlaywright = process.env.PLAYWRIGHT_PACKAGE_JSON ?? (existsSync(localPrestaya) ? localPrestaya : path.resolve('package.json'))
const { chromium } = createRequire(paquetePlaywright)('@playwright/test')
const salida = path.resolve(process.argv[2] ?? path.join(os.tmpdir(), 'contratos-humo'))
mkdirSync(salida, { recursive: true })

// Servidor http mínimo (solo 127.0.0.1, puerto libre) que sirve docs/sitio desde disco: los módulos <script type="module"> no cargan por file://.
const TIPOS = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2' }
const servidor = http.createServer((req, res) => {
  const ruta = path.resolve(sitio, '.' + path.sep + decodeURIComponent(new URL(req.url, 'http://x').pathname).replace(/^\/+/, ''))
  if (ruta !== sitio && !ruta.startsWith(sitio + path.sep)) { res.writeHead(403).end(); return }
  const archivo = existsSync(ruta) && statSync(ruta).isDirectory() ? path.join(ruta, 'index.html') : ruta
  if (!existsSync(archivo)) { res.writeHead(404).end(); return }
  res.writeHead(200, { 'Content-Type': TIPOS[path.extname(archivo)] ?? 'application/octet-stream' }).end(readFileSync(archivo))
})
await new Promise((ok) => servidor.listen(0, '127.0.0.1', ok))
const origen = `http://127.0.0.1:${servidor.address().port}`
const base = `${origen}/contratos.html`
const esLocal = (url) => url.origin === origen

// Posición de una columna (en thead) a partir del texto de su encabezado, sin números fijos.
async function columna(tabla, titulo) {
  const ths = await tabla.locator('thead th').allInnerTexts()
  const i = ths.findIndex((t) => t.split('\n')[0].trim() === titulo)
  if (i < 0) throw new Error(`No hay columna «${titulo}» (${ths.join(' | ')})`)
  return i
}

// Datos inventados para simular la función (no son de ningún contrato real).
function periodo(n, pago, extra = {}) {
  return {
    periodo: n, fechaVencimiento: `2025-02-${String(n).padStart(2, '0')}`, instruccion: 'x', valorReal: pago, diasAtraso: 0, justificado: false,
    estado: pago ? 'al_dia' : 'sin_pago', cuotasVencidas: 0, saldoInicial: 1000000 - n * 80000, interesCorriente: 10000, interesMora: 0,
    cobranza: 0, servicios: 5000, cuotaFinanciera: 100000, pagoCapital: pago ? 85000 : 0, pagoInteres: 10000, pagoMora: 0, pagoCobranza: 0,
    pagoServicios: 5000, pagoFondo: 3500, pagoSeguro: 500, pagoAsistencia: 1000, saldoEnMora: n * 1000, cxcMora: n * 100, pagoTotalCliente: pago, capitalVencido: 0, saldoFinal: 1000000 - (n + 1) * 80000, listoRecuperacion: false, tasaEA: 0.25, usuraEA: 0.26, ...extra,
  }
}
const DATOS = (() => {
  const corrida = (maxima = false) => ({
    resumen: {
      simPagado: 200000, simSaldoFinal: 800000, simPendientes: 0, simTotalConDeuda: 1000000, simMora: 0, simCobranza: 0, simInteres: 20000,
      simServicios: 10000, simCuotasVencidasMax: 1, simMarcaRecuperacion: false, simPeriodoMarca: null, simEstadoFinal: 'al_dia', realEaConMultas: 0.3,
    },
    periodos: [periodo(1, 100000), periodo(2, 0, { cuotasVencidas: 1 }), periodo(3, 100000, { justificado: true })]
      .map((p) => (maxima ? { ...p, interesCorriente: 25000, tasaEA: 0.26 } : p)),
  })
  const ep = (id, imputada, conMaxima) => ({
    id, estadoReal: 'activo', cohorte: '2025', fechaInicio: '2025-01-01', fechaFin: null, cuotas: 12, cuotaRealMiles: 100, inversion: 1000000,
    inversionImputada: imputada, lineaUsura: 'x', tasaInicialEA: 0.25, diasJustificados: 0, realPagadoCuotas: 200000, realMultas: 0,
    pagosReales: [{ fecha: '2025-01-08', valorMiles: 100, multaMiles: 0 }], corridas: { sin_justificaciones: corrida(), con_justificaciones: corrida(), ...(conMaxima ? { max_sin_justificaciones: corrida(true), max_con_justificaciones: corrida(true) } : {}) },
  })
  return { numero: '123', episodios: [ep('EP-1', false, true), ep('EP-2', true, false)] }
})()

let fallas = 0
const verificar = (ok, msg) => {
  console.log(`  ${ok ? 'OK ' : 'FALLA'} ${msg}`)
  if (!ok) fallas++
}

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

const navegador = await chromium.launch()

// Se bloquea toda la red: la página debe funcionar sola.
async function contextoSinRed(viewport) {
  const contexto = await navegador.newContext({ viewport, acceptDownloads: true })
  await contexto.route((url) => !esLocal(url), (r) => r.abort())
  return contexto
}

// ---- 1) Modo demostración ----
for (const [nombre, ancho, alto] of [['celular', 360, 800], ['escritorio', 1280, 900]]) {
  console.log(`Demo ${nombre}`)
  const contexto = await contextoSinRed({ width: ancho, height: alto })
  const page = await contexto.newPage()
  const errores = []
  page.on('pageerror', (e) => errores.push(String(e)))
  page.on('console', (m) => m.type() === 'error' && errores.push(m.text()))
  await page.goto(`${base}?demo=1`)
  await page.getByText('Datos de ejemplo, no reales').first().waitFor()
  verificar((await page.locator('tbody tr').count()) > 0, 'la tabla de amortización se dibuja')
  const amort = page.locator('table:not(.mensual)').first()
  const filas = await amort.locator('tbody tr').count()
  verificar(filas === 7, `corrida sin justificaciones: 7 periodos (hay ${filas})`)
  verificar((await page.locator('tr.listo').count()) === 1, 'una fila "listo para recuperar"')
  verificar((await page.locator('tr.sinpago').count()) === 2, 'dos filas "sin pago"')
  const dosGraficas = async () => {
    await page.locator('svg[role=img]').nth(1).waitFor()
    return page.locator('svg[role=img]').evaluateAll((l) => l.map((x) => x.querySelector('title')?.textContent))
  }
  const titulos = await dosGraficas()
  verificar(titulos.length === 2 && /Tasa efectiva anual/.test(titulos[0]) && /Interés cobrado/.test(titulos[1]), `aparecen las dos gráficas (${titulos.join(' | ')})`)
  verificar((await page.locator('svg[role=img]').first().locator('path').count()) === 2 && (await page.locator('svg[role=img]').first().locator('line[stroke-dasharray]').count()) === 1, 'la gráfica de tasa tiene líneas de tasa y usura y la punteada de la tasa real')
  verificar(/implícita/.test(await page.locator('.bloque-grafica .ayuda').first().innerText()), 'aviso de tasa implícita visible')
  const veredictoTxt = await page.locator('.veredicto').first().innerText()
  verificar(/Con el motor, lo recaudado habría sido (igual|mayor|menor)/.test(veredictoTxt) && /Deuda que el motor aún figura por cobrar \(no es dinero recaudado\)/.test(veredictoTxt) && !/la financiera habría recaudado/.test(veredictoTxt), 'veredicto: compara dinero recaudado y muestra la deuda por cobrar aparte')
  const totalMensual = () => page.locator('table.mensual tfoot td').first().innerText()
  const t1 = await totalMensual()
  verificar((await page.getByRole('group', { name: 'Tasa aplicada' }).count()) === 1, 'selector "Tasa aplicada" visible')
  await page.getByLabel('Usura máxima', { exact: true }).check()
  const t2 = await totalMensual()
  verificar(t1 !== t2, `cambiar "Tasa aplicada" cambia la tabla mensual (${t1} -> ${t2})`)
  await page.getByLabel('Usura − 1 punto', { exact: true }).check()
  await page.locator('#resultado').getByLabel('Con justificaciones', { exact: true }).check()
  await page.locator('svg[role=img]').nth(1).waitFor()
  const filas2 = await amort.locator('tbody tr').count()
  verificar(((await page.locator('svg[role=img]').count()) === 2),'sigue habiendo dos gráficas tras cambiar de corrida')
  verificar(filas2 === 12, `corrida con justificaciones: 12 periodos (hay ${filas2})`)
  verificar((await page.locator('tr.justificado').count()) === 1, 'una fila "justificado"')
  const p = await problemas(page)
  verificar(p.length === 0, `sin desbordes de página ${JSON.stringify(p)}`)
  verificar(errores.length === 0, `0 errores JS (${errores.length}) ${errores.join(' | ')}`)
  // La tabla periodo a periodo va plegada: se abre para descargar el CSV.
  await page.locator('details.amortizacion > summary').first().click()
  const [descarga] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Descargar CSV' }).first().click()])
  const bytes = readFileSync(await descarga.path())
  const conBom = bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf
  const csv = bytes.toString('utf8').replace(/^﻿/, '')
  const lineas = csv.trim().split(/\r?\n/)
  verificar(lineas.length === 13 && conBom && lineas[0].startsWith('Periodo;Vence;Fecha de pago;Pago real'), `CSV descargado (${descarga.suggestedFilename()}): ${lineas.length} líneas, cabecera correcta, con BOM UTF-8`)
  // Excel de 3 hojas: motor con tasa histórica, sistema actual (sin lógica: la hizo la empresa) y motor con tasa fija 88,44 %
  const [excel] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Descargar Excel (3 hojas)' }).click()])
  const b64 = readFileSync(await excel.path()).toString('base64')
  const x = await page.evaluate((datos) => {
    const X = window.XLSX
    const libro = X.read(datos, { type: 'base64' })
    const filas = (i) => X.utils.sheet_to_json(libro.Sheets[libro.SheetNames[i]], { header: 1 })
    const f1 = filas(0), c1 = f1[0]
    const col = (n) => c1.indexOf(n)
    return { hojas: libro.SheetNames, cab1: c1, cab2: filas(1)[0], filas1: f1.length, filas3: filas(2).length, tasa3: filas(2)[1][filas(2)[0].indexOf('Tasa EA')], fv: [f1[1][col('Vence')], f1[1][col('Fecha de pago')], f1[1][col('Días de atraso')], f1[2][col('Vence')], f1[2][col('Fecha de pago')], f1[2][col('Días de atraso')]],
      sinPago: f1.slice(1, -1).filter((f) => f[col('Pago real')] === 0).map((f) => f[col('Fecha de pago')]),
      tres: [f1[1][col('Fondo de garantías')], f1[1][col('Seguro')], f1[1][col('Asistencia')]], mora: [f1[2][col('Saldo en mora')], f1[2][col('Mora acumulada por cobrar')]],
      totalTres: [f1.at(-1)[col('Fondo de garantías')], f1.at(-1)[col('Seguro')], f1.at(-1)[col('Asistencia')]] }
  }, b64)
  verificar(x.hojas.length === 3 && /histórica/.test(x.hojas[0]) && /real/.test(x.hojas[1]) && /fija/.test(x.hojas[2]), `Excel (${excel.suggestedFilename()}): 3 hojas ${JSON.stringify(x.hojas)}`)
  verificar(x.cab1.at(-1) === 'Lógica aplicada' && !x.cab2.includes('Lógica aplicada'), 'la lógica va al final de las hojas del motor y no en la del sistema actual')
  const iServ = x.cab1.indexOf('Servicios')
  verificar(x.cab1.slice(iServ, iServ + 4).join('|') === 'Servicios|Fondo de garantías|Seguro|Asistencia' && x.cab1.includes('Saldo en mora') && x.cab1.includes('Mora acumulada por cobrar') && !x.cab2.includes('Saldo en mora'),
    `Excel: columnas de servicios abiertas en fondo, seguro y asistencia, y saldo en mora (${x.cab1.join(' | ')})`)
  verificar(x.tres.join() === '3500,500,1000' && x.totalTres[0] > 0 && x.totalTres[0] === 7 * x.totalTres[1] && x.totalTres[2] === 2 * x.totalTres[1] && x.mora.every((v) => typeof v === 'number'), `Excel: valores de las columnas nuevas ${JSON.stringify([x.tres, x.mora, x.totalTres])}`)
  verificar(x.filas1 > 2 && x.filas3 > 2 && x.tasa3 === 0.8844, `hojas del motor con datos y fila de totales (${x.filas1} y ${x.filas3} filas; tasa hoja 3 = ${x.tasa3})`)
  verificar(x.fv.join('|') === '13/01/2025|13/01/2025|0|20/01/2025|21/01/2025|1' && x.sinPago.length >= 2 && x.sinPago.every((v) => v === '—'),
    `Excel: columna «Fecha de pago» (vence + días de atraso; «—» sin pago) ${JSON.stringify([x.fv, x.sinPago])}`)
  // En pantalla: columna junto a «Vence», 1 día de atraso => un día después, y «—» en las filas sin pago.
  const ths = await amort.locator('thead th[scope=col]').allInnerTexts()
  const iV = ths.indexOf('Vence')
  verificar(iV >= 0 && ths[iV + 1] === 'Fecha de pago' && ths.includes('Días de atraso'), `encabezados: «Fecha de pago» junto a «Vence» (${ths.slice(0, 5).join(' | ')})`)
  const celdas = async (r) => amort.locator('tbody tr').nth(r).locator('th, td').allInnerTexts()
  const f2 = await celdas(1), fSin = await celdas(4)
  verificar(f2[iV] === '20/01/2025' && f2[iV + 1] === '21/01/2025' && f2[ths.indexOf('Días de atraso')] === '1', `pantalla: periodo con 1 día de atraso paga un día después (${f2.slice(0, 5).join(' | ')})`)
  verificar(fSin[iV + 1] === '—', `pantalla: sin pago muestra «—» (${fSin.slice(0, 5).join(' | ')})`)
  await page.locator('details.desglose > summary').first().click()
  const nota = await page.locator('#nota-multas').first().innerText()
  verificar(nota === NOTA_MULTAS, `nota de multas visible (${nota})`)
  await page.screenshot({ path: path.join(salida, `contratos-demo-${nombre}.png`), fullPage: true })
  await contexto.close()
}

// ---- 2) Consulta con la función simulada ----
console.log('Consulta con función simulada')
{
  const contexto = await navegador.newContext({ viewport: { width: 360, height: 800 } })
  const visto = []
  await contexto.route(/fn-motor-cartera-respuestas\.azurewebsites\.net/, (r) => {
    const req = r.request()
    const url = new URL(req.url())
    const clave = req.headers()['x-clave']
    visto.push({ numero: url.searchParams.get('numero'), clave })
    const json = (status, cuerpo) => r.fulfill({ status, contentType: 'application/json', body: JSON.stringify(cuerpo), headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'x-clave' } })
    if (req.method() === 'OPTIONS') return r.fulfill({ status: 204, headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'x-clave' } })
    if (clave !== 'buena') return json(401, { error: 'clave' })
    if (url.searchParams.get('numero') === '7') return json(404, { error: 'no_encontrado' })
    if (url.searchParams.get('numero') === '8') return json(429, { error: 'demasiadas_consultas' })
    return json(200, DATOS)
  })
  await contexto.route((url) => !esLocal(url) && !url.hostname.endsWith('azurewebsites.net'), (r) => r.abort())
  const page = await contexto.newPage()
  const errores = []
  page.on('pageerror', (e) => errores.push(String(e)))
  await page.goto(base)
  const consultar = async (clave, numero) => {
    await page.getByLabel('Clave de consulta').fill(clave)
    await page.getByLabel('Número de contrato').fill(numero)
    await page.getByLabel('Número de contrato').press('Enter')
  }
  await consultar('mala', '1001')
  await page.getByText(/La clave es incorrecta/).waitFor()
  verificar(true, '401 -> mensaje de clave incorrecta')
  await consultar('buena', '7')
  await page.getByText(/No se encontró ese número/).waitFor()
  verificar(true, '404 -> mensaje de contrato no encontrado')
  await consultar('buena', '8')
  await page.getByText(/Demasiadas consultas/).waitFor()
  verificar(true, '429 -> mensaje de demasiadas consultas')
  await consultar('buena', 'abc')
  await page.getByText(/no es válido/).waitFor()
  verificar(true, 'número inválido se rechaza en el navegador sin llamar a la función')
  const antes = visto.length
  await consultar('buena', '0123')
  await page.getByRole('heading', { name: /Contrato EP-1/ }).waitFor()
  verificar(visto.at(-1).numero === '0123' && visto.at(-1).clave === 'buena', 'la cabecera x-clave y el número viajan a la función')
  verificar(visto.length === antes + 1, 'una sola petición por consulta')
  verificar((await page.getByRole('heading', { name: /Contrato EP-/ }).count()) === 2, 'dos episodios dibujados')
  await page.locator('svg[role=img]').nth(3).waitFor()
  verificar((await page.locator('svg[role=img]').count()) === 4, 'dos gráficas por episodio')
  verificar((await page.getByRole('group', { name: 'Tasa aplicada' }).count()) === 1, 'solo el episodio con corridas de usura máxima muestra el selector de tasa')
  await page.getByLabel('Usura máxima', { exact: true }).check()
  verificar(/75.000/.test(await page.locator('table.mensual tfoot td').first().innerText()), 'con usura máxima la tabla mensual refleja el interés de esa corrida')
  verificar((await page.locator('.etiqueta', { hasText: 'imputada' }).count()) === 1, 'etiqueta "imputada" visible en el episodio 2')
  verificar(!page.url().includes('buena'), 'la clave no está en la URL')
  const guardada = await page.evaluate(() => [sessionStorage.getItem('clave-consulta'), localStorage.length])
  verificar(guardada[0] === 'buena' && guardada[1] === 0, 'clave en sessionStorage y nada en localStorage')
  const p = await problemas(page)
  verificar(p.length === 0, `sin desbordes de página ${JSON.stringify(p)}`)
  verificar(errores.length === 0, `0 errores JS (${errores.length})`)
  await page.screenshot({ path: path.join(salida, 'contratos-consulta-celular.png'), fullPage: true })
  // Sin red: mensaje claro.
  await contexto.unroute(/fn-motor-cartera-respuestas\.azurewebsites\.net/)
  await contexto.route(/fn-motor-cartera-respuestas\.azurewebsites\.net/, (r) => r.abort())
  await consultar('buena', '1001')
  await page.getByText(/No hay conexión/).waitFor()
  verificar(true, 'sin conexión -> mensaje claro')
  await contexto.close()
}

// ---- 1b) Contratos activos con tasa fija (demo) ----
for (const [nombre, ancho, alto] of [['celular', 360, 800], ['escritorio', 1280, 900]]) {
  console.log(`Activos ${nombre}`)
  const contexto = await contextoSinRed({ width: ancho, height: alto })
  const page = await contexto.newPage()
  const errores = []
  page.on('pageerror', (e) => errores.push(String(e)))
  await page.goto(`${base}?demo=1`)
  await page.getByRole('tab', { name: 'Contratos activos' }).click()
  await page.locator('#ver-activos').click()
  await page.locator('#act-resultado table.activos tbody tr').first().waitFor()
  verificar((await page.locator('#act-resultado table.activos tbody tr').count()) === 3, 'activos: 3 contratos de ejemplo')
  // Sin sesgo: el resumen trae las dos lecturas lado a lado y la tabla arranca en «Sin justificaciones» con rótulo visible.
  const lecturas = await page.locator('#act-resultado .lecturas .lectura h3').allInnerTexts()
  verificar(lecturas.length === 2 && /^Sin justificaciones/i.test(lecturas[0]) && /^Con justificaciones/i.test(lecturas[1]), `activos: resumen con las dos lecturas lado a lado (${lecturas.join(' | ')})`)
  verificar((await page.inputValue('#act-just')) === 'fija_sin_justificaciones' && (await page.getByLabel('Lectura de la tabla').isVisible()), 'activos: la tabla arranca en «Sin justificaciones» con el rótulo «Lectura de la tabla» visible')
  verificar(/sin justificaciones/.test(await page.locator('table.activos caption').innerText()), 'activos: el título de la tabla dice la lectura elegida')
  await page.selectOption('#act-just', 'fija_con_justificaciones')
  verificar(/con justificaciones/.test(await page.locator('table.activos caption').innerText()) && (await page.locator('#act-resultado .lecturas .lectura').count()) === 2, 'activos: cambiar la lectura de la tabla no quita ninguna de las dos lecturas del resumen')
  await page.selectOption('#act-just', 'fija_sin_justificaciones')
  await page.selectOption('#act-filtro', 'mora')
  verificar((await page.locator('#act-resultado table.activos tbody tr').count()) === 1, 'activos: el filtro de mora deja 1')
  await page.selectOption('#act-filtro', 'todos')
  await page.fill('#act-buscar', '9003')
  verificar((await page.locator('#act-resultado table.activos tbody tr').count()) === 1, 'activos: la búsqueda por contrato deja 1')
  await page.fill('#act-buscar', '')
  await page.locator('#act-resultado table.activos tbody tr').first().getByRole('button', { name: 'Ver' }).click()
  await page.locator('#resultado table').first().waitFor()
  verificar(await page.locator('#act-resultado').isHidden() && (await page.getByRole('tab', { name: 'Un contrato' }).getAttribute('aria-selected')) === 'true', 'activos: «Ver» pasa a la pestaña «Un contrato» y abre el detalle')
  const p = await problemas(page)
  verificar(p.length === 0, `activos: sin desbordes de página ${JSON.stringify(p)}`)
  verificar(errores.length === 0, `activos: 0 errores JS (${errores.length})`)
  await contexto.close()
}

// ---- 1c) Recaudo de la cartera activa (demo): proyección con deslizador y mes cerrado ----
for (const [nombre, ancho, alto] of [['celular', 360, 800], ['escritorio', 1280, 900]]) {
  console.log(`Recaudo ${nombre}`)
  const contexto = await contextoSinRed({ width: ancho, height: alto })
  const page = await contexto.newPage()
  const errores = []
  page.on('pageerror', (e) => errores.push(String(e)))
  page.on('console', (m) => m.type() === 'error' && errores.push(m.text()))
  await page.goto(`${base}?demo=1`)
  await page.getByRole('tab', { name: 'Recaudo de la cartera activa' }).click()
  verificar(await page.locator('#panel-flujo').isVisible(), 'recaudo: la pestaña se abre')
  await page.locator('#ver-flujo').click()
  await page.locator('#flujo-tabla table.anual').waitFor()
  // A) tabla mensual del año: 12 meses, subtotal de cerrados y total del año
  const filasMes = page.locator('#flujo-tabla tbody tr[data-mes]')
  verificar((await page.inputValue('#flujo-anio')) === '2026' && (await filasMes.count()) === 12, 'recaudo: la tabla abre en 2026 con 12 filas (enero a diciembre)')
  verificar((await page.locator('#flujo-tabla thead th').count()) === 11 && /Recaudo real del sistema actual\s+Pagos por carta \(liquidaciones\)/.test(await page.locator('#flujo-tabla thead').innerText()), 'recaudo: columnas de la tabla (mes + 10) con el recaudo real y, justo después, los pagos por carta')
  verificar((await page.inputValue('#flujo-tabla-lectura')) === 'sin' && (await page.locator('#flujo-resultado').getByLabel('Lectura de la tabla').isVisible()), 'recaudo: la tabla arranca en «Sin justificaciones» con el rótulo «Lectura de la tabla» visible')
  const estados = await filasMes.evaluateAll((l) => l.map((t) => t.querySelector('.est')?.textContent))
  verificar(estados.slice(0, 10).every((e) => e === 'Cerrado') && estados[10] === 'En curso' && estados[11] === 'Proyectado', `recaudo: estados Cerrado / En curso / Proyectado (${estados.join(', ')})`)
  verificar((await page.locator('#flujo-tabla tbody tr.curso').count()) === 1 && (await page.locator('#flujo-tabla tbody tr.proy').count()) === 1, 'recaudo: el mes en curso y el proyectado se distinguen visualmente')
  const aNum = (t) => Number(t.split('\n')[0].replace(/[^\d-]/g, ''))
  // Columnas por encabezado; en tbody/tfoot la primera celda es el rótulo (th), así que el índice de td es el de la columna menos 1.
  const tablaFlujo = page.locator('#flujo-tabla')
  const iTotal = await columna(tablaFlujo, 'Total del motor'), iCarta = await columna(tablaFlujo, 'Pagos por carta (liquidaciones)')
  const iCapital = await columna(tablaFlujo, 'Capital'), iIntereses = await columna(tablaFlujo, 'Intereses')
  const colTotal = await filasMes.evaluateAll((l, i) => l.map((t) => t.cells[i].innerText.split('\n')[0]), iTotal)
  const sumaCol = colTotal.map(aNum).reduce((a, b) => a + b, 0)
  const totalAnio = aNum(await page.locator('#flujo-tabla tfoot tr.total-anio td').nth(iTotal - 1).innerText())
  verificar(totalAnio === sumaCol && sumaCol > 0, `recaudo: la fila de total del año suma los 12 meses (${totalAnio})`)
  const cerr = colTotal.slice(0, 10).map(aNum).reduce((a, b) => a + b, 0)
  verificar(aNum(await page.locator('#flujo-tabla tfoot tr.sub-cerrados td').nth(iTotal - 1).innerText()) === cerr && /Solo meses cerrados/.test(await page.locator('#flujo-tabla tfoot tr.sub-cerrados th').innerText()), 'recaudo: subtotal «Solo meses cerrados» suma solo los 10 meses cerrados')
  verificar(/%/.test(await filasMes.first().locator('td').nth(iCapital - 1).innerText()) && /%/.test(await filasMes.first().locator('td').nth(iIntereses - 1).innerText()), 'recaudo: capital e intereses muestran su % del total del motor')
  // columna de pagos por carta: valor + «N pagos»; en curso y proyectado «—»; los totales suman solo cerrados
  const colCarta = await filasMes.evaluateAll((l, i) => l.map((t) => t.cells[i].innerText), iCarta)
  verificar(colCarta.slice(0, 10).every((t) => /\d/.test(t.split('\n')[0]) && /\d+ pagos?/.test(t)) && colCarta[10].trim() === '—' && colCarta[11].trim() === '—', `recaudo: columna de pagos por carta con «N pagos» en cerrados y «—» en el resto (${colCarta[0].replace(/\n/g, ' ')})`)
  const sumaCarta = colCarta.slice(0, 10).map(aNum).reduce((a, b) => a + b, 0)
  verificar(sumaCarta > 0 && aNum(await page.locator('#flujo-tabla tfoot tr.sub-cerrados td').nth(iCarta - 1).innerText()) === sumaCarta && aNum(await page.locator('#flujo-tabla tfoot tr.total-anio td').nth(iCarta - 1).innerText()) === sumaCarta, `recaudo: «Solo meses cerrados» y «Total del año» suman la columna de carta (${sumaCarta})`)
  // B) filtro por mes y tarjetas
  verificar((await page.inputValue('#flujo-mes')) === '2026-10' && /octubre de 2026/.test(await page.locator('#flujo-t-mes').innerText()), 'recaudo: el mes por defecto es octubre de 2026 (último cerrado)')
  const tarjetas = await page.locator('#flujo-tarjetas .t-mes .t-rot').allInnerTexts()
  verificar(tarjetas.length === 7 && /Capital recaudado/.test(tarjetas[0]) && /Intereses recaudados/.test(tarjetas[1]) && /Mora y cobranza/.test(tarjetas[2]) && /Servicios/.test(tarjetas[3]) && /Total del motor/.test(tarjetas[4]) && /Recaudo real/.test(tarjetas[5]) && /Pagos por carta \(liquidaciones\)/.test(tarjetas[6]), `recaudo: 7 tarjetas (${tarjetas.join(' | ')})`)
  verificar(/% del total del motor/.test(await page.locator('#flujo-tarjetas .t-mes.cap').innerText()), 'recaudo: cada tarjeta trae su % del total')
  const flota = await page.locator('#flujo-tarjetas .flota').innerText()
  verificar(/Motos activas al 31 de octubre de 2026: 1\.5\d\d \(foto de ese día\)/.test(flota) && /Con cuota simulada ese mes: 1\.\d{3}/.test(flota), 'recaudo: muestra la flota activa al cierre del mes y cuántas tuvieron cuota')
  const tc = page.locator('#flujo-tarjetas .t-mes.carta')
  verificar(/\d+ pagos?/.test(await tc.innerText()) && /no son cuotas y no entran al motor ni al recaudo real/.test(await tc.innerText()) && /no incluye los pagos por carta/.test(await page.locator('#flujo-tarjetas .nota-honesta').innerText()), 'recaudo: tarjeta de carta con «N pagos», ayuda y nota de que el recaudo real los excluye')
  const motivos = await tc.locator('details.desglose li strong').allTextContents()
  const vm = motivos.map(aNum)
  verificar(!(await tc.locator('details.desglose').evaluate((d) => d.open)) && vm.length >= 2 && vm.every((v, i) => i === 0 || vm[i - 1] >= v) && vm.reduce((a, b) => a + b, 0) === aNum(await tc.locator('.t-valor').innerText()), `recaudo: desglose plegado, por motivo de mayor a menor y que suma la tarjeta (${motivos.join(' | ')})`)
  await tc.locator('summary').click()
  verificar(/^Carta /.test(await tc.locator('details.desglose li').first().innerText()), 'recaudo: motivos con nombre legible')
  const p0 = await problemas(page)
  verificar(p0.length === 0, `recaudo: sin desbordes con el desglose abierto ${JSON.stringify(p0)}`)
  const antes = await page.locator('#flujo-tarjetas .t-mes.tot .t-valor').innerText()
  await page.locator('#flujo-tabla tbody tr[data-mes="2026-03"] .mes-btn').click()
  verificar((await page.inputValue('#flujo-mes')) === '2026-03' && /marzo de 2026/.test(await page.locator('#flujo-t-mes').innerText()) && (await page.locator('#flujo-tabla tr.elegida').count()) === 1 && (await page.locator('#flujo-tarjetas .t-mes.tot .t-valor').innerText()) !== antes, 'recaudo: hacer clic en una fila cambia el mes de las tarjetas')
  await page.locator('#flujo-tabla tbody tr[data-mes="2026-12"]').click()
  verificar(/Proyectado/.test(await page.locator('#flujo-tarjetas').innerText()) && /Sin dato/.test(await page.locator('#flujo-tarjetas .t-mes.real').innerText()), 'recaudo: un mes proyectado se rotula y no inventa recaudo real')
  verificar(/No aplica/.test(await page.locator('#flujo-tarjetas .t-mes.carta').innerText()), 'recaudo: en un mes no cerrado la tarjeta de carta dice «No aplica»')
  await page.selectOption('#flujo-mes', '2026-09')
  verificar(/septiembre de 2026/.test(await page.locator('#flujo-t-mes').innerText()) && (await page.locator('#flujo-tabla tr.elegida').getAttribute('data-mes')) === '2026-09', 'recaudo: el selector de mes actualiza las tarjetas y la fila marcada')
  const sinV = await page.locator('#flujo-tarjetas .t-mes.tot .t-valor').innerText()
  await page.selectOption('#flujo-tabla-lectura', 'con')
  verificar((await page.locator('#flujo-tarjetas .t-mes.tot .t-valor').innerText()) !== sinV && /Con justificaciones/.test(await page.locator('#flujo-tarjetas').innerText()), 'recaudo: la lectura «Con justificaciones» cambia tabla y tarjetas')
  await page.selectOption('#flujo-tabla-lectura', 'sin')
  await page.selectOption('#flujo-anio', '2025')
  verificar((await page.locator('#flujo-tabla tbody tr').count()) === 12 && (await page.locator('#flujo-tabla tbody tr.sin-datos').count()) === 4 && (await page.locator('#flujo-tabla tbody tr.curso, #flujo-tabla tbody tr.proy').count()) === 0, 'recaudo: el filtro de año muestra 2025 (4 meses sin datos y ninguno sin cerrar)')
  verificar(/2025/.test(await page.locator('#flujo-t-mes').innerText()), 'recaudo: al cambiar de año el detalle pasa a un mes de ese año')
  await page.selectOption('#flujo-anio', '2026')
  await page.selectOption('#flujo-mes', '2026-10')
  // C) proyección con deslizador
  const svgs = page.locator('#flujo-resultado svg[role=img]')
  await svgs.first().waitFor()
  verificar((await svgs.count()) === 2 && (await svgs.evaluateAll((l) => l.every((s) => s.querySelector('title') && s.querySelector('desc')))), `recaudo: 2 gráficas de proyección accesibles con title y desc, hay ${await svgs.count()}`)
  verificar((await page.locator('#flujo-zona-proy .lectura').count()) === 2 && (await page.locator('#flujo-lectura-ambas').isChecked()), 'recaudo: la proyección arranca con «Ambas» (sin y con justificaciones)')
  const cifra = () => page.locator('#flujo-zona-proy .lectura .cifra-grande').first().evaluate((e) => e.firstChild.textContent)
  verificar(/31 de diciembre de 2026/.test(await page.locator('#flujo-fin-texto').innerText()), 'recaudo: el deslizador arranca en el 31 de diciembre de 2026')
  verificar(await page.getByRole('button', { name: '31 dic 2026' }).getAttribute('aria-pressed') === 'true', 'recaudo: el atajo «31 dic 2026» aparece marcado')
  const c1 = await cifra()
  await page.getByRole('button', { name: 'Fin de todos los planes' }).click()
  const c2 = await cifra()
  verificar(c1 !== c2 && /diciembre de 2027/.test(await page.locator('#flujo-fin-texto').innerText()), `recaudo: el atajo «Fin de todos los planes» cambia el total (${c1} -> ${c2})`)
  await page.locator('#flujo-fin').fill('19')
  const c3 = await cifra()
  verificar(c3 !== c2 && /31 de diciembre de 2026/.test(await page.locator('#flujo-fin-texto').innerText()), `recaudo: mover el deslizador actualiza el total y el texto (${c3})`)
  await page.locator('#flujo-lectura-sin').check()
  await page.locator('#flujo-zona-proy svg[role=img]').first().waitFor()
  verificar((await page.locator('#flujo-zona-proy .lectura').count()) === 1 && /diciembre de 2026/.test(await page.locator('#flujo-fin-texto').innerText()), 'recaudo: la lectura «Sin justificaciones» deja un bloque y conserva el fin elegido')
  // la tabla no desborda la página: va con scroll propio y primera columna fija
  const t = await page.evaluate(() => {
    const c = document.querySelector('#flujo-tabla .tabla-scroll')
    const th = document.querySelector('#flujo-tabla tbody th')
    return { cabe: c.getBoundingClientRect().right <= document.documentElement.clientWidth + 1, scrollea: c.scrollWidth >= c.clientWidth, fija: getComputedStyle(th).position === 'sticky' }
  })
  verificar(t.cabe && t.scrollea && t.fija, `recaudo: la tabla va en su propio contenedor con la primera columna fija ${JSON.stringify(t)}`)
  if (ancho === 360) {
    await page.locator('#flujo-tabla .tabla-scroll').evaluate((e) => { e.scrollLeft = 300 })
    const fija = await page.evaluate(() => { const th = document.querySelector('#flujo-tabla tbody th').getBoundingClientRect(), c = document.querySelector('#flujo-tabla .tabla-scroll').getBoundingClientRect(); return Math.abs(th.left - c.left) < 3 })
    verificar(fija, 'recaudo: al desplazar la tabla en el celular, la columna de meses queda fija')
    await page.locator('#flujo-tabla .tabla-scroll').evaluate((e) => { e.scrollLeft = 0 })
  }
  const p = await problemas(page)
  verificar(p.length === 0, `recaudo: sin desbordes de página ${JSON.stringify(p)}`)
  verificar(errores.length === 0, `recaudo: 0 errores JS (${errores.length}) ${errores.join(' | ')}`)
  await page.screenshot({ path: path.join(salida, `contratos-recaudo-${nombre}.png`), fullPage: true })
  await contexto.close()
}

// ---- 1d) Respuesta de una función vieja (sin `carta`): «—», sin errores ----
for (const [nombre, ancho, alto] of [['celular', 360, 800], ['escritorio', 1280, 900]]) {
  console.log(`Recaudo sin carta ${nombre}`)
  const contexto = await contextoSinRed({ width: ancho, height: alto })
  const page = await contexto.newPage()
  const errores = []
  page.on('pageerror', (e) => errores.push(String(e)))
  await page.goto(`${base}?demo=1`)
  await page.evaluate(() => { const o = window.__demo.crearDemoFlujo; window.__demo.crearDemoFlujo = () => { const d = o(); d.meses.forEach((m) => delete m.carta); return d } })
  await page.getByRole('tab', { name: 'Recaudo de la cartera activa' }).click()
  await page.locator('#ver-flujo').click()
  await page.locator('#flujo-tabla table.anual').waitFor()
  const iCartaSin = await columna(page.locator('#flujo-tabla'), 'Pagos por carta (liquidaciones)')
  const col = await page.locator('#flujo-tabla tbody tr[data-mes]').evaluateAll((l, i) => l.map((t) => t.cells[i].innerText.trim()), iCartaSin)
  verificar(col.every((t) => t === '—') && /—/.test(await page.locator('#flujo-tabla tfoot tr.total-anio td').nth(iCartaSin - 1).innerText()), 'sin carta: la columna y los totales muestran «—»')
  verificar(/Sin dato/.test(await page.locator('#flujo-tarjetas .t-mes.carta').innerText()), 'sin carta: la tarjeta dice «Sin dato»')
  const p = await problemas(page)
  verificar(p.length === 0 && errores.length === 0, `sin carta: sin desbordes ni errores JS ${JSON.stringify(p)} ${errores.join(' | ')}`)
  await contexto.close()
}

// ---- 3) Dos sedes: el selector se aplica a las tres pestañas, viaja como ?sede= y se recuerda ----
for (const [nombre, ancho, alto] of [['celular', 360, 800], ['escritorio', 1280, 900]]) {
  console.log(`Sedes ${nombre}`)
  const contexto = await navegador.newContext({ viewport: { width: ancho, height: alto } })
  const visto = []
  let demoActivos
  let demoFlujo
  await contexto.route(/fn-motor-cartera-respuestas\.azurewebsites\.net/, (r) => {
    const req = r.request()
    const url = new URL(req.url())
    const cabeceras = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'x-clave' }
    if (req.method() === 'OPTIONS') return r.fulfill({ status: 204, headers: cabeceras })
    const sede = url.searchParams.get('sede')
    visto.push({ modo: ['sedes', 'activos', 'flujo', 'numero'].find((k) => url.searchParams.has(k)), sede })
    const json = (cuerpo, status = 200) => r.fulfill({ status, contentType: 'application/json', body: JSON.stringify(cuerpo), headers: cabeceras })
    if (url.searchParams.has('sedes')) return json({ sedes: ['CARTAGENA', 'MONTERIA'], todas: ['CARTAGENA', 'MONTERIA', 'SINCELEJO', 'BICIMOTOS'], defecto: 'CARTAGENA' })
    if (sede !== 'CARTAGENA' && sede !== 'MONTERIA') return json({ error: 'sede_invalida' }, 400)
    if (url.searchParams.has('activos')) return json({ sede, contratos: sede === 'CARTAGENA' ? demoActivos : demoActivos.slice(0, 1) })
    if (url.searchParams.has('flujo')) return json({ sede, ...demoFlujo })
    // Mismo número en las dos sedes, contratos distintos.
    return json({ sede, numero: '123', episodios: [{ ...DATOS.episodios[0], id: `${sede}-123-1` }] })
  })
  await contexto.route((url) => !esLocal(url) && !url.hostname.endsWith('azurewebsites.net'), (r) => r.abort())
  const page = await contexto.newPage()
  const errores = []
  page.on('pageerror', (e) => errores.push(String(e)))
  page.on('console', (m) => m.type() === 'error' && !/status of 400/.test(m.text()) && errores.push(m.text()))
  await page.goto(base)
  demoActivos = await page.evaluate(() => window.__demo.crearDemoActivos())
  demoFlujo = await page.evaluate(() => window.__demo.crearDemoFlujo())
  verificar((await page.getByLabel('Sede').inputValue()) === 'CARTAGENA', 'la sede por defecto es Cartagena')
  await page.getByLabel('Clave de consulta').fill('buena')
  await page.getByLabel('Clave de consulta').blur()
  await page.waitForFunction(() => document.getElementById('sede').options.length === 2)
  const opciones = await page.locator('#sede option').allInnerTexts()
  verificar(opciones.join() === 'Cartagena,Montería', `solo se ofrecen las sedes que la API informa (${opciones.join()})`)
  // Un contrato
  await page.getByLabel('Número de contrato').fill('123')
  await page.getByLabel('Número de contrato').press('Enter')
  await page.getByRole('heading', { name: /Contrato CARTAGENA-123-1/ }).waitFor()
  verificar(visto.at(-1).modo === 'numero' && visto.at(-1).sede === 'CARTAGENA', 'el contrato se pide con ?sede=CARTAGENA')
  // Cambiar de sede limpia lo que había en pantalla y recuerda la elección
  await page.getByLabel('Sede').selectOption('MONTERIA')
  verificar((await page.getByRole('heading', { name: /Contrato CARTAGENA-123-1/ }).count()) === 0, 'al cambiar de sede se limpia el resultado de la otra')
  verificar((await page.evaluate(() => localStorage.getItem('sede-consulta'))) === 'MONTERIA', 'la sede se recuerda en localStorage')
  await page.getByLabel('Número de contrato').press('Enter')
  await page.getByRole('heading', { name: /Contrato MONTERIA-123-1/ }).waitFor()
  verificar(visto.at(-1).sede === 'MONTERIA', 'el mismo número en otra sede se pide con ?sede=MONTERIA y trae otro contrato')
  // Activos
  await page.getByRole('tab', { name: 'Contratos activos' }).click()
  await page.locator('#ver-activos').click()
  await page.locator('table.activos').waitFor()
  verificar(visto.at(-1).modo === 'activos' && visto.at(-1).sede === 'MONTERIA' && (await page.locator('table.activos tbody tr').count()) === 1, 'activos: ?sede=MONTERIA y solo los de esa sede (1 fila)')
  // Flujo
  await page.getByRole('tab', { name: 'Recaudo de la cartera activa' }).click()
  await page.locator('#ver-flujo').click()
  await page.locator('#flujo-tabla table.anual').waitFor()
  verificar(visto.at(-1).modo === 'flujo' && visto.at(-1).sede === 'MONTERIA', 'recaudo: ?sede=MONTERIA')
  // Cambiar a Cartagena desde otra pestaña: activos se vuelve a pedir y trae las 3 filas
  await page.getByLabel('Sede').selectOption('CARTAGENA')
  verificar((await page.locator('table.activos').count()) === 0, 'cambiar de sede limpia también activos y recaudo')
  await page.getByRole('tab', { name: 'Contratos activos' }).click()
  await page.locator('#ver-activos').click()
  await page.locator('table.activos').waitFor()
  verificar((await page.locator('table.activos tbody tr').count()) === 3, 'activos de Cartagena: 3 filas')
  const p = await problemas(page)
  verificar(p.length === 0, `sin desbordes de página ${JSON.stringify(p)}`)
  await page.screenshot({ path: path.join(salida, `contratos-sedes-${nombre}.png`), fullPage: true })
  // Recargar: se acuerda de la última sede elegida
  await page.getByLabel('Sede').selectOption('MONTERIA')
  await page.reload()
  verificar((await page.getByLabel('Sede').inputValue()) === 'MONTERIA', 'tras recargar, la sede recordada sigue seleccionada')
  verificar(errores.length === 0, `0 errores JS (${errores.length}) ${errores.join(' | ')}`)
  await contexto.close()
}

// Sin datos de sedes (la API informa una lista vacía): se muestran todas con aviso.
{
  console.log('Sedes sin datos')
  const contexto = await navegador.newContext({ viewport: { width: 360, height: 800 } })
  await contexto.route(/fn-motor-cartera-respuestas\.azurewebsites\.net/, (r) => r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ sedes: [] }), headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'x-clave' } }))
  const page = await contexto.newPage()
  await page.goto(base)
  await page.getByLabel('Clave de consulta').fill('buena')
  await page.getByLabel('Clave de consulta').blur()
  await page.getByText(/sin datos/).waitFor()
  verificar((await page.locator('#sede option').count()) === 4, 'sedes vacías: se muestran las 4 con aviso «sin datos»')
  await contexto.close()
}

await navegador.close()
servidor.close()
console.log(fallas ? `\n${fallas} FALLAS` : '\nTodo en orden')
console.log(`Capturas en ${salida}`)
process.exit(fallas ? 1 : 0)

