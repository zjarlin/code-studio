import path from 'node:path'

import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

export const DEFAULT_STUDIO_API_BASE = 'http://127.0.0.1:8080'
export const STUDIO_API_PROXY_PATHS = ['/studio'] as const

export function resolveStudioApiBase(configuredApiBase: string, mode: string) {
  const apiBase = configuredApiBase || DEFAULT_STUDIO_API_BASE
  const defaultApiBase = configuredApiBase || (mode === 'development' ? apiBase : '')

  return { apiBase, defaultApiBase }
}

export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, process.cwd(), '')
  const configuredApiBase = process.env.CODE_STUDIO_API_BASE ?? environment.CODE_STUDIO_API_BASE ?? ''
  const { apiBase, defaultApiBase } = resolveStudioApiBase(configuredApiBase, mode)

  return {
    base: '/studio/',
    plugins: [vue(), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    define: {
      __DEFAULT_API_BASE__: JSON.stringify(defaultApiBase),
    },
    build: {
      outDir: process.env.CODE_STUDIO_UI_OUT_DIR ?? 'dist',
      emptyOutDir: true,
    },
    server: {
      port: 5175,
      proxy: Object.fromEntries(STUDIO_API_PROXY_PATHS.map(proxyPath => [proxyPath, apiBase])),
    },
    test: {
      environment: 'jsdom',
    },
  }
})
