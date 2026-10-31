# ADR 0006 · Servicios siempre en la cuota y cobranza por cociente

**Estado:** aceptada · 2026-10-31 (ronda 10 de Diego, R10-CONTADOR-VENCIDAS; también cierra R11-SEGUROS-TRAS-SUSPENSION y R10-PERIODO-ATRASADO). **Reemplaza a la [ADR 0005](0005-umbral-cuenta-cuota-en-curso.md)** en el perfil NEGOCIO.

## Contexto

Hasta la ronda 9 la cobranza y la suspensión de fondo y asistencia dependían de un contador de cuotas vencidas por eventos (cada pago incompleto o sin pago sumaba 1). Para que «la 2.ª cuota atrasada» cobrara en su propio periodo, la ADR 0005 tuvo que decidir con la instrucción del periodo, antes de causar, si esa cuota quedaba atrasada (`atrasaElPeriodo`, `cuotaMinimaDelPeriodo`), con una zona sin lectura coherente y una decisión conservadora.

El 31 oct Diego respondió R10-CONTADOR-VENCIDAS con otra regla:

1. El cliente siempre paga los servicios (fondo + asistencia + seguros) en la cuota, esté al día o atrasado.
2. La cuenta por pagar con terceros solo se registra cuando hay recaudo efectivo.
3. La cobranza se ejecuta cuando saldo total en mora / valor total de la cuota > 2; con abonos parciales que no llegan a 2 cuotas no hay cobranza, pero sí interés de mora.
4. En retoma o dación se suspenden por completo los servicios (se cierra el crédito).

## Decisión

Tres claves nuevas en `ReglasNegocio`, con el Excel intacto:

| Clave | EXCEL | NEGOCIO | Qué hace |
|---|---|---|---|
| `causacionServicios` | `SOLO_AL_DIA` (D17) | `SIEMPRE` | `SOLO_AL_DIA`: fondo y asistencia solo al día. `SUSPENDER_POR_UMBRAL`: las rondas 8 y 9 (se suspenden con `cuotasVencidasParaSuspenderServicios`). `SIEMPRE`: los tres servicios en todo periodo del plazo; el periodo de una dación o una retoma no causa servicios. |
| `umbralCocienteCobranza` | vacío | `2` | Con valor, la cobranza se causa cuando saldo en mora / cuota total supera el umbral; se ignoran `cuotasVencidasParaCobranza` y `umbralesCuentanCuotaEnCurso` para la cobranza. |
| `baseMoraCobranza` | `TODO_PENDIENTE` | `TODO_PENDIENTE` | Numerador: `CUOTAS_VENCIDAS` (capital vencido + interés + servicios pendientes) o `TODO_PENDIENTE` (además mora y cobranza). Supuesto. |
| `comparacionCobranza` | `MAYOR` | `MAYOR` | `MAYOR` (estricto, «> 2») o `MAYOR_O_IGUAL`. Supuesto. |

`ReglasNegocio.llegaAlUmbralDeCobranza(saldoEnMora, valorCuota)` compara sin dividir (saldo frente a umbral × cuota) con la tolerancia de medio centavo de los saldos: quien debe exactamente 2 cuotas no queda «por encima» por un residuo DECIMAL128. El denominador es la cuota financiera vigente + los servicios por cuota (fondo, asistencia y seguros, sin suspender), al peso si rige `redondeoCuotaAlPeso`. El valor cobrado es el de siempre: `cobranzaPorPeriodo` (D58) o, sin él, los días nominales del periodo × el valor diario.

### Cuándo se evalúa el cociente

Dos opciones: (a) al cerrar el periodo k, con los saldos después del reparto, dejando la cobranza en `cxcCobranza` para el periodo siguiente; (b) al iniciar el periodo k + 1 con los saldos con que cerró el k. **Se eligió (b).** Las dos miden el mismo instante (el cierre de k es el inicio de k + 1) y dan los mismos saldos; (b) es más simple: vive en la causación (`Causador.cobranzaPorCociente`), no añade un paso después del reparto, la cobranza sale como cobranza causada de la fila en que se cobra (la contabilidad de la causación no cambia) y es lo primero que paga la prelación. No mira la instrucción del periodo: si al iniciar la semana 3 el cliente debe más de 2 cuotas, la semana 3 causa la cobranza pague o no. Desaparecen `atrasaElPeriodo`, `cuotaMinimaDelPeriodo` y la zona sin lectura coherente de la ADR 0005 (siguen en el código solo para `umbralesCuentanCuotaEnCurso`, que NEGOCIO ya no usa).

Ejemplo de Diego (deja de pagar desde la semana 1): al iniciar la 2 debe 1 cuota; al iniciar la 3, 2 cuotas más la mora de la 2 (> 2): la semana 3 cobra 40.000 y la 4 otros 40.000. En filas, el primer cobro llega un periodo después que con la ADR 0005 (allí la semana 2 ya cobraba), aunque en el tiempo es el mismo momento: el cierre de la semana 2.

### El contador de cuotas vencidas

`contarCuotasVencidas` no cambia y ya no decide ni la cobranza ni los servicios en NEGOCIO: solo la marca «listo para recuperar» (D46, D54, D67), que sigue por eventos. Si la marca también debe ir por cociente es la pregunta R12-MARCA-POR-COCIENTE.

### Contabilidad

El motor nunca causó una CxC de servicios: los asientos registran los servicios solo cuando se recaudan (punto 2 de Diego, verificado en `RespuestasDiegoRonda10Test`). Con `SIEMPRE`, `Contabilizador.para` usa «Ingresos recibidos para terceros» (`INGRESOS_RECIBIDOS_PARA_TERCEROS`, PUC 2815 provisional) en lugar de «CxP servicios a terceros», y en la dación o la retoma la parte de los servicios pendientes que cubre la moto es recaudo efectivo (`ReglaContable.extincionConRecaudo`); lo que no cubre se extingue contra las CxC como en el Excel. El Excel conserva sus cuentas (no se renombró el enum: `Contabilizador.estandar()` y el perfil EXCEL siguen con `CXP_SERVICIOS_TERCEROS`).

## Qué cambia en los casos 11 y 13 (NEGOCIO)

| | Antes (ADR 0005) | Ahora |
|---|---|---|
| Caso 11 (pago del 40 % en el 43, sin pago en el 44 y el 45, dación en el 45) | Cobranza de 40.000 en el 44 y en el 45 (80.000); fondo y asistencia suspendidos en el 44 y el 45. | Sin cobranza: al iniciar el 44 debe ~0,6 cuotas y al iniciar el 45 ~1,6. El 44 causa fondo y asistencia (CxC de servicios al cierre del 44: 61.837,36 → 121.465,15). El 45 (dación) no causa servicios. La moto paga lo pendiente; el cierre sigue en el 45. Con 4 periodos de espera la cobranza empezaría en el 46. |
| Caso 13 (sin pago en la 40, 5.000 con 3 días en la 41, abono de 1.500.000 en la 42) | La 41 y la 42 cobraban 40.000 cada una y suspendían fondo y asistencia; el abono pagaba 462.575,83 de pendientes; terminaba en el periodo 85. | Sin cobranza (al iniciar la 41 debe 1 cuota; al iniciar la 42, 2 cuotas menos 5.000 más la mora: no supera 2). La 41 y la 42 causan fondo y asistencia (+2 × 59.627,79). El abono paga 501.831,83 de pendientes (−80.000 + 119.255,58 + residuos de redondeo) y 998.168,17 a capital; saldo de la 42: 3.354.386,36 (antes 3.315.130,36); termina en el periodo 86. |

## Consecuencias

- EXCEL no cambia (los casos 1 a 12 y las huellas EXCEL de caracterización quedan iguales). Las huellas NEGOCIO (`negocio.sha256`, `periodicidades.sha256`, `api.sha256`) se regeneraron: cambian por los servicios, la cobranza y la cuenta de terceros.
- `RespuestasDiegoRonda8Test` y `RespuestasDiegoRonda9Test` fijan sus reglas con `ReglasNegocioConfiguradaTest.negocioRonda9()` (`SUSPENDER_POR_UMBRAL`, umbrales por cuotas, sin cociente) para seguir documentando esas rondas.
- `PropiedadesFinancierasAuditTest`: I8 exige, con cociente, que haya cobranza si y solo si el cociente supera el umbral (holgura de 1 peso); I13 exige, con `SIEMPRE`, fondo, asistencia y seguro en todo periodo con capital contractual y ningún servicio en el periodo de una dación o una retoma.
- La versión NEGOCIO pasa a `negocio-2026-10-31`.
- Preguntas de la ronda 12 por los supuestos: R12-COBRANZA-SALDO-MORA, R12-COBRANZA-DENOMINADOR, R12-COBRANZA-PERIODICIDADES, R12-MARCA-POR-COCIENTE, R12-SERVICIOS-CIERRE, R12-SERVICIOS-DESPUES-PLAZO, R12-VALOR-EXIGIBLE, R12-GIRO-TERCEROS y R12-CUENTA-TERCEROS.
