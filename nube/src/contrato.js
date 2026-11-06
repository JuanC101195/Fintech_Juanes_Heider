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
    pagoFondo: num(p.pago_fondo),
    pagoSeguro: num(p.pago_seguro),
    pagoAsistencia: num(p.pago_asistencia),
    saldoEnMora: num(p.saldo_en_mora),
    cxcMora: num(p.cxc_mora),
  }
}

const COLUMNAS_PERIODO =
  'corrida, id, periodo, fecha_vencimiento, instruccion, valor_real, dias_atraso, justificado, estado, cuotas_vencidas, ' +
  'saldo_inicial, interes_corriente, interes_mora, cobranza, servicios, pago_total_cliente, saldo_final, listo_recuperacion, ' +
  'tasa_ea, cuota_financiera, pago_capital, pago_interes, pago_mora, pago_cobranza, pago_servicios, capital_vencido'
// Sedes del backtesting. El número de contrato se repite entre sedes: la llave es (sede, contrato).
const SEDES = ['CARTAGENA', 'MONTERIA', 'SINCELEJO', 'BICIMOTOS']
const SEDE_DEFECTO = 'CARTAGENA'

// Normaliza el parámetro ?sede=: undefined = no se envió; null = inválida; si no, la sede en mayúsculas.
function normalizarSede(v) {
  if (v === undefined) return undefined
  if (typeof v !== 'string') return null
  const s = v.trim().toUpperCase()
  return SEDES.includes(s) ? s : null
}

// Intentos de una consulta, del más completo al más antiguo: con filtro de sede (si se pidió) y con las columnas nuevas.
// 42703 (columna inexistente) pasa al siguiente intento; sin la columna `sede` se consulta sin filtro y se asume CARTAGENA.
function intentos(sede, conExtra) {
  const lista = []
  for (const conSede of sede ? [true, false] : [false]) for (const extra of conExtra ? [true, false] : [true]) lista.push({ conSede, extra })
  return lista
}

// `estado.conSede` queda en true si el filtro de sede se pudo aplicar.
async function conRespaldo(consultar, armar, parametros, { sede, estado = {}, conExtra = true } = {}) {
  const lista = intentos(sede, conExtra)
  for (let i = 0; i < lista.length; i++) {
    const { conSede, extra } = lista[i]
    try {
      const filas = await consultar(armar(extra, conSede), conSede ? [...parametros, sede] : parametros)
      estado.conSede = conSede
      return filas
    } catch (e) {
      if (!e || e.code !== '42703' || i === lista.length - 1) throw e
    }
  }
}

// `usura_ea`, `logica` y las del pago por servicio / saldo en mora son columnas nuevas: si la base aún no las tiene
// (código 42703, columna inexistente) se consulta sin ellas.
async function consultarPeriodos(consultar, ids, sede) {
  const nuevas = ', pago_fondo, pago_seguro, pago_asistencia, saldo_en_mora, cxc_mora'
  const lista = []
  for (const conSede of sede ? [true, false] : [false])
    for (const extra of [', usura_ea, logica' + nuevas, ', usura_ea, logica', ', usura_ea', '']) lista.push({ conSede, extra })
  for (let i = 0; i < lista.length; i++) {
    const { conSede, extra } = lista[i]
    try {
      const pie = ' FROM periodo WHERE id = ANY($1::text[])' + (conSede ? ' AND sede = $2' : '') + ' ORDER BY id, corrida, periodo'
      return await consultar('SELECT ' + COLUMNAS_PERIODO + extra + pie, conSede ? [ids, sede] : [ids])
    } catch (e) {
      if (!e || e.code !== '42703' || i === lista.length - 1) throw e
    }
  }
}

async function buscar(numero, consultar, sede) {
  const estado = {}
  const contratos = await conRespaldo(
    consultar,
    (extra, conSede) =>
      'SELECT id, sede, contrato, cohorte, estado_real, fecha_inicio, fecha_fin, cuotas, cuota_real_miles, inversion, ' +
      'inversion_imputada, linea_usura, tasa_inicial_ea, pagos_reales, multas_reales_miles, dias_justificados' +
      (extra ? ', valor_total_miles, saldo_real_miles' : '') +
      ' FROM contrato WHERE contrato = ANY($1::text[])' + (conSede ? ' AND sede = $2' : '') + ' ORDER BY fecha_inicio, id',
    [variantes(numero)],
    { sede, estado },
  )
  // Sin columna `sede` todo es CARTAGENA: para otra sede no hay datos.
  if (!contratos.length || (sede && !estado.conSede && sede !== SEDE_DEFECTO)) return []
  const ss = estado.conSede ? sede : undefined
  const ids = contratos.map((c) => c.id)
  const [pagos, resultados, periodos] = await Promise.all([
    conRespaldo(
      consultar,
      (_extra, conSede) => 'SELECT id, fecha, valor_miles, multa_miles FROM pago_real WHERE id = ANY($1::text[])' + (conSede ? ' AND sede = $2' : '') + ' ORDER BY id, fecha',
      [ids],
      { sede: ss, conExtra: false },
    ),
    conRespaldo(
      consultar,
      (extra, conSede) =>
        'SELECT corrida, id, sim_pagado, sim_saldo_final, sim_pendientes, sim_mora, sim_cobranza, sim_interes, sim_servicios, ' +
        'sim_cuotas_vencidas_final, sim_cuotas_vencidas_max, sim_marca_recuperacion, sim_periodo_marca, sim_estado_final, ' +
        'sim_periodos, periodo_corte, real_ea_con_multas' + (extra ? ', sim_proyectado_total' : '') + ' FROM resultado WHERE id = ANY($1::text[])' + (conSede ? ' AND sede = $2' : ''),
      [ids],
      { sede: ss },
    ),
    consultarPeriodos(consultar, ids, ss),
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

async function listarActivos(consultar, sede) {
  const estado = {}
  const filas = await conRespaldo(
    consultar,
    (extra, conSede) =>
    'SELECT c.id, c.contrato, c.fecha_inicio, c.cuotas, c.cuota_real_miles, c.inversion, c.inversion_imputada, c.pagos_reales, ' +
      'c.dias_justificados, c.multas_reales_miles, ' + (extra ? 'c.valor_total_miles, c.saldo_real_miles, r.sim_proyectado_total, ' : '') +
      'COALESCE(p.real_pagado, 0) AS real_pagado, ' +
      'r.corrida, r.sim_pagado, r.sim_saldo_final, r.sim_pendientes, r.sim_mora, r.sim_cobranza, r.sim_interes, r.sim_servicios, ' +
      'r.sim_cuotas_vencidas_final, r.sim_cuotas_vencidas_max, r.sim_marca_recuperacion, r.sim_periodo_marca, r.sim_estado_final, ' +
      'r.sim_periodos, r.periodo_corte ' +
      'FROM contrato c JOIN resultado r ON r.id = c.id' + (conSede ? ' AND r.sede = c.sede' : '') + ' ' +
      'LEFT JOIN (SELECT id, SUM(valor_miles) * 1000 AS real_pagado FROM pago_real' + (conSede ? ' WHERE sede = $2' : '') + ' GROUP BY id) p ON p.id = c.id ' +
      "WHERE c.estado_real = 'VIGENTE' AND r.corrida = ANY($1::text[]) " + (conSede ? 'AND c.sede = $2 ' : '') +
      'ORDER BY c.fecha_inicio, c.id',
    [CORRIDAS_ACTIVOS],
    { sede, estado },
  )
  if (sede && !estado.conSede && sede !== SEDE_DEFECTO) return []
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

const FLUJO_VACIO = { corte: null, contratos: 0, meses: [] }

// Con sede, todas las tablas se filtran y se cruzan por (sede, contrato). Si la base aún no tiene la columna (42703),
// CARTAGENA se consulta sin filtro y cualquier otra sede devuelve un flujo vacío.
async function flujoActivos(consultar, sede) {
  if (!sede) return flujoSede(consultar, undefined)
  try {
    return await flujoSede(consultar, sede)
  } catch (e) {
    if (!e || e.code !== '42703') throw e
    return sede === SEDE_DEFECTO ? flujoSede(consultar, undefined) : FLUJO_VACIO
  }
}

async function flujoSede(consultar, sede) {
  // Fragmentos de SQL que solo existen cuando se filtra por sede; sin sede las consultas quedan como siempre.
  const pS = sede ? [sede] : []
  const corteFila = await consultar(
    sede ? 'SELECT (SELECT MAX(corte) FROM corte_activos WHERE sede = $1) AS corte' : 'SELECT (SELECT MAX(corte) FROM corte_activos) AS corte', pS)
  const corte = fecha(corteFila[0]?.corte)
  const mesCorte = corte ? corte.slice(0, 7) : '9999-99'
  const mesDe = "to_char(date_trunc('month', p.fecha_vencimiento), 'YYYY-MM')"
  const [cerrados, hoy, motos, realCerrado, realCorte, cartas] = await Promise.all([
    consultar(
      `SELECT ${mesDe} AS mes, p.corrida, ${SUMAS}, COUNT(DISTINCT p.id) AS contratos FROM periodo p ` +
        `JOIN contrato c ON c.id = p.id${sede ? ' AND c.sede = p.sede' : ''} JOIN activas_fin_mes a ON a.contrato = c.contrato${sede ? ' AND a.sede = c.sede' : ''} AND a.mes = ${mesDe} ` +
        `WHERE p.corrida = ANY($1::text[]) AND p.fecha_vencimiento IS NOT NULL AND ${mesDe} < $2${sede ? ' AND p.sede = $3' : ''} GROUP BY 1, 2 ORDER BY 1, 2`,
      [CORRIDAS_ACTIVOS, mesCorte, ...pS],
    ),
    consultar(
      `SELECT ${mesDe} AS mes, p.corrida, ${SUMAS}, COUNT(DISTINCT p.id) AS contratos FROM proyeccion p ` +
        `WHERE p.corrida = ANY($1::text[]) AND ${mesDe} >= $2${sede ? ' AND p.sede = $3' : ''} GROUP BY 1, 2 ORDER BY 1, 2`,
      [CORRIDAS_ACTIVOS, mesCorte, ...pS],
    ),
    consultar(sede ? 'SELECT mes, COUNT(*) AS motos FROM activas_fin_mes WHERE sede = $1 GROUP BY mes' : 'SELECT mes, COUNT(*) AS motos FROM activas_fin_mes GROUP BY mes', pS),
    consultar(
      "SELECT to_char(date_trunc('month', r.fecha), 'YYYY-MM') AS mes, SUM(r.valor_miles) * 1000 AS pagos FROM pago_real r " +
        "JOIN contrato c ON c.id = r.id" + (sede ? ' AND c.sede = r.sede' : '') + " JOIN activas_fin_mes a ON a.contrato = c.contrato" + (sede ? ' AND a.sede = c.sede' : '') + " AND a.mes = to_char(date_trunc('month', r.fecha), 'YYYY-MM')" + (sede ? ' WHERE r.sede = $1' : '') + ' GROUP BY 1',
      pS,
    ),
    consultar(
      "SELECT SUM(r.valor_miles) * 1000 AS pagos FROM pago_real r JOIN contrato c ON c.id = r.id " +
        "WHERE c.estado_real = 'VIGENTE' AND to_char(date_trunc('month', r.fecha), 'YYYY-MM') = $1" + (sede ? ' AND c.sede = $2 AND r.sede = $2' : ''),
      [mesCorte, ...pS],
    ),
    // Pagos por carta (liquidaciones de cierre, np_his_abonos.CARTA = 'SI' salvo pronto pago) de las motos activas ese último día del mes.
    // No son cuotas: ni el motor ni el recaudo real los incluyen.
    consultar('SELECT mes, motivo, pagos, valor_miles FROM carta_fin_mes' + (sede ? ' WHERE sede = $1' : ''), pS),
  ])
  const cartaPorMes = new Map()
  for (const c of cartas) {
    const x = cartaPorMes.get(c.mes) ?? { pagos: 0, valor: 0, porMotivo: {} }
    x.pagos += num(c.pagos) ?? 0
    x.valor += (num(c.valor_miles) ?? 0) * 1000
    x.porMotivo[c.motivo] = { pagos: num(c.pagos), valor: (num(c.valor_miles) ?? 0) * 1000 }
    cartaPorMes.set(c.mes, x)
  }
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
        carta: estado === 'cerrado' ? (cartaPorMes.get(f.mes) ?? { pagos: 0, valor: 0, porMotivo: {} }) : null,
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
const TTL_DEFECTO_SEG = 600
const TAMANO_DEFECTO = 200
const TAMANO_MAX = 1000

function ttlDesdeEntorno() {
  const v = process.env.CACHE_TTL_SEGUNDOS
  if (v === undefined || v === '') return TTL_DEFECTO_SEG * 1000
  const n = Number(v)
  return Number.isFinite(n) && n >= 0 ? n * 1000 : TTL_DEFECTO_SEG * 1000
}

const entero = (v) => (typeof v === 'string' && /^[0-9]{1,9}$/.test(v) ? Number(v) : null)

// Cache en memoria (una instancia) de los modos pesados. Guarda la promesa para que dos peticiones simultáneas compartan una consulta.
function crearCache(ttlMs, ahora) {
  const entradas = new Map()
  return async (clave, calcular) => {
    if (ttlMs <= 0) return calcular()
    const e = entradas.get(clave)
    if (e && ahora() - e.t < ttlMs) return e.promesa
    const promesa = calcular()
    entradas.set(clave, { t: ahora(), promesa })
    promesa.catch(() => { if (entradas.get(clave)?.promesa === promesa) entradas.delete(clave) })
    return promesa
  }
}

// Sedes con datos. Sin la columna `sede` (migración 003 pendiente) solo hay CARTAGENA.
async function sedesDisponibles(consultar) {
  try {
    const filas = await consultar('SELECT DISTINCT sede FROM contrato', [])
    const con = new Set(filas.map((f) => f.sede))
    return { sedes: SEDES.filter((x) => con.has(x)), todas: SEDES, defecto: SEDE_DEFECTO }
  } catch (e) {
    if (!e || e.code !== '42703') throw e
    return { sedes: [SEDE_DEFECTO], todas: SEDES, defecto: SEDE_DEFECTO }
  }
}

function crearManejador({ consultar, claveEsperada, limitador = crearLimitador(), registrar = () => {}, ttlMs = ttlDesdeEntorno(), ahora = Date.now }) {
  const conCache = crearCache(ttlMs, ahora)
  // Respuesta de un modo cacheable: Cache-Control privado + ETag, y 304 si el cliente ya tiene esa versión.
  const cacheable = (headers, cuerpo, ifNoneMatch) => {
    const etag = '"' + crypto.createHash('sha256').update(JSON.stringify(cuerpo)).digest('hex').slice(0, 32) + '"'
    const h = { ...headers, 'Cache-Control': 'private, max-age=300', ETag: etag }
    const coincide = typeof ifNoneMatch === 'string' && ifNoneMatch.split(',').some((x) => x.trim().replace(/^W\//, '') === etag)
    return coincide ? { status: 304, headers: h } : { status: 200, headers: h, cuerpo }
  }
  return async function manejar({ metodo, origen, clave, ip, numero, activos, flujo, sedes, sede: sedeCruda, pagina, tamano, ifNoneMatch }) {
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
    const sede = normalizarSede(sedeCruda)
    if (sede === null) return { status: 400, headers, cuerpo: { error: 'sede_invalida' } }
    // Sin ?sede= no se filtra (comportamiento histórico, respuesta idéntica); con ella, todo se filtra y la respuesta la incluye.
    const conSede = (cuerpo) => (sede ? { sede, ...cuerpo } : cuerpo)
    if (sedes === '1') {
      try {
        return cacheable(headers, await conCache('sedes', () => sedesDisponibles(consultar)), ifNoneMatch)
      } catch (e) {
        registrar(`Listado de sedes falló: ${e && e.code ? e.code : 'error'}`)
        return { status: 500, headers, cuerpo: { error: 'interno' } }
      }
    }
    if (flujo === '1') {
      try {
        const datos = await conCache(`flujo|${sede ?? ''}`, () => flujoActivos(consultar, sede))
        return cacheable(headers, conSede(datos), ifNoneMatch)
      } catch (e) {
        registrar(`Flujo de activos falló: ${e && e.code ? e.code : 'error'}`)
        return { status: 500, headers, cuerpo: { error: 'interno' } }
      }
    }
    if (activos === '1') {
      try {
        const contratos = await conCache(`activos|${sede ?? ''}`, () => listarActivos(consultar, sede))
        // Sin `pagina` ni `tamano` se devuelve todo (formato histórico); con alguno de los dos, paginado con metadatos.
        if (pagina === undefined && tamano === undefined) return cacheable(headers, conSede({ activos: contratos.length, contratos }), ifNoneMatch)
        const pag = pagina === undefined ? 1 : entero(pagina)
        const tam = tamano === undefined ? TAMANO_DEFECTO : entero(tamano)
        if (!pag || !tam || pag < 1 || tam < 1 || tam > TAMANO_MAX) return { status: 400, headers, cuerpo: { error: 'paginacion_invalida' } }
        const total = contratos.length
        const cuerpo = conSede({
          activos: total, total, pagina: pag, tamano: tam, paginas: Math.ceil(total / tam),
          contratos: contratos.slice((pag - 1) * tam, pag * tam),
        })
        return cacheable(headers, cuerpo, ifNoneMatch)
      } catch (e) {
        registrar(`Listado de activos falló: ${e && e.code ? e.code : 'error'}`)
        return { status: 500, headers, cuerpo: { error: 'interno' } }
      }
    }
    if (typeof numero !== 'string' || !/^[0-9]{1,10}$/.test(numero)) {
      return { status: 400, headers, cuerpo: { error: 'numero_invalido' } }
    }
    try {
      const episodios = await buscar(numero, consultar, sede)
      if (!episodios.length) return { status: 404, headers, cuerpo: { error: 'no_encontrado' } }
      return { status: 200, headers, cuerpo: conSede({ numero, episodios }) }
    } catch (e) {
      registrar(`Consulta de contrato falló: ${e && e.code ? e.code : 'error'}`)
      return { status: 500, headers, cuerpo: { error: 'interno' } }
    }
  }
}

module.exports = { crearManejador, crearLimitador, claveValida, variantes, normalizarSede, ORIGENES, SEDES, SEDE_DEFECTO }
