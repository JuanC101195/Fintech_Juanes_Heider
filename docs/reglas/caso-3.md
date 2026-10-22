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
| R-3.13 | **REC-1 / REC-2.** En la dación el fondo de garantías cubre **todo** el faltante y **todos** los conceptos: capital, interés corriente, mora, servicios y gastos de cobranza. La moto se aplica por prelación (cobranza → mora → interés → fondo → asistencia → seguro → capital) y el fondo queda con el resto: moto + CxC fondo = deuda total; el cliente no queda debiendo. **Fuente: respuesta de Diego 2026-10-20 (ronda 3), preguntas REC-1 y REC-2.** | `deuda = saldo + CxC cobranza + CxC mora + CxC interés + CxC servicios`; `CxC fondo = MAX(0, deuda − moto)` | L, N (el Excel solo tiene interés pendiente) | Ya estaba (`MotorCartera.cerrar`); se agregó la prueba con todos los conceptos pendientes: `RespuestasDiegoRonda3Test.Rec1Rec2FondoCubreTodo`. REC-3 (si el fondo no alcanza, CxC al cliente y cobro jurídico) no está implementada: el motor no modela el cupo del fondo. |
| R-3.14 | **REC-8.** La semana del evento (dación o retoma) cuenta como **vencida**: no se supone un pago a tiempo, se causa como semana sin pago (con mora y servicios si `moraAntesDeRecuperacion`) y suma una cuota vencida (caso 3: 4 = semanas 31 a 34). Si el escenario solo trae el `CierrePorRecuperacion` en esa semana, el motor la trata como `SinPago`; un pago explícito en la misma semana se respeta. **Fuente: respuesta de Diego 2026-10-20 (ronda 3), pregunta REC-8.** | `K = A − C43` en la fila del evento | K37 (validación 10) | `MotorCartera.leer` (antes, sin pago explícito, el motor suponía un pago a tiempo; los casos 3, 4 y 11 ya ponían `SinPago` explícito y no cambian) | `RespuestasDiegoRonda3Test.Rec8SemanaDelEventoVencida` |

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

## Respuestas de la ronda 4 (2026-10-21)

| Id | Regla | Implementada en | Probada |
|---|---|---|---|
| R4-R2 | En la recuperación se descuenta el **costo real con soporte** (grúa, bodegaje, avalúo, trámites), en dación y en retoma. `CierrePorRecuperacion` lleva `costoRecuperacion` opcional (0 por defecto, como el Excel; en el API, el campo `costoRecuperacion` de la instrucción `CIERRE_POR_RECUPERACION`). **Fuente: respuesta de Diego 2026-10-21 (ronda 4), pregunta R4-R2.** | `MotorCartera.cerrar`, `Instruccion.CierrePorRecuperacion` | `RespuestasDiegoRonda4Test.R2CostoDeLaRecuperacion` |
| R4-R2-DACION | **Provisional.** En la dación el costo se **suma al faltante que cubre el fondo de garantías**: CxC fondo = deuda + costo − valor de la moto; la moto paga primero el costo y el resto va a la deuda por prelación. Es la opción más conservadora mientras Diego responde P5-COSTO-DACION (ronda 5: ¿lo cubre el fondo o lo asume la financiera?). Detrás de la clave `costoRecuperacionAlFondo` (`true` en `config/` y en `EXCEL`; con costo 0 no cambia nada). Con `false` la moto paga primero la deuda, el fondo cubre solo deuda − moto y el costo lo asume la financiera. **Fuente: respuesta de Diego 2026-10-21 (ronda 4), pregunta R4-R2.** | `MotorCartera.cerrar`, `ReglasNegocio.costoRecuperacionAlFondo` | `enLaDacionElCostoSeSumaAlFaltanteQueCubreElFondo`, `sinCargoAlFondoElCostoDeLaDacionLoAsumeLaFinanciera` |
| R4-R2-ASIENTO | Asiento de la dación con costo: débito Inventario (valor de la moto) + débito CxC fondo de garantías (faltante, con el costo si va al fondo) + débito Gasto por costos de recuperación asumidos (solo si lo asume la financiera) = créditos de cada concepto extinguido + crédito **CxP costos de recuperación** (el costo completo, a los proveedores de grúa, bodegaje, avalúo y trámites). Cuentas provisionales hasta el PUC (C2). **Fuente: respuesta de Diego 2026-10-21 (ronda 4), pregunta R4-R2.** | `contabilidad/reglas/DacionEnPago`, `ReglaContable.costosRecuperacion` | mismas pruebas (el `Contabilizador` rechaza asientos descuadrados) |
| R4-PERITO | **Requisito (sin código).** El valor de la moto en la recuperación es la guía Fasecolda × 65 %. Se usa un perito externo solo cuando la moto está en Fasecolda pero su estado real tiene alto deterioro. El motor recibe el valor ya definido (`valorMoto`); calcularlo es del módulo de recuperación. **Fuente: respuesta de Diego 2026-10-21 (ronda 4), pregunta R4-PERITO.** | — (fuera del motor) | — |
| R4-FONDO-TOPE | **Regla (sin código).** El fondo de garantías no tiene tope por crédito: cubre todo el faltante de la dación. El motor ya no tenía tope (`cxcFondoGarantias` = todo el faltante), así que no hay nada que cambiar. **Fuente: respuesta de Diego 2026-10-21 (ronda 4), pregunta R4-FONDO-TOPE.** | `MotorCartera.cerrar` (sin cambios) | `RespuestasDiegoRonda3Test` (REC-1/REC-2) |
