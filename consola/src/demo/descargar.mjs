// Regenera los fixtures del modo demostración con respuestas REALES del API.
// Uso: levantar el backend (./mvnw spring-boot:run) y correr
//   node src/demo/descargar.mjs [urlApi] [--completo]
// Escribe en src/demo/fixtures/ los archivos que espera el cargador de demo (src/demo/index.ts):
//   catalogo, reglas, casos, simulacion-{semanal|quincenal|mensual}, caso-{1..13}-{EXCEL|NEGOCIO}.
// Los JSON se guardan compactos y, por defecto, recortados: de cada movimiento solo quedan los campos que la consola
// muestra (los decimales siguen completos). Con --completo se guarda la respuesta íntegra del API (unos 6 MB).
import { mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const args = process.argv.slice(2)
const recortar = !args.includes('--completo')
const base = (args.find((a) => !a.startsWith('--')) ?? 'http://localhost:8080').replace(/\/+$/, '') + '/api/v1'
const carpeta = path.join(path.dirname(fileURLToPath(import.meta.url)), 'fixtures')

const CAMPOS_MOVIMIENTO = new Set([
  'periodo', 'mes', 'estado', 'evento', 'etiqueta', 'saldoInicial', 'cuotaFinanciera', 'interesCorriente', 'interesMora', 'capitalContractual',
  'cobranzaCausada', 'pagoRecibido', 'pagoCobranza', 'pagoMora', 'pagoInteresCorriente', 'pagoServicios', 'pagoCapital', 'abonoExtra', 'prepagoCapital',
  'cxcCobranza', 'cxcMora', 'cxcInteresCorriente', 'cxcServicios', 'capitalVencido', 'capitalVigente', 'saldoFinal', 'cuotasVencidas',
  'pendientesPagadosExtra', 'pagoTotalCliente', 'pagoExtraordinario',
])

function recortarCaso(caso) {
  if (!recortar) return caso
  return {
    ...caso,
    movimientos: caso.movimientos.map((m) => Object.fromEntries(Object.entries(m).filter(([k]) => CAMPOS_MOVIMIENTO.has(k)))),
    asientos: caso.asientos,
  }
}

async function pedir(ruta, init) {
  const r = await fetch(base + ruta, init)
  if (!r.ok) throw new Error(`${init?.method ?? 'GET'} ${ruta} → HTTP ${r.status}: ${await r.text()}`)
  return r.json()
}
const guardar = (nombre, datos) => writeFileSync(path.join(carpeta, `${nombre}.json`), JSON.stringify(datos))
const simular = (periodicidad, cuotas) =>
  pedir('/simulaciones', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ linea: 'CPPU', periodicidad, cuotas, meses: null, valorMoto: '5100000', pagoInicial: '0', perfilReglas: 'NEGOCIO' }),
  })

mkdirSync(carpeta, { recursive: true })
for (const f of readdirSync(carpeta)) if (f.endsWith('.json')) rmSync(path.join(carpeta, f))

guardar('catalogo', await pedir('/catalogo'))
guardar('reglas', await pedir('/reglas'))
guardar('casos', await pedir('/casos'))
for (const [p, cuotas] of [['SEMANAL', 104], ['QUINCENAL', 48], ['MENSUAL', 24]]) guardar(`simulacion-${p.toLowerCase()}`, await simular(p, cuotas))
for (let n = 1; n <= 13; n++) for (const perfil of ['EXCEL', 'NEGOCIO']) guardar(`caso-${n}-${perfil}`, recortarCaso(await pedir(`/casos/${n}?perfilReglas=${perfil}`)))
console.log(`Fixtures regenerados desde ${base}${recortar ? ' (recortados)' : ' (completos)'} en ${carpeta}`)
