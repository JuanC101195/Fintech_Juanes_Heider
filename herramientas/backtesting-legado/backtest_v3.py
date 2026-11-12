"""Backtesting final (solo lectura en la BD de origen). Reproduce el comportamiento real de cada contrato en el motor, con:
 - tasa historica: al desembolso usura(fecha) - 1 punto, y cada mes la usura vigente (el motor la baja si la supera, regla de Diego);
   serie oficial de la Superfinanciera (datos.gov.co, TIBC x 1,5): CREDITO POPULAR PRODUCTIVO URBANO desde 2023-04, MICROCREDITO antes;
 - dos corridas: sin_justificaciones y con_justificaciones (periodos con justificacion aprobada: sin mora, cobranza ni servicios);
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
CORRIDAS = ("sin_justificaciones", "con_justificaciones", "max_sin_justificaciones", "max_con_justificaciones")


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
def instrucciones(esp, pagos, multas_f, ini, fin, concepto, cuota_miles, cuota_sim, serie, justif, es_vig, margen=MARGEN):
    """Un PAGO_VALOR por periodo con el dinero real escalado (abono + multa): todo se aplica por prelacion, nada se descarta.
    - bolsa de efectivo: el pago hecho antes del vencimiento (o de mas) se lleva al periodo que lo necesita;
    - justificaciones: la semana justificada sin pago corre el calendario una semana (no cuenta como falta);
    - vigentes: solo ventanas cerradas;
    - cuotas vencidas por efectivo (cv_ef): lo que debia el cliente menos lo que entrego, en cuotas.
    Devuelve (instrucciones, info por periodo, tasa0, modalidad, vencimientos usados, ultimo periodo emitido)."""
    n = len(esp)
    escala = cuota_sim / cuota_miles          # pesos del motor por cada mil pesos reales
    ins, info, usados = [], {}, []
    ult, ult_dia = 0, None
    ea0, mod = usura_en(serie, ini)
    tasa0 = round(max(ea0 - margen, 0.01), 4)
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
        debe = cuota_sim * (k + 1) - acum_pagado
        pago = bolsa if ultima else min(bolsa, max(debe, 0.0) + 0.5)
        if en and 0 < cuota_sim - pago <= TOLERANCIA * cuota_sim and not ultima:
            # pago redondeado ("175.000" por "175.500"): se acepta como cuota completa; la diferencia se registra como recibida
            acum_recibido += cuota_sim - pago
            bolsa = max(0.0, bolsa - (cuota_sim - pago))
            pago = cuota_sim
        sin_dinero = pago <= 0.5 and multa_w <= 0
        if sin_dinero and just and not en:
            desplaz += 1                           # semana justificada sin pago: se corre el calendario
            lo_fijo = lo
            continue
        lo_fijo = None
        usados.append(d)
        if k > 0 and (d.year, d.month) != mes_prev:
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
        dias = 0 if (just or not en) else int(min(max(round(sum(max(0, (f - d).days) * v for f, v in en) / total), 0), 3650))
        proporcional = k == 0 and dias_primera < 7 and total / cuota_miles >= dias_primera / 7 - 0.12
        if sin_dinero:
            ins.append({"periodo": k + 1, "tipo": "SIN_PAGO", "causaMoraYServicios": True})
            info[k + 1] = ("SIN_PAGO", 0.0, 0, just, cv_ef)
        elif proporcional:
            ins.append({"periodo": k + 1, "tipo": "PAGO_VALOR", "valor": str(math.ceil(cuota_sim + multa_w)), "diasAtraso": dias})
            acum_pagado += cuota_sim
            bolsa = max(0.0, bolsa - cuota_sim)
            info[k + 1] = ("PAGO_VALOR (1.a cuota proporcional)", total * 1000, dias, just, cv_ef)
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


def cred(inv, n):
    return {"linea": b.LINEA, "periodicidad": "SEMANAL", "cuotas": int(n), "valorMoto": str(int(inv)), "matriculaRunt": "0", "soat": "0",
            "prenda": "0", "alistamiento": "0", "gpsPlataforma": "0", "gastosComerciales": "0", "pagoInicial": "0"}


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
        "SELECT m.contrato, m.fecha_inicio, i.egrempfinl, (SELECT COUNT(*) FROM rentabilidad_activos r WHERE r.contrato=m.contrato AND r.FechaFinal <= m.fecha_inicio) "
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
    for contrato, ini, egr, previos in vigentes:
        h = hist.get(contrato)
        if h:
            trabajos.append((contrato, ini, CORTE, "VIGENTE", h[3], h[4], h[2], h[1], egr, previos == 0, True))
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
    w_con.writerow(["id", "sede", "contrato", "cohorte", "estado_real", "fecha_inicio", "fecha_fin", "cuotas", "cuota_real_miles",
                    "inversion", "inversion_imputada", "linea_usura", "tasa_inicial_ea", "pagos_reales", "multas_reales_miles", "dias_justificados"])
    w_pag.writerow(["id", "fecha", "valor_miles", "multa_miles"])
    w_res.writerow(["corrida", "id", "sim_pagado", "sim_saldo_final", "sim_pendientes", "sim_mora", "sim_cobranza", "sim_interes", "sim_servicios",
                    "sim_cuotas_vencidas_final", "sim_cuotas_vencidas_max", "sim_marca_recuperacion", "sim_periodo_marca", "sim_estado_final",
                    "sim_periodos", "periodo_corte", "real_ea_con_multas", "cv_efectivo_max", "marca_efectivo", "periodo_marca_efectivo"])
    w_per.writerow(["corrida", "id", "periodo", "fecha_vencimiento", "instruccion", "valor_real", "dias_atraso", "justificado", "estado", "cuotas_vencidas",
                    "saldo_inicial", "interes_corriente", "interes_mora", "cobranza", "servicios", "pago_total_cliente", "saldo_final", "listo_recuperacion", "tasa_ea", "cuota_financiera", "pago_capital", "pago_interes", "pago_mora", "pago_cobranza", "pago_servicios", "capital_vencido", "cv_efectivo", "usura_ea"])
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
            salidas = []
            for corrida in CORRIDAS:
                maxima = corrida.startswith("max_")
                margen = 0.0 if maxima else MARGEN
                tasa_ini = round(max(ea0 - margen, 0.01), 4)
                cuota_sim = cuota_total(inv, n, tasa_ini)
                js = justif if corrida.endswith("con_justificaciones") else set()
                ins, info, tasa0, mod, usados, p_corte = instrucciones(esp, pagos, multas_f, ini, fin, concepto, float(cuota), cuota_sim, serie, js, es_vig, margen)
                if not maxima:
                    tasa_base = tasa0
                r = b.post({"credito": cred(inv, n), "perfilReglas": "NEGOCIO", "instrucciones": ins, "incluirAsientos": False})
                movs = r["movimientos"]
                movs = [m for m in movs if m["periodo"] <= p_corte]
                salidas.append((corrida, ins, info, movs, usados, p_corte))
        except Exception as e:  # noqa: BLE001
            descartes[f"error {type(e).__name__}"] += 1
            continue
        w_con.writerow([cid, SEDE, contrato, "PRIMERO" if primero else "SEGUNDO_O_MAS", concepto, ini, fin if not es_vig else "", int(n), float(cuota),
                        round(inv), imputada, mod, tasa_base, len(pagos), round(multas, 3), len(justif)])
        for fecha, val, multa in reales:
            w_pag.writerow([cid, fecha, val, multa])
        for corrida, ins, info, movs, usados, p_corte in salidas:
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
                            max(cv_ef) if cv_ef else 0, bool(marca_ef), marca_ef])
            for m in movs[1:]:
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
                                round(usura_en(serie, usados[p - 1])[0], 4) if p - 1 < len(usados) else ""])
        hechos += 1
        if hechos % 200 == 0:
            print("  contratos", hechos, flush=True)
    for f in (f_con, f_pag, f_res, f_per):
        f.close()
    with open(os.path.join(OUT, "usura.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(serie[0].keys()))
        w.writeheader()
        w.writerows(serie)
    print("listo:", hechos, "contratos | descartes:", dict(descartes))


if __name__ == "__main__":
    main()
