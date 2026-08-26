import type {
  JsonObject,
  LsiDtoTypeDraft,
  LowcodeApiSchemaDraft,
  LowcodeDtoFieldDraft,
  LowcodeDtoKind,
  LowcodeDtoResourceDraft,
  LowcodeDtoResourceSummary,
} from '@/types'
import { toResourceCodeFromClassName } from '@/lib/identifier'

interface DtoCreationContext {
  featureId: number | string
  packageName: string
  contributorId: string
}

export function createDtoResource(context?: DtoCreationContext): LowcodeDtoResourceDraft {
  return {
    featureId: context?.featureId ?? 0,
    dtoCode: '',
    name: '',
    packageName: context?.packageName ?? '',
    className: '',
    kind: 'OUTPUT',
    visibility: 'PUBLIC',
    sourceModelCode: null,
    selectionMode: 'EXPLICIT',
    excludedPaths: [],
    fields: [],
    annotations: [],
    superTypes: [],
    contributorId: context?.contributorId ?? null,
    status: 1,
    version: 1,
    description: null,
  }
}

export function normalizeDtoResource(
  value: LowcodeDtoResourceSummary | JsonObject,
): LowcodeDtoResourceDraft {
  const record = value as LowcodeDtoResourceSummary
  const sourceModelCode = (value as JsonObject).sourceModelCode
  const { sourceModel, ...resource } = record
  return {
    ...createDtoResource(),
    ...resource,
    kind: resource.kind === 'VIEW' ? 'OUTPUT' : resource.kind,
    visibility: resource.visibility ?? 'PUBLIC',
    sourceModelCode: sourceModel?.modelCode ?? (typeof sourceModelCode === 'string' ? sourceModelCode : null),
    excludedPaths: [...(record.excludedPaths ?? [])],
    fields: (record.fields ?? []).map((field) => ({
      ...field,
      sourcePath: field.sourcePath || field.name,
      description: typeof field.description === 'string' ? field.description : null,
      schema: field.schema ? normalizeSchema(field.schema) : null,
      kotlinType: field.kotlinType ? normalizeKotlinType(field.kotlinType) : null,
      validations: (field.validations ?? []).map((rule) => ({
        ...rule,
        parameters: stringRecord(rule.parameters),
      })),
      annotations: (field.annotations ?? []).map((annotation) => ({
        ...annotation,
        arguments: (annotation.arguments ?? []).map((argument) => ({ ...argument })),
      })),
      defaultValue: field.defaultValue ? { ...field.defaultValue } : null,
    })),
    annotations: (record.annotations ?? []).map((annotation) => ({
      ...annotation,
      arguments: (annotation.arguments ?? []).map((argument) => ({ ...argument })),
    })),
    superTypes: (record.superTypes ?? []).map(normalizeKotlinType),
  }
}

export function applyAgentDtoDraft(
  current: LowcodeDtoResourceDraft,
  generated: JsonObject,
): LowcodeDtoResourceDraft {
  const generatedFields = Array.isArray(generated.fields)
    ? generated.fields.filter(isJsonObject)
    : current.fields
  const currentFields = new Map(current.fields.map((field) => [dtoFieldKey(field), field]))
  const fields = generatedFields.map((field) => {
    const existing = currentFields.get(dtoFieldKey(field))
    return existing ? { ...existing, ...field, id: existing.id ?? field.id } : field
  })
  return normalizeDtoResource({
    ...current,
    ...generated,
    id: current.id,
    featureId: current.featureId,
    name: current.name,
    fields,
  })
}

export function createDtoResourceField(kind: LowcodeDtoKind, independent: boolean): LowcodeDtoFieldDraft {
  const structure = kind === 'STRUCTURE'
  return {
    name: '',
    sourcePath: '',
    description: null,
    nullability: independent ? 'NON_NULL' : 'INHERIT',
    schema: independent && !structure ? createFieldSchema('string') : null,
    kotlinType: structure ? createKotlinType() : null,
    validations: [],
    annotations: [],
    defaultValue: null,
  }
}

export function applyDtoKind(
  draft: LowcodeDtoResourceDraft,
  kind: LowcodeDtoKind,
): LowcodeDtoResourceDraft {
  const structure = kind === 'STRUCTURE'
  return {
    ...draft,
    kind,
    sourceModelCode: structure ? null : draft.sourceModelCode,
    selectionMode: 'EXPLICIT',
    excludedPaths: structure ? [] : draft.excludedPaths,
    fields: draft.fields.map((field) => ({
      ...field,
      schema: structure ? null : field.schema ?? createFieldSchema('string'),
      kotlinType: structure ? field.kotlinType ?? createKotlinType() : null,
      validations: structure ? [] : field.validations,
      nullability: field.nullability === 'INHERIT' && !draft.sourceModelCode ? 'NON_NULL' : field.nullability,
    })),
  }
}

export function applyDtoClassName(
  draft: LowcodeDtoResourceDraft,
  className: string,
): LowcodeDtoResourceDraft {
  const suffix = dtoClassSuffix(draft.kind)
  const previousCode = toResourceCodeFromClassName(draft.className, suffix)
  return {
    ...draft,
    className,
    dtoCode: !draft.dtoCode || draft.dtoCode === previousCode
      ? toResourceCodeFromClassName(className, suffix)
      : draft.dtoCode,
  }
}

export function createFieldSchema(type: string): LowcodeApiSchemaDraft {
  const [schemaType, format] = type.split(':')
  return normalizeSchema({ type: schemaType, format: format || null })
}

export function fieldSchemaKey(schema?: LowcodeApiSchemaDraft | null): string {
  if (!schema?.type) return 'string'
  return schema.format ? `${schema.type}:${schema.format}` : schema.type
}

export function validateDtoResource(draft: LowcodeDtoResourceDraft): string[] {
  const errors: string[] = []
  if (!draft.name.trim()) errors.push('请输入 DTO 注释')
  if (!draft.dtoCode.trim()) errors.push('DTO 内部标识生成失败，请检查类名')
  if (!draft.className.trim()) errors.push('请输入 类名')
  if (!draft.packageName.trim()) errors.push('请输入业务包名')
  if (!draft.contributorId?.trim()) errors.push('请输入Contributor ID')
  if (draft.kind === 'STRUCTURE' && draft.sourceModelCode) errors.push('结构 DTO 不能选择来源实体')
  if (!draft.sourceModelCode && !draft.fields.length) errors.push('独立 DTO 至少需要一个字段')
  if (draft.kind === 'STRUCTURE') {
    draft.fields.forEach((field) => {
      if (!field.kotlinType?.qualifiedName.trim()) errors.push(`字段 ${field.name || '未命名'} 缺少 Kotlin 全限定类型`)
    })
  }
  return errors
}

export function createKotlinType(qualifiedName = 'kotlin.String'): LsiDtoTypeDraft {
  return { qualifiedName, arguments: [], nullable: false }
}

export function dtoClassSuffix(kind: LowcodeDtoKind): string {
  if (kind === 'OUTPUT') return 'Output'
  if (kind === 'VIEW') return 'View'
  if (kind === 'INPUT') return 'Input'
  return ''
}

function normalizeSchema(value: Partial<LowcodeApiSchemaDraft>): LowcodeApiSchemaDraft {
  return {
    type: value.type ?? null,
    typeRef: value.typeRef ?? null,
    format: value.format ?? null,
    description: value.description ?? null,
    properties: value.properties ?? {},
    required: value.required ?? [],
    items: value.items ?? null,
    enumValues: value.enumValues ?? [],
    oneOf: value.oneOf ?? [],
  }
}

function normalizeKotlinType(value: LsiDtoTypeDraft): LsiDtoTypeDraft {
  return {
    qualifiedName: value.qualifiedName ?? '',
    arguments: (value.arguments ?? []).map(normalizeKotlinType),
    nullable: value.nullable ?? false,
  }
}

function dtoFieldKey(value: JsonObject): string {
  const sourcePath = typeof value.sourcePath === 'string' ? value.sourcePath.trim() : ''
  const name = typeof value.name === 'string' ? value.name.trim() : ''
  return sourcePath || name
}

function isJsonObject(value: unknown): value is JsonObject {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function stringRecord(value: unknown): Record<string, string> {
  if (!isJsonObject(value)) return {}
  return Object.fromEntries(
    Object.entries(value).filter((entry): entry is [string, string] => typeof entry[1] === 'string'),
  )
}
