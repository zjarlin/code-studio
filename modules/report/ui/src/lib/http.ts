import { authenticatedFetch } from './access-context'

export interface ApiRequestOptions extends RequestInit {
  baseUrl?: string
}

export interface ApiResult<T> {
  code: number
  msg: string
  data?: T | null
}

export async function requestJson<T>(path: string, init?: ApiRequestOptions): Promise<T> {
  const { baseUrl, ...requestInit } = init ?? {}
  const isFormData = typeof FormData !== 'undefined' && requestInit.body instanceof FormData
  const response = await authenticatedFetch(resolveApiPath(path, baseUrl), {
    ...requestInit,
    headers: {
      Accept: 'application/json',
      ...(requestInit.body === undefined || isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...requestInit.headers,
    },
  })
  const payload: unknown = await response.json().catch(() => undefined)
  if (!response.ok) {
    throw new Error(payloadMessage(payload) || `HTTP ${response.status}`)
  }
  return payload as T
}

export async function requestBlob<T extends Blob = Blob>(path: string, init?: ApiRequestOptions): Promise<T> {
  const { baseUrl, ...requestInit } = init ?? {}
  const response = await authenticatedFetch(resolveApiPath(path, baseUrl), requestInit)
  if (!response.ok) {
    const payload: unknown = await response.json().catch(() => undefined)
    throw new Error(payloadMessage(payload) || `HTTP ${response.status}`)
  }
  return response.blob() as Promise<T>
}

export function requireApiData<T>(result: ApiResult<T>, emptyMessage = '响应缺少 data'): T {
  if (result.code !== 0) throw new Error(result.msg || `请求失败：${result.code}`)
  if (result.data === undefined || result.data === null) throw new Error(emptyMessage)
  return result.data
}

export function resolveApiPath(path: string, baseUrl?: string): string {
  if (!baseUrl) return path
  const base = baseUrl.trim()
  if (!base.startsWith('/') || base.startsWith('//')) {
    throw new Error('应用 API 基址必须是同源绝对路径')
  }
  const prefix = base.replace(/\/+$/, '')
  return `${prefix}/${path.replace(/^\/+/, '')}`
}

function payloadMessage(value: unknown): string | undefined {
  if (!isObject(value)) return undefined
  const message = value.msg ?? value.message ?? value.error
  return typeof message === 'string' ? message : undefined
}

function isObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}
