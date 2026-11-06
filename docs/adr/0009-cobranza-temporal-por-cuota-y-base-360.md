# ADR 0009 · Cobranza temporal de 25.000 por cuota atrasada y base 360

Fecha: 2026-11-06 · Estado: aceptada, con supuestos (ronda 15) · Decisiones: D88 (**temporal**), D89 (instrucciones de Diego del 2026-11-05, `respuestas-diego-2026-11-05-ronda14-y-nuevas.md`)

## Contexto

En la sesión de la ronda 14 (2026-11-05) Diego no cambió la lógica de las dos preguntas abiertas (R14-SERVICIOS-CIERRE y R14-MARCA-SEMANAS siguen abiertas) y dio dos instrucciones nuevas:

1. «Vamos a cambiar temporalmente la política de cobranza: si el cliente paga atrasado así sea 1 día, se le cobra un cargo de cobranza de $25.000 (se conserva su posición en la prelación de aplicación de pagos)».
2. «Cambiaremos la base 365 a base 360 para el cálculo de las cuotas».

Ninguna de las dos trae el detalle que el motor necesita, así que se implementan con los supuestos de abajo, solo en el perfil NEGOCIO (EXCEL no cambia), y los supuestos se preguntan en la ronda 15.

## Decisión

### 1. Cobranza por cuota atrasada (D88, temporal)

Opción `ReglasNegocio.cobranzaPorCuotaAtrasada` (BigDecimal; `0` o vacío = apagada, el Excel; NEGOCIO = `25000`).

- **Cuándo se causa (supuesto, R15-COBRANZA-CUOTA).** Una vez por **cuota**, en el periodo en que esa cuota vence sin quedar pagada a tiempo: el pago del periodo trae días de atraso (`PagoContractual`, `PagoParcial` o `PagoValor` con días > 0), no alcanza la cuota total del periodo (cuota financiera + servicios: `PagoParcial` de menos del 100 %, `PagoValor` menor) o no hay pago (`SinPago`). Nunca por día: 1 día o 20 días de atraso causan lo mismo; tres semanas sin pago causan 75.000 (25.000 por cuota).
- **Contra qué se mide.** Contra la cuota del periodo, no contra todo lo pendiente. Si se midiera contra todo lo pendiente, un cliente que paga una vez 1 día tarde y después paga su cuota completa a tiempo cada semana causaría 25.000 todas las semanas hasta el final: el propio cargo, que la prelación cobra primero, deja la cuota corta y la siguiente cuota «no queda cubierta». Con la operación del Excel: cuota 2 pagada 1 día tarde → 25.000 (más 50,09 de mora) una sola vez; con la otra lectura se volvían a causar 25.000 en las cuotas 3, 4 y siguientes, mientras quedara algo pendiente. Se pregunta en R15-COBRANZA-CUOTA.
- **No se causa** fuera del plazo vigente (ya no vence ninguna cuota; lo pendiente sigue con su mora), sin saldo, con la cobranza inactiva del crédito (`Escenario.cobranzaActiva`, la exoneración del director financiero) ni en una semana sin pago que no causa mora (P-16 en `false`). El abono extra del mismo periodo no cuenta para decidirlo (se aplica después de la cuota). Sí se causa en la semana de una dación o una retoma sin pago (REC-8: esa semana cuenta como vencida), y la recuperación lo extingue con lo demás.
- **Qué es.** Es cobranza como la de siempre: va en `cobranzaCausada`, se paga **primero** en la prelación (cobranza → mora → interés → servicios → capital), entra al saldo en mora del cociente (`TOTAL_PENDIENTE`) y se contabiliza con el mismo asiento (ingreso no operacional, R4-COBRANZA-CONTABLE).
- **Reemplaza a la anterior (supuesto, R15-COBRANZA-REEMPLAZA).** Mientras la opción sea mayor que 0, `Causador.cobranza` devuelve 0: no se causan los 40.000 por periodo con cociente ≥ 2 (`cobranzaPorPeriodo`, `umbralCocienteCobranza`) ni los 3.000 diarios (`cobranzaDiaria`). Las claves anteriores siguen en `config/reglas-negocio.properties`: para volver a la cobranza anterior basta poner `cobranzaPorCuotaAtrasada=0` y subir la versión de reglas. Nunca se suman los dos cobros.
- **Temporal (R15-COBRANZA-FIN).** `VigenciasCobranza` guarda un solo valor diario por fecha de originación (D26, D44) y el motor solo conoce fechas cuando el escenario trae calendario, así que no sirve para un «desde/hasta» por periodo. Se deja la opción simple: la política se enciende y se apaga cambiando la clave (cada cambio es una versión nueva de reglas y los créditos guardan la versión con que se originaron, ADR 0004). Si Diego da una fecha de fin o quiere que aplique solo a créditos nuevos, se modela entonces.
- **La marca no cambia.** «Listo para recuperar» sigue siendo el cociente saldo en mora / cuota total ≥ 2 al cerrar el periodo (D77, D85). Como el saldo en mora incluye la cobranza pendiente, las cifras de la marca pueden cambiar con el valor nuevo (por ejemplo, en la cola de un crédito en mora en que la regla anterior seguía cobrando 40.000 por periodo), pero la regla es la misma.

Implementación: `Causador.conCobranzaPorCuotaAtrasada` (lo llama `MotorCartera` después de calcular la cuota total del periodo y antes del reparto) y `Causacion.conCobranza`.

### 2. Base 360 (D89)

Opción `ReglasNegocio.baseDiasAnio` (`365` = el Excel y el motor hasta hoy; NEGOCIO = `360`). `Tasas.desdeEA(ea, periodicidad, base)`:

| | Base 365 (EXCEL) | Base 360 (NEGOCIO, supuesto R15-BASE-360) |
|---|---|---|
| Tasa periódica | (1 + EA)^(1/ppa) − 1 | (1 + EA)^(días/360) − 1, días = 7, 15 o 30 |
| Semanal | ^(1/52) | ^(7/360) (sube) |
| Quincenal | ^(1/24) | ^(15/360) = ^(1/24): igual |
| Mensual | ^(1/12) | ^(30/360) = ^(1/12): igual |
| Tasa diaria | (1 + EA)^(1/365) − 1 | (1 + EA)^(1/360) − 1 |
| Mensual equivalente | ^(1/12) | ^(1/12) |

Con 360, siete días de la tasa diaria dan exactamente la semanal y treinta la mensual: el periodo nominal (7, 15, 30) y la tasa del periodo son coherentes.

Revisión de cada 365 y 52 de `src/main`:

| Lugar | Qué es | ¿Cambia con la base? |
|---|---|---|
| `Tasas.desdeEA` (periódica y diaria) | conversión de la EA | **Sí**: es el cálculo de la cuota |
| `EstadoCorrida` (cuota fija inicial, PMT) y `MotorCartera.cambiarTasa` (casos 2 y 10, usura) | cuota del plan | **Sí**: usan la tasa de la base del crédito |
| `Causador.prorratear` (primera cuota por días), `Causador.mora` (días de atraso), `Causador.factorMoraDelPeriodo` (mensual con días reales), `Abonos` (interés del prepago por días) | tasa diaria × días | **Sí**: usan la diaria de la base (360 en NEGOCIO); los días siguen siendo los reales |
| `ServicioCartera` (`tasaPeriodica` del resumen de la simulación) | lo que muestra el API | **Sí** |
| `Causador.fraccionPrimerPeriodo`, `diasParaCobranza`, cobranza por días del cociente | días nominales 7/15/30 | Ya usaban 7/15/30 (no 365/ppa): sin cambio |
| `Periodicidad.periodosPorAnio` (52/24/12) en `Calendario` (mes de cada cuota, giro mensual a terceros), `cuotasEnMeses` (plazo 24 meses = 104 semanas), `periodosDesdeSemanas` (plazos límite del Excel) | calendario y plazos | No: no son cálculo de cuotas |
| Servicios: valor mensual × 12 / ppa (fondo, asistencia, seguros) | periodización de servicios de terceros | No: Diego habló de la cuota (interés); se pregunta en R15-BASE-360 |
| `ValidadorInstrucciones.MAX_DIAS = 3650` | tope de validación | No |

Cuotas antes y después (cuota financiera, sin servicios):

| Crédito | Base 365 | Base 360 |
|---|---|---|
| Operación del Excel: 5.904.400, EA 87,62 %, 104 semanas | 100.406,27 | 100.972,38 |
| Cuota total NEGOCIO de esa operación (con servicios, al peso) | 168.305 | 168.871 |
| Ejemplo: 5.533.760, EA 88,44 %, 88 semanas | 103.137,22 | 103.651,56 |
| Quincenal y mensual (cualquier operación) | sin cambio | sin cambio |

En las dos bases la amortización cierra en 0 en la cuota N (`RespuestasDiegoRonda15Test.Base360`).

## Consecuencias

- Versión de reglas nueva `negocio-2026-11-06` (`PerfilesReglas.NEGOCIO_VIGENTE_DESDE`). Las dos claves nuevas agregan dos líneas al contenido canónico de **todas** las versiones, así que cambian las huellas de `excel-2026-10` y de NEGOCIO (como en la ronda 12); el comportamiento de EXCEL es idéntico (casos 1 a 12 contra el Excel en verde).
- Cambian las cifras semanales de NEGOCIO (cuota, interés, servicios no) y la cobranza de NEGOCIO; se actualizaron las pruebas de rondas anteriores que dependían de ellas (con `conBaseDiasAnio(365)` y `conCobranzaPorCuotaAtrasada(0)` cuando documentan la regla anterior), la caracterización y `docs/api-ejemplos`.
- El cargo se decide con tolerancia de medio centavo: con `redondeoCuotaAlPeso=true` (NEGOCIO) la cuota exigible es entera y un pago en pesos enteros la cubre; sin redondeo, un pago que no alcanza la cuota por centavos causaría el cargo (`HallazgosAuditoriaTest` documenta H2 con la regla anterior).
- La auditoría por propiedades (`PropiedadesFinancierasAuditTest`) sortea las dos claves y verifica I14 (la cobranza es 0 o exactamente el cargo) e I16 (una cuota pagada a tiempo nunca causa el cargo; una sin pago dentro del plan, siempre).
- `/api/v1/reglas` publica las dos claves (contrato: `consola/src/demo/fixtures/reglas.json`).
- Preguntas de la ronda 15: R15-COBRANZA-CUOTA, R15-COBRANZA-REEMPLAZA, R15-COBRANZA-FIN, R15-BASE-360; siguen abiertas R14-SERVICIOS-CIERRE y R14-MARCA-SEMANAS (esta con el ejemplo detallado que pidió Diego).
