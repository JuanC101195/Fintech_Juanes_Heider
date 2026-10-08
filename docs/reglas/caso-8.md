# Caso 8 · Pago inferior

Hoja `Caso8_PagoInferior` de `docs/fuentes/casos-operaciones-v2.xlsx`. En el mes 10 (periodo 43) el cliente paga el 40 % de la cuota total (financiera + servicios); después vuelve a pagar la cuota pactada y los vencidos se aplican por prelación. Plazo límite 100 semanas; el crédito se cancela en el periodo 89.

- Escenario: `replicaExcel().conPlazoLimite(100)` + `PagoParcial(0.4, 0)` en el periodo 43.
- Prueba: `src/test/java/co/financiera/cartera/casos/Caso8PagoInferiorTest.java` compara las 23 columnas numéricas de la hoja (todas menos Periodo, Evento y Estado) con tolerancia de 1 COP, el estado en cada periodo, los indicadores del panel AB2:AC9 y las validaciones 25 a 28.
- Regla contable: `contabilidad/reglas/PagoInferior.java`, prueba `contabilidad/reglas/PagoInferiorTest.java`.
- El motor no necesitó cambios: `MotorCartera` ya reproducía el caso.

## Parámetros (hoja Inputs)

| Celda | Valor | Uso |
|---|---|---|
| C58 | 10 | Mes del pago inferior |
| C59 | 0,4 | Fracción de la cuota total pagada |
| C60 | `ROUNDDOWN(C58 × B14)` = 43 | Periodo del evento |
| C61 | 100 | Plazo límite (semanas) |
| B15 | 86 | Cuotas pactadas |

## Reglas

Fórmulas tomadas de la fila 46 (periodo 43) y la fila 47 (periodo 44); son iguales en todas las filas 4 a 103.

| Id | Regla | Fórmula del Excel | Celda | Implementada en | Probada |
|---|---|---|---|---|---|
| R-8.1 | Interés corriente sobre el saldo total (vigente + vencido) | `E = D × tasa semanal` | E46 | `MotorCartera` paso 2 | Sí |
| R-8.2 | Interés de mora sobre el capital vencido del cierre anterior | `F = N_ant × tasa semanal` | F47 | `MotorCartera` paso 2 (D6 con días = 0) | Sí (44: 792,63) |
| R-8.3 | Capital contractual = capital del cronograma del caso 1; 0 después de la cuota 86 | `G = IF(A ≤ B15, INDEX(Caso1_Normal!H, A), 0)` | G46 | `MotorCartera` (`saldoPlan`, `dentroDelPlazo`) | Sí |
| R-8.4 | Pago del evento = 40 % de (cuota financiera + servicios) | `H = C59 × (B20 + U)` | H46 | `PagoParcial` | Sí (69.619,60) |
| R-8.5 | Pago de las demás semanas = cuota pactada, tope en la deuda total | `H = MIN(B20 + U, D + L_ant + F + M_ant + E + W_ant + U)` | H47 | `PagoContractual` (`min(cuotaTotal, exigible)`) | Sí (89: 81.944,44) |
| R-8.6 | Prelación: mora → interés corriente → servicios → capital | `I = MIN(H, L_ant + F)`; `J = MIN(MAX(0, H − I), M_ant + E)`; `V = MIN(MAX(0, H − I − J), W_ant + U)`; `K = MIN(MAX(0, H − I − J − V), D)` | I46, J46, V46, K46 | `MotorCartera` paso 3 (D5) | Sí (43 y 44) |
| R-8.7 | Cuentas por cobrar: lo causado y no pagado | `L = MAX(0, L_ant + F − I)`; `M = MAX(0, M_ant + E − J)`; `W = MAX(0, W_ant + U − V)` | L46, M46, W46 | `MotorCartera` paso 3 | Sí (W43 = 39.156,51) |
| R-8.8 | Capital vencido: el capital contractual no cubierto; el capital pagado reduce primero el vencido | `N = MAX(0, N_ant + G − K)`; `O = MAX(0, P − N)`; `P = MAX(0, D − K)` | N46, O46, P46 | `MotorCartera` paso 3 | Sí (N43 = 65.272,89; N44 = 106.014,65) |
| R-8.9 | Servicios solo hasta la cuota 86; después del 86 "Periodo adicional" sin servicios ni capital contractual, hasta cancelar o llegar al plazo límite | `R..T = IF(A ≤ B15, Inputs!B28..B30, 0)`; `A = IF(AND(A_ant ≥ B15, P + L + M + W ≤ 0,005), "", IF(A_ant ≥ C61, "", A_ant + 1))` | R46:T46, A46 | `MotorCartera` (`dentroDelPlazo`, `plazoLimiteSemanas`, estado CANCELADO) | Sí (último periodo 89) |
| R-8.10 | Estado: Cancelado si P + L + M + W ≤ 0,005; En mora si N + L + M + W > 0; si no, Vigente al día | Q | Q46 | `MotorCartera` paso 6 | Sí (todos los periodos) |
| R-8.11 | Giro mensual a terceros = servicios **causados** del mes (no los recaudados) | `X = SUMIF(mes, R)` al cerrar el mes | X46:Z46 | `MotorCartera` paso 6 | Sí (43: 194.572,69 FCC) |
| R-8.12 | Asiento del evento: débito Caja = H; crédito CxC interés corriente = J, Cartera de créditos = K, Servicios complementarios = V | AC14, AD15, AD16, AD17 | AB14:AE17 | `PagoInferior` | Sí |
| R-8.13 | Reclasificación: débito Cartera capital vencida, crédito Cartera capital vigente por el capital contractual no pagado (en el Excel `N43`, porque el vencido anterior es 0) | `AC18 = AD19 = INDEX(N, C60)` | AB18:AE19 | `PagoInferior` (`MAX(0, G − K)`, que es el aumento del vencido de la semana) | Sí (65.272,89; control AC20 = AD20 = 134.892,49) |

### Validaciones (hoja Validaciones)

| # | Variable | Periodo | Esperado | Prueba |
|---|---|---|---|---|
| 25 | Pago recibido | 43 (mes 10) | 69.619,60 | `validacion25PagoRecibidoEnElEvento` |
| 26 | Capital vencido | 43 (mes 10) | 65.272,89 | `validacion26CapitalVencidoEnElEvento` |
| 27 | Interés mora | 44 | 792,63 | `validacion27InteresMoraEnElPeriodoSiguiente` |
| 28 | Periodos adicionales (conteo, exacto) | — | 3 | `validacion28PeriodosAdicionalesExacto` |

La validación 28 no trae fórmula ni periodo en la hoja. Se tomó como **último periodo con pago (panel AC7 = `MAX(A4:A103)` = 89) − cuotas pactadas (B15 = 86) = 3**, que coincide con las filas "Periodo adicional" 87, 88 y 89.

## Frontera contable con el recaudo normal (evitar doble asiento)

| Semana | Quién contabiliza | Qué |
|---|---|---|
| Periodo del pago inferior (evento `"Pago inferior"`, `PagoInferior.aplica(m)`) | **`PagoInferior` (caso 8), solo ella** | Caja completa, aplicación por prelación (cobranza, mora, corriente, servicios, capital) y la reclasificación vigente → vencida |
| Pagos completos a tiempo (caso 1) | `RecaudoCuota` (caso 1) | Caja, intereses, capital, servicios a terceros |
| Pagos posteriores con vencidos (44 a 89, "Pago normal - aplica vencidos" en el Excel) | **Sin dueño definido** (ver riesgo 2) | — |

**Riesgo 1 (doble asiento):** si `RecaudoCuota` contabiliza todo movimiento con pago recibido, también tomará el periodo 43 y la Caja quedará debitada dos veces. `RecaudoCuota` debe excluir los movimientos con `PagoInferior.aplica(m)`. La prueba `PagoInferiorTest.conTodasLasReglasLaCajaDelEventoSeDebitaUnaSolaVez` usa `Contabilizador.estandar()` y falla si eso pasa al unir las ramas.

**Riesgo 2 (asientos que faltan):** el Excel solo muestra el panel del evento. En los periodos 44 a 89 hay pago de mora, recuperación de vencidos y, mientras el pago no alcanza el capital contractual, nueva reclasificación (el vencido sube de 65.272,89 a 290.447,99 en el 86). `RecaudoCuota` (panel del caso 1) no tiene mora ni cartera vencida. Hay que decidir qué regla los contabiliza (probablemente la de mora del caso 9 o una generalización de esta).

## Discrepancias del Excel

1. **Prelación sin servicios (P1).** El texto de la hoja Casos (C9, "Mora - Caso 8") dice: 1. intereses de mora, 2. intereses corrientes, 3. amortización de capital. Las fórmulas (V46, K46) pagan servicios **antes** del capital. El motor sigue las fórmulas (D5). En el periodo 43 eso decide si los 23.877,67 van a servicios o a capital.
2. **¿Doble cobro sobre el capital vencido?** El interés corriente se calcula sobre D (saldo total, que incluye el vencido) y además el vencido paga mora (F = N_ant × tasa). En los periodos 87 a 89 se ve claro: todo el saldo está vencido y la mora es igual al interés corriente (87: 3.526,996 y 3.526,996). En todo el crédito el interés corriente del caso 8 supera al del caso 1 en 99.772,35, igual a la mora total cobrada: el cliente paga dos veces la tasa sobre el capital vencido. Se reproduce tal cual.
3. **Un solo pago inferior deja al cliente en mora 46 semanas.** Como el cliente vuelve a pagar exactamente la cuota pactada, la mora y los servicios atrasados se comen parte del capital de cada semana: el vencido nunca baja y crece de 65.272,89 (43) a 290.447,99 (86). Solo se recupera en los periodos adicionales 87 a 89. El texto de Casos ("los vencidos se recuperan por prelación") sugiere otra cosa.
4. **Servicios después del 86.** R..T valen 0 desde el 87 aunque el crédito sigue activo, en mora y con seguro de vida. Si el seguro debe cubrir hasta la cancelación, faltan servicios (P6).
5. **Giro a terceros sobre lo causado.** En el mes 10 se giran a terceros 194.572,69 de FCC (servicios causados del mes) aunque en la semana 43 solo se recaudaron 23.877,67 de servicios; la diferencia (CxC servicios 39.156,51) la adelanta la financiera.
6. **Panel contable distinto al del caso 1.** El caso 8 acredita "CxC interés corriente" (supone una causación previa débito CxC / crédito Ingresos que ningún panel muestra), mientras el caso 1 acredita "Ingresos por intereses" directo. El caso 8 acredita "Servicios complementarios (FCC, asistencia, seguro)" y el caso 1 "CxP servicios a terceros". Tampoco registra la CxC de servicios no pagados (39.156,51). La regla usa `Cuenta.CXP_SERVICIOS_TERCEROS` para "Servicios complementarios", igual que el caso 1, para no agregar cuentas al enum compartido.
7. **"Cartera de créditos" y "Cartera capital vigente"** aparecen como dos cuentas en el panel (AB16 y AB19); el motor usa `CARTERA_VIGENTE` para las dos.
8. **Texto del evento.** El Excel marca "Pago normal - aplica vencidos" en los periodos 44 a 86; el motor dice "Pago normal". Es solo texto (no se compara); `PagoInferior` depende únicamente del evento "Pago inferior", que sí coincide.
9. **Validación 28 sin fórmula** (ver arriba).
10. **Plazo límite distinto por caso:** 100 semanas en el caso 8 (C61) y 120 en el caso 9 (C67). En este caso no se llega al límite (cancela en el 89).

## Preguntas para Diego

1. (P1) ¿La prelación oficial incluye servicios antes del capital, como hacen las fórmulas, o es mora → corriente → capital como dice el texto?
2. ¿El capital vencido debe pagar interés corriente **y** mora a la vez? Si no, ¿el interés corriente se calcula sobre el capital vigente (O) solamente?
3. ¿Es correcto que, tras un pago inferior, el cliente quede en mora hasta después de la cuota 86? ¿O se espera que pague más para ponerse al día (por ejemplo, recalculando la cuota)?
4. (P6) ¿Se causan FCC, asistencia y seguro en los periodos adicionales mientras el crédito sigue activo?
5. ¿El giro mensual a terceros es sobre servicios causados o sobre servicios recaudados?
6. ¿Qué asiento lleva el interés corriente: crédito directo a ingresos (caso 1) o a la CxC causada antes (caso 8)? ¿"Servicios complementarios" es la misma cuenta que "CxP servicios a terceros"?
7. ¿Qué asiento esperan en los periodos posteriores (pago de mora, recuperación de vencidos y nuevas reclasificaciones)?
8. "Periodos adicionales" (validación 28): ¿es último periodo − 86 (= 3)?
