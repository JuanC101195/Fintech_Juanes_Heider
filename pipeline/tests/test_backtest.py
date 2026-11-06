"""Pruebas de pipeline/backtest.py SIN base de datos de origen ni motor real: usa un motor falso local (http.server).
Comprueba: tabla de sedes, reintentos, columnas de los CSV, sede/run_id propagados y que los hilos no cambian el resultado."""
import csv
import json
import os
import shutil
import tempfile
import threading
import unittest
from datetime import date
from http.server import BaseHTTPRequestHandler, HTTPServer

from pipeline import backtest, comun


def movimientos(n=30):
    movs = [{"periodo": 0, "estado": "ACTIVO", "saldoFinal": 9000000}]
    for p in range(1, n + 1):
        movs.append({"periodo": p, "estado": "ACTIVO", "cuotasVencidas": 0, "saldoInicial": 9000000 - 1000 * p, "saldoFinal": 9000000 - 1000 * (p + 1),
                     "cuotaFinanciera": 250000, "serviciosCausados": {"total": 10000}, "pagoTotalCliente": 260000, "interesCorriente": 1000,
                     "tasaPeriodica": 0.004, "pagoCapital": 200000, "pagoInteresCorriente": 1000, "pagosServicios": {"seguro": 1.0}})
    return movs


class MotorFalso(BaseHTTPRequestHandler):
    fallos_pendientes = 0     # los primeros N POST responden 503 (para probar reintentos)
    llamadas = 0
    lock = threading.Lock()

    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length", 0)))
        with MotorFalso.lock:
            MotorFalso.llamadas += 1
            falla = MotorFalso.fallos_pendientes > 0
            if falla:
                MotorFalso.fallos_pendientes -= 1
        if falla:
            self.send_response(503)
            self.end_headers()
            return
        cuerpo = json.dumps({"movimientos": movimientos()}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(cuerpo)

    def log_message(self, *a):
        pass


class TestBacktest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.srv = HTTPServer(("127.0.0.1", 0), MotorFalso)
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()
        cls.api = f"http://127.0.0.1:{cls.srv.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        MotorFalso.fallos_pendientes, MotorFalso.llamadas = 0, 0

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    # ---- sedes
    def test_tabla_de_sedes(self):
        self.assertEqual(set(comun.SEDES), {"CARTAGENA", "MONTERIA", "SINCELEJO", "BICIMOTOS"})
        self.assertEqual(comun.SEDES["MONTERIA"], "server-motomonteria.netbird.cloud")
        self.assertEqual(comun.validar_sede(" montería".replace("í", "i")), "MONTERIA")
        with self.assertRaises(ValueError):
            comun.validar_sede("MEDELLIN")

    def test_placa_normalizada(self):
        self.assertEqual(comun.normalizar_placa(" abc-123 "), "ABC123")
        self.assertIsNone(comun.normalizar_placa("  "))

    # ---- reintentos
    def test_reintenta_5xx_y_se_recupera(self):
        MotorFalso.fallos_pendientes = 2
        r = backtest.post(self.api, {"x": 1}, reintentos=3, espera=0.01)
        self.assertIn("movimientos", r)
        self.assertEqual(MotorFalso.llamadas, 3)

    def test_se_rinde_tras_los_reintentos(self):
        MotorFalso.fallos_pendientes = 99
        with self.assertRaises(backtest.ErrorMotor):
            backtest.post(self.api, {"x": 1}, reintentos=2, espera=0.01)
        self.assertEqual(MotorFalso.llamadas, 3)

    # ---- CSV
    def _trabajos(self):
        ini = date(2024, 1, 2)   # martes
        pagos = [(date(2024, 1, 9), 250.0, 0, "NO", None), (date(2024, 1, 16), 250.0, 0, "NO", None), (date(2024, 1, 23), 250.0, 0, "NO", None)]
        trabajos = [("1428", ini, date(2024, 2, 20), "RETIRO", 250.0, 52, "mar", "SEMANAL", 9000000.0, True, False),
                    ("1500", ini, date(2024, 2, 20), "RETIRO", 250.0, 52, "mar", "SEMANAL", 9000000.0, True, False),
                    ("1600", ini, date(2024, 2, 20), "RETIRO", 250.0, 52, "mar", "QUINCENAL", 9000000.0, True, False)]   # se descarta
        pagos_por = {"1428": pagos, "1500": pagos, "1600": pagos}
        serie = [{"modalidad": "CREDITO POPULAR PRODUCTIVO URBANO", "resolucion": "r", "desde": "2020-01-01", "hasta": "2030-01-01", "ibc": 0.2, "usura_ea": 0.3, "fuente": "x"}]
        return trabajos, pagos_por, serie

    def _correr(self, sede, workers, salida):
        trabajos, pagos_por, serie = self._trabajos()
        ctx = backtest.Contexto(sede, "run-x", self.api, serie, ({}, {}, 0.4), pagos_por, {}, {}, backtest.CORRIDAS_ACTIVOS, 1)
        os.makedirs(salida)
        return backtest.procesar(ctx, trabajos, {"1428": "AAA111"}, workers, salida)

    def _leer(self, salida, nombre):
        with open(os.path.join(salida, nombre), encoding="utf-8", newline="") as f:
            return list(csv.reader(f))

    def test_csv_con_sede_run_id_y_columnas_del_contrato(self):
        salida = os.path.join(self.tmp, "mon")
        hechos, descartes = self._correr("MONTERIA", 4, salida)
        self.assertEqual(hechos, 2)
        self.assertEqual(descartes, {"no semanal / incompleto": 1})
        for nombre in ("contratos.csv", "pagos_reales.csv", "resultados.csv", "periodos.csv", "proyeccion.csv"):
            filas = self._leer(salida, nombre)
            self.assertEqual(filas[0], comun.COLUMNAS[nombre], nombre)
            for f in filas[1:]:
                self.assertEqual(len(f), len(filas[0]), nombre)
                self.assertEqual(f[:2], ["MONTERIA", "run-x"], nombre)
        con = self._leer(salida, "contratos.csv")
        self.assertEqual(con[1][2:5], ["MONTERIA|1428|2024-01-02", "1428", "AAA111"])   # id incluye la sede
        self.assertEqual(con[2][4], "")                                                  # sin placa conocida
        self.assertEqual(len(self._leer(salida, "resultados.csv")) - 1, 2 * 2)           # 2 contratos x 2 corridas de activos

    def test_resultado_no_depende_de_los_hilos(self):
        a, b = os.path.join(self.tmp, "w1"), os.path.join(self.tmp, "w8")
        self._correr("CARTAGENA", 1, a)
        self._correr("CARTAGENA", 8, b)
        for nombre in ("contratos.csv", "pagos_reales.csv", "resultados.csv", "periodos.csv", "proyeccion.csv"):
            self.assertEqual(self._leer(a, nombre), self._leer(b, nombre), nombre)

    def test_id_de_cartagena_conserva_el_formato_historico(self):
        salida = os.path.join(self.tmp, "car")
        self._correr("CARTAGENA", 2, salida)
        self.assertEqual(self._leer(salida, "contratos.csv")[1][2], "CARTAGENA|1428|2024-01-02")


if __name__ == "__main__":
    unittest.main()
