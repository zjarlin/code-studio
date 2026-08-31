import path from 'node:path'

import tailwindcss from '@tailwindcss/vite'
import { tanstackStart } from '@tanstack/react-start/plugin/vite'
import viteReact from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const apiBase = process.env.CODE_STUDIO_API_BASE ?? 'http://127.0.0.1:8080'

export default defineConfig({
  base: '/console/',
  plugins: [
    tanstackStart({
      spa: {
        enabled: true,
        prerender: {
          outputPath: '/index.html',
        },
      },
    }),
    viteReact(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  build: {
    emptyOutDir: true,
    outDir: process.env.CODE_STUDIO_CONSOLE_OUT_DIR ?? 'dist',
  },
  server: {
    port: 5176,
    proxy: {
      '/console/api': apiBase,
      '/studio/api': apiBase,
      '/studio/config': apiBase,
    },
  },
})
