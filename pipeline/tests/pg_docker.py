"""Postgres 16 local en Docker para las pruebas (contenedor `pg-p6`, puerto 55432). Nunca toca Azure."""
import os
import subprocess
import time
import uuid

import psycopg

NOMBRE = "pg-p6"
PUERTO = int(os.environ.get("P6_PG_PUERTO", "55432"))
RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
_creado_por_nosotros = False


def _docker(*args, check=True):
    return subprocess.run(["docker", *args], capture_output=True, text=True, check=check)


def _admin(db="postgres"):
    return psycopg.connect(host="localhost", port=PUERTO, user="postgres", password="test", dbname=db, autocommit=True, connect_timeout=3)


def levantar():
    global _creado_por_nosotros
    existe = NOMBRE in _docker("ps", "-a", "--format", "{{.Names}}").stdout.split()
    if not existe:
        _docker("run", "-d", "--name", NOMBRE, "-e", "POSTGRES_PASSWORD=test", "-p", f"{PUERTO}:5432", "postgres:16")
        _creado_por_nosotros = True
    elif NOMBRE not in _docker("ps", "--format", "{{.Names}}").stdout.split():
        _docker("start", NOMBRE)
    limite = time.time() + 90
    while time.time() < limite:
        try:
            _admin().close()
            return
        except psycopg.OperationalError:
            time.sleep(1)
    raise RuntimeError("Postgres local no quedo listo en 90 s")


def bajar():
    """Apaga y borra el contenedor solo si lo creo esta corrida de pruebas."""
    if _creado_por_nosotros:
        _docker("rm", "-f", NOMBRE, check=False)


def nueva_bd():
    """Crea una BD vacia y devuelve su nombre."""
    nombre = "t_" + uuid.uuid4().hex[:10]
    with _admin() as c:
        c.execute(f"CREATE DATABASE {nombre}")
    return nombre


def borrar_bd(nombre):
    with _admin() as c:
        c.execute(f"DROP DATABASE IF EXISTS {nombre} WITH (FORCE)")


def conectar(nombre):
    return psycopg.connect(host="localhost", port=PUERTO, user="postgres", password="test", dbname=nombre)


def aplicar(con, *archivos):
    """Aplica scripts de db/ (en el orden dado) a la conexion."""
    for a in archivos:
        with open(os.path.join(RAIZ, "db", a), encoding="utf-8") as f:
            con.execute(f.read())
    con.commit()


def escribir_env(carpeta, bd):
    ruta = os.path.join(carpeta, "pg.env")
    with open(ruta, "w", encoding="utf-8") as f:
        f.write(f"PGHOST=localhost\nPGPORT={PUERTO}\nPGUSER=postgres\nPGPASSWORD=test\nPGDATABASE={bd}\nPGSSLMODE=disable\n")
    return ruta
