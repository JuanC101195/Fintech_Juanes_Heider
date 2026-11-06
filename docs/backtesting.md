# Backtesting multi-sede

El backtesting reproduce el comportamiento real de cada contrato en el motor y lo compara con lo que pasó de verdad. Hay 4 sedes, cada una con su propio servidor MySQL (misma BD `db_packgps`), y todas cargan a **un solo** PostgreSQL (`backtesting`).

Código: carpeta [`pipeline/`](../pipeline/). Esquema: `db/001_esquema.sql`, `db/002_indices.sql`, `db/003_sede.sql`.

## Llaves: (sede, contrato)

| Dato | Regla |
|---|---|
| Número de contrato | **Se repite entre sedes** y son contratos distintos (hay un 1428 en Cartagena, otro en Montería y otro en Sincelejo). |
| Llave de negocio | `(sede, contrato)`. La sede se conoce por el servidor al que se conecta `backtest.py`. |
| `id` (texto) | `SEDE\|contrato\|fecha_inicio` (ej. `MONTERIA\|1428\|2024-01-02`). Ya lleva la sede, y distingue los varios créditos que un número de contrato pudo tener. |
| Placa | **No** se repite entre sedes, pero **no** identifica un contrato (una moto puede tener varios contratos). Solo control de calidad: `cargar.py` avisa (warning, no aborta) si una placa aparece en dos sedes. |
| Corrida (ejecución) | `run_id`. Cada carga de una sede lleva su `run_id`; `corrida_meta` la describe; `vw_corrida_vigente` da la última terminada por sede. |

Ojo con el nombre: la columna `corrida` de `resultado`/`periodo`/`proyeccion` es la **variante de simulación** (`sin_justificaciones`, `con_justificaciones`, `max_*`, `fija_*`), no la ejecución. La ejecución es `run_id`.

## Contrato de columnas para la API y la página (P7)

Todas las tablas de datos tienen ahora **`sede text NOT NULL`** (default `'CARTAGENA'`) y **`run_id text NOT NULL`** (default `'legado'`). Nombres exactos:

| Tabla | Columnas nuevas | Llave primaria |
|---|---|---|
| `contrato` | `sede`, `run_id`, `placa` (nullable) | `(sede, run_id, id)` |
| `pago_real` | `sede`, `run_id` | sin PK; FK `(sede, run_id, id)` → `contrato` |
| `resultado` | `sede`, `run_id` | `(sede, run_id, corrida, id)`; FK a `contrato` |
| `periodo` | `sede`, `run_id` | `(sede, run_id, corrida, id, periodo)`; FK a `contrato` |
| `proyeccion` | `sede`, `run_id` | `(sede, run_id, corrida, id, periodo)` |
| `activas_fin_mes` | `sede`, `run_id` | `(sede, run_id, mes, contrato)` |
| `carta_fin_mes` | `sede`, `run_id` | `(sede, run_id, mes, motivo)` |
| `corte_activos` | `sede`, `run_id` | sin PK |
| `corrida_meta` (nueva) | `run_id`, `sede`, `fecha_corte`, `version_motor`, `git_sha`, `inicio`, `fin`, `estado` | `(sede, run_id)` |
| `tasa_usura` | (global, sin sede) | `(modalidad, vigencia_desde)` |

Vistas: `vw_corrida_vigente(sede, run_id, fecha_corte, version_motor, git_sha, inicio, fin, estado)` (una fila por sede, la última con `estado='terminada'`), y `vw_comparacion` / `vw_periodos_contrato`, que ahora traen `sede`, `run_id` y `placa`.

**Cómo debe consultar la API** (regla para `nube/src/contrato.js`):

1. Recibir `sede` (obligatoria, validada contra las 4) y, si se quiere ver una corrida concreta, `run_id`; si no, tomar la vigente:
   `SELECT run_id FROM vw_corrida_vigente WHERE sede = $1`.
2. Filtrar **toda** consulta por `sede` y `run_id`, y unir siempre por las tres columnas:
   `JOIN contrato c ON c.id = p.id AND c.sede = p.sede AND c.run_id = p.run_id`.
   Buscar un contrato por `WHERE sede = $1 AND run_id = $2 AND contrato = ANY($3)`; **nunca** solo por `contrato`.
3. `activas_fin_mes` se une por `a.sede = c.sede AND a.run_id = c.run_id AND a.contrato = c.contrato AND a.mes = ...`; `corte_activos` y `carta_fin_mes` se filtran por `sede` y `run_id`.
4. Los datos que existían antes de la migración quedan como `sede='CARTAGENA'`, `run_id='legado'`; mientras no se cargue una corrida nueva de Cartagena, `vw_corrida_vigente` devuelve `legado`.

Índices útiles ya creados: `contrato(sede, run_id, contrato)`, `contrato(sede, run_id, estado_real)`, `periodo|resultado|pago_real|proyeccion(sede, run_id, id)`, `activas_fin_mes(sede, run_id, contrato)`, `contrato(placa)`.

## Correr una sede de punta a punta

Requisitos: Python 3.12 con `pymysql` y `psycopg[binary]` (`pipeline/requirements.txt`), el motor corriendo (local o Azure), acceso NetBird a la sede, un `.env` externo con `DB_USER` y `DB_PASSWORD` (opcionales `DB_PORT`, `DB_NAME`; el host sale de `pipeline/comun.py`, nunca del `.env`), y un archivo para PostgreSQL con `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD` (opcionales `PGDATABASE`=backtesting y `PGSSLMODE`=require). Ninguno de esos archivos va al repo.

```powershell
# 0. Una sola vez por base de datos (idempotente): esquema + migración multi-sede
psql ... -f db/001_esquema.sql -f db/002_indices.sql -f db/003_sede.sql

# 1. Backtesting de la sede (solo lee MySQL; llama al motor en paralelo con reintentos)
py -3.12 pipeline\backtest.py --sede MONTERIA --env C:\ruta\monteria.env --api http://localhost:8099 --salida salida\monteria --workers 8

# 2. Mirar salida\monteria\corrida_meta.csv (estado terminada, run_id, versión del motor, git sha) y cargar
py -3.12 pipeline\cargar.py --sede MONTERIA --run-id <run_id de corrida_meta.csv> --csv salida\monteria --pg-env C:\ruta\pg.env
```

Notas de `backtest.py`:
- Es la lógica de `backtest_v11.py` sin cambios de cálculo (para Cartagena produce los mismos resultados y el mismo `id`); se verificó con un comparativo de `instrucciones()` y `logica()` contra v11 sobre 900 casos aleatorios.
- `--workers` (8 por defecto) = contratos evaluados a la vez; cada contrato hace sus 6 corridas en serie. `--reintentos` (3) reintenta red, 5xx y 429 con espera creciente.
- `run_id` por defecto = fecha UTC + sha corto (`20261106T150000Z-abc1234`); `--run-id` lo fija. La versión del motor sale de `/actuator/info` (o `--version-motor`); el sha, del repo (o `--git-sha` / `GIT_SHA`), con sufijo `-sucio` si hay cambios sin commit.
- La corrida queda `fallida` (código de salida 2) si no hubo contratos o más del 20 % falló por error (`--max-errores-pct`); una corrida `fallida` se puede cargar para inspeccionarla pero nunca es la vigente.
- `--solo-activos` (o `SOLO_ACTIVOS=1`): solo vigentes y corridas de tasa fija; `--contratos 1,2,3` y `--limite N` para depurar; `BACKTEST_DEBUG=1` imprime el traceback de los contratos descartados por error.
- Genera: `contratos.csv`, `pagos_reales.csv`, `resultados.csv`, `periodos.csv`, `proyeccion.csv`, `activas_fin_mes.csv`, `carta_fin_mes.csv`, `corte_activos.csv`, `usura.csv`, `corrida_meta.csv`. Todos (menos `usura.csv` y `corrida_meta.csv`) empiezan con las columnas `sede,run_id`; el orden y los nombres están en `pipeline/comun.py` (`COLUMNAS`).
- La **placa** se busca en `motos_app`, `rentabilidad_activos` y `motos_inv` (columna `placa`); si alguna no existe, avisa por stderr y sigue con la placa vacía. Los nombres de tabla/columna de la placa **no se han verificado contra la BD real** (esta tarea no la consulta): confirmarlos la primera vez y ajustar `CONSULTAS_PLACA` si hace falta.

Notas de `cargar.py`:
- Una sola transacción: borra solo `WHERE sede = X AND run_id = Y` (hijos primero), carga por nombre de columna con `COPY` y registra `corrida_meta`. Si algo falla, `ROLLBACK` y la base queda como estaba. No hace `DROP` ni `TRUNCATE` globales.
- Idempotente: cargar dos veces el mismo CSV deja los mismos conteos. Las otras sedes y las otras corridas de la misma sede no se tocan.
- Rechaza (sin tocar nada) un CSV cuya columna `sede` o `run_id` no coincida con los argumentos, o con columnas que la tabla no tiene.
- `--purgar-anteriores`: tras cargar, borra las demás corridas de esa sede (incluida `legado`); por defecto se conservan para comparar.
- `--modo activos`: refresco incremental con la salida de `backtest.py --solo-activos`; reemplaza los contratos del CSV y los `VIGENTE` de esa sede y corrida (que debe existir). Sustituye a `cargar_activos.py`.
- Control de placas: tras cargar, avisa (`AVISO: la placa X de SEDE también aparece en OTRA`) si una placa de la sede existe en la corrida vigente de otra sede. Solo avisa.
- `tasa_usura` es global: se hace upsert, no se borra.

## Agregar una sede

1. Una línea en `SEDES` de `pipeline/comun.py` (`"NUEVA": "server-xxx.netbird.cloud"`; mismo nombre de BD `db_packgps`, o `DB_NAME` en el `.env`).
2. Tener el `.env` con `DB_USER`/`DB_PASSWORD` de ese servidor.
3. Correr `backtest.py --sede NUEVA ...` y `cargar.py --sede NUEVA ...`. No hay que tocar el esquema: la sede es un valor de la columna `sede`.
4. Confirmar que no hay AVISOS de placas repetidas, y que la API/página reciben el nuevo valor de `sede` (la lista de sedes válidas de `nube/` debe incluirlo).

## Pruebas

```powershell
# Requiere Docker Desktop encendido. Levanta postgres:16 como `pg-p6` en el puerto 55432 y lo borra al terminar.
py -3.12 -m unittest discover -s pipeline/tests -t . -v
```

Cubren: migración 003 sobre datos `legado` (conteos intactos, idempotente), carga doble con el mismo conteo, el mismo número de contrato (1428) en dos sedes como filas independientes, aviso por placa repetida, rollback ante error, corrida vigente, modo activos, y el backtest contra un motor falso (reintentos, columnas, resultado igual con 1 u 8 hilos). Los CSV de `pipeline/tests/fixtures/` son datos inventados. Nada se ejecuta contra Azure.

## Orden de aplicación del esquema

`001` → `002` → `003`. La 003 es idempotente por sí sola; volver a correr `001` **después** de la 003 fallaría al recrear las vistas con el formato viejo, así que en una base ya migrada se aplica solo la 003.
