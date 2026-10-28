"""Pruebas del mapeo de la hoja Validaciones al motor, sin red: usan la respuesta real de
docs/api-ejemplos/caso-13-negocio.json. Uso: python -I herramientas/test_llenar_validaciones.py"""
import copy
import hashlib
import json
import sys
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import openpyxl

import llenar_validaciones as v

EJEMPLO = v.RAIZ / 'docs' / 'api-ejemplos' / 'caso-13-negocio.json'


def respuesta():
    return json.loads(EJEMPLO.read_text(encoding='utf-8'))


class ExtractoresTest(unittest.TestCase):
    def setUp(self):
        self.r = respuesta()

    def test_campo_del_periodo(self):
        self.assertEqual(v.campo('saldoFinal')(self.r, 0), Decimal('5904400'))
        self.assertEqual(v.campo('abonoExtra')(self.r, 42), Decimal('1111017.408502946089287426466091208'))
        self.assertEqual(v.campo('cuotasVencidas')(self.r, 42), Decimal(0))

    def test_campo_anidado(self):
        m = v.movimiento(self.r, 42)
        self.assertEqual(v.campo('pendientesPagadosExtra.servicios.total')(self.r, 42),
                         Decimal(m['pendientesPagadosExtra']['servicios']['total']))

    def test_periodo_inexistente_es_key_error(self):
        leer = v.campo('saldoFinal')
        with self.assertRaises(KeyError):
            leer(self.r, 104)

    def test_resultados_del_credito(self):
        self.assertEqual(v.ultimo_periodo(self.r), Decimal(84))
        self.assertEqual(v.cuotas_eliminadas(self.r), Decimal(104 - 84))
        self.assertEqual(v.periodos_adicionales(self.r), Decimal(0))
        self.assertEqual(v.estado_final(self.r), 'CANCELADO')

    def test_periodos_adicionales_cuenta_lo_que_pasa_de_las_cuotas(self):
        r = copy.deepcopy(self.r)
        r['credito']['cuotas'] = 80
        self.assertEqual(v.periodos_adicionales(r), Decimal(4))

    def test_primer_cambio_de_tasa(self):
        with self.assertRaises(KeyError):  # el caso 13 no cambia la tasa
            v.primer_cambio_de_tasa(self.r)
        r = copy.deepcopy(self.r)
        for m in r['movimientos']:
            if m['periodo'] >= 44:
                m['tasaPeriodica'] = '0.0113677166736779'
        self.assertEqual(v.primer_cambio_de_tasa(r), Decimal(44))


class ComparacionTest(unittest.TestCase):
    def test_dinero_con_tolerancia_de_un_peso(self):
        self.assertEqual(v.comparar('Dinero', Decimal('100.4'), 100.0, Decimal(1)), (Decimal('0.4'), 'OK'))
        self.assertEqual(v.comparar('Dinero', Decimal('101.5'), 100.0, Decimal(1))[1], 'FALLA')

    def test_conteo_exacto(self):
        self.assertEqual(v.comparar('Conteo', Decimal(71), 71, Decimal(0))[1], 'OK')
        self.assertEqual(v.comparar('Conteo', Decimal(72), 71, Decimal(0))[1], 'FALLA')

    def test_tasa(self):
        self.assertEqual(v.comparar('Tasa', Decimal('0.0121744443208027'), 0.012174444320802724, Decimal('1e-6'))[1], 'OK')

    def test_estado_exacto_sin_tildes_ni_mayusculas(self):
        self.assertEqual(v.normalizar_estado('Vigente al día'), 'VIGENTE_AL_DIA')
        self.assertEqual(v.comparar('Exacto', 'EN_MORA', 'En mora', Decimal(0)), (Decimal(0), 'OK'))
        self.assertEqual(v.comparar('Exacto', 'CANCELADO', 'En mora', Decimal(0))[1], 'FALLA')


class MapeoTest(unittest.TestCase):
    def test_cubre_las_45_pruebas_con_su_caso(self):
        self.assertEqual(sorted(v.MAPEO), list(range(1, 46)))
        self.assertTrue(all(caso in v.HOJAS for caso, _ in v.MAPEO.values()))

    def test_caso_distinto_queda_sin_mapeo(self):
        mapeo, motivo = v.mapeo_de(1, 'Caso 2')
        self.assertIsNone(mapeo)
        self.assertTrue(motivo.startswith('sin mapeo:'))
        self.assertIsNone(v.mapeo_de(99, 'Caso 1')[0])

    def test_ruta_de_salida_nunca_es_el_original(self):
        with tempfile.TemporaryDirectory() as d:
            original = Path(d) / 'libro.xlsx'
            original.write_bytes(b'x')
            primera = v.ruta_de_salida(original, Path(d), 'EXCEL')
            self.assertNotEqual(primera, original)
            primera.write_bytes(b'y')
            self.assertNotEqual(v.ruta_de_salida(original, Path(d), 'EXCEL'), primera)


class LlenadoTest(unittest.TestCase):
    """De punta a punta con el Excel real y el caso 13 de ejemplo como respuesta de todos los casos."""

    @classmethod
    def setUpClass(cls):
        cls.huella = hashlib.sha256(v.EXCEL.read_bytes()).hexdigest()
        cls.ejemplo = respuesta()
        cls.pedidos = []

        def falso(numero):
            cls.pedidos.append(numero)
            return cls.ejemplo

        cls.resultados = v.evaluar(openpyxl.load_workbook(v.EXCEL, data_only=True), falso)
        cls.dir = tempfile.TemporaryDirectory()
        cls.destino = v.ruta_de_salida(v.EXCEL, Path(cls.dir.name), 'EXCEL')
        v.guardar(v.EXCEL, cls.destino, cls.resultados)
        cls.salida = openpyxl.load_workbook(cls.destino)

    @classmethod
    def tearDownClass(cls):
        cls.salida.close()
        cls.dir.cleanup()

    def por_numero(self, n):
        return next(r for r in self.resultados if r.numero == n)

    def test_lee_las_45_pruebas_y_pide_cada_caso_una_vez(self):
        self.assertEqual([r.numero for r in self.resultados], list(range(1, 46)))
        self.assertEqual(sorted(self.pedidos), list(range(1, 13)))

    def test_valores_del_motor_y_del_excel(self):
        r = self.por_numero(1)  # monto desembolsado: periodo 0 de Caso1_Normal!N3
        self.assertEqual((r.motor, r.formula_esperado, r.estado), (Decimal('5904400'), '=Caso1_Normal!N3', 'OK'))
        r = self.por_numero(19)  # abono del caso 13 (1,12 M) contra el del caso 6 (2 M)
        self.assertEqual(r.formula_esperado, '=Caso6_MenorPlazo!H46')
        self.assertEqual(r.motor, Decimal(0))
        self.assertEqual(r.estado, 'FALLA')
        self.assertEqual(self.por_numero(33).motor, 'CANCELADO')

    def test_periodo_que_el_motor_no_trae_queda_sin_mapeo(self):
        r = self.por_numero(4)  # periodo 104: el caso 13 termina en el 84
        self.assertEqual(r.estado, 'SIN MAPEO')
        self.assertIn('periodo 104', r.nota)
        self.assertTrue(r.nota.startswith('sin mapeo:'))

    def test_archivo_nuevo_con_columnas_y_formulas(self):
        self.assertEqual(hashlib.sha256(v.EXCEL.read_bytes()).hexdigest(), self.huella)
        h = self.salida[v.HOJA]
        self.assertEqual(h['F2'].value, 'Resultado software')
        self.assertEqual([h.cell(2, c).value for c in range(11, 15)], list(v.ENCABEZADOS_NUEVOS.values()))
        self.assertEqual(h['F3'].value, 5904400)
        self.assertEqual(h['K3'].value, '=Caso1_Normal!N3')
        self.assertEqual(h['M3'].value, '=IF(L3="","",IF(L3<=G3,"OK","FALLA"))')
        self.assertTrue(h['G3'].value.startswith('=IF(E3="Dinero"'))  # fórmula original intacta
        self.assertIsNone(h['F6'].value)  # prueba 4 sin mapeo
        self.assertEqual(h['M6'].value, 'SIN MAPEO')
        self.assertTrue(h['J6'].value.startswith('sin mapeo:'))
        self.assertTrue(str(self.salida['Inputs']['B3'].value).startswith('='))
        self.assertTrue(self.salida.calculation.fullCalcOnLoad)


if __name__ == '__main__':
    unittest.main()
