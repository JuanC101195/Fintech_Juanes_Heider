-- Indices del PostgreSQL del backtesting. Idempotente (IF NOT EXISTS).
-- Cada comentario cita la consulta de nube/src/contrato.js que lo justifica.
-- Las PK ya cubren (corrida,id) / (corrida,id,periodo) / (mes,contrato) / (mes,motivo).

-- contrato(contrato): "FROM contrato WHERE contrato = ANY($1)" (carga de un contrato)
-- y los JOIN a activas_fin_mes por a.contrato = c.contrato (recaudo/activos de fin de mes).
CREATE INDEX IF NOT EXISTS ix_contrato_contrato ON contrato(contrato);

-- contrato(estado_real): "WHERE c.estado_real = 'VIGENTE'" (lista de vigentes y recaudo del mes).
CREATE INDEX IF NOT EXISTS ix_contrato_estado_real ON contrato(estado_real);

-- periodo(id): "FROM periodo WHERE id = ANY($1) ORDER BY id, corrida, periodo" (PIE_PERIODO)
-- y "JOIN contrato c ON c.id = p.id" en la flota de fin de mes. La PK empieza por corrida.
CREATE INDEX IF NOT EXISTS ix_periodo_id ON periodo(id);

-- resultado(id): "FROM resultado WHERE id = ANY($1)" y "JOIN resultado r ON r.id = c.id".
CREATE INDEX IF NOT EXISTS ix_resultado_id ON resultado(id);

-- pago_real(id): "FROM pago_real WHERE id = ANY($1) ORDER BY id, fecha", la subconsulta
-- "WHERE p.id = c.id" y "JOIN contrato c ON c.id = r.id" del recaudo real.
CREATE INDEX IF NOT EXISTS ix_pago_real_id ON pago_real(id);

-- proyeccion(fecha_vencimiento): recaudo proyectado por mes (mes calculado sobre p.fecha_vencimiento
-- en "FROM proyeccion p WHERE p.corrida = ANY($1) AND mes >= $2").
CREATE INDEX IF NOT EXISTS ix_proyeccion_fecha ON proyeccion(fecha_vencimiento);

-- activas_fin_mes(mes, contrato): "JOIN activas_fin_mes a ON a.contrato = c.contrato AND a.mes = ..."
-- y "SELECT mes, COUNT(*) ... GROUP BY mes". (Equivale a la PK; se crea explicito por pedido.)
CREATE INDEX IF NOT EXISTS ix_activas_fin_mes_mes_contrato ON activas_fin_mes(mes, contrato);
-- Busqueda por contrato solo (la creaba cargar_activas_fin_mes.py).
CREATE INDEX IF NOT EXISTS ix_activas_contrato ON activas_fin_mes(contrato);
