import type {
  JsonObject,
  LowcodeApiBodyDraft,
  LowcodeApiContractDraft,
  LowcodeApiParameterDraft,
  LowcodeApiSchemaDraft,
  LowcodeCustomOperationDraft,
  LowcodeDtoResourceDraft,
  LowcodeModelDraft,
  LowcodeNamedDtoSchemaDraft,
} from '@/types'

import type { MetadataDisplayTextScope } from './metadata-display-text'

export interface MetadataDisplayTextTarget {
  key: string
  context: string
  value: string
  replace: (value: string) => void
}

export function collectMetadataDisplayTextTargets(
  scope: MetadataDisplayTextScope,
  draft: JsonObject,
): MetadataDisplayTextTarget[] {
  const targets = scope === 'model'
    ? collectModelTargets(draft as LowcodeModelDraft)
    : scope === 'dto'
      ? collectDtoTargets(draft as LowcodeDtoResourceDraft)
      : collectContractTargets(draft as LowcodeApiContractDraft)
  const uniqueTargets = new Map<string, MetadataDisplayTextTarget>()
  for (const target of targets) {
    if (uniqueTargets.has(target.key)) {
      throw new Error(`展示文本目标重复: ${target.key}`)
    }
    uniqueTargets.set(target.key, target)
  }
  return [...uniqueTargets.values()].filter((target) =>
    target.value === '' || needsChineseTranslation(target.value))
}

export function needsChineseTranslation(value: string): boolean {
  const latinTerms = value.match(/[A-Za-z][A-Za-z0-9.+#-]*/g) ?? []
  return latinTerms.some((term) => !TECHNICAL_TERMS.has(term.toLowerCase()))
}

function collectModelTargets(model: LowcodeModelDraft): MetadataDisplayTextTarget[] {
  const targets: MetadataDisplayTextTarget[] = []
  const modelContext = `模型 ${model.modelCode}`
  addTextTarget(targets, ['model', 'name'], `${modelContext} 的展示名称`, model, 'name')
  addTextTarget(targets, ['model', 'remark'], `${modelContext} 的说明`, model, 'remark')

  model.fields.forEach((field, index) => {
    const identity = memberIdentity(field, 'fieldCode', index)
    const context = `${modelContext} 字段 ${field.fieldCode}，数据库列 ${field.dbColumn}`
    addTextTarget(targets, ['field', identity, 'label'], `${context} 的注释`, field, 'label')
    addTextTarget(targets, ['field', identity, 'remark'], `${context} 的说明`, field, 'remark')
  })
  model.queries.forEach((query, index) => {
    const identity = memberIdentity(query, 'queryCode', index)
    addTextTarget(
      targets,
      ['query', identity, 'label'],
      `${modelContext} 查询 ${query.queryCode} 的展示名称`,
      query,
      'label',
    )
  })
  model.relations.forEach((relation, index) => {
    const identity = memberIdentity(relation, 'relationCode', index)
    const target = relation.targetModelCode ? `，目标模型 ${relation.targetModelCode}` : ''
    addTextTarget(
      targets,
      ['relation', identity, 'label'],
      `${modelContext} 关联 ${relation.relationCode}${target} 的注释`,
      relation,
      'label',
    )
  })

  model.entityConfig.inheritedProperties.forEach((property, index) => {
    const identity = memberIdentity(property, 'name', index)
    addTextTarget(
      targets,
      ['inherited-property', identity, 'description'],
      `${modelContext} 继承属性 ${property.name} 的说明`,
      property,
      'description',
    )
  })
  model.entityConfig.formulaProperties.forEach((property, index) => {
    const identity = memberIdentity(property, 'propertyCode', index)
    const context = `${modelContext} 计算属性 ${property.propertyCode}`
    addTextTarget(targets, ['formula-property', identity, 'label'], `${context} 的注释`, property, 'label')
    addTextTarget(
      targets,
      ['formula-property', identity, 'description'],
      `${context} 的说明`,
      property,
      'description',
    )
  })
  model.entityConfig.transientProperties.forEach((property, index) => {
    const identity = memberIdentity(property, 'propertyCode', index)
    const context = `${modelContext} 瞬态属性 ${property.propertyCode}`
    addTextTarget(targets, ['transient-property', identity, 'label'], `${context} 的注释`, property, 'label')
    addTextTarget(
      targets,
      ['transient-property', identity, 'description'],
      `${context} 的说明`,
      property,
      'description',
    )
  })

  const route = model.routeConfig
  addTextTarget(targets, ['route', 'description'], `${modelContext} 路由的说明`, route, 'description')
  if (model.queries.length === 0) {
    route.queryFields.forEach((field, index) => {
      const identity = memberIdentity(field, 'propertyName', index, field.parameterName)
      addTextTarget(
        targets,
        ['route-query', identity, 'description'],
        `${modelContext} 路由查询参数 ${field.parameterName} 的说明`,
        field,
        'description',
      )
    })
  }
  collectOperationTargets(targets, ['route-operation'], modelContext, route.customOperations)
  collectNamedDtoSchemaTargets(targets, ['route-dto-schema'], modelContext, route.dtoSchemas)
  return targets
}

function collectDtoTargets(dto: LowcodeDtoResourceDraft): MetadataDisplayTextTarget[] {
  const targets: MetadataDisplayTextTarget[] = []
  const dtoContext = `DTO ${dto.dtoCode}`
  addTextTarget(targets, ['dto', 'name'], `${dtoContext} 的中文注释`, dto, 'name')
  addTextTarget(targets, ['dto', 'description'], `${dtoContext} 的说明`, dto, 'description')
  dto.fields.forEach((field, index) => {
    const identity = memberIdentity(field, 'sourcePath', index, field.name)
    addTextTarget(
      targets,
      ['dto-field', identity, 'description'],
      `${dtoContext} 字段 ${field.name} 的说明`,
      field,
      'description',
      !dto.sourceModelCode,
    )
    if (!field.schema) {
      return
    }
    collectSchemaChildrenTargets(
      targets,
      ['dto-field', identity, 'schema'],
      `${dtoContext} 字段 ${field.name}`,
      field.schema,
    )
  })
  return targets
}

function collectContractTargets(contract: LowcodeApiContractDraft): MetadataDisplayTextTarget[] {
  const targets: MetadataDisplayTextTarget[] = []
  const contractContext = `契约 ${contract.contractCode}`
  addTextTarget(targets, ['contract', 'name'], `${contractContext} 的展示名称`, contract, 'name')
  addTextTarget(
    targets,
    ['contract', 'description'],
    `${contractContext} 的说明`,
    contract,
    'description',
  )
  collectOperationTargets(targets, ['contract-operation'], contractContext, contract.operations)
  return targets
}

function collectOperationTargets(
  targets: MetadataDisplayTextTarget[],
  keyPrefix: string[],
  ownerContext: string,
  operations: LowcodeCustomOperationDraft[],
): void {
  operations.forEach((operation, index) => {
    const identity = memberIdentity(operation, 'operationCode', index)
    const key = [...keyPrefix, identity]
    const context = `${ownerContext} 操作 ${operation.operationCode}`
    addTextTarget(targets, [...key, 'name'], `${context} 的展示名称`, operation, 'name')
    addTextTarget(targets, [...key, 'description'], `${context} 的说明`, operation, 'description')
    operation.parameters.forEach((parameter, parameterIndex) => {
      collectParameterTargets(targets, [...key, 'parameter'], context, parameter, parameterIndex)
    })
    collectBodyTargets(targets, [...key, 'request'], `${context} 请求体`, operation.requestBody)
    collectBodyTargets(targets, [...key, 'response'], `${context} 响应体`, operation.responseBody)
  })
}

function collectParameterTargets(
  targets: MetadataDisplayTextTarget[],
  keyPrefix: string[],
  operationContext: string,
  parameter: LowcodeApiParameterDraft,
  index: number,
): void {
  const identity = memberIdentity(parameter, 'name', index, parameter.location)
  const key = [...keyPrefix, identity]
  const context = `${operationContext} 参数 ${parameter.location}:${parameter.name}`
  addTextTarget(targets, [...key, 'description'], `${context} 的说明`, parameter, 'description')
  collectSchemaChildrenTargets(targets, [...key, 'schema'], context, parameter.schema)
}

function collectBodyTargets(
  targets: MetadataDisplayTextTarget[],
  keyPrefix: string[],
  context: string,
  body?: LowcodeApiBodyDraft | null,
): void {
  if (!body) {
    return
  }
  addTextTarget(targets, [...keyPrefix, 'description'], `${context} 的说明`, body, 'description')
  collectSchemaChildrenTargets(targets, [...keyPrefix, 'schema'], context, body.schema)
}

function collectSchemaTargets(
  targets: MetadataDisplayTextTarget[],
  keyPrefix: string[],
  context: string,
  schema: LowcodeApiSchemaDraft,
): void {
  addTextTarget(targets, [...keyPrefix, 'description'], `${context} Schema 的说明`, schema, 'description')
  collectSchemaChildrenTargets(targets, keyPrefix, context, schema)
}

function collectSchemaChildrenTargets(
  targets: MetadataDisplayTextTarget[],
  keyPrefix: string[],
  context: string,
  schema: LowcodeApiSchemaDraft,
): void {
  Object.entries(schema.properties).forEach(([propertyName, propertySchema]) => {
    collectSchemaTargets(
      targets,
      [...keyPrefix, 'property', propertyName],
      `${context} Schema 属性 ${propertyName}`,
      propertySchema,
    )
  })
  if (schema.items) {
    collectSchemaTargets(targets, [...keyPrefix, 'items'], `${context} Schema 元素`, schema.items)
  }
  schema.oneOf.forEach((candidate, index) => {
    collectSchemaTargets(targets, [...keyPrefix, 'one-of', String(index)], `${context} Schema 候选 ${index + 1}`, candidate)
  })
}

function collectNamedDtoSchemaTargets(
  targets: MetadataDisplayTextTarget[],
  keyPrefix: string[],
  ownerContext: string,
  schemas: LowcodeNamedDtoSchemaDraft[],
): void {
  schemas.forEach((schema) => {
    const schemaName = componentSchemaName(schema)
    const key = [...keyPrefix, schemaName]
    const context = `${ownerContext} DTO Schema ${schemaName}`
    addTextTarget(targets, [...key, 'description'], `${context} 的说明`, schema, 'description')
    Object.entries(schema.properties).forEach(([propertyName, propertySchema]) => {
      collectSchemaTargets(
        targets,
        [...key, 'property', propertyName],
        `${context} 属性 ${propertyName}`,
        propertySchema,
      )
    })
  })
}

function componentSchemaName(schema: LowcodeNamedDtoSchemaDraft): string {
  const modelCode = schema.ref.modelCode
  if (!modelCode?.trim()) {
    return schema.ref.dtoCode
  }
  if (!schema.ref.dtoCode.trim()) {
    return `${modelCode}_entity`
  }
  return `${modelCode}_${schema.ref.dtoCode}`
}

function addTextTarget(
  targets: MetadataDisplayTextTarget[],
  keyParts: string[],
  context: string,
  owner: JsonObject,
  property: string,
  allowEmpty = property === 'description' || property === 'remark',
): void {
  const value = owner[property]
  if (value !== null && value !== undefined && typeof value !== 'string') {
    return
  }
  const text = typeof value === 'string' && value.trim() ? value : ''
  if (!text && !allowEmpty) return
  targets.push({
    key: keyParts.map(encodeURIComponent).join('/'),
    context,
    value: text,
    replace: (replacement) => {
      owner[property] = replacement
    },
  })
}

function memberIdentity(
  value: JsonObject,
  codeProperty: string,
  index: number,
  secondaryValue?: string,
): string {
  const id = value.id
  if (typeof id === 'number' || typeof id === 'string') {
    return `id:${id}`
  }
  const code = value[codeProperty]
  if (typeof code === 'string' && code.trim()) {
    return secondaryValue ? `code:${code}:${secondaryValue}` : `code:${code}`
  }
  return `index:${index}`
}

const TECHNICAL_TERMS = new Set([
  'api',
  'dto',
  'excel',
  'http',
  'id',
  'iot',
  'json',
  'kdoc',
  'kotlin',
  'mqtt',
  'openapi',
  'sql',
  'sse',
  'url',
  'websocket',
])
