# Respuestas de Diego · ronda 3 (enviada 2026-10-20 15:54)

Diego (Gerente, dueño de producto) respondió las 59 preguntas de la ronda 3 del cuestionario. Datos crudos: `respuestas-negocio.md` (generado por `herramientas/importar_respuestas.py --nube`). Formato y criterio como en `respuestas-diego-2026-10-20.md`: **vale el comentario o detalle sobre la opción marcada**.

Regla de trabajo: lo seguro se documenta y se implementa; lo no seguro va a la ronda 4 (sección posterior a las tablas). Hay cuatro preguntas (A3, T5, R2, C5) que Diego no volvió a responder: traen la misma opción y comentario de la mañana (autoguardado) y siguen abiertas.

## Periodicidad

| ID | Opción marcada | Comentario / detalle | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| PPER | Semanal, quincenal y mensual según la negociación | Detalle: conversión de la E.A.: mensual 12 periodos/año, semanal 52, "quincena 104". | Tres periodicidades; tasa del periodo por conversión financiera de la E.A. | Mensual 12, semanal 52. La quincena se implementa con 24 (2 por mes); el "104" parece un error (va a la ronda 4). |
| PPER-1 | Pendiente (comentario) | "Quincenal pagan los 5 y el 20, y mensual 5 o el primer día del mes." | Quincenal: días 5 y 20. Mensual: ver ronda 4. | Calendario quincenal fijo 5 y 20. |
| PPER-2 | En número de cuotas | "Respetar el máximo de 24 meses; las cuotas acorde a la periodicidad." | Plazo en cuotas según la periodicidad, máximo 24 meses. | Validar plazo máximo de 24 meses por periodicidad. |
| PPER-3 | Hacia abajo | (sin comentario) | ROUNDDOWN(meses × periodos/12). | Redondeo hacia abajo del número de cuotas. |
| PPER-4 | Quincenal entra, la diaria se retira | "La mensual también entra." | Catálogo: semanal, quincenal, mensual. Sin diaria. | Retirar la diaria del catálogo. |
| PPER-5 | Días reales del mes | (sin comentario) | En mensual la mora cuenta los días reales (28 a 31). | Mora mensual con calendario real. |
| PPER-6 | En periodos de la periodicidad del crédito | (sin comentario) | Plazo límite en periodos del crédito. | Plazo límite expresado en periodos propios. |
| PPER-7 | Sí, interés de los días transcurridos | (sin comentario) | El prepago a mitad de periodo cobra los días transcurridos. | Consistente con D22. |
| PPER-8 | Pendiente | "Está pendiente." | Sin respuesta; depende de la política de cobranza. | Sin cambio. |
| PPER-9 | Pendiente | "Pendiente por desarrollar la política de ejecución de la dación de pago." | Sin respuesta. | Sin cambio. |
| PPER-10 | No, queda fija | "Si se requiere un cambio se liquida el contrato y se abre uno nuevo." | La periodicidad no cambia en la vida del crédito. | Sin operación de cambio de periodicidad; liquidar y reabrir. |

## Calendario semanal

| ID | Opción marcada | Comentario | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| CAL-1 | A tiempo hasta el fin del martes | (sin comentario) | El atraso empieza el miércoles (día 1). | Los días de atraso cuentan desde el miércoles. |
| CAL-2 | Sin festivos | (sin comentario) | Vence el martes aunque sea festivo. | Sin calendario de festivos. |
| CAL-3 | Martes fijo, primera fecha pactada | (sin comentario) | El martes es igual para todos; la primera cuota se pacta en la oferta. | Primera fecha como dato de la oferta. |

## Servicios complementarios

| ID | Opción marcada | Comentario | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| SRV-1 | Periodos causados, sin devolución | (sin comentario) | En un prepago solo se giran los periodos causados. | Giro a terceros hasta el prepago. |
| SRV-2 | Termina con el nuevo plazo | (sin comentario) | Si un abono acorta el plazo, la cobertura termina con él. | Servicios solo hasta el nuevo plazo. |
| SRV-3 | Completos | (sin comentario) | La última cuota lleva los servicios completos. | Sin prorrateo en la última cuota. |
| SRV-4 | Cuenta por tercero e IVA aparte | (sin comentario) | Una CxP por tercero y el IVA en su propia cuenta. | Cuentas separadas por tercero e IVA. |

## Abonos y prepago

| ID | Opción marcada | Comentario | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| ABN-1 | El caso propuesto sirve | (sin comentario) | El equipo arma el caso 13 (abono extra con pendientes) y Diego valida. | Armar el caso 13. |
| ABN-2 | Vencido primero | (sin comentario) | El excedente cubre el capital vencido más antiguo y luego el vigente. | Orden del excedente de capital. |
| ABN-3 | Sin límite | (sin comentario) | Sin tope de abonos. | Sin validación de cantidad. |
| ABN-4 | Pendiente (comentario) | "La cuota ya causada tiene plazo hasta el martes; pagar el valor exacto antes no es anticipo, es pago corriente. Si supera el valor, es abono extra." | Pago exacto de cuota causada = pago corriente; el excedente = abono extra. | Distinguir pago corriente de abono extra por el valor exacto. |
| ABN-5 | Cada abono recalcula | (sin comentario) | Cada abono a menor cuota recalcula sobre las cuotas restantes. | Recálculo en cada abono. |
| ABN-6 | Sin costo, con plazo | Detalle: "30 días". | Prepago sin costo; paz y salvo y liberación de la prenda en 30 días. | Sin cargo; plazo de 30 días como dato. |
| A3 | Sin mínimo | "El abono extra se aplica según la prelación de Inputs." | **Abierta** (repetida sin responder). | Sin `abonoMinimo`; descartada (ronda 4). |

## Cambio de tasa y usura

| ID | Opción marcada | Comentario | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| USU-1 | Desde la cuota siguiente a la publicación | (sin comentario) | El motor queda así; el Excel se corrige. | Confirma el comportamiento actual. |
| USU-2 | Riesgos o dirección financiera | (sin comentario) | Esa persona autoriza la usura. | Rol autorizador. |
| USU-3 | Aplica la usura publicada y avisa | (sin comentario) | Si nadie autoriza, se aplica sola. Choca con USU-5 (ronda 4). | Por precisar el plazo de aprobación. |
| USU-4 | Una usura por línea | Detalle: "la publica la Superfinanciera el último día hábil de cada mes." | Usura por línea de crédito. | Tabla de usura por línea. |
| USU-5 | Todos los vigentes, con aprobación del lote | (sin comentario) | Se recalculan todos los vigentes tras aprobar el lote. | Recálculo masivo con aprobación. |
| USU-6 | Siempre misma cuota y menor plazo | (sin comentario) | Con tasa a la baja se mantiene la cuota y baja el plazo (`MISMA_CUOTA`). | **Cambia D20**: `UsuraVigente` hoy recalcula la cuota como el caso 2. |
| USU-7 | Usura y ley o regulación | (sin comentario) | Solo esas dos causas cambian la tasa. | Sin otras causas. |
| USU-8 | Servicios constantes, sin aviso | (sin comentario) | Los servicios no cambian con la tasa. El "sin aviso" va a la ronda 4. | Servicios fijos tras el cambio. |
| T5 | Saldo total | "Investigar las cláusulas aceleratorias de los contratos de mutuo." | **Abierta** (repetida sin responder). | Pendiente (ronda 4); descartada. |

## Dación y retoma

| ID | Opción marcada | Comentario | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| REC-1 | Faltante completo | (sin comentario) | El fondo cubre todo el faltante. | Sin tope al fondo. |
| REC-2 | Todos los conceptos | (sin comentario) | Capital, intereses, mora, servicios y cobranza. | El fondo cubre todos los conceptos. |
| REC-3 | Cobro al cliente | (sin comentario) | Si no alcanza, CxC al cliente y cobro jurídico. | Saldo remanente como CxC. |
| REC-4 | Compensar primero | "Las obligaciones adicionales pueden surgir de los gastos de cobranza por definir." | Se compensan otras obligaciones y se devuelve el resto. | Compensación antes de devolver. |
| REC-5 | Transferencia menos costos | (sin comentario) | Excedente menos costos de la retoma, con soporte. | Costos de retoma con soporte. |
| REC-6 | Pendiente (comentario) | "Mix: principal Fasecolda al 65 %, segunda opción perito externo." | Guía Fasecolda × 65 %; perito externo como segunda opción. Falta cuándo aplica el perito (ronda 4). | Valor de recuperación por Fasecolda × 65 %. |
| REC-7 | Recuperación obligatoria | (sin comentario) | Al plazo límite con saldo, dación o retoma. | Recuperación obligatoria. |
| REC-8 | Cuenta como vencida | (sin comentario) | La semana del evento es vencida. | Confirma el tratamiento. |
| R2 | Sí, valor estimado | "No se tiene aún definido el valor." | **Abierta** (repetida sin responder). | Sin `costoRecuperacion`; descartada. |

## Contabilidad y roles

| ID | Opción marcada | Comentario | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| CON-1 | PUC por correo | "En un Excel, con la metodología PUC colombiana." | Pendiente de documento. | Códigos PUC sin definir. |
| CON-2 | Auxiliar por línea | (sin comentario) | Mismas cuentas con un auxiliar por línea. | Dimensión auxiliar por línea. |
| CON-3 | El CRM clasifica | (sin comentario) | El CRM entrega el pago asociado al crédito. | Entrada con crédito ya identificado. |
| CON-4 | Pendiente (comentario) | "No es ingreso: es compensación de gastos reales, la haga la financiera o un tercero." | Los gastos de cobranza no son ingreso. Tratamiento contable a la ronda 4. | Sin cuenta de ingreso para cobranza. |
| C5 | Del valor total con GPS y gastos comerciales | Detalle: "el total es el valor de la moto más alistamiento." | **Abierta**: el detalle contradice la opción. | Base del pago inicial pendiente (ronda 4); descartada. |
| ROL-1 | Una sola persona, sin suplente | (sin comentario) | Un administrador. Contingencia a la ronda 4. | Un rol administrador. |
| ROL-2 | Todos los del producto | (sin comentario) | Todos los parámetros son sensibles (incluidos plazo límite y periodicidades). | Todos restringidos al administrador. |
| ROL-3 | Solo nuevos | "Excepto la usura: afecta a los anteriores, recalculando desde la cuota siguiente." | Cambios rigen solo para créditos nuevos, salvo usura. | Los créditos conservan lo firmado; la usura recalcula los vigentes. |
| ROL-4 | Bitácora sola | (sin comentario) | Cambio inmediato, con bitácora (quién, qué, cuándo), sin segundo aprobador. | Bitácora de parámetros. |
| ROL-5 | Pendiente (comentario) | "Lo que sigue es montar el motor de scoring, que va antes de la fábrica." | Siguiente alcance: scoring antes de la fábrica de créditos. | Hoja de ruta. |

## No seguro: va a la ronda 4

- **Q-104.** PPER (detalle) dice "quincena se toman 104 periodos año". Una quincena son 24 periodos/año; 104 sería un pago cada 3,5 días. Probable error: se implementa con 24 y se pide confirmación.
- **A3, T5, R2, C5** (repetidas sin responder; misma opción y comentario de la mañana):
  - A3, mínimo de abono extra: solo habló de la prelación.
  - T5, saldo para recalcular la cuota en mora al cambiar la tasa: ofrecer opciones concretas (saldo vigente, saldo total o el crédito se acelera).
  - R2, costos de recuperación: "no se tiene aún definido el valor".
  - C5: ¿el GPS y los gastos comerciales son parte del alistamiento? Sí/no.
- **USU-3 frente a USU-5.** Sin autorización la usura se aplica sola, pero el recálculo de los vigentes espera la aprobación del lote. Lectura propuesta: se aplica sola al vencer un plazo de aprobación. Preguntar el plazo (días hábiles tras la publicación) y si vale también para el lote.
- **PPER-1.** Mensual "el 5 o el primer día del mes": ¿fecha fija para todos o se elige por crédito?
- **ROL-1.** Un solo administrador sin suplente: ¿qué pasa si falta? (suplente o procedimiento de contingencia).
- **USU-8.** "No se avisa al cliente" al cambiar la tasa: posible choque con la protección al consumidor financiero; pedir que lo confirme con legal.
- **CON-4.** Tratamiento contable de los gastos de cobranza: CxC reembolsable contra el gasto, o ingreso recibido para terceros.
- **REC-6.** ¿Cuándo aplica el perito externo (sin referencia Fasecolda, objeción del cliente...)?

## Pendiente de documentos de Diego

No se vuelve a preguntar; solo queda visible.

- Política de gastos de cobranza: COB-1 a COB-6 (activación, día o evento, tope, después de la última cuota, exoneración, legalidad de los 3.000/día) y PPER-8.
- Política de ejecución de la dación: PPER-9.
- PUC en Excel por correo: CON-1.
- Ya pendiente de antes: Excel corregido (X1, X4).
