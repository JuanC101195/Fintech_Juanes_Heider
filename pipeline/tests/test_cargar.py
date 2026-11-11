"""Pruebas de db/003_sede.sql y pipeline/cargar.py contra un Postgres 16 local en Docker.
Ejecutar desde la raiz del repo:  py -3.12 -m unittest discover -s pipeline/tests -t . -v"""
import os
import shutil
import tempfile
import unittest

import psycopg

from pipeline import cargar, comun
from pipeline.tests import pg_docker

FX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures")
CARTAGENA, MONTERIA = os.path.join(FX, "cartagena"), os.path.join(FX, "monteria")
RUN = "run-test-1"
TABLAS = ["contrato", "pago_real", "resultado", "periodo", "proyeccion", "activas_fin_mes", "carta_fin_mes", "corte_activos"]


def setUpModule():
    pg_docker.levantar()


def tearDownModule():
    pg_docker.bajar()


def conteos(con, where="", params=()):
    return {t: con.execute(f"SELECT COUNT(*) FROM {t} {where}", params).fetchone()[0] for t in TABLAS}


class BaseBD(unittest.TestCase):
    aplicar_003 = True

    def setUp(self):
        self.bd = pg_docker.nueva_bd()
        self.con = pg_docker.conectar(self.bd)
        pg_docker.aplicar(self.con, "001_esquema.sql", "002_indices.sql", *(["003_sede.sql"] if self.aplicar_003 else []))
        self.tmp = tempfile.mkdtemp()
        self.logs = []

    def tearDown(self):
        self.con.close()
        pg_docker.borrar_bd(self.bd)
        shutil.rmtree(self.tmp, ignore_errors=True)

    def carga(self, sede, carpeta, run=RUN, **kw):
        if run != RUN:   # los CSV traen su run_id: se copian con el pedido
            carpeta = self.con_run(carpeta, run)
        return cargar.cargar(self.con, sede, run, carpeta, log=self.logs.append, **kw)

    def con_run(self, origen, run):
        destino = os.path.join(self.tmp, f"{os.path.basename(origen)}_{run}")
        shutil.copytree(origen, destino)
        for nombre in os.listdir(destino):
            ruta = os.path.join(destino, nombre)
            with open(ruta, encoding="utf-8") as f:
                txt = f.read()
            with open(ruta, "w", encoding="utf-8", newline="") as f:
                f.write(txt.replace(RUN, run))
        return destino

    def copia_fixture(self, origen, cambios):
        """Copia una carpeta de fixtures y reemplaza texto: cambios = [(archivo, viejo, nuevo), ...]."""
        destino = os.path.join(self.tmp, os.path.basename(origen) + "_copia")
        if not os.path.exists(destino):
            shutil.copytree(origen, destino)
        for archivo, viejo, nuevo in cambios:
            ruta = os.path.join(destino, archivo)
            with open(ruta, encoding="utf-8") as f:
                txt = f.read()
            self.assertIn(viejo, txt)
            with open(ruta, "w", encoding="utf-8", newline="") as f:
                f.write(txt.replace(viejo, nuevo))
        return destino


class TestCarga(BaseBD):
    def test_cargar_dos_veces_da_el_mismo_conteo(self):
        r1 = self.carga("CARTAGENA", CARTAGENA)
        c1 = conteos(self.con)
        self.assertEqual(c1["contrato"], 2)
        self.assertEqual(c1["resultado"], 4)
        self.assertEqual(c1["periodo"], 8)
        self.assertEqual(c1["proyeccion"], 4)
        self.assertEqual(r1["filas"]["contrato"], 2)
        r2 = self.carga("CARTAGENA", CARTAGENA)
        self.assertEqual(conteos(self.con), c1)
        self.assertEqual(r2["filas"]["borradas_contrato"], 2)   # reemplazo, no suma
        self.assertEqual(self.con.execute("SELECT COUNT(*) FROM corrida_meta").fetchone()[0], 1)

    def test_mismo_numero_de_contrato_en_dos_sedes_es_independiente(self):
        self.carga("CARTAGENA", CARTAGENA)
        self.carga("MONTERIA", MONTERIA)
        self.assertEqual(self.con.execute("SELECT COUNT(*) FROM contrato WHERE contrato = '1428'").fetchone()[0], 2)
        for sede, placa in (("CARTAGENA", "AAA111"), ("MONTERIA", "CCC333")):
            filas = self.con.execute("SELECT sede, placa FROM contrato WHERE sede = %s AND contrato = '1428'", [sede]).fetchall()
            self.assertEqual(filas, [(sede, placa)])
            # periodos y pagos del 1428 de esa sede no se mezclan con los del otro
            self.assertEqual(self.con.execute("SELECT COUNT(*) FROM periodo WHERE sede = %s AND id = %s", [sede, f"{sede}|1428|2024-01-02"]).fetchone()[0], 4)
            self.assertEqual(self.con.execute("SELECT COUNT(*) FROM pago_real WHERE sede = %s AND id = %s", [sede, f"{sede}|1428|2024-01-02"]).fetchone()[0], 2)
            self.assertEqual(self.con.execute("SELECT COUNT(DISTINCT sede) FROM vw_periodos_contrato WHERE sede = %s AND contrato = '1428'", [sede]).fetchone()[0], 1)
            self.assertEqual(self.con.execute("SELECT COUNT(*) FROM vw_comparacion WHERE sede = %s AND contrato = '1428'", [sede]).fetchone()[0], 2)  # 2 variantes (corrida)
        # recargar Cartagena no altera Monteria
        antes = conteos(self.con, "WHERE sede = 'MONTERIA'")
        self.carga("CARTAGENA", CARTAGENA)
        self.assertEqual(conteos(self.con, "WHERE sede = 'MONTERIA'"), antes)
        self.assertEqual(conteos(self.con)["contrato"], 4)
        # la misma sede no admite dos filas con la misma (sede, run_id, id)
        with self.assertRaises(psycopg.errors.UniqueViolation):
            self.con.execute("INSERT INTO contrato (sede, run_id, id, contrato) VALUES ('MONTERIA', %s, 'MONTERIA|1428|2024-01-02', '1428')", [RUN])
        self.con.rollback()

    def test_placa_en_dos_sedes_avisa_sin_abortar(self):
        self.carga("CARTAGENA", CARTAGENA)
        monteria = self.copia_fixture(MONTERIA, [("contratos.csv", "CCC333", "AAA111")])
        r = self.carga("MONTERIA", monteria)
        self.assertEqual(r["placas_duplicadas"], [("AAA111", ["CARTAGENA"])])
        self.assertTrue(any("AAA111" in m and "AVISO" in m for m in self.logs))
        self.assertEqual(conteos(self.con, "WHERE sede = 'MONTERIA'")["contrato"], 2)   # se cargo igual

    def test_sin_placas_repetidas_no_avisa(self):
        self.carga("CARTAGENA", CARTAGENA)
        r = self.carga("MONTERIA", MONTERIA)
        self.assertEqual(r["placas_duplicadas"], [])

    def test_csv_de_otra_sede_se_rechaza_y_no_cambia_nada(self):
        self.carga("CARTAGENA", CARTAGENA)
        antes = conteos(self.con)
        with self.assertRaises(cargar.ErrorCarga):
            self.carga("CARTAGENA", MONTERIA)
        self.con.rollback()
        self.assertEqual(conteos(self.con), antes)

    def test_error_a_mitad_revierte_todo(self):
        self.carga("CARTAGENA", CARTAGENA)
        antes = conteos(self.con)
        malo = self.copia_fixture(CARTAGENA, [("resultados.csv", "500000", "no-es-numero")])
        with self.assertRaises(psycopg.Error):
            self.carga("CARTAGENA", malo)
        self.con.rollback()
        self.assertEqual(conteos(self.con), antes)   # el DELETE previo tambien se revirtio

    def test_corrida_vigente_es_la_ultima_terminada_y_no_mezcla_sedes(self):
        self.carga("CARTAGENA", CARTAGENA, run="run-A")
        self.carga("MONTERIA", MONTERIA, run="run-A")
        self.carga("CARTAGENA", CARTAGENA, run="run-B")   # dos corridas de la misma sede conviven
        self.assertEqual(self.con.execute("SELECT COUNT(*) FROM contrato WHERE sede='CARTAGENA'").fetchone()[0], 4)
        v = dict(self.con.execute("SELECT sede, run_id FROM vw_corrida_vigente").fetchall())
        self.assertEqual(v, {"CARTAGENA": "run-B", "MONTERIA": "run-A"})
        # purgar deja solo la nueva
        self.carga("CARTAGENA", CARTAGENA, run="run-C", purgar_anteriores=True)
        self.assertEqual(self.con.execute("SELECT DISTINCT run_id FROM contrato WHERE sede='CARTAGENA'").fetchall(), [("run-C",)])
        self.assertEqual(self.con.execute("SELECT COUNT(*) FROM contrato WHERE sede='MONTERIA'").fetchone()[0], 2)

    def test_corrida_fallida_no_es_vigente(self):
        self.carga("CARTAGENA", CARTAGENA, run="run-A")
        mal = self.copia_fixture(CARTAGENA, [("corrida_meta.csv", "terminada", "fallida")])
        self.carga("CARTAGENA", mal)   # run-test-1 queda 'fallida'
        self.assertTrue(any("fallida" in m for m in self.logs))
        self.assertEqual(self.con.execute("SELECT run_id FROM vw_corrida_vigente WHERE sede='CARTAGENA'").fetchone()[0], "run-A")

    def test_modo_activos_reemplaza_solo_vigentes(self):
        self.carga("CARTAGENA", CARTAGENA)
        antes = conteos(self.con)
        self.carga("CARTAGENA", CARTAGENA, modo="activos")   # mismo CSV: los vigentes se reemplazan, los cerrados quedan
        self.assertEqual(conteos(self.con)["contrato"], antes["contrato"])
        self.assertEqual(conteos(self.con)["periodo"], antes["periodo"])
        with self.assertRaises(cargar.ErrorCarga):
            self.carga("CARTAGENA", CARTAGENA, run="no-existe", modo="activos")

    def test_main_con_archivo_env(self):
        env = pg_docker.escribir_env(self.tmp, self.bd)
        self.assertEqual(cargar.main(["--sede", "monteria", "--run-id", RUN, "--csv", MONTERIA, "--pg-env", env]), 0)
        self.assertEqual(self.con.execute("SELECT COUNT(*) FROM contrato WHERE sede='MONTERIA'").fetchone()[0], 2)

    def test_sede_invalida(self):
        with self.assertRaises(ValueError):
            self.carga("MEDELLIN", CARTAGENA)


class TestMigracion003(BaseBD):
    aplicar_003 = False

    def _datos_legado(self):
        c = self.con
        c.execute("INSERT INTO contrato (id, sede, contrato, estado_real, fecha_inicio, cuotas) VALUES "
                  "('CARTAGENA|1428|2024-01-02','CARTAGENA','1428','VIGENTE','2024-01-02',52),"
                  "('CARTAGENA|1500|2024-01-02','CARTAGENA','1500','NORMAL','2024-01-02',52),"
                  "('CARTAGENA|1600|2024-01-02',NULL,'1600','NORMAL','2024-01-02',52)")   # sede NULL en el esquema viejo
        c.execute("INSERT INTO pago_real (id, fecha, valor_miles, multa_miles) VALUES ('CARTAGENA|1428|2024-01-02','2024-01-09',250,0),('CARTAGENA|1428|2024-01-02','2024-01-16',250,0)")
        c.execute("INSERT INTO resultado (corrida, id, sim_pagado) VALUES ('sin','CARTAGENA|1428|2024-01-02',1),('sin','CARTAGENA|1500|2024-01-02',2)")
        c.execute("INSERT INTO periodo (corrida, id, periodo) VALUES ('sin','CARTAGENA|1428|2024-01-02',1),('sin','CARTAGENA|1428|2024-01-02',2)")
        c.execute("INSERT INTO proyeccion (corrida, id, periodo) VALUES ('sin','CARTAGENA|1428|2024-01-02',3)")
        c.execute("INSERT INTO activas_fin_mes (mes, fecha_foto, contrato) VALUES ('2024-01','2024-01-31','1428')")
        c.execute("INSERT INTO carta_fin_mes (mes, motivo, pagos, valor_miles) VALUES ('2024-01','RETIRO',1,300)")
        c.execute("INSERT INTO corte_activos (corte) VALUES ('2026-11-03')")
        c.commit()

    def test_datos_legado_se_conservan(self):
        self._datos_legado()
        antes = conteos(self.con)
        pg_docker.aplicar(self.con, "003_sede.sql")
        self.assertEqual(conteos(self.con), antes)
        for t in TABLAS:
            self.assertEqual(self.con.execute(f"SELECT DISTINCT sede, run_id FROM {t}").fetchall(), [("CARTAGENA", "legado")], t)
        self.assertEqual(self.con.execute("SELECT run_id, sede, estado, fecha_corte::text FROM corrida_meta").fetchall(),
                         [("legado", "CARTAGENA", "terminada", "2026-11-03")])
        self.assertEqual(self.con.execute("SELECT sede, run_id FROM vw_corrida_vigente").fetchall(), [("CARTAGENA", "legado")])
        self.assertEqual(self.con.execute("SELECT COUNT(*) FROM vw_comparacion").fetchone()[0], 2)
        self.assertEqual(self.con.execute("SELECT COUNT(*) FROM vw_periodos_contrato").fetchone()[0], 2)
        self.assertIsNone(self.con.execute("SELECT placa FROM contrato LIMIT 1").fetchone()[0])

    def test_es_idempotente(self):
        self._datos_legado()
        pg_docker.aplicar(self.con, "003_sede.sql")
        despues_1 = conteos(self.con)
        pg_docker.aplicar(self.con, "003_sede.sql")
        pg_docker.aplicar(self.con, "003_sede.sql")
        self.assertEqual(conteos(self.con), despues_1)
        self.assertEqual(self.con.execute("SELECT COUNT(*) FROM corrida_meta").fetchone()[0], 1)

    def test_tras_migrar_se_puede_cargar_otra_sede_sin_tocar_el_legado(self):
        self._datos_legado()
        pg_docker.aplicar(self.con, "003_sede.sql")
        antes = conteos(self.con, "WHERE sede = 'CARTAGENA' AND run_id = 'legado'")
        cargar.cargar(self.con, "MONTERIA", RUN, MONTERIA, log=self.logs.append)
        self.assertEqual(conteos(self.con, "WHERE sede = 'CARTAGENA' AND run_id = 'legado'"), antes)
        # el 1428 de Monteria convive con el 1428 legado de Cartagena
        self.assertEqual(self.con.execute("SELECT sede FROM contrato WHERE contrato = '1428' ORDER BY sede").fetchall(), [("CARTAGENA",), ("MONTERIA",)])
        # y una corrida nueva de Cartagena pasa a ser la vigente sin borrar el legado
        cargar.cargar(self.con, "CARTAGENA", RUN, CARTAGENA, log=self.logs.append)
        self.assertEqual(self.con.execute("SELECT run_id FROM vw_corrida_vigente WHERE sede='CARTAGENA'").fetchone()[0], RUN)
        self.assertEqual(conteos(self.con, "WHERE sede = 'CARTAGENA' AND run_id = 'legado'"), antes)

    def test_fk_compuesta_impide_huerfanos(self):
        pg_docker.aplicar(self.con, "003_sede.sql")
        with self.assertRaises(psycopg.errors.ForeignKeyViolation):
            self.con.execute("INSERT INTO pago_real (sede, run_id, id, fecha) VALUES ('MONTERIA','x','MONTERIA|9|2024-01-01','2024-01-01')")
        self.con.rollback()


class TestFixtures(unittest.TestCase):
    def test_encabezados_de_fixtures_coinciden_con_el_contrato_de_columnas(self):
        for carpeta in (CARTAGENA, MONTERIA):
            for archivo, cols in comun.COLUMNAS.items():
                with open(os.path.join(carpeta, archivo), encoding="utf-8") as f:
                    self.assertEqual(f.readline().strip().split(","), cols, archivo)


if __name__ == "__main__":
    unittest.main()
