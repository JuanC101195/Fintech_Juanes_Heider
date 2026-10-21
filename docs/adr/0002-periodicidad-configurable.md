# ADR 0002 · Periodicidad configurable del crédito

**Estado:** aceptada y en curso · 2026-10-20. Diego respondió las preguntas PPER (ronda 3), y los pasos 0 a 6 del plan y el `CalendarioPagos` del paso 7 están hechos en la rama `feat/periodicidad`. Ver "Avance" al final.

## Contexto

El motor nació para reproducir el Excel "Casos Operaciones V2", que trabaja con cuotas **semanales**: tasa semanal = (1 + EA)^(1/52) − 1, 104 cuotas (24 meses × 52 / 12), servicios mensuales ÷ 4,333, mora del caso 9 con tasa diaria y plazos límite expresados en semanas. El 2026-10-20 Diego (dueño de producto) aclaró que un crédito puede ser **semanal, quincenal o mensual** según la negociación, y que todo (servicios, causación, cambios de tasa) se cobra "acorde a la configuración del crédito, respetando la periodicidad pactada".

La hoja Inputs ya anticipa parte de esto:

- Catálogo ⑤ (A105:D108): `Diario 365 · diaria · cuotas diarias`, `Semanal 52 · semanal · cuotas semanales`, `Mensual 12 · mensual · cuotas mensuales`. **No hay fila quincenal.**
- B13 (lista) → B17 = períodos por año (INDEX del catálogo), B18 = B17/12 (períodos por mes).
- B15 = ROUNDDOWN(plazo máximo de la línea × B18): máximo de cuotas. B19 = cuotas pactadas (104); B16 = B19/B18 = plazo equivalente en meses.
- B21 = (1 + EA)^(1/B17) − 1 (tasa periódica); B22 = (1 + EA)^(1/365) − 1 (diaria); B23 = (1 + EA)^(1/12) − 1 (mensual); B24 = PMT(B21, B19, −B3).
- B32:B34: servicios por cuota = valor mensual / B18 (con IVA donde aplique).
- Eventos por mes → periodo: ROUNDDOWN(mes × B18) o ROUNDDOWN((mes − 1) × B18) + 1 (C43, C47, C55…); mes de una fila = ROUNDUP(periodo / B18) y el giro a terceros se hace al cambiar de mes.
- Plazos límite de los casos 8 y 9 en **semanas** (C65 = 100, C72 = 120) convertidos con ROUNDDOWN(semanas × B17 / 52) (C66, C73).
- Caso 9: mora = vencido × tasa periódica + capital de la cuota × ((1 + B22)^días − 1), con días de atraso como dato (C68 = 14); cobranza = días × 3.000 por pago (C70).

Es decir, el Excel ya es paramétrico en B17 salvo en tres puntos: los plazos límite en semanas, la falta de quincenal y que nunca se validó con otra periodicidad. El código siguió el mismo camino (ya existen `Periodicidad` y `ParametrosProducto.periodosPorAnio`), pero quedan supuestos semanales repartidos.

### Inventario de dependencias de "semana" (rama `main` del 2026-10-20)

**Ya paramétrico (funciona con cualquier `periodosPorAnio`, solo validado en 52):**

| Archivo:línea | Qué hace | Observación |
|---|---|---|
| `nucleo/Tasas.java:14` | tasa periódica (1 + EA)^(1/periodosPorAnio) − 1 | Correcto para 24 y 12. |
| `nucleo/Tasas.java:15` | tasa diaria con 365 fijo | Correcto: es base anual, no del periodo. |
| `nucleo/Tasas.java:16` | tasa mensual con `mesesPorAnio` | Hoy no la usa el motor. |
| `nucleo/ParametrosProducto.java:44` | máximo de cuotas = plazo máximo × ppa / 12 (división entera = ROUNDDOWN) | Igual a Inputs B15. |
| `nucleo/ParametrosProducto.java:69-70` | `periodosPorMes()` = ppa / 12 (4,333…) | Quincenal 2, mensual 1. |
| `nucleo/Servicios.java:17-22` | servicios mensuales ÷ `periodosPorMes` (variable `semanasMes`) | Fórmula correcta; nombre semanal. |
| `nucleo/Calendario.java:15-17` | mes = ROUNDUP(periodo × 12 / ppa) | Mensual: mes = periodo; quincenal: ⌈p/2⌉. |
| `nucleo/Calendario.java:24-25` | primer periodo del mes = ROUNDDOWN((mes − 1) × ppa / 12) + 1 | Cambio de tasa del mes 11 → cuota 44 (semanal). |
| `nucleo/Calendario.java:29-30` | periodo de cierre del mes = ROUNDDOWN(mes × ppa / 12) | Eventos de los casos 3 a 12. |
| `nucleo/motor/MotorCartera.java:80, 179, 309` | PMT con la tasa periódica y el número de periodos | Agnóstico. |
| `nucleo/motor/MotorCartera.java:176` | cambio de tasa: `Tasas.desdeEA(nueva, ppa, 12)` | Agnóstico. |
| `nucleo/motor/MotorCartera.java:187` | NPER del caso 10 en periodos | Agnóstico. |
| `nucleo/motor/MotorCartera.java:224` | mora = capital vencido × tasa **periódica** | Implica que un periodo sin pago dura 365/ppa días (7,02 en semanal), no los días de calendario. |
| `nucleo/motor/MotorCartera.java:226` | factor de días (1 + diaria)^días − 1 (caso 9) | Agnóstico; días los da la instrucción. |
| `nucleo/motor/MotorCartera.java:233` | cobranza = días × `cobranzaDiaria` | Agnóstico. |
| `nucleo/motor/MotorCartera.java:374` | giro a terceros al cambiar de mes (`Calendario.mes`) | En mensual gira cada periodo. |

**Supuestos semanales que hay que cambiar:**

| Archivo:línea | Supuesto |
|---|---|
| `nucleo/Periodicidad.java:4-7` | Solo `DIARIA(365)`, `SEMANAL(52)`, `MENSUAL(12)`: falta `QUINCENAL(24)`; no hay días por periodo, adjetivo ni unidad de cuota (Inputs C105:D108). Javadoc línea 3: "paga los martes, semanal (P-06)". |
| `nucleo/ParametrosProducto.java:24-25` | Guarda `int periodosPorAnio` y `int mesesPorAnio` sueltos en vez de la `Periodicidad`: se pierde qué se pactó y cualquier entero pasa (p. ej. 50). |
| `nucleo/ParametrosProducto.java:47` | Mensaje con `name().toLowerCase() + "es"`: produce "diariaes" y no sirve para quincenal. |
| `nucleo/ParametrosProducto.java:50` | `plazoLimitePeriodos = Math.max(120, numeroCuotas)`: 120 son **semanas** (Inputs C72). En mensual serían 120 meses. |
| `nucleo/ParametrosProducto.java:59` | `conNumeroCuotas` sube el límite a las cuotas sin margen de periodos adicionales. |
| `nucleo/Calendario.java:9, 14, 21, 28` | Javadocs "cuota semanal", "52/12". |
| `nucleo/Servicios.java:6, 17` | "por cuota semanal", variable `semanasMes`. |
| `nucleo/Calc.java:74` | Javadoc del ejemplo (1 + EA)^(1/52). Solo comentario. |
| `nucleo/motor/MotorCartera.java:88, 103` | Método `Corrida.semana(periodo, …)`; comentario línea 108 "toda semana sin pago". |
| `nucleo/motor/MotorCartera.java:224` | Mora de un periodo sin pago con la tasa periódica: en mensual la mora del mes es la misma sin importar si el mes tuvo 28 o 31 días (ver P-PER-5). |
| `nucleo/motor/Instruccion.java:6, 33, 44, 49` | Javadocs "semana". `PagoContractual/PagoParcial/PagoValor(diasAtraso)` (líneas 12, 17, 21): los días de atraso se digitan; no hay fechas. |
| `nucleo/motor/Escenario.java:12, 20, 44` | Javadocs "semana a semana". |
| `nucleo/motor/AbonoMayorQueElSaldoException.java:13` | Mensaje "Semana N: …". |
| `nucleo/motor/ReglasNegocio.java:17, 45, 47, 61` | Javadocs "tasa semanal", "semanas sin pago". |
| `nucleo/motor/Movimiento.java:17, 55` | Comentarios "de la semana". El registro ya usa `periodo` y `mes`. |
| `simulaciones/EscenariosExcel.java:57, 63` | `conPlazoLimite(100)` y `conPlazoLimite(120)`: semanas como literales; el Excel los convierte con ROUNDDOWN(semanas × B17 / 52) (C66, C73). |
| `simulaciones/EscenariosExcel.java:79` | Caso 11: `inferior + 4` periodos de espera (C80): en mensual serían 4 meses. |
| `simulaciones/EscenariosExcel.java:87` | Comentario "ROUNDDOWN(14 × 52/12)". |
| `simulaciones/GeneradorEscenarios.java:57-81` | Preguntas del cuestionario con cuotas fijas (43, 44, 31, 34, 61, **86**, 87): solo valen en semanal y el 86 es del Excel viejo (hoy 104). |
| `simulaciones/GeneradorEscenarios.java:110, 111, 121` | Claves JSON `cuotaSemanal`, `semanaFinal`, `semanasEnMora` (y `semanasClave`, línea 43): contrato con `cuestionario/`. |
| `contabilidad/reglas/*.java` | Solo javadocs ("la semana del pago inferior", "tasa semanal"); la lógica usa el `Movimiento`. |
| — | **No existe calendario de vencimientos.** P-06 (martes, sin festivos) está anotado en `docs/respuestas-diego-2026-10-16.md:33` pero no hay código con fechas: el motor trabaja por número de periodo. |

**Pruebas que dependen de 52:** `Caso1CuotaInicialTest.java:34-37` (`1.0/52`, 104, `52.0/12.0`), `Caso2CambioTasaTest.java:138` y `Caso2UsuraTest.java:72` (`Tasas.desdeEA(…, 52, 12)`), `Caso1InvariantesTest.java:60-61` (cuotas = meses × 52/12 y el comentario de 120 semanas). Las demás afirman 104, 43, 44 o 103 a partir de `replicaExcel()`; siguen siendo válidas mientras la réplica sea semanal.

## Decisión propuesta

1. **`Periodicidad` como dato del producto**, no un entero. El enum gana `QUINCENAL` y los atributos del catálogo de Inputs:

   ```java
   public enum Periodicidad {
       DIARIA(365, 1, "diaria", "cuotas diarias"),
       SEMANAL(52, 7, "semanal", "cuotas semanales"),
       QUINCENAL(24, 15, "quincenal", "cuotas quincenales"),
       MENSUAL(12, 30, "mensual", "cuotas mensuales");

       int periodosPorAnio();
       int diasNominales();            // solo para calendario, tolerancias y textos; no entra en la tasa
       BigDecimal periodosPorMes();    // ppa / 12
       int cuotasEnMeses(int meses);   // ROUNDDOWN(meses × ppa / 12), igual que Inputs B15
       int periodosDesdeSemanas(int s);// ROUNDDOWN(s × ppa / 52), igual que Inputs C66 y C73
   }
   ```

   `ParametrosProducto` reemplaza `int periodosPorAnio, int mesesPorAnio` por `Periodicidad periodicidad` y conserva `periodosPorAnio()` y `mesesPorAnio()` como métodos derivados, para que el resto del código compile sin cambios en el primer paso.

2. **Tasa del periodo** = (1 + EA)^(1/ppa) − 1, efectiva (D9 se generaliza). La tasa diaria sigue con base 365 y la mensual con 12. Un cambio de tasa (casos 2 y 10, usura) convierte la EA nueva con la periodicidad del crédito.

3. **Número de cuotas** = ROUNDDOWN(meses × ppa / 12), validado contra el plazo máximo de la línea, mientras Diego no diga lo contrario (P-PER-2 y P-PER-3). Semanal 24 meses = 104, quincenal = 48, mensual = 24.

4. **Servicios** (fondo de garantías, asistencia, seguro): valor mensual × 12 / ppa por cuota, como hoy (Inputs B32:B34). Mensual cobra el valor mensual completo; quincenal la mitad. El giro a terceros sigue siendo por mes calendario del crédito (`Calendario.mes`).

5. **Mora y prepago con tasa diaria**: se conserva la fórmula del Excel (vencido × tasa periódica + capital de la cuota × ((1 + diaria)^días − 1)). Como (1 + diaria)^(365/ppa) = 1 + tasa periódica, la mora de un periodo completo equivale a 365/ppa días en cualquier periodicidad. Si Diego pide días reales de calendario (P-PER-5), se agrega una variante en `ReglasNegocio` (`DiasMora.PERIODO_NOMINAL` = Excel, `DIAS_CALENDARIO`), sin cambiar la réplica. El prepago a mitad de periodo (hoy no existe: se aplica después de la cuota) usaría interés = saldo × ((1 + diaria)^días − 1).

6. **Plazo límite** en el producto como **periodos adicionales** sobre la última cuota o en meses, nunca en semanas sueltas. Para la réplica del Excel, `periodicidad.periodosDesdeSemanas(100 | 120)`; el valor por defecto lo fija la respuesta a P-PER-6.

7. **Calendario de vencimientos** separado del motor: `nucleo/CalendarioPagos` (función pura) calcula la fecha de vencimiento del periodo k a partir de la fecha de desembolso, la periodicidad y la regla de día (semanal: martes, P-06; quincenal y mensual según P-PER-1). El motor sigue indexado por periodo; el módulo `abonos` calcula los días de atraso con esas fechas y se los pasa a las instrucciones, que ya los reciben como entero.

8. **Nombres neutros**: "periodo" en código, mensajes y JSON; el adjetivo ("semanal", "quincenal") sale de la `Periodicidad` cuando se muestra al usuario.

## Alternativas consideradas

- **Dejar el entero `periodosPorAnio`.** Ya funciona para 12 y 24, pero acepta valores sin sentido, no sabe los días del periodo ni el texto, y obliga a repetir `52`/`12` en pruebas y simulaciones. Se descarta.
- **Motor por días (devengo diario y cuotas como fechas).** Sería exacto con meses de 28 a 31 días y facilitaría prepagos a mitad de periodo, pero rompe la réplica del Excel (que causa por periodo con tasa periódica) y obliga a reescribir los 12 casos. Se deja como variante futura de la mora (punto 5) si negocio la pide.
- **Tasa periódica nominal (EA nominal / ppa), como Fineract.** Contradice D9 y el Excel. Se descarta.
- **Motores distintos por periodicidad.** Duplicaría la prelación, la causación y los cierres. Se descarta: la diferencia cabe en parámetros.
- **Calendario dentro de `MotorCartera`.** Mezclaría fechas con el cálculo puro y haría que cada prueba del Excel necesite fechas. Se prefiere el calendario aparte (punto 7).

## Consecuencias

- La réplica semanal no cambia: `replicaExcel()` sigue siendo CPPU semanal 104 y todas las cifras salen de las mismas fórmulas.
- Quincenal y mensual se validan **sin un Excel nuevo** (decisión 2026-10-20: la lógica se extrapola): invariantes (saldo final 0, suma de capital = monto, interés = saldo × tasa del periodo), la cuota contra PAGO() de Excel / un simulador estándar, y un plan quincenal y uno mensual de ejemplo que Diego revisa.
- `ParametrosProducto` cambia de firma: cambian las pruebas que construyen el registro a mano o llaman `Tasas.desdeEA(…, 52, 12)`.
- Las claves JSON del cuestionario cambian de nombre; hay que coordinarlo con `cuestionario/` (o mantener las viejas como alias un tiempo).
- Cada crédito debe guardar la periodicidad pactada junto con la versión del producto (HT-04); ya no basta la versión.
- La mora de un periodo sin pago depende de la periodicidad (más días en mensual), lo que puede sorprender a negocio si espera días reales.

## Plan de migración

Cada paso deja verdes las 17 clases de prueba de `casos/` y `contabilidad/` contra el Excel semanal; ningún paso cambia una cifra de la réplica.

| Paso | Cambio | Pruebas nuevas | Esfuerzo | Bloqueado por |
|---|---|---|---|---|
| 0 | Prueba de caracterización: los 12 casos con `replicaExcel()` producen el mismo cronograma que hoy (hash o comparación fila a fila guardada). | `ReplicaSemanalNoCambiaTest` | 0,5 d | — |
| 1 | `Periodicidad`: agrega `QUINCENAL(24)`, `diasNominales`, adjetivo, unidad, `periodosPorMes()`, `cuotasEnMeses()`, `periodosDesdeSemanas()`; corrige el plural de `ParametrosProducto.java:47`. Sin cambios de comportamiento. | `PeriodicidadTest` (52 → 104 cuotas en 24 meses, 24 → 48, 12 → 24; 100 semanas → 100/46/23) | 0,5 d | — |
| 2 | `ParametrosProducto` guarda `Periodicidad`; `periodosPorAnio()` y `mesesPorAnio()` derivados; `Tasas.de(Periodicidad)`. Ajustar `Caso1CuotaInicialTest`, `Caso2CambioTasaTest:138`, `Caso2UsuraTest:72`, `Caso1InvariantesTest:60`. | las existentes | 1 d | — |
| 3 | Plazo límite: `EscenariosExcel.java:57, 63` con `periodosDesdeSemanas`; el defecto `Math.max(120, …)` pasa a "cuotas + margen" o meses. En semanal da 100 y 120 como hoy. | casos 8 y 9 sin cambios | 0,5 d | P-PER-6 (solo el defecto) |
| 4 | Nombres neutros: `Corrida.semana` → `periodo`, javadocs, mensaje de `AbonoMayorQueElSaldoException`; claves JSON de `GeneradorEscenarios` con alias y cuotas de las preguntas calculadas con `Calendario` (corrige el 86 del Excel viejo). | `GeneradorEscenariosTest` de las claves | 0,5 d + coordinación con `cuestionario/` | — |
| 5 | Invariantes por periodicidad: `Caso1InvariantesTest` parametrizado en SEMANAL, QUINCENAL y MENSUAL; casos 2 a 12 en mensual con controles de saldo cero, prelación, servicios del mes = valor mensual y giro mensual. | 3 × 300 créditos + 11 escenarios mensuales | 1,5 d | — |
| 6 | Validación sin Excel nuevo: invariantes, cuota contra PAGO() de Excel y planes de ejemplo quincenal y mensual para que Diego los revise. | `PeriodicidadInvariantesTest` | 1 d | Revisión de Diego |
| 7 | `CalendarioPagos` (fechas de vencimiento por periodicidad; martes para semanal) y cálculo de días de atraso en `abonos`. | fechas de fin de mes, febrero, 29/30/31 | 1,5 d | P-PER-1, P-PER-4 |
| 8 | Variante `DiasMora.DIAS_CALENDARIO` y prepago a mitad de periodo con tasa diaria, detrás de `ReglasNegocio` (EXCEL conserva la actual). | casos 5 y 9 con ambas variantes | 1,5 d | P-PER-5, P-PER-7 |

Total: unos 8 a 9 días; 4 días (pasos 0 a 5) se pueden hacer ya sin respuestas de Diego.

## Riesgos

- **Sin referencia para quincenal y mensual.** Lo que hoy se valida al centavo es solo semanal; en otras periodicidades el motor puede ser coherente y aun así no ser lo que negocio espera. Mitigación: paso 6 antes de abrir quincenal o mensual en producción.
- **Diferencias con el Excel por enteros.** Las conversiones mes → periodo son ROUNDDOWN/ROUNDUP con división entera; con 24 o 12 son exactas, pero con 52 los meses tienen 4 o 5 cuotas y el giro mensual de servicios varía. Cualquier cambio en `Calendario` debe pasar por el paso 0.
- **Pruebas atadas al 52.** Las cuatro clases listadas arriba se rompen al cambiar la firma (paso 2); las que afirman 104/43/44/103 son válidas solo con `replicaExcel()`: no deben reutilizarse para mensual.
- **Plazo límite en semanas.** El literal 120 en `ParametrosProducto.java:50` en mensual daría 10 años de proyección (o muy pocos periodos adicionales en diaria). Es el error más probable si se activa mensual sin el paso 3.
- **Mora por periodo vs. por días.** Con tasa periódica un mes sin pago causa ≈ 30,4 días de mora siempre; con días reales varía entre 28 y 31. Si Diego elige días reales, el Excel semanal sigue igual pero hay que explicar la diferencia.
- **Contrato del cuestionario.** Renombrar claves JSON sin coordinar rompe `cuestionario/` (lo trabajan otros agentes).
- **Precisión de `Calc.potencia`.** Usa `double` (igual que Excel); con exponentes 1/24 y 1/12 no hay riesgo nuevo, pero la comparación contra PAGO() y los planes de ejemplo debe usar las mismas tolerancias (Inputs ④: 1 COP, 1e-6 en tasas).
- **DIARIA.** Está en el catálogo pero con 1 día por periodo un atraso de días siempre pisa la cuota siguiente; conviene no ofrecerla hasta que negocio la defina (P-PER-8).

## Preguntas abiertas para Diego

- **P-PER-1** Día de vencimiento: en quincenal, ¿los días 15 y último de cada mes, cada 15 días desde el desembolso o cada dos martes (14 días)? En mensual, ¿el mismo día del desembolso, un día fijo (5, 15, 30) o el último día? ¿Qué pasa si el día no existe (31 de febrero)?
- **P-PER-2** ¿El plazo se pacta en **meses** (y se convierte a cuotas) o directamente en **número de cuotas**? ¿Se puede pactar un número de cuotas que no cierre meses completos?
- **P-PER-3** Redondeo del número de cuotas: con meses × 52/12 no entero (p. ej. 7 meses = 30,33 semanas), ¿hacia abajo como Inputs B15, hacia arriba o al más cercano?
- **P-PER-4** ¿Quincenal entra al catálogo con 24 periodos por año (y su fila en Inputs ⑤)? ¿La diaria sigue siendo una opción real?
- **P-PER-5** Días de mora en mensual (y quincenal): un mes completo sin pago, ¿causa mora por el mes (tasa mensual, como el Excel) o por los días reales del mes (28 a 31)? ¿Desde qué día se cuenta el atraso: el día siguiente al vencimiento o tras un periodo de gracia?
- **P-PER-6** Plazo límite de proyección con saldo pendiente (hoy 100 o 120 semanas): ¿se expresa en meses, en periodos adicionales a la última cuota o en semanas para todas las periodicidades?
- **P-PER-7** Prepago o abono a mitad de periodo: ¿se cobra el interés de los días transcurridos (tasa diaria) o el del periodo completo?
- **P-PER-8** Cobranza: los 3.000 diarios y su tope por pago, ¿son iguales en todas las periodicidades? En mensual un atraso de 20 días es normal dentro del mes siguiente: ¿cuándo se considera cuota vencida?
- **P-PER-9** Eventos que hoy se cuentan en periodos (caso 11: 4 periodos de espera antes de la dación), ¿se cuentan en periodos o en días/semanas cuando el crédito es mensual?
- **P-PER-10** ¿Puede cambiar la periodicidad durante la vida del crédito (reestructuración)? Si sí, ¿cómo se recalcula la cuota?

## Avance (2026-10-20, rama `feat/periodicidad`)

Respuestas de Diego (ronda 3, PPER): hay periodicidad semanal, quincenal y mensual, y la tasa es la conversión efectiva de la EA con 52, 24 y 12 periodos por año. Diego escribió "104 para quincena"; lo tomamos como error y usamos 24, pendiente de su confirmación. La diaria sale del catálogo (PPER-4). El plazo se pacta en cuotas con un máximo de 24 meses; si se pacta en meses, las cuotas son ROUNDDOWN(meses × ppa / 12) (PPER-2 y PPER-3). El plazo límite va en periodos del crédito (PPER-6). La periodicidad no cambia después del desembolso (PPER-10). El prepago a mitad de periodo cobra los días transcurridos (PPER-7). En mensual, la mora se cuenta por los días reales del mes (PPER-5). Vencimientos: la quincenal paga el 5 y el 20 y la semanal los martes, sin festivos y a tiempo hasta el fin del martes (CAL-1 a CAL-3). La fecha de la mensual (el 1 o el 5) sigue sin decidir.

| Paso | Estado | Qué quedó |
|---|---|---|
| 0 | Hecho | `ReplicaSemanalNoCambiaTest`: SHA-256 de cada fila y asiento de los 12 casos con `replicaExcel()` (`src/test/resources/caracterizacion/replica-semanal.sha256`). Pasó sin regenerarse en todos los pasos siguientes. |
| 1 | Hecho | `Periodicidad` con `SEMANAL`, `QUINCENAL(24)` y `MENSUAL`, más `diasNominales`, `adjetivo`, `unidadCuota`, `periodosPorMes`, `cuotasEnMeses` y `periodosDesdeSemanas`. La `DIARIA` se eliminó porque nada la usaba. El mensaje de cuotas fuera de rango usa la unidad de la periodicidad. Prueba: `PeriodicidadTest`. |
| 2 | Hecho | `ParametrosProducto` guarda la `Periodicidad`; `periodosPorAnio()`, `mesesPorAnio()` y `periodosPorMes()` son derivados. Se agregó `Tasas.desdeEA(ea, Periodicidad)`. La firma `desdeEA(ea, 52, 12)` se conserva porque la usan el cambio de tasa del motor y las pruebas de usura que trabaja otro agente. |
| 3 | Hecho | Máximo de 24 meses (`PLAZO_MAXIMO_MESES`, también para la línea ORDINARIO de 36) y `deMeses`. Los casos 8 y 9 usan `periodosDesdeSemanas(100 / 120)`, como Inputs C66 y C73. El límite por defecto son 120 semanas expresadas en periodos (semanal 120, quincenal 55, mensual 27) y es **provisional**: Diego fijó la unidad pero no el valor. |
| 4 | Hecho en parte | `Corrida.semana` pasó a `procesarPeriodo`, y los javadocs de `Calendario`, `Servicios`, `Escenario`, `Instruccion` y `Movimiento` se reescribieron en periodos. Las cuotas clave de `GeneradorEscenarios` salen del `Calendario`; el JSON queda idéntico y sus claves no se renombran (`GeneradorEscenariosTest`). **Pendiente:** las cuotas 86 y 87 de S4, PDOBLE y PTOPE, que vienen del Excel viejo de 86 cuotas y hay que coordinar con `cuestionario/`; el mensaje "Semana N" de `AbonoMayorQueElSaldoException`, que está en el área de abonos y lo verifican pruebas de otro agente; y los javadocs de `ReglasNegocio` y `contabilidad/reglas`. |
| 5 | Hecho | `Caso1InvariantesTest` corre los 300 créditos en las tres periodicidades. `PeriodicidadEscenariosTest` corre los 12 casos en quincenal y mensual y verifica que ningún saldo quede negativo, la prelación, los asientos cuadrados, el cierre en cero o por evento, que los servicios de un mes sumen el valor mensual y el giro mensual. |
| 6 | Hecho, falta la revisión de Diego | `PeriodicidadInvariantesTest` verifica que el saldo final dé 0, que el capital pagado sume el monto, que el interés sea saldo × tasa del periodo y que la cuota coincida con PAGO() de Excel en forma cerrada. Los planes quincenal y mensual están en `docs/reglas/planes-ejemplo-periodicidad.md`. |
| 7 | Hecho en parte | `nucleo/CalendarioPagos` calcula los vencimientos (semanal los martes; quincenal el 5 y el 20; mensual el 1 o el 5 con `DiaPagoMensual` obligatorio y sin valor por defecto), los días de atraso (CAL-1) y los días reales de cada periodo (PPER-5). Prueba: `CalendarioPagosTest`. **Pendiente:** usarlo en el módulo `abonos`, que aún no existe. |
| 8 | Pendiente | La mora por días reales (`DiasMora.DIAS_CALENDARIO`, PPER-5 ya respondida) y el prepago a mitad de periodo (PPER-7: ya existe `interesPrepagoPorDias` con días digitados). |
