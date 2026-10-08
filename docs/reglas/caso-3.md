# Caso 3 · Dación en pago

Hoja `Caso3_Dacion` del Excel de Diego. El cliente deja de pagar; en el mes del siniestro entrega la moto como parte de pago (avalúo < deuda) y el faltante queda como CxC al fondo de garantías (FCC).

- Prueba: `src/test/java/co/financiera/cartera/casos/Caso3DacionTest.java` (9 pruebas, celda por celda contra `Caso3_Dacion.csv`, validaciones 10–13 y panel contable).
- Escenario: `replicaExcel()`, `SinPago(false)` del periodo 31 al 34 y `CierrePorRecuperacion(DACION_EN_PAGO, 3.000.000)` en el 34.
- Regla contable: `contabilidad/reglas/DacionEnPago.java`, registrada en `Contabilizador.estandar()`.
- Cambios en el motor: **ninguno**. El ciclo actual reproduce la hoja tal cual.

## Parámetros (hoja Inputs)

| Celda | Parámetro | Fórmula | Valor |
|---|---|---|---|
| C40 | Último mes pagado | dato | 7 |
| C41 | Mes del siniestro | `=C40+1` | 8 |
| C42 | Valor dación | dato | 3.000.000 |
| C43 | Último periodo pagado | `=ROUNDDOWN(C40*52/12,0)` | 30 |
| C44 | Periodo siniestro | `=ROUNDDOWN(C41*52/12,0)` | 34 |

## Reglas

| Id | Regla | Fórmula del Excel | Celda | Estado |
|---|---|---|---|---|
| R-3.1 | Periodos 1 a C43: pago normal (igual que el caso 1). | `F = MIN(cuota, D + I_ant + E)`, `G = MIN(F, I_ant + E)`, `H = MIN(MAX(0, F − G), D)` | F4:H53 | Implementada · probada |
| R-3.2 | Periodos C43+1 a C44−1 (31–33), "Mora - sin pago": se causa interés corriente sobre el saldo y se acumula en CxC interés corriente. No hay pago, ni mora, ni capital vencido, ni cobranza. | `E = D × tasa semanal`; `F = 0`; `I = I_ant + E − G` | E, F, I | Implementada (`SinPago(false)`, D8) · probada |
| R-3.3 | Sin servicios desde el primer periodo sin pago: FCC, asistencia y seguro valen 0 y no quedan por cobrar. | `Q,R,S = IF(A ≤ C43, servicio, 0)` | Q4:S53 | Implementada (`SinPago(false)`) · probada |
| R-3.4 | Cuotas vencidas = periodos transcurridos desde el último pagado. | `K = IF(A ≤ C43, 0, A − C43)` → 1, 2, 3, 4 | K4:K53 | Implementada (contador del motor) · probada (validación 10: 4 exacto) |
| R-3.5 | Estado: "Vigente al día" si K = 0, "En mora" si K > 0, "Cerrado por evento" en C44. | `P` | P4:P53 | Implementada · probada |
| R-3.6 | Deuda a extinguir en el siniestro = saldo + CxC interés anterior + interés del periodo. | `L = D + I_ant + E` = 4.709.819,75 | L37 | Implementada (paso 5 del motor: saldo + todas las CxC) · probada (validación 11) |
| R-3.7 | Inventario = valor de la dación. | `M = C42` = 3.000.000 | M37 | Implementada · probada (validación 12) |
| R-3.8 | CxC FCC = faltante de la deuda sobre la moto. | `N = MAX(0, L − M)` = 1.709.819,75 | N37 | Implementada · probada (validación 13) |
| R-3.9 | CxP deudor = excedente de la moto sobre la deuda (0 en este caso). | `O = MAX(0, M − L)` | O37 | Implementada · probada |
| R-3.10 | En el siniestro saldo capital y CxC interés quedan en 0 y el crédito termina. | `I = 0`, `J = 0` si A = C44; filas > C44 vacías | I37, J37 | Implementada · probada |
| R-3.11 | Giro a terceros al cerrar el mes (y al cerrar el crédito): suma de servicios del mes. En el mes 8 es 0 porque no hubo servicios. | `V = SUMIF(mes, Q)` al cambiar de mes | V4:X53 | Implementada · probada |
| R-3.12 | Asiento de la dación: Db Inventario de motos (M), Db CxC FCC (N), Cr Cartera de créditos (D), Cr CxC intereses corrientes (L − D). Control = 0. | panel | Z8:AC14 | Implementada (`DacionEnPago`) · probada |

Indicadores (Z2:AA5): inventario reconocido 3.000.000, CxC FCC 1.709.819,75, CxP deudor 0, saldo capital posterior 0. Probados.

Asiento (valores del Excel):

| Cuenta | Débito | Crédito |
|---|---:|---:|
| Inventario de motos | 3.000.000,00 | |
| CxC FCC | 1.709.819,75 | |
| Cartera de créditos | | 4.491.646,20 |
| CxC intereses corrientes | | 218.173,56 |
| **Total** | **4.709.819,75** | **4.709.819,75** |

## Discrepancias del Excel

1. **Sin mora ni servicios mientras no paga (P7, D8).** En los periodos 31–33 el Excel no causa interés de mora, no pasa capital a vencido y pone los servicios en 0. El caso 8 (pago inferior) sí pasa capital a vencido, causa mora y deja servicios por cobrar. Son la misma situación económica (cliente que no paga la cuota) con dos tratamientos. Se reproduce con `SinPago(false)`; el comportamiento general del motor es `SinPago(true)`.
2. **Sin gastos de cobranza.** El cliente está 4 semanas sin pagar y no se causan gastos de cobranza (el caso 9 los causa con días × 3.000 desde el primer atraso). En la hoja no hay columna de cobranza.
3. **Cuotas vencidas vs. capital vencido.** K cuenta 4 cuotas vencidas pero no hay capital vencido: el saldo se trata entero como vigente hasta el cierre. El asiento acredita todo a "Cartera de créditos" sin separar capital vencido.
4. **La cuota del siniestro cuenta como vencida.** En el periodo 34 K = 4: la semana del siniestro también se cuenta como cuota vencida aunque ese mismo día se entrega la moto.
5. **Nombre de la cuenta.** El panel usa "CxC intereses corrientes" (plural); el caso 8 usa "CxC interés corriente" y así quedó en `Cuenta.CXC_INTERES_CORRIENTE.nombreExcel()`. Es la misma cuenta; la prueba compara por enum, no por texto.
6. **Texto de la hoja Casos (fila 5):** dice que los servicios "no se simulan" fuera del caso 1, pero la hoja 3 sí los causa y gira en los periodos 1–30.
7. **El tipo de evento no queda en la fila.** El Excel distingue dación y retoma solo por la hoja. En el motor el `Movimiento` no guarda el `TipoRecuperacion`; la regla `DacionEnPago` se activa por "cerrado por evento con CxC FCC > 0" (faltante). Funciona mientras dación = faltante y retoma = excedente.

## Preguntas para Diego

- **P7 (ya abierta).** ¿Mientras el cliente no paga antes de la dación se causa interés de mora, capital vencido y servicios, como en el caso 8? ¿O es una regla especial de los casos con siniestro (moto en taller, robo…) que congela todo menos el interés corriente?
- **P3.1** ¿Se causan gastos de cobranza en las semanas sin pago previas a la dación?
- **P3.2** Si hay mora, servicios o cobranza pendientes al recibir la moto, ¿cómo se reparte el valor de la moto? ¿Misma prelación de abonos (cobranza → mora → interés → servicios → capital) y el faltante a CxC FCC? ¿El FCC cubre también servicios y cobranza, o solo capital e intereses?
- **P3.3** ¿Puede haber una dación con avalúo mayor que la deuda (CxP deudor) o una retoma con faltante? Si sí, el movimiento debe llevar el tipo de recuperación y la regla contable no puede decidir por el signo.
- **P3.4** ¿La semana del siniestro cuenta como cuota vencida (K = 4) o el conteo es hasta la semana anterior (3)?
- **P3.5** ¿El periodo del siniestro sale siempre de ROUNDDOWN(mes × 52/12) o de la fecha real de entrega de la moto? En producción el evento llega con fecha, no con mes.
- **P3.6** ¿El valor de la dación es el avalúo comercial, el valor de recepción menos costos (alistamiento, traslado) o lo define un comité? ¿Hay gastos de la dación que se sumen a la deuda?
- **P3.7** Código PUC de "Inventario de motos" y "CxC FCC" (P9). ¿La CxC FCC se reconoce contra el fondo de garantías en el mismo asiento o en uno posterior al reclamo?

## Propuesta si se unifica con el caso 8

Si Diego responde P7 con "sí, se causa todo", la regla general quedaría:

1. Las semanas sin pago se modelan con `SinPago(true)`: el capital contractual pasa a vencido, se causa mora sobre el capital vencido (D6), los servicios quedan en CxC servicios y, si P3.1 = sí, cobranza por días.
2. En el siniestro, deuda a extinguir = saldo capital (vigente + vencido) + CxC interés corriente + CxC mora + CxC servicios + CxC cobranza (el motor ya lo calcula así en el paso 5, así que no cambia).
3. El valor de la moto se aplica con la misma prelación de abonos (P1/P3.2); el faltante va a CxC FCC y el excedente a CxP deudor.
4. El asiento acredita cada componente a su cuenta: Cartera vigente, Cartera vencida, CxC interés corriente, CxC mora, CxC servicios (o CxP terceros si ya se giró) y CxC cobranza. Para eso el `Movimiento` del cierre debe traer el desglose de lo extinguido (hoy deja todo en 0 y solo informa la deuda total) y el tipo de recuperación.
5. El caso 3 seguiría reproduciéndose con `SinPago(false)` como opción de réplica del Excel, igual que hoy.
