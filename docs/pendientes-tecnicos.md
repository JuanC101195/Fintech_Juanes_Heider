# Pendientes técnicos (internos)

Estado al 2026-10-08, después de integrar los 9 casos: **124 pruebas, 0 fallas**. Los 9 casos del Excel se reproducen celda por celda.

## Contabilidad
1. **Semanas sin asiento en el caso 8 (44 a 89):** pagos que recuperan vencidos y pagan mora sin cobranza. `RecaudoCuota` los excluye, `PagoInferior` solo toma la semana "Pago inferior" y `CobranzaYMora` exige cobranza. Falta una regla general de "recaudo con vencidos" (o generalizar `PagoInferior`) y su reclasificación vencida ↔ vigente semana a semana.
2. `Movimiento` no guarda el tipo de recuperación (dación / retoma) ni el desglose de lo extinguido: las reglas lo infieren por el signo del residuo. Agregarlo si Diego unifica con la mora (P7).
3. Nombres de cuenta: el Excel usa "CxC intereses corrientes" en unos paneles y "CxC interés corriente" en otros; "Servicios complementarios" se mapeó a `CXP_SERVICIOS_TERCEROS`.

## Motor
4. `plazoLimiteSemanas` vale 120 por defecto: un crédito de más de 27 meses no alcanza su última cuota. Debería derivarse del plazo (cuotas pactadas + margen).
5. `Movimiento.cuotaFinanciera` guarda la cuota pactada; el Excel muestra lo efectivamente cobrado en la última cuota (MIN(cuota, saldo + interés)). Exponer ambas si el CRM las necesita.
6. Texto de eventos: "Pago normal - aplica vencidos" (caso 8) y "Periodo adicional con cobranza" (caso 9) no se distinguen; no se comparan en las pruebas.
7. Combinación pago tardío + incompleto (P10) no está en el Excel: el motor la soporta (`PagoParcial(fraccion, dias)`) pero no hay caso de referencia.

## Próximos módulos (sprints 2 y 3 del backlog)
8. `creditos`: persistencia (PostgreSQL), historial inmutable de movimientos (HT-03), versionado de parámetros (HT-04).
9. `abonos`: clasificador de abonos (HU-25), idempotencia (HT-05), API para el CRM (HU-23).
10. `procesos`: cierre diario (HU-36); `integracion-crm`: avisos con outbox (HT-07).
11. Herramienta que llene la columna "Resultado software" de la hoja Validaciones para devolverle el Excel a Diego (HU-17b).
