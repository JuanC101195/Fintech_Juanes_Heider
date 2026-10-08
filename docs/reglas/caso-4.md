# Caso 4 · Retoma con excedente

Hoja `Caso4_Retoma`. Texto de la hoja Casos (C18): *"Cliente deja de pagar. Se recibe la moto como parte de pago de la deuda (avalúo comercial moto > saldo deuda), el residual es una CxP al cliente."*

Prueba: `src/test/java/co/financiera/cartera/casos/Caso4RetomaTest.java` (7 pruebas, todas en verde).
Regla contable: `src/main/java/co/financiera/cartera/contabilidad/reglas/Retoma.java`.

Escenario del motor:

```java
new Escenario(Operacion.ejemploExcel(), ParametrosProducto.replicaExcel())
        .entre(14, 17, new SinPago(false))
        .en(17, new CierrePorRecuperacion(TipoRecuperacion.RETOMA, 6_800_000))
```

## Parámetros (hoja Inputs)

| Celda | Parámetro | Valor |
|---|---|---|
| C45 | Mes inicio incumplimiento | 4 |
| C46 | Mes retoma | 4 |
| C47 | Avalúo moto | 6.800.000 |
| C48 | Último periodo pagado = ROUNDDOWN((C45 − 1) × 52 / 12) | 13 |
| C49 | Periodo retoma = ROUNDDOWN(C46 × 52 / 12) | 17 |

## Reglas

| Id | Regla | Fórmula | Celda | Implementada | Probada |
|---|---|---|---|---|---|
| R-4.1 | Periodos 1 a 13: pago normal (cuota financiera + servicios). | F = MIN(cuota, D + I₋₁ + E); G = MIN(F, I₋₁ + E); H = MIN(MAX(0, F − G), D) | F4:H16 | Sí (motor, pago contractual) | Sí |
| R-4.2 | Periodos 14 a 16 "Mora - sin pago": se causa el interés corriente sobre el saldo y se acumula en CxC; el saldo de capital no se mueve. | E = D × tasa semanal; I = I₋₁ + E | E17:I19 | Sí (`SinPago(false)`) | Sí |
| R-4.3 | Mientras no paga **no** se causa mora, **no** pasa capital a vencido y **no** se causan servicios. | Q:S = 0 si periodo > C48 | Q17:S20 | Sí (`SinPago(false)`, D8) | Sí |
| R-4.4 | Cuotas vencidas = periodo − último periodo pagado (incluye la semana de la retoma). | K = A − C48 | K17:K20 | Sí (contador del motor) | Sí |
| R-4.5 | Estado: "En mora" si hay cuotas vencidas; "Cerrado por evento" en la retoma. | P | P4:P20 | Sí | Sí |
| R-4.6 | Deuda a extinguir = saldo de capital + CxC interés anterior + interés de la semana de la retoma. | L = D + I₋₁ + E = 5.614.421,54 | L20 | Sí (motor, paso 5) | Sí |
| R-4.7 | Inventario = avalúo de la moto. | M = C47 = 6.800.000 | M20 | Sí | Sí |
| R-4.8 | CxP deudor = excedente de la moto sobre la deuda. | O = MAX(0, M − L) = 1.185.578,46 | O20 | Sí | Sí |
| R-4.9 | CxC FCC = 0 (constante en la hoja, ver D-4.2). | N = 0 | N4:N53 | Sí (el motor da MAX(0, L − M) = 0) | Sí |
| R-4.10 | En la retoma el saldo de capital y la CxC de interés quedan en 0. | I = 0, J = 0 si periodo = C49 | I20, J20 | Sí | Sí |
| R-4.11 | Giro a terceros: el mes 4 no tiene servicios causados, el giro es 0. | V:X = SUMIF del mes | V4:X20 | Sí | Sí |
| R-4.12 | Asiento de la retoma: Db Inventario de motos = M; Cr Cartera de créditos = D (capital); Cr CxC intereses corrientes = L − D; Cr CxP deudor = O. Control = 0. | ver panel | Z12:AC18 | Sí (`Retoma`) | Sí |

Panel de indicadores (Z2:AA8), reproducido en `reproduceLosIndicadoresDelPanelLateral`: último periodo pagado 13, periodo evento 17, cuotas vencidas 4, deuda a extinguir 5.614.421,54, inventario 6.800.000, CxP deudor 1.185.578,46, saldo capital posterior 0.

Panel contable (Z12:AC18): débito Inventario de motos 6.800.000; crédito Cartera de créditos 5.354.344,01, CxC intereses corrientes 260.077,54 y CxP deudor 1.185.578,46. Control 0.

Validaciones (hoja Validaciones, filas 14 a 16): deuda a extinguir, inventario y CxP deudor en el periodo 17 (mes 4), tolerancia 1 COP. La columna "Resultado software" está vacía en el Excel; la prueba compara contra el valor de la hoja del caso en ese periodo.

## Discrepancias y observaciones del Excel

- **D-4.1 Dación vs. retoma.** Las hojas `Caso3_Dacion` y `Caso4_Retoma` usan exactamente las mismas fórmulas (L, M, O, I, J, K, P). La única diferencia de cálculo es el signo del residuo: en la dación la moto vale menos que la deuda (CxC FCC) y en la retoma vale más (CxP deudor). No hay nada que distinga la naturaleza jurídica de los eventos (dación = entrega voluntaria; retoma = recuperación forzosa por incumplimiento): ni costos de recuperación, ni avalúo distinto, ni trámite. El motor los modela con la misma instrucción `CierrePorRecuperacion` y solo guarda el tipo en la instrucción (no en el `Movimiento`); la regla contable `Retoma` se activa por CxP deudor > 0, no por el tipo.
- **D-4.2 CxC FCC fijo en 0.** En el caso 4 la columna N es la constante `=0`, no la fórmula `MAX(0, L − M)` del caso 3. Si el avalúo cayera por debajo de la deuda, la hoja no mostraría el faltante. El motor siempre calcula `MAX(0, deuda − moto)`, que en este escenario da 0, así que coincide.
- **D-4.3 No se causa mora, vencido ni servicios (P7 / D8).** De la semana 14 a la 17 el Excel solo acumula interés corriente: no hay interés de mora, el capital de las cuotas no pagadas no pasa a vencido (el saldo sigue "vigente" en Cartera de créditos) y no se causan FCC, asistencia ni seguro aunque el crédito siga vivo. En los casos 8 y 9 sí se causan. Se reproduce con `SinPago(false)`.
- **D-4.4 Interés de la semana de la retoma.** La deuda a extinguir incluye el interés corriente completo de la semana 17 (E20), aunque la moto se recibe esa misma semana.
- **D-4.5 Cuotas vencidas incluye la semana de la retoma.** K20 = 17 − 13 = 4, contando como vencida la cuota 17, que vence el mismo día del evento.
- **D-4.6 Panel contable agrupa todo en "CxC intereses corrientes".** AB16 = L − D: todo lo que se extingue aparte del capital se acredita a CxC intereses corrientes. En este caso solo hay interés corriente, pero si hubiera mora, servicios o cobranza pendientes quedarían en la misma cuenta. La regla `Retoma` hace lo mismo (el `Movimiento` del cierre ya trae esas CxC en cero y no las separa).
- **D-4.7 Nombre de la cuenta.** El panel dice "CxC intereses corrientes"; el enum `Cuenta.CXC_INTERES_CORRIENTE` tiene `nombreExcel` "CxC interés corriente" (lo usan otros paneles). No se cambió por ser compartido.
- **D-4.8 Servicios.** La hoja Casos (C8) dice que los servicios solo se simulan en el caso 1, pero la hoja del caso 4 sí los causa y los gira de la cuota 1 a la 13. Se reproduce tal cual.
- **D-4.9 Mes de incumplimiento = mes de retoma.** Con ambos en 4, el cliente deja de pagar al empezar el mes 4 y la moto se retoma al final del mismo mes (3 semanas sin pago). No hay un plazo mínimo de mora antes de la retoma.

## Preguntas para Diego

1. **Destino del excedente:** ¿el excedente (CxP deudor 1.185.578,46) se le devuelve al cliente en efectivo/transferencia? ¿En qué plazo? ¿Se compensa con otras obligaciones del cliente o se le cruza contra un crédito nuevo?
2. **Costos de la retoma:** ¿se descuentan del excedente los costos de recuperación (grúa, bodegaje, avalúo, trámites, honorarios de cobranza, comparendos/impuestos de la moto)? ¿Con qué orden y soporte?
3. **Avalúo vs. venta:** ¿el inventario se reconoce por el avalúo comercial o por el valor de venta posterior? Si la moto se vende por menos que el avalúo, ¿se ajusta la CxP al cliente?
4. **Mora y servicios durante el incumplimiento (P7):** ¿de verdad no se causa interés de mora ni servicios (FCC, asistencia, seguro) entre el primer incumplimiento y la retoma? ¿El capital no pasa a cartera vencida?
5. **Diferencia dación / retoma:** aparte del signo del residuo, ¿qué cambia entre los dos eventos (trámite, voluntariedad, costos, reporte a centrales, cuentas contables)? ¿Una retoma con faltante es posible (CxC FCC) y por qué la hoja fija N en 0?
6. **Interés de la semana del evento:** ¿se cobra el interés completo de la semana de la retoma o proporcional a los días?
7. **Cuenta contable:** ¿el residuo de mora/servicios/cobranza (si existiera) va a "CxC intereses corrientes" o a sus propias cuentas?
