# Caso 9 · Pago tardío y gastos de cobranza

Hoja `Caso9_Cobranza` de `docs/fuentes/casos-operaciones-v2.xlsx`. Prueba: `src/test/java/co/financiera/cartera/casos/Caso9CobranzaTest.java`. Regla contable: `contabilidad/reglas/CobranzaYMora.java`.

**Escenario.** Crédito del ejemplo del Excel (`replicaExcel()`, sin cuota inicial, plazo límite 120 semanas). Del periodo 1 al 42 paga a tiempo. Desde el mes 10, es decir el periodo 43 (`Inputs!C66 = ROUNDDOWN(C62 × B14, 0)`), paga **todas** las cuotas con 14 días de atraso:

```java
new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel().conPlazoLimite(120))
        .desde(43, new PagoContractual(14));
```

**Resultado.** El motor reproduce la hoja **celda por celda** en las 121 filas (periodos 0 a 120) y en las 26 columnas numéricas (de Mes a Pago aseguradora), con tolerancia de 1 COP. El estado coincide en todas las filas. No hubo que cambiar `MotorCartera`: ya tenía la mora por días, la cobranza y la prelación.

## Reglas

| Id | Regla | Fórmula del Excel (fila 4) | Motor | Estado |
|---|---|---|---|---|
| R-9.1 | Inicio de los pagos tardíos: periodo = ROUNDDOWN(mes inicio × semanas/mes) = 43 | `Inputs!C66`, columna C (evento) | `Escenario.desde(43, …)` | Implementada y probada |
| R-9.2 | Cobranza por pago = días de atraso × 3.000 = 42.000, causada si el periodo ≥ 43 y el saldo inicial > 0,005 | `H4 = IF(AND(A4>=C66, D4>0.005), C65, 0)` | `cobranza` en `semana()` (`!esCero(saldoInicial)`) | Implementada y probada |
| R-9.3 | Mora = 0 antes del 43; desde el 43: capital vencido anterior × tasa semanal + capital contractual × ((1 + tasa diaria)^14 − 1) | `F4 = IF(A4<C66, 0, Q3*B17 + G4*(POWER(1+B18, C63)-1))` | `mora` en `semana()` (D6) | Implementada y probada |
| R-9.4 | Capital contractual = capital pagado del caso 1 en ese periodo, y 0 después de la cuota 86 | `G4 = IF(A4<=B15, INDEX(Caso1_Normal!H, A4), 0)` | `saldoPlan` con la cuota original | Implementada y probada |
| R-9.5 | Pago = MIN(cuota pactada + servicios, todo lo adeudado) | `I4 = MIN(B20+X4, D4+N3+H4+O3+F4+P3+E4+Z3+X4)` | `PagoContractual` → `min(cuotaTotal, exigible)` | Implementada y probada |
| R-9.6 | Prelación: cobranza → mora → interés corriente → servicios → capital (D5) | `J4 = MIN(I4, N3+H4)`; `K4 = MIN(MAX(0, I4−J4), O3+F4)`; `L4 = MIN(MAX(0, I4−J4−K4), P3+E4)`; `Y4 = MIN(MAX(0, I4−J4−K4−L4), Z3+X4)`; `M4 = MIN(MAX(0, I4−J4−K4−L4−Y4), D4)` | paso 3 de `semana()` | Implementada y probada |
| R-9.7 | Saldos al cierre: CxC = anterior + causado − pagado (≥ 0); capital vencido = anterior + contractual − capital pagado; capital vigente = saldo − vencido | `N4:S4`, `Z4` | paso 3 de `semana()` | Implementada y probada |
| R-9.8 | Estado: "Cancelado" si saldo + CxC ≤ 0,005; "En mora" si vencido + CxC > 0; si no, "Vigente al día" | `T4` | paso 6 de `semana()` | Implementada y probada |
| R-9.9 | Hay filas mientras quede deuda, hasta el plazo límite (120); después de la 86 se sigue cobrando la cuota financiera sin servicios | `A5`, `C67`, `U4:W4` | `plazoLimiteSemanas`, `dentroDelPlazo` | Implementada y probada |
| R-9.10 | Asiento del pago tardío (AE13:AH25): causación de la cobranza y la mora, y aplicación del recaudo por prelación. En el 43: débitos = créditos = 217.639,45 | `AF15:AG25` | `CobranzaYMora` | Implementada y probada |
| R-9.11 | El recaudo de servicios se acredita a `CXP_SERVICIOS_TERCEROS` (la misma cuenta del caso 1). El Excel la llama "Servicios complementarios (FCC, asistencia, seguro)" | `AE23` | `CobranzaYMora` | Implementada; el nombre de la cuenta queda como pregunta |

### Validaciones (hoja Validaciones, # 29 a 33)

| # | Variable | Periodo | Esperado | Prueba |
|---|---|---|---|---|
| 29 | Cobranza causada | 43 | 42.000 | `validacion29…` |
| 30 | Interés mora | 43 | 1.590,46 | `validacion30…` |
| 31 | Capital pagado | 43 | 21.682,43 | `validacion31…` |
| 32 | Capital vencido | 43 | 43.590,46 | `validacion32…` |
| 33 | Estado al horizonte (exacto) | 120 | "En mora" | `validacion33…` |

### Indicadores (AE3:AF12)

Todos se prueban en `indicadoresDelPanel`. **AF12, "CxC servicios al horizonte" = 70.732,20, sí es una fórmula**: la fórmula matricial `=LOOKUP(2,1/(A4:A123<>""),Z4:Z123)`, que toma la CxC servicios de la última fila (periodo 120). Coincide con la columna Z y con el motor.

## Frontera contable (para no contabilizar nada dos veces)

| Movimiento | Regla dueña del asiento |
|---|---|
| Pago con cobranza causada (`cobranzaCausada > 0`, eventos "Pago tardío + cobranza" y "Periodo adicional con cobranza") | **`CobranzaYMora`** (caso 9): causación de cobranza y mora **y** todo el recaudo (caja → cobranza, mora, interés corriente, servicios, capital) |
| Pago completo a tiempo, sin cobranza | `RecaudoCuota` (caso 1) |
| Pago inferior a la cuota, sin cobranza (incluida la mora sobre el vencido del caso 8) | `PagoInferior` (caso 8) |
| Abono extra o prepago (`abonoExtra`, `prepagoCapital`) | `AbonoExtraordinario` (casos 5, 6 y 7). `CobranzaYMora` solo usa `pagoRecibido` |

`RecaudoCuota` y `PagoInferior` deben devolver vacío cuando `CobranzaYMora.aplica(m)` sea verdadero. La prueba `enUnPagoTardioLaCajaSeDebitaUnaSolaVez` corre `Contabilizador.estandar()` en el periodo 43 y falla si otra regla también debita la caja. Así, al juntar las ramas, se detecta el doble registro.

Pendiente de la frontera: la cobranza y la mora se causan dentro del asiento de este caso, pero el **interés corriente** no. El panel acredita "CxC interés corriente" sin causarla (como el caso 8). Hay que decidir quién causa el interés corriente semanal (CxC interés corriente contra Ingresos por intereses). Si nadie lo hace, la CxC queda negativa en libros.

## Discrepancias del Excel

1. **La mora del caso 9 tiene dos componentes y la del caso 8 uno (P3).** En el caso 9 la mora es el vencido × la tasa semanal más el capital de la cuota × ((1 + diaria)^14 − 1). En el caso 8 es solo el vencido × la tasa semanal. El motor las une en una sola fórmula (D6): con días = 0 se reduce a la del caso 8.
2. **El crédito nunca termina y el capital deja de amortizar.** Desde el periodo 76, el pago de la cuota (174.048,99) se va completo en cobranza (42.000), mora, interés corriente y servicios. El capital pagado es 0 y la CxC servicios empieza a crecer (se congela en 70.732,20). Después de la cuota 86 el cliente sigue pagando 111.014,82 por semana, pero solo cubre cobranza, mora y parte del interés corriente. La CxC interés corriente sube 11.592,66 por semana hasta 394.150,51. Al horizonte el cliente ha pagado 18.742.717 sobre un crédito de 5.904.400 y todavía debe 3.319.011,75 de capital. De ese total, 3.276.000 fueron cobranza y 2.187.091 mora.
3. **Doble cobro sobre el mismo capital después de la 86.** Todo el saldo está vencido (capital vigente ≈ 0). Se causa interés corriente = saldo × tasa (E) **y** mora = vencido × tasa (F), cada uno de 40.303,74 por semana. La misma base paga dos veces la tasa. Hay que revisarlo frente a la usura (P5).
4. **¿Debe tener tope la cobranza?** Son 42.000 fijos por pago, el 24 % de la cuota total, sin relación con el valor en mora. Se cobran 78 veces, incluidas las 34 semanas posteriores al plan en que no vence ninguna cuota nueva ("Periodo adicional con cobranza"). La regulación de gastos de cobranza suele exigir que sean proporcionales y que exista una gestión real.
5. **¿14 días de atraso en una cuota semanal significa que ya venció la siguiente?** El Excel trata cada semana aparte: la cuota del periodo n se paga 14 días tarde, pero la del periodo n+1 se cobra en su fila como si no hubiera vencido aún. Con 14 días de atraso, al pagar la cuota n ya vencieron la n+1 y la n+2. Además, la mora por días (`(1+diaria)^14`) se aplica sobre el capital contractual de la semana aunque en esa misma fila esa cuota se pague parcialmente a capital.
6. **La cobranza cuenta como un pago aunque el pago no cubra la cuota.** Desde el 76 el pago no alcanza el capital y aun así se causan los 42.000 completos, que tienen prioridad 1.
7. **Nombre del evento después de la 86.** El Excel dice "Periodo adicional con cobranza" y el motor, "Periodo adicional". La columna Evento no se compara. No se cambió el motor porque el texto es compartido con otros casos.
8. **Cuenta de servicios (R-9.11).** El panel dice "Servicios complementarios (FCC, asistencia, seguro)"; el caso 1 dice "CxP servicios a terceros". Si es un ingreso propio y no un pasivo con terceros, hace falta otra cuenta en `Cuenta`.

## Preguntas para Diego

- **P3** ¿Cuál es la fórmula oficial de la mora: con el componente por días (caso 9) o solo sobre el vencido (caso 8)? ¿Los días de atraso se cuentan desde el vencimiento de cada cuota?
- **P9.1** ¿Los gastos de cobranza tienen tope (por pago, por mes, % del valor en mora)? ¿Se cobran en semanas sin cuota vencida nueva (después de la 86)?
- **P9.2** ¿Se puede causar interés corriente y mora a la vez sobre el mismo capital vencido? ¿O la mora reemplaza al corriente sobre la parte vencida?
- **P9.3** Si el cliente siempre paga la cuota pactada con atraso, ¿qué pasa al llegar al plazo límite (120 semanas) con saldo pendiente: castigo, cobro jurídico, retoma (caso 4)?
- **P9.4** Con 14 días de atraso en cuotas semanales, ¿el cliente debe 2 cuotas al pagar? ¿La mora debe contar las cuotas vencidas siguientes?
- **P9.5** ¿El recaudo de servicios es un pasivo con terceros (CxP) o un ingreso? Además, ¿quién causa el interés corriente semanal en el asiento?
