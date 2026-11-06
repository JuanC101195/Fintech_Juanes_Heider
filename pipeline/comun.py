"""Constantes y utilidades compartidas por backtest.py y cargar.py."""
import os
import subprocess

# Unico lugar donde se relaciona sede -> servidor MySQL (NetBird). Todas tienen la misma BD `db_packgps`.
# Para agregar una sede: una linea aqui (y correr backtest.py --sede NUEVA). Ver docs/backtesting.md.
SEDES = {
    "CARTAGENA": "server-motohoy.netbird.cloud",
    "MONTERIA": "server-motomonteria.netbird.cloud",
    "SINCELEJO": "server-motosince.netbird.cloud",
    "BICIMOTOS": "server-bicimotos.netbird.cloud",
}
DB_NAME_DEFECTO = "db_packgps"
RUN_ID_LEGADO = "legado"

# Columnas de cada CSV (el contrato con cargar.py y con las tablas de db/001 + db/003).
# `sede` y `run_id` van primero en todos. cargar.py carga por NOMBRE de columna.
LLAVE = ["sede", "run_id"]
COLUMNAS = {
    "contratos.csv": LLAVE + ["id", "contrato", "placa", "cohorte", "estado_real", "fecha_inicio", "fecha_fin", "cuotas", "cuota_real_miles",
                              "inversion", "inversion_imputada", "linea_usura", "tasa_inicial_ea", "pagos_reales", "multas_reales_miles",
                              "dias_justificados", "valor_total_miles", "saldo_real_miles"],
    "pagos_reales.csv": LLAVE + ["id", "fecha", "valor_miles", "multa_miles"],
    "resultados.csv": LLAVE + ["corrida", "id", "sim_pagado", "sim_saldo_final", "sim_pendientes", "sim_mora", "sim_cobranza", "sim_interes",
                               "sim_servicios", "sim_cuotas_vencidas_final", "sim_cuotas_vencidas_max", "sim_marca_recuperacion",
                               "sim_periodo_marca", "sim_estado_final", "sim_periodos", "periodo_corte", "real_ea_con_multas",
                               "cv_efectivo_max", "marca_efectivo", "periodo_marca_efectivo", "sim_proyectado_total"],
    "periodos.csv": LLAVE + ["corrida", "id", "periodo", "fecha_vencimiento", "instruccion", "valor_real", "dias_atraso", "justificado", "estado",
                             "cuotas_vencidas", "saldo_inicial", "interes_corriente", "interes_mora", "cobranza", "servicios",
                             "pago_total_cliente", "saldo_final", "listo_recuperacion", "tasa_ea", "cuota_financiera", "pago_capital",
                             "pago_interes", "pago_mora", "pago_cobranza", "pago_servicios", "capital_vencido", "cv_efectivo", "usura_ea",
                             "logica", "pago_fondo", "pago_seguro", "pago_asistencia", "saldo_en_mora", "cxc_mora"],
    "proyeccion.csv": LLAVE + ["corrida", "id", "periodo", "fecha_vencimiento", "proyectado", "pago_capital", "pago_interes", "pago_mora",
                               "pago_cobranza", "pago_servicios", "pago_total", "saldo_final"],
    "activas_fin_mes.csv": LLAVE + ["mes", "fecha_foto", "contrato"],
    "carta_fin_mes.csv": LLAVE + ["mes", "motivo", "pagos", "valor_miles"],
    "corte_activos.csv": LLAVE + ["corte"],
}
# usura.csv es global (sin sede): lo escribe backtest.py con estas columnas y cargar.py lo upsertea en tasa_usura.
COLUMNAS_USURA = ["modalidad", "resolucion", "desde", "hasta", "ibc", "usura_ea", "fuente"]
COLUMNAS_META = ["run_id", "sede", "fecha_corte", "version_motor", "git_sha", "inicio", "fin", "estado"]

RAIZ_REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def leer_env(ruta):
    """Lee un archivo KEY=VALUE (ignora comentarios y lineas vacias). No imprime ni registra valores."""
    cfg = {}
    with open(ruta, encoding="utf-8") as f:
        for linea in f:
            linea = linea.strip()
            if linea and not linea.startswith("#") and "=" in linea:
                k, v = linea.split("=", 1)
                cfg[k.strip()] = v.strip().strip('"').strip("'")
    return cfg


def validar_sede(sede):
    s = (sede or "").strip().upper()
    if s not in SEDES:
        raise ValueError(f"sede desconocida {sede!r}; validas: {', '.join(SEDES)}")
    return s


def git_sha(directorio=RAIZ_REPO):
    """SHA corto del commit del repo (con sufijo -sucio si hay cambios sin commitear). 'desconocido' si no hay git."""
    try:
        sha = subprocess.run(["git", "rev-parse", "--short=12", "HEAD"], cwd=directorio, capture_output=True, text=True, timeout=15, check=True).stdout.strip()
        sucio = subprocess.run(["git", "status", "--porcelain", "--untracked-files=no"], cwd=directorio, capture_output=True, text=True, timeout=15).stdout.strip()
        return sha + ("-sucio" if sucio else "")
    except (OSError, subprocess.SubprocessError):
        return "desconocido"


def normalizar_placa(p):
    p = "".join(ch for ch in (p or "").upper() if ch.isalnum())
    return p or None
