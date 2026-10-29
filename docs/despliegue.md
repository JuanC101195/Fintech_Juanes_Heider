# Despliegue

| Pieza | Dónde | URL |
|---|---|---|
| API REST (`co.financiera.cartera.api`) | Azure App Service, plan **F1 gratuito** Linux, Java 21 (`rg-motor-cartera`, North Central US) | https://motor-cartera-api-jc.azurewebsites.net/api/v1/catalogo |
| Consola | GitHub Pages (workflow `Sitio`, carpeta `consola/`) | https://juanc101195.github.io/Fintech_Juanes_Heider/consola/ |
| Cuestionario | GitHub Pages | https://juanc101195.github.io/Fintech_Juanes_Heider/cuestionario.html |
| Respuestas del cuestionario | Azure Function `fn-motor-cartera-respuestas` + tabla `respuestas` | — |
| Consulta de contratos | GitHub Pages + función `contrato` + PostgreSQL | https://juanc101195.github.io/Fintech_Juanes_Heider/contratos.html |

## Costo

- El plan F1 es gratuito. Tiene una cuota de CPU de 60 minutos al día y se duerme cuando no se usa: la primera petición después de un rato tarda unos segundos mientras arranca Spring.
- El API no tiene base de datos ni guarda estado.
- Si se agota la cuota diaria, el API responde 403 hasta el día siguiente y la consola cae al modo demostración.

## Seguridad

- Sin autenticación por defecto: el API expone solo los casos del Excel y las reglas del motor, sin datos de clientes. La autenticación por clave es opcional (ver abajo) y hay que activarla antes de cargar datos reales.
- El CORS solo admite `http://localhost:5173` y `https://juanc101195.github.io`, con las cabeceras `Content-Type` y `X-API-Key`.
- Límites de entrada (números, instrucciones, días, fechas y cuerpo de 256 KB) y cabeceras de seguridad: `docs/api.md`. Los errores inesperados responden 500 `ERROR_INTERNO` con un `idCorrelacion` que también está en el log (`az webapp log tail`).
- Solo se expone `/actuator/health`; las trazas y mensajes de `/error` están apagados.

### Activar la clave de acceso (X-API-Key)

Roles: `CONSUMIDOR` (todo `/api/v1/**`: consola, n8n) y `LECTURA` (solo `GET` de `/catalogo`, `/reglas` y `/casos*`). Se guarda en Azure únicamente el hash SHA-256 de cada clave.

```bash
# 1. Generar una clave aleatoria y su hash (guarde la clave en un gestor de secretos; solo el hash va a Azure)
CLAVE=$(openssl rand -hex 32)
HASH=$(printf '%s' "$CLAVE" | sha256sum | cut -d' ' -f1)
echo "$CLAVE"      # la que usa el cliente en la cabecera X-API-Key

# 2. Configurarla en Azure (formato 'sha256hex:ROL', varias separadas por coma)
az webapp config appsettings set -g rg-motor-cartera -n motor-cartera-api-jc   --settings "CARTERA_API_CLAVES=$HASH:CONSUMIDOR"

# 3. Probar
curl -i https://motor-cartera-api-jc.azurewebsites.net/api/v1/catalogo                       # 401
curl -i -H "X-API-Key: $CLAVE" https://motor-cartera-api-jc.azurewebsites.net/api/v1/catalogo  # 200
```

- En PowerShell el hash es `(Get-FileHash -InputStream ([IO.MemoryStream]::new([Text.Encoding]::UTF8.GetBytes($clave))) -Algorithm SHA256).Hash.ToLower()`.
- Rotar: agregar el hash nuevo a la lista, actualizar a los clientes y quitar el viejo. Apagar la autenticación: `az webapp config appsettings delete ... --setting-names CARTERA_API_CLAVES`.
- Activarla rompe a quien no mande la cabecera: la consola publicada (GitHub Pages) y n8n deben enviar `X-API-Key`. Una clave que viaje en el JavaScript de GitHub Pages es pública; para la consola use `LECTURA` o no la active hasta tener otra forma de entrar.
- Una entrada mal formada impide arrancar la app (el mensaje de error no repite lo configurado).

## Consulta de contratos del backtesting

```
página (GitHub Pages, contratos.html) --x-clave--> Azure Function GET /api/contrato --SELECT--> PostgreSQL (Azure)
```

La página es pública pero no contiene datos; la función vive en la misma Function App `fn-motor-cartera-respuestas` (código en `nube/src/contrato.js`) y solo lee la base. Sin la cabecera `x-clave` correcta responde 401. Tiene un límite de 60 consultas por minuto por IP y CORS solo para `https://juanc101195.github.io` y `localhost:5173`. Si la Function App tiene CORS configurado en la plataforma, debe incluir el mismo origen de GitHub Pages.

Variables de la función (Configuración de la Function App; nunca en el repo):

| Variable | Qué es |
|---|---|
| `CLAVE_CONSULTA` | Clave que debe enviar la página |
| `PG_HOST`, `PG_DB` | Servidor y base PostgreSQL |
| `PG_USER`, `PG_PASSWORD` | Usuario de **solo lectura** y su contraseña |

```bash
az functionapp config appsettings set -g rg-motor-cartera -n fn-motor-cartera-respuestas   --settings CLAVE_CONSULTA=... PG_HOST=... PG_DB=... PG_USER=... PG_PASSWORD=...
```

Usuario de solo lectura: crear un rol con `CONNECT` a la base, `USAGE` en el esquema y `SELECT` únicamente sobre `contrato`, `pago_real`, `resultado` y `periodo`. La función solo ejecuta `SELECT` parametrizados, pero el rol es la garantía real.

Firewall de PostgreSQL: agregar una regla por cada IP de salida de la función (y revisar si cambian tras un cambio de plan):

```bash
az functionapp show -g rg-motor-cartera -n fn-motor-cartera-respuestas --query possibleOutboundIpAddresses -o tsv
az postgres flexible-server firewall-rule create -g rg-motor-cartera -n <servidor> --rule-name fn-<n> --start-ip-address <ip> --end-ip-address <ip>
```

Desplegar: como la función de respuestas (`npm install --omit=dev` en `nube/`, zip sin `node_modules` ajenos ni pruebas, `az functionapp deployment source config-zip`). Probar: `curl -i -H "x-clave: $CLAVE" "https://fn-motor-cartera-respuestas.azurewebsites.net/api/contrato?numero=1001"`; sin la cabecera debe dar 401.

Rotar la clave: generar una nueva (`openssl rand -hex 24`), cambiar `CLAVE_CONSULTA` con `az functionapp config appsettings set` y entregarla aparte a quien corresponda; la anterior deja de servir al instante. Si hubo una fuga, rotar también `PG_PASSWORD`.

Pausar el servidor (para no gastar crédito; la consulta responderá 500 mientras tanto):

```bash
az postgres flexible-server stop  -g rg-motor-cartera -n <servidor>
az postgres flexible-server start -g rg-motor-cartera -n <servidor>
```

Azure reinicia solo un servidor detenido a los 7 días.

## Reglas de negocio

`config/reglas-negocio.properties` se empaqueta dentro del jar (`pom.xml`, `<resources>`). Si el archivo no está en disco, como pasa en Azure, `PerfilesReglas` lo lee del classpath. Un cambio de reglas exige volver a desplegar.

Cada perfil es una versión de reglas con id fijo (ADR 0004): `excel-2026-10` y `negocio-<fecha>`, hoy `negocio-2026-10-23`. Cambiar el archivo es una versión nueva: suba `PerfilesReglas.NEGOCIO_VIGENTE_DESDE` (o configure `CARTERA_REGLAS_VIGENTEDESDE=aaaa-mm-dd` en la app) y actualice la huella de `VersionesPerfilesTest`, que falla si el contenido cambia sin cambiar el id. La fecha debe ser posterior al 2026-10-11 o la app no arranca.

## Persistencia y lote nocturno (planeado, ADR 0004)

Todavía no existe. La propuesta de `docs/adr/0004-persistencia-y-versionado.md`:

| Pieza | Dónde | Costo aprox. (USD/mes) |
|---|---|---|
| Base de datos | Azure Database for PostgreSQL Flexible Server, Burstable **B1ms**, 32 GiB, North Central US | ~17 (puede estar cubierto 12 meses por los servicios gratuitos) |
| Lote nocturno | **Container Apps Job** con la imagen del API, cron `0 7 * * *` UTC (2:00 a. m. Colombia), `--fecha-corte` como argumento | 0 a 5 (concesión gratuita; ACR Basic si no se usa GitHub Container Registry) |
| API con datos reales | App Service **B1** con Always On (el F1 se duerme y tiene 60 min de CPU al día) | ~13 |

El plan F1 no sirve para el lote: no tiene Always On y se duerme sin tráfico. La cadena de conexión y la contraseña irán en la configuración de la app o en Key Vault, nunca en el repo.

## Volver a desplegar el API

```bash
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.10.7-hotspot"
./mvnw -q -B package
az webapp deploy -g rg-motor-cartera -n motor-cartera-api-jc \
  --src-path target/motor-cartera-0.0.1-SNAPSHOT.jar --type jar
curl https://motor-cartera-api-jc.azurewebsites.net/api/v1/catalogo
```

Configuración de la app (ya aplicada):
- `SERVER_PORT=80`
- `JAVA_OPTS=-Xmx512m -XX:+UseSerialGC`
- runtime `JAVA:21-java21`
- solo HTTPS

## Apagar

```bash
az webapp stop -g rg-motor-cartera -n motor-cartera-api-jc     # detener
az webapp delete -g rg-motor-cartera -n motor-cartera-api-jc   # borrar
az appservice plan delete -g rg-motor-cartera -n plan-motor-cartera-f1
```
