# Preguntas para Diego · consolidado de los 9 casos

Salen de reproducir el Excel celda por celda (2026-10-08). El motor hoy **reproduce el Excel tal cual** en los 9 casos; estas preguntas son para decidir si el Excel es la regla final. El detalle de cada una está en `docs/reglas/caso-N.md`.

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
