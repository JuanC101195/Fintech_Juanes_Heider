# Plantilla común de los 9 agentes

Texto que recibió cada agente. `{N}`, `{nombre}` y la sección "Reglas del caso / Tus entregables" cambian por agente (ver `casos.md`). El caso 1 recibió además la tarea de las piezas transversales.

---

Eres el agente del **Caso {N} ({nombre})** del motor de reglas de cartera de motos. Trabajas en una copia (git worktree) del repo `motor-cartera` (Java 21 + Spring Boot 4 + Spring Modulith). Otros 8 agentes trabajan en paralelo en los demás casos; tú no tocas sus archivos.

## Antes de empezar
1. Lee `CLAUDE.md`, `docs/decisiones.md` y `docs/adr/0001-monolito-modular.md`. Ahí están comandos, convenciones (todo en español, BigDecimal con `Calc`, pruebas contra el Excel) y decisiones.
2. Pruebas: `export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.10.7-hotspot" && ./mvnw -q -B test -Dtest='Caso{N}*'`. Con `-q` revisa `target/surefire-reports/TEST-*.xml`. Hay otros agentes compilando a la vez: pruebas enfocadas y la suite completa solo al final.
3. Datos: `src/test/resources/excel/<su hoja>.csv`, `inputs.csv`, `validaciones.csv`. Excel original con fórmulas: `docs/fuentes/casos-operaciones-v2.xlsx` (léelo con Python + openpyxl, `python -I`, `data_only=False`). No edites los CSV a mano.
4. Modelo: `src/test/java/co/financiera/cartera/casos/Caso1NormalTest.java` (`ComparadorExcel`, `HojaExcel`). Motor: `nucleo/motor/MotorCartera.java`.

## Reglas del caso (del Excel)
*(específicas, ver `casos.md`)*

## Tus entregables
*(específicos, ver `casos.md`)*

## Reglas de trabajo
- No toques archivos de otros casos. `MotorCartera.java` es compartido: si necesitas cambiarlo, cambio mínimo, comentado y todas las pruebas en verde.
- Commit en tu rama al terminar, mensaje en español, sin atribución a Claude: `git -c user.name="Juan Esteban Cardozo Rivera" -c user.email="juan.cardozor@udea.edu.co" commit ...`.

## Reporte final (en español, conciso)
1. Qué hiciste y resultado de pruebas.
2. Cambios en `MotorCartera.java` o clases compartidas, con por qué.
3. Discrepancias del Excel y preguntas para Diego.
4. Rama y hash del commit.
