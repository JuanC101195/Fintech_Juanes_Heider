import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// base relativa: el build funciona en cualquier carpeta (GitHub Pages, servidor estático, preview).
export default defineConfig({
  base: './',
  plugins: [react(), tailwindcss()],
  test: {
    environment: 'jsdom',
    // Con --coverage (instrumentación v8) las pruebas de pantallas tardan más de 5 s en equipos lentos y en CI.
    testTimeout: 20000,
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: { provider: 'v8', reporter: ['text-summary', 'lcov'], include: ['src/**/*.{ts,tsx}'] },
  },
})
