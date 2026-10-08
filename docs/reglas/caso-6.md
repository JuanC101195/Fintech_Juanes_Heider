# Caso 6 · Abono extra como menor plazo

Hoja `Caso6_MenorPlazo` de `docs/fuentes/casos-operaciones-v2.xlsx`. Texto de la hoja Casos (C20): "Cliente hace un abono extra después de la cuota ordinaria. Pide mantener constante el valor de las siguientes cuotas y reducir el plazo restante de financiación."

Escenario: `replicaExcel()` + `AbonoExtra(2.000.000, MENOR_PLAZO)` en el periodo 43. Pruebas: `src/test/java/co/financiera/cartera/casos/Caso6MenorPlazoTest.java` (12 pruebas, todas en verde).

## Entradas

| Dato | Celda | Valor |
|---|---|---|
| Mes del abono | Inputs!C52 | 10 |
| Valor del abono | Inputs!C53 | 2.000.000 |
| Periodo del abono | Inputs!C54 = `ROUNDDOWN(C52 × B14, 0)` | 43 (`Calendario.periodoDelMes`) |

## Reglas

| Id | Regla | Fórmula | Celda de origen | Estado |
|---|---|---|---|---|
| R-6.1 | Interés de la semana sobre el saldo inicial | `E = D × tasa semanal` | E4:E103 | Implementada y probada (celda por celda) |
| R-6.2 | Cuota financiera fija; la última es el residuo | `F = MIN(cuota pactada, D + E)` | F4:F103, Inputs!B20 | Implementada y probada. El motor guarda la cuota pactada en `Movimiento.cuotaFinanciera` y lo pagado es `pagoInteresCorriente + pagoCapital` (ver D-6.1) |
| R-6.3 | Capital ordinario | `G = MIN(D, F − E)` | G4:G103 | Implementada y probada |
| R-6.4 | El abono se aplica después de la cuota ordinaria, 100 % a capital, con tope en el saldo restante | `H = MIN(abono, D − G)` solo en el periodo C54 | H46 | Implementada (`MotorCartera` paso 4: `min(abono, saldo)`) y probada, incluido el tope con un abono de 10.000.000 |
| R-6.5 | Menor plazo: la cuota no se recalcula; el crédito termina cuando el saldo llega a 0 | `J = MAX(0, D − G − H)`; `A(n+1) = "" si J(n) ≤ 0,005` | J4:J103, A5:A103 | Implementada y probada: último periodo 61 |
| R-6.6 | Pago financiero total y pago total del cliente | `I = F + H`; `P = I + O` | I, P | Probada (`pagoTotalCliente` = recibido + abono) |
| R-6.7 | Servicios (FCC, asistencia, seguro) constantes en cada semana con pago, hasta la última (61), aunque la cuota financiera de esa semana sea solo el residuo | `L = Inputs!B28`, `M = Inputs!B29`, `N = Inputs!B30` mientras A no esté vacío | L:O | Implementada y probada. Se dejan de cobrar 25 × 63.034,17 = 1.575.854,31 (ver P-6.1) |
| R-6.8 | Giro a terceros al cerrar cada mes y en la última semana | `SUMIF` del mes si `B(n+1) = ""` o el siguiente periodo es de otro mes | Q:S | Implementada y probada: el mes 14 (57–60) se gira en la 60; el mes 15 solo tiene la semana 61 y se gira completo en ella (63.034,17) |
| R-6.9 | Estado "Amortizado" cuando el saldo final ≤ 0,005 | `K = IF(J ≤ 0,005, "Amortizado", "Vigente")` | K | Probado: `CANCELADO` en la 61, `VIGENTE_AL_DIA` antes |
| R-6.10 | Capital contractual del plan vigente tras el abono | `saldoPlan = saldo − capitalVencido` después del abono | (implícita: G47 en adelante) | Implementada y probada: capital contractual = capital pagado en cada semana, sin capital vencido falso; si el cliente no paga la semana 50, vence exactamente el G53 del Excel (plan nuevo) |
| R-6.11 | Asiento del abono: débito Caja / crédito Cartera de créditos por el abono | V11 = W12 = Inputs!C53; control X13 = 0 | U9:X13 | No implementada aquí: la implementa el caso 5 en `AbonoExtraordinario`. Revisado: el panel coincide con esa regla (débito Caja 2.000.000, crédito Cartera 2.000.000, diferencia 0) |

## Indicadores (panel lateral) y validaciones

| Indicador | Celda | Excel | Motor |
|---|---|---|---|
| Periodo abono | V2 | 43 | 43 |
| Abono extra | V3 | 2.000.000 | 2.000.000 |
| Saldo tras abono | V4 | 1.701.573,62 | 1.701.573,62 |
| Cuota posterior | V5 | 111.014,82 | 111.014,82 |
| Último periodo con pago | V6 = `MAX(A4:A103)` | 61 | 61 |
| Validación 19 · Abono aplicado (per. 43) | Validaciones!A21 | 2.000.000 (tol. 1) | 2.000.000 |
| Validación 20 · Último periodo con pago | Validaciones!A22 | 61 (exacto) | 61 |
| Validación 21 · Cuotas eliminadas | Validaciones!A23 | 25 (exacto) | 86 − 61 = 25 |

La validación 21 no tiene fórmula en el Excel (F23 vacío). Se tomó la definición **cuotas pactadas (Inputs!B15 = 86) − último periodo con pago (V6)**.

## Discrepancias y observaciones

- **D-6.1 · Columna "Cuota financiera" en la última semana.** El Excel muestra 6.998,93 (lo pagado, `MIN(cuota, D + E)`), mientras que `Movimiento.cuotaFinanciera` guarda la cuota pactada 111.014,82. No es un error del motor: la prueba compara la columna F con `pagoInteresCorriente + pagoCapital`. Si el CRM necesita "cuota a cobrar esta semana", conviene exponerla aparte.
- **D-6.2 · Validación 21 sin fórmula.** Ver arriba; confirmar la definición.
- **D-6.3 · Residuo numérico (no es del caso 6).** Desde el periodo 1, también en el caso 1, el capital vencido queda en ~2E-27 y la mora en ~3E-29 por redondeo de DECIMAL128 entre `pagoCapital` y `capitalContractual`. No afecta estados porque `Calc.esCero` tolera 0,005, pero aparece en `capitalVencido` y `pagoMora`. Se sugiere al dueño del núcleo llevar a cero los valores dentro de `EPSILON_SALDO`.
- **D-6.4 · Excedente de abono.** Si el abono es mayor que el saldo, el Excel (H = MIN) y el motor aplican solo el saldo. El excedente no se registra en ningún lado (ni CxP cliente ni devolución).

## Preguntas para Diego

- **P-6.1** ¿Los servicios (FCC, asistencia, seguro) se siguen cobrando sobre el monto inicial aunque el plazo se acorte? El Excel los cobra completos hasta la semana 61 y deja de cobrar las 25 semanas eliminadas (1.575.854,31). ¿La aseguradora y el FCC aceptan que la cobertura termine antes, o hay que cobrar o devolver algo? (Relacionada con P6.)
- **P-6.2** ¿Quién elige menor plazo o menor cuota y cuál es la opción por defecto si el cliente no dice nada? El texto de Casos dice "pide", o sea que elige el cliente.
- **P-6.3** En la última semana el cliente paga la cuota residual (6.998,93) más los servicios completos de la semana (63.034,17). ¿Se cobra la semana completa de servicios o se prorratea?
- **P-6.4** Si el abono supera el saldo, ¿el excedente se devuelve, queda como saldo a favor (CxP cliente) o se rechaza el abono?
- **P-6.5** ¿Hay mínimo de abono extra o un número máximo de abonos por crédito? ¿Se puede abonar si el cliente tiene capital vencido o cuentas por cobrar? (El motor hoy lo permite y el plan nuevo arranca de `saldo − capitalVencido`.)
- **P-6.6** ¿El abono siempre va después de la cuota ordinaria de la semana, o puede aplicarse sin cuota (por ejemplo, entre semanas)?
