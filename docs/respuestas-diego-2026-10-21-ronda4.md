# Respuestas de Diego · ronda 4 (enviada 2026-10-21 17:21)

Diego (Gerente, dueño de producto) respondió las 16 preguntas de la ronda 4 del cuestionario. Datos crudos: `respuestas-negocio.md` (generado por `herramientas/importar_respuestas.py --nube`). Formato y criterio como en `respuestas-diego-2026-10-20-ronda3.md`: **vale el comentario o detalle sobre la opción marcada**.

Regla de trabajo: lo seguro se documenta y se implementa (decisiones D35 a D41 de `decisiones.md`); lo no seguro va a la ronda 5 (sección posterior a las tablas). De las 16, 15 quedan decididas y una (R4-T5) sigue sin respuesta.

## Periodicidad y plazos

| ID | Opción marcada | Comentario / detalle | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| R4-QUIN | Sí, 24 | (sin comentario) | La quincena tiene 24 periodos al año. | Se quita el "por confirmar" de la quincena (`Periodicidad`, consola, catálogo del API y docs). Cierra el "104" de PPER. |
| R4-MENSUAL-DIA | Fija para todos: el día 5 de cada mes | "De acuerdo a la fecha de desembolso, el primer pago será prorrateado según lo corrido entre dicha fecha y el próximo 5 correspondiente." | La mensual vence el día 5 de cada mes, igual para todos. La primera cuota se prorratea entre el desembolso y el próximo 5. | Día de pago mensual fijo (5). El cómo del prorrateo no está definido: P5-PRORRATEO. |
| R4-PLAZO-LINEA | 24 meses para todas las líneas | "La primera versión de la fábrica de créditos será con tope máximo de 24 meses, pero a futuro puede ser adaptable a más meses." | Tope de 24 meses para todas las líneas en la primera versión, parametrizable (puede crecer). | El tope de plazo debe ser un parámetro, no una constante. La línea ORDINARIO del catálogo (36 meses) baja a 24. |
| R4-PLAZO-LIMITE | Otro plazo, en meses: 24 | (sin comentario) | El plazo límite de recuperación es de 24 meses, convertidos a periodos según la periodicidad. | `plazoLimitePeriodos` = ROUNDDOWN(24 × periodos/12). Desde cuándo se cuenta: P5-LIMITE-DESDE. |

## Abonos y prepago

| ID | Opción marcada | Comentario / detalle | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| R4-A3 | Sin mínimo: se acepta cualquier valor | (sin comentario) | No hay valor mínimo para el abono extra. | `abonoMinimo = 0`. |
| R4-PAGO-SIN-CUOTA | Pendiente (comentario) | "El único evento en que no hay cuotas causadas es antes de la primera; posterior a esto siempre va a existir una cuota causada. Si llega un pago no se maneja como anticipo sino que cubre la respectiva cuota, y en caso de sobrar dinero sí cuenta como extra." | Solo antes de la primera cuota no hay cuota causada. Después siempre la hay: el pago cubre la cuota causada y lo que sobre es abono extra. | Confirma ABN-4 y `ClasificacionPago`. Qué pasa con un pago anterior a la primera cuota: P5-PAGO-ANTES-PRIMERA. |
| R4-CASO13 | Pendiente (comentario) | "Sí se aplica todo lo vencido de acuerdo al ejemplo, pero del 1.182.645,20 sobrantes se vuelve a respetar la prelación de aplicación de pagos de las 7 líneas previamente explicadas." | **Cambio de regla.** Se paga todo lo vencido como en el ejemplo, pero el sobrante (1.182.645,20) vuelve a pasar por la prelación de las 7 líneas (cobranza, mora, interés corriente, fondo, asistencia, seguro, capital) sobre lo ya causado del periodo; solo el resto va a capital. | Cambia D21 y D30 (ver D37): hay que recalcular el caso 13 y mostrarle las cifras nuevas: P5-CASO13. No contradice A4: no se cobran conceptos de periodos futuros, solo los ya causados. |

## Cambio de tasa y usura

| ID | Opción marcada | Comentario / detalle | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| R4-T5 | Pendiente (comentario) | "Pendiente por dar respuesta." | Sin respuesta. | Sin cambio. No se vuelve a preguntar: queda visible como pendiente de Diego. |
| R4-USURA-PLAZO | Se aplica sola al vencer el plazo, también para el lote de vigentes: 1 día | (sin comentario; el valor traía "1 días") | Si nadie autoriza, la usura se aplica sola 1 día hábil después de su publicación, también al lote de vigentes. | El motor no maneja fechas: es un requisito del proceso de usura (quien orquesta). El "1 día hábil" se confirma en P5-USURA-DIA. |
| R4-AVISO-CLIENTE | Confirmo: no se avisa al cliente | (sin comentario) | No se avisa al cliente cuando cambia la tasa. | Sin notificación al cliente. Decisión del dueño de producto; conviene que la valide legal (D39). |

## Dación y retoma

| ID | Opción marcada | Comentario / detalle | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| R4-R2 | Sí, el costo real con soporte, en dación y en retoma | (sin comentario) | En la recuperación se descuenta el costo real con soporte (grúa, bodegaje, avalúo, trámites), en dación y en retoma. | Costo de recuperación real, no un valor estimado. Si en la dación lo cubre el fondo o la financiera: P5-COSTO-DACION. |
| R4-PERITO | En otros casos | Detalle: "El otro caso es que la moto sí registre en Fasecolda pero que el estado real de esta esté en alto deterioro." | El perito externo se usa cuando la moto está en Fasecolda pero su estado real tiene alto deterioro. En lo demás, guía Fasecolda × 65 %. | Valor de recuperación = Fasecolda × 65 %, salvo alto deterioro (perito). |
| R4-FONDO-TOPE | Sin tope por crédito | (sin comentario) | El fondo de garantías no tiene tope por crédito. | Sin tope en el motor; cierra REC-1/REC-3. |

## Contabilidad y roles

| ID | Opción marcada | Comentario / detalle | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| R4-C5 | Sí: la inicial se resta del valor total (moto + alistamiento + GPS + gastos comerciales) | (sin comentario) | El pago inicial se resta del valor total: moto + alistamiento + GPS + gastos comerciales. El GPS y los gastos comerciales son parte del alistamiento. | Cierra C5 y la base de D10. |
| R4-COBRANZA-CONTABLE | Otro tratamiento | Detalle: "Si se puede manejar como ingresos pero no operacionales, de esta manera no son imputables a ingresos por mayor interés de financiamiento." | Los gastos de cobranza se registran como ingresos no operacionales, para que no se imputen como mayor interés de financiación (PUC clase 42). | Regla contable de cobranza como ingreso no operacional; la cuenta exacta espera el PUC. |
| R4-SUPLENTE | Se nombra un suplente con los mismos permisos y misma bitácora | (sin comentario) | Se nombra un suplente con los mismos permisos y la misma bitácora. | Rol suplente equivalente al administrador, con bitácora. |

## No seguro: va a la ronda 5

Cada una es una confirmación corta con opciones concretas (ids `P5-*` en `cuestionario/src/preguntas.ts`).

- **P5-PRORRATEO.** Cómo se prorratea la primera cuota mensual (del desembolso al próximo 5): solo el interés por días reales (capital y servicios completos), o todo proporcional. Si también pasa en la quincenal (5 y 20) y en la semanal (primer martes).
- **P5-LIMITE-DESDE.** El plazo límite de recuperación de 24 meses, ¿se cuenta desde el desembolso o desde el primer atraso? En un crédito de 12 meses, ¿el límite sigue en 24?
- **P5-PAGO-ANTES-PRIMERA.** Un pago antes de la primera cuota (no hay cuota causada): ¿abono extra a capital o se guarda para la primera cuota?
- **P5-CASO13.** Las cifras nuevas del caso 13, con el sobrante pasando por la prelación, para que Diego las valide. El cuestionario lleva el marcador `{{CIFRAS_CASO13}}` hasta que el motor entregue las cifras.
- **P5-COSTO-DACION.** En la dación, el costo real de la recuperación: ¿se suma al faltante que cubre el fondo de garantías o lo asume la financiera?
- **P5-USURA-DIA.** Confirmar "1 día hábil" (el campo traía "1 días").

## Pendiente de Diego

No se vuelve a preguntar; solo queda visible.

- **R4-T5.** Saldo para recalcular la cuota con el cliente en mora al cambiar la tasa: respondió "pendiente por dar respuesta".
- Política de gastos de cobranza: COB-1 a COB-6, PPER-8, PTOPE, PCOB y PDIAS.
- Política de ejecución de la dación: PPER-9.
- PUC en Excel por correo: CON-1 y C2. La cobranza como ingreso no operacional (R4-COBRANZA-CONTABLE) usa la clase 42; la cuenta exacta espera el PUC.
- Ya pendiente de antes: Excel corregido (X1, X4).
