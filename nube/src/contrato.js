const crypto = require('node:crypto')

// Lógica pura de la consulta de contratos: no conoce Azure ni la base real.
// `consultar(sql, parametros)` devuelve las filas; en producción es pg, en las pruebas un falso.

const ORIGENES = ['https://juanc101195.github.io', 'http://localhost:5173', 'http://127.0.0.1:5173']
const LIMITE_POR_MINUTO = 60

const hash = (texto) => crypto.createHash('sha256').update(String(texto)).digest()

function claveValida(recibida, esperada) {
  if (!esperada || !recibida) return false
  return crypto.timingSafeEqual(hash(recibida), hash(esperada))
}

// Limitador de ventana fija por IP, en memoria (una instancia de la función; suficiente para frenar abusos simples).
function crearLimitador(limite = LIMITE_POR_MINUTO, ahora = Date.now) {
  const ventanas = new Map()
  return (ip) => {
    const t = ahora()
    if (ventanas.size > 5000) for (const [k, v] of ventanas) if (t - v.inicio >= 60000) ventanas.delete(k)
    const v = ventanas.get(ip)
    if (!v || t - v.inicio >= 60000) {
      ventanas.set(ip, { inicio: t, cuenta: 1 })
      return true
    }
    v.cuenta += 1
    return v.cuenta <= limite
  }
}

const num = (v) => (v === null || v === undefined ? null : Number(v))
const bool = (v) => (v === null || v === undefined ? null : Boolean(v))
const fecha = (v) => {
  if (v === null || v === undefined) return null
  if (v instanceof Date) {
    const p = (n) => String(n).padStart(2, '0')
    return `${v.getFullYear()}-${p(v.getMonth() + 1)}-${p(v.getDate())}`
  }
  return String(v).slice(0, 10)
}

function variantes(numero) {
  const sinCeros = numero.replace(/^0+/, '') || '0'
  return [...new Set([numero, sinCeros])]
}

function resumen(r) {
  const pagado = num(r.sim_pagado)
  const saldo = num(r.sim_saldo_final)
  const pend = num(r.sim_pendientes)
  return {
    simPagado: pagado,
    simSaldoFinal: saldo,
    simPendientes: pend,
    simTotalConDeuda: (pagado ?? 0) + (saldo ?? 0) + (pend ?? 0),
    simMora: num(r.sim_mora),
    simCobranza: num(r.sim_cobranza),
    simInteres: num(r.sim_interes),
    simServicios: num(r.sim_servicios),
    simCuotasVencidasMax: num(r.sim_cuotas_vencidas_max),
    simMarcaRecuperacion: bool(r.sim_marca_recuperacion),
    simPeriodoMarca: num(r.sim_periodo_marca),
    simEstadoFinal: r.sim_estado_final ?? null,
    realEaConMultas: num(r.real_ea_con_multas),
  }
}

function periodo(p) {
  return {
    periodo: num(p.periodo),
    fechaVencimiento: fecha(p.fecha_vencimiento),
    instruccion: p.instruccion ?? null,
    valorReal: num(p.valor_real),
    diasAtraso: num(p.dias_atraso),
    justificado: bool(p.justificado),
    estado: p.estado ?? null,
    cuotasVencidas: num(p.cuotas_vencidas),
    saldoInicial: num(p.saldo_inicial),
    interesCorriente: num(p.interes_corriente),
    interesMora: num(p.interes_mora),
    cobranza: num(p.cobranza),
    servicios: num(p.servicios),
    cuotaFinanciera: num(p.cuota_financiera),
    pagoCapital: num(p.pago_capital),
    pagoInteres: num(p.pago_interes),
    pagoMora: num(p.pago_mora),
    pagoCobranza: num(p.pago_cobranza),
    pagoServicios: num(p.pago_servicios),
    pagoTotalCliente: num(p.pago_total_cliente),
    capitalVencido: num(p.capital_vencido),
    saldoFinal: num(p.saldo_final),
    listoRecuperacion: bool(p.listo_recuperacion),
    tasaEA: num(p.tasa_ea),
  }
}

async function buscar(numero, consultar) {
  const contratos = await consultar(
    'SELECT id, sede, contrato, cohorte, estado_real, fecha_inicio, fecha_fin, cuotas, cuota_real_miles, inversion, ' +
      'inversion_imputada, linea_usura, tasa_inicial_ea, pagos_reales, multas_reales_miles, dias_justificados ' +
      'FROM contrato WHERE contrato = ANY($1::text[]) ORDER BY fecha_inicio, id',
    [variantes(numero)],
  )
  if (!contratos.length) return []
  const ids = contratos.map((c) => c.id)
  const [pagos, resultados, periodos] = await Promise.all([
    consultar('SELECT id, fecha, valor_miles, multa_miles FROM pago_real WHERE id = ANY($1::text[]) ORDER BY id, fecha', [ids]),
    consultar(
      'SELECT corrida, id, sim_pagado, sim_saldo_final, sim_pendientes, sim_mora, sim_cobranza, sim_interes, sim_servicios, ' +
        'sim_cuotas_vencidas_final, sim_cuotas_vencidas_max, sim_marca_recuperacion, sim_periodo_marca, sim_estado_final, ' +
        'sim_periodos, periodo_corte, real_ea_con_multas FROM resultado WHERE id = ANY($1::text[])',
      [ids],
    ),
    consultar(
      'SELECT corrida, id, periodo, fecha_vencimiento, instruccion, valor_real, dias_atraso, justificado, estado, cuotas_vencidas, ' +
        'saldo_inicial, interes_corriente, interes_mora, cobranza, servicios, pago_total_cliente, saldo_final, listo_recuperacion, ' +
        'tasa_ea, cuota_financiera, pago_capital, pago_interes, pago_mora, pago_cobranza, pago_servicios, capital_vencido ' +
        'FROM periodo WHERE id = ANY($1::text[]) ORDER BY id, corrida, periodo',
      [ids],
    ),
  ])
  return contratos.map((c) => {
    const susPagos = pagos.filter((p) => p.id === c.id)
    const susPeriodos = periodos.filter((p) => p.id === c.id)
    const corridas = {}
    for (const r of resultados.filter((x) => x.id === c.id)) {
      corridas[r.corrida] = { resumen: resumen(r), periodos: susPeriodos.filter((p) => p.corrida === r.corrida).map(periodo) }
    }
    // Una corrida con periodos pero sin fila de resultado no debe perderse.
    for (const p of susPeriodos) {
      corridas[p.corrida] ??= { resumen: null, periodos: susPeriodos.filter((x) => x.corrida === p.corrida).map(periodo) }
    }
    return {
      id: c.id,
      estadoReal: c.estado_real ?? null,
      cohorte: c.cohorte ?? null,
      fechaInicio: fecha(c.fecha_inicio),
      fechaFin: fecha(c.fecha_fin),
      cuotas: num(c.cuotas),
      cuotaRealMiles: num(c.cuota_real_miles),
      inversion: num(c.inversion),
      inversionImputada: bool(c.inversion_imputada),
      lineaUsura: c.linea_usura ?? null,
      tasaInicialEA: num(c.tasa_inicial_ea),
      diasJustificados: num(c.dias_justificados),
      realPagadoCuotas: susPagos.reduce((s, p) => s + (num(p.valor_miles) ?? 0) * 1000, 0),
      realMultas: (num(c.multas_reales_miles) ?? 0) * 1000,
      pagosReales: susPagos.map((p) => ({ fecha: fecha(p.fecha), valorMiles: num(p.valor_miles), multaMiles: num(p.multa_miles) })),
      corridas,
    }
  })
}

/**
 * Atiende una petición ya normalizada: { metodo, origen, clave, ip, numero }.
 * Devuelve { status, headers, cuerpo } (cuerpo undefined = sin cuerpo).
 */
function crearManejador({ consultar, claveEsperada, limitador = crearLimitador(), registrar = () => {} }) {
  return async function manejar({ metodo, origen, clave, ip, numero }) {
    const headers = { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }
    if (ORIGENES.includes(origen)) {
      headers['Access-Control-Allow-Origin'] = origen
      headers.Vary = 'Origin'
      headers['Access-Control-Allow-Methods'] = 'GET, OPTIONS'
      headers['Access-Control-Allow-Headers'] = 'x-clave, content-type'
      headers['Access-Control-Max-Age'] = '600'
    }
    if (metodo === 'OPTIONS') return { status: 204, headers }
    if (!limitador(ip || 'desconocida')) return { status: 429, headers, cuerpo: { error: 'demasiadas_consultas' } }
    if (!claveValida(clave, claveEsperada)) return { status: 401, headers, cuerpo: { error: 'clave' } }
    if (typeof numero !== 'string' || !/^[0-9]{1,10}$/.test(numero)) {
      return { status: 400, headers, cuerpo: { error: 'numero_invalido' } }
    }
    try {
      const episodios = await buscar(numero, consultar)
      if (!episodios.length) return { status: 404, headers, cuerpo: { error: 'no_encontrado' } }
      return { status: 200, headers, cuerpo: { numero, episodios } }
    } catch (e) {
      registrar(`Consulta de contrato falló: ${e && e.code ? e.code : 'error'}`)
      return { status: 500, headers, cuerpo: { error: 'interno' } }
    }
  }
}

module.exports = { crearManejador, crearLimitador, claveValida, variantes, ORIGENES }
