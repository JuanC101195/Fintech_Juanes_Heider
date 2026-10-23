# Respuestas de Diego a las rondas 5 y 6 (2026-10-22)

Diego respondió por el cuestionario, el 2026-10-22 a las 19:53, las **9 de 9** preguntas de las rondas 5 y 6. Este documento contrasta cada una: la opción que marcó contra lo que escribió en el comentario. Formato y criterio como en `respuestas-diego-2026-10-22-documento.md`: **vale el comentario o detalle** cuando lo hay; si no, la opción marcada.

Resultado: en las **9 coinciden** la opción y el comentario. **7 quedan decididas** (D48 a D54 de `decisiones.md`) y **2 quedan pendientes** de la política de cobranza (R6-COBRANZA-ACTIVA y R6-RECUP-VS-CASO11); esas dos se reformulan en la **ronda 7** con opciones concretas.

## Contraste pregunta por pregunta

| ID | Opción marcada | Comentario de Diego | ¿Coinciden? | Respuesta válida | Estado |
|---|---|---|---|---|---|
| P5-PRORRATEO | `solo-interes-todas`: solo el interés, por los días reales; capital y servicios completos; en las tres periodicidades | Se prorratea solo el interés por días reales; capital y servicios completos, "sumándole los días previos a la primera cuota"; en las tres periodicidades | Sí | Solo el interés se prorratea por días reales; capital y servicios van completos; vale para semanal, quincenal y mensual. Los días previos a la primera cuota (desembolso hasta el inicio del primer periodo) se suman al interés de esa cuota | Decidida (D48) |
| P5-PAGO-ANTES-PRIMERA | `abono-extra`: es abono extra a capital | Abono extra a capital | Sí | El pago anterior a la primera cuota es abono extra a capital, aplicado de una vez; el cliente elige menor plazo o menor cuota | Decidida (D49) |
| P5-CASO13 | `correctos`: las cifras nuevas son correctas | Las cifras nuevas son correctas (capital 1.123.017,41; saldo 3.229.537,12) | Sí | Confirma D37: el sobrante del abono vuelve a pasar por la prelación; capital vigente 1.123.017,41 y saldo 3.229.537,12 | Decidida (D50) |
| P5-USURA-DIA | `un-dia-habil`: sí, 1 día hábil | Sí, 1 día hábil; es un requisito del proceso de usura, no del motor | Sí | Sin autorizador, la usura se aplica sola a 1 día hábil de su publicación. El motor no maneja fechas: el proceso de usura dispara la aplicación | Decidida (D51) |
| P5-COSTO-DACION | `suma-al-faltante`: se suma al faltante y lo cubre el fondo de garantías | Se suma al faltante; lo cubre el fondo de garantías (deja de ser PROVISIONAL) | Sí | En la dación el costo real con soporte se suma al faltante y lo cubre el fondo de garantías | Decidida (D52) |
| R6-LIMITE-CORTO | `plazo-del-credito`: su propio plazo | El plazo límite es el propio plazo del crédito (12 meses, límite 12) | Sí | Límite = plazo del crédito contado desde el desembolso, con tope de 24 meses | Decidida (D53) |
| R6-RECUP-AUTO | `marca-persona-sigue`: el motor solo marca; una persona la ordena; aunque el cliente se ponga al día, la persona decide si se ejecuta | El motor solo marca; una persona ordena; aunque el cliente se ponga al día, la persona decide si se ejecuta | Sí | Al llegar a 2 cuotas atrasadas el motor solo marca el crédito «para recuperación». Una persona ordena la dación o la retoma, y es quien decide aunque el cliente se ponga al día | Decidida (D54) |
| R6-COBRANZA-ACTIVA | Pendiente | "Pendiente de definir política de cobranza" | Sí | Sin respuesta: depende de la política de cobranza | Pendiente; se reformula en R7-COBRANZA-ACTIVA |
| R6-RECUP-VS-CASO11 | Pendiente | "Política por definir" | Sí | Sin respuesta: depende de la política de ejecución de la dación | Pendiente; se reformula en R7-RECUP-VS-CASO11 |

## Lo que cambia en el motor

Por implementar en la rama del motor (otro agente la trabaja; este documento solo registra la regla):

- **D48 prorrateo.** Periodo 1 con interés por los días reales más los días previos a la primera cuota; capital y servicios completos; en las tres periodicidades. Detrás de una clave de `ReglasNegocio` para no cambiar la réplica semanal del Excel.
- **D49 pago antes de la primera cuota.** Se trata como abono extra a capital (menor plazo o menor cuota, a elección del cliente).
- **D53 límite corto.** El plazo límite en periodos = MIN(plazo del crédito, `plazoLimiteMeses`).
- **D54 recuperación.** Marca persistente por crédito «para recuperación» al llegar a 2 periodos atrasados (D46); no se quita sola cuando el cliente se pone al día; la dación o la retoma solo las ordena una persona.

Sin cambio de regla (confirmaciones): D50 (caso 13, el motor ya calcula las cifras), D51 (usura 1 día hábil, requisito del proceso). D52: el costo de la dación se suma al faltante del fondo y se quita la marca de provisional.

## Pendiente: ronda 7

Diego marcó «pendiente» dos preguntas que dependen de políticas que aún no define. Se vuelven a preguntar con opciones más concretas (ids `R7-*` en `cuestionario/src/preguntas.ts`):

- **R7-COBRANZA-ACTIVA.** ¿Quién activa la cobranza (director financiero, gerente o comité) y se hace crédito por crédito o por regla (por ejemplo, tras N días de mora)?
- **R7-RECUP-VS-CASO11.** El caso 11 espera 4 periodos antes de la dación; con el umbral de 2 cuotas (D46), ¿se corrige a 2 o se deja en 4 como ejemplo?

Mientras tanto: la cobranza sigue siendo por crédito con un responsable (D44) y el caso 11 sigue en 4 periodos de espera.
