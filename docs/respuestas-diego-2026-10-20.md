# Respuestas de Diego · cuestionario en la nube (enviado 2026-10-20 10:11)

Diego (Gerente, dueño de producto) respondió las 24 preguntas del cuestionario publicado. Datos crudos: `respuestas-negocio.md` (generado por `herramientas/importar_respuestas.py --nube`).

**Cómo leerlo.** El formulario no guardaba sin elegir una opción, así que en varias preguntas Diego marcó una opción de relleno y la respuesta real está en el comentario. Aquí se contrasta cada una y **vale el comentario**. Las preguntas cuya opción contradice el comentario (A3 y R2) se descartan: el importador (`DESCARTADOS` en `herramientas/importar_respuestas.py`) no aplica sus ajustes (`abonoMinimo=0`, `costoRecuperacion=0`). También ignora los ajustes de opciones `PENDIENTE` y de filas sin opción.

Sin comentario (tomadas como reales, por confirmar): A6, R3, R4, X1, X3.

## Pagos, mora y cobranza

| ID | Opción marcada | Comentario / detalle | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| PTOPE | Otra regla | "Ese 3.000 por día es un ejemplo, debe ser configurable, es sensible como la tasa de interés y solo la puede tocar una persona (administrador)." | Los gastos de cobranza dependen de una política que enviará Diego. El valor es un parámetro sensible. | Parámetro de cobranza restringido a un rol administrador (D25). El 3.000/día y el tope por pago quedan como configuración, no como regla fija. Pendiente: política de activación. |
| PCOB | Otra forma | "Va dentro de la política que nos pasa Diego." | Cómo se cuentan los días de cobranza sin pago lo define la política de cobranza. | Sin cambio hasta recibirla (D8 sigue sin cobranza en semanas sin pago). |
| PDIAS | Otra forma | "Todo depende de la periodicidad: semanal, quincenal, mensual; no se puede encajar en un periodo, depende de la negociación." | La periodicidad se negocia por crédito. | Mora, cobranza y servicios deben calcularse según la periodicidad del crédito, no solo semanal (D24, pendiente de diseño). |

## Servicios complementarios

| ID | Opción marcada | Comentario | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| S3 | Valor mensual ÷ 4,333 en cada cuota | "No es que se cobren por semana, el ejemplo es semanal; todo se cobra acorde a la configuración del crédito." | Los servicios se cobran según la periodicidad del crédito, no por semana. Vale el comentario. | El ÷4,333 es solo el caso semanal del ejemplo; el reparto depende de la periodicidad (D24). |
| S6 | Pasivo con terceros | "Ninguna de las tres opciones: se maneja como un ingreso recibido para terceros; en la contabilidad queda como una cuenta por pagar contra el banco, dado que nosotros recaudamos el dinero." | Ingreso recibido para terceros: CxP contra banco. En el PUC colombiano es la 2815 (ingresos recibidos para terceros), que es pasivo: coincide en el fondo con la opción marcada. | Registrar con ese nombre (D23). Código PUC por confirmar (C2). |

## Abonos y prepago

| ID | Opción marcada | Comentario | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| A3 | Sin mínimo (`abonoMinimo=0`) | "En caso de un abono extra se debe aplicar según la prelación de los conceptos en la pestaña Inputs." | **No respondió el mínimo: pregunta abierta.** Opción contradicha por el comentario, no se aplica. | Sin `abonoMinimo`. Preguntar de nuevo. |
| A4 | Primero los vencidos por prelación, el resto a capital | "Siempre se respeta la prelación de pagos, entendiendo que los ítems 1 a 6 no se pueden cobrar anticipadamente." | Prelación de Inputs: 1 cobranza, 2 mora, 3 interés corriente, 4 fondo de garantías, 5 asistencias, 6 seguros, 7 capital. Los ítems 1 a 6 no se cobran por adelantado; el excedente va solo a capital. | Confirma D16 y la aplica también al abono extra (D20). |
| A5 | Tasa vigente | "Si un pago lleva a recalcular la cuota es porque hubo un pago extra, pero no se altera la tasa." | El recálculo por abono no cambia la tasa. | El abono recalcula cuota o plazo con la tasa del crédito, sin tocarla. |
| A6 | Sí, interés corriente por los días transcurridos | (sin comentario) | Prepago total con interés corriente por los días desde la última cuota. | Por confirmar (D21): el prepago cobra interés por días. |

## Cambio de tasa y usura

| ID | Opción marcada | Comentario | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| T1 | Desde la cuota siguiente | "Importante respetar la periodicidad." | El cambio aplica desde la cuota siguiente a la fecha del cambio, según la periodicidad. | Sin cambio en el semanal; con otras periodicidades la "siguiente" es la de su calendario (D24). |
| T2 | Puntos porcentuales de la E.A. (1 pp) | "1 % equivalente a 100 puntos básicos." | Tasa ajustada = usura − 1 pp de la E.A. (100 pb). | `margenUsura=1`, válido; pendiente de desarrollo en el motor (D19). |
| T4 | No, se queda en la tasa ajustada | "Si la usura baja debe seguir bajando, respetando el margen de usura." | No vuelve a la tasa pactada; si la usura baja, la tasa sigue bajando con el margen. Si sube, se queda. | Tasa efectiva = mínimo entre la ajustada previa y (usura − margen) (D19). |
| T5 | Sobre el saldo total | "Investigar sobre las cláusulas aceleratorias de los contratos de mutuo." | **Abierta**: no es una respuesta sobre el saldo. | Pendiente de investigación jurídica. |

## Dación y retoma

| ID | Opción marcada | Comentario / detalle | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| R1 | Otra diferencia | "Es un tema de terminología. Dación: el avalúo comercial de la moto es inferior al saldo insoluto; el diferencial se cobra al fondo de garantías y se cierra la operación. Retoma: se debe devolver dinero porque el valor comercial es mayor al saldo insoluto." | Dación = avalúo < saldo insoluto; retoma = avalúo > saldo. | Es el resultado del avalúo frente al saldo, no un tipo de evento distinto (D22). El diferencial de la dación va al fondo de garantías. |
| R2 | Sí, valor estimado (`costoRecuperacion=0`) | "Eso va dentro de los gastos de cobranza pendientes por definir." | **Abierta**: depende de la política de cobranza. Opción contradicha, no se aplica. | Sin `costoRecuperacion`. |
| R3 | Por el avalúo; la diferencia se ajusta después | (sin comentario) | La moto entra al inventario por el avalúo. | Por confirmar. |
| R4 | Completo, como el Excel | (sin comentario) | Interés completo en la semana del evento. | Por confirmar; es el comportamiento actual. |

## Contabilidad

| ID | Opción marcada | Comentario | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| C1 | Causación semanal | "La causación se hace según los pagos del caso 1 (el plan normal), respetando la periodicidad pactada." | Causación según el plan normal (caso 1), con la periodicidad del crédito. | Sin cambio en el semanal; periodicidad en D24. |
| C2 | Los envío por correo | "Pendiente." | PUC pendiente por correo. | Los códigos PUC quedan sin definir. |
| C3 | La recauda el concesionario | "No afecta en nada la fábrica de crédito: el crédito es sobre lo que se presta. Importante para la simulación con el plan de pagos de la oferta comercial. Guardar el dato para auditoría, no para la fábrica de créditos." | El pago inicial lo recauda el concesionario; el crédito es sobre lo prestado. | Guardar `pagoInicial` para auditoría y para la oferta/simulación; no genera asiento en la fábrica de crédito. |
| C5 | Del valor total: moto + alistamiento + GPS + gastos comerciales | Detalle: "El total de la operación es la misma cosa que el valor de la moto más alistamiento." | **Abierta**: el detalle ("total = moto + alistamiento") contradice la opción marcada (con GPS y gastos comerciales). | Pendiente de confirmar la base del pago inicial. |

## Ajustes al Excel

| ID | Opción marcada | Detalle | Respuesta válida | Implica para el motor |
|---|---|---|---|---|
| X1 | Enviará una versión corregida | (sin comentario) | Mandará un Excel corregido. | Esperar el Excel; luego regenerar CSV y revisar pruebas. |
| X3 | Dación (caso 3) o retoma (caso 4) | (sin comentario) | Dación = caso 3, retoma = caso 4. | Confirma la correspondencia ya usada (ver R1 para el criterio de fondo). |
| X4 | Otra regla | "Se adjunta según Excel." | El plazo límite del caso 8 llega "según Excel". | Esperar el Excel nuevo. |

## Pendiente de Diego

- Política de gastos de cobranza (activación, tope, días sin pago, costos de recuperación): cubre PTOPE, PCOB y R2.
- Códigos del PUC (C2).
- Excel corregido (X1, X4).
- Monto mínimo de un abono extra (A3).
- Cláusulas aceleratorias y saldo de recálculo en mora (T5).
- Base del pago inicial (C5).
- Periodicidad: reglas de cálculo para quincenal y mensual (PDIAS, S3, T1, C1).
