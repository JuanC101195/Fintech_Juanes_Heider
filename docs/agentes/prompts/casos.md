# Sección específica de cada agente

Se agregó a la [plantilla común](plantilla-comun.md). Las cifras son las esperadas del Excel `Casos Operaciones V2 mejorado.xlsx`.

## Caso 1 · Operación normal y piezas transversales
**Reglas:** el motor ya reproducía la hoja Caso1_Normal (`Caso1NormalTest`).
**Entregables:**
1. Regla contable `RecaudoCuota` que reproduzca el panel V12:Y17 (periodo 1): débito Caja 174.048,99; crédito Ingresos por intereses 71.698,88, Cartera 39.315,95 y CxP servicios a terceros 63.034,17. Documentar la frontera con los casos 8 y 9 para no contabilizar dos veces.
2. Validaciones 1 a 5 (monto desembolsado, tasa semanal, cuota financiera, saldo final, pago FCC del mes 1).
3. Invariantes con datos aleatorios (semilla fija): montos de 1 a 40 millones, plazos de 6 a 36 meses, EA de 20 % a 120 %: ningún saldo negativo, cierre en cero en la cuota pactada y pago recibido = suma de la prelación.
4. Producto con inicial del 10 % (decisión D2): total 5.904.400, inicial 590.440, financiado 5.313.960, cuota con PMT calculado aparte en la prueba.
5. `docs/reglas/caso-1.md`: catálogo de reglas de Inputs y del caso 1.

## Caso 2 · Cambio de tasa
**Reglas:** mes del cambio 11 → periodo 44; EA nueva 0,80. En el 44, cuota = PMT(tasa nueva, 86 − 44 + 1, saldo). Esperado: nueva cuota 109.308,38; intereses totales 3.569.497,85 (73.376,83 menos que el caso 1); saldo final 0. Sin asiento.
**Entregables:** réplica celda por celda; validaciones 6 a 9; clase pura de **usura** (si la EA supera la usura → usura − 1 punto, conservando la fecha final) con pruebas; `docs/reglas/caso-2.md`.

## Caso 3 · Dación en pago
**Reglas:** último periodo pagado 30, siniestro 34, valor de la dación 3.000.000. Periodos 31 a 34 sin pago: solo interés corriente acumulado, sin mora, vencido ni servicios. En el 34: deuda 4.709.819,75; inventario 3.000.000; CxC FCC 1.709.819,75; estado "Cerrado por evento". Panel Z8:AC14.
**Entregables:** réplica con `SinPago(false)` y `CierrePorRecuperacion(DACION_EN_PAGO, 3.000.000)`; validaciones 10 a 13; regla contable `DacionEnPago`; `docs/reglas/caso-3.md` con propuesta de unificación con el caso 8.

## Caso 4 · Retoma con excedente
**Reglas:** último pagado 13, retoma 17, avalúo 6.800.000. En el 17: deuda 5.614.421,54; CxP deudor 1.185.578,46; CxC FCC 0 (constante en el Excel). Panel Z12:AC18.
**Entregables:** réplica; validaciones 14 a 16; regla contable `Retoma` (no la dación); `docs/reglas/caso-4.md`.

## Caso 5 · Prepago total
**Reglas:** periodo 43: cuota ordinaria y prepago de capital 3.701.573,62; pago total 3.875.622,62; intereses evitados 1.072.063,72. Panel T11:W15.
**Entregables:** réplica; validaciones 17 y 18; regla contable `AbonoExtraordinario` para prepago **y** abono extra (la usan los casos 6 y 7); revisión del giro a terceros cuando el crédito termina a mitad de mes; `docs/reglas/caso-5.md`.

## Caso 6 · Abono extra como menor plazo
**Reglas:** abono de 2.000.000 en el 43, 100 % a capital; saldo 1.701.573,62; la cuota sigue en 111.014,82; último periodo 61, última cuota 6.998,93.
**Entregables:** réplica; validaciones 19 a 21; pruebas de abono mayor que el saldo y de que no aparezca capital vencido falso; `docs/reglas/caso-6.md`. No implementar el asiento (lo hace el caso 5).

## Caso 7 · Abono extra como menor cuota
**Reglas:** abono de 2.000.000 en el 43; desde el 44 cuota = PMT(tasa, 86 − 43, saldo) = 51.032,32 (reducción 59.982,50); cierre en la 86.
**Entregables:** réplica; validaciones 22 a 24; pruebas de plan vigente sin vencido, abono en la última cuota y dos abonos en meses distintos; `docs/reglas/caso-7.md`. No implementar el asiento.

## Caso 8 · Pago inferior, capital vencido y mora
**Reglas:** en el 43 paga 40 % de (cuota + servicios) = 69.619,60; luego la cuota pactada. Prelación mora → interés → servicios → capital. Mora = capital vencido anterior × tasa semanal. Esperado en el 43: capital 0, CxC servicios 39.156,51, vencido 65.272,89; en el 44: mora 792,63. Se cancela en el 89 (límite 100). Panel AB12:AE20 con reclasificación a cartera vencida.
**Entregables:** réplica de todas las columnas; validaciones 25 a 28; regla contable `PagoInferior`; frontera con `RecaudoCuota`; `docs/reglas/caso-8.md`.

## Caso 9 · Pago tardío y gastos de cobranza
**Reglas:** desde el 43 cada cuota se paga con 14 días de atraso; cobranza 3.000/día = 42.000 por pago; mora = vencido × tasa semanal + capital de la cuota × ((1 + tasa diaria)^14 − 1); prelación cobranza → mora → interés → servicios → capital. Esperado en el 43: mora 1.590,46, capital pagado 21.682,43, vencido 43.590,46. Al horizonte de 120 semanas sigue en mora con saldo 3.319.011,75. Panel AE13:AH25 (total 217.639,45).
**Entregables:** réplica hasta la semana 120; validaciones 29 a 33; regla contable `CobranzaYMora`; frontera con las reglas de los casos 1 y 8; `docs/reglas/caso-9.md`.
