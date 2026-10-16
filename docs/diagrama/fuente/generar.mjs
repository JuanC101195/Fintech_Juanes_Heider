// Arma la entrada de archify (agentes.architecture.json) del diagrama de los 9 agentes.
// Uso: node docs/diagrama/fuente/generar.mjs <ruta-al-repo-archify>
// Luego: node <archify>/archify/bin/archify.mjs finalize architecture docs/diagrama/fuente/agentes.architecture.json docs/diagrama/agentes-motor-cartera.html --quality showcase --json
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const archify = process.argv[2];
const es = JSON.parse(readFileSync(path.join(archify, 'archify/examples/locales/es.json'), 'utf8'));

// caso, nombre, segundos, llamadas a herramientas, pruebas, commit, entrega
const agentes = [
  [1, 'Normal', 434, 34, 25, 'a12318c', 'RecaudoCuota + invariantes'],
  [2, 'Cambio de tasa', 280, 24, 17, '4a7a55d', 'Usura · sin asiento'],
  [3, 'Dación en pago', 323, 32, 9, 'ec217cf', 'regla DacionEnPago'],
  [4, 'Retoma', 247, 19, 7, 'a68b27e', 'regla Retoma'],
  [5, 'Prepago total', 266, 25, 13, '30efd7a', 'regla AbonoExtraordinario'],
  [6, 'Menor plazo', 260, 21, 12, '6df09b3', 'usa AbonoExtraordinario'],
  [7, 'Menor cuota', 307, 29, 14, 'af8dbaf', 'usa AbonoExtraordinario'],
  [8, 'Pago inferior', 382, 30, 12, '1962b47', 'regla PagoInferior'],
  [9, 'Cobranza', 301, 23, 13, '8fefa8d', 'regla CobranzaYMora'],
];
const PASO = 72, Y0 = 40, H = 60, CY = Y0 + 4 * PASO + H / 2; // fila central = caso 5
// Los 9 carriles salen de la base (y llegan a la integración) por tres lados, como pide archify para un nodo
// con muchas conexiones. En los lados de arriba y abajo archify ordena los puertos por la x del otro extremo
// (y empata por id): correr 1-2 px los casos 7-9 y las entregas 1-3 invierte ese orden donde hace falta
// para que las líneas queden anidadas y no se crucen.
const ladoBase = (n) => (n <= 3 ? 'top' : n >= 7 ? 'bottom' : 'right');
const ladoIntegracion = (n) => (n <= 3 ? 'top' : n >= 7 ? 'bottom' : 'left');
const ajusteAgente = (n) => (n >= 7 ? 9 - n : 0);
const ajusteEntrega = (n) => (n <= 3 ? 3 - n : 0);

const components = [
  { id: 'juan', type: 'external', label: 'Juan Esteban', sublabel: 'pide el motor', pos: [20, CY - 30], size: [130, 60] },
  { id: 'orquestador', type: 'frontend', label: 'Orquestador', sublabel: 'arma y coordina', tag: 'sesión principal', pos: [230, CY - 34], size: [150, 68] },
  { id: 'base', type: 'frontend', label: 'Base común', sublabel: 'núcleo + caso 1', tag: '8e24c4a', pos: [440, CY - 65], size: [170, 130] },
  { id: 'excel', type: 'database', label: 'Excel de Diego', sublabel: '9 hojas → 9 CSV', pos: [230, CY + 150], size: [150, 60] },
  { id: 'integracion', type: 'frontend', label: 'Integración', sublabel: '9 merges · 124 pruebas', tag: 'e815f82', pos: [1240, CY - 65], size: [170, 130] },
  { id: 'cuestionario', type: 'frontend', label: 'Cuestionario', sublabel: 'ReglasNegocio · 139 pruebas', tag: '73756ea', pos: [1470, CY - 34], size: [170, 68] },
  { id: 'diego', type: 'external', label: 'Diego', sublabel: 'respondió P-01 a P-27', pos: [1470, CY + 130], size: [170, 60] },
  { id: 'respuestas', type: 'frontend', label: 'Reglas de Diego', sublabel: 'inicial · abonos · mora · 147 pruebas', tag: '16 oct', pos: [1440, CY + 250], size: [230, 68] },
  { id: 'github', type: 'external', label: 'GitHub', sublabel: 'Fintech_Juanes_Heider', pos: [1470, CY - 190], size: [170, 60] },
];
const connections = [
  { id: 'solicita', from: 'juan', to: 'orquestador', label: 'solicita', variant: 'emphasis' },
  { id: 'arma-base', from: 'orquestador', to: 'base', label: 'arma', variant: 'emphasis' },
  { id: 'exporta-csv', from: 'excel', to: 'orquestador', label: 'exporta a CSV', variant: 'dashed', fromSide: 'top', toSide: 'bottom' },
  { id: 'dudas', from: 'integracion', to: 'cuestionario', label: 'dudas', variant: 'emphasis' },
  { id: 'envia', from: 'cuestionario', to: 'diego', label: 'envía', variant: 'dashed', fromSide: 'bottom', toSide: 'top' },
  { id: 'respaldo', from: 'cuestionario', to: 'github', label: 'push', variant: 'dashed', fromSide: 'top', toSide: 'bottom' },
  { id: 'aplica', from: 'diego', to: 'respuestas', label: 'aplica', variant: 'emphasis', fromSide: 'bottom', toSide: 'top' },
];
for (const [n, nombre, seg, her, pruebas, commit, entrega] of agentes) {
  const y = Y0 + (n - 1) * PASO;
  components.push({ id: `agente${n}`, type: 'backend', label: `Caso ${n} · ${nombre}`, sublabel: `agente IA · ${seg} s · ${her} herram.`, pos: [690 + ajusteAgente(n), y], size: [220, H] });
  components.push({ id: `entrega${n}`, type: 'database', label: `${pruebas} pruebas · ${commit}`, sublabel: entrega, pos: [960 + ajusteEntrega(n), y], size: [200, H] });
  connections.push({ id: `entrega${n}-hecha`, from: `agente${n}`, to: `entrega${n}` });
}
for (const n of [1, 2, 3, 4, 5, 6, 7, 8, 9]) {
  connections.push({ id: `lanza${n}`, from: 'base', to: `agente${n}`, fromSide: ladoBase(n), toSide: 'left', ...(n === 1 ? { label: 'lanza' } : {}) });
}
for (const n of [1, 2, 3, 4, 5, 6, 7, 8, 9]) {
  connections.push({ id: `merge${n}`, from: `entrega${n}`, to: 'integracion', fromSide: 'right', toSide: ladoIntegracion(n), ...(n === 1 ? { label: 'merge' } : {}) });
}

const doc = {
  schema_version: 1,
  diagram_type: 'architecture',
  meta: {
    title: 'Motor de cartera · 9 agentes',
    subtitle: 'Orquestador + 9 agentes de IA en git worktrees · 8 al 16 oct 2026',
    locale: 'es',
    translations: es,
    output: 'docs/diagrama/agentes-motor-cartera.html',
    quality_profile: 'showcase',
    legend: {
      mode: 'auto',
      entries: {
        frontend: { label: 'Orquestador (sesión principal)' },
        backend: { label: 'Agente de IA' },
        database: { label: 'Datos y entregas' },
        external: { label: 'Personas y externos' },
      },
    },
  },
  components,
  boundaries: [{ kind: 'region', label: '9 git worktrees en paralelo · 7 min 14 s', wraps: agentes.flatMap(([n]) => [`agente${n}`, `entrega${n}`]) }],
  connections,
  cards: [
    { dot: 'cyan', title: '9 casos, 9 carriles separados', items: ['Un agente por hoja del Excel, cada uno en su git worktree', 'Solo tocó su prueba, su regla contable y su documento', '1.032.778 tokens y 237 llamadas a herramientas en total'] },
    { dot: 'emerald', title: 'Un solo motor compartido', items: ['La base común se hizo antes de lanzarlos (8e24c4a)', '8 de 9 agentes no tuvieron que cambiar MotorCartera', 'Los 9 casos se reproducen celda por celda (1 COP)'] },
    { dot: 'violet', title: 'La integración es el punto de encuentro', items: ['9 merges, 4 choques en Contabilizador unidos a mano', '124 pruebas en verde; el cuestionario lleva la suite a 139', 'Las respuestas de Diego la llevan a 147'] },
    { dot: 'amber', title: 'Hallazgos de los agentes', items: [
      'Caso 3: faltante para el fondo de garantías de 1.709.819,75',
      'Caso 4: excedente para el cliente de 1.185.578,46',
      'Caso 8: el capital vencido paga interés y mora a la vez',
      'Caso 9: el crédito nunca termina con 14 días de atraso',
    ] },
    { dot: 'rose', title: 'Respuestas de Diego (16 oct)', items: [
      'Pago inicial opcional que digita el asesor',
      'Abono sin elección → menor plazo; mayor que el saldo → rechazo',
      'Mora antes de dación y retoma; vencido paga solo mora',
      'Falta su último Excel: tasas por línea y servicios en mora',
    ] },
    { dot: 'slate', title: 'Cómo se lanzaron', items: ['Subagentes de propósito general del asistente de programación', 'isolation: worktree, en segundo plano y en paralelo', 'Prompts en docs/agentes/prompts/ · commits sin atribución'] },
  ],
};
writeFileSync(new URL('./agentes.architecture.json', import.meta.url), JSON.stringify(doc, null, 2) + '\n');
console.log('ok', components.length, 'nodos', connections.length, 'conexiones');
