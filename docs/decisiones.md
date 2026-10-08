# Decisiones del motor

Registro de lo decidido. Lo marcado **provisional** se tomó para avanzar y debe confirmarse con negocio (Diego González / Heider Galvis).

| # | Fecha | Decisión | Estado |
|---|---|---|---|
| D1 | 2026-10-08 | Arquitectura: monolito modular en Java 21 + Spring Boot 4 + Spring Modulith. Ver `adr/0001-monolito-modular.md`. | Tomada |
| D2 | 2026-10-08 | Cuota inicial = **10 % del valor total de la operación** (moto + alistamiento + GPS + gastos comerciales); se financia el 90 %. Con el ejemplo del Excel: inicial 590.440 y se financian 5.313.960. | Tomada (Juan Esteban) |
| D3 | 2026-10-08 | El Excel no tiene cuota inicial: las réplicas de los casos usan `ParametrosProducto.replicaExcel()` (inicial 0) para poder comparar al centavo. | Tomada |
| D4 | 2026-10-08 | Sin redondeo interno (BigDecimal DECIMAL128), igual que el Excel. El redondeo a pesos se aplicará en un solo punto cuando se defina P2. | Provisional (P2) |
| D5 | 2026-10-08 | Prelación de abonos según las fórmulas de los casos 8 y 9: cobranza → mora → interés corriente → servicios → capital. El texto de la hoja Casos omite los servicios. | Provisional (P1) |
| D6 | 2026-10-08 | Interés de mora = capital vencido × tasa semanal + capital contractual × ((1 + tasa diaria)^días de atraso − 1). Con días = 0 se reduce a la fórmula del caso 8. | Provisional (P3) |
| D7 | 2026-10-08 | Servicios (FCC y seguro) sobre el monto financiado inicial, constantes por cuota, solo hasta la cuota pactada final (86). | Provisional (P6) |
| D8 | 2026-10-08 | En los casos 3 y 4 el Excel no causa mora, vencido ni servicios mientras el cliente no paga. Se reproduce con `SinPago(false)`; el comportamiento general es `SinPago(true)`. | Provisional (P7) |
| D9 | 2026-10-08 | Tasas efectivas: semanal = (1+EA)^(1/52) − 1, diaria = (1+EA)^(1/365) − 1, en doble precisión como Excel. | Tomada |

## Preguntas abiertas (P)

- **P1** Prelación oficial: ¿servicios antes del capital?
- **P2** Redondeo: ¿centavos o pesos? ¿dónde se absorbe el residuo?
- **P3** Fórmula única de mora.
- **P5** ¿La EA de 87,32 % cumple la usura de la modalidad?
- **P6** ¿FCC y seguro sobre monto inicial o saldo? ¿Se cobran después de un prepago o abono?
- **P7** ¿Mora y servicios en dación/retoma?
- **P10** Abono tardío e incompleto a la vez.
- **P11** (nueva) ¿El 10 % de inicial cambia la base de FCC y seguro (monto financiado 90 %)? El motor hoy los calcula sobre el monto financiado.
