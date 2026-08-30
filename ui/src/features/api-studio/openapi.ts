import type {
  ApiDocument,
  ApiGroup,
  ApiHttpMethod,
  ApiOperation,
  ApiOperationDocument,
  ApiMultipartField,
  ApiFileReferenceField,
  ApiResponse,
  ApiSchema,
} from './types'

const HTTP_METHODS: ApiHttpMethod[] = ['get', 'post', 'put', 'patch', 'delete', 'head', 'options']

const FILE_REFERENCE_UPLOAD_OPERATIONS: Record<string, string> = {
  storedFile: 'uploadFileWithId',
}

export interface ApiSchemaRow {
  path: string
  type: string
  required: boolean
  description?: string
  depth: number
}

export interface ApiResponseDocumentation {
  status: string
  description: string
  contentTypes: string[]
  headers: Array<{
    name: string
    type: string
    description: string
    example?: unknown
  }>
  response: ApiResponse
}

export function collectApiOperations(document: ApiDocument): ApiOperation[] {
  const projected = Object.entries(document.paths ?? {})
    .flatMap(([path, pathItem]) =>
      Object.entries(pathItem ?? {})
        .filter(([method, operation]) => isHttpMethod(method) && operation != null)
        .map(([method, operation]) => {
          const detail = operation as ApiOperationDocument
          const metadataIssues = collectMetadataIssues(detail)
          return {
            id: detail.operationId ?? `${method}:${path}`,
            method: method as ApiHttpMethod,
            path,
            addresses: [{ path, permission: detail['x-permission'] }],
            summary: detail.summary ?? `${method.toUpperCase()} ${path}`,
            description: detail.description,
            tags: detail.tags?.length ? detail.tags : ['未分组'],
            parameters: detail.parameters ?? [],
            requestBody: detail.requestBody,
            responses: detail.responses ?? {},
            lowcodeContract: detail['x-lowcode-contract'] === true,
            transport: detail['x-lowcode-transport'] ?? 'HTTP',
            permission: detail['x-permission'],
            metadataIssues,
            aliasKey: operationAliasKey(method as ApiHttpMethod, path, detail),
          }
        }),
    )

  const operations = new Map<string, ApiOperation>()
  projected.forEach(({ aliasKey, ...operation }) => {
    const existing = operations.get(aliasKey)
    if (existing) {
      existing.addresses.push(...operation.addresses)
      return
    }
    operations.set(aliasKey, operation)
  })

  return [...operations.values()]
    .sort((left, right) => left.path.localeCompare(right.path) || left.method.localeCompare(right.method))
}

function operationAliasKey(method: ApiHttpMethod, path: string, operation: ApiOperationDocument): string {
  const identity = operation.operationId?.trim() || operation.summary?.trim()
  if (!identity) {
    return `${method}:${path}`
  }
  return JSON.stringify({
    identity,
    method,
    summary: operation.summary,
    description: operation.description,
    tags: operation.tags ?? [],
    parameters: operation.parameters ?? [],
    requestBody: operation.requestBody,
    responses: operation.responses ?? {},
    lowcodeContract: operation['x-lowcode-contract'] === true,
    transport: operation['x-lowcode-transport'] ?? 'HTTP',
  })
}

function collectMetadataIssues(operation: ApiOperationDocument): string[] {
  const issues: string[] = []
  if (!operation.summary?.trim()) {
    issues.push('缺少名称')
  }
  if (!operation.tags?.length) {
    issues.push('缺少分组')
  }
  if (!Object.keys(operation.responses ?? {}).length) {
    issues.push('缺少响应')
  }
  return issues
}

export function groupApiOperations(document: ApiDocument, operations: ApiOperation[]): ApiGroup[] {
  const descriptions = new Map((document.tags ?? []).map((tag) => [tag.name, tag.description]))
  const groups = new Map<string, ApiOperation[]>()
  operations.forEach((operation) => {
    operation.tags.forEach((tag) => {
      const grouped = groups.get(tag) ?? []
      grouped.push(operation)
      groups.set(tag, grouped)
    })
  })
  return [...groups.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([name, grouped]) => ({ name, description: descriptions.get(name), operations: grouped }))
}

export function schemaSample(schema: ApiSchema | undefined, document: ApiDocument, resolving = new Set<string>()): unknown {
  if (!schema) {
    return null
  }
  if (schema.example !== undefined) {
    return schema.example
  }
  if (schema.default !== undefined) {
    return schema.default
  }
  if (schema.$ref) {
    const name = schema.$ref.split('/').pop() ?? ''
    if (resolving.has(name)) {
      return {}
    }
    const referenced = document.components?.schemas?.[name]
    if (!referenced) {
      return {}
    }
    const next = new Set(resolving)
    next.add(name)
    return schemaSample(referenced, document, next)
  }
  if (schema.enum?.length) {
    return schema.enum[0]
  }
  if (schema.oneOf?.length || schema.anyOf?.length) {
    return schemaSample((schema.oneOf ?? schema.anyOf)?.[0], document, resolving)
  }
  if (schema.allOf?.length) {
    return schema.allOf.reduce<Record<string, unknown>>((result, item) => {
      const sample = schemaSample(item, document, resolving)
      return typeof sample === 'object' && sample !== null ? { ...result, ...sample } : result
    }, {})
  }
  if (schema.type === 'array' || (Array.isArray(schema.type) && schema.type.includes('array'))) {
    return [schemaSample(schema.items, document, resolving)]
  }
  if (schema.properties || schema.type === 'object' || (Array.isArray(schema.type) && schema.type.includes('object'))) {
    return Object.fromEntries(
      Object.entries(schema.properties ?? {}).map(([key, value]) => [key, schemaSample(value, document, resolving)]),
    )
  }
  if (Array.isArray(schema.type)) {
    return schema.type.find((item) => item !== 'null') === 'boolean' ? false : ''
  }
  switch (schema.type) {
    case 'integer':
      return 1
    case 'number':
      return 0
    case 'boolean':
      return false
    default:
      return ''
  }
}

export function requestBodySample(operation: ApiOperation, document: ApiDocument): string {
  const [contentType, media] = Object.entries(operation.requestBody?.content ?? {})[0] ?? []
  if (!media) {
    return ''
  }
  if (contentType === 'multipart/form-data') {
    return ''
  }
  const example = media.example ?? Object.values(media.examples ?? {})[0]?.value ?? schemaSample(media.schema, document)
  return JSON.stringify(example, null, 2)
}

export function requestContentType(operation: ApiOperation | undefined): string | undefined {
  return Object.keys(operation?.requestBody?.content ?? {})[0]
}

export function requestBodySchema(operation: ApiOperation | undefined): ApiSchema | undefined {
  const contentType = requestContentType(operation)
  return contentType ? operation?.requestBody?.content?.[contentType]?.schema : undefined
}

export function multipartFields(operation: ApiOperation | undefined, document: ApiDocument): ApiMultipartField[] {
  if (requestContentType(operation) !== 'multipart/form-data') {
    return []
  }
  const media = operation?.requestBody?.content?.['multipart/form-data']
  const schema = resolveSchema(media?.schema, document)
  const required = new Set(schema?.required ?? [])
  return Object.entries(schema?.properties ?? {}).map(([name, property]) => ({
    name,
    schema: resolveSchema(property, document) ?? property,
    required: required.has(name),
  }))
}

export function fileReferenceFields(operation: ApiOperation | undefined, document: ApiDocument): ApiFileReferenceField[] {
  if (!operation || requestContentType(operation) === 'multipart/form-data') {
    return []
  }
  const media = Object.values(operation.requestBody?.content ?? {})[0]
  const schema = resolveSchema(media?.schema, document)
  const required = new Set(schema?.required ?? [])
  return Object.entries(schema?.properties ?? {}).flatMap(([name, property]) => {
    const resolved = resolveSchema(property, document) ?? property
    const targetModelCode = resolved['x-lowcode-reference']?.targetModelCode
    const uploadOperationId = targetModelCode ? FILE_REFERENCE_UPLOAD_OPERATIONS[targetModelCode] : undefined
    return uploadOperationId ? [{ name, schema: resolved, required: required.has(name), uploadOperationId }] : []
  })
}

export function responseSchema(
  operation: ApiOperation | undefined,
  document: ApiDocument,
  status?: number,
): ApiSchema | undefined {
  if (!operation) {
    return undefined
  }
  const response = operation.responses[String(status)] ?? operation.responses['200'] ?? Object.values(operation.responses)[0]
  const media = Object.values(response?.content ?? {})[0]
  return media?.schema
}

export function responseDocumentation(
  operation: ApiOperation | undefined,
  document: ApiDocument,
): ApiResponseDocumentation[] {
  if (!operation) {
    return []
  }
  return Object.entries(operation.responses)
    .sort(([left], [right]) => responseStatusOrder(left) - responseStatusOrder(right))
    .map(([status, response]) => ({
      status,
      description: response.description?.trim() || '未提供响应说明。',
      contentTypes: Object.keys(response.content ?? {}),
      headers: Object.entries(response.headers ?? {}).map(([name, header]) => ({
        name,
        type: schemaType(header.schema, document),
        description: header.description?.trim() || '未提供响应头说明。',
        example: header.example,
      })),
      response,
    }))
}

export function schemaRows(schema: ApiSchema | undefined, document: ApiDocument): ApiSchemaRow[] {
  const rows: ApiSchemaRow[] = []
  appendSchemaRows(schema, document, '', 0, rows)
  return rows
}

function appendSchemaRows(
  schema: ApiSchema | undefined,
  document: ApiDocument,
  prefix: string,
  depth: number,
  rows: ApiSchemaRow[],
): void {
  const resolved = resolveSchema(schema, document)
  if (!resolved?.properties) {
    return
  }
  const required = new Set(resolved.required ?? [])
  Object.entries(resolved.properties).forEach(([name, property]) => {
    const path = prefix ? `${prefix}.${name}` : name
    const propertySchema = resolveSchema(property, document)
    rows.push({
      path,
      type: schemaType(propertySchema, document),
      required: required.has(name),
      description: propertySchema?.description,
      depth,
    })
    appendSchemaRows(nestedSchema(propertySchema), document, arrayPath(path, propertySchema), depth + 1, rows)
  })
}

function resolveSchema(schema: ApiSchema | undefined, document: ApiDocument, resolving = new Set<string>()): ApiSchema | undefined {
  if (!schema) {
    return undefined
  }
  if (schema.$ref) {
    const name = schema.$ref.split('/').pop() ?? ''
    const referenced = document.components?.schemas?.[name]
    if (!referenced || resolving.has(name)) {
      return schema
    }
    const next = new Set(resolving)
    next.add(name)
    return resolveSchema(referenced, document, next)
  }
  if (schema.allOf?.length) {
    return schema.allOf.reduce<ApiSchema>((merged, item) => {
      const part = resolveSchema(item, document, resolving) ?? {}
      return {
        ...merged,
        ...part,
        properties: { ...merged.properties, ...part.properties },
        required: [...new Set([...(merged.required ?? []), ...(part.required ?? [])])],
      }
    }, {})
  }
  if (schema.oneOf?.length || schema.anyOf?.length) {
    return resolveSchema((schema.oneOf ?? schema.anyOf)?.[0], document, resolving)
  }
  return schema
}

function nestedSchema(schema: ApiSchema | undefined): ApiSchema | undefined {
  if (!schema) {
    return undefined
  }
  const types = schemaTypes(schema)
  return types.includes('array') ? schema.items : types.includes('object') || schema.properties ? schema : undefined
}

function arrayPath(path: string, schema: ApiSchema | undefined): string {
  return schemaTypes(schema).includes('array') ? `${path}[]` : path
}

function schemaType(schema: ApiSchema | undefined, document: ApiDocument): string {
  const resolved = resolveSchema(schema, document)
  if (!resolved) {
    return 'unknown'
  }
  const types = schemaTypes(resolved)
  if (types.includes('array')) {
    return `array<${schemaType(resolved.items, document)}>`
  }
  if (types.includes('object') || resolved.properties) {
    return 'object'
  }
  return types[0] ?? 'unknown'
}

function schemaTypes(schema: ApiSchema | undefined): string[] {
  if (!schema?.type) {
    return []
  }
  return Array.isArray(schema.type) ? schema.type.filter((type) => type !== 'null') : [schema.type]
}

function isHttpMethod(value: string): value is ApiHttpMethod {
  return HTTP_METHODS.includes(value as ApiHttpMethod)
}

function responseStatusOrder(status: string): number {
  const numeric = Number(status)
  return Number.isFinite(numeric) ? numeric : Number.MAX_SAFE_INTEGER
}
