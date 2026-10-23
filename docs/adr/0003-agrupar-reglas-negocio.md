# ADR 0003 · Agrupar ReglasNegocio en sub-records

**Estado:** propuesta, no se implementa · 2026-10-23.

## Contexto

`ReglasNegocio` es un record plano de 19 componentes y cada ronda de Diego le suma claves. El refactor del 2026-10-23 ya resolvió el costo de agregar una regla sin cambiar la forma pública:

- `CLAVES` sale de los componentes del record por reflexión (ya no es un `Set` escrito a mano).
- `ReglasNegocio.Constructor` (`copia()` / `construir()`) arma las reglas por nombre; los `conX` quedan como delegados de una línea. Solo `construir()` lista los argumentos en orden, y `ReglasNegocioClavesTest` falla si dos se cruzan, aunque los dos sean booleanos.
- `desde(Map)` recorre una tabla (nombre, parser por tipo, setter del `Constructor`). La clase no carga si la tabla y los componentes no coinciden. `desdeEstricto` rechaza las claves desconocidas y `ReglasNegocioArchivoEstrictoTest` lo usa con `config/reglas-negocio.properties`.

Hoy, agregar una regla toca: el componente (con su javadoc), el campo y el setter del `Constructor`, el argumento de `construir()`, el valor en `EXCEL`, la línea de la tabla y, si se quiere, un `conX`. Todo queda en el mismo archivo y las pruebas genéricas avisan si falta algo.

Queda abierta la pregunta de si conviene agrupar los componentes en records cohesivos, por ejemplo:

| Grupo | Componentes |
|---|---|
| `ReglasPago` | prelacion, aplicacionAbono, interesPrepagoPorDias |
| `ReglasCausacion` | formulaMora, baseInteres, baseServicios, serviciosDespuesDelPlazo, prorrateoPrimeraCuota, diasMora |
| `ReglasCobranza` | topeCobranzaPorPago, cobranzaDiaria, cobranzaIngresoNoOperacional |
| `ReglasTasa` | margenUsura, modalidadUsura, cambioTasaSobreCapitalVigente |
| `ReglasRecuperacion` | moraAntesDeRecuperacion, costoRecuperacionAlFondo, plazoLimiteMeses, cuotasVencidasParaRecuperacion |

## Decisión

**No agrupar por ahora.** El record sigue plano y la ayuda para agregar reglas es la del refactor (Constructor, tabla y pruebas genéricas).

## Por qué no

La forma plana es contrato público en varios sitios:

1. **API `/reglas`**: `ServicioCartera.valores` recorre los componentes del record por reflexión y publica un mapa plano `clave → valor` por perfil, en el orden de los componentes. Con sub-records saldrían 5 componentes con valores anidados (`toString()` del record), y se romperían `docs/api-ejemplos`, `ContratoApiTest`, `consola/src/tipos.ts` y la pestaña Reglas de la consola.
2. **El orden no sigue a los grupos.** Las claves están en el orden en que llegaron las rondas: pago (1, 8, 9), causación (2, 3, 5, 6, 18, 19), recuperación (7, 12, 14, 17)… Aplanar por grupo cambia el orden de `/reglas`. Conservarlo obliga a escribir otra vez una lista ordenada a mano, justo lo que el refactor quitó.
3. **Claves planas en la configuración**: `config/reglas-negocio.properties`, `CLAVES_MOTOR` de `herramientas/importar_respuestas.py` y los `ajustes` de `cuestionario/src/preguntas.ts` usan nombres planos (`diasMora`, no `causacion.diasMora`). Se pueden conservar con la tabla de `desde`, pero hay que mantener a mano el mapeo de clave plana a grupo.
4. **Consumidores**: 21 accesos `reglas.x()` en `src/main` (18 en `MotorCartera` y 3 en `ServicioCartera`, más `Contabilizador.para(reglas)`) y decenas en pruebas pasarían a `reglas.grupo().x()`. Son archivos que otros agentes tocan en paralelo, y el cambio no le da nada nuevo al motor.

## Alternativas

- **A. Sub-records con accesos planos de fachada** (`ReglasNegocio` como record de grupos más métodos `diasMora()` que delegan). No rompe a los consumidores del motor, pero sí `/reglas` (por la reflexión sobre componentes) y el orden. También duplica cada regla: el accesor del grupo más la fachada. Descartada.
- **B. Sub-records y un `valores()` explícito que aplane en el orden actual.** Exige cambiar `ServicioCartera`, mantener una lista ordenada a mano y cambiar a los consumidores. Es la opción a usar si algún día se versiona `/reglas` (v2) o la consola pide los grupos.
- **C. Etiquetar cada componente con su grupo** (una anotación `@Grupo("causacion")` en el componente o una columna más en la tabla de `desde`). Así la consola o la documentación pueden agrupar sin tocar la forma. Es barata y compatible. Se puede hacer cuando la consola la necesite.
- **D. Dejarlo plano** (elegida).

## Riesgos de dejarlo plano

- El record sigue creciendo: unos 25 a 30 componentes si las rondas 7 en adelante siguen al ritmo actual. La lectura empeora, pero el riesgo de argumentos cruzados ya lo cubren el `Constructor` y `cadaSetterDelConstructorCambiaSoloSuComponente`.
- `construir()` sigue siendo posicional. Lo protegen las pruebas genéricas, no el compilador.

## Cuándo reconsiderar

- Si se abre una v2 del API, o si la consola pide las reglas agrupadas: alternativa B, junto con el cambio de `Dtos`, `tipos.ts` y los ejemplos.
- Si el record pasa de unos 30 componentes.
