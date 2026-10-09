"""
SUBIR BACKLOG · Motor de reglas de cartera de motos (Financiera)
================================================================

Qué es: 9 épicas, 24 features y 78 historias: 64 de usuario y 14 técnicas (las técnicas
llevan el prefijo [HT] y la etiqueta historia-tecnica), con criterios de aceptación en Gherkin, para el proyecto Azure DevOps https://dev.azure.com/motosdelcaribe/Financiera.
Las reglas salen del Excel "Casos Operaciones V2 mejorado.xlsx" (Diego González).

Cómo usarlo (persona):
  1. Tener Python 3.9 o superior. No requiere instalar librerías.
  2. Crear un token personal (PAT) en Azure DevOps: User settings → Personal access
     tokens → New token, con el permiso "Work Items: Read & Write".
  3. Ejecutar:  python subir_backlog_financiera.py
  4. Aceptar organización y proyecto (Enter), pegar el token cuando lo pida (no se ve
     al escribir), revisar el resumen y responder "s".

Instrucciones para un asistente de IA que reciba este archivo:
  - Lee el archivo completo antes de ejecutarlo. Solo usa la biblioteca estándar y
    solo llama a dev.azure.com.
  - Ejecútalo con `python subir_backlog_financiera.py`. Pide el PAT al usuario en su
    propia terminal; no lo guardes en el archivo ni lo muestres.
  - El script detecta la plantilla del proyecto (Agile, Scrum, CMMI o Basic), muestra
    cuántos elementos va a crear y pide confirmación antes de escribir.
  - Es idempotente: si se vuelve a ejecutar no duplica (usa la etiqueta
    backlog-motor-reglas-v1). Para cambiar contenido, edita la lista EPICAS.
"""

EPICAS = [
    # ─────────────────────────────────────────────────────────────────────────────
    {
        "titulo": "E1 · Descubrimiento y definición de reglas de negocio",
        "descripcion": "Entender, validar y dejar firmadas con negocio (Diego González, Heider Galvis) todas las reglas del Excel de casos operativos antes de programarlas. Incluye resolver inconsistencias, decisiones regulatorias y la decisión de arquitectura (motor propio vs Apache Fineract).",
        "features": [
            {
                "titulo": "F1.1 · Catálogo de reglas del Excel de casos operativos",
                "descripcion": "Convertir el Excel en un catálogo de reglas numeradas, trazables y aprobadas, que sea la fuente única para desarrollo y pruebas.",
                "historias": [
                    {
                        "titulo": "Documentar el catálogo de reglas de amortización y servicios",
                        "como": "Product Owner", "quiero": "un catálogo de reglas numeradas (R-001…) extraído de las hojas Inputs y Casos del Excel", "para": "que desarrollo, pruebas y negocio hablen de la misma regla con el mismo identificador",
                        "gherkin": """Característica: Catálogo de reglas del motor de cartera
  Escenario: Cada fórmula del Excel queda como regla trazable
    Dado el Excel "Casos Operaciones V2 mejorado.xlsx" con sus 12 hojas
    Cuando se documenta el catálogo de reglas
    Entonces cada fórmula de las hojas Inputs y Caso1 a Caso9 tiene una regla con identificador, descripción, fórmula y hoja/celda de origen
    Y cada regla indica en qué caso (1 a 9) aplica

  Escenario: El catálogo queda aprobado por negocio
    Dado el catálogo de reglas documentado
    Cuando Diego González lo revisa
    Entonces cada regla queda marcada como "aprobada", "ajustar" o "descartar"
    Y ninguna historia de desarrollo del motor inicia con reglas en estado distinto de "aprobada\"""",
                    },
                    {
                        "titulo": "Resolver el orden de prelación de abonos (servicios antes o después de interés)",
                        "como": "Product Owner", "quiero": "confirmar con negocio el orden exacto en que se aplica un abono", "para": "programar una sola prelación sin ambigüedad",
                        "gherkin": """Característica: Prelación oficial de aplicación de abonos
  Escenario: Se elimina la contradicción entre el texto y las fórmulas del Excel
    Dado que la hoja Casos describe la prelación "mora, interés corriente, capital"
    Y que las fórmulas de los casos 8 y 9 aplican "cobranza, mora, interés corriente, servicios, capital"
    Cuando negocio define la prelación oficial
    Entonces queda documentado un único orden con todos los conceptos: gastos de cobranza, interés de mora, interés corriente, servicios complementarios y capital
    Y el Excel se actualiza para que texto y fórmulas digan lo mismo""",
                        "notas": "Pregunta para Diego: ¿los servicios (FCC, asistencia, seguro) van antes del capital como en las fórmulas?",
                    },
                    {
                        "titulo": "Definir la regla de mora en dación y retoma (casos 3 y 4)",
                        "como": "Product Owner", "quiero": "saber si en los casos 3 y 4 se causa interés de mora y capital vencido mientras el cliente no paga", "para": "que el motor trate igual toda la mora o documente la excepción",
                        "gherkin": """Característica: Mora antes de una dación o retoma
  Escenario: Se decide si la mora se causa antes del evento de cierre
    Dado que en los casos 3 y 4 el Excel solo causa interés corriente durante las cuotas sin pago
    Y que en los casos 8 y 9 sí se causa interés de mora sobre el capital vencido
    Cuando negocio define la regla
    Entonces queda escrito si en dación y retoma se causa interés de mora y capital vencido
    Y el valor de "deuda a extinguir" del Excel se recalcula con esa regla si cambia""",
                    },
                    {
                        "titulo": "Definir la fórmula de interés de mora (semanal vs días de atraso)",
                        "como": "Product Owner", "quiero": "una sola fórmula de interés de mora", "para": "no tener dos cálculos distintos entre el caso 8 y el caso 9",
                        "gherkin": """Característica: Fórmula única de interés de mora
  Escenario: Se unifica la mora del caso 8 y del caso 9
    Dado que el caso 8 calcula mora = capital vencido anterior × tasa semanal
    Y que el caso 9 suma además capital de la cuota × ((1 + tasa diaria)^días de atraso − 1)
    Cuando negocio define la fórmula oficial
    Entonces existe una única regla de mora con su base, su tasa y su periodo de causación
    Y se confirma que la tasa de mora no supera el límite legal vigente""",
                    },
                    {
                        "titulo": "Validar el comportamiento del caso 9 al horizonte de 120 semanas",
                        "como": "Product Owner", "quiero": "confirmar si es correcto que en el caso 9 el crédito siga en mora a la semana 120 sin amortizar capital", "para": "saber si el motor debe reproducirlo o si hay que limitar cobranza y mora",
                        "gherkin": """Característica: Crédito con atraso permanente
  Escenario: Se revisa el resultado del Excel en la semana 120
    Dado el caso 9 con 14 días de atraso por cuota desde la semana 43
    Cuando el Excel llega al periodo 120
    Entonces el saldo de capital es 3.319.011,75 y el estado es "En mora"
    Y negocio confirma si ese resultado es el esperado o si se requiere un tope a la cobranza o a la mora""",
                    },
                    {
                        "titulo": "Confirmar la base de cálculo de FCC y seguro de vida",
                        "como": "Product Owner", "quiero": "confirmar si FCC y seguro se calculan sobre el monto inicial o sobre el saldo", "para": "programar correctamente los servicios cuando hay abonos extra",
                        "gherkin": """Característica: Base de cálculo de los servicios
  Escenario: Servicios después de un abono extraordinario
    Dado un crédito de 5.904.400 con FCC del 3 % mensual y seguro del 0,25 % mensual
    Y un abono extra de 2.000.000 en la semana 43
    Cuando negocio define la base de cálculo
    Entonces queda escrito si las cuotas siguientes cobran FCC y seguro sobre 5.904.400 o sobre el saldo
    Y si los servicios se dejan de cobrar cuando el crédito termina antes de la semana 86""",
                    },
                    {
                        "titulo": "Decidir la política de cuota inicial y monto financiado",
                        "como": "Product Owner", "quiero": "decidir si el crédito exige cuota inicial o financia el 116 % del valor de la moto como el Excel", "para": "alinear el front de solicitud con el motor",
                        "gherkin": """Característica: Composición del monto financiado
  Escenario: Se define si hay cuota inicial
    Dado que el Excel financia moto 5.100.000 + alistamiento 664.400 + tecnología 80.000 + gastos comerciales 60.000 = 5.904.400
    Y que el front actual exige una cuota inicial mínima del 10 %
    Cuando negocio toma la decisión
    Entonces queda definido si existe cuota inicial, su porcentaje mínimo y si se descuenta de la moto o del total
    Y queda definido el LTV máximo permitido""",
                    },
                    {
                        "titulo": "Definir el redondeo del motor frente al Excel",
                        "como": "Product Owner", "quiero": "definir si el motor trabaja con centavos o redondea a pesos", "para": "que las pruebas contra el Excel tengan una tolerancia clara",
                        "gherkin": """Característica: Precisión de los cálculos
  Escenario: Se fija la convención de redondeo
    Dado que el Excel no redondea y la hoja Validaciones acepta 1 COP de tolerancia
    Cuando negocio y contabilidad definen la convención
    Entonces queda escrito con cuántos decimales se calcula, cómo se redondea lo que se cobra al cliente y lo que se contabiliza
    Y en qué cuota se absorbe la diferencia de redondeo""",
                    },
                ],
            },
            {
                "titulo": "F1.2 · Decisiones regulatorias y tributarias",
                "descripcion": "Usura, modalidad de crédito, IVA de servicios y requisitos de reporte que condicionan las reglas.",
                "historias": [
                    {
                        "titulo": "Validar la tasa EA frente al límite de usura de la modalidad",
                        "como": "Product Owner", "quiero": "confirmar en qué modalidad se registra el crédito y su tasa de usura vigente", "para": "que la tasa de 87,32 % EA y los cargos no superen el límite legal",
                        "gherkin": """Característica: Cumplimiento de usura
  Escenario: La tasa pactada se compara con la usura de la modalidad
    Dado un crédito con tasa EA de 87,32 %
    Cuando se consulta la tasa de usura certificada por la Superintendencia Financiera para la modalidad definida
    Entonces queda documentado si la tasa cumple
    Y si no cumple, queda definida la regla de ajuste (usura menos 1 punto, según lo acordado)""",
                    },
                    {
                        "titulo": "Confirmar el tratamiento de IVA de los servicios complementarios",
                        "como": "contador", "quiero": "confirmar qué servicios llevan IVA del 19 % y cuáles no", "para": "liquidar correctamente lo que se cobra y lo que se paga a terceros",
                        "gherkin": """Característica: IVA de servicios
  Escenario: Servicios gravados y excluidos
    Dado que el Excel grava FCC y asistencia con IVA del 19 % y excluye el seguro de vida
    Cuando contabilidad confirma el tratamiento tributario
    Entonces cada servicio queda marcado como gravado o excluido con su tarifa
    Y queda definido quién es responsable del IVA en cada servicio""",
                    },
                ],
            },
            {
                "titulo": "F1.3 · Decisión de arquitectura del motor",
                "descripcion": "Decidir si el motor de reglas se construye a medida o sobre Apache Fineract, con prueba de concepto contra los casos del Excel.",
                "historias": [
                    {
                        "titulo": "Spike: reproducir los casos 1, 2, 6 y 7 en Apache Fineract",
                        "tecnica": True, "para": "decidir con datos si Fineract sirve como núcleo", "necesita": "configurar un producto de crédito semanal en Fineract y comparar su cronograma con el Excel",
                        "gherkin": """Característica: Prueba de concepto con Fineract
  Escenario: Cronograma semanal comparado con el Excel
    Dado un producto en Fineract con 86 cuotas semanales y tasa nominal anual equivalente a 1,2143296 % semanal
    Cuando se genera el cronograma de un crédito de 5.904.400
    Entonces la cuota financiera difiere en máximo 1 COP de 111.014,82
    Y el saldo final es 0

  Escenario: Abonos y cambio de tasa en Fineract
    Dado el mismo crédito en Fineract
    Cuando se aplica un cambio de tasa a 80 % EA desde la cuota 44 y abonos de 2.000.000 en la cuota 43
    Entonces la nueva cuota del caso 2 es 109.308,38 y la del caso 7 es 51.032,32 con tolerancia de 1 COP
    Y el caso 6 termina en la cuota 61""",
                        "notas": "Fineract calcula la tasa semanal como nominal anual / 52 (verificado en LoanApplicationTerms.java), así que hay que cargarle la nominal equivalente.",
                    },
                    {
                        "titulo": "ADR de monolito modular y esqueleto de los módulos",
                        "tecnica": True, "para": "construir sobre una base acordada y que las fronteras entre módulos no se rompan", "necesita": "un registro de decisión (ADR) y el esqueleto de los 7 módulos (nucleo-calculo, creditos, abonos, contabilidad, integracion-crm, procesos, api) con sus reglas de dependencia",
                        "gherkin": """Característica: Arquitectura de monolito modular
  Escenario: Se documenta la decisión
    Dado las opciones evaluadas: monolito modular, microservicios y Fineract
    Cuando el equipo registra el ADR
    Entonces queda la decisión, sus razones y cuándo separar un módulo como servicio

  Escenario: Las fronteras se verifican solas
    Dado el módulo nucleo-calculo
    Cuando corre la prueba de arquitectura
    Entonces falla si nucleo-calculo depende de otro módulo
    Y falla si un módulo lee las tablas de otro módulo""",
                    },
                ],
            },
        ],
    },
    # ─────────────────────────────────────────────────────────────────────────────
    {
        "titulo": "E2 · Motor de reglas: originación y plan de pagos normal",
        "descripcion": "Parámetros del producto de crédito de moto y generación del plan de pagos semanal del caso 1 (operación normal), que es además lo que muestra el front en la solicitud.",
        "features": [
            {
                "titulo": "F2.1 · Parámetros del producto de crédito de moto",
                "descripcion": "Configuración versionada de las condiciones generales, tasas y servicios (hoja Inputs).",
                "historias": [
                    {
                        "titulo": "Calcular el monto financiado a partir de sus componentes",
                        "como": "analista de crédito", "quiero": "que el monto financiado sume la moto, los ítems de alistamiento, la tecnología y los gastos comerciales", "para": "colocar el valor final correcto",
                        "gherkin": """Característica: Monto financiado
  Escenario: Suma de componentes
    Dado una moto de 5.100.000
    Y alistamiento de matrícula y RUNT 171.100, SOAT 373.300 y constitución de prenda 120.000
    Y GPS y plataforma 80.000
    Y gastos comerciales 60.000
    Cuando se calcula el monto financiado
    Entonces el monto financiado es 5.904.400
    Y el porcentaje de financiación sobre la moto es 115,77 %""",
                    },
                    {
                        "titulo": "Calcular el número de cuotas semanales a partir del plazo en meses",
                        "como": "analista de crédito", "quiero": "convertir el plazo en meses a cuotas semanales", "para": "generar el cronograma semanal",
                        "gherkin": """Característica: Número de cuotas semanales
  Esquema del escenario: Conversión de meses a semanas
    Dado un año de 52 semanas y 12 meses
    Cuando el plazo es de <meses> meses
    Entonces el número de cuotas es <cuotas> (redondeo hacia abajo de meses × 52 / 12)

    Ejemplos:
      | meses | cuotas |
      | 20    | 86     |
      | 12    | 52     |
      | 18    | 78     |""",
                    },
                    {
                        "titulo": "Convertir la tasa efectiva anual a tasas semanal, diaria y mensual",
                        "como": "analista de crédito", "quiero": "que el motor derive las tasas periódicas de la EA pactada", "para": "usar la tasa contractual en todos los cálculos",
                        "gherkin": """Característica: Conversión de tasas
  Escenario: Tasas equivalentes de una EA de 87,32 %
    Dado una tasa efectiva anual de 87,32 %
    Cuando se calculan las tasas equivalentes
    Entonces la tasa semanal es (1 + EA)^(1/52) − 1 = 0,012143296 con tolerancia 0,000001
    Y la tasa diaria es (1 + EA)^(1/365) − 1 = 0,001721063
    Y la tasa mensual de referencia es (1 + EA)^(1/12) − 1 = 0,053696035""",
                    },
                    {
                        "titulo": "Configurar los servicios complementarios por cuota",
                        "como": "analista de crédito", "quiero": "configurar FCC, asistencia y seguro de vida con su base, periodicidad e IVA", "para": "calcular lo que paga el cliente en cada cuota",
                        "gherkin": """Característica: Servicios complementarios
  Escenario: Servicios de una cuota semanal
    Dado un monto financiado de 5.904.400 y 4,333333 semanas por mes
    Y FCC del 3 % mensual sobre el monto con IVA del 19 %
    Y asistencia de 40.000 mensuales con IVA del 19 %
    Y seguro de vida del 0,25 % mensual sobre el monto sin IVA
    Cuando se calculan los servicios por cuota
    Entonces el FCC por cuota es 48.643,17
    Y la asistencia por cuota es 10.984,62
    Y el seguro por cuota es 3.406,38
    Y los servicios por cuota suman 63.034,17""",
                    },
                    {
                        "titulo": "Versionar los parámetros del producto con fecha de vigencia",
                        "tecnica": True, "para": "que cada crédito conserve las condiciones con las que se originó", "necesita": "que cada cambio de parámetros tenga fecha de vigencia",
                        "gherkin": """Característica: Versionado de parámetros
  Escenario: Un crédito conserva sus condiciones
    Dado un crédito originado con la versión 1 de parámetros (EA 87,32 %)
    Cuando se publica la versión 2 con una EA distinta
    Entonces el crédito existente sigue calculando con la versión 1
    Y los créditos nuevos usan la versión 2

  Escenario: No se pueden editar parámetros ya usados
    Dado una versión de parámetros con créditos originados
    Cuando un usuario intenta modificarla
    Entonces el sistema lo rechaza y le pide crear una nueva versión""",
                    },
                ],
            },
            {
                "titulo": "F2.2 · Plan de pagos de la operación normal (caso 1)",
                "descripcion": "Cronograma semanal con amortización francesa, servicios y pagos a terceros cuando el cliente paga a tiempo.",
                "historias": [
                    {
                        "titulo": "Proteger los invariantes del motor",
                        "tecnica": True, "para": "que nunca haya saldos ni libros inconsistentes", "necesita": "que el motor rechace cualquier operación que rompa un invariante y que los invariantes se prueben con datos aleatorios",
                        "gherkin": """Característica: Invariantes del motor
  Escenario: Cálculo siempre consistente
    Dado cualquier monto, tasa y plazo válidos
    Cuando se genera el cronograma
    Entonces ningún saldo es negativo y el saldo final es 0

  Escenario: La distribución suma el abono
    Dado cualquier abono aplicado
    Cuando se suma su distribución por concepto
    Entonces el total es igual al valor del abono

  Escenario: Crédito cerrado
    Dado un crédito en estado "Cancelado" o "Cerrado por evento"
    Cuando llega un abono
    Entonces el motor lo rechaza y no modifica saldos

  Escenario: Pruebas con datos aleatorios
    Dado 10.000 secuencias aleatorias de abonos válidos
    Cuando se aplican
    Entonces ningún saldo queda negativo y todos los asientos cuadran""",
                    },
                    {
                        "titulo": "Calcular la cuota financiera semanal fija",
                        "como": "analista de crédito", "quiero": "calcular la cuota fija de capital más interés", "para": "informar al cliente cuánto paga cada semana",
                        "gherkin": """Característica: Cuota financiera
  Escenario: Cuota del crédito base
    Dado un monto financiado de 5.904.400
    Y una tasa semanal de 0,012143296
    Y 86 cuotas semanales
    Cuando se calcula la cuota financiera con amortización francesa (PMT)
    Entonces la cuota financiera es 111.014,82 con tolerancia de 1 COP
    Y la cuota total al cliente con servicios es 174.048,99""",
                    },
                    {
                        "titulo": "Generar el cronograma semanal de amortización",
                        "como": "analista de crédito", "quiero": "el plan cuota a cuota con saldo, interés, capital y servicios", "para": "conocer el comportamiento esperado del crédito",
                        "gherkin": """Característica: Cronograma de amortización
  Escenario: Primera cuota
    Dado el crédito base de 5.904.400 a 86 cuotas
    Cuando se genera el cronograma
    Entonces la cuota 1 tiene saldo inicial 5.904.400, interés 71.698,88, capital 39.315,95 y saldo final 5.865.084,05

  Escenario: Cierre del crédito
    Dado el mismo cronograma
    Cuando se llega a la cuota 86
    Entonces el saldo final es 0 con tolerancia de 1 COP
    Y el estado es "Amortizado"
    Y los intereses totales son 3.642.874,68

  Escenario: Mes de cada cuota
    Dado el mismo cronograma
    Cuando se asigna el mes a cada cuota
    Entonces el mes es el redondeo hacia arriba de periodo / 4,333333
    Y la cuota 4 pertenece al mes 1 y la cuota 5 al mes 2""",
                    },
                    {
                        "titulo": "Calcular fechas de vencimiento semanales",
                        "como": "analista de cartera", "quiero": "que cada cuota tenga fecha de vencimiento", "para": "medir días de atraso y enviar recordatorios",
                        "gherkin": """Característica: Fechas de vencimiento
  Escenario: Vencimientos cada 7 días
    Dado un crédito desembolsado el lunes 5 de octubre de 2026
    Y el primer pago pactado para el lunes 12 de octubre de 2026
    Cuando se genera el cronograma
    Entonces la cuota 1 vence el 12 de octubre de 2026
    Y cada cuota siguiente vence 7 días después de la anterior
    Y la cuota 86 vence el 29 de mayo de 2028

  Escenario: Vencimiento en festivo
    Dado que una cuota vence en un día festivo en Colombia
    Cuando se genera el cronograma
    Entonces el sistema aplica la regla de festivos definida por negocio""",
                        "notas": "Por definir: día de la semana de pago y regla de festivos (no está en el Excel).",
                    },
                    {
                        "titulo": "Liquidar mensualmente los pagos a terceros",
                        "como": "tesorero", "quiero": "saber cuánto pagar cada mes al fondo de garantías, a la asistencia y a la aseguradora", "para": "girar a terceros lo recaudado por su cuenta",
                        "gherkin": """Característica: Pagos a terceros
  Escenario: Liquidación del mes 1
    Dado el cronograma del crédito base
    Cuando se cierra el mes 1 (cuotas 1 a 4)
    Entonces el pago FCC del mes es 194.572,69
    Y el pago de asistencia es 43.938,46
    Y el pago a la aseguradora es 13.625,54

  Escenario: Último mes
    Dado la cuota 86, que es la última del crédito
    Cuando se liquida
    Entonces se liquidan a terceros los servicios acumulados del último mes aunque no esté completo""",
                    },
                ],
            },
        ],
    },
    # ─────────────────────────────────────────────────────────────────────────────
    {
        "titulo": "E3 · Motor de reglas: eventos de cartera",
        "descripcion": "Reglas de los casos 2 a 9: cambio de tasa, abonos extraordinarios, pagos incompletos, mora, cobranza y cierres por recuperación de la moto.",
        "features": [
            {
                "titulo": "F3.0 · Estados del crédito",
                "descripcion": "Máquina de estados que define las transiciones permitidas del crédito.",
                "historias": [
                    {
                        "titulo": "Máquina de estados del crédito",
                        "como": "analista de cartera", "quiero": "que el crédito solo cambie de estado por las transiciones permitidas", "para": "que el estado siempre refleje la realidad del crédito",
                        "gherkin": """Característica: Estados del crédito
  Esquema del escenario: Transición permitida
    Dado un crédito en estado "<desde>"
    Cuando ocurre "<evento>"
    Entonces el crédito queda en "<hacia>"

    Ejemplos:
      | desde          | evento                | hacia              |
      | Originado      | desembolso            | Vigente al día     |
      | Vigente al día | pago inferior         | En mora            |
      | En mora        | cubre todo lo vencido | Vigente al día     |
      | Vigente al día | prepago total         | Cancelado          |
      | En mora        | pago de todo lo debido| Cancelado          |
      | En mora        | dación o retoma       | Cerrado por evento |

  Escenario: Transición no permitida
    Dado un crédito "Cancelado"
    Cuando se intenta pasarlo a "En mora"
    Entonces el motor lo rechaza y registra el intento""",
                    },
                ],
            },
            {
                "titulo": "F3.1 · Cambio de tasa (caso 2)",
                "descripcion": "Cambio de la EA durante la vida del crédito conservando la fecha final.",
                "historias": [
                    {
                        "titulo": "Recalcular la cuota cuando cambia la tasa EA",
                        "como": "analista de cartera", "quiero": "aplicar una nueva tasa desde un periodo y recalcular la cuota", "para": "reflejar cambios de tasa sin mover la fecha final",
                        "gherkin": """Característica: Cambio de tasa
  Escenario: Nueva tasa desde el mes 11
    Dado el crédito base con 86 cuotas al 87,32 % EA
    Y un cambio de tasa a 80 % EA en el mes 11
    Cuando se aplica el cambio
    Entonces el periodo desde el que aplica es 44 (redondeo hacia abajo de (11 − 1) × 4,333333, más 1)
    Y la nueva tasa semanal es 0,011367717
    Y la nueva cuota financiera es 109.308,38, calculada sobre el saldo de 3.701.573,62 a 43 cuotas restantes
    Y la cuota 86 sigue siendo la última y el saldo final es 0

  Escenario: Efecto sobre los intereses
    Dado el mismo cambio de tasa
    Cuando se compara con la operación normal
    Entonces los intereses totales bajan de 3.642.874,68 a 3.569.497,85""",
                    },
                    {
                        "titulo": "Ajustar automáticamente la tasa cuando supera la usura",
                        "como": "analista de cartera", "quiero": "que el motor revise los créditos vigentes contra la usura de cada periodo", "para": "cumplir la ley sin revisar crédito por crédito",
                        "gherkin": """Característica: Ajuste por usura
  Escenario: La usura baja por debajo de la tasa pactada
    Dado un crédito vigente con tasa EA superior a la nueva usura certificada
    Cuando se publica la nueva tasa de usura
    Entonces el motor aplica un cambio de tasa a usura menos 1 punto desde el siguiente periodo
    Y recalcula la cuota conservando la fecha final como en el caso 2
    Y deja registro del cambio para notificar al cliente""",
                        "notas": "Depende de la regla de usura confirmada en E1.",
                    },
                ],
            },
            {
                "titulo": "F3.2 · Abonos extraordinarios (casos 5, 6 y 7)",
                "descripcion": "Pago total anticipado y abonos extra aplicados a capital, reduciendo plazo o cuota.",
                "historias": [
                    {
                        "titulo": "Aplicar el pago anticipado total (caso 5)",
                        "como": "analista de cartera", "quiero": "cancelar el crédito cuando el cliente paga todo el saldo", "para": "cerrar la obligación y dejar de causar intereses",
                        "gherkin": """Característica: Prepago total
  Escenario: Cancelación en el mes 10
    Dado el crédito base al día hasta la cuota 42
    Cuando en la cuota 43 el cliente paga la cuota ordinaria más el saldo total de capital
    Entonces la cuota ordinaria aplica interés 45.741,93 y capital 65.272,89
    Y el prepago de capital es 3.701.573,62
    Y el pago financiero total del evento es 3.812.588,44
    Y el saldo posterior es 0 y el crédito queda "Cancelado"
    Y no se generan cuotas posteriores
    Y los intereses futuros evitados son 1.072.063,72""",
                    },
                    {
                        "titulo": "Aplicar un abono extra reduciendo el plazo (caso 6)",
                        "como": "cliente", "quiero": "que mi abono extra reduzca el número de cuotas", "para": "terminar de pagar antes con la misma cuota",
                        "gherkin": """Característica: Abono extra con menor plazo
  Escenario: Abono de 2.000.000 en la cuota 43
    Dado el crédito base al día hasta la cuota 42
    Cuando en la cuota 43 el cliente paga la cuota ordinaria y un abono extra de 2.000.000 con opción "menor plazo"
    Entonces el abono se aplica 100 % a capital
    Y el saldo tras el abono es 1.701.573,62
    Y la cuota financiera sigue siendo 111.014,82
    Y el último periodo con pago es el 61
    Y la última cuota es de 6.998,93""",
                    },
                    {
                        "titulo": "Aplicar un abono extra reduciendo la cuota (caso 7)",
                        "como": "cliente", "quiero": "que mi abono extra baje el valor de las cuotas siguientes", "para": "pagar menos cada semana manteniendo el plazo",
                        "gherkin": """Característica: Abono extra con menor cuota
  Escenario: Abono de 2.000.000 en la cuota 43
    Dado el crédito base al día hasta la cuota 42
    Cuando en la cuota 43 el cliente paga la cuota ordinaria y un abono extra de 2.000.000 con opción "menor cuota"
    Entonces el saldo tras el abono es 1.701.573,62
    Y desde la cuota 44 la cuota financiera es 51.032,32, recalculada a 43 cuotas restantes
    Y la reducción de cuota es 59.982,50
    Y la cuota 86 sigue siendo la última y el saldo final es 0""",
                    },
                    {
                        "titulo": "Limitar el abono extra al saldo de capital",
                        "como": "analista de cartera", "quiero": "que el abono extra nunca supere el saldo pendiente", "para": "no dejar saldos negativos y devolver o registrar el excedente",
                        "gherkin": """Característica: Tope del abono extra
  Escenario: Abono mayor que el saldo
    Dado un crédito con saldo de capital de 500.000 después de la cuota ordinaria
    Cuando el cliente abona 800.000 adicionales
    Entonces se aplican 500.000 a capital y el crédito queda cancelado
    Y los 300.000 restantes quedan como saldo a favor del cliente según la regla definida por negocio""",
                        "notas": "El Excel aplica MIN(abono, saldo − capital ordinario). Falta definir el destino del excedente.",
                    },
                ],
            },
            {
                "titulo": "F3.3 · Pagos incompletos y mora (caso 8)",
                "descripcion": "Pago menor a la cuota, capital vencido, interés de mora, cuentas por cobrar de servicios y periodos adicionales.",
                "historias": [
                    {
                        "titulo": "Aplicar un pago menor a la cuota según la prelación",
                        "como": "analista de cartera", "quiero": "repartir un pago incompleto según la prelación definida", "para": "saber qué conceptos quedaron pendientes",
                        "gherkin": """Característica: Pago inferior a la cuota
  Escenario: El cliente paga el 40 % de la cuota 43
    Dado el crédito base al día hasta la cuota 42
    Y la cuota total 43 de 174.048,99
    Cuando el cliente paga 69.619,60
    Entonces se aplican 45.741,93 a interés corriente
    Y 23.877,67 a servicios
    Y 0 a capital
    Y quedan 39.156,51 como cuenta por cobrar de servicios
    Y quedan 65.272,89 como capital vencido
    Y el estado del crédito es "En mora\"""",
                    },
                    {
                        "titulo": "Causar interés de mora sobre el capital vencido",
                        "como": "analista de cartera", "quiero": "que el capital vencido genere interés de mora en cada periodo", "para": "cobrar el costo del atraso",
                        "gherkin": """Característica: Interés de mora
  Escenario: Mora del periodo siguiente al pago inferior
    Dado un capital vencido de 65.272,89 al cierre de la cuota 43
    Cuando se causa la cuota 44
    Entonces el interés de mora es 65.272,89 × tasa semanal = 792,63
    Y se cobra antes que el interés corriente según la prelación""",
                    },
                    {
                        "titulo": "Recuperar saldos vencidos con los pagos siguientes",
                        "como": "analista de cartera", "quiero": "que los pagos normales posteriores cubran primero lo vencido", "para": "normalizar el crédito",
                        "gherkin": """Característica: Aplicación de vencidos
  Escenario: Pago completo después del pago inferior
    Dado capital vencido de 65.272,89 y cuenta por cobrar de servicios de 39.156,51
    Cuando en la cuota 44 el cliente paga la cuota total de 174.048,99
    Entonces se aplican 792,63 a mora, 45.741,93 a interés corriente, 102.190,68 a servicios y 25.323,76 a capital
    Y el capital vencido queda en 106.014,65
    Y el evento se registra como "Pago normal - aplica vencidos\"""",
                    },
                    {
                        "titulo": "Generar periodos adicionales hasta cancelar el saldo",
                        "como": "analista de cartera", "quiero": "que el crédito siga después de la cuota 86 si queda saldo", "para": "recaudar lo pendiente sin pasar del plazo límite",
                        "gherkin": """Característica: Periodos adicionales
  Escenario: El crédito se cancela después del plazo contractual
    Dado el caso 8 con saldo pendiente al terminar la cuota 86
    Cuando el cliente sigue pagando la cuota pactada
    Entonces se generan periodos adicionales sin capital contractual ni servicios
    Y el crédito se cancela en el periodo 89

  Escenario: Plazo límite
    Dado un plazo límite permitido de 100 semanas
    Cuando el crédito llega a la semana 100 con saldo
    Entonces el motor marca el crédito para gestión según la regla definida por negocio""",
                    },
                    {
                        "titulo": "Calcular el estado del crédito en cada periodo",
                        "como": "analista de cartera", "quiero": "conocer el estado de cada crédito", "para": "segmentar la cartera y gestionarla",
                        "gherkin": """Característica: Estado del crédito
  Esquema del escenario: Estado según saldos
    Dado un crédito con saldo de capital <saldo>, vencidos <vencidos> y cuentas por cobrar <cxc>
    Cuando se evalúa el estado al cierre del periodo
    Entonces el estado es "<estado>"

    Ejemplos:
      | saldo     | vencidos | cxc   | estado         |
      | 0         | 0        | 0     | Cancelado      |
      | 3.766.846 | 0        | 0     | Vigente al día |
      | 3.766.846 | 65.272   | 0     | En mora        |
      | 3.766.846 | 0        | 39.156| En mora        |""",
                    },
                ],
            },
            {
                "titulo": "F3.4 · Pagos tardíos y gastos de cobranza (caso 9)",
                "descripcion": "Cobro de gastos de gestión de cobranza por días de atraso y prelación con cobranza en primer lugar.",
                "historias": [
                    {
                        "titulo": "Causar gastos de cobranza por días de atraso",
                        "como": "analista de cobranza", "quiero": "cobrar un gasto por cada día de atraso", "para": "recuperar el costo de la gestión",
                        "gherkin": """Característica: Gastos de cobranza
  Escenario: Pago con 14 días de atraso
    Dado un gasto de cobranza de 3.000 por día
    Y una cuota pagada con 14 días de atraso
    Cuando se registra el pago
    Entonces se causan 42.000 de gastos de cobranza
    Y se aplican antes que cualquier otro concepto

  Escenario: Sin saldo no hay cobranza
    Dado un crédito con saldo de capital 0
    Cuando pasa un periodo
    Entonces no se causan gastos de cobranza""",
                    },
                    {
                        "titulo": "Aplicar un pago tardío con la prelación de cobranza",
                        "como": "analista de cartera", "quiero": "repartir un pago tardío entre cobranza, mora, interés, servicios y capital", "para": "registrar correctamente cada concepto",
                        "gherkin": """Característica: Pago tardío
  Escenario: Primer pago tardío en la cuota 43
    Dado el crédito base al día hasta la cuota 42
    Cuando el cliente paga 174.048,99 con 14 días de atraso en la cuota 43
    Entonces se aplican 42.000 a cobranza
    Y 1.590,46 a interés de mora
    Y 45.741,93 a interés corriente
    Y 63.034,17 a servicios
    Y 21.682,43 a capital
    Y el capital vencido al cierre es 43.590,46""",
                    },
                    {
                        "titulo": "Proyectar el crédito con atraso hasta el horizonte límite",
                        "como": "analista de riesgo", "quiero": "proyectar un crédito con atraso recurrente hasta el plazo límite", "para": "anticipar pérdidas",
                        "gherkin": """Característica: Proyección con atraso recurrente
  Escenario: Horizonte de 120 semanas
    Dado un plazo límite permitido de 120 semanas
    Y pagos con 14 días de atraso desde la cuota 43
    Cuando se proyecta hasta la semana 120
    Entonces el saldo de capital al horizonte es 3.319.011,75
    Y el estado es "En mora\"""",
                        "notas": "Esperar confirmación de negocio (E1) sobre si este resultado es el esperado.",
                    },
                ],
            },
            {
                "titulo": "F3.5 · Cierre por recuperación de la moto (casos 3 y 4)",
                "descripcion": "Dación en pago y retoma: extinción de la deuda contra el valor de la moto, con cuenta por cobrar al fondo de garantías o cuenta por pagar al deudor.",
                "historias": [
                    {
                        "titulo": "Registrar una dación en pago con faltante (caso 3)",
                        "como": "analista de cartera", "quiero": "recibir la moto como parte de pago cuando vale menos que la deuda", "para": "cerrar el crédito y cobrar el faltante al fondo de garantías",
                        "gherkin": """Característica: Dación en pago
  Escenario: Moto con valor menor que la deuda
    Dado el crédito base pagado hasta la cuota 30
    Y sin pagos entre las cuotas 31 y 33
    Cuando en la cuota 34 se recibe la moto por 3.000.000
    Entonces la deuda a extinguir es 4.709.819,75 (capital 4.491.646,20 más intereses corrientes 218.173,56)
    Y se reconoce inventario por 3.000.000
    Y se registra una cuenta por cobrar al FCC por 1.709.819,75
    Y el saldo de capital queda en 0 y el estado es "Cerrado por evento\"""",
                    },
                    {
                        "titulo": "Registrar una retoma con excedente para el deudor (caso 4)",
                        "como": "analista de cartera", "quiero": "recibir la moto cuando su avalúo supera la deuda", "para": "cerrar el crédito y reconocer lo que se le debe al cliente",
                        "gherkin": """Característica: Retoma con excedente
  Escenario: Avalúo mayor que la deuda
    Dado el crédito base pagado hasta la cuota 13
    Y sin pagos entre las cuotas 14 y 16
    Cuando en la cuota 17 se retoma la moto con avalúo de 6.800.000
    Entonces la deuda a extinguir es 5.614.421,54 (capital 5.354.344,01 más intereses 260.077,54)
    Y se reconoce inventario por 6.800.000
    Y se registra una cuenta por pagar al deudor por 1.185.578,46
    Y el estado es "Cerrado por evento\"""",
                    },
                    {
                        "titulo": "Contar cuotas vencidas antes del evento de cierre",
                        "como": "analista de cartera", "quiero": "saber cuántas cuotas lleva vencidas el crédito", "para": "decidir cuándo iniciar dación o retoma",
                        "gherkin": """Característica: Cuotas vencidas
  Escenario: Cuotas vencidas en el siniestro
    Dado un crédito cuyo último periodo pagado es el 30
    Cuando se evalúa el periodo 34
    Entonces las cuotas vencidas son 4
    Y el estado en los periodos 31 a 33 es "En mora\"""",
                    },
                ],
            },
        ],
    },
    # ─────────────────────────────────────────────────────────────────────────────
    {
        "titulo": "E4 · Aplicación automática de abonos (clasificador de eventos)",
        "descripcion": "Que cada abono que registra el CRM se clasifique solo en el caso correcto (normal, inferior, tardío, extra, prepago) y se aplique con las reglas del motor, sin intervención manual.",
        "features": [
            {
                "titulo": "F4.1 · Clasificación del abono en el caso aplicable",
                "descripcion": "Reglas de decisión que, a partir del valor, la fecha y el estado del crédito, determinan qué caso del Excel aplica.",
                "historias": [
                    {
                        "titulo": "Clasificar un abono según valor y fecha",
                        "como": "motor de reglas", "quiero": "decidir qué caso aplica a cada abono", "para": "aplicarlo sin intervención manual",
                        "gherkin": """Característica: Clasificador de abonos
  Esquema del escenario: Caso según el abono
    Dado un crédito con cuota total exigible de 174.048,99 y saldo total de 3.875.622,62
    Cuando llega un abono de <valor> con <dias> días de atraso
    Entonces el motor lo clasifica como "<caso>"

    Ejemplos:
      | valor        | dias | caso                                  |
      | 174.048,99   | 0    | Caso 1 · pago normal                  |
      | 69.619,60    | 0    | Caso 8 · pago inferior                |
      | 174.048,99   | 14   | Caso 9 · pago tardío con cobranza     |
      | 2.174.048,99 | 0    | Caso 6 o 7 · abono extra              |
      | 3.875.622,62 | 0    | Caso 5 · prepago total                |""",
                    },
                    {
                        "titulo": "Pedir la decisión menor plazo o menor cuota en un abono extra",
                        "como": "asesor de cartera", "quiero": "registrar si el cliente quiere menor plazo o menor cuota", "para": "aplicar el abono extra como el cliente lo pidió",
                        "gherkin": """Característica: Elección del tratamiento del abono extra
  Escenario: El cliente elige menor cuota
    Dado un abono mayor a la cuota exigible que no cancela el crédito
    Cuando el CRM envía el abono con la opción "menor cuota"
    Entonces el motor aplica el caso 7

  Escenario: El abono llega sin elección
    Dado un abono extra sin opción indicada
    Cuando el motor lo recibe
    Entonces aplica la opción por defecto definida por negocio
    Y deja una alerta en el CRM para confirmarla con el cliente""",
                        "notas": "Definir la opción por defecto (la ley colombiana permite al cliente elegir).",
                    },
                    {
                        "titulo": "Calcular días de atraso con el calendario de vencimientos",
                        "como": "motor de reglas", "quiero": "calcular los días de atraso de cada abono", "para": "saber si aplica mora y cobranza",
                        "gherkin": """Característica: Días de atraso
  Escenario: Abono después del vencimiento
    Dado una cuota que vence el 12 de octubre de 2026
    Cuando el abono se registra el 26 de octubre de 2026
    Entonces los días de atraso son 14

  Escenario: Abono anticipado
    Dado la misma cuota
    Cuando el abono se registra el 10 de octubre de 2026
    Entonces los días de atraso son 0 y no hay cobranza ni mora""",
                    },
                    {
                        "titulo": "Combinar casos cuando un abono cubre varios eventos",
                        "como": "motor de reglas", "quiero": "aplicar un abono que es tardío y además incompleto", "para": "no perder conceptos cuando se cruzan dos casos",
                        "gherkin": """Característica: Eventos combinados
  Escenario: Pago tardío e inferior
    Dado una cuota vencida hace 14 días
    Cuando el cliente paga el 40 % de la cuota total
    Entonces se causan los gastos de cobranza del caso 9
    Y el pago se reparte con la prelación oficial
    Y lo no cubierto queda vencido como en el caso 8""",
                        "notas": "Este cruce no está en el Excel: pedir a Diego un caso 10 de ejemplo.",
                    },
                ],
            },
            {
                "titulo": "F4.2 · Integridad del registro de abonos",
                "descripcion": "Idempotencia, reversos, orden de procesamiento y auditoría de cada aplicación.",
                "historias": [
                    {
                        "titulo": "Evitar aplicar dos veces el mismo abono",
                        "tecnica": True, "para": "no descuadrar la cartera", "necesita": "que un abono repetido no se aplique dos veces",
                        "gherkin": """Característica: Idempotencia
  Escenario: Abono reenviado por el CRM
    Dado un abono con identificador "REC-2026-0001" ya aplicado
    Cuando el CRM lo vuelve a enviar
    Entonces el motor responde con el resultado de la primera aplicación
    Y no modifica saldos ni genera asientos nuevos""",
                    },
                    {
                        "titulo": "Reversar un abono aplicado por error",
                        "como": "analista de cartera", "quiero": "reversar un abono y recalcular el crédito", "para": "corregir errores de recaudo",
                        "gherkin": """Característica: Reverso de abonos
  Escenario: Reverso de un abono
    Dado un abono aplicado a la cuota 43
    Cuando un usuario autorizado lo reversa indicando el motivo
    Entonces el crédito vuelve al estado anterior al abono
    Y se generan asientos contables de reverso
    Y los abonos posteriores se reaplican en orden cronológico""",
                    },
                    {
                        "titulo": "Guardar la traza de cada aplicación",
                        "como": "auditor", "quiero": "ver qué regla aplicó el motor a cada abono", "para": "explicar cualquier saldo al cliente o al revisor fiscal",
                        "gherkin": """Característica: Auditoría
  Escenario: Consulta de un abono aplicado
    Dado un abono aplicado
    Cuando el auditor consulta su detalle
    Entonces ve el caso aplicado, la versión de reglas, la versión de parámetros, la distribución por concepto, el usuario o sistema origen y la fecha""",
                    },
                ],
            },
        ],
    },
    # ─────────────────────────────────────────────────────────────────────────────
    {
        "titulo": "E5 · Contabilización automática",
        "descripcion": "Cada evento del motor genera sus asientos en partida doble según el plan de cuentas, como en los paneles \"Movimiento contable clave del evento\" del Excel.",
        "features": [
            {
                "titulo": "F5.1 · Plan de cuentas y mapeo por concepto",
                "descripcion": "Catálogo de cuentas y su relación con cada concepto del motor.",
                "historias": [
                    {
                        "titulo": "Configurar el mapeo de conceptos a cuentas contables",
                        "como": "contador", "quiero": "asociar cada concepto del motor a una cuenta del plan de cuentas", "para": "que los asientos salgan solos",
                        "gherkin": """Característica: Mapeo contable
  Escenario: Conceptos mínimos mapeados
    Dado el plan de cuentas de la financiera
    Cuando se configura el producto de moto
    Entonces cada concepto tiene cuenta: caja, cartera vigente, cartera vencida, ingresos por intereses, CxC intereses corrientes, CxC interés de mora, ingresos por mora, CxC gastos de cobranza, ingresos por cobranza, CxP servicios a terceros, inventario de motos, CxC FCC y CxP deudor
    Y el motor no deja originar créditos si falta alguna cuenta""",
                        "notas": "Pedir a contabilidad el plan de cuentas (PUC) y los códigos.",
                    },
                ],
            },
            {
                "titulo": "F5.2 · Asientos por evento",
                "descripcion": "Asientos de recaudo, causación, reclasificación, prepago, abonos, dación y retoma.",
                "historias": [
                    {
                        "titulo": "Contabilizar el recaudo de una cuota normal",
                        "como": "contador", "quiero": "el asiento de cada cuota pagada a tiempo", "para": "reflejar el recaudo en los libros",
                        "gherkin": """Característica: Asiento de cuota normal
  Escenario: Recaudo de la cuota 1
    Dado el pago de la cuota 1 por 174.048,99
    Cuando se contabiliza
    Entonces se debita caja por 174.048,99
    Y se acreditan ingresos por intereses 71.698,88, cartera de créditos 39.315,95 y CxP servicios a terceros 63.034,17
    Y débitos y créditos suman igual""",
                    },
                    {
                        "titulo": "Contabilizar pagos incompletos y reclasificación a cartera vencida",
                        "como": "contador", "quiero": "el asiento del pago inferior y de la reclasificación del capital no pagado", "para": "separar cartera vigente y vencida",
                        "gherkin": """Característica: Asiento de pago inferior
  Escenario: Pago del 40 % en la cuota 43
    Dado un pago de 69.619,60
    Cuando se contabiliza
    Entonces se debita caja por 69.619,60
    Y se acredita CxC interés corriente por 45.741,93 y servicios complementarios por 23.877,67
    Y se reclasifican 65.272,89 de cartera vigente a cartera vencida
    Y el control de débitos menos créditos es 0""",
                    },
                    {
                        "titulo": "Contabilizar cobranza y mora causadas",
                        "como": "contador", "quiero": "causar la cobranza y la mora y aplicar el pago con su prelación", "para": "reconocer esos ingresos",
                        "gherkin": """Característica: Asiento de pago tardío
  Escenario: Primer pago tardío del caso 9
    Dado cobranza causada de 42.000 y mora de 1.590,46
    Cuando se contabiliza el pago de 174.048,99
    Entonces se causa CxC cobranza contra ingresos por cobranza por 42.000
    Y se causa CxC mora contra ingresos por mora por 1.590,46
    Y la aplicación del pago acredita cobranza, mora, interés corriente, servicios y cartera en ese orden
    Y el total de débitos y créditos es 217.639,45""",
                    },
                    {
                        "titulo": "Contabilizar prepagos y abonos extra",
                        "como": "contador", "quiero": "el asiento de los abonos extraordinarios", "para": "reducir la cartera por el valor abonado",
                        "gherkin": """Característica: Asiento de abono extraordinario
  Escenario: Abono extra de 2.000.000
    Dado un abono extra aplicado 100 % a capital
    Cuando se contabiliza
    Entonces se debita caja y se acredita cartera de créditos por 2.000.000

  Escenario: Prepago total
    Dado un prepago de capital de 3.701.573,62
    Cuando se contabiliza
    Entonces se debita caja y se acredita cartera de créditos por 3.701.573,62""",
                    },
                    {
                        "titulo": "Contabilizar dación en pago y retoma",
                        "como": "contador", "quiero": "los asientos de extinción de la deuda contra la moto", "para": "reconocer inventario y saldos con el fondo o el deudor",
                        "gherkin": """Característica: Asiento de dación y retoma
  Escenario: Dación con faltante
    Dado una deuda de 4.709.819,75 y una moto recibida por 3.000.000
    Cuando se contabiliza
    Entonces se debita inventario de motos 3.000.000 y CxC FCC 1.709.819,75
    Y se acredita cartera 4.491.646,20 y CxC intereses corrientes 218.173,56

  Escenario: Retoma con excedente
    Dado una deuda de 5.614.421,54 y una moto avaluada en 6.800.000
    Cuando se contabiliza
    Entonces se debita inventario 6.800.000
    Y se acredita cartera 5.354.344,01, CxC intereses 260.077,54 y CxP deudor 1.185.578,46""",
                    },
                    {
                        "titulo": "Validar que todo asiento esté cuadrado antes de enviarlo",
                        "tecnica": True, "para": "no ensuciar los libros", "necesita": "que el motor rechace asientos descuadrados",
                        "gherkin": """Característica: Control de partida doble
  Escenario: Asiento descuadrado
    Dado un asiento cuyos débitos y créditos difieren en más de 1 COP
    Cuando el motor intenta registrarlo
    Entonces lo rechaza, no actualiza el crédito y genera una alerta""",
                    },
                ],
            },
            {
                "titulo": "F5.3 · Liquidación a terceros y cierre contable",
                "descripcion": "Cuentas por pagar a FCC, asistencia y aseguradora y conciliación periódica.",
                "historias": [
                    {
                        "titulo": "Generar el reporte mensual de pagos a terceros",
                        "como": "tesorero", "quiero": "un reporte por tercero con lo recaudado en el mes", "para": "pagar al fondo, a la asistencia y a la aseguradora",
                        "gherkin": """Característica: Reporte de terceros
  Escenario: Cierre de mes
    Dado los recaudos de servicios de todos los créditos en el mes
    Cuando se genera el reporte mensual
    Entonces se ve por tercero el valor recaudado, el IVA y el valor a girar
    Y los servicios en cuenta por cobrar no se giran hasta que se recauden""",
                        "notas": "Confirmar si a terceros se les gira lo causado o lo recaudado.",
                    },
                ],
            },
        ],
    },
    # ─────────────────────────────────────────────────────────────────────────────
    {
        "titulo": "E6 · Integración con el CRM",
        "descripcion": "El motor de reglas se acopla al CRM: el CRM registra abonos y consulta estados; el motor responde y contabiliza. No requiere front propio.",
        "features": [
            {
                "titulo": "F6.1 · API del motor de reglas",
                "descripcion": "Servicios para simular, originar, registrar abonos y consultar el estado del crédito.",
                "historias": [
                    {
                        "titulo": "Contrato de datos con el CRM",
                        "como": "área de cartera", "quiero": "que el CRM y el motor acuerden qué datos se mandan, qué se recibe y quién es dueño de cada dato", "para": "que los abonos lleguen completos y no se dupliquen gestiones",
                        "gherkin": """Característica: Contrato de integración CRM-motor
  Escenario: Contrato acordado
    Dado el CRM que registra los abonos de los clientes
    Cuando se firma el contrato de integración
    Entonces quedan definidos los campos del abono, el identificador único, el formato de respuesta, los avisos que recibe el CRM y los códigos de error
    Y queda definido que el motor es dueño de saldos, estados y asientos
    Y que el CRM es dueño de clientes, contactos y gestiones de cobro""",
                        "notas": "Pendiente saber qué CRM es y si puede llamar una API y recibir webhooks (o si va por n8n).",
                    },
                    {
                        "titulo": "Exponer el servicio para registrar un abono desde el CRM",
                        "como": "CRM", "quiero": "enviar cada abono al motor", "para": "que se clasifique, aplique y contabilice",
                        "gherkin": """Característica: API de abonos
  Escenario: Abono válido
    Dado un crédito vigente con identificador "CR-0001"
    Cuando el CRM envía un abono con crédito, valor, fecha, medio de pago, identificador único y opción de abono extra
    Entonces el motor responde 201 con el caso aplicado, la distribución por concepto, el nuevo saldo y el estado

  Escenario: Crédito inexistente
    Dado un identificador de crédito que no existe
    Cuando el CRM envía un abono
    Entonces el motor responde 404 y no registra nada""",
                    },
                    {
                        "titulo": "Exponer la consulta del estado y el plan de pagos del crédito",
                        "como": "CRM", "quiero": "consultar saldos, vencidos y próximas cuotas", "para": "mostrarlo al asesor y al cliente",
                        "gherkin": """Característica: Consulta del crédito
  Escenario: Consulta de un crédito en mora
    Dado el crédito del caso 8 después de la cuota 43
    Cuando el CRM consulta su estado
    Entonces recibe saldo de capital, capital vencido, CxC de interés, mora, servicios y cobranza, estado "En mora" y el plan de pagos vigente""",
                    },
                    {
                        "titulo": "Exponer la simulación y originación del crédito",
                        "como": "front de solicitud", "quiero": "simular y originar créditos con las reglas del motor", "para": "que el front no tenga cálculos propios",
                        "gherkin": """Característica: Simulación y originación
  Escenario: Simulación
    Dado los datos de la moto, alistamiento, tecnología, gastos y plazo
    Cuando el front pide una simulación
    Entonces el motor responde monto financiado, número de cuotas, cuota financiera, servicios, cuota total y plan de pagos normal

  Escenario: Originación
    Dado una solicitud aprobada
    Cuando se origina el crédito
    Entonces el motor guarda el crédito con la versión de parámetros vigente y la fecha de desembolso""",
                    },
                    {
                        "titulo": "Asegurar la API del motor",
                        "tecnica": True, "para": "proteger la información financiera", "necesita": "que solo sistemas autorizados usen la API",
                        "gherkin": """Característica: Seguridad de la API
  Escenario: Llamado sin credenciales
    Dado un llamado sin credencial válida
    Cuando llega a cualquier servicio del motor
    Entonces responde 401 y registra el intento

  Escenario: Datos sensibles
    Dado cualquier respuesta del motor
    Cuando contiene datos del cliente
    Entonces solo incluye lo necesario, conforme a la Ley 1581 de 2012""",
                    },
                ],
            },
            {
                "titulo": "F6.2 · Notificaciones y sincronización con el CRM",
                "descripcion": "Eventos del motor hacia el CRM (cambios de estado, mora, cancelación) y conciliación.",
                "historias": [
                    {
                        "titulo": "Avisos al CRM sin pérdida (outbox)",
                        "tecnica": True, "para": "que el CRM no pierda ningún cambio de estado aunque esté caído", "necesita": "guardar cada aviso en la misma transacción del abono y enviarlo con reintentos",
                        "gherkin": """Característica: Envío confiable de avisos
  Escenario: El CRM no responde
    Dado un abono que deja el crédito "En mora"
    Y el CRM caído
    Cuando se aplica el abono
    Entonces el aviso queda guardado en la misma transacción del abono
    Y se reintenta hasta que el CRM confirme la recepción
    Y el CRM no recibe el mismo aviso dos veces como si fuera nuevo""",
                    },
                    {
                        "titulo": "Notificar al CRM los cambios de estado del crédito",
                        "como": "CRM", "quiero": "recibir un evento cuando un crédito cambia de estado", "para": "activar gestiones de cobranza o cierre",
                        "gherkin": """Característica: Eventos al CRM
  Escenario: El crédito entra en mora
    Dado un crédito "Vigente al día"
    Cuando un pago inferior lo deja "En mora"
    Entonces el motor envía al CRM un evento con crédito, estado anterior, estado nuevo y valores vencidos
    Y reintenta el envío si el CRM no responde""",
                    },
                    {
                        "titulo": "Conciliar diariamente los recaudos del CRM con el motor",
                        "como": "tesorero", "quiero": "una conciliación diaria", "para": "detectar abonos no aplicados",
                        "gherkin": """Característica: Conciliación diaria
  Escenario: Abono sin aplicar
    Dado un abono registrado en el CRM que no llegó al motor
    Cuando corre la conciliación diaria
    Entonces aparece en el reporte de diferencias con su identificador y valor""",
                    },
                ],
            },
        ],
    },
    # ─────────────────────────────────────────────────────────────────────────────
    {
        "titulo": "E7 · Front de solicitud inicial (asesores)",
        "descripcion": "Ajustar el front existente (Prestaya para asesores de concesionarios) para que cotice y muestre solo la fase normal (caso 1) con las reglas del motor. Los demás casos no necesitan front.",
        "features": [
            {
                "titulo": "F7.1 · Cotizador con las reglas del motor",
                "descripcion": "Reemplazar el cálculo mensual actual por la simulación semanal del motor.",
                "historias": [
                    {
                        "titulo": "Cotizar con cuotas semanales y tasa EA",
                        "como": "asesor del concesionario", "quiero": "cotizar la moto con cuotas semanales", "para": "darle al cliente la cuota real que va a pagar",
                        "gherkin": """Característica: Cotizador semanal
  Escenario: Cotización base
    Dado que el asesor ingresa una moto de 5.100.000 y un plazo de 20 meses
    Cuando el cotizador consulta al motor
    Entonces muestra 86 cuotas semanales de 174.048,99
    Y muestra el desglose: cuota financiera 111.014,82 y servicios 63.034,17""",
                    },
                    {
                        "titulo": "Mostrar el desglose del monto financiado",
                        "como": "asesor del concesionario", "quiero": "ver qué se suma a la moto en el monto financiado", "para": "explicarle al cliente por qué financia más que la moto",
                        "gherkin": """Característica: Desglose del monto financiado
  Escenario: Desglose visible
    Dado una cotización de una moto de 5.100.000
    Cuando el asesor abre el detalle
    Entonces ve matrícula y RUNT, SOAT, prenda, GPS, gastos comerciales y el total financiado de 5.904.400""",
                    },
                    {
                        "titulo": "Ajustar el flujo de solicitud a la política de cuota inicial definida",
                        "como": "asesor del concesionario", "quiero": "que el front pida cuota inicial solo si la política lo exige", "para": "no cobrar algo que el producto no contempla",
                        "gherkin": """Característica: Cuota inicial en el front
  Escenario: Política sin cuota inicial
    Dado que negocio definió que no hay cuota inicial
    Cuando el asesor cotiza
    Entonces el cotizador no muestra el campo de cuota inicial""",
                        "notas": "Depende de la decisión de E1 (F1.1).",
                    },
                ],
            },
            {
                "titulo": "F7.2 · Plan de pagos normal para el cliente",
                "descripcion": "Entregar al cliente el plan semanal del caso 1 con fechas.",
                "historias": [
                    {
                        "titulo": "Mostrar e imprimir el plan semanal de la operación normal",
                        "como": "asesor del concesionario", "quiero": "entregarle al cliente su plan de pagos semanal", "para": "que conozca fechas y valores desde el inicio",
                        "gherkin": """Característica: Plan de pagos en el front
  Escenario: Plan después del desembolso
    Dado un crédito desembolsado
    Cuando el asesor abre el resumen del crédito
    Entonces ve las 86 cuotas con fecha, cuota financiera, servicios, total y saldo
    Y puede imprimirlo
    Y en celular la tabla se lee sin desplazamiento horizontal de la página""",
                    },
                ],
            },
        ],
    },
    # ─────────────────────────────────────────────────────────────────────────────
    {
        "titulo": "E8 · Calidad y validación contra el Excel",
        "descripcion": "Garantizar que el motor reproduce cada caso del Excel dentro de la tolerancia y que se mantiene así en cada cambio.",
        "features": [
            {
                "titulo": "F8.1 · Pruebas automatizadas de la hoja Validaciones",
                "descripcion": "Las 33 pruebas de la hoja Validaciones como pruebas automáticas del motor.",
                "historias": [
                    {
                        "titulo": "Cronogramas del Excel como datos de prueba",
                        "tecnica": True, "para": "comparar el motor contra el Excel sin copiar cifras a mano", "necesita": "exportar los parámetros y los cronogramas de los 9 casos a archivos que las pruebas puedan leer",
                        "gherkin": """Característica: Datos de prueba desde el Excel
  Escenario: Exportación de los casos
    Dado el Excel "Casos Operaciones V2 mejorado.xlsx"
    Cuando se ejecuta el exportador
    Entonces se genera un archivo por caso con todas las filas y columnas del cronograma
    Y un archivo con las 33 filas de la hoja Validaciones

  Escenario: Nueva versión del Excel
    Dado que Diego entrega una versión corregida del Excel
    Cuando se vuelve a ejecutar el exportador
    Entonces los archivos se regeneran y las pruebas muestran qué cambió""",
                    },
                    {
                        "titulo": "Automatizar las 33 pruebas de la hoja Validaciones",
                        "como": "QA", "quiero": "que cada fila de Validaciones sea una prueba automática", "para": "saber en cada cambio si el motor sigue cuadrando con el Excel",
                        "gherkin": """Característica: Pruebas de validación del Excel
  Esquema del escenario: Prueba de la hoja Validaciones
    Dado el caso <caso> con los parámetros de la hoja Inputs
    Cuando el motor calcula <variable> en el periodo <periodo>
    Entonces el resultado coincide con el Excel dentro de la tolerancia <tolerancia>

    Ejemplos:
      | caso   | variable            | periodo | tolerancia |
      | Caso 1 | Cuota financiera    | 1       | 1 COP      |
      | Caso 1 | Saldo final         | 86      | 1 COP      |
      | Caso 2 | Nueva cuota         | 44      | 1 COP      |
      | Caso 6 | Último periodo pago | 61      | exacto     |
      | Caso 8 | Interés mora        | 44      | 1 COP      |
      | Caso 9 | Cobranza causada    | 43      | 1 COP      |""",
                    },
                    {
                        "titulo": "Devolver a negocio el Excel con la columna Resultado software llena",
                        "como": "Product Owner", "quiero": "llenar la columna Resultado software de la hoja Validaciones con lo que calcula el motor", "para": "que Diego valide los resultados en su propio formato",
                        "gherkin": """Característica: Entrega de resultados a negocio
  Escenario: Excel diligenciado
    Dado el motor con todas las reglas aprobadas
    Cuando se ejecutan las 33 validaciones
    Entonces se genera una copia del Excel con la columna F llena
    Y cada fila indica si cumple la tolerancia""",
                    },
                ],
            },
            {
                "titulo": "F8.2 · Conciliación completa de cronogramas",
                "descripcion": "Comparar fila por fila todos los cronogramas de los 9 casos, no solo los indicadores.",
                "historias": [
                    {
                        "titulo": "Comparar fila por fila los cronogramas de los 9 casos",
                        "como": "QA", "quiero": "comparar cada celda calculada por el motor contra el Excel", "para": "detectar diferencias que los indicadores no muestran",
                        "gherkin": """Característica: Conciliación de cronogramas
  Escenario: Caso sin diferencias
    Dado el cronograma del caso 1 exportado del Excel
    Cuando se compara con el cronograma del motor
    Entonces todas las columnas de todos los periodos difieren en máximo 1 COP

  Escenario: Reporte de diferencias
    Dado una diferencia mayor a la tolerancia
    Cuando termina la comparación
    Entonces el reporte muestra caso, periodo, columna, valor Excel y valor motor""",
                    },
                    {
                        "titulo": "Probar de punta a punta la integración CRM, motor y contabilidad",
                        "como": "QA", "quiero": "simular abonos desde el CRM y revisar saldos y asientos", "para": "validar el flujo completo antes de producción",
                        "gherkin": """Característica: Prueba integral
  Escenario: Ciclo del caso 8 desde el CRM
    Dado un crédito originado en ambiente de pruebas
    Cuando el CRM envía los abonos del caso 8 en orden
    Entonces los saldos del motor coinciden con el Excel
    Y los asientos contables generados cuadran y coinciden con el panel contable del caso""",
                    },
                ],
            },
        ],
    },
    # ─────────────────────────────────────────────────────────────────────────────
    {
        "titulo": "E9 · Plataforma y operación del motor",
        "descripcion": "Persistencia, ambientes, despliegue, monitoreo y procesos programados (cierre diario, causación, usura).",
        "features": [
            {
                "titulo": "F9.1 · Persistencia y ambientes",
                "descripcion": "Base de datos de créditos, movimientos y asientos; ambientes de desarrollo, pruebas y producción.",
                "historias": [
                    {
                        "titulo": "Repositorio y estructura del proyecto",
                        "tecnica": True, "para": "que el equipo empiece a programar el primer día sin bloquearse", "necesita": "un repositorio con el lenguaje, la base de datos local, las convenciones y la estructura de carpetas acordadas",
                        "gherkin": """Característica: Proyecto listo para desarrollar
  Escenario: Arranque en un equipo nuevo
    Dado un desarrollador que clona el repositorio
    Cuando sigue las instrucciones del README
    Entonces compila el proyecto y corre las pruebas en menos de 15 minutos
    Y la base de datos local se levanta con un solo comando

  Escenario: Convenciones escritas
    Dado el repositorio creado
    Cuando se revisa la documentación
    Entonces están el lenguaje y su versión, el estilo de código, las ramas y el flujo de pull request""",
                    },
                    {
                        "titulo": "Historial inmutable de movimientos y recálculo",
                        "tecnica": True, "para": "reconstruir el crédito a cualquier fecha y aceptar pagos con fecha atrasada", "necesita": "guardar cada abono, reverso o cambio de tasa como un movimiento que no se borra y recalcular recorriéndolos en orden (con fotos del estado para que sea rápido)",
                        "gherkin": """Característica: Historial de movimientos
  Escenario: Pago registrado con fecha atrasada
    Dado un crédito con abonos aplicados hasta la cuota 45
    Cuando llega un abono con fecha de la cuota 43 que no se había registrado
    Entonces el motor recalcula el crédito desde la cuota 43 en orden cronológico
    Y genera los asientos de ajuste necesarios

  Escenario: Consulta a una fecha
    Dado un crédito con abonos, reversos y cambio de tasa
    Cuando se consulta su estado al cierre de la cuota 40
    Entonces se obtienen los saldos tal como estaban en esa fecha""",
                    },
                    {
                        "titulo": "Pipeline de integración continua",
                        "tecnica": True, "para": "detectar en cada cambio si el motor deja de cuadrar con el Excel", "necesita": "un pipeline que compile y corra todas las pruebas en cada pull request",
                        "gherkin": """Característica: Integración continua
  Escenario: Pull request con pruebas en verde
    Dado un pull request hacia la rama principal
    Cuando se ejecuta el pipeline
    Entonces corren las pruebas unitarias, las del Excel y la de arquitectura
    Y el resultado queda visible en el pull request

  Escenario: Una prueba falla
    Dado un cambio que rompe una validación del Excel
    Cuando corre el pipeline
    Entonces el pull request no se puede unir a la rama principal""",
                    },
                    {
                        "titulo": "Ambiente de pruebas desplegado",
                        "tecnica": True, "para": "probar la integración con el CRM antes de producción", "necesita": "un ambiente de pruebas con su propia base de datos que se actualiza solo cuando se une un cambio a la rama principal; a producción solo con aprobación del Product Owner",
                        "gherkin": """Característica: Ambiente de pruebas
  Escenario: Despliegue automático
    Dado un cambio unido a la rama principal con el pipeline en verde
    Cuando termina el pipeline
    Entonces la nueva versión queda desplegada en el ambiente de pruebas
    Y el CRM de pruebas puede llamar a la API con sus credenciales

  Escenario: Datos separados
    Dado el ambiente de pruebas
    Cuando se revisa su configuración
    Entonces usa su propia base de datos y ninguna credencial de producción""",
                    },
                ],
            },
            {
                "titulo": "F9.2 · Procesos programados",
                "descripcion": "Cierre diario de cartera: causación de intereses, cálculo de mora, marcación de vencidos y revisión de usura.",
                "historias": [
                    {
                        "titulo": "Ejecutar el cierre diario de cartera",
                        "como": "analista de cartera", "quiero": "que cada día se actualicen vencidos, mora y estados", "para": "tener la cartera al día aunque no haya abonos",
                        "gherkin": """Característica: Cierre diario
  Escenario: Cuota vencida sin pago
    Dado una cuota que venció ayer sin abono
    Cuando corre el cierre diario
    Entonces su capital pasa a vencido, se causa la mora correspondiente y el crédito queda "En mora"
    Y se envía el evento al CRM""",
                    },
                    {
                        "titulo": "Monitorear el motor y alertar fallas",
                        "tecnica": True, "para": "reaccionar antes de que afecte a clientes", "necesita": "alertas cuando falle un proceso o una integración",
                        "gherkin": """Característica: Monitoreo
  Escenario: Falla del cierre diario
    Dado que el cierre diario no termina
    Cuando pasan 30 minutos de la hora programada
    Entonces se envía una alerta al equipo con el error""",
                    },
                ],
            },
        ],
    },
]

# ═════════════════════════════════════════════════════════════════════════════
# CARGA EN AZURE DEVOPS (no hace falta editar nada de aquí para abajo)
# ═════════════════════════════════════════════════════════════════════════════
import base64, getpass, html, json, os, subprocess, sys, urllib.error, urllib.parse, urllib.request

ORGANIZACION = 'motosdelcaribe'
PROYECTO = 'Financiera'
ETIQUETA = 'backlog-motor-reglas-v1'
ETIQUETA_TECNICA = 'historia-tecnica'
RECURSO_AZURE_DEVOPS = '499b84ac-1321-427f-aa17-267ca6975798'


def credencial():
    """PAT de la variable AZURE_DEVOPS_PAT, el que se escriba al pedirlo o la sesión de `az login`."""
    pat = os.environ.get('AZURE_DEVOPS_PAT') or getpass.getpass(
        'Token personal (PAT) de Azure DevOps con permiso Work Items: Read & Write\n'
        '(Enter vacío para usar la sesión de Azure CLI): ').strip()
    if pat:
        return 'Basic ' + base64.b64encode(f':{pat}'.encode()).decode()
    token = subprocess.run(['az', 'account', 'get-access-token', '--resource', RECURSO_AZURE_DEVOPS, '--query', 'accessToken', '-o', 'tsv'],
                           capture_output=True, text=True, shell=os.name == 'nt').stdout.strip()
    if not token:
        sys.exit('Sin credenciales: crea un PAT en Azure DevOps (User settings → Personal access tokens) o ejecuta `az login`.')
    return 'Bearer ' + token


class Azure:
    def __init__(self, org, proyecto):
        self.base = f'https://dev.azure.com/{org}/{urllib.parse.quote(proyecto)}/_apis'
        self.auth = credencial()

    def pedir(self, metodo, ruta, cuerpo=None, tipo='application/json'):
        url = f'{self.base}/{ruta}{"&" if "?" in ruta else "?"}api-version=7.1'
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
        req = urllib.request.Request(url, data=datos, method=metodo, headers={'Authorization': self.auth, 'Content-Type': tipo})
        try:
            with urllib.request.urlopen(req) as r:
                cuerpo_resp = r.read()
                if r.headers.get_content_type() != 'application/json':
                    sys.exit('Azure DevOps pidió iniciar sesión: el token no tiene acceso a esta organización.')
                return json.loads(cuerpo_resp or b'{}')
        except urllib.error.HTTPError as e:
            sys.exit(f'Error {e.code} en {metodo} {ruta}: {e.read().decode(errors="replace")[:500]}')
        except urllib.error.URLError as e:
            sys.exit(f'No se pudo conectar con Azure DevOps: {e}')

    def tipos(self):
        return {t['name'] for t in self.pedir('GET', 'wit/workitemtypes')['value']}

    def existentes(self):
        wiql = {'query': "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project "
                         f"AND [System.Tags] CONTAINS '{ETIQUETA}'"}
        ids = [w['id'] for w in self.pedir('POST', 'wit/wiql', wiql)['workItems']]
        vistos = {}
        for i in range(0, len(ids), 200):
            lote = ','.join(map(str, ids[i:i + 200]))
            for w in self.pedir('GET', f'wit/workitems?ids={lote}&fields=System.Title,System.WorkItemType')['value']:
                vistos[(w['fields']['System.WorkItemType'], w['fields']['System.Title'])] = w['id']
        return vistos

    def crear(self, tipo, campos, padre=None):
        ops = [{'op': 'add', 'path': f'/fields/{k}', 'value': v} for k, v in campos.items()]
        if padre:
            ops.append({'op': 'add', 'path': '/relations/-', 'value': {
                'rel': 'System.LinkTypes.Hierarchy-Reverse', 'url': f'{self.base}/wit/workItems/{padre}'}})
        return self.pedir('POST', f'wit/workitems/${urllib.parse.quote(tipo)}', ops, 'application/json-patch+json')['id']


def gherkin_html(texto):
    return '<pre>' + html.escape(texto) + '</pre>'


def titulo_historia(h):
    return ('[HT] ' if h.get('tecnica') else '') + h['titulo']


def descripcion_historia(h):
    if h.get('tecnica'):
        partes = [f"<p><b>Historia técnica.</b> <b>Para</b> {html.escape(h['para'])}, <b>se necesita</b> {html.escape(h['necesita'])}.</p>"]
    else:
        partes = [f"<p><b>Como</b> {html.escape(h['como'])}<br><b>quiero</b> {html.escape(h['quiero'])}<br><b>para</b> {html.escape(h['para'])}</p>"]
    if h.get('notas'):
        partes.append(f"<p><b>Notas para refinamiento:</b> {html.escape(h['notas'])}</p>")
    partes.append('<p><i>Fuente de reglas: Excel "Casos Operaciones V2 mejorado.xlsx" (Diego González).</i></p>')
    return ''.join(partes)


def main():
    for flujo in (sys.stdout, sys.stderr):
        if hasattr(flujo, 'reconfigure'):
            flujo.reconfigure(encoding='utf-8')
    org = input(f'Organización [{ORGANIZACION}]: ').strip() or ORGANIZACION
    proyecto = input(f'Proyecto [{PROYECTO}]: ').strip() or PROYECTO

    az = Azure(org, proyecto)
    tipos = az.tipos()
    # Agile: User Story · Scrum: Product Backlog Item · CMMI: Requirement · Basic: Issue (sin Feature).
    tipo_historia = next((t for t in ['User Story', 'Product Backlog Item', 'Requirement', 'Issue'] if t in tipos), None)
    if 'Epic' not in tipos or tipo_historia is None:
        sys.exit(f'El proceso del proyecto no tiene los tipos esperados. Tipos disponibles: {sorted(tipos)}')
    usa_feature = 'Feature' in tipos
    criterios_aparte = tipo_historia != 'Issue'

    n_f = sum(len(e['features']) for e in EPICAS)
    n_h = sum(len(f['historias']) for e in EPICAS for f in e['features'])
    vistos = az.existentes()
    print(f'\nProyecto: {org}/{proyecto}')
    print(f'Jerarquía: Epic → {"Feature → " if usa_feature else ""}{tipo_historia}')
    n_t = sum(1 for e in EPICAS for f in e['features'] for h in f['historias'] if h.get('tecnica'))
    print(f'Backlog: {len(EPICAS)} épicas, {n_f} features, {n_h} historias ({n_h - n_t} de usuario y {n_t} técnicas)')
    if vistos:
        print(f'Ya existen {len(vistos)} elementos de una carga anterior (etiqueta {ETIQUETA}); no se duplicarán.')
    if input('\n¿Crear los elementos ahora? (s/n): ').strip().lower() not in ('s', 'si', 'sí', 'y', 'yes'):
        sys.exit('Cancelado. No se creó nada.')

    creados = 0

    def asegurar(tipo, campos, padre=None, tecnica=False):
        nonlocal creados
        clave = (tipo, campos['System.Title'])
        if clave in vistos:
            return vistos[clave]
        campos['System.Tags'] = ETIQUETA + ('; ' + ETIQUETA_TECNICA if tecnica else '')
        nuevo = az.crear(tipo, campos, padre)
        vistos[clave] = nuevo
        creados += 1
        print(f'  + {tipo} #{nuevo}: {campos["System.Title"]}')
        return nuevo

    for e in EPICAS:
        id_e = asegurar('Epic', {'System.Title': e['titulo'], 'System.Description': f"<p>{html.escape(e['descripcion'])}</p>"})
        for f in e['features']:
            padre = id_e
            if usa_feature:
                padre = asegurar('Feature', {'System.Title': f['titulo'], 'System.Description': f"<p>{html.escape(f['descripcion'])}</p>"}, id_e)
            for h in f['historias']:
                campos = {'System.Title': titulo_historia(h), 'System.Description': descripcion_historia(h)}
                if not usa_feature:
                    campos['System.Description'] = f"<p><b>Feature:</b> {html.escape(f['titulo'])}</p>" + campos['System.Description']
                if criterios_aparte:
                    campos['Microsoft.VSTS.Common.AcceptanceCriteria'] = gherkin_html(h['gherkin'])
                else:
                    campos['System.Description'] += '<p><b>Criterios de aceptación</b></p>' + gherkin_html(h['gherkin'])
                asegurar(tipo_historia, campos, padre, h.get('tecnica', False))

    print(f'\nListo: {creados} elementos creados. Revísalos en Boards → Backlogs (nivel Epics), filtrando por la etiqueta {ETIQUETA}.')


if __name__ == '__main__':
    main()
