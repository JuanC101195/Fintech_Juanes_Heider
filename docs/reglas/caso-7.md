# Caso 7 · Abono extra como menor cuota

Hoja `Caso7_MenorCuota` del Excel `docs/fuentes/casos-operaciones-v2.xlsx`. Prueba: `src/test/java/co/financiera/cartera/casos/Caso7MenorCuotaTest.java`.

En el mes 10 (periodo 43), después de pagar la cuota ordinaria, el cliente abona 2.000.000 a capital. Se conserva la cuota 86 como última y desde el periodo 44 se cobra una cuota menor. En el motor es `Instruccion.AbonoExtra(valor, ModalidadAbono.MENOR_CUOTA)`.

## Reglas

| Id | Regla | Fórmula | Celda de origen | Implementada en | Probada en |
|---|---|---|---|---|---|
| R-7.1 | Periodo del abono | `ROUNDDOWN(mes × semanas/mes, 0)` = 43 con mes 10 | Inputs C55, C57 | `Calendario.periodoDelMes` | `elPeriodoDelAbonoEsEl43` |
| R-7.2 | Hasta el periodo del abono, inclusive, la cuota es la original | `F = MIN(B20, D + E)` = 111.014,82 | Caso7 F4:F46 | `MotorCartera` (la fila reporta `cuotaDeLaSemana`) | celda por celda, `laNuevaCuota…` |
| R-7.3 | El abono va después de la cuota ordinaria, 100 % a capital, limitado al saldo que queda | `H = MIN(C56, D − G)` | Caso7 H46 | `MotorCartera` paso 4: `min(abono, saldo)` | celda por celda, V22, `abonoMayorQueElSaldo…` |
| R-7.4 | Saldo tras abono | `J = MAX(0, D − G − H)` = 1.701.573,62 | Caso7 J46, panel V4 | `MotorCartera` paso 4 | celda por celda, panel |
| R-7.5 | Nueva cuota desde el periodo siguiente, conservando la cuota 86 como última | `PMT(tasa semanal, 86 − 43, saldo tras abono)` = 51.032,32 | Caso7 F47, panel V5 | `MotorCartera` paso 4: `cuotaFija(tasa, cuotasPactadas − periodo, saldo)` | V23, `laNuevaCuota…` |
| R-7.6 | La nueva cuota se mantiene hasta el final | `F = F(anterior)` | Caso7 F48:F89 | variable `cuota` de la corrida | celda por celda |
| R-7.7 | Capital ordinario | `G = MIN(D, F − E)` | Caso7 G | prelación (paso 3) | celda por celda |
| R-7.8 | Reducción de cuota | `B20 − nueva cuota` = 59.982,50 | panel V6 | derivado | panel |
| R-7.9 | Cierre: saldo 0 en la cuota 86, estado Amortizado | `J89 = 0` | Caso7 J89, panel V7, V8 | estado `CANCELADO` | V24, panel |
| R-7.10 | Servicios constantes y giro mensual a terceros, sin cambios por el abono | `L:N = Inputs B28:B30`; `Q:S` = suma del mes | Caso7 L:S | `Servicios.porCuota`, giro del paso 6 | celda por celda |
| R-7.11 | Pago total del cliente en la semana del abono = cuota + servicios + abono | `P = I + O` = 2.174.048,99 | Caso7 P46 | `Movimiento.pagoTotalCliente()` | celda por celda |
| R-7.12 | Asiento del abono: débito Caja / crédito Cartera de créditos por el valor del abono; control débito − crédito = 0 | `V14 = W15 = C56` | Caso7 U12:X16 | `contabilidad/reglas/AbonoExtraordinario` (agente del caso 5) | pendiente de esa regla |

Columnas comparadas celda por celda (tolerancia 1 COP, Inputs B71): Saldo inicial, Interés, Cuota financiera, Capital ordinario, Abono extra, Pago financiero total, Saldo final, FCC+IVA, Asistencia+IVA, Seguro vida, Servicios, Pago total cliente, Pago FCC, Pago asistencia, Pago aseguradora. Además Estado y Evento texto por texto, y el panel V2:V8.

Validaciones: 22 (abono aplicado en el 43 = 2.000.000), 23 (nueva cuota en el 44 = 51.032,32) y 24 (saldo final en el 86 = 0) pasan.

### R-7.12 · Panel contable

El panel U12:X16 registra un solo asiento por el abono: Caja (débito) 2.000.000 · Cartera de créditos (crédito) 2.000.000 · control 0. Es la misma regla del abono extraordinario de los casos 5 y 6 (100 % a capital); la cuota ordinaria de la semana se contabiliza aparte con la regla del recaudo (caso 1). Este caso **no** implementa el asiento: lo hace `AbonoExtraordinario`. Al integrarla basta verificar que, para el periodo 43, produzca exactamente ese asiento con `Movimiento.abonoExtra()` = 2.000.000.

## Revisiones del motor

- **(a) Plan vigente.** Tras el abono, `saldoPlan = saldo − capital vencido` y la cuota se recalcula. Desde el 44 el capital contractual es igual al capital pagado y no se genera capital vencido ni mora. Prueba: `elCapitalContractualSigueLaNuevaCuotaSinGenerarVencido`. (Hay residuos de BigDecimal del orden de 1E-27 en el vencido. No afectan los resultados.)
- **(b) Abono en la última cuota o mayor que el saldo.** `Amortizacion.cuotaFija` lanza excepción con 0 periodos; el motor solo recalcula si quedan cuotas (`cuotasPactadas − periodo > 0`) y el saldo no es cero, así que no falla. En la cuota 86 el abono aplicado es ~0 porque la cuota ya liquida el saldo. Si el abono supera el saldo, se aplica solo el saldo y el crédito queda `CANCELADO` en esa semana. En la penúltima cuota el PMT de 1 periodo es `saldo × (1 + i)`. Pruebas: `abonoEnLaUltimaCuotaNoRompeElPmt`, `abonoEnLaPenultimaCuotaDejaUnaSolaCuota`, `abonoMayorQueElSaldoCancelaElCreditoSinRecalcularCuota`. En el Excel, un abono en el periodo 86 daría `PMT(…, 0, …)` = `#NUM!` en la fila 87, que no existe, así que no se nota.
- **(c) Dos abonos en meses distintos.** Cada abono recalcula `PMT(tasa vigente, 86 − periodo, saldo)`. El crédito termina en la 86 con saldo 0 y sin vencido. El Excel solo modela un abono. Prueba: `dosAbonosEnMesesDistintosRecalculanLaCuotaCadaVez`.

## Cambio en código compartido

`MotorCartera.semana`: la fila ahora reporta la cuota que se cobró en la semana (`cuotaDeLaSemana`, capturada antes del paso 4) y no la recalculada por el abono. Antes, el periodo 43 mostraba 51.032,32 en vez de 111.014,82 (Caso7 F46). Este cambio no altera ningún cálculo: solo lo que muestra la fila.

## Discrepancias del Excel

- **D-7.1 · Tasa de la nueva cuota.** F47 usa `PMT(Inputs!$B$17, …)`, que es la tasa **original**. Si antes del abono hubo un cambio de tasa (caso 2), el Excel calcularía la cuota con una tasa que ya no aplica, mientras el interés se causa con la nueva, y el saldo no llegaría a 0 en la 86. El motor usa la **tasa vigente**. En la réplica del caso 7 no hay cambio de tasa, así que da igual. Prueba: `conCambioDeTasaPrevioLaNuevaCuotaUsaLaTasaVigente`.
- **D-7.2 · Cuota tope solo antes del abono.** F aplica `MIN(B20, D + E)` hasta el periodo 43, pero desde el 44 copia la cuota sin tope. Con el PMT exacto la última cuota cuadra (G89 = D89), así que no hay diferencia. El motor limita el capital contractual al saldo del plan.
- **D-7.3 · Excedente del abono.** `H = MIN(C56, D − G)` descarta en silencio lo que supera el saldo. El motor hace lo mismo: el excedente no aparece en ningún saldo ni en una CxP al deudor.
- **D-7.4 · Panel V7 “Periodo final”.** `MAX(A4:A89)` siempre da 86 por cómo está armada la tabla. No mide el último periodo con pago.
- **D-7.5 · Solo un abono.** La hoja admite un único abono (Inputs C55:C57). El comportamiento con varios abonos (c) es del motor, no del Excel.

## Preguntas para Diego

1. **(D-7.1)** Si el cliente tuvo un cambio de tasa y luego abona a menor cuota, ¿la nueva cuota se calcula con la tasa vigente? El motor asume que sí.
2. **(D-7.3)** Si el abono supera el saldo, ¿el excedente se devuelve al cliente (CxP deudor) o se rechaza el abono? ¿Debería tratarse como prepago total (caso 5)?
3. Si el cliente tiene capital vencido o cuentas por cobrar (mora, cobranza, servicios) el día del abono, ¿el abono paga primero eso por prelación y solo el resto va a capital? Hoy el motor aplica todo el abono al saldo y recalcula la cuota sobre el saldo total (incluido el vencido), mientras que el plan vigente excluye el vencido. No hay prueba para este caso porque falta la regla.
4. Con varios abonos a menor cuota, ¿cada uno recalcula la cuota sobre las cuotas restantes hasta la 86? El motor asume que sí.
5. ¿FCC, asistencia y seguro siguen igual después del abono (calculados sobre el monto inicial) o bajan con el saldo? Se relaciona con P6. El Excel los deja constantes.
6. ¿Hay un monto mínimo de abono extra, o un número máximo de abonos por mes, para aplicar la modalidad de menor cuota?
