# Preguntas para Diego · consolidado de los 9 casos

Salen de reproducir el Excel celda por celda (2026-10-08). El motor hoy **reproduce el Excel tal cual** en los 9 casos; estas preguntas son para decidir si el Excel es la regla final. El detalle de cada una está en `docs/reglas/caso-N.md`.

> **Estado al 2026-10-30 (el detalle de rondas es del 22 oct).** Este consolidado es de la primera ronda (8 oct); casi todo está respondido (ver `decisiones.md`, D1 a D55, y `completitud-reglas.md`). Las rondas 5 y 6 se respondieron el 22 oct (`respuestas-diego-2026-10-22-ronda5-6.md`): prorrateo de la primera cuota (D48), pago antes de la primera cuota (D49), caso 13 (D50), usura a 1 día hábil (D51), costo de la dación (D52), límite de un crédito corto (D53) y recuperación solo marcada por el motor (D54) quedaron cerrados. Las rondas 7, 8 y 9 ya están respondidas (D56 a D64). **Abiertas hoy:** las 7 de la ronda 10 (backtesting) y las 4 de la ronda 11 (seguimiento de la ronda 9, abajo).

## Ronda 11 · Abierta (30 oct 2026)

Seguimiento de la ronda 9, que Diego respondió el 30 oct (`respuestas-diego-2026-10-30-ronda9.md`, D62 a D64). Son 4 preguntas del bloque Servicios complementarios. Aún no tienen clave del motor; mientras responde, el motor hace el supuesto indicado.

| ID | Pregunta | Opciones |
|---|---|---|
| R11-SEGUROS-TRAS-SUSPENSION | Tras 2 periodos atrasados, cuando «solo se causa el seguro», ¿se siguen causando los dos seguros (vida y todo riesgo) o solo uno? | (a) Los dos (supuesto provisional del motor). (b) Solo el de vida. (c) Solo el todo riesgo. (d) Otra regla. |
| R11-BASE-FINANCIACION-INICIAL | El 0,3 % mensual es «sobre la financiación inicial»: ¿qué monto es esa base? | (a) El monto financiado a secas. (b) El monto financiado más los servicios y gastos que se financian. (c) Otra base. |
| R11-PERIODIZACION-SEGURO | El 0,3 % es mensual y «se paga igual que el de vida»: ¿se convierte a semanal y quincenal igual que el seguro de vida? | (a) Sí, igual. (b) No, otra conversión. (c) Otra regla. |
| R11-CONTABILIDAD-SEGURO-IVA | En la contabilidad, ¿el seguro todo riesgo es ingreso propio o pasivo a pagar a la aseguradora, y el IVA es IVA generado? | (a) Pasivo a la aseguradora e IVA generado. (b) Ingreso propio e IVA generado. (c) Otro tratamiento. |

## Ronda 10 · Abierta (29 oct 2026)

Salen del **backtesting** del motor con el historial real de pagos de Motos del Caribe. Se reprodujo el comportamiento de pago de unos 4.500 contratos como si hubieran entrado por la financiera con las reglas actuales. Son 7 preguntas. Aún no tienen clave del motor: primero necesitamos su respuesta.

Lo que encontramos:

- **Una cuota con cualquier saldo pendiente cuenta como atrasada.** Así, cerca del 34 % de los clientes que terminaron pagando todo quedaría marcado «listo para recuperar» alguna vez, y cerca del 29 % pagaría cobranza (corrida corregida del 29-oct).
- **Entre los clientes vigentes, cerca del 30 % quedaría marcado «listo para recuperar».** (Corrida corregida del 29-oct.)
- **La marca «listo para recuperar» (2 cuotas vencidas) distingue poco** entre quienes terminan pagando y quienes se retiran: quedan marcados entre el 11 % y el 51 % de los retirados, según el motivo del retiro, y cerca del 34 % de los que terminan pagando.
- **El gasto de cobranza usado es 40.000 por periodo**, un valor provisional de ejemplo. Con él, la cobranza media por cliente es cerca de 1 millón.
- **La primera cuota proporcional se lee como pago incompleto.** Hoy el sistema cobra la primera cuota proporcional a los días entre la entrega y el primer vencimiento (con 3 días de 7, el cliente paga 3/7: 96.000 de 225.000). El motor de la financiera espera una cuota completa y solo prorratea el interés si se le dan las fechas; así el crédito queda «en mora» desde la primera semana. En la prueba, el 68 % pagó menos del 90 % de la cuota en la primera semana, y en 4 de cada 10 de esos el valor coincide con los días proporcionales.
- **El contador de cuotas vencidas casi nunca baja.** Hoy sube con cada pago incompleto o sin pago y solo vuelve a cero cuando todo lo pendiente queda exactamente en cero; un cliente que paga con 1 a 3 días de atraso arrastra restos de pocos pesos y no se reinicia. Con el contador actual quedó marcado cerca de 34 % de los contratos que terminaron pagando; midiendo el atraso con el efectivo acumulado, solo cerca de 17 % llegó alguna vez a 2 cuotas de atraso real. Estas cifras son del backtesting corregido y se actualizan tras la nueva corrida.

| ID | Pregunta | Opciones |
|---|---|---|
| R10-PERIODO-ATRASADO | ¿Qué cuenta como un periodo atrasado? | (a) Cualquier saldo pendiente, aunque sea pequeño (como hoy). (b) Solo si falta más de un porcentaje de la cuota (indique el porcentaje; por ejemplo, 10 %). (c) Solo pasados unos días de gracia después del vencimiento (indique los días). (d) Otra regla. |
| R10-MARCA-RECUPERACION | ¿Cuándo un crédito queda «listo para recuperar» (dación o retoma)? | (a) La primera vez que llega a 2 periodos atrasados, aunque luego se ponga al día. (b) Solo si se mantienen 2 periodos atrasados seguidos o más de una semana. (c) Solo si hay 2 atrasados y no hay justificación vigente. (d) Otra regla. |
| R10-JUSTIFICACIONES | Con una justificación aprobada (enfermedad, familiar fallecido, moto varada, accidentada, en taller, en garantía, en fiscalía), ¿qué se perdona mientras dura? | La mora, la cobranza, los servicios (fondo, asistencia, seguro), que el periodo cuente como atrasado para la marca de recuperación, o nada. En el comentario: si la moto retirada temporalmente (en oficina, en realquiler, pendiente de triage) debe congelar el crédito, y qué hacer con los «permisos de geocerca» y las «motos varadas». |
| R10-VALOR-COBRANZA | ¿Cuál es el valor real del gasto de cobranza por periodo atrasado y quién lo define? | Un valor en pesos y quién lo define (director financiero u otro). Hoy el motor usa 40.000 de ejemplo. |
| R10-PRIMERA-CUOTA | ¿Cómo se cobra la primera cuota cuando el vencimiento cae a pocos días de la entrega de la moto? | (a) Proporcional a los días, la cuota entera (capital, interés y servicios), como hoy en el sistema actual. (b) Cuota completa, con solo el interés prorrateado por días (como calcula el motor hoy). (c) Cuota completa, sin prorrateo. (d) Otra regla. |
| R10-CONTADOR-VENCIDAS | ¿Cómo debe contarse el número de cuotas vencidas de un crédito? Además: ¿la marca «listo para recuperar» debe poder quitarse cuando el cliente se pone al día? | (a) Por deuda acumulada: deuda vencida pendiente dividida entre la cuota (baja al pagar de más o ponerse al día). (b) Por eventos, sin reinicio (como hoy). (c) Por eventos, con reinicio al pagar una cuota completa más lo vencido o ponerse al día. (d) Otra regla (aclarar también lo de la marca). |
| R10-VALOR-MOTO-REENTREGADA | Cuando una moto se entrega a un segundo cliente, ¿cuál es el valor que se financia? | (a) Valor de la moto usada según avalúo. (b) Valor en libros (costo menos depreciación). (c) Lo no recuperado de la inversión original. (d) Solo el costo de reacondicionarla. (e) Otra regla. En el comentario: cómo se calcula hoy. |

## Bloque 1 · Las que más cambian el cálculo (resolver primero)

| # | Pregunta | Por qué importa | Casos |
|---|---|---|---|
| P1 | ¿La prelación es cobranza → mora → interés → **servicios** → capital? El texto de la hoja Casos omite los servicios; las fórmulas los ponen antes del capital. | Cambia cuánto capital se recupera en cada pago incompleto. | 8, 9 |
| P3 | ¿Una sola fórmula de mora? El caso 8 cobra capital vencido × tasa semanal; el caso 9 suma además capital de la cuota × ((1+tasa diaria)^días − 1). | Hoy hay dos mecanismos distintos. | 8, 9 |
| P7 | En dación y retoma no se causa mora, capital vencido, servicios ni cobranza mientras el cliente no paga; en los casos 8 y 9 sí. ¿Cuál es la regla? | Cambia la deuda a extinguir y el faltante que cubre el FCC. | 3, 4 |
| P-doble | El interés corriente se calcula sobre el saldo total **incluido el capital vencido**, que además paga mora. En el caso 8 el interés corriente extra es exactamente igual a la mora cobrada (99.772,35). ¿Es intencional? Revisar contra usura. | Posible doble cobro. | 8, 9 |
| P-horizonte | Con 14 días de atraso recurrente (caso 9) el crédito **nunca termina**: el cliente paga 18,74 M sobre 5,9 M y aún debe 3,32 M a la semana 120; desde la 76 no amortiza capital. ¿Tope a la cobranza (hoy 24 % de la cuota) o a la mora? | Riesgo legal y reputacional. | 9 |
| P2 | Redondeo: el Excel no redondea. ¿Centavos o pesos? ¿Dónde se absorbe el residuo? | Afecta todo lo que se cobra y contabiliza. | todos |

## Bloque 2 · Servicios complementarios (FCC, asistencia, seguro)

- **P6** ¿FCC y seguro sobre el monto inicial o sobre el saldo? Con abono de menor plazo se dejan de cobrar 25 semanas (1.575.854 en el ejemplo): ¿qué pasa con la cobertura?
- **P11** Con la inicial del 10 %, FCC y seguro bajan al 90 % (unos 418.000 menos de FCC en el crédito). ¿Correcto?
- Se pactan por mes pero se cobran por semana (mensual / 4,333): los meses de 5 cuotas giran 25 % más y 86 cuotas = 19,85 meses (unos 32.400 menos de FCC). ¿Se acepta?
- Después de la cuota 86 los servicios valen 0 aunque el crédito siga en mora: ¿el seguro de vida deja de cubrir?
- Al tercero se le gira lo **causado**, no lo recaudado: la financiera adelanta lo que el cliente no paga. ¿Es así?
- ¿Los servicios son ingreso propio o pasivo con terceros? (el caso 1 dice "CxP servicios a terceros"; el 8 y el 9, "Servicios complementarios").

## Bloque 3 · Abonos y prepago

- ¿Quién elige menor plazo o menor cuota y cuál es la opción por defecto?
- Si el abono supera el saldo, el Excel descarta el excedente: ¿se devuelve, queda saldo a favor o se rechaza?
- ¿Monto mínimo de abono? ¿Se puede abonar con capital vencido? ¿El abono va siempre después de la cuota?
- Con varios abonos, ¿cada uno recalcula la cuota hasta la 86? ¿Con la tasa vigente o la original? (El Excel usa la original de Inputs; el motor, la vigente.)
- ¿El prepago cobra interés por los días desde la última cuota? (Ley 1555 de 2012: sin penalidad.)

## Bloque 4 · Cambio de tasa y usura

- ¿El cambio aplica desde la primera cuota del mes o desde una fecha (con prorrateo)?
- ¿"Usura − 1 punto" es punto porcentual de EA? ¿Si la EA es igual a la usura también se baja?
- ¿Qué usura aplica: consumo, bajo monto o microcrédito? (**P5**: ¿87,32 % EA cabe?)
- Si la usura vuelve a subir, ¿se regresa a la tasa pactada?
- Si el cliente está en mora cuando cambia la tasa, ¿la nueva cuota se calcula sobre el saldo total o solo el vigente?

## Bloque 5 · Dación y retoma

- Aparte del signo del residuo, ¿qué diferencia hay entre dación y retoma? ¿Puede haber retoma con faltante o dación con excedente?
- ¿El excedente se devuelve en efectivo? ¿Se descuentan grúa, bodegaje, avalúo, trámites?
- ¿Inventario por avalúo o por valor de venta?
- ¿El interés de la semana del evento se cobra completo o por días? ¿Esa semana cuenta como cuota vencida?

## Bloque 6 · Contabilidad

- El caso 1 acredita "Ingresos por intereses" al recaudar; los casos 8 y 9 acreditan "CxC interés corriente" sin que nadie la cause. ¿Cuál es el modelo (causación semanal o recaudo directo)?
- Códigos PUC de cada cuenta (P9) y cómo se contabiliza la cuota inicial.
- La hoja Validaciones no trae la definición de "cuotas eliminadas" (caso 6) ni de "periodos adicionales" (caso 8): se tomaron como 86 − último periodo y último periodo − 86.

## Bloque 7 · Inconsistencias menores del Excel (para que las corrija)

- La hoja Casos dice que los servicios solo se simulan en el caso 1, pero los casos 3, 4, 8 y 9 sí los tienen.
- Estado final "Amortizado" (casos 1, 2, 7) vs "Cancelado" (resto).
- Inputs B3 se llama "Monto financiado" pero es el valor total de la operación.
- Caso 4: la columna CxC FCC es la constante 0 (en el caso 3 es fórmula).
- La retoma del caso 4 ocurre tras solo 3 semanas sin pago (mismo mes de incumplimiento y de retoma).
