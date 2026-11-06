# Respuestas de Diego a la ronda 14 e instrucciones nuevas (5 de noviembre de 2026)

Sesión con Diego González del 5 nov 2026. La ronda 14 no cambia la lógica del motor; Diego dio dos instrucciones nuevas (D88, temporal, y D89) que se implementan con supuestos (ADR 0009) y se preguntan en la ronda 15.

## Ronda 14 (sin cambio de lógica)

| Pregunta | Lo que dijo Diego | Qué queda |
|---|---|---|
| R14-SERVICIOS-CIERRE | Pidió volver a preguntarla en la próxima sesión | Sigue abierta en el cuestionario (D87 pendiente); el motor sigue extinguiendo lo no recaudado contra las CxC |
| R14-MARCA-SEMANAS | Pidió un ejemplo detallado | Sigue abierta, ahora con el ejemplo calculado por el motor (abajo) |

Ejemplo para R14-MARCA-SEMANAS (operación del Excel, semanal, NEGOCIO `negocio-2026-11-06`, cuota total 168.871): el cliente no paga las semanas 1 a 4. Al cerrar la semana 4 debe 777.590,49 (4,60 cuotas) y está marcado. En la semana 5 paga 800.000: el contador de cuotas vencidas sigue en 4 (solo vuelve a 0 cuando paga todo), pero el saldo en mora baja a 147.880,29 (0,88 cuotas) y la marca se quita. Si «2 semanas de mora» fuera contar periodos atrasados, seguiría marcado. Prueba: `RespuestasDiegoRonda15Test.ejemploDeLaMarcaConCuatroPeriodosAtrasadosSinMarca`.

## Instrucciones nuevas

| # | Lo que dijo Diego | Decisión | Supuestos (ronda 15) |
|---|---|---|---|
| 1 | «Vamos a cambiar temporalmente la política de cobranza: si el cliente paga atrasado así sea 1 día, se le cobra un cargo de cobranza de $25.000 (se conserva su posición en la prelación de aplicación de pagos)» | D88 (**temporal**): `cobranzaPorCuotaAtrasada=25000` | 25.000 por cuota, una vez, cuando la cuota vence sin quedar pagada a tiempo (atraso de 1 día o más, pago parcial o sin pago), medido contra la cuota del periodo (R15-COBRANZA-CUOTA); reemplaza la cobranza anterior de 40.000 / 3.000 diarios (R15-COBRANZA-REEMPLAZA); sin fecha de fin, se apaga con la clave (R15-COBRANZA-FIN) |
| 2 | «Cambiaremos la base 365 a base 360 para el cálculo de las cuotas» | D89: `baseDiasAnio=360` | Periódica (1 + EA)^(días/360) − 1 con 7, 15 o 30 días y diaria (1 + EA)^(1/360) − 1; solo cambia la semanal (R15-BASE-360) |

## Qué cambió en el motor (versión de reglas `negocio-2026-11-06`)

- Cobranza NEGOCIO: 25.000 por cada cuota que vence sin quedar pagada a tiempo, primero en la prelación y con el asiento de siempre. Con la operación del Excel, la cuota 2 pagada 1 día tarde causa 25.000 (más 50,09 de mora) y nada más; tres semanas sin pago causan 75.000. Ya no se causan los 40.000 por periodo con cociente ≥ 2 ni los 3.000 diarios (para volver a ellos: `cobranzaPorCuotaAtrasada=0`).
- Base 360: la cuota semanal de la operación del Excel (5.904.400, EA 87,62 %, 104 semanas) pasa de 100.406,27 a 100.972,38 (cuota total al peso de 168.305 a 168.871); el ejemplo de 88 cuotas (5.533.760, EA 88,44 %) de 103.137,22 a 103.651,56. Quincenal y mensual no cambian. La amortización sigue cerrando en 0 en la cuota N.
- El perfil EXCEL no cambia (casos 1 a 12 contra el Excel en verde).

## Preguntas nuevas (ronda 15)

- **R15-COBRANZA-CUOTA:** frecuencia y alcance del cargo de 25.000 (por cuota, una vez; frente a cada periodo mientras siga en mora o contra todo lo pendiente).
- **R15-COBRANZA-REEMPLAZA:** si reemplaza la cobranza anterior (40.000 con cociente ≥ 2) o se suma.
- **R15-COBRANZA-FIN:** hasta cuándo rige la política temporal y si aplica a los créditos vivos o solo a los nuevos.
- **R15-BASE-360:** la fórmula de la base 360 y su alcance (solo la cuota, también la mora y el prorrateo por días, los servicios).
- Siguen abiertas **R14-SERVICIOS-CIERRE** y **R14-MARCA-SEMANAS**.
