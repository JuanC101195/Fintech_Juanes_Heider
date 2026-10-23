# ADR 0004 · Persistencia, versionado de reglas y parámetros, y lote nocturno

**Estado:** propuesta · 2026-10-23. El modelo de dominio (sin base de datos) está hecho; la base de datos y el lote **no** se implementan todavía.

## Contexto

Hoy el motor no guarda nada: el API recibe el crédito y las instrucciones, corre el motor y responde. Para operar créditos reales (backlog: HT-03 historial inmutable, HT-04 versionado de parámetros, HT-05 idempotencia, HT-07 outbox, HU-36 cierre diario) hace falta persistencia. Antes de elegir la base de datos, el dominio debe saber con qué versión de reglas y de parámetros se originó cada crédito, porque Diego decidió:

- **D33:** todos los parámetros del producto son sensibles; un cambio rige **solo para créditos nuevos, salvo la usura**, que recalcula los vigentes desde la cuota siguiente. El administrador cambia de inmediato, con bitácora (quién, qué, cuándo) y sin segundo aprobador. **D41:** hay un suplente con los mismos permisos.
- **D26 y D44:** la cobranza diaria es un parámetro con vigencia; cada crédito guarda el valor vigente cuando se origina.
- **D45:** una exoneración o condonación la autoriza el director financiero o el gerente, con justificación, y queda quién, cuándo, a qué crédito y por qué.

Volumen esperado: **decenas de miles de créditos** (se diseña para 50.000 activos), con 24 a 104 periodos cada uno, más los periodos de mora hasta el plazo límite.

## Lo que ya quedó en el dominio (sin base de datos)

| Pieza | Dónde | Qué hace |
|---|---|---|
| `VersionReglas` | `nucleo/versiones` | Versión inmutable de `ReglasNegocio`: id legible, `vigenteDesde`, origen y **huella SHA-256** del contenido canónico (`formato=reglas-negocio/1` y luego `clave=valor` en el orden de `ReglasNegocio.CLAVES`; decimales sin ceros a la derecha, `null` vacío). `desdeGuardado` reconstruye una versión desde la base de datos y falla si la huella no coincide (contenido alterado). |
| `RepositorioReglas` | `nucleo/versiones` | Interfaz sin Spring: `porVersion(id)`, `vigenteEn(fecha)`, `versiones()`. |
| `RepositorioReglasEnMemoria` | `nucleo/versiones` | Implementación en memoria. Solo hacia adelante (cada versión empieza después de la última), sin ids repetidos. `registrar(...)` es el alta de un administrador o su suplente con bitácora, idempotente ante un reintento idéntico y sin efecto si algo falla. |
| `OrigenCredito` | `nucleo/versiones` | Ata un crédito a la versión vigente en su fecha de originación (id + huella) y la recupera verificando que no haya cambiado. |
| `PerfilesReglas` (adaptador de archivo) | `api` | Expone los perfiles como versiones: `excel-2026-10` (desde 2026-10-11) y `negocio-<fecha>` (por defecto `negocio-2026-10-23`, configurable con `cartera.reglas.vigente-desde`). `/reglas` y los JSON no cambian. |
| `EventoBitacora` (sellada) | `nucleo` | `CambioParametro`, `AltaVersionReglas` y `Exoneracion`: quién, cuándo (lo da quien llama, nunca el reloj) y por qué. |
| `BitacoraEnMemoria` | `nucleo` | Implementación de `BitacoraParametros` para pruebas y simulaciones; solo agrega. |
| `Responsable` y `Rol` | `nucleo` | Regla de quién puede qué: `ADMINISTRADOR`/`SUPLENTE_ADMINISTRADOR` cambian parámetros y reglas (D33, D41); `DIRECTOR_FINANCIERO`/`GERENTE` exoneran (D45). `PermisoInsuficienteException` si no alcanza. Los permisos reales (autenticación) los pondrá el API. |
| `VigenciasCobranza.cambiar(..., Responsable, ...)` | `nucleo` | Cambio de la cobranza diaria con el rol validado y registro en la bitácora. |

### Convención de ids de versión

- Minúsculas, dígitos y guiones (`[a-z0-9]+(-[a-z0-9]+)*`, hasta 64). Legibles, estables y **nunca reutilizados con otro contenido**.
- `excel-2026-10`: el perfil EXCEL (Excel "Casos Operaciones V2" de Diego, versión del 11 de octubre).
- `negocio-<aaaa-mm-dd>`: el perfil NEGOCIO, con la fecha desde la que rige la configuración. **Cambiar `config/reglas-negocio.properties` es una versión nueva:** hay que subir `PerfilesReglas.NEGOCIO_VIGENTE_DESDE` (o `cartera.reglas.vigente-desde`). `VersionesPerfilesTest` fija la huella de cada id y falla si el contenido cambia sin cambiar el id.
- Versiones dadas de alta por un administrador (con base de datos): mismo patrón, `negocio-<fecha>`, y un sufijo `-2`, `-3` si hubiera dos el mismo día (el repositorio igual exige que cada una empiece después de la anterior).

### Lo que el API no expone (todavía)

El API **no** publica la versión de reglas usada. Agregar un campo, aunque sea opcional, cambia los JSON de `docs/api-ejemplos` (Jackson lo serializa) y `ContratoApiTest`, que compara las claves de los DTO con `consola/src/tipos.ts`. Se hará cuando el API reciba créditos persistidos (el crédito ya trae su versión), junto con `tipos.ts` y la regeneración de los ejemplos.

### Cuando se agrega una regla nueva a `ReglasNegocio`

El contenido canónico de **todas** las versiones cambia (aparece una clave más), así que la huella recalculada de una versión vieja ya no es la guardada. Por eso la base de datos guarda el **texto canónico** y su huella, y la verificación se hace sobre ese texto (`desdeGuardado`), no sobre las reglas recalculadas. Al leer un texto viejo, la clave que falta toma el valor de `EXCEL`; esto es correcto porque la convención del repo es que una regla nueva entra con el comportamiento anterior como valor de `EXCEL` ("nunca cambia en silencio el comportamiento EXCEL"). Si alguna vez no se cumple, la migración debe reescribir las versiones viejas con la clave explícita y **una huella nueva con un formato nuevo** (`reglas-negocio/2`), dejando en la bitácora la equivalencia.

## Decisión

1. **PostgreSQL** administrado en Azure (**Flexible Server, Burstable B1ms**) como única base de datos del monolito modular (ADR 0001), en el módulo nuevo `creditos` (persistencia) más `abonos` (idempotencia) y `procesos` (lote). El `nucleo` sigue sin Spring ni base de datos: la persistencia implementa `RepositorioReglas` y `BitacoraParametros` desde fuera.
2. **Las entradas son la fuente de verdad** (crédito, versiones, vigencias, eventos); los **movimientos y asientos se guardan inmutables** como registro contable; las **instantáneas de corrida** y el estado actual son caché que se puede borrar y reconstruir.
3. **Solo inserción** en versiones, vigencias, eventos, movimientos, asientos y bitácora. Una corrección es un evento nuevo (o un contraasiento), nunca un `UPDATE`.
4. **Lote nocturno** fuera del App Service F1, como **Azure Container Apps Job** programado con la misma imagen del API.

## Esquema lógico

Tipos: dinero y tasas en `numeric` sin escala fija (el motor no redondea, `Calc.MC`); fechas de negocio en `date`; marcas de registro en `timestamptz`. Nombres en español.

### Versiones y parámetros

```
version_reglas            (solo inserción)
  id               text PK                -- excel-2026-10, negocio-2026-10-23
  vigente_desde    date UNIQUE NOT NULL
  formato          text NOT NULL          -- reglas-negocio/1
  contenido        text NOT NULL          -- VersionReglas.contenidoCanonico()
  huella           char(64) NOT NULL      -- SHA-256 del contenido
  origen           text NOT NULL
  registrado_por   text NOT NULL, registrado_en timestamptz NOT NULL

version_producto          (solo inserción; HT-04)
  id               text PK                -- cppu-2026-10-23
  linea            text NOT NULL          -- LineaCredito
  vigente_desde    date NOT NULL, UNIQUE (linea, vigente_desde)
  contenido        jsonb NOT NULL         -- tasa EA, servicios, IVA, plazo máximo… (ParametrosProducto sin lo del crédito)
  huella           char(64) NOT NULL

vigencia_parametro        (solo inserción; D26, D44; generaliza VigenciasCobranza)
  parametro        text                   -- cobranzaDiaria
  vigente_desde    date
  valor            numeric NOT NULL
  PK (parametro, vigente_desde)
```

Al arrancar, la aplicación carga `excel-2026-10` y `negocio-<fecha>` del archivo si no existen, y si existen verifica la huella; una diferencia impide arrancar (alguien cambió el archivo sin cambiar el id).

### Créditos y eventos

```
credito
  id                    text PK          -- id del CRM
  linea, periodicidad, numero_cuotas, fecha_originacion, fecha_desembolso
  id_version_reglas     text FK version_reglas, huella_reglas char(64)   -- OrigenCredito
  id_version_producto   text FK version_producto
  parametros            jsonb NOT NULL   -- ParametrosProducto copiados al originar (cobranza vigente, plazo límite)
  operacion             jsonb NOT NULL   -- Operacion (moto, matrícula, SOAT, pago inicial…)
  cobranza_activa       boolean          -- D44: se activa por crédito
  estado                text             -- caché del último movimiento
  ultimo_periodo        int              -- caché
  proxima_fecha_corte   date             -- caché, para el lote
  version_fila          int              -- bloqueo optimista

evento_credito            (solo inserción; las instrucciones del motor)
  id                    bigint identity PK
  credito_id            text FK
  periodo               int NOT NULL
  fecha_efecto          date
  tipo                  text NOT NULL    -- PagoValor, AbonoExtra, PrepagoTotal, CambioTasa, UsuraVigente, CierrePorRecuperacion, Exoneracion…
  datos                 jsonb NOT NULL
  clave_idempotencia    text NOT NULL
  huella_solicitud      char(64) NOT NULL
  origen                text             -- CRM, consola, lote
  registrado_por, registrado_en
  anula_evento_id       bigint NULL      -- corrección: un evento nuevo que anula otro
  UNIQUE (credito_id, clave_idempotencia)
  INDEX (credito_id, periodo)
```

La exoneración (D45) entra como un evento más (`tipo = Exoneracion`) además de su registro en la bitácora; aplicarla a los saldos es una instrucción del motor por diseñar.

### Resultado del motor

```
corrida                   (una por crédito cada vez que se recalcula)
  id                    bigint identity PK
  credito_id            text FK
  numero                int              -- 1, 2, 3… por crédito; UNIQUE (credito_id, numero)
  desde_periodo         int              -- desde dónde se recalculó
  motivo                text             -- lote, evento tardío, usura, anulación
  id_version_reglas     text             -- la del crédito (o la usura vigente)
  hasta_evento_id       bigint           -- último evento considerado
  registrado_en         timestamptz

movimiento                (solo inserción; HT-03)
  credito_id, periodo, corrida_id        PK (credito_id, periodo, corrida_id)
  fecha_corte           date NOT NULL
  ... columnas de Movimiento (saldos, causación, aplicación por concepto, CxC, estado, cuotas vencidas)
  vigente               boolean          -- false cuando una corrida posterior lo reemplaza (único UPDATE permitido, de true a false)

asiento / linea_asiento   (solo inserción; HT-06)
  asiento: id, credito_id, periodo, corrida_id, fecha_contable, evento, contraasiento_de NULL
  linea_asiento: asiento_id, cuenta, auxiliar, debito, credito
  CHECK de cuadre por asiento en la aplicación (Contabilizador) y una vista de control

instantanea_corrida       (caché para reanudar; la implementa el motor reanudable)
  credito_id, periodo, corrida_id        PK
  formato               text             -- versión del formato del estado serializado
  estado                bytea / jsonb    -- estado del motor al cerrar el periodo
  huella                char(64)
```

Un evento tardío (un pago que llega con fecha de un periodo ya cerrado) o la usura generan una **corrida nueva desde ese periodo**: se toma la instantánea de corrida del periodo anterior, se reprocesa, los movimientos nuevos se insertan con el nuevo `corrida_id`, los anteriores quedan con `vigente = false` y la contabilidad registra contraasientos de la diferencia. Nada se borra.

### Bitácora y avisos

```
bitacora                  (solo inserción; D26, D33, D45)
  id                    bigint identity PK
  tipo                  text             -- CambioParametro, AltaVersionReglas, Exoneracion…
  quien, rol            text
  registrado_en         timestamptz
  justificacion         text NOT NULL
  credito_id            text NULL
  datos                 jsonb            -- el registro completo
  huella_anterior, huella char(64)       -- cadena de huellas: alterar una fila rompe las siguientes
  INDEX (credito_id, registrado_en), INDEX (tipo, registrado_en)

outbox                    (HT-07)
  id, tipo, credito_id, datos jsonb, creado_en, enviado_en NULL, intentos
```

La cuenta de la aplicación no tiene `UPDATE` ni `DELETE` sobre las tablas de solo inserción (`REVOKE`), salvo `movimiento.vigente`, `credito` (caché) y `outbox.enviado_en`.

## Idempotencia de eventos (HT-05)

- Cada pago o abono trae una **clave de idempotencia** del origen: el id de la transacción de recaudo del CRM (o la referencia bancaria); en el API, la cabecera `Idempotency-Key` o un campo del cuerpo.
- `UNIQUE (credito_id, clave_idempotencia)` en `evento_credito`. Al recibir:
  - clave nueva → se inserta el evento y se procesa, en **una sola transacción** con los movimientos, asientos, bitácora y outbox (ADR 0001);
  - misma clave y misma `huella_solicitud` (SHA-256 del cuerpo canónico) → se devuelve el resultado ya registrado, sin efecto (200);
  - misma clave con otra solicitud → 409 `CLAVE_IDEMPOTENCIA_REUTILIZADA`.
- Dos peticiones simultáneas con la misma clave: la segunda choca con la restricción única y responde como reintento.
- El lote nocturno es idempotente por fecha de corte (tabla `corrida_lote`, abajo), y el alta de una versión de reglas es idempotente por id y huella (como `RepositorioReglasEnMemoria.registrar`).

## Qué se guarda y qué se recalcula

| Dato | ¿Se guarda? | Por qué |
|---|---|---|
| Crédito, operación, parámetros copiados, versión de reglas y producto | Sí, fuente de verdad | Es lo pactado; D33 exige que no cambie. |
| Versiones de reglas, de producto y vigencias | Sí, solo inserción | Un crédito viejo debe poder recalcularse con sus reglas. |
| Eventos (instrucciones) | Sí, solo inserción | Con crédito + versiones + eventos el motor reproduce todo (es determinista: `DeterminismoTest`). |
| Movimientos y asientos | Sí, solo inserción | Registro contable y de auditoría: lo que se informó a contabilidad y al cliente no puede cambiar porque cambió el código. Se verifica que el recálculo coincida. |
| Instantáneas de corrida | Sí, como caché | Reanudar desde el último periodo sin reprocesar todo el crédito; se pueden borrar y regenerar. Se conserva la última por crédito y una por cierre de mes. |
| Estado actual del crédito (`credito.estado`, `ultimo_periodo`, `proxima_fecha_corte`) | Sí, como caché | Consultas y selección del lote sin leer movimientos. |
| Reportes (cartera por edad de mora, provisiones) | No | Vistas o consultas sobre los movimientos vigentes. |

**Verificación:** el lote recalcula desde cero una muestra de créditos (p. ej. 1 % cada noche, rotando) y compara contra los movimientos guardados; una diferencia es una alerta (cambio de código que altera créditos viejos), igual que las huellas de `caracterizacion/`.

## Estrategia de migración

1. **Flyway** con migraciones SQL versionadas en `src/main/resources/db/migration`; nunca se edita una migración aplicada. Pruebas de integración con Testcontainers (PostgreSQL 16).
2. Cambios de esquema con **expandir y contraer**: agregar columna/tabla nueva, desplegar código que escribe en las dos, migrar datos, quitar lo viejo en una versión posterior. El API es un solo servicio, pero así un despliegue fallido se revierte sin perder datos.
3. Versiones de reglas: el `.properties` queda como **semilla e importador**; en producción las versiones nuevas entran por el alta de administrador (con bitácora), no por despliegue. El importador del cuestionario genera un candidato que el administrador registra.
4. Cambio del formato canónico o del estado serializado: formato nuevo (`reglas-negocio/2`, `formato` de la instantánea), lectura de los dos formatos durante la transición; las instantáneas viejas se pueden descartar y regenerar.
5. Carga inicial de créditos existentes (si llegan del sistema actual): como créditos con un evento de saldo inicial y su versión de reglas, sin inventar historia.

## Volumen, índices y particionado

Estimación con 50.000 créditos activos, mezcla de periodicidades, ~110 periodos de vida promedio:

| Tabla | Filas en la vida de la cartera | Tamaño aproximado |
|---|---|---|
| movimiento | ~5,5 millones (más las corridas recalculadas, +10 a 20 %) | 3 a 5 GB |
| linea_asiento | ~25 a 35 millones | 3 a 4 GB |
| evento_credito | ~6 millones | 1 GB |
| instantanea_corrida (con poda) | ~1 a 2 millones | 2 a 5 GB |
| bitacora | miles | despreciable |

El lote diario procesa los créditos con corte ese día: ~7.000 semanales + ~3.300 quincenales los días 5 y 20 + ~1.700 mensuales el día 5. Con la instantánea de corrida cada crédito procesa **un** periodo (milisegundos), así que el lote dura minutos.

- **Índices:** PK compuestas indicadas arriba; `credito (proxima_fecha_corte) WHERE estado IN ('VIGENTE','EN_MORA')` (índice parcial para el lote); `movimiento (fecha_corte)` para reportes; `evento_credito (credito_id, periodo)`; `UNIQUE (credito_id, clave_idempotencia)`; `asiento (fecha_contable)`; `bitacora (credito_id, registrado_en)`.
- **Particionado:** a esta escala PostgreSQL no lo necesita para rendimiento. Se particionan por **rango anual** desde el inicio solo las tablas grandes de solo inserción (`movimiento` por `fecha_corte`, `asiento`/`linea_asiento` por `fecha_contable`), porque hacerlo después exige reescribir la tabla y facilita archivar años cerrados. `instantanea_corrida` no se particiona: se poda. Revisar al pasar de ~200.000 créditos (particionar `movimiento` por hash de `credito_id` dentro del año).

## Alternativas de base de datos (precios de lista aproximados, USD/mes, a verificar en la calculadora de Azure)

| Opción | Costo aprox. | A favor | En contra |
|---|---|---|---|
| **PostgreSQL Flexible Server, Burstable B1ms** (1 vCore, 2 GiB, 32 GiB) | ~13 cómputo + ~4 almacenamiento ≈ **17**; las cuentas nuevas y Azure for Students suelen tener 750 h/mes de B1ms gratis por 12 meses | Relacional, transacciones, `jsonb`, particionado, Flyway/Testcontainers; misma región que el API; respaldos automáticos 7 días | Burstable: CPU con créditos (suficiente para el lote de minutos); sin alta disponibilidad (agregarla duplica el costo) |
| PostgreSQL Flexible, General Purpose D2ds_v5 | ~130 a 150 | Rendimiento constante, HA opcional | Excesivo para decenas de miles de créditos |
| Azure SQL Database (serverless o Basic 5 DTU) | Basic ~5; oferta gratuita serverless (100.000 vCore·s/mes) | Barato, administrado | T-SQL; el equipo y las herramientas del repo apuntan a PostgreSQL; serverless se pausa (arranque en frío) |
| Cosmos DB (nivel gratuito 1.000 RU/s, 25 GB) | 0 a ~25 | Gratis en pequeño | No relacional: sin transacciones entre crédito, movimientos y asientos de la forma que exige ADR 0001 |
| PostgreSQL fuera de Azure (Neon, Supabase, plan gratuito) | 0 a ~25 | Gratis para empezar | Datos personales y financieros fuera del tenant; latencia entre nubes; límites de 0,5 a 1 GB |
| PostgreSQL propio en una VM B1s | ~8 + disco | Barato | Respaldos, parches y seguridad a mano |

**Recomendación:** PostgreSQL Flexible Server **B1ms** en `rg-motor-cartera` (North Central US, la región del API), con acceso privado o reglas de firewall solo para el App Service y el job, TLS obligatorio, respaldos de 7 días (subir a 35 con datos reales) y la contraseña en Key Vault o variables de la app (nunca en el repo). Subir a General Purpose solo si el lote o el API lo piden.

## Lote nocturno fuera del plan F1

El plan F1 del App Service **no sirve** para el lote: 60 minutos de CPU al día, se duerme sin tráfico y no tiene *Always On*, así que un `@Scheduled` dentro del API no corre de forma confiable.

| Opción | Costo aprox. (USD/mes) | Notas |
|---|---|---|
| **Azure Container Apps Job** programado (cron) | **0 a 5**: un job de 0,5 vCPU / 1 GiB durante 10 min cada noche ≈ 9.000 vCPU·s/mes, dentro de la concesión gratuita mensual (180.000 vCPU·s y 360.000 GiB·s). Registro de imágenes: GitHub Container Registry (gratis) o ACR Basic (~5) | La misma imagen del API con otro perfil de arranque (`--spring.profiles.active=lote`), sin servidor web. Reintentos y tiempo máximo por ejecución configurables; registros en Log Analytics (unos centavos al mes a este volumen). |
| Azure Functions (Flex Consumption, disparador de tiempo) | ~0 (concesión gratuita) | Exige empaquetar el motor como función Java; arranque en frío de Spring; límites de tiempo por ejecución a vigilar. Más trabajo de empaquetado que el job. |
| App Service **B1** con *Always On* y `@Scheduled` | ~13 (Linux B1) | La opción más simple en código y además saca al API del F1 (sin cuota de CPU, sin dormirse). Con más de una instancia hace falta un candado (`pg_try_advisory_lock`) para que el lote no corra dos veces. |
| GitHub Actions programado que llama un endpoint | 0 | Horario no garantizado y el trabajo corre igual dentro del F1 con su cuota: descartado. |

**Recomendación:** **Container Apps Job** para el lote (`0 7 * * *` en UTC = 2:00 a. m. en Colombia) y, cuando haya datos reales, pasar el API a **B1** (~13 USD) por disponibilidad. Costo total estimado con la base de datos: **~17 a 35 USD/mes**, parte cubierta por los créditos gratuitos del primer año.

Diseño del lote (`procesos`):

1. Toma un candado de PostgreSQL (`pg_try_advisory_lock`) y registra la ejecución en `corrida_lote (fecha_corte PK, estado, inicio, fin, procesados, fallidos)`. Si ya existe una ejecución **terminada** para esa fecha, no hace nada (idempotente); si quedó a medias, continúa con los créditos que faltan.
2. Selecciona los créditos con `proxima_fecha_corte <= fecha_corte` y estado activo, en lotes de ~500.
3. Por crédito, en **una transacción**: lee la última instantánea de corrida, los eventos hasta la fecha, corre el periodo con la versión de reglas del crédito (`OrigenCredito`) y la usura vigente, inserta movimiento, asientos, instantánea, bitácora y outbox, y actualiza la caché del crédito. Un crédito que falla queda registrado y no detiene a los demás.
4. Al final: verificación por muestra (recalcular desde cero y comparar), totales de control (caja de los asientos = pagos recibidos, como `Contabilizador`) y aviso si hubo fallas.
5. La hora de corte la pasa el job como argumento (`--fecha-corte=2026-11-03`), así se puede volver a correr una fecha y el núcleo nunca lee el reloj.

## Consecuencias

- El núcleo no cambia de forma: la persistencia implementa `RepositorioReglas` y `BitacoraParametros` en `creditos`, y el motor reanudable aporta la instantánea de corrida.
- Cambiar `config/reglas-negocio.properties` exige un id nuevo (`VersionesPerfilesTest` lo vigila).
- Costo nuevo: ~17 a 35 USD/mes cuando se active; hasta entonces, nada.

## Fuera de alcance de este ADR (pendiente)

- Implementar la base de datos, Flyway, el módulo `creditos`, el lote y el outbox.
- Aplicar la exoneración a los saldos (instrucción del motor) y su asiento contable; confirmar con Diego qué conceptos se pueden exonerar (hoy: cobranza, mora, interés corriente y servicios; capital no).
- Autenticación de usuarios y roles reales en el API (hoy solo la clave por aplicación de `FiltroApiKey`); mapear `PermisoInsuficienteException` a 403.
- Que el API use `VigenciasCobranza` y el repositorio de versiones al originar (hoy usa el perfil elegido en la petición).
- Política de retención y archivo de movimientos y bitácora (años que exige la ley contable y la Superintendencia).
