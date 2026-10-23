"""Las claves de reglas que conoce el importador deben ser las mismas que los componentes de ReglasNegocio.

Si se agrega una regla en Java y no en CLAVES_MOTOR (o al revés), las respuestas de Diego se perderían o se
escribirían en el archivo de configuración con una clave que el motor no lee.
"""
import re
import sys
import unittest
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))

import importar_respuestas  # noqa: E402

REGLAS_JAVA = RAIZ / "src/main/java/co/financiera/cartera/nucleo/motor/ReglasNegocio.java"


def componentes_del_record() -> list[str]:
    fuente = REGLAS_JAVA.read_text(encoding="utf8")
    cabecera = re.search(r"public record ReglasNegocio\((.*?)\)\s*\{", fuente, re.S)
    if cabecera is None:
        raise AssertionError("No se encontró la cabecera del record ReglasNegocio")
    sin_comentarios = re.sub(r"/\*.*?\*/|//[^\n]*", "", cabecera.group(1), flags=re.S)
    partes = [p.strip() for p in re.split(r",(?![^<]*>)", sin_comentarios) if p.strip()]
    return [re.findall(r"(\w+)\s*$", p)[0] for p in partes]


class ClavesDelMotor(unittest.TestCase):
    def test_el_importador_conoce_exactamente_las_claves_de_reglas_negocio(self):
        java = set(componentes_del_record())
        python = set(importar_respuestas.CLAVES_MOTOR)
        self.assertEqual(set(), java - python, "Claves de Java que el importador no conoce")
        self.assertEqual(set(), python - java, "Claves del importador que ReglasNegocio no tiene")

    def test_el_record_tiene_claves(self):
        self.assertGreater(len(componentes_del_record()), 10)


if __name__ == "__main__":
    unittest.main()
