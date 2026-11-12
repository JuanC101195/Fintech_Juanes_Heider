"""Motos activas al ULTIMO dia de cada mes (foto de motos_app_hist) -> tabla activas_fin_mes en PostgreSQL.
Un mes cerrado se analiza con la flota que habia ese dia, no con la de motos_app de hoy.
Uso: py -3.12 cargar_activas_fin_mes.py   (lee MySQL por NetBird, solo SELECT; escribe en PG)"""
import os
import sys

import psycopg

sys.path.insert(0, os.path.dirname(__file__))
import backtest_fase_a as b  # noqa: E402

ENV = os.path.expanduser("~/.motor-cartera-pg.env")
cfg = dict(l.strip().split("=", 1) for l in open(ENV) if "=" in l)

con = b.conectar()
cur = con.cursor()
cur.execute("SELECT DATE_FORMAT(fecha_registro,'%Y-%m'), MAX(fecha_registro) FROM motos_app_hist WHERE fecha_registro >= '2024-04-01' "
            "AND fecha_registro < DATE_FORMAT(CURDATE(), '%Y-%m-01') GROUP BY 1 ORDER BY 1")
meses = cur.fetchall()
filas = []
for mes, dia in meses:
    cur.execute("SELECT DISTINCT TRIM(contrato) FROM motos_app_hist WHERE fecha_registro = %s AND contrato IS NOT NULL AND TRIM(contrato) <> ''", [dia])
    contratos = [c[0] for c in cur.fetchall()]
    print(mes, dia, len(contratos), flush=True)
    filas += [(mes, dia, c) for c in contratos]
con.close()

with psycopg.connect(host=cfg["PGHOST"], port=cfg["PGPORT"], user=cfg["PGUSER"], password=cfg["PGPASSWORD"], dbname="backtesting", sslmode="require") as pg:
    c = pg.cursor()
    c.execute("DROP TABLE IF EXISTS activas_fin_mes")
    c.execute("CREATE TABLE activas_fin_mes (mes text, fecha_foto date, contrato text, PRIMARY KEY (mes, contrato))")
    c.execute("CREATE INDEX ix_activas_contrato ON activas_fin_mes(contrato)")
    with c.copy("COPY activas_fin_mes FROM STDIN") as cp:
        for f in dict(((m, k), (m, d, k)) for m, d, k in filas).values():
            cp.write_row(f)
    pg.commit()
    print("filas:", c.execute("SELECT COUNT(*) FROM activas_fin_mes").fetchone()[0])
