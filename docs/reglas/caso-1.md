# Caso 1 · Operación normal (y hoja Inputs)

Hoja `Caso1_Normal` del Excel `docs/fuentes/casos-operaciones-v2.xlsx`: el cliente paga las 86 cuotas a tiempo. Este documento también cataloga las reglas de la hoja `Inputs`, que usan todos los casos.

Estado: **Impl.** = implementada en el motor · **Prob.** = con prueba contra el Excel (o contra fórmula cerrada cuando el Excel no la tiene).

## Reglas de la hoja Inputs

| Id | Regla | Fórmula | Celda | Dónde | Impl. | Prob. |
|---|---|---|---|---|---|---|
| R-1.1 | Valor total de la operación | moto + alistamiento + tecnología + gastos comerciales = 5.904.400 | Inputs!B3 = B4+B5+B9+B11 | `Operacion.valorTotal` | Sí | Sí (`Caso1ValidacionesTest`, `Caso1CuotaInicialTest`) |
| R-1.2 | Alistamiento | matrícula y RUNT + SOAT + prenda = 664.400 | Inputs!B5 = SUM(B6:B8) | `Operacion.alistamiento` | Sí | Sí (indirecta, vía R-1.1) |
| R-1.3 | Semanas por mes | 52 / 12 = 4,3333… | Inputs!B14 | `ParametrosProducto.semanasPorMes` | Sí | Sí (indirecta) |
| R-1.4 | Número de cuotas semanales | ROUNDDOWN(plazo meses × 52/12) → 20 meses = 86 | Inputs!B15 | `Calendario.numeroCuotas` (aritmética entera) | Sí | Sí |
| R-1.5 | Tasa semanal efectiva | (1 + EA)^(1/52) − 1 = 0,0121432960 | Inputs!B17 | `Tasas.desdeEA` | Sí | Sí (validación 2) |
| R-1.6 | Tasa diaria efectiva | (1 + EA)^(1/365) − 1 | Inputs!B18 | `Tasas.desdeEA` | Sí | No en caso 1 (la usa el caso 9) |
| R-1.7 | Tasa mensual equivalente | (1 + EA)^(1/12) − 1. Solo referencia (hoja Casos, fila 7) | Inputs!B19 | `Tasas.desdeEA` | Sí | No (no se usa en cálculos) |
| R-1.8 | Cuota financiera semanal | PMT(tasa semanal, n.º cuotas, −monto) = 111.014,82 | Inputs!B20 | `Amortizacion.cuotaFija` | Sí | Sí (validación 3, `Caso1CuotaInicialTest` con fórmula cerrada) |
| R-1.9 | FCC por cuota con IVA | monto × 3 % / (52/12) × 1,19 = 48.643,17 | Inputs!B28 | `Servicios.porCuota` | Sí | Sí |
| R-1.10 | Asistencia por cuota con IVA | 40.000 / (52/12) × 1,19 = 10.984,62 | Inputs!B29 | `Servicios.porCuota` | Sí | Sí |
| R-1.11 | Seguro de vida por cuota (sin IVA) | monto × 0,25 % / (52/12) = 3.406,38 | Inputs!B30 | `Servicios.porCuota` | Sí | Sí |
| R-1.12 | Servicios totales por cuota | FCC + asistencia + seguro = 63.034,17 | Inputs!B31 | `Servicios.total` | Sí | Sí |
| R-1.13 | Pago total base al cliente | cuota financiera + servicios = 174.048,99 | Inputs!B32 | motor: `cuotaTotal` en `MotorCartera` | Sí | Sí (columna "Pago total cliente") |
| R-1.14 | Tolerancias | Dinero 1 COP, Tasa 0,000001, Conteo 0, Exacto 0 | Inputs!B71:B74 | `ComparadorExcel` | Sí | Sí (`Caso1ValidacionesTest` cruza la tolerancia de Validaciones con Inputs) |
| R-1.15 | Cuota inicial 10 % del valor total (D2, **no está en el Excel**) | inicial = 10 % × R-1.1 = 590.440; financiado = 5.313.960 | — (decisión D2) | `Operacion.cuotaInicial`, `ParametrosProducto.motoEstandar` | Sí | Sí (`Caso1CuotaInicialTest`) |

## Reglas de la hoja Caso1_Normal (fila 4 = periodo 1; iguales hasta la fila 89 = periodo 86)

| Id | Regla | Fórmula | Celda | Impl. | Prob. |
|---|---|---|---|---|---|
| R-1.16 | Desembolso | saldo del periodo 0 = monto financiado | D3 = Inputs!B3, N3 = D3 | Sí | Sí (validación 1) |
| R-1.17 | Mes de la cuota | ROUNDUP(periodo / (52/12)) | B4 | Sí (`Calendario.mes`, entero) | Sí (indirecta, giro a terceros) |
| R-1.18 | Etiqueta del evento | "Pago final" si periodo = 86, si no "Pago normal" | C4 | Sí (`describir`) | No (texto) |
| R-1.19 | Saldo inicial | saldo final del periodo anterior | D4 = N3 | Sí | Sí |
| R-1.20 | Interés corriente | saldo inicial × tasa semanal | F4 = D4*E4 | Sí | Sí |
| R-1.21 | Cuota financiera con tope | MIN(cuota, saldo + interés) | G4 | Sí (equivalente: el pago contractual se topa al exigible) | Sí |
| R-1.22 | Capital pagado | MIN(saldo, cuota − interés) | H4 | Sí | Sí |
| R-1.23 | Servicios por cuota | FCC, asistencia y seguro constantes | I4:L4 = Inputs!B28:B31 | Sí | Sí |
| R-1.24 | Pago total del cliente | cuota + servicios | M4 = G4+L4 | Sí | Sí |
| R-1.25 | Saldo final | MAX(0, saldo − capital) | N4 | Sí | Sí (validación 4: periodo 86 ≈ 4,3e-9) |
| R-1.26 | Giro mensual a terceros | al cerrar el mes (o en la cuota 86) se gira la suma de FCC, asistencia y seguro del mes | O4:Q4 = IF(OR(A=86, ROUNDUP((A+1)/B14) > B), SUMIF(mes)) | Sí | Sí (validación 5: FCC mes 1 = 194.572,69 en el periodo 4) |
| R-1.27 | Estado | "Amortizado" si saldo ≤ 0,005, si no "Vigente al día" | R4 | Sí (como `CANCELADO` / `VIGENTE_AL_DIA`) | Sí (`terminaEnLaCuota86ConSaldoCero`) |
| R-1.28 | Indicadores | intereses totales = SUM(F4:F89) = 3.642.874,68; saldo final = N89; pago FCC mes 1 = INDEX(O, ROUNDDOWN(B14)) | W7:W9 | Sí | Sí (W8 y W9 vía validaciones 4 y 5) |
| R-1.29 | **Asiento del recaudo de una cuota normal** | Caja D = pago total (M4); Ingresos por intereses C = interés (F4); Cartera de créditos C = capital (H4); CxP servicios a terceros C = servicios (L4) | V12:Y17 | Sí (`contabilidad/reglas/RecaudoCuota`) | Sí (`Caso1RecaudoCuotaTest`: periodo 1 y las 86 cuotas) |

### R-1.29 · Frontera de la regla contable con los casos 5 a 9

`RecaudoCuota` contabiliza **solo un recaudo normal** (`RecaudoCuota.esRecaudoNormal(m)`):

- hay pago (`pagoRecibido > 0`);
- no hay mora ni cobranza causadas ni pagadas en la semana;
- el interés y los servicios pagados son exactamente los causados en la semana (no se pagaron CxC de semanas anteriores);
- al cierre no queda nada pendiente: CxC de cobranza, mora, interés y servicios en cero, y capital vencido en cero.

Sin mora causada no puede haber capital vencido anterior, porque mora = capital vencido × tasa semanal (D6). Con eso el predicado se evalúa con un solo `Movimiento`, sin mirar la semana anterior.

En cualquier otro caso con pago (pago parcial, pago tardío, semana que paga vencidos), **el recaudo completo** de la semana le corresponde a la regla del caso 8 o del caso 9, que van por CxC (sus paneles acreditan "CxC interés corriente" y debitan Caja por todo el pago recibido). Esas reglas deben aplicar exactamente cuando `!RecaudoCuota.esRecaudoNormal(m) && m.pagoRecibido() > 0`. Así ninguna semana se contabiliza dos veces ni se queda sin contabilizar. `Caso1InvariantesTest` lo vigila: con pagos contractuales, `Contabilizador.estandar()` debe generar exactamente un asiento por cuota.

Abonos extraordinarios y prepagos (casos 5, 6 y 7) van en `abonoExtra` y `prepagoCapital`, no en `pagoRecibido`. `RecaudoCuota` contabiliza la cuota ordinaria de esa semana y la regla de abonos solo la parte extraordinaria, igual que los paneles de esas hojas (Caso5 T13:W15 y Caso6 U11:X13 solo muestran el abono). Los cierres por dación o retoma (casos 3 y 4) ocurren en semanas sin pago, así que no se cruzan con esta regla.

## Invariantes (HT-02) · `Caso1InvariantesTest`

300 créditos aleatorios (semilla fija 20261008, más las 4 esquinas de los rangos): monto 1–40 millones, plazo 6–36 meses, EA 20 %–120 %, pagos contractuales a tiempo, sin cuota inicial. Para cada uno:

1. ningún saldo (saldo inicial y final, capital vigente y vencido, todas las CxC) es negativo;
2. el crédito termina en la cuota pactada (`Calendario.numeroCuotas`) con estado `CANCELADO` y saldo ≤ 1 COP, y no antes;
3. en cada movimiento, pago recibido = cobranza + mora + interés + servicios + capital (±1 COP);
4. cada cuota genera exactamente un asiento cuadrado, la Caja contabilizada es igual a lo recaudado y la cartera abonada es igual al monto (±1 COP).

Para plazos de más de 27 meses el horizonte del motor (`plazoLimiteSemanas`, 120 por defecto) no alcanza la última cuota. La prueba lo sube a n.º de cuotas + 10. **Ojo para producción:** el horizonte por defecto debe derivarse del plazo, no ser fijo.

## Producto con cuota inicial del 10 % (D2) · `Caso1CuotaInicialTest`

Con `ParametrosProducto.motoEstandar()` y `Operacion.ejemploExcel()`:

| Concepto | Valor |
|---|---|
| Valor total de la operación | 5.904.400 |
| Cuota inicial (10 %) | 590.440 |
| Monto financiado (desembolso) | 5.313.960 |
| Cuotas | 86 |
| Cuota financiera | ≈ 99.913,34 (PMT con fórmula cerrada en la prueba; es el 90 % de 111.014,82) |
| FCC por cuota | ≈ 43.778,86 (90 % del Excel) |
| Asistencia por cuota | 10.984,62 (igual: es un valor fijo) |
| Seguro por cuota | ≈ 3.065,75 (90 % del Excel) |
| LTV sobre la moto | 104,2 % (en el Excel, 115,8 %) |

Implicaciones (P11):

- Hoy el motor calcula FCC y seguro sobre el monto financiado (90 %). Si negocio quiere que el FCC y el seguro de vida cubran el valor total de la operación, hay que agregar una base de cálculo de servicios distinta del desembolso. Con la base al 90 % la financiera recauda por FCC 10 % menos, unos 418.000 COP menos en las 86 cuotas del ejemplo.
- El seguro de vida normalmente ampara el saldo de la deuda. Sobre el 90 % queda alineado con lo que se debe; sobre el 100 % estaría sobreasegurado.
- La inicial no entra a cartera: no hay asiento para ella en el Excel. Hay que definir si se recibe antes del desembolso (Caja contra anticipo del cliente) y quién la recauda (¿el concesionario?).
- Aun con la inicial, el LTV sobre la moto es mayor que 100 %, porque se financian alistamiento, GPS y gastos comerciales.

## Discrepancias y ambigüedades del Excel

1. **"Monto financiado" (Inputs!B3) es el valor total de la operación.** En el Excel no hay cuota inicial y el LTV sale en 115,8 % (G15). Con D2 el monto financiado real es el 90 %. El rótulo de B3 confunde.
2. **El panel contable del caso 1 no causa el interés.** Acredita "Ingresos por intereses" directo al recaudar. Los casos 8 y 9 causan el interés en "CxC interés corriente" y el pago cancela la CxC. Son dos modelos contables distintos (base de caja frente a causación). Se reprodujo cada panel tal cual.
3. **Servicios cobrados por semana, pero pactados por mes.** FCC y seguro son "% mensual" y asistencia "por mes", pero se cobran como valor mensual / 4,333 por cuota. Por eso los meses con 5 cuotas (mes 3, por ejemplo: giro de 243.215,86) giran más que los de 4 (194.572,69). Además, 86 cuotas = 19,85 meses, así que en 20 meses de plazo se cobran 0,15 meses menos de servicios (≈ 32.400 COP de FCC en el ejemplo).
4. **El estado final se llama distinto.** El caso 1 dice "Amortizado" (igual que los casos 2 y 7) y los demás casos dicen "Cancelado". El motor usa un solo estado, `CANCELADO`.
5. **El mes se calcula con punto flotante.** ROUNDUP(periodo / 4,333…) da el mes correcto en Excel porque Excel trunca a 15 dígitos (13/4,333… = 3,0000000000000004). Una implementación ingenua en `double` pondría la cuota 13 en el mes 4. El motor usa aritmética entera (`Calendario.mes`).
6. **Residuo final.** La cuota 86 deja un saldo de 4,29e-9 (N89), por la tasa calculada en doble precisión. Está dentro de la tolerancia, y el estado usa el umbral 0,005. Falta decidir el redondeo (P2).
7. **El tope de la cuota no está en ningún texto.** G4 = MIN(cuota, saldo + interés) evita cobrar de más en la última cuota, pero la hoja Casos no lo menciona.
8. **La hoja Casos (fila 8) dice que los servicios "solo se simulan en el caso 1".** Sin embargo, las hojas de los casos 3, 4, 8 y 9 sí tienen columnas de servicios, y las de los casos 8 y 9 los aplican en la prelación.
9. **El panel contable lleva un solo renglón para servicios.** "CxP servicios a terceros" agrupa FCC, asistencia y seguro, que se giran a terceros distintos (O:Q). Para la contabilidad real probablemente se necesitan tres subcuentas, y el IVA del FCC y de la asistencia por separado.

## Preguntas para Diego

Las preguntas nuevas llevan id local (P-C1.x) para no chocar con los otros casos. Reciben número global P al consolidarlas en `docs/decisiones.md`.

- **P11** ¿FCC y seguro de vida se calculan sobre el monto financiado (90 %) o sobre el valor total de la operación? ¿La asistencia sigue siendo fija?
- **P-C1.a** (nueva) ¿Los servicios se cobran por cuota semanal (valor mensual / 4,333) o por mes calendario? ¿Se acepta que en 86 cuotas se cobren 19,85 meses de servicios y que los meses con 5 cuotas giren más?
- **P-C1.b** (nueva) ¿El interés corriente se causa semanalmente en una CxC (como en los casos 8 y 9) o se reconoce como ingreso al recaudar (como en el panel del caso 1)? Afecta los asientos de todas las semanas normales.
- **P-C1.c** (nueva) ¿"Amortizado" y "Cancelado" son el mismo estado o hay diferencia de negocio (por ejemplo, amortizado = pagó en el plazo, cancelado = pagó antes o con evento)?
- **P-C1.d** (nueva) Para la contabilidad real, ¿hay que separar "CxP servicios a terceros" por tercero (fondo de garantías, asistencia, aseguradora) y el IVA generado?
- **P-C1.e** (nueva) ¿Cómo se contabiliza y quién recauda la cuota inicial? ¿Se exige antes del desembolso?
- **P-C1.f** (nueva) Plazos permitidos del producto: ¿hay mínimo y máximo (las invariantes prueban de 6 a 36 meses y EA de 20 % a 120 %)? ¿Cuál es el horizonte máximo de proyección con saldo pendiente?
