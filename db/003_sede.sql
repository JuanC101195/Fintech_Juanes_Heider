-- Migracion 003: multi-sede y versionado de corridas. IDEMPOTENTE (se puede aplicar varias veces).
-- Orden de aplicacion: 001_esquema.sql, 002_indices.sql, 003_sede.sql.
--
-- Llave de un contrato: (sede, contrato). El NUMERO de contrato se repite entre sedes y son contratos
-- distintos; la sede se conoce por el servidor MySQL de origen. La placa NO identifica un contrato
-- (solo control de calidad: no debe aparecer en dos sedes).
--
-- Cada carga lleva un run_id (version de la corrida del backtesting). Los datos que existian antes de esta
-- migracion (solo Cartagena) quedan con sede='CARTAGENA' y run_id='legado'.
-- OJO: la columna `corrida` de resultado/periodo/proyeccion es la VARIANTE de simulacion
-- (sin_justificaciones, fija_con_justificaciones, ...), NO la ejecucion; la ejecucion es `run_id`.

-- Las vistas dependientes se recrean al final.
DROP VIEW IF EXISTS vw_comparacion;
DROP VIEW IF EXISTS vw_periodos_contrato;
DROP VIEW IF EXISTS vw_corrida_vigente;

CREATE TABLE IF NOT EXISTS corrida_meta (
  run_id text NOT NULL,
  sede text NOT NULL,
  fecha_corte date,
  version_motor text,
  git_sha text,
  inicio timestamptz,
  fin timestamptz,
  estado text NOT NULL DEFAULT 'terminada',   -- 'terminada' | 'fallida' | 'en_curso'
  PRIMARY KEY (sede, run_id)
);

DO $migracion$
DECLARE
  t text;
  pendiente boolean;
BEGIN
  -- Marcador de migracion: contrato aun sin run_id.
  pendiente := NOT EXISTS (SELECT 1 FROM information_schema.columns
                           WHERE table_schema = current_schema() AND table_name = 'contrato' AND column_name = 'run_id');

  -- contrato.sede existia como text NULL: se normaliza antes de exigirla.
  UPDATE contrato SET sede = 'CARTAGENA' WHERE sede IS NULL;
  ALTER TABLE contrato ALTER COLUMN sede SET DEFAULT 'CARTAGENA';
  ALTER TABLE contrato ALTER COLUMN sede SET NOT NULL;

  FOREACH t IN ARRAY ARRAY['contrato','pago_real','resultado','periodo','proyeccion','activas_fin_mes','carta_fin_mes','corte_activos'] LOOP
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS sede text NOT NULL DEFAULT %L', t, 'CARTAGENA');
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS run_id text NOT NULL DEFAULT %L', t, 'legado');
  END LOOP;

  ALTER TABLE contrato ADD COLUMN IF NOT EXISTS placa text;   -- nullable: solo control de calidad

  IF pendiente THEN
    -- FKs viejas (id) -> se reemplazan por FKs compuestas (sede, run_id, id).
    ALTER TABLE pago_real DROP CONSTRAINT IF EXISTS pago_real_id_fkey;
    ALTER TABLE resultado DROP CONSTRAINT IF EXISTS resultado_id_fkey;
    ALTER TABLE periodo   DROP CONSTRAINT IF EXISTS periodo_id_fkey;

    ALTER TABLE contrato        DROP CONSTRAINT IF EXISTS contrato_pkey;
    ALTER TABLE resultado       DROP CONSTRAINT IF EXISTS resultado_pkey;
    ALTER TABLE periodo         DROP CONSTRAINT IF EXISTS periodo_pkey;
    ALTER TABLE proyeccion      DROP CONSTRAINT IF EXISTS proyeccion_pkey;
    ALTER TABLE activas_fin_mes DROP CONSTRAINT IF EXISTS activas_fin_mes_pkey;
    ALTER TABLE carta_fin_mes   DROP CONSTRAINT IF EXISTS carta_fin_mes_pkey;

    ALTER TABLE contrato        ADD CONSTRAINT contrato_pkey PRIMARY KEY (sede, run_id, id);
    ALTER TABLE resultado       ADD CONSTRAINT resultado_pkey PRIMARY KEY (sede, run_id, corrida, id);
    ALTER TABLE periodo         ADD CONSTRAINT periodo_pkey PRIMARY KEY (sede, run_id, corrida, id, periodo);
    ALTER TABLE proyeccion      ADD CONSTRAINT proyeccion_pkey PRIMARY KEY (sede, run_id, corrida, id, periodo);
    ALTER TABLE activas_fin_mes ADD CONSTRAINT activas_fin_mes_pkey PRIMARY KEY (sede, run_id, mes, contrato);
    ALTER TABLE carta_fin_mes   ADD CONSTRAINT carta_fin_mes_pkey PRIMARY KEY (sede, run_id, mes, motivo);

    ALTER TABLE pago_real ADD CONSTRAINT pago_real_contrato_fkey FOREIGN KEY (sede, run_id, id) REFERENCES contrato (sede, run_id, id);
    ALTER TABLE resultado ADD CONSTRAINT resultado_contrato_fkey FOREIGN KEY (sede, run_id, id) REFERENCES contrato (sede, run_id, id);
    ALTER TABLE periodo   ADD CONSTRAINT periodo_contrato_fkey   FOREIGN KEY (sede, run_id, id) REFERENCES contrato (sede, run_id, id);

    -- Los datos previos quedan registrados como corrida 'legado' de cada sede que tenga contratos.
    -- inicio/fin NULL: vw_corrida_vigente ordena NULLS LAST, asi que cualquier corrida nueva terminada reemplaza al legado como vigente.
    INSERT INTO corrida_meta (run_id, sede, fecha_corte, version_motor, git_sha, inicio, fin, estado)
    SELECT 'legado', s.sede, (SELECT MAX(corte) FROM corte_activos), 'legado', NULL, NULL, NULL, 'terminada'
    FROM (SELECT DISTINCT sede FROM contrato WHERE run_id = 'legado') s
    ON CONFLICT (sede, run_id) DO NOTHING;
  END IF;
END
$migracion$;

-- Indices por sede / corrida (IF NOT EXISTS). Los de 002 se conservan.
CREATE INDEX IF NOT EXISTS ix_contrato_sede_contrato ON contrato(sede, run_id, contrato);
CREATE INDEX IF NOT EXISTS ix_contrato_sede_estado ON contrato(sede, run_id, estado_real);
CREATE INDEX IF NOT EXISTS ix_contrato_placa ON contrato(placa) WHERE placa IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_periodo_sede_id ON periodo(sede, run_id, id);
CREATE INDEX IF NOT EXISTS ix_resultado_sede_id ON resultado(sede, run_id, id);
CREATE INDEX IF NOT EXISTS ix_pago_real_sede_id ON pago_real(sede, run_id, id);
CREATE INDEX IF NOT EXISTS ix_proyeccion_sede_id ON proyeccion(sede, run_id, id);
CREATE INDEX IF NOT EXISTS ix_activas_sede_contrato ON activas_fin_mes(sede, run_id, contrato);
CREATE INDEX IF NOT EXISTS ix_corrida_meta_estado ON corrida_meta(sede, estado, fin DESC);

-- Ultima corrida terminada de cada sede (la que debe leer la API / la pagina).
CREATE VIEW vw_corrida_vigente AS
SELECT DISTINCT ON (sede) sede, run_id, fecha_corte, version_motor, git_sha, inicio, fin, estado
FROM corrida_meta
WHERE estado = 'terminada'
ORDER BY sede, fin DESC NULLS LAST, inicio DESC NULLS LAST, run_id DESC;

CREATE VIEW vw_comparacion AS
SELECT c.id, c.sede, c.run_id, c.contrato, c.placa, c.cohorte, c.estado_real, c.fecha_inicio, c.fecha_fin, c.cuotas, c.inversion, c.inversion_imputada, r.corrida,
       (SELECT COALESCE(SUM(valor_miles),0)*1000 FROM pago_real p WHERE p.id=c.id AND p.sede=c.sede AND p.run_id=c.run_id) AS real_pagado_cuotas,
       c.multas_reales_miles*1000 AS real_multas,
       r.sim_pagado, r.sim_saldo_final, r.sim_pendientes, r.sim_pagado + r.sim_saldo_final + r.sim_pendientes AS sim_total_con_deuda,
       r.sim_mora, r.sim_cobranza, r.sim_interes, r.sim_servicios, r.sim_cuotas_vencidas_max, r.sim_marca_recuperacion, r.sim_periodo_marca,
       r.sim_estado_final, c.tasa_inicial_ea, c.linea_usura, r.real_ea_con_multas, r.cv_efectivo_max, r.marca_efectivo, r.periodo_marca_efectivo
FROM contrato c JOIN resultado r ON r.id=c.id AND r.sede=c.sede AND r.run_id=c.run_id;

-- p.* ya trae sede y run_id.
CREATE VIEW vw_periodos_contrato AS
SELECT c.contrato, c.placa, c.estado_real, c.cohorte, p.* FROM periodo p JOIN contrato c ON c.id=p.id AND c.sede=p.sede AND c.run_id=p.run_id;
