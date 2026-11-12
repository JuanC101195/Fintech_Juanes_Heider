"""Backtesting fase A (solo lectura en la BD): motos con un solo cliente, cierres ya ocurridos.

Opcion A: cada cuota cubierta (acumulado abono+transf) se paga en el motor el mismo dia con la cuota financiada.
Resultados con contratos reales -> solo en esta carpeta, nunca al repo.
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
from datetime import timedelta

import pymysql

API = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8099"
SALIDA = sys.argv[2] if len(sys.argv) > 2 else "fase_a.csv"
LINEA = "CPPU"
TOL = float(os.environ.get("TOL", "0.01"))
ENV = r"C:\Users\LeNoVo\Documents\proyecto_modelos_2\.env"
DIAS = {"lun": 0, "mar": 1, "mie": 2, "jue": 3, "vie": 4, "sab": 5, "dom": 6}


def conectar():
    cfg = {}
    for linea in open(ENV, encoding="utf-8"):
        linea = linea.strip()
        if linea and not linea.startswith("#") and "=" in linea:
            k, v = linea.split("=", 1)
            cfg[k.strip()] = v.strip().strip('"').strip("'")
    return pymysql.connect(host=cfg["DB_HOST"], port=int(cfg["DB_PORT"] or 3306), user=cfg["DB_USER"],
                           password=cfg["DB_PASSWORD"], database=cfg["DB_NAME"], connect_timeout=15, read_timeout=300)


def num(x):
    try:
        return float(x)
    except (TypeError, ValueError):
        return 0.0


def dia_semana(txt):
    t = unicodedata.normalize("NFD", (txt or "").strip().lower())
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    return DIAS[t[:3]]


def fechas_esperadas(inicio, dia_canon, n):
    objetivo = dia_semana(dia_canon)
    d = inicio + timedelta(days=1)
    while d.weekday() != objetivo:
        d += timedelta(days=1)
    return [d + timedelta(days=7 * i) for i in range(n)]


def post(cuerpo):
    req = urllib.request.Request(API + "/api/v1/escenarios/ejecutar", data=json.dumps(cuerpo).encode(),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read())


def cubrir(esperadas, pagos, cuota):
    cum, i, cub = 0.0, 0, {}
    for f, v in pagos:
        cum += v
        while i < len(esperadas) and cum >= (i + 1) * cuota * 0.95:
            cub[i + 1] = f
            i += 1
    return cub


def instrucciones(esperadas, cub, fin, concepto):
    ins, ultimo = [], 0
    for p, d in enumerate(esperadas, start=1):
        if p in cub:
            ins.append({"periodo": p, "tipo": "PAGO_CONTRACTUAL", "diasAtraso": max(0, (cub[p] - d).days)})
            ultimo = p
        elif d <= fin:
            ins.append({"periodo": p, "tipo": "SIN_PAGO", "causaMoraYServicios": True})
    if concepto == "PRONTO PAGO" and ultimo < len(esperadas):
        ins = [x for x in ins if x["periodo"] <= ultimo]
        dias = (fin - esperadas[ultimo - 1]).days if ultimo else 0
        ins.append({"periodo": ultimo + 1, "tipo": "PREPAGO_TOTAL", "diasDesdeUltimaCuota": max(0, min(dias, 3650))})
    return ins


_CUOTA_SIM = {}


def cuota_total_sim(inv, n):
    clave = (int(inv), int(n))
    if clave not in _CUOTA_SIM:
        req = urllib.request.Request(API + "/api/v1/simulaciones", data=json.dumps({
            "linea": LINEA, "periodicidad": "SEMANAL", "cuotas": int(n), "valorMoto": str(int(inv)), "matriculaRunt": "0",
            "soat": "0", "prenda": "0", "alistamiento": "0", "gpsPlataforma": "0", "gastosComerciales": "0",
            "pagoInicial": "0", "perfilReglas": "NEGOCIO"}).encode(), headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=120) as r:
            _CUOTA_SIM[clave] = num(json.loads(r.read())["resumen"]["cuotaTotal"])
    return _CUOTA_SIM[clave]


def por_ventana(esperadas, pagos, ini, fin, concepto, cuota_miles, cuota_sim):
    """Cada periodo recibe lo que el cliente pago en su ventana [vencimiento, siguiente vencimiento); sin pago = SIN_PAGO.
    El valor se escala a la cuota financiada (misma fraccion de la cuota que pago en la realidad)."""
    n = len(esperadas)
    ins, ultimo_con_pago, ultimo_dia = [], 0, None
    for i, d in enumerate(esperadas):
        if d > fin:
            break
        lo = ini + timedelta(days=1) if i == 0 else d
        hi = min(esperadas[i + 1], fin + timedelta(days=1)) if i + 1 < n else fin + timedelta(days=1)
        en = [(f, v) for f, v in pagos if lo <= f < hi]
        total = sum(v for _, v in en)
        if total <= 0:
            ins.append({"periodo": i + 1, "tipo": "SIN_PAGO", "causaMoraYServicios": True})
            continue
        dias = round(sum(max(0, (f - d).days) * v for f, v in en) / total)
        dias = int(min(max(dias, 0), 3650))
        if abs(total / cuota_miles - 1) <= TOL or (TOL > 0.05 and 1 - TOL <= total / cuota_miles < 1):
            # Pago de la cuota completa: exacto, sin residuos de centavos que el motor leeria como cuota vencida.
            ins.append({"periodo": i + 1, "tipo": "PAGO_CONTRACTUAL", "diasAtraso": dias})
        else:
            ins.append({"periodo": i + 1, "tipo": "PAGO_VALOR", "valor": str(math.ceil(total * cuota_sim / cuota_miles)),
                        "diasAtraso": dias})
        ultimo_con_pago, ultimo_dia = i + 1, max(f for f, _ in en)
    if concepto == "PRONTO PAGO" and ultimo_con_pago:
        ins = [x for x in ins if x["periodo"] <= ultimo_con_pago]
        dias = max(0, (ultimo_dia - esperadas[ultimo_con_pago - 1]).days)
        ins[-1] = {"periodo": ultimo_con_pago, "tipo": "PREPAGO_TOTAL", "diasDesdeUltimaCuota": min(dias, 3650)}
    return ins


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


def main():
    con = conectar()
    cur = con.cursor()
    cur.execute(
        "SELECT r.contrato, r.fecha_inicio, r.FechaFinal, r.CONCEPTO, r.cuota, r.no_canon, r.dia_canon, r.tipopago, i.egrempfinl "
        "FROM rentabilidad_activos r JOIN motos_inv i ON i.contrato=r.contrato "
        "WHERE r.contrato REGEXP '^[0-9]+$' AND r.CONCEPTO <> 'CARTA MEDELLIN' AND i.egrempfinl > 0 AND r.cuota > 0 AND r.no_canon > 0 "
        "AND (SELECT COUNT(DISTINCT x.fecha_inicio) FROM rentabilidad_activos x WHERE x.contrato=r.contrato) = 1 "
        "AND r.contrato NOT IN (SELECT m.contrato FROM motos_app m) "
        "GROUP BY r.contrato, r.fecha_inicio")
    cohorte = cur.fetchall()
    print("cohorte (1 solo cliente, cerrada, no vigente):", len(cohorte))
    contratos = sorted({c[0] for c in cohorte})
    pagos_por = defaultdict(list)
    for i in range(0, len(contratos), 500):
        trozo = contratos[i:i + 500]
        cur.execute("SELECT CONTRATO, FECHA, ABONO+TRANSF, MULTA, CARTA, CARTAMOTIVO FROM np_his_abonos WHERE CONTRATO IN (%s) "
                    "ORDER BY FECHA, HORA" % ",".join(["%s"] * len(trozo)), trozo)
        for fila in cur.fetchall():
            pagos_por[fila[0]].append(fila[1:])
    con.close()

    filas, descartes = [], defaultdict(int)
    for contrato, ini, fin, concepto, cuota, n, dia, tipopago, inv in cohorte:
        if tipopago != "SEMANAL":
            descartes["no semanal"] += 1
            continue
        if n > 104:
            descartes["mas de 104 cuotas"] += 1
            continue
        pagos, multas, flujos, flujos_m = [], 0.0, [], []
        for fecha, val, multa, carta, motivo in pagos_por.get(contrato, []):
            if not (ini < fecha <= fin):
                continue
            multas += num(multa)
            if carta == "SI" and motivo != "PRONTO PAGO":
                continue
            if num(val) > 0:
                pagos.append((fecha, num(val)))
                flujos.append((fecha, num(val) * 1000))
                flujos_m.append((fecha, num(val) * 1000))
            if num(multa) > 0:
                flujos_m.append((fecha, num(multa) * 1000))
        if not pagos:
            descartes["sin pagos"] += 1
            continue
        try:
            esperadas = fechas_esperadas(ini, dia, int(n))
            cub = cubrir(esperadas, pagos, float(cuota))
            ins = por_ventana(esperadas, pagos, ini, fin, concepto, float(cuota), cuota_total_sim(inv, n))
            cred = {"linea": LINEA, "periodicidad": "SEMANAL", "cuotas": int(n), "valorMoto": str(int(inv)),
                    "matriculaRunt": "0", "soat": "0", "prenda": "0", "alistamiento": "0", "gpsPlataforma": "0",
                    "gastosComerciales": "0", "pagoInicial": "0"}
            r = post({"credito": cred, "perfilReglas": "NEGOCIO", "instrucciones": ins, "incluirAsientos": False})
        except Exception as e:  # noqa: BLE001
            descartes[f"error {type(e).__name__}"] += 1
            continue
        res, movs = r["resumen"], r["movimientos"]
        p_retiro = sum(1 for d in esperadas if d <= fin)
        completo = concepto in ("NORMAL", "PRONTO PAGO")
        if not completo:
            # El motor da por pagadas a tiempo las semanas sin instruccion: se corta en el retiro real.
            movs = [m for m in movs if m["periodo"] <= p_retiro]
            marcados = [m["periodo"] for m in movs if m.get("listoParaRecuperacion")]
            res = dict(res, totalPagadoCliente=sumar(movs, "pagoTotalCliente"), estadoFinal=movs[-1].get("estado"),
                       periodoFinal=movs[-1]["periodo"], periodoListoParaRecuperacion=marcados[0] if marcados else None,
                       cuotasVencidasMax=max(num(m.get("cuotasVencidas")) for m in movs))
        ult = movs[-1]
        cxc = sum(num(ult.get(k)) for k in ("cxcMora", "cxcCobranza", "cxcInteresCorriente", "cxcServicios"))
        inv_f = float(inv)
        ea = ea_implicita(inv_f, ini, flujos) if completo else None
        ea_m = ea_implicita(inv_f, ini, flujos_m) if completo else None
        filas.append({
            "contrato": contrato, "cierre": concepto, "cuotas": int(n), "inicio": ini, "fin": fin,
            "inversion": inv_f, "real_cuotas": sum(v for _, v in flujos), "real_multas": multas * 1000,
            "real_ea": ea, "real_ea_con_multas": ea_m, "cuotas_cubiertas": len(cub), "periodo_retiro": p_retiro,
            "sim_monto_fin": num(res.get("montoFinanciado")), "sim_pagado": num(res.get("totalPagadoCliente")),
            "sim_saldo_final": num(ult.get("saldoFinal")), "sim_cxc": cxc, "sim_estado": res.get("estadoFinal"),
            "sim_periodo_final": res.get("periodoFinal"), "sim_venc_max": res.get("cuotasVencidasMax"),
            "sim_listo_recuperacion": res.get("periodoListoParaRecuperacion"),
            "sim_mora": sumar(movs, "interesMora"), "sim_cobranza": sumar(movs, "cobranzaCausada"),
            "sim_interes": sumar(movs, "interesCorriente"), "sim_servicios": sumar(movs, "serviciosCausados"),
        })
        if len(filas) % 100 == 0:
            print("  procesados", len(filas), flush=True)

    with open(SALIDA, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(filas[0].keys()))
        w.writeheader()
        w.writerows(filas)
    print("descartes:", dict(descartes), "| resultados:", len(filas), "->", SALIDA)

    por = defaultdict(list)
    for x in filas:
        por[x["cierre"]].append(x)
    for cierre, xs in sorted(por.items(), key=lambda kv: -len(kv[1])):
        print(f"\n== {cierre}: {len(xs)} contratos")
        if cierre in ("NORMAL", "PRONTO PAGO"):
            real = [x["real_cuotas"] + x["real_multas"] for x in xs]
            sim = [x["sim_pagado"] + x["sim_saldo_final"] + x["sim_cxc"] for x in xs]
            dif = [(s - r) / r * 100 for s, r in zip(sim, real) if r > 0]
            eas = [x["real_ea"] for x in xs if x["real_ea"] is not None]
            eam = [x["real_ea_con_multas"] for x in xs if x["real_ea_con_multas"] is not None]
            print(f"  real (cuotas+multas) mediana {st.median(real)/1e6:.2f} M | financiera mediana {st.median(sim)/1e6:.2f} M")
            print(f"  diferencia mediana {st.median(dif):.1f} % (p10 {sorted(dif)[len(dif)//10]:.1f} %, p90 {sorted(dif)[9*len(dif)//10]:.1f} %)")
            print(f"  multas reales mediana {st.median(x['real_multas'] for x in xs)/1e6:.2f} M | mora simulada mediana {st.median(x['sim_mora'] for x in xs)/1e6:.2f} M | cobranza simulada media {st.mean(x['sim_cobranza'] for x in xs)/1e6:.2f} M")
            if eas:
                eam_o, eas_o = sorted(eam), sorted(eas)
                print(f"  EA implicita real: mediana {st.median(eas)*100:.0f} % sin multas, {st.median(eam)*100:.0f} % con multas "
                      f"(p10 {eam_o[len(eam_o)//10]*100:.0f} %, p90 {eam_o[9*len(eam_o)//10]*100:.0f} %); sobre 87,62 %: {sum(1 for v in eam if v > 0.8762)} de {len(eam)}")
        else:
            listo = [x for x in xs if x["sim_listo_recuperacion"] not in (None, "")]
            print(f"  cuotas cubiertas mediana {st.median(x['cuotas_cubiertas'] for x in xs):.0f} de {st.median(x['cuotas'] for x in xs):.0f}; "
                  f"cuotas vencidas max mediana {st.median(num(x['sim_venc_max']) for x in xs):.0f}")
            print(f"  el motor marca 'listo para recuperacion': {len(listo)} de {len(xs)}")
            if listo:
                brecha = [x["periodo_retiro"] - num(x["sim_listo_recuperacion"]) for x in listo]
                print(f"  periodos entre la marca y el retiro real: mediana {st.median(brecha):.0f} (p10 {sorted(brecha)[len(brecha)//10]:.0f}, p90 {sorted(brecha)[9*len(brecha)//10]:.0f})")
            print(f"  pagado real mediana {st.median(x['real_cuotas'] for x in xs)/1e6:.2f} M | deuda de capital al retiro (simulada) mediana {st.median(x['sim_saldo_final'] for x in xs)/1e6:.2f} M")


if __name__ == "__main__":
    main()
