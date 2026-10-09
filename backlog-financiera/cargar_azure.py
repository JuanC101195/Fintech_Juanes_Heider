"""Carga el backlog de backlog.py en Azure DevOps (épicas → features → historias).

Uso:
    python cargar_azure.py --org motosdelcaribe --proyecto Financiera            # simulación
    python cargar_azure.py --org motosdelcaribe --proyecto Financiera --crear    # crea

Autenticación: variable AZURE_DEVOPS_PAT (token personal con permiso Work Items: Read & Write)
o, si no existe, el token de la sesión de Azure CLI (`az login` con una cuenta del org).
Es idempotente: si ya existe un elemento del mismo tipo y título con la etiqueta del lote,
no lo vuelve a crear.
"""
import argparse, base64, html, json, os, subprocess, sys, urllib.error, urllib.parse, urllib.request

from backlog import EPICAS

sys.stdout.reconfigure(encoding='utf-8')
ETIQUETA = 'backlog-motor-reglas-v1'
RECURSO_ADO = '499b84ac-1321-427f-aa17-267ca6975798'


def cabecera_auth():
    pat = os.environ.get('AZURE_DEVOPS_PAT')
    if pat:
        return 'Basic ' + base64.b64encode(f':{pat}'.encode()).decode()
    token = subprocess.run(
        ['az', 'account', 'get-access-token', '--resource', RECURSO_ADO, '--query', 'accessToken', '-o', 'tsv'],
        capture_output=True, text=True, shell=os.name == 'nt',
    ).stdout.strip()
    if not token:
        sys.exit('No hay credenciales: define AZURE_DEVOPS_PAT o ejecuta `az login`.')
    return 'Bearer ' + token


class Azure:
    def __init__(self, org, proyecto):
        self.base = f'https://dev.azure.com/{org}/{urllib.parse.quote(proyecto)}/_apis'
        self.auth = cabecera_auth()

    def pedir(self, metodo, ruta, cuerpo=None, tipo='application/json'):
        url = f'{self.base}/{ruta}{"&" if "?" in ruta else "?"}api-version=7.1'
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
        req = urllib.request.Request(url, data=datos, method=metodo, headers={'Authorization': self.auth, 'Content-Type': tipo})
        try:
            with urllib.request.urlopen(req) as r:
                return json.loads(r.read() or b'{}')
        except urllib.error.HTTPError as e:
            sys.exit(f'Error {e.code} en {metodo} {ruta}: {e.read().decode()[:500]}')
        except urllib.error.URLError as e:
            sys.exit(f'No se pudo conectar: {e}')

    def tipos(self):
        return {t['name'] for t in self.pedir('GET', 'wit/workitemtypes')['value']}

    def existentes(self):
        wiql = {'query': f"SELECT [System.Id], [System.Title], [System.WorkItemType] FROM WorkItems "
                         f"WHERE [System.TeamProject] = @project AND [System.Tags] CONTAINS '{ETIQUETA}'"}
        ids = [w['id'] for w in self.pedir('POST', 'wit/wiql', wiql)['workItems']]
        vistos = {}
        for i in range(0, len(ids), 200):
            lote = ','.join(map(str, ids[i:i + 200]))
            for w in self.pedir('GET', f'wit/workitems?ids={lote}&fields=System.Title,System.WorkItemType')['value']:
                vistos[(w['fields']['System.WorkItemType'], w['fields']['System.Title'])] = w['id']
        return vistos

    def crear(self, tipo, campos, padre=None):
        ops = [{'op': 'add', 'path': f'/fields/{k}', 'value': v} for k, v in campos.items()]
        if padre:
            ops.append({'op': 'add', 'path': '/relations/-', 'value': {
                'rel': 'System.LinkTypes.Hierarchy-Reverse', 'url': f'{self.base}/wit/workItems/{padre}'}})
        return self.pedir('POST', f'wit/workitems/${urllib.parse.quote(tipo)}', ops, 'application/json-patch+json')['id']


def gherkin_html(texto):
    return '<pre>' + html.escape(texto) + '</pre>'


def descripcion_historia(h):
    if h.get('tecnica'):
        partes = [f"<p><b>Historia técnica.</b> <b>Para</b> {html.escape(h['para'])}, <b>se necesita</b> {html.escape(h['necesita'])}.</p>"]
    else:
        partes = [f"<p><b>Como</b> {html.escape(h['como'])}<br><b>quiero</b> {html.escape(h['quiero'])}<br><b>para</b> {html.escape(h['para'])}</p>"]
    if h.get('notas'):
        partes.append(f"<p><b>Notas para refinamiento:</b> {html.escape(h['notas'])}</p>")
    partes.append('<p><i>Fuente de reglas: Excel "Casos Operaciones V2 mejorado.xlsx" (Diego González).</i></p>')
    return ''.join(partes)


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--org', required=True)
    p.add_argument('--proyecto', required=True)
    p.add_argument('--crear', action='store_true', help='sin esto solo simula')
    a = p.parse_args()

    n_f = sum(len(e['features']) for e in EPICAS)
    n_h = sum(len(f['historias']) for e in EPICAS for f in e['features'])
    print(f'Backlog: {len(EPICAS)} épicas, {n_f} features, {n_h} historias')

    az = Azure(a.org, a.proyecto)
    tipos = az.tipos()
    tipo_historia = next((t for t in ['User Story', 'Product Backlog Item', 'Requirement', 'Issue'] if t in tipos), None)
    if 'Epic' not in tipos or tipo_historia is None:
        sys.exit(f'El proceso del proyecto no tiene los tipos esperados: {sorted(tipos)}')
    usa_feature = 'Feature' in tipos
    campo_criterios = 'Microsoft.VSTS.Common.AcceptanceCriteria' if tipo_historia != 'Issue' else None
    print(f'Tipos: Epic → {"Feature" if usa_feature else "(sin Feature)"} → {tipo_historia}')

    vistos = az.existentes()
    creados = omitidos = 0

    def asegurar(tipo, campos, padre=None):
        nonlocal creados, omitidos
        clave = (tipo, campos['System.Title'])
        if clave in vistos:
            omitidos += 1
            return vistos[clave]
        if not a.crear:
            creados += 1
            return None
        campos['System.Tags'] = ETIQUETA + ('; historia-tecnica' if campos['System.Title'].startswith('[HT] ') else '')
        nuevo = az.crear(tipo, campos, padre)
        vistos[clave] = nuevo
        creados += 1
        print(f'  + {tipo} #{nuevo}: {campos["System.Title"]}')
        return nuevo

    for e in EPICAS:
        id_e = asegurar('Epic', {'System.Title': e['titulo'], 'System.Description': f"<p>{html.escape(e['descripcion'])}</p>"})
        for f in e['features']:
            padre_h = id_e
            if usa_feature:
                padre_h = asegurar('Feature', {'System.Title': f['titulo'], 'System.Description': f"<p>{html.escape(f['descripcion'])}</p>"}, id_e)
            for h in f['historias']:
                campos = {'System.Title': ('[HT] ' if h.get('tecnica') else '') + h['titulo'], 'System.Description': descripcion_historia(h)}
                if campo_criterios:
                    campos[campo_criterios] = gherkin_html(h['gherkin'])
                else:
                    campos['System.Description'] += '<p><b>Criterios de aceptación</b></p>' + gherkin_html(h['gherkin'])
                asegurar(tipo_historia, campos, padre_h)

    verbo = 'Creados' if a.crear else 'Se crearían'
    print(f'{verbo}: {creados} · ya existían: {omitidos}')


if __name__ == '__main__':
    main()
