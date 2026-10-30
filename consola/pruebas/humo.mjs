// Prueba de humo de la consola compilada (dist/) en Chromium: celular (360 px), escritorio (1366 px, claro y oscuro)
// y laptop de Windows con la escala al 150 % (1366 px, deviceScaleFactor 1.5).
// Recorre las 4 pantallas en modo demostración (el API en localhost:8080 se bloquea a propósito, así se prueba
// también el respaldo), abre el selector propio y las tarjetas de casos, y falla si hay errores de JS, desbordes
// horizontales, algún <select> nativo, áreas táctiles de menos de 44 px o letra de menos de 14 px.
// Usa el Playwright instalado en el front de Prestaya.
// Uso: npm run build && node pruebas/humo.mjs [carpeta-capturas]
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { chromium, problemas, raiz, servirDist, usabilidad } from './comun.mjs'

const salida = path.resolve(process.argv[2] ?? path.join(raiz, 'pruebas', 'capturas'))
mkdirSync(salida, { recursive: true })

const { servidor, origen } = await servirDist(0)

const CONFIGURACIONES = [
  { nombre: 'celular', ancho: 360, alto: 780, escala: 2, esquemas: ['light'] },
  { nombre: 'escritorio', ancho: 1366, alto: 768, escala: 1, esquemas: ['light', 'dark'] },
  { nombre: 'laptop150', ancho: 1366, alto: 768, escala: 1.5, esquemas: ['light'] },
]

let fallas = 0
const navegador = await chromium.launch()
for (const { nombre, ancho, alto, escala, esquemas } of CONFIGURACIONES) {
  for (const esquema of esquemas) {
    const contexto = await navegador.newContext({ viewport: { width: ancho, height: alto }, deviceScaleFactor: escala, colorScheme: esquema, hasTouch: nombre === 'celular' })
    await contexto.route(/localhost:8080/, (r) => r.abort())
    const page = await contexto.newPage()
    const errores = []
    page.on('pageerror', (e) => errores.push(String(e)))
    page.on('console', (m) => m.type() === 'error' && !/Failed to load resource|ERR_FAILED|net::/.test(m.text()) && errores.push(m.text()))
    const etiqueta = `${nombre}-${esquema}`
    const revisar = async (pantalla) => {
      const p = [...(await problemas(page)), ...(await usabilidad(page))]
      console.log(`${etiqueta} · ${pantalla}: ${p.length ? JSON.stringify(p) : 'ok'}`)
      fallas += p.length
      await page.screenshot({ path: path.join(salida, `consola-${etiqueta}-${pantalla}.png`), fullPage: true })
    }
    const foto = (pantalla) => page.screenshot({ path: path.join(salida, `consola-${etiqueta}-${pantalla}.png`) })
    const ir = (nombreEnlace) => page.locator('nav:visible').getByRole('link', { name: nombreEnlace }).click()
    const nbsp = (t) => t.replace(/[  ]/g, ' ')

    await page.goto(origen)
    await page.getByText('Datos de demostración').first().waitFor({ timeout: 10000 })

    // 1. Simulador (quincenal por defecto, luego mensual); el dinero se ve con miles
    await page.getByRole('region', { name: 'Resultado de la simulación' }).waitFor()
    if (!(await page.getByText('$ 366.206').first().isVisible())) { console.log('FALTA cuota total 366.206'); fallas++ }
    if ((await page.getByLabel('Valor de la moto').inputValue()) !== '5.100.000') { console.log('FALTA el formato de miles en el valor de la moto'); fallas++ }
    await revisar('simulador')
    await page.getByRole('radio', { name: /Mensual/ }).check({ force: true })
    await page.getByRole('button', { name: 'Simular' }).click()
    await page.getByText('$ 738.233').first().waitFor()

    // 2. Casos: lista, detalle del 13 y el selector propio abierto
    await ir('Casos')
    await page.getByRole('link', { name: /Caso 13/ }).waitFor()
    await revisar('casos')
    await page.getByRole('link', { name: /Caso 13/ }).click()
    await page.getByRole('region', { name: 'Tabla periodo a periodo' }).waitFor()
    await page.getByText('Termina en el periodo 85').waitFor()
    await page.locator('details').first().locator('summary').click()
    await revisar('caso-13')
    const selector = page.getByRole('button', { name: /Ver otro caso/ })
    await selector.scrollIntoViewIfNeeded()
    await selector.click()
    await page.getByRole('listbox').waitFor()
    const opciones = page.getByRole('option')
    if ((await opciones.count()) !== 13) { console.log(`el selector tiene ${await opciones.count()} opciones, no 13`); fallas++ }
    const alturaOpcion = (await opciones.first().boundingBox())?.height ?? 0
    const letraOpcion = await opciones.first().evaluate((el) => parseFloat(getComputedStyle(el).fontSize))
    if (alturaOpcion < 48 || letraOpcion < 16) { console.log(`opción del selector de ${alturaOpcion} px y letra ${letraOpcion} px`); fallas++ }
    fallas += (await usabilidad(page)).length
    await foto('selector-abierto')
    await page.keyboard.type('retoma')
    await foto('selector-buscando')
    await page.keyboard.press('Enter')
    await page.waitForURL(/#\/casos\/4$/)
    await page.getByRole('heading', { level: 1, name: /Caso 4/ }).waitFor()

    // 3. Laboratorio: desde el caso 13, calcular; tarjetas de casos; un atajo con un abono imposible
    await page.getByRole('button', { name: /Ver otro caso/ }).click()
    await page.getByRole('option', { name: /Caso 13/ }).click()
    await page.getByRole('heading', { level: 1, name: /Caso 13/ }).waitFor()
    await page.getByRole('button', { name: /Probar variaciones en el laboratorio/ }).click()
    await page.getByRole('button', { name: /Editar el evento del periodo 42/ }).first().waitFor()
    const ahora = await page.locator('[data-prueba="partida-actual"]').innerText()
    if (ahora !== 'Caso 13 · Abono extra con pendientes') { console.log(`el laboratorio dice "${ahora}"`); fallas++ }
    await page.getByRole('button', { name: 'Calcular resultado' }).click()
    await page.getByRole('region', { name: 'Tabla periodo a periodo' }).waitFor()
    await revisar('laboratorio')
    await page.getByRole('button', { name: 'Elegir otro caso' }).click()
    if (!(await page.getByRole('radio', { name: /^Caso 13:/ }).isChecked())) { console.log('la tarjeta del caso 13 no está marcada'); fallas++ }
    await page.getByRole('heading', { name: '1. ¿De dónde partes?' }).scrollIntoViewIfNeeded()
    await foto('laboratorio-casos')
    await page.locator('label.opcion-tarjeta', { hasText: 'Abono extra con pendientes' }).click()
    await page.getByRole('button', { name: /Abono extra de \$ 500\.000 a menor plazo/ }).click()
    // El atajo enfoca el periodo en el siguiente cuadro de animación: se espera a que termine antes de escribir,
    // para que el resultado no dependa de la velocidad de la máquina (en el servidor de GitHub fallaba).
    await page.waitForFunction(() => document.activeElement?.closest('[data-periodo]') !== null && document.activeElement?.closest('[data-periodo]') !== undefined)
    await page.getByLabel('Periodo', { exact: true }).fill('10')
    await page.getByLabel('Valor del abono').fill('99000000')
    const abonoVisto = await page.getByLabel('Valor del abono').inputValue()
    if (abonoVisto !== '99.000.000') { console.log(`el abono no se ve con miles: llegó "${abonoVisto}"`); fallas++ }
    await page.getByRole('heading', { name: 'Agregar un evento' }).scrollIntoViewIfNeeded()
    await foto('laboratorio-atajo')
    await page.getByRole('button', { name: 'Agregar evento' }).click()
    await page.getByRole('button', { name: 'Calcular resultado' }).click()
    const alerta = page.getByRole('alert')
    await alerta.waitFor()
    const textoAlerta = nbsp(await alerta.innerText())
    if (!textoAlerta.includes('Abono: $ 99.000.000')) { console.log(`el error no muestra el abono legible: "${textoAlerta.slice(0, 160)}"`); fallas++ }
    await revisar('laboratorio-error')

    // 4. Reglas
    await ir('Reglas')
    await page.getByRole('list', { name: 'Reglas por perfil' }).waitFor()
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
console.log('HUMO OK: 4 pantallas en celular, escritorio y laptop al 150 %, sin errores JS, desbordes, <select>, áreas táctiles pequeñas ni letra diminuta')
