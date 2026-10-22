# Caso 13 · Abono extra con pendientes

No hay hoja en el Excel de Diego: el caso lo arma el equipo y **Diego valida los valores** (ABN-1). Usa las reglas que Diego ya respondió: A4/D21 (el abono paga primero lo pendiente por la prelación de Inputs ⑥), ABN-2 (el excedente va primero al capital vencido más antiguo y después al vigente) y, desde la ronda 4, **R4-CASO13** (el excedente vuelve a pasar por la prelación sobre lo causado del periodo corriente).

Prueba: `src/test/java/co/financiera/cartera/casos/Caso13AbonoConPendientesTest.java` (la clase anidada `RondaCuatro` tiene las cifras nuevas). Las cifras de este documento son las que da el motor (redondeadas a centavos para leerlas); la prueba las fija con tolerancia de 1 COP.

**Fuente: respuesta de Diego 2026-10-20 (ronda 3), preguntas ABN-1 y ABN-2; regla A4 de la respuesta del 2026-10-20 en la mañana; respuesta de Diego 2026-10-21 (ronda 4), pregunta R4-CASO13.**

## Escenario

Réplica del Excel (`ParametrosProducto.replicaExcel()`, `Operacion.ejemploExcel()`): 104 cuotas semanales, línea CPPU (EA 87,62 %), cuota financiera 100.406,27, servicios por semana al día 63.034,17 (fondo 48.643,17 + asistencia 10.984,62 + seguro 3.406,38), cuota total 163.440,45. Reglas: interés corriente sobre el capital vigente (P-03) y servicios sobre el monto inicial (P-07).

| Semana | Lo que hace el cliente |
|---|---|
| 1 a 39 | Paga la cuota completa a tiempo |
| 40 | No paga |
| 41 | Paga 5.000 con 3 días de atraso (no alcanza ni la cobranza) |
| 42 | No paga la cuota por separado: trae un **abono extra de 1.500.000** (menor plazo, la opción por defecto, P-13) |
| 43 en adelante | Paga la cuota completa a tiempo |

En las semanas que no se pagan al día solo se causa el seguro (Inputs ⑥, P-07, D17); el fondo y la asistencia no.

## Qué cambió en la ronda 4 (R4-CASO13)

Diego (2026-10-21): *"Sí se aplica todo lo vencido de acuerdo al ejemplo, pero del 1.182.645,20 sobrante se vuelve a respetar la prelación de aplicación de pagos de las 7 líneas"*.

- **Antes (ronda 3, `aplicacionAbono = PRIMERO_PENDIENTES`):** el abono paga lo vencido por prelación y todo el sobrante va a capital vigente.
- **Después (ronda 4, `aplicacionAbono = PRELACION_COMPLETA`, el que trae `config/reglas-negocio.properties`):** el abono paga lo vencido igual que antes; el sobrante vuelve a pasar por la prelación (cobranza → mora → interés corriente → fondo → asistencia → seguro → capital) sobre lo **causado de la semana 42**, y solo el resto va a capital vigente.

Cómo lo interpreta el motor: lo vencido del ejemplo ya incluye todo lo causado de la semana 42 (su interés, su mora, su seguro y su capital), porque la semana 42 no tuvo pago aparte. Lo único de la semana 42 que no estaba causado son su **fondo de garantías y su asistencia**, que no se causan en una semana sin pago al día (D17). Con el abono la cuota 42 queda pagada en su propia semana, así que la segunda pasada de la prelación cobra esos dos servicios (48.643,17 + 10.984,62 = **59.627,79**) y el resto va a capital. La segunda pasada **nunca** cobra nada de la semana 43 ni de las siguientes (A4: los ítems 1 a 6 no se cobran por adelantado).

`EXCEL` no cambia (`SOLO_CAPITAL`: todo el abono va a capital). `PRIMERO_PENDIENTES` se conserva para comparar.

## Tabla semana a semana (menor plazo, ronda 4)

Causado en la semana:

| Semana | Evento | Saldo inicial | Cuota financiera | Interés corriente | Mora | Capital contractual | Cobranza | Servicios | Pago recibido |
|---|---|---|---|---|---|---|---|---|---|
| 39 | Pago normal | 4.536.584,84 | 100.406,27 | 55.230,40 | 0 | 45.175,87 | 0 | 63.034,17 | 163.440,45 |
| 40 | Mora - sin pago | 4.491.408,97 | 100.406,27 | 54.680,41 | 0 | 45.725,87 | 0 | 3.406,38 | 0 |
| 41 | Pago tardío + cobranza | 4.491.408,97 | 100.406,27 | 54.123,72 | 796,68 | 46.282,55 | 9.000,00 | 3.406,38 | 5.000,00 (todo a cobranza) |
| 42 | Cuota + abono extra | 4.491.408,97 | 100.406,27 | 53.560,26 | 1.120,15 | 46.846,02 | 0 | **63.034,17** (antes 3.406,38) | 0 + abono 1.500.000 |
| 43 | Pago normal | **3.229.537,12** (antes 3.169.909,34) | 100.406,27 | **39.317,82** (antes 38.591,88) | 0 | **61.088,45** (antes 61.814,39) | 0 | 63.034,17 | 163.440,45 |
| 44 | Pago normal | 3.168.448,67 (antes 3.108.094,95) | 100.406,27 | 38.574,10 (antes 37.839,33) | 0 | 61.832,17 (antes 62.566,95) | 0 | 63.034,17 | 163.440,45 |
| 45 | Pago normal | 3.106.616,50 (antes 3.045.528,00) | 100.406,27 | 37.821,33 (antes 37.077,61) | 0 | 62.584,94 (antes 63.328,66) | 0 | 63.034,17 | 163.440,45 |
| 83 | Pago normal (antes: pago final) | 105.418,17 (antes 8.665,40) | 100.406,27 | 1.283,41 (antes 105,50) | 0 | 99.122,87 (antes 8.665,40) | 0 | 63.034,17 | 163.440,45 (antes 71.805,06) |
| 84 | **Pago final** (nueva) | 6.295,30 | 100.406,27 (paga 6.371,94) | 76,64 | 0 | 6.295,30 | 0 | 63.034,17 | 69.406,12 |

La mora de la 41 es capital vencido × tasa semanal + capital de la cuota × ((1 + tasa diaria)³ − 1); la de la 42 es solo capital vencido × tasa semanal (sin días de atraso). El interés corriente se causa sobre el capital vigente (saldo − capital vencido). En la semana 42 los servicios causados son los 3.406,38 del seguro (causado sin pago) más el fondo y la asistencia que causó el abono.

Pendientes al cierre de cada semana (en la 42, antes y después del abono; igual en las dos rondas salvo el saldo final):

| Semana | Cobranza | Mora | Interés corriente | Servicios (seguro) | Capital vencido | Saldo de capital | Cuotas vencidas | Estado |
|---|---|---|---|---|---|---|---|---|
| 39 | 0 | 0 | 0 | 0 | 0 | 4.491.408,97 | 0 | Al día |
| 40 | 0 | 0 | 54.680,41 | 3.406,38 | 45.725,87 | 4.491.408,97 | 1 | En mora |
| 41 | 4.000,00 | 796,68 | 108.804,13 | 6.812,77 | 92.008,42 | 4.491.408,97 | 2 | En mora |
| 42 antes del abono | 4.000,00 | 1.916,83 | 162.364,39 | 10.219,15 | 138.854,44 | 4.491.408,97 | 3 | En mora |
| 42 después del abono | 0 | 0 | 0 | 0 | 0 | **3.229.537,12** (antes 3.169.909,34) | 0 | Al día |

## Aplicación del abono de 1.500.000 en la semana 42: antes y después

| Orden | Concepto | Ronda 3 (antes) | Ronda 4 (después) |
|---|---|---|---|
| | **Primera pasada: todo lo vencido** (prelación de Inputs ⑥, A4; capital vencido antes que vigente, ABN-2) | | |
| 1 | Gastos de cobranza | 4.000,00 | 4.000,00 |
| 2 | Interés de mora | 1.916,83 | 1.916,83 |
| 3 | Interés corriente (semanas 40 a 42) | 162.364,39 | 162.364,39 |
| 4 | Fondo de garantías | 0 | 0 |
| 5 | Asistencia | 0 | 0 |
| 6 | Seguro (semanas 40 a 42) | 10.219,15 | 10.219,15 |
| 7 | Capital vencido (el más antiguo primero) | 138.854,44 | 138.854,44 |
| | Subtotal vencido | 317.354,81 | 317.354,81 |
| | **Segunda pasada: lo causado de la semana 42 con el sobrante de 1.182.645,20** (R4-CASO13) | | |
| 1 a 3 | Cobranza, mora e interés corriente de la semana 42 | — | 0 (ya pagados en la primera pasada) |
| 4 | Fondo de garantías de la semana 42 | — | **48.643,17** |
| 5 | Asistencia de la semana 42 | — | **10.984,62** |
| 6 | Seguro de la semana 42 | — | 0 (ya pagado en la primera pasada) |
| | Subtotal semana 42 | — | **59.627,79** |
| | **Total a pendientes y conceptos** | **317.354,81** | **376.982,59** |
| 7 | Capital vigente (lo que queda) | 1.182.645,20 | **1.123.017,41** |
| | **Total** | **1.500.000,00** | **1.500.000,00** |

Saldo después del abono: 4.491.408,97 − 138.854,44 − 1.123.017,41 = **3.229.537,12** (antes 3.169.909,34). (Las sumas de cifras redondeadas pueden diferir en un centavo; el motor no redondea.) Lo recaudado de fondo y asistencia de la 42 se gira a los terceros al cerrar el mes, con la semana 43 (fondo girado en la 43: 97.286,34 = 2 × 48.643,17).

**Nueva cuota:**

| Modalidad | Ronda 3 (antes) | Ronda 4 (después) |
|---|---|---|
| Menor plazo (por defecto) | Cuota financiera 100.406,27; termina en la **semana 83** | Cuota financiera 100.406,27; termina en la **semana 84** (última cuota de 69.406,12: 6.371,94 financiera + 63.034,17 de servicios) |
| Menor cuota | Desde la 43, **73.124,59** = PMT(tasa semanal, 104 − 42, 3.169.909,34); termina en la 104 | Desde la 43, **74.500,11** = PMT(tasa semanal, 104 − 42, 3.229.537,12); termina en la 104 |

Con menor plazo los servicios terminan con el nuevo plazo (SRV-2) y la última cuota los lleva completos (SRV-3).

## Reglas

| Id | Regla | Fórmula | Implementada en | Probada |
|---|---|---|---|---|
| R-13.1 | El abono extra se aplica después de la cuota de la semana y paga primero los pendientes ya causados por la prelación de Inputs ⑥: cobranza → mora → interés corriente → fondo → asistencia → seguro → capital vencido. **Fuente: respuesta de Diego 2026-10-20, pregunta A4 (D21); ronda 3, pregunta ABN-1.** | `pendiente_k pagado = MIN(resto, CxC_k)` en ese orden | `MotorCartera.pagarPendientes` con `aplicacionAbono = PRIMERO_PENDIENTES` o `PRELACION_COMPLETA` | `elAbonoCubreCadaPendienteYElExcedenteVaACapitalVigente` |
| R-13.2 | El excedente cubre primero el capital vencido más antiguo y después el vigente. **Fuente: respuesta de Diego 2026-10-20 (ronda 3), pregunta ABN-2.** | `vencido = MIN(resto, capital vencido)`; `vigente = resto − vencido` | `MotorCartera.pagarPendientes` y `aplicarAbono` | `unAbonoQueNoAlcanzaElVencidoNoTocaElCapitalVigente` |
| R-13.3 | Nunca se cobran por adelantado intereses ni servicios de semanas futuras: el abono solo toca lo causado (incluida la semana del abono). **Fuente: respuesta de Diego 2026-10-20, pregunta A4; confirmado en la ronda 4, pregunta R4-CASO13.** | — | `MotorCartera.aplicarAbono` | `RespuestasDiego20OctTest.elAbonoNoAdelantaInteresesNiServiciosDeCuotasFuturas`, `RondaCuatro.nuncaSeCobranConceptosDePeriodosFuturos` |
| R-13.4 | Si el abono cubre todo lo pendiente, el cliente queda al día (cuotas vencidas en 0). **Fuente: respuesta de Diego 2026-10-20, pregunta A4.** | — | `MotorCartera.procesarPeriodo` | `elAbonoCubreCadaPendiente…` |
| R-13.5 | Con menor plazo la cuota se mantiene y el número de cuotas se recalcula (NPER); los servicios terminan con el nuevo plazo. **Fuente: respuesta de Diego 2026-10-20 (ronda 3), pregunta SRV-2.** | `cuotas = periodo + NPER(tasa, cuota, saldo − vencido)` | `MotorCartera.aplicarAbono` | `aMenorPlazoLaCuotaSeMantieneYElCreditoTerminaEnLa83`, `RondaCuatro.aMenorPlazoTerminaEnLa84…` |
| R-13.6 | Con menor cuota la cuota se recalcula sobre el capital vigente con la tasa vigente; cada abono a menor cuota la vuelve a recalcular. **Fuente: respuesta de Diego 2026-10-20 (ronda 3), pregunta ABN-5; pregunta A5 de la mañana.** | `PMT(tasa vigente, cuotas − periodo, saldo − vencido)` | `MotorCartera.aplicarAbono` | `aMenorCuotaLaCuotaSeRecalculaSobreElCapitalVigenteYTerminaEnLa104`, `RondaCuatro.…YAMenorCuotaLaCuotaBajaA74500` |
| R-13.7 | Sin límite de abonos por crédito. **Fuente: respuesta de Diego 2026-10-20 (ronda 3), pregunta ABN-3.** | — | El motor acepta un `AbonoExtra` en cualquier semana | `abn5…` (dos abonos) |
| R-13.8 | **ABN-4.** Pagar antes del martes el valor **exacto** de una cuota ya causada es pago corriente, no anticipo; lo que supere ese valor es abono extra. **Fuente: respuesta de Diego 2026-10-20 (ronda 3), pregunta ABN-4.** | `pago corriente = MIN(recibido, exigible causado)`; `abono extra = recibido − pago corriente` | `nucleo/ClasificacionPago.clasificar` (función pura, sin fechas) | `ClasificacionPagoTest`; `RespuestasDiegoRonda3Test.abn4…` |
| R-13.9 | El asiento del abono acredita cada pendiente en su cuenta (CxC cobranza, CxC mora, CxC interés, CxP servicios a terceros, cartera vencida) y el resto a cartera de créditos; cuadra. Los servicios de la segunda pasada van a la misma CxP servicios a terceros. **Fuente: respuesta de Diego 2026-10-20, pregunta A4.** | — | `contabilidad/reglas/AbonoExtraordinario` | `losAsientosCuadran`, `RondaCuatro.losAsientosCuadran…` |
| R-13.10 | **R4-CASO13.** Después de pagar todo lo vencido, el excedente del abono vuelve a pasar por la prelación de 7 conceptos sobre lo causado del periodo corriente; solo el resto va a capital vigente. En un periodo que no se pagó al día lo único sin causar son su fondo y su asistencia (D17): el excedente los paga en ese orden (fondo → asistencia → seguro) y quedan causados en el periodo. Si el excedente no alcanza, lo que falte no queda como CxC (el periodo sigue sin pago al día). Si la cuota del periodo ya se pagó al día, no cambia nada. **Fuente: respuesta de Diego 2026-10-21 (ronda 4), pregunta R4-CASO13.** | `servicios corrientes = MIN(sobrante, servicios al día − servicios causados)` en orden; `capital vigente = sobrante − servicios corrientes` | `MotorCartera.aplicarAbono` y `pagarServiciosCorrientes` con `aplicacionAbono = PRELACION_COMPLETA` (`config/` lo trae) | `RondaCuatro.elExcedentePagaElFondoYLaAsistenciaDeLaSemana42…`, `…conLaCuotaPagadaAlDiaEsIgualQuePrimeroPendientes`, `…unExcedenteCortoPagaPrimeroElFondo…` |
| R-13.11 | **R4-PAGO-SIN-CUOTA.** Solo antes de la primera cuota no hay cuota causada. Después siempre la hay: un pago cubre la cuota causada y lo que sobre es abono extra. Confirma ABN-4 y `ClasificacionPago`. **Fuente: respuesta de Diego 2026-10-21 (ronda 4), pregunta R4-PAGO-SIN-CUOTA.** | — | `nucleo/ClasificacionPago` (sin cambios) | `ClasificacionPagoTest` |
| R-13.12 | **R4-A3.** No hay mínimo para el abono extra. **Fuente: respuesta de Diego 2026-10-21 (ronda 4), pregunta R4-A3.** | — | El motor acepta cualquier valor positivo (sin cambios) | — |

`PRELACION_COMPLETA` se aplica al abono extra. El prepago total paga todo lo pendiente y todo el saldo (A4) y no hace la segunda pasada: un prepago en una semana sin pago al día no cobra el fondo ni la asistencia de esa semana. Si Diego lo pide igual que el abono, es un cambio pequeño en `MotorCartera.prepagar`.

## Requisito para el módulo `abonos` (ABN-4)

El motor trabaja por periodo y sin fechas, así que la parte de calendario de ABN-4 queda para el módulo futuro `abonos` (clasificador de pagos):

1. Con la fecha del pago y el calendario del crédito (martes fijo, CAL-1/CAL-3: la cuota es a tiempo hasta el fin del martes y el atraso empieza el miércoles), determinar cuál es el periodo cuya cuota **ya está causada** en esa fecha. Lectura propuesta (no confirmada): un pago entre el miércoles y el martes siguiente corresponde a la cuota de ese martes.
2. Calcular lo exigible de ese periodo: cuota financiera + servicios del periodo + lo pendiente de periodos anteriores.
3. Llamar a `ClasificacionPago.clasificar(recibido, exigible)` y enviar al motor `PagoValor(pagoCorriente, días de atraso)` y, si `tieneAbonoExtra()`, `AbonoExtra(abonoExtra, modalidad que elija el cliente)`.

R4-PAGO-SIN-CUOTA confirma que el único caso sin cuota causada es antes de la primera cuota. Ahí hoy lo exigible es 0 y todo el valor es abono extra; si eso es abono a capital o se guarda para la primera cuota es la pregunta P5-PAGO-ANTES-PRIMERA de la ronda 5.

## Preguntas para Diego (ronda 5, P5-CASO13)

1. ¿Las cifras de la columna "Ronda 4 (después)" son las que espera? En particular: (a) que el sobrante de 1.182.645,20 pague el fondo (48.643,17) y la asistencia (10.984,62) de la semana 42, y que a capital vayan 1.123.017,41; (b) que el saldo quede en 3.229.537,12; (c) a menor plazo, que el crédito termine en la semana 84; a menor cuota, que la cuota baje a 74.500,11.
2. ¿Esa es la lectura correcta de "se vuelve a respetar la prelación de las 7 líneas"? La alternativa sería que el sobrante también pagara la cuota de la semana 43 (interés, servicios y capital), pero eso adelanta conceptos de un periodo futuro y contradice A4, por eso no se hizo.
3. Si en vez de un abono el cliente hace un prepago total en una semana sin pago al día, ¿también se cobran el fondo y la asistencia de esa semana?
