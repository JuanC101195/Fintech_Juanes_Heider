# Caso 11 · Pago inferior y dación

Hoja `Caso11_PagoInfDacion` de `docs/fuentes/casos-operaciones-v2.xlsx`. En el mes 10 (periodo 43) el cliente paga el 40 % de la cuota total, no paga los periodos 44 a 46 y en el 47 entrega la moto en dación por 3.000.000 (Inputs C80: 4 periodos de espera, incluido el cierre).

- Escenario: `EscenariosExcel.caso11` (`PagoParcial(0.4, 0)` en el 43, `SinPago(true)` del 44 al 47 y `CierrePorRecuperacion(DACION_EN_PAGO, 3.000.000)` en el 47).
- Prueba: `src/test/java/co/financiera/cartera/casos/Caso11PagoInfDacionTest.java` (celda por celda contra la hoja).

## Respuestas del documento "rptas software" (2026-10-22)

| Id | Regla | Implementada en | Probada |
|---|---|---|---|
| PPER-9 | No hay días específicos: la política es ejecutar la **dación o la retoma después de 2 cuotas atrasadas**, contadas en periodos de la periodicidad pactada (2 semanas, 2 quincenas o 2 meses). Clave `cuotasVencidasParaRecuperacion` (`2` en `config/`; `null` en `EXCEL`). El motor **no ejecuta la recuperación solo** (es la pregunta R6-RECUP-AUTO): marca el crédito **"listo para recuperación"** en `Movimiento.listoParaRecuperacion` desde el periodo en que llega a 2 cuotas vencidas y mientras siga en mora; si el cliente se pone al día, la marca se quita. La cuenta de cuotas es la del motor (`cuotasVencidas`: periodos sin pago completo desde la última vez al día). En el API: `listoParaRecuperacion` en cada movimiento y `listoParaRecuperacion` / `periodoListoParaRecuperacion` en el resumen. **Fuente: respuesta de Diego 2026-10-22 (documento 'rptas software'), pregunta PPER-9.** | `MotorCartera.procesarPeriodo`, `ReglasNegocio.cuotasVencidasParaRecuperacion` / `llegaAlUmbralDeRecuperacion` | `RespuestasDiegoDocumentoTest.Pper9ListoParaRecuperacion`, `RespuestasDocumentoApiTest.pper9ElCreditoQuedaListoParaRecuperacionConDosCuotasAtrasadas` |

**El caso 11 del Excel no cambia.** Con `EXCEL` ningún movimiento se marca y la dación sigue en el periodo 47 (4 periodos de espera), celda por celda contra la hoja. Con `NEGOCIO` el crédito queda listo para recuperación en el periodo 44 (2 cuotas vencidas: el pago inferior del 43 y el no pago del 44), pero como el motor no ejecuta la dación, el cierre sigue en el 47 porque así lo ordena el escenario. Si el caso 11 se ajusta a 2 periodos es la pregunta R6-RECUP-VS-CASO11.

**Límite de la cuenta.** Un cliente que paga la cuota completa pero tarde (caso 9: 14 días de atraso en cuotas semanales) no suma cuotas vencidas en el motor, aunque al pagar ya deba 2 cuotas (pregunta P9.4 del caso 9). Por eso el caso 9 nunca se marca.

Preguntas abiertas (ronda 6):

- **R6-RECUP-AUTO**: al llegar a 2 cuotas atrasadas, ¿el motor ejecuta la dación o la retoma solo, o solo marca el crédito y una persona la ordena? ¿Qué pasa si el cliente se pone al día antes de ejecutarla?
- **R6-RECUP-VS-CASO11**: con la política de 2 cuotas, ¿se ajusta el caso 11 a 2 periodos (se corrige el Excel)?
