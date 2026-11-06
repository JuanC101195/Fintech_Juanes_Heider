"""Carga los CSV de UNA sede y UNA corrida (run_id) al PostgreSQL del backtesting, en una sola transaccion.

Uso:
  py -3.12 pipeline/cargar.py --sede MONTERIA --run-id 20261106T150000Z-abc1234 --csv salida\\monteria --pg-env RUTA\\pg.env

Reglas:
  - Reemplaza SOLO las filas con sede=X y run_id=Y (nunca DROP ni TRUNCATE). Si algo falla, no queda nada a medias (ROLLBACK).
  - Idempotente: cargar dos veces el mismo CSV deja los mismos conteos.
  - Las otras sedes y las otras corridas de la misma sede no se tocan (salvo --purgar-anteriores, que borra las corridas
    anteriores de ESTA sede despues de cargar).
  - Control de calidad: una placa que aparezca en dos sedes genera un AVISO (no aborta).
  - --modo activos: refresco incremental (salida de backtest.py --solo-activos): reemplaza solo los contratos del CSV
    y los VIGENTES de esa sede y corrida; la corrida debe existir ya.

El archivo --pg-env es KEY=VALUE: PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE (por defecto backtesting), PGSSLMODE (por defecto require).
"""
import argparse
import csv
import os
import sys

if __package__ in (None, ""):
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pipeline import comun  # noqa: E402

# Orden de carga (padres primero) y de borrado (hijos primero).
TABLAS_CARGA = [("contrato", "contratos.csv"), ("pago_real", "pagos_reales.csv"), ("resultado", "resultados.csv"),
                ("periodo", "periodos.csv"), ("proyeccion", "proyeccion.csv"),
                ("activas_fin_mes", "activas_fin_mes.csv"), ("carta_fin_mes", "carta_fin_mes.csv"), ("corte_activos", "corte_activos.csv")]
TABLAS_BORRADO = ["proyeccion", "periodo", "resultado", "pago_real", "contrato", "activas_fin_mes", "carta_fin_mes", "corte_activos"]
TABLAS_POR_ID = ["proyeccion", "periodo", "resultado", "pago_real", "contrato"]   # modo activos
OBLIGATORIOS = {"contratos.csv", "pagos_reales.csv", "resultados.csv", "periodos.csv"}


class ErrorCarga(Exception):
    pass


def conectar(pg_env):
    import psycopg
    cfg = comun.leer_env(pg_env)
    return psycopg.connect(host=cfg["PGHOST"], port=cfg.get("PGPORT") or 5432, user=cfg["PGUSER"], password=cfg["PGPASSWORD"],
                           dbname=cfg.get("PGDATABASE") or "backtesting", sslmode=cfg.get("PGSSLMODE") or "require")


def _vacio(v):
    return None if v == "" else v


def _columnas_tabla(cur, tabla):
    cur.execute("SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = %s", [tabla])
    return {r[0] for r in cur.fetchall()}


def _filas_csv(ruta, sede, run_id):
    """Devuelve (columnas, iterador de filas). Valida/inyecta sede y run_id: si el CSV los trae deben coincidir."""
    with open(ruta, encoding="utf-8", newline="") as f:
        cols = list(csv.DictReader(f).fieldnames or [])
    for c in ("sede", "run_id"):
        if c not in cols:
            cols.append(c)

    def gen():
        with open(ruta, encoding="utf-8", newline="") as f:
            for i, r in enumerate(csv.DictReader(f), start=2):
                if r.get("sede") not in (None, "", sede):
                    raise ErrorCarga(f"{os.path.basename(ruta)} linea {i}: sede {r['sede']!r} distinta de {sede!r}; no se carga una sede con el CSV de otra")
                if r.get("run_id") not in (None, "", run_id):
                    raise ErrorCarga(f"{os.path.basename(ruta)} linea {i}: run_id {r['run_id']!r} distinto de {run_id!r}")
                r["sede"], r["run_id"] = sede, run_id
                yield [_vacio(r[c]) for c in cols]
    return cols, gen()


def _copiar(cur, tabla, ruta, sede, run_id):
    cols, filas = _filas_csv(ruta, sede, run_id)
    desconocidas = [c for c in cols if c not in _columnas_tabla(cur, tabla)]
    if desconocidas:
        raise ErrorCarga(f"{os.path.basename(ruta)}: columnas que no existen en {tabla}: {desconocidas} (falta aplicar db/003_sede.sql?)")
    n = 0
    with cur.copy(f"COPY {tabla} ({', '.join(cols)}) FROM STDIN") as cp:
        for fila in filas:
            cp.write_row(fila)
            n += 1
    return n


def _upsert_usura(cur, ruta):
    n = 0
    with open(ruta, encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            cur.execute(
                "INSERT INTO tasa_usura (modalidad, resolucion, vigencia_desde, vigencia_hasta, interes_bancario_corriente, usura_ea, fuente) "
                "VALUES (%s,%s,%s,%s,%s,%s,%s) ON CONFLICT (modalidad, vigencia_desde) DO UPDATE SET resolucion=EXCLUDED.resolucion, "
                "vigencia_hasta=EXCLUDED.vigencia_hasta, interes_bancario_corriente=EXCLUDED.interes_bancario_corriente, "
                "usura_ea=EXCLUDED.usura_ea, fuente=EXCLUDED.fuente",
                [_vacio(r[k]) for k in ("modalidad", "resolucion", "desde", "hasta", "ibc", "usura_ea", "fuente")])
            n += 1
    return n


def _upsert_meta(cur, ruta, sede, run_id):
    with open(ruta, encoding="utf-8", newline="") as f:
        filas = [r for r in csv.DictReader(f) if r["sede"] == sede and r["run_id"] == run_id]
    if len(filas) != 1:
        raise ErrorCarga(f"corrida_meta.csv debe traer exactamente una fila para sede={sede} run_id={run_id} (trae {len(filas)})")
    m = filas[0]
    cur.execute(
        "INSERT INTO corrida_meta (run_id, sede, fecha_corte, version_motor, git_sha, inicio, fin, estado) VALUES (%s,%s,%s,%s,%s,%s,%s,%s) "
        "ON CONFLICT (sede, run_id) DO UPDATE SET fecha_corte=EXCLUDED.fecha_corte, version_motor=EXCLUDED.version_motor, git_sha=EXCLUDED.git_sha, "
        "inicio=EXCLUDED.inicio, fin=EXCLUDED.fin, estado=EXCLUDED.estado",
        [m["run_id"], m["sede"], _vacio(m["fecha_corte"]), _vacio(m["version_motor"]), _vacio(m["git_sha"]), _vacio(m["inicio"]), _vacio(m["fin"]), m["estado"] or "terminada"])
    return m["estado"]


def placas_duplicadas(cur, sede, run_id):
    """Placas de esta sede/corrida que tambien estan en OTRA sede (en su corrida vigente). Control de calidad: no deberia haber ninguna."""
    cur.execute(
        "SELECT c.placa, array_agg(DISTINCT o.sede ORDER BY o.sede) FROM contrato c "
        "JOIN contrato o ON o.placa = c.placa AND o.sede <> c.sede "
        "JOIN vw_corrida_vigente v ON v.sede = o.sede AND v.run_id = o.run_id "
        "WHERE c.sede = %s AND c.run_id = %s AND c.placa IS NOT NULL GROUP BY c.placa ORDER BY c.placa", [sede, run_id])
    return [(p, s) for p, s in cur.fetchall()]


def cargar(conexion, sede, run_id, carpeta, modo="completo", purgar_anteriores=False, log=print):
    """Carga una sede/corrida en UNA transaccion sobre `conexion`. Devuelve {'filas': {...}, 'placas_duplicadas': [...]}."""
    sede = comun.validar_sede(sede)
    if not run_id or run_id.strip() != run_id or "|" in run_id:
        raise ErrorCarga("run_id vacio o con espacios/'|'")
    if modo not in ("completo", "activos"):
        raise ErrorCarga("modo debe ser 'completo' o 'activos'")
    faltan = [a for a in OBLIGATORIOS if not os.path.exists(os.path.join(carpeta, a))]
    if faltan:
        raise ErrorCarga(f"faltan archivos en {carpeta}: {sorted(faltan)}")
    if modo == "completo" and not os.path.exists(os.path.join(carpeta, "corrida_meta.csv")):
        raise ErrorCarga("falta corrida_meta.csv (lo escribe backtest.py)")

    filas = {}
    try:
        with conexion.transaction():   # todo o nada
            cur = conexion.cursor()
            cur.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", ["cargar:" + sede])   # una carga a la vez por sede
            if modo == "completo":
                for t in TABLAS_BORRADO:
                    cur.execute(f"DELETE FROM {t} WHERE sede = %s AND run_id = %s", [sede, run_id])
                    filas[f"borradas_{t}"] = cur.rowcount
                if os.path.exists(os.path.join(carpeta, "usura.csv")):
                    filas["tasa_usura"] = _upsert_usura(cur, os.path.join(carpeta, "usura.csv"))
                estado = _upsert_meta(cur, os.path.join(carpeta, "corrida_meta.csv"), sede, run_id)
                if estado != "terminada":
                    log(f"AVISO: la corrida {run_id} de {sede} esta en estado {estado!r}; se carga pero no sera la vigente")
            else:
                if cur.execute("SELECT 1 FROM corrida_meta WHERE sede = %s AND run_id = %s", [sede, run_id]).fetchone() is None:
                    raise ErrorCarga(f"modo activos: la corrida {run_id} de {sede} no existe; cargue primero la corrida completa")
                ids = set()
                with open(os.path.join(carpeta, "contratos.csv"), encoding="utf-8", newline="") as f:
                    ids = {r["id"] for r in csv.DictReader(f)}
                cur.execute("CREATE TEMP TABLE _borrar ON COMMIT DROP AS SELECT id FROM contrato WHERE sede = %s AND run_id = %s AND estado_real = 'VIGENTE'", [sede, run_id])
                with cur.copy("COPY _borrar (id) FROM STDIN") as cp:
                    for i in ids:
                        cp.write_row([i])
                for t in TABLAS_POR_ID:
                    cur.execute(f"DELETE FROM {t} WHERE sede = %s AND run_id = %s AND id IN (SELECT id FROM _borrar)", [sede, run_id])
                    filas[f"borradas_{t}"] = cur.rowcount
                cur.execute("DELETE FROM corte_activos WHERE sede = %s AND run_id = %s", [sede, run_id])

            for tabla, archivo in TABLAS_CARGA:
                ruta = os.path.join(carpeta, archivo)
                if not os.path.exists(ruta):
                    continue
                if modo == "activos" and tabla in ("activas_fin_mes", "carta_fin_mes"):
                    continue
                filas[tabla] = _copiar(cur, tabla, ruta, sede, run_id)

            if purgar_anteriores and modo == "completo":
                for t in TABLAS_BORRADO:
                    cur.execute(f"DELETE FROM {t} WHERE sede = %s AND run_id <> %s", [sede, run_id])
                cur.execute("DELETE FROM corrida_meta WHERE sede = %s AND run_id <> %s", [sede, run_id])

            dup = placas_duplicadas(cur, sede, run_id)
            for placa, otras in dup:
                log(f"AVISO: la placa {placa} de {sede} tambien aparece en {', '.join(otras)} (la placa no deberia repetirse entre sedes)")
    except Exception:
        log("ERROR: transaccion revertida; la base queda como estaba")
        raise
    return {"filas": filas, "placas_duplicadas": dup}


def main(argv=None):
    ap = argparse.ArgumentParser(description="Carga una sede y una corrida al PostgreSQL del backtesting (una transaccion).")
    ap.add_argument("--sede", required=True, help="|".join(comun.SEDES))
    ap.add_argument("--run-id", required=True, help="id de la corrida (el de corrida_meta.csv que escribio backtest.py)")
    ap.add_argument("--csv", required=True, help="carpeta con los CSV de la sede")
    ap.add_argument("--pg-env", required=True, help="archivo KEY=VALUE con PGHOST, PGPORT, PGUSER, PGPASSWORD, [PGDATABASE], [PGSSLMODE]")
    ap.add_argument("--modo", choices=("completo", "activos"), default="completo")
    ap.add_argument("--purgar-anteriores", action="store_true", help="tras cargar, borra las demas corridas de ESTA sede")
    a = ap.parse_args(argv)
    con = conectar(a.pg_env)
    try:
        r = cargar(con, a.sede, a.run_id, a.csv, a.modo, a.purgar_anteriores)
    finally:
        con.close()
    for k, v in r["filas"].items():
        print(f"{k}: {v}")
    print(f"placas repetidas entre sedes: {len(r['placas_duplicadas'])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
