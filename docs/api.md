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
- CORS: `http://localhost:5173` y `https://juanc101195.github.io` (propiedad `cartera.api.cors.origenes`), métodos GET, POST y OPTIONS.
- Errores: `{"codigo": "...", "mensaje": "...", "detalle": {...}}`.

| HTTP | `codigo` | Cuándo |
|---|---|---|
| 400 | `ABONO_MAYOR_QUE_SALDO` | Un abono extra supera el saldo de capital (P-14, D12). `detalle`: `periodo`, `abono`, `saldo`. |
| 400 | `PLAZO_MAXIMO` | Más de 24 meses o más cuotas que el máximo de la periodicidad (104 / 48 / 24). `detalle`: `maximoMeses`, `maximoCuotas`, `periodicidad`. |
| 400 | `VALIDACION` | Campo faltante o mal formado, enum desconocido (`detalle.valoresValidos`), JSON inválido o validación del núcleo (pago inicial negativo, etc.). `detalle.campo` cuando se sabe. |
| 404 | `CASO_INEXISTENTE` | `/casos/{numero}` fuera de 1 a 13. |
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
  "periodicidades": [{"codigo": "QUINCENAL", "periodosPorAnio": 24, "adjetivo": "quincenal", "porConfirmar": true,
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

`plazoMaximoMeses` es el de la línea (ORDINARIO dice 36), pero ningún crédito pasa de 24 meses (PPER-2): `cuotasMaximas` ya lo aplica. Respuesta completa: [`api-ejemplos/catalogo.json`](api-ejemplos/catalogo.json).

### GET /api/v1/reglas

```bash
curl localhost:8080/api/v1/reglas
```

```json
{
  "perfiles": {
    "EXCEL":   {"prelacion": "SERVICIOS_ANTES_DE_CAPITAL", "aplicacionAbono": "SOLO_CAPITAL", "modalidadUsura": "MISMA_FECHA_FINAL", "topeCobranzaPorPago": null, "...": "..."},
    "NEGOCIO": {"prelacion": "SERVICIOS_ANTES_DE_CAPITAL", "aplicacionAbono": "PRIMERO_PENDIENTES", "modalidadUsura": "MISMA_CUOTA", "margenUsura": "1", "...": "..."}
  },
  "claves": [{"clave": "prelacion", "descripcion": "Orden de aplicación de un pago", "fuente": "P1 / D16"}],
  "archivoNegocio": "config/reglas-negocio.properties"
}
```

Las claves son las 11 de `ReglasNegocio.CLAVES`, en el orden del record. `topeCobranzaPorPago` es `null` cuando no hay tope.

### POST /api/v1/simulaciones

Plan de pagos de un cliente que paga siempre a tiempo.

```bash
curl -X POST localhost:8080/api/v1/simulaciones -H 'Content-Type: application/json' \
  -d '{"linea":"CPPU","periodicidad":"QUINCENAL","cuotas":48,"meses":null,"valorMoto":"5100000","pagoInicial":"0","perfilReglas":"NEGOCIO"}'
```

Request (`CreditoRequest`): se manda `cuotas` **o** `meses` (con meses, cuotas = ROUNDDOWN(meses × periodos por año / 12)). Si llegan los dos deben coincidir. Opcionales: `linea` (CPPU), `periodicidad` (SEMANAL), `valorMoto`, `pagoInicial` (0), `matriculaRunt`, `soat`, `prenda`, `gpsPlataforma`, `gastosComerciales` (los de Inputs) y `plazoLimitePeriodos` (horizonte de proyección con saldo pendiente).

```json
{"resumen": {"linea": "CPPU", "perfilReglas": "NEGOCIO", "montoFinanciado": "5904400", "tasaEA": "0.8762",
             "tasaPeriodo": "0.0265654190161528", "cuotas": 48,
             "cuotaFinanciera": "219092.9355960436057213056188035084", "serviciosPorCuota": "136574.04",
             "cuotaTotal": "355666.9755960436057213056188035084", "totalPagado": "17072014.82861009307462266970256842",
             "totalIntereses": "4612060.908610093074622669702568407", "totalServicios": "6555553.92",
             "periodicidad": "QUINCENAL", "porConfirmar": ["QUINCENAL: 24 periodos/año por confirmar con Diego"]},
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

`perfilReglas` va arriba o dentro de `credito` (gana el de arriba). `incluirAsientos` es `true` por defecto; en `false`, `asientos` llega vacío. Un periodo puede llevar varias instrucciones: se aplican en el orden en que llegan (el caso 13 manda `SIN_PAGO` y `ABONO_EXTRA` en la semana 42).

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
| `CIERRE_POR_RECUPERACION` | `tipoRecuperacion` (DACION_EN_PAGO / RETOMA), `valorMoto` | — |

Response:

```json
{"resumen": {"periodoFinal": "...", "estadoFinal": "CANCELADO", "saldoFinal": "0", "totalPagadoCliente": "...",
             "cuotasVencidasMax": "...", "montoFinanciado": "5904400", "perfilReglas": "NEGOCIO"},
 "movimientos": [{"periodo": 42, "mes": 10, "estado": "VIGENTE_AL_DIA", "evento": "Cuota + abono extra",
                  "etiqueta": "Cuota + abono extra", "saldoInicial": "...", "serviciosCausados": {"fondoGarantias": "...", "asistencia": "...", "seguro": "...", "total": "..."},
                  "pendientesPagadosExtra": {"cobranza": "...", "mora": "...", "interesCorriente": "...", "servicios": {"...": "..."}, "capital": "...", "total": "..."},
                  "pagoTotalCliente": "...", "pagoExtraordinario": "...", "...": "todos los campos de Movimiento"}],
 "asientos": [{"periodo": 42, "evento": "Cuota + abono extra",
               "lineas": [{"cuenta": "CAJA", "nombreCuenta": "Caja", "debito": "...", "credito": "0", "explicacion": "Abono extraordinario recibido"}],
               "totalDebitos": "...", "totalCreditos": "...", "cuadra": true}]}
```

- `movimientos` empieza en el periodo 0 (desembolso, estado `DESEMBOLSADO`) y trae todos los campos de `Movimiento`; los `Servicios` y `DeudaExtinguida` van como objetos con su `total`. `etiqueta` es el `evento` del motor ("Pago normal", "Cuota + abono extra"…). Además vienen `pagoTotalCliente` y `pagoExtraordinario`.
- `asientos` sale de `Contabilizador.estandar()`. `cuadra` = débitos y créditos iguales al centavo (las sumas DECIMAL128 difieren en residuos de 1e-28; el `Contabilizador` además rechaza cualquier asiento con más de 1 COP de diferencia).

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
 {"numero": 13, "nombre": "Abono extra con pendientes", "descripcion": "...", "fuente": "Equipo, valores validados por Diego (ABN-1, docs/reglas/caso-13.md); no está en el Excel"}]
```

Los casos 1 a 12 salen de `simulaciones.EscenariosExcel` (hojas Caso1 a Caso12) y el 13 de `EscenariosExcel.caso13` (el mismo escenario que `Caso13AbonoConPendientesTest`). Todos sobre la réplica del Excel: CPPU, semanal, 104 cuotas.

### GET /api/v1/casos/{numero}?perfilReglas=EXCEL|NEGOCIO

```bash
curl 'localhost:8080/api/v1/casos/1?perfilReglas=EXCEL'
curl 'localhost:8080/api/v1/casos/13?perfilReglas=NEGOCIO'
```

La respuesta de `/escenarios/ejecutar` (`resumen`, `movimientos`, `asientos`) más:

- `caso`: número, nombre, descripción y fuente.
- `perfilReglas`: el perfil con que corrió.
- `credito`: el crédito del caso en formato de request (incluye `plazoLimitePeriodos`: 100 en el caso 8, 120 en el 9).
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

Respuestas reales del API, generadas por `EjemplosApiTest` en cada `./mvnw test` (no se editan a mano):

| Archivo | Petición |
|---|---|
| `catalogo.json` | `GET /api/v1/catalogo` |
| `simulacion-quincenal.json` | `POST /api/v1/simulaciones` con CPPU, quincenal, 48 cuotas, NEGOCIO |
| `caso-13-negocio.json` | `GET /api/v1/casos/13?perfilReglas=NEGOCIO` |

## Desviaciones del contrato (`contrato-api-cartera.md`)

1. **`tiposRecuperacion`** es `["DACION_EN_PAGO", "RETOMA"]`, no `["DACION", "RETOMA"]`: son los nombres reales de `Instruccion.TipoRecuperacion`, como pide el contrato.
2. **Instrucción `CIERRE_POR_RECUPERACION`**: su record tiene un campo `tipo` (dación o retoma) que choca con el `tipo` de la instrucción. Va en **`tipoRecuperacion`**; `valorMoto` se llama igual que en el record.
3. **`modalidad`** es un solo campo para `ABONO_EXTRA` (ModalidadAbono) y `CAMBIO_TASA` (ModalidadCambioTasa), porque los dos records lo llaman igual.
4. **Dinero sin redondear**: el contrato muestra `"219093.00"`; el API manda el valor completo del motor (`"219092.9355960436…"`) para no perder precisión, sin ceros sobrantes (`"5904400"`, `"0"`). Las tasas igual (`"0.0265654190161528"`, no `"0.0265"`).
5. **`cuadra`** compara débitos y créditos **al centavo**, no con `==` exacto: con DECIMAL128 las sumas de un asiento cuadrado difieren en residuos de 1e-28 y la igualdad exacta daba `false` en asientos correctos.
6. **Campos agregados** (no rompen el contrato): `catalogo.perfilesReglas`, `lineas[].periodicidades`, `periodicidades[].diasNominales/unidadCuota/cuotasMaximas`; `reglas.archivoNegocio`; `simulacion.resumen.linea/perfilReglas/serviciosPorCuota` y `plan[].mes/saldoInicial/cuotaFinanciera`; `ejecucion.resumen.montoFinanciado/perfilReglas`; en los movimientos `evento` (además de `etiqueta`), `pagoTotalCliente` y `pagoExtraordinario`; en los asientos `totalDebitos`, `totalCreditos` y en cada línea `nombreCuenta` y `explicacion`; en `/casos/{numero}` `perfilReglas` y `credito`; en `CreditoRequest` los costos de la operación y `plazoLimitePeriodos`.
7. **Perfil por defecto**: `NEGOCIO` en simulaciones y escenarios, `EXCEL` en `/casos/{numero}` (el contrato no lo fija).
8. **Error 500 `ASIENTO_DESCUADRADO`**: el contrato solo habla de 400 y 404; este es un error interno.

## Cambios en el núcleo que trajo el API

- `EscenariosExcel` ahora tiene los 13 casos: se agregaron `caso2`, `caso4`, `caso5`, `caso7` (antes solo estaban en las pruebas) y `caso13` / `caso13ConPendientes` (movido de `Caso13AbonoConPendientesTest`, que ahora lo referencia).
- `RecaudoConVencidos` no acreditaba la cobranza pendiente que paga una cuota a tiempo después de un pago tardío (semana 42 del ejemplo del contrato sin `SIN_PAGO`): el asiento se descuadraba en 4.000 y el `Contabilizador` lanzaba excepción. Ahora acredita `CXC_GASTOS_COBRANZA` por `pagoCobranza` (prueba en `RecaudoConVencidosTest`).
