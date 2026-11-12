"""v11 (6-nov-2026, Diego): periodos.csv suma al final pago_fondo, pago_seguro, pago_asistencia (pago por concepto = causado + cxc anterior - cxc actual), saldo_en_mora (el del cociente de cobranza al cierre del periodo) y cxc_mora. v9 (2-nov-2026, ronda 13 de Diego): la marca de recuperacion se quita al bajar de 2 cuotas, el capital no cobrado de la primera cuota no causa interes. v8: corridas fija_* (tasa fija 88,44 %) y columna logica por periodo. Backtesting final (solo lectura en la BD de origen). Reproduce el comportamiento real de cada contrato en el motor, con:
 - tasa historica: al desembolso usura(fecha) - 1 punto, y cada mes la usura vigente (el motor la baja si la supera, regla de Diego);
   serie oficial de la Superfinanciera (datos.gov.co, TIBC x 1,5): CREDITO POPULAR PRODUCTIVO URBANO desde 2023-04, MICROCREDITO antes;
 - dos corridas: sin_justificaciones y con_justificaciones (D82 de Diego: la justificacion congela el calendario una semana sin pago, pero NO perdona cargos: mora y cobranza se causan igual por los dias de atraso);
 - inversion exacta (egrempfinl) para primeros contratos e imputada (regla de tres) para segundos o posteriores.
Escribe CSV en la carpeta de salida. Contratos reales: nunca al repo.
"""
import csv
import json
import math
import os
import statistics as st
import sys
import unicodedata
import urllib.request
from collections import defaultdict
from datetime import date, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import backtest_fase_a as b  # noqa: E402

b.API = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8099"
OUT = sys.argv[2] if len(sys.argv) > 2 else "salida"
LIMITE = int(sys.argv[3]) if len(sys.argv) > 3 else 0
HOY = date.today()
COMPLETOS = ("NORMAL", "PRONTO PAGO")
PERDONADAS = ("enfermo", "familiar muerto", "varado", "acidentado", "accidentado", "patio", "patios", "fiscalia", "en taller", "garantia")
SEDE = "CARTAGENA"
MARGEN = 0.01
TOLERANCIA = 0.01  # pagos redondeados: hasta 1 % por debajo de la cuota cuentan como cuota completa
FIJA = 0.8844   # corridas fija_*: tasa fija de 88,44 % EA durante todo el credito (sin tope de usura)
CORRIDAS = ("sin_justificaciones", "con_justificaciones", "max_sin_justificaciones", "max_con_justificaciones",
            "fija_sin_justificaciones", "fija_con_justificaciones")
# SOLO_ACTIVOS=1: solo las motos de motos_app (VIGENTE) y solo las corridas de tasa fija (para cargar_activos.py).
if os.environ.get("SOLO_ACTIVOS"):
    CORRIDAS = ("fija_sin_justificaciones", "fija_con_justificaciones")


def norm(t):
    t = unicodedata.normalize("NFD", (t or "").strip().lower())
    return "".join(c for c in t if unicodedata.category(c) != "Mn")


# ---- serie de usura ----
def cargar_usura():
    url = "https://www.datos.gov.co/resource/pare-7x5i.json?$limit=2000&$order=vigencia_desde"
    with urllib.request.urlopen(url, timeout=60) as r:
        filas = json.loads(r.read())
    out = []
    for f in filas:
        ibc = float(f["interes_bancario_corriente"].strip("%")) / 100
        out.append({"modalidad": f["modalidad"], "resolucion": f.get("resolucion"), "desde": f["vigencia_desde"][:10],
                    "hasta": f["vigencia_hasta"][:10], "ibc": ibc, "usura_ea": round(ibc * 1.5, 6),
                    "fuente": "datos.gov.co/pare-7x5i (Superfinanciera, TIBC); usura = 1,5 x IBC"})
    return out


def usura_en(serie, fecha):
    modalidad = "CREDITO POPULAR PRODUCTIVO URBANO" if fecha >= date(2023, 4, 1) else "MICROCREDITO"
    f = fecha.isoformat()
    for r in serie:
        if r["modalidad"] == modalidad and r["desde"] <= f <= r["hasta"]:
            return r["usura_ea"], modalidad
    # fecha fuera de la serie: la mas cercana
    cand = [r for r in serie if r["modalidad"] == modalidad]
    cand.sort(key=lambda r: abs((date.fromisoformat(r["desde"]) - fecha).days))
    return cand[0]["usura_ea"], modalidad


# ---- imputacion de inversion ----
def tabla_relaciones(cur):
    cur.execute(
        "SELECT r.no_canon, YEAR(r.fecha_inicio), i.egrempfinl / (r.cuota*r.no_canon*1000) FROM rentabilidad_activos r "
        "JOIN motos_inv i ON i.contrato=r.contrato WHERE r.contrato REGEXP '^[0-9]+$' AND r.cuota>0 AND r.no_canon>0 AND i.egrempfinl>0 "
        "AND (SELECT COUNT(DISTINCT x.fecha_inicio) FROM rentabilidad_activos x WHERE x.contrato=r.contrato)=1 GROUP BY r.contrato")
    por_ny, por_n, todos = defaultdict(list), defaultdict(list), []
    for n, anio, rel in cur.fetchall():
        if 0.15 <= float(rel) <= 0.7:
            por_ny[(n, anio)].append(float(rel))
            por_n[n].append(float(rel))
            todos.append(float(rel))
    return por_ny, por_n, st.median(todos)


def relacion(tabla, n, anio):
    por_ny, por_n, glob = tabla
    if len(por_ny.get((n, anio), [])) >= 15:
        return st.median(por_ny[(n, anio)])
    if len(por_n.get(n, [])) >= 15:
        return st.median(por_n[n])
    return glob


# ---- instrucciones ----
def instrucciones(esp, pagos, multas_f, ini, fin, concepto, cuota_miles, cuota_sim, serie, justif, es_vig, margen=MARGEN, fija=None):
    """Un PAGO_VALOR por periodo con el dinero real escalado (abono + multa): todo se aplica por prelacion, nada se descarta.
    - bolsa de efectivo: el pago hecho antes del vencimiento (o de mas) se lleva al periodo que lo necesita;
    - justificaciones: la semana justificada sin pago corre el calendario una semana (no cuenta como falta);
    - vigentes: solo ventanas cerradas;
    - cuotas vencidas por efectivo (cv_ef): lo que debia el cliente menos lo que entrego, en cuotas.
    Devuelve (instrucciones, info por periodo, tasa0, modalidad, vencimientos usados, ultimo periodo emitido)."""
    n = len(esp)
    escala = 1000.0                           # sin escalado: el motor recibe exactamente lo que pago el cliente (miles -> pesos)
    cuota_real = cuota_miles * 1000.0
    ins, info, usados = [], {}, []
    ult, ult_dia = 0, None
    ea0, mod = usura_en(serie, ini)
    tasa0 = fija if fija else round(max(ea0 - margen, 0.01), 4)
    ins.append({"periodo": 1, "tipo": "CAMBIO_TASA", "nuevaTasaEA": str(tasa0), "modalidad": "MISMA_FECHA_FINAL"})
    tasa_act = tasa0
    mes_prev, desplaz = None, 0
    bolsa = acum_pagado = acum_recibido = 0.0   # pesos del motor
    lo_fijo, k, ultimo = None, 0, 0
    while k < n:
        d = esp[k] + timedelta(days=7 * desplaz)
        if d > fin:
            break
        lo = lo_fijo if lo_fijo is not None else (ini + timedelta(days=1) if k == 0 else d)
        sig = esp[k + 1] + timedelta(days=7 * desplaz) if k + 1 < n else None
        if es_vig and sig is not None and sig > fin + timedelta(days=1):
            break                                  # ventana abierta: todavia no se puede juzgar
        hi = min(sig, fin + timedelta(days=1)) if sig is not None else fin + timedelta(days=1)
        en = [(f, v) for f, v in pagos if lo <= f < hi]
        total = sum(v for _, v in en)
        multa_miles = sum(m for f, m in multas_f if lo <= f < hi)
        multa_w = multa_miles * escala
        just = any(lo <= j < hi for j in justif)
        bolsa += total * escala
        acum_recibido += total * escala
        ultima = sig is None or (sig > fin + timedelta(days=1) and not es_vig)
        debe = cuota_real * (k + 1) - acum_pagado
        pago = bolsa if ultima else min(bolsa, max(debe, 0.0) + 0.5)
        sin_dinero = pago <= 0.5 and multa_w <= 0
        if sin_dinero and just and not en:
            desplaz += 1                           # semana justificada sin pago: se corre el calendario
            lo_fijo = lo
            continue
        lo_fijo = None
        usados.append(d)
        if not fija and k > 0 and (d.year, d.month) != mes_prev:
            u = round(usura_en(serie, d)[0], 4)
            if margen == MARGEN:
                ins.append({"periodo": k + 1, "tipo": "USURA_VIGENTE", "usuraEA": str(u)})
            elif u < tasa_act:   # tasa maxima: el motor solo baja cuando la usura baja (sin margen)
                ins.append({"periodo": k + 1, "tipo": "CAMBIO_TASA", "nuevaTasaEA": str(u), "modalidad": "MISMA_CUOTA"})
                tasa_act = u
        mes_prev = (d.year, d.month)
        dias_primera = (d - ini).days
        frac1 = min(1.0, dias_primera / 7) if k == 0 and dias_primera < 7 else 1.0
        cv_ef = int(max(0.0, cuota_sim * (k + frac1) - acum_recibido) / cuota_sim + 0.02)
        dias = 0 if not en else int(min(max(round(sum(max(0, (f - d).days) * v for f, v in en) / total), 0), 3650))
        if sin_dinero:
            ins.append({"periodo": k + 1, "tipo": "SIN_PAGO", "causaMoraYServicios": True})
            info[k + 1] = ("SIN_PAGO", 0.0, 0, just, cv_ef)
        else:
            ins.append({"periodo": k + 1, "tipo": "PAGO_VALOR", "valor": str(math.ceil(pago + multa_w)), "diasAtraso": dias})
            acum_pagado += pago
            bolsa -= pago
            info[k + 1] = ("PAGO_VALOR", (total + multa_miles) * 1000, dias, just, cv_ef)
        if en:
            ult, ult_dia = k + 1, max(f for f, _ in en)
        ultimo = k + 1
        k += 1
    if concepto == "PRONTO PAGO" and ult and ult_dia:
        ins = [x for x in ins if x["periodo"] <= ult]
        dias = max(0, (ult_dia - usados[ult - 1]).days)
        ins = [x for x in ins if not (x["periodo"] == ult and x["tipo"] in ("PAGO_VALOR", "PAGO_CONTRACTUAL"))]
        ins.append({"periodo": ult, "tipo": "PREPAGO_TOTAL", "diasDesdeUltimaCuota": min(dias, 3650)})
        info[ult] = ("PREPAGO_TOTAL", info[ult][1], dias, info[ult][3], info[ult][4])
        ultimo = ult
    return ins, info, tasa0, mod, usados, ultimo


_CUOTA = {}


def cuota_total(inv, n, tasa0):  # la tasa inicial forma parte de la clave
    clave = (int(inv), int(n), tasa0)
    if clave not in _CUOTA:
        r = b.post({"credito": cred(inv, n), "perfilReglas": "NEGOCIO", "incluirAsientos": False,
                    "instrucciones": [{"periodo": 1, "tipo": "CAMBIO_TASA", "nuevaTasaEA": str(tasa0), "modalidad": "MISMA_FECHA_FINAL"}]})
        m = r["movimientos"][1]
        _CUOTA[clave] = b.num(m.get("cuotaFinanciera")) + b.num((m.get("serviciosCausados") or {}).get("total"))
    return _CUOTA[clave]


def valor_mov(m, clave):
    v = m.get(clave)
    if isinstance(v, dict):
        v = v.get("total")
    return b.num(v)


TUESDAY = date(2024, 1, 2)   # el API exige martes: se fija un martes y se resta lo que dura el primer periodo (solo importan los dias)


def cred(inv, n, dias_primero=None):
    extra = {}
    if dias_primero and 0 < dias_primero < 7:
        extra = {"fechaDesembolso": (TUESDAY - timedelta(days=dias_primero)).isoformat(), "primerVencimiento": TUESDAY.isoformat()}
    return {**extra, "linea": b.LINEA, "periodicidad": "SEMANAL", "cuotas": int(n), "valorMoto": str(int(inv)), "matriculaRunt": "0", "soat": "0",
            "prenda": "0", "alistamiento": "0", "gpsPlataforma": "0", "gastosComerciales": "0", "pagoInicial": "0"}


# ---- pago de servicios por concepto y saldo en mora (Diego, 6-nov) ----
def pago_concepto(m, k):
    """Pago del periodo por concepto, de `pagosServicios` del motor (equivale a causado(t) + cxc(t-1) - cxc(t)).
    «Seguro» incluye el seguro todo riesgo y su IVA: el total del motor suma fondo + asistencia + seguro + seguro todo riesgo + IVA."""
    d = m.get("pagosServicios") or {}
    if k == "seguro":
        return sum(b.num(d.get(x)) for x in ("seguro", "seguroTodoRiesgo", "ivaSeguroTodoRiesgo"))
    return b.num(d.get(k))


def saldo_en_mora(m):
    """Igual que Causador.saldoEnMora (baseMoraCobranza = TOTAL_PENDIENTE, perfil NEGOCIO) al cierre del periodo:
    capital vencido + interes corriente pendiente + servicios pendientes + mora + cobranza pendientes."""
    return sum(b.num(m.get(k)) for k in ("capitalVencido", "cxcInteresCorriente", "cxcServicios", "cxcMora", "cxcCobranza"))


# ---- columna "logica": que hizo el motor en cada semana (texto legible) ----
def _cop(v):
    return ("-$ " if v < 0 else "$ ") + f"{abs(round(v)):,}".replace(",", ".")


def _pct(v):
    return f"{v * 100:.2f}".replace(".", ",") + " %"


def _desplaz(usados, esp, p):
    """Semanas que se corrió el calendario hasta el periodo p (semanas justificadas sin pago)."""
    if p < 1 or p - 1 >= len(usados) or p - 1 >= len(esp):
        return 0
    return round((usados[p - 1] - esp[p - 1]).days / 7)


def logica(p, tipo, dias, just, m, prev, cuota_sim, tasa0, modo_tasa, shift, shift_prev, dias_primera):
    cv, cvp = b.num(m.get("cuotasVencidas")), b.num(prev.get("cuotasVencidas"))
    mora, cob = b.num(m.get("interesMora")), b.num(m.get("cobranzaCausada"))
    pago, sob = b.num(m.get("pagoTotalCliente")), b.num(m.get("sobranteCliente"))
    tp, tp_prev = b.num(m.get("tasaPeriodica")), b.num(prev.get("tasaPeriodica"))
    partes = []
    if p == 1:
        if modo_tasa == "tasa fija":
            partes.append(f"tasa fija de {_pct(tasa0)} EA durante todo el crédito (sin tope de usura)")
        elif modo_tasa == "usura maxima":
            partes.append(f"tasa inicial {_pct(tasa0)} EA = usura vigente al desembolso (sin margen)")
        else:
            partes.append(f"tasa inicial {_pct(tasa0)} EA = usura vigente al desembolso - 1 punto")
        if dias_primera < 7:
            partes.append(f"primera cuota proporcional: el desembolso fue {dias_primera} día(s) antes del vencimiento, se cobra esa parte de capital y servicios; el capital no cobrado no causa interés y lo recoge la última cuota")
    if shift > shift_prev:
        partes.append(f"antes de esta semana hubo {shift - shift_prev} semana(s) justificada(s) sin pago: se corrió el calendario y no cuenta como atraso")
    if tipo == "SIN_PAGO":
        partes.append("sin pago en la semana: el motor causa interés, servicios y mora sobre la cuota y esta queda vencida")
    elif tipo == "PREPAGO_TOTAL":
        partes.append("pronto pago: el cliente cancela todo el saldo pendiente de una vez")
    else:
        completo = pago + 0.5 >= cuota_sim
        if cv < 0.5:
            if dias > 0:
                partes.append(f"pagó con {dias} día(s) de atraso: se causa mora por esos días ({_cop(mora)})")
            else:
                partes.append("pago a tiempo: amortización normal (el pago se aplica a cobranza, mora, interés, servicios y capital, en ese orden)")
            if cvp >= 0.5:
                partes.append("el cliente se pone al día")
            if pago > cuota_sim * 1.05:
                partes.append("pago mayor que la cuota: el excedente se aplica a capital")
        elif completo:
            partes.append(f"pagó la cuota pero arrastra {cv:g} cuota(s) vencida(s) de semanas anteriores (contador de cuotas vencidas del motor)")
        else:
            extra = (f"; faltan solo {_cop(cuota_sim - pago)} pero el motor cuenta cualquier saldo pendiente como cuota vencida" if cuota_sim - pago < 0.02 * cuota_sim else "")
            partes.append(f"pago {_cop(pago)} menor que la cuota {_cop(cuota_sim)}: se aplica por prelación y queda parte de la cuota vencida ({cv:g} cuota(s) vencida(s)){extra}")
        if cv < cvp - 0.5 and cv >= 0.5:
            partes.append(f"bajan las cuotas vencidas de {cvp:g} a {cv:g}")
        if just:
            partes.append("semana con justificación: el pago se procesa igual y no se perdonan cargos")
    if cob > 0.5:
        partes.append(f"cobranza {_cop(cob)} (el saldo en mora llega a 2 o más cuotas)")
    if mora > 0.5 and not any("mora" in x for x in partes):
        partes.append(f"mora causada {_cop(mora)}")
    if m.get("listoParaRecuperacion") and not prev.get("listoParaRecuperacion"):
        partes.append("se marca LISTO PARA RECUPERAR (la marca no se quita sola)")
    elif m.get("listoParaRecuperacion"):
        partes.append("sigue marcado para recuperar")
    elif prev.get("listoParaRecuperacion") and m.get("estado") != "CANCELADO":
        partes.append("se quita la marca de recuperar: al cerrar la semana el saldo en mora (todo lo pendiente ÷ cuota total) bajó de 2, y deja de cobrarse cobranza")
    if tp and tp_prev and abs(tp - tp_prev) > 1e-9 and p > 1:
        nueva = (1 + tp) ** 52 - 1
        partes.append(f"la tasa pasa a {_pct(nueva)} EA porque la usura vigente bajó (el motor solo baja la tasa, nunca la sube)")
    if m.get("estado") == "CANCELADO":
        partes.append("el crédito queda cancelado" + (f"; el sobrante de {_cop(sob)} no se cuenta" if sob > 0.5 else ""))
    texto = "; ".join(partes)
    return texto[:1].upper() + texto[1:] + "."


def main():
    os.makedirs(OUT, exist_ok=True)
    serie = cargar_usura()
    con = b.conectar()
    cur = con.cursor()
    tabla = tabla_relaciones(cur)
    cur.execute(
        "SELECT r.contrato, r.fecha_inicio, r.FechaFinal, r.CONCEPTO, r.cuota, r.no_canon, r.dia_canon, r.tipopago, i.egrempfinl, "
        "(SELECT COUNT(*) FROM rentabilidad_activos p WHERE p.contrato=r.contrato AND p.fecha_inicio<r.fecha_inicio) "
        "FROM rentabilidad_activos r LEFT JOIN motos_inv i ON i.contrato=r.contrato "
        "WHERE r.contrato REGEXP '^[0-9]+$' AND r.CONCEPTO <> 'CARTA MEDELLIN' AND r.cuota>0 AND r.no_canon>0 GROUP BY r.contrato, r.fecha_inicio")
    cerrados = cur.fetchall()
    cur.execute("SELECT contrato, tipopago, dia_canon, cuota, no_canon FROM motos_app_hist WHERE fecha_registro = (SELECT MAX(fecha_registro) FROM motos_app_hist)")
    hist = {f[0]: f for f in cur.fetchall()}
    cur.execute(
        "SELECT m.contrato, m.fecha_inicio, i.egrempfinl, MAX(m.vlrtotal), MAX(m.saldo), (SELECT COUNT(*) FROM rentabilidad_activos r WHERE r.contrato=m.contrato AND r.FechaFinal <= m.fecha_inicio) "
        "FROM motos_app m LEFT JOIN motos_inv i ON i.contrato=m.contrato WHERE m.fecha_inicio > '2000-01-01' AND m.fecha_inicio < CURDATE() - INTERVAL 14 DAY "
        "AND m.contrato REGEXP '^[0-9]+$' GROUP BY m.contrato, m.fecha_inicio")
    vigentes = cur.fetchall()
    ids = sorted({c[0] for c in cerrados} | {v[0] for v in vigentes})
    pagos_por, justif_por = defaultdict(list), defaultdict(set)
    for i in range(0, len(ids), 500):
        trozo = ids[i:i + 500]
        cur.execute("SELECT CONTRATO, FECHA, ABONO+TRANSF, MULTA, CARTA, CARTAMOTIVO FROM np_his_abonos WHERE CONTRATO IN (%s) ORDER BY FECHA, HORA"
                    % ",".join(["%s"] * len(trozo)), trozo)
        for fila in cur.fetchall():
            pagos_por[fila[0]].append(fila[1:])
        cur.execute("SELECT contrato, fecha_registro, JUSTIFICACION FROM motos_app_hist WHERE contrato IN (%s) AND JUSTIFICACION IS NOT NULL AND JUSTIFICACION<>''"
                    % ",".join(["%s"] * len(trozo)), trozo)
        for contrato, fecha, j in cur.fetchall():
            if norm(j) in PERDONADAS:
                justif_por[contrato].add(fecha)
    cur.execute("SELECT MAX(FECHA) FROM np_his_abonos")
    CORTE = cur.fetchone()[0]
    con.close()
    print("corte de datos:", CORTE)
    print("cerrados:", len(cerrados), "| vigentes:", len(vigentes), "| usura filas:", len(serie), flush=True)

    trabajos = [(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9] == 0, False) for c in cerrados]
    EXTRA = {}
    for contrato, ini, egr, vtot, sdo, previos in vigentes:
        EXTRA[contrato] = (vtot, sdo)
        h = hist.get(contrato)
        if h:
            trabajos.append((contrato, ini, CORTE, "VIGENTE", h[3], h[4], h[2], h[1], egr, previos == 0, True))
    if os.environ.get("SOLO_ACTIVOS"):
        trabajos = [t for t in trabajos if t[-1]]
    if os.environ.get("CONTRATOS"):
        quiero = set(os.environ["CONTRATOS"].split(","))
        trabajos = [t for t in trabajos if str(t[0]) in quiero]
    if LIMITE:
        trabajos = trabajos[:LIMITE]

    f_con = open(os.path.join(OUT, "contratos.csv"), "w", newline="", encoding="utf-8")
    f_pag = open(os.path.join(OUT, "pagos_reales.csv"), "w", newline="", encoding="utf-8")
    f_res = open(os.path.join(OUT, "resultados.csv"), "w", newline="", encoding="utf-8")
    f_per = open(os.path.join(OUT, "periodos.csv"), "w", newline="", encoding="utf-8")
    w_con = csv.writer(f_con)
    w_pag = csv.writer(f_pag)
    w_res = csv.writer(f_res)
    w_per = csv.writer(f_per)
    f_pro = open(os.path.join(OUT, "proyeccion.csv"), "w", newline="", encoding="utf-8")
    w_pro = csv.writer(f_pro)
    w_pro.writerow(["corrida", "id", "periodo", "fecha_vencimiento", "proyectado", "pago_capital", "pago_interes", "pago_mora", "pago_cobranza", "pago_servicios", "pago_total", "saldo_final"])
    w_con.writerow(["id", "sede", "contrato", "cohorte", "estado_real", "fecha_inicio", "fecha_fin", "cuotas", "cuota_real_miles",
                    "inversion", "inversion_imputada", "linea_usura", "tasa_inicial_ea", "pagos_reales", "multas_reales_miles", "dias_justificados", "valor_total_miles", "saldo_real_miles"])
    w_pag.writerow(["id", "fecha", "valor_miles", "multa_miles"])
    w_res.writerow(["corrida", "id", "sim_pagado", "sim_saldo_final", "sim_pendientes", "sim_mora", "sim_cobranza", "sim_interes", "sim_servicios",
                    "sim_cuotas_vencidas_final", "sim_cuotas_vencidas_max", "sim_marca_recuperacion", "sim_periodo_marca", "sim_estado_final",
                    "sim_periodos", "periodo_corte", "real_ea_con_multas", "cv_efectivo_max", "marca_efectivo", "periodo_marca_efectivo", "sim_proyectado_total"])
    w_per.writerow(["corrida", "id", "periodo", "fecha_vencimiento", "instruccion", "valor_real", "dias_atraso", "justificado", "estado", "cuotas_vencidas",
                    "saldo_inicial", "interes_corriente", "interes_mora", "cobranza", "servicios", "pago_total_cliente", "saldo_final", "listo_recuperacion", "tasa_ea", "cuota_financiera", "pago_capital", "pago_interes", "pago_mora", "pago_cobranza", "pago_servicios", "capital_vencido", "cv_efectivo", "usura_ea", "logica", "pago_fondo", "pago_seguro", "pago_asistencia", "saldo_en_mora", "cxc_mora"])
    descartes, hechos = defaultdict(int), 0
    for contrato, ini, fin, concepto, cuota, n, dia, tipopago, egr, primero, es_vig in trabajos:
        if tipopago != "SEMANAL" or not cuota or not n or n > 104:
            descartes["no semanal / incompleto"] += 1
            continue
        pagos, flujos, multas, multas_f, reales = [], [], 0.0, [], []
        for fecha, val, multa, carta, motivo in pagos_por.get(contrato, []):
            if not (ini < fecha <= fin):
                continue
            if carta == "SI" and motivo != "PRONTO PAGO":
                continue
            multas += b.num(multa)
            reales.append((fecha, val, multa))
            if b.num(multa) > 0:
                multas_f.append((fecha, b.num(multa)))
            if b.num(val) > 0:
                pagos.append((fecha, b.num(val)))
            flujos.append((fecha, (b.num(val) + b.num(multa)) * 1000))
        if not pagos:
            descartes["sin pagos"] += 1
            continue
        if primero and egr and egr > 0:
            inv, imputada = float(egr), False
        else:
            inv, imputada = float(cuota) * int(n) * 1000 * relacion(tabla, int(n), ini.year), True
        cid = f"{SEDE}|{contrato}|{ini.isoformat()}"
        justif = {j for j in justif_por.get(contrato, set()) if ini < j <= fin}
        esp = b.fechas_esperadas(ini, dia, int(n))
        completo = concepto in COMPLETOS
        ea_real = b.ea_implicita(inv, ini, flujos) if (completo and not imputada) else None
        try:
            ea0, mod = usura_en(serie, ini)
            tasa_base = round(max(ea0 - MARGEN, 0.01), 4)   # solo informativa si no hay corridas de usura
            salidas = []
            for corrida in CORRIDAS:
                maxima = corrida.startswith("max_")
                fija = FIJA if corrida.startswith("fija_") else None
                margen = 0.0 if maxima else MARGEN
                tasa_ini = fija if fija else round(max(ea0 - margen, 0.01), 4)
                cuota_sim = cuota_total(inv, n, tasa_ini)
                js = justif if corrida.endswith("con_justificaciones") else set()
                ins, info, tasa0, mod, usados, p_corte = instrucciones(esp, pagos, multas_f, ini, fin, concepto, float(cuota), cuota_sim, serie, js, es_vig, margen, fija)
                if not maxima and not fija:
                    tasa_base = tasa0
                r = b.post({"credito": cred(inv, n, (esp[0] - ini).days), "perfilReglas": "NEGOCIO", "instrucciones": ins, "incluirAsientos": False})
                movs = r["movimientos"]
                fin_plan = next((i for i, m in enumerate(movs) if i and m.get("estado") == "CANCELADO"), len(movs) - 1)
                full = movs[:fin_plan + 1]   # el plan completo (lo que pasa despues del corte es proyeccion con cuotas a tiempo)
                proy = b.sumar(movs[:fin_plan + 1], "pagoTotalCliente") - b.sumar(movs[:fin_plan + 1], "sobranteCliente")
                cancelado = next((m["periodo"] for m in movs[1:] if m.get("estado") == "CANCELADO"), None)
                if cancelado:
                    p_corte = min(p_corte, cancelado)   # el credito se pago antes: lo que el cliente siguio pagando ya no es de la financiera
                movs = [m for m in movs if m["periodo"] <= p_corte]
                salidas.append((corrida, ins, info, movs, usados, p_corte, cuota_sim, tasa0, "tasa fija" if fija else ("usura maxima" if maxima else "usura - 1 punto"), proy, full))
        except Exception as e:  # noqa: BLE001
            descartes[f"error {type(e).__name__}"] += 1
            continue
        w_con.writerow([cid, SEDE, contrato, "PRIMERO" if primero else "SEGUNDO_O_MAS", concepto, ini, fin if not es_vig else "", int(n), float(cuota),
                        round(inv), imputada, mod, tasa_base, len(pagos), round(multas, 3), len(justif),
                        *(EXTRA.get(contrato, ('', '')) if es_vig else ('', ''))])
        for fecha, val, multa in reales:
            w_pag.writerow([cid, fecha, val, multa])
        for corrida, ins, info, movs, usados, p_corte, cuota_sim_c, tasa0_c, modo_tasa, proy, full in salidas:
            ult = movs[-1]
            pend = sum(b.num(ult.get(k)) for k in ("cxcMora", "cxcCobranza", "cxcInteresCorriente", "cxcServicios"))
            marcados = [m["periodo"] for m in movs if m.get("listoParaRecuperacion")]
            cv_ef = [info.get(m["periodo"], ("", 0, 0, False, 0))[4] for m in movs[1:]]
            marca_ef = next((m["periodo"] for m in movs[1:] if info.get(m["periodo"], ("", 0, 0, False, 0))[4] >= 2), "")
            w_res.writerow([corrida, cid, round(b.sumar(movs, "pagoTotalCliente") - b.sumar(movs, "sobranteCliente"), 2), round(b.num(ult.get("saldoFinal")), 2), round(pend, 2),
                            round(b.sumar(movs, "interesMora"), 2), round(b.sumar(movs, "cobranzaCausada"), 2), round(b.sumar(movs, "interesCorriente"), 2),
                            round(b.sumar(movs, "serviciosCausados"), 2), b.num(ult.get("cuotasVencidas")), max(b.num(m.get("cuotasVencidas")) for m in movs),
                            bool(marcados), marcados[0] if marcados else "", ult.get("estado"), len(movs) - 1, p_corte,
                            round(ea_real, 4) if ea_real is not None else "",
                            max(cv_ef) if cv_ef else 0, bool(marca_ef), marca_ef, round(proy, 2)])
            if es_vig:
                for m in full[1:]:
                    q = m["periodo"]
                    if q - 1 < len(usados):
                        fv = usados[q - 1]
                    elif usados:
                        fv = usados[-1] + timedelta(days=7 * (q - len(usados)))
                    else:
                        continue
                    w_pro.writerow([corrida, cid, q, fv, q > p_corte, round(valor_mov(m, "pagoCapital"), 2), round(valor_mov(m, "pagoInteresCorriente"), 2),
                                    round(valor_mov(m, "pagoMora"), 2), round(valor_mov(m, "pagoCobranza"), 2), round(valor_mov(m, "pagoServicios"), 2),
                                    round(b.num(m.get("pagoTotalCliente")) - b.num(m.get("sobranteCliente")), 2), round(b.num(m.get("saldoFinal")), 2)])
            for idx, m in enumerate(movs[1:], start=1):
                p = m["periodo"]
                tipo, valor, dias, just, cvef = info.get(p, ("SIN_INSTRUCCION", 0.0, 0, False, 0))
                tp = b.num(m.get("tasaPeriodica"))
                w_per.writerow([corrida, cid, p, usados[p - 1] if p - 1 < len(usados) else "", tipo, round(valor, 2), dias, just, m.get("estado"),
                                b.num(m.get("cuotasVencidas")), round(b.num(m.get("saldoInicial")), 2), round(b.num(m.get("interesCorriente")), 2),
                                round(b.num(m.get("interesMora")), 2), round(b.num(m.get("cobranzaCausada")), 2),
                                round(b.num((m.get("serviciosCausados") or {}).get("total")), 2), round(b.num(m.get("pagoTotalCliente")), 2),
                                round(b.num(m.get("saldoFinal")), 2), bool(m.get("listoParaRecuperacion")),
                                round((1 + tp) ** 52 - 1, 4) if tp else "", round(valor_mov(m, "cuotaFinanciera"), 2), round(valor_mov(m, "pagoCapital"), 2),
                                round(valor_mov(m, "pagoInteresCorriente"), 2), round(valor_mov(m, "pagoMora"), 2), round(valor_mov(m, "pagoCobranza"), 2),
                                round(valor_mov(m, "pagoServicios"), 2), round(valor_mov(m, "capitalVencido"), 2), cvef,
                                round(usura_en(serie, usados[p - 1])[0], 4) if p - 1 < len(usados) else "",
                                logica(p, tipo, dias, just, m, movs[idx - 1], cuota_sim_c, tasa0_c, modo_tasa,
                                       _desplaz(usados, esp, p), _desplaz(usados, esp, p - 1), (usados[0] - ini).days if usados else 7),
                                round(pago_concepto(m, "fondoGarantias"), 2), round(pago_concepto(m, "seguro"), 2),
                                round(pago_concepto(m, "asistencia"), 2), round(saldo_en_mora(m), 2), round(b.num(m.get("cxcMora")), 2)])
        hechos += 1
        if hechos % 200 == 0:
            print("  contratos", hechos, flush=True)
    for f in (f_con, f_pag, f_res, f_per, f_pro):
        f.close()
    with open(os.path.join(OUT, "usura.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(serie[0].keys()))
        w.writeheader()
        w.writerows(serie)
    print("listo:", hechos, "contratos | descartes:", dict(descartes))


if __name__ == "__main__":
    main()
