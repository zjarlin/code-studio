import path from 'node:path'

import tailwindcss from '@tailwindcss/vite'
import { tanstackStart } from '@tanstack/react-start/plugin/vite'
import viteReact from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const apiBase = process.env.CODE_STUDIO_API_BASE ?? 'http://127.0.0.1:8080'

export default defineConfig({
  base: '/report/',
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
      '@generated': path.resolve(import.meta.dirname, './dist/generated'),
    },
  },
  build: {
    emptyOutDir: false,
    outDir: process.env.REPORT_UI_OUT_DIR ?? 'dist',
  },
  server: {
    port: 5177,
    proxy: {
      '/console/api': apiBase,
    },
  },
})
