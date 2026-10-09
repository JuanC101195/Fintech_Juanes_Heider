import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { viteSingleFile } from 'vite-plugin-singlefile'
import { defineConfig } from 'vitest/config'

// El build sale en un solo index.html (JS, CSS y escenarios dentro) para enviarlo por correo:
// quien responde lo abre en el navegador sin instalar nada.
export default defineConfig({
  plugins: [react(), tailwindcss(), viteSingleFile()],
  test: {
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
