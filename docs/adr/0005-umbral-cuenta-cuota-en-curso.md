# ADR 0005 · Umbrales de cobranza y suspensión que cuentan la cuota en curso

**Estado:** reemplazada por la [ADR 0006](0006-servicios-siempre-y-cobranza-por-cociente.md) en NEGOCIO (2026-10-31, R10-CONTADOR-VENCIDAS). Aceptada el 2026-10-30 (ronda 9 de Diego, R9-COBRO-MOMENTO = segunda-cuota); sigue documentada con `umbralesCuentanCuotaEnCurso` en `RespuestasDiegoRonda9Test`.

## Contexto

Diego eligió la **lectura A**: el cliente deja de pagar desde la semana 1; al terminar la semana 2 ya tiene 2 cuotas atrasadas y ahí se cobran 40.000 de cobranza y se suspenden el fondo y la asistencia; en la semana 3 se cobran otros 40.000 (80.000 en total), y así cada periodo atrasado.

Hasta la ronda 8 el motor aplicaba la lectura B: `Causador` comparaba los umbrales (`cuotasVencidasParaCobranza = 2`, `cuotasVencidasParaSuspenderServicios = 2`) con las cuotas vencidas de **periodos anteriores**, así que el primer cobro y la suspensión llegaban con la 3.ª cuota atrasada.

El problema de fondo es el orden del ciclo (`MotorCartera.Corrida.procesarPeriodo`): primero se causa (interés, capital, servicios, mora, cobranza) y después se reparte el pago. Saber si la cuota del periodo "queda atrasada" exige conocer el pago, y lo que se cause (servicios suspendidos o no, cobranza) cambia la cuota que ese pago tiene que cubrir.

## Decisión

Clave nueva `umbralesCuentanCuotaEnCurso` en `ReglasNegocio` (`true` en NEGOCIO, `false` en EXCEL). Con `true`, los dos umbrales cuentan las cuotas atrasadas de periodos anteriores **más la del periodo en curso si queda atrasada**. Los umbrales siguen en 2, ahora con el sentido de "desde la 2.ª cuota atrasada".

El periodo en curso se decide **con la instrucción del periodo, antes de causar** (`MotorCartera.Corrida.atrasaElPeriodo`):

| Instrucción | ¿Cuenta como cuota atrasada? |
|---|---|
| `SinPago` | Sí, siempre |
| `PagoParcial` | Si fracción × cuota total pactada no alcanza la cuota mínima del periodo |
| `PagoValor` | Si el valor no alcanza la cuota mínima del periodo |
| `PagoContractual`, `PagoAlDia` | No (cubren la cuota) |

La **cuota mínima del periodo** es la cuota financiera pactada más los servicios por cuota, con el fondo y la asistencia ya suspendidos si la suspensión aplicaría al contar esta cuota, y redondeada al peso si rige `redondeoCuotaAlPeso`. Si el periodo cuenta como atrasado y llega al umbral, la cobranza fija (`cobranzaPorPeriodo`) se causa aunque el pago sea un `PagoValor` sin días de atraso: cada periodo atrasado cobra.

## Decisiones conservadoras

- **Zona sin lectura coherente.** Un pago que cubre la cuota con fondo y asistencia suspendidos pero no la cuota completa no tiene una respuesta coherente: si se suspenden, el pago cubre la cuota; si no se suspenden, no la cubre. En ese caso **no se adelanta** ni el cobro ni la suspensión (el cliente no paga cobranza por un pago casi completo) y el contador de cuotas vencidas lo cuenta como hasta la ronda 8, así que la cuota siguiente atrasada sí cobra y suspende.
- **El contador de cuotas vencidas no cambia.** `contarCuotasVencidas` sigue decidiendo después del pago (pago completo contra la cuota causada). La marca de recuperación (`cuotasVencidasParaRecuperacion`) ya contaba la cuota en curso, así que ahora la cobranza, la suspensión y la marca empiezan en el mismo periodo.
- **EXCEL no cambia**: sin umbrales (`null`) la clave no tiene efecto, y además EXCEL la tiene en `false`.

## Consecuencias

- En NEGOCIO el primer cobro y la suspensión llegan un periodo antes: el caso 11 cobra desde el periodo 44 (antes 45) y en el caso 13 la semana 41 ya cobra los 40.000 y no causa fondo ni asistencia.
- `RespuestasDiegoRonda8Test` fija la lectura B (`conUmbralesCuentanCuotaEnCurso(false)`) para seguir documentando la ronda 8; la lectura A se prueba en `RespuestasDiegoRonda9Test`.
- Los invariantes I8 e I13 de `PropiedadesFinancierasAuditTest` usan una cota: con la clave, la cuota en curso puede sumar solo si la instrucción es `SinPago`, `PagoParcial` o `PagoValor`, y suma siempre con `SinPago`.
