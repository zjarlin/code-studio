import { describe, expect, it } from 'vitest'

import { DEFAULT_STUDIO_API_BASE, STUDIO_API_PROXY_PATHS, resolveStudioApiBase } from '../vite.config'

describe('resolveStudioApiBase', () => {
  it('proxies the embedded Studio surface in development', () => {
    expect(STUDIO_API_PROXY_PATHS).toEqual(['/studio'])
  })

  it('uses the shared development host for standalone development', () => {
    expect(resolveStudioApiBase('', 'development')).toEqual({
      apiBase: DEFAULT_STUDIO_API_BASE,
      defaultApiBase: DEFAULT_STUDIO_API_BASE,
    })
  })

  it('keeps an explicitly configured backend', () => {
    expect(resolveStudioApiBase('http://127.0.0.1:49000', 'development')).toEqual({
      apiBase: 'http://127.0.0.1:49000',
      defaultApiBase: 'http://127.0.0.1:49000',
    })
  })

  it('does not embed the development backend in production builds', () => {
    expect(resolveStudioApiBase('', 'production')).toEqual({
      apiBase: DEFAULT_STUDIO_API_BASE,
      defaultApiBase: '',
    })
  })
})
