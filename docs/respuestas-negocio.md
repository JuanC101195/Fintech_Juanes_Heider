# Respuestas de negocio al cuestionario de reglas

Fuente: la nube (2026-10-20 15:54 (enviado)) · Respondió: Diego (Gerente) · 2026-10-20 15:54 (enviado) · 59 de 59 completas.

## Aplicado al motor (`config/reglas-negocio.properties`)

- `aplicacionAbono=PRIMERO_PENDIENTES` (pregunta anterior)
- `baseInteres=SALDO_VIGENTE` (pregunta anterior)
- `baseServicios=MONTO_INICIAL` (pregunta anterior)
- `interesPrepagoPorDias=true` (pregunta anterior)
- `margenUsura=1` (pregunta anterior)
- `moraAntesDeRecuperacion=true` (pregunta anterior)

## Decidido pero pendiente de desarrollo en el motor

- Ninguno.

## Opción contradicha por el comentario: no se aplica

- A3: abonoMinimo=0: Diego no dio un mínimo; el abono sigue la prelación de Inputs (pregunta abierta)
- T5: saldo-total: el comentario pide investigar las cláusulas aceleratorias; se repitió sin responder (ronda 4)
- R2: costoRecuperacion=0: los costos de recuperación van dentro de los gastos de cobranza, aún sin definir
- C5: valor-total: el detalle (total = moto + alistamiento) contradice la opción con GPS y gastos comerciales; se repitió sin responder (ronda 4)

## Todas las respuestas

| ID | Pregunta | Respuesta | Valor | Detalle | Comentario |
|---|---|---|---|---|---|
| PPER | ¿Con qué periodicidades se ofrecen los créditos y cómo se calculan? | Semanal, quincenal y mensual según la negociación |  | Se realiza la conversión finaciera de la tasa efectiva anual de la siguiente manera: mensual se toman 12 periodos año, semanal se toman 52 periodos año, y quincena se toman 104 periodos año |  |
| PPER-1 | ¿En qué día vencen las cuotas quincenales y las mensuales? | Respondo en el comentario / queda pendiente |  |  | Los quincenales y mensuales puede pagar asi : quincenal  pagan los 5 y el 20 , y mensual 5  o el primer dia del mes . |
| PPER-2 | ¿El plazo se pacta en meses o en número de cuotas? | En número de cuotas |  |  | Lo importante es respetar el maximo permitido de 24 meses y se calculan en numero de cuotas acorde a la periodicidad que aplique. |
| PPER-3 | ¿Cómo se redondea el número de cuotas cuando el plazo se pacta en meses? | Hacia abajo, como el Excel: ROUNDDOWN(meses × periodos/12) |  |  |  |
| PPER-4 | ¿La periodicidad quincenal entra al catálogo de Inputs? ¿La diaria sigue siendo una opción real? | Quincenal entra y la diaria se retira |  |  | La mensual tambien entra |
| PPER-5 | En mensual, ¿la mora de un periodo se cuenta por el periodo nominal o por los días reales del mes (28 a 31)? ¿Desde qué día corre el atraso? | Por los días reales del mes (28 a 31) |  |  |  |
| PPER-6 | ¿Cómo se expresa el plazo límite de recuperación: en periodos, en meses o en semanas? | En periodos de la periodicidad del crédito |  |  |  |
| PPER-7 | En un prepago a mitad de periodo, ¿se cobran los días transcurridos? | Sí, se cobra el interés de los días transcurridos |  |  |  |
| PPER-8 | En mensual, ¿cuándo cuenta una cuota como vencida y cómo se cobra la cobranza? | Respondo en el comentario / queda pendiente |  |  | Esta pendiente. |
| PPER-9 | Los "4 periodos de espera" del caso 11, ¿se cuentan en periodos o en días? | Respondo en el comentario / queda pendiente |  |  | Pendiente por desarrollar la politica de ejecución de la dación de pago |
| PPER-10 | ¿La periodicidad puede cambiar durante la vida del crédito? | No, queda fija desde el desembolso |  |  | En caso de requerir un cambio de periodicidad se liquida el contrato y se abre uno nuevo , no se hace cambio de periodicidad sobre el mismo. |
| A3 | ¿Hay un monto mínimo para un abono extra? | Sin mínimo |  |  | En caso de un abono extra se debe aplicar segun la prelación de locs conceptos  en  la pestaña de inputs de aplicación de pagos que esta en el Excel |
| T5 | Si el cliente está en mora cuando cambia la tasa, ¿sobre qué saldo se recalcula la cuota? | Sobre el saldo total (vigente + vencido) |  |  | Investigar sobre las clausulas aceleratorias de los contratos de mutuo. |
| R2 | ¿Se descuentan costos de la recuperación (grúa, bodegaje, avalúo, trámites)? | Sí, un valor estimado por recuperación | 0 COP |  | No se tiene aún definido el valor. |
| C5 | El pago inicial se resta del total de la operación (moto + alistamiento): ¿el GPS y los gastos comerciales hacen parte del alistamiento? | GPS y gastos comerciales hacen parte del alistamiento: se resta del valor total (moto + alistamiento + GPS + gastos comerciales) |  | El total de la operción es la misma cosa que el valor de la moto mas alistamiento . |  |
| COB-1 | Política de gastos de cobranza (1 de 6): ¿cuándo se activa el cobro? | Respondo en el comentario / queda pendiente |  |  | pendiente  |
| COB-2 | Política de gastos de cobranza (2 de 6): ¿se cobran por día o por evento? | Respondo en el comentario / queda pendiente |  |  | Pendiente |
| COB-3 | Política de gastos de cobranza (3 de 6): ¿cuál es el tope y sobre qué se mide? | Respondo en el comentario / queda pendiente |  |  | pendinete  |
| COB-4 | Política de gastos de cobranza (4 de 6): ¿se siguen cobrando después de la última cuota pactada? | Respondo en el comentario / queda pendiente |  |  | pendiente |
| COB-5 | Política de gastos de cobranza (5 de 6): ¿quién autoriza que se exonere o se condone un cobro a un cliente? | Respondo en el comentario / queda pendiente |  |  | pendiente |
| COB-6 | Política de gastos de cobranza (6 de 6): ¿el cobro de 3.000 por día es legal en el contrato? | Respondo en el comentario / queda pendiente |  |  | pendiente |
| CAL-1 | Vencimiento los martes (1 de 3): ¿hasta qué momento del martes se paga a tiempo? | Es a tiempo hasta el fin del martes; el atraso empieza el miércoles (día 1) |  |  |  |
| CAL-2 | Vencimiento los martes (2 de 3): si el martes es festivo, ¿cuándo vence la cuota? | Sin manejo de festivos: vence el martes aunque sea festivo |  |  |  |
| CAL-3 | Vencimiento los martes (3 de 3): ¿cuándo vence la primera cuota y el martes es igual para todos los créditos? | El martes es fijo para todos, pero la fecha de la primera cuota se pacta en la oferta |  |  |  |
| SRV-1 | Si el crédito termina a mitad de mes por un prepago, ¿se giran los servicios del mes completo al tercero? | Solo los periodos causados hasta el prepago, sin devolución |  |  |  |
| SRV-2 | Si un abono acorta el plazo, ¿qué pasa con la cobertura del seguro y del fondo de garantías en las cuotas eliminadas? | La cobertura termina con el nuevo plazo y no se cobra más |  |  |  |
| SRV-3 | En la última cuota, ¿se cobran los servicios completos del periodo o se prorratean? | Servicios completos del periodo |  |  |  |
| SRV-4 | ¿La cuenta por pagar de servicios se separa por tercero y por IVA? | Una cuenta distinta por tercero y el IVA generado en su propia cuenta |  |  |  |
| ABN-1 | Caso de validación: abono extra con pendientes. ¿Qué resultado espera? | El caso propuesto sirve: el equipo lo arma y yo valido los valores |  |  |  |
| ABN-2 | Con capital vencido, ¿el excedente del abono cubre primero el capital vencido y luego el vigente? | Primero el capital vencido (el más antiguo) y después el vigente |  |  |  |
| ABN-3 | ¿Hay un máximo de abonos extra por mes o por crédito? | Sin límite |  |  |  |
| ABN-4 | Un abono extra recibido entre cuotas, ¿se aplica de una vez o junto con la siguiente cuota? | Respondo en el comentario / queda pendiente |  |  | En este caso , como la cuota ya fue causada y tiene un plazo de pago hasta el martes de vencimiento dicho pago no cuenta como anticipo simplemente es el pago corriente  de la cuota ya liquidada. En la fabrica de creditos cuando la cuota ya fue causada asi se pague dias previos no cuenta como anticipo , siempre y cuando sea por el valor exacto , en caso de que sena valores superiores ya es un abono extra. |
| ABN-5 | Con varios abonos a menor cuota, ¿cada uno recalcula la cuota hasta la cuota pactada? | Sí, cada abono recalcula la cuota sobre las cuotas restantes |  |  |  |
| ABN-6 | En un prepago total, ¿hay algún costo y cuándo se entrega el paz y salvo? | Sin costo; paz y salvo y liberación de la prenda dentro de un plazo |  | 30 dias |  |
| USU-1 | Usura: ¿confirma que el cambio rige desde la cuota siguiente a la publicación, no desde la primera cuota del mes? | Desde la cuota siguiente a la publicación (el motor se queda así y el Excel se corrige) |  |  |  |
| USU-2 | Alerta mensual de usura (1 de 3): ¿quién es el autorizador que actualiza la usura? | La persona de riesgos o de la dirección financiera |  |  |  |
| USU-3 | Alerta mensual de usura (2 de 3): si nadie autoriza a tiempo, ¿qué hace el sistema? | Aplica automáticamente la usura publicada y avisa al autorizador para que la revise |  |  |  |
| USU-4 | Alerta mensual de usura (3 de 3): ¿qué usura aplica a cada línea de crédito? | Una modalidad distinta por línea de crédito |  | Se usa deacuerdo a la linea que aplique publicado por la super finaciera, el ultimo dia habil de cada mes. |  |
| USU-5 | ¿Cuando la usura baja se recalculan todos los créditos vigentes o solo los nuevos? | Todos los vigentes, después de que el autorizador apruebe el lote |  |  |  |
| USU-6 | Cambio de tasa en sus dos modalidades: ¿cuál se aplica cuando la tasa baja? | Siempre misma cuota y menor plazo |  |  |  |
| USU-7 | ¿Qué otras causas externas, además de la usura, pueden cambiar la tasa de un crédito? | La usura y cambios de ley o regulación |  |  |  |
| USU-8 | Tras un cambio de tasa, ¿cambian el fondo de garantías, la asistencia y el seguro? ¿Se avisa al cliente? | Los servicios no cambian y no se avisa al cliente |  |  |  |
| REC-1 | Dación (1 de 3): ¿cuánto cubre el fondo de garantías del faltante? | El fondo cubre todo el faltante |  |  |  |
| REC-2 | Dación (2 de 3): ¿qué conceptos cubre el fondo: solo capital e intereses, o también mora, servicios y cobranza? | Todos: capital, intereses, mora, servicios y cobranza |  |  |  |
| REC-3 | Dación (3 de 3): si el fondo no alcanza, ¿qué pasa con lo que falta? | Queda como cuenta por cobrar al cliente y pasa a cobro jurídico |  |  |  |
| REC-4 | Retoma (1 de 2): ¿cuándo se le devuelve al cliente el excedente? | Primero se compensa con cualquier otra obligación del cliente y se devuelve el resto |  |  | Las obligaciones adicionales del cliente pueden surgir d elos gastos de cobranza proximos a definir |
| REC-5 | Retoma (2 de 2): ¿cómo se paga el excedente y de dónde sale el dinero? | Transferencia a la cuenta del cliente descontando los costos de la retoma con soporte |  |  |  |
| REC-6 | ¿Quién fija el valor de la dación o la retoma? | Respondo en el comentario / queda pendiente |  |  | Es un mix entre la opción 1 y 2 , donde la  principal es la opción dos tomando como referencia valores fase colda al 65% y segunda opción el perito externo. |
| REC-7 | Si el cliente nunca se pone al día y llega al plazo límite con saldo, ¿qué ocurre? | Se recupera la moto por dación o retoma al llegar al plazo límite |  |  |  |
| REC-8 | La semana del siniestro o de la recuperación, ¿cuenta como cuota vencida? | Sí, cuenta como vencida |  |  |  |
| CON-1 | Plan de cuentas (PUC): ¿cómo nos entrega los códigos de cada cuenta? | Envío el PUC completo en un archivo adjunto por correo |  |  | Se envian en unexcel conservando la metodologia PUC Colombiana |
| CON-2 | ¿El PUC cambia según la línea de crédito? | Las mismas cuentas con un auxiliar por línea |  |  |  |
| CON-3 | ¿Por cuáles canales llegan los pagos y cómo se sabe a qué crédito pertenecen? | El CRM entrega cada pago ya asociado al crédito |  |  |  |
| CON-4 | Los gastos de cobranza, ¿son ingreso de la financiera o se giran a un tercero? | Respondo en el comentario / queda pendiente |  |  | No cuenta como ingresi dado que es la compensación de gastos reales indiferente si la gestión la hace la financiera o un tercero |
| ROL-1 | ¿Quién es el «administrador» que puede cambiar la tasa y los gastos de cobranza? | Una sola persona, sin suplente |  |  |  |
| ROL-2 | ¿Qué parámetros son sensibles y solo toca el administrador? | Todos los parámetros del producto (incluidos plazo límite y periodicidades) |  |  |  |
| ROL-3 | Cuando el administrador cambia un parámetro, ¿rige para los créditos vigentes o solo para los nuevos? | Solo los créditos nuevos; los vigentes conservan lo firmado |  |  | a Ecspción cuando el cambio es la tasa de usura donde si se impactan los creditos anteriores y se deben recalcular desde la cuota siguiente " segun resouestas anteriores del  cuestionario " |
| ROL-4 | ¿Un cambio de parámetro sensible necesita segundo aprobador y queda en bitácora? | Cambio inmediato del administrador, con bitácora de quién, qué y cuándo |  |  |  |
| ROL-5 | Después del simulador y el motor de abonos, ¿cuál es el siguiente alcance? | Respondo en el comentario / queda pendiente |  |  | Lo que sigue es montar el motor del scoring que va antes de la fabrica  |
