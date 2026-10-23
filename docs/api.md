# API REST del motor de cartera (v1)

Módulo de Spring Modulith `co.financiera.cartera.api`. Expone el motor (`nucleo`), la contabilidad (`contabilidad`) y los escenarios del Excel (`simulaciones`) por HTTP. Depende de esos tres módulos y ningún módulo depende de él (`ModularidadTest`).

```bash
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.10.7-hotspot"
./mvnw spring-boot:run                     # http://localhost:8080
curl localhost:8080/api/v1/catalogo
```

## Convenciones

- Base `/api/v1`, JSON en camelCase.
- **Dinero y tasas como string decimal**: `BigDecimal.stripTrailingZeros().toPlainString()`, sin notación científica y **sin redondear**. El motor trabaja con DECIMAL128 (D4), así que una cuota sale como `"219092.9355960436057213056188035084"`; el front redondea al mostrar (Diego, P-05: 0 decimales en pantalla). El cero va como `"0"`.
- En los requests los decimales también van como string (`"5100000"`); Jackson acepta además un número JSON.
- Perfiles de reglas (`perfilReglas`):
  - `EXCEL` = `ReglasNegocio.EXCEL`, el comportamiento del Excel de Diego del 11 de octubre.
  - `NEGOCIO` = `ReglasNegocio.desde(config/reglas-negocio.properties)`, las respuestas de negocio. Ruta configurable con `cartera.reglas.archivo`; si el archivo no existe, NEGOCIO queda igual a EXCEL.
  - Por defecto: `NEGOCIO` en `/simulaciones` y `/escenarios/ejecutar`; `EXCEL` en `/casos/{numero}`, porque de ahí salen los casos 1 a 12.
- CORS: `http://localhost:5173` y `https://juanc101195.github.io` (propiedad `cartera.api.cors.origenes`), métodos GET, POST y OPTIONS, cabeceras `Content-Type` y `X-API-Key`.
- Cabeceras de seguridad en toda respuesta: `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` y, en `/api/**`, `Cache-Control: no-store`. Las respuestas JSON de más de 1 KB se comprimen (gzip) si el cliente manda `Accept-Encoding`.
- **Autenticación opcional** (`X-API-Key`): apagada por defecto. Con `cartera.api.claves` (variable `CARTERA_API_CLAVES`) configurada, toda petición salvo `OPTIONS` y `/actuator/health` necesita la cabecera. Roles `CONSUMIDOR` (todo `/api/v1/**`) y `LECTURA` (solo `GET` de `/catalogo`, `/reglas` y `/casos*`). Cómo activarla: `docs/despliegue.md`.
- **Límites** (todos responden 400 `VALIDACION` con `detalle.campo`, antes de calcular, salvo el del cuerpo):
  - Números: máximo 30 dígitos y escala entre -20 y 20 (así `"1e999999999"` se rechaza); dinero entre 0 y 10.000.000.000 (`valorMoto` mayor que 0); tasas (`nuevaTasaEA`, `usuraEA`) en (0, 5]; `fraccion` entre 0 y 100.
  - Fechas del crédito entre los años 2000 y 2100.
  - Escenarios: máximo 500 instrucciones por petición; `plazoLimitePeriodos` hasta 208 (el doble de las cuotas máximas del catálogo, 104); `diasAtraso` y `diasDesdeUltimaCuota` entre 0 y 3.650.
  - Cuerpo de la petición: máximo 256 KB (`cartera.api.cuerpo-maximo-bytes`); más responde 413.
- Errores: `{"codigo": "...", "mensaje": "...", "detalle": {...}}`.

| HTTP | `codigo` | Cuándo |
|---|---|---|
| 400 | `ABONO_MAYOR_QUE_SALDO` | Un abono extra supera el saldo de capital (P-14, D12). `detalle`: `periodo`, `abono`, `saldo`. |
| 400 | `PLAZO_MAXIMO` | Más de 24 meses o más cuotas que el máximo de la periodicidad (104 / 48 / 24). `detalle`: `maximoMeses`, `maximoCuotas`, `periodicidad`. |
| 400 | `VALIDACION` | Campo faltante o mal formado, enum desconocido (`detalle.valoresValidos`), JSON inválido o validación del núcleo (pago inicial negativo, etc.). También fechas del crédito incoherentes (ver "Fechas y prorrateo de la primera cuota"). `detalle.campo` cuando se sabe. |
| 401 | `NO_AUTORIZADO` | Solo con autenticación activada: falta `X-API-Key` o no es válida. |
| 403 | `PROHIBIDO` | Solo con autenticación activada: la clave es de rol `LECTURA` y el recurso o el método no está permitido. |
| 404 | `CASO_INEXISTENTE` | `/casos/{numero}` fuera de 1 a 13. |
| 413 | `CUERPO_DEMASIADO_GRANDE` | El cuerpo supera 256 KB. |
| 500 | `ERROR_INTERNO` | Cualquier excepción no prevista. `detalle.idCorrelacion` es un UUID que también queda en el log del servidor; el mensaje no revela el error interno. |
| 500 | `ASIENTO_DESCUADRADO` | Error interno: el `Contabilizador` generó un asiento descuadrado (HT-06). No depende del request. |

## Endpoints

### GET /api/v1/catalogo

Líneas, periodicidades, tipos de instrucción, enums y valores por defecto de la hoja Inputs.

```bash
curl localhost:8080/api/v1/catalogo
```

```json
{
  "lineas": [{"codigo": "CPPU", "nombre": "Crédito popular productivo urbano", "tasaEA": "0.8762", "plazoMaximoMeses": 24,
              "periodicidades": ["SEMANAL", "QUINCENAL", "MENSUAL"]}],
  "periodicidades": [{"codigo": "QUINCENAL", "periodosPorAnio": 24, "adjetivo": "quincenal", "porConfirmar": false,
                      "diasNominales": 15, "unidadCuota": "cuotas quincenales", "cuotasMaximas": 48}],
  "tiposInstruccion": ["PAGO_CONTRACTUAL", "PAGO_PARCIAL", "PAGO_VALOR", "SIN_PAGO", "ABONO_EXTRA", "PREPAGO_TOTAL",
                       "USURA_VIGENTE", "CAMBIO_TASA", "PAGO_AL_DIA", "CIERRE_POR_RECUPERACION"],
  "modalidadesAbono": ["MENOR_PLAZO", "MENOR_CUOTA"],
  "modalidadesCambioTasa": ["MISMA_FECHA_FINAL", "MISMA_CUOTA"],
  "tiposRecuperacion": ["DACION_EN_PAGO", "RETOMA"],
  "perfilesReglas": ["EXCEL", "NEGOCIO"],
  "valoresPorDefecto": {"linea": "CPPU", "periodicidad": "SEMANAL", "cuotas": "104", "plazoMaximoMeses": "24",
                        "valorMoto": "5100000", "matriculaRunt": "171100", "soat": "373300", "prenda": "120000",
                        "alistamiento": "664400", "gpsPlataforma": "80000", "gastosComerciales": "60000",
                        "pagoInicial": "0", "valorTotal": "5904400", "montoFinanciado": "5904400", "...": "..."}
}
```

`plazoMaximoMeses` es el de la línea: 24 meses en todas, incluida ORDINARIO, que antes decía 36 (R4-PLAZO-LINEA, ronda 4: el tope es un parámetro del catálogo de líneas y puede crecer). `cuotasMaximas` lo convierte a cuotas de cada periodicidad. `porConfirmar` es `false` en todas desde que Diego confirmó que la quincena son 24 periodos por año (R4-QUIN); el campo se conserva para no romper a quien lo lee. Respuesta completa: [`api-ejemplos/catalogo.json`](api-ejemplos/catalogo.json).

### GET /api/v1/reglas

```bash
curl localhost:8080/api/v1/reglas
```

```json
{
  "perfiles": {
    "EXCEL":   {"prelacion": "SERVICIOS_ANTES_DE_CAPITAL", "aplicacionAbono": "SOLO_CAPITAL", "modalidadUsura": "MISMA_FECHA_FINAL", "topeCobranzaPorPago": null, "...": "..."},
    "NEGOCIO": {"prelacion": "SERVICIOS_ANTES_DE_CAPITAL", "aplicacionAbono": "PRELACION_COMPLETA", "modalidadUsura": "MISMA_CUOTA", "margenUsura": "1",
                "costoRecuperacionAlFondo": "true", "cobranzaIngresoNoOperacional": "true", "plazoLimiteMeses": "24", "...": "..."}
  },
  "claves": [{"clave": "prelacion", "descripcion": "Orden de aplicación de un pago", "fuente": "P1 / D16"}],
  "archivoNegocio": "config/reglas-negocio.properties"
}
```

Las claves son las 17 de `ReglasNegocio.CLAVES`, en el orden del record. `topeCobranzaPorPago` es `null` cuando no hay tope y `plazoLimiteMeses` es `null` en EXCEL (queda el del producto). Lo que agrega la ronda 4 (2026-10-21):

| Clave | Qué hace | NEGOCIO | EXCEL |
|---|---|---|---|
| `aplicacionAbono` = `PRELACION_COMPLETA` | R4-CASO13: después de lo vencido, el sobrante del abono vuelve a pasar por la prelación sobre lo causado del periodo corriente (ver `reglas/caso-13.md`) | `PRELACION_COMPLETA` | `SOLO_CAPITAL` |
| `costoRecuperacionAlFondo` | R4-R2 (provisional, P5-COSTO-DACION): en la dación el costo de la recuperación se suma al faltante que cubre el fondo | `true` | `true` (sin efecto: el Excel no tiene costo) |
| `cobranzaIngresoNoOperacional` | R4-COBRANZA-CONTABLE: la cobranza causada se acredita a `INGRESOS_NO_OPERACIONALES_COBRANZA` (PUC clase 42) | `true` | `false` (`INGRESOS_COBRANZA`, como el Excel) |
| `plazoLimiteMeses` | R4-PLAZO-LIMITE: plazo límite de los créditos nuevos y las simulaciones, en meses convertidos a periodos | `24` | `null` |

Lo que agrega el documento "rptas software" de Diego (2026-10-22):

| Clave | Qué hace | NEGOCIO | EXCEL |
|---|---|---|---|
| `cambioTasaSobreCapitalVigente` | R4-T5: con el cliente en mora, la cuota nueva de un cambio de tasa (o de usura con `MISMA_FECHA_FINAL`) va solo sobre el capital vigente; lo vencido se cobra aparte y no se capitalizan intereses | `true` | `false` |
| `cobranzaDiaria` | COB-6: valor diario de la cobranza para los créditos nuevos y las simulaciones; queda fijo en los parámetros del crédito (3.000 es un ejemplo, provisional) | `3000` | `null` (el del producto: 3.000) |
| `cuotasVencidasParaRecuperacion` | PPER-9: cuotas atrasadas desde las que el crédito queda `listoParaRecuperacion` (solo se marca; el motor no ejecuta la dación ni la retoma, R6-RECUP-AUTO) | `2` | `null` (no se marca) |

### POST /api/v1/simulaciones

Plan de pagos de un cliente que paga siempre a tiempo.

```bash
curl -X POST localhost:8080/api/v1/simulaciones -H 'Content-Type: application/json' \
  -d '{"linea":"CPPU","periodicidad":"QUINCENAL","cuotas":48,"meses":null,"valorMoto":"5100000","pagoInicial":"0","perfilReglas":"NEGOCIO"}'
```

Request (`CreditoRequest`): se manda `cuotas` **o** `meses` (con meses, cuotas = ROUNDDOWN(meses × periodos por año / 12)). Si llegan los dos deben coincidir. Opcionales: `linea` (CPPU), `periodicidad` (SEMANAL), `valorMoto`, `pagoInicial` (0), `matriculaRunt`, `soat`, `prenda`, `gpsPlataforma`, `gastosComerciales` (los de Inputs), `plazoLimitePeriodos` (horizonte de proyección con saldo pendiente) y `cobranzaActiva` (COB-6: si al crédito se le causan gastos de cobranza; `true` por defecto, como el Excel; en `false` un pago tardío causa mora pero no cobranza) y `fechaDesembolso` + `primerVencimiento` (juntas; ver "Fechas y prorrateo de la primera cuota" abajo). Con el perfil NEGOCIO el valor diario de la cobranza sale de la clave `cobranzaDiaria`. Sin `plazoLimitePeriodos`, con el perfil NEGOCIO el límite son 24 meses en periodos del crédito (104 semanas, 48 quincenas, 24 meses; R4-PLAZO-LIMITE) y con EXCEL las 120 semanas del Excel convertidas (120 / 55 / 27). Eso vale también para `/escenarios/ejecutar`: con NEGOCIO una instrucción en la semana 110 de un crédito de 104 cuotas da 400, salvo que el request traiga `plazoLimitePeriodos`.

```json
{"resumen": {"linea": "CPPU", "perfilReglas": "NEGOCIO", "montoFinanciado": "5904400", "tasaEA": "0.8762",
             "tasaPeriodo": "0.0265654190161528", "cuotas": 48,
             "cuotaFinanciera": "219092.9355960436057213056188035084", "serviciosPorCuota": "136574.04",
             "cuotaTotal": "355666.9755960436057213056188035084", "totalPagado": "17072014.82861009307462266970256842",
             "totalIntereses": "4612060.908610093074622669702568407", "totalServicios": "6555553.92",
             "periodicidad": "QUINCENAL", "porConfirmar": []},
 "plan": [{"periodo": 1, "mes": 1, "saldoInicial": "5904400", "interes": "156852.86003897259232",
           "capital": "62240.0755570710134013056188035084", "cuotaFinanciera": "219092.9355960436057213056188035084",
           "fondoGarantias": "105393.54", "asistencia": "23800", "seguro": "7380.5",
           "cuotaTotal": "355666.9755960436057213056188035084", "saldo": "5842159.924442928986598694381196492"}]}
```

Al peso da 219.093 / 355.667 / 17.072.015, como [`reglas/planes-ejemplo-periodicidad.md`](reglas/planes-ejemplo-periodicidad.md). Respuesta completa: [`api-ejemplos/simulacion-quincenal.json`](api-ejemplos/simulacion-quincenal.json).

```bash
# 25 meses → 400 PLAZO_MAXIMO
curl -X POST localhost:8080/api/v1/simulaciones -H 'Content-Type: application/json' -d '{"periodicidad":"MENSUAL","meses":25}'
```

#### Fechas y prorrateo de la primera cuota (P5-PRORRATEO, D48)

`CreditoRequest` acepta dos campos opcionales, **juntos o ninguno**: `fechaDesembolso` y `primerVencimiento`, en ISO `yyyy-MM-dd`. Valen en `/simulaciones`, en el `credito` de `/escenarios/ejecutar` y como parámetros de `/casos/{numero}`. Con ellos el API arma el `CalendarioPagos` del crédito y se lo pasa al motor (`Escenario.conCalendario`); si el perfil tiene `prorrateoPrimeraCuota = true` (NEGOCIO), el **interés** del periodo 1 se cobra por los días reales entre las dos fechas con la tasa diaria: saldo × ((1 + tasa diaria)^días − 1). El capital y los servicios de esa cuota van completos y desde el periodo 2 todo sigue igual. Con EXCEL las fechas se aceptan y se validan, pero no cambian nada.

**Sin las fechas la respuesta es idéntica a la de antes**: no aparece ningún campo nuevo (los ejemplos `catalogo.json`, `simulacion-quincenal.json` y `caso-13-negocio.json` no cambian).

Validación (400 `VALIDACION`, `detalle.campo` = el campo culpable):

| Regla | Ejemplo de mensaje |
|---|---|
| Van juntas | `Las fechas van juntas: falta primerVencimiento (mande fechaDesembolso y primerVencimiento, o ninguna de las dos)` |
| Formato ISO | `fechaDesembolso debe ser una fecha con formato yyyy-MM-dd (por ejemplo 2026-11-03); llegó "23/10/2026"` |
| Vencimiento después del desembolso | `El primer vencimiento (2026-12-05) debe ser posterior a la fecha de desembolso (2026-12-10)` |
| Semanal: martes (D28) | `En un crédito semanal la cuota vence los martes; el 2026-11-04 es miércoles` |
| Quincenal: 5 o 20 (D27) | `En un crédito quincenal la cuota vence el 5 y el 20 de cada mes; el 2026-11-15 no es ninguno de los dos` |
| Mensual: día 5 (D35) | `En un crédito mensual la cuota vence el día 5 de cada mes; el 2026-11-06 no lo es` |

No hay tope a los días del primer periodo (Diego no lo ha fijado): un primer vencimiento lejano da un interés alto, no un error. El API nunca usa la fecha de hoy: las dos fechas las manda quien llama.

Con fechas, el `resumen` de `/simulaciones` y de `/escenarios/ejecutar` (y de `/casos/{numero}`) trae `prorrateoPrimeraCuota`:

```bash
curl -X POST localhost:8080/api/v1/simulaciones -H 'Content-Type: application/json' -d '{"linea":"CPPU","periodicidad":"MENSUAL","cuotas":24,
  "valorMoto":"5100000","pagoInicial":"0","perfilReglas":"NEGOCIO","fechaDesembolso":"2026-10-23","primerVencimiento":"2026-12-05"}'
```

```json
{"resumen": {"cuotaFinanciera": "444006.1668296752041287889940113958", "cuotaTotal": "717154.2468296752041287889940113958", "...": "...",
             "prorrateoPrimeraCuota": {"fechaDesembolso": "2026-10-23", "primerVencimiento": "2026-12-05",
                                       "diasPrimerPeriodo": 43, "diasNominales": 30, "aplicado": true,
                                       "interesSinProrrateo": "317872.58202876262136",
                                       "interesProrrateado": "454328.7516096464392",
                                       "diferencia": "136456.16958088381784"}},
 "plan": [{"periodo": 1, "interes": "454328.7516096464392", "capital": "126133.5848009125827687889940113958",
           "cuotaFinanciera": "580462.3364105590219687889940113958", "...": "..."}]}
```

| Campo | Qué es |
|---|---|
| `diasPrimerPeriodo` | Días reales del desembolso al primer vencimiento (`CalendarioPagos.diasDelPeriodo(1)`). |
| `diasNominales` | Días nominales de la periodicidad (7, 15 o 30), como referencia. |
| `aplicado` | Si el perfil prorratea (`prorrateoPrimeraCuota`): `true` con NEGOCIO, `false` con EXCEL. |
| `interesSinProrrateo` | Interés de un periodo completo: saldo inicial × tasa del periodo (lo que cobraría sin fechas). |
| `interesProrrateado` | Interés que causó el motor en el periodo 1 (igual a `plan[0].interes` y a `movimientos[1].interesCorriente`). |
| `diferencia` | `interesProrrateado − interesSinProrrateo`: positiva si el primer periodo es más largo que uno normal, negativa si es más corto, 0 si no se aplicó. |

Con fechas, `resumen.cuotaFinanciera`, `serviciosPorCuota` y `cuotaTotal` de `/simulaciones` son los de una **cuota regular** (la del periodo 2); la primera, prorrateada, está en `plan[0]`. `totalPagado` y `totalIntereses` ya incluyen el prorrateo. Ejemplos completos: [`api-ejemplos/simulacion-mensual-prorrateo.json`](api-ejemplos/simulacion-mensual-prorrateo.json) y [`api-ejemplos/escenario-semanal-prorrateo.json`](api-ejemplos/escenario-semanal-prorrateo.json).

### POST /api/v1/escenarios/ejecutar

Corre el motor con instrucciones por periodo; los periodos sin instrucción son pagos a tiempo.

```bash
curl -X POST localhost:8080/api/v1/escenarios/ejecutar -H 'Content-Type: application/json' -d '{
  "credito": {"linea":"CPPU","periodicidad":"SEMANAL","cuotas":104,"valorMoto":"5100000","pagoInicial":"0"},
  "perfilReglas": "NEGOCIO",
  "instrucciones": [{"periodo":40,"tipo":"SIN_PAGO","causaMoraYServicios":true},
                    {"periodo":41,"tipo":"PAGO_VALOR","valor":"5000","diasAtraso":3},
                    {"periodo":42,"tipo":"ABONO_EXTRA","valor":"1500000","modalidad":"MENOR_PLAZO"},
                    {"periodo":43,"tipo":"USURA_VIGENTE","usuraEA":"0.81"}],
  "incluirAsientos": true}'
```

`perfilReglas` va arriba o dentro de `credito` (gana el de arriba). `incluirAsientos` es `true` por defecto (se mantiene así: la consola y varios clientes lo usan sin mandarlo); en `false`, `asientos` llega vacío. Un periodo puede llevar varias instrucciones: se aplican en el orden en que llegan (el caso 13 manda `SIN_PAGO` y `ABONO_EXTRA` en la semana 42).

Campos de cada instrucción (los de su record en `Instruccion`):

| `tipo` | Campos | Por defecto |
|---|---|---|
| `PAGO_CONTRACTUAL` | `diasAtraso` | 0 |
| `PAGO_PARCIAL` | `fraccion` (0.4 = 40 %), `diasAtraso` | días 0 |
| `PAGO_VALOR` | `valor`, `diasAtraso` | días 0 |
| `SIN_PAGO` | `causaMoraYServicios` | `true` |
| `ABONO_EXTRA` | `valor`, `modalidad` (MENOR_PLAZO / MENOR_CUOTA) | MENOR_PLAZO (P-13) |
| `PREPAGO_TOTAL` | `diasDesdeUltimaCuota` | 0 |
| `USURA_VIGENTE` | `usuraEA` | — |
| `CAMBIO_TASA` | `nuevaTasaEA`, `modalidad` (MISMA_FECHA_FINAL / MISMA_CUOTA) | MISMA_FECHA_FINAL |
| `PAGO_AL_DIA` | — | — |
| `CIERRE_POR_RECUPERACION` | `tipoRecuperacion` (DACION_EN_PAGO / RETOMA), `valorMoto`, `costoRecuperacion` (opcional: costo real de la recuperación con soporte, R4-R2) | `costoRecuperacion` 0 |

Response:

```json
{"resumen": {"periodoFinal": "...", "estadoFinal": "CANCELADO", "saldoFinal": "0", "totalPagadoCliente": "...",
             "cuotasVencidasMax": "...", "montoFinanciado": "5904400", "perfilReglas": "NEGOCIO",
             "listoParaRecuperacion": false, "periodoListoParaRecuperacion": 41},
 "movimientos": [{"periodo": 42, "mes": 10, "estado": "VIGENTE_AL_DIA", "evento": "Cuota + abono extra",
                  "etiqueta": "Cuota + abono extra", "saldoInicial": "...", "serviciosCausados": {"fondoGarantias": "...", "asistencia": "...", "seguro": "...", "total": "..."},
                  "pendientesPagadosExtra": {"cobranza": "...", "mora": "...", "interesCorriente": "...", "servicios": {"...": "..."}, "capital": "...", "total": "..."},
                  "pagoTotalCliente": "...", "pagoExtraordinario": "...", "...": "todos los campos de Movimiento"}],
 "asientos": [{"periodo": 42, "evento": "Cuota + abono extra",
               "lineas": [{"cuenta": "CAJA", "nombreCuenta": "Caja", "debito": "...", "credito": "0", "explicacion": "Abono extraordinario recibido"}],
               "totalDebitos": "...", "totalCreditos": "...", "cuadra": true}]}
```

- `movimientos` empieza en el periodo 0 (desembolso, estado `DESEMBOLSADO`) y trae todos los campos de `Movimiento`; los `Servicios` y `DeudaExtinguida` van como objetos con su `total`. `etiqueta` es el `evento` del motor ("Pago normal", "Cuota + abono extra"…). Además vienen `pagoTotalCliente` y `pagoExtraordinario`.
- Cada movimiento trae además `costoRecuperacion` y `costoRecuperacionAsumido` (R4-R2): el costo descontado en la dación o la retoma y la parte que asume la financiera (0 con `costoRecuperacionAlFondo = true`).
- PPER-9: cada movimiento trae `listoParaRecuperacion` (boolean): el crédito llegó a `cuotasVencidasParaRecuperacion` cuotas atrasadas (2 con NEGOCIO) y sigue en mora. Es solo una marca: el motor no ejecuta la dación ni la retoma. El resumen trae `listoParaRecuperacion` (el del último periodo) y `periodoListoParaRecuperacion` (el primer periodo marcado; `null` si nunca llegó o el perfil no lo fija). Con EXCEL siempre `false` / `null`.
- `asientos` sale de `Contabilizador.para(reglas del perfil)`: con EXCEL es igual a `Contabilizador.estandar()`; con NEGOCIO la cobranza causada va a `INGRESOS_NO_OPERACIONALES_COBRANZA` (R4-COBRANZA-CONTABLE) y los cierres con costo acreditan `CXP_COSTOS_RECUPERACION` (y debitan `GASTO_COSTOS_RECUPERACION` si lo asume la financiera). `cuadra` = débitos y créditos iguales al centavo (las sumas DECIMAL128 difieren en residuos de 1e-28; el `Contabilizador` además rechaza cualquier asiento con más de 1 COP de diferencia).

```bash
# Abono mayor que el saldo → 400 ABONO_MAYOR_QUE_SALDO
curl -X POST localhost:8080/api/v1/escenarios/ejecutar -H 'Content-Type: application/json' \
  -d '{"credito":{"cuotas":104},"instrucciones":[{"periodo":10,"tipo":"ABONO_EXTRA","valor":"99000000"}]}'
```

### GET /api/v1/casos

```bash
curl localhost:8080/api/v1/casos
```

```json
[{"numero": 1, "nombre": "Operación normal", "descripcion": "Paga las 104 cuotas semanales a tiempo.", "fuente": "Excel 11-oct, hoja Caso1_Normal"},
 {"numero": 13, "nombre": "Abono extra con pendientes", "descripcion": "...", "fuente": "Propuesto por el equipo; regla de la ronda 4 (R4-CASO13); Diego debe validar las cifras nuevas (pregunta P5-CASO13). No está en el Excel"}]
```

Los casos 1 a 12 salen de `simulaciones.EscenariosExcel` (hojas Caso1 a Caso12) y el 13 de `EscenariosExcel.caso13` (el mismo escenario que `Caso13AbonoConPendientesTest`). Todos sobre la réplica del Excel: CPPU, semanal, 104 cuotas.

### GET /api/v1/casos/{numero}?perfilReglas=EXCEL|NEGOCIO

```bash
curl 'localhost:8080/api/v1/casos/1?perfilReglas=EXCEL'
curl 'localhost:8080/api/v1/casos/13?perfilReglas=NEGOCIO'
# Con fechas (opcionales, juntas): los casos son semanales, el primer vencimiento debe ser martes
curl 'localhost:8080/api/v1/casos/13?perfilReglas=NEGOCIO&fechaDesembolso=2026-10-23&primerVencimiento=2026-11-03'
```

Con `fechaDesembolso` y `primerVencimiento` el caso corre con su calendario (ver "Fechas y prorrateo de la primera cuota"): el `credito` devuelto las trae y el `resumen` trae `prorrateoPrimeraCuota`. Sin ellas el caso es el del Excel tal cual.

La respuesta de `/escenarios/ejecutar` (`resumen`, `movimientos`, `asientos`) más:

- `caso`: número, nombre, descripción y fuente.
- `perfilReglas`: el perfil con que corrió.
- `credito`: el crédito del caso en formato de request (incluye `plazoLimitePeriodos`: en el caso 8, 100 con EXCEL y 104 con NEGOCIO por X4, 24 meses de `plazoLimiteMeses`; 120 en el 9 con los dos perfiles; y `cobranzaActiva`, `true`).
- `instrucciones`: las del caso en formato de request, con todos los campos de su tipo explícitos. Las instrucciones "desde el periodo N" (caso 9: pago con 14 días de atraso desde el mes 10) llegan expandidas, una por periodo.

Mandar `credito` + `instrucciones` a `/escenarios/ejecutar` con el mismo perfil da el mismo resultado (`CarteraControllerTest.lasInstruccionesDelCasoSeReejecutanIgual`). Ejemplo completo: [`api-ejemplos/caso-13-negocio.json`](api-ejemplos/caso-13-negocio.json).

### POST /api/v1/pagos/clasificar

ABN-4 (`ClasificacionPago`): divide un valor recibido en pago corriente y abono extra.

```bash
curl -X POST localhost:8080/api/v1/pagos/clasificar -H 'Content-Type: application/json' \
  -d '{"valorRecibido":"400000","valorAdeudado":"355667"}'
# {"pagoCorriente":"355667","abonoExtra":"44333"}
```

## Ejemplos para el front (`docs/api-ejemplos/`)

Respuestas reales del API (no se editan a mano). `EjemplosApiTest` las compara en cada `./mvnw test` con lo que responde el API y falla si difieren, sin modificar archivos; tras un cambio intencional se regeneran con `./mvnw test -Dtest=EjemplosApiTest -Dregenerar=true` y se revisa el diff. `ContratoApiTest` y `consola/src/contrato.test.ts` comprueban que sus claves coincidan con los DTOs de Java y con `consola/src/tipos.ts`:

| Archivo | Petición |
|---|---|
| `catalogo.json` | `GET /api/v1/catalogo` |
| `simulacion-quincenal.json` | `POST /api/v1/simulaciones` con CPPU, quincenal, 48 cuotas, NEGOCIO |
| `caso-13-negocio.json` | `GET /api/v1/casos/13?perfilReglas=NEGOCIO` |
| `simulacion-mensual-prorrateo.json` | `POST /api/v1/simulaciones` con CPPU, mensual, 24 cuotas, NEGOCIO, desembolso 2026-10-23 y primer vencimiento 2026-12-05 (43 días) |
| `escenario-semanal-prorrateo.json` | `POST /api/v1/escenarios/ejecutar` con CPPU, semanal, 104 cuotas, NEGOCIO, sin instrucciones ni asientos, desembolso 2026-10-23 y primer vencimiento 2026-11-03 (11 días) |

## Devolverle a Diego la hoja Validaciones llena (HU-17b)

`python -I herramientas/llenar_validaciones.py` pide `GET /casos/{n}?perfilReglas=EXCEL` para los casos 1 a 12 (API local en `http://localhost:8080/api/v1`; `--api` usa el de Azure y `--url` cualquier otro) y llena la columna F "Resultado software" de las 45 pruebas de la hoja Validaciones (eran 33 hasta que el Excel agregó los casos 10 a 12). A la derecha agrega K "Valor Excel" (fórmula a la celda de la hoja del caso que tiene el valor esperado), L "Diferencia", M "Resultado (OK / FALLA)" contra la tolerancia de la columna G (1 COP en dinero, 0,000001 en tasas, exacto en conteos y estados) y N con el origen de cada lado (campo del API ↔ celda). Una prueba que no se pueda mapear con certeza queda con F vacía, M = "SIN MAPEO" y el motivo en J. Escribe un archivo nuevo en `--salida` (por defecto `<temporal del sistema>/motor-cartera-validaciones`), nunca sobre el original, con el resto del libro intacto; openpyxl no guarda los valores calculados, así que el libro queda marcado para recalcular al abrirlo en Excel. `--perfil NEGOCIO` corre los casos con las reglas de negocio. Sale con código 1 si alguna prueba no da OK. El mapeo de cada prueba está en `MAPEO` y sus pruebas (sin red, con `api-ejemplos/caso-13-negocio.json`) en `herramientas/test_llenar_validaciones.py`. Resultado del 2026-10-23: 45 de 45 OK con el API local y con el de Azure, en los dos perfiles.

## Desviaciones del contrato (`contrato-api-cartera.md`)

1. **`tiposRecuperacion`** es `["DACION_EN_PAGO", "RETOMA"]`, no `["DACION", "RETOMA"]`: son los nombres reales de `Instruccion.TipoRecuperacion`, como pide el contrato.
2. **Instrucción `CIERRE_POR_RECUPERACION`**: su record tiene un campo `tipo` (dación o retoma) que choca con el `tipo` de la instrucción. Va en **`tipoRecuperacion`**; `valorMoto` se llama igual que en el record.
3. **`modalidad`** es un solo campo para `ABONO_EXTRA` (ModalidadAbono) y `CAMBIO_TASA` (ModalidadCambioTasa), porque los dos records lo llaman igual.
4. **Dinero sin redondear**: el contrato muestra `"219093.00"`; el API manda el valor completo del motor (`"219092.9355960436…"`) para no perder precisión, sin ceros sobrantes (`"5904400"`, `"0"`). Las tasas igual (`"0.0265654190161528"`, no `"0.0265"`).
5. **`cuadra`** compara débitos y créditos **al centavo**, no con `==` exacto: con DECIMAL128 las sumas de un asiento cuadrado difieren en residuos de 1e-28 y la igualdad exacta daba `false` en asientos correctos.
6. **Campos agregados** (no rompen el contrato): `catalogo.perfilesReglas`, `lineas[].periodicidades`, `periodicidades[].diasNominales/unidadCuota/cuotasMaximas`; `reglas.archivoNegocio`; `simulacion.resumen.linea/perfilReglas/serviciosPorCuota` y `plan[].mes/saldoInicial/cuotaFinanciera`; `ejecucion.resumen.montoFinanciado/perfilReglas`; en los movimientos `evento` (además de `etiqueta`), `pagoTotalCliente` y `pagoExtraordinario`; en los asientos `totalDebitos`, `totalCreditos` y en cada línea `nombreCuenta` y `explicacion`; en `/casos/{numero}` `perfilReglas` y `credito`; en `CreditoRequest` los costos de la operación, `plazoLimitePeriodos` y `cobranzaActiva` (COB-6); en los movimientos `listoParaRecuperacion` y en `ejecucion.resumen` `listoParaRecuperacion` y `periodoListoParaRecuperacion` (PPER-9); en `CreditoRequest` (y como parámetros de `/casos/{numero}`) `fechaDesembolso` y `primerVencimiento`, y en `simulacion.resumen` y `ejecucion.resumen` `prorrateoPrimeraCuota`, que solo aparece si el crédito trae fechas (P5-PRORRATEO).
7. **Perfil por defecto**: `NEGOCIO` en simulaciones y escenarios, `EXCEL` en `/casos/{numero}` (el contrato no lo fija).
8. **Error 500 `ASIENTO_DESCUADRADO`**: el contrato solo habla de 400 y 404; este es un error interno.

## Cambios en el núcleo que trajo el API

- `EscenariosExcel` ahora tiene los 13 casos: se agregaron `caso2`, `caso4`, `caso5`, `caso7` (antes solo estaban en las pruebas) y `caso13` / `caso13ConPendientes` (movido de `Caso13AbonoConPendientesTest`, que ahora lo referencia).
- `RecaudoConVencidos` no acreditaba la cobranza pendiente que paga una cuota a tiempo después de un pago tardío (semana 42 del ejemplo del contrato sin `SIN_PAGO`): el asiento se descuadraba en 4.000 y el `Contabilizador` lanzaba excepción. Ahora acredita `CXC_GASTOS_COBRANZA` por `pagoCobranza` (prueba en `RecaudoConVencidosTest`).
