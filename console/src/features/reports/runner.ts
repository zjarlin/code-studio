import { authenticatedFetch } from '@/lib/access-context'
import { requestData } from '@/lib/http'

import {
  MAX_REPORT_ROW_COUNT,
  type JsonLiteral,
  type ReportBlockSpec,
  type ReportDatasetField,
  type ReportDatasetSpec,
  type ReportDocument,
  type ReportRunResult,
  type ReportSourceCatalog,
} from './models'

interface StudioConfig {
  apiBaseUrl: string
  openApiPath: string
}

interface OpenApiParameter {
  name?: string
  in?: string
  required?: boolean
}

interface OpenApiOperation {
  operationId?: string
  summary?: string
  description?: string
  parameters?: OpenApiParameter[]
  requestBody?: unknown
  responses?: Record<string, { content?: Record<string, { schema?: OpenApiSchema }> }>
}

interface OpenApiPathItem {
  parameters?: OpenApiParameter[]
  get?: OpenApiOperation
}

interface OpenApiSchema {
  $ref?: string
  description?: string
  title?: string
  type?: string
  properties?: Record<string, OpenApiSchema>
  items?: OpenApiSchema
  allOf?: OpenApiSchema[]
}

interface OpenApiDocument {
  openapi: string
  paths: Record<string, OpenApiPathItem>
  components?: { schemas?: Record<string, OpenApiSchema> }
}

interface ResolvedGet {
  path: string
  operation: OpenApiOperation
  parameters: OpenApiParameter[]
}

interface OpenApiContext {
  document: OpenApiDocument
  baseUrl: URL
}

export async function fetchReportSourceCatalog(): Promise<ReportSourceCatalog> {
  const { document } = await fetchOpenApiDocument()
  const schemas = document.components?.schemas ?? {}
  const models = Object.entries(schemas)
    .filter(([name]) => name.endsWith('_entity'))
    .map(([name, schema]) => {
      const modelCode = name.slice(0, -'_entity'.length)
      const resolved = resolveModelGet(document, modelCode)
      return {
        key: modelCode,
        modelCode,
        name: schema.title ?? schema.description ?? modelCode,
        fields: fieldsFromSchema(document, schema),
        parameters: sourceParameters(resolved?.parameters ?? []),
      }
    })
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
  const operationIds = Object.entries(document.paths)
    .map(([path, pathItem]) => resolveOpenApiGet(document, path, pathItem)?.operation.operationId)
    .filter((value): value is string => Boolean(value))
  const duplicateIds = new Set(operationIds.filter((value, index) => operationIds.indexOf(value) !== index))
  const operations = Object.entries(document.paths).flatMap(([path, pathItem]) => {
    const resolved = resolveOpenApiGet(document, path, pathItem)
    if (!resolved || duplicateIds.has(resolved.operation.operationId!)) return []
    const { operation, parameters } = resolved
    const operationId = operation.operationId!
    return [{
      key: operationId,
      operationId,
      name: operation.summary ?? operationId,
      path,
      fields: fieldsFromSchema(document, responseSchema(operation)),
      parameters: sourceParameters(parameters),
    }]
  }).sort((left, right) => left.path.localeCompare(right.path))
  return { models, operations }
}

export async function runReportDatasets(
  document: ReportDocument,
  parameterValues: Record<string, string>,
): Promise<ReportRunResult> {
  const missingParameters = document.parameters
    .filter((parameter) => parameter.required && !parameterValues[parameter.key]?.trim())
    .map(({ label }) => label)
  if (missingParameters.length) throw new Error(`缺少必填参数：${missingParameters.join('、')}`)
  if (!document.datasets.length) return { values: {}, errors: {} }
  const { document: openApi, baseUrl } = await fetchOpenApiDocument()
  const entries = await Promise.all(document.datasets.map(async (dataset) => {
    try {
      const resolved = resolveDataset(openApi, dataset)
      const value = await runGet(resolved, dataset, parameterValues, baseUrl)
      validateDatasetShape(document, dataset, value)
      return [dataset.key, value, undefined] as const
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : '数据集运行失败'
      return [dataset.key, undefined, message] as const
    }
  }))
  return {
    values: Object.fromEntries(entries.filter(([, , error]) => !error).map(([key, value]) => [key, value])),
    errors: Object.fromEntries(entries.filter(([, , error]) => error).map(([key, , error]) => [key, error!])),
  }
}

export function rowsFromResult(value: unknown): Record<string, unknown>[] {
  if (Array.isArray(value)) return value.filter(isObject).slice(0, MAX_REPORT_ROW_COUNT)
  if (!isObject(value)) return value === undefined ? [] : [{ value }]
  const rows = value.list ?? value.rows ?? value.items
  if (Array.isArray(rows)) return rows.filter(isObject).slice(0, MAX_REPORT_ROW_COUNT)
  return [value]
}

export function resolveJsonPointer(value: unknown, pointer: string): unknown {
  if (!isJsonPointer(pointer)) return undefined
  if (!pointer) return value
  return pointer.slice(1).split('/').reduce<unknown>((current, part) => {
    if (!isObject(current) && !Array.isArray(current)) return undefined
    const key = part.replaceAll('~1', '/').replaceAll('~0', '~')
    return current[key as keyof typeof current]
  }, value)
}

export function isJsonPointer(value: string): boolean {
  return /^(?:\/(?:[^~/]|~[01])*)*$/.test(value)
}

async function fetchOpenApiDocument(): Promise<OpenApiContext> {
  const config = await requestData<StudioConfig>('/studio/config')
  const base = config.apiBaseUrl.trim()
    ? new URL(`${config.apiBaseUrl.replace(/\/+$/, '')}/`, window.location.origin)
    : new URL('/', window.location.origin)
  if (base.origin !== window.location.origin) throw new Error('报表数据源必须与管理后台同源')
  const path = config.openApiPath.trim() || '/v3/api-docs'
  const response = await authenticatedFetch(new URL(path.replace(/^\/+/, ''), base), { headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error(`读取 OpenAPI 失败：HTTP ${response.status}`)
  const document = await parseJson(response, 'OpenAPI 文档') as OpenApiDocument
  if (!document.openapi || !isObject(document.paths)) throw new Error('OpenAPI 文档格式无效')
  return { document, baseUrl: base }
}

function resolveDataset(document: OpenApiDocument, dataset: ReportDatasetSpec): ResolvedGet {
  if (dataset.source === 'OPENAPI') {
    const matches = Object.entries(document.paths).flatMap(([path, pathItem]) => {
      const resolved = resolveOpenApiGet(document, path, pathItem)
      return resolved?.operation.operationId === dataset.operationId ? [resolved] : []
    })
    if (matches.length !== 1) throw new Error(`未找到唯一 GET 操作：${dataset.operationId}`)
    return matches[0]!
  }
  const resolved = resolveModelGet(document, dataset.modelCode ?? '')
  if (!resolved) throw new Error(`模型 ${dataset.modelCode} 没有可用 GET 查询`)
  return resolved
}

function resolveModelGet(document: OpenApiDocument, modelCode: string): ResolvedGet | undefined {
  const alias = document.components?.schemas?.[`${modelCode}_entity`]
  const schemaReference = alias?.$ref
  if (!schemaReference) return undefined
  return Object.entries(document.paths).flatMap(([path, pathItem]) => {
    const resolved = resolveSafeGet(document, path, pathItem)
    if (!resolved || !JSON.stringify(resolved.operation.responses ?? {}).includes(schemaReference)) return []
    return [resolved]
  }).sort((left, right) => scoreModelPath(left.path) - scoreModelPath(right.path))[0]
}

function resolveOpenApiGet(document: OpenApiDocument, path: string, pathItem: OpenApiPathItem): ResolvedGet | undefined {
  const resolved = resolveSafeGet(document, path, pathItem)
  const operationId = resolved?.operation.operationId
  return operationId && OPERATION_ID.test(operationId) ? resolved : undefined
}

function resolveSafeGet(document: OpenApiDocument, path: string, pathItem: OpenApiPathItem): ResolvedGet | undefined {
  const operation = pathItem.get
  if (!operation || operation.requestBody || !hasRecognizableJsonResponse(document, operation)) return undefined
  const parameters = [...(pathItem.parameters ?? []), ...(operation.parameters ?? [])]
  return parameters.every(isPathOrQueryParameter) ? { path, operation, parameters } : undefined
}

async function runGet(
  resolved: ResolvedGet,
  dataset: ReportDatasetSpec,
  parameterValues: Record<string, string>,
  baseUrl: URL,
): Promise<unknown> {
  let resolvedPath = resolved.path
  const query = new URLSearchParams()
  const allowedParameters = new Map(resolved.parameters.flatMap((parameter) =>
    parameter.name && isPathOrQueryParameter(parameter) ? [[parameter.name, parameter] as const] : [],
  ))
  Object.entries(dataset.parameterBindings).forEach(([apiParameter, binding]) => {
    if (!allowedParameters.has(apiParameter)) throw new Error(`数据集绑定了未知 path/query 参数：${apiParameter}`)
    const raw = binding.kind === 'PARAMETER' ? parameterValues[binding.parameterKey] : literalValue(binding.literal)
    if (raw === undefined || raw === '') return
    const marker = `{${apiParameter}}`
    if (resolvedPath.includes(marker)) resolvedPath = resolvedPath.replaceAll(marker, encodeURIComponent(raw))
    else query.set(apiParameter, raw)
  })
  resolved.parameters.filter(({ required }) => required).forEach((parameter) => {
    if (!parameter.name) return
    const marker = `{${parameter.name}}`
    if (resolvedPath.includes(marker) || (!query.has(parameter.name) && parameter.in === 'query')) {
      throw new Error(`GET 路径缺少参数：${parameter.name}`)
    }
  })
  if (resolvedPath.includes('{')) throw new Error(`GET 路径缺少参数：${resolvedPath}`)
  if (allowedParameters.has('pageNo') && !query.has('pageNo')) query.set('pageNo', '1')
  if (allowedParameters.has('pageSize')) {
    const requested = query.has('pageSize') ? Number(query.get('pageSize')) : MAX_REPORT_ROW_COUNT
    query.set('pageSize', String(Number.isFinite(requested) ? Math.min(MAX_REPORT_ROW_COUNT, Math.max(1, requested)) : MAX_REPORT_ROW_COUNT))
  }

  const url = resolveDataUrl(baseUrl, resolvedPath)
  if (url.origin !== window.location.origin) throw new Error('报表数据源必须与管理后台同源')
  query.forEach((value, key) => url.searchParams.set(key, value))
  const response = await authenticatedFetch(url, { headers: { Accept: 'application/json' } })
  const payload = await parseJson(response, `数据集 ${dataset.name}`)
  if (!response.ok) throw new Error(`数据集请求失败：HTTP ${response.status}`)
  if (!isObject(payload) || typeof payload.code !== 'number') return payload
  if (payload.code !== 0) throw new Error(typeof payload.msg === 'string' ? payload.msg : `请求失败：${payload.code}`)
  return payload.data
}

function resolveDataUrl(baseUrl: URL, path: string): URL {
  const trimmedPath = path.trim()
  if (!trimmedPath || trimmedPath.startsWith('//') || /^[A-Za-z][A-Za-z\d+.-]*:/.test(trimmedPath)) {
    throw new Error('报表数据源路径必须是同源相对路径')
  }
  const basePath = baseUrl.pathname.replace(/\/+$/, '')
  const normalizedPath = trimmedPath.replace(/^\/+/, '')
  const pathWithBase = basePath && (trimmedPath === basePath || trimmedPath.startsWith(`${basePath}/`))
    ? trimmedPath
    : `${basePath}/${normalizedPath}`
  return new URL(pathWithBase || '/', baseUrl.origin)
}

function validateDatasetShape(document: ReportDocument, dataset: ReportDatasetSpec, value: unknown): void {
  const rows = rowsFromResult(value)
  if (!rows.length) return
  const pointers = new Set(dataset.fields.map(({ pointer }) => pointer))
  document.rows.flatMap(({ blocks }) => blocks).filter((block) => datasetKeyOf(block) === dataset.key).forEach((block) => {
    blockPointers(block).forEach((pointer) => pointers.add(pointer))
  })
  pointers.forEach((pointer) => {
    const exists = resolveJsonPointer(value, pointer) !== undefined || rows.some((row) => resolveJsonPointer(row, pointer) !== undefined)
    if (!exists) throw new Error(`字段结构已变更：${pointer}`)
  })
}

function blockPointers(block: ReportBlockSpec): string[] {
  if (block.kind === 'TEXT') return []
  if (block.kind === 'METRIC') return [block.valuePointer]
  if (block.kind === 'TABLE') return block.columns.map(({ valuePointer }) => valuePointer)
  if (block.kind === 'CHART') return [block.categoryPointer, block.valuePointer]
  return [block.sourcePointer]
}

function datasetKeyOf(block: ReportBlockSpec): string | undefined {
  return block.kind === 'TEXT' ? undefined : block.datasetKey
}

function fieldsFromSchema(document: OpenApiDocument, schema: OpenApiSchema | undefined): ReportDatasetField[] {
  const rowSchema = unwrapRowSchema(document, schema, 0)
  return Object.entries(rowSchema?.properties ?? {}).map(([name, field], index) => ({
    key: stableFieldKey(name, index),
    label: field.title ?? field.description ?? name,
    pointer: `/${escapePointer(name)}`,
  }))
}

function unwrapRowSchema(document: OpenApiDocument, schema: OpenApiSchema | undefined, depth: number): OpenApiSchema | undefined {
  if (!schema || depth > 8) return schema
  if (schema.$ref) return unwrapRowSchema(document, resolveSchemaReference(document, schema.$ref), depth + 1)
  if (schema.allOf?.length) return unwrapRowSchema(document, mergeSchemas(schema.allOf), depth + 1)
  if (schema.type === 'array' || schema.items) return unwrapRowSchema(document, schema.items, depth + 1)
  for (const key of ['data', 'rows', 'list', 'items']) {
    const nested = schema.properties?.[key]
    if (nested) return unwrapRowSchema(document, nested, depth + 1)
  }
  return schema
}

function resolveSchemaReference(document: OpenApiDocument, reference: string): OpenApiSchema | undefined {
  const prefix = '#/components/schemas/'
  return reference.startsWith(prefix) ? document.components?.schemas?.[reference.slice(prefix.length)] : undefined
}

function mergeSchemas(schemas: OpenApiSchema[]): OpenApiSchema {
  return {
    properties: Object.assign({}, ...schemas.map(({ properties }) => properties ?? {})),
  }
}

function responseSchema(operation: OpenApiOperation): OpenApiSchema | undefined {
  const responses = operation.responses ?? {}
  const response = responses['200'] ?? responses['2XX'] ?? Object.entries(responses)
    .find(([status]) => /^2\d\d$/.test(status))?.[1]
  const content = response?.content ?? {}
  return content['application/json']?.schema ?? Object.entries(content)
    .find(([contentType]) => contentType.endsWith('+json'))?.[1].schema
}

function hasRecognizableJsonResponse(document: OpenApiDocument, operation: OpenApiOperation): boolean {
  return isRecognizableSchema(document, responseSchema(operation), 0)
}

function isRecognizableSchema(document: OpenApiDocument, schema: OpenApiSchema | undefined, depth: number): boolean {
  if (!schema || depth > 8) return false
  if (schema.$ref) return isRecognizableSchema(document, resolveSchemaReference(document, schema.$ref), depth + 1)
  if (schema.allOf?.length) return isRecognizableSchema(document, mergeSchemas(schema.allOf), depth + 1)
  if (schema.type === 'array' || schema.items) return isRecognizableSchema(document, schema.items, depth + 1)
  for (const key of ['data', 'rows', 'list', 'items']) {
    const nested = schema.properties?.[key]
    if (nested) return isRecognizableSchema(document, nested, depth + 1)
  }
  return schema.type === 'object' || Boolean(schema.properties)
}

function sourceParameters(parameters: OpenApiParameter[]): Array<{ name: string; required: boolean }> {
  return parameters.flatMap((parameter) => parameter.name && isPathOrQueryParameter(parameter)
    ? [{ name: parameter.name, required: Boolean(parameter.required) }]
    : [],
  )
}

function isPathOrQueryParameter(parameter: OpenApiParameter): boolean {
  return parameter.in === 'path' || parameter.in === 'query'
}

function literalValue(value: JsonLiteral): string {
  return value === null ? 'null' : String(value)
}

async function parseJson(response: Response, role: string): Promise<unknown> {
  const content = await response.text()
  try {
    return JSON.parse(content) as unknown
  } catch {
    throw new Error(`${role} 返回了无效 JSON`)
  }
}

function stableFieldKey(name: string, index: number): string {
  const normalized = name
    .replace(/[^A-Za-z0-9]+(.)?/g, (_, next: string | undefined) => next?.toUpperCase() ?? '')
    .replace(/^[^a-z]+/, '')
  return normalized || `field${index + 1}`
}

function escapePointer(value: string): string {
  return value.replaceAll('~', '~0').replaceAll('/', '~1')
}

function scoreModelPath(path: string): number {
  if (path.endsWith('/page')) return 0
  if (path.endsWith('/simple-list') || path.endsWith('/list')) return 1
  return 2
}

function isObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

const OPERATION_ID = /^[A-Za-z_][A-Za-z0-9_.-]*$/
