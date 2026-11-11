"""Backtesting multi-sede (solo LECTURA en el MySQL de la sede). Escribe CSV con `sede` y `run_id` en la carpeta de salida.

Uso:
  py -3.12 pipeline/backtest.py --sede MONTERIA --env RUTA\\.env --api http://localhost:8099 --salida salida\\monteria [--workers 8]

Es la logica de backtest_v11.py (v11, 6-nov-2026) sin cambios de calculo: para CARTAGENA produce los mismos resultados.
Cambios respecto a v11: la sede es argumento (servidor en comun.SEDES), el motor se invoca en paralelo con reintentos
(un hilo por contrato, que evalua sus 6 corridas), se agregan placa / activas_fin_mes / carta_fin_mes / corte_activos /
corrida_meta, y ya no depende de backtest_fase_a.py ni de rutas fijas. Contratos reales: nunca al repo.

Variables de entorno heredadas de v11: SOLO_ACTIVOS=1 (equivale a --solo-activos), CONTRATOS=1,2,3 (equivale a --contratos).
"""
import argparse
import csv
import json
import math
import os
import statistics as st
import sys
import threading
import time
import traceback
import unicodedata
import urllib.error
import urllib.request
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timedelta, timezone

if __package__ in (None, ""):
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pipeline import comun  # noqa: E402

COMPLETOS = ("NORMAL", "PRONTO PAGO")
PERDONADAS = ("enfermo", "familiar muerto", "varado", "acidentado", "accidentado", "patio", "patios", "fiscalia", "en taller", "garantia")
MARGEN = 0.01
FIJA = 0.8844   # corridas fija_*: tasa fija de 88,44 % EA durante todo el credito (sin tope de usura)
CORRIDAS_TODAS = ("sin_justificaciones", "con_justificaciones", "max_sin_justificaciones", "max_con_justificaciones",
                  "fija_sin_justificaciones", "fija_con_justificaciones")
CORRIDAS_ACTIVOS = ("fija_sin_justificaciones", "fija_con_justificaciones")
LINEA = "CPPU"
TUESDAY = date(2024, 1, 2)   # el API exige martes: se fija un martes y se resta lo que dura el primer periodo (solo importan los dias)
DIAS = {"lun": 0, "mar": 1, "mie": 2, "jue": 3, "vie": 4, "sab": 5, "dom": 6}


# ---------------------------------------------------------------- utilidades numericas / fechas
def num(x):
    try:
        return float(x)
    except (TypeError, ValueError):
        return 0.0


def norm(t):
    t = unicodedata.normalize("NFD", (t or "").strip().lower())
    return "".join(c for c in t if unicodedata.category(c) != "Mn")


def dia_semana(txt):
    return DIAS[norm(txt)[:3]]


def fechas_esperadas(inicio, dia_canon, n):
    objetivo = dia_semana(dia_canon)
    d = inicio + timedelta(days=1)
    while d.weekday() != objetivo:
        d += timedelta(days=1)
    return [d + timedelta(days=7 * i) for i in range(n)]


def ea_implicita(inv, t0, flujos):
    """Tasa efectiva anual que iguala el valor presente de los flujos a la inversion (biseccion)."""
    def vp(r):
        return sum(v / (1 + r) ** (max((f - t0).days, 0) / 365.0) for f, v in flujos)
    lo, hi = 0.0, 100.0
    if vp(lo) < inv:
        return None
    for _ in range(200):
        mid = (lo + hi) / 2
        if vp(mid) > inv:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


def sumar(movs, clave):
    tot = 0.0
    for m in movs:
        v = m.get(clave)
        if isinstance(v, dict):
            v = v.get("total")
        tot += num(v)
    return tot


# ---------------------------------------------------------------- API del motor (con reintentos)
class ErrorMotor(Exception):
    pass


def post(api, cuerpo, reintentos=3, espera=1.0, timeout=120):
    """POST /api/v1/escenarios/ejecutar. Reintenta errores transitorios (red, 5xx, 429) con espera creciente; 4xx no se reintenta."""
    datos = json.dumps(cuerpo).encode()
    ultimo = None
    for intento in range(reintentos + 1):
        try:
            req = urllib.request.Request(api.rstrip("/") + "/api/v1/escenarios/ejecutar", data=datos, headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            if e.code < 500 and e.code != 429:
                raise
            ultimo = e
        except (urllib.error.URLError, TimeoutError, ConnectionError, OSError) as e:
            ultimo = e
        if intento < reintentos:
            time.sleep(espera * (2 ** intento))
    raise ErrorMotor(f"el motor no respondio tras {reintentos + 1} intentos: {ultimo!r}")


def version_motor(api, timeout=15):
    """Version del motor desplegado: /actuator/info (build.version) si existe; si no, 'desconocida'."""
    try:
        with urllib.request.urlopen(api.rstrip("/") + "/actuator/info", timeout=timeout) as r:
            info = json.loads(r.read())
        v = (info.get("build") or {}).get("version") or (info.get("app") or {}).get("version")
        if v:
            return str(v)
    except (OSError, ValueError):
        pass
    return "desconocida"


# ---------------------------------------------------------------- serie de usura
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


# ---------------------------------------------------------------- imputacion de inversion
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


# ---------------------------------------------------------------- instrucciones al motor (identico a v11)
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


def cred(inv, n, dias_primero=None):
    extra = {}
    if dias_primero and 0 < dias_primero < 7:
        extra = {"fechaDesembolso": (TUESDAY - timedelta(days=dias_primero)).isoformat(), "primerVencimiento": TUESDAY.isoformat()}
    return {**extra, "linea": LINEA, "periodicidad": "SEMANAL", "cuotas": int(n), "valorMoto": str(int(inv)), "matriculaRunt": "0", "soat": "0",
            "prenda": "0", "alistamiento": "0", "gpsPlataforma": "0", "gastosComerciales": "0", "pagoInicial": "0"}


def valor_mov(m, clave):
    v = m.get(clave)
    if isinstance(v, dict):
        v = v.get("total")
    return num(v)


def pago_concepto(m, k):
    """Pago del periodo por concepto, de `pagosServicios` del motor (equivale a causado(t) + cxc(t-1) - cxc(t)).
    «Seguro» incluye el seguro todo riesgo y su IVA: el total del motor suma fondo + asistencia + seguro + seguro todo riesgo + IVA."""
    d = m.get("pagosServicios") or {}
    if k == "seguro":
        return sum(num(d.get(x)) for x in ("seguro", "seguroTodoRiesgo", "ivaSeguroTodoRiesgo"))
    return num(d.get(k))


def saldo_en_mora(m):
    """Igual que Causador.saldoEnMora (baseMoraCobranza = TOTAL_PENDIENTE, perfil NEGOCIO) al cierre del periodo:
    capital vencido + interes corriente pendiente + servicios pendientes + mora + cobranza pendientes."""
    return sum(num(m.get(k)) for k in ("capitalVencido", "cxcInteresCorriente", "cxcServicios", "cxcMora", "cxcCobranza"))


# ---- columna "logica": que hizo el motor en cada semana (texto legible) ----
def _cop(v):
    return ("-$ " if v < 0 else "$ ") + f"{abs(round(v)):,}".replace(",", ".")


def _pct(v):
    return f"{v * 100:.2f}".replace(".", ",") + " %"


def _desplaz(usados, esp, p):
    """Semanas que se corrio el calendario hasta el periodo p (semanas justificadas sin pago)."""
    if p < 1 or p - 1 >= len(usados) or p - 1 >= len(esp):
        return 0
    return round((usados[p - 1] - esp[p - 1]).days / 7)


def logica(p, tipo, dias, just, m, prev, cuota_sim, tasa0, modo_tasa, shift, shift_prev, dias_primera):
    cv, cvp = num(m.get("cuotasVencidas")), num(prev.get("cuotasVencidas"))
    mora, cob = num(m.get("interesMora")), num(m.get("cobranzaCausada"))
    pago, sob = num(m.get("pagoTotalCliente")), num(m.get("sobranteCliente"))
    tp, tp_prev = num(m.get("tasaPeriodica")), num(prev.get("tasaPeriodica"))
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


# ---------------------------------------------------------------- contexto compartido entre hilos
class Contexto:
    """Datos de solo lectura para los hilos + cache de cuotas (protegida por lock)."""

    def __init__(self, sede, run_id, api, serie, tabla, pagos_por, justif_por, extra, corridas, reintentos):
        self.sede, self.run_id, self.api = sede, run_id, api
        self.serie, self.tabla = serie, tabla
        self.pagos_por, self.justif_por, self.extra = pagos_por, justif_por, extra
        self.corridas, self.reintentos = corridas, reintentos
        self._cuota, self._lock = {}, threading.Lock()

    def post(self, cuerpo):
        return post(self.api, cuerpo, reintentos=self.reintentos)

    def cuota_total(self, inv, n, tasa0):  # la tasa inicial forma parte de la clave
        clave = (int(inv), int(n), tasa0)
        with self._lock:
            if clave in self._cuota:
                return self._cuota[clave]
        r = self.post({"credito": cred(inv, n), "perfilReglas": "NEGOCIO", "incluirAsientos": False,
                       "instrucciones": [{"periodo": 1, "tipo": "CAMBIO_TASA", "nuevaTasaEA": str(tasa0), "modalidad": "MISMA_FECHA_FINAL"}]})
        m = r["movimientos"][1]
        v = num(m.get("cuotaFinanciera")) + num((m.get("serviciosCausados") or {}).get("total"))
        with self._lock:
            self._cuota[clave] = v
        return v


def evaluar(ctx, trabajo, placas):
    """Evalua UN contrato (todas sus corridas). Devuelve ('ok', filas_por_archivo) o ('descarte', motivo).
    Las filas llevan sede/run_id al principio. Es la parte de main() de v11 por contrato, sin cambios de calculo."""
    contrato, ini, fin, concepto, cuota, n, dia, tipopago, egr, primero, es_vig = trabajo
    sede, run_id, serie = ctx.sede, ctx.run_id, ctx.serie
    if tipopago != "SEMANAL" or not cuota or not n or n > 104:
        return "descarte", "no semanal / incompleto"
    pagos, flujos, multas, multas_f, reales = [], [], 0.0, [], []
    for fecha, val, multa, carta, motivo in ctx.pagos_por.get(contrato, []):
        if not (ini < fecha <= fin):
            continue
        if carta == "SI" and motivo != "PRONTO PAGO":
            continue
        multas += num(multa)
        reales.append((fecha, val, multa))
        if num(multa) > 0:
            multas_f.append((fecha, num(multa)))
        if num(val) > 0:
            pagos.append((fecha, num(val)))
        flujos.append((fecha, (num(val) + num(multa)) * 1000))
    if not pagos:
        return "descarte", "sin pagos"
    if primero and egr and egr > 0:
        inv, imputada = float(egr), False
    else:
        inv, imputada = float(cuota) * int(n) * 1000 * relacion(ctx.tabla, int(n), ini.year), True
    cid = f"{sede}|{contrato}|{ini.isoformat()}"
    justif = {j for j in ctx.justif_por.get(contrato, set()) if ini < j <= fin}
    esp = fechas_esperadas(ini, dia, int(n))
    completo = concepto in COMPLETOS
    ea_real = ea_implicita(inv, ini, flujos) if (completo and not imputada) else None
    try:
        ea0, mod = usura_en(serie, ini)
        tasa_base = round(max(ea0 - MARGEN, 0.01), 4)   # solo informativa si no hay corridas de usura
        salidas = []
        for corrida in ctx.corridas:
            maxima = corrida.startswith("max_")
            fija = FIJA if corrida.startswith("fija_") else None
            margen = 0.0 if maxima else MARGEN
            tasa_ini = fija if fija else round(max(ea0 - margen, 0.01), 4)
            cuota_sim = ctx.cuota_total(inv, n, tasa_ini)
            js = justif if corrida.endswith("con_justificaciones") else set()
            ins, info, tasa0, mod, usados, p_corte = instrucciones(esp, pagos, multas_f, ini, fin, concepto, float(cuota), cuota_sim, serie, js, es_vig, margen, fija)
            if not maxima and not fija:
                tasa_base = tasa0
            r = ctx.post({"credito": cred(inv, n, (esp[0] - ini).days), "perfilReglas": "NEGOCIO", "instrucciones": ins, "incluirAsientos": False})
            movs = r["movimientos"]
            fin_plan = next((i for i, m in enumerate(movs) if i and m.get("estado") == "CANCELADO"), len(movs) - 1)
            full = movs[:fin_plan + 1]   # el plan completo (lo que pasa despues del corte es proyeccion con cuotas a tiempo)
            proy = sumar(movs[:fin_plan + 1], "pagoTotalCliente") - sumar(movs[:fin_plan + 1], "sobranteCliente")
            cancelado = next((m["periodo"] for m in movs[1:] if m.get("estado") == "CANCELADO"), None)
            if cancelado:
                p_corte = min(p_corte, cancelado)   # el credito se pago antes: lo que el cliente siguio pagando ya no es de la financiera
            movs = [m for m in movs if m["periodo"] <= p_corte]
            salidas.append((corrida, ins, info, movs, usados, p_corte, cuota_sim, tasa0, "tasa fija" if fija else ("usura maxima" if maxima else "usura - 1 punto"), proy, full))
    except Exception as e:  # noqa: BLE001
        if os.environ.get("BACKTEST_DEBUG"):
            traceback.print_exc()
        return "descarte", f"error {type(e).__name__}"

    L = [sede, run_id]
    filas = {k: [] for k in ("contratos.csv", "pagos_reales.csv", "resultados.csv", "periodos.csv", "proyeccion.csv")}
    filas["contratos.csv"].append(L + [cid, contrato, placas.get(contrato, ""), "PRIMERO" if primero else "SEGUNDO_O_MAS", concepto, ini,
                                      fin if not es_vig else "", int(n), float(cuota), round(inv), imputada, mod, tasa_base, len(pagos),
                                      round(multas, 3), len(justif), *(ctx.extra.get(contrato, ('', '')) if es_vig else ('', ''))])
    for fecha, val, multa in reales:
        filas["pagos_reales.csv"].append(L + [cid, fecha, val, multa])
    for corrida, ins, info, movs, usados, p_corte, cuota_sim_c, tasa0_c, modo_tasa, proy, full in salidas:
        ult = movs[-1]
        pend = sum(num(ult.get(k)) for k in ("cxcMora", "cxcCobranza", "cxcInteresCorriente", "cxcServicios"))
        marcados = [m["periodo"] for m in movs if m.get("listoParaRecuperacion")]
        cv_ef = [info.get(m["periodo"], ("", 0, 0, False, 0))[4] for m in movs[1:]]
        marca_ef = next((m["periodo"] for m in movs[1:] if info.get(m["periodo"], ("", 0, 0, False, 0))[4] >= 2), "")
        filas["resultados.csv"].append(L + [corrida, cid, round(sumar(movs, "pagoTotalCliente") - sumar(movs, "sobranteCliente"), 2), round(num(ult.get("saldoFinal")), 2), round(pend, 2),
                                           round(sumar(movs, "interesMora"), 2), round(sumar(movs, "cobranzaCausada"), 2), round(sumar(movs, "interesCorriente"), 2),
                                           round(sumar(movs, "serviciosCausados"), 2), num(ult.get("cuotasVencidas")), max(num(m.get("cuotasVencidas")) for m in movs),
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
                filas["proyeccion.csv"].append(L + [corrida, cid, q, fv, q > p_corte, round(valor_mov(m, "pagoCapital"), 2), round(valor_mov(m, "pagoInteresCorriente"), 2),
                                                   round(valor_mov(m, "pagoMora"), 2), round(valor_mov(m, "pagoCobranza"), 2), round(valor_mov(m, "pagoServicios"), 2),
                                                   round(num(m.get("pagoTotalCliente")) - num(m.get("sobranteCliente")), 2), round(num(m.get("saldoFinal")), 2)])
        for idx, m in enumerate(movs[1:], start=1):
            p = m["periodo"]
            tipo, valor, dias, just, cvef = info.get(p, ("SIN_INSTRUCCION", 0.0, 0, False, 0))
            tp = num(m.get("tasaPeriodica"))
            filas["periodos.csv"].append(L + [corrida, cid, p, usados[p - 1] if p - 1 < len(usados) else "", tipo, round(valor, 2), dias, just, m.get("estado"),
                                             num(m.get("cuotasVencidas")), round(num(m.get("saldoInicial")), 2), round(num(m.get("interesCorriente")), 2),
                                             round(num(m.get("interesMora")), 2), round(num(m.get("cobranzaCausada")), 2),
                                             round(num((m.get("serviciosCausados") or {}).get("total")), 2), round(num(m.get("pagoTotalCliente")), 2),
                                             round(num(m.get("saldoFinal")), 2), bool(m.get("listoParaRecuperacion")),
                                             round((1 + tp) ** 52 - 1, 4) if tp else "", round(valor_mov(m, "cuotaFinanciera"), 2), round(valor_mov(m, "pagoCapital"), 2),
                                             round(valor_mov(m, "pagoInteresCorriente"), 2), round(valor_mov(m, "pagoMora"), 2), round(valor_mov(m, "pagoCobranza"), 2),
                                             round(valor_mov(m, "pagoServicios"), 2), round(valor_mov(m, "capitalVencido"), 2), cvef,
                                             round(usura_en(serie, usados[p - 1])[0], 4) if p - 1 < len(usados) else "",
                                             logica(p, tipo, dias, just, m, movs[idx - 1], cuota_sim_c, tasa0_c, modo_tasa,
                                                    _desplaz(usados, esp, p), _desplaz(usados, esp, p - 1), (usados[0] - ini).days if usados else 7),
                                             round(pago_concepto(m, "fondoGarantias"), 2), round(pago_concepto(m, "seguro"), 2),
                                             round(pago_concepto(m, "asistencia"), 2), round(saldo_en_mora(m), 2), round(num(m.get("cxcMora")), 2)])
    return "ok", filas


# ---------------------------------------------------------------- lectura del MySQL de la sede (solo SELECT)
def conectar(sede, env_ruta):
    """Conecta al servidor de la sede (host en comun.SEDES). Credenciales de DB_USER/DB_PASSWORD del .env (DB_PORT y DB_NAME opcionales)."""
    import pymysql
    cfg = comun.leer_env(env_ruta)
    return pymysql.connect(host=comun.SEDES[sede], port=int(cfg.get("DB_PORT") or 3306), user=cfg["DB_USER"], password=cfg["DB_PASSWORD"],
                           database=cfg.get("DB_NAME") or comun.DB_NAME_DEFECTO, connect_timeout=15, read_timeout=300)


# Donde puede estar la placa de cada contrato. No verificado contra la BD (esta tarea no la consulta): cada consulta es opcional,
# si la tabla/columna no existe se avisa y se sigue (la placa es solo control de calidad).
CONSULTAS_PLACA = (
    "SELECT contrato, placa FROM motos_app WHERE placa IS NOT NULL AND placa <> ''",
    "SELECT contrato, placa FROM rentabilidad_activos WHERE placa IS NOT NULL AND placa <> ''",
    "SELECT contrato, placa FROM motos_inv WHERE placa IS NOT NULL AND placa <> ''",
)


def leer_placas(cur):
    placas = {}
    for sql in CONSULTAS_PLACA:
        try:
            cur.execute(sql)
            for contrato, placa in cur.fetchall():
                p = comun.normalizar_placa(placa)
                if p and str(contrato).strip() not in placas:
                    placas[str(contrato).strip()] = p
        except Exception as e:  # noqa: BLE001
            print(f"  aviso: sin placa desde [{sql.split(' FROM ')[1].split()[0]}]: {type(e).__name__}", file=sys.stderr)
            try:
                cur.connection.rollback()
            except Exception:  # noqa: BLE001
                pass
    return placas


def leer_origen(cur, solo_activos):
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
    corte = cur.fetchone()[0]

    trabajos = [(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9] == 0, False) for c in cerrados]
    extra = {}
    for contrato, ini, egr, vtot, sdo, previos in vigentes:
        extra[contrato] = (vtot, sdo)
        h = hist.get(contrato)
        if h:
            trabajos.append((contrato, ini, corte, "VIGENTE", h[3], h[4], h[2], h[1], egr, previos == 0, True))
    if solo_activos:
        trabajos = [t for t in trabajos if t[-1]]
    return trabajos, tabla, pagos_por, justif_por, extra, corte


def leer_fin_de_mes(cur, desde_mes):
    """(activas_fin_mes, carta_fin_mes): fotos de la flota al ultimo dia de cada mes cerrado y pagos por carta de esa flota."""
    cur.execute("SELECT DATE_FORMAT(fecha_registro,'%%Y-%%m'), MAX(fecha_registro) FROM motos_app_hist WHERE fecha_registro >= %s "
                "AND fecha_registro < DATE_FORMAT(CURDATE(), '%%Y-%%m-01') GROUP BY 1 ORDER BY 1", [desde_mes])
    meses = cur.fetchall()
    activas, cartas = {}, []
    for mes, dia in meses:
        cur.execute("SELECT DISTINCT TRIM(contrato) FROM motos_app_hist WHERE fecha_registro = %s AND contrato IS NOT NULL AND TRIM(contrato) <> ''", [dia])
        for (c,) in cur.fetchall():
            activas[(mes, c)] = (mes, dia, c)
        primero = date(dia.year, dia.month, 1)
        cur.execute(
            "SELECT n.CARTAMOTIVO, COUNT(*), SUM(n.ABONO + n.TRANSF) FROM np_his_abonos n WHERE n.FECHA >= %s AND n.FECHA <= %s "
            "AND n.CARTA = 'SI' AND n.CARTAMOTIVO <> 'PRONTO PAGO' "
            "AND n.CONTRATO IN (SELECT DISTINCT TRIM(contrato) FROM motos_app_hist WHERE fecha_registro = %s) GROUP BY 1",
            [primero, dia, dia])
        for motivo, n, valor in cur.fetchall():
            cartas.append((mes, motivo, int(n), float(valor or 0)))
    return list(activas.values()), cartas


# ---------------------------------------------------------------- orquestacion
def escribir_csv(ruta, columnas, filas):
    with open(ruta, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(columnas)
        w.writerows(filas)


def run_id_nuevo(sha):
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + sha.replace("-sucio", "")[:7]


def procesar(ctx, trabajos, placas, workers, salida):
    """Evalua los contratos en paralelo (un hilo por contrato) y escribe los CSV en el orden de `trabajos` (determinista)."""
    abiertos = {k: open(os.path.join(salida, k), "w", newline="", encoding="utf-8") for k in
                ("contratos.csv", "pagos_reales.csv", "resultados.csv", "periodos.csv", "proyeccion.csv")}
    escritores = {k: csv.writer(f) for k, f in abiertos.items()}
    for k, w in escritores.items():
        w.writerow(comun.COLUMNAS[k])
    descartes, hechos = defaultdict(int), 0
    try:
        with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
            for estado, dato in pool.map(lambda t: evaluar(ctx, t, placas), trabajos):
                if estado == "descarte":
                    descartes[dato] += 1
                    continue
                for k, filas in dato.items():
                    escritores[k].writerows(filas)
                hechos += 1
                if hechos % 200 == 0:
                    print("  contratos", hechos, flush=True)
    finally:
        for f in abiertos.values():
            f.close()
    return hechos, dict(descartes)


def main(argv=None):
    ap = argparse.ArgumentParser(description="Backtesting de una sede (lectura en MySQL, motor por API, CSV de salida).")
    ap.add_argument("--sede", required=True, help="|".join(comun.SEDES))
    ap.add_argument("--env", required=True, help="ruta del .env con DB_USER y DB_PASSWORD (DB_PORT y DB_NAME opcionales)")
    ap.add_argument("--api", default="http://localhost:8099", help="URL base del motor (por defecto http://localhost:8099)")
    ap.add_argument("--salida", required=True, help="carpeta de salida de los CSV")
    ap.add_argument("--workers", type=int, default=8, help="hilos que invocan el motor en paralelo (por defecto 8)")
    ap.add_argument("--reintentos", type=int, default=3, help="reintentos por llamada al motor (red, 5xx, 429)")
    ap.add_argument("--run-id", default=None, help="id de la corrida (por defecto fecha UTC + sha)")
    ap.add_argument("--git-sha", default=os.environ.get("GIT_SHA"), help="sha a registrar (por defecto el del repo)")
    ap.add_argument("--version-motor", default=None, help="version del motor a registrar (por defecto /actuator/info)")
    ap.add_argument("--solo-activos", action="store_true", default=bool(os.environ.get("SOLO_ACTIVOS")), help="solo vigentes y corridas de tasa fija")
    ap.add_argument("--contratos", default=os.environ.get("CONTRATOS", ""), help="lista de contratos separados por coma (depuracion)")
    ap.add_argument("--limite", type=int, default=0, help="procesar solo los N primeros (depuracion)")
    ap.add_argument("--desde-mes", default="2024-04-01", help="primer mes de las fotos de fin de mes")
    ap.add_argument("--max-errores-pct", type=float, default=20.0, help="si mas de este %% de contratos falla por error, la corrida queda 'fallida'")
    a = ap.parse_args(argv)

    sede = comun.validar_sede(a.sede)
    sha = a.git_sha or comun.git_sha()
    run_id = a.run_id or run_id_nuevo(sha)
    os.makedirs(a.salida, exist_ok=True)
    inicio = datetime.now(timezone.utc)
    vmotor = a.version_motor or version_motor(a.api)
    print(f"sede {sede} ({comun.SEDES[sede]}) | run_id {run_id} | motor {vmotor} | sha {sha} | workers {a.workers}", flush=True)

    serie = cargar_usura()
    con = conectar(sede, a.env)
    cur = con.cursor()
    trabajos, tabla, pagos_por, justif_por, extra, corte = leer_origen(cur, a.solo_activos)
    placas = leer_placas(cur)
    fin_mes = None if a.solo_activos else leer_fin_de_mes(cur, a.desde_mes)
    con.close()
    print("corte de datos:", corte, "| trabajos:", len(trabajos), "| usura filas:", len(serie), "| placas:", len(placas), flush=True)

    if a.contratos:
        quiero = set(a.contratos.split(","))
        trabajos = [t for t in trabajos if str(t[0]) in quiero]
    if a.limite:
        trabajos = trabajos[:a.limite]
    corridas = CORRIDAS_ACTIVOS if a.solo_activos else CORRIDAS_TODAS
    ctx = Contexto(sede, run_id, a.api, serie, tabla, pagos_por, justif_por, extra, corridas, a.reintentos)
    hechos, descartes = procesar(ctx, trabajos, placas, a.workers, a.salida)

    with open(os.path.join(a.salida, "usura.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=comun.COLUMNAS_USURA)
        w.writeheader()
        w.writerows(serie)
    if fin_mes is not None:
        activas, cartas = fin_mes
        escribir_csv(os.path.join(a.salida, "activas_fin_mes.csv"), comun.COLUMNAS["activas_fin_mes.csv"], [[sede, run_id, *x] for x in activas])
        escribir_csv(os.path.join(a.salida, "carta_fin_mes.csv"), comun.COLUMNAS["carta_fin_mes.csv"], [[sede, run_id, *x] for x in cartas])
    escribir_csv(os.path.join(a.salida, "corte_activos.csv"), comun.COLUMNAS["corte_activos.csv"], [[sede, run_id, corte]])

    errores = sum(v for k, v in descartes.items() if k.startswith("error"))
    pct = 100.0 * errores / max(1, len(trabajos))
    estado = "terminada" if hechos > 0 and pct <= a.max_errores_pct else "fallida"
    escribir_csv(os.path.join(a.salida, "corrida_meta.csv"), comun.COLUMNAS_META,
                 [[run_id, sede, corte, vmotor, sha, inicio.isoformat(), datetime.now(timezone.utc).isoformat(), estado]])
    print(f"listo: {hechos} contratos | descartes: {descartes} | estado: {estado}")
    return 0 if estado == "terminada" else 2


if __name__ == "__main__":
    sys.exit(main())
