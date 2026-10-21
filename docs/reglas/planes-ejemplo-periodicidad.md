# Planes de ejemplo por periodicidad (para revisión de Diego)

ADR 0002, paso 6. No existe un Excel de referencia para quincenal ni para mensual, así que estos planes son lo que el motor calcula con las reglas que Diego dio el 2026-10-20 (PPER). Sirven para que Diego confirme que las cifras son las que negocio espera antes de abrir quincenal o mensual. El semanal va como referencia: es la réplica del Excel y coincide con él al peso.

**Datos (hoja Inputs):** línea CPPU, EA 87,62 %, monto financiado 5.904.400 (moto 5.100.000 + alistamiento 664.400 + GPS 80.000 + gastos comerciales 60.000, sin pago inicial), plazo 24 meses, cliente que paga siempre a tiempo.

**Reglas aplicadas:**

- Tasa del periodo = (1 + EA)^(1/periodos por año) − 1, con 52, 24 y 12 periodos por año (PPER). La quincenal usa **24**. Diego escribió "104 para quincena", y lo tomamos como error: 104 periodos al año sería un pago cada 3,5 días. **Hay que confirmarlo.**
- Número de cuotas = ROUNDDOWN(meses × periodos por año / 12) (PPER-2 y PPER-3): 104, 48 y 24.
- Cuota financiera = PAGO(tasa del periodo; cuotas; −monto), igual que Excel. `PeriodicidadInvariantesTest` la compara con la fórmula cerrada.
- Servicios por cuota = valor mensual × 12 / periodos por año: en mensual cada cuota lleva el valor mensual completo y en quincenal la mitad. Fondo de garantías 3 % del monto + IVA, asistencia 40.000 + IVA y seguro 0,25 % del monto, sin IVA.
- Las cifras van redondeadas al peso solo para mostrarlas; el motor no redondea.

## Resumen

| | Semanal (Excel) | Quincenal | Mensual |
|---|---:|---:|---:|
| Periodos por año | 52 | 24 | 12 |
| Tasa del periodo | 1,2174444 % | 2,6565419 % | 5,3836560 % |
| Número de cuotas | 104 | 48 | 24 |
| Cuota financiera | 100.406 | 219.093 | 444.006 |
| Servicios por cuota | 63.034 | 136.575 | 273.148 |
| Cuota total | 163.440 | 355.667 | 717.154 |
| Total intereses | 4.537.853 | 4.612.061 | 4.751.748 |
| Total servicios | 6.555.554 | 6.555.554 | 6.555.554 |
| Total pagado por el cliente | 16.997.806 | 17.072.015 | 17.211.702 |

Las tres periodicidades suman los mismos servicios: 24 meses × valor mensual. El interés total crece cuando los pagos son menos frecuentes porque el capital se amortiza más despacio. La tasa efectiva anual es la misma en los tres casos.

## Quincenal: 48 cuotas

| Cuota | Mes | Saldo inicial | Interés | Capital | Cuota financiera | Fondo de garantías | Asistencia | Seguro | Cuota total | Saldo final |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 5.904.400 | 156.853 | 62.240 | 219.093 | 105.394 | 23.800 | 7.381 | 355.667 | 5.842.160 |
| 2 | 1 | 5.842.160 | 155.199 | 63.894 | 219.093 | 105.394 | 23.800 | 7.381 | 355.667 | 5.778.266 |
| 3 | 2 | 5.778.266 | 153.502 | 65.591 | 219.093 | 105.394 | 23.800 | 7.381 | 355.667 | 5.712.676 |
| 4 | 2 | 5.712.676 | 151.760 | 67.333 | 219.093 | 105.394 | 23.800 | 7.381 | 355.667 | 5.645.342 |
| 5 | 3 | 5.645.342 | 149.971 | 69.122 | 219.093 | 105.394 | 23.800 | 7.381 | 355.667 | 5.576.220 |
| 6 | 3 | 5.576.220 | 148.135 | 70.958 | 219.093 | 105.394 | 23.800 | 7.381 | 355.667 | 5.505.262 |
| … | | | | | | | | | | |
| 47 | 24 | 421.324 | 11.193 | 207.900 | 219.093 | 105.394 | 23.800 | 7.381 | 355.667 | 213.423 |
| 48 | 24 | 213.423 | 5.670 | 213.423 | 219.093 | 105.394 | 23.800 | 7.381 | 355.667 | 0 |
| **Total (48 cuotas)** | | | **4.612.061** | **5.904.400** | **10.516.461** | **5.058.890** | **1.142.400** | **354.264** | **17.072.015** | |

Las cuotas vencen el 5 y el 20 de cada mes (PPER-1). El giro a terceros de los servicios se hace al cerrar cada mes, es decir, cada dos cuotas.

## Mensual: 24 cuotas

| Cuota | Mes | Saldo inicial | Interés | Capital | Cuota financiera | Fondo de garantías | Asistencia | Seguro | Cuota total | Saldo final |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 5.904.400 | 317.873 | 126.134 | 444.006 | 210.787 | 47.600 | 14.761 | 717.154 | 5.778.266 |
| 2 | 2 | 5.778.266 | 311.082 | 132.924 | 444.006 | 210.787 | 47.600 | 14.761 | 717.154 | 5.645.342 |
| 3 | 3 | 5.645.342 | 303.926 | 140.080 | 444.006 | 210.787 | 47.600 | 14.761 | 717.154 | 5.505.262 |
| 4 | 4 | 5.505.262 | 296.384 | 147.622 | 444.006 | 210.787 | 47.600 | 14.761 | 717.154 | 5.357.640 |
| 5 | 5 | 5.357.640 | 288.437 | 155.569 | 444.006 | 210.787 | 47.600 | 14.761 | 717.154 | 5.202.071 |
| 6 | 6 | 5.202.071 | 280.062 | 163.945 | 444.006 | 210.787 | 47.600 | 14.761 | 717.154 | 5.038.126 |
| … | | | | | | | | | | |
| 23 | 23 | 821.123 | 44.206 | 399.800 | 444.006 | 210.787 | 47.600 | 14.761 | 717.154 | 421.324 |
| 24 | 24 | 421.324 | 22.683 | 421.324 | 444.006 | 210.787 | 47.600 | 14.761 | 717.154 | 0 |
| **Total (24 cuotas)** | | | **4.751.748** | **5.904.400** | **10.656.148** | **5.058.890** | **1.142.400** | **354.264** | **17.211.702** | |

Cada cuota cierra un mes, así que el giro a terceros se hace en todas. El día de pago (el 1 o el 5) no está decidido; el plan no depende de él porque el motor causa por periodo y no por fechas.

## Lo que Diego debe revisar

1. ¿La quincenal tiene **24** periodos por año (2 por mes)? Diego escribió 104.
2. ¿La cuota mensual de 444.006 más 273.148 de servicios (717.154 en total) es la que espera negocio para esta operación?
3. En mensual, ¿cada cuota lleva el valor mensual completo de los servicios (210.787 + 47.600 + 14.761)? ¿Y en quincenal, la mitad?
4. El día de pago de la mensual: ¿el 1 o el 5? (sigue abierto desde PPER-1.)
5. La mora de un mes sin pago se va a contar por los días reales del mes (PPER-5). El calendario ya da los días de cada mes (`CalendarioPagos.diasDelPeriodo`), pero el motor todavía causa la mora del periodo con la tasa periódica, que equivale a unos 30,4 días. Ese cambio es el paso 8 del ADR.

Los planes se regeneran desde el motor: `ParametrosProducto.deMeses(LineaCredito.CPPU, periodicidad, 24)` con `Operacion.ejemploExcel()`. `PeriodicidadInvariantesTest` verifica estas cifras: saldo final 0, capital pagado igual al monto, interés igual a saldo × tasa y cuota igual a PAGO().
