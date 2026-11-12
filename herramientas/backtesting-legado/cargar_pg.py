"""Crea el esquema del backtesting en PostgreSQL (Azure) y carga los CSV. La clave sale de ~/.motor-cartera-pg.env (no se imprime)."""
import csv
import os
import sys

import psycopg

ENV = os.path.expanduser("~/.motor-cartera-pg.env")
cfg = dict(l.strip().split("=", 1) for l in open(ENV) if "=" in l)
SALIDA = sys.argv[1] if len(sys.argv) > 1 else "salida"

DDL = """
CREATE TABLE IF NOT EXISTS tasa_usura (
  modalidad text, resolucion text, vigencia_desde date, vigencia_hasta date, interes_bancario_corriente numeric, usura_ea numeric, fuente text,
  PRIMARY KEY (modalidad, vigencia_desde));
CREATE TABLE IF NOT EXISTS contrato (
  id text PRIMARY KEY, sede text, contrato text, cohorte text, estado_real text, fecha_inicio date, fecha_fin date, cuotas int,
  cuota_real_miles numeric, inversion numeric, inversion_imputada boolean, linea_usura text, tasa_inicial_ea numeric,
  pagos_reales int, multas_reales_miles numeric, dias_justificados int);
CREATE TABLE IF NOT EXISTS pago_real (id text REFERENCES contrato(id), fecha date, valor_miles numeric, multa_miles numeric);
CREATE INDEX IF NOT EXISTS ix_pago_real_id ON pago_real(id);
CREATE TABLE IF NOT EXISTS resultado (
  corrida text, id text REFERENCES contrato(id), sim_pagado numeric, sim_saldo_final numeric, sim_pendientes numeric, sim_mora numeric,
  sim_cobranza numeric, sim_interes numeric, sim_servicios numeric, sim_cuotas_vencidas_final numeric, sim_cuotas_vencidas_max numeric,
  sim_marca_recuperacion boolean, sim_periodo_marca int, sim_estado_final text, sim_periodos int, periodo_corte int, real_ea_con_multas numeric,
  PRIMARY KEY (corrida, id));
ALTER TABLE resultado ADD COLUMN IF NOT EXISTS cv_efectivo_max numeric, ADD COLUMN IF NOT EXISTS marca_efectivo boolean, ADD COLUMN IF NOT EXISTS periodo_marca_efectivo int;
CREATE TABLE IF NOT EXISTS periodo (
  corrida text, id text REFERENCES contrato(id), periodo int, fecha_vencimiento date, instruccion text, valor_real numeric, dias_atraso int,
  justificado boolean, estado text, cuotas_vencidas numeric, saldo_inicial numeric, interes_corriente numeric, interes_mora numeric, cobranza numeric,
  servicios numeric, pago_total_cliente numeric, saldo_final numeric, listo_recuperacion boolean, tasa_ea numeric,
  cuota_financiera numeric, pago_capital numeric, pago_interes numeric, pago_mora numeric, pago_cobranza numeric, pago_servicios numeric, capital_vencido numeric,
  PRIMARY KEY (corrida, id, periodo));
ALTER TABLE periodo ADD COLUMN IF NOT EXISTS cv_efectivo int, ADD COLUMN IF NOT EXISTS usura_ea numeric, ADD COLUMN IF NOT EXISTS logica text;
ALTER TABLE periodo ADD COLUMN IF NOT EXISTS pago_fondo numeric, ADD COLUMN IF NOT EXISTS pago_seguro numeric, ADD COLUMN IF NOT EXISTS pago_asistencia numeric, ADD COLUMN IF NOT EXISTS saldo_en_mora numeric, ADD COLUMN IF NOT EXISTS cxc_mora numeric;
DROP VIEW IF EXISTS vw_periodos_contrato; DROP VIEW IF EXISTS vw_comparacion;
CREATE OR REPLACE VIEW vw_comparacion AS
SELECT c.id, c.sede, c.contrato, c.cohorte, c.estado_real, c.fecha_inicio, c.fecha_fin, c.cuotas, c.inversion, c.inversion_imputada, r.corrida,
       (SELECT COALESCE(SUM(valor_miles),0)*1000 FROM pago_real p WHERE p.id=c.id) AS real_pagado_cuotas,
       c.multas_reales_miles*1000 AS real_multas,
       r.sim_pagado, r.sim_saldo_final, r.sim_pendientes, r.sim_pagado + r.sim_saldo_final + r.sim_pendientes AS sim_total_con_deuda,
       r.sim_mora, r.sim_cobranza, r.sim_interes, r.sim_servicios, r.sim_cuotas_vencidas_max, r.sim_marca_recuperacion, r.sim_periodo_marca,
       r.sim_estado_final, c.tasa_inicial_ea, c.linea_usura, r.real_ea_con_multas, r.cv_efectivo_max, r.marca_efectivo, r.periodo_marca_efectivo
FROM contrato c JOIN resultado r ON r.id=c.id;
CREATE OR REPLACE VIEW vw_periodos_contrato AS
SELECT c.contrato, c.sede, c.estado_real, c.cohorte, p.* FROM periodo p JOIN contrato c ON c.id=p.id;
"""

TABLAS = [
    ("tasa_usura", "usura.csv", "modalidad,resolucion,vigencia_desde,vigencia_hasta,interes_bancario_corriente,usura_ea,fuente",
     lambda r: [r["modalidad"], r["resolucion"], r["desde"], r["hasta"], r["ibc"], r["usura_ea"], r["fuente"]]),
]


def vacio(v):
    return None if v == "" else v


with psycopg.connect(host=cfg["PGHOST"], port=cfg["PGPORT"], user=cfg["PGUSER"], password=cfg["PGPASSWORD"], dbname="postgres",
                     sslmode="require", autocommit=True) as c0:
    if not c0.execute("SELECT 1 FROM pg_database WHERE datname='backtesting'").fetchone():
        c0.execute("CREATE DATABASE backtesting")
with psycopg.connect(host=cfg["PGHOST"], port=cfg["PGPORT"], user=cfg["PGUSER"], password=cfg["PGPASSWORD"], dbname="backtesting", sslmode="require") as con:
    cur = con.cursor()
    cur.execute("DROP VIEW IF EXISTS vw_periodos_contrato, vw_comparacion; TRUNCATE TABLE IF EXISTS periodo, resultado, pago_real, contrato, tasa_usura CASCADE") \
        if False else None
    cur.execute("DROP VIEW IF EXISTS vw_periodos_contrato; DROP TABLE IF EXISTS periodo")
    cur.execute(DDL)
    cur.execute("TRUNCATE periodo, resultado, pago_real, contrato, tasa_usura CASCADE")
    cargas = [
        ("tasa_usura", "usura.csv", ["modalidad", "resolucion", "vigencia_desde", "vigencia_hasta", "interes_bancario_corriente", "usura_ea", "fuente"],
         lambda r: [r["modalidad"], r["resolucion"], r["desde"], r["hasta"], r["ibc"], r["usura_ea"], r["fuente"]]),
        ("contrato", "contratos.csv", None, lambda r: [vacio(v) for v in r.values()]),
        ("pago_real", "pagos_reales.csv", None, lambda r: [vacio(v) for v in r.values()]),
        ("resultado", "resultados.csv", None, lambda r: [vacio(v) for v in r.values()]),
        ("periodo", "periodos.csv", "ENCABEZADO", lambda r: [vacio(v) for v in r.values()]),
    ]
    for tabla, archivo, columnas, fila in cargas:
        with open(os.path.join(SALIDA, archivo), encoding="utf-8", newline="") as f:
            lector = csv.DictReader(f)
            cols = ", ".join(lector.fieldnames if columnas == "ENCABEZADO" else columnas) if columnas else ""   # periodo: por nombre, segun el encabezado del CSV
            with cur.copy(f"COPY {tabla} {'(' + cols + ')' if cols else ''} FROM STDIN") as cp:
                n = 0
                for r in lector:
                    cp.write_row(fila(r))
                    n += 1
        print(tabla, n, "filas")
    con.commit()
    for t in ("contrato", "pago_real", "resultado", "periodo", "tasa_usura"):
        print(t, cur.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0])
