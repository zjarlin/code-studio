import type {
  JsonObject,
  LowcodeApiBodyDraft,
  LowcodeApiContractDraft,
  LowcodeApiParameterDraft,
  LowcodeApiSchemaDraft,
  LowcodeCustomOperationDraft,
} from '../../types'
import {
  createApiSchema,
  normalizeAgentExposure,
  normalizeCustomOperation,
  toPascalCase,
  validateCustomOperations,
} from '../model-studio/model-draft'
import { toResourceCodeFromClassName } from '@/lib/identifier'

export function createEmptyContract(): LowcodeApiContractDraft {
  return {
    featureId: 0,
    contractCode: '',
    name: '',
    packageName: 'application.service',
    className: '',
    path: '',
    contributorId: null,
    status: 1,
    version: 1,
    description: null,
    operations: [],
    agentExposure: { operations: {} },
  }
}

export function normalizeContractDraft(value: JsonObject): LowcodeApiContractDraft {
  return {
    ...value,
    id: value.id as number | string | undefined,
    featureId: (value.featureId as number | string | undefined) ?? 0,
    contractCode: stringValue(value.contractCode),
    name: stringValue(value.name),
    packageName: stringValue(value.packageName) || 'application.service',
    className: stringValue(value.className),
    path: stringValue(value.path),
    contributorId: optionalString(value.contributorId),
    status: numberValue(value.status, 1),
    version: numberValue(value.version, 1),
    description: optionalString(value.description),
    operations: arrayValue(value.operations).map(normalizeCustomOperation),
    agentExposure: normalizeAgentExposure(value.agentExposure),
  }
}

export function applyAgentContractDraft(
  current: LowcodeApiContractDraft,
  generated: JsonObject,
): LowcodeApiContractDraft {
  const next = normalizeContractDraft({
    ...generated,
    id: current.id,
    status: current.status,
    version: current.version,
    contributorId: current.contributorId,
    operations: arrayValue(generated.operations).map(normalizeAgentOperation),
    agentExposure: generated.agentExposure ?? current.agentExposure,
  })
  return {
    ...next,
    id: current.id,
    status: current.status,
    version: current.version,
    contributorId: current.contributorId,
  }
}

export function completeContractMetadata(draft: LowcodeApiContractDraft): LowcodeApiContractDraft {
  const firstOperation = draft.operations[0]
  const contractCode = draft.contractCode
    || toResourceCodeFromClassName(draft.className, 'Service')
    || firstOperation?.operationCode
    || 'generatedContract'
  const completed = applyContractCode(draft, contractCode)
  return {
    ...completed,
    name: completed.name || firstOperation?.name || '领域 Service',
    packageName: completed.packageName || 'application.service',
  }
}

export function applyContractClassName(
  draft: LowcodeApiContractDraft,
  className: string,
): LowcodeApiContractDraft {
  const previousCode = toResourceCodeFromClassName(draft.className, 'Service')
  if (draft.contractCode && draft.contractCode !== previousCode) {
    return { ...draft, className }
  }
  return applyContractCode(
    { ...draft, className },
    toResourceCodeFromClassName(className, 'Service'),
  )
}

export function applyContractCode(
  draft: LowcodeApiContractDraft,
  contractCode: string,
): LowcodeApiContractDraft {
  const previousClassName = `${toPascalCase(draft.contractCode)}Service`
  const previousPath = suggestedPath(draft.contractCode)
  return {
    ...draft,
    contractCode,
    className: replaceConventionValue(
      draft.className,
      previousClassName,
      `${toPascalCase(contractCode)}Service`,
    ),
    path: replaceConventionValue(draft.path, previousPath, suggestedPath(contractCode)),
  }
}

export function validateContractDraft(draft: LowcodeApiContractDraft): string[] {
  const errors: string[] = []
  required(draft.contractCode, 'Service 内部标识', errors)
  required(draft.name, 'Service 注释', errors)
  required(draft.packageName, '业务包名', errors)
  required(draft.className, '领域 Service 接口', errors)
  required(draft.contributorId, '生成Contributor ID', errors)
  if (draft.className && !draft.className.endsWith('Service')) {
    errors.push('领域 Service 接口必须以 Service 结尾')
  }
  required(draft.path, '基础路径', errors)
  if (draft.path && !draft.path.startsWith('/')) {
    errors.push('基础路径必须以 / 开头')
  }
  if (draft.operations.length === 0) {
    errors.push('领域 Service 至少配置一个操作')
  }
  errors.push(...validateCustomOperations(draft.operations))
  const operations = new Map(draft.operations.map((operation) => [operation.operationCode, operation]))
  Object.keys(draft.agentExposure.operations).forEach((operationCode) => {
    const operation = operations.get(operationCode)
    if (!operation) {
      errors.push(`Agent 暴露操作 ${operationCode} 不存在`)
    } else if (operation.implementation !== 'GENERATED' || operation.transport !== 'HTTP') {
      errors.push(`Agent 暴露操作 ${operationCode} 必须是平台生成的 HTTP 操作`)
    } else if (operation.authenticated && !operation.permission?.trim()) {
      errors.push(`Agent 暴露操作 ${operationCode} 必须配置权限标识`)
    }
  })
  return errors
}

export function updateContractOperations(
  draft: LowcodeApiContractDraft,
  operations: LowcodeCustomOperationDraft[],
): LowcodeApiContractDraft {
  const nextExposure = { ...draft.agentExposure.operations }
  if (operations.length === draft.operations.length) {
    draft.operations.forEach((operation, index) => {
      const next = operations[index]
      const exposure = nextExposure[operation.operationCode]
      if (exposure && next?.operationCode && next.operationCode !== operation.operationCode) {
        delete nextExposure[operation.operationCode]
        nextExposure[next.operationCode] = exposure
      }
    })
  }
  const operationByCode = new Map(operations.map((operation) => [operation.operationCode, operation]))
  return {
    ...draft,
    operations,
    agentExposure: {
      operations: Object.fromEntries(
        Object.entries(nextExposure).filter(([operationCode]) => {
          const operation = operationByCode.get(operationCode)
          return operation?.implementation === 'GENERATED' && operation.transport === 'HTTP'
        }),
      ),
    },
  }
}

function suggestedPath(contractCode: string): string {
  return contractCode
    ? `/${contractCode.replace(/([a-z0-9])([A-Z])/g, '$1-$2').replace(/_/g, '-').toLowerCase()}`
    : ''
}

function replaceConventionValue(current: string, previous: string, next: string): string {
  return !current || current === previous ? next : current
}

function required(value: string | null | undefined, label: string, errors: string[]): void {
  if (!value?.trim()) {
    errors.push(`${label}不能为空`)
  }
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function optionalString(value: unknown): string | null {
  const result = stringValue(value).trim()
  return result || null
}

function numberValue(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function arrayValue(value: unknown): JsonObject[] {
  return Array.isArray(value)
    ? value.filter((item): item is JsonObject => typeof item === 'object' && item !== null && !Array.isArray(item))
    : []
}

function normalizeAgentOperation(value: JsonObject): LowcodeCustomOperationDraft {
  return normalizeCustomOperation({
    ...value,
    parameters: arrayValue(value.parameters).map(normalizeAgentParameter),
    requestBody: normalizeAgentBody(value.requestBody),
    responseBody: normalizeAgentBody(value.responseBody),
  })
}

function normalizeAgentParameter(value: JsonObject): LowcodeApiParameterDraft {
  const schema = createApiSchema(stringValue(value.type) || 'string')
  schema.format = optionalString(value.format)
  return {
    name: stringValue(value.name),
    location: enumValue(value.location, ['PATH', 'QUERY', 'HEADER', 'COOKIE'], 'QUERY'),
    required: booleanValue(value.required, false),
    description: optionalString(value.description),
    schema,
  }
}

function normalizeAgentBody(value: unknown): LowcodeApiBodyDraft | null {
  const body = objectValue(value)
  if (!Object.keys(body).length) return null

  const typeRef = normalizeAgentDtoRef(body.typeRef)
  if (typeRef) {
    const schema = createApiSchema()
    schema.type = null
    schema.typeRef = typeRef
    return {
      contentType: stringValue(body.contentType) || 'application/json',
      required: booleanValue(body.required, true),
      description: optionalString(body.description),
      schema,
    }
  }

  const fields = arrayValue(body.fields)
  const properties = Object.fromEntries(fields.map((field) => [
    stringValue(field.name),
    normalizeAgentBodyField(field),
  ]).filter(([name]) => name))
  const schema = createApiSchema()
  schema.properties = properties
  schema.required = fields
    .filter((field) => booleanValue(field.required, false))
    .map((field) => stringValue(field.name))
    .filter(Boolean)
  return {
    contentType: stringValue(body.contentType) || 'application/json',
    required: booleanValue(body.required, true),
    description: optionalString(body.description),
    schema,
  }
}

function normalizeAgentBodyField(value: JsonObject): LowcodeApiSchemaDraft {
  const typeRef = normalizeAgentDtoRef(value.typeRef)
  if (typeRef) {
    const referenced = createApiSchema()
    referenced.type = null
    referenced.typeRef = typeRef
    return referenced
  }
  const type = stringValue(value.type) || 'string'
  const schema = createApiSchema(type)
  schema.format = optionalString(value.format)
  schema.description = optionalString(value.description)
  schema.enumValues = stringArray(value.enumValues)
  if (type === 'array') {
    const itemRef = normalizeAgentDtoRef(value.arrayItemTypeRef)
    schema.items = createApiSchema(stringValue(value.arrayItemType) || 'string')
    if (itemRef) {
      schema.items.type = null
      schema.items.typeRef = itemRef
    }
  }
  return schema
}

function normalizeAgentDtoRef(value: unknown): { modelCode?: string | null, dtoCode: string } | null {
  const ref = objectValue(value)
  const modelCode = stringValue(ref.modelCode)
  const dtoCode = stringValue(ref.dtoCode)
  return modelCode || dtoCode ? { modelCode: modelCode || null, dtoCode } : null
}

function objectValue(value: unknown): JsonObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? value as JsonObject
    : {}
}

function booleanValue(value: unknown, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

function enumValue<T extends string>(value: unknown, values: readonly T[], fallback: T): T {
  return typeof value === 'string' && values.includes(value as T) ? value as T : fallback
}
