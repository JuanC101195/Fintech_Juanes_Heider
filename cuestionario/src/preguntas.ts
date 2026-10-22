import type { Opcion, Pregunta, Respondida } from './tipos'

// Preguntas que salieron de reproducir el Excel "Casos Operaciones V2" celda por celda en el motor.
// Los códigos de opción y las claves de "ajustes" los lee el importador del motor: no cambiarlos
// sin actualizar herramientas/importar_respuestas.py.

export const BLOQUES = [
  'Pagos, mora y cobranza',
  'Servicios complementarios',
  'Abonos y prepago',
  'Cambio de tasa y usura',
  'Dación y retoma',
  'Contabilidad',
  'Roles y parámetros',
  'Ajustes al Excel',
] as const

/** Código estable de la opción común a todas las preguntas: la respuesta real va en el comentario. */
export const CODIGO_PENDIENTE = 'PENDIENTE'

const OPCION_PENDIENTE: Opcion = {
  codigo: CODIGO_PENDIENTE,
  texto: 'Respondo en el comentario / queda pendiente',
  detalle: 'No cambia nada en el motor. Escriba su respuesta en el comentario para que cuente como completa.',
  pideComentario: true,
}

const [PAGOS, SERVICIOS, ABONOS, TASA, RECUPERACION, CONTABILIDAD, ROLES, EXCEL] = BLOQUES

const BASE: Pregunta[] = [
  // ── Pagos, mora y cobranza ────────────────────────────────────────────────────
  {
    id: 'P1',
    bloque: PAGOS,
    titulo: 'Cuando un pago no alcanza, ¿se pagan primero los servicios o el capital?',
    contexto:
      'Define el orden en que se reparte cualquier pago. Si los servicios van antes, un pago incompleto deja el capital sin abonar y el cliente acumula capital vencido (y mora) más rápido.',
    excelHoy:
      'El texto de la hoja Casos dice "mora → interés corriente → capital" (no menciona servicios). Las fórmulas de los casos 8 y 9 pagan cobranza → mora → interés → servicios → capital.',
    previsualizacion: 'P1',
    opciones: [
      {
        codigo: 'servicios-antes',
        texto: 'Cobranza → mora → interés → servicios → capital',
        detalle: 'Como las fórmulas de los casos 8 y 9.',
        esExcel: true,
        escenario: 'servicios-antes',
        ajustes: { prelacion: 'SERVICIOS_ANTES_DE_CAPITAL' },
      },
      {
        codigo: 'capital-antes',
        texto: 'Cobranza → mora → interés → capital de la cuota → servicios',
        detalle: 'El capital de la cuota se paga antes que los servicios; lo que sobre va a servicios.',
        escenario: 'capital-antes',
        ajustes: { prelacion: 'CAPITAL_ANTES_DE_SERVICIOS' },
      },
      { codigo: 'otra', texto: 'Otro orden', pideTexto: true },
    ],
  },
  {
    id: 'P3',
    bloque: PAGOS,
    titulo: '¿Cómo se calcula el interés de mora?',
    contexto: 'Hoy hay dos fórmulas distintas en el Excel. El motor necesita una sola regla para todos los atrasos.',
    excelHoy:
      'Caso 8: capital vencido × tasa semanal. Caso 9: además suma capital de la cuota × ((1 + tasa diaria)^días de atraso − 1).',
    previsualizacion: 'P3',
    opciones: [
      {
        codigo: 'vencido-mas-dias',
        texto: 'Capital vencido × tasa semanal + días de atraso sobre el capital de la cuota',
        detalle: 'La fórmula del caso 9.',
        esExcel: true,
        escenario: 'vencido-mas-dias',
        ajustes: { formulaMora: 'VENCIDO_MAS_DIAS' },
      },
      {
        codigo: 'solo-vencido',
        texto: 'Solo capital vencido × tasa semanal',
        detalle: 'La fórmula del caso 8.',
        escenario: 'solo-vencido',
        ajustes: { formulaMora: 'SOLO_VENCIDO' },
      },
      { codigo: 'otra', texto: 'Otra fórmula (por ejemplo, tasa de mora distinta a la corriente)', pideTexto: true },
    ],
  },
  {
    id: 'PDOBLE',
    bloque: PAGOS,
    titulo: '¿El capital vencido paga interés corriente además de mora?',
    contexto:
      'En el Excel el interés corriente se calcula sobre el saldo total, incluido el capital vencido, que además paga mora. En el caso 8 el interés corriente de más es exactamente igual a la mora cobrada (99.772,35). Conviene revisarlo frente a la usura.',
    excelHoy: 'Interés corriente = saldo total (vigente + vencido) × tasa semanal; mora = capital vencido × tasa semanal.',
    previsualizacion: 'PDOBLE',
    opciones: [
      {
        codigo: 'saldo-total',
        texto: 'Sí: interés corriente sobre todo el saldo, y mora sobre lo vencido',
        esExcel: true,
        escenario: 'saldo-total',
        ajustes: { baseInteres: 'SALDO_TOTAL' },
      },
      {
        codigo: 'saldo-vigente',
        texto: 'No: interés corriente solo sobre el capital vigente; lo vencido paga solo mora',
        escenario: 'saldo-vigente',
        ajustes: { baseInteres: 'SALDO_VIGENTE' },
      },
    ],
  },
  {
    id: 'PPER',
    bloque: PAGOS,
    titulo: '¿Con qué periodicidades se ofrecen los créditos y cómo se calculan?',
    contexto:
      'Usted dijo que los créditos pueden ser semanales, quincenales o mensuales según la negociación. Esta pregunta cubre: qué periodicidades se ofrecen, cómo se convierte la tasa E.A. a la tasa del periodo y si los servicios mensuales (fondo de garantías, asistencia, seguro) se prorratean al periodo. Escriba esas respuestas en el detalle o en el comentario. Las demás decisiones (vencimientos, plazo, redondeo, mora, prepago…) están en las preguntas PPER-1 a PPER-10. No hace falta un Excel nuevo: el motor generará un plan quincenal y uno mensual de ejemplo para que usted los revise.',
    excelHoy: 'Solo cuotas semanales; el Excel no modela cuotas quincenales ni mensuales.',
    opciones: [
      { codigo: 'solo-semanal', texto: 'Solo semanal, como el Excel', esExcel: true },
      { codigo: 'semanal-quincenal-mensual', texto: 'Semanal, quincenal y mensual según la negociación', detalle: 'Indique en el detalle las periodicidades, la conversión de la tasa y el prorrateo de servicios.', pideTexto: true },
      { codigo: 'otra', texto: 'Otra combinación de periodicidades', pideTexto: true },
    ],
  },
  {
    id: 'PPER-1',
    bloque: PAGOS,
    titulo: '¿En qué día vencen las cuotas quincenales y las mensuales?',
    contexto: 'Define el calendario de vencimientos y desde cuándo corre el atraso.',
    excelHoy: 'El Excel solo modela cuotas semanales.',
    opciones: [
      { codigo: 'quincenal-15-y-ultimo', texto: 'Quincenal: el 15 y el último día del mes; mensual: el mismo día del desembolso' },
      { codigo: 'cada-15-dias', texto: 'Quincenal: cada 15 días desde el desembolso; mensual: el mismo día de cada mes' },
      { codigo: 'cada-2-martes', texto: 'Quincenal: cada 2 martes (o el día fijo de la semana); mensual: un día fijo del mes' },
      { codigo: 'otra', texto: 'Otra regla', pideTexto: true },
    ],
  },
  {
    id: 'PPER-2',
    bloque: PAGOS,
    titulo: '¿El plazo se pacta en meses o en número de cuotas?',
    contexto: 'Con cuotas semanales el plazo es un número de cuotas (104). Con quincenal o mensual hay que saber cuál es el dato que se digita.',
    excelHoy: 'El Excel solo modela cuotas semanales.',
    opciones: [
      { codigo: 'meses', texto: 'En meses; el número de cuotas se calcula' },
      { codigo: 'cuotas', texto: 'En número de cuotas' },
      { codigo: 'otra', texto: 'Otra regla', pideTexto: true },
    ],
  },
  {
    id: 'PPER-3',
    bloque: PAGOS,
    titulo: '¿Cómo se redondea el número de cuotas cuando el plazo se pacta en meses?',
    contexto: 'Hoy el Excel calcula ROUNDDOWN(meses × periodos/12): siempre hacia abajo.',
    excelHoy: 'El Excel solo modela cuotas semanales.',
    opciones: [
      { codigo: 'hacia-abajo', texto: 'Hacia abajo, como el Excel: ROUNDDOWN(meses × periodos/12)' },
      { codigo: 'hacia-arriba', texto: 'Hacia arriba' },
      { codigo: 'otra', texto: 'Otra regla', pideTexto: true },
    ],
  },
  {
    id: 'PPER-4',
    bloque: PAGOS,
    titulo: '¿La periodicidad quincenal entra al catálogo de Inputs? ¿La diaria sigue siendo una opción real?',
    contexto: 'El catálogo de Inputs define qué periodicidades se pueden escoger en B13.',
    excelHoy: 'El Excel solo modela cuotas semanales.',
    opciones: [
      { codigo: 'quincenal-si-diaria-si', texto: 'Quincenal entra y la diaria se mantiene' },
      { codigo: 'quincenal-si-diaria-no', texto: 'Quincenal entra y la diaria se retira' },
      { codigo: 'otra', texto: 'Otra regla', pideTexto: true },
    ],
  },
  {
    id: 'PPER-5',
    bloque: PAGOS,
    titulo: 'En mensual, ¿la mora de un periodo se cuenta por el periodo nominal o por los días reales del mes (28 a 31)? ¿Desde qué día corre el atraso?',
    contexto: 'Cambia el valor de la mora de cada cuota mensual vencida.',
    excelHoy: 'El Excel solo modela cuotas semanales.',
    opciones: [
      { codigo: 'periodo-nominal', texto: 'Por el periodo nominal (30 días)' },
      { codigo: 'dias-reales', texto: 'Por los días reales del mes (28 a 31)' },
      { codigo: 'otra', texto: 'Otra regla', pideTexto: true },
    ],
  },
  {
    id: 'PPER-6',
    bloque: PAGOS,
    titulo: '¿Cómo se expresa el plazo límite de recuperación: en periodos, en meses o en semanas?',
    contexto: 'Hoy el plazo límite se da en semanas (por ejemplo, 120). Con otras periodicidades debe tener una sola unidad.',
    excelHoy: 'El Excel solo modela cuotas semanales.',
    opciones: [
      { codigo: 'periodos', texto: 'En periodos de la periodicidad del crédito' },
      { codigo: 'meses', texto: 'En meses' },
      { codigo: 'semanas', texto: 'En semanas, para todas las periodicidades' },
      { codigo: 'otra', texto: 'Otra regla', pideTexto: true },
    ],
  },
  {
    id: 'PPER-7',
    bloque: PAGOS,
    titulo: 'En un prepago a mitad de periodo, ¿se cobran los días transcurridos?',
    contexto: 'Define si el interés corre hasta la fecha del prepago o hasta el cierre del periodo.',
    excelHoy: 'El Excel solo modela cuotas semanales.',
    opciones: [
      { codigo: 'si-dias-transcurridos', texto: 'Sí, se cobra el interés de los días transcurridos' },
      { codigo: 'no', texto: 'No, solo se liquida la cuota del periodo' },
      { codigo: 'otra', texto: 'Otra regla', pideTexto: true },
    ],
  },
  {
    id: 'PPER-8',
    bloque: PAGOS,
    titulo: 'En mensual, ¿cuándo cuenta una cuota como vencida y cómo se cobra la cobranza?',
    contexto: 'Hoy la cobranza es de 3.000 por cada día de atraso en cuotas semanales.',
    excelHoy: 'El Excel solo modela cuotas semanales.',
    opciones: [
      { codigo: 'dia-siguiente-3000', texto: 'Vencida desde el día siguiente al vencimiento; cobranza de 3.000 por día' },
      { codigo: 'otra', texto: 'Otra regla (vencimiento y cobranza)', pideTexto: true },
    ],
  },
  {
    id: 'PPER-9',
    bloque: PAGOS,
    titulo: 'Los "4 periodos de espera" del caso 11, ¿se cuentan en periodos o en días?',
    contexto: 'Con cuotas semanales 4 periodos son 28 días; con mensuales serían 4 meses.',
    excelHoy: 'El Excel solo modela cuotas semanales.',
    opciones: [
      { codigo: 'periodos', texto: 'En periodos de la periodicidad del crédito' },
      { codigo: 'dias', texto: 'En días (28), sea cual sea la periodicidad' },
      { codigo: 'otra', texto: 'Otra regla', pideTexto: true },
    ],
  },
  {
    id: 'PPER-10',
    bloque: PAGOS,
    titulo: '¿La periodicidad puede cambiar durante la vida del crédito?',
    contexto: 'Si puede cambiar, hay que recalcular el plan restante con la nueva periodicidad.',
    excelHoy: 'El Excel solo modela cuotas semanales.',
    opciones: [
      { codigo: 'no', texto: 'No, queda fija desde el desembolso' },
      { codigo: 'si', texto: 'Sí, por acuerdo con el cliente', pideTexto: true },
      { codigo: 'otra', texto: 'Otra regla', pideTexto: true },
    ],
  },
  {
    id: 'PTOPE',
    bloque: PAGOS,
    titulo: '¿Los gastos de cobranza tienen tope por pago?',
    contexto:
      'Con 14 días de atraso en cada cuota, el Excel cobra 42.000 por pago (24 % de la cuota). El crédito del caso 9 nunca termina: el cliente paga 18,7 millones sobre 5,9 y a la semana 120 aún debe. Un tope cambia eso.',
    excelHoy: '3.000 por cada día de atraso, sin tope (14 días = 42.000 en cada pago).',
    previsualizacion: 'PTOPE',
    opciones: [
      { codigo: 'sin-tope', texto: 'Sin tope: 3.000 por día de atraso', esExcel: true, escenario: 'sin-tope' },
      { codigo: 'con-tope', texto: 'Con tope por pago', pideValor: true },
      { codigo: 'otra', texto: 'Otra regla (por ejemplo, una sola vez por cuota vencida)', pideTexto: true },
    ],
    valor: {
      etiqueta: 'Tope de cobranza por pago',
      unidad: 'COP',
      ajuste: 'topeCobranzaPorPago',
      sugeridos: [
        { valor: 20000, escenario: 'tope-20000' },
        { valor: 10000, escenario: 'tope-10000' },
        { valor: 0, escenario: 'tope-0' },
      ],
    },
  },
  {
    id: 'P7',
    bloque: PAGOS,
    titulo: 'Antes de una dación o retoma, ¿se causan mora y servicios mientras el cliente no paga?',
    contexto:
      'En los casos 3 y 4 el cliente deja de pagar y el Excel solo acumula interés corriente. En los casos 8 y 9, ante el mismo atraso, sí hay capital vencido, mora y servicios por cobrar. Cambia la deuda que se extingue y el faltante que cubre el FCC.',
    excelHoy: 'Casos 3 y 4: sin mora, sin capital vencido, sin servicios. Casos 8 y 9: con todo.',
    previsualizacion: 'P7',
    opciones: [
      { codigo: 'sin-mora', texto: 'No: solo interés corriente, como los casos 3 y 4', esExcel: true, escenario: 'sin-mora', ajustes: { moraAntesDeRecuperacion: 'false' } },
      { codigo: 'con-mora', texto: 'Sí: igual que cualquier atraso (casos 8 y 9)', escenario: 'con-mora', ajustes: { moraAntesDeRecuperacion: 'true' } },
    ],
  },
  {
    id: 'P2',
    bloque: PAGOS,
    titulo: '¿Cómo se redondean los valores?',
    contexto: 'El Excel trabaja con decimales sin redondear (por ejemplo, cuota de 111.014,82187…). Lo que se cobra y se contabiliza debe tener una regla clara.',
    excelHoy: 'No redondea. Las pruebas aceptan 1 peso de diferencia.',
    opciones: [
      { codigo: 'peso-por-concepto', texto: 'Cada concepto (interés, capital, servicios) al peso; la última cuota absorbe la diferencia', ajustes: { redondeo: 'PESO_POR_CONCEPTO' } },
      { codigo: 'cuota-al-peso', texto: 'Solo la cuota total al peso; el desglose interno con centavos', ajustes: { redondeo: 'CUOTA_AL_PESO' } },
      { codigo: 'cuota-a-la-centena', texto: 'Cuota total a la centena superior (más fácil de pagar en efectivo)', ajustes: { redondeo: 'CUOTA_A_LA_CENTENA' } },
      { codigo: 'sin-redondeo', texto: 'Sin redondeo, como el Excel', esExcel: true, ajustes: { redondeo: 'NINGUNO' } },
    ],
  },
  {
    id: 'PCOB',
    bloque: PAGOS,
    titulo: 'Antes de una dación o retoma, ¿cómo se cuentan los días de cobranza si el cliente no paga?',
    contexto:
      'Usted respondió que en dación y retoma sí se cobran mora y cobranza. La mora ya se causa cada semana sin pago, pero la cobranza se calcula por los días de atraso de un pago recibido, y en esas semanas no llega ningún pago.',
    excelHoy: 'Casos 3 y 4: sin cobranza. Caso 9: 3.000 por día de atraso de cada pago.',
    opciones: [
      { codigo: 'siete-por-semana', texto: '7 días por cada semana sin pago (21.000 por semana)' },
      { codigo: 'desde-primer-atraso', texto: 'Los días desde la primera cuota vencida hasta el evento, una sola vez' },
      { codigo: 'no-cobranza', texto: 'Solo mora; la cobranza no aplica sin pago' },
      { codigo: 'otra', texto: 'Otra forma', pideTexto: true },
    ],
  },
  {
    id: 'PDIAS',
    bloque: PAGOS,
    titulo: 'Con cuotas semanales, un atraso de 14 días deja dos cuotas más vencidas. ¿Cómo se cobra?',
    contexto: 'En el caso 9, cuando el cliente paga con 14 días de atraso ya vencieron las dos cuotas siguientes, pero el Excel trata cada semana por separado.',
    excelHoy: 'Cada semana es independiente: un pago por semana, cada uno con su cobranza.',
    opciones: [
      { codigo: 'por-semana', texto: 'Cada semana por separado, como el Excel', esExcel: true },
      { codigo: 'consolidado', texto: 'Consolidar lo vencido: un pago cubre las cuotas atrasadas en orden y la cobranza se cobra una vez' },
      { codigo: 'otra', texto: 'Otra forma', pideTexto: true },
    ],
  },

  // ── Servicios complementarios ─────────────────────────────────────────────────
  {
    id: 'S1',
    bloque: SERVICIOS,
    titulo: '¿FCC y seguro de vida se calculan sobre el monto inicial o sobre el saldo?',
    contexto: 'Si el cliente abona y acorta el plazo, con base en el monto inicial se dejan de cobrar semanas completas; con base en el saldo, cada semana se cobra menos.',
    excelHoy: 'Sobre el monto financiado inicial, fijo en todas las cuotas.',
    previsualizacion: 'S1',
    opciones: [
      { codigo: 'monto-inicial', texto: 'Sobre el monto financiado inicial (fijo)', esExcel: true, escenario: 'monto-inicial', ajustes: { baseServicios: 'MONTO_INICIAL' } },
      { codigo: 'saldo', texto: 'Sobre el saldo de capital de cada semana (decreciente)', escenario: 'saldo', ajustes: { baseServicios: 'SALDO' } },
    ],
  },
  {
    id: 'S2',
    bloque: SERVICIOS,
    titulo: 'Con la cuota inicial del 10 %, ¿sobre qué valor se calculan FCC y seguro?',
    contexto: 'La cuota inicial es el 10 % del valor total de la operación. Si los servicios se calculan sobre lo financiado (90 %), el FCC baja unos 418.000 en el crédito del ejemplo.',
    excelHoy: 'El Excel no tiene cuota inicial.',
    previsualizacion: 'INICIAL',
    opciones: [
      { codigo: 'financiado', texto: 'Sobre el monto financiado (90 % del valor total)', escenario: 'inicial-10', ajustes: { baseServiciosConInicial: 'FINANCIADO' } },
      { codigo: 'valor-total', texto: 'Sobre el valor total de la operación (incluida la inicial)', ajustes: { baseServiciosConInicial: 'VALOR_TOTAL' } },
    ],
  },
  {
    id: 'S3',
    bloque: SERVICIOS,
    titulo: 'Los servicios son mensuales pero se cobran por semana. ¿Cómo se reparten?',
    contexto: 'Valor mensual ÷ 4,333 semanas: los meses con 5 cuotas cobran y giran 25 % más que los de 4.',
    excelHoy: 'Valor mensual ÷ 4,333 en cada cuota semanal.',
    opciones: [
      { codigo: 'mensual-entre-semanas', texto: 'Valor mensual ÷ 4,333 en cada cuota, como el Excel', esExcel: true },
      { codigo: 'primera-semana-del-mes', texto: 'Se cobra completo en la primera cuota de cada mes' },
      { codigo: 'por-dias', texto: 'Prorrateo exacto por días de cada semana' },
    ],
  },
  {
    id: 'S4',
    bloque: SERVICIOS,
    titulo: 'Si el crédito sigue con saldo después de la última cuota pactada, ¿se siguen cobrando servicios?',
    contexto: 'En el caso 8 el crédito sigue con saldo después de la cuota 104, la última pactada, y desde ahí no se causan servicios: por ejemplo, el seguro de vida no tendría prima esas semanas.',
    excelHoy: 'Los servicios se cobran solo hasta la última cuota pactada (104).',
    previsualizacion: 'S4',
    opciones: [
      { codigo: 'no', texto: 'No, solo hasta la última cuota pactada', esExcel: true, escenario: 'no', ajustes: { serviciosDespuesDelPlazo: 'false' } },
      { codigo: 'si', texto: 'Sí, mientras haya saldo', escenario: 'si', ajustes: { serviciosDespuesDelPlazo: 'true' } },
    ],
  },
  {
    id: 'S5',
    bloque: SERVICIOS,
    titulo: '¿A los terceros (FCC, asistencia, aseguradora) se les gira lo causado o lo recaudado?',
    contexto: 'Si se gira lo causado, la financiera adelanta lo que el cliente no paga.',
    excelHoy: 'Se gira lo causado cada mes, aunque el cliente no haya pagado los servicios.',
    opciones: [
      { codigo: 'causado', texto: 'Lo causado, como el Excel', esExcel: true },
      { codigo: 'recaudado', texto: 'Solo lo efectivamente recaudado' },
      { codigo: 'depende', texto: 'Depende del tercero', pideTexto: true },
    ],
  },
  {
    id: 'S6',
    bloque: SERVICIOS,
    titulo: 'Contablemente, ¿los servicios son un pasivo con terceros o un ingreso propio?',
    contexto: 'El caso 1 los registra como "CxP servicios a terceros" y los casos 8 y 9 como "Servicios complementarios".',
    excelHoy: 'Dos nombres distintos según el caso.',
    opciones: [
      { codigo: 'pasivo', texto: 'Pasivo con terceros (se recauda por cuenta de ellos)' },
      { codigo: 'ingreso', texto: 'Ingreso propio' },
      { codigo: 'mixto', texto: 'Mixto (por ejemplo, el FCC es propio y la aseguradora es tercero)', pideTexto: true },
    ],
  },

  // ── Abonos y prepago ──────────────────────────────────────────────────────────
  {
    id: 'A1',
    bloque: ABONOS,
    titulo: '¿Quién elige si un abono extra reduce el plazo o la cuota?',
    contexto: 'El CRM registra abonos todos los días; si el cliente no dice nada, el motor necesita una opción por defecto.',
    excelHoy: 'Dos casos separados (6 y 7); no dice cuál es el defecto.',
    opciones: [
      { codigo: 'cliente-defecto-plazo', texto: 'El cliente elige; si no dice nada, menor plazo', ajustes: { abonoPorDefecto: 'MENOR_PLAZO' } },
      { codigo: 'cliente-defecto-cuota', texto: 'El cliente elige; si no dice nada, menor cuota', ajustes: { abonoPorDefecto: 'MENOR_CUOTA' } },
      { codigo: 'siempre-plazo', texto: 'Siempre menor plazo', ajustes: { abonoPorDefecto: 'MENOR_PLAZO' } },
      { codigo: 'siempre-cuota', texto: 'Siempre menor cuota', ajustes: { abonoPorDefecto: 'MENOR_CUOTA' } },
    ],
  },
  {
    id: 'A2',
    bloque: ABONOS,
    titulo: 'Si un abono es mayor que el saldo, ¿qué pasa con el excedente?',
    contexto: 'Hoy el excedente desaparece: el Excel aplica solo hasta el saldo y no registra el resto.',
    excelHoy: 'Se descarta sin registro.',
    opciones: [
      { codigo: 'devolver', texto: 'Se devuelve al cliente', ajustes: { excedenteAbono: 'DEVOLVER' } },
      { codigo: 'saldo-a-favor', texto: 'Queda como saldo a favor', ajustes: { excedenteAbono: 'SALDO_A_FAVOR' } },
      { codigo: 'rechazar', texto: 'Se rechaza el abono por el excedente', ajustes: { excedenteAbono: 'RECHAZAR' } },
    ],
  },
  {
    id: 'A3',
    bloque: ABONOS,
    titulo: '¿Hay un monto mínimo para un abono extra?',
    contexto:
      'Evita recalcular el plan por abonos de muy bajo valor. Su comentario del 20 de octubre cubre en qué orden se aplica el abono (prelación), pero no dice cuál es el monto mínimo: ¿lo hay o no, y de cuánto?',
    excelHoy: 'No lo define.',
    opciones: [
      { codigo: 'sin-minimo', texto: 'Sin mínimo', ajustes: { abonoMinimo: '0' } },
      { codigo: 'con-minimo', texto: 'Con mínimo', pideValor: true },
    ],
    valor: { etiqueta: 'Abono extra mínimo', unidad: 'COP', ajuste: 'abonoMinimo', sugeridos: [{ valor: 50000 }, { valor: 100000 }, { valor: 174049 }] },
  },
  {
    id: 'A4',
    bloque: ABONOS,
    titulo: 'Si el cliente tiene capital vencido y hace un abono extra, ¿cómo se aplica?',
    contexto: 'El Excel solo modela abonos de clientes al día.',
    excelHoy: 'No lo define.',
    opciones: [
      { codigo: 'primero-vencidos', texto: 'Primero se pagan los vencidos por prelación y el resto va a capital' },
      { codigo: 'rechazar', texto: 'No se acepta abono extra hasta que esté al día' },
      { codigo: 'todo-capital', texto: 'Todo el abono va a capital vigente' },
    ],
  },
  {
    id: 'A5',
    bloque: ABONOS,
    titulo: 'Al recalcular la cuota después de un abono, ¿con qué tasa?',
    contexto: 'Si antes hubo un cambio de tasa (caso 2), usar la tasa original no deja el saldo en cero en la última cuota.',
    excelHoy: 'La tasa original de la hoja Inputs.',
    opciones: [
      { codigo: 'vigente', texto: 'La tasa vigente en ese momento' },
      { codigo: 'original', texto: 'La tasa original pactada', esExcel: true },
    ],
  },
  {
    id: 'A6',
    bloque: ABONOS,
    titulo: 'En un prepago total, ¿se cobra interés por los días desde la última cuota?',
    contexto: 'En el Excel el prepago ocurre el mismo día de la cuota. En la vida real llega cualquier día. La Ley 1555 de 2012 prohíbe penalizar el prepago.',
    excelHoy: 'Prepago el día de la cuota: solo saldo de capital.',
    opciones: [
      { codigo: 'por-dias', texto: 'Sí, interés corriente por los días transcurridos' },
      { codigo: 'no', texto: 'No, solo el saldo de capital' },
    ],
  },

  // ── Cambio de tasa y usura ────────────────────────────────────────────────────
  {
    id: 'T1',
    bloque: TASA,
    titulo: '¿Desde cuándo aplica un cambio de tasa?',
    contexto: 'El Excel ubica el cambio por mes (mes 11 → cuota 44).',
    excelHoy: 'Desde la primera cuota del mes del cambio.',
    opciones: [
      { codigo: 'primera-cuota-del-mes', texto: 'Desde la primera cuota del mes', esExcel: true },
      { codigo: 'siguiente-cuota', texto: 'Desde la cuota siguiente a la fecha del cambio' },
      { codigo: 'fecha-prorrateo', texto: 'Desde la fecha exacta, prorrateando el interés de esa semana' },
    ],
  },
  {
    id: 'T2',
    bloque: TASA,
    titulo: 'Si la tasa supera la usura, ¿cuánto por debajo se ajusta?',
    contexto: 'Lo acordado fue "usura menos 1 punto". Hay que confirmar si es un punto porcentual de la tasa efectiva anual.',
    excelHoy: 'No está en el Excel (acuerdo de la reunión de requisitos).',
    opciones: [
      { codigo: 'puntos-ea', texto: 'Puntos porcentuales de la tasa efectiva anual', pideValor: true },
      { codigo: 'otra', texto: 'Otra forma', pideTexto: true },
    ],
    valor: { etiqueta: 'Margen bajo la usura', unidad: 'pp', ajuste: 'margenUsura', sugeridos: [{ valor: 1 }, { valor: 0.5 }, { valor: 2 }] },
  },
  {
    id: 'T3',
    bloque: TASA,
    titulo: '¿En qué modalidad se registra el crédito y cuál es su tasa de usura vigente?',
    contexto: 'La tasa del Excel es 87,32 % efectivo anual. Hay que confirmar que cabe en la usura de la modalidad.',
    excelHoy: 'No lo dice.',
    opciones: [
      { codigo: 'consumo', texto: 'Consumo y ordinario', pideValor: true },
      { codigo: 'bajo-monto', texto: 'Consumo de bajo monto', pideValor: true },
      { codigo: 'microcredito', texto: 'Microcrédito', pideValor: true },
      { codigo: 'otra', texto: 'Otra', pideTexto: true },
    ],
    valor: { etiqueta: 'Tasa de usura vigente', unidad: '%', ajuste: 'usuraEA' },
  },
  {
    id: 'T4',
    bloque: TASA,
    titulo: 'Si la usura vuelve a subir, ¿el crédito regresa a la tasa pactada?',
    contexto: 'Después de un ajuste por usura, la tasa quedó por debajo de la pactada.',
    excelHoy: 'No lo define.',
    opciones: [
      { codigo: 'regresa', texto: 'Sí, hasta la tasa pactada' },
      { codigo: 'se-queda', texto: 'No, se queda en la tasa ajustada' },
    ],
  },
  {
    id: 'T5',
    bloque: TASA,
    titulo: 'Si el cliente está en mora cuando cambia la tasa, ¿sobre qué saldo se recalcula la cuota?',
    contexto:
      'El Excel solo modela el cambio de tasa con el cliente al día. Usted indicó que hay que investigar las cláusulas aceleratorias: cuando se sepa qué dicen, ¿la cuota se recalcula sobre todo el saldo o solo sobre el capital vigente?',
    excelHoy: 'No lo define.',
    opciones: [
      { codigo: 'saldo-total', texto: 'Sobre el saldo total (vigente + vencido)' },
      { codigo: 'saldo-vigente', texto: 'Solo sobre el capital vigente; lo vencido se cobra aparte' },
    ],
  },

  // ── Dación y retoma ───────────────────────────────────────────────────────────
  {
    id: 'R1',
    bloque: RECUPERACION,
    titulo: '¿Qué diferencia hay entre dación en pago y retoma?',
    contexto: 'En el Excel las dos hojas tienen las mismas fórmulas; solo cambia si la moto vale más o menos que la deuda.',
    excelHoy: 'Dación = faltante a cargo del FCC; retoma = excedente a favor del cliente.',
    opciones: [
      { codigo: 'solo-residuo', texto: 'Solo el signo del residuo, como el Excel', esExcel: true },
      { codigo: 'tramite', texto: 'Son trámites distintos (la dación la ofrece el cliente; la retoma es forzosa)', pideTexto: true },
      { codigo: 'otra', texto: 'Otra diferencia', pideTexto: true },
    ],
  },
  {
    id: 'R2',
    bloque: RECUPERACION,
    titulo: '¿Se descuentan costos de la recuperación (grúa, bodegaje, avalúo, trámites)?',
    contexto:
      'Hoy el valor de la moto se aplica completo a la deuda. Usted dijo que estos costos van dentro de la política de gastos de cobranza, que sigue pendiente: mientras tanto, ¿se descuentan o no del valor de la moto, y de cuánto?',
    excelHoy: 'No se descuenta nada.',
    opciones: [
      { codigo: 'no', texto: 'No', esExcel: true },
      { codigo: 'si', texto: 'Sí, un valor estimado por recuperación', pideValor: true },
    ],
    valor: { etiqueta: 'Costo estimado de una recuperación', unidad: 'COP', ajuste: 'costoRecuperacion' },
  },
  {
    id: 'R3',
    bloque: RECUPERACION,
    titulo: '¿La moto entra al inventario por el avalúo o por el valor de venta?',
    contexto: 'Si se vende por menos que el avalúo, cambia el faltante o el excedente.',
    excelHoy: 'Por el valor de la dación o el avalúo.',
    opciones: [
      { codigo: 'avaluo', texto: 'Por el avalúo; la diferencia en la venta se ajusta después', esExcel: true },
      { codigo: 'venta', texto: 'Por el valor de venta (el cierre espera la venta)' },
    ],
  },
  {
    id: 'R4',
    bloque: RECUPERACION,
    titulo: 'El interés de la semana del evento, ¿se cobra completo o por días?',
    contexto: 'El Excel suma el interés completo de la semana del siniestro a la deuda y cuenta esa semana como cuota vencida.',
    excelHoy: 'Completo.',
    opciones: [
      { codigo: 'completo', texto: 'Completo, como el Excel', esExcel: true },
      { codigo: 'por-dias', texto: 'Por los días hasta la fecha de entrega de la moto' },
    ],
  },

  // ── Contabilidad ──────────────────────────────────────────────────────────────
  {
    id: 'C1',
    bloque: CONTABILIDAD,
    titulo: '¿El interés corriente se causa cada semana o se registra cuando se recauda?',
    contexto: 'El caso 1 acredita "Ingresos por intereses" al recaudar; los casos 8 y 9 acreditan "CxC interés corriente", que nadie causó antes. Son dos modelos contables distintos.',
    excelHoy: 'Mezcla los dos modelos.',
    opciones: [
      { codigo: 'causacion-semanal', texto: 'Causación: cada semana se causa el interés (CxC contra ingreso) y el pago cancela la CxC' },
      { codigo: 'recaudo', texto: 'Recaudo: el interés se registra como ingreso cuando se paga' },
      { codigo: 'mixto', texto: 'Recaudo si se paga a tiempo; causación si queda pendiente' },
    ],
  },
  {
    id: 'C2',
    bloque: CONTABILIDAD,
    titulo: '¿Cuáles son los códigos del plan de cuentas (PUC) de cada cuenta?',
    contexto: 'Caja, cartera vigente y vencida, ingresos por intereses, mora y cobranza, CxC, CxP servicios a terceros, inventario de motos, CxC FCC y CxP deudor.',
    excelHoy: 'El Excel usa nombres, sin códigos.',
    opciones: [
      { codigo: 'adjunto', texto: 'Los envío por correo en un archivo aparte' },
      { codigo: 'texto', texto: 'Los escribo aquí', pideTexto: true },
    ],
  },
  {
    id: 'C3',
    bloque: CONTABILIDAD,
    titulo: '¿Quién recauda el pago inicial y cómo se registra?',
    contexto: 'Usted respondió que el pago inicial es opcional y lo digita el asesor. Falta definir quién lo recibe y cómo se contabiliza.',
    excelHoy: 'El Excel no tiene cuota inicial.',
    opciones: [
      { codigo: 'concesionario', texto: 'La recauda el concesionario y se descuenta del desembolso' },
      { codigo: 'financiera', texto: 'La recauda la financiera antes del desembolso' },
      { codigo: 'otra', texto: 'Otra forma', pideTexto: true },
    ],
  },
  {
    id: 'C5',
    bloque: CONTABILIDAD,
    titulo: 'El pago inicial se resta del total de la operación (moto + alistamiento): ¿el GPS y los gastos comerciales hacen parte del alistamiento?',
    contexto:
      'Usted dijo que "el total de la operación es moto + alistamiento". En su ejemplo la inicial se resta de 6.500.000 + 700.000. El valor total del Excel incluye además GPS y plataforma (80.000) y gastos comerciales (60.000). ¿Esos dos conceptos hacen parte del alistamiento, o quedan fuera del total sobre el que se resta el pago inicial? Hoy el motor la resta del valor total con todo incluido.',
    excelHoy: 'El Excel no tiene pago inicial.',
    opciones: [
      { codigo: 'valor-total', texto: 'GPS y gastos comerciales hacen parte del alistamiento: se resta del valor total (moto + alistamiento + GPS + gastos comerciales)' },
      { codigo: 'moto-alistamiento', texto: 'GPS y gastos comerciales quedan fuera: la inicial se resta solo de moto + alistamiento y ellos se financian siempre' },
      { codigo: 'otra', texto: 'Otra forma', pideTexto: true },
    ],
  },
  {
    id: 'C4',
    bloque: CONTABILIDAD,
    titulo: '¿Son correctas estas definiciones de la hoja Validaciones?',
    contexto: 'La hoja no trae fórmula para dos validaciones. Se tomaron así: "cuotas eliminadas" = 104 − última cuota pagada (caso 6) y "periodos adicionales" = última semana − 104 (caso 8).',
    excelHoy: 'Sin fórmula.',
    opciones: [
      { codigo: 'si', texto: 'Sí, son correctas' },
      { codigo: 'no', texto: 'No', pideTexto: true },
    ],
  },

  // ── Ajustes al Excel ──────────────────────────────────────────────────────────
  {
    id: 'X1',
    bloque: EXCEL,
    titulo: '¿Se corregirán estas inconsistencias en una nueva versión del Excel?',
    contexto:
      '1) La hoja Casos dice que los servicios solo se simulan en el caso 1, pero los casos 3, 4, 8 y 9 los tienen. 2) El estado final es "Amortizado" en los casos 1, 2 y 7 y "Cancelado" en el resto. 3) Inputs B3 se llama "Monto financiado" pero es el valor total. 4) En el caso 4 la CxC FCC es la constante 0 (en el caso 3 es fórmula). 5) En el caso 4 la retoma ocurre tras solo 3 semanas sin pago.',
    excelHoy: 'Como se describe.',
    opciones: [
      { codigo: 'si', texto: 'Sí, enviaré una versión corregida' },
      { codigo: 'algunas', texto: 'Algunas', pideTexto: true },
      { codigo: 'no', texto: 'No, se quedan como están' },
    ],
  },
  {
    id: 'X4',
    bloque: EXCEL,
    titulo: 'En el caso 8 el plazo límite sigue en 100 semanas, pero ahora el crédito tiene 104 cuotas. ¿Cuál debe ser el límite?',
    contexto:
      'Con el límite en 100 semanas (Inputs C65) la tabla se corta antes de la última cuota pactada: el crédito queda en mora con saldo y nunca hay periodos adicionales. En el caso 9 el límite es de 120 semanas.',
    excelHoy: 'Caso 8: límite de 100 semanas con 104 cuotas; el crédito termina en mora en la semana 100.',
    opciones: [
      { codigo: 'ampliar', texto: 'Ampliarlo por encima de las 104 cuotas (por ejemplo, 120 semanas como el caso 9)' },
      { codigo: 'dejar-100', texto: 'Dejarlo en 100: al llegar al límite el crédito pasa a dación o retoma' },
      { codigo: 'otra', texto: 'Otra regla', pideTexto: true },
    ],
  },
  {
    id: 'X3',
    bloque: EXCEL,
    titulo: 'En su respuesta sobre el plazo límite habla de los casos 4 o 5 como dación o retoma. ¿Se refiere a los casos 3 y 4?',
    contexto: 'En el Excel la dación en pago es el caso 3, la retoma el caso 4 y el prepago total el caso 5.',
    excelHoy: 'Caso 3 = dación, caso 4 = retoma, caso 5 = prepago total.',
    opciones: [
      { codigo: 'casos-3-y-4', texto: 'Sí, dación (caso 3) o retoma (caso 4)' },
      { codigo: 'otra', texto: 'Me refería a otra cosa', pideTexto: true },
    ],
  },
]

// ── Ronda 3 (20 de octubre de 2026): reglas que el motor necesita y siguen sin decidir ─────────
// Salen del inventario de docs/completitud-reglas.md. Son preguntas abiertas sin previsualización ni
// ajustes: lo que Diego conteste se anota y después se decide el cambio en el motor. Ids nuevos
// (COB, CAL, SRV, ABN, USU, REC, CON, ROL) para no chocar con los anteriores.
const RONDA_3: Pregunta[] = [
  // ── Pagos, mora y cobranza ────────────────────────────────────────────────────
  {
    id: 'COB-1',
    bloque: PAGOS,
    titulo: 'Política de gastos de cobranza (1 de 6): ¿cuándo se activa el cobro?',
    contexto:
      'Usted dijo que los gastos de cobranza van en una política que enviará. Para que el motor los pueda causar necesita saber desde qué momento corre el cobro. Si prefiere enviar la política completa, elija «Respondo en el comentario» y adjúntela por correo.',
    excelHoy: 'Se cobran desde el primer día de atraso de un pago recibido tarde (caso 9). En semanas sin pago no se causan.',
    opciones: [
      { codigo: 'primer-dia', texto: 'Desde el primer día de atraso', esExcel: true },
      { codigo: 'dias-gracia', texto: 'Después de unos días de gracia', detalle: 'Indique cuántos días.', pideTexto: true },
      { codigo: 'cartera-vencida', texto: 'Solo cuando la cuota pasa a cartera vencida (cuota no pagada en su periodo)' },
      { codigo: 'con-gestion', texto: 'Solo cuando hay una gestión de cobro registrada (llamada, carta, visita)' },
    ],
  },
  {
    id: 'COB-2',
    bloque: PAGOS,
    titulo: 'Política de gastos de cobranza (2 de 6): ¿se cobran por día o por evento?',
    contexto:
      'Define la fórmula. Por día crece con el atraso; por evento (por gestión realizada) no depende de cuánto tarde el cliente. En el caso 9, con 14 días de atraso en cada cuota, el Excel cobra 42.000 por pago.',
    excelHoy: '3.000 por cada día de atraso (días × 3.000), por pago.',
    opciones: [
      { codigo: 'por-dia', texto: 'Un valor fijo por cada día de atraso', detalle: 'Indique el valor (el 3.000 es un ejemplo).', pideTexto: true, esExcel: true },
      { codigo: 'por-evento', texto: 'Un valor fijo por cada gestión de cobro realizada', detalle: 'Indique el valor por gestión.', pideTexto: true },
      { codigo: 'porcentaje', texto: 'Un porcentaje de la cuota vencida o del saldo vencido', detalle: 'Indique el porcentaje y la base.', pideTexto: true },
      { codigo: 'por-tramos', texto: 'Una tabla por tramos de mora (1-30, 31-60, más de 60 días)', detalle: 'Indique el valor de cada tramo.', pideTexto: true },
    ],
  },
  {
    id: 'COB-3',
    bloque: PAGOS,
    titulo: 'Política de gastos de cobranza (3 de 6): ¿cuál es el tope y sobre qué se mide?',
    contexto:
      'Sin tope, un cliente que siempre paga con atraso acumula cobranza sin límite (en el caso 9 el crédito no termina). Aquí interesa la forma del tope; el valor lo puede escribir en el detalle.',
    excelHoy: 'Sin tope.',
    opciones: [
      { codigo: 'sin-tope', texto: 'Sin tope', esExcel: true },
      { codigo: 'tope-por-pago', texto: 'Un tope por pago', detalle: 'Indique el valor.', pideTexto: true },
      { codigo: 'tope-por-mes', texto: 'Un tope por mes', detalle: 'Indique el valor.', pideTexto: true },
      { codigo: 'tope-por-credito', texto: 'Un tope acumulado por crédito', detalle: 'Indique el valor.', pideTexto: true },
      { codigo: 'tope-porcentaje', texto: 'Un porcentaje del capital vencido', detalle: 'Indique el porcentaje.', pideTexto: true },
    ],
  },
  {
    id: 'COB-4',
    bloque: PAGOS,
    titulo: 'Política de gastos de cobranza (4 de 6): ¿se siguen cobrando después de la última cuota pactada?',
    contexto:
      'Si el crédito pasa de la cuota 104 con saldo en mora, hay periodos adicionales. ¿La cobranza sigue corriendo en esos periodos y hasta cuándo?',
    excelHoy: 'Sí: en el caso 9 se cobra hasta el plazo límite (120 semanas).',
    opciones: [
      { codigo: 'hasta-limite', texto: 'Sí, hasta el plazo límite de recuperación', esExcel: true },
      { codigo: 'hasta-ultima-cuota', texto: 'No: se cobra solo hasta la última cuota pactada' },
      { codigo: 'hasta-tope', texto: 'Sí, pero hasta alcanzar el tope acumulado de la pregunta COB-3' },
    ],
  },
  {
    id: 'COB-5',
    bloque: PAGOS,
    titulo: 'Política de gastos de cobranza (5 de 6): ¿quién autoriza que se exonere o se condone un cobro a un cliente?',
    contexto:
      'El valor y el tope los configura solo un administrador (D25). Esta pregunta es aparte: si un cliente concreto pide que no se le cobre, ¿quién puede decidirlo?',
    excelHoy: 'No lo define.',
    opciones: [
      { codigo: 'nadie', texto: 'Nadie: se cobra siempre lo que dice la política' },
      { codigo: 'asesor-con-tope', texto: 'El asesor, hasta un valor máximo', detalle: 'Indique el valor.', pideTexto: true },
      { codigo: 'gerente', texto: 'Solo el gerente, dejando constancia del motivo' },
      { codigo: 'comite', texto: 'Un comité de cartera' },
    ],
  },
  {
    id: 'COB-6',
    bloque: PAGOS,
    titulo: 'Política de gastos de cobranza (6 de 6): ¿el cobro de 3.000 por día es legal en el contrato?',
    contexto:
      'En el documento del equipo quedó en blanco si este cobro está permitido (P-18). El motor puede dejarlo activo, limitado a lo que diga la norma o bloqueado hasta tener el concepto.',
    excelHoy: 'Se cobra sin validación legal.',
    opciones: [
      { codigo: 'legal-confirmado', texto: 'Está permitido: lo respalda el contrato y el concepto jurídico' },
      { codigo: 'limitar-a-norma', texto: 'Se limita al máximo que diga la norma o el jurídico' },
      { codigo: 'bloquear-hasta-concepto', texto: 'Queda desactivado hasta tener el concepto jurídico' },
    ],
  },
  {
    id: 'CAL-1',
    bloque: PAGOS,
    titulo: 'Vencimiento los martes (1 de 3): ¿hasta qué momento del martes se paga a tiempo?',
    contexto:
      'Usted dijo que las cuotas vencen los martes. El motor necesita saber cuándo empieza el atraso: un pago el martes a las 11 p. m., ¿es a tiempo? ¿y el miércoles?',
    excelHoy: 'El Excel no tiene fechas: los días de atraso se digitan.',
    opciones: [
      { codigo: 'martes-fin-de-dia', texto: 'Es a tiempo hasta el fin del martes; el atraso empieza el miércoles (día 1)' },
      { codigo: 'martes-hora-corte', texto: 'Es a tiempo hasta una hora de corte del martes', detalle: 'Indique la hora de corte.', pideTexto: true },
      { codigo: 'dias-de-gracia', texto: 'Hay unos días de gracia sin mora ni cobranza después del martes', detalle: 'Indique cuántos días.', pideTexto: true },
    ],
  },
  {
    id: 'CAL-2',
    bloque: PAGOS,
    titulo: 'Vencimiento los martes (2 de 3): si el martes es festivo, ¿cuándo vence la cuota?',
    contexto: 'Dijo que por ahora no hay manejo de festivos. Confirmar qué hace el motor el día que cae festivo.',
    excelHoy: 'Sin festivos.',
    opciones: [
      { codigo: 'sin-festivos', texto: 'Sin manejo de festivos: vence el martes aunque sea festivo', esExcel: true },
      { codigo: 'siguiente-habil', texto: 'Vence el siguiente día hábil, sin mora por ese día' },
      { codigo: 'anterior-habil', texto: 'Vence el día hábil anterior' },
    ],
  },
  {
    id: 'CAL-3',
    bloque: PAGOS,
    titulo: 'Vencimiento los martes (3 de 3): ¿cuándo vence la primera cuota y el martes es igual para todos los créditos?',
    contexto:
      'Para armar el plan de pagos con fechas hace falta saber cuál es la primera cuota y si el día de pago lo escoge el cliente o es fijo.',
    excelHoy: 'El Excel cuenta periodos, no fechas.',
    opciones: [
      { codigo: 'primer-martes-tras-desembolso', texto: 'El primer martes después del desembolso' },
      { codigo: 'primer-martes-7-dias', texto: 'El primer martes que quede al menos 7 días después del desembolso' },
      { codigo: 'martes-fijo-fecha-pactada', texto: 'El martes es fijo para todos, pero la fecha de la primera cuota se pacta en la oferta' },
      { codigo: 'dia-por-cliente', texto: 'El día de la semana lo escoge cada cliente al firmar' },
    ],
  },

  // ── Servicios complementarios ─────────────────────────────────────────────────
  {
    id: 'SRV-1',
    bloque: SERVICIOS,
    titulo: 'Si el crédito termina a mitad de mes por un prepago, ¿se giran los servicios del mes completo al tercero?',
    contexto:
      'Los servicios se pactan por mes y se cobran por periodo. Cuando el crédito se cancela antes, hay que decidir si se cobra lo causado hasta el día o el mes entero, y si el cliente recupera algo.',
    excelHoy: 'Se cobran los periodos causados hasta la cuota del prepago (caso 5); no hay devolución.',
    opciones: [
      { codigo: 'periodos-causados', texto: 'Solo los periodos causados hasta el prepago, sin devolución', esExcel: true },
      { codigo: 'mes-completo', texto: 'El mes completo de cada servicio' },
      { codigo: 'proporcional-a-dias', texto: 'Proporcional a los días del mes transcurridos' },
      { codigo: 'devolver-anticipado', texto: 'Lo causado hasta el prepago y se devuelve al cliente lo pagado por adelantado, si lo hay' },
    ],
  },
  {
    id: 'SRV-2',
    bloque: SERVICIOS,
    titulo: 'Si un abono acorta el plazo, ¿qué pasa con la cobertura del seguro y del fondo de garantías en las cuotas eliminadas?',
    contexto:
      'En el caso 6 el abono elimina 25 semanas y deja de cobrarse 1.575.854 de servicios. ¿La aseguradora y el fondo aceptan que la cobertura termine antes, o hay que cobrar o devolver algo?',
    excelHoy: 'Se dejan de cobrar los servicios de las cuotas eliminadas.',
    opciones: [
      { codigo: 'termina-con-el-plazo', texto: 'La cobertura termina con el nuevo plazo y no se cobra más', esExcel: true },
      { codigo: 'cobertura-hasta-fecha-original', texto: 'La cobertura sigue hasta la fecha original y la financiera asume el costo' },
      { codigo: 'cobrar-hasta-fecha-original', texto: 'Se siguen cobrando los servicios hasta la fecha original' },
    ],
  },
  {
    id: 'SRV-3',
    bloque: SERVICIOS,
    titulo: 'En la última cuota, ¿se cobran los servicios completos del periodo o se prorratean?',
    contexto:
      'En la última semana del caso 6 el cliente paga la cuota residual de 6.998,93 más los servicios completos de la semana (63.034,17): los servicios son más de nueve veces la cuota.',
    excelHoy: 'Servicios completos del periodo.',
    opciones: [
      { codigo: 'completos', texto: 'Servicios completos del periodo', esExcel: true },
      { codigo: 'prorrateados', texto: 'Prorrateados por los días que realmente cubre el crédito' },
      { codigo: 'sin-servicios-ultima', texto: 'No se cobran servicios en una cuota residual' },
    ],
  },
  {
    id: 'SRV-4',
    bloque: SERVICIOS,
    titulo: '¿La cuenta por pagar de servicios se separa por tercero y por IVA?',
    contexto:
      'Los servicios son ingreso recibido para terceros (2815). Para girarlos y declarar IVA hay que saber si se llevan en una sola cuenta o por tercero (fondo de garantías, asistencia, aseguradora) y si el IVA del 19 % en fondo y asistencia se registra aparte.',
    excelHoy: 'Una sola cuenta «Servicios complementarios» sin separar tercero ni IVA.',
    opciones: [
      { codigo: 'una-cuenta', texto: 'Una sola cuenta para todos los servicios', esExcel: true },
      { codigo: 'auxiliar-por-tercero', texto: 'Una cuenta con auxiliar por tercero (fondo, asistencia, aseguradora)' },
      { codigo: 'cuenta-por-tercero-e-iva', texto: 'Una cuenta distinta por tercero y el IVA generado en su propia cuenta' },
    ],
  },

  // ── Abonos y prepago ──────────────────────────────────────────────────────────
  {
    id: 'ABN-1',
    bloque: ABONOS,
    titulo: 'Caso de validación: abono extra con pendientes. ¿Qué resultado espera?',
    contexto:
      'Ninguno de los 12 casos del Excel tiene un abono extra con pendientes, así que no hay con qué validar la regla A4. Propuesta del equipo (caso 13): crédito de 104 cuotas con el pago inferior del caso 8 en la semana 43; en la semana 47 el cliente aún debe capital vencido, mora y servicios y hace un abono extra de 2.000.000 con modalidad menor plazo. Esperamos que primero se paguen cobranza, mora, interés, fondo, asistencia y seguro causados (sin cobrar adelantado), el excedente baje el capital y se recalcule el plazo con la misma tasa. Necesitamos los valores esperados por usted para comparar.',
    excelHoy: 'No hay caso: el abono extra solo se prueba con el cliente al día (casos 6 y 7).',
    opciones: [
      { codigo: 'caso-propuesto-ok', texto: 'El caso propuesto sirve: el equipo lo arma y yo valido los valores' },
      { codigo: 'enviare-caso-resuelto', texto: 'Enviaré el caso resuelto en el Excel corregido' },
      { codigo: 'otro-caso', texto: 'El caso debe ser otro', detalle: 'Describa el escenario en el detalle.', pideTexto: true },
    ],
  },
  {
    id: 'ABN-2',
    bloque: ABONOS,
    titulo: 'Con capital vencido, ¿el excedente del abono cubre primero el capital vencido y luego el vigente?',
    contexto:
      'Después de pagar los pendientes por prelación, el resto va a capital. Hay dos capitales: el vencido (cuotas atrasadas) y el vigente. De esa elección depende cuánta mora sigue corriendo y qué se recalcula.',
    excelHoy: 'No lo define: aplica el abono al saldo total y recalcula sobre el saldo incluido el vencido.',
    opciones: [
      { codigo: 'vencido-primero', texto: 'Primero el capital vencido (el más antiguo) y después el vigente' },
      { codigo: 'vigente-primero', texto: 'Directo al capital vigente; el vencido sigue vencido hasta pagarse' },
      { codigo: 'solo-si-al-dia', texto: 'El abono extra solo se acepta si el cliente queda sin capital vencido' },
    ],
  },
  {
    id: 'ABN-3',
    bloque: ABONOS,
    titulo: '¿Hay un máximo de abonos extra por mes o por crédito?',
    contexto: 'Cada abono recalcula el plan. Un límite evita recálculos constantes y simplifica el historial.',
    excelHoy: 'No lo define.',
    opciones: [
      { codigo: 'sin-limite', texto: 'Sin límite' },
      { codigo: 'uno-por-mes', texto: 'Uno por mes' },
      { codigo: 'maximo-por-credito', texto: 'Un máximo por crédito', detalle: 'Indique cuántos.', pideTexto: true },
    ],
  },
  {
    id: 'ABN-4',
    bloque: ABONOS,
    titulo: 'Un abono extra recibido entre cuotas, ¿se aplica de una vez o junto con la siguiente cuota?',
    contexto:
      'El CRM puede recibir un abono un jueves, lejos del martes de vencimiento. Aplicarlo de una vez exige cobrar interés por los días; esperar a la cuota lo deja en un saldo transitorio.',
    excelHoy: 'El abono extra siempre va después de la cuota ordinaria de la misma semana.',
    opciones: [
      { codigo: 'despues-de-la-cuota', texto: 'Se guarda y se aplica después de la siguiente cuota ordinaria', esExcel: true },
      { codigo: 'de-una-vez-con-dias', texto: 'Se aplica de una vez, cobrando el interés de los días transcurridos' },
      { codigo: 'de-una-vez-sin-dias', texto: 'Se aplica de una vez y el interés se ajusta en la siguiente cuota' },
    ],
  },
  {
    id: 'ABN-5',
    bloque: ABONOS,
    titulo: 'Con varios abonos a menor cuota, ¿cada uno recalcula la cuota hasta la cuota pactada?',
    contexto: 'Si cada abono recalcula, la cuota baja varias veces durante la vida del crédito; el historial debe guardar cada versión.',
    excelHoy: 'El caso 7 tiene un solo abono; el motor supone que cada uno recalcula.',
    opciones: [
      { codigo: 'cada-abono', texto: 'Sí, cada abono recalcula la cuota sobre las cuotas restantes' },
      { codigo: 'acumulado-mensual', texto: 'Los abonos de un mes se acumulan y se recalcula una vez al cierre del mes' },
    ],
  },
  {
    id: 'ABN-6',
    bloque: ABONOS,
    titulo: 'En un prepago total, ¿hay algún costo y cuándo se entrega el paz y salvo?',
    contexto:
      'La Ley 1555 de 2012 impide cobrar penalidad por prepago en los casos que cubre. Falta la regla operativa: costo del trámite, plazo del paz y salvo y liberación de la prenda.',
    excelHoy: 'Sin costo; no modela paz y salvo ni prenda.',
    opciones: [
      { codigo: 'sin-costo-inmediato', texto: 'Sin costo; paz y salvo y liberación de la prenda el mismo día del pago' },
      { codigo: 'sin-costo-con-plazo', texto: 'Sin costo; paz y salvo y liberación de la prenda dentro de un plazo', detalle: 'Indique el plazo en días.', pideTexto: true },
      { codigo: 'con-costo-tramite', texto: 'Con un costo por el trámite del paz y salvo', detalle: 'Indique el valor.', pideTexto: true },
    ],
  },

  // ── Cambio de tasa y usura ────────────────────────────────────────────────────
  {
    id: 'USU-1',
    bloque: TASA,
    titulo: 'Usura: ¿confirma que el cambio rige desde la cuota siguiente a la publicación, no desde la primera cuota del mes?',
    contexto:
      'Usted respondió que el cambio aplica desde la cuota siguiente (T1). El Excel del caso 2 lo aplica desde la primera cuota del mes (cuota 44), así que las cifras del motor y las del Excel no coincidirán en las cuotas intermedias. Necesitamos saber cuál vale.',
    excelHoy: 'Desde la primera cuota del mes en que cambia la tasa (cuota 44 en el caso 2).',
    opciones: [
      { codigo: 'cuota-siguiente', texto: 'Desde la cuota siguiente a la publicación (el motor se queda así y el Excel se corrige)' },
      { codigo: 'primera-cuota-del-mes', texto: 'Desde la primera cuota del mes, como el Excel', esExcel: true },
      { codigo: 'desde-fecha-prorrateada', texto: 'Desde la fecha de vigencia de la usura, prorrateando el interés de esa cuota' },
    ],
  },
  {
    id: 'USU-2',
    bloque: TASA,
    titulo: 'Alerta mensual de usura (1 de 3): ¿quién es el autorizador que actualiza la usura?',
    contexto:
      'Usted dijo que la alerta mensual para actualizar la usura es obligatoria y la hace un autorizador. Hay que definir quién es y si necesita un segundo firmante.',
    excelHoy: 'La usura es un dato digitado a mano en el escenario.',
    opciones: [
      { codigo: 'gerente', texto: 'El gerente (dueño de producto)' },
      { codigo: 'riesgos-financiera', texto: 'La persona de riesgos o de la dirección financiera' },
      { codigo: 'administrador-del-sistema', texto: 'El administrador del sistema' },
      { codigo: 'dos-firmantes', texto: 'Dos personas: una la carga y otra la aprueba' },
    ],
  },
  {
    id: 'USU-3',
    bloque: TASA,
    titulo: 'Alerta mensual de usura (2 de 3): si nadie autoriza a tiempo, ¿qué hace el sistema?',
    contexto:
      'Si la usura baja y no se actualiza, los créditos seguirían con una tasa por encima del máximo legal. Hay que definir qué pasa a falta de autorizador (y, en el detalle, hasta qué día del mes vence el plazo).',
    excelHoy: 'No lo define.',
    opciones: [
      { codigo: 'seguir-con-la-anterior', texto: 'Sigue con la usura anterior y repite la alerta cada día hasta que se autorice' },
      { codigo: 'bloquear-desembolsos', texto: 'Sigue con la anterior, pero se bloquean los desembolsos nuevos hasta autorizar' },
      { codigo: 'aplicar-publicada', texto: 'Aplica automáticamente la usura publicada y avisa al autorizador para que la revise' },
    ],
  },
  {
    id: 'USU-4',
    bloque: TASA,
    titulo: 'Alerta mensual de usura (3 de 3): ¿qué usura aplica a cada línea de crédito?',
    contexto:
      'La Superfinanciera publica varias tasas máximas: consumo ordinario, consumo de bajo monto, microcrédito. El catálogo por línea debe decir cuál usa cada una (la línea CPPU está en 87,62 % E.A.).',
    excelHoy: 'Una sola usura para todas las líneas.',
    opciones: [
      { codigo: 'consumo-ordinario', texto: 'Todas las líneas con la usura de consumo y ordinario' },
      { codigo: 'bajo-monto-microcredito', texto: 'Todas con la de consumo de bajo monto o microcrédito' },
      { codigo: 'por-linea', texto: 'Una modalidad distinta por línea de crédito', detalle: 'Indique cuál usa cada línea.', pideTexto: true },
    ],
  },
  {
    id: 'USU-5',
    bloque: TASA,
    titulo: '¿Cuando la usura baja se recalculan todos los créditos vigentes o solo los nuevos?',
    contexto:
      'Usted dijo que si la usura baja la tasa sigue bajando con el margen. El documento del equipo dejó en blanco si eso alcanza a todos los créditos ya desembolsados (P-19).',
    excelHoy: 'El caso 2 ajusta un solo crédito en un evento digitado.',
    opciones: [
      { codigo: 'todos-automatico', texto: 'Todos los vigentes, automáticamente en la cuota siguiente' },
      { codigo: 'todos-con-aprobacion', texto: 'Todos los vigentes, después de que el autorizador apruebe el lote' },
      { codigo: 'solo-nuevos', texto: 'Solo los créditos nuevos' },
    ],
  },
  {
    id: 'USU-6',
    bloque: TASA,
    titulo: 'Cambio de tasa en sus dos modalidades: ¿cuál se aplica cuando la tasa baja?',
    contexto:
      'Usted dijo que al cambiar la tasa se recalcula con igual plazo y menor cuota, o con igual cuota y menor plazo. El motor ya tiene las dos (casos 2 y 10); falta saber cuál se usa y quién decide.',
    excelHoy: 'Caso 2: igual plazo, menor cuota. Caso 10: misma cuota, menor plazo.',
    opciones: [
      { codigo: 'siempre-menor-cuota', texto: 'Siempre igual plazo y menor cuota' },
      { codigo: 'siempre-menor-plazo', texto: 'Siempre misma cuota y menor plazo' },
      { codigo: 'cliente-elige', texto: 'El cliente elige; si no responde, igual plazo y menor cuota' },
      { codigo: 'segun-causa', texto: 'Depende de la causa: por usura una modalidad y por otra causa externa la otra', detalle: 'Indique cuál para cada causa.', pideTexto: true },
    ],
  },
  {
    id: 'USU-7',
    bloque: TASA,
    titulo: '¿Qué otras causas externas, además de la usura, pueden cambiar la tasa de un crédito?',
    contexto:
      'Usted dijo que la tasa solo cambia por causas externas. El motor hoy solo conoce el cambio de la hoja del caso 2 y el ajuste por usura.',
    excelHoy: 'Solo el evento de cambio de tasa del caso 2.',
    opciones: [
      { codigo: 'solo-usura', texto: 'Solo la usura' },
      { codigo: 'usura-y-regulacion', texto: 'La usura y cambios de ley o regulación' },
      { codigo: 'usura-e-indice', texto: 'La usura y una tasa de referencia (por ejemplo, DTF o IBR) en líneas futuras' },
    ],
  },
  {
    id: 'USU-8',
    bloque: TASA,
    titulo: 'Tras un cambio de tasa, ¿cambian el fondo de garantías, la asistencia y el seguro? ¿Se avisa al cliente?',
    contexto:
      'Los servicios se calculan sobre el monto financiado inicial. Un cambio de tasa recalcula la cuota; ¿afecta también los servicios? Además, el cambio es una novedad sin asiento contable que conviene registrar.',
    excelHoy: 'Los servicios quedan constantes; no se registra la novedad.',
    opciones: [
      { codigo: 'servicios-constantes-con-aviso', texto: 'Los servicios no cambian y se registra una novedad con aviso al cliente de la nueva cuota' },
      { codigo: 'servicios-constantes-sin-aviso', texto: 'Los servicios no cambian y no se avisa al cliente', esExcel: true },
      { codigo: 'servicios-recalculados', texto: 'Los servicios se recalculan con el nuevo saldo y se avisa al cliente' },
    ],
  },

  // ── Dación y retoma ───────────────────────────────────────────────────────────
  {
    id: 'REC-1',
    bloque: RECUPERACION,
    titulo: 'Dación (1 de 3): ¿cuánto cubre el fondo de garantías del faltante?',
    contexto:
      'Usted dijo que en la dación el diferencial entre el avalúo y el saldo se cobra al fondo de garantías. Hace falta saber hasta cuánto responde el fondo.',
    excelHoy: 'El faltante completo va a «CxC fondo de garantías» sin límite.',
    opciones: [
      { codigo: 'faltante-completo', texto: 'El fondo cubre todo el faltante', esExcel: true },
      { codigo: 'tope-porcentaje-monto', texto: 'Hasta un porcentaje del monto financiado', detalle: 'Indique el porcentaje.', pideTexto: true },
      { codigo: 'lo-aportado-por-el-cliente', texto: 'Hasta lo que ese crédito ha aportado al fondo' },
      { codigo: 'tope-del-fondo-comun', texto: 'Hasta un tope fijado por el fondo común', detalle: 'Indique el tope.', pideTexto: true },
    ],
  },
  {
    id: 'REC-2',
    bloque: RECUPERACION,
    titulo: 'Dación (2 de 3): ¿qué conceptos cubre el fondo: solo capital e intereses, o también mora, servicios y cobranza?',
    contexto:
      'Al recibir la moto hay capital, intereses, mora, servicios y cobranza pendientes. El avalúo se reparte por la prelación; lo que no alcance es el faltante que se cobra al fondo.',
    excelHoy: 'Todo el faltante, sin distinguir conceptos.',
    opciones: [
      { codigo: 'capital-e-intereses', texto: 'Solo capital e intereses corrientes' },
      { codigo: 'capital-intereses-y-mora', texto: 'Capital, intereses corrientes y mora' },
      { codigo: 'todos-los-conceptos', texto: 'Todos: capital, intereses, mora, servicios y cobranza', esExcel: true },
    ],
  },
  {
    id: 'REC-3',
    bloque: RECUPERACION,
    titulo: 'Dación (3 de 3): si el fondo no alcanza, ¿qué pasa con lo que falta?',
    contexto:
      'Hoy el motor supone que el fondo siempre alcanza y cierra el crédito. Si no alcanza, el crédito no puede quedar cerrado sin decidir quién asume el resto.',
    excelHoy: 'El crédito se cierra siempre; no hay caso de fondo insuficiente.',
    opciones: [
      { codigo: 'cobro-al-cliente', texto: 'Queda como cuenta por cobrar al cliente y pasa a cobro jurídico' },
      { codigo: 'perdida-financiera', texto: 'La financiera lo asume como pérdida contra provisión y cierra el crédito' },
      { codigo: 'nuevo-acuerdo', texto: 'Se negocia un nuevo acuerdo de pago con el cliente por el saldo' },
    ],
  },
  {
    id: 'REC-4',
    bloque: RECUPERACION,
    titulo: 'Retoma (1 de 2): ¿cuándo se le devuelve al cliente el excedente?',
    contexto:
      'En la retoma el avalúo es mayor que el saldo y se debe devolver dinero (en el caso 4, 1.185.578). Hay que definir el momento: antes de vender la moto o después.',
    excelHoy: 'Se reconoce como cuenta por pagar al deudor; no dice cuándo se paga.',
    opciones: [
      { codigo: 'plazo-fijo-desde-retoma', texto: 'En un plazo fijo desde la retoma, sin esperar la venta', detalle: 'Indique el plazo en días.', pideTexto: true },
      { codigo: 'tras-la-venta', texto: 'Después de vender la moto, por el menor entre el avalúo y el precio de venta' },
      { codigo: 'compensar-primero', texto: 'Primero se compensa con cualquier otra obligación del cliente y se devuelve el resto' },
    ],
  },
  {
    id: 'REC-5',
    bloque: RECUPERACION,
    titulo: 'Retoma (2 de 2): ¿cómo se paga el excedente y de dónde sale el dinero?',
    contexto:
      'Define el asiento de la devolución y el flujo de caja: si sale de la caja de la financiera o de la venta de la moto, y si se pueden descontar costos (grúa, bodegaje, avalúo, trámites).',
    excelHoy: 'No lo define; los costos no se descuentan (ver R2).',
    opciones: [
      { codigo: 'transferencia-caja-propia', texto: 'Transferencia a la cuenta del cliente, con dinero de la caja de la financiera' },
      { codigo: 'transferencia-con-producto-de-venta', texto: 'Transferencia al cliente con el producto de la venta de la moto' },
      { codigo: 'transferencia-menos-costos', texto: 'Transferencia a la cuenta del cliente descontando los costos de la retoma con soporte' },
    ],
  },
  {
    id: 'REC-6',
    bloque: RECUPERACION,
    titulo: '¿Quién fija el valor de la dación o la retoma?',
    contexto:
      'El avalúo es la base de todo el cierre. Cambia si lo hace un perito externo, una tabla de referencia o un comité interno, y quién responde si luego la moto se vende por menos.',
    excelHoy: 'El valor viene como dato (avalúo) sin fuente.',
    opciones: [
      { codigo: 'perito-externo', texto: 'Un perito externo' },
      { codigo: 'tabla-referencia', texto: 'Una tabla de referencia de mercado, ajustada por estado de la moto' },
      { codigo: 'comite-interno', texto: 'Un comité interno de la financiera' },
    ],
  },
  {
    id: 'REC-7',
    bloque: RECUPERACION,
    titulo: 'Si el cliente nunca se pone al día y llega al plazo límite con saldo, ¿qué ocurre?',
    contexto:
      'En el caso 9 el crédito llega a la semana 120 debiendo 3,3 millones. Usted dijo que el crédito termina antes en dación o retoma; falta el criterio que fuerza ese cierre.',
    excelHoy: 'El crédito queda «En mora» al llegar al límite.',
    opciones: [
      { codigo: 'dacion-o-retoma-obligatoria', texto: 'Se recupera la moto por dación o retoma al llegar al plazo límite' },
      { codigo: 'cobro-juridico', texto: 'Pasa a cobro jurídico manteniendo la moto' },
      { codigo: 'castigo-de-cartera', texto: 'Se castiga la cartera contra provisión' },
    ],
  },
  {
    id: 'REC-8',
    bloque: RECUPERACION,
    titulo: 'La semana del siniestro o de la recuperación, ¿cuenta como cuota vencida?',
    contexto: 'En el caso 3 la dación es en la semana 43 y el Excel cuenta 4 cuotas vencidas (incluye esa semana). Si no cuenta, serían 3.',
    excelHoy: 'La semana del evento cuenta como vencida (4).',
    opciones: [
      { codigo: 'cuenta-como-vencida', texto: 'Sí, cuenta como vencida', esExcel: true },
      { codigo: 'hasta-semana-anterior', texto: 'No: se cuentan las cuotas vencidas hasta la semana anterior' },
    ],
  },

  // ── Contabilidad ──────────────────────────────────────────────────────────────
  {
    id: 'CON-1',
    bloque: CONTABILIDAD,
    titulo: 'Plan de cuentas (PUC): ¿cómo nos entrega los códigos de cada cuenta?',
    contexto:
      'Faltan los códigos de: caja y bancos, cartera vigente, cartera vencida, CxC interés corriente, CxC mora, CxC servicios, CxC cobranza, CxC fondo de garantías, ingresos recibidos para terceros (¿2815?), inventario de motos, cuenta por pagar al deudor, ingresos por intereses, por mora y por cobranza, e IVA generado. Puede adjuntar el PUC en un Excel por correo, escribirlo en el comentario o aceptar una propuesta del equipo para revisión de su contador.',
    excelHoy: 'Las cuentas se identifican por nombre, sin código.',
    opciones: [
      { codigo: 'adjunto-puc-por-correo', texto: 'Envío el PUC completo en un archivo adjunto por correo' },
      { codigo: 'codigos-en-comentario', texto: 'Escribo los códigos cuenta por cuenta en el comentario' },
      { codigo: 'propuesta-del-equipo', texto: 'Que el equipo proponga los códigos del PUC colombiano y los revise mi contador' },
    ],
  },
  {
    id: 'CON-2',
    bloque: CONTABILIDAD,
    titulo: '¿El PUC cambia según la línea de crédito?',
    contexto: 'El documento del equipo preguntó el plan de cuentas «por línea» (P-04). Define si hay un juego de cuentas único o uno por línea.',
    excelHoy: 'Un solo juego de cuentas.',
    opciones: [
      { codigo: 'igual-para-todas', texto: 'Igual para todas las líneas', esExcel: true },
      { codigo: 'auxiliar-por-linea', texto: 'Las mismas cuentas con un auxiliar por línea' },
      { codigo: 'cuentas-distintas-por-linea', texto: 'Cuentas distintas por línea' },
    ],
  },
  {
    id: 'CON-3',
    bloque: CONTABILIDAD,
    titulo: '¿Por cuáles canales llegan los pagos y cómo se sabe a qué crédito pertenecen?',
    contexto: 'Define cómo entra el abono al motor: el documento del equipo dejó en blanco el origen de los pagos (P-22).',
    excelHoy: 'Los pagos son instrucciones por semana, sin canal ni conciliación.',
    opciones: [
      { codigo: 'extracto-bancario-referencia', texto: 'Del extracto bancario, identificando el crédito por la referencia de pago' },
      { codigo: 'crm-clasifica', texto: 'El CRM entrega cada pago ya asociado al crédito' },
      { codigo: 'varios-canales', texto: 'Varios canales (banco, corresponsal, efectivo) con conciliación diaria', detalle: 'Liste los canales.', pideTexto: true },
    ],
  },
  {
    id: 'CON-4',
    bloque: CONTABILIDAD,
    titulo: 'Los gastos de cobranza, ¿son ingreso de la financiera o se giran a un tercero?',
    contexto:
      'Si la cobranza la hace una firma externa, lo recaudado por ese concepto es de un tercero (cuenta por pagar) y no ingreso, con otro asiento y posiblemente IVA.',
    excelHoy: 'Se acredita a la cuenta de cobranza como ingreso propio.',
    opciones: [
      { codigo: 'ingreso-propio', texto: 'Ingreso propio de la financiera', esExcel: true },
      { codigo: 'cuenta-por-pagar-tercero', texto: 'Se gira a un tercero que hace la cobranza' },
      { codigo: 'mixto', texto: 'Una parte es ingreso y otra se gira al tercero', detalle: 'Indique la proporción.', pideTexto: true },
    ],
  },

  // ── Roles y parámetros ────────────────────────────────────────────────────────
  {
    id: 'ROL-1',
    bloque: ROLES,
    titulo: '¿Quién es el «administrador» que puede cambiar la tasa y los gastos de cobranza?',
    contexto:
      'Usted dijo que solo una persona (el administrador) puede cambiar esos parámetros. Para configurar el acceso hace falta definir el rol y si hay suplente.',
    excelHoy: 'No hay roles: los parámetros son celdas de la hoja Inputs.',
    opciones: [
      { codigo: 'una-persona', texto: 'Una sola persona, sin suplente' },
      { codigo: 'una-persona-y-suplente', texto: 'Una persona con un suplente para ausencias' },
      { codigo: 'cargo-con-dos-personas', texto: 'Un cargo que ocupan dos personas con el mismo permiso' },
    ],
  },
  {
    id: 'ROL-2',
    bloque: ROLES,
    titulo: '¿Qué parámetros son sensibles y solo toca el administrador?',
    contexto:
      'Usted ya dijo dos: la tasa y los gastos de cobranza. Podrían serlo también el margen de usura, las tarifas de servicios y el plazo límite.',
    excelHoy: 'Cualquiera que abra el Excel puede cambiarlos.',
    opciones: [
      { codigo: 'tasa-y-cobranza', texto: 'Solo la tasa y los gastos de cobranza (valor, tope y activación)' },
      { codigo: 'tasa-cobranza-usura-servicios', texto: 'Tasa, cobranza, margen de usura y tarifas de los servicios' },
      { codigo: 'todos-los-del-producto', texto: 'Todos los parámetros del producto (incluidos plazo límite y periodicidades)' },
    ],
  },
  {
    id: 'ROL-3',
    bloque: ROLES,
    titulo: 'Cuando el administrador cambia un parámetro, ¿rige para los créditos vigentes o solo para los nuevos?',
    contexto:
      'Usted dijo que el crédito conserva sus condiciones salvo la tasa por causas externas. Falta decidir si un cambio de política de cobranza alcanza a los créditos ya firmados y desde cuándo.',
    excelHoy: 'No hay versionado de parámetros.',
    opciones: [
      { codigo: 'solo-nuevos', texto: 'Solo los créditos nuevos; los vigentes conservan lo firmado' },
      { codigo: 'vigentes-desde-fecha', texto: 'También los vigentes, desde una fecha de vigencia futura que fija el administrador' },
      { codigo: 'segun-parametro', texto: 'Depende del parámetro', detalle: 'Indique cuáles alcanzan a los vigentes.', pideTexto: true },
    ],
  },
  {
    id: 'ROL-4',
    bloque: ROLES,
    titulo: '¿Un cambio de parámetro sensible necesita segundo aprobador y queda en bitácora?',
    contexto: 'Es el control sobre el administrador: quién cambió qué y cuándo, y si otra persona debe aprobarlo antes de que rija.',
    excelHoy: 'Sin control ni historial de cambios.',
    opciones: [
      { codigo: 'bitacora-sola', texto: 'Cambio inmediato del administrador, con bitácora de quién, qué y cuándo' },
      { codigo: 'segundo-aprobador', texto: 'El cambio rige solo cuando un segundo aprobador lo confirma, con bitácora' },
    ],
  },
  {
    id: 'ROL-5',
    bloque: ROLES,
    titulo: 'Después del simulador y el motor de abonos, ¿cuál es el siguiente alcance?',
    contexto:
      'El documento del equipo dejó en blanco el alcance posterior al simulador (P-27). Define el orden de los próximos módulos: desembolso y plan con fechas, cierre diario y contabilidad, e integración con el CRM.',
    excelHoy: 'No aplica.',
    opciones: [
      { codigo: 'plan-con-fechas-y-desembolso', texto: 'Primero el desembolso y el plan de pagos con fechas' },
      { codigo: 'cierre-diario-contabilidad', texto: 'Primero el cierre diario y la contabilidad' },
      { codigo: 'integracion-crm', texto: 'Primero la integración con el CRM y el registro de abonos' },
    ],
  },
]

// Ronda 4: confirmaciones cortas de lo que la ronda 3 dejó contradictorio, incompleto o sin repetir.
const RONDA_4: Pregunta[] = [
  // ── Pagos, mora y cobranza ────────────────────────────────────────────────────
  {
    id: 'R4-QUIN',
    bloque: PAGOS,
    titulo: 'Escribió 104 periodos al año para la quincena. Una quincena son 24 periodos al año (2 por mes). ¿Confirma 24?',
    contexto:
      'En el detalle de PPER escribió "quincena se toman 104 periodos año". Con 104 periodos habría un pago cada 3,5 días, que no es una quincena; el 104 es el número de cuotas semanales del caso 8. El motor ya lo implementa con 24 (tasa del periodo = (1+EA)^(1/24) − 1).',
    excelHoy: 'El Excel solo modela cuotas semanales (52 periodos al año).',
    opciones: [
      { codigo: 'si-24', texto: 'Sí, 24' },
      { codigo: 'otro-valor', texto: 'No, otro valor', detalle: 'Indique el valor y por qué.', pideTexto: true },
    ],
  },
  {
    id: 'R4-MENSUAL-DIA',
    bloque: PAGOS,
    titulo: 'Para la cuota mensual escribió "el 5 o el primer día del mes". ¿Es una fecha fija para todos o se elige por crédito?',
    contexto: 'Para la quincena ya quedó el 5 y el 20. Falta saber si el motor usa un solo día mensual para todos los créditos o lo guarda en cada uno.',
    excelHoy: 'El Excel solo modela cuotas semanales.',
    opciones: [
      { codigo: 'dia-5-todos', texto: 'Fija para todos: el día 5 de cada mes' },
      { codigo: 'dia-1-todos', texto: 'Fija para todos: el primer día de cada mes' },
      { codigo: 'por-credito', texto: 'Se elige por crédito (1 o 5) y se pacta en la oferta' },
    ],
  },

  // ── Abonos y prepago ──────────────────────────────────────────────────────────
  {
    id: 'R4-A3',
    bloque: ABONOS,
    titulo: '¿Se acepta cualquier abono extra o hay un valor mínimo?',
    contexto:
      'Esta pregunta (antes A3) quedó igual: su respuesta del 20 de octubre habló solo del orden de la prelación y no dijo si hay un mínimo, así que no alcanzó para decidir. Basta responder: sin mínimo, o el valor mínimo en pesos.',
    excelHoy: 'No lo define.',
    opciones: [
      { codigo: 'sin-minimo', texto: 'Sin mínimo: se acepta cualquier valor', ajustes: { abonoMinimo: '0' } },
      { codigo: 'con-minimo', texto: 'Con mínimo', pideValor: true },
    ],
    valor: { etiqueta: 'Abono extra mínimo', unidad: 'COP', ajuste: 'abonoMinimo', sugeridos: [{ valor: 50000 }, { valor: 100000 }, { valor: 174049 }] },
  },

  // ── Cambio de tasa y usura ────────────────────────────────────────────────────
  {
    id: 'R4-T5',
    bloque: TASA,
    titulo: 'Si el cliente está en mora cuando cambia la tasa, ¿sobre qué saldo se recalcula la cuota?',
    contexto:
      'Esta pregunta (antes T5) quedó igual: su respuesta fue "investigar las cláusulas aceleratorias", lo que no dice qué saldo usar mientras tanto. Proponemos tres reglas concretas para que escoja una; si luego la cláusula dice otra cosa, se ajusta.',
    excelHoy: 'El Excel solo modela el cambio de tasa con el cliente al día.',
    opciones: [
      { codigo: 'saldo-vigente', texto: 'Solo sobre el capital vigente; lo vencido se cobra aparte' },
      { codigo: 'saldo-total', texto: 'Sobre el saldo total (vigente + vencido)' },
      { codigo: 'acelerar', texto: 'El crédito se acelera: se exige todo el saldo y no se recalcula la cuota' },
    ],
  },
  {
    id: 'R4-USURA-PLAZO',
    bloque: TASA,
    titulo: 'Si nadie autoriza la nueva usura, ¿a los cuántos días hábiles de su publicación se aplica sola? ¿Vale lo mismo para el lote de créditos vigentes?',
    contexto:
      'USU-3 dijo que si nadie autoriza la usura se aplica sola, pero USU-5 dijo que el recálculo de los vigentes espera la aprobación del lote. Para que no se contradigan, proponemos que se aplique sola al vencer un plazo de aprobación. Falta el plazo y si ese plazo cubre también el lote.',
    excelHoy: 'No hay alerta ni plazo: la tasa la digita quien arma el caso.',
    opciones: [
      { codigo: 'sola-plazo-y-lote', texto: 'Se aplica sola al vencer el plazo, también para el lote de vigentes', pideValor: true },
      { codigo: 'sola-plazo-lote-espera', texto: 'Sola al vencer el plazo para los nuevos; el lote de vigentes siempre espera la aprobación', pideValor: true },
    ],
    valor: { etiqueta: 'Días hábiles desde la publicación', unidad: 'días', sugeridos: [{ valor: 1 }, { valor: 3 }, { valor: 5 }] },
  },
  {
    id: 'R4-AVISO-CLIENTE',
    bloque: TASA,
    titulo: 'Dijo que no se avisa al cliente cuando cambia la tasa. ¿Lo confirma o lo valida antes con legal?',
    contexto:
      'Los servicios (fondo, asistencia, seguro) no cambian con la tasa: eso ya quedó. Lo que preocupa es el aviso: la protección al consumidor financiero suele exigir informar los cambios de condiciones del crédito, y aquí cambia la cuota o el plazo.',
    excelHoy: 'No lo define.',
    opciones: [
      { codigo: 'no-se-avisa', texto: 'Confirmo: no se avisa al cliente' },
      { codigo: 'se-avisa', texto: 'Sí se avisa al cliente cuando cambia la tasa', detalle: 'Indique por qué medio y con cuánta anticipación.', pideTexto: true },
      { codigo: 'valida-legal', texto: 'Lo valido con legal y respondo después', detalle: 'Queda pendiente el concepto de legal.' },
    ],
  },

  // ── Dación y retoma ───────────────────────────────────────────────────────────
  {
    id: 'R4-R2',
    bloque: RECUPERACION,
    titulo: 'Al recuperar la moto, ¿se descuenta el costo real (grúa, bodegaje, avalúo, trámites) con su soporte?',
    contexto:
      'Esta pregunta (antes R2) quedó igual: respondió que aún no tiene definido el valor. No hace falta el valor, solo la regla: en la retoma (REC-5) ya dijo que se descuentan los costos con soporte. Falta saber si en la dación también y si hay un valor fijo mientras se define la política.',
    excelHoy: 'No se descuenta nada.',
    opciones: [
      { codigo: 'no', texto: 'No se descuenta nada', esExcel: true },
      { codigo: 'costo-real', texto: 'Sí, el costo real con soporte, tanto en dación como en retoma' },
      { codigo: 'si', texto: 'Sí, un valor estimado fijo por recuperación mientras se define la política', pideValor: true },
    ],
    valor: { etiqueta: 'Costo estimado de una recuperación', unidad: 'COP', ajuste: 'costoRecuperacion' },
  },
  {
    id: 'R4-PERITO',
    bloque: RECUPERACION,
    titulo: 'El valor de la dación o la retoma es la guía Fasecolda × 65 %. ¿Cuándo se usa el perito externo?',
    contexto: 'Dijo que el perito es la segunda opción. Para programarlo hay que saber cuándo entra: solo cuando falta la referencia, también si el cliente objeta el valor, o en otros casos.',
    excelHoy: 'El avalúo es un dato de entrada del caso.',
    opciones: [
      { codigo: 'sin-referencia', texto: 'Solo si la moto no tiene referencia en la guía Fasecolda' },
      { codigo: 'sin-referencia-u-objecion', texto: 'Si no hay referencia Fasecolda o el cliente objeta el valor' },
      { codigo: 'otros-casos', texto: 'En otros casos', detalle: 'Indique cuáles.', pideTexto: true },
    ],
  },

  // ── Contabilidad ──────────────────────────────────────────────────────────────
  {
    id: 'R4-C5',
    bloque: CONTABILIDAD,
    titulo: '¿El GPS y los gastos comerciales son parte del alistamiento? Sí o no.',
    contexto:
      'Esta pregunta (antes C5) quedó igual: marcó la opción "incluye GPS y gastos comerciales", pero su detalle decía "total = moto + alistamiento", que los deja fuera; las dos respuestas se contradicen. En su ejemplo la inicial se resta de 6.500.000 + 700.000; el valor total del Excel suma además GPS y plataforma (80.000) y gastos comerciales (60.000).',
    excelHoy: 'El Excel no tiene pago inicial.',
    opciones: [
      { codigo: 'valor-total', texto: 'Sí: la inicial se resta del valor total (moto + alistamiento + GPS + gastos comerciales)' },
      { codigo: 'moto-alistamiento', texto: 'No: se resta solo de moto + alistamiento y el GPS y los gastos comerciales se financian siempre' },
    ],
  },
  {
    id: 'R4-COBRANZA-CONTABLE',
    bloque: CONTABILIDAD,
    titulo: 'Los gastos de cobranza son una compensación de gastos reales, no un ingreso. ¿Cómo se registran?',
    contexto: 'Si no son ingreso, el asiento cambia: o la financiera paga el gasto y le cobra al cliente el reembolso (cuenta por cobrar), o recibe el dinero para terceros.',
    excelHoy: 'El Excel los trata como un concepto más del pago.',
    opciones: [
      { codigo: 'cxc-reembolsable', texto: 'Cuenta por cobrar reembolsable contra el gasto real' },
      { codigo: 'ingreso-para-terceros', texto: 'Ingreso recibido para terceros (cuenta por pagar)' },
      { codigo: 'otro-tratamiento', texto: 'Otro tratamiento', pideTexto: true },
    ],
  },

  // ── Roles y parámetros ────────────────────────────────────────────────────────
  {
    id: 'R4-SUPLENTE',
    bloque: ROLES,
    titulo: 'Hay un solo administrador y ningún suplente. ¿Qué pasa si no está?',
    contexto: 'Sin él nadie puede cambiar la tasa, la usura ni los gastos de cobranza, y la usura vence cada mes.',
    excelHoy: 'No aplica.',
    opciones: [
      { codigo: 'suplente', texto: 'Se nombra un suplente con los mismos permisos y misma bitácora' },
      { codigo: 'contingencia', texto: 'Procedimiento de contingencia: la dirección financiera designa a alguien por escrito mientras falte', pideTexto: true },
      { codigo: 'esperar', texto: 'Se espera a que vuelva; no se prevé reemplazo' },
    ],
  },

  {
    id: 'R4-PLAZO-LINEA',
    bloque: PAGOS,
    titulo: 'Dijo "máximo 24 meses" de plazo. ¿El tope de 24 vale para todas las líneas o cada línea tiene el suyo?',
    contexto: 'El catálogo de Inputs tiene la línea ORDINARIO con 36 meses. Si el tope es 24 para todas, esa línea se recorta; si no, el motor guarda el tope en cada línea.',
    excelHoy: 'El catálogo de Inputs trae el plazo de cada línea (ORDINARIO: 36 meses).',
    opciones: [
      { codigo: 'todas-24', texto: '24 meses para todas las líneas' },
      { codigo: 'por-linea', texto: 'Cada línea con su tope (ORDINARIO 36)', detalle: 'Si algún tope del catálogo cambia, indíquelo.', pideTexto: true },
    ],
  },
  {
    id: 'R4-PLAZO-LIMITE',
    bloque: PAGOS,
    titulo: 'El plazo límite de recuperación va en periodos. ¿Cuántos?',
    contexto: 'Ya quedó que se expresa en periodos de la periodicidad del crédito (PPER-6), pero falta el valor. Hoy el motor usa el equivalente a 120 semanas (casos 8 y 9: 100 y 120 semanas).',
    excelHoy: 'Plazo límite de 100 semanas en el caso 8 y 120 en el caso 9.',
    opciones: [
      { codigo: 'equivalente-120', texto: 'Mantener el equivalente a 120 semanas (≈ 27 meses)' },
      { codigo: 'meses', texto: 'Otro plazo, en meses', pideValor: true },
    ],
    valor: { etiqueta: 'Plazo límite', unidad: 'meses', sugeridos: [{ valor: 24 }, { valor: 27 }, { valor: 36 }] },
  },
  {
    id: 'R4-PAGO-SIN-CUOTA',
    bloque: ABONOS,
    titulo: 'Si un pago llega cuando todavía no hay cuota causada, ¿todo cuenta como abono extra?',
    contexto: 'Pasa antes de la primera cuota o justo después de pagar al día. Hoy el motor lo trata todo como abono extra (recalcula plazo o cuota); la otra lectura es guardarlo como saldo a favor para la próxima cuota.',
    excelHoy: 'No lo define.',
    opciones: [
      { codigo: 'todo-abono-extra', texto: 'Sí, todo es abono extra' },
      { codigo: 'saldo-a-favor', texto: 'Se guarda como saldo a favor para la próxima cuota' },
    ],
  },
  {
    id: 'R4-CASO13',
    bloque: ABONOS,
    titulo: 'Caso 13 (abono extra con pendientes): ¿los valores son correctos?',
    contexto:
      'Armado por el equipo con sus reglas. En la semana 42 el cliente tiene pendientes por 317.354,81 (cobranza, mora, interés, seguro y capital vencido) y trae un abono de 1.500.000: se pagan los pendientes y 1.182.645,20 van a capital vigente; el saldo queda en 3.169.909,34. Con menor plazo la cuota sigue en 100.406,27 y el crédito termina en la semana 83; con menor cuota baja a 73.124,59 y termina en la 104. El detalle semana a semana está en docs/reglas/caso-13.md.',
    excelHoy: 'No hay hoja en el Excel: lo arma el equipo.',
    opciones: [
      { codigo: 'correctos', texto: 'Los valores son correctos' },
      { codigo: 'diferencias', texto: 'Hay diferencias', detalle: 'Indique cuáles valores y cuáles esperaba.', pideTexto: true },
    ],
  },
  {
    id: 'R4-FONDO-TOPE',
    bloque: RECUPERACION,
    titulo: 'El fondo de garantías cubre todo el faltante de una dación. ¿Tiene un tope que el motor deba controlar?',
    contexto: 'Con REC-1 el fondo cubre el faltante completo. Si tuviera un tope (por crédito o global), el resto quedaría como cuenta por cobrar al cliente (REC-3).',
    excelHoy: 'El faltante de la dación se carga al fondo sin tope.',
    opciones: [
      { codigo: 'sin-tope', texto: 'Sin tope por crédito' },
      { codigo: 'tope-aportado', texto: 'Tope = lo aportado por el cliente al fondo' },
      { codigo: 'otro', texto: 'Otro tope', detalle: 'Indique cuál y si es por crédito o global.', pideTexto: true },
    ],
  },
]

// Ronda 5: confirmaciones cortas de lo que la ronda 4 dejó abierto o cambió.
// Cifras del caso 13 calculadas por el motor con aplicacionAbono=PRELACION_COMPLETA (docs/reglas/caso-13.md).
export const CIFRAS_CASO13 =
  'Ahora el motor paga lo vencido (317.354,81) y con el sobrante cobra también el fondo de garantías (48.643,17) y la asistencia (10.984,62) de la semana 42, que no se habían causado por estar en atraso: en total 376.982,59. ' +
  'A capital vigente van 1.123.017,41 y el saldo queda en 3.229.537,12. A menor cuota, la cuota desde la semana 43 es 74.500,11 (termina en la 104); a menor plazo, el crédito termina en la semana 84 (última cuota 69.406,12). ' +
  'Nunca se cobran conceptos de la semana 43 en adelante.'

const RONDA_5: Pregunta[] = [
  // ── Pagos, mora y cobranza ────────────────────────────────────────────────────
  {
    id: 'P5-PRORRATEO',
    bloque: PAGOS,
    titulo: 'La primera cuota mensual se prorratea entre el desembolso y el próximo día 5. ¿Qué se prorratea y en qué periodicidades?',
    contexto:
      'Dijo que la mensual vence el día 5 y que la primera cuota se prorratea según lo corrido entre el desembolso y el próximo 5. Falta saber qué parte de la cuota se prorratea. Por ejemplo, con el desembolso el día 20, la primera cuota cubre 15 días reales: el interés se calcula por esos días; la pregunta es si el capital y los servicios (fondo, asistencia, seguro) van completos o también proporcionales. Y si lo mismo vale para la quincenal (5 y 20) y la semanal (primer martes).',
    excelHoy: 'El Excel solo modela cuotas semanales con periodos completos; no prorratea la primera cuota.',
    opciones: [
      { codigo: 'solo-interes-todas', texto: 'Solo el interés, por los días reales; capital y servicios completos. En las tres periodicidades' },
      { codigo: 'solo-interes-mensual', texto: 'Solo el interés por días reales, y solo en la mensual', detalle: 'En la quincenal y la semanal la primera cuota es un periodo completo.' },
      { codigo: 'todo-proporcional', texto: 'Todo proporcional a los días: interés, capital y servicios', detalle: 'Indique en el comentario si vale también para la quincenal y la semanal.' },
      { codigo: 'otro', texto: 'Otra forma', pideTexto: true },
    ],
  },

  {
    id: 'P5-LIMITE-DESDE',
    bloque: PAGOS,
    titulo: 'El plazo límite de recuperación es de 24 meses. ¿Desde cuándo se cuenta?',
    contexto:
      'Para un crédito de 24 meses, contar desde el desembolso hace coincidir el límite con el fin del crédito; contarlo desde el primer atraso daría más tiempo. En un crédito de 12 meses hay que decidir si el límite sigue siendo de 24 meses o es el plazo del crédito.',
    excelHoy: 'El Excel fija el límite en semanas (100 en el caso 8), sin una fecha de partida.',
    opciones: [
      { codigo: 'desde-desembolso-24', texto: 'Desde el desembolso: 24 meses para cualquier plazo del crédito' },
      { codigo: 'desde-desembolso-plazo', texto: 'Desde el desembolso, y es el plazo pactado', detalle: 'Un crédito de 12 meses tiene límite de 12 meses.' },
      { codigo: 'desde-primer-atraso', texto: 'Desde el primer atraso: 24 meses' },
    ],
  },

  // ── Abonos y prepago ──────────────────────────────────────────────────────────
  {
    id: 'P5-PAGO-ANTES-PRIMERA',
    bloque: ABONOS,
    titulo: 'Si llega un pago antes de la primera cuota (cuando aún no hay cuota causada), ¿qué se hace con él?',
    contexto:
      'Dijo que solo antes de la primera cuota no hay cuota causada; después, un pago siempre cubre la cuota causada y lo que sobre es abono extra. Falta ese único caso: el pago que llega entre el desembolso y la primera cuota.',
    excelHoy: 'No lo define.',
    opciones: [
      { codigo: 'abono-extra', texto: 'Es abono extra a capital', detalle: 'Se aplica de una vez y el cliente elige menor plazo o menor cuota.' },
      { codigo: 'se-guarda', texto: 'Se guarda y se aplica a la primera cuota cuando se cause' },
    ],
  },
  {
    id: 'P5-CASO13',
    bloque: ABONOS,
    titulo: 'Caso 13 recalculado: el sobrante del abono pasa por la prelación. ¿Las cifras nuevas son correctas?',
    contexto:
      'Antes: en la semana 42 el cliente tenía pendientes por 317.354,81 y traía un abono de 1.500.000; se pagaban los pendientes y los 1.182.645,20 sobrantes iban directo a capital vigente (el saldo quedaba en 3.169.909,34). Ahora, con su respuesta de la ronda 4: se paga todo lo vencido como en el ejemplo y el sobrante de 1.182.645,20 vuelve a pasar por la prelación (cobranza, mora, interés corriente, fondo, asistencia, seguro, capital) sobre lo ya causado del periodo; solo el resto va a capital. Cifras nuevas: ' +
      CIFRAS_CASO13 +
      ' El detalle semana a semana está en docs/reglas/caso-13.md.',
    excelHoy: 'No hay hoja en el Excel: lo arma el equipo.',
    opciones: [
      { codigo: 'correctos', texto: 'Las cifras nuevas son correctas' },
      { codigo: 'diferencias', texto: 'Hay diferencias', detalle: 'Indique cuáles valores y cuáles esperaba.', pideTexto: true },
    ],
  },

  // ── Cambio de tasa y usura ────────────────────────────────────────────────────
  {
    id: 'P5-USURA-DIA',
    bloque: TASA,
    titulo: 'Si nadie autoriza la nueva usura, ¿confirma que se aplica sola a 1 día hábil de su publicación?',
    contexto:
      'En la ronda 4 el campo de días traía "1 días". Entendemos 1 día hábil después de la publicación de la usura, también para el lote de créditos vigentes. Basta confirmar o dar el número correcto.',
    excelHoy: 'No hay alerta ni plazo: la tasa la digita quien arma el caso.',
    opciones: [
      { codigo: 'un-dia-habil', texto: 'Sí, 1 día hábil' },
      { codigo: 'otro-plazo', texto: 'No, otro plazo', pideValor: true },
    ],
    valor: { etiqueta: 'Días hábiles desde la publicación', unidad: 'días', sugeridos: [{ valor: 2 }, { valor: 3 }, { valor: 5 }] },
  },

  // ── Dación y retoma ───────────────────────────────────────────────────────────
  {
    id: 'P5-COSTO-DACION',
    bloque: RECUPERACION,
    titulo: 'En la dación, el costo real de la recuperación (grúa, bodegaje, avalúo, trámites), ¿lo cubre el fondo de garantías o la financiera?',
    contexto:
      'Dijo que en dación y en retoma se descuenta el costo real con soporte. En la retoma sale del excedente del cliente, pero en la dación no hay excedente: el fondo de garantías cubre el faltante de la deuda. Falta saber si el costo se suma a ese faltante (lo paga el fondo) o lo asume la financiera.',
    excelHoy: 'El Excel no descuenta costos de recuperación.',
    opciones: [
      { codigo: 'suma-al-faltante', texto: 'Se suma al faltante: lo cubre el fondo de garantías' },
      { codigo: 'financiera', texto: 'Lo asume la financiera; el fondo cubre solo el faltante de la deuda' },
      { codigo: 'otro', texto: 'Otro tratamiento', pideTexto: true },
    ],
  },
]

// Ronda 6 (22 oct 2026): lo NO seguro de las respuestas por documento de Diego. Confirmaciones cortas con opciones concretas.
const RONDA_6: Pregunta[] = [
  // ── Pagos, mora y cobranza ────────────────────────────────────────────────────
  {
    id: 'R6-COBRANZA-ACTIVA',
    bloque: PAGOS,
    titulo: '¿Quién decide a qué créditos se les activa la cobranza, y se activa crédito por crédito o por regla?',
    contexto:
      'Dijo que los gastos de cobranza solo se cobran en mora, que el valor puede cambiar en el tiempo y que hace falta "un ente regulador que active a qué créditos se les inicia dicho cobro o no". Falta saber quién es ese responsable y si decide caso por caso o con una regla general (por ejemplo, todos los créditos que lleguen a cierta mora). Según su respuesta el motor guarda una marca de activación por crédito con quién la puso y cuándo, o aplica la regla sola.',
    excelHoy: 'El Excel cobra 3.000 por día de atraso a todo crédito en mora, sin activación.',
    opciones: [
      { codigo: 'administrador-credito', texto: 'El administrador activa crédito por crédito' },
      { codigo: 'director-credito', texto: 'El director financiero o el gerente activan crédito por crédito' },
      { codigo: 'comite-credito', texto: 'Un comité decide crédito por crédito' },
      { codigo: 'regla-mora', texto: 'Se activa sola por regla (al llegar a cierta mora) y un responsable puede exonerar', detalle: 'Indique en el comentario a qué mora y quién exonera.' },
      { codigo: 'otro', texto: 'Otra forma', pideTexto: true },
    ],
  },
  {
    id: 'R6-LIMITE-CORTO',
    bloque: PAGOS,
    titulo: 'En un crédito de menos de 24 meses, ¿el plazo límite de recuperación es su propio plazo o los 24 meses?',
    contexto:
      'Para el caso 8 dijo "ampliarlo hasta 104 cuotas", es decir 24 meses contados desde el desembolso, y que el límite coincide con el plazo del crédito. Falta el caso de un crédito más corto: uno de 12 meses puede tener el límite en 12 meses (su plazo) o seguir en 24 meses (más tiempo para recuperar antes de la recuperación obligatoria).',
    excelHoy: 'El Excel fija el límite en semanas (100 en el caso 8, 104 según su respuesta), sin distinguir plazos.',
    opciones: [
      { codigo: 'plazo-del-credito', texto: 'Su propio plazo: un crédito de 12 meses tiene límite de 12 meses' },
      { codigo: 'siempre-24', texto: 'Siempre 24 meses, sea cual sea el plazo del crédito' },
      { codigo: 'otro', texto: 'Otra regla', pideTexto: true },
    ],
  },

  // ── Dación y retoma ───────────────────────────────────────────────────────────
  {
    id: 'R6-RECUP-AUTO',
    bloque: RECUPERACION,
    titulo: 'Al llegar a 2 cuotas atrasadas, ¿el motor ejecuta la dación o la retoma solo, o solo marca el crédito para que una persona la ordene?',
    contexto:
      'Dijo que "la política es ejecutar dación o retoma luego de 2 cuotas atrasadas". La dación y la retoma terminan el crédito y mueven la moto y el dinero, así que conviene saber si el motor las hace solo o si apenas avisa. También falta qué pasa si el cliente se pone al día después de las 2 cuotas pero antes de que se ejecute.',
    excelHoy: 'El Excel ejecuta la recuperación en la semana que dice la instrucción del caso; no hay un umbral automático.',
    opciones: [
      { codigo: 'marca-persona', texto: 'El motor solo marca "listo para recuperación"; una persona la ordena. Si el cliente se pone al día antes, la marca se quita y el crédito sigue' },
      { codigo: 'marca-persona-sigue', texto: 'El motor solo marca; una persona la ordena. Aunque el cliente se ponga al día, la persona decide si se ejecuta' },
      { codigo: 'automatica', texto: 'El motor la ejecuta solo al llegar a 2 cuotas atrasadas', detalle: 'Indique en el comentario si el cliente puede evitarla pagando antes de la ejecución.' },
      { codigo: 'otro', texto: 'Otra forma', pideTexto: true },
    ],
  },
  {
    id: 'R6-RECUP-VS-CASO11',
    bloque: RECUPERACION,
    titulo: 'El caso 11 espera 4 periodos antes de la dación. Con la política de 2 cuotas atrasadas, ¿se corrige el caso 11 a 2?',
    contexto:
      'El caso 11 del Excel (pago inferior y luego dación) espera 4 periodos. Su respuesta fija el umbral en 2 cuotas atrasadas. Si el caso 11 se ajusta a 2, el Excel queda corregido y el motor reproduce la política; si se deja en 4, el caso es un ejemplo de otra situación.',
    excelHoy: 'El caso 11 espera 4 periodos de atraso antes de la dación.',
    opciones: [
      { codigo: 'ajustar-a-2', texto: 'Sí, el caso 11 se ajusta a 2 cuotas atrasadas (el Excel se corrige)' },
      { codigo: 'dejar-4', texto: 'No, el caso 11 se queda en 4 como ejemplo', detalle: 'Indique en el comentario por qué el caso 11 no sigue la política de 2.' },
    ],
  },
]

// Respuestas de Diego del 16 de octubre de 2026 (documento Preguntas-PO-Financiera).
const RESPONDIDAS_16_OCT: Record<string, Omit<Respondida, 'fecha'>> = {
  P1: { referencia: 'P-02', respuesta: 'Excel del 11 de octubre (Inputs ⑥): cobranza → mora → interés corriente → fondo de garantías → asistencia → seguro → capital.' },
  P3: { referencia: 'P-03', respuesta: 'Mora = capital vencido × tasa periódica (la misma corriente); en pagos tardíos se suman los días de atraso de la cuota (caso 9).' },
  PDOBLE: { referencia: 'P-03', respuesta: 'No: el interés corriente va sobre el capital vigente y el capital vencido paga solo mora.' },
  P7: { referencia: 'P-16', respuesta: 'Sí: en dación y retoma se cobran mora y cobranza.' },
  P2: { referencia: 'P-05', respuesta: 'Sin redondeo en el cálculo, como el Excel; solo el formato se muestra sin decimales.' },
  S1: { referencia: 'P-07', respuesta: 'Sobre el monto financiado inicial. En mora o no pago el fondo y la asistencia no se cobran; solo el seguro.' },
  S2: { referencia: 'P-07 · P-09', respuesta: 'Sobre el monto financiado, que ya descuenta el pago inicial. La inicial es opcional y la digita el asesor.' },
  S4: { referencia: 'P-17', respuesta: 'No aplica: antes del plazo límite el crédito termina en dación o retoma.' },
  A1: { referencia: 'P-13', respuesta: 'El cliente elige; si no responde, se reduce el plazo.' },
  A2: { referencia: 'P-14', respuesta: 'No es posible: nunca se recibe más dinero que el saldo del crédito.' },
  T3: { referencia: 'P-01', respuesta: 'Tasa según el catálogo por línea de crédito: la máxima legal publicada cada mes, con actualización obligatoria por un autorizador.' },
  C4: { referencia: 'P-24', respuesta: 'No tener en cuenta la hoja Validaciones.' },
  S5: { referencia: 'Excel 11 oct', respuesta: 'Se gira lo recaudado de cada servicio: el Pago FCC del mes es la suma de lo aplicado al fondo.' },
}

// Respondidas el 20 de octubre de 2026 con respuestas consistentes. El detalle de cada una está en
// el documento de respuestas de Diego. PTOPE, PCOB y PDIAS dependen de la política de gastos de
// cobranza: se contestaron, pero la política sigue pendiente.
const NOTA_COBRANZA = 'Pendiente: política de gastos de cobranza.'
const REF_20_OCT = 'Respuestas 20 oct'
const RESPONDIDAS_20_OCT: Record<string, Omit<Respondida, 'fecha'>> = {
  PTOPE: { referencia: REF_20_OCT, nota: NOTA_COBRANZA, respuesta: 'Los gastos de cobranza dependen de una política que enviará Diego; el valor (3.000 por día) es un ejemplo, debe ser configurable y solo lo puede tocar un administrador.' },
  PCOB: { referencia: REF_20_OCT, nota: NOTA_COBRANZA, respuesta: 'Cómo se cuentan los días de cobranza sin pago va dentro de la política de cobranza que enviará Diego.' },
  PDIAS: { referencia: REF_20_OCT, nota: NOTA_COBRANZA, respuesta: 'Todo depende de la periodicidad (semanal, quincenal o mensual) y se negocia por crédito: no se encaja en un solo periodo.' },
  S3: { referencia: REF_20_OCT, respuesta: 'Los servicios no se cobran por semana: se cobran según la configuración (periodicidad) del crédito; el ejemplo semanal es solo eso.' },
  S6: { referencia: REF_20_OCT, respuesta: 'Se maneja como ingreso recibido para terceros: una cuenta por pagar contra el banco, porque la financiera recauda el dinero. Código del PUC por confirmar.' },
  A4: { referencia: REF_20_OCT, respuesta: 'Siempre se respeta la prelación de Inputs; los ítems 1 a 6 (cobranza, mora, interés, fondo, asistencia, seguro) no se cobran por adelantado y el excedente va solo a capital.' },
  A5: { referencia: REF_20_OCT, respuesta: 'Un abono extra recalcula la cuota o el plazo, pero no altera la tasa del crédito.' },
  A6: { referencia: REF_20_OCT, respuesta: 'El prepago total cobra interés corriente por los días transcurridos desde la última cuota (sin comentario; por confirmar).' },
  T1: { referencia: REF_20_OCT, respuesta: 'El cambio de tasa aplica desde la cuota siguiente, respetando la periodicidad del crédito.' },
  T2: { referencia: REF_20_OCT, respuesta: 'El margen de usura se da en puntos porcentuales de la E.A.: 1 % equivale a 100 puntos básicos.' },
  T4: { referencia: REF_20_OCT, respuesta: 'La tasa no vuelve a la pactada: si la usura baja, la tasa sigue bajando respetando el margen; si sube, se queda como está.' },
  R1: { referencia: REF_20_OCT, respuesta: 'Es un tema de terminología: dación es cuando el avalúo es menor al saldo (el diferencial se cobra al fondo de garantías); retoma, cuando es mayor y se devuelve dinero.' },
  R3: { referencia: REF_20_OCT, respuesta: 'La moto entra al inventario por el avalúo y la diferencia se ajusta después (sin comentario; por confirmar).' },
  R4: { referencia: REF_20_OCT, respuesta: 'Interés completo en la semana del evento, como el Excel (sin comentario; por confirmar).' },
  C1: { referencia: REF_20_OCT, respuesta: 'La causación sigue el plan normal (caso 1), respetando la periodicidad pactada.' },
  C2: { referencia: REF_20_OCT, nota: 'Pendiente: PUC que enviará por correo (ver CON-1).', respuesta: 'Los códigos del PUC los enviará por correo; quedan pendientes.' },
  C3: { referencia: REF_20_OCT, respuesta: 'El pago inicial lo recauda el concesionario y no afecta a la fábrica de crédito; se guarda para auditoría y para la simulación de la oferta comercial.' },
  X3: { referencia: REF_20_OCT, respuesta: 'Se refería a dación (caso 3) o retoma (caso 4).' },
}

// Respondidas en la ronda 3 (Diego, 20 de octubre de 2026, 15:54), ya contrastadas. Cuando hay comentario,
// el resumen es el del comentario. Las que dependen de un documento de Diego llevan nota y no cuentan como
// cerradas; las que se volvieron a preguntar en la ronda 4 quedan como reemplazadas.
const REF_RONDA_3 = 'Ronda 3'
const RESPONDIDAS_RONDA_3: Record<string, Omit<Respondida, 'fecha'>> = {
  'PPER': { referencia: REF_RONDA_3, respuesta: 'Se ofrecen semanal, quincenal y mensual según la negociación; la tasa del periodo es la conversión financiera de la E.A. (mensual 12, semanal 52, quincenal 24; el 24 se confirma en R4-QUIN).' },
  'PPER-2': { referencia: REF_RONDA_3, respuesta: 'El plazo se pacta en número de cuotas según la periodicidad, con un máximo de 24 meses.' },
  'PPER-3': { referencia: REF_RONDA_3, respuesta: 'Si se pacta en meses, el número de cuotas se redondea hacia abajo: ROUNDDOWN(meses × periodos/12).' },
  'PPER-4': { referencia: REF_RONDA_3, respuesta: 'Quincenal y mensual entran al catálogo; la periodicidad diaria se retira.' },
  'PPER-5': { referencia: REF_RONDA_3, respuesta: 'En mensual, la mora se cuenta por los días reales del mes.' },
  'PPER-6': { referencia: REF_RONDA_3, respuesta: 'El plazo límite se expresa en periodos de la periodicidad del crédito.' },
  'PPER-7': { referencia: REF_RONDA_3, respuesta: 'En un prepago a mitad de periodo se cobran los días transcurridos.' },
  'PPER-10': { referencia: REF_RONDA_3, respuesta: 'La periodicidad no cambia durante el crédito; si hace falta, se liquida y se abre uno nuevo.' },
  'CAL-1': { referencia: REF_RONDA_3, respuesta: 'La cuota es a tiempo hasta el fin del martes; el atraso empieza el miércoles (día 1).' },
  'CAL-2': { referencia: REF_RONDA_3, respuesta: 'Sin manejo de festivos.' },
  'CAL-3': { referencia: REF_RONDA_3, respuesta: 'El martes es fijo para todos los créditos; la primera fecha se pacta en la oferta.' },
  'SRV-1': { referencia: REF_RONDA_3, respuesta: 'En un prepago se giran a los terceros solo los periodos causados, sin devolución.' },
  'SRV-2': { referencia: REF_RONDA_3, respuesta: 'Si un abono acorta el plazo, la cobertura de seguro y fondo termina con el nuevo plazo.' },
  'SRV-3': { referencia: REF_RONDA_3, respuesta: 'La última cuota lleva los servicios completos.' },
  'SRV-4': { referencia: REF_RONDA_3, respuesta: 'Una cuenta por pagar por tercero y el IVA en su propia cuenta.' },
  'ABN-1': { referencia: REF_RONDA_3, respuesta: 'El equipo arma el caso 13 (abono extra con pendientes) y Diego valida los valores.' },
  'ABN-2': { referencia: REF_RONDA_3, respuesta: 'El excedente del abono cubre primero el capital vencido más antiguo y después el vigente.' },
  'ABN-3': { referencia: REF_RONDA_3, respuesta: 'Sin límite de abonos extra.' },
  'ABN-4': { referencia: REF_RONDA_3, respuesta: 'Pagar antes del martes el valor exacto de una cuota ya causada es pago corriente, no anticipo; lo que supere ese valor es abono extra.' },
  'ABN-5': { referencia: REF_RONDA_3, respuesta: 'Cada abono a menor cuota recalcula la cuota sobre las cuotas restantes.' },
  'ABN-6': { referencia: REF_RONDA_3, respuesta: 'El prepago no tiene costo; paz y salvo y liberación de la prenda en 30 días.' },
  'USU-1': { referencia: REF_RONDA_3, respuesta: 'La nueva usura rige desde la cuota siguiente a la publicación; se corrige el Excel.' },
  'USU-2': { referencia: REF_RONDA_3, respuesta: 'La actualiza alguien de riesgos o de la dirección financiera.' },
  'USU-4': { referencia: REF_RONDA_3, respuesta: 'Hay una usura por línea de crédito: la que publica la Superfinanciera el último día hábil de cada mes.' },
  'USU-5': { referencia: REF_RONDA_3, respuesta: 'Se recalculan todos los créditos vigentes, con aprobación del lote.' },
  'USU-6': { referencia: REF_RONDA_3, respuesta: 'Cuando la tasa baja, siempre se mantiene la misma cuota y baja el plazo (modalidad del caso 10).' },
  'USU-7': { referencia: REF_RONDA_3, respuesta: 'Solo cambian la tasa la usura y la ley o regulación.' },
  'REC-1': { referencia: REF_RONDA_3, respuesta: 'El fondo de garantías cubre todo el faltante de la dación.' },
  'REC-2': { referencia: REF_RONDA_3, respuesta: 'El fondo cubre todos los conceptos: capital, intereses, mora, servicios y cobranza.' },
  'REC-3': { referencia: REF_RONDA_3, respuesta: 'Si el fondo no alcanza, queda una cuenta por cobrar al cliente y pasa a cobro jurídico.' },
  'REC-4': { referencia: REF_RONDA_3, respuesta: 'En la retoma, primero se compensan otras obligaciones del cliente y se le devuelve el resto.' },
  'REC-5': { referencia: REF_RONDA_3, respuesta: 'El excedente se transfiere menos los costos de la retoma, con soporte.' },
  'REC-7': { referencia: REF_RONDA_3, respuesta: 'Al llegar al plazo límite con saldo, la recuperación (dación o retoma) es obligatoria.' },
  'REC-8': { referencia: REF_RONDA_3, respuesta: 'La semana del evento cuenta como vencida.' },
  'CON-2': { referencia: REF_RONDA_3, respuesta: 'Mismas cuentas del PUC con un auxiliar por línea de crédito.' },
  'CON-3': { referencia: REF_RONDA_3, respuesta: 'El CRM entrega cada pago asociado a su crédito.' },
  'ROL-2': { referencia: REF_RONDA_3, respuesta: 'Todos los parámetros del producto son sensibles.' },
  'ROL-3': { referencia: REF_RONDA_3, respuesta: 'Un cambio de parámetro rige solo para créditos nuevos, salvo la usura, que recalcula los vigentes desde la cuota siguiente.' },
  'ROL-4': { referencia: REF_RONDA_3, respuesta: 'El administrador cambia de inmediato, con bitácora (quién, qué, cuándo), sin segundo aprobador.' },
  'ROL-5': { referencia: REF_RONDA_3, respuesta: 'El siguiente alcance es el motor de scoring, antes de la fábrica de créditos.' },
  'COB-1': { referencia: REF_RONDA_3, nota: 'Pendiente: política de gastos de cobranza.', respuesta: 'Diego enviará la política completa de gastos de cobranza; esta decisión va dentro de ella.' },
  'COB-2': { referencia: REF_RONDA_3, nota: 'Pendiente: política de gastos de cobranza.', respuesta: 'Diego enviará la política completa de gastos de cobranza; esta decisión va dentro de ella.' },
  'COB-3': { referencia: REF_RONDA_3, nota: 'Pendiente: política de gastos de cobranza.', respuesta: 'Diego enviará la política completa de gastos de cobranza; esta decisión va dentro de ella.' },
  'CON-1': { referencia: REF_RONDA_3, nota: 'Pendiente: PUC en Excel.', respuesta: 'Diego enviará el plan de cuentas (PUC) en un Excel por correo.' },
  'PPER-1': { referencia: REF_RONDA_3, reemplazadaPor: 'R4-MENSUAL-DIA', nota: 'Reemplazada por R4-MENSUAL-DIA.', respuesta: 'Quincenal: se paga el 5 y el 20. Mensual: "el 5 o el primer día del mes"; falta saber si es fija para todos o por crédito.' },
  'USU-3': { referencia: REF_RONDA_3, reemplazadaPor: 'R4-USURA-PLAZO', nota: 'Reemplazada por R4-USURA-PLAZO.', respuesta: 'Si nadie autoriza, la usura se aplica sola; choca con USU-5 (el lote espera aprobación), así que falta el plazo.' },
  'USU-8': { referencia: REF_RONDA_3, reemplazadaPor: 'R4-AVISO-CLIENTE', nota: 'Reemplazada por R4-AVISO-CLIENTE.', respuesta: 'Los servicios (fondo, asistencia, seguro) no cambian con la tasa. Dijo que no se avisa al cliente; falta confirmarlo con legal.' },
  'CON-4': { referencia: REF_RONDA_3, reemplazadaPor: 'R4-COBRANZA-CONTABLE', nota: 'Reemplazada por R4-COBRANZA-CONTABLE.', respuesta: 'Los gastos de cobranza no son ingreso: son compensación de gastos reales; falta el tratamiento contable.' },
  'REC-6': { referencia: REF_RONDA_3, reemplazadaPor: 'R4-PERITO', nota: 'Reemplazada por R4-PERITO.', respuesta: 'Valor de dación o retoma = guía Fasecolda × 65 %, con perito externo como segunda opción; falta cuándo aplica el perito.' },
  'ROL-1': { referencia: REF_RONDA_3, reemplazadaPor: 'R4-SUPLENTE', nota: 'Reemplazada por R4-SUPLENTE.', respuesta: 'Un solo administrador, sin suplente; falta qué pasa si no está.' },
  'A3': { referencia: REF_RONDA_3, reemplazadaPor: 'R4-A3', nota: 'Reemplazada por R4-A3.', respuesta: 'Habló solo de la prelación; no dijo si hay un mínimo.' },
  'T5': { referencia: REF_RONDA_3, reemplazadaPor: 'R4-T5', nota: 'Reemplazada por R4-T5.', respuesta: 'Pidió investigar las cláusulas aceleratorias; no escogió un saldo.' },
  'R2': { referencia: REF_RONDA_3, reemplazadaPor: 'R4-R2', nota: 'Reemplazada por R4-R2.', respuesta: 'Dijo que aún no tiene definido el valor.' },
  'C5': { referencia: REF_RONDA_3, reemplazadaPor: 'R4-C5', nota: 'Reemplazada por R4-C5.', respuesta: 'Marcó "incluye GPS y gastos comerciales" pero su detalle dice "moto + alistamiento"; se contradicen.' },
}

// Respondidas en la ronda 4 (Diego, 21 de octubre de 2026, 17:21), ya contrastadas. El resumen es la respuesta
// VÁLIDA (el comentario o detalle manda sobre la opción marcada). R4-T5 la contestó Diego por documento (RESPONDIDAS_22_OCT); R4-CASO13 cambió la regla y se vuelve a validar en la ronda 5 (P5-CASO13).
const REF_RONDA_4 = 'Ronda 4'
const RESPONDIDAS_RONDA_4: Record<string, Omit<Respondida, 'fecha'>> = {
  'R4-QUIN': { referencia: REF_RONDA_4, respuesta: 'La quincena tiene 24 periodos al año (el 104 era un error).' },
  'R4-MENSUAL-DIA': { referencia: REF_RONDA_4, respuesta: 'La mensual vence el día 5 de cada mes, igual para todos; la primera cuota se prorratea entre el desembolso y el próximo 5 (el cómo, en la ronda 5).' },
  'R4-PLAZO-LINEA': { referencia: REF_RONDA_4, respuesta: 'Tope de 24 meses para todas las líneas en la primera versión, parametrizable; a futuro puede crecer.' },
  'R4-PLAZO-LIMITE': { referencia: REF_RONDA_4, respuesta: 'El plazo límite de recuperación es de 24 meses, convertidos a periodos según la periodicidad (desde cuándo se cuenta, en la ronda 5).' },
  'R4-A3': { referencia: REF_RONDA_4, respuesta: 'No hay valor mínimo para el abono extra.' },
  'R4-PAGO-SIN-CUOTA': { referencia: REF_RONDA_4, respuesta: 'Solo antes de la primera cuota no hay cuota causada; después un pago cubre la cuota causada y lo que sobre es abono extra (ABN-4).' },
  'R4-CASO13': { referencia: REF_RONDA_4, reemplazadaPor: 'P5-CASO13', nota: 'Reemplazada por P5-CASO13.', respuesta: 'Se paga todo lo vencido, pero el sobrante (1.182.645,20) vuelve a pasar por la prelación de las 7 líneas; solo el resto va a capital. Cambia D21/D30.' },
  'R4-USURA-PLAZO': { referencia: REF_RONDA_4, respuesta: 'Si nadie autoriza, la usura se aplica sola 1 día hábil después de su publicación, también al lote de vigentes.' },
  'R4-AVISO-CLIENTE': { referencia: REF_RONDA_4, respuesta: 'No se avisa al cliente cuando cambia la tasa (decisión del dueño de producto; conviene validarla con legal).' },
  'R4-R2': { referencia: REF_RONDA_4, respuesta: 'En la recuperación se descuenta el costo real con soporte (grúa, bodegaje, avalúo, trámites), en dación y en retoma.' },
  'R4-PERITO': { referencia: REF_RONDA_4, respuesta: 'El perito externo se usa cuando la moto está en Fasecolda pero su estado real es de alto deterioro; en lo demás, Fasecolda × 65 %.' },
  'R4-FONDO-TOPE': { referencia: REF_RONDA_4, respuesta: 'El fondo de garantías no tiene tope por crédito.' },
  'R4-C5': { referencia: REF_RONDA_4, respuesta: 'El pago inicial se resta del valor total (moto + alistamiento + GPS + gastos comerciales); el GPS y los gastos comerciales son parte del alistamiento.' },
  'R4-COBRANZA-CONTABLE': { referencia: REF_RONDA_4, respuesta: 'Los gastos de cobranza se registran como ingresos no operacionales, para que no se imputen como mayor interés de financiación (PUC clase 42).' },
  'R4-SUPLENTE': { referencia: REF_RONDA_4, respuesta: 'Se nombra un suplente con los mismos permisos y la misma bitácora.' },
}

// Respondidas por documento (Diego, 22 de octubre de 2026, "rptas software.docx"), fuera del cuestionario. El resumen
// es la respuesta VÁLIDA. PPER-8 sigue pendiente de la política de gastos de cobranza. P5-LIMITE-DESDE queda
// reemplazada por R6-LIMITE-CORTO porque X4 la contesta solo en parte. Lo no seguro va a la ronda 6.
const REF_22_OCT = 'Documento 22 oct'
const RESPONDIDAS_22_OCT: Record<string, Omit<Respondida, 'fecha'>> = {
  'R4-T5': { referencia: REF_22_OCT, respuesta: 'La cuota se recalcula sobre el capital vigente; lo vencido se cobra aparte (está prohibido liquidar intereses sobre intereses).' },
  X1: { referencia: REF_22_OCT, respuesta: 'Todas las operaciones tienen servicios complementarios; el Excel solo los simuló en una operación como ejemplo.' },
  'COB-4': { referencia: REF_22_OCT, respuesta: 'Los gastos de cobranza solo se cobran mientras el cliente esté en mora; si se pone al día o termina de pagar, no se liquidan.' },
  'COB-5': { referencia: REF_22_OCT, respuesta: 'Los exonera el director financiero o el gerente, con justificación y aprobando directamente en el software.' },
  'COB-6': { referencia: REF_22_OCT, respuesta: 'La tarifa no está regulada: el cobro vale si el contrato de mutuo dice cuándo se causa y cuánto. Los 3.000 son un ejemplo; el valor real está pendiente y cambia en el tiempo. Un responsable activa la cobranza crédito por crédito (R6-COBRANZA-ACTIVA).' },
  'PPER-8': { referencia: REF_22_OCT, nota: 'Pendiente: política de gastos de cobranza.', respuesta: 'Sin respuesta: dijo que este tema está pendiente de definir y es de los gastos de cobranza.' },
  'PPER-9': { referencia: REF_22_OCT, respuesta: 'No hay días específicos: la política es ejecutar la dación o la retoma luego de 2 cuotas atrasadas, en periodos de la periodicidad pactada (R6-RECUP-AUTO y R6-RECUP-VS-CASO11).' },
  X4: { referencia: REF_22_OCT, respuesta: 'El plazo límite del caso 8 se amplía a 104 cuotas (24 meses), según el recálculo; en cualquier momento puede darse la retoma o la dación.' },
  'P5-LIMITE-DESDE': { referencia: REF_22_OCT, reemplazadaPor: 'R6-LIMITE-CORTO', nota: 'Reemplazada por R6-LIMITE-CORTO.', respuesta: 'X4 la contesta en parte: el límite es de 104 cuotas (24 meses) desde el desembolso; falta el caso de un crédito de menos de 24 meses.' },
}

/** Todas las preguntas llevan la opción común "Respondo en el comentario / queda pendiente". */
function conPendiente(p: Pregunta): Pregunta {
  return p.opciones.some((o) => o.codigo === CODIGO_PENDIENTE) ? p : { ...p, opciones: [...p.opciones, OPCION_PENDIENTE] }
}

export const TODAS: Pregunta[] = [...BASE, ...RONDA_3, ...RONDA_4, ...RONDA_5, ...RONDA_6].map((p) => {
  if (RESPONDIDAS_22_OCT[p.id]) return { ...p, respondida: { fecha: '22 oct 2026', ...RESPONDIDAS_22_OCT[p.id] } }
  if (RESPONDIDAS_16_OCT[p.id]) return { ...p, respondida: { fecha: '16 oct 2026', ...RESPONDIDAS_16_OCT[p.id] } }
  if (RESPONDIDAS_20_OCT[p.id]) return { ...p, respondida: { fecha: '20 oct 2026', ...RESPONDIDAS_20_OCT[p.id] } }
  if (RESPONDIDAS_RONDA_3[p.id]) return { ...p, respondida: { fecha: '20 oct 2026', ...RESPONDIDAS_RONDA_3[p.id] } }
  if (RESPONDIDAS_RONDA_4[p.id]) return { ...p, respondida: { fecha: '21 oct 2026', ...RESPONDIDAS_RONDA_4[p.id] } }
  return p
}).map(conPendiente)

/** Las que siguen abiertas: son las que se le preguntan a negocio. */
export const PREGUNTAS = TODAS.filter((p) => !p.respondida)

export const RESPONDIDAS = TODAS.filter((p) => p.respondida)

/** Bloques que aún tienen preguntas abiertas: los demás ya quedaron respondidos. */
export const BLOQUES_ABIERTOS: string[] = BLOQUES.filter((b) => PREGUNTAS.some((p) => p.bloque === b))

/**
 * Completitud de reglas: cerradas sobre el total inventariado (respondidas + abiertas), por bloque.
 * Una respondida con salvedad vigente (`nota`, p. ej. la política de cobranza) aún no cuenta como cerrada.
 * El detalle regla por regla está en docs/completitud-reglas.md.
 */
export function completitudPorBloque(): { bloque: string; cerradas: number; total: number }[] {
  return BLOQUES.map((bloque) => {
    // Las reemplazadas por una pregunta de una ronda posterior no cuentan: la cuenta la nueva.
    const delBloque = TODAS.filter((p) => p.bloque === bloque && !p.respondida?.reemplazadaPor)
    return { bloque, total: delBloque.length, cerradas: delBloque.filter((p) => p.respondida && !p.respondida.nota).length }
  })
}
