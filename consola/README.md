# Consola del motor de cartera

Front interno (React 19 + Vite + Tailwind 4, tokens de Prestaya, claro y oscuro) para analistas de cartera y producto. No es para clientes. Habla con el API del motor (`/api/v1`, contrato v1).

Pantallas: **Simulador** (cuota, plan y gráficos), **Casos** (los 13 casos periodo a periodo, línea de tiempo y asientos, perfil EXCEL o NEGOCIO), **Laboratorio** (partir de un caso o de un crédito nuevo, armar los eventos por periodo con tarjetas o atajos, calcular y ver errores del API) y **Reglas** (cada regla en lenguaje de negocio en los dos perfiles, su pregunta del cuestionario y lo que sigue por confirmar).

```bash
cd consola
npm install
npm run dev        # http://localhost:5173 (con el backend en :8080: ../README.md)
npm test           # vitest: dinero, cliente HTTP, instrucciones, pantallas, Selector, contrato y que no haya <select>
npm run build      # genera dist/
node pruebas/humo.mjs   # Chromium a 360 px, 1366 px (claro y oscuro) y 1366 px con escala 1,5; modo demostración
node pruebas/e2e.mjs    # Chromium contra el API REAL (E2E_API_URL, por defecto :8080; requiere build con VITE_API_URL)
```

## Usabilidad

La usa Diego (gerente, no técnico). Reglas que las pruebas vigilan:

- **Nada de `<select>` nativo** (en Windows abre una lista con letra diminuta). Hasta 4 opciones: botones segmentados o tarjetas (`Segmentado`). 5 o más: `componentes/Selector.tsx` (listbox ARIA, teclado, buscador con más de 7 opciones, hoja inferior en el celular). `src/sinSelect.test.ts` falla si vuelve a aparecer.
- Texto base de 16 px (nada por debajo de 14 px), áreas táctiles de 44 px o más; `humo.mjs` y `e2e.mjs` lo revisan.
- Dinero con miles en vivo (`EntradaDinero`), tasas como porcentaje (`EntradaPorcentaje`) y enteros con − y + (`EntradaPasos`); el valor que viaja al API sigue siendo el decimal exacto.
- Sin nombres internos a la vista (MISMA_CUOTA, NEGOCIO…): se traducen a lenguaje de negocio.
- Mientras el API (plan gratuito, se duerme) no haya respondido la primera vez, los indicadores de carga avisan que puede tardar hasta 30 s.

## API y modo demostración

`VITE_API_URL` (ver `.env.example`) apunta al API; sin definir es `http://localhost:8080`.

- **Vacía** (`VITE_API_URL=`) o **API que no responde**: modo demostración con los fixtures de `src/demo/fixtures/` y un aviso fijo "Datos de demostración". Las cifras de la demo no cambian con los parámetros (solo se validan el plazo máximo y que un abono no supere el saldo).
- Un error de negocio del API (HTTP 400/404 con `codigo`) **no** activa la demo: se muestra legible.

Los fixtures son respuestas **reales** del API (catálogo, reglas, casos, simulaciones y los 13 casos en EXCEL y NEGOCIO). Se regeneran con el backend arriba: `npm run fixtures` (`src/demo/descargar.mjs [urlApi]`). Por defecto quita de cada movimiento los campos que la consola no usa para que cada archivo pese ~320 KB como máximo; `--completo` guarda la respuesta íntegra (unos 6 MB). Se cargan de forma perezosa, uno por pantalla.

## Dinero

El API manda el dinero **sin redondear** (hasta 34 decimales, DECIMAL128). Viaja como string decimal y nunca pasa por `parseFloat`: `src/dinero.ts` suma con BigInt a 34 decimales (sin perder nada) y redondea solo al mostrar: `cop` a pesos (355.666,9755… → $ 355.667, P-05) y `copCentavos` a centavos en las tablas periodo a periodo y los asientos ($ 3.169.909,34).
