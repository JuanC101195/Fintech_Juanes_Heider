---
name: proyecto-motor-cartera
description: Retomar el MOTOR DE REGLAS DE CARTERA (repo Desktop\motor-cartera, Java 21 + Spring Modulith, respaldo github JuanC101195/Fintech_Juanes_Heider) que reproduce el Excel "Casos Operaciones V2" de Diego González (9 casos, cuotas semanales, asientos contables). Usar SIEMPRE que Juan Esteban diga "motor de cartera", "motor-cartera", "los 9 casos", "Casos Operaciones V2", "MotorCartera", "el cuestionario de Diego", "preguntas para Diego", "prelación", "caso 8 / caso 9", "Fintech_Juanes_Heider", o pregunte en qué vamos con el motor. NO es la app Prestaya (repo JuanDavidGarciaGarcia/Fintech, skill proyecto-fintech-prestaya) ni el backlog del equipo en Azure DevOps.
---

# Motor de reglas de cartera (motor-cartera)

## Cuál repo es cuál (no mezclar)

| | **motor-cartera** (este) | **Fintech / Prestaya** | **Financiera (equipo)** |
|---|---|---|---|
| Qué es | Motor de reglas: ante cada abono decide el caso del Excel, lo aplica y genera el asiento | App web React para asesores de concesionarios (originar, simular) | Proyecto Azure DevOps `motosdelcaribe/Financiera`: backlog y sprints del equipo |
| Dónde | `C:\Users\LeNoVo\Desktop\motor-cartera` | `C:\Users\LeNoVo\Desktop\Fintech` | Azure DevOps |
| Remoto | `respaldo` → github.com/JuanC101195/Fintech_Juanes_Heider (**público**) | `origin` → github.com/JuanDavidGarciaGarcia/Fintech (privado) | — |
| Stack | Java 21, Spring Boot 4.1.1, Spring Modulith, Maven | React 19 + Vite + TS + Tailwind | — |
| Skill | esta | `proyecto-fintech-prestaya` | — |

- El **backlog y la priorización de HU** (`priorizacion_hu.tex`, CSV y scripts de carga a Azure) son para el equipo de Financiera. La copia vigente vive en `backlog-financiera/` de este repo; en `Desktop\Fintech\docs\backlog\` hay una copia idéntica **sin commitear** (no pertenece al repo Fintech).
- Nunca commitear cosas del motor en el repo Fintech ni al revés.

## Cómo se trabaja

Leer primero `CLAUDE.md` (comandos, arquitectura, convenciones) y `docs/decisiones.md`. Resumen:
- JDK: `export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.10.7-hotspot"`; pruebas `./mvnw -q -B test` (resultados en `target/surefire-reports`).
- Fuente de verdad: `docs/fuentes/casos-operaciones-v2.xlsx`; CSV de prueba con `python herramientas/exportar_excel.py` (no editarlos a mano).
- Si el Excel es inconsistente se reproduce tal cual y se anota como pregunta en `docs/reglas/caso-N.md`.
- Todo en español, dinero en `BigDecimal` (`Calc`), sin atribución a Claude en commits.
- Diagramas al estilo **archify** (github.com/tt-a1i/archify, clonado en `Desktop\herramientas-ext\archify`), fuente en `docs/diagrama/fuente/generar.mjs`; no dibujarlos a mano.

## Dónde quedamos (2026-10-08)

**Hecho**
- Los 9 casos del Excel se reproducen celda por celda: **124 pruebas, 0 fallas** (`casos/Caso1..9*Test`).
- Un solo ciclo semanal (`nucleo/motor/MotorCartera`), prelación cobranza → mora → interés → servicios → capital, contabilidad en partida doble (`contabilidad/`).
- Decisión: cuota inicial = 10 % del valor total de la operación (`motoEstandar()`; `replicaExcel()` usa 0).
- `cuestionario/`: front para que Diego responda las preguntas viendo el efecto calculado por el motor; exporta Excel → `herramientas/importar_respuestas.py` → `config/reglas-negocio.properties`.
- Documentación: README, `docs/agentes` (9 agentes en worktrees), diagrama archify, `backlog-financiera/` con el contrato de capacidad y la priorización en 3 sprints (98 puntos; Sprint 1 = caso 1 + base técnica).

**Esperando a negocio**
- Respuestas de Diego al cuestionario (`docs/preguntas-diego.md`): P1 prelación, P2 redondeo, P3 mora única, P7 mora en dación/retoma, P-doble (interés sobre capital vencido), P-horizonte (caso 9 nunca termina). Al llegar el Excel: importar y correr `ReglasNegocioConfiguradaTest`.

**Siguiente** (ver `docs/pendientes-tecnicos.md`)
1. Contabilidad del caso 8, semanas 44-89 (recaudo con vencidos, reclasificación vencida ↔ vigente).
2. `plazoLimiteSemanas` derivado del plazo (hoy 120 fijo).
3. Módulos de los sprints 2-3: `creditos` (PostgreSQL, historial inmutable, versionado de parámetros), `abonos` (clasificador, idempotencia, API para el CRM), `procesos` (cierre diario), `integracion-crm` (outbox).
4. Herramienta que llene "Resultado software" de la hoja Validaciones para devolverle el Excel a Diego.

Al cerrar una sesión de trabajo: actualizar esta sección (fecha, conteo de pruebas, qué quedó pendiente).
