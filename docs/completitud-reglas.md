# Completitud de las reglas del motor

Fuente para medir el avance de la definición de reglas con Diego González (dueño de producto). Estado al **2026-10-20**.

Cada regla o dato que el motor necesita está en una de dos tablas con uno de estos estados:

- **Decidida**: con su fuente (respuesta de Diego, decisión `D*` de `decisiones.md` o pregunta `P-*` del documento de Heider).
- **Abierta**: tiene una pregunta en el cuestionario (`cuestionario/src/preguntas.ts`) y se indica su id.
- **Pendiente de un documento de Diego**: ya contestó o remitió a un archivo que aún no llega (Excel corregido, política de cobranza, PUC) o a una reunión.

Solo las **decididas** cuentan como cerradas. Una pregunta respondida con una salvedad vigente (por ejemplo, PTOPE depende de la política de cobranza) cuenta como pendiente de documento.

Fuentes usadas: `respuestas-diego-2026-10-20.md` (sección «Pendiente de Diego»), `respuestas-diego-2026-10-16.md` (P-01 a P-27), `decisiones.md` (D1 a D25), `reglas/caso-1.md` a `caso-9.md` (preguntas abiertas de cada caso), `adr/0002-periodicidad-configurable.md`, `pendientes-tecnicos.md` y `preguntas-diego.md`.

## Resumen

| Bloque del cuestionario | Cerradas | Total | % |
|---|---|---|---|
| Pagos, mora y cobranza | 5 | 28 | 18 % |
| Servicios complementarios | 6 | 10 | 60 % |
| Abonos y prepago | 5 | 12 | 42 % |
| Cambio de tasa y usura | 4 | 13 | 31 % |
| Dación y retoma | 3 | 12 | 25 % |
| Contabilidad | 3 | 9 | 33 % |
| Roles y parámetros | 0 | 5 | 0 % |
| Ajustes al Excel | 1 | 3 | 33 % |
| **Tabla A (preguntas del cuestionario)** | **27** | **92** | **29 %** |
| Tabla B (reglas fuera del cuestionario) | 11 | 18 | 61 % |
| **Total de reglas inventariadas** | **38** | **110** | **35 %** |

Las 92 reglas de la tabla A son las 48 de rondas anteriores (33 respondidas, 15 abiertas) más 44 preguntas nuevas de la ronda 3 (ids `COB`, `CAL`, `SRV`, `ABN`, `USU`, `REC`, `CON` y `ROL`). El cuestionario publicado muestra el porcentaje de la tabla A.

Hay 6 preguntas respondidas con salvedad que aún no cuentan como cerradas: tres esperan la política de gastos de cobranza (PTOPE, PCOB, PDIAS), una el PUC (C2) y dos el Excel corregido (X1, X4).

## Tabla A · Reglas con pregunta en el cuestionario

### Pagos, mora y cobranza

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| P1 | Cuando un pago no alcanza, ¿se pagan primero los servicios o el capital? | Decidida | P-02 (16 oct 2026): Excel del 11 de octubre (Inputs ⑥): cobranza → mora → interés corriente → fondo de garantías → asistencia → seguro → capital. |
| P3 | ¿Cómo se calcula el interés de mora? | Decidida | P-03 (16 oct 2026): Mora = capital vencido × tasa periódica (la misma corriente); en pagos tardíos se suman los días de atraso de la cuota (caso 9). |
| PDOBLE | ¿El capital vencido paga interés corriente además de mora? | Decidida | P-03 (16 oct 2026): No: el interés corriente va sobre el capital vigente y el capital vencido paga solo mora. |
| PPER | ¿Con qué periodicidades se ofrecen los créditos y cómo se calculan? | Abierta | Pregunta PPER del cuestionario |
| PPER-1 | ¿En qué día vencen las cuotas quincenales y las mensuales? | Abierta | Pregunta PPER-1 del cuestionario |
| PPER-2 | ¿El plazo se pacta en meses o en número de cuotas? | Abierta | Pregunta PPER-2 del cuestionario |
| PPER-3 | ¿Cómo se redondea el número de cuotas cuando el plazo se pacta en meses? | Abierta | Pregunta PPER-3 del cuestionario |
| PPER-4 | ¿La periodicidad quincenal entra al catálogo de Inputs? ¿La diaria sigue siendo una opción real? | Abierta | Pregunta PPER-4 del cuestionario |
| PPER-5 | En mensual, ¿la mora de un periodo se cuenta por el periodo nominal o por los días reales del mes (28 a 31)? ¿Desde qué día corre el atraso? | Abierta | Pregunta PPER-5 del cuestionario |
| PPER-6 | ¿Cómo se expresa el plazo límite de recuperación: en periodos, en meses o en semanas? | Abierta | Pregunta PPER-6 del cuestionario |
| PPER-7 | En un prepago a mitad de periodo, ¿se cobran los días transcurridos? | Abierta | Pregunta PPER-7 del cuestionario |
| PPER-8 | En mensual, ¿cuándo cuenta una cuota como vencida y cómo se cobra la cobranza? | Abierta | Pregunta PPER-8 del cuestionario |
| PPER-9 | Los "4 periodos de espera" del caso 11, ¿se cuentan en periodos o en días? | Abierta | Pregunta PPER-9 del cuestionario |
| PPER-10 | ¿La periodicidad puede cambiar durante la vida del crédito? | Abierta | Pregunta PPER-10 del cuestionario |
| PTOPE | ¿Los gastos de cobranza tienen tope por pago? | Pendiente de un documento de Diego | Respuestas 20 oct: Pendiente: política de gastos de cobranza. Los gastos de cobranza dependen de una política que enviará Diego; el valor (3.000 por día) es un ejemplo, debe ser configurable y solo lo puede tocar un administrador. |
| P7 | Antes de una dación o retoma, ¿se causan mora y servicios mientras el cliente no paga? | Decidida | P-16 (16 oct 2026): Sí: en dación y retoma se cobran mora y cobranza. |
| P2 | ¿Cómo se redondean los valores? | Decidida | P-05 (16 oct 2026): Sin redondeo en el cálculo, como el Excel; solo el formato se muestra sin decimales. |
| PCOB | Antes de una dación o retoma, ¿cómo se cuentan los días de cobranza si el cliente no paga? | Pendiente de un documento de Diego | Respuestas 20 oct: Pendiente: política de gastos de cobranza. Cómo se cuentan los días de cobranza sin pago va dentro de la política de cobranza que enviará Diego. |
| PDIAS | Con cuotas semanales, un atraso de 14 días deja dos cuotas más vencidas. ¿Cómo se cobra? | Pendiente de un documento de Diego | Respuestas 20 oct: Pendiente: política de gastos de cobranza. Todo depende de la periodicidad (semanal, quincenal o mensual) y se negocia por crédito: no se encaja en un solo periodo. |
| COB-1 | Política de gastos de cobranza (1 de 6): ¿cuándo se activa el cobro? | Abierta | Pregunta COB-1 del cuestionario |
| COB-2 | Política de gastos de cobranza (2 de 6): ¿se cobran por día o por evento? | Abierta | Pregunta COB-2 del cuestionario |
| COB-3 | Política de gastos de cobranza (3 de 6): ¿cuál es el tope y sobre qué se mide? | Abierta | Pregunta COB-3 del cuestionario |
| COB-4 | Política de gastos de cobranza (4 de 6): ¿se siguen cobrando después de la última cuota pactada? | Abierta | Pregunta COB-4 del cuestionario |
| COB-5 | Política de gastos de cobranza (5 de 6): ¿quién autoriza que se exonere o se condone un cobro a un cliente? | Abierta | Pregunta COB-5 del cuestionario |
| COB-6 | Política de gastos de cobranza (6 de 6): ¿el cobro de 3.000 por día es legal en el contrato? | Abierta | Pregunta COB-6 del cuestionario |
| CAL-1 | Vencimiento los martes (1 de 3): ¿hasta qué momento del martes se paga a tiempo? | Abierta | Pregunta CAL-1 del cuestionario |
| CAL-2 | Vencimiento los martes (2 de 3): si el martes es festivo, ¿cuándo vence la cuota? | Abierta | Pregunta CAL-2 del cuestionario |
| CAL-3 | Vencimiento los martes (3 de 3): ¿cuándo vence la primera cuota y el martes es igual para todos los créditos? | Abierta | Pregunta CAL-3 del cuestionario |

### Servicios complementarios

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| S1 | ¿FCC y seguro de vida se calculan sobre el monto inicial o sobre el saldo? | Decidida | P-07 (16 oct 2026): Sobre el monto financiado inicial. En mora o no pago el fondo y la asistencia no se cobran; solo el seguro. |
| S2 | Con la cuota inicial del 10 %, ¿sobre qué valor se calculan FCC y seguro? | Decidida | P-07 · P-09 (16 oct 2026): Sobre el monto financiado, que ya descuenta el pago inicial. La inicial es opcional y la digita el asesor. |
| S3 | Los servicios son mensuales pero se cobran por semana. ¿Cómo se reparten? | Decidida | Respuestas 20 oct (20 oct 2026): Los servicios no se cobran por semana: se cobran según la configuración (periodicidad) del crédito; el ejemplo semanal es solo eso. |
| S4 | Si el crédito sigue con saldo después de la última cuota pactada, ¿se siguen cobrando servicios? | Decidida | P-17 (16 oct 2026): No aplica: antes del plazo límite el crédito termina en dación o retoma. |
| S5 | ¿A los terceros (FCC, asistencia, aseguradora) se les gira lo causado o lo recaudado? | Decidida | Excel 11 oct (16 oct 2026): Se gira lo recaudado de cada servicio: el Pago FCC del mes es la suma de lo aplicado al fondo. |
| S6 | Contablemente, ¿los servicios son un pasivo con terceros o un ingreso propio? | Decidida | Respuestas 20 oct (20 oct 2026): Se maneja como ingreso recibido para terceros: una cuenta por pagar contra el banco, porque la financiera recauda el dinero. Código del PUC por confirmar. |
| SRV-1 | Si el crédito termina a mitad de mes por un prepago, ¿se giran los servicios del mes completo al tercero? | Abierta | Pregunta SRV-1 del cuestionario |
| SRV-2 | Si un abono acorta el plazo, ¿qué pasa con la cobertura del seguro y del fondo de garantías en las cuotas eliminadas? | Abierta | Pregunta SRV-2 del cuestionario |
| SRV-3 | En la última cuota, ¿se cobran los servicios completos del periodo o se prorratean? | Abierta | Pregunta SRV-3 del cuestionario |
| SRV-4 | ¿La cuenta por pagar de servicios se separa por tercero y por IVA? | Abierta | Pregunta SRV-4 del cuestionario |

### Abonos y prepago

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| A1 | ¿Quién elige si un abono extra reduce el plazo o la cuota? | Decidida | P-13 (16 oct 2026): El cliente elige; si no responde, se reduce el plazo. |
| A2 | Si un abono es mayor que el saldo, ¿qué pasa con el excedente? | Decidida | P-14 (16 oct 2026): No es posible: nunca se recibe más dinero que el saldo del crédito. |
| A3 | ¿Hay un monto mínimo para un abono extra? | Abierta | Pregunta A3 del cuestionario |
| A4 | Si el cliente tiene capital vencido y hace un abono extra, ¿cómo se aplica? | Decidida | Respuestas 20 oct (20 oct 2026): Siempre se respeta la prelación de Inputs; los ítems 1 a 6 (cobranza, mora, interés, fondo, asistencia, seguro) no se cobran por adelantado y el excedente va solo a capital. |
| A5 | Al recalcular la cuota después de un abono, ¿con qué tasa? | Decidida | Respuestas 20 oct (20 oct 2026): Un abono extra recalcula la cuota o el plazo, pero no altera la tasa del crédito. |
| A6 | En un prepago total, ¿se cobra interés por los días desde la última cuota? | Decidida | Respuestas 20 oct (20 oct 2026): El prepago total cobra interés corriente por los días transcurridos desde la última cuota (sin comentario; por confirmar). |
| ABN-1 | Caso de validación: abono extra con pendientes. ¿Qué resultado espera? | Abierta | Pregunta ABN-1 del cuestionario |
| ABN-2 | Con capital vencido, ¿el excedente del abono cubre primero el capital vencido y luego el vigente? | Abierta | Pregunta ABN-2 del cuestionario |
| ABN-3 | ¿Hay un máximo de abonos extra por mes o por crédito? | Abierta | Pregunta ABN-3 del cuestionario |
| ABN-4 | Un abono extra recibido entre cuotas, ¿se aplica de una vez o junto con la siguiente cuota? | Abierta | Pregunta ABN-4 del cuestionario |
| ABN-5 | Con varios abonos a menor cuota, ¿cada uno recalcula la cuota hasta la cuota pactada? | Abierta | Pregunta ABN-5 del cuestionario |
| ABN-6 | En un prepago total, ¿hay algún costo y cuándo se entrega el paz y salvo? | Abierta | Pregunta ABN-6 del cuestionario |

### Cambio de tasa y usura

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| T1 | ¿Desde cuándo aplica un cambio de tasa? | Decidida | Respuestas 20 oct (20 oct 2026): El cambio de tasa aplica desde la cuota siguiente, respetando la periodicidad del crédito. |
| T2 | Si la tasa supera la usura, ¿cuánto por debajo se ajusta? | Decidida | Respuestas 20 oct (20 oct 2026): El margen de usura se da en puntos porcentuales de la E.A.: 1 % equivale a 100 puntos básicos. |
| T3 | ¿En qué modalidad se registra el crédito y cuál es su tasa de usura vigente? | Decidida | P-01 (16 oct 2026): Tasa según el catálogo por línea de crédito: la máxima legal publicada cada mes, con actualización obligatoria por un autorizador. |
| T4 | Si la usura vuelve a subir, ¿el crédito regresa a la tasa pactada? | Decidida | Respuestas 20 oct (20 oct 2026): La tasa no vuelve a la pactada: si la usura baja, la tasa sigue bajando respetando el margen; si sube, se queda como está. |
| T5 | Si el cliente está en mora cuando cambia la tasa, ¿sobre qué saldo se recalcula la cuota? | Abierta | Pregunta T5 del cuestionario |
| USU-1 | Usura: ¿confirma que el cambio rige desde la cuota siguiente a la publicación, no desde la primera cuota del mes? | Abierta | Pregunta USU-1 del cuestionario |
| USU-2 | Alerta mensual de usura (1 de 3): ¿quién es el autorizador que actualiza la usura? | Abierta | Pregunta USU-2 del cuestionario |
| USU-3 | Alerta mensual de usura (2 de 3): si nadie autoriza a tiempo, ¿qué hace el sistema? | Abierta | Pregunta USU-3 del cuestionario |
| USU-4 | Alerta mensual de usura (3 de 3): ¿qué usura aplica a cada línea de crédito? | Abierta | Pregunta USU-4 del cuestionario |
| USU-5 | ¿Cuando la usura baja se recalculan todos los créditos vigentes o solo los nuevos? | Abierta | Pregunta USU-5 del cuestionario |
| USU-6 | Cambio de tasa en sus dos modalidades: ¿cuál se aplica cuando la tasa baja? | Abierta | Pregunta USU-6 del cuestionario |
| USU-7 | ¿Qué otras causas externas, además de la usura, pueden cambiar la tasa de un crédito? | Abierta | Pregunta USU-7 del cuestionario |
| USU-8 | Tras un cambio de tasa, ¿cambian el fondo de garantías, la asistencia y el seguro? ¿Se avisa al cliente? | Abierta | Pregunta USU-8 del cuestionario |

### Dación y retoma

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| R1 | ¿Qué diferencia hay entre dación en pago y retoma? | Decidida | Respuestas 20 oct (20 oct 2026): Es un tema de terminología: dación es cuando el avalúo es menor al saldo (el diferencial se cobra al fondo de garantías); retoma, cuando es mayor y se devuelve dinero. |
| R2 | ¿Se descuentan costos de la recuperación (grúa, bodegaje, avalúo, trámites)? | Abierta | Pregunta R2 del cuestionario |
| R3 | ¿La moto entra al inventario por el avalúo o por el valor de venta? | Decidida | Respuestas 20 oct (20 oct 2026): La moto entra al inventario por el avalúo y la diferencia se ajusta después (sin comentario; por confirmar). |
| R4 | El interés de la semana del evento, ¿se cobra completo o por días? | Decidida | Respuestas 20 oct (20 oct 2026): Interés completo en la semana del evento, como el Excel (sin comentario; por confirmar). |
| REC-1 | Dación (1 de 3): ¿cuánto cubre el fondo de garantías del faltante? | Abierta | Pregunta REC-1 del cuestionario |
| REC-2 | Dación (2 de 3): ¿qué conceptos cubre el fondo: solo capital e intereses, o también mora, servicios y cobranza? | Abierta | Pregunta REC-2 del cuestionario |
| REC-3 | Dación (3 de 3): si el fondo no alcanza, ¿qué pasa con lo que falta? | Abierta | Pregunta REC-3 del cuestionario |
| REC-4 | Retoma (1 de 2): ¿cuándo se le devuelve al cliente el excedente? | Abierta | Pregunta REC-4 del cuestionario |
| REC-5 | Retoma (2 de 2): ¿cómo se paga el excedente y de dónde sale el dinero? | Abierta | Pregunta REC-5 del cuestionario |
| REC-6 | ¿Quién fija el valor de la dación o la retoma? | Abierta | Pregunta REC-6 del cuestionario |
| REC-7 | Si el cliente nunca se pone al día y llega al plazo límite con saldo, ¿qué ocurre? | Abierta | Pregunta REC-7 del cuestionario |
| REC-8 | La semana del siniestro o de la recuperación, ¿cuenta como cuota vencida? | Abierta | Pregunta REC-8 del cuestionario |

### Contabilidad

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| C1 | ¿El interés corriente se causa cada semana o se registra cuando se recauda? | Decidida | Respuestas 20 oct (20 oct 2026): La causación sigue el plan normal (caso 1), respetando la periodicidad pactada. |
| C2 | ¿Cuáles son los códigos del plan de cuentas (PUC) de cada cuenta? | Pendiente de un documento de Diego | Respuestas 20 oct: Pendiente: PUC que enviará por correo (ver CON-1). Los códigos del PUC los enviará por correo; quedan pendientes. |
| C3 | ¿Quién recauda el pago inicial y cómo se registra? | Decidida | Respuestas 20 oct (20 oct 2026): El pago inicial lo recauda el concesionario y no afecta a la fábrica de crédito; se guarda para auditoría y para la simulación de la oferta comercial. |
| C5 | El pago inicial se resta del total de la operación (moto + alistamiento): ¿el GPS y los gastos comerciales hacen parte del alistamiento? | Abierta | Pregunta C5 del cuestionario |
| C4 | ¿Son correctas estas definiciones de la hoja Validaciones? | Decidida | P-24 (16 oct 2026): No tener en cuenta la hoja Validaciones. |
| CON-1 | Plan de cuentas (PUC): ¿cómo nos entrega los códigos de cada cuenta? | Abierta | Pregunta CON-1 del cuestionario |
| CON-2 | ¿El PUC cambia según la línea de crédito? | Abierta | Pregunta CON-2 del cuestionario |
| CON-3 | ¿Por cuáles canales llegan los pagos y cómo se sabe a qué crédito pertenecen? | Abierta | Pregunta CON-3 del cuestionario |
| CON-4 | Los gastos de cobranza, ¿son ingreso de la financiera o se giran a un tercero? | Abierta | Pregunta CON-4 del cuestionario |

### Roles y parámetros

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| ROL-1 | ¿Quién es el «administrador» que puede cambiar la tasa y los gastos de cobranza? | Abierta | Pregunta ROL-1 del cuestionario |
| ROL-2 | ¿Qué parámetros son sensibles y solo toca el administrador? | Abierta | Pregunta ROL-2 del cuestionario |
| ROL-3 | Cuando el administrador cambia un parámetro, ¿rige para los créditos vigentes o solo para los nuevos? | Abierta | Pregunta ROL-3 del cuestionario |
| ROL-4 | ¿Un cambio de parámetro sensible necesita segundo aprobador y queda en bitácora? | Abierta | Pregunta ROL-4 del cuestionario |
| ROL-5 | Después del simulador y el motor de abonos, ¿cuál es el siguiente alcance? | Abierta | Pregunta ROL-5 del cuestionario |

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
| Periodicidad configurable por crédito (semanal, quincenal, mensual); las reglas de cálculo están en PPER y PPER-1 a PPER-10 | Decidida (principio) | D24, `adr/0002-periodicidad-configurable.md` |
| IVA del 19 % en fondo de garantías y asistencia; el seguro está exento | Decidida | P-08 |
| Los estados del Excel son la lista oficial (ampliable) | Decidida | P-21 |
| Las cuotas vencen los martes; sin manejo de festivos por ahora (el detalle del atraso está en CAL-1 a CAL-3) | Decidida | P-06 |
| P-12: orden de los conceptos en el pago anticipado | Pendiente de un documento de Diego | Remitió al Excel corregido |
| P-15 y P10: pagos que combinan eventos (tardío e incompleto a la vez); no hay caso de referencia | Pendiente de un documento de Diego | Remitió al Excel corregido; `pendientes-tecnicos.md` n.º 7 |
| P-25: el indicador del caso 9 está escrito a mano | Pendiente de un documento de Diego | Sin respuesta; se espera el Excel corregido |
| Definición de «cuotas eliminadas» (caso 6) y de «periodos adicionales» (caso 8, validación 28) | Pendiente de un documento de Diego | El Excel no las define; se esperan en el Excel corregido |
| Política de gastos de cobranza completa | Pendiente de un documento de Diego | Política que enviará Diego; cubre PTOPE, PCOB, R2 y COB-1 a COB-6 |
| P-04: plan de cuentas (PUC), por línea y por cuenta | Pendiente de un documento de Diego | Lo enviará por correo; cubre C2, CON-1 y CON-2 |
| P-23: reglas sin caso en el Excel (idempotencia, reversos, trazabilidad, clasificación de pagos) | Pendiente de un documento de Diego | Va a reunión; sin fecha |

## Cómo se mide el avance

1. Una regla pasa de abierta a decidida cuando se contesta su pregunta, se anota la fuente en `preguntas.ts` (`RESPONDIDAS_*`) y se actualiza esta tabla.
2. Una pregunta contestada con «Respondo en el comentario» cuenta como respondida en el cuestionario, pero aquí solo pasa a decidida cuando alguien del equipo lee el comentario y lo resume como regla.
3. Los porcentajes de esta tabla salen de contar los estados; la prueba `cuestionario.test.ts` verifica que cada pregunta del cuestionario aparezca aquí.
