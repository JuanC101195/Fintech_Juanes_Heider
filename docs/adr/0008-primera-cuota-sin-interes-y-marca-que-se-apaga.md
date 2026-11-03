# ADR 0008 · Primera cuota sin interés sobre el capital no cobrado y marca que se apaga

Fecha: 2026-11-02 · Estado: aceptada · Decisiones: D84, D85, D86 (ronda 13 de Diego, `respuestas-diego-2026-11-02-ronda13.md`)

## Contexto

La ronda 12 dejó dos supuestos del motor sin confirmar (interés del capital que la primera cuota proporcional no cobra; la entrega a más de un periodo del primer vencimiento) y la duda de si la marca «listo para recuperar» sigue mientras el saldo en mora baje de 2 cuotas. Diego respondió la ronda 13 el 2-nov-2026.

## Decisión

1. **Capital no cobrado sin interés (D84).** `alcanceProrrateoPrimeraCuota=CUOTA_COMPLETA_SIN_INTERES`: en la primera cuota prorrateada el capital que no se cobra queda aparte (`EstadoCorrida.capitalSinInteres`); el interés de los periodos siguientes se calcula sin él y el capital contractual del plan sale de ese mismo saldo, de modo que el plan es el de una primera cuota completa. La última cuota suma ese capital (116.705,64 en el ejemplo del Excel con 3 días de 7; con interés eran 157.091,51). El valor viaja en la `Instantanea` (`capitalSinInteres`) para que reanudar dé lo mismo.
2. **Primer vencimiento dentro del primer periodo (D84).** El API (`FechasCredito.validarContra`) rechaza con NEGOCIO un primer vencimiento a más días que el periodo (7, 15 o 30). No va en el motor porque los escenarios sintéticos y la auditoría usan calendarios arbitrarios; el Excel no tiene tope.
3. **La marca se apaga (D85).** Con `marcaPorCociente=true` la marca vale lo que dice el cociente al cerrar el periodo (y solo con el crédito en mora); ya no persiste. Con `marcaPorCociente=false` sigue persistiendo (D54, D67). La cobranza ya solo se causaba mientras el cociente fuera 2 o más.
4. **Congelar (D86)** corre el calendario; es de la fábrica de créditos (fase 3) y el motor no lo implementa. El backtesting lo aplica.

## Consecuencias

- Versión de reglas nueva `negocio-2026-11-02` (huella y pruebas de caracterización de NEGOCIO regeneradas; las de EXCEL no cambian).
- Las pruebas de rondas anteriores que asumían la marca persistente corren ahora con `conMarcaPorCociente(false)`.
- Pendiente: el contador de cuotas vencidas del motor y el cociente pueden discrepar (un crédito con 4 cuotas vencidas por contador puede quedar sin marca si en la semana pagó casi todo lo pendiente): R14-MARCA-SEMANAS.
