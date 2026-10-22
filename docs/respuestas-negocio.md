# Respuestas de negocio al cuestionario de reglas

Fuente: la nube (2026-10-21 17:21 (enviado)) · Respondió: Diego (Gerente) · 2026-10-21 17:21 (enviado) · 16 de 16 completas.

## Aplicado al motor (`config/reglas-negocio.properties`)

- `aplicacionAbono=PRIMERO_PENDIENTES` (pregunta anterior)
- `baseInteres=SALDO_VIGENTE` (pregunta anterior)
- `baseServicios=MONTO_INICIAL` (pregunta anterior)
- `interesPrepagoPorDias=true` (pregunta anterior)
- `margenUsura=1` (pregunta anterior)
- `modalidadUsura=MISMA_CUOTA` (pregunta anterior)
- `moraAntesDeRecuperacion=true` (pregunta anterior)

## Decidido pero pendiente de desarrollo en el motor

- `abonoMinimo=0` (pregunta R4-A3)

## Opción contradicha por el comentario: no se aplica

- Ninguna.

## Todas las respuestas

| ID | Pregunta | Respuesta | Valor | Detalle | Comentario |
|---|---|---|---|---|---|
| R4-QUIN | Escribió 104 periodos al año para la quincena. Una quincena son 24 periodos al año (2 por mes). ¿Confirma 24? | Sí, 24 |  |  |  |
| R4-MENSUAL-DIA | Para la cuota mensual escribió "el 5 o el primer día del mes". ¿Es una fecha fija para todos o se elige por crédito? | Fija para todos: el día 5 de cada mes |  |  | Deacuerdo a la fecha de desembolso , el primer pago sera prorrateado segun lo corrido entre dicha fecha y el proximo 5 correspondiente . |
| R4-A3 | ¿Se acepta cualquier abono extra o hay un valor mínimo? | Sin mínimo: se acepta cualquier valor |  |  |  |
| R4-T5 | Si el cliente está en mora cuando cambia la tasa, ¿sobre qué saldo se recalcula la cuota? | Respondo en el comentario / queda pendiente |  |  | Pendiente por dar respuesta. |
| R4-USURA-PLAZO | Si nadie autoriza la nueva usura, ¿a los cuántos días hábiles de su publicación se aplica sola? ¿Vale lo mismo para el lote de créditos vigentes? | Se aplica sola al vencer el plazo, también para el lote de vigentes | 1 días |  |  |
| R4-AVISO-CLIENTE | Dijo que no se avisa al cliente cuando cambia la tasa. ¿Lo confirma o lo valida antes con legal? | Confirmo: no se avisa al cliente |  |  |  |
| R4-R2 | Al recuperar la moto, ¿se descuenta el costo real (grúa, bodegaje, avalúo, trámites) con su soporte? | Sí, el costo real con soporte, tanto en dación como en retoma |  |  |  |
| R4-PERITO | El valor de la dación o la retoma es la guía Fasecolda × 65 %. ¿Cuándo se usa el perito externo? | En otros casos |  | El otro caso es que la moto si registre en fasecolda pero que el estado real de esta este en alto deterioro. |  |
| R4-C5 | ¿El GPS y los gastos comerciales son parte del alistamiento? Sí o no. | Sí: la inicial se resta del valor total (moto + alistamiento + GPS + gastos comerciales) |  |  |  |
| R4-COBRANZA-CONTABLE | Los gastos de cobranza son una compensación de gastos reales, no un ingreso. ¿Cómo se registran? | Otro tratamiento |  | Si se puede manejar como ingresos pero no operacionales, de esta manera no son imputables a ingresos por mayor interes de finaciamiento. |  |
| R4-SUPLENTE | Hay un solo administrador y ningún suplente. ¿Qué pasa si no está? | Se nombra un suplente con los mismos permisos y misma bitácora |  |  |  |
| R4-PLAZO-LINEA | Dijo "máximo 24 meses" de plazo. ¿El tope de 24 vale para todas las líneas o cada línea tiene el suyo? | 24 meses para todas las líneas |  |  | La primer versión de la fabrica de creditos sera con tope maximo de 24 meses pero sin embargo a futuro puede ser adaptable a mas meses . |
| R4-PLAZO-LIMITE | El plazo límite de recuperación va en periodos. ¿Cuántos? | Otro plazo, en meses | 24 meses |  |  |
| R4-PAGO-SIN-CUOTA | Si un pago llega cuando todavía no hay cuota causada, ¿todo cuenta como abono extra? | Respondo en el comentario / queda pendiente |  |  | El unico evento en que no hayan cuotas causadas es antes de la primera , posterior a esto siempre va a existir una cuota causada en ese orden de ideas si llega un pago no se maneja como anticipo si no que cubre la respectiva cuota , y en caso de sobrar dinero si cuenta como extra . |
| R4-CASO13 | Caso 13 (abono extra con pendientes): ¿los valores son correctos? | Respondo en el comentario / queda pendiente |  |  | Si se aplica todo lo vencido deacuerdo al ejemplo , pero del 1.182.645,20 sobrantes se vuelve a respetar la prelación de aplicación de pagos de las 7 lineas previamente explicadas. |
| R4-FONDO-TOPE | El fondo de garantías cubre todo el faltante de una dación. ¿Tiene un tope que el motor deba controlar? | Sin tope por crédito |  |  |  |
