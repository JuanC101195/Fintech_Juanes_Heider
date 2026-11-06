-- Esquema del PostgreSQL del backtesting (base "backtesting").
-- Idempotente: se puede aplicar varias veces. Reconstruido de los scripts de carga
-- (cargar_pg.py, cargar_activos.py, cargar_activas_fin_mes.py, cargar_cartas_fin_mes.py)
-- y de las consultas de nube/src/contrato.js. Los indices estan en 002_indices.sql.

CREATE TABLE IF NOT EXISTS tasa_usura (
  modalidad text,
  resolucion text,
  vigencia_desde date,
  vigencia_hasta date,
  interes_bancario_corriente numeric,
  usura_ea numeric,
  fuente text,
  PRIMARY KEY (modalidad, vigencia_desde)
);

CREATE TABLE IF NOT EXISTS contrato (
  id text PRIMARY KEY,
  sede text,
  contrato text,
  cohorte text,
  estado_real text,
  fecha_inicio date,
  fecha_fin date,
  cuotas int,
  cuota_real_miles numeric,
  inversion numeric,
  inversion_imputada boolean,
  linea_usura text,
  tasa_inicial_ea numeric,
  pagos_reales int,
  multas_reales_miles numeric,
  dias_justificados int,
  valor_total_miles numeric,
  saldo_real_miles numeric
);
ALTER TABLE contrato
  ADD COLUMN IF NOT EXISTS valor_total_miles numeric,
  ADD COLUMN IF NOT EXISTS saldo_real_miles numeric;

CREATE TABLE IF NOT EXISTS pago_real (
  id text REFERENCES contrato(id),
  fecha date,
  valor_miles numeric,
  multa_miles numeric
);

CREATE TABLE IF NOT EXISTS resultado (
  corrida text,
  id text REFERENCES contrato(id),
  sim_pagado numeric,
  sim_saldo_final numeric,
  sim_pendientes numeric,
  sim_mora numeric,
  sim_cobranza numeric,
  sim_interes numeric,
  sim_servicios numeric,
  sim_cuotas_vencidas_final numeric,
  sim_cuotas_vencidas_max numeric,
  sim_marca_recuperacion boolean,
  sim_periodo_marca int,
  sim_estado_final text,
  sim_periodos int,
  periodo_corte int,
  real_ea_con_multas numeric,
  cv_efectivo_max numeric,
  marca_efectivo boolean,
  periodo_marca_efectivo int,
  sim_proyectado_total numeric,
  PRIMARY KEY (corrida, id)
);
ALTER TABLE resultado
  ADD COLUMN IF NOT EXISTS cv_efectivo_max numeric,
  ADD COLUMN IF NOT EXISTS marca_efectivo boolean,
  ADD COLUMN IF NOT EXISTS periodo_marca_efectivo int,
  ADD COLUMN IF NOT EXISTS sim_proyectado_total numeric;

CREATE TABLE IF NOT EXISTS periodo (
  corrida text,
  id text REFERENCES contrato(id),
  periodo int,
  fecha_vencimiento date,
  instruccion text,
  valor_real numeric,
  dias_atraso int,
  justificado boolean,
  estado text,
  cuotas_vencidas numeric,
  saldo_inicial numeric,
  interes_corriente numeric,
  interes_mora numeric,
  cobranza numeric,
  servicios numeric,
  pago_total_cliente numeric,
  saldo_final numeric,
  listo_recuperacion boolean,
  tasa_ea numeric,
  cuota_financiera numeric,
  pago_capital numeric,
  pago_interes numeric,
  pago_mora numeric,
  pago_cobranza numeric,
  pago_servicios numeric,
  capital_vencido numeric,
  cv_efectivo int,
  usura_ea numeric,
  logica text,
  pago_fondo numeric,
  pago_seguro numeric,
  pago_asistencia numeric,
  saldo_en_mora numeric,
  cxc_mora numeric,
  PRIMARY KEY (corrida, id, periodo)
);
ALTER TABLE periodo
  ADD COLUMN IF NOT EXISTS cv_efectivo int,
  ADD COLUMN IF NOT EXISTS usura_ea numeric,
  ADD COLUMN IF NOT EXISTS logica text,
  ADD COLUMN IF NOT EXISTS pago_fondo numeric,
  ADD COLUMN IF NOT EXISTS pago_seguro numeric,
  ADD COLUMN IF NOT EXISTS pago_asistencia numeric,
  ADD COLUMN IF NOT EXISTS saldo_en_mora numeric,
  ADD COLUMN IF NOT EXISTS cxc_mora numeric;

-- Proyeccion de cuotas futuras de los contratos activos (sin FK: se recarga por id).
CREATE TABLE IF NOT EXISTS proyeccion (
  corrida text,
  id text,
  periodo int,
  fecha_vencimiento date,
  proyectado boolean,
  pago_capital numeric,
  pago_interes numeric,
  pago_mora numeric,
  pago_cobranza numeric,
  pago_servicios numeric,
  pago_total numeric,
  saldo_final numeric,
  PRIMARY KEY (corrida, id, periodo)
);

-- Foto de la flota al ultimo dia de cada mes (de motos_app_hist).
CREATE TABLE IF NOT EXISTS activas_fin_mes (
  mes text,           -- 'YYYY-MM'
  fecha_foto date,
  contrato text,
  PRIMARY KEY (mes, contrato)
);

-- Pagos por carta (liquidaciones de cierre) por mes y motivo.
CREATE TABLE IF NOT EXISTS carta_fin_mes (
  mes text,
  motivo text,
  pagos int,
  valor_miles numeric,
  PRIMARY KEY (mes, motivo)
);

-- Fecha de corte de los datos de los activos (una sola fila).
CREATE TABLE IF NOT EXISTS corte_activos (
  corte date
);

-- Vistas de apoyo (analisis en SQL; la nube consulta las tablas directamente).
CREATE OR REPLACE VIEW vw_comparacion AS
SELECT c.id, c.sede, c.contrato, c.cohorte, c.estado_real, c.fecha_inicio, c.fecha_fin, c.cuotas, c.inversion, c.inversion_imputada, r.corrida,
       (SELECT COALESCE(SUM(valor_miles),0)*1000 FROM pago_real p WHERE p.id=c.id) AS real_pagado_cuotas,
       c.multas_reales_miles*1000 AS real_multas,
       r.sim_pagado, r.sim_saldo_final, r.sim_pendientes, r.sim_pagado + r.sim_saldo_final + r.sim_pendientes AS sim_total_con_deuda,
       r.sim_mora, r.sim_cobranza, r.sim_interes, r.sim_servicios, r.sim_cuotas_vencidas_max, r.sim_marca_recuperacion, r.sim_periodo_marca,
       r.sim_estado_final, c.tasa_inicial_ea, c.linea_usura, r.real_ea_con_multas, r.cv_efectivo_max, r.marca_efectivo, r.periodo_marca_efectivo
FROM contrato c JOIN resultado r ON r.id=c.id;

CREATE OR REPLACE VIEW vw_periodos_contrato AS
SELECT c.contrato, c.sede, c.estado_real, c.cohorte, p.* FROM periodo p JOIN contrato c ON c.id=p.id;
