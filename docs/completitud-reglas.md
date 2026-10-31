# Completitud de las reglas del motor

Fuente para medir el avance de la definición de reglas con Diego González (dueño de producto). Estado al **2026-10-31** (rondas 4 a 9 respondidas; 9 de las 11 de las rondas 10 y 11 respondidas el 31 oct; respuestas por documento del 22 oct incorporadas; ronda 10 abierta con 2 preguntas; ronda 11 cerrada; ronda 12 abierta con 11).

Cada regla o dato que el motor necesita está en una de dos tablas con uno de estos estados:

- **Decidida**: con su fuente (respuesta de Diego, decisión `D*` de `decisiones.md` o pregunta `P-*` del documento de Heider).
- **Abierta**: tiene una pregunta en el cuestionario (`cuestionario/src/preguntas.ts`) y se indica su id.
- **Reemplazada**: se volvió a preguntar en la ronda 4, 5 o 6 con una versión concreta; la cuenta la pregunta nueva (`R4-*`, `P5-*` o `R6-*`).
- **Pendiente de un documento de Diego**: ya contestó o remitió a un archivo que aún no llega (Excel corregido, política de cobranza, PUC) o a una reunión.

Solo las **decididas** cuentan como cerradas. Una pregunta respondida con una salvedad vigente (por ejemplo, PTOPE depende de la política de cobranza) cuenta como pendiente de documento.

Fuentes usadas: `respuestas-diego-2026-10-20.md` (sección «Pendiente de Diego»), `respuestas-diego-2026-10-16.md` (P-01 a P-27), `decisiones.md` (D1 a D25), `reglas/caso-1.md` a `caso-9.md` (preguntas abiertas de cada caso), `adr/0002-periodicidad-configurable.md`, `pendientes-tecnicos.md` y `preguntas-diego.md`.

## Resumen

| Bloque del cuestionario | Cerradas | Total | % |
|---|---|---|---|
| Pagos, mora y cobranza | 32 | 47 | 68 % |
| Servicios complementarios | 17 | 20 | 85 % |
| Abonos y prepago | 16 | 16 | 100 % |
| Cambio de tasa y usura | 14 | 14 | 100 % |
| Dación y retoma | 17 | 20 | 85 % |
| Contabilidad | 7 | 10 | 70 % |
| Roles y parámetros | 5 | 5 | 100 % |
| Ajustes al Excel | 3 | 3 | 100 % |
| **Tabla A (preguntas del cuestionario)** | **111** | **135** | **82 %** |
| Tabla B (reglas fuera del cuestionario) | 11 | 18 | 61 % |
| **Total de reglas inventariadas** | **122** | **153** | **80 %** |

Las 135 reglas de la tabla A (111 cerradas = 82,2 % → 82 %; con la tabla B, 122 de 153 = 79,7 % → 80 %) son las 92 preguntas de las rondas 1 a 3 (de las que 10 se volvieron a preguntar y no se cuentan dos veces), las 16 de la ronda 4 (ids `R4-*`, de las que R4-CASO13 se vuelve a preguntar y no se cuenta dos veces), las 6 de la ronda 5 (ids `P5-*`, de las que P5-LIMITE-DESDE se reemplaza por R6-LIMITE-CORTO y no se cuenta dos veces) y las 4 de la ronda 6 (ids `R6-*`, de las que R6-COBRANZA-ACTIVA y R6-RECUP-VS-CASO11 se reemplazan por R7-COBRANZA-ACTIVA y R7-RECUP-VS-CASO11, las 2 de la ronda 7, y no se cuentan dos veces) las 4 de la ronda 8 (ids `R8-*`, respondidas el 28 oct y decididas en D58 a D61; R8-PAGO-INCOMPLETO-EN-FECHA cuenta como decidida aunque deja dos detalles a la ronda 9) las 3 de la ronda 9 (ids `R9-*`, respondidas el 30 oct y decididas en D62 a D64; R9-SEGURO-TODO-RIESGO cuenta como decidida aunque deja cuatro detalles a la ronda 11), las 7 de la ronda 10 (ids `R10-*`, salidas del backtesting con el historial real de pagos) y las 4 de la ronda 11 (ids `R11-*`, seguimiento de la ronda 9); de ellas, 9 se respondieron el 31 oct (D65 a D73; R10-PRIMERA-CUOTA y R10-CONTADOR-VENCIDAS llevan salvedad y no cuentan como cerradas), y las 11 de la ronda 12 (ids `R12-*`, abiertas, detalles de esas respuestas; 9 salen del rediseño de servicios y cobranza, D70 a D72). El cuestionario publicado muestra el porcentaje de la tabla A.

Ronda 3 (20 oct 2026, 15:54): Diego contestó las 59 preguntas abiertas. 40 quedaron decididas, 9 esperan un documento suyo (COB-1 a COB-6: política de gastos de cobranza; PPER-8 y PPER-9: política de ejecución de la dación; CON-1: PUC en Excel) y no se vuelven a preguntar, y 10 se reemplazan por una pregunta concreta de la ronda 4 (PPER-1, USU-3, USU-8, CON-4, REC-6, ROL-1, A3, T5, R2, C5), más R4-QUIN, que confirma el valor de PPER para la quincena.

Ronda 4 (21 oct 2026, 17:21): Diego contestó las 16 preguntas. 14 quedaron decididas (D35 a D41 de `decisiones.md`), R4-T5 quedó sin respuesta ("pendiente por dar respuesta"; la contestó el 22 oct por documento) y R4-CASO13 cambió la regla (D37): se vuelve a validar con las cifras nuevas en P5-CASO13. Lo no seguro pasó a la ronda 5: P5-PRORRATEO, P5-LIMITE-DESDE, P5-PAGO-ANTES-PRIMERA, P5-CASO13, P5-COSTO-DACION y P5-USURA-DIA. Detalle en `respuestas-diego-2026-10-21-ronda4.md`.

Respuestas por documento (22 oct 2026, «rptas software.docx»): Diego contestó por Word 8 preguntas que estaban pendientes. 7 quedaron decididas (R4-T5, X1, COB-4, COB-5, COB-6, PPER-9 y X4; D42 a D47 de `decisiones.md`) y PPER-8 sigue pendiente de la política de gastos de cobranza. X4 contesta solo en parte P5-LIMITE-DESDE, que se reemplaza por R6-LIMITE-CORTO. Lo no seguro pasó a la ronda 6: R6-RECUP-AUTO, R6-RECUP-VS-CASO11, R6-COBRANZA-ACTIVA y R6-LIMITE-CORTO. Detalle en `respuestas-diego-2026-10-22-documento.md`.

Rondas 5 y 6 (22 oct 2026, 19:53): Diego contestó las 9 preguntas y en las 9 coinciden la opción marcada y el comentario. 7 quedaron decididas (P5-PRORRATEO, P5-PAGO-ANTES-PRIMERA, P5-CASO13, P5-USURA-DIA, P5-COSTO-DACION, R6-LIMITE-CORTO y R6-RECUP-AUTO; D48 a D54 de `decisiones.md`) y 2 las dejó pendientes de la política de cobranza (R6-COBRANZA-ACTIVA y R6-RECUP-VS-CASO11); se reformulan en la ronda 7 (R7-COBRANZA-ACTIVA y R7-RECUP-VS-CASO11). Detalle en `respuestas-diego-2026-10-22-ronda5-6.md`.

Hay 9 preguntas respondidas con salvedad que aún no cuentan como cerradas: PTOPE, PCOB, PDIAS, COB-1 a COB-3 y PPER-8 esperan la política de gastos de cobranza; C2 y CON-1 el PUC (la política de ejecución de la dación la sigue esperando, ya con el umbral de 2 cuotas de PPER-9).

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
| PPER-8 | En mensual, ¿cuándo cuenta una cuota como vencida y cómo se cobra la cobranza? | Pendiente de un documento de Diego | Documento 22 oct (22 oct 2026): Pendiente: política de gastos de cobranza. Dijo que este tema está pendiente de definir y es de los gastos de cobranza; no se vuelve a preguntar. |
| PPER-9 | Los "4 periodos de espera" del caso 11, ¿se cuentan en periodos o en días? | Decidida | Documento 22 oct (22 oct 2026): La política es ejecutar la dación o la retoma luego de 2 cuotas atrasadas, en periodos de la periodicidad pactada (R6-RECUP-AUTO y R6-RECUP-VS-CASO11). D46. |
| PPER-10 | ¿La periodicidad puede cambiar durante la vida del crédito? | Decidida | Ronda 3 (20 oct 2026): La periodicidad no cambia durante el crédito; si hace falta, se liquida y se abre uno nuevo. |
| PTOPE | ¿Los gastos de cobranza tienen tope por pago? | Pendiente de un documento de Diego | Respuestas 20 oct: Pendiente: política de gastos de cobranza. Los gastos de cobranza dependen de una política que enviará Diego; el valor (3.000 por día) es un ejemplo, debe ser configurable y solo lo puede tocar un administrador. |
| P7 | Antes de una dación o retoma, ¿se causan mora y servicios mientras el cliente no paga? | Decidida | P-16 (16 oct 2026): Sí: en dación y retoma se cobran mora y cobranza. |
| P2 | ¿Cómo se redondean los valores? | Decidida | P-05 (16 oct 2026): Sin redondeo en el cálculo, como el Excel; solo el formato se muestra sin decimales. |
| PCOB | Antes de una dación o retoma, ¿cómo se cuentan los días de cobranza si el cliente no paga? | Pendiente de un documento de Diego | Respuestas 20 oct: Pendiente: política de gastos de cobranza. Cómo se cuentan los días de cobranza sin pago va dentro de la política de cobranza que enviará Diego. |
| PDIAS | Con cuotas semanales, un atraso de 14 días deja dos cuotas más vencidas. ¿Cómo se cobra? | Pendiente de un documento de Diego | Respuestas 20 oct: Pendiente: política de gastos de cobranza. Todo depende de la periodicidad (semanal, quincenal o mensual) y se negocia por crédito: no se encaja en un solo periodo. |
| COB-1 | Política de gastos de cobranza (1 de 6): ¿cuándo se activa el cobro? | Pendiente de un documento de Diego | Ronda 3 (20 oct 2026): Pendiente: política de gastos de cobranza. Diego enviará la política completa de gastos de cobranza; esta decisión va dentro de ella. |
| COB-2 | Política de gastos de cobranza (2 de 6): ¿se cobran por día o por evento? | Pendiente de un documento de Diego | Ronda 3 (20 oct 2026): Pendiente: política de gastos de cobranza. Diego enviará la política completa de gastos de cobranza; esta decisión va dentro de ella. |
| COB-3 | Política de gastos de cobranza (3 de 6): ¿cuál es el tope y sobre qué se mide? | Pendiente de un documento de Diego | Ronda 3 (20 oct 2026): Pendiente: política de gastos de cobranza. Diego enviará la política completa de gastos de cobranza; esta decisión va dentro de ella. |
| COB-4 | Política de gastos de cobranza (4 de 6): ¿se siguen cobrando después de la última cuota pactada? | Decidida | Documento 22 oct (22 oct 2026): Los gastos de cobranza solo se cobran mientras el cliente esté en mora; si se pone al día o termina de pagar, no se liquidan. D44. |
| COB-5 | Política de gastos de cobranza (5 de 6): ¿quién autoriza que se exonere o se condone un cobro a un cliente? | Decidida | Documento 22 oct (22 oct 2026): Los exonera el director financiero o el gerente, con justificación y aprobando directamente en el software. D45. |
| COB-6 | Política de gastos de cobranza (6 de 6): ¿el cobro de 3.000 por día es legal en el contrato? | Decidida | Documento 22 oct (22 oct 2026): La tarifa no está regulada: el cobro vale si el contrato de mutuo dice cuándo se causa y cuánto. Los 3.000 son un ejemplo; el valor real está pendiente y cambia en el tiempo. Un responsable activa la cobranza crédito por crédito (R6-COBRANZA-ACTIVA). D44. |
| CAL-1 | Vencimiento los martes (1 de 3): ¿hasta qué momento del martes se paga a tiempo? | Decidida | Ronda 3 (20 oct 2026): La cuota es a tiempo hasta el fin del martes; el atraso empieza el miércoles (día 1). |
| CAL-2 | Vencimiento los martes (2 de 3): si el martes es festivo, ¿cuándo vence la cuota? | Decidida | Ronda 3 (20 oct 2026): Sin manejo de festivos. |
| CAL-3 | Vencimiento los martes (3 de 3): ¿cuándo vence la primera cuota y el martes es igual para todos los créditos? | Decidida | Ronda 3 (20 oct 2026): El martes es fijo para todos los créditos; la primera fecha se pacta en la oferta. |
| R4-PLAZO-LINEA | Plazo máximo: ¿24 meses para todas las líneas o cada línea con su tope? | Decidida | Ronda 4 (21 oct 2026): Tope de 24 meses para todas las líneas en la primera versión, parametrizable; a futuro puede crecer. |
| R4-PLAZO-LIMITE | ¿Cuántos periodos tiene el plazo límite de recuperación? | Decidida | Ronda 4 (21 oct 2026): El plazo límite de recuperación es de 24 meses, convertidos a periodos según la periodicidad (desde cuándo se cuenta, en la ronda 5). |

| R4-QUIN | Quincenal: ¿son 24 periodos al año (y no 104)? | Decidida | Ronda 4 (21 oct 2026): La quincena tiene 24 periodos al año (el 104 era un error). |
| R4-MENSUAL-DIA | Mensual: ¿el día de pago es fijo para todos o se elige por crédito? | Decidida | Ronda 4 (21 oct 2026): La mensual vence el día 5 de cada mes, igual para todos; la primera cuota se prorratea entre el desembolso y el próximo 5 (el cómo, en la ronda 5). |
| P5-PRORRATEO | La primera cuota mensual se prorratea entre el desembolso y el próximo 5: ¿qué se prorratea y en qué periodicidades? | Decidida | Ronda 5 (22 oct 2026): Se prorratea solo el interés por los días reales, sumándole los días previos a la primera cuota; capital y servicios completos; en las tres periodicidades (D48). |
| P5-LIMITE-DESDE | El plazo límite de recuperación (24 meses): ¿desde el desembolso o desde el primer atraso? | Reemplazada por R6-LIMITE-CORTO | Documento 22 oct (22 oct 2026): X4 la contesta en parte: el límite es de 104 cuotas (24 meses) desde el desembolso; falta el caso de un crédito de menos de 24 meses. No cuenta en el total: la cuenta R6-LIMITE-CORTO. |
| R6-COBRANZA-ACTIVA | ¿Quién activa la cobranza por crédito: el administrador, el director, un comité o una regla? | Reemplazada por R7-COBRANZA-ACTIVA | Ronda 6 (22 oct 2026): Diego la dejó pendiente ("pendiente de definir política de cobranza"). No cuenta en el total: la cuenta R7-COBRANZA-ACTIVA. |
| R6-LIMITE-CORTO | En un crédito de menos de 24 meses, ¿el plazo límite es su propio plazo o los 24 meses? | Decidida | Ronda 6 (22 oct 2026): El plazo límite es el propio plazo del crédito: 12 meses, límite 12 (D53). |
| R7-COBRANZA-ACTIVA | ¿Quién activa el cobro de los gastos de cobranza (director financiero, gerente o comité) y se hace crédito por crédito o por una regla (tras N días de mora)? | Decidida | Ronda 7 (27 oct 2026): Por regla: la cobranza se activa sola tras 2 periodos en mora (tercera cuota vencida), en periodos de la periodicidad pactada, y el director financiero puede exonerar (D56). El valor y la política fina se pulen después. |
| R8-COBRANZA-PAGO-TARDIO | Con la regla nueva (D56), ¿se cobra cobranza por los días de atraso cuando el cliente paga la cuota completa pero tarde (caso 9)? | Decidida | Ronda 8 (28 oct 2026): Otra regla: la cobranza es un cobro fijo por periodo, no por días (provisional 40.000, concepto aparte; lo define el director financiero); desde 2 periodos atrasados y cada periodo hasta el cierre. El pago completo pero tardío no genera cobranza (D58). |
| R9-COBRO-MOMENTO | El primer cobro de cobranza y la suspensión de fondo y asistencia, ¿al cumplirse la 2.ª cuota atrasada o al llegar a la 3.ª? | Decidida | Ronda 9 (30 oct 2026): Al cumplirse la 2.ª cuota atrasada: primer cobro y suspensión de fondo y asistencia desde ahí (lectura A) (D62). Motor en implementación (rama feat/ronda9-motor). |
| R8-REDONDEO-PESO | Si el cliente paga en pesos enteros y la cuota tiene decimales (163.440,4465), ¿se acepta una tolerancia o se redondea la cuota? (H2) | Decidida | Ronda 8 (28 oct 2026): La cuota exigible se redondea a pesos enteros (D59; HALF_UP es un supuesto del equipo). |
| R10-PERIODO-ATRASADO | ¿Qué cuenta como un periodo atrasado: cualquier saldo pendiente, solo si falta más de un porcentaje de la cuota o solo pasados unos días de gracia? | Decidida | Rondas 10 y 11 (31 oct 2026): el capital no cubierto queda vencido, se suma a la cuota siguiente y genera mora; es lo que ya hacía el motor (D73, prueba en `RespuestasDiegoRonda10Test`). |
| R10-JUSTIFICACIONES | Con una justificación aprobada, ¿qué se perdona mientras dura (mora, cobranza, servicios, marca de recuperación) y la moto retirada temporalmente congela el crédito? | Abierta | Pregunta R10-JUSTIFICACIONES del cuestionario (ronda 10). Incluye en el comentario los permisos de geocerca y las motos varadas. |
| R10-VALOR-COBRANZA | ¿Cuál es el valor real del gasto de cobranza por periodo atrasado y quién lo define? | Decidida | Rondas 10 y 11 (31 oct 2026): Lo define el director financiero (parámetro con vigencias); los 3.000 diarios y los 40.000 por periodo quedan como valores provisionales de ejemplo (D65). |
| R10-PRIMERA-CUOTA | ¿Cómo se cobra la primera cuota cuando el vencimiento cae a pocos días de la entrega: proporcional a los días, cuota completa con interés prorrateado o cuota completa sin prorrateo? | Pendiente de un documento de Diego | Rondas 10 y 11 (31 oct 2026): Proporcional a los días, la cuota entera (capital, interés y servicios), como el sistema actual (D66). Respondida con salvedad: falta qué pasa con el capital y los servicios que no se cobran en la primera cuota (R12-PRIMERA-CUOTA-DETALLE); el motor sigue prorrateando solo el interés (D48). No cuenta como cerrada. |
| R12-PRIMERA-CUOTA-DETALLE | Con la primera cuota proporcional a los días, ¿qué pasa con el capital y los servicios que esa cuota no cobra (se pierden, se difieren o solo se prorratean interés y servicios)? | Abierta | Pregunta R12-PRIMERA-CUOTA-DETALLE del cuestionario (ronda 12), derivada de D66. Sin clave de motor todavía. |
| R10-CONTADOR-VENCIDAS | ¿Cómo debe contarse el número de cuotas vencidas (por deuda acumulada, por eventos sin reinicio o por eventos con reinicio al pagar completo) y la marca «listo para recuperar» debe poder quitarse al ponerse al día? | Respondida con salvedad | Rondas 10 y 11 (31 oct 2026): otra regla. Servicios siempre en la cuota (D70, `causacionServicios = SIEMPRE`), cuenta con terceros solo con recaudo efectivo (D72) y cobranza cuando saldo en mora / cuota total > 2 (D71, `umbralCocienteCobranza = 2`); la dación o la retoma suspenden los servicios. Salvedad: supuestos provisionales (saldo en mora = todo lo pendiente, estrictamente > 2, medido al iniciar el periodo) que se confirman en R12-COBRANZA-SALDO-MORA, R12-COBRANZA-DENOMINADOR, R12-COBRANZA-PERIODICIDADES y R12-MARCA-POR-COCIENTE. No cuenta como cerrada. ADR 0006. |
| R12-COBRANZA-SALDO-MORA | Cobranza por cociente: ¿qué entra en el saldo en mora, es estrictamente más de 2 y cuándo se mide? | Abierta | Pregunta R12-COBRANZA-SALDO-MORA (ronda 12), derivada de D71. Supuesto del motor: todo lo pendiente, estrictamente > 2, al iniciar el periodo (`baseMoraCobranza = TODO_PENDIENTE`, `comparacionCobranza = MAYOR`). |
| R12-COBRANZA-DENOMINADOR | Cobranza por cociente: ¿el valor total de la cuota es la cuota vigente o la pactada cuando cambia (abono a menor cuota, cambio de tasa)? | Abierta | Pregunta R12-COBRANZA-DENOMINADOR (ronda 12), derivada de D71. Supuesto: cuota vigente + servicios por cuota, al peso. |
| R12-COBRANZA-PERIODICIDADES | Cobranza por cociente: ¿el umbral de 2 cuotas y el valor por periodo son iguales en semanal, quincenal y mensual? | Abierta | Pregunta R12-COBRANZA-PERIODICIDADES (ronda 12), derivada de D71. Supuesto: iguales en las tres. |
| R12-VALOR-EXIGIBLE | El valor a pagar que se le muestra al cliente, ¿incluye lo pendiente o solo la cuota del periodo? | Abierta | Pregunta R12-VALOR-EXIGIBLE (ronda 12), derivada de D73. No cambia el cálculo del motor (el pago siempre va primero a lo pendiente). |

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
| R8-PAGO-INCOMPLETO-EN-FECHA | Un pago incompleto hecho en la fecha (0 días de atraso), ¿es "al día" para el fondo y la asistencia? (H7) | Decidida | Ronda 8 (28 oct 2026): Otra regla: la causación siempre genera servicios + interés + capital; tras 2 periodos atrasados solo se causa el seguro (D60). El seguro todo riesgo y los servicios suspendidos al ponerse al día se respondieron en la ronda 9 (D63 y D64). |
| R9-SEGURO-TODO-RIESGO | El seguro todo riesgo, ¿es el mismo seguro que ya causa el motor o uno nuevo? ¿Valor, base de cálculo, periodicidad e IVA del 19 %? | Decidida | Ronda 9 (30 oct 2026): Es un seguro nuevo que se suma al actual: 0,3 % mensual sobre la financiación inicial, se paga igual que el de vida, más 19 % de IVA; la contabilidad segmenta seguro e IVA (D63). Motor en implementación; los detalles quedan en R11-*. |
| R9-SERVICIOS-AL-PONERSE-AL-DIA | Si el cliente se pone al día tras la suspensión, ¿los fondos y asistencias de los periodos suspendidos se pierden o se cobran de forma retroactiva? | Decidida | Ronda 9 (30 oct 2026): Se pierden: los servicios se retoman desde el periodo siguiente y los suspendidos no se cobran (D64). Motor en implementación. |
| R11-SEGUROS-TRAS-SUSPENSION | Tras 2 periodos atrasados, cuando solo se causa el seguro, ¿se causan los dos seguros (vida y todo riesgo) o solo uno? | Decidida | Rondas 10 y 11 (31 oct 2026): ya no hay suspensión por atraso (R10-CONTADOR-VENCIDAS): los dos seguros se causan siempre; solo la dación o la retoma suspenden los servicios (D70). |
| R11-BASE-FINANCIACION-INICIAL | El 0,3 % mensual del seguro todo riesgo, ¿se calcula sobre el monto financiado a secas o incluye servicios y gastos financiados? | Decidida | Rondas 10 y 11 (31 oct 2026): El monto financiado a secas, la misma base del seguro de vida (D68). |
| R11-PERIODIZACION-SEGURO | ¿El 0,3 % mensual se convierte a semanal y quincenal igual que el seguro de vida? | Decidida | Rondas 10 y 11 (31 oct 2026): Sí: el 0,3 % mensual se convierte a semanal y quincenal igual que el seguro de vida (D68). |
| R11-CONTABILIDAD-SEGURO-IVA | ¿El seguro todo riesgo es ingreso propio o pasivo a pagar a la aseguradora, y el IVA es IVA generado? | Decidida | Rondas 10 y 11 (31 oct 2026): CxP seguros 10.000 / IVA por pagar por seguros 1.900 / CxP aseguradoras 11.900; el IVA nunca es responsabilidad de la financiera frente a la DIAN (D69). |
| R12-IVA-FONDO-ASISTENCIAS | El IVA del fondo de garantías y de las asistencias, ¿también se segmenta en la contabilidad como el del seguro? | Abierta | Pregunta R12-IVA-FONDO-ASISTENCIAS del cuestionario (ronda 12), derivada de D69. Hoy va incluido en sus cuentas («IVA incl.»). |
| R12-SERVICIOS-DESPUES-PLAZO | «El cliente siempre paga los servicios»: ¿también después de la última cuota pactada, mientras siga debiendo? | Abierta | Pregunta R12-SERVICIOS-DESPUES-PLAZO (ronda 12), derivada de D70. Hoy `serviciosDespuesDelPlazo = false`: solo dentro del plazo. |
| R12-GIRO-TERCEROS | ¿Cada cuánto se gira a los terceros lo recaudado por sus servicios? | Abierta | Pregunta R12-GIRO-TERCEROS (ronda 12), derivada de D72. Hoy al cerrar cada mes (o al terminar el crédito). |


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
| R4-PAGO-SIN-CUOTA | Pago sin cuota causada: ¿todo es abono extra o saldo a favor? | Decidida | Ronda 4 (21 oct 2026): Solo antes de la primera cuota no hay cuota causada; después un pago cubre la cuota causada y lo que sobre es abono extra (ABN-4). |
| R4-CASO13 | Caso 13 (abono extra con pendientes): ¿los valores son correctos? | Reemplazada por P5-CASO13 | Ronda 4 (21 oct 2026): Se paga todo lo vencido, pero el sobrante (1.182.645,20) vuelve a pasar por la prelación de las 7 líneas; solo el resto va a capital. Cambia D21/D30. No cuenta en el total: la cuenta P5-CASO13. |

| R4-A3 | ¿Se acepta cualquier abono extra o hay un valor mínimo? | Decidida | Ronda 4 (21 oct 2026): No hay valor mínimo para el abono extra. |
| P5-PAGO-ANTES-PRIMERA | Un pago antes de la primera cuota: ¿abono extra a capital o se guarda para la primera cuota? | Decidida | Ronda 5 (22 oct 2026): Es abono extra a capital (D49). |
| P5-CASO13 | Caso 13 recalculado con el sobrante por la prelación: ¿las cifras nuevas son correctas? | Decidida | Ronda 5 (22 oct 2026): Las cifras nuevas son correctas: capital 1.123.017,41 y saldo 3.229.537,12 (D50). |
| R8-PAGO-MAYOR-EXIGIBLE | Un pago mayor que lo exigible, ¿se rechaza, se devuelve como cuenta por pagar o se aplica como abono extraordinario? (H8) | Decidida | Ronda 8 (28 oct 2026): El sobrante queda como cuenta por pagar al cliente para devolverlo (D61); cuenta provisional hasta tener el PUC. |

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

| R4-T5 | Con el cliente en mora, ¿sobre qué saldo se recalcula la cuota al cambiar la tasa? | Decidida | Documento 22 oct (22 oct 2026): La cuota se recalcula sobre el capital vigente; lo vencido se cobra aparte (está prohibido liquidar intereses sobre intereses). D42. |
| R4-USURA-PLAZO | Si nadie autoriza la usura, ¿a los cuántos días hábiles se aplica sola y vale para el lote de vigentes? | Decidida | Ronda 4 (21 oct 2026): Si nadie autoriza, la usura se aplica sola 1 día hábil después de su publicación, también al lote de vigentes. |
| R4-AVISO-CLIENTE | ¿Se avisa al cliente cuando cambia la tasa o se valida con legal? | Decidida | Ronda 4 (21 oct 2026): No se avisa al cliente cuando cambia la tasa (decisión del dueño de producto; conviene validarla con legal). |
| P5-USURA-DIA | Si nadie autoriza la usura, ¿se aplica sola a 1 día hábil de su publicación? | Decidida | Ronda 5 (22 oct 2026): Sí, 1 día hábil; es un requisito del proceso de usura, no del motor (D51). |
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
| R4-FONDO-TOPE | ¿El fondo de garantías tiene un tope que el motor deba controlar? | Decidida | Ronda 4 (21 oct 2026): El fondo de garantías no tiene tope por crédito. |

| R4-R2 | Al recuperar la moto, ¿se descuenta el costo real con soporte? | Decidida | Ronda 4 (21 oct 2026): En la recuperación se descuenta el costo real con soporte (grúa, bodegaje, avalúo, trámites), en dación y en retoma. |
| R4-PERITO | Valor Fasecolda × 65 %: ¿cuándo se usa el perito externo? | Decidida | Ronda 4 (21 oct 2026): El perito externo se usa cuando la moto está en Fasecolda pero su estado real es de alto deterioro; en lo demás, Fasecolda × 65 %. |
| P5-COSTO-DACION | En la dación, el costo real de la recuperación: ¿lo cubre el fondo de garantías o la financiera? | Decidida | Ronda 5 (22 oct 2026): Se suma al faltante y lo cubre el fondo de garantías; deja de ser provisional (D52). |
| R6-RECUP-AUTO | Al llegar a 2 cuotas atrasadas, ¿el motor ejecuta la dación o la retoma solo o solo marca el crédito para que una persona la ordene? | Decidida | Ronda 6 (22 oct 2026): El motor solo marca; una persona ordena, y decide si se ejecuta aunque el cliente se ponga al día (D54). |
| R6-RECUP-VS-CASO11 | El caso 11 espera 4 periodos; con la política de 2 cuotas atrasadas, ¿se ajusta a 2? | Reemplazada por R7-RECUP-VS-CASO11 | Ronda 6 (22 oct 2026): Diego la dejó pendiente ("política por definir"). No cuenta en el total: la cuenta R7-RECUP-VS-CASO11. |
| R7-RECUP-VS-CASO11 | El caso 11 espera 4 periodos antes de la dación; con el umbral de 2 cuotas, ¿se corrige a 2 o se deja en 4 como ejemplo? | Decidida | Ronda 7 (27 oct 2026): El caso 11 se corrige a 2 periodos de espera; con EXCEL sigue en 4 (D57). |
| R10-MARCA-RECUPERACION | ¿Cuándo un crédito queda «listo para recuperar»: la primera vez que llega a 2 periodos atrasados, si se mantiene o si no hay justificación vigente? | Decidida | Rondas 10 y 11 (31 oct 2026): La primera vez que llega a 2 periodos atrasados, aunque luego se ponga al día (como funciona hoy) (D67). |
| R12-MARCA-POR-COCIENTE | La marca «listo para recuperar», ¿sigue por eventos o también por el cociente de 2 cuotas, como la cobranza? | Abierta | Pregunta R12-MARCA-POR-COCIENTE (ronda 12), derivada de D67 y D71. Hoy por eventos (`cuotasVencidasParaRecuperacion = 2`). |
| R12-SERVICIOS-CIERRE | En la dación o la retoma, ¿el periodo del cierre causa servicios y qué pasa con los cobrados que nunca se recaudaron (cuentas de orden)? | Abierta | Pregunta R12-SERVICIOS-CIERRE (ronda 12), derivada de D70 y D72. Supuesto: el periodo del cierre no causa servicios; lo que cubre la moto es recaudo efectivo y lo demás se extingue contra las CxC. |
| R10-VALOR-MOTO-REENTREGADA | Al entregar la moto a un segundo cliente, ¿qué valor se financia: avalúo, valor en libros, lo no recuperado o solo el reacondicionamiento? | Abierta | Pregunta R10-VALOR-MOTO-REENTREGADA del cuestionario (ronda 10). Sin clave de motor todavía. |

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

| R4-C5 | ¿El GPS y los gastos comerciales son parte del alistamiento? | Decidida | Ronda 4 (21 oct 2026): El pago inicial se resta del valor total (moto + alistamiento + GPS + gastos comerciales); el GPS y los gastos comerciales son parte del alistamiento. |
| R4-COBRANZA-CONTABLE | Los gastos de cobranza son compensación de gastos reales: ¿cómo se registran? | Decidida | Ronda 4 (21 oct 2026): Los gastos de cobranza se registran como ingresos no operacionales, para que no se imputen como mayor interés de financiación (PUC clase 42). |
| R12-CUENTA-TERCEROS | ¿En qué cuenta del PUC se registran los servicios recaudados por cuenta de terceros? | Abierta | Pregunta R12-CUENTA-TERCEROS (ronda 12), derivada de D72. Provisional: «Ingresos recibidos para terceros» (PUC 2815) en NEGOCIO; el Excel conserva «CxP servicios a terceros». |
### Roles y parámetros

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| ROL-1 | ¿Quién es el «administrador» que puede cambiar la tasa y los gastos de cobranza? | Reemplazada por R4-SUPLENTE | Ronda 3 (20 oct 2026): Un solo administrador, sin suplente; falta qué pasa si no está. No cuenta en el total: la cuenta R4-SUPLENTE. |
| ROL-2 | ¿Qué parámetros son sensibles y solo toca el administrador? | Decidida | Ronda 3 (20 oct 2026): Todos los parámetros del producto son sensibles. |
| ROL-3 | Cuando el administrador cambia un parámetro, ¿rige para los créditos vigentes o solo para los nuevos? | Decidida | Ronda 3 (20 oct 2026): Un cambio de parámetro rige solo para créditos nuevos, salvo la usura, que recalcula los vigentes desde la cuota siguiente. |
| ROL-4 | ¿Un cambio de parámetro sensible necesita segundo aprobador y queda en bitácora? | Decidida | Ronda 3 (20 oct 2026): El administrador cambia de inmediato, con bitácora (quién, qué, cuándo), sin segundo aprobador. |
| ROL-5 | Después del simulador y el motor de abonos, ¿cuál es el siguiente alcance? | Decidida | Ronda 3 (20 oct 2026): El siguiente alcance es el motor de scoring, antes de la fábrica de créditos. |

| R4-SUPLENTE | Un solo administrador sin suplente: ¿qué pasa si no está? | Decidida | Ronda 4 (21 oct 2026): Se nombra un suplente con los mismos permisos y la misma bitácora. |
### Ajustes al Excel

| ID | Regla (pregunta) | Estado | Fuente / detalle |
|---|---|---|---|
| X1 | ¿Se corregirán estas inconsistencias en una nueva versión del Excel? | Decidida | Documento 22 oct (22 oct 2026): Todas las operaciones tienen servicios complementarios; el Excel solo los simuló en una operación como ejemplo. D43. |
| X4 | En el caso 8 el plazo límite sigue en 100 semanas, pero ahora el crédito tiene 104 cuotas. ¿Cuál debe ser el límite? | Decidida | Documento 22 oct (22 oct 2026): El plazo límite del caso 8 se amplía a 104 cuotas (24 meses), según el recálculo; en cualquier momento puede darse la retoma o la dación. D47. |
| X3 | En su respuesta sobre el plazo límite habla de los casos 4 o 5 como dación o retoma. ¿Se refiere a los casos 3 y 4? | Decidida | Respuestas 20 oct (20 oct 2026): Se refería a dación (caso 3) o retoma (caso 4). |


## Tabla B · Reglas sin pregunta en el cuestionario

| Regla | Estado | Fuente / detalle |
|---|---|---|
| Arquitectura: monolito modular en Java 21 con Spring Modulith | Decidida | D1, `adr/0001-monolito-modular.md` |
| Tasas efectivas: semanal = (1+EA)^(1/52) − 1 y diaria = (1+EA)^(1/365) − 1, en doble precisión como el Excel | Decidida | D9 |
| Pago inicial opcional que digita el asesor y se resta del total | Decidida | D10, D41, P-09 (la base del total quedó cerrada en R4-C5) |
| «FCC» se llama fondo de garantías en el código y en la cuenta contable | Decidida | D14, P-07 |
| Fuente de verdad: Excel del 11 de octubre; línea CPPU 87,62 % E.A. con 104 cuotas semanales; catálogo de líneas | Decidida | D15, P-10 |
| Casos 10 (cambio de tasa conservando la cuota), 11 (pago inferior + dación) y 12 (normalización) | Decidida | D18 |
| Servicios sobre el monto financiado inicial, hasta la cuota pactada; en mora solo se causa el seguro | Decidida | D7, D17, P-07 |
| Periodicidad configurable por crédito (semanal, quincenal, mensual); las reglas de cálculo están en PPER y PPER-1 a PPER-10; la quincena con 24 periodos quedó confirmada en R4-QUIN; la mensual vence el día 5 y la primera cuota prorratea solo el interés (P5-PRORRATEO, D48) | Decidida (principio) | D24, `adr/0002-periodicidad-configurable.md` |
| IVA del 19 % en fondo de garantías y asistencia; el seguro está exento | Decidida | P-08 |
| Los estados del Excel son la lista oficial (ampliable) | Decidida | P-21 |
| Las cuotas vencen los martes; sin manejo de festivos por ahora (el detalle del atraso está en CAL-1 a CAL-3) | Decidida | P-06 |
| P-12: orden de los conceptos en el pago anticipado | Pendiente de un documento de Diego | Remitió al Excel corregido |
| P-15 y P10: pagos que combinan eventos (tardío e incompleto a la vez); no hay caso de referencia | Pendiente de un documento de Diego | Remitió al Excel corregido; `pendientes-tecnicos.md` n.º 7 |
| P-25: el indicador del caso 9 está escrito a mano | Pendiente de un documento de Diego | Sin respuesta; se espera el Excel corregido |
| Definición de «cuotas eliminadas» (caso 6) y de «periodos adicionales» (caso 8, validación 28) | Pendiente de un documento de Diego | El Excel no las define; se esperan en el Excel corregido |
| Política de gastos de cobranza completa | Pendiente de un documento de Diego | Política que enviará Diego; cubre PTOPE, PCOB, PDIAS, COB-1 a COB-3 y PPER-8 (COB-4 a COB-6 ya decididas el 22 oct: solo en mora, exoneración y contrato; D44 y D45); el registro contable como ingreso no operacional ya está decidido en R4-COBRANZA-CONTABLE |
| P-04: plan de cuentas (PUC), por línea y por cuenta | Pendiente de un documento de Diego | Lo enviará por correo; cubre C2 y CON-1 (la clase 42 de la cobranza en R4-COBRANZA-CONTABLE espera la cuenta exacta) |
| P-23: reglas sin caso en el Excel (idempotencia, reversos, trazabilidad, clasificación de pagos) | Pendiente de un documento de Diego | Va a reunión; sin fecha |

## Cómo se mide el avance

1. Una regla pasa de abierta a decidida cuando se contesta su pregunta, se anota la fuente en `preguntas.ts` (`RESPONDIDAS_*`) y se actualiza esta tabla.
2. Una pregunta contestada con «Respondo en el comentario» cuenta como respondida en el cuestionario, pero aquí solo pasa a decidida cuando alguien del equipo lee el comentario y lo resume como regla.
3. Los porcentajes de esta tabla salen de contar los estados; la prueba `cuestionario.test.ts` verifica que cada pregunta del cuestionario aparezca aquí.
