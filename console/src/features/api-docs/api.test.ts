import { afterEach, describe, expect, it, vi } from 'vitest'

import { fetchApiCatalog } from './api'

afterEach(() => {
  delete window.adminHostBridge
  vi.unstubAllGlobals()
})

describe('OpenAPI catalog access', () => {
  it('uses the host access context for the OpenAPI document request', async () => {
    window.adminHostBridge = {
      getAccessContext: () => ({ accessToken: 'token', tenantId: 'tenant', visitTenantId: 'visit' }),
    }
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({
        code: 0,
        msg: '',
        data: { apiBaseUrl: '/admin-api', displayName: 'Application', openApiPath: '/v3/api-docs' },
      }))
      .mockResolvedValueOnce(Response.json({ openapi: '3.1.0', paths: {} }))
    vi.stubGlobal('fetch', fetcher)

    await fetchApiCatalog()

    const init = fetcher.mock.calls[1]?.[1]
    const headers = new Headers(init?.headers)
    expect(headers.get('Authorization')).toBe('Bearer token')
    expect(headers.get('tenant-id')).toBe('tenant')
    expect(headers.get('visit-tenant-id')).toBe('visit')
    expect(new URL(String(fetcher.mock.calls[1]?.[0])).pathname).toBe('/admin-api/v3/api-docs')
  })
})
