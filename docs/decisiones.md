# Decisiones del motor

Registro de lo decidido. Lo marcado **provisional** se tomó para avanzar y debe confirmarse con negocio (Diego González / Heider Galvis).

| # | Fecha | Decisión | Estado |
|---|---|---|---|
| D1 | 2026-10-08 | Arquitectura: monolito modular en Java 21 + Spring Boot 4 + Spring Modulith. Ver `adr/0001-monolito-modular.md`. | Tomada |
| D2 | 2026-10-08 | ~~Cuota inicial = 10 % del valor total de la operación.~~ Reemplazada por D10. | Reemplazada |
| D3 | 2026-10-08 | El Excel no tiene cuota inicial: las réplicas de los casos usan operaciones sin pago inicial para comparar al centavo. | Tomada |
| D4 | 2026-10-08 | Sin redondeo interno (BigDecimal DECIMAL128), igual que el Excel. Diego (P-05): no redondear; solo el formato de presentación va a 0 decimales. | Tomada (Diego) |
| D5 | 2026-10-08 | Prelación de abonos según las fórmulas de los casos 8 y 9: cobranza → mora → interés corriente → servicios → capital. El texto de la hoja Casos omite los servicios. | Provisional (P1) |
| D6 | 2026-10-08 | Interés de mora = capital vencido × tasa semanal + capital contractual × ((1 + tasa diaria)^días de atraso − 1). Con días = 0 se reduce a la fórmula del caso 8. | Provisional (P3) |
| D7 | 2026-10-08 | Servicios (fondo de garantías y seguro) sobre el monto financiado inicial, constantes por cuota, solo hasta la cuota pactada final (86). Diego (P-07) confirma la base. | Base tomada (Diego); falta suspender fondo y asistencia en mora |
| D8 | 2026-10-08 | En los casos 3 y 4 el Excel no causa mora, vencido ni servicios mientras el cliente no paga. Se reproduce con `SinPago(false)`. Diego (P-16) pide mora y cobranza: con `moraAntesDeRecuperacion = true` (en `config/`) se causan mora, vencido y servicios. La cobranza falta: se causa por días de atraso de un pago y en esas semanas no hay pago. | Tomada (Diego) |
| D9 | 2026-10-08 | Tasas efectivas: semanal = (1+EA)^(1/52) − 1, diaria = (1+EA)^(1/365) − 1, en doble precisión como Excel. | Tomada |
| D10 | 2026-10-16 | Pago inicial **opcional**, digitado por el asesor (`Operacion.pagoInicial`, 0 por defecto); se resta del valor total y todo el crédito parte de ahí (P-09). | Tomada (Diego) |
| D11 | 2026-10-16 | Abono extraordinario sin elección del cliente → **menor plazo** (`ModalidadAbono.POR_DEFECTO`, P-13). | Tomada (Diego) |
| D12 | 2026-10-16 | Un abono mayor que el saldo se **rechaza** (`AbonoMayorQueElSaldoException`); el Excel lo recortaba con MIN (P-14). | Tomada (Diego) |
| D13 | 2026-10-16 | El interés corriente se causa solo sobre el capital vigente; el vencido paga solo mora (`baseInteres = SALDO_VIGENTE`, P-03). | Tomada (Diego) |
| D14 | 2026-10-16 | "FCC" pasa a llamarse **fondo de garantías** en el código y la cuenta contable (`CXC_FONDO_GARANTIAS`); las columnas del Excel conservan su nombre (P-07). | Tomada (Diego) |
| D15 | 2026-10-16 | Fuente de verdad: Excel de Diego del 11 de octubre (12 casos). Línea CPPU 87,62 % EA, 104 cuotas semanales, catálogo de líneas y periodicidad (`LineaCredito`, `Periodicidad`). | Tomada (Diego) |
| D16 | 2026-10-16 | Prelación oficial (Inputs ⑥): cobranza → mora → interés corriente → fondo → asistencia → seguro → capital, con CxC y pagos por servicio. Se aplica igual al valor de la moto en dación y retoma. Cierra P1. | Tomada (Diego) |
| D17 | 2026-10-16 | En periodos de atraso o impago (sin pago, pago inferior, pago tardío) solo se causa el seguro; fondo y asistencia no. A terceros se gira lo recaudado. | Tomada (Diego) |
| D18 | 2026-10-16 | Casos nuevos: 10 (cambio de tasa conservando la cuota: `ModalidadCambioTasa.MISMA_CUOTA`, NPER), 11 (pago inferior + dación) y 12 (normalización: `Instruccion.PagoAlDia`). | Tomada (Diego) |

## Preguntas abiertas (P)

Respuestas de Diego del 2026-10-16: ver `respuestas-diego-2026-10-16.md`. Cerradas: P2, P6 (base), P7, P11 y P-horizonte. P1, P3 (fórmula), P5 y P10 esperan la última versión del Excel.

- **P1** Prelación oficial: ¿servicios antes del capital?
- **P2** Redondeo: ¿centavos o pesos? ¿dónde se absorbe el residuo?
- **P3** Fórmula única de mora.
- **P5** ¿La EA de 87,32 % cumple la usura de la modalidad?
- **P6** ¿FCC y seguro sobre monto inicial o saldo? ¿Se cobran después de un prepago o abono?
- **P7** ¿Mora y servicios en dación/retoma?
- **P10** Abono tardío e incompleto a la vez.
- **P11** (nueva) ¿El 10 % de inicial cambia la base de FCC y seguro (monto financiado 90 %)? El motor hoy los calcula sobre el monto financiado.
