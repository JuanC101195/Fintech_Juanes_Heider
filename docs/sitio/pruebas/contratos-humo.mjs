// Prueba de humo de docs/sitio/contratos.html en Chromium:
//  1) modo demostración (?demo=1) en celular (390 px) y escritorio: sin desbordes de página, 0 errores JS, la tabla se descarga a CSV;
//  2) consulta con la función simulada (page.route): 200, clave incorrecta (401), contrato no encontrado (404), 429.
// Usa el Playwright del front de Prestaya o, en CI, el que indique PLAYWRIGHT_PACKAGE_JSON. Uso: node docs/sitio/pruebas/contratos-humo.mjs [carpeta-capturas]
import { mkdirSync, readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const aqui = path.dirname(fileURLToPath(import.meta.url))
const pagina = path.resolve(aqui, '..', 'contratos.html')
const { chromium } = createRequire(process.env.PLAYWRIGHT_PACKAGE_JSON ?? 'C:/Users/LeNoVo/Desktop/Fintech/app/package.json')('@playwright/test')
const salida = path.resolve(process.argv[2] ?? path.join(os.tmpdir(), 'contratos-humo'))
mkdirSync(salida, { recursive: true })
const base = pathToFileURL(pagina).href

// Datos inventados para simular la función (no son de ningún contrato real).
function periodo(n, pago, extra = {}) {
  return {
    periodo: n, fechaVencimiento: `2025-02-${String(n).padStart(2, '0')}`, instruccion: 'x', valorReal: pago, diasAtraso: 0, justificado: false,
    estado: pago ? 'al_dia' : 'sin_pago', cuotasVencidas: 0, saldoInicial: 1000000 - n * 80000, interesCorriente: 10000, interesMora: 0,
    cobranza: 0, servicios: 5000, cuotaFinanciera: 100000, pagoCapital: pago ? 85000 : 0, pagoInteres: 10000, pagoMora: 0, pagoCobranza: 0,
    pagoServicios: 5000, pagoTotalCliente: pago, capitalVencido: 0, saldoFinal: 1000000 - (n + 1) * 80000, listoRecuperacion: false, tasaEA: 0.25, ...extra,
  }
}
const DATOS = (() => {
  const corrida = () => ({
    resumen: {
      simPagado: 200000, simSaldoFinal: 800000, simPendientes: 0, simTotalConDeuda: 1000000, simMora: 0, simCobranza: 0, simInteres: 20000,
      simServicios: 10000, simCuotasVencidasMax: 1, simMarcaRecuperacion: false, simPeriodoMarca: null, simEstadoFinal: 'al_dia', realEaConMultas: 0.3,
    },
    periodos: [periodo(1, 100000), periodo(2, 0, { cuotasVencidas: 1 }), periodo(3, 100000, { justificado: true })],
  })
  const ep = (id, imputada) => ({
    id, estadoReal: 'activo', cohorte: '2025', fechaInicio: '2025-01-01', fechaFin: null, cuotas: 12, cuotaRealMiles: 100, inversion: 1000000,
    inversionImputada: imputada, lineaUsura: 'x', tasaInicialEA: 0.25, diasJustificados: 0, realPagadoCuotas: 200000, realMultas: 0,
    pagosReales: [{ fecha: '2025-01-08', valorMiles: 100, multaMiles: 0 }], corridas: { sin_justificaciones: corrida(), con_justificaciones: corrida() },
  })
  return { numero: '123', episodios: [ep('EP-1', false), ep('EP-2', true)] }
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
  await contexto.route((url) => !url.protocol.startsWith('file'), (r) => r.abort())
  return contexto
}

// ---- 1) Modo demostración ----
for (const [nombre, ancho, alto] of [['celular', 390, 800], ['escritorio', 1280, 900]]) {
  console.log(`Demo ${nombre}`)
  const contexto = await contextoSinRed({ width: ancho, height: alto })
  const page = await contexto.newPage()
  const errores = []
  page.on('pageerror', (e) => errores.push(String(e)))
  page.on('console', (m) => m.type() === 'error' && errores.push(m.text()))
  await page.goto(`${base}?demo=1`)
  await page.getByText('Datos de ejemplo, no reales').first().waitFor()
  verificar((await page.locator('tbody tr').count()) > 0, 'la tabla de amortización se dibuja')
  const filas = await page.locator('table').first().locator('tbody tr').count()
  verificar(filas === 7, `corrida sin justificaciones: 7 periodos (hay ${filas})`)
  verificar((await page.locator('tr.listo').count()) === 1, 'una fila "listo para recuperar"')
  verificar((await page.locator('tr.sinpago').count()) === 2, 'dos filas "sin pago"')
  await page.getByLabel('Con justificaciones').check()
  const filas2 = await page.locator('table').first().locator('tbody tr').count()
  verificar(filas2 === 12, `corrida con justificaciones: 12 periodos (hay ${filas2})`)
  verificar((await page.locator('tr.justificado').count()) === 1, 'una fila "justificado"')
  const p = await problemas(page)
  verificar(p.length === 0, `sin desbordes de página ${JSON.stringify(p)}`)
  verificar(errores.length === 0, `0 errores JS (${errores.length}) ${errores.join(' | ')}`)
  const [descarga] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Descargar CSV' }).first().click()])
  const bytes = readFileSync(await descarga.path())
  const conBom = bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf
  const csv = bytes.toString('utf8').replace(/^﻿/, '')
  const lineas = csv.trim().split(/\r?\n/)
  verificar(lineas.length === 13 && conBom && lineas[0].startsWith('Periodo;Vence;Pago real'), `CSV descargado (${descarga.suggestedFilename()}): ${lineas.length} líneas, cabecera correcta, con BOM UTF-8`)
  await page.screenshot({ path: path.join(salida, `contratos-demo-${nombre}.png`), fullPage: true })
  await contexto.close()
}

// ---- 2) Consulta con la función simulada ----
console.log('Consulta con función simulada')
{
  const contexto = await navegador.newContext({ viewport: { width: 390, height: 800 } })
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
  await contexto.route((url) => !url.protocol.startsWith('file') && !url.hostname.endsWith('azurewebsites.net'), (r) => r.abort())
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

await navegador.close()
console.log(fallas ? `\n${fallas} FALLAS` : '\nTodo en orden')
console.log(`Capturas en ${salida}`)
process.exit(fallas ? 1 : 0)

