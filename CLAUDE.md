# Motor de reglas de cartera · guía para Claude

Repo **interno** (Juan Esteban + Claude), adelantado al trabajo del equipo de Financiera. Motor de reglas de cartera para créditos de moto con **cuotas semanales**: ante cada abono decide qué caso del Excel aplica, lo aplica y genera el asiento contable. Fuente de verdad de las reglas: `docs/fuentes/casos-operaciones-v2.xlsx` (Excel de Diego González).

## Comandos

```bash
# JDK 21 (el de Microsoft; el JDK 25 también está instalado pero el proyecto es Java 21)
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.10.7-hotspot"
./mvnw -q -B test                                   # todas las pruebas
./mvnw -q -B test -Dtest='Caso3*'                   # una clase de prueba
python herramientas/exportar_excel.py               # regenera los CSV de src/test/resources/excel desde el Excel
```

`-q` no imprime el resumen: los resultados están en `target/surefire-reports/TEST-*.xml` (atributos `tests`, `failures`, `errors`).

## Arquitectura: monolito modular (Spring Modulith)

```
co.financiera.cartera
├── nucleo/            cálculo puro, sin Spring ni base de datos (módulo OPEN: los demás lo usan)
│   ├── Calc, ParametrosProducto, Operacion, Tasas, Calendario, Amortizacion, Servicios
│   └── motor/         MotorCartera (ciclo semanal único), Escenario, Instruccion, Movimiento, EstadoCredito
└── contabilidad/      Cuenta, Asiento, ReglaContable, Contabilizador (+ reglas/ por evento)
```

`ModularidadTest` falla si un módulo usa internos de otro o hay ciclos. Módulos futuros: `creditos` (persistencia, historial), `abonos` (clasificador, idempotencia), `integracion-crm`, `procesos`, `api`.

### Cómo funciona el motor

Un solo ciclo semanal (`MotorCartera.Corrida.semana`) para los 9 casos:
1. Eventos de tasa (caso 2): nueva cuota = PMT(tasa nueva, cuotas restantes + 1, saldo).
2. Causación: interés corriente = saldo × tasa semanal; capital contractual del plan vigente; servicios (FCC, asistencia, seguro) solo hasta la cuota 86; mora = capital vencido × tasa semanal + capital contractual × ((1 + tasa diaria)^días − 1); cobranza = días × 3.000.
3. Pago por **prelación**: cobranza → mora → interés corriente → servicios → capital (P1, según fórmulas de los casos 8 y 9).
4. Abono extra (menor plazo / menor cuota) o prepago total, después de la cuota.
5. Cierre por recuperación (dación / retoma): extingue capital + todo lo pendiente contra el valor de la moto.
6. Estado, cuotas vencidas y giro mensual a terceros.

Cada caso del Excel es un `Escenario`: la operación, los parámetros y las instrucciones por semana (`Instruccion`: `PagoContractual`, `PagoParcial`, `PagoValor`, `SinPago`, `AbonoExtra`, `PrepagoTotal`, `CambioTasa`, `CierrePorRecuperacion`). Las semanas sin instrucción son pagos completos a tiempo.

## Cuestionario de reglas para negocio (`cuestionario/`)

Front (React 19 + Vite + Tailwind 4 con los tokens de Prestaya) para que Diego responda las preguntas abiertas viendo el efecto de cada opción. Las cifras vienen del motor real, no del navegador.

```bash
cd cuestionario
npm install
npm run escenarios   # corre GeneradorEscenarios (Java) → src/datos/escenarios.json
npm test             # integridad de preguntas/escenarios y exportación a Excel
npm run build        # dist/index.html: un solo archivo para enviar por correo
node pruebas/humo.mjs   # Chromium: celular y escritorio sin desbordes + descarga del Excel
```

Flujo: Diego responde → descarga el Excel → `python herramientas/importar_respuestas.py <excel>` escribe `config/reglas-negocio.properties` (lo que el motor ya aplica, `ReglasNegocio.CLAVES`) y `docs/respuestas-negocio.md` (todo, con lo pendiente de desarrollo) → `./mvnw -q -B test`: `ReglasNegocioConfiguradaTest` corre los casos con esas reglas.

- Preguntas en `cuestionario/src/preguntas.ts`. Los códigos de opción y las claves de `ajustes` los lee el importador: no cambiarlos sin actualizarlo.
- Una pregunta con previsualización necesita su entrada en `GeneradorEscenarios` (misma clave de opción).
- `ReglasNegocio.EXCEL` es el comportamiento del Excel; las pruebas de los casos 1 a 9 siempre corren con él.

## Decisiones vigentes

Ver `docs/decisiones.md`. Las más importantes:
- **Cuota inicial = 10 % del valor total de la operación** (moto + alistamiento + GPS + gastos comerciales). `ParametrosProducto.motoEstandar()` la trae; `replicaExcel()` la pone en 0 porque el Excel no tiene inicial.
- Sin redondeo: `BigDecimal` con `Calc.MC` (DECIMAL128). Tolerancia de las pruebas: 1 COP en dinero, 0,000001 en tasas.
- Las potencias fraccionarias ((1+EA)^(1/52)) se calculan en `double` como Excel (`Calc.potencia`).

## Convenciones

- Todo en **español**: clases, métodos, variables, comentarios, commits.
- Dinero siempre `BigDecimal` con los métodos de `Calc` (nunca `double`, nunca `new BigDecimal(double)` para dinero).
- **Pruebas primero** y contra el Excel: cada caso tiene `src/test/java/.../casos/CasoN*Test.java`, que compara el motor celda por celda con `ComparadorExcel` y la hoja exportada a CSV. No editar los CSV a mano: se regeneran con el exportador.
- Si el Excel tiene algo inconsistente, **no se "corrige" en silencio**: se reproduce tal cual (detrás de una opción si hace falta) y se anota en `docs/reglas/caso-N.md` como pregunta para Diego.
- Cada regla documentada en `docs/reglas/caso-N.md` con id (`R-N.k`), fórmula y celda del Excel de donde sale.
- Git: sin atribución a Claude en commits (ni Co-Authored-By ni "Generated with").

## Trabajo en paralelo (un agente por caso)

Para no chocar:
- Cada agente es dueño de: `casos/CasoN*Test.java`, `docs/reglas/caso-N.md` y su regla contable en `contabilidad/reglas/`.
- `MotorCartera.java` es compartido: cambios mínimos, con comentario de por qué, y **todas** las pruebas en verde al terminar (`./mvnw -q -B test`).
- Registrar la regla contable con una línea en `Contabilizador.estandar()`.
