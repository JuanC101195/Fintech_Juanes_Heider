# Guía rápida para consumir el API del motor de cartera

Para equipos que integran el API desde otra aplicación (por ejemplo, la sede de Cartagena).

## Dónde está

| | |
|---|---|
| URL base | `https://motor-cartera-api-jc.azurewebsites.net` |
| Documentación interactiva (Swagger UI) | `https://motor-cartera-api-jc.azurewebsites.net/swagger-ui.html` |
| Contrato OpenAPI (JSON) | `https://motor-cartera-api-jc.azurewebsites.net/v3/api-docs` |
| Contrato OpenAPI (YAML) | `https://motor-cartera-api-jc.azurewebsites.net/v3/api-docs.yaml`; la misma copia está en [`openapi.yaml`](openapi.yaml) |

El contrato YAML se importa directo en Postman, Insomnia o un generador de clientes (`openapi-generator`).

## Autenticación

Cada petición a `/api/v1/**` lleva la cabecera `X-API-Key` con la clave que entrega la financiera. Hay dos tipos de clave:

- **CONSUMIDOR:** todo el API.
- **LECTURA:** solo las consultas GET (catálogo, reglas y casos).

Sin clave válida el API responde `401`; con una clave de lectura, los POST responden `403`. En Swagger UI la clave se pone una sola vez con el botón **Authorize**.

La documentación (`/swagger-ui.html` y `/v3/api-docs`) no pide clave: solo describe el contrato, no devuelve datos.

## Primer llamado

```bash
curl -X POST https://motor-cartera-api-jc.azurewebsites.net/api/v1/simulaciones \
  -H 'Content-Type: application/json' -H 'X-API-Key: <su clave>' \
  -d '{"linea":"CPPU","periodicidad":"SEMANAL","cuotas":104,"valorMoto":"5100000","pagoInicial":"0","perfilReglas":"NEGOCIO"}'
```

Responde el resumen del crédito (cuota, servicios, totales) y el plan de pagos completo.

## Lo que conviene saber

- **Dinero y tasas viajan como texto decimal** (`"5904400"`, `"0.8762"`), sin redondear. Conviértalos con un tipo decimal (no `float`/`double`) y redondee solo al mostrar.
- **Fechas:** `yyyy-MM-dd`. Con `fechaDesembolso` y `primerVencimiento` (las dos juntas) la primera cuota se cobra en proporción a los días. Las cuotas semanales vencen los martes, las quincenales el 5 y el 20 y las mensuales el 5.
- **Perfil de reglas:** `NEGOCIO` aplica las reglas vigentes de la financiera; `EXCEL` reproduce el Excel de casos de referencia. Para producción use `NEGOCIO`.
- **Errores:** siempre `{codigo, mensaje, detalle}`. Programe contra `codigo` (estable), no contra `mensaje` (texto para personas). En los errores de validación, `detalle.campo` dice qué dato falló.
- **Primera petición lenta:** el servicio está en un plan gratuito y se duerme si no se usa. Si estuvo inactivo, la primera petición puede tardar unos 30 segundos. Use un timeout de al menos 60 s.
- **Lotes:** `POST /api/v1/escenarios/lotes` acepta hasta 500 escenarios y responde NDJSON (una línea JSON por escenario).

## Endpoints

| Método | Ruta | Para qué |
|---|---|---|
| GET | `/api/v1/catalogo` | Líneas de crédito, periodicidades, perfiles y tipos de instrucción |
| GET | `/api/v1/reglas` | Reglas de negocio vigentes y de dónde sale cada una |
| POST | `/api/v1/simulaciones` | Cuota y plan de pagos de un crédito nuevo |
| POST | `/api/v1/escenarios` | Un crédito con pagos tarde, parciales, abonos, prepagos, cambios de tasa, dación o retoma |
| POST | `/api/v1/escenarios/lotes` | Hasta 500 escenarios en una petición (NDJSON) |
| GET | `/api/v1/casos` | Los 13 casos de referencia |
| GET | `/api/v1/casos/{numero}` | Un caso de referencia con su resultado |
| POST | `/api/v1/clasificaciones` | La clasificación de un pago: en qué caso de negocio cae |

El detalle de cada campo está en Swagger UI y en [`api.md`](api.md).
