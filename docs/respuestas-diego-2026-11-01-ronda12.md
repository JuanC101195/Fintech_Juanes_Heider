# Respuestas de Diego, ronda 12 (2026-11-01)

Diego respondió el 1 nov las 13 preguntas que seguían abiertas: las 11 de la ronda 12 y R10-JUSTIFICACIONES y R10-VALOR-MOTO-REENTREGADA de la ronda 10. Se aplicaron en la rama `feat/ronda12-respuestas` (decisiones D74 a D83, ADR 0007, versión de reglas `negocio-2026-11-01`). Lo que quedó ambiguo pasó a la ronda 13.

| Pregunta | Respuesta de Diego | Qué se hizo | Decisión |
|---|---|---|---|
| R12-COBRANZA-SALDO-MORA | «Todo lo pendiente (con mora y cobranza), y aplica desde IGUAL o MAYOR a 2 .. el ejemplo es correcto en la lógica matemática» | `comparacionCobranza` pasa de `MAYOR` a `MAYOR_O_IGUAL` en NEGOCIO (sigue la tolerancia de medio centavo); `baseMoraCobranza = TOTAL_PENDIENTE` ya estaba. El ejemplo de la pregunta (la semana 3 y la 4 cobran 40.000) no cambia. | D74 |
| R12-COBRANZA-DENOMINADOR | `cuota-vigente` | Sin cambios; prueba con un abono a menor cuota. | D75 |
| R12-COBRANZA-PERIODICIDADES | `igual-en-todas` | Sin cambios; prueba en semanal, quincenal y mensual. | D75 |
| R12-GIRO-TERCEROS | `mensual` | Sin cambios; prueba de que el giro cae al cerrar cada mes y suma lo recaudado. | D75 |
| R12-SERVICIOS-DESPUES-PLAZO | `mientras-haya-deuda` | `serviciosDespuesDelPlazo = true` en NEGOCIO. Con `SIEMPRE`, después de la última cuota se causan los tres servicios mientras haya saldo de capital; el periodo de una dación o una retoma no los causa. | D76 |
| R12-VALOR-EXIGIBLE | `cuota-mas-pendiente` | Sin cambio de cálculo. Exponerlo en el API es invasivo (cambia el formato de `Movimiento` que fijan las huellas del Excel): pendiente técnico 37. | D80 |
| R12-MARCA-POR-COCIENTE | `por-cociente` + «es cuando el cociente de 2 o más cuotas totales, igual que la cobranza (el hecho que se marque como "lista para recuperar", es lo que activa el cobro de cobranza)» | Clave nueva `marcaPorCociente`: la marca se mide al cerrar cada periodo con el cociente de la cobranza. Persiste (D67); la cobranza solo mientras el cociente siga en 2 o más (supuesto). | D77 |
| R12-PRIMERA-CUOTA-DETALLE (con R10-PRIMERA-CUOTA) | `se-pierden`: «No se cobran después: el cliente solo paga la parte proporcional y el plan sigue igual (la última cuota queda con el saldo)» | Clave nueva `alcanceProrrateoPrimeraCuota = CUOTA_COMPLETA`: capital y servicios × días reales / días nominales; el capital no cobrado lo recoge la última cuota; los servicios no cobrados se pierden. | D79 |
| R12-IVA-FONDO-ASISTENCIAS | Comentario: «se corrige la política del seguro todo riesgo... se debe dar el tratamiento de los fondos y asistencias, donde se lleva un gran total como cuenta por pagar y no se segmenta» | Se revierte la segmentación de D69: el seguro riesgo total con su IVA va en el gran total de terceros. Se retiran `CXP_SEGUROS`, `IVA_SEGUROS_POR_PAGAR`, `cxpAseguradoras` y `SeguroIvaContabilidadTest`. Al cliente se le sigue cobrando 10.000 + 19 % = 11.900. | D78 |
| R12-CUENTA-TERCEROS | Comentario: «Se cambia la política en la pregunta R12, donde se dará tratamiento al seguro todo riesgo igual al de los otros servicios» | Cubierto por D78. El código PUC sigue provisional (2815): se pide en R13-SERVICIOS-CIERRE-ASIENTO. | D78 |
| R12-SERVICIOS-CIERRE | `sin-servicios-cierre-se-pagan` | La primera parte ya la hacía el motor (D70). Pagarle al tercero lo no recaudado con el fondo cambia la contabilidad y no se sabe el asiento: **no se implementa**; pregunta R13-SERVICIOS-CIERRE-ASIENTO. | D81 |
| R10-JUSTIFICACIONES | Comentario: espacio «Justificaciones que congelan el crédito» en el setup de la fábrica de créditos, definidas por el director financiero; congelan por un periodo revisable, NO se perdonan cargos; inicialmente enfermedad, familiar fallecido, moto varada, accidentada, en taller, en garantía o en fiscalía; solo CPPU y CPPR. | Decisión registrada; no va en el motor (fase 3, pendiente técnico 38). Pregunta R13-CONGELAR-CREDITO. | D82 |
| R10-VALOR-MOTO-REENTREGADA | Comentario: el motor no calcula ningún valor; es un crédito nuevo y el asesor ingresa el valor de la moto; es un proceso administrativo fuera del motor. | Ya es así: `Operacion.valorMoto` es un dato de entrada. Prueba nueva. | D83 |

## Efecto en los casos (NEGOCIO)

| | Antes (negocio-2026-10-31) | Ahora (negocio-2026-11-01) |
|---|---|---|
| Caso 11 (40 % en el 43, sin pago en el 44 y el 45, dación en el 45) | Sin cobranza; CxC de servicios al cierre del 44: 121.465,14; cierre en el 45; **marcado desde el 44** (2 eventos). | Mismas cifras; **ya no se marca** (al cierre del 44 debe ~1,6 cuotas). |
| Caso 13 (sin pago en la 40, 5.000 en la 41, abono de 1.500.000 en la 42) | Sin cobranza; el abono paga 501.831,82 de pendientes y 998.168,17 a capital; saldo de la 42: 3.354.386,35; termina en el 86; **marcado en la 41** (periodo listo 41). | Mismas cifras (al iniciar la 42 debe menos de 2 cuotas también con «mayor o igual»); **ya no se marca**. |

Los asientos NEGOCIO de todos los casos cambian por D78 (el seguro riesgo total ya no va en dos líneas aparte). Las simulaciones del API sin fechas no cambian; con fechas, la primera y la última cuota cambian por D79 (ejemplo semanal con la operación del Excel y 3 días de 7: primera semana 71.940 en vez de 127.038; cuota 104 de 157.091,51 en vez de 100.406,51).

## Preguntas que se generan (ronda 13)

1. **R13-COBRANZA-TRAS-MARCA.** Con el crédito ya marcado, si un abono baja el saldo en mora de 2 cuotas, ¿se sigue cobrando la cobranza mientras esté marcado o solo mientras siga en 2 cuotas o más (supuesto)?
2. **R13-PRIMERA-CUOTA-INTERES.** ¿El capital que la primera cuota proporcional no cobra causa interés hasta la última cuota (supuesto: sí, cuota 104 de 157.091,51) o no (116.705,64)?
3. **R13-PRIMERA-CUOTA-LARGA.** Si la entrega es más de un periodo antes del primer vencimiento, ¿capital y servicios completos con interés por los días (supuesto), todo proporcional o no se permite?
4. **R13-SERVICIOS-CIERRE-ASIENTO.** ¿Con qué asiento se le paga al tercero lo causado y no recaudado en el cierre (CxC al fondo de garantías, gasto de la financiera u otro) y cuál es el PUC de la cuenta de terceros?
5. **R13-CONGELAR-CREDITO.** ¿Qué significa «congelar»: se corre el calendario sin causar nada, se corre causando interés y servicios, o el calendario sigue y solo no se cobra cobranza ni se marca?
