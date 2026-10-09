"""Genera los CSV para importar el backlog en Azure Boards (Work items → Import from CSV).

Azure DevOps arma la jerarquía con las columnas "Title 1/2/3": la fila de la épica lleva
Title 1, la de la feature Title 2 y la de la historia Title 3, en ese orden.
Se genera un archivo por plantilla de proceso porque el tipo de historia cambia de nombre.
"""
import csv, html, sys
from pathlib import Path

from backlog import EPICAS

sys.stdout.reconfigure(encoding='utf-8')
ETIQUETA = 'backlog-motor-reglas-v1'
PLANTILLAS = {
    'Agile': 'User Story',
    'Scrum': 'Product Backlog Item',
    'CMMI': 'Requirement',
}


def gherkin_html(texto):
    return '<pre>' + html.escape(texto) + '</pre>'


def descripcion(h):
    if h.get('tecnica'):
        partes = [f"<p><b>Historia técnica.</b> <b>Para</b> {html.escape(h['para'])}, <b>se necesita</b> {html.escape(h['necesita'])}.</p>"]
    else:
        partes = [f"<p><b>Como</b> {html.escape(h['como'])}<br><b>quiero</b> {html.escape(h['quiero'])}<br><b>para</b> {html.escape(h['para'])}</p>"]
    if h.get('notas'):
        partes.append(f"<p><b>Notas para refinamiento:</b> {html.escape(h['notas'])}</p>")
    partes.append('<p><i>Fuente de reglas: Excel "Casos Operaciones V2 mejorado.xlsx" (Diego González).</i></p>')
    return ''.join(partes)


def filas(tipo_historia):
    for e in EPICAS:
        yield ['Epic', e['titulo'], '', '', f"<p>{html.escape(e['descripcion'])}</p>", '', ETIQUETA]
        for f in e['features']:
            yield ['Feature', '', f['titulo'], '', f"<p>{html.escape(f['descripcion'])}</p>", '', ETIQUETA]
            for h in f['historias']:
                titulo = ('[HT] ' if h.get('tecnica') else '') + h['titulo']
                etiquetas = ETIQUETA + ('; historia-tecnica' if h.get('tecnica') else '')
                yield [tipo_historia, '', '', titulo, descripcion(h), gherkin_html(h['gherkin']), etiquetas]


def main():
    salida = Path(__file__).parent
    for plantilla, tipo in PLANTILLAS.items():
        ruta = salida / f'backlog_azure_{plantilla.lower()}.csv'
        # utf-8-sig: con BOM para que tildes y ñ entren bien al importar.
        with ruta.open('w', encoding='utf-8-sig', newline='') as f:
            w = csv.writer(f, quoting=csv.QUOTE_ALL)
            w.writerow(['ID', 'Work Item Type', 'Title 1', 'Title 2', 'Title 3', 'Description', 'Acceptance Criteria', 'Tags'])
            for fila in filas(tipo):
                w.writerow([''] + fila)
        print(f'{ruta.name}: {sum(1 for _ in filas(tipo))} elementos')


if __name__ == '__main__':
    main()
