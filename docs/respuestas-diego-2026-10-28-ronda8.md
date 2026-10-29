# Respuestas de Diego, ronda 8 (2026-10-28, 11:56)

4 de 4 respondidas. Contraste entre lo que marcó y lo que escribió, y qué se implementa o se pregunta.

| Pregunta | Respuesta de Diego | Seguro de implementar | Va a la ronda 9 |
|---|---|---|---|
| R8-COBRANZA-PAGO-TARDIO | Otra regla. La cobranza **no se prorratea por días**: al llegar a 2 periodos atrasados se activa **un cobro único** de Gastos de Cobranza (valor a definir, campo configurable por el director financiero, como la tasa). Si el crédito llega a otro periodo sin gestión del activo (dación o retoma), se **vuelve a cobrar**, hasta que cierre por dación, retoma o pago. Debe ser un concepto **aparte** de los ítems de la cuota. Ejemplo: 40.000. | Cobro fijo por periodo en lugar de días × valor diario (solo NEGOCIO). El pago completo pero tardío no cuenta como periodo atrasado, así que no cobra (caso 9). Concepto separado. Valor provisional 40.000 (su ejemplo) hasta que el director lo defina. | Momento exacto del primer cobro (ver abajo). |
| R8-REDONDEO-PESO | La cuota exigible se redondea a pesos enteros. | Sí, con redondeo al peso más cercano en NEGOCIO (supuesto a documentar). | — |
| R8-PAGO-INCOMPLETO-EN-FECHA | Otra regla. La causación siempre genera la exigencia de servicios + interés + capital. Hasta 2 periodos de atraso: cuota normal con servicios. Después de 2 periodos: solo se causa el **seguro**; la suspensión de fondo y asistencia coincide con la activación de la cobranza. Incluir el concepto **seguro todo riesgo**, con IVA del 19 % como fondo y asistencias. | Suspender fondo y asistencia tras 2 periodos atrasados y seguir causando solo el seguro. Esto cambia D17 (hoy fondo y asistencia solo en periodos pagados al día). | Seguro todo riesgo (valor, base, si es el seguro actual); servicios suspendidos al ponerse al día. |
| R8-PAGO-MAYOR-EXIGIBLE | El sobrante queda como cuenta por pagar al cliente para devolverlo. | Sí: asiento de CxP al cliente en vez de recortar el pago. Cuenta provisional hasta tener el PUC. | — |

## Preguntas que se generan (ronda 9)

1. **R9-COBRO-MOMENTO.** Su ejemplo dice "luego de 2 cuotas atrasadas se genera el cobro" y "si llega a la 3ra cuota se vuelve a cobrar". El motor hoy cobra desde la tercera cuota vencida (ronda 7). ¿El primer cobro y la suspensión de fondo y asistencia ocurren al cumplirse la 2.ª cuota atrasada, o al llegar la 3.ª?
2. **R9-SEGURO-TODO-RIESGO.** ¿Es el mismo seguro que ya causa el motor o uno nuevo? ¿Cuál es su valor, sobre qué base se calcula y con qué periodicidad? ¿El IVA del 19 % va sobre todo el valor?
3. **R9-SERVICIOS-AL-PONERSE-AL-DIA.** Si el cliente se pone al día después de la suspensión, ¿los fondos y asistencias de los periodos suspendidos se pierden o se cobran de forma retroactiva?

Pendiente del director financiero (no es pregunta): el valor del gasto de cobranza. Mientras tanto queda provisional en 40.000 por periodo.
