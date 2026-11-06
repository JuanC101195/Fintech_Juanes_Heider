# Calidad y CI

## Qué corre en cada PR (`.github/workflows/pruebas.yml`)

| Job | Qué hace | Bloquea el PR |
|---|---|---|
| `motor` | `./mvnw verify`: JUnit + JaCoCo. Incluye `ModularidadTest` y `ArquitecturaTest` (ArchUnit), `ContratoApiTest` (claves de `docs/api-ejemplos` y de `consola/src/tipos.ts` contra los records de `Dtos`) y `EjemplosApiTest` (compara los ejemplos comprometidos con el API). Después verifica que `docs/api-ejemplos` no cambió. | Sí |
| `cuestionario` | `npx vitest run --coverage` | Sí |
| `consola` | `npx vitest run --coverage` y `npm run build` (`tsc -b` + vite) | Sí |
| `herramientas` | `python -I -m unittest discover -s herramientas -p "test_*.py"` (Python 3.12 + openpyxl; sin red ni Azure) | Sí |
| `humo` | Playwright/Chromium: `consola/pruebas/humo.mjs`, `cuestionario/pruebas/humo.mjs`, `docs/sitio/pruebas/contratos-humo.mjs` y `consola/pruebas/e2e.mjs` contra el jar del API levantado en el runner | Sí (ya sin `continue-on-error`) |

Cada job sube su cobertura como artefacto (`cobertura-java`, `cobertura-cuestionario`, `cobertura-consola`) y el de humo sube las capturas.

### Humo y e2e en CI

Los scripts de humo buscaban Playwright en `C:/Users/LeNoVo/Desktop/Fintech/app` (el front de Prestaya). Ahora usan `PLAYWRIGHT_PACKAGE_JSON` si está definida (`contratos-humo.mjs`: si no, el front de Prestaya cuando existe y, si no, el `package.json` de la carpeta actual); en CI el job instala `@playwright/test` en una carpeta temporal y apunta ahí. No necesitan servicios externos: la consola se sirve desde `dist/` (modo demostración en el humo; en el e2e contra el API real, que es el jar corriendo en `localhost:8080`, con el CORS en `localhost:5173`). El job no se ha podido ejecutar en GitHub Actions todavía (se probaron los humos en local); el primer PR con este cambio lo confirma. Si falla por razones del entorno del runner, corregirlo en el job; no volver a poner `continue-on-error`.

## Despliegues manuales (solo `workflow_dispatch`)

No corren solos: se lanzan desde la pestaña Actions de GitHub.

| Workflow | Qué hace | Secretos de GitHub |
|---|---|---|
| `despliegue-funcion.yml` | Copia `nube/` sin pruebas a una carpeta limpia, `npm ci --omit=dev` (el zip lleva `node_modules` con `pg`; falla si falta) y `az functionapp deployment source config-zip` a `fn-motor-cartera-respuestas` | `AZURE_CREDENTIALS` (JSON de un service principal con permiso sobre la función, para `azure/login`), `AZURE_RESOURCE_GROUP` (grupo de recursos de la función) |
| `despliegue-api.yml` | `./mvnw -DskipTests package` y `azure/webapps-deploy` del jar a `motor-cartera-api-jc` | `AZURE_WEBAPP_PUBLISH_PROFILE` (contenido del perfil de publicación del App Service) |

Los secretos se crean en Settings > Secrets and variables > Actions; no hay valores en el repo. Los flujos no se han ejecutado nunca: la primera corrida real es la prueba (confirmar con `curl` al catálogo del API y a `/api/contrato` de la función, ver `docs/despliegue.md`).

## Ejemplos del API (`docs/api-ejemplos`)

`EjemplosApiTest` ya no reescribe los archivos en cada corrida (ensuciaba el árbol de trabajo). Los compara con la respuesta real del API y, si difieren, falla con el mensaje `./mvnw test -Dtest=EjemplosApiTest -Dregenerar=true`. Solo esa bandera reescribe. `.gitattributes` fija `docs/api-ejemplos/*.json text eol=lf` para que Windows no los convierta a CRLF.

## Cobertura y SonarCloud

SonarCloud analiza con **análisis automático** (`.sonarcloud.properties`): lee el código fuente pero **no recibe informes de cobertura**. Por eso `sonar.coverage.jacoco.xmlReportPaths` y `sonar.javascript.lcov.reportPaths` del `pom.xml` solo sirven para un análisis local con SonarQube; en SonarCloud la cobertura aparece vacía.

- `consola/` **no está** en `sonar.sources` (solo `cuestionario/src`), así que su `lcov.info` no se agregó a `sonar.javascript.lcov.reportPaths`: Sonar lo ignoraría, y además sus rutas (`src/...`) son relativas a `consola/`, no a la raíz del repo.
- Para que la cobertura llegue a SonarCloud hay que pasar a **análisis desde CI**: desactivar el análisis automático en SonarCloud, agregar un paso `./mvnw verify sonar:sonar` (o `SonarSource/sonarcloud-github-action`) con `SONAR_TOKEN` en los secretos, bajar los artefactos `lcov.info` de la consola y el cuestionario, agregar `consola/src` a `sonar.sources` y `consola/src/**/*.test.{ts,tsx}` a las exclusiones, y apuntar `sonar.javascript.lcov.reportPaths` a `cuestionario/coverage/lcov.info,consola/coverage/lcov.info` (con `sonar.javascript.lcov.reportPaths` Sonar resuelve las rutas del lcov contra la raíz: hay que reescribir `SF:src/` a `SF:consola/src/` o ejecutar el scanner desde cada carpeta). No se hizo porque exige un secreto y cambiar la configuración del proyecto en SonarCloud.

## Pruebas de mutación (PIT)

Perfil `pit` del `pom.xml` (pitest 1.19.1 + junit5-plugin 1.2.2) sobre `nucleo.*` y `nucleo.motor.*`, umbral 85 % de mutantes muertos. Es lento: solo para uso manual o nocturno, no en cada PR.

```bash
./mvnw -B -Ppit test-compile org.pitest:pitest-maven:mutationCoverage
# informe en target/pit-reports/index.html
```

`MotorLimitesTest` y `CalcLimitesTest` se escribieron contra los mutantes que sobrevivieron en la primera auditoría (límites de `MotorCartera`, `Calc`, `Amortizacion`, `Tasas`, `VigenciasCobranza`).
