# Motor de reglas de cartera · créditos de moto

## Accesos

| | Qué es | Para qué sirve |
|---|---|---|
| 🧮 [**Consola del motor**](https://juanc101195.github.io/Fintech_Juanes_Heider/consola/) | Aplicación web | Simular créditos, ver los 13 casos y probar abonos, tasas y daciones. La primera carga puede tardar unos 30 s porque el servidor gratuito despierta. |
| 📝 [**Cuestionario de reglas**](https://juanc101195.github.io/Fintech_Juanes_Heider/cuestionario.html) | Formulario web | Las preguntas abiertas para negocio. Las respuestas se guardan solas. |
| 🗺️ [**Diagrama del proyecto**](https://juanc101195.github.io/Fintech_Juanes_Heider/diagrama.html) | Diagrama interactivo | Ver cómo encajan las piezas del proyecto. |
| 🏠 [Inicio](https://juanc101195.github.io/Fintech_Juanes_Heider/) | Sitio | Portada con todos los enlaces. |
| 🔌 [API (técnicos)](https://motor-cartera-api-jc.azurewebsites.net/api/v1/catalogo) | Servicio HTTP | Catálogo de líneas de crédito; el resto del contrato está en [`docs/api.md`](docs/api.md). |

## Qué es

Un motor de reglas de cartera para créditos de moto. Ante cada pago decide qué caso aplica, lo aplica por prelación (cobranza, mora, interés, servicios y capital) y genera el asiento contable en partida doble. Reproduce el Excel de casos operativos del negocio ([`docs/fuentes/`](docs/fuentes/)). Maneja periodicidad semanal, quincenal y mensual.

## Cómo está armado

Monolito modular en Java 21 y Spring Boot (Spring Modulith), más las aplicaciones que lo rodean. El detalle está en el [diagrama](https://juanc101195.github.io/Fintech_Juanes_Heider/diagrama.html).

| Pieza | Qué hace |
|---|---|
| `src/` · `nucleo` | Ciclo de cada periodo: causación, prelación, abonos, prepago, dación y retoma |
| `src/` · `contabilidad` | Asientos en partida doble de cada evento |
| `src/` · `simulaciones` | Casos y escenarios que se calculan con el motor |
| `src/` · `api` | API HTTP `/api/v1` que expone el motor |
| [`consola/`](consola/README.md) | Consola web (React) para analistas |
| [`cuestionario/`](cuestionario/) | Formulario web de reglas abiertas para negocio |
| [`nube/`](nube/) | Funciones de Azure: guardan las respuestas del cuestionario y sirven la consulta de contratos |
| [`herramientas/`](herramientas/) | Scripts: exportar el Excel a CSV e importar respuestas de negocio |

## Para desarrollar

Requisitos: JDK 21, Node 20+ y Python 3.

```bash
# Pruebas Java
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.10.7-hotspot"
./mvnw -q -B test

# API en http://localhost:8080
./mvnw spring-boot:run

# Consola en http://localhost:5173 (ese puerto, por el CORS del API)
cd consola && npm install && npm run dev

# Cuestionario
cd cuestionario && npm install && npm run dev

# Leer las respuestas de negocio (requiere az login)
python herramientas/importar_respuestas.py --nube --ver
```

## Estado y documentos

| Documento | Para qué sirve |
|---|---|
| [`docs/decisiones.md`](docs/decisiones.md) | Decisiones del motor y preguntas abiertas |
| [`docs/completitud-reglas.md`](docs/completitud-reglas.md) | Qué reglas están cubiertas y cuáles faltan |
| [`docs/adr/`](docs/adr/) | Decisiones de arquitectura (monolito modular, periodicidad configurable) |
| [`docs/reglas/`](docs/reglas/) | Una regla por caso, con sus cifras |
| [`docs/api.md`](docs/api.md) | Contrato del API |
| [`docs/despliegue.md`](docs/despliegue.md) | Cómo se publica cada pieza |

## Consulta de contratos del backtesting

Página: https://juanc101195.github.io/Fintech_Juanes_Heider/contratos.html

Se escribe el número de un contrato de Motos del Caribe y se ve la tabla de amortización que el motor calculó con el comportamiento de pago real de ese contrato (con y sin justificaciones), junto a un resumen contra lo pagado en el sistema actual, para compararla con el sistema de la empresa. Requiere una clave de consulta que se entrega aparte a quien corresponda. Los datos nunca están en el repo ni en la página: viven en una base PostgreSQL privada y la página los pide a una Azure Function protegida con esa clave (`?demo=1` muestra un contrato inventado). Arquitectura y operación: [`docs/despliegue.md`](docs/despliegue.md).

## Despliegue

- **API**: Azure App Service F1 (gratuito).
- **Consola, cuestionario y diagrama**: GitHub Pages, se publican solos al mergear a `main`.
- Detalle en [`docs/despliegue.md`](docs/despliegue.md).
