"""Pagos por CARTA (liquidaciones de cierre) por mes de las motos activas el ultimo dia de ese mes -> tabla carta_fin_mes en PostgreSQL.
Esos pagos (np_his_abonos.CARTA = 'SI', salvo PRONTO PAGO) no son cuotas: el motor no los usa y el recaudo real de la pagina tampoco los incluye.
Uso: py -3.12 cargar_cartas_fin_mes.py   (MySQL solo SELECT por NetBird; escribe en PG)"""
import os
import sys
from datetime import date, timedelta

import psycopg

sys.path.insert(0, os.path.dirname(__file__))
import backtest_fase_a as b  # noqa: E402

cfg = dict(l.strip().split("=", 1) for l in open(os.path.expanduser("~/.motor-cartera-pg.env")) if "=" in l)

con = b.conectar()
cur = con.cursor()
cur.execute("SELECT DATE_FORMAT(fecha_registro,'%Y-%m'), MAX(fecha_registro) FROM motos_app_hist WHERE fecha_registro >= '2024-04-01' "
            "AND fecha_registro < DATE_FORMAT(CURDATE(), '%Y-%m-01') GROUP BY 1 ORDER BY 1")
meses = cur.fetchall()
filas = []
for mes, ultimo in meses:
    primero = date(ultimo.year, ultimo.month, 1)
    cur.execute(
        "SELECT n.CARTAMOTIVO, COUNT(*), SUM(n.ABONO + n.TRANSF) FROM np_his_abonos n WHERE n.FECHA >= %s AND n.FECHA <= %s "
        "AND n.CARTA = 'SI' AND n.CARTAMOTIVO <> 'PRONTO PAGO' "
        "AND n.CONTRATO IN (SELECT DISTINCT TRIM(contrato) FROM motos_app_hist WHERE fecha_registro = %s) GROUP BY 1",
        [primero, ultimo, ultimo])
    for motivo, n, valor in cur.fetchall():
        filas.append((mes, motivo, int(n), float(valor or 0)))
    print(mes, sum(f[3] for f in filas if f[0] == mes), flush=True)
con.close()

with psycopg.connect(host=cfg["PGHOST"], port=cfg["PGPORT"], user=cfg["PGUSER"], password=cfg["PGPASSWORD"], dbname="backtesting", sslmode="require") as pg:
    c = pg.cursor()
    c.execute("DROP TABLE IF EXISTS carta_fin_mes")
    c.execute("CREATE TABLE carta_fin_mes (mes text, motivo text, pagos int, valor_miles numeric, PRIMARY KEY (mes, motivo))")
    with c.copy("COPY carta_fin_mes FROM STDIN") as cp:
        for f in filas:
            cp.write_row(f)
    pg.commit()
    print("filas:", c.execute("SELECT COUNT(*) FROM carta_fin_mes").fetchone()[0])
