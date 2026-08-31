import { requestData } from '@/lib/http'

interface StudioConfig {
  apiBaseUrl: string
  displayName: string
  openApiPath: string
}

interface OpenApiOperation {
  description?: string
  operationId?: string
  summary?: string
  tags?: string[]
  'x-permission'?: string
}

interface OpenApiDocument {
  info?: { title?: string; version?: string }
  openapi: string
  paths: Record<string, Record<string, OpenApiOperation>>
}

export interface ApiOperationRow {
  id: string
  method: string
  path: string
  summary: string
  description: string
  group: string
  permission: string
}

export interface ApiCatalog {
  name: string
  openapi: string
  operations: ApiOperationRow[]
  version: string
}

export async function fetchApiCatalog(): Promise<ApiCatalog> {
  const config = await requestData<StudioConfig>('/studio/config')
  const document = await fetchOpenApi(config)
  const methods = new Set(['delete', 'get', 'head', 'options', 'patch', 'post', 'put'])
  const operations = Object.entries(document.paths).flatMap(([path, pathItem]) =>
    Object.entries(pathItem)
      .filter(([method]) => methods.has(method.toLowerCase()))
      .map(([method, operation]) => ({
        id: operation.operationId ?? `${method}:${path}`,
        method: method.toUpperCase(),
        path,
        summary: operation.summary ?? operation.operationId ?? `${method.toUpperCase()} ${path}`,
        description: operation.description ?? '',
        group: operation.tags?.[0] ?? '未分组',
        permission: operation['x-permission'] ?? '',
      })),
  ).sort((left, right) => left.path.localeCompare(right.path) || left.method.localeCompare(right.method))

  return {
    name: config.displayName || document.info?.title || 'Application',
    openapi: document.openapi,
    operations,
    version: document.info?.version ?? '',
  }
}

async function fetchOpenApi(config: StudioConfig): Promise<OpenApiDocument> {
  const browserOrigin = window.location.origin
  const base = config.apiBaseUrl.trim()
    ? new URL(config.apiBaseUrl.replace(/\/+$/, '') + '/', browserOrigin)
    : new URL('/', browserOrigin)
  const url = new URL((config.openApiPath.trim() || '/v3/api-docs').replace(/^\/+/, ''), base)
  const response = await fetch(url)
  if (!response.ok) throw new Error(`读取 OpenAPI 失败：HTTP ${response.status}`)
  const document = await response.json() as OpenApiDocument
  if (!document.openapi || !document.paths) throw new Error('OpenAPI 文档格式无效')
  return document
}
