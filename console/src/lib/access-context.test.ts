import { afterEach, describe, expect, it, vi } from 'vitest'

import { authenticatedFetch } from './access-context'

afterEach(() => {
  delete window.adminHostBridge
  vi.unstubAllGlobals()
})

describe('authenticatedFetch', () => {
  it('adds host access and tenant context to same-origin requests', async () => {
    window.adminHostBridge = {
      getAccessContext: () => ({ accessToken: 'token', tenantId: 12, visitTenantId: '18' }),
    }
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response('{}'))
    vi.stubGlobal('fetch', fetcher)

    await authenticatedFetch('/console/api/reports', { headers: { Accept: 'application/json' } })

    const init = fetcher.mock.calls[0]?.[1]
    const headers = new Headers(init?.headers)
    expect(init?.credentials).toBe('same-origin')
    expect(headers.get('Authorization')).toBe('Bearer token')
    expect(headers.get('tenant-id')).toBe('12')
    expect(headers.get('visit-tenant-id')).toBe('18')
  })

  it('rejects cross-origin access before sending credentials', async () => {
    const fetcher = vi.fn<typeof fetch>()
    vi.stubGlobal('fetch', fetcher)

    await expect(authenticatedFetch('https://example.invalid/data')).rejects.toThrow('当前站点')
    expect(fetcher).not.toHaveBeenCalled()
  })
})
