import os

import psycopg

cfg = dict(l.strip().split("=", 1) for l in open(os.path.expanduser("~/.motor-cartera-pg.env")) if "=" in l)
con = psycopg.connect(host=cfg["PGHOST"], port=cfg["PGPORT"], user=cfg["PGUSER"], password=cfg["PGPASSWORD"], dbname="backtesting", sslmode="require")
cur = con.cursor()


def q(titulo, sql):
    cur.execute(sql)
    cols = [d.name for d in cur.description]
    print("\n==", titulo)
    print(" | ".join(cols))
    for f in cur.fetchall():
        print(" | ".join("" if x is None else (f"{float(x):,.2f}" if isinstance(x, (int, float)) or type(x).__name__ == "Decimal" else str(x)) for x in f))


GRUPO = ("CASE WHEN estado_real IN ('NORMAL','PRONTO PAGO') THEN 'cerro pagando' WHEN estado_real='VIGENTE' THEN 'vigente' ELSE 'retirado' END")
q("contratos por cohorte y grupo", f"SELECT cohorte, {GRUPO} grupo, COUNT(DISTINCT id) contratos, SUM(inversion_imputada::int)/2 imputados FROM vw_comparacion GROUP BY 1,2 ORDER BY 1,2")
q("marca de recuperacion y cobranza por grupo y corrida",
  f"SELECT {GRUPO} grupo, corrida, COUNT(*) n, ROUND(100.0*AVG(sim_marca_recuperacion::int),1) pct_marcados, "
  "ROUND(100.0*AVG((sim_cobranza>0)::int),1) pct_con_cobranza, ROUND(AVG(sim_cobranza)/1e6,2) cobranza_media_M, "
  "ROUND(AVG(sim_cuotas_vencidas_max),1) venc_max_media FROM vw_comparacion GROUP BY 1,2 ORDER BY 1,2")
q("cerraron pagando, PRIMER contrato (inversion exacta): real vs financiera",
  "SELECT corrida, COUNT(*) n, ROUND((SUM(real_pagado_cuotas+real_multas))/1e9,2) real_mil_M, ROUND(SUM(sim_total_con_deuda)/1e9,2) financiera_mil_M, "
  "ROUND(100.0*(SUM(sim_total_con_deuda)/SUM(real_pagado_cuotas+real_multas)-1),1) dif_pct, "
  "ROUND((PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY 100.0*(sim_total_con_deuda/(real_pagado_cuotas+real_multas)-1)))::numeric,1) dif_mediana_pct, "
  "ROUND((PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY 100.0*(sim_total_con_deuda/(real_pagado_cuotas+real_multas)-1)))::numeric,1) p10, "
  "ROUND((PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY 100.0*(sim_total_con_deuda/(real_pagado_cuotas+real_multas)-1)))::numeric,1) p90, "
  "ROUND((PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY real_multas))::numeric/1e6,2) multas_reales_med_M, "
  "ROUND((PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY sim_mora))::numeric/1e6,2) mora_sim_med_M "
  "FROM vw_comparacion WHERE cohorte='PRIMERO' AND estado_real IN ('NORMAL','PRONTO PAGO') AND (real_pagado_cuotas+real_multas)>0 GROUP BY corrida ORDER BY corrida")
q("tasa efectiva anual implicita real (primeros, cerraron pagando)",
  "SELECT estado_real, COUNT(*) n, ROUND((PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY real_ea_con_multas))::numeric*100) mediana_pct, "
  "ROUND((PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY real_ea_con_multas))::numeric*100) p10, ROUND((PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY real_ea_con_multas))::numeric*100) p90, "
  "ROUND(AVG(tasa_inicial_ea)*100,1) usura_menos_1_media_pct, ROUND(100.0*AVG((real_ea_con_multas>tasa_inicial_ea)::int),1) pct_real_sobre_usura "
  "FROM vw_comparacion WHERE corrida='sin_justificaciones' AND cohorte='PRIMERO' AND estado_real IN ('NORMAL','PRONTO PAGO') AND real_ea_con_multas IS NOT NULL GROUP BY 1")
q("efecto de las justificaciones (contratos con dias justificados)",
  "SELECT a.corrida, COUNT(*) n, ROUND(100.0*AVG(a.sim_marca_recuperacion::int),1) pct_marcados, ROUND(100.0*AVG((a.sim_cobranza>0)::int),1) pct_con_cobranza, "
  "ROUND(AVG(a.sim_cobranza)/1e6,2) cobranza_media_M, ROUND(AVG(a.sim_mora)/1e6,3) mora_media_M FROM vw_comparacion a JOIN contrato c ON c.id=a.id WHERE c.dias_justificados>0 GROUP BY 1 ORDER BY 1")
q("cuantos contratos tienen justificaciones y cuantos dias",
  "SELECT COUNT(*) FILTER (WHERE dias_justificados>0) con_just, COUNT(*) total, SUM(dias_justificados) dias_total FROM contrato")
q("vigentes: cuotas vencidas max, corrida sin justificaciones",
  "SELECT CASE WHEN sim_cuotas_vencidas_max=0 THEN '0' WHEN sim_cuotas_vencidas_max=1 THEN '1' WHEN sim_cuotas_vencidas_max<=3 THEN '2-3' WHEN sim_cuotas_vencidas_max<=6 THEN '4-6' ELSE '7+' END tramo, "
  "COUNT(*) n FROM vw_comparacion WHERE corrida='sin_justificaciones' AND estado_real='VIGENTE' GROUP BY 1 ORDER BY 1")
q("retirados: marcados antes del retiro (corrida con justificaciones)",
  "SELECT estado_real, COUNT(*) n, ROUND(100.0*AVG(sim_marca_recuperacion::int),0) pct_marcados, ROUND(AVG(sim_cobranza)/1e6,2) cobranza_media_M, "
  "ROUND(AVG(sim_pagado)/1e6,2) pagado_med_M, ROUND(AVG(sim_saldo_final)/1e6,2) capital_pendiente_med_M FROM vw_comparacion WHERE corrida='con_justificaciones' "
  "AND estado_real NOT IN ('NORMAL','PRONTO PAGO','VIGENTE') GROUP BY 1 ORDER BY 2 DESC")
q("segundos o posteriores (inversion imputada), cerraron pagando",
  "SELECT corrida, COUNT(*) n, ROUND(100.0*(SUM(sim_total_con_deuda)/SUM(real_pagado_cuotas+real_multas)-1),1) dif_pct FROM vw_comparacion "
  "WHERE cohorte='SEGUNDO_O_MAS' AND estado_real IN ('NORMAL','PRONTO PAGO') AND (real_pagado_cuotas+real_multas)>0 GROUP BY 1 ORDER BY 1")
q("evolucion de la usura aplicada (tasa inicial media por ano de inicio)",
  "SELECT EXTRACT(YEAR FROM fecha_inicio)::int anio, COUNT(*) n, ROUND(AVG(tasa_inicial_ea)*100,1) tasa_inicial_media_pct FROM contrato GROUP BY 1 ORDER BY 1")
con.close()
