# Respuestas de Diego por documento (2026-10-22)

Diego (Gerente, dueño de producto) respondió por Word ("rptas software.docx"), y no por el cuestionario, ocho preguntas que habían quedado pendientes: R4-T5, X1, COB-4, COB-5, COB-6, PPER-8, PPER-9 y X4. Cada respuesta venía debajo de la captura de su pregunta. La ronda 5 del cuestionario en la nube sigue sin responder.

Formato y criterio como en `respuestas-diego-2026-10-21-ronda4.md`: **vale el comentario o detalle**. Regla de trabajo: lo seguro se documenta y se implementa (decisiones D42 a D47 de `decisiones.md`); lo no seguro va a la ronda 6 (ids `R6-*` en `cuestionario/src/preguntas.ts`). De las 8, 7 quedan respondidas y una (PPER-8) sigue pendiente.

## Respuestas

| ID | Texto literal de Diego | Respuesta válida | Implica para el motor | Estado |
|---|---|---|---|---|
| R4-T5 (antes T5) | "Sobre el saldo capital vigente. En Colombia está prohibido liquidar intereses sobre intereses". | Con el cliente en mora al cambiar la tasa, la cuota se recalcula sobre el capital vigente; lo vencido se cobra aparte. Equivale a la opción «solo sobre el capital vigente». | La base de recálculo es `SALDO_VIGENTE`; ni el interés ni la mora se capitalizan (anatocismo). No acelera el crédito. | Decidida (D42) |
| X1 | "Todas las operaciones tienen servicios complementarios. A manera de ejemplo solo se simuló en una operación normal, pero estos hacen parte de todas las posibles situaciones de las operaciones". | Los servicios (fondo de garantías, asistencia, seguro) aplican a todas las operaciones; la hoja Casos solo los muestra en el caso 1 como ejemplo. | Los casos 3, 4, 8, 9 y cualquier situación nueva llevan servicios con las reglas ya decididas (D7, D17, D24, D29). Ya no depende del Excel corregido. | Decidida (D43) |
| COB-4 | "Los gastos de cobranza sólo se pueden cobrar cuando el cliente esté en mora. Si se pone al día o finaliza sus pagos, no se deben liquidar". | La cobranza se causa solo mientras haya mora; al ponerse al día o terminar de pagar deja de liquidarse. No se cobra después de la última cuota si no hay mora. | La causación de la cobranza depende de que el crédito esté en mora en ese periodo. | Decidida (D44) |
| COB-5 | "El mismo director financiero o gerente de ser el caso dejando la debida justificación y aprobando directamente en el software". | Exonerar o condonar un cobro lo autoriza el director financiero o el gerente, con justificación, aprobando en el software. | Permiso por rol y bitácora de la exoneración: quién, cuándo, qué crédito y por qué. | Decidida (D45) |
| COB-6 | "Los gastos de cobranza no están regulados en su tarifa. Por ley deben estar incluidos dentro del contrato de mutuo y definir claramente cuándo se empiezan a causar y su valor. Eso es lo que faculta el cobro". Y: "El valor de los $3000 es solo un ejemplo, se encuentra pendiente definir el valor real, pero este puede cambiar en el tiempo, lo que es importante es tener un proceso de un ente regulador que active a qué créditos se les inicia dicho cobro o no". | El cobro es legal si el contrato de mutuo dice cuándo se causa y cuánto. El valor real está por definir y cambia en el tiempo. La cobranza se activa por crédito, por un responsable. | Valor de cobranza como parámetro con vigencia en el tiempo (sigue siendo sensible, D26). Activación por crédito, no automática. El contrato debe llevar cuándo se causa y cuánto. Quién activa y con qué criterio: R6-COBRANZA-ACTIVA. | Decidida (D44); el valor real, pendiente |
| PPER-9 | "No hay días específicos, realmente la política es ejecutar dación o retoma luego de 2 cuotas atrasadas, allí depende de la periodicidad pactada". | El umbral de recuperación es de 2 cuotas atrasadas, en periodos de la periodicidad del crédito (no en días). | Umbral parametrizable de 2 periodos en mora. Si el motor ejecuta solo y el caso 11 (4 periodos): R6-RECUP-AUTO y R6-RECUP-VS-CASO11. | Decidida (D46); la política de ejecución de la dación sigue pendiente |
| X4 | "Ampliarlo hasta 104 cuotas, según el recálculo. Muy importante tener presente que en cualquier momento se puede generar la retoma o dación y se acabaría el crédito". | El plazo límite del caso 8 pasa de 100 semanas a 104 cuotas (= 24 meses semanales), coherente con `plazoLimiteMeses = 24` (R4-PLAZO-LIMITE). | El límite del caso 8 = 104 cuotas. El límite es el plazo del crédito contado desde el desembolso (inferido de 104 cuotas = 24 meses). Responde solo en parte P5-LIMITE-DESDE: queda la duda de los créditos de menos de 24 meses (R6-LIMITE-CORTO). | Decidida (D47) |
| PPER-8 | "Este tema está pendiente de definir. Es con respecto a los gastos de cobranza". | Sin respuesta: cuándo vence la cuota mensual y cómo se cobra la cobranza depende de la política de gastos de cobranza. | Sin cambio. No se vuelve a preguntar. | Pendiente de Diego |

## No seguro: va a la ronda 6

Cada una es una confirmación corta con opciones concretas (ids `R6-*` en `cuestionario/src/preguntas.ts`).

- **R6-RECUP-AUTO.** Al llegar a 2 cuotas atrasadas, ¿el motor ejecuta la dación o la retoma automáticamente, o solo marca el crédito «listo para recuperación» y una persona la ordena? ¿Qué pasa si el cliente se pone al día antes de ejecutarla?
- **R6-RECUP-VS-CASO11.** El caso 11 del Excel espera 4 periodos; con la política de 2 cuotas, ¿se ajusta el caso 11 a 2 (el Excel se corrige)?
- **R6-COBRANZA-ACTIVA.** ¿Quién es el «ente regulador» que activa la cobranza por crédito (el administrador, el director financiero, un comité)? ¿Se activa crédito por crédito o por regla (por ejemplo, todos los que pasen X días de mora)?
- **R6-LIMITE-CORTO.** En un crédito de menos de 24 meses, ¿el plazo límite es su propio plazo o los 24 meses? Reemplaza a P5-LIMITE-DESDE, que X4 contesta solo en parte (desde el desembolso y límite = plazo del crédito para el caso de 24 meses).

La ronda 5 (P5-PRORRATEO, P5-PAGO-ANTES-PRIMERA, P5-CASO13, P5-USURA-DIA y P5-COSTO-DACION) sigue abierta sin cambios.

## Pendiente de Diego

No se vuelve a preguntar; solo queda visible.

- **PPER-8** y la política de gastos de cobranza: ahora tiene criterios parciales (solo en mora, activación por crédito, valor con vigencia, contrato, exoneración; D44 y D45), pero faltan el valor real, el tope, por día o por evento y cuándo se activa (COB-1 a COB-3, PTOPE, PCOB, PDIAS).
- Política de ejecución de la dación: PPER-9 fijó el umbral de 2 cuotas; falta la política completa.
- PUC en Excel por correo: CON-1 y C2. La cobranza como ingreso no operacional (R4-COBRANZA-CONTABLE) usa la clase 42; la cuenta exacta espera el PUC.
- Ya pendiente de antes: demás inconsistencias del Excel corregido (P-12, P-15, P10, P-25 y definiciones de «cuotas eliminadas» y «periodos adicionales»). X1 y X4 ya no dependen de él.
