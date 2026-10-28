# Mapa de casos del motor (fase 2)

Objetivo de la fase 2: que el motor, al recibir datos de un crédito, genere **todos los casos posibles** y los genere bien. Este mapa dice qué combinaciones se prueban hoy, cómo, y qué queda sin cubrir. Estado al **2026-10-27**.

## Qué puede pasar en un crédito (las dimensiones)

| Dimensión | Valores |
|---|---|
| Periodicidad | semanal, quincenal, mensual |
| Línea de crédito | las 4 del catálogo |
| Plazo | 1 cuota hasta el límite de la línea (24 meses) |
| Perfil de reglas | EXCEL, NEGOCIO, o **cualquier combinación** de las 20 claves de `ReglasNegocio` |
| Fechas | con o sin calendario (desembolso, primer vencimiento, prorrateo) |
| Pago inicial | con o sin cuota inicial |
| Cobranza | activa o apagada por crédito |
| Evento en cada periodo | pago al día, pago contractual con días de atraso, pago parcial, pago de un valor, sin pago (con o sin mora y servicios), abono extra (menor plazo o menor cuota), prepago total, cambio de tasa (misma cuota o misma fecha final), usura vigente, cierre por recuperación (dación o retoma) |

Un crédito es una secuencia de eventos en esos periodos, y los eventos se pueden combinar sin límite. Por eso no se enumeran a mano: se **generan al azar** y se comprueban invariantes.

## Cómo se prueba hoy

| Capa | Qué comprueba | Dónde |
|---|---|---|
| 13 casos con nombre | Las cifras de cada caso del Excel (y el caso 13) celda por celda; los 12 del Excel contra la hoja | `casos/Caso*Test`, `ComparadorExcel` |
| Huellas de comportamiento | Que ningún caso, periodicidad ni respuesta del API cambie sin que se note (SHA-256) | `caracterizacion/*NoCambia*` |
| Propiedades con perfiles | 3.000 créditos aleatorios con EXCEL o NEGOCIO: no negativos, continuidad del saldo, el pago se reparte completo, conservación de capital y de cada concepto, asientos que cuadran, y la regla de cobranza tras 2 cuotas vencidas (I8) | `PropiedadesFinancierasAuditTest#invariantesEnEscenariosAleatorios` |
| **Propiedades con reglas combinadas** | Lo mismo, sorteando cada una de las claves de reglas (prelación, fórmula de mora, base de interés y servicios, aplicación del abono, días de mora, umbrales de cobranza y recuperación, prorrateo, etc.) | `PropiedadesFinancierasAuditTest#invariantesConCombinacionesAleatoriasDeReglas` |
| Reanudación | Correr hasta un periodo y reanudar da lo mismo que correr de corrido | `ReanudacionEquivalenteTest` |
| Determinismo | Mismo resultado siempre (sin azar, reloj ni paralelismo en el núcleo) | `DeterminismoTest` |

Se puede subir la escala sin tocar código:

```bash
./mvnw test -Dtest=PropiedadesFinancierasAuditTest -Dpropiedades.escenarios=50000 -Dpropiedades.semilla=7
```

### Resultado de la corrida grande (27 oct 2026)

Unos 130.000 créditos aleatorios (50.000 con perfiles y 78.000 con reglas combinadas, varias semillas): **ningún invariante violado**. Lo único que no corre es rechazo legítimo:

- Abono mayor que el saldo (D12): se rechaza con `AbonoMayorQueElSaldoException`.
- Subida de tasa con cuota fija que ya no alcanza ni para el interés: se rechaza con un mensaje claro ("no amortiza").

## Lo que NO cubre todavía

| Hueco | Por qué | Quién lo cierra |
|---|---|---|
| Pago completo pero tarde (caso 9) no genera cobranza en NEGOCIO | La regla de Diego cuenta cuotas vencidas, y un pago completo tardío no suma | Pregunta R8-COBRANZA-PAGO-TARDIO (Diego) |
| Pagos en pesos enteros dejan 0,45 COP de residuo y llevan a mora | Falta definir tolerancia | R8-REDONDEO-PESO (Diego) |
| Pago incompleto en la fecha causa los 3 servicios | Falta definir si es "al día" | R8-PAGO-INCOMPLETO-EN-FECHA (Diego) |
| Pago mayor que lo exigible se recorta sin registrar el sobrante | Falta definir qué hacer | R8-PAGO-MAYOR-EXIGIBLE (Diego) |
| Dos instrucciones del mismo tipo en un periodo | El motor las rechaza (H5); el generador no las arma | — |
| `PrepagoTotal` con fecha real y `PagoValor` con fecha | El generador usa días, no fechas, en esos eventos | Ampliar el generador |
| Historias de pago reales de clientes | No hay datos históricos conectados | Fase 3 (backtesting) |

## Orden de trabajo siguiente

1. Ampliar el generador (fechas en prepago y pago de un valor, `desde` para más tipos de pago).
2. Cuando Diego conteste la ronda 8, implementar H2, H7 y H8 y el caso 9, y agregar un invariante por cada decisión.
3. Fase 3: alimentar el motor con el historial real de pagos y comparar contra lo que ocurrió.
