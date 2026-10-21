# Completitud de las reglas del motor

Fuente para medir el avance de la definición de reglas con Diego González (dueño de producto). Estado al **2026-10-20**.

Cada regla o dato que el motor necesita está en una de dos tablas con uno de estos estados:

- **Decidida**: con su fuente (respuesta de Diego, decisión `D*` de `decisiones.md` o pregunta `P-*` del documento de Heider).
- **Abierta**: tiene una pregunta en el cuestionario (`cuestionario/src/preguntas.ts`) y se indica su id.
- **Reemplazada**: se volvió a preguntar en la ronda 4 con una versión concreta; la cuenta la pregunta nueva (`R4-*`).
- **Pendiente de un documento de Diego**: ya contestó o remitió a un archivo que aún no llega (Excel corregido, política de cobranza, PUC) o a una reunión.

Solo las **decididas** cuentan como cerradas. Una pregunta respondida con una salvedad vigente (por ejemplo, PTOPE depende de la política de cobranza) cuenta como pendiente de documento.

Fuentes usadas: `respuestas-diego-2026-10-20.md` (sección «Pendiente de Diego»), `respuestas-diego-2026-10-16.md` (P-01 a P-27), `decisiones.md` (D1 a D25), `reglas/caso-1.md` a `caso-9.md` (preguntas abiertas de cada caso), `adr/0002-periodicidad-configurable.md`, `pendientes-tecnicos.md` y `preguntas-diego.md`.

## Resumen

| Bloque del cuestionario | Cerradas | Total | % |
|---|---|---|---|
| Pagos, mora y cobranza | 16 | 29 | 55 % |
| Servicios complementarios | 10 | 10 | 100 % |
| Abonos y prepago | 11 | 12 | 92 % |
| Cambio de tasa y usura | 10 | 13 | 77 % |
| Dación y retoma | 10 | 12 | 83 % |
| Contabilidad | 5 | 9 | 56 % |
| Roles y parámetros | 4 | 5 | 80 % |
| Ajustes al Excel | 1 | 3 | 33 % |
| **Tabla A (preguntas del cuestionario)** | **67** | **93** | **72 %** |
| Tabla B (reglas fuera del cuestionario) | 11 | 18 | 61 % |
| **Total de reglas inventariadas** | **78** | **111** | **70 %** |

Las 93 reglas de la tabla A son las 92 preguntas de las rondas 1 a 3 (de las que 10 se volvieron a preguntar y no se cuentan dos veces) más las 11 de la ronda 4 (ids `R4-*`). El cuestionario publicado muestra el porcentaje de la tabla A.

Ronda 3 (20 oct 2026, 15:54): Diego contestó las 59 preguntas abiertas. 40 quedaron decididas, 9 esperan un documento suyo (COB-1 a COB-6: política de gastos de cobranza; PPER-8 y PPER-9: política de ejecución de la dación; CON-1: PUC en Excel) y no se vuelven a preguntar, y 10 se reemplazan por una pregunta concreta de la ronda 4 (PPER-1, USU-3, USU-8, CON-4, REC-6, ROL-1, A3, T5, R2, C5), más R4-QUIN, que confirma el valor de PPER para la quincena.

Hay 15 preguntas respondidas con salvedad que aún no cuentan como cerradas: PTOPE, PCOB, PDIAS y COB-1 a COB-6 esperan la política de gastos de cobranza; PPER-8 y PPER-9 la política de ejecución de la dación; C2 y CON-1 el PUC; X1 y X4 el Excel corregido.

## Tabla A · Reglas con pregunta en el cuestionario

### Pagos, mora y cobranza

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| P1 | Cuando un pago no alcanza, ¿se pagan primero los servicios o el capital? | Decidida | P-02 (16 oct 2026): Excel del 11 de octubre (Inputs ⑥): cobranza → mora → interés corriente → fondo de garantías → asistencia → seguro → capital. |
| P3 | ¿Cómo se calcula el interés de mora? | Decidida | P-03 (16 oct 2026): Mora = capital vencido × tasa periódica (la misma corriente); en pagos tardíos se suman los días de atraso de la cuota (caso 9). |
| PDOBLE | ¿El capital vencido paga interés corriente además de mora? | Decidida | P-03 (16 oct 2026): No: el interés corriente va sobre el capital vigente y el capital vencido paga solo mora. |
| PPER | ¿Con qué periodicidades se ofrecen los créditos y cómo se calculan? | Decidida | Ronda 3 (20 oct 2026): Se ofrecen semanal, quincenal y mensual según la negociación; la tasa del periodo es la conversión financiera de la E.A. (mensual 12, semanal 52, quincenal 24; el 24 se confirma en R4-QUIN). |
| PPER-1 | ¿En qué día vencen las cuotas quincenales y las mensuales? | Reemplazada por R4-MENSUAL-DIA | Ronda 3 (20 oct 2026): Quincenal: se paga el 5 y el 20. Mensual: "el 5 o el primer día del mes"; falta saber si es fija para todos o por crédito. No cuenta en el total: la cuenta R4-MENSUAL-DIA. |
| PPER-2 | ¿El plazo se pacta en meses o en número de cuotas? | Decidida | Ronda 3 (20 oct 2026): El plazo se pacta en número de cuotas según la periodicidad, con un máximo de 24 meses. |
| PPER-3 | ¿Cómo se redondea el número de cuotas cuando el plazo se pacta en meses? | Decidida | Ronda 3 (20 oct 2026): Si se pacta en meses, el número de cuotas se redondea hacia abajo: ROUNDDOWN(meses × periodos/12). |
| PPER-4 | ¿La periodicidad quincenal entra al catálogo de Inputs? ¿La diaria sigue siendo una opción real? | Decidida | Ronda 3 (20 oct 2026): Quincenal y mensual entran al catálogo; la periodicidad diaria se retira. |
| PPER-5 | En mensual, ¿la mora de un periodo se cuenta por el periodo nominal o por los días reales del mes (28 a 31)? ¿Desde qué día corre el atraso? | Decidida | Ronda 3 (20 oct 2026): En mensual, la mora se cuenta por los días reales del mes. |
| PPER-6 | ¿Cómo se expresa el plazo límite de recuperación: en periodos, en meses o en semanas? | Decidida | Ronda 3 (20 oct 2026): El plazo límite se expresa en periodos de la periodicidad del crédito. |
| PPER-7 | En un prepago a mitad de periodo, ¿se cobran los días transcurridos? | Decidida | Ronda 3 (20 oct 2026): En un prepago a mitad de periodo se cobran los días transcurridos. |
| PPER-8 | En mensual, ¿cuándo cuenta una cuota como vencida y cómo se cobra la cobranza? | Pendiente de un documento de Diego | Ronda 3 (20 oct 2026): Pendiente: política de ejecución de la dación. Diego enviará la política de ejecución de la dación; esta decisión va dentro de ella. |
| PPER-9 | Los "4 periodos de espera" del caso 11, ¿se cuentan en periodos o en días? | Pendiente de un documento de Diego | Ronda 3 (20 oct 2026): Pendiente: política de ejecución de la dación. Diego enviará la política de ejecución de la dación; esta decisión va dentro de ella. |
| PPER-10 | ¿La periodicidad puede cambiar durante la vida del crédito? | Decidida | Ronda 3 (20 oct 2026): La periodicidad no cambia durante el crédito; si hace falta, se liquida y se abre uno nuevo. |
| PTOPE | ¿Los gastos de cobranza tienen tope por pago? | Pendiente de un documento de Diego | Respuestas 20 oct: Pendiente: política de gastos de cobranza. Los gastos de cobranza dependen de una política que enviará Diego; el valor (3.000 por día) es un ejemplo, debe ser configurable y solo lo puede tocar un administrador. |
| P7 | Antes de una dación o retoma, ¿se causan mora y servicios mientras el cliente no paga? | Decidida | P-16 (16 oct 2026): Sí: en dación y retoma se cobran mora y cobranza. |
| P2 | ¿Cómo se redondean los valores? | Decidida | P-05 (16 oct 2026): Sin redondeo en el cálculo, como el Excel; solo el formato se muestra sin decimales. |
| PCOB | Antes de una dación o retoma, ¿cómo se cuentan los días de cobranza si el cliente no paga? | Pendiente de un documento de Diego | Respuestas 20 oct: Pendiente: política de gastos de cobranza. Cómo se cuentan los días de cobranza sin pago va dentro de la política de cobranza que enviará Diego. |
| PDIAS | Con cuotas semanales, un atraso de 14 días deja dos cuotas más vencidas. ¿Cómo se cobra? | Pendiente de un documento de Diego | Respuestas 20 oct: Pendiente: política de gastos de cobranza. Todo depende de la periodicidad (semanal, quincenal o mensual) y se negocia por crédito: no se encaja en un solo periodo. |
| COB-1 | Política de gastos de cobranza (1 de 6): ¿cuándo se activa el cobro? | Pendiente de un documento de Diego | Ronda 3 (20 oct 2026): Pendiente: política de gastos de cobranza. Diego enviará la política completa de gastos de cobranza; esta decisión va dentro de ella. |
| COB-2 | Política de gastos de cobranza (2 de 6): ¿se cobran por día o por evento? | Pendiente de un documento de Diego | Ronda 3 (20 oct 2026): Pendiente: política de gastos de cobranza. Diego enviará la política completa de gastos de cobranza; esta decisión va dentro de ella. |
| COB-3 | Política de gastos de cobranza (3 de 6): ¿cuál es el tope y sobre qué se mide? | Pendiente de un documento de Diego | Ronda 3 (20 oct 2026): Pendiente: política de gastos de cobranza. Diego enviará la política completa de gastos de cobranza; esta decisión va dentro de ella. |
| COB-4 | Política de gastos de cobranza (4 de 6): ¿se siguen cobrando después de la última cuota pactada? | Pendiente de un documento de Diego | Ronda 3 (20 oct 2026): Pendiente: política de gastos de cobranza. Diego enviará la política completa de gastos de cobranza; esta decisión va dentro de ella. |
| COB-5 | Política de gastos de cobranza (5 de 6): ¿quién autoriza que se exonere o se condone un cobro a un cliente? | Pendiente de un documento de Diego | Ronda 3 (20 oct 2026): Pendiente: política de gastos de cobranza. Diego enviará la política completa de gastos de cobranza; esta decisión va dentro de ella. |
| COB-6 | Política de gastos de cobranza (6 de 6): ¿el cobro de 3.000 por día es legal en el contrato? | Pendiente de un documento de Diego | Ronda 3 (20 oct 2026): Pendiente: política de gastos de cobranza. Diego enviará la política completa de gastos de cobranza; esta decisión va dentro de ella. |
| CAL-1 | Vencimiento los martes (1 de 3): ¿hasta qué momento del martes se paga a tiempo? | Decidida | Ronda 3 (20 oct 2026): La cuota es a tiempo hasta el fin del martes; el atraso empieza el miércoles (día 1). |
| CAL-2 | Vencimiento los martes (2 de 3): si el martes es festivo, ¿cuándo vence la cuota? | Decidida | Ronda 3 (20 oct 2026): Sin manejo de festivos. |
| CAL-3 | Vencimiento los martes (3 de 3): ¿cuándo vence la primera cuota y el martes es igual para todos los créditos? | Decidida | Ronda 3 (20 oct 2026): El martes es fijo para todos los créditos; la primera fecha se pacta en la oferta. |

| R4-QUIN | Quincenal: ¿son 24 periodos al año (y no 104)? | Abierta | Pregunta R4-QUIN del cuestionario (ronda 4) |
| R4-MENSUAL-DIA | Mensual: ¿el día de pago es fijo para todos o se elige por crédito? | Abierta | Pregunta R4-MENSUAL-DIA del cuestionario (ronda 4) |
### Servicios complementarios

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| S1 | ¿FCC y seguro de vida se calculan sobre el monto inicial o sobre el saldo? | Decidida | P-07 (16 oct 2026): Sobre el monto financiado inicial. En mora o no pago el fondo y la asistencia no se cobran; solo el seguro. |
| S2 | Con la cuota inicial del 10 %, ¿sobre qué valor se calculan FCC y seguro? | Decidida | P-07 · P-09 (16 oct 2026): Sobre el monto financiado, que ya descuenta el pago inicial. La inicial es opcional y la digita el asesor. |
| S3 | Los servicios son mensuales pero se cobran por semana. ¿Cómo se reparten? | Decidida | Respuestas 20 oct (20 oct 2026): Los servicios no se cobran por semana: se cobran según la configuración (periodicidad) del crédito; el ejemplo semanal es solo eso. |
| S4 | Si el crédito sigue con saldo después de la última cuota pactada, ¿se siguen cobrando servicios? | Decidida | P-17 (16 oct 2026): No aplica: antes del plazo límite el crédito termina en dación o retoma. |
| S5 | ¿A los terceros (FCC, asistencia, aseguradora) se les gira lo causado o lo recaudado? | Decidida | Excel 11 oct (16 oct 2026): Se gira lo recaudado de cada servicio: el Pago FCC del mes es la suma de lo aplicado al fondo. |
| S6 | Contablemente, ¿los servicios son un pasivo con terceros o un ingreso propio? | Decidida | Respuestas 20 oct (20 oct 2026): Se maneja como ingreso recibido para terceros: una cuenta por pagar contra el banco, porque la financiera recauda el dinero. Código del PUC por confirmar. |
| SRV-1 | Si el crédito termina a mitad de mes por un prepago, ¿se giran los servicios del mes completo al tercero? | Decidida | Ronda 3 (20 oct 2026): En un prepago se giran a los terceros solo los periodos causados, sin devolución. |
| SRV-2 | Si un abono acorta el plazo, ¿qué pasa con la cobertura del seguro y del fondo de garantías en las cuotas eliminadas? | Decidida | Ronda 3 (20 oct 2026): Si un abono acorta el plazo, la cobertura de seguro y fondo termina con el nuevo plazo. |
| SRV-3 | En la última cuota, ¿se cobran los servicios completos del periodo o se prorratean? | Decidida | Ronda 3 (20 oct 2026): La última cuota lleva los servicios completos. |
| SRV-4 | ¿La cuenta por pagar de servicios se separa por tercero y por IVA? | Decidida | Ronda 3 (20 oct 2026): Una cuenta por pagar por tercero y el IVA en su propia cuenta. |

### Abonos y prepago

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| A1 | ¿Quién elige si un abono extra reduce el plazo o la cuota? | Decidida | P-13 (16 oct 2026): El cliente elige; si no responde, se reduce el plazo. |
| A2 | Si un abono es mayor que el saldo, ¿qué pasa con el excedente? | Decidida | P-14 (16 oct 2026): No es posible: nunca se recibe más dinero que el saldo del crédito. |
| A3 | ¿Hay un monto mínimo para un abono extra? | Reemplazada por R4-A3 | Ronda 3 (20 oct 2026): Habló solo de la prelación; no dijo si hay un mínimo. No cuenta en el total: la cuenta R4-A3. |
| A4 | Si el cliente tiene capital vencido y hace un abono extra, ¿cómo se aplica? | Decidida | Respuestas 20 oct (20 oct 2026): Siempre se respeta la prelación de Inputs; los ítems 1 a 6 (cobranza, mora, interés, fondo, asistencia, seguro) no se cobran por adelantado y el excedente va solo a capital. |
| A5 | Al recalcular la cuota después de un abono, ¿con qué tasa? | Decidida | Respuestas 20 oct (20 oct 2026): Un abono extra recalcula la cuota o el plazo, pero no altera la tasa del crédito. |
| A6 | En un prepago total, ¿se cobra interés por los días desde la última cuota? | Decidida | Respuestas 20 oct (20 oct 2026): El prepago total cobra interés corriente por los días transcurridos desde la última cuota (sin comentario; por confirmar). |
| ABN-1 | Caso de validación: abono extra con pendientes. ¿Qué resultado espera? | Decidida | Ronda 3 (20 oct 2026): El equipo arma el caso 13 (abono extra con pendientes) y Diego valida los valores. |
| ABN-2 | Con capital vencido, ¿el excedente del abono cubre primero el capital vencido y luego el vigente? | Decidida | Ronda 3 (20 oct 2026): El excedente del abono cubre primero el capital vencido más antiguo y después el vigente. |
| ABN-3 | ¿Hay un máximo de abonos extra por mes o por crédito? | Decidida | Ronda 3 (20 oct 2026): Sin límite de abonos extra. |
| ABN-4 | Un abono extra recibido entre cuotas, ¿se aplica de una vez o junto con la siguiente cuota? | Decidida | Ronda 3 (20 oct 2026): Pagar antes del martes el valor exacto de una cuota ya causada es pago corriente, no anticipo; lo que supere ese valor es abono extra. |
| ABN-5 | Con varios abonos a menor cuota, ¿cada uno recalcula la cuota hasta la cuota pactada? | Decidida | Ronda 3 (20 oct 2026): Cada abono a menor cuota recalcula la cuota sobre las cuotas restantes. |
| ABN-6 | En un prepago total, ¿hay algún costo y cuándo se entrega el paz y salvo? | Decidida | Ronda 3 (20 oct 2026): El prepago no tiene costo; paz y salvo y liberación de la prenda en 30 días. |

| R4-A3 | ¿Se acepta cualquier abono extra o hay un valor mínimo? | Abierta | Pregunta R4-A3 del cuestionario (ronda 4) |
### Cambio de tasa y usura

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| T1 | ¿Desde cuándo aplica un cambio de tasa? | Decidida | Respuestas 20 oct (20 oct 2026): El cambio de tasa aplica desde la cuota siguiente, respetando la periodicidad del crédito. |
| T2 | Si la tasa supera la usura, ¿cuánto por debajo se ajusta? | Decidida | Respuestas 20 oct (20 oct 2026): El margen de usura se da en puntos porcentuales de la E.A.: 1 % equivale a 100 puntos básicos. |
| T3 | ¿En qué modalidad se registra el crédito y cuál es su tasa de usura vigente? | Decidida | P-01 (16 oct 2026): Tasa según el catálogo por línea de crédito: la máxima legal publicada cada mes, con actualización obligatoria por un autorizador. |
| T4 | Si la usura vuelve a subir, ¿el crédito regresa a la tasa pactada? | Decidida | Respuestas 20 oct (20 oct 2026): La tasa no vuelve a la pactada: si la usura baja, la tasa sigue bajando respetando el margen; si sube, se queda como está. |
| T5 | Si el cliente está en mora cuando cambia la tasa, ¿sobre qué saldo se recalcula la cuota? | Reemplazada por R4-T5 | Ronda 3 (20 oct 2026): Pidió investigar las cláusulas aceleratorias; no escogió un saldo. No cuenta en el total: la cuenta R4-T5. |
| USU-1 | Usura: ¿confirma que el cambio rige desde la cuota siguiente a la publicación, no desde la primera cuota del mes? | Decidida | Ronda 3 (20 oct 2026): La nueva usura rige desde la cuota siguiente a la publicación; se corrige el Excel. |
| USU-2 | Alerta mensual de usura (1 de 3): ¿quién es el autorizador que actualiza la usura? | Decidida | Ronda 3 (20 oct 2026): La actualiza alguien de riesgos o de la dirección financiera. |
| USU-3 | Alerta mensual de usura (2 de 3): si nadie autoriza a tiempo, ¿qué hace el sistema? | Reemplazada por R4-USURA-PLAZO | Ronda 3 (20 oct 2026): Si nadie autoriza, la usura se aplica sola; choca con USU-5 (el lote espera aprobación), así que falta el plazo. No cuenta en el total: la cuenta R4-USURA-PLAZO. |
| USU-4 | Alerta mensual de usura (3 de 3): ¿qué usura aplica a cada línea de crédito? | Decidida | Ronda 3 (20 oct 2026): Hay una usura por línea de crédito: la que publica la Superfinanciera el último día hábil de cada mes. |
| USU-5 | ¿Cuando la usura baja se recalculan todos los créditos vigentes o solo los nuevos? | Decidida | Ronda 3 (20 oct 2026): Se recalculan todos los créditos vigentes, con aprobación del lote. |
| USU-6 | Cambio de tasa en sus dos modalidades: ¿cuál se aplica cuando la tasa baja? | Decidida | Ronda 3 (20 oct 2026): Cuando la tasa baja, siempre se mantiene la misma cuota y baja el plazo (modalidad del caso 10). |
| USU-7 | ¿Qué otras causas externas, además de la usura, pueden cambiar la tasa de un crédito? | Decidida | Ronda 3 (20 oct 2026): Solo cambian la tasa la usura y la ley o regulación. |
| USU-8 | Tras un cambio de tasa, ¿cambian el fondo de garantías, la asistencia y el seguro? ¿Se avisa al cliente? | Reemplazada por R4-AVISO-CLIENTE | Ronda 3 (20 oct 2026): Los servicios (fondo, asistencia, seguro) no cambian con la tasa. Dijo que no se avisa al cliente; falta confirmarlo con legal. No cuenta en el total: la cuenta R4-AVISO-CLIENTE. |

| R4-T5 | Con el cliente en mora, ¿sobre qué saldo se recalcula la cuota al cambiar la tasa? | Abierta | Pregunta R4-T5 del cuestionario (ronda 4) |
| R4-USURA-PLAZO | Si nadie autoriza la usura, ¿a los cuántos días hábiles se aplica sola y vale para el lote de vigentes? | Abierta | Pregunta R4-USURA-PLAZO del cuestionario (ronda 4) |
| R4-AVISO-CLIENTE | ¿Se avisa al cliente cuando cambia la tasa o se valida con legal? | Abierta | Pregunta R4-AVISO-CLIENTE del cuestionario (ronda 4) |
### Dación y retoma

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| R1 | ¿Qué diferencia hay entre dación en pago y retoma? | Decidida | Respuestas 20 oct (20 oct 2026): Es un tema de terminología: dación es cuando el avalúo es menor al saldo (el diferencial se cobra al fondo de garantías); retoma, cuando es mayor y se devuelve dinero. |
| R2 | ¿Se descuentan costos de la recuperación (grúa, bodegaje, avalúo, trámites)? | Reemplazada por R4-R2 | Ronda 3 (20 oct 2026): Dijo que aún no tiene definido el valor. No cuenta en el total: la cuenta R4-R2. |
| R3 | ¿La moto entra al inventario por el avalúo o por el valor de venta? | Decidida | Respuestas 20 oct (20 oct 2026): La moto entra al inventario por el avalúo y la diferencia se ajusta después (sin comentario; por confirmar). |
| R4 | El interés de la semana del evento, ¿se cobra completo o por días? | Decidida | Respuestas 20 oct (20 oct 2026): Interés completo en la semana del evento, como el Excel (sin comentario; por confirmar). |
| REC-1 | Dación (1 de 3): ¿cuánto cubre el fondo de garantías del faltante? | Decidida | Ronda 3 (20 oct 2026): El fondo de garantías cubre todo el faltante de la dación. |
| REC-2 | Dación (2 de 3): ¿qué conceptos cubre el fondo: solo capital e intereses, o también mora, servicios y cobranza? | Decidida | Ronda 3 (20 oct 2026): El fondo cubre todos los conceptos: capital, intereses, mora, servicios y cobranza. |
| REC-3 | Dación (3 de 3): si el fondo no alcanza, ¿qué pasa con lo que falta? | Decidida | Ronda 3 (20 oct 2026): Si el fondo no alcanza, queda una cuenta por cobrar al cliente y pasa a cobro jurídico. |
| REC-4 | Retoma (1 de 2): ¿cuándo se le devuelve al cliente el excedente? | Decidida | Ronda 3 (20 oct 2026): En la retoma, primero se compensan otras obligaciones del cliente y se le devuelve el resto. |
| REC-5 | Retoma (2 de 2): ¿cómo se paga el excedente y de dónde sale el dinero? | Decidida | Ronda 3 (20 oct 2026): El excedente se transfiere menos los costos de la retoma, con soporte. |
| REC-6 | ¿Quién fija el valor de la dación o la retoma? | Reemplazada por R4-PERITO | Ronda 3 (20 oct 2026): Valor de dación o retoma = guía Fasecolda × 65 %, con perito externo como segunda opción; falta cuándo aplica el perito. No cuenta en el total: la cuenta R4-PERITO. |
| REC-7 | Si el cliente nunca se pone al día y llega al plazo límite con saldo, ¿qué ocurre? | Decidida | Ronda 3 (20 oct 2026): Al llegar al plazo límite con saldo, la recuperación (dación o retoma) es obligatoria. |
| REC-8 | La semana del siniestro o de la recuperación, ¿cuenta como cuota vencida? | Decidida | Ronda 3 (20 oct 2026): La semana del evento cuenta como vencida. |

| R4-R2 | Al recuperar la moto, ¿se descuenta el costo real con soporte? | Abierta | Pregunta R4-R2 del cuestionario (ronda 4) |
| R4-PERITO | Valor Fasecolda × 65 %: ¿cuándo se usa el perito externo? | Abierta | Pregunta R4-PERITO del cuestionario (ronda 4) |
### Contabilidad

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| C1 | ¿El interés corriente se causa cada semana o se registra cuando se recauda? | Decidida | Respuestas 20 oct (20 oct 2026): La causación sigue el plan normal (caso 1), respetando la periodicidad pactada. |
| C2 | ¿Cuáles son los códigos del plan de cuentas (PUC) de cada cuenta? | Pendiente de un documento de Diego | Respuestas 20 oct: Pendiente: PUC que enviará por correo (ver CON-1). Los códigos del PUC los enviará por correo; quedan pendientes. |
| C3 | ¿Quién recauda el pago inicial y cómo se registra? | Decidida | Respuestas 20 oct (20 oct 2026): El pago inicial lo recauda el concesionario y no afecta a la fábrica de crédito; se guarda para auditoría y para la simulación de la oferta comercial. |
| C5 | El pago inicial se resta del total de la operación (moto + alistamiento): ¿el GPS y los gastos comerciales hacen parte del alistamiento? | Reemplazada por R4-C5 | Ronda 3 (20 oct 2026): Marcó "incluye GPS y gastos comerciales" pero su detalle dice "moto + alistamiento"; se contradicen. No cuenta en el total: la cuenta R4-C5. |
| C4 | ¿Son correctas estas definiciones de la hoja Validaciones? | Decidida | P-24 (16 oct 2026): No tener en cuenta la hoja Validaciones. |
| CON-1 | Plan de cuentas (PUC): ¿cómo nos entrega los códigos de cada cuenta? | Pendiente de un documento de Diego | Ronda 3 (20 oct 2026): Pendiente: PUC en Excel. Diego enviará el plan de cuentas (PUC) en un Excel por correo. |
| CON-2 | ¿El PUC cambia según la línea de crédito? | Decidida | Ronda 3 (20 oct 2026): Mismas cuentas del PUC con un auxiliar por línea de crédito. |
| CON-3 | ¿Por cuáles canales llegan los pagos y cómo se sabe a qué crédito pertenecen? | Decidida | Ronda 3 (20 oct 2026): El CRM entrega cada pago asociado a su crédito. |
| CON-4 | Los gastos de cobranza, ¿son ingreso de la financiera o se giran a un tercero? | Reemplazada por R4-COBRANZA-CONTABLE | Ronda 3 (20 oct 2026): Los gastos de cobranza no son ingreso: son compensación de gastos reales; falta el tratamiento contable. No cuenta en el total: la cuenta R4-COBRANZA-CONTABLE. |

| R4-C5 | ¿El GPS y los gastos comerciales son parte del alistamiento? | Abierta | Pregunta R4-C5 del cuestionario (ronda 4) |
| R4-COBRANZA-CONTABLE | Los gastos de cobranza son compensación de gastos reales: ¿cómo se registran? | Abierta | Pregunta R4-COBRANZA-CONTABLE del cuestionario (ronda 4) |
### Roles y parámetros

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| ROL-1 | ¿Quién es el «administrador» que puede cambiar la tasa y los gastos de cobranza? | Reemplazada por R4-SUPLENTE | Ronda 3 (20 oct 2026): Un solo administrador, sin suplente; falta qué pasa si no está. No cuenta en el total: la cuenta R4-SUPLENTE. |
| ROL-2 | ¿Qué parámetros son sensibles y solo toca el administrador? | Decidida | Ronda 3 (20 oct 2026): Todos los parámetros del producto son sensibles. |
| ROL-3 | Cuando el administrador cambia un parámetro, ¿rige para los créditos vigentes o solo para los nuevos? | Decidida | Ronda 3 (20 oct 2026): Un cambio de parámetro rige solo para créditos nuevos, salvo la usura, que recalcula los vigentes desde la cuota siguiente. |
| ROL-4 | ¿Un cambio de parámetro sensible necesita segundo aprobador y queda en bitácora? | Decidida | Ronda 3 (20 oct 2026): El administrador cambia de inmediato, con bitácora (quién, qué, cuándo), sin segundo aprobador. |
| ROL-5 | Después del simulador y el motor de abonos, ¿cuál es el siguiente alcance? | Decidida | Ronda 3 (20 oct 2026): El siguiente alcance es el motor de scoring, antes de la fábrica de créditos. |

| R4-SUPLENTE | Un solo administrador sin suplente: ¿qué pasa si no está? | Abierta | Pregunta R4-SUPLENTE del cuestionario (ronda 4) |
### Ajustes al Excel

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| X1 | ¿Se corregirán estas inconsistencias en una nueva versión del Excel? | Pendiente de un documento de Diego | Respuestas 20 oct: Pendiente: Excel corregido. Enviará una versión corregida del Excel (sin comentario). |
| X4 | En el caso 8 el plazo límite sigue en 100 semanas, pero ahora el crédito tiene 104 cuotas. ¿Cuál debe ser el límite? | Pendiente de un documento de Diego | Respuestas 20 oct: Pendiente: Excel corregido. El plazo límite del caso 8 llega según el Excel que adjuntará. |
| X3 | En su respuesta sobre el plazo límite habla de los casos 4 o 5 como dación o retoma. ¿Se refiere a los casos 3 y 4? | Decidida | Respuestas 20 oct (20 oct 2026): Se refería a dación (caso 3) o retoma (caso 4). |


## Tabla B · Reglas sin pregunta en el cuestionario

| Regla | Estado | Fuente / detalle |
|---|---|---|
| Arquitectura: monolito modular en Java 21 con Spring Modulith | Decidida | D1, `adr/0001-monolito-modular.md` |
| Tasas efectivas: semanal = (1+EA)^(1/52) − 1 y diaria = (1+EA)^(1/365) − 1, en doble precisión como el Excel | Decidida | D9 |
| Pago inicial opcional que digita el asesor y se resta del total | Decidida | D10, P-09 (la base del total sigue abierta en C5) |
| «FCC» se llama fondo de garantías en el código y en la cuenta contable | Decidida | D14, P-07 |
| Fuente de verdad: Excel del 11 de octubre; línea CPPU 87,62 % E.A. con 104 cuotas semanales; catálogo de líneas | Decidida | D15, P-10 |
| Casos 10 (cambio de tasa conservando la cuota), 11 (pago inferior + dación) y 12 (normalización) | Decidida | D18 |
| Servicios sobre el monto financiado inicial, hasta la cuota pactada; en mora solo se causa el seguro | Decidida | D7, D17, P-07 |
| Periodicidad configurable por crédito (semanal, quincenal, mensual); las reglas de cálculo están en PPER y PPER-1 a PPER-10; la quincena se confirma en R4-QUIN | Decidida (principio) | D24, `adr/0002-periodicidad-configurable.md` |
| IVA del 19 % en fondo de garantías y asistencia; el seguro está exento | Decidida | P-08 |
| Los estados del Excel son la lista oficial (ampliable) | Decidida | P-21 |
| Las cuotas vencen los martes; sin manejo de festivos por ahora (el detalle del atraso está en CAL-1 a CAL-3) | Decidida | P-06 |
| P-12: orden de los conceptos en el pago anticipado | Pendiente de un documento de Diego | Remitió al Excel corregido |
| P-15 y P10: pagos que combinan eventos (tardío e incompleto a la vez); no hay caso de referencia | Pendiente de un documento de Diego | Remitió al Excel corregido; `pendientes-tecnicos.md` n.º 7 |
| P-25: el indicador del caso 9 está escrito a mano | Pendiente de un documento de Diego | Sin respuesta; se espera el Excel corregido |
| Definición de «cuotas eliminadas» (caso 6) y de «periodos adicionales» (caso 8, validación 28) | Pendiente de un documento de Diego | El Excel no las define; se esperan en el Excel corregido |
| Política de gastos de cobranza completa | Pendiente de un documento de Diego | Política que enviará Diego; cubre PTOPE, PCOB, R4-R2 y COB-1 a COB-6 |
| P-04: plan de cuentas (PUC), por línea y por cuenta | Pendiente de un documento de Diego | Lo enviará por correo; cubre C2 y CON-1 |
| P-23: reglas sin caso en el Excel (idempotencia, reversos, trazabilidad, clasificación de pagos) | Pendiente de un documento de Diego | Va a reunión; sin fecha |

## Cómo se mide el avance

1. Una regla pasa de abierta a decidida cuando se contesta su pregunta, se anota la fuente en `preguntas.ts` (`RESPONDIDAS_*`) y se actualiza esta tabla.
2. Una pregunta contestada con «Respondo en el comentario» cuenta como respondida en el cuestionario, pero aquí solo pasa a decidida cuando alguien del equipo lee el comentario y lo resume como regla.
3. Los porcentajes de esta tabla salen de contar los estados; la prueba `cuestionario.test.ts` verifica que cada pregunta del cuestionario aparezca aquí.
