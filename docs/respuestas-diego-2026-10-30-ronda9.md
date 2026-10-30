# Respuestas de Diego, ronda 9 (2026-10-30)

3 de 3 respondidas (el cuestionario sigue «en progreso»; las 7 preguntas de la ronda 10 aún no tienen respuesta). Contraste entre lo que marcó y lo que escribió, y qué se implementa o se pregunta.

| Pregunta | Respuesta de Diego | Seguro de implementar | Va a la ronda 11 |
|---|---|---|---|
| R9-COBRO-MOMENTO | Marcó «Al cumplirse la 2.ª cuota atrasada: primer cobro y suspensión de fondo y asistencia desde ahí» (lectura A). Sin comentario. | Primer cobro de cobranza y suspensión de fondo y asistencia desde la 2.ª cuota atrasada (D62). | — |
| R9-SEGURO-TODO-RIESGO | Marcó «Es uno nuevo que se suma al seguro actual». Comentario: «Inicialmente pactemos como un porcentaje mensual sobre financiación inicial del 0,3% y se paga igual que el de vida, pero se debe sumar el 19% de IVA en el calculo: ejm: si el seguro da $10.000, el cobro al cliente es de $11.900 pero en la contabilidad debe quedar segmentado lo que es el SEGURO y el IVA». La opción y el comentario coinciden. | Seguro todo riesgo nuevo que se suma al de vida; 0,3 % mensual sobre la financiación inicial, pagado como el de vida, más 19 % de IVA; la contabilidad segmenta seguro e IVA (D63). | Los cuatro detalles de abajo. |
| R9-SERVICIOS-AL-PONERSE-AL-DIA | Marcó «Se pierden: los servicios se retoman desde el periodo siguiente y los suspendidos no se cobran». Sin comentario. | Los servicios suspendidos no se cobran de forma retroactiva (D64). | — |

## Preguntas que se generan (ronda 11)

1. **R11-SEGUROS-TRAS-SUSPENSION.** Diego dijo que tras 2 periodos atrasados «solo se causa el seguro». Ahora hay dos seguros (vida y todo riesgo): ¿se siguen causando los dos o solo uno? El motor hará los dos de forma provisional.
2. **R11-BASE-FINANCIACION-INICIAL.** El 0,3 % es «sobre la financiación inicial»: ¿es el monto financiado a secas o incluye los servicios y gastos que se financian?
3. **R11-PERIODIZACION-SEGURO.** «Se paga igual que el de vida»: ¿el 0,3 % mensual se convierte a semanal y quincenal igual que el seguro de vida actual? El motor copia esa conversión mientras se confirma.
4. **R11-CONTABILIDAD-SEGURO-IVA.** ¿El seguro todo riesgo es ingreso propio o un pasivo a pagar a la aseguradora? ¿El IVA es IVA generado? Las cuentas siguen provisionales hasta tener el PUC.

El motor de estas tres decisiones (D62 a D64) está en implementación en la rama `feat/ronda9-motor`.
