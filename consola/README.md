# Consola del motor de cartera

Front interno (React 19 + Vite + Tailwind 4, tokens de Prestaya, claro y oscuro) para analistas de cartera y producto. No es para clientes. Habla con el API del motor (`/api/v1`, contrato v1).

Pantallas: **Simulador** (cuota, plan y gráficos), **Casos** (los 13 casos periodo a periodo, línea de tiempo y asientos, perfil EXCEL o NEGOCIO), **Laboratorio** (armar instrucciones por periodo, ejecutar y ver errores del API) y **Reglas** (claves por perfil, fuente y lo que sigue por confirmar).

```bash
cd consola
npm install
npm run dev        # http://localhost:5173
npm test           # vitest: dinero, cliente HTTP, instrucciones, pantallas con fixtures
npm run build      # genera dist/
node pruebas/humo.mjs   # Chromium en celular y escritorio (requiere npm run build antes)
```

## API y modo demostración

`VITE_API_URL` (ver `.env.example`) apunta al API; sin definir es `http://localhost:8080`.

- **Vacía** (`VITE_API_URL=`) o **API que no responde**: modo demostración con los fixtures de `src/demo/fixtures/` y un aviso fijo "Datos de demostración". Las cifras de la demo no cambian con los parámetros (solo se validan el plazo máximo y que un abono no supere el saldo).
- Un error de negocio del API (HTTP 400/404 con `codigo`) **no** activa la demo: se muestra legible.

Para usar los JSON reales de `docs/api-ejemplos/`, cópialos en `src/demo/fixtures/` con estos nombres: `catalogo.json`, `reglas.json`, `casos.json`, `simulacion-{semanal|quincenal|mensual}.json` y `caso-{N}-{EXCEL|NEGOCIO}.json` (si falta el de EXCEL se usa el de NEGOCIO). `npm run fixtures` regenera los actuales con un mini-motor de ilustración (`src/demo/generar.mjs`); no reemplaza al motor Java.

## Dinero

Viaja como string decimal y nunca pasa por `parseFloat`: `src/dinero.ts` formatea con `Intl.NumberFormat('es-CO')` desde el string y suma con BigInt en micro-pesos.
