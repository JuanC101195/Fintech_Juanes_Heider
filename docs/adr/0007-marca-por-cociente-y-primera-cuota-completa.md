# ADR 0007 · Marca de recuperación por cociente y primera cuota completa prorrateada

**Estado:** aceptada · 2026-11-01 (ronda 12 de Diego: R12-MARCA-POR-COCIENTE, R12-PRIMERA-CUOTA-DETALLE con R10-PRIMERA-CUOTA). Complementa la [ADR 0006](0006-servicios-siempre-y-cobranza-por-cociente.md).

## Contexto

La ADR 0006 dejó el contador de cuotas vencidas por eventos solo para la marca «listo para recuperar» (D46, D54, D67) y la cobranza por el cociente saldo en mora / cuota total. Con dos pagos casi completos el contador llegaba a 2 y marcaba un crédito que debía mucho menos de 2 cuotas, así que la marca y la cobranza no coincidían. Diego respondió el 1 nov que la marca va por el mismo cociente («el hecho que se marque como "lista para recuperar", es lo que activa el cobro de cobranza»).

La primera cuota se prorrateaba solo en el interés (P5-PRORRATEO, D48). Diego pidió la cuota entera proporcional a los días (R10-PRIMERA-CUOTA) y aclaró que lo no cobrado no se cobra después y que «el plan sigue igual (la última cuota queda con el saldo)».

## Decisión

Dos claves nuevas en `ReglasNegocio`, con el Excel intacto:

| Clave | EXCEL | NEGOCIO | Qué hace |
|---|---|---|---|
| `marcaPorCociente` | `false` | `true` | La marca se decide con `llegaAlUmbralDeCobranza(saldoEnMora, valorCuotaParaCobranza)` evaluado al cerrar el periodo, con el estado en mora. Sin `umbralCocienteCobranza` no aplica. |
| `alcanceProrrateoPrimeraCuota` | `SOLO_INTERES` | `CUOTA_COMPLETA` | Con `prorrateoPrimeraCuota` y fechas, `CUOTA_COMPLETA` multiplica el capital contractual y los servicios del periodo 1 por días reales / días nominales (como máximo 1). |

### Marca por cociente

El motor mide la marca en el paso 6 (`MotorCartera`, `Recuperacion.listoParaRecuperacion(estado, llegaAlUmbral)`) con las mismas funciones de la cobranza (`Causador.saldoEnMora` y `Causador.valorCuotaParaCobranza`). Como el cierre del periodo k es el inicio del k + 1, la marca de k y la cobranza de k + 1 miden el mismo cociente: la primera marca llega un periodo antes que la primera cobranza (invariante I15 de `PropiedadesFinancierasAuditTest`, salvo que el periodo k + 1 cambie la tasa y con ella la cuota). La marca persiste aunque el cociente baje (D67, I15b); la cobranza sigue decidiéndose cada periodo por el cociente, así que deja de cobrarse si baja de 2 (supuesto, R13-COBRANZA-TRAS-MARCA). El contador por eventos sigue en la fila (`cuotasVencidas`) y decide la marca solo sin la clave.

### Primera cuota completa

- Periodo 1: interés por los días reales (como D48); capital contractual y servicios × fracción. Los servicios no cobrados se pierden: el periodo causa solo la parte proporcional (también en `serviciosAlDia`, para que un abono no la reviva).
- El saldo del plan (`saldoPlan`) baja solo con el capital cobrado, así que en los periodos 2 a n − 1 la cuota fija cubre el interés del saldo real y amortiza un poco menos.
- La última cuota del plan (`periodo == cuotasVigentes`) vence el saldo del plan completo y la fila reporta su cuota como capital + interés (`Causacion.cuotaPorComponentes`, antes `prorrateada`). Solo si la primera cuota fue más corta que el periodo; si el saldo del plan ya llegó a cero (p. ej. tras un cambio de tasa sobre el saldo total) la cuota sigue siendo la fija.
- Un crédito de una sola cuota la cobra completa.

Supuestos provisionales: el capital diferido causa interés como el resto del saldo (R13-PRIMERA-CUOTA-INTERES) y con más días que el periodo solo el interés cuenta los días de más (R13-PRIMERA-CUOTA-LARGA).

## Consecuencias

- EXCEL no cambia (casos 1 a 12 y huellas EXCEL de caracterización iguales). Cambian las huellas NEGOCIO (`negocio.sha256`, `periodicidades.sha256`, `api.sha256`) y la huella EXCEL de `VersionReglas` (por las dos claves nuevas en el contenido canónico).
- Caso 11 NEGOCIO: mismas cifras; ya no queda marcado (en el 44 debe ~1,6 cuotas). Caso 13 NEGOCIO: mismas cifras (el abono paga 501.831,82 de pendientes, saldo de la 42 en 3.354.386,35, termina en el 86); ya no queda marcado en la 41.
- Las pruebas de las rondas anteriores que documentan la marca por eventos o el prorrateo solo del interés fijan esas reglas (`conMarcaPorCociente(false)`, `conAlcanceProrrateoPrimeraCuota(SOLO_INTERES)`).
- El invariante I8 de la cobranza usa, en la fila de la última cuota con saldo, la cuota fija de la fila anterior como denominador.
- La versión NEGOCIO pasa a `negocio-2026-11-01`.
