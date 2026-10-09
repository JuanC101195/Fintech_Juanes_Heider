"""Importa el Excel de respuestas del cuestionario de reglas al motor.

Uso (desde la raíz del repo):
    python herramientas/importar_respuestas.py ruta/al/excel-de-respuestas.xlsx

Lee la hoja "Respuestas" (la genera el cuestionario en cuestionario/) y escribe:
  - config/reglas-negocio.properties: los ajustes que el motor ya sabe aplicar
    (ReglasNegocio.CLAVES). ReglasNegocioConfiguradaTest corre los 9 casos con ellos.
  - docs/respuestas-negocio.md: todas las respuestas, con lo que queda por desarrollar.
Después: ./mvnw -q -B test y revisar las pruebas que cambien frente al Excel.
"""
import sys
from datetime import datetime
from pathlib import Path

import openpyxl

RAIZ = Path(__file__).resolve().parent.parent
# Debe coincidir con ReglasNegocio.CLAVES.
CLAVES_MOTOR = {'prelacion', 'formulaMora', 'baseInteres', 'topeCobranzaPorPago', 'baseServicios', 'serviciosDespuesDelPlazo'}
ENCABEZADOS = ['ID', 'Bloque', 'Pregunta', 'Código', 'Respuesta', 'Valor', 'Unidad', 'Detalle', 'Comentario',
               'Ajustes al motor', 'Completa']


def leer(ruta):
    libro = openpyxl.load_workbook(ruta, data_only=True)
    if 'Respuestas' not in libro.sheetnames:
        sys.exit('El archivo no tiene la hoja "Respuestas": ¿es el Excel que genera el cuestionario?')
    hoja = libro['Respuestas']
    encabezados = [c.value for c in hoja[1]]
    if encabezados[:len(ENCABEZADOS)] != ENCABEZADOS:
        sys.exit(f'Encabezados inesperados en "Respuestas": {encabezados}')
    filas = []
    for fila in hoja.iter_rows(min_row=2, values_only=True):
        if fila[0]:
            filas.append(dict(zip(ENCABEZADOS, ['' if v is None else v for v in fila])))
    resumen = {}
    if 'Resumen' in libro.sheetnames:
        for a, b in libro['Resumen'].iter_rows(min_row=1, max_col=2, values_only=True):
            if a:
                resumen[str(a)] = '' if b is None else str(b)
    return filas, resumen


def ajustes(fila):
    resultado = {}
    for parte in str(fila['Ajustes al motor']).split(';'):
        if '=' in parte:
            clave, valor = parte.split('=', 1)
            resultado[clave.strip()] = valor.strip()
    return resultado


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    ruta = Path(sys.argv[1])
    filas, resumen = leer(ruta)

    aplicables, por_desarrollar = {}, {}
    for f in filas:
        for clave, valor in ajustes(f).items():
            (aplicables if clave in CLAVES_MOTOR else por_desarrollar)[clave] = (valor, f['ID'])

    (RAIZ / 'config').mkdir(exist_ok=True)
    props = [f'# Generado desde {ruta.name} el {datetime.now():%Y-%m-%d %H:%M}',
             f'# Respondió: {resumen.get("Respondió", "")} ({resumen.get("Cargo", "")})']
    props += [f'{clave}={valor}' for clave, (valor, _) in sorted(aplicables.items())]
    (RAIZ / 'config' / 'reglas-negocio.properties').write_text('\n'.join(props) + '\n', encoding='utf-8')

    completas = sum(1 for f in filas if f['Completa'] == 'Sí')
    md = [
        '# Respuestas de negocio al cuestionario de reglas',
        '',
        f'Fuente: `{ruta.name}` · Respondió: {resumen.get("Respondió", "")} ({resumen.get("Cargo", "")}) · '
        f'{resumen.get("Fecha", "")} · {completas} de {len(filas)} completas.',
        '',
        '## Aplicado al motor (`config/reglas-negocio.properties`)',
        '',
    ]
    md += [f'- `{c}={v}` (pregunta {p})' for c, (v, p) in sorted(aplicables.items())] or ['- Ninguno.']
    md += ['', '## Decidido pero pendiente de desarrollo en el motor', '']
    md += [f'- `{c}={v}` (pregunta {p})' for c, (v, p) in sorted(por_desarrollar.items())] or ['- Ninguno.']
    md += ['', '## Todas las respuestas', '', '| ID | Pregunta | Respuesta | Valor | Detalle | Comentario |', '|---|---|---|---|---|---|']
    for f in filas:
        valor = f'{f["Valor"]} {f["Unidad"]}'.strip()
        celdas = [f['ID'], f['Pregunta'], f['Respuesta'] or '*sin responder*', valor, f['Detalle'], f['Comentario']]
        md.append('| ' + ' | '.join(str(c).replace('|', '/').replace('\n', ' ') for c in celdas) + ' |')
    (RAIZ / 'docs' / 'respuestas-negocio.md').write_text('\n'.join(md) + '\n', encoding='utf-8')

    print(f'{completas} de {len(filas)} preguntas completas')
    print(f'Aplicado al motor: {", ".join(sorted(aplicables)) or "nada"}')
    print(f'Pendiente de desarrollo: {", ".join(sorted(por_desarrollar)) or "nada"}')
    print('Escrito config/reglas-negocio.properties y docs/respuestas-negocio.md. Siguiente: ./mvnw -q -B test')


if __name__ == '__main__':
    main()
