"""Pruebas del filtrado de ajustes del importador. Uso: python -m unittest herramientas/test_importar_respuestas.py"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import importar_respuestas as r


def fila(id_, codigo, ajustes=''):
    return {'ID': id_, 'Código': codigo, 'Ajustes al motor': ajustes}


class DescartadasTest(unittest.TestCase):
    def test_ids_descartados_no_se_aplican(self):
        self.assertIsNotNone(r.descartada(fila('A3', 'sin-minimo', 'abonoMinimo=0')))
        self.assertIsNotNone(r.descartada(fila('R2', 'si', 'costoRecuperacion=0')))
        self.assertIsNotNone(r.descartada(fila('T5', 'saldo-total')))
        self.assertIsNotNone(r.descartada(fila('C5', 'valor-total')))

    def test_opcion_pendiente_o_vacia_no_se_aplica(self):
        self.assertIsNotNone(r.descartada(fila('Z1', 'PENDIENTE', 'x=1')))
        self.assertIsNotNone(r.descartada(fila('Z1', 'pendiente', 'x=1')))
        self.assertIsNotNone(r.descartada(fila('Z1', '', 'x=1')))

    def test_fila_valida_se_aplica(self):
        self.assertIsNone(r.descartada(fila('T2', 'puntos-ea', 'margenUsura=1')))
        self.assertEqual(r.ajustes(fila('T2', 'puntos-ea', 'margenUsura=1')), {'margenUsura': '1'})


if __name__ == '__main__':
    unittest.main()
