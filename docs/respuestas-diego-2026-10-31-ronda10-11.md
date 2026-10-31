# Respuestas de Diego, rondas 10 y 11 (2026-10-31)

Diego respondió el 31 oct 6 de las 11 preguntas abiertas de las rondas 10 y 11. Este documento recoge solo las que se aplican en esta entrega (rama `feat/ronda10-11-seguras`); R10-PERIODO-ATRASADO, R10-CONTADOR-VENCIDAS y R11-SEGUROS-TRAS-SUSPENSION (servicios siempre y cobranza por cociente) se aplican en la rama `feat/rediseno-servicios-cobranza` (sección «Rediseño de servicios y cobranza», abajo); R10-JUSTIFICACIONES y R10-VALOR-MOTO-REENTREGADA siguen abiertas.

| Pregunta | Respuesta de Diego | Qué se hizo | Decisión |
|---|---|---|---|
| R10-VALOR-COBRANZA | Lo define el director financiero. | Se documenta: el valor es un parámetro configurable con vigencias (`VigenciasCobranza`, cada crédito guarda el vigente al originarse). Los 3.000 diarios (`cobranzaDiaria`) y los 40.000 por periodo (`cobranzaPorPeriodo`) quedan solo como valores provisionales de ejemplo. **No cambia ninguna cifra.** | D65 |
| R10-PRIMERA-CUOTA | `proporcional-dias`: la cuota entera (capital, interés y servicios) se prorratea por los días, como el sistema actual. | **No se implementa.** Hoy (D48, clave `prorrateoPrimeraCuota`) solo se prorratea el interés y capital y servicios van completos. Prorratear toda la cuota deja sin cobrar una parte del capital y de los servicios (4/7 en el ejemplo de 3 días de 7) y la respuesta no dice qué pasa con ella: se pierde, se difiere a las cuotas siguientes o el plan cierra distinto. Cambiarlo en silencio alteraría el plan de pagos. Pregunta nueva `R12-PRIMERA-CUOTA-DETALLE`. | D66 |
| R10-MARCA-RECUPERACION | `primera-vez`: se marca la primera vez que llega a 2 periodos atrasados, aunque luego se ponga al día. | Confirma el comportamiento actual (`cuotasVencidasParaRecuperacion` = 2; la marca persiste hasta que el crédito cierra). Sin cambio de código; prueba nueva. | D67 |
| R11-BASE-FINANCIACION-INICIAL | `monto-financiado`: el monto financiado a secas. | Confirma lo que hace el motor desde D63 (el 0,3 % mensual sobre el monto financiado, la base del seguro de vida). Sin cambio de código; prueba nueva. | D68 |
| R11-PERIODIZACION-SEGURO | `igual-que-vida`. | Confirma lo que hace el motor desde D63 (monto × % / periodos por mes, igual que el de vida). Sin cambio de código; prueba nueva con las tres periodicidades. | D68 |
| R11-CONTABILIDAD-SEGURO-IVA | El asiento segmenta: CxP seguros 10.000 / IVA por pagar por seguros 1.900 / CxP aseguradoras 11.900. El IVA nunca es responsabilidad de la financiera frente a la DIAN. | Cuentas nuevas `CXP_SEGUROS` e `IVA_SEGUROS_POR_PAGAR` (reemplazan la provisional `IVA_POR_PAGAR` de D63 y el uso de `CXP_SERVICIOS_TERCEROS` para el todo riesgo); `ReglaContable.cxpAseguradoras` suma las dos (11.900). Solo cambian los asientos con seguro todo riesgo (NEGOCIO); EXCEL queda intacto. Pruebas de partida doble con el ejemplo 10.000 / 1.900 / 11.900. | D69 |

## Preguntas que se generan (ronda 12)

1. **R12-PRIMERA-CUOTA-DETALLE.** Con la primera cuota proporcional a los días, ¿qué pasa con el capital y los servicios que esa cuota no cobra (no se cobran después, se reparten en las cuotas siguientes o solo se prorratean interés y servicios)? Qué pasa además si la entrega es más de un periodo antes del primer vencimiento.
2. **R12-IVA-FONDO-ASISTENCIAS.** Diego pidió segmentar el IVA del seguro y de «los demás servicios con IVA». El motor solo modela IVA aparte para el seguro todo riesgo; el del fondo de garantías y las asistencias va incluido en sus cuentas («IVA incl.», como el Excel). ¿Se segmentan igual? Hasta que responda no se toca (cambiaría las cifras de todos los asientos con servicios).

## Rediseño de servicios y cobranza (rama `feat/rediseno-servicios-cobranza`, D70 a D73, ADR 0006)

Diego respondió R10-CONTADOR-VENCIDAS con otra regla, en cuatro puntos. R11-SEGUROS-TRAS-SUSPENSION remite a ella y R10-PERIODO-ATRASADO coincide con el motor.

| Pregunta | Respuesta de Diego | Qué se hizo | Decisión |
|---|---|---|---|
| R10-CONTADOR-VENCIDAS (1) | El cliente siempre paga los servicios (fondo + asistencia + seguros) en la cuota, esté al día o atrasado. | Clave `causacionServicios = SIEMPRE` en NEGOCIO: los tres servicios en todo periodo del plazo, sin suspensión por atraso. EXCEL sigue con D17 (`SOLO_AL_DIA`) y las rondas 8 y 9 se conservan como `SUSPENDER_POR_UMBRAL`. | D70 |
| R10-CONTADOR-VENCIDAS (2) | La CxP con terceros solo se registra contablemente cuando hay recaudo efectivo. | Verificado: el motor nunca causó una CxC de servicios; solo los registra al recaudarlos. En NEGOCIO la cuenta pasa a «Ingresos recibidos para terceros» (PUC 2815, provisional) y en dación o retoma lo que cubre la moto es recaudo efectivo. EXCEL intacto. | D72 |
| R10-CONTADOR-VENCIDAS (3) | La cobranza se ejecuta cuando saldo total en mora / valor total de la cuota > 2; con abonos parciales que no llegan a 2 cuotas no hay cobranza pero sí interés de mora. | Clave `umbralCocienteCobranza = 2`; se mide al iniciar cada periodo con los saldos del cierre del anterior. **Supuestos provisionales:** saldo en mora = todo lo pendiente (`baseMoraCobranza = TOTAL_PENDIENTE`), estrictamente mayor (`comparacionCobranza = MAYOR`), denominador = cuota vigente + servicios por cuota, al peso. | D71 |
| R10-CONTADOR-VENCIDAS (4) | En retoma o dación se suspenden por completo los servicios (cierra el crédito). | Con `SIEMPRE`, el periodo de la dación o la retoma no causa servicios (supuesto del equipo; la mora sí se causa). | D70 |
| R11-SEGUROS-TRAS-SUSPENSION | Remite a R10-CONTADOR-VENCIDAS: sin suspensión, los dos seguros se causan siempre. | Cubierto por `SIEMPRE`. | D70 |
| R10-PERIODO-ATRASADO | El capital no cubierto queda vencido, se suma a la cuota siguiente y genera mora. | Coincide con el motor; prueba nueva. | D73 |

Efecto en NEGOCIO: el caso 11 deja de cobrar los 80.000 de cobranza (nunca supera 2 cuotas antes de la dación) y vuelve a causar fondo y asistencia en el 44; el caso 13 deja de cobrar los 80.000 de las semanas 41 y 42, causa fondo y asistencia en ellas, el abono paga 501.831,83 de pendientes (antes 462.575,83) y termina en el periodo 86 (antes 85). Detalle en la ADR 0006.

### Preguntas que se generan (ronda 12)

3. **R12-COBRANZA-SALDO-MORA.** Qué entra en el saldo en mora (todo lo pendiente o solo lo que fue cuota), si es estrictamente más de 2 y cuándo se mide.
4. **R12-COBRANZA-DENOMINADOR.** El valor total de la cuota tras un abono a menor cuota o un cambio de tasa: la cuota vigente (supuesto) o la pactada.
5. **R12-COBRANZA-PERIODICIDADES.** Si el umbral de 2 cuotas y el valor por periodo son iguales en semanal, quincenal y mensual.
6. **R12-MARCA-POR-COCIENTE.** Si la marca «listo para recuperar» sigue por eventos o también por el cociente.
7. **R12-SERVICIOS-CIERRE.** Si el periodo de la dación o la retoma causa servicios y qué pasa con los cobrados y no recaudados (cuentas de orden).
8. **R12-SERVICIOS-DESPUES-PLAZO.** Si «siempre» incluye los periodos después de la última cuota pactada.
9. **R12-VALOR-EXIGIBLE.** Si el valor a pagar que se le muestra al cliente incluye lo pendiente.
10. **R12-GIRO-TERCEROS.** Cada cuánto se gira a los terceros lo recaudado (hoy, mensual).
11. **R12-CUENTA-TERCEROS.** La cuenta del PUC de los servicios recaudados para terceros (hoy 2815, provisional).
