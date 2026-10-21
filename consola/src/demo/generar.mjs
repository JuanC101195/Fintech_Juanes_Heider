// Genera los fixtures del modo demostración en src/demo/fixtures/*.json siguiendo el contrato del API.
// Es un mini-motor semanal de ilustración: NO reemplaza al motor Java. Cuando existan los JSON reales
// de docs/api-ejemplos/, basta con copiarlos en src/demo/fixtures/ con los mismos nombres.
// Uso: npm run fixtures
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const salida = path.join(path.dirname(fileURLToPath(import.meta.url)), 'fixtures')
rmSync(salida, { recursive: true, force: true })
mkdirSync(salida, { recursive: true })
const guardar = (nombre, datos) => writeFileSync(path.join(salida, nombre), JSON.stringify(datos) + '\n')

// ---------- utilidades numéricas (solo para fabricar fixtures) ----------
const EA = 0.8762
const MONTO = 5904400
const tasaPeriodo = (ea, ppa) => (1 + ea) ** (1 / ppa) - 1
const pmt = (r, n, pv) => (pv * r) / (1 - (1 + r) ** -n)
const d2 = (x) => (Math.abs(x) < 0.005 ? 0 : x).toFixed(2)
const d3 = (x) => x.toFixed(3)
const entero = (x) => String(Math.round(x))

// ---------- catálogo y reglas ----------
guardar('catalogo.json', {
  lineas: [{ codigo: 'CPPU', nombre: 'Crédito de moto estándar', tasaEA: '0.8762', plazoMaximoMeses: 24, periodicidades: ['SEMANAL', 'QUINCENAL', 'MENSUAL'] }],
  periodicidades: [
    { codigo: 'SEMANAL', periodosPorAnio: 52, adjetivo: 'semanal', porConfirmar: false },
    { codigo: 'QUINCENAL', periodosPorAnio: 24, adjetivo: 'quincenal', porConfirmar: true },
    { codigo: 'MENSUAL', periodosPorAnio: 12, adjetivo: 'mensual', porConfirmar: false },
  ],
  tiposInstruccion: ['PAGO_CONTRACTUAL', 'PAGO_PARCIAL', 'PAGO_VALOR', 'SIN_PAGO', 'ABONO_EXTRA', 'PREPAGO_TOTAL', 'USURA_VIGENTE', 'CAMBIO_TASA', 'PAGO_AL_DIA', 'CIERRE_POR_RECUPERACION'],
  modalidadesAbono: ['MENOR_PLAZO', 'MENOR_CUOTA'],
  modalidadesCambioTasa: ['MISMA_FECHA_FINAL', 'MISMA_CUOTA'],
  tiposRecuperacion: ['DACION', 'RETOMA'],
  valoresPorDefecto: { valorMoto: '5100000', pagoInicial: '0', alistamiento: '300000', gps: '350000', gastosComerciales: '154400' },
})

const base = {
  prelacion: 'SERVICIOS_ANTES_DE_CAPITAL', formulaMora: 'VENCIDO_MAS_DIAS', baseInteres: 'SALDO_VIGENTE', topeCobranzaPorPago: 'SIN_TOPE',
  baseServicios: 'MONTO_INICIAL', serviciosDespuesDelPlazo: 'false', moraAntesDeRecuperacion: 'true', aplicacionAbono: 'SOLO_CAPITAL',
  interesPrepagoPorDias: 'false', margenUsura: '1', modalidadUsura: 'MISMA_FECHA_FINAL',
}
guardar('reglas.json', {
  perfiles: { EXCEL: base, NEGOCIO: { ...base, aplicacionAbono: 'PRIMERO_PENDIENTES', interesPrepagoPorDias: 'true', modalidadUsura: 'MISMA_CUOTA' } },
  claves: [
    { clave: 'prelacion', descripcion: 'Orden en que un pago cubre cada concepto: cobranza, mora, interés corriente, servicios y capital.', fuente: 'Excel, fórmulas de los casos 8 y 9 (P1)' },
    { clave: 'formulaMora', descripcion: 'Cómo se calcula la mora: capital vencido por la tasa del periodo más el capital de la cuota por los días de atraso.', fuente: 'Excel, casos 8 y 9' },
    { clave: 'baseInteres', descripcion: 'Sobre qué saldo se cobra el interés corriente: solo el capital vigente (el vencido paga únicamente mora).', fuente: 'P-03' },
    { clave: 'topeCobranzaPorPago', descripcion: 'Máximo de gastos de cobranza que puede cubrir un solo pago.', fuente: 'Por confirmar con Diego' },
    { clave: 'baseServicios', descripcion: 'Fondo de garantías y seguro se calculan sobre el monto financiado inicial.', fuente: 'P-07' },
    { clave: 'serviciosDespuesDelPlazo', descripcion: 'Si los servicios se siguen cobrando cuando el crédito pasa del plazo original.', fuente: 'Por confirmar con Diego' },
    { clave: 'moraAntesDeRecuperacion', descripcion: 'En dación y retoma se causan mora y servicios en las semanas sin pago.', fuente: 'P-16' },
    { clave: 'aplicacionAbono', descripcion: 'Un abono extra paga primero los pendientes por prelación y el resto va a capital vigente.', fuente: 'A4' },
    { clave: 'interesPrepagoPorDias', descripcion: 'El prepago total cobra el interés corriente de los días transcurridos desde la última cuota.', fuente: 'A6' },
    { clave: 'margenUsura', descripcion: 'Puntos porcentuales de EA que la nueva tasa queda por debajo de la usura vigente.', fuente: 'T2' },
    { clave: 'modalidadUsura', descripcion: 'Qué se conserva cuando la usura obliga a bajar la tasa: la cuota (baja el plazo) o la fecha final.', fuente: 'USU-6' },
  ],
})

// ---------- simulaciones ----------
function simulacion(periodicidad, ppa, cuotas, cuotaFin, cuotaTot, totalPagado, partesServ) {
  const r = tasaPeriodo(EA, ppa)
  const serv = cuotaTot - cuotaFin
  const plan = []
  let saldo = MONTO
  let sumInt = 0
  let sumServ = 0
  let sumTot = 0
  for (let k = 1; k <= cuotas; k++) {
    const interes = saldo * r
    const ultima = k === cuotas
    const capital = ultima ? saldo : cuotaFin - interes
    let servK = serv
    if (ultima) servK = totalPagado - sumTot - (capital + interes) // la última cuota absorbe el redondeo
    saldo = ultima ? 0 : saldo - capital
    const fg = servK * partesServ[0]
    const asis = servK * partesServ[1]
    plan.push({ periodo: k, interes: d2(interes), capital: d2(capital), fondoGarantias: d2(fg), asistencia: d2(asis), seguro: d2(servK - fg - asis), cuotaTotal: d2(capital + interes + servK), saldo: d2(saldo) })
    sumInt += interes
    sumServ += servK
    sumTot += capital + interes + servK
  }
  const resumen = {
    montoFinanciado: String(MONTO), tasaEA: '0.8762', tasaPeriodo: r.toFixed(4), cuotas, cuotaFinanciera: d2(cuotaFin), cuotaTotal: d2(cuotaTot),
    totalPagado: d2(totalPagado), totalIntereses: d2(sumInt), totalServicios: d2(sumServ), periodicidad,
    porConfirmar: periodicidad === 'QUINCENAL' ? ['QUINCENAL: 24 periodos/año por confirmar con Diego'] : [],
  }
  return { resumen, plan }
}
const rSem = tasaPeriodo(EA, 52)
const cfSem = Math.round(pmt(rSem, 104, MONTO))
guardar('simulacion-semanal.json', simulacion('SEMANAL', 52, 104, cfSem, cfSem + 68287, (cfSem + 68287) * 104, [0.45, 0.35]))
guardar('simulacion-quincenal.json', simulacion('QUINCENAL', 24, 48, 219093, 355667, 17072015, [0.45, 0.35]))
guardar('simulacion-mensual.json', simulacion('MENSUAL', 12, 24, 444006, 717154, 17211702, [0.45, 0.35]))

// ---------- mini-motor semanal para los casos ----------
const MESES = (p) => Math.ceil(p / 4.345)
const DESCRIPCION_USURA = 0.01

function correr({ principal, cuota, cuotas = 104, serv, ins, perfil, eaInicial = EA }) {
  const negocio = perfil === 'NEGOCIO'
  let ea = eaInicial
  let r = tasaPeriodo(ea, 52)
  let sv = principal
  let cv = 0, cxcI = 0, cxcM = 0, cxcS = 0, cxcC = 0
  let C = cuota
  let finPlan = cuotas
  let venc = 0, maxVenc = 0, pagadoCliente = 0
  let tasaPendiente = null
  const filas = [], asientos = []
  const porPeriodo = new Map()
  for (const i of ins) porPeriodo.set(i.periodo, [...(porPeriodo.get(i.periodo) ?? []), i])

  const pend = () => cxcC + cxcM + cxcI + cxcS + cv
  function pagarPendientes(monto) {
    const sal = { cobranza: 0, mora: 0, interesCorriente: 0, servicios: 0, capitalVencido: 0 }
    const tomar = (clave, disp) => { const t = Math.min(monto, disp); sal[clave] = t; monto -= t; return disp - t }
    cxcC = tomar('cobranza', cxcC); cxcM = tomar('mora', cxcM); cxcI = tomar('interesCorriente', cxcI); cxcS = tomar('servicios', cxcS); cv = tomar('capitalVencido', cv)
    sal.total = sal.cobranza + sal.mora + sal.interesCorriente + sal.servicios + sal.capitalVencido
    return sal
  }

  for (let p = 1; p <= 260; p++) {
    if (sv < 0.005 && pend() < 0.005) break
    const lista = [...(porPeriodo.get(p) ?? [])]
    if (sv < 0.005 && pend() > 0.005) lista.push({ periodo: p, tipo: 'PAGO_AL_DIA' }) // liquida los pendientes al final
    const get = (t) => lista.find((x) => x.tipo === t)
    const saldoInicial = sv + cv
    const eventos = []

    if (tasaPendiente) {
      const t = tasaPendiente; tasaPendiente = null
      ea = t.ea; r = tasaPeriodo(ea, 52)
      if (t.modalidad === 'MISMA_FECHA_FINAL') C = pmt(r, Math.max(1, finPlan - p + 1), sv)
      eventos.push('cambio de tasa')
    }
    const ct = get('CAMBIO_TASA')
    if (ct) {
      ea = Number(ct.nuevaTasaEA); r = tasaPeriodo(ea, 52)
      if (ct.modalidad === 'MISMA_FECHA_FINAL') C = pmt(r, Math.max(1, finPlan - p + 1), sv)
      eventos.push('cambio de tasa')
    }

    const us = get('USURA_VIGENTE')
    if (us && ea > Number(us.usuraEA)) {
      tasaPendiente = { ea: Number(us.usuraEA) - DESCRIPCION_USURA, modalidad: negocio ? 'MISMA_CUOTA' : 'MISMA_FECHA_FINAL' }
      eventos.push('nueva usura')
    }

    // causación
    const interes = sv * r
    let capital = Math.min(Math.max(C - interes, 0), sv)
    if (sv - capital < 1 || p >= finPlan + 60) capital = sv
    const servicios = sv > 0 && p <= 86 ? serv : 0
    const rd = tasaPeriodo(ea, 365)

    let tipo = 'PAGO'
    let valor = interes + capital + servicios // pago a tiempo de la cuota total
    let dias = 0
    let causa = true
    const pc = get('PAGO_CONTRACTUAL'), pp = get('PAGO_PARCIAL'), pv = get('PAGO_VALOR'), sp = get('SIN_PAGO'), pad = get('PAGO_AL_DIA')
    if (pc) dias = pc.diasAtraso ?? 0
    if (pp) { dias = pp.diasAtraso ?? 0; valor *= Number(pp.fraccion); tipo = 'PARCIAL' }
    if (pv) { dias = pv.diasAtraso ?? 0; tipo = 'VALOR' }
    if (sp) { valor = 0; dias = 7; causa = sp.causaMoraYServicios !== false; tipo = 'SINPAGO' }

    const cobranza = causa && dias > 0 ? dias * 3000 : 0
    const mora = causa && (cv > 0 || dias > 0) ? cv * r + capital * ((1 + rd) ** dias - 1) : 0
    const servCausa = causa ? servicios : 0
    if (pv) valor = Number(pv.valor)
    if (pad) { dias = 0; tipo = 'ALDIA' }
    let disponible = pad ? interes + capital + servCausa + pend() : valor
    const pagoRecibido = disponible
    const tomar = (debido) => { const t = Math.min(disponible, debido); disponible -= t; return t }
    const pCob = tomar(cobranza), pMora = tomar(mora), pInt = tomar(interes), pServ = tomar(servCausa), pCap = tomar(capital)
    // lo que el pago no cubrió pasa a pendiente; el capital no pagado pasa a vencido
    cxcC += cobranza - pCob; cxcM += mora - pMora; cxcI += interes - pInt; cxcS += servCausa - pServ
    const capNoPagado = capital - pCap
    sv -= capital
    cv += capNoPagado
    let pendPagadosPago = { cobranza: 0, mora: 0, interesCorriente: 0, servicios: 0, capitalVencido: 0, total: 0 }
    if (pad || disponible > 0) { pendPagadosPago = pagarPendientes(pad ? pend() : disponible); disponible = 0 }
    const pagoCuota = pagoRecibido
    venc = pend() > 0.005 ? venc + (capNoPagado > 0.005 || pInt < interes - 0.005 ? 1 : 0) : 0
    if (pad) venc = 0

    // abono, prepago, cierre: después de la cuota
    let abonoExtra = 0, prepago = 0, interesPrepago = 0
    let pendExtra = { cobranza: 0, mora: 0, interesCorriente: 0, servicios: 0, capitalVencido: 0, total: 0 }
    const ab = get('ABONO_EXTRA'), pre = get('PREPAGO_TOTAL'), ci = get('CIERRE_POR_RECUPERACION')
    let cierre = null
    if (ab) {
      let v = Number(ab.valor)
      if (negocio) { pendExtra = pagarPendientes(v); v -= pendExtra.total }
      abonoExtra = v; sv -= v
      eventos.push(`abono ${ab.modalidad === 'MENOR_CUOTA' ? 'a menor cuota' : 'a menor plazo'}`)
      if (ab.modalidad === 'MENOR_CUOTA') C = pmt(r, Math.max(1, finPlan - p), sv)
      if (pend() < 0.005) venc = 0
    }
    if (pre) {
      const dd = pre.diasDesdeUltimaCuota ?? 0
      interesPrepago = negocio ? (sv * r * dd) / 7 : 0
      pendExtra = pagarPendientes(pend() + 1e-9)
      prepago = sv; sv = 0; venc = 0
      eventos.push('prepago total')
    }
    if (ci) {
      const vm = Number(ci.valorMoto)
      const deuda = sv + pend()
      cierre = { deuda, inventario: Math.min(vm, deuda), cxpDeudor: Math.max(vm - deuda, 0), faltante: Math.max(deuda - vm, 0), tipo: ci.tipoRecuperacion }
      sv = 0; cv = 0; cxcC = cxcM = cxcI = cxcS = 0
      eventos.push(ci.tipoRecuperacion === 'RETOMA' ? 'retoma de la moto' : 'dación en pago')
    }
    if (sp) eventos.push('sin pago')
    if (pp || pv) eventos.push('pago parcial')
    if (pad) eventos.push('pago al día')
    if (dias > 0 && !sp) eventos.push(`pago con ${dias} días de atraso`)

    maxVenc = Math.max(maxVenc, venc)
    const saldoFinal = sv + cv
    const cancelado = saldoFinal < 0.005 && pend() < 0.005 && !cierre
    const estado = cierre ? (cierre.tipo === 'RETOMA' ? 'RETOMADO' : 'RECUPERADO') : cancelado ? 'CANCELADO' : venc > 0 || pend() > 0.005 ? 'EN_MORA' : 'AL_DIA'
    const totalCliente = pagoCuota + abonoExtra + prepago + pendExtra.total + interesPrepago
    pagadoCliente += totalCliente
    const etiqueta = eventos.length ? eventos[0][0].toUpperCase() + eventos[0].slice(1) : 'Pago normal'
    const sextra = (o) => ({ cobranza: d3(o.cobranza), mora: d3(o.mora), interesCorriente: d3(o.interesCorriente), servicios: d3(o.servicios), capitalVencido: d3(o.capitalVencido), total: d3(o.total) })
    filas.push({
      periodo: p, mes: MESES(p), estado, etiqueta, evento: eventos.join(' + ') || 'PAGO_NORMAL',
      saldoInicial: d2(saldoInicial), tasaPeriodica: r.toFixed(6), cuotaFinanciera: d2(C),
      interesCorriente: d2(interes), interesMora: d2(mora), capitalContractual: d2(capital), cobranzaCausada: d2(cobranza),
      pagoRecibido: d2(pagoCuota), pagoCobranza: d2(pCob), pagoMora: d2(pMora), pagoInteresCorriente: d2(pInt), pagoServicios: d2(pServ), pagoCapital: d2(pCap),
      abonoExtra: ab ? d3(abonoExtra) : '0', prepagoCapital: d2(prepago),
      pendientesPagadosExtra: sextra(pendExtra),
      cxcCobranza: d2(cxcC), cxcMora: d2(cxcM), cxcInteresCorriente: d2(cxcI), cxcServicios: d2(cxcS),
      capitalVencido: d2(cv), capitalVigente: d2(sv), saldoFinal: ab ? d2(saldoFinal) : d2(saldoFinal), cuotasVencidas: venc,
      ...(cierre ? { deudaAExtinguir: d2(cierre.deuda), inventario: d2(cierre.inventario), cxpDeudor: d2(cierre.cxpDeudor) } : {}),
      ...(interesPrepago ? { interesPrepago: d2(interesPrepago) } : {}),
    })

    // asiento contable del periodo (partida doble)
    const lineas = []
    const dr = (cuenta, v) => v > 0.004 && lineas.push({ cuenta, debito: d2(v), credito: '0' })
    const cr = (cuenta, v) => v > 0.004 && lineas.push({ cuenta, debito: '0', credito: d2(v) })
    const cuotaCaja = pagoCuota + pendPagadosPago.total
    dr('CAJA', cuotaCaja + abonoExtra + pendExtra.total + prepago + interesPrepago)
    cr('CARTERA_CAPITAL', pCap + abonoExtra + prepago + pendPagadosPago.capitalVencido + pendExtra.capitalVencido)
    cr('INGRESO_INTERES_CORRIENTE', pInt + interesPrepago + pendPagadosPago.interesCorriente + pendExtra.interesCorriente)
    cr('INGRESO_INTERES_MORA', pMora + pendPagadosPago.mora + pendExtra.mora)
    cr('INGRESO_COBRANZA', pCob + pendPagadosPago.cobranza + pendExtra.cobranza)
    cr('CXP_SERVICIOS_TERCEROS', pServ + pendPagadosPago.servicios + pendExtra.servicios)
    const sinPagar = (interes - pInt) + (mora - pMora) + (cobranza - pCob) + (servCausa - pServ)
    if (sinPagar > 0.004) {
      dr('CXC_CLIENTE', sinPagar)
      cr('INGRESO_INTERES_CORRIENTE', interes - pInt)
      cr('INGRESO_INTERES_MORA', mora - pMora)
      cr('INGRESO_COBRANZA', cobranza - pCob)
      cr('CXP_SERVICIOS_TERCEROS', servCausa - pServ)
    }
    if (capNoPagado > 0.004) { dr('CARTERA_VENCIDA', capNoPagado); cr('CARTERA_VIGENTE', capNoPagado) }
    if (cierre) {
      dr('INVENTARIO_MOTOS', cierre.inventario)
      dr('PERDIDA_POR_DETERIORO', cierre.faltante)
      cr('CARTERA_CAPITAL', cierre.deuda - 0)
      cr('CXP_DEUDOR', cierre.cxpDeudor)
      // la deuda extinguida incluye capital y pendientes; se ajusta contra inventario + pérdida - excedente
    }
    const sumD = lineas.reduce((a, l) => a + Number(l.debito), 0)
    const sumC = lineas.reduce((a, l) => a + Number(l.credito), 0)
    if (lineas.length) {
      const evento = cierre ? (cierre.tipo === 'RETOMA' ? 'RETOMA' : 'DACION_EN_PAGO') : ab || pre ? 'ABONO_EXTRAORDINARIO' : sp ? 'CAUSACION_SIN_PAGO' : pad ? 'NORMALIZACION' : sinPagar > 0.004 ? 'PAGO_PARCIAL' : 'RECAUDO_CUOTA'
      asientos.push({ periodo: p, evento, lineas, cuadra: Math.abs(sumD - sumC) < 0.02 })
    }
    if (cierre) break
  }
  const ultima = filas.at(-1)
  return {
    resumen: { periodoFinal: ultima.periodo, estadoFinal: ultima.estado, saldoFinal: ultima.saldoFinal, totalPagadoCliente: d2(pagadoCliente), cuotasVencidasMax: maxVenc },
    movimientos: filas,
    asientos,
  }
}

// ---------- los 13 casos ----------
const rSemanal = tasaPeriodo(EA, 52)
const cuotaBase = pmt(rSemanal, 104, MONTO)
const SERV = 63000
const ejecutar = (ins, perfil, extra = {}) => correr({ principal: MONTO, cuota: cuotaBase, serv: SERV, ins, perfil, ...extra })
const cred = { linea: 'CPPU', periodicidad: 'SEMANAL', cuotas: 104, meses: null, valorMoto: '5100000', pagoInicial: '0', perfilReglas: 'NEGOCIO' }

const sinPagos = (desde, hasta) => Array.from({ length: hasta - desde + 1 }, (_, k) => ({ periodo: desde + k, tipo: 'SIN_PAGO', causaMoraYServicios: true }))
const casos = [
  { numero: 1, nombre: 'Operación normal', descripcion: 'El cliente paga todas las cuotas a tiempo hasta cancelar.', fuente: 'Excel 11-oct, hoja Caso1', ins: [] },
  { numero: 2, nombre: 'Cambio de tasa', descripcion: 'La tasa baja en la semana 30 y se conserva la fecha final: se recalcula la cuota.', fuente: 'Excel 11-oct, hoja Caso2', ins: [{ periodo: 30, tipo: 'CAMBIO_TASA', nuevaTasaEA: '0.70', modalidad: 'MISMA_FECHA_FINAL' }] },
  { numero: 3, nombre: 'Dación en pago', descripcion: 'Cinco semanas sin pago y entrega de la moto por menos de la deuda: queda un faltante.', fuente: 'Excel 11-oct, hoja Caso3', ins: [...sinPagos(20, 24), { periodo: 25, tipo: 'CIERRE_POR_RECUPERACION', tipoRecuperacion: 'DACION', valorMoto: '3200000' }] },
  { numero: 4, nombre: 'Retoma con excedente', descripcion: 'Cuatro semanas sin pago y retoma de la moto por más de la deuda: el excedente se devuelve al deudor.', fuente: 'Excel 11-oct, hoja Caso4', ins: [...sinPagos(30, 33), { periodo: 34, tipo: 'CIERRE_POR_RECUPERACION', tipoRecuperacion: 'RETOMA', valorMoto: '5500000' }] },
  { numero: 5, nombre: 'Pago anticipado total (prepago)', descripcion: 'En la semana 26 el cliente paga el saldo completo.', fuente: 'Excel 11-oct, hoja Caso5', ins: [{ periodo: 26, tipo: 'PREPAGO_TOTAL', diasDesdeUltimaCuota: 3 }] },
  { numero: 6, nombre: 'Abono extra como menor plazo', descripcion: 'Abono de 1.500.000 en la semana 20; se mantiene la cuota y baja el número de cuotas.', fuente: 'Excel 11-oct, hoja Caso6', ins: [{ periodo: 20, tipo: 'ABONO_EXTRA', valor: '1500000', modalidad: 'MENOR_PLAZO' }] },
  { numero: 7, nombre: 'Abono extra como menor cuota', descripcion: 'Abono de 1.500.000 en la semana 20; se mantiene el plazo y baja la cuota.', fuente: 'Excel 11-oct, hoja Caso7', ins: [{ periodo: 20, tipo: 'ABONO_EXTRA', valor: '1500000', modalidad: 'MENOR_CUOTA' }] },
  { numero: 8, nombre: 'Pago inferior', descripcion: 'En las semanas 15 y 16 el cliente paga solo el 40 % de la cuota.', fuente: 'Excel 11-oct, hoja Caso8', ins: [{ periodo: 15, tipo: 'PAGO_PARCIAL', fraccion: '0.4', diasAtraso: 0 }, { periodo: 16, tipo: 'PAGO_PARCIAL', fraccion: '0.4', diasAtraso: 0 }] },
  { numero: 9, nombre: 'Pago tardío y gastos de cobranza', descripcion: 'En la semana 12 paga la cuota con 10 días de atraso: se causan mora y cobranza.', fuente: 'Excel 11-oct, hoja Caso9', ins: [{ periodo: 12, tipo: 'PAGO_CONTRACTUAL', diasAtraso: 10 }] },
  { numero: 10, nombre: 'Cambio de tasa conservando la cuota', descripcion: 'La tasa baja en la semana 30 y se conserva la cuota: el crédito termina antes.', fuente: 'Excel 11-oct, hoja Caso10', ins: [{ periodo: 30, tipo: 'CAMBIO_TASA', nuevaTasaEA: '0.70', modalidad: 'MISMA_CUOTA' }] },
  { numero: 11, nombre: 'Tasa de usura vigente', descripcion: 'En la semana 30 se publica una usura de 81 % EA; la tasa baja a 80 % EA desde la cuota siguiente.', fuente: 'Respuestas de Diego, ronda 3 (USU-6)', ins: [{ periodo: 30, tipo: 'USURA_VIGENTE', usuraEA: '0.81' }] },
  { numero: 12, nombre: 'Normalización', descripcion: 'Tres semanas sin pago y en la 23 el cliente se pone al día.', fuente: 'Excel 11-oct, hoja Caso12', ins: [...sinPagos(20, 22), { periodo: 23, tipo: 'PAGO_AL_DIA' }] },
]

// Caso 13: abono extra con pendientes. Se calibra para que en la semana 42 salgan las cifras acordadas:
// pendientes 317.354,805 (se muestran 317.354,81), abono 1.500.000, 1.182.645,195 a capital vigente y saldo 3.169.909,34.
const SALDO_42 = 3169909.34
const PEND_42 = 317354.805
const ins13 = [...sinPagos(40, 41), { periodo: 42, tipo: 'ABONO_EXTRA', valor: '1500000', modalidad: 'MENOR_PLAZO' }]
const cuota13 = pmt(rSemanal, 41, SALDO_42) // menor plazo: queda 41 cuotas más, hasta la semana 83
let serv13 = 63000
let principal13 = MONTO
for (let vuelta = 0; vuelta < 8; vuelta++) {
  let lo = 2e6, hi = 12e6
  for (let k = 0; k < 90; k++) {
    principal13 = (lo + hi) / 2
    const f = correr({ principal: principal13, cuota: cuota13, serv: serv13, ins: ins13, perfil: 'NEGOCIO' }).movimientos.find((m) => m.periodo === 42)
    if (Number(f.saldoFinal) > SALDO_42) hi = principal13; else lo = principal13
  }
  const f = correr({ principal: principal13, cuota: cuota13, serv: serv13, ins: ins13, perfil: 'NEGOCIO' }).movimientos.find((m) => m.periodo === 42)
  const pagados = Number(f.pendientesPagadosExtra.total)
  serv13 += (PEND_42 - pagados) / 2
}
principal13 -= 0.004 // la bisección converge al borde del redondeo; se baja medio centavo para que muestre .34
const resultados13 = {
  NEGOCIO: correr({ principal: principal13, cuota: cuota13, serv: serv13, ins: ins13, perfil: 'NEGOCIO' }),
  EXCEL: correr({ principal: principal13, cuota: cuota13, serv: serv13, ins: ins13, perfil: 'EXCEL' }),
}
casos.push({ numero: 13, nombre: 'Abono extra con pendientes', descripcion: 'Dos semanas sin pago (40 y 41) y en la 42 un abono de 1.500.000: con la regla de negocio primero paga los pendientes y el resto va a capital.', fuente: 'Respuestas de Diego 2026-10-20 (A4)', ins: ins13 })

guardar('casos.json', casos.map(({ ins, ...c }) => c))
for (const c of casos) {
  const perfiles = c.numero === 13 || c.numero === 11 ? ['NEGOCIO', 'EXCEL'] : ['NEGOCIO']
  for (const perfil of perfiles) {
    const resultado = c.numero === 13
      ? resultados13[perfil]
      : correr({ principal: MONTO, cuota: cuotaBase, serv: SERV, ins: c.ins, perfil, eaInicial: EA })
    // caso 11: la usura baja la tasa desde la cuota siguiente (en NEGOCIO conserva la cuota; en EXCEL la recalcula)
    const { ins, ...caso } = c
    guardar(`caso-${c.numero}-${perfil}.json`, { ...resultado, caso, instrucciones: ins, credito: { ...cred, perfilReglas: perfil } })
  }
}

// ---------- resumen en consola para revisar a ojo ----------
const f42 = resultados13.NEGOCIO.movimientos.find((m) => m.periodo === 42)
console.log('caso 13 semana 42 NEGOCIO:', f42.pendientesPagadosExtra.total, f42.abonoExtra, f42.saldoFinal, '· termina en', resultados13.NEGOCIO.resumen.periodoFinal)
console.log('caso 13 EXCEL termina en', resultados13.EXCEL.resumen.periodoFinal, resultados13.EXCEL.resumen.estadoFinal)
for (const c of casos) console.log(c.numero, c.nombre)
