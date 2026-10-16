# Respuestas de Diego · documento "Preguntas-PO-Financiera" (recibido 2026-10-16)

Documento de Heider (8-oct, preguntas P-01 a P-27) respondido por Diego. Aquí se cruza cada respuesta con nuestras preguntas (`preguntas-diego.md`), con `ReglasNegocio` y con el Excel que usa el motor (`fuentes/casos-operaciones-v2.xlsx`, mismo archivo que "Casos Operaciones V2 mejorado.xlsx" del 7-oct).

## Actualización: llegó el Excel del 11 de octubre

Diego envió "Casos Operaciones V2 mejorado2 oct11.xlsx" (12 casos, catálogo de líneas, prelación en Inputs ⑥). Es la nueva fuente de verdad (`fuentes/casos-operaciones-v2.xlsx`) y el motor lo reproduce celda por celda (decisiones D15 a D18). Lo de abajo describe la situación anterior.

## Antes: el Excel "mejorado" de Diego parecía más nuevo que el nuestro

Diego remite al "Excel mejorado" en P-01, P-02, P-03, P-07, P-10, P-12 y P-15, pero en nuestra copia **no está**:
- No hay catálogo de tasas por línea de crédito ni varias líneas (Inputs tiene una sola tasa, 87,32 % EA).
- FCC y asistencia se causan siempre hasta la cuota 86, también en mora (`Caso8!R20 = IF(A20<=Inputs!B15, Inputs!B28, 0)`); Diego dice que el mejorado ya no los cobra en mora/no pago.
- El interés corriente sigue sobre el saldo total con el vencido (P-doble); Diego dice que el mejorado ya lo corrigió.

**Acción:** pedirle a Diego la última versión del Excel antes de implementar P-02, P-03, P-07, P-10 y P-12. Al llegar: reemplazar `fuentes/`, regenerar CSV y ver qué pruebas cambian.

## Respuestas que cambian el motor

| P (doc) | Nuestra | Respuesta | Efecto en el motor |
|---|---|---|---|
| P-03 | P3, P-doble | Mora = tasa corriente (es la máxima legal). El mejorado separa saldo corriente y saldo en mora: ya no se cobra corriente + mora sobre todo el saldo. | `baseInteres = SALDO_VIGENTE`. La fórmula de mora (`formulaMora`) se confirma con el Excel nuevo. |
| P-05 | P2 | Sin redondeo; cálculos exactos y solo formato a 0 decimales. | Ya es así (`Calc.MC`). Cerrar P2. |
| P-07 | P6 | FCC = "Fondo de Coberturas Crediticias", genérico **Fondo de garantías**. Sobre el monto financiado inicial. En mora o no pago el fondo y la asistencia **no se cobran**; solo el seguro de vida. | `baseServicios = MONTO_INICIAL` (ya). Regla nueva: suspender fondo y asistencia en semanas en mora o sin pago. Renombrar FCC → Fondo de garantías en el código y las cuentas. |
| P-08 | — | IVA 19 % en fondo y asistencia; seguro exento. | Ya es así. |
| P-09 | P11 | La inicial es **opcional**: la digita el asesor y se resta del valor a financiar (ej. 6.500.000 − 700.000 = 5.800.000). | Revierte la decisión del 10 %: la inicial pasa a ser un **valor** de la operación (0 por defecto), no un porcentaje. Confirmar si se resta del total (moto + alistamiento + GPS + gastos comerciales) o solo de moto + alistamiento, como en su ejemplo. |
| P-11 | — | El crédito conserva sus condiciones; solo la tasa puede cambiar por causas externas y entonces se recalcula (igual plazo y menor cuota, o igual cuota y menor plazo). | Versionado de parámetros (HT-04). El cambio de tasa necesita las dos modalidades (hoy el caso 2 solo recalcula la cuota). |
| P-13 | Bloque 3 | Sin elección del cliente: **reducción de plazo**. | `ModalidadAbono.MENOR_PLAZO` por defecto en el clasificador de abonos. |
| P-14 | Bloque 3 | Un abono mayor que el saldo **no es posible**. | Rechazar el abono (validación) en vez de descartar el excedente. |
| P-16 | P7 | En dación y retoma **sí** se cobran mora y cobranza. | **Hecho:** `moraAntesDeRecuperacion = true` causa mora, vencido y servicios en las semanas sin pago (los casos 3 y 4 del Excel no lo hacen). **Falta la cobranza:** el motor la causa por días de atraso de un pago recibido; hay que preguntar cómo se cuentan los días cuando no hay pago (¿7 por semana?). |
| P-17 | P-horizonte | No se llega al horizonte: antes termina en dación o retoma. | Cerrar P-horizonte. `plazoLimiteSemanas` sigue como tope técnico. Ojo: Diego dice "caso 4 o 5"; en el Excel la dación es el caso 3 y la retoma el 4. |
| P-01 | P5 | Catálogo de tasas por línea (máximas, 1,5 × la ordinaria, de la Superfinanciera). Alerta mensual **obligatoria** para que un autorizador actualice la usura. | HU nueva: actualización mensual de la usura con aprobación. Catálogo de tasas por línea (pendiente del Excel nuevo). |
| P-06 | — | Pagos los **martes**; sin manejo de festivos por ahora. | Calendario con vencimiento los martes. |
| P-21 | — | Los estados del Excel son la lista oficial (ampliable). | Ya es así. |
| P-24 | — | **No tener en cuenta** la hoja Validaciones. | Se cae la HU "Devolver el Excel con Resultado software" y el pendiente técnico 11. La HU de las 33 validaciones se reemplaza por la comparación fila a fila, que ya existe. |

## Remiten al Excel mejorado (esperar la versión nueva)

P-02 prelación (P1), P-10 líneas de crédito, P-12 orden de conceptos en el pago anticipado y P-15 pagos que combinan eventos (P10).

## Sin respuesta o pendientes

- **Pendiente explícito:** P-04 plan de cuentas PUC por línea y P-22 origen de los pagos.
- **En blanco:** P-18 legalidad de la cobranza de 3.000/día, P-19 recálculo por usura de los créditos vigentes, P-20 giro a terceros (causado o recaudado), P-25 indicador del caso 9 escrito a mano y P-27 alcance después del simulador.
- **A reunión:** P-23 reglas sin caso en el Excel (idempotencia, reversos, trazabilidad, clasificación de pagos).
