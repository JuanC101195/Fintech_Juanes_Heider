# Scripts del backtesting (versiones v2 a v12)

Respaldo de los scripts que calculan el backtesting del motor contra el historial real y lo cargan en PostgreSQL.
Vivían fuera de git, en `Documents\backtesting-motor-cartera\scripts`. El pipeline con soporte de sedes y `run_id`
(P6) está en `pipeline/`; estos son los anteriores y siguen siendo los que generaron las corridas actuales en Azure.

- `backtest_v12.py`: el vigente. Usa `FECHA_AFEC` como fecha efectiva solo para las instrucciones del motor; lo "real" sigue con `FECHA`.
- `backtest_v11.py` y anteriores: versiones previas, útiles para reproducir o comparar corridas.
- `cargar_pg.py`, `cargar_activos.py`, `cargar_activas_fin_mes.py`, `cargar_cartas_fin_mes.py`: carga a PostgreSQL.
- `cifras_pg.py`, `consultar_contrato.py`: consultas de verificación.

Las credenciales (MySQL de las sedes y PostgreSQL) se leen del entorno o de archivos locales fuera del repo; no se guardan aquí.
Las salidas (CSV, logs) no se versionan: contienen datos de clientes.
