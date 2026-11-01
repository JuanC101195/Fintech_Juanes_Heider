# Preguntas para Diego · consolidado de los 9 casos

Salen de reproducir el Excel celda por celda (2026-10-08). El motor hoy **reproduce el Excel tal cual** en los 9 casos; estas preguntas son para decidir si el Excel es la regla final. El detalle de cada una está en `docs/reglas/caso-N.md`.

> **Estado al 2026-11-01 (el detalle de rondas es del 22 oct).** Este consolidado es de la primera ronda (8 oct); casi todo está respondido (ver `decisiones.md`, D1 a D55, y `completitud-reglas.md`). Las rondas 5 y 6 se respondieron el 22 oct (`respuestas-diego-2026-10-22-ronda5-6.md`): prorrateo de la primera cuota (D48), pago antes de la primera cuota (D49), caso 13 (D50), usura a 1 día hábil (D51), costo de la dación (D52), límite de un crédito corto (D53) y recuperación solo marcada por el motor (D54) quedaron cerrados. Las rondas 7, 8 y 9 ya están respondidas (D56 a D64). Diego respondió el 31 oct 9 de las 11 de las rondas 10 y 11 (`respuestas-diego-2026-10-31-ronda10-11.md`, D65 a D73; el rediseño de servicios y cobranza en la ADR 0006). El 1 nov respondió las 13 abiertas (`respuestas-diego-2026-11-01-ronda12.md`, D74 a D83; ADR 0007). **Abiertas hoy:** las 5 de la ronda 13 (abajo).

## Ronda 13 · Abierta (1 nov 2026)

Lo que quedó ambiguo en las respuestas de Diego del 1 nov a la ronda 12 (`respuestas-diego-2026-11-01-ronda12.md`, D74 a D83, ADR 0007). Son 5 preguntas; el motor ya aplica el supuesto indicado en cada una (la de congelar no se implementa todavía: es de la fase 3).

| ID | Pregunta | Opciones |
|---|---|---|
| R13-COBRANZA-TRAS-MARCA | Con el crédito ya marcado «listo para recuperar», si abona y el saldo en mora baja de 2 cuotas, ¿se sigue cobrando la cobranza? Ejemplo: no paga las semanas 1 a 3 (marcado al cerrar la 2, la 3 cobra 40.000) y en la 4 paga 600.000. | (a) Solo mientras el saldo en mora siga en 2 cuotas o más; la marca se queda (supuesto: las semanas 5 y 6 no cobran). (b) Mientras esté marcado, hasta que se ponga al día. (c) Otra regla. |
| R13-PRIMERA-CUOTA-INTERES | ¿El capital que la primera cuota proporcional no cobra causa interés hasta la última cuota? Ejemplo semanal, 3 días de 7: quedan 16.299,13 sin cobrar. | (a) Sí, como el resto del saldo; la cuota 104 sube de 100.406,51 a 157.091,51 (supuesto). (b) No; la cuota 104 sería 116.705,64. (c) Otra regla. |
| R13-PRIMERA-CUOTA-LARGA | Si la moto se entrega más de un periodo antes del primer vencimiento (p. ej. 10 días para una cuota semanal), ¿cómo se cobra la primera cuota? | (a) Capital y servicios completos; solo el interés cuenta los días de más: 199.094 (supuesto). (b) Todo proporcional (10/7): unos 240.418. (c) No se permite. (d) Otra regla. |
| R13-SERVICIOS-CIERRE-ASIENTO | En la dación o la retoma, ¿con qué asiento se le paga al tercero lo causado y no recaudado (p. ej. 100.000 que la moto no cubre) y cuál es el PUC de la cuenta de terceros (hoy 2815, provisional)? Hoy lo no cubierto se extingue contra las CxC de cada servicio. | (a) Débito CxC fondo de garantías / crédito cuenta de terceros. (b) Débito gasto de la financiera / crédito cuenta de terceros. (c) Otro asiento. (d) Lo consulta con contabilidad. |
| R13-CONGELAR-CREDITO | Con una justificación que congela el crédito (no se perdonan cargos), ¿qué pasa en el periodo congelado? | (a) Se corre el calendario y el periodo no causa nada. (b) Se corre el calendario pero causa interés y servicios. (c) El calendario sigue; se causa todo, pero no se cobra cobranza ni se marca. (d) Otra regla. |

## Ronda 12 · Respondida (1 nov 2026)

Diego respondió las 11 preguntas de la ronda 12 y las 2 que seguían abiertas de la ronda 10 (`respuestas-diego-2026-11-01-ronda12.md`). Cobranza desde 2 cuotas con la totalidad de lo pendiente (D74); cuota vigente, iguales en las tres periodicidades y giro mensual (D75); servicios después del plazo mientras haya deuda (D76); marca por cociente (D77); el seguro riesgo total como los demás servicios, sin segmentar el IVA (D78, supera a D69); primera cuota completa proporcional, con el capital no cobrado en la última cuota (D79); valor exigible = cuota + la totalidad de lo pendiente (D80, presentación pendiente); servicios no recaudados en el cierre (D81, sin implementar); justificaciones que congelan (D82, fase 3) y valor de la moto reentregada como input (D83).

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
