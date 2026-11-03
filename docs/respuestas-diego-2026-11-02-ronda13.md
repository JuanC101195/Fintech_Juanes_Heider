# Respuestas de Diego a la ronda 13 (2 de noviembre de 2026)

Cuestionario publicado, envío «en progreso» del 2 nov 2026, 11:00 (5 de 5 respondidas). Quedan decididas D84 a D86; D87 queda pendiente (ronda 14).

| Pregunta | Respuesta de Diego | Decisión |
|---|---|---|
| R13-COBRANZA-TRAS-MARCA | Pendiente, con comentario: «la marca se debe activar solo en el caso de 2 o más semanas de mora; en caso de bajar de allí SÍ debe desactivar la marca y no generar el gasto de cobranza (en el ejemplo de $40.000)» | D85: la marca se desactiva al bajar de 2 cuotas de mora (implementado). Duda: cómo se mide «2 semanas» (R14-MARCA-SEMANAS) |
| R13-PRIMERA-CUOTA-INTERES | `sin-interes`: «No: ese capital no causa interés y la última cuota solo suma lo no cobrado (116.705,64 en el ejemplo)» | D84 (implementado) |
| R13-PRIMERA-CUOTA-LARGA | `no-se-permite`: «No se permite: el primer vencimiento siempre cae dentro del primer periodo» | D84 (implementado en el API) |
| R13-SERVICIOS-CIERRE-ASIENTO | Pendiente, con comentario: «se corrige esta apreciación del enunciado y con seguridad corregirá la política. Usted indicó que en el cierre los servicios no recaudados se le pagan igual al tercero, con el fondo de garantías» | D87 pendiente; se vuelve a preguntar (R14-SERVICIOS-CIERRE) |
| R13-CONGELAR-CREDITO | `corre-calendario`: «Se corre el calendario: ese periodo no causa cuota, interés, servicios, mora ni cobranza y el plan termina un periodo después» | D86 (sin código en el motor; el backtesting lo aplica) |

## Qué cambió en el motor (versión de reglas `negocio-2026-11-02`)

- `alcanceProrrateoPrimeraCuota=CUOTA_COMPLETA_SIN_INTERES`: el capital que la primera cuota no cobra no causa interés y la última cuota solo lo suma (116.705,64 con la operación del Excel y 3 días de 7; antes 157.091,51).
- El API rechaza con NEGOCIO un primer vencimiento a más días que el periodo.
- `marcaPorCociente=true`: la marca ya no persiste; se apaga cuando el saldo en mora baja de 2 cuotas.

## Preguntas nuevas (ronda 14)

- **R14-SERVICIOS-CIERRE:** la política correcta para los servicios causados y no recaudados al cierre (y el PUC de la cuenta de terceros).
- **R14-MARCA-SEMANAS:** si «2 o más semanas de mora» es el mismo cociente de la cobranza o cuenta periodos atrasados (con el cociente un cliente puede tener 4 periodos atrasados por el contador de cuotas vencidas y quedar sin marca si en la semana pagó casi todo lo pendiente).
