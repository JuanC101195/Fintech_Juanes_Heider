# Caso 5 · Pago anticipado total (prepago)

Hoja `Caso5_Prepago` del Excel `docs/fuentes/casos-operaciones-v2.xlsx`. Prueba: `src/test/java/co/financiera/cartera/casos/Caso5PrepagoTest.java`. Escenario: `ParametrosProducto.replicaExcel()` + `Instruccion.PrepagoTotal` en la cuota 43.

El cliente paga normalmente hasta la cuota 42. En la cuota 43 paga la cuota ordinaria completa (financiera + servicios) y después todo el saldo de capital. El crédito queda cancelado y no hay cuotas posteriores.

## Reglas

| Id | Regla | Fórmula | Celda del Excel | Implementada | Probada |
|---|---|---|---|---|---|
| R-5.1 | Periodo del prepago | `ROUNDDOWN(mes × 52/12)`: mes 10 → cuota 43 (`Calendario.periodoDelMes`) | `Inputs!C51` (`C50` = 10) | Sí | `elPeriodoDelPrepagoEsLaCuota43` |
| R-5.2 | Cuota ordinaria del evento normal | interés = saldo × tasa semanal (45.741,93); capital = cuota − interés (65.272,89) | `E46`, `F46`, `G46` | Sí (ciclo normal del motor) | celda por celda + `indicadoresDelPanel` |
| R-5.3 | Prepago de capital = saldo restante después de la cuota | `MAX(0, D − G)` = 3.701.573,62 | `H46`, `U5` | Sí (`MotorCartera`, paso 4) | celda por celda, validación 17 |
| R-5.4 | Saldo posterior = 0 | `MAX(0, D − G − H)` | `I46`, `U8` | Sí | celda por celda, validación 18 |
| R-5.5 | Estado "Cancelado" cuando el saldo final ≤ 0,005 | `IF(I ≤ 0.005, "Cancelado", "Vigente al día")` | `J46` | Sí (`EstadoCredito.CANCELADO`) | `elEstadoYElEventoCoincidenConElExcel` |
| R-5.6 | No hay cuotas después del prepago | filas `A > C51` vacías | `A47:R…` | Sí (el motor corta al cancelar) | `elCreditoTerminaEnLaSemanaDelPrepagoSinCuotasPosteriores` |
| R-5.7 | Servicios de la semana del prepago se cobran completos | FCC + asistencia + seguro por cuota (63.034,17) | `K46:N46` | Sí | celda por celda |
| R-5.8 | Pago total del cliente = cuota + prepago + servicios | `F + H + N` = 3.875.622,62 | `O46` | Sí (`Movimiento.pagoTotalCliente`) | celda por celda |
| R-5.9 | Pago financiero total del evento = cuota + prepago (sin servicios) | 3.812.588,44 | `U6` | Indicador (se calcula en la prueba) | `indicadoresDelPanel` |
| R-5.10 | Intereses futuros evitados = intereses del caso 1 después del prepago | `SUMIF(Caso1_Normal!A, ">"&C51, Caso1_Normal!F)` = 1.072.063,72 | `U7` | Indicador (se calcula en la prueba sobre la corrida del caso 1) | `interesesFuturosEvitadosSonLosDelCaso1DespuesDelPrepago` |
| R-5.11 | Giro a terceros al cierre del mes o del crédito | `SUMIF` de los servicios del mes cuando la fila siguiente está vacía o cambia de mes | `P46:R46` | Sí (`MotorCartera`, paso 6) | celda por celda + `giroATercerosEnLaSemana43EsElMesCompleto` |
| R-5.12 | Asiento del prepago: débito Caja / crédito Cartera de créditos por el prepago (la cuota va por el asiento de recaudo) | 3.701.573,62 / 3.701.573,62 | `T13:W15` | Sí (`contabilidad/reglas/AbonoExtraordinario`) | `asientoDelPrepagoIgualAlPanelContable` |
| R-5.13 | El mismo asiento cubre el abono extra de los casos 6 y 7 | débito Caja / crédito Cartera 2.000.000 | `Caso6_MenorPlazo!U11:X13`, `Caso7_MenorCuota!U14:X16` | Sí (`AbonoExtraordinario`) | `asientoDelAbonoExtraDeLosCasos6y7` |

## Giro a terceros cuando el crédito termina en la semana 43

- Con el calendario del Excel (`mes = ROUNDUP(periodo / (52/12))`) la cuota 43 **no es mitad de mes: es la última semana del mes 10** (el mes 10 tiene las cuotas 40 a 43; la 44 ya es del mes 11). Por eso `ROUNDDOWN(10 × 52/12)` cae justo en el cierre del mes.
- El Excel gira en la cuota 43 el mes completo: FCC 194.572,69, asistencia 43.938,46, aseguradora 13.625,54 (4 semanas). El motor da lo mismo: la semana 43 cierra el mes y además cancela el crédito.
- Si el prepago fuera de verdad a mitad de mes (probado con la cuota 41, fuera del Excel), el motor gira al cancelar solo las semanas ya causadas del mes (40 y 41). La fórmula `P` del Excel haría lo mismo, porque la fila siguiente queda vacía (`OR($B47="", …)`). Las semanas 42 y 43 no se causan ni se giran.
- Queda abierto si el FCC (garantía) o el seguro se deben liquidar distinto cuando el crédito termina antes: ver preguntas.

## Discrepancias y observaciones del Excel

1. **El prepago no incluye intereses entre cuotas.** El Excel trabaja por semanas completas: el prepago se hace el mismo día de la cuota 43, después de pagarla, así que no hay interés causado pendiente. En la operación real el cliente puede prepagar cualquier día; el motor hoy tampoco causa interés por días.
2. **El panel contable solo muestra el prepago** (`T13:W14`). La cuota ordinaria de la semana 43 (con sus servicios) va por el asiento de recaudo normal (caso 1). El asiento del prepago no lleva intereses ni servicios.
3. **Los "intereses futuros evitados" (U7) solo cuentan el interés corriente.** No incluyen los servicios futuros que tampoco se cobran (43 semanas × 63.034,17 ≈ 2.710.469 de FCC, asistencia y seguro). Es un indicador informativo, no afecta saldos.
4. **`U6` (pago financiero total del evento) no incluye servicios**, pero `O46` (pago total cliente) sí. Las dos cifras son correctas, pero conviene nombrarlas distinto en el CRM.
5. **La tabla Validaciones no trae el valor esperado** (la columna "Resultado software" está vacía). Las validaciones 17 y 18 se prueban contra los indicadores del caso (`U5` = prepago capital, `U8` = saldo posterior) con la tolerancia de la tabla (1 COP).
6. **No hay costo por prepago.** El Excel no cobra penalidad ni comisión. Coincide con la regla colombiana (Ley 1555 de 2012: el deudor puede prepagar sin penalidad en créditos de hasta 880 SMMLV), pero falta confirmarlo con negocio.

## Preguntas para Diego

- **P5.1** Si el cliente prepaga entre dos cuotas (por ejemplo, el miércoles), ¿el prepago incluye los intereses causados por días desde la última cuota hasta la fecha del pago? ¿Con qué tasa diaria: `(1+EA)^(1/365) − 1`?
- **P5.2** ¿El prepago exige estar al día? Si hay capital vencido, mora o cobranza, ¿se aplica primero la prelación (cobranza → mora → interés → servicios → capital) y luego el prepago?
- **P5.3** ¿Hay algún costo por prepago (comisión, estudio de paz y salvo)? Entendemos que no (Ley 1555 de 2012).
- **P5.4** Servicios ya facturados del mes: el FCC, la asistencia y el seguro se giran por las semanas causadas. ¿Se debe girar el mes completo al tercero aunque el crédito termine a mitad de mes? ¿Hay devolución al cliente de algo pagado por adelantado?
- **P5.5** El FCC es una garantía calculada sobre el plazo completo. Al terminar antes, ¿el fondo cobra algo adicional o se deja de causar como en el Excel?
- **P5.6** ¿El prepago de la cuota 43 cuenta como "último pago" para el paz y salvo y la liberación de la prenda en ese mismo momento?
