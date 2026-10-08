# Caso 2 · Cambio de tasa

Hoja `Caso2_CambioTasa` del Excel de Diego (`docs/fuentes/casos-operaciones-v2.xlsx`). En el mes 11 la EA del crédito pasa de 87,32 % a 80 %; desde la primera cuota de ese mes se recalcula la cuota conservando la fecha final.

Pruebas: `src/test/java/co/financiera/cartera/casos/Caso2CambioTasaTest.java` (réplica del Excel, panel lateral y validaciones 6 a 9) y `Caso2UsuraTest.java` (regla de usura).

## Reglas

| Id | Regla | Fórmula | Origen en el Excel | Implementada en | Probada |
|---|---|---|---|---|---|
| R-2.1 | Periodo desde el que aplica el cambio = primera cuota semanal del mes del cambio | `ROUNDDOWN((mes − 1) × 52/12) + 1` → mes 11 = cuota 44 | `Inputs!C39` (con `Inputs!C36` = 11) | `Calendario.primerPeriodoDelMes` | Validación 6 (tolerancia 0) |
| R-2.2 | Tasa semanal nueva = equivalente efectiva de la EA nueva | `(1 + EA nueva)^(1/52) − 1` = 0,011367716673677863 | `Inputs!C38` (con `Inputs!C37` = 0,80) | `Tasas.desdeEA` | Validación 7 (1e-6) |
| R-2.3 | Antes del periodo de cambio rige la tasa original; desde él, la nueva | `E = IF(periodo < C39; B17; C38)` | `Caso2_CambioTasa!E4:E89` | `MotorCartera` paso 1 (`CambioTasa`) | Comparación celda a celda |
| R-2.4 | En el periodo de cambio la cuota se recalcula sobre el saldo inicial del periodo, conservando la fecha final (cuota 86) | `G = PMT(tasa nueva; 86 − periodo + 1; −saldo inicial)` = 109.308,38 | `Caso2_CambioTasa!G47` (fórmula de toda la columna G) | `MotorCartera` paso 1 + `Amortizacion.cuotaFija` | Validación 8 (1 COP) e indicador U2 |
| R-2.5 | Después del cambio la cuota nueva se mantiene hasta el final | `G = G(fila anterior)` | `Caso2_CambioTasa!G48:G89` | `MotorCartera` | Comparación celda a celda |
| R-2.6 | Interés, capital, servicios y giro a terceros siguen la mecánica del caso 1 con la tasa vigente; el crédito termina en la cuota 86 con saldo 0 | `F = D × E`; `H = MIN(D; G − F)`; `N = MAX(0; D − H)` | `Caso2_CambioTasa!F, H, I:Q, N` | `MotorCartera` | Comparación celda a celda + validación 9 (saldo final 0 ± 1 COP) |
| R-2.7 | Indicadores: intereses totales 3.569.497,85; reducción frente al caso 1 73.376,83 | `SUM(F4:F89)`; `SUM(Caso1_Normal!F4:F89) − SUM(F4:F89)` | `Caso2_CambioTasa!U3, U4` | (se calcula en la prueba sobre el cronograma) | `indicadorInteresesTotales`, `indicadorReduccionDeInteresesFrenteAlCaso1` |
| R-2.8 | El cambio de tasa **no genera asiento** propio: solo cambia la causación futura de intereses y la cuota | Panel "Sin asiento extraordinario", control U10 − V10 = 0 | `Caso2_CambioTasa!T7:W10` | No hay regla contable del caso 2; la semana 44 se contabiliza como un recaudo normal (regla del caso 1) | `elCambioDeTasaNoGeneraMovimientoExtraordinario` |
| R-2.9 | **Usura** (acuerdo con negocio, no está en el Excel): la tasa del crédito es fija y solo cambia por ley. Si la EA pactada **supera** la usura vigente, se ajusta a **usura − 1 punto** | `si EA > usura → nueva EA = usura − 0,01`; si no, sin cambio | Acuerdo con negocio (Juan Esteban); relacionado con P5 | `nucleo/Usura.java` (`superaUsura`, `tasaAjustada`, `ajustar` → `Instruccion.CambioTasa`) | `Caso2UsuraTest`: 87,32 % vs 80 % → 79 %; por debajo o igual → sin cambio |
| R-2.10 | El ajuste por usura usa la misma mecánica de R-2.4: nueva cuota con la fecha final conservada | `PMT(tasa(usura − 1 pt); 86 − periodo + 1; saldo)` | — | `Usura.ajustar` + `MotorCartera` | `elAjustePorUsuraConservaLaFechaFinalEnElMotor`; con usura 81 % reproduce el Excel celda por celda |

## Discrepancias y ambigüedades del Excel

1. **Saldo base del PMT cuando el cliente está en mora.** El Excel solo modela un crédito al día: la PMT se calcula sobre el saldo inicial completo (`D47`). El motor hace lo mismo (`saldo`, que incluye el capital vencido) y reinicia el plan contractual sobre `saldo − capital vencido`. Si el cliente tiene capital vencido en la semana del cambio, ¿la cuota nueva se calcula sobre el saldo total o solo sobre el capital vigente?
2. **Sin prorrateo.** El interés de la semana 44 se causa completo con la tasa nueva (`F47 = D47 × E47`), aunque el cambio legal pueda regir desde un día que no coincide con el inicio de la semana.
3. **El cambio es por mes, no por fecha.** La hoja ubica el cambio en "la primera cuota del mes 11" con la convención de 52/12 semanas por mes; en la operación real la tasa cambiará en una fecha (resolución de la Superfinanciera).
4. **Residuo final.** El saldo final de la cuota 86 es 1,1e-9 en el Excel (`U5 = N89`), no 0 exacto: la tolerancia de 1 COP lo absorbe (P2, redondeo).
5. **Ruido numérico en el motor (no es del Excel).** En la semana del cambio el motor deja capital vencido y mora del orden de 1e-27 por la resta `saldo − capital vencido` en DECIMAL128; está muy por debajo de `Calc.EPSILON_SALDO` y no cambia estado ni pagos. Se documenta por si en el futuro alguien compara contra cero exacto.
6. **La usura no está en el Excel.** La hoja trata la baja de tasa como un dato de entrada (80 %), sin decir por qué cambia. La regla R-2.9 viene del acuerdo con negocio y se probó que, con usura 81 %, da exactamente el caso 2.

## Preguntas para Diego

1. ¿El cambio de tasa aplica desde **la primera cuota del mes** (como la hoja) o desde **la fecha** en que rige la nueva usura? Si es por fecha y cae a mitad de semana, ¿se prorratea el interés de esa semana?
2. ¿"1 punto" por debajo de la usura es **un punto porcentual de EA** (80 % → 79 %) o un punto de otra tasa (nominal, mensual)? El motor asume punto porcentual de EA.
3. Si la EA pactada es **igual** a la usura, ¿se deja así o también se baja un punto? El motor solo ajusta si la supera estrictamente.
4. ¿Qué usura aplica: la de **consumo y ordinario** o la de **consumo de bajo monto / microcrédito**? (Liga con P5: ¿la EA de 87,32 % cumple la usura de la modalidad?)
5. Si la usura **sube** después de un ajuste, ¿el crédito vuelve a la tasa pactada original o se queda en la ajustada? (Negocio dijo "tasa fija, solo cambia por ley"; el motor no sube tasas por su cuenta.)
6. Si el cliente está **en mora** cuando cambia la tasa, ¿la cuota nueva se calcula sobre el saldo total o sobre el capital vigente? ¿El capital vencido sigue causando mora con la tasa vieja o con la nueva? (Hoy el motor usa la nueva.)
7. ¿El cambio debe quedar registrado como **novedad** (auditoría, aviso al cliente con la nueva cuota) aunque no tenga asiento contable?
8. ¿FCC y seguro se mantienen iguales tras el cambio de tasa? (El Excel los deja constantes; relacionado con P6.)
