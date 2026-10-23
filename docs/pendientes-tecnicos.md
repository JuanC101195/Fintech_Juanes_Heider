# Pendientes técnicos (internos)

Estado al 2026-10-16, con el Excel de Diego del 11 de octubre: **167 pruebas, 0 fallas**. Los 12 casos se reproducen celda por celda.

## Contabilidad
1. ~~Semanas sin asiento en los casos 8 y 12~~ **Resuelto (2026-10-16):** regla `RecaudoConVencidos` con causación de mora, aplicación por prelación y reclasificación vencida ↔ vigente. Pendiente menor: el caso 9 (`CobranzaYMora`) sigue el panel del Excel, que no reclasifica el capital vencido.
2. `Movimiento` no guarda el tipo de recuperación (dación / retoma): las reglas lo infieren por el signo del residuo. El desglose de lo extinguido ya viene en `Movimiento.extinguido`.
3. Nombres de cuenta: el Excel usa "CxC intereses corrientes" en unos paneles y "CxC interés corriente" en otros; "Servicios complementarios" se mapeó a `CXP_SERVICIOS_TERCEROS`.

## Motor
4. `plazoLimitePeriodos` vale MAX(120, cuotas) por defecto. En el Excel el caso 8 tiene límite de 100 semanas con 104 cuotas: la tabla se corta en mora. Diego lo cerró (X4, D47): 104 cuotas; y en un crédito corto el límite es su propio plazo (D53): MIN(plazo del crédito, `plazoLimiteMeses`) en periodos. **Por implementar.**
5. `Movimiento.cuotaFinanciera` guarda la cuota pactada; el Excel muestra lo efectivamente cobrado en la última cuota (MIN(cuota, saldo + interés)). Exponer ambas si el CRM las necesita.
6. Texto de eventos: "Pago normal - aplica vencidos" (caso 8) y "Periodo adicional con cobranza" (caso 9) no se distinguen; no se comparan en las pruebas.
7. Combinación pago tardío + incompleto (P10) no está en el Excel: el motor la soporta (`PagoParcial(fraccion, dias)`) pero no hay caso de referencia.

## Próximos módulos (sprints 2 y 3 del backlog)
8. `creditos`: persistencia (PostgreSQL), historial inmutable de movimientos (HT-03), versionado de parámetros (HT-04).
9. `abonos`: clasificador de abonos (HU-25), idempotencia (HT-05), API para el CRM (HU-23).
10. `procesos`: cierre diario (HU-36); `integracion-crm`: avisos con outbox (HT-07).
11. ~~Herramienta que llene la columna "Resultado software" de la hoja Validaciones para devolverle el Excel a Diego (HU-17b).~~ **Hecho (2026-10-23):** `herramientas/llenar_validaciones.py` (uso en `docs/api.md`); 45 de 45 pruebas OK contra el API.

## Reglas de las rondas 5 y 6 por implementar en el motor (2026-10-22)
12. **Prorrateo de la primera cuota (D48):** solo el interés por los días reales, más los días previos a la primera cuota; capital y servicios completos; en las tres periodicidades.
13. **Pago antes de la primera cuota (D49):** abono extra a capital, de una vez (menor plazo o menor cuota).
14. **Límite de un crédito corto (D53):** el plazo límite es el plazo del crédito (tope `plazoLimiteMeses` = 24).
15. **Marca de recuperación (D54):** el motor solo marca el crédito al llegar a 2 periodos atrasados (D46); la marca persiste aunque el cliente se ponga al día y solo una persona ordena la dación o la retoma (quién y cuándo, en bitácora).
16. **Costo de la dación (D52):** se suma al faltante que cubre el fondo de garantías; quitar la marca de provisional.
17. Ya cubiertos sin cambio: caso 13 con el sobrante por la prelación (D50) y usura a 1 día hábil, requisito del proceso (D51).

## ADR 0002 paso 8 y cobranza con vigencias (2026-10-23)
18. **Hecho: mora mensual por días reales (PPER-5, D27).** Clave `diasMora` (`PERIODO_NOMINAL` en el Excel, `DIAS_CALENDARIO_MENSUAL` en `config/`). Con fechas en el escenario (`Escenario.conCalendario`), un crédito mensual causa la mora del capital vencido con (1 + diaria)^días − 1 y los días reales del 5 al 5 (28 a 31). Sin fechas, o en quincenal y semanal, sigue la tasa del periodo. **Pendiente de Diego:** si la quincenal (13 a 16 días reales) también cuenta días reales; D27 solo habla de la mensual. Prueba: `MoraYPrepagoConCalendarioTest`.
19. **Hecho: prepago a mitad de periodo con fechas (PPER-7, D27).** `PrepagoTotal.enFecha(fecha)`: los días van del vencimiento de la cuota del periodo a la fecha del prepago (`CalendarioPagos.diasAtraso`), con la misma regla `interesPrepagoPorDias`. Supuestos conservadores: una fecha anterior al vencimiento cobra 0 días (la cuota ya cobró el periodo, como el Excel) y una fecha desde el vencimiento siguiente se rechaza (va en el otro periodo). El API todavía no recibe la fecha ni el calendario.
20. **Hecho: cobranza diaria con vigencias (D26, D44), modelo puro.** `nucleo/VigenciasCobranza` (fecha desde → valor, inmutable), `ParametrosProducto.conCobranzaVigente(tabla, originación)` copia el valor vigente al crédito; un cambio posterior de la tabla no lo afecta. Sin tabla, o antes de la primera vigencia, queda `cobranzaDiaria` (3.000 o `ReglasNegocio.cobranzaDiaria`). Bitácora como punto de extensión: `BitacoraParametros` + `CambioParametro` (quién, cuándo, valor anterior y nuevo, justificación), sin persistencia. Supuestos conservadores pendientes de confirmar: las vigencias solo se agregan hacia adelante y no pueden empezar antes del día del cambio. **Falta:** guardar la tabla en `creditos` (base de datos), validar el rol de administrador en el API y que el API use la tabla al originar (hoy usa `ReglasNegocio.cobranzaDiaria`).

## Auditoría financiera independiente (2026-10-23)
Pruebas: `auditoria/HallazgosAuditoriaTest` (un repro por hallazgo) y `auditoria/PropiedadesFinancierasAuditTest` (3.000 escenarios aleatorios con semilla fija 20261023, ~5 s; invariantes de no negatividad, continuidad, conservación del capital y de cada CxC, cierre, capital vencido ≤ saldo y caja contable = pago del cliente). Huellas de `ReplicaSemanalNoCambiaTest` y los 13 casos del Excel: sin cambios.

21. **Hecho (H1): Caja contabilizada dos veces.** Una última cuota residual pagada con `PagoValor` por todo lo exigible salía con evento "Pago inferior" y era a la vez recaudo normal: `RecaudoCuota` y `PagoInferior` debitaban la Caja (144.939,30 por un pago de 72.469,65; repro: `replicaExcel`, `en(10, AbonoExtra(5.300.000))`, `en(14, PagoValor(72.470))`). `PagoInferior.aplica` excluye ahora el recaudo normal y el pago tardío con cobranza, y `Contabilizador` verifica en cada periodo que la Caja de los asientos = `Movimiento.pagoTotalCliente()` (±0,01); si no, lanza `CajaDescuadradaException`.
22. **Hecho (H3): capital vencido con abono o prepago SOLO_CAPITAL (perfil EXCEL).** El abono va primero al capital vigente y lo que lo supere extingue capital vencido; el prepago total extingue también el vencido. Ese capital vencido se reporta en `pendientesPagadosExtra.capital` (crédito a Cartera capital vencida). Además el capital contractual nunca es negativo ni mayor que el capital vigente real (tope solo si la diferencia supera medio centavo, para no tocar los residuos de los casos del Excel).
23. **Hecho (H4):** el núcleo (`Instruccion`) y el API rechazan valores imposibles: pago negativo, abono ≤ 0, fracción fuera de (0, 1], tasa nueva ≤ 0, valor de la moto ≤ 0, días negativos.
24. **Hecho (H5):** dos instrucciones del mismo tipo (pago, abono, prepago, cierre, usura) en un periodo se rechazan con un error que nombra el periodo. No se suman ni se elige una. Varios cambios de tasa en un periodo siguen aplicándose en orden.
25. **Hecho, decisión conservadora (H6): cambio de tasa o usura después de la última cuota pactada.** Antes lanzaba "El número de periodos debe ser positivo" (repro: mensual n = 11, `en(6, PagoParcial(0,654))`, `en(12, CambioTasa(0,4178))`) y un lote de usura caía con cualquier crédito en mora fuera de plazo. Ahora la tasa nueva queda registrada (rige el interés y la mora desde ese periodo) pero no se recalculan la cuota ni el plazo: no quedan cuotas y lo pendiente es capital vencido. **Pregunta para Diego:** ¿así, o la tasa nueva no debe aplicar a un crédito vencido fuera de plazo?
26. **Hecho (H9):** con tasa 0, el número de cuotas hasta saldo cero (abono a menor plazo, caso 10) es saldo / cuota redondeado hacia arriba; antes daba NaN → 0 cuotas y cortaba el plan.

### Preguntas para Diego (no implementadas: dependen de reglas de negocio)
27. **H2 · Tolerancia de redondeo al peso.** La cuota total del ejemplo es 163.440,4465. Un cliente que paga en pesos enteros, `desde(1, PagoValor(163.440))`, queda cada periodo como "Pago inferior" por 0,45 COP, acumula mora sobre ese residuo y el crédito se marca para recuperación (2 cuotas atrasadas, D46/D54). ¿Se acepta una tolerancia (p. ej., pagos que difieren menos de 1 peso de la cuota cuentan como completos) y dónde se absorbe el residuo (P2)?
28. **H7 · `PagoValor` inferior sin días de atraso.** `en(3, PagoParcial(0,4))` causa solo el seguro (D17: fondo y asistencia solo en periodos pagados al día), pero `en(3, PagoValor(<el mismo valor>, 0))` causa los tres servicios porque el motor trata todo `PagoValor` con 0 días como "al día". ¿Un pago incompleto en la fecha es "al día" para D17?
29. **H8 · `PagoValor` mayor que lo exigible.** `en(5, PagoValor(99.000.000))` se recorta a lo exigible sin aviso; el sobrante no queda registrado. ¿Se rechaza, se devuelve (CxP al cliente) o se aplica como abono extraordinario (D11, D12, D30, D36)?
30. **Tolerancia `Calc.esCero` (0,005).** Un saldo o pendiente de hasta medio centavo se considera cero y el crédito se cancela, condonando ese residuo. ¿Se acepta, o el residuo debe cobrarse o redondearse al peso en la última cuota (P2)?
