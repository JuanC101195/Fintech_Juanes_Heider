// Genera el diagrama del proyecto con archify: dos vistas (arquitectura y fases de construcción) en un solo HTML con pestañas.
// Uso (desde la raíz del repo): node docs/diagrama/fuente/generar.mjs <ruta-al-repo-archify>
//   1. Escribe las entradas de archify: fuente/arquitectura.architecture.json y fuente/fases.architecture.json.
//   2. Corre `archify finalize architecture ... --quality showcase` para cada una (valida y renderiza).
//   3. Arma docs/diagrama/agentes-motor-cartera.html: pestañas + cada vista en un iframe (srcdoc), sin archivos aparte.
// El workflow .github/workflows/sitio.yml publica ese HTML como diagrama.html.
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const archify = process.argv[2];
if (!archify) throw new Error('Falta la ruta al repo de archify: node docs/diagrama/fuente/generar.mjs <archify>');
const es = JSON.parse(readFileSync(path.join(archify, 'archify/examples/locales/es.json'), 'utf8'));
const fuente = new URL('.', import.meta.url);
const salida = new URL('../agentes-motor-cartera.html', import.meta.url);

// ───────────────────────── Vista 1 · Arquitectura actual ─────────────────────────
const arquitectura = {
  schema_version: 1,
  diagram_type: 'architecture',
  meta: {
    title: 'Motor de cartera · arquitectura',
    subtitle: 'Monolito modular Spring Modulith (Java 21) con API REST, consola y cuestionario de reglas · estado al 23 oct 2026',
    locale: 'es',
    translations: es,
    output: 'arquitectura.html',
    quality_profile: 'showcase',
    legend: {
      mode: 'auto',
      entries: {
        frontend: { label: 'Páginas web (React)' },
        backend: { label: 'Módulos y herramientas' },
        database: { label: 'Datos y configuración' },
        cloud: { label: 'Función en la nube' },
        external: { label: 'Personas' },
      },
    },
  },
  components: [
    { id: 'diego', type: 'external', label: 'Negocio', sublabel: 'responde las reglas', pos: [20, 60], size: [150, 60] },
    { id: 'equipo', type: 'external', label: 'Equipo de cartera', sublabel: 'simula y revisa', pos: [20, 254], size: [150, 60] },
    { id: 'cuestionario', type: 'frontend', label: 'Cuestionario', sublabel: 'cuestionario/ · React', tag: 'ronda 7', pos: [240, 56], size: [200, 68] },
    { id: 'consola', type: 'frontend', label: 'Consola', sublabel: 'consola/ · React', tag: 'usa el API', pos: [240, 250], size: [200, 68] },
    { id: 'diagrama', type: 'frontend', label: 'Diagrama del proyecto', sublabel: 'docs/diagrama/ · archify', pos: [240, 420], size: [200, 68] },
    { id: 'funcion', type: 'cloud', label: 'Azure Function', sublabel: 'nube/ · guarda respuestas', pos: [540, 56], size: [200, 68] },
    { id: 'tabla', type: 'database', label: 'Table Storage', sublabel: 'tabla respuestas', pos: [800, 56], size: [200, 68] },
    { id: 'importador', type: 'backend', label: 'Importador', sublabel: 'importar_respuestas.py', tag: 'herramientas/', pos: [1080, 56], size: [200, 68] },
    // Región Azure App Service: cuatro columnas (api, servicios, núcleo, piezas del núcleo)
    { id: 'filtros', type: 'backend', label: 'Filtros de entrada', sublabel: 'cabeceras · tamaño · API-Key', tag: 'clave opcional', pos: [540, 250], size: [220, 68] },
    { id: 'api', type: 'backend', label: 'api · fachada', sublabel: 'CarteraController · ServicioCartera', tag: 'REST /api/v1', pos: [540, 400], size: [220, 68] },
    { id: 'apoyo', type: 'backend', label: 'Apoyo del API', sublabel: 'validadores · mapeo · errores', tag: 'PerfilesReglas', pos: [540, 550], size: [220, 68] },
    { id: 'simulaciones', type: 'backend', label: 'simulaciones', sublabel: 'EscenariosExcel · 13 casos', pos: [820, 310], size: [220, 68] },
    { id: 'contabilidad', type: 'backend', label: 'contabilidad', sublabel: 'Contabilizador · reglas', pos: [820, 490], size: [220, 68] },
    { id: 'reglas', type: 'database', label: 'reglas-negocio.properties', sublabel: 'config/ · va en el jar', pos: [1100, 230], size: [220, 68] },
    { id: 'motor', type: 'backend', label: 'nucleo · MotorCartera', sublabel: 'ciclo por periodo · delega', tag: 'cálculo puro', pos: [1100, 400], size: [220, 68] },
    { id: 'versiones', type: 'backend', label: 'Versiones y bitácora', sublabel: 'VersionReglas · OrigenCredito', tag: 'en memoria', pos: [1100, 560], size: [220, 68] },
    { id: 'reglasnegocio', type: 'backend', label: 'ReglasNegocio', sublabel: 'Constructor · desde / desdeEstricto', pos: [1380, 230], size: [220, 68] },
    { id: 'extraidas', type: 'backend', label: 'Piezas del motor', sublabel: 'Causador · Abonos · Recuperacion', tag: 'extraídas', pos: [1380, 340], size: [220, 68] },
    { id: 'reanudable', type: 'backend', label: 'Estado reanudable', sublabel: 'Instantanea · RecalculoUsura', tag: 'streaming', pos: [1380, 450], size: [220, 68] },
    { id: 'calendario', type: 'backend', label: 'Periodicidad', sublabel: 'CalendarioPagos · prorrateo', tag: 'sem · quin · mens', pos: [1380, 560], size: [220, 68] },
    { id: 'persistencia', type: 'database', label: 'PostgreSQL · historial', sublabel: 'ADR 0004 · solo diseñado', tag: 'no construido', pos: [1100, 740], size: [220, 68] },
  ],
  boundaries: [
    { kind: 'region', label: 'GitHub Pages', wraps: ['cuestionario', 'diagrama', 'consola'] },
    { kind: 'region', label: 'Azure · respuestas sin servidor', wraps: ['funcion', 'tabla'] },
    { kind: 'region', label: 'Azure App Service F1 · Java 21 · un jar', wraps: ['filtros', 'api', 'apoyo', 'simulaciones', 'contabilidad', 'reglas', 'motor', 'versiones', 'reglasnegocio', 'extraidas', 'reanudable', 'calendario'] },
  ],
  connections: [
    { id: 'responde', from: 'diego', to: 'cuestionario', label: 'responde', variant: 'emphasis' },
    { id: 'autoguarda', from: 'cuestionario', to: 'funcion', label: 'autoguarda', variant: 'emphasis' },
    { id: 'escribe-tabla', from: 'funcion', to: 'tabla', label: 'escribe' },
    { id: 'lee-nube', from: 'tabla', to: 'importador', label: '--nube', variant: 'dashed' },
    { id: 'escribe-reglas', from: 'importador', to: 'reglas', label: 'escribe', variant: 'emphasis', fromSide: 'bottom', toSide: 'top' },
    { id: 'usa-consola', from: 'equipo', to: 'consola', label: 'usa', variant: 'emphasis' },
    { id: 'consulta', from: 'equipo', to: 'diagrama', label: 'consulta', variant: 'dashed', fromSide: 'bottom', toSide: 'left' },
    { id: 'https', from: 'consola', to: 'filtros', label: 'HTTPS', variant: 'emphasis' },
    { id: 'valida', from: 'filtros', to: 'api', label: 'pasa', fromSide: 'bottom', toSide: 'top' },
    { id: 'usa-apoyo', from: 'api', to: 'apoyo', label: 'valida y mapea', fromSide: 'bottom', toSide: 'top' },
    { id: 'casos', from: 'api', to: 'simulaciones', label: 'casos', fromSide: 'right', toSide: 'left' },
    { id: 'asientos', from: 'api', to: 'contabilidad', label: 'asientos', fromSide: 'right', toSide: 'left' },
    { id: 'escenarios', from: 'simulaciones', to: 'motor', label: 'escenarios', fromSide: 'right', toSide: 'left' },
    { id: 'movimientos', from: 'contabilidad', to: 'motor', label: 'movimientos', fromSide: 'right', toSide: 'left' },
    { id: 'carga-reglas', from: 'reglas', to: 'reglasnegocio', label: 'desde()', variant: 'dashed' },
    { id: 'reglas-motor', from: 'reglasnegocio', to: 'motor', label: 'reglas', fromSide: 'left', toSide: 'top' },
    { id: 'delega', from: 'motor', to: 'extraidas', label: 'delega', fromSide: 'right', toSide: 'left' },
    { id: 'reanuda', from: 'motor', to: 'reanudable', label: 'reanuda', fromSide: 'right', toSide: 'left' },
    { id: 'calcula-fechas', from: 'motor', to: 'calendario', label: 'fechas', fromSide: 'right', toSide: 'left' },
    { id: 'version-motor', from: 'versiones', to: 'motor', label: 'versión', variant: 'dashed', fromSide: 'top', toSide: 'bottom' },
    { id: 'guardaria', from: 'versiones', to: 'persistencia', label: 'guardaría', variant: 'dashed', fromSide: 'bottom', toSide: 'top' },
  ],
  cards: [
    { dot: 'cyan', title: 'nucleo · cálculo puro', items: [
      'MotorCartera: un ciclo por periodo para todos los casos; delega en Causador, Abonos, RepartoPrelacion, Recuperacion, DescriptorEventos y EstadoCorrida',
      'Instantanea y FormatoInstantanea: ejecutarHasta / ejecutarDesde y ejecutar en streaming',
      'RecalculoUsura: recálculo masivo reanudando desde la cuota siguiente',
      'Periodicidad semanal, quincenal o mensual con CalendarioPagos y prorrateo de la primera cuota',
      'Sin Spring ni base de datos; BigDecimal sin redondeo',
    ] },
    { dot: 'violet', title: 'Reglas, versiones y bitácora', items: [
      'ReglasNegocio: Constructor por nombre y lectura guiada por tabla (desde / desdeEstricto)',
      'VersionReglas inmutable, RepositorioReglas (con versión en memoria) y OrigenCredito',
      'BitacoraParametros, EventoBitacora, Responsable y Rol, Exoneracion y VigenciasCobranza',
      'Persistencia en PostgreSQL con historial inmutable: diseñada en el ADR 0004 (línea punteada), todavía no construida',
    ] },
    { dot: 'emerald', title: 'api, contabilidad y simulaciones', items: [
      'CarteraController y ServicioCartera, con MapeadorDto, FechasCredito, ValidadorCredito, CatalogoServicio y ValidadorInstrucciones',
      'ManejadorErrores y PerfilesReglas (perfiles EXCEL y NEGOCIO)',
      'Filtros de cabeceras de seguridad, tamaño de cuerpo y X-API-Key, registrados por ConfiguracionFiltros; la clave está apagada por defecto',
      'contabilidad: Contabilizador y una regla por evento; simulaciones: los 13 casos',
    ] },
    { dot: 'orange', title: 'Pruebas y calidad', items: [
      '602 pruebas de Java; cobertura de ramas del 91 %',
      'Huellas de regresión (caracterizacion/) y prueba de propiedades con 3.000 escenarios',
      'ArquitecturaTest (ArchUnit), ModularidadTest y ArranqueRealTest',
      'CI con jobs separados: motor, cuestionario, consola, herramientas y humo/e2e; análisis de SonarCloud',
    ] },
    { dot: 'amber', title: 'Despliegue', items: [
      'API: Azure App Service plan F1 gratuito, Java 21, sin estado',
      'Consola, cuestionario y diagrama: GitHub Pages (workflow Sitio)',
      'Respuestas del cuestionario: Azure Function y Table Storage; el importador las lleva a config/',
      'Si el API duerme o agota su cuota, la consola usa datos de demostración',
    ] },
    { dot: 'rose', title: 'Estado y pendiente', items: [
      'Rondas 5 y 6 de negocio aplicadas; la ronda 7 deja 2 preguntas abiertas',
      'Sin construir: persistencia, abonos con idempotencia, cierre diario y CRM (ADR 0004 y pendientes técnicos)',
      'El archivo de reglas va dentro del jar: un cambio de reglas exige redesplegar',
    ] },
  ],
};

// ───────────────────────── Vista 2 · Fases de construcción ─────────────────────────
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
const lado = (n, medio) => {
  if (n <= 3) return 'top';
  if (n >= 7) return 'bottom';
  return medio;
};
const ladoBase = (n) => lado(n, 'right');
const ladoIntegracion = (n) => lado(n, 'left');
const ajusteAgente = (n) => (n >= 7 ? 9 - n : 0);
const ajusteEntrega = (n) => (n <= 3 ? 3 - n : 0);
// Fila de abajo (fases 6 a 10): se lee de derecha a izquierda, siguiendo la columna de la derecha.
const YB = 840;
// Columna de la izquierda (fases 11 a 14, 22 y 23 oct): sigue a la fase 10 y baja de arriba hacia abajo.
const YC = 640, PC = 100;

const componentesFases = [
  { id: 'juan', type: 'external', label: 'Juan Esteban', sublabel: 'pide el motor', pos: [20, CY - 30], size: [130, 60] },
  { id: 'orquestador', type: 'frontend', label: 'Orquestador', sublabel: 'arma y coordina', tag: 'sesión principal', pos: [230, CY - 34], size: [150, 68] },
  { id: 'base', type: 'frontend', label: '1 · Base común', sublabel: 'núcleo + caso 1', tag: '8e24c4a', pos: [440, CY - 65], size: [170, 130] },
  { id: 'excel', type: 'database', label: 'Excel de Diego', sublabel: '9 hojas → 9 CSV', pos: [230, CY + 150], size: [150, 60] },
  { id: 'integracion', type: 'frontend', label: '3 · Integración', sublabel: '9 merges · 124 pruebas', tag: 'e815f82', pos: [1240, CY - 65], size: [170, 130] },
  { id: 'cuestionario', type: 'frontend', label: 'Cuestionario', sublabel: 'ReglasNegocio · 139 pruebas', tag: '73756ea', pos: [1470, CY - 34], size: [170, 68] },
  { id: 'diego', type: 'external', label: 'Diego · negocio', sublabel: 'responde por rondas', pos: [1470, CY + 100], size: [170, 60] },
  { id: 'respuestas', type: 'frontend', label: '4 · Respuestas 16 oct', sublabel: 'inicial · abonos · mora · 147 pruebas', tag: 'd3ce717', pos: [1440, CY + 190], size: [230, 68] },
  { id: 'excel11', type: 'frontend', label: '5 · Excel del 11 oct', sublabel: 'casos 10 a 12 · 167 pruebas', tag: '36d9995', pos: [1440, CY + 290], size: [230, 68] },
  { id: 'github', type: 'external', label: 'GitHub', sublabel: 'Fintech_Juanes_Heider', pos: [1470, CY - 190], size: [170, 60] },
  { id: 'ronda3', type: 'backend', label: '6 · Respuestas 20 oct', sublabel: 'ronda 3 · D20 a D33', tag: '4 ramas', pos: [1440, CY + 390], size: [230, 68] },
  { id: 'ronda4', type: 'frontend', label: 'Cuestionario ronda 4', sublabel: '16 confirmaciones', tag: 'abierta', pos: [1440, CY + 500], size: [230, 68] },
  { id: 'motor', type: 'backend', label: '7 · Motor', sublabel: 'prelación · misma cuota · caso 13', tag: '4d76c58', pos: [1110, YB - 90], size: [240, 68] },
  { id: 'periodicidad', type: 'backend', label: '8 · Periodicidad', sublabel: 'ADR 0002 · semanal a mensual', tag: '49539f9', pos: [1110, YB + 30], size: [240, 68] },
  { id: 'api', type: 'backend', label: '9 · API REST v1', sublabel: 'módulo api · /api/v1', tag: '2d04f7a', pos: [770, YB - 90], size: [220, 68] },
  { id: 'consola', type: 'backend', label: '9 · Consola', sublabel: 'React · consume el API', tag: '67bcc63', pos: [770, YB + 30], size: [220, 68] },
  { id: 'despliegue', type: 'frontend', label: '10 · Despliegue', sublabel: 'App Service F1 · GitHub Pages', tag: '296 pruebas', pos: [420, YB - 34], size: [230, 68] },
  { id: 'rondas56', type: 'frontend', label: '11 · Rondas 5 y 6', sublabel: 'respuestas de Diego · D48 a D55', tag: '22 oct', pos: [30, YC], size: [340, 68] },
  { id: 'ola1', type: 'backend', label: '12 · Ola 1', sublabel: 'finanzas y API endurecido', tag: '23 oct', pos: [30, YC + PC], size: [340, 68] },
  { id: 'ola2', type: 'backend', label: '13 · Ola 2', sublabel: 'refactor con red de huellas', tag: '23 oct', pos: [30, YC + 2 * PC], size: [340, 68] },
  { id: 'ola3', type: 'backend', label: '14 · Ola 3', sublabel: 'motor reanudable · reglas versionadas', tag: '23 oct', pos: [30, YC + 3 * PC], size: [340, 68] },
];
const conexionesFases = [
  { id: 'solicita', from: 'juan', to: 'orquestador', label: 'solicita', variant: 'emphasis' },
  { id: 'arma-base', from: 'orquestador', to: 'base', label: 'arma', variant: 'emphasis' },
  { id: 'exporta-csv', from: 'excel', to: 'orquestador', label: 'exporta a CSV', variant: 'dashed', fromSide: 'top', toSide: 'bottom' },
  { id: 'dudas', from: 'integracion', to: 'cuestionario', label: 'dudas', variant: 'emphasis' },
  { id: 'envia', from: 'cuestionario', to: 'diego', label: 'envía', variant: 'dashed', fromSide: 'bottom', toSide: 'top' },
  { id: 'respaldo', from: 'cuestionario', to: 'github', label: 'push', variant: 'dashed', fromSide: 'top', toSide: 'bottom' },
  { id: 'aplica', from: 'diego', to: 'respuestas', label: 'aplica', variant: 'emphasis', fromSide: 'bottom', toSide: 'top' },
  { id: 'excel-nuevo', from: 'respuestas', to: 'excel11', label: 'Excel nuevo', variant: 'emphasis', fromSide: 'bottom', toSide: 'top' },
  { id: 'ronda-3', from: 'excel11', to: 'ronda3', label: 'rondas 2 y 3', variant: 'emphasis', fromSide: 'bottom', toSide: 'top' },
  { id: 'abre-ronda4', from: 'ronda3', to: 'ronda4', label: 'abre', variant: 'dashed', fromSide: 'bottom', toSide: 'top' },
  { id: 'reglas-motor', from: 'ronda3', to: 'motor', label: 'reglas', variant: 'emphasis', fromSide: 'left', toSide: 'right' },
  { id: 'reglas-periodo', from: 'ronda3', to: 'periodicidad', fromSide: 'left', toSide: 'right' },
  { id: 'motor-api', from: 'motor', to: 'api', label: 'expone', variant: 'emphasis', fromSide: 'left', toSide: 'right' },
  { id: 'periodo-consola', from: 'periodicidad', to: 'consola', fromSide: 'left', toSide: 'right' },
  { id: 'consume', from: 'consola', to: 'api', label: 'consume', variant: 'dashed', fromSide: 'top', toSide: 'bottom' },
  { id: 'publica-api', from: 'api', to: 'despliegue', label: 'publica', variant: 'emphasis', fromSide: 'left', toSide: 'right' },
  { id: 'publica-consola', from: 'consola', to: 'despliegue', fromSide: 'left', toSide: 'right' },
  { id: 'sigue-rondas', from: 'despliegue', to: 'rondas56', label: 'sigue', variant: 'emphasis', fromSide: 'left', toSide: 'right' },
  { id: 'sigue-ola1', from: 'rondas56', to: 'ola1', variant: 'emphasis', fromSide: 'bottom', toSide: 'top' },
  { id: 'sigue-ola2', from: 'ola1', to: 'ola2', fromSide: 'bottom', toSide: 'top' },
  { id: 'sigue-ola3', from: 'ola2', to: 'ola3', fromSide: 'bottom', toSide: 'top' },
];
for (const [n, nombre, seg, her, pruebas, commit, entrega] of agentes) {
  const y = Y0 + (n - 1) * PASO;
  componentesFases.push(
    { id: `agente${n}`, type: 'backend', label: `Caso ${n} · ${nombre}`, sublabel: `agente IA · ${seg} s · ${her} herram.`, pos: [690 + ajusteAgente(n), y], size: [220, H] },
    { id: `entrega${n}`, type: 'database', label: `${pruebas} pruebas · ${commit}`, sublabel: entrega, pos: [960 + ajusteEntrega(n), y], size: [200, H] },
  );
  conexionesFases.push({ id: `entrega${n}-hecha`, from: `agente${n}`, to: `entrega${n}` });
}
for (const n of [1, 2, 3, 4, 5, 6, 7, 8, 9]) {
  conexionesFases.push({ id: `lanza${n}`, from: 'base', to: `agente${n}`, fromSide: ladoBase(n), toSide: 'left', ...(n === 1 ? { label: 'lanza' } : {}) });
}
for (const n of [1, 2, 3, 4, 5, 6, 7, 8, 9]) {
  conexionesFases.push({ id: `merge${n}`, from: `entrega${n}`, to: 'integracion', fromSide: 'right', toSide: ladoIntegracion(n), ...(n === 1 ? { label: 'merge' } : {}) });
}

const fases = {
  schema_version: 1,
  diagram_type: 'architecture',
  meta: {
    title: 'Motor de cartera · cómo se construyó',
    subtitle: '14 fases del 8 al 23 oct 2026: base común, 9 casos en paralelo, integración, rondas con negocio, motor, periodicidad, API, consola, despliegue y tres olas de robustez',
    locale: 'es',
    translations: es,
    output: 'fases.html',
    quality_profile: 'showcase',
    legend: {
      mode: 'auto',
      entries: {
        frontend: { label: 'Fase en secuencia (sesión principal)' },
        backend: { label: 'Carril en paralelo (agente o rama)' },
        database: { label: 'Datos y entregas' },
        external: { label: 'Personas y externos' },
      },
    },
  },
  components: componentesFases,
  boundaries: [
    { kind: 'region', label: '2 · 9 git worktrees en paralelo · 7 min 14 s', wraps: agentes.flatMap(([n]) => [`agente${n}`, `entrega${n}`]) },
    { kind: 'region', label: '20 oct · ramas en paralelo', wraps: ['motor', 'periodicidad'] },
    { kind: 'region', label: '20 oct · API y consola en paralelo', wraps: ['api', 'consola'] },
    { kind: 'region', label: '22 y 23 oct · rondas 5 y 6 y olas de robustez', wraps: ['rondas56', 'ola1', 'ola2', 'ola3'] },
  ],
  connections: conexionesFases,
  cards: [
    { dot: 'cyan', title: '1 a 3 · Base y 9 casos (8 oct)', items: [
      'Base común antes de lanzar los carriles (8e24c4a)',
      'Un agente por hoja del Excel, cada uno en su git worktree',
      '9 merges, 4 choques en Contabilizador unidos a mano: 124 pruebas',
    ] },
    { dot: 'emerald', title: '4 y 5 · Negocio y Excel nuevo (16 oct)', items: [
      'Pago inicial opcional, abono sin elección → menor plazo',
      'Excel del 11 oct: casos 10, 11 y 12 en la sesión principal',
      'Recaudo con vencidos: 167 pruebas',
    ] },
    { dot: 'violet', title: '6 · Rondas con negocio (20 oct)', items: [
      'Respuestas del 20 oct y ronda 3: decisiones D20 a D33',
      'El cuestionario guarda en la nube; el importador las lleva a config/',
      'Ronda 4 abierta: 16 confirmaciones concretas',
    ] },
    { dot: 'amber', title: '7 y 8 · Motor y periodicidad', items: [
      'Todo abono respeta la prelación; el excedente va al vencido más antiguo',
      'Usura con misma cuota; caso 13: abono extra con pendientes',
      'ADR 0002: periodicidad semanal, quincenal o mensual y CalendarioPagos',
    ] },
    { dot: 'rose', title: '9 y 10 · API, consola y despliegue (20 y 21 oct)', items: [
      'API REST v1 (D34) y consola React integrada con prueba e2e',
      'API en Azure App Service F1; consola, cuestionario y diagrama en GitHub Pages',
      '296 pruebas; completitud de reglas 67 %',
    ] },
    { dot: 'cyan', title: '11 · Rondas 5 y 6 (22 oct)', items: [
      'Diego respondió por el cuestionario y por documento: decisiones D48 a D55',
      'Prorrateo de la primera cuota, mora mensual por días reales y cobranza con vigencias',
      'API y consola con fechas opcionales del crédito',
    ] },
    { dot: 'orange', title: '12 a 14 · Olas de robustez (23 oct)', items: [
      'Ola 1: correcciones de la auditoría financiera, API endurecido (topes, cuerpo, cabeceras, clave opcional) y CI en jobs separados',
      'Ola 2: MotorCartera y ServicioCartera se parten en clases pequeñas, sin cambiar comportamiento y con la red de huellas',
      'Ola 3: estado reanudable del motor, recálculo por usura, versiones de reglas y bitácora; persistencia solo diseñada (ADR 0004)',
      'Al cierre: 602 pruebas de Java y cobertura de ramas del 91 %',
    ] },
    { dot: 'slate', title: 'Pendiente', items: [
      'Ronda 7: 2 preguntas abiertas con negocio',
      'Construir la persistencia, la idempotencia de abonos y el cierre diario',
    ] },
  ],
};

// ───────────────────────── Render con archify y armado del HTML ─────────────────────────
const cli = path.join(archify, 'archify/bin/archify.mjs');
const tmp = mkdtempSync(path.join(tmpdir(), 'diagrama-'));
const vistas = [
  { id: 'arquitectura', nombre: 'Arquitectura', doc: arquitectura },
  { id: 'fases', nombre: 'Cómo se construyó', doc: fases },
];
for (const v of vistas) {
  const json = new URL(`./${v.id}.architecture.json`, fuente);
  writeFileSync(json, JSON.stringify(v.doc, null, 2) + '\n');
  const html = path.join(tmp, `${v.id}.html`);
  execFileSync(process.execPath, [cli, 'finalize', 'architecture', fileURLToPath(json), html, '--quality', 'showcase', '--out-dir', tmp], { stdio: 'inherit' });
  // En celular la barra de ruta/lente/zoom de archify no cabe en 360 px: se deja partir en dos filas.
  const movil = '<style>@media (max-width: 480px) { .diagram-nav { flex-wrap: wrap; justify-content: center; row-gap: 4px; overflow: visible; } }</style>';
  v.html = readFileSync(html, 'utf8').replace('</head>', `${movil}</head>`);
  console.log('ok', v.id, v.doc.components.length, 'nodos', v.doc.connections.length, 'conexiones');
}

const escapar = (s) => s.replaceAll('&', '&amp;').replaceAll('"', '&quot;');
const pagina = `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Motor de cartera · Diagrama</title>
<meta name="description" content="Arquitectura del motor de cartera y fases de su construcción, generado con archify.">
<style>
  :root { --fondo: #f4f5f7; --barra: #ffffff; --borde: #e2e5ea; --texto: #0f172a; --suave: #64748b; --activa: #0e7490; --activa-fondo: #e0f2f7; color-scheme: light; }
  @media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) { --fondo: #0b1220; --barra: #0f172a; --borde: #1e293b; --texto: #e2e8f0; --suave: #94a3b8; --activa: #67e8f9; --activa-fondo: #123040; color-scheme: dark; } }
  :root[data-theme="dark"] { --fondo: #0b1220; --barra: #0f172a; --borde: #1e293b; --texto: #e2e8f0; --suave: #94a3b8; --activa: #67e8f9; --activa-fondo: #123040; color-scheme: dark; }
  * { box-sizing: border-box; }
  html, body { margin: 0; height: 100%; background: var(--fondo); color: var(--texto); font: 14px/1.4 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
  body { display: flex; flex-direction: column; overflow: hidden; }
  header { flex: none; display: flex; align-items: center; gap: 12px; padding: 8px 16px; background: var(--barra); border-bottom: 1px solid var(--borde); }
  header strong { font-size: 14px; white-space: nowrap; }
  nav { display: flex; gap: 4px; min-width: 0; }
  nav button { font: inherit; font-weight: 600; color: var(--suave); background: transparent; border: 1px solid transparent; border-radius: 8px; padding: 6px 12px; cursor: pointer; white-space: nowrap; }
  nav button:hover { color: var(--texto); }
  nav button[aria-selected="true"] { color: var(--activa); background: var(--activa-fondo); border-color: var(--borde); }
  nav button:focus-visible { outline: 2px solid var(--activa); outline-offset: 2px; }
  main { flex: 1; min-height: 0; position: relative; }
  iframe { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; background: var(--fondo); }
  iframe[hidden] { display: none; }
  @media (max-width: 480px) { header strong { display: none; } nav { flex: 1; } nav button { flex: 1; padding: 6px 8px; } }
</style>
</head>
<body>
<header>
  <strong>Motor de cartera</strong>
  <nav role="tablist" aria-label="Vistas del diagrama">
${vistas.map((v, i) => `    <button role="tab" id="pestana-${v.id}" aria-controls="vista-${v.id}" aria-selected="${i === 0}" data-vista="${v.id}">${v.nombre}</button>`).join('\n')}
  </nav>
</header>
<main>
${vistas.map((v) => `  <iframe role="tabpanel" id="vista-${v.id}" aria-labelledby="pestana-${v.id}" title="${v.nombre}" hidden data-doc="${escapar(v.html)}"></iframe>`).join('\n')}
</main>
<script>
(() => {
  const pestanas = [...document.querySelectorAll('[role="tab"]')];
  const raiz = document.documentElement;
  // El tema lo maneja archify dentro de cada vista: la barra copia el de la vista activa y lo pasa a la otra.
  const seguirTema = (marco) => {
    const doc = marco.contentDocument;
    if (!doc) return;
    const copiar = () => { const t = doc.documentElement.getAttribute('data-theme'); if (t) raiz.setAttribute('data-theme', t); };
    copiar();
    new MutationObserver(copiar).observe(doc.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
  };
  const mostrar = (id, foco) => {
    for (const p of pestanas) {
      const activa = p.dataset.vista === id;
      p.setAttribute('aria-selected', String(activa));
      p.tabIndex = activa ? 0 : -1;
      const marco = document.getElementById('vista-' + p.dataset.vista);
      marco.hidden = !activa;
      if (activa && marco.dataset.doc) {
        marco.addEventListener('load', () => seguirTema(marco), { once: true });
        marco.srcdoc = marco.dataset.doc;
        delete marco.dataset.doc;
      } else if (activa) {
        const t = raiz.getAttribute('data-theme');
        const doc = marco.contentDocument;
        if (t && doc && doc.documentElement.getAttribute('data-theme') !== t) doc.documentElement.setAttribute('data-theme', t);
      }
      if (activa && foco) p.focus();
    }
    history.replaceState(null, '', '#' + id);
  };
  pestanas.forEach((p, i) => {
    p.addEventListener('click', () => mostrar(p.dataset.vista));
    p.addEventListener('keydown', (e) => {
      const d = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
      if (d) mostrar(pestanas[(i + d + pestanas.length) % pestanas.length].dataset.vista, true);
    });
  });
  const inicial = location.hash.slice(1);
  mostrar(pestanas.some((p) => p.dataset.vista === inicial) ? inicial : pestanas[0].dataset.vista);
})();
</script>
</body>
</html>
`;
writeFileSync(salida, pagina);
console.log('ok', salida.pathname, Math.round(pagina.length / 1024), 'KB');
