# ADR 0001 · Monolito modular

**Estado:** aceptada · 2026-10-08

## Contexto

El motor aplica reglas de dinero: cada abono causa intereses, se reparte por prelación, actualiza saldos, genera un asiento y avisa al CRM. Es un solo producto (crédito de moto) y un equipo pequeño. Se evaluaron tres opciones: monolito modular, microservicios y Apache Fineract como núcleo.

## Decisión

Un **monolito modular**: un solo servicio, una base de datos, módulos con fronteras estrictas verificadas por Spring Modulith (`ModularidadTest`). Java 21 + Spring Boot 4.

Módulos: `nucleo` (cálculo puro, del que dependen todos), `creditos`, `abonos`, `contabilidad`, `integracion-crm`, `procesos`, `api`.

## Razones

- **Un abono se aplica completo o no se aplica:** una sola transacción de base de datos, sin sagas ni compensaciones entre servicios.
- **Microservicios** no se justifican: un producto, un equipo, sin necesidades de escala distintas por módulo.
- **Fineract** no cubre de forma nativa la conversión EA → semanal efectiva (divide la nominal entre 52, ver `LoanApplicationTerms.java`), la cobranza por días, la mora del caso 9, los servicios con IVA girados a terceros, la dación ni la retoma. Habría que mantener Fineract **y** un servicio con esas reglas. Se usa como referencia de diseño (productos parametrizables, estrategias de aplicación de pagos, reprogramación), no como dependencia.

## Cuándo separar un módulo

Solo con una razón concreta: varios CRM o un CRM que cambia mucho (`integracion-crm`), otro producto que necesite la contabilidad (`contabilidad`) o procesos que compitan por recursos con la API (`procesos`).
