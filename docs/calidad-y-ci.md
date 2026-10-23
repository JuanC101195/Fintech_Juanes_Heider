# Calidad y CI

## Qué corre en cada PR (`.github/workflows/pruebas.yml`)

| Job | Qué hace | Bloquea el PR |
|---|---|---|
| `motor` | `./mvnw verify`: JUnit + JaCoCo. Incluye `ModularidadTest` y `ArquitecturaTest` (ArchUnit), `ContratoApiTest` (claves de `docs/api-ejemplos` y de `consola/src/tipos.ts` contra los records de `Dtos`) y `EjemplosApiTest` (compara los ejemplos comprometidos con el API). Después verifica que `docs/api-ejemplos` no cambió. | Sí |
| `cuestionario` | `npx vitest run --coverage` | Sí |
| `consola` | `npx vitest run --coverage` y `npm run build` (`tsc -b` + vite) | Sí |
| `herramientas` | `python -I -m unittest discover -s herramientas -p "test_*.py"` (Python 3.12 + openpyxl; sin red ni Azure) | Sí |
| `humo` | Playwright/Chromium: `consola/pruebas/humo.mjs`, `cuestionario/pruebas/humo.mjs` y `consola/pruebas/e2e.mjs` contra el jar del API levantado en el runner | No (`continue-on-error`) hasta que pase estable una vez |

Cada job sube su cobertura como artefacto (`cobertura-java`, `cobertura-cuestionario`, `cobertura-consola`) y el de humo sube las capturas.

### Humo y e2e en CI

Los tres scripts buscaban Playwright en `C:/Users/LeNoVo/Desktop/Fintech/app` (el front de Prestaya). Ahora usan `PLAYWRIGHT_PACKAGE_JSON` si está definida; en CI el job instala `@playwright/test` en una carpeta temporal y apunta ahí. No necesitan servicios externos: la consola se sirve desde `dist/` (modo demostración en el humo; en el e2e contra el API real, que es el jar corriendo en `localhost:8080`, con el CORS en `localhost:5173`). El job se escribió sin poder ejecutarlo en GitHub Actions; se probó el humo de la consola en local. Cuando pase en verde, quitar `continue-on-error`.

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
