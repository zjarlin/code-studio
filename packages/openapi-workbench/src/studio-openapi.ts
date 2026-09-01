import type { ApiDocument } from './types'

export interface StudioOpenApiConfig {
  apiBaseUrl: string
  displayName: string
  openApiPath: string
}

interface StudioOpenApiLoadOptions {
  attempts?: number
  browserOrigin?: string
  defaultApiBase?: string
  fetcher?: typeof fetch
  retryDelayMs?: number
  wait?: (delayMs: number) => Promise<void>
  onRetry?: (message: string) => void
}

interface OpenApiLoadAttempt {
  document?: ApiDocument
  error?: Error
  retryable: boolean
}

export function resolveStudioApiBaseUrl(
  value: string | null | undefined,
  browserOrigin: string,
  defaultApiBase: string = '',
): string {
  const configuredValue = value?.trim() ?? ''
  if (!configuredValue) {
    return ''
  }
  const normalizedValue = configuredValue.replace(/\/+$/, '') || '/'

  const browserUrl = new URL(browserOrigin)
  const backendUrl = browserAccessibleBackendUrl(defaultApiBase, browserUrl)
  if (normalizedValue.startsWith('/')) {
    return normalizeUrl(new URL(normalizedValue, backendUrl))
  }

  const configuredUrl = new URL(normalizedValue)
  if (!isLocalHost(configuredUrl.hostname)) {
    return normalizeUrl(configuredUrl)
  }
  if (isLocalHost(browserUrl.hostname) && isLocalHost(backendUrl.hostname)) {
    return normalizeUrl(configuredUrl)
  }

  configuredUrl.protocol = backendUrl.protocol
  configuredUrl.hostname = backendUrl.hostname
  configuredUrl.port = backendUrl.port
  return normalizeUrl(configuredUrl)
}

export async function loadStudioOpenApi(
  config: StudioOpenApiConfig,
  options: StudioOpenApiLoadOptions = {},
): Promise<ApiDocument> {
  const browserOrigin = options.browserOrigin ?? window.location.origin
  const baseUrl = resolveStudioApiBaseUrl(
    config.apiBaseUrl,
    browserOrigin,
    options.defaultApiBase,
  )
  if (!baseUrl) {
    throw new Error(`${config.displayName} 未配置 API 访问地址`)
  }

  const openApiUrl = resolveOpenApiUrl(baseUrl, config.openApiPath)
  const attempts = Math.max(1, options.attempts ?? DEFAULT_ATTEMPTS)
  const fetcher = options.fetcher ?? fetch
  const wait = options.wait ?? waitFor
  let lastError = new Error(`读取 ${config.displayName} OpenAPI 失败`)

  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    const result = await loadAttempt(openApiUrl, config.displayName, fetcher)
    if (result.document) {
      return result.document
    }

    lastError = result.error ?? lastError
    if (!result.retryable || attempt === attempts) {
      throw lastError
    }
    options.onRetry?.(`${lastError.message}，正在重试`)
    await wait(options.retryDelayMs ?? DEFAULT_RETRY_DELAY_MS)
  }

  throw lastError
}

function resolveOpenApiUrl(baseUrl: string, path: string): string {
  const value = path.trim() || '/v3/api-docs'
  return new URL(value.replace(/^\/+/, ''), `${baseUrl}/`).toString()
}

async function loadAttempt(
  openApiUrl: string,
  displayName: string,
  fetcher: typeof fetch,
): Promise<OpenApiLoadAttempt> {
  try {
    const response = await fetcher(openApiUrl)
    if (!response.ok) {
      return {
        error: new Error(`读取 ${displayName} OpenAPI 失败：HTTP ${response.status}`),
        retryable: !NON_RETRYABLE_STATUSES.has(response.status),
      }
    }

    const payload: unknown = await response.json()
    if (isApiDocument(payload)) {
      return { document: payload, retryable: false }
    }
    return {
      error: new Error(`读取 ${displayName} OpenAPI 失败：${payloadMessage(payload)}`),
      retryable: false,
    }
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : '网络请求失败'
    return {
      error: new Error(`读取 ${displayName} OpenAPI 失败：${message}`),
      retryable: true,
    }
  }
}

function browserAccessibleBackendUrl(defaultApiBase: string, browserUrl: URL): URL {
  const backendUrl = new URL(defaultApiBase.trim() || browserUrl.origin, browserUrl)
  if (isLocalHost(backendUrl.hostname) && !isLocalHost(browserUrl.hostname)) {
    backendUrl.hostname = browserUrl.hostname
  }
  return backendUrl
}

function isApiDocument(value: unknown): value is ApiDocument {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return false
  }
  const document = value as Record<string, unknown>
  return typeof document.openapi === 'string'
    && Boolean(document.paths)
    && typeof document.paths === 'object'
    && !Array.isArray(document.paths)
}

function payloadMessage(value: unknown): string {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const message = (value as Record<string, unknown>).msg
    if (typeof message === 'string' && message.trim()) {
      return message.trim()
    }
  }
  return '返回内容不是 OpenAPI 文档'
}

function isLocalHost(hostname: string): boolean {
  const normalized = hostname.toLowerCase().replace(/^\[|\]$/g, '')
  return normalized === 'localhost'
    || normalized.startsWith('127.')
    || normalized === '0.0.0.0'
    || normalized === '::'
    || normalized === '::1'
}

function normalizeUrl(url: URL): string {
  return url.toString().replace(/\/+$/, '')
}

function waitFor(delayMs: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, delayMs))
}

const DEFAULT_ATTEMPTS = 46
const DEFAULT_RETRY_DELAY_MS = 2_000
const NON_RETRYABLE_STATUSES = new Set([401, 403, 404])
