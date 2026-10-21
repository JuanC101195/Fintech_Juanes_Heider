# Caso 13 · Abono extra con pendientes

No hay hoja en el Excel de Diego: el caso lo arma el equipo y **Diego valida los valores** (ABN-1). Usa las reglas que Diego ya respondió: A4/D21 (`aplicacionAbono = PRIMERO_PENDIENTES`: el abono paga primero lo pendiente por la prelación de Inputs ⑥) y ABN-2 (el excedente va primero al capital vencido más antiguo y después al vigente).

Prueba: `src/test/java/co/financiera/cartera/casos/Caso13AbonoConPendientesTest.java`. Las cifras de este documento son las que da el motor (redondeadas a centavos para leerlas); la prueba las fija con tolerancia de 1 COP.

**Fuente: respuesta de Diego 2026-10-20 (ronda 3), preguntas ABN-1 y ABN-2; regla A4 de la respuesta del 2026-10-20 en la mañana.**

## Escenario

Réplica del Excel (`ParametrosProducto.replicaExcel()`, `Operacion.ejemploExcel()`): 104 cuotas semanales, EA 87,32 %, cuota financiera 100.406,27, servicios por semana al día 63.034,17 (fondo 48.643,17 + asistencia 10.984,62 + seguro 3.406,38), cuota total 163.440,45. Reglas: las del Excel con `aplicacionAbono = PRIMERO_PENDIENTES` (interés corriente sobre el capital vigente, P-03; servicios sobre el monto inicial, P-07).

| Semana | Lo que hace el cliente |
|---|---|
| 1 a 39 | Paga la cuota completa a tiempo |
| 40 | No paga |
| 41 | Paga 5.000 con 3 días de atraso (no alcanza ni la cobranza) |
| 42 | No paga la cuota por separado: trae un **abono extra de 1.500.000** (menor plazo, la opción por defecto, P-13) |
| 43 en adelante | Paga la cuota completa a tiempo |

En las semanas que no se pagan al día solo se causa el seguro (Inputs ⑥, P-07); el fondo y la asistencia no.

## Tabla semana a semana (menor plazo)

Causado en la semana:

| Semana | Evento | Saldo inicial | Cuota financiera | Interés corriente | Mora | Capital contractual | Cobranza | Servicios | Pago recibido |
|---|---|---|---|---|---|---|---|---|---|
| 39 | Pago normal | 4.536.584,84 | 100.406,27 | 55.230,40 | 0 | 45.175,87 | 0 | 63.034,17 | 163.440,45 |
| 40 | Mora - sin pago | 4.491.408,97 | 100.406,27 | 54.680,41 | 0 | 45.725,87 | 0 | 3.406,38 | 0 |
| 41 | Pago tardío + cobranza | 4.491.408,97 | 100.406,27 | 54.123,72 | 796,68 | 46.282,55 | 9.000,00 | 3.406,38 | 5.000,00 (todo a cobranza) |
| 42 | Cuota + abono extra | 4.491.408,97 | 100.406,27 | 53.560,26 | 1.120,15 | 46.846,02 | 0 | 3.406,38 | 0 + abono 1.500.000 |
| 43 | Pago normal | 3.169.909,34 | 100.406,27 | 38.591,88 | 0 | 61.814,39 | 0 | 63.034,17 | 163.440,45 |
| 44 | Pago normal | 3.108.094,95 | 100.406,27 | 37.839,33 | 0 | 62.566,95 | 0 | 63.034,17 | 163.440,45 |
| 45 | Pago normal | 3.045.528,00 | 100.406,27 | 37.077,61 | 0 | 63.328,66 | 0 | 63.034,17 | 163.440,45 |
| 83 | Pago final | 8.665,40 | 100.406,27 (paga 8.770,90) | 105,50 | 0 | 8.665,40 | 0 | 63.034,17 | 71.805,06 |

La mora de la 41 es capital vencido × tasa semanal + capital de la cuota × ((1 + tasa diaria)³ − 1); la de la 42 es solo capital vencido × tasa semanal (sin días de atraso). El interés corriente se causa sobre el capital vigente (saldo − capital vencido).

Pendientes al cierre de cada semana (en la 42, antes y después del abono):

| Semana | Cobranza | Mora | Interés corriente | Servicios (seguro) | Capital vencido | Saldo de capital | Cuotas vencidas | Estado |
|---|---|---|---|---|---|---|---|---|
| 39 | 0 | 0 | 0 | 0 | 0 | 4.491.408,97 | 0 | Al día |
| 40 | 0 | 0 | 54.680,41 | 3.406,38 | 45.725,87 | 4.491.408,97 | 1 | En mora |
| 41 | 4.000,00 | 796,68 | 108.804,13 | 6.812,77 | 92.008,42 | 4.491.408,97 | 2 | En mora |
| 42 antes del abono | 4.000,00 | 1.916,83 | 162.364,39 | 10.219,15 | 138.854,44 | 4.491.408,97 | 3 | En mora |
| 42 después del abono | 0 | 0 | 0 | 0 | 0 | 3.169.909,34 | 0 | Al día |

Aplicación del abono de 1.500.000 en la semana 42 (prelación de Inputs ⑥, A4; capital vencido antes que vigente, ABN-2):

| Orden | Concepto | Aplicado |
|---|---|---|
| 1 | Gastos de cobranza | 4.000,00 |
| 2 | Interés de mora | 1.916,83 |
| 3 | Interés corriente | 162.364,39 |
| 4 | Fondo de garantías | 0 |
| 5 | Asistencia | 0 |
| 6 | Seguro | 10.219,15 |
| 7 | Capital vencido (el más antiguo primero) | 138.854,44 |
| | **Subtotal pendientes** | **317.354,81** |
| 8 | Capital vigente (excedente) | 1.182.645,20 |
| | **Total** | **1.500.000,00** |

Saldo después del abono: 4.491.408,97 − 138.854,44 − 1.182.645,20 = 3.169.909,34. (Las sumas de cifras redondeadas pueden diferir en un centavo; el motor no redondea.)

**Nueva cuota:**
- Menor plazo (por defecto): la cuota financiera sigue en 100.406,27 y el crédito termina en la **semana 83** (antes, 104). Los servicios terminan con el nuevo plazo (SRV-2) y la última cuota los lleva completos (SRV-3).
- Menor cuota: desde la semana 43 la cuota financiera baja a **73.124,59** = PMT(tasa semanal, 104 − 42, 3.169.909,34) y el crédito termina en la 104.

## Reglas

| Id | Regla | Fórmula | Implementada en | Probada |
|---|---|---|---|---|
| R-13.1 | El abono extra se aplica después de la cuota de la semana y paga primero los pendientes ya causados por la prelación de Inputs ⑥: cobranza → mora → interés corriente → fondo → asistencia → seguro → capital vencido. **Fuente: respuesta de Diego 2026-10-20, pregunta A4 (D21); ronda 3, pregunta ABN-1.** | `pendiente_k pagado = MIN(resto, CxC_k)` en ese orden | `MotorCartera.pagarPendientes` con `aplicacionAbono = PRIMERO_PENDIENTES` (`config/` lo trae) | `elAbonoCubreCadaPendienteYElExcedenteVaACapitalVigente` |
| R-13.2 | El excedente cubre primero el capital vencido más antiguo y después el vigente. **Fuente: respuesta de Diego 2026-10-20 (ronda 3), pregunta ABN-2.** | `vencido = MIN(resto, capital vencido)`; `vigente = resto − vencido` | `MotorCartera.pagarPendientes` y `aplicarAbono` | `unAbonoQueNoAlcanzaElVencidoNoTocaElCapitalVigente` |
| R-13.3 | Nunca se cobran por adelantado intereses ni servicios de semanas futuras: el abono solo toca lo causado (incluida la semana del abono). **Fuente: respuesta de Diego 2026-10-20, pregunta A4.** | — | `MotorCartera.aplicarAbono` | `RespuestasDiego20OctTest.elAbonoNoAdelantaInteresesNiServiciosDeCuotasFuturas` |
| R-13.4 | Si el abono cubre todo lo pendiente, el cliente queda al día (cuotas vencidas en 0). **Fuente: respuesta de Diego 2026-10-20, pregunta A4.** | — | `MotorCartera.semana` | `elAbonoCubreCadaPendiente…` |
| R-13.5 | Con menor plazo la cuota se mantiene y el número de cuotas se recalcula (NPER); los servicios terminan con el nuevo plazo. **Fuente: respuesta de Diego 2026-10-20 (ronda 3), pregunta SRV-2.** | `cuotas = periodo + NPER(tasa, cuota, saldo − vencido)` | `MotorCartera.aplicarAbono` | `aMenorPlazoLaCuotaSeMantieneYElCreditoTerminaEnLa83`, `RespuestasDiegoRonda3Test.Srv2ServiciosConElNuevoPlazo` |
| R-13.6 | Con menor cuota la cuota se recalcula sobre el capital vigente con la tasa vigente; cada abono a menor cuota la vuelve a recalcular. **Fuente: respuesta de Diego 2026-10-20 (ronda 3), pregunta ABN-5; pregunta A5 de la mañana.** | `PMT(tasa vigente, cuotas − periodo, saldo − vencido)` | `MotorCartera.aplicarAbono` | `aMenorCuotaLaCuotaSeRecalculaSobreElCapitalVigenteYTerminaEnLa104`, `RespuestasDiegoRonda3Test.abn5…` |
| R-13.7 | Sin límite de abonos por crédito. **Fuente: respuesta de Diego 2026-10-20 (ronda 3), pregunta ABN-3.** | — | El motor acepta un `AbonoExtra` en cualquier semana | `abn5…` (dos abonos) |
| R-13.8 | **ABN-4.** Pagar antes del martes el valor **exacto** de una cuota ya causada es pago corriente, no anticipo; lo que supere ese valor es abono extra. **Fuente: respuesta de Diego 2026-10-20 (ronda 3), pregunta ABN-4.** | `pago corriente = MIN(recibido, exigible causado)`; `abono extra = recibido − pago corriente` | `nucleo/ClasificacionPago.clasificar` (función pura, sin fechas) | `ClasificacionPagoTest`; `RespuestasDiegoRonda3Test.abn4…` (la clasificación alimenta al motor como `PagoValor` + `AbonoExtra`) |
| R-13.9 | El asiento del abono acredita cada pendiente en su cuenta (CxC cobranza, CxC mora, CxC interés, CxP servicios, cartera vencida) y el resto a cartera de créditos; cuadra. **Fuente: respuesta de Diego 2026-10-20, pregunta A4.** | — | `contabilidad/reglas/AbonoExtraordinario` | `losAsientosCuadran` |

## Requisito para el módulo `abonos` (ABN-4)

El motor trabaja por periodo y sin fechas, así que la parte de calendario de ABN-4 queda para el módulo futuro `abonos` (clasificador de pagos):

1. Con la fecha del pago y el calendario del crédito (martes fijo, CAL-1/CAL-3: la cuota es a tiempo hasta el fin del martes y el atraso empieza el miércoles), determinar cuál es el periodo cuya cuota **ya está causada** en esa fecha. Lectura propuesta (no confirmada): un pago entre el miércoles y el martes siguiente corresponde a la cuota de ese martes.
2. Calcular lo exigible de ese periodo: cuota financiera + servicios del periodo + lo pendiente de periodos anteriores.
3. Llamar a `ClasificacionPago.clasificar(recibido, exigible)` y enviar al motor `PagoValor(pagoCorriente, días de atraso)` y, si `tieneAbonoExtra()`, `AbonoExtra(abonoExtra, modalidad que elija el cliente)`.

Supuesto que no está en la respuesta de Diego: si en la fecha del pago no hay ninguna cuota causada (el cliente paga semanas antes), lo exigible es 0 y todo el valor es abono extra. Se deja así en la función y se pregunta en la ronda 4.

## Preguntas para Diego

1. ¿Las cifras de las tres tablas son las que espera? En particular: (a) que en las semanas 40 a 42 solo se cause el seguro porque no se pagó al día; (b) que el interés corriente de la semana del abono (42) se cobre completo con el abono; (c) que la mora de la 42 sea solo sobre el capital vencido, sin días de atraso.
2. En la semana 42 el cliente no pagó la cuota por separado: el motor trata los 1.500.000 como abono y su primera parte cubre la cuota de esa semana por prelación. Con ABN-4 se llegaría a lo mismo (la cuota exigible es pago corriente y el resto, abono extra). ¿Está bien?
3. Si el cliente paga semanas antes de que se cause la cuota, ¿todo es abono extra (lo que hoy supone `ClasificacionPago`) o se guarda como anticipo para la próxima cuota?
