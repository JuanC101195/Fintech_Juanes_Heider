"""Carga incremental de los contratos ACTIVOS (motos_app) al PostgreSQL del backtesting.
Reemplaza solo las filas de los vigentes (y de los ids que traiga el CSV); los contratos cerrados y las corridas de usura no se tocan.
Uso: py -3.12 cargar_activos.py <carpeta-salida>   (la salida de backtest_v11.py con SOLO_ACTIVOS=1)"""
import csv
import os
import sys

import psycopg

ENV = os.path.expanduser("~/.motor-cartera-pg.env")
cfg = dict(l.strip().split("=", 1) for l in open(ENV) if "=" in l)
SALIDA = sys.argv[1]


def vacio(v):
    return None if v == "" else v


ids = set()
with open(os.path.join(SALIDA, "contratos.csv"), encoding="utf-8", newline="") as f:
    for r in csv.DictReader(f):
        ids.add(r["id"])
print("ids a cargar:", len(ids), flush=True)

with psycopg.connect(host=cfg["PGHOST"], port=cfg["PGPORT"], user=cfg["PGUSER"], password=cfg["PGPASSWORD"], dbname="backtesting", sslmode="require") as con:
    cur = con.cursor()
    cur.execute("SELECT COUNT(*) FROM contrato WHERE estado_real = 'VIGENTE'")
    print("vigentes antes:", cur.fetchone()[0], flush=True)
    cur.execute("ALTER TABLE contrato ADD COLUMN IF NOT EXISTS valor_total_miles numeric, ADD COLUMN IF NOT EXISTS saldo_real_miles numeric")
    cur.execute("ALTER TABLE periodo ADD COLUMN IF NOT EXISTS pago_fondo numeric, ADD COLUMN IF NOT EXISTS pago_seguro numeric, ADD COLUMN IF NOT EXISTS pago_asistencia numeric, ADD COLUMN IF NOT EXISTS saldo_en_mora numeric, ADD COLUMN IF NOT EXISTS cxc_mora numeric")
    cur.execute("ALTER TABLE resultado ADD COLUMN IF NOT EXISTS sim_proyectado_total numeric")
    cur.execute("CREATE TABLE IF NOT EXISTS proyeccion (corrida text, id text, periodo int, fecha_vencimiento date, proyectado boolean, pago_capital numeric, "
                "pago_interes numeric, pago_mora numeric, pago_cobranza numeric, pago_servicios numeric, pago_total numeric, saldo_final numeric, PRIMARY KEY (corrida, id, periodo))")
    cur.execute("CREATE INDEX IF NOT EXISTS ix_proyeccion_fecha ON proyeccion(fecha_vencimiento)")
    cur.execute("CREATE TEMP TABLE nuevos(id text)")
    with cur.copy("COPY nuevos FROM STDIN") as cp:
        for i in ids:
            cp.write_row([i])
    cur.execute("CREATE TEMP TABLE borrar AS SELECT id FROM contrato WHERE estado_real = 'VIGENTE' UNION SELECT id FROM nuevos")
    for t in ("proyeccion", "periodo", "resultado", "pago_real", "contrato"):
        cur.execute(f"DELETE FROM {t} WHERE id IN (SELECT id FROM borrar)")
        print("borradas de", t, cur.rowcount, flush=True)
    for tabla, archivo in (("contrato", "contratos.csv"), ("pago_real", "pagos_reales.csv"), ("resultado", "resultados.csv"), ("periodo", "periodos.csv"), ("proyeccion", "proyeccion.csv")):
        with open(os.path.join(SALIDA, archivo), encoding="utf-8", newline="") as f:
            lector = csv.DictReader(f)
            with cur.copy(f"COPY {tabla} ({', '.join(lector.fieldnames)}) FROM STDIN") as cp:
                n = 0
                for r in lector:
                    cp.write_row([vacio(v) for v in r.values()])
                    n += 1
        print(tabla, n, "filas", flush=True)
    if len(sys.argv) > 2:   # fecha de corte de los datos (py cargar_activos.py salida 2026-11-03)
        cur.execute("CREATE TABLE IF NOT EXISTS corte_activos (corte date)")
        cur.execute("DELETE FROM corte_activos")
        cur.execute("INSERT INTO corte_activos VALUES (%s)", [sys.argv[2]])
    con.commit()
    cur.execute("SELECT estado_real, COUNT(*) FROM contrato GROUP BY 1 ORDER BY 2 DESC")
    print(cur.fetchall())
