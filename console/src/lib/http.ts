import type { CommonResult } from '@/catalog/types'

export async function requestData<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...init?.headers,
    },
  })
  const payload: unknown = await response.json().catch(() => undefined)
  if (!response.ok) {
    throw new Error(payloadMessage(payload) || `HTTP ${response.status}`)
  }
  if (!isObject(payload) || typeof payload.code !== 'number') {
    return payload as T
  }
  const result = payload as unknown as CommonResult<T>
  if (result.code !== 0) throw new Error(result.msg || `请求失败：${result.code}`)
  return result.data as T
}

function payloadMessage(value: unknown): string | undefined {
  if (!isObject(value)) return undefined
  const message = value.msg ?? value.message ?? value.error
  return typeof message === 'string' ? message : undefined
}

function isObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}
