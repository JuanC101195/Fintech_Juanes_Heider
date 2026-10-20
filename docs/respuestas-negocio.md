# Respuestas de negocio al cuestionario de reglas

Fuente: la nube (2026-10-20 10:11 (enviado)) · Respondió: Diego (Gerente) · 2026-10-20 10:11 (enviado) · 24 de 24 completas.

## Aplicado al motor (`config/reglas-negocio.properties`)

- `baseInteres=SALDO_VIGENTE` (pregunta anterior)
- `baseServicios=MONTO_INICIAL` (pregunta anterior)
- `moraAntesDeRecuperacion=true` (pregunta anterior)

## Decidido pero pendiente de desarrollo en el motor

- `margenUsura=1` (pregunta T2)

## Opción contradicha por el comentario: no se aplica

- A3: abonoMinimo=0: Diego no dio un mínimo; el abono sigue la prelación de Inputs (pregunta abierta)
- R2: costoRecuperacion=0: los costos de recuperación van dentro de los gastos de cobranza, aún sin definir

## Todas las respuestas

| ID | Pregunta | Respuesta | Valor | Detalle | Comentario |
|---|---|---|---|---|---|
| PTOPE | ¿Los gastos de cobranza tienen tope por pago? | Otra regla (por ejemplo, una sola vez por cuota vencida) |  | Ese 3000 por dia es un ejemplo, debe poder ser configurable , es sensble como la tasa d einteres y solo la puede tocar 1 sola persona que afuturo se asiganara como administrador con poder de cambiar esto, queda pendiente de que Diego nos pase la politica de activación de gastos de cobranza . |  |
| PCOB | Antes de una dación o retoma, ¿cómo se cuentan los días de cobranza si el cliente no paga? | Otra forma |  | Va dentro de la politica que nos pasa Diego |  |
| PDIAS | Con cuotas semanales, un atraso de 14 días deja dos cuotas más vencidas. ¿Cómo se cobra? | Otra forma |  | Todo depende de la periosidad , los creditos pueden ser semanales quincenbales , mensuales no se puede encajar un un periodo todo depende de la negiciación . |  |
| S3 | Los servicios son mensuales pero se cobran por semana. ¿Cómo se reparten? | Valor mensual ÷ 4,333 en cada cuota, como el Excel |  |  | No es que se cobren por semana , el ejemplo es semanal todo se cobra acorde a la configuración del credito,  |
| S6 | Contablemente, ¿los servicios son un pasivo con terceros o un ingreso propio? | Pasivo con terceros (se recauda por cuenta de ellos) |  |  | Ninguna de las tres opciones , se maneja como un ingreso recibido para terceros , en la contabilidad quedaria como una cuenta por pagar contra el banco, dadoq ue nosotros recaudamos el dinero |
| A3 | ¿Hay un monto mínimo para un abono extra? | Sin mínimo |  |  | En caso de un abono extra se debe aplicar segun la prelación de locs conceptos  en  la pestaña de inputs de aplicación de pagos que esta en el Excel |
| A4 | Si el cliente tiene capital vencido y hace un abono extra, ¿cómo se aplica? | Primero se pagan los vencidos por prelación y el resto va a capital |  |  | Segun la prelación de pagos . cualquier pago extras o lo que debe siempre debe respetar la prelación de pagos en cualquier caso se respeta la prelación de pagos, entendiendo que los items 1 a 6 , no los puede cobrar anticipadamente . |
| A5 | Al recalcular la cuota después de un abono, ¿con qué tasa? | La tasa vigente en ese momento |  |  | En caso de que un pago lleve a recalcular la cuota , es por que hubo un pago extra pero no se altera la tasa. |
| A6 | En un prepago total, ¿se cobra interés por los días desde la última cuota? | Sí, interés corriente por los días transcurridos |  |  |  |
| T1 | ¿Desde cuándo aplica un cambio de tasa? | Desde la cuota siguiente a la fecha del cambio |  |  | Importante respetar la periosidad . |
| T2 | Si la tasa supera la usura, ¿cuánto por debajo se ajusta? | Puntos porcentuales de la tasa efectiva anual | 1 pp |  | 1% equivalente a 100 puntos basicos  |
| T4 | Si la usura vuelve a subir, ¿el crédito regresa a la tasa pactada? | No, se queda en la tasa ajustada |  |  | Si la usuara baja debe seguir bajando, respetando el margen de usura , respuesta dada anteriormente  |
| T5 | Si el cliente está en mora cuando cambia la tasa, ¿sobre qué saldo se recalcula la cuota? | Sobre el saldo total (vigente + vencido) |  |  | Investigar sobre las clausulas aceleratorias de los contratos de mutuo. |
| R1 | ¿Qué diferencia hay entre dación en pago y retoma? | Otra diferencia |  | Es un tema siemplemente de terminologia, Dación se utiliza cuando el avaluo comercial de la moto es inferior al saldo insoluto  de la deuda, el diferencial se le cobra al fondo de garantias y se cierra la opración, Retoma  es que se debe devolver dinero por que el valor comercial es mayor al saldo insoluto. |  |
| R2 | ¿Se descuentan costos de la recuperación (grúa, bodegaje, avalúo, trámites)? | Sí, un valor estimado por recuperación | 0 COP |  | Eso va dentro de los gastos de cobranza pendientes por definir  |
| R3 | ¿La moto entra al inventario por el avalúo o por el valor de venta? | Por el avalúo; la diferencia en la venta se ajusta después |  |  |  |
| R4 | El interés de la semana del evento, ¿se cobra completo o por días? | Completo, como el Excel |  |  |  |
| C1 | ¿El interés corriente se causa cada semana o se registra cuando se recauda? | Causación: cada semana se causa el interés (CxC contra ingreso) y el pago cancela la CxC |  |  | La causación se hace segun el pagos plasmado en el  caso 1 ( el plan normal ) , respetando la periodicidad pactada . |
| C2 | ¿Cuáles son los códigos del plan de cuentas (PUC) de cada cuenta? | Los envío por correo en un archivo aparte |  |  | Pendiente. |
| C3 | ¿Quién recauda el pago inicial y cómo se registra? | La recauda el concesionario y se descuenta del desembolso |  |  | Eso no afecta en nada la fabrica de credito , a la final el credito es sobre lo que se presta , esto es importante para la simulación con el plan d epagos que se le entrega al cliente como oferta comercial. Es importante guardar el dato para un tema de auditoria mas no para la fabrica de creditos  |
| C5 | El pago inicial, ¿se resta del valor total de la operación o solo de la moto más el alistamiento? | Del valor total: moto + alistamiento + GPS + gastos comerciales |  | El total de la operción es la misma cosa que el valor de la moto mas alistamiento . |  |
| X1 | ¿Se corregirán estas inconsistencias en una nueva versión del Excel? | Sí, enviaré una versión corregida |  |  |  |
| X4 | En el caso 8 el plazo límite sigue en 100 semanas, pero ahora el crédito tiene 104 cuotas. ¿Cuál debe ser el límite? | Otra regla |  | Se adjunta segun Excel |  |
| X3 | En su respuesta sobre el plazo límite habla de los casos 4 o 5 como dación o retoma. ¿Se refiere a los casos 3 y 4? | Sí, dación (caso 3) o retoma (caso 4) |  |  |  |
