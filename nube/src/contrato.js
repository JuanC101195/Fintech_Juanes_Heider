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
    simProyectadoTotal: num(r.sim_proyectado_total),
    realEaConMultas: num(r.real_ea_con_multas),
    realEA: num(r.real_ea_con_multas),
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
    usuraEA: num(p.usura_ea),
    logica: p.logica ?? null,
  }
}

const COLUMNAS_PERIODO =
  'corrida, id, periodo, fecha_vencimiento, instruccion, valor_real, dias_atraso, justificado, estado, cuotas_vencidas, ' +
  'saldo_inicial, interes_corriente, interes_mora, cobranza, servicios, pago_total_cliente, saldo_final, listo_recuperacion, ' +
  'tasa_ea, cuota_financiera, pago_capital, pago_interes, pago_mora, pago_cobranza, pago_servicios, capital_vencido'
const PIE_PERIODO = ' FROM periodo WHERE id = ANY($1::text[]) ORDER BY id, corrida, periodo'

// `usura_ea` y `logica` son columnas nuevas: si la base aún no las tiene (código 42703, columna inexistente) se consulta sin ellas.
async function consultarPeriodos(consultar, ids) {
  for (const extra of [', usura_ea, logica', ', usura_ea', '']) {
    try {
      return await consultar('SELECT ' + COLUMNAS_PERIODO + extra + PIE_PERIODO, [ids])
    } catch (e) {
      if (!e || e.code !== '42703' || extra === '') throw e
    }
  }
}

// Columnas nuevas (valor total y saldo de motos_app, proyeccion del motor): si la base aun no las tiene (42703) se consulta sin ellas.
async function conRespaldo(consultar, armar, parametros) {
  for (const extra of [true, false]) {
    try {
      return await consultar(armar(extra), parametros)
    } catch (e) {
      if (!e || e.code !== '42703' || !extra) throw e
    }
  }
}

async function buscar(numero, consultar) {
  const contratos = await conRespaldo(
    consultar,
    (extra) =>
      'SELECT id, sede, contrato, cohorte, estado_real, fecha_inicio, fecha_fin, cuotas, cuota_real_miles, inversion, ' +
      'inversion_imputada, linea_usura, tasa_inicial_ea, pagos_reales, multas_reales_miles, dias_justificados' +
      (extra ? ', valor_total_miles, saldo_real_miles' : '') +
      ' FROM contrato WHERE contrato = ANY($1::text[]) ORDER BY fecha_inicio, id',
    [variantes(numero)],
  )
  if (!contratos.length) return []
  const ids = contratos.map((c) => c.id)
  const [pagos, resultados, periodos] = await Promise.all([
    consultar('SELECT id, fecha, valor_miles, multa_miles FROM pago_real WHERE id = ANY($1::text[]) ORDER BY id, fecha', [ids]),
    conRespaldo(
      consultar,
      (extra) =>
        'SELECT corrida, id, sim_pagado, sim_saldo_final, sim_pendientes, sim_mora, sim_cobranza, sim_interes, sim_servicios, ' +
        'sim_cuotas_vencidas_final, sim_cuotas_vencidas_max, sim_marca_recuperacion, sim_periodo_marca, sim_estado_final, ' +
        'sim_periodos, periodo_corte, real_ea_con_multas' + (extra ? ', sim_proyectado_total' : '') + ' FROM resultado WHERE id = ANY($1::text[])',
      [ids],
    ),
    consultarPeriodos(consultar, ids),
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
      valorTotalMiles: num(c.valor_total_miles),
      saldoRealMiles: num(c.saldo_real_miles),
      realPagadoCuotas: susPagos.reduce((s, p) => s + (num(p.valor_miles) ?? 0) * 1000, 0),
      realMultas: (num(c.multas_reales_miles) ?? 0) * 1000,
      pagosReales: susPagos.map((p) => ({ fecha: fecha(p.fecha), valorMiles: num(p.valor_miles), multaMiles: num(p.multa_miles) })),
      corridas,
    }
  })
}

// Contratos activos (los de la tabla motos_app al momento de la corrida) con las dos corridas de tasa fija.
const CORRIDAS_ACTIVOS = ['fija_con_justificaciones', 'fija_sin_justificaciones']

async function listarActivos(consultar) {
  const filas = await conRespaldo(
    consultar,
    (extra) =>
    'SELECT c.id, c.contrato, c.fecha_inicio, c.cuotas, c.cuota_real_miles, c.inversion, c.inversion_imputada, c.pagos_reales, ' +
      'c.dias_justificados, c.multas_reales_miles, ' + (extra ? 'c.valor_total_miles, c.saldo_real_miles, r.sim_proyectado_total, ' : '') +
      '(SELECT COALESCE(SUM(p.valor_miles), 0) * 1000 FROM pago_real p WHERE p.id = c.id) AS real_pagado, ' +
      'r.corrida, r.sim_pagado, r.sim_saldo_final, r.sim_pendientes, r.sim_mora, r.sim_cobranza, r.sim_interes, r.sim_servicios, ' +
      'r.sim_cuotas_vencidas_final, r.sim_cuotas_vencidas_max, r.sim_marca_recuperacion, r.sim_periodo_marca, r.sim_estado_final, ' +
      'r.sim_periodos, r.periodo_corte ' +
      "FROM contrato c JOIN resultado r ON r.id = c.id WHERE c.estado_real = 'VIGENTE' AND r.corrida = ANY($1::text[]) " +
      'ORDER BY c.fecha_inicio, c.id',
    [CORRIDAS_ACTIVOS],
  )
  const porId = new Map()
  for (const f of filas) {
    let c = porId.get(f.id)
    if (!c) {
      c = {
        id: f.id,
        contrato: f.contrato,
        fechaInicio: fecha(f.fecha_inicio),
        cuotas: num(f.cuotas),
        cuotaRealMiles: num(f.cuota_real_miles),
        inversion: num(f.inversion),
        inversionImputada: bool(f.inversion_imputada),
        pagosReales: num(f.pagos_reales),
        diasJustificados: num(f.dias_justificados),
        realPagado: num(f.real_pagado),
        realMultas: (num(f.multas_reales_miles) ?? 0) * 1000,
        valorTotalMiles: num(f.valor_total_miles),
        saldoRealMiles: num(f.saldo_real_miles),
        corridas: {},
      }
      porId.set(f.id, c)
    }
    c.corridas[f.corrida] = {
      simPagado: num(f.sim_pagado),
      simSaldoFinal: num(f.sim_saldo_final),
      simPendientes: num(f.sim_pendientes),
      simMora: num(f.sim_mora),
      simCobranza: num(f.sim_cobranza),
      simInteres: num(f.sim_interes),
      simServicios: num(f.sim_servicios),
      simCuotasVencidasFinal: num(f.sim_cuotas_vencidas_final),
      simCuotasVencidasMax: num(f.sim_cuotas_vencidas_max),
      simMarcaRecuperacion: bool(f.sim_marca_recuperacion),
      simPeriodoMarca: num(f.sim_periodo_marca),
      simEstadoFinal: f.sim_estado_final ?? null,
      simPeriodos: num(f.sim_periodos),
      periodoCorte: num(f.periodo_corte),
      simProyectadoTotal: num(f.sim_proyectado_total),
    }
  }
  return [...porId.values()]
}

// Recaudo mensual de los contratos activos con tasa fija. Cada cuota se atribuye al mes en que vence.
//  - Meses CERRADOS (anteriores al mes del corte): la flota es la que estaba activa el último día de ese mes (foto de motos_app_hist,
//    tabla activas_fin_mes), con lo simulado de cada contrato (tabla periodo), aunque hoy ya no esté activo.
//  - Mes en curso y siguientes: las motos activas de hoy (tabla proyeccion); lo posterior al corte supone cuotas pagadas a tiempo.
const SUMAS = 'SUM(pago_capital) AS capital, SUM(pago_interes) AS interes, SUM(pago_mora) AS mora, SUM(pago_cobranza) AS cobranza, ' +
  'SUM(pago_servicios) AS servicios, SUM(pago_capital + pago_interes + pago_mora + pago_cobranza + pago_servicios) AS total'

async function flujoActivos(consultar) {
  const corteFila = await consultar('SELECT (SELECT MAX(corte) FROM corte_activos) AS corte', [])
  const corte = fecha(corteFila[0]?.corte)
  const mesCorte = corte ? corte.slice(0, 7) : '9999-99'
  const mesDe = "to_char(date_trunc('month', p.fecha_vencimiento), 'YYYY-MM')"
  const [cerrados, hoy, motos, realCerrado, realCorte] = await Promise.all([
    consultar(
      `SELECT ${mesDe} AS mes, p.corrida, ${SUMAS}, COUNT(DISTINCT p.id) AS contratos FROM periodo p ` +
        `JOIN contrato c ON c.id = p.id JOIN activas_fin_mes a ON a.contrato = c.contrato AND a.mes = ${mesDe} ` +
        `WHERE p.corrida = ANY($1::text[]) AND p.fecha_vencimiento IS NOT NULL AND ${mesDe} < $2 GROUP BY 1, 2 ORDER BY 1, 2`,
      [CORRIDAS_ACTIVOS, mesCorte],
    ),
    consultar(
      `SELECT ${mesDe} AS mes, p.corrida, ${SUMAS}, COUNT(DISTINCT p.id) AS contratos FROM proyeccion p ` +
        `WHERE p.corrida = ANY($1::text[]) AND ${mesDe} >= $2 GROUP BY 1, 2 ORDER BY 1, 2`,
      [CORRIDAS_ACTIVOS, mesCorte],
    ),
    consultar('SELECT mes, COUNT(*) AS motos FROM activas_fin_mes GROUP BY mes', []),
    consultar(
      "SELECT to_char(date_trunc('month', r.fecha), 'YYYY-MM') AS mes, SUM(r.valor_miles) * 1000 AS pagos FROM pago_real r " +
        "JOIN contrato c ON c.id = r.id JOIN activas_fin_mes a ON a.contrato = c.contrato AND a.mes = to_char(date_trunc('month', r.fecha), 'YYYY-MM') GROUP BY 1",
      [],
    ),
    consultar(
      "SELECT SUM(r.valor_miles) * 1000 AS pagos FROM pago_real r JOIN contrato c ON c.id = r.id " +
        "WHERE c.estado_real = 'VIGENTE' AND to_char(date_trunc('month', r.fecha), 'YYYY-MM') = $1",
      [mesCorte],
    ),
  ])
  const motosPorMes = new Map(motos.map((r) => [r.mes, num(r.motos)]))
  const realPorMes = new Map(realCerrado.map((r) => [r.mes, num(r.pagos)]))
  const porMes = new Map()
  const agregar = (f, estado) => {
    let m = porMes.get(f.mes)
    if (!m) {
      m = {
        mes: f.mes,
        estado,
        // Motos que estaban activas el último día del mes (solo meses cerrados) y cuántas de ellas tienen simulación.
        motosActivas: estado === 'cerrado' ? (motosPorMes.get(f.mes) ?? null) : null,
        real: estado === 'proyectado' ? null : { pagos: (estado === 'cerrado' ? realPorMes.get(f.mes) : num(realCorte[0]?.pagos)) ?? 0 },
        corridas: {},
      }
      porMes.set(f.mes, m)
    }
    m.corridas[f.corrida] = {
      capital: num(f.capital), interes: num(f.interes), mora: num(f.mora), cobranza: num(f.cobranza),
      servicios: num(f.servicios), total: num(f.total), contratos: num(f.contratos),
    }
  }
  for (const f of cerrados) agregar(f, 'cerrado')
  for (const f of hoy) agregar(f, f.mes === mesCorte ? 'en_curso' : 'proyectado')
  const contratosHoy = hoy.length ? Math.max(...hoy.map((f) => num(f.contratos) ?? 0)) : 0
  return { corte, contratos: contratosHoy, meses: [...porMes.values()] }
}

/**
 * Atiende una petición ya normalizada: { metodo, origen, clave, ip, numero, activos, flujo }.
 * Devuelve { status, headers, cuerpo } (cuerpo undefined = sin cuerpo).
 */
function crearManejador({ consultar, claveEsperada, limitador = crearLimitador(), registrar = () => {} }) {
  return async function manejar({ metodo, origen, clave, ip, numero, activos, flujo }) {
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
    if (flujo === '1') {
      try {
        return { status: 200, headers, cuerpo: await flujoActivos(consultar) }
      } catch (e) {
        registrar(`Flujo de activos falló: ${e && e.code ? e.code : 'error'}`)
        return { status: 500, headers, cuerpo: { error: 'interno' } }
      }
    }
    if (activos === '1') {
      try {
        const contratos = await listarActivos(consultar)
        return { status: 200, headers, cuerpo: { activos: contratos.length, contratos } }
      } catch (e) {
        registrar(`Listado de activos falló: ${e && e.code ? e.code : 'error'}`)
        return { status: 500, headers, cuerpo: { error: 'interno' } }
      }
    }
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
