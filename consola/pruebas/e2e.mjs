// Prueba de punta a punta: la consola compilada (dist/) en Chromium contra el API REAL en http://localhost:8080.
// Hay que tener el backend arriba (./mvnw spring-boot:run) y haber corrido "npm run build" (VITE_API_URL sin definir
// apunta a localhost:8080). Sirve dist/ en el puerto 5173 porque el CORS del API solo acepta http://localhost:5173.
// Recorre simulador, caso 13, laboratorio y reglas en celular (360 px) y escritorio; falla si hay errores de JS,
// peticiones fallidas, desbordes horizontales o si aparece el aviso de modo demostración.
// Uso: node pruebas/e2e.mjs [carpeta-capturas]
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { chromium, problemas, raiz, servirDist, usabilidad } from './comun.mjs'

const API = process.env.E2E_API_URL ?? 'http://localhost:8080'
const salida = path.resolve(process.argv[2] ?? path.join(raiz, 'pruebas', 'capturas'))
mkdirSync(salida, { recursive: true })

try {
  const r = await fetch(`${API}/api/v1/catalogo`)
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
} catch (e) {
  console.error(`E2E: el API no responde en ${API} (${e.message}). Levanta el backend con ./mvnw spring-boot:run`)
  process.exit(2)
}

const { servidor, origen } = await servirDist(5173, 'localhost')
const nbsp = (t) => t.replaceAll(' ', ' ')
let fallas = 0
const falla = (mensaje) => {
  console.log(`  FALLA: ${mensaje}`)
  fallas++
}

const navegador = await chromium.launch()
for (const [nombre, ancho, alto, escala] of [['celular', 360, 780, 2], ['escritorio', 1366, 768, 1], ['laptop150', 1366, 768, 1.5]]) {
  const contexto = await navegador.newContext({ viewport: { width: ancho, height: alto }, deviceScaleFactor: escala, colorScheme: 'light' })
  const page = await contexto.newPage()
  const errores = []
  page.on('pageerror', (e) => errores.push(`JS: ${e}`))
  // El 400 del abono imposible es lo que la prueba provoca a propósito: Chromium lo registra como error de recurso.
  page.on('console', (m) => m.type() === 'error' && !/status of 400/.test(m.text()) && errores.push(`consola: ${m.text()}`))
  page.on('requestfailed', (r) => errores.push(`petición fallida: ${r.url()} ${r.failure()?.errorText}`))
  page.on('response', (r) => r.url().startsWith(API) && r.status() >= 401 && errores.push(`HTTP ${r.status()} ${r.url()}`))
  const llamadasApi = []
  page.on('request', (r) => r.url().startsWith(API) && llamadasApi.push(`${r.method()} ${new URL(r.url()).pathname}`))

  const revisar = async (pantalla) => {
    const p = [...(await problemas(page)), ...(await usabilidad(page))]
    console.log(`${nombre} · ${pantalla}: desbordes ${JSON.stringify(p)}`)
    fallas += p.length
    await page.screenshot({ path: path.join(salida, `e2e-${nombre}-${pantalla}.png`), fullPage: true })
  }
  const ir = (enlace) => page.locator('nav:visible').getByRole('link', { name: enlace }).click()

  await page.goto(origen)

  // 1. Simulador: quincenal por defecto → cuota total 355.667 (viene sin redondear del API y se redondea al mostrar)
  // La primera llamada puede tardar ~30 s: el API está en un plan gratuito que se duerme.
  await page.getByRole('region', { name: 'Resultado de la simulación' }).waitFor({ timeout: 90000 })
  const resultado = nbsp(await page.getByRole('region', { name: 'Resultado de la simulación' }).innerText())
  if (!resultado.includes('$ 355.667')) falla(`simulador: falta la cuota total $ 355.667 en ${JSON.stringify(resultado.slice(0, 200))}`)
  if (!resultado.includes('$ 219.093')) falla('simulador: falta la cuota financiera $ 219.093')
  if (await page.getByText('Datos de demostración').count()) falla('aparece el aviso de modo demostración: la consola no está usando el API')
  await revisar('simulador')

  // 2. Caso 13 NEGOCIO: saldo 3.169.909,34 en la semana 42 y asiento con su explicación
  await ir('Casos')
  await page.getByRole('link', { name: /Caso 13/ }).click()
  const tabla = page.getByRole('region', { name: 'Tabla periodo a periodo' })
  await tabla.waitFor({ timeout: 30000 })
  const fila42 = tabla.locator('tbody tr').filter({ has: page.locator('th[scope="row"]', { hasText: /^42$/ }) })
  const texto42 = nbsp(await fila42.innerText())
  if (!texto42.includes('$ 3.169.909,34')) falla(`caso 13 semana 42: falta el saldo $ 3.169.909,34 en ${JSON.stringify(texto42)}`)
  if (!texto42.includes('Cuota + abono extra')) falla('caso 13 semana 42: falta el evento "Cuota + abono extra"')
  await page.getByText('Termina en el periodo 83').waitFor()
  const asiento42 = page.locator('details').filter({ hasText: 'Periodo 42' }).first()
  await asiento42.locator('summary').click()
  if (!(await asiento42.getByText('Recaudo extraordinario aplicado a pendientes').first().isVisible())) falla('caso 13: no se ve la explicación del asiento del periodo 42')
  if (!(await asiento42.getByText('Cuadra').first().isVisible())) falla('caso 13: el asiento del periodo 42 no dice que cuadra')
  await revisar('caso-13')

  // 3. Laboratorio: el caso 13 se reejecuta igual y un abono mayor que el saldo da ABONO_MAYOR_QUE_SALDO
  await page.getByRole('button', { name: /Probar variaciones en el laboratorio/ }).click()
  await page.getByRole('button', { name: /Editar el evento del periodo 42/ }).first().waitFor()
  const ahora = await page.locator('[data-prueba="partida-actual"]').innerText()
  if (ahora !== 'Caso 13 · Abono extra con pendientes') falla(`laboratorio: el punto de partida dice "${ahora}"`)
  await page.getByRole('button', { name: 'Calcular resultado' }).click()
  await page.getByText('Termina en el periodo 83').waitFor({ timeout: 30000 })
  await page.getByRole('button', { name: /Abono extra de \$ 500\.000 a menor plazo/ }).click()
  await page.getByLabel('Periodo', { exact: true }).fill('10')
  await page.getByLabel('Valor del abono').fill('99000000')
  await page.getByRole('button', { name: 'Agregar evento' }).click()
  await page.getByRole('button', { name: 'Calcular resultado' }).click()
  const alerta = page.getByRole('alert')
  await alerta.waitFor({ timeout: 30000 })
  const textoAlerta = nbsp(await alerta.innerText())
  if (!textoAlerta.includes('ABONO_MAYOR_QUE_SALDO')) falla(`laboratorio: falta ABONO_MAYOR_QUE_SALDO en ${JSON.stringify(textoAlerta)}`)
  if (!/Abono: \$ 99\.000\.000/.test(textoAlerta) || !/Saldo de capital: \$/.test(textoAlerta)) falla(`laboratorio: el detalle del error no es legible: ${JSON.stringify(textoAlerta)}`)
  await revisar('laboratorio-error')

  // 4. Reglas: modalidadUsura
  await ir('Reglas')
  const reglas = page.getByRole('list', { name: 'Reglas por perfil' })
  await reglas.waitFor({ timeout: 30000 })
  if (!(await reglas.getByText('Qué se conserva cuando la usura obliga a bajar la tasa').first().isVisible())) falla('reglas: no se ve la regla de usura')
  if (!(await reglas.getByText('Se conserva la cuota y cambia el número de cuotas').first().isVisible())) falla('reglas: no se ve traducido el valor «misma cuota» de las reglas de negocio')
  if (/[A-Z]{3,}_[A-Z]/.test(await reglas.innerText())) falla('reglas: se ven nombres internos en mayúsculas')
  await revisar('reglas')

  console.log(`${nombre}: llamadas al API ${llamadasApi.length} · errores ${errores.length} ${errores.length ? JSON.stringify(errores.slice(0, 4)) : ''}`)
  fallas += errores.length
  await contexto.close()
}
await navegador.close()
servidor.close()
if (fallas) {
  console.error(`E2E FALLÓ: ${fallas} problema(s)`)
  process.exit(1)
}
console.log('E2E OK: simulador, caso 13, laboratorio y reglas contra el API real, en celular y escritorio, sin errores JS ni desbordes')
