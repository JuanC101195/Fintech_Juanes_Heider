# Despliegue

| Pieza | Dónde | URL |
|---|---|---|
| API REST (`co.financiera.cartera.api`) | Azure App Service, plan **F1 gratuito** Linux, Java 21 (`rg-motor-cartera`, North Central US) | https://motor-cartera-api-jc.azurewebsites.net/api/v1/catalogo |
| Consola | GitHub Pages (workflow `Sitio`, carpeta `consola/`) | https://juanc101195.github.io/Fintech_Juanes_Heider/consola/ |
| Cuestionario | GitHub Pages | https://juanc101195.github.io/Fintech_Juanes_Heider/cuestionario.html |
| Respuestas del cuestionario | Azure Function `fn-motor-cartera-respuestas` + tabla `respuestas` | — |

## Costo

- El plan F1 es gratuito. Tiene una cuota de CPU de 60 minutos al día y se duerme cuando no se usa: la primera petición después de un rato tarda unos segundos mientras arranca Spring.
- El API no tiene base de datos ni guarda estado.
- Si se agota la cuota diaria, el API responde 403 hasta el día siguiente y la consola cae al modo demostración.

## Seguridad

- El API no tiene autenticación. Expone solo los casos del Excel y las reglas del motor, sin datos de clientes.
- El CORS solo admite `http://localhost:5173` y `https://juanc101195.github.io`.
- Antes de cargar datos reales hay que agregar autenticación.

## Reglas de negocio

`config/reglas-negocio.properties` se empaqueta dentro del jar (`pom.xml`, `<resources>`). Si el archivo no está en disco, como pasa en Azure, `PerfilesReglas` lo lee del classpath. Un cambio de reglas exige volver a desplegar.

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
