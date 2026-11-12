"""Consulta un contrato en la base de backtesting (Azure). Uso: python consultar_contrato.py 2842 [corrida]
corrida: sin_justificaciones (por defecto) o con_justificaciones. La clave se lee de ~/.motor-cartera-pg.env."""
import os
import sys

import psycopg

cfg = dict(l.strip().split("=", 1) for l in open(os.path.expanduser("~/.motor-cartera-pg.env")) if "=" in l)
contrato = sys.argv[1]
corrida = sys.argv[2] if len(sys.argv) > 2 else "sin_justificaciones"
with psycopg.connect(host=cfg["PGHOST"], port=cfg["PGPORT"], user=cfg["PGUSER"], password=cfg["PGPASSWORD"], dbname="backtesting", sslmode="require") as con:
    cur = con.cursor()
    cur.execute("SELECT id, estado_real, cohorte, fecha_inicio, fecha_fin, cuotas, inversion, inversion_imputada, tasa_inicial_ea, real_pagado_cuotas, real_multas, "
                "sim_total_con_deuda, sim_mora, sim_cobranza, sim_marca_recuperacion, sim_periodo_marca FROM vw_comparacion WHERE contrato=%s AND corrida=%s ORDER BY fecha_inicio",
                (contrato, corrida))
    for fila in cur.fetchall():
        print("\n== episodio", fila[0])
        for n, v in zip(("estado", "cohorte", "inicio", "fin", "cuotas", "inversion", "imputada", "tasa inicial EA", "real pagado cuotas", "real multas",
                         "financiera total", "mora sim", "cobranza sim", "marca recuperacion", "periodo marca"), fila[1:]):
            print(f"  {n}: {v}")
        cur.execute("SELECT periodo, fecha_vencimiento, instruccion, valor_real, dias_atraso, justificado, estado, cuotas_vencidas, interes_mora, cobranza, saldo_final "
                    "FROM periodo WHERE id=%s AND corrida=%s ORDER BY periodo", (fila[0], corrida))
        print("  periodo | vence | instruccion | real | dias | just | estado | venc | mora | cobranza | saldo")
        for p in cur.fetchall():
            print("  " + " | ".join(str(x) for x in p))
