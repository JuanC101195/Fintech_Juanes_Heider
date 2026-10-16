import type { Pregunta, Respondida } from './tipos'

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
  'Ajustes al Excel',
] as const

const [PAGOS, SERVICIOS, ABONOS, TASA, RECUPERACION, CONTABILIDAD, EXCEL] = BLOQUES

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
    contexto: 'En el caso 8 el crédito termina en la semana 89, pero desde la 87 no se causan servicios: por ejemplo, el seguro de vida no tendría prima esas semanas.',
    excelHoy: 'Los servicios se cobran solo hasta la cuota 86.',
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
    contexto: 'Evita recalcular el plan por abonos de muy bajo valor.',
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
    contexto: 'El Excel solo modela el cambio de tasa con el cliente al día.',
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
    contexto: 'Hoy el valor de la moto se aplica completo a la deuda.',
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
    titulo: 'El pago inicial, ¿se resta del valor total de la operación o solo de la moto más el alistamiento?',
    contexto:
      'En su ejemplo la inicial se resta de "monto de la moto + gastos de alistamiento" (6.500.000 − 700.000). El valor total del Excel incluye además GPS y plataforma (80.000) y gastos comerciales (60.000). Hoy el motor la resta del valor total.',
    excelHoy: 'El Excel no tiene pago inicial.',
    opciones: [
      { codigo: 'valor-total', texto: 'Del valor total: moto + alistamiento + GPS + gastos comerciales' },
      { codigo: 'moto-alistamiento', texto: 'Solo de moto + alistamiento; GPS y gastos comerciales se financian siempre' },
      { codigo: 'otra', texto: 'Otra forma', pideTexto: true },
    ],
  },
  {
    id: 'C4',
    bloque: CONTABILIDAD,
    titulo: '¿Son correctas estas definiciones de la hoja Validaciones?',
    contexto: 'La hoja no trae fórmula para dos validaciones. Se tomaron así: "cuotas eliminadas" = 86 − última cuota pagada (caso 6: 25) y "periodos adicionales" = última semana − 86 (caso 8: 3).',
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

export const TODAS: Pregunta[] = BASE.map((p) =>
  RESPONDIDAS_16_OCT[p.id] ? { ...p, respondida: { fecha: '16 oct 2026', ...RESPONDIDAS_16_OCT[p.id] } } : p,
)

/** Las que siguen abiertas: son las que se le preguntan a negocio. */
export const PREGUNTAS = TODAS.filter((p) => !p.respondida)

export const RESPONDIDAS = TODAS.filter((p) => p.respondida)
