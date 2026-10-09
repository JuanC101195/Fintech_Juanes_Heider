import ExcelJS from 'exceljs'
import { FECHA_ESCENARIOS, previsualizacion } from './escenarios'
import { PREGUNTAS } from './preguntas'
import { ajustesDe, avance, estaCompleta, type Estado } from './respuestas'

// Arma el Excel de respuestas. La hoja "Respuestas" es la que lee herramientas/importar_respuestas.py:
// sus encabezados y los códigos de opción no deben cambiar.

export const ENCABEZADOS = [
  'ID',
  'Bloque',
  'Pregunta',
  'Código',
  'Respuesta',
  'Valor',
  'Unidad',
  'Detalle',
  'Comentario',
  'Ajustes al motor',
  'Completa',
] as const

export function filasRespuestas(estado: Estado): (string | number)[][] {
  return PREGUNTAS.map((p) => {
    const r = estado.respuestas[p.id]
    const opcion = p.opciones.find((o) => o.codigo === r?.codigo)
    const ajustes = Object.entries(ajustesDe(p, r))
      .map(([k, v]) => `${k}=${v}`)
      .join('; ')
    return [
      p.id,
      p.bloque,
      p.titulo,
      r?.codigo ?? '',
      opcion?.texto ?? '',
      opcion?.pideValor && r?.valor !== undefined ? r.valor : '',
      opcion?.pideValor ? (p.valor?.unidad ?? '') : '',
      r?.texto ?? '',
      r?.comentario ?? '',
      ajustes,
      estaCompleta(p, r) ? 'Sí' : 'No',
    ]
  })
}

const VERDE = 'FF006B5E'

function encabezar(hoja: ExcelJS.Worksheet) {
  const fila = hoja.getRow(1)
  fila.font = { bold: true, color: { argb: 'FFFFFFFF' } }
  fila.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: VERDE } }
  fila.alignment = { vertical: 'middle', wrapText: true }
  hoja.views = [{ state: 'frozen', ySplit: 1 }]
}

export async function construirLibro(estado: Estado, ahora = new Date()): Promise<ExcelJS.Workbook> {
  const libro = new ExcelJS.Workbook()
  libro.creator = estado.respondente.nombre || 'Cuestionario de reglas'
  libro.created = ahora

  const { completas, total } = avance(estado.respuestas)
  const portada = libro.addWorksheet('Resumen')
  portada.columns = [{ width: 32 }, { width: 70 }]
  portada.addRows([
    ['Cuestionario de reglas del motor de cartera'],
    [],
    ['Respondió', estado.respondente.nombre],
    ['Cargo', estado.respondente.cargo],
    ['Fecha', ahora.toLocaleString('es-CO')],
    ['Preguntas completas', `${completas} de ${total}`],
    ['Escenarios calculados el', FECHA_ESCENARIOS],
    [],
    ['Cómo se usa este archivo', 'Se devuelve tal cual: la hoja "Respuestas" alimenta la configuración del motor de cartera.'],
  ])
  portada.getCell('A1').font = { bold: true, size: 14, color: { argb: VERDE } }
  portada.getColumn(1).font = { bold: true }

  const hoja = libro.addWorksheet('Respuestas')
  hoja.columns = [
    { width: 9 }, { width: 24 }, { width: 60 }, { width: 22 }, { width: 50 }, { width: 12 },
    { width: 9 }, { width: 40 }, { width: 40 }, { width: 45 }, { width: 10 },
  ]
  hoja.addRow([...ENCABEZADOS])
  filasRespuestas(estado).forEach((f) => hoja.addRow(f))
  encabezar(hoja)
  hoja.eachRow((fila, n) => {
    if (n > 1) fila.alignment = { vertical: 'top', wrapText: true }
  })

  // Lo que el motor calculó para la opción elegida, para dejar trazabilidad de lo que se vio al responder.
  const vistas = libro.addWorksheet('Efecto de lo elegido')
  vistas.columns = [
    { header: 'ID', width: 9 }, { header: 'Opción', width: 22 }, { header: 'Caso de prueba', width: 60 },
    { header: 'Semana final', width: 13 }, { header: 'Estado final', width: 20 }, { header: 'Total pagado', width: 16 },
    { header: 'Saldo pendiente', width: 16 }, { header: 'Mora pagada', width: 14 }, { header: 'Cobranza pagada', width: 16 },
    { header: 'Servicios pagados', width: 17 }, { header: 'Deuda a extinguir', width: 17 }, { header: 'Faltante FCC', width: 14 },
  ]
  for (const p of PREGUNTAS) {
    const r = estado.respuestas[p.id]
    const prev = previsualizacion(p.previsualizacion)
    if (!prev || !r?.codigo) continue
    const opcion = p.opciones.find((o) => o.codigo === r.codigo)
    const sugerido = p.valor?.sugeridos?.find((s) => s.valor === r.valor)?.escenario
    const clave = opcion?.escenario ?? (opcion?.pideValor ? sugerido : undefined)
    const res = clave ? prev.opciones[clave] : undefined
    if (!res) continue
    const k = res.kpis
    vistas.addRow([p.id, r.codigo, prev.caso, k.semanaFinal, k.estadoFinal, k.totalPagado, k.saldoPendiente, k.moraPagada,
      k.cobranzaPagada, k.serviciosPagados, k.deudaAExtinguir, k.faltanteFcc])
  }
  encabezar(vistas)
  for (const c of [6, 7, 8, 9, 10, 11, 12]) vistas.getColumn(c).numFmt = '#,##0.00'
  return libro
}

export async function descargarExcel(estado: Estado) {
  const libro = await construirLibro(estado)
  const datos = await libro.xlsx.writeBuffer()
  const blob = new Blob([datos], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  const nombre = (estado.respondente.nombre || 'respuestas').trim().toLowerCase().replace(/\s+/g, '-')
  enlace.href = url
  enlace.download = `reglas-motor-cartera-${nombre}-${new Date().toISOString().slice(0, 10)}.xlsx`
  enlace.click()
  URL.revokeObjectURL(url)
}
