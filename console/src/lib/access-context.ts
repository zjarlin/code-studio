export interface AdminAccessContext {
  accessToken?: string | null
  tenantId?: string | number | null
  visitTenantId?: string | number | null
}

interface AdminHostBridge {
  getAccessContext: () => AdminAccessContext | Promise<AdminAccessContext>
}

declare global {
  interface Window {
    adminHostBridge?: AdminHostBridge
  }
}

export async function authenticatedFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  assertSameOrigin(input)
  const context = await readAccessContext()
  const headers = new Headers(init.headers)
  const accessToken = normalize(context.accessToken)
  const tenantId = normalize(context.tenantId)
  const visitTenantId = normalize(context.visitTenantId)

  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  if (tenantId) headers.set('tenant-id', tenantId)
  if (visitTenantId) headers.set('visit-tenant-id', visitTenantId)

  return fetch(input, {
    ...init,
    credentials: 'same-origin',
    headers,
  })
}

export async function readAccessContextFingerprint(): Promise<string> {
  const context = await readAccessContext()
  return JSON.stringify([
    normalize(context.accessToken) ?? null,
    normalize(context.tenantId) ?? null,
    normalize(context.visitTenantId) ?? null,
  ])
}

async function readAccessContext(): Promise<AdminAccessContext> {
  if (typeof window === 'undefined') return {}
  return await window.adminHostBridge?.getAccessContext() ?? {}
}

function assertSameOrigin(input: RequestInfo | URL): void {
  if (typeof window === 'undefined') return
  const rawUrl = input instanceof Request ? input.url : String(input)
  const url = new URL(rawUrl, window.location.origin)
  if (url.origin !== window.location.origin) {
    throw new Error('管理端请求只能访问当前站点')
  }
}

function normalize(value: string | number | null | undefined): string | undefined {
  if (value === null || value === undefined) return undefined
  const normalized = String(value).trim()
  return normalized || undefined
}
