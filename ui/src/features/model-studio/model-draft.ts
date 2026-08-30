import type {
  JsonObject,
  JsonValue,
  LowcodeFieldDraft,
  LowcodeDtoRefDraft,
  LowcodeEntityConfigDraft,
  LowcodeFormulaPropertyDraft,
  LowcodeTransientPropertyDraft,
  LowcodeApiBodyDraft,
  LowcodeApiParameterDraft,
  LowcodeApiSchemaDraft,
  LowcodeAgentExposureDraft,
  LowcodeCustomOperationDraft,
  LowcodeModelDesignerSection,
  LowcodeModelDraft,
  LowcodeEntityConfigSummary,
  LowcodeModelFieldSummary,
  LowcodeModelRelationSummary,
  LowcodeModelSummary,
  LowcodeModelRecentChanges,
  LowcodeNamedDtoSchemaDraft,
  LowcodeOperationImplementation,
  LowcodeQueryConditionDraft,
  LowcodeQueryDraft,
  LowcodeQueryLogic,
  LowcodeQueryOperator,
  LowcodeQueryValueType,
  LowcodeRelationDraft,
  LowcodeRelationKind,
  LowcodeRouteDraft,
  LowcodeRouteExcelDraft,
  LowcodeRoutePropertyDraft,
  LowcodeRouteQueryFieldDraft,
  LowcodeRouteTreeDraft,
} from '../../types'
import {
  databaseIdentifierToPascalCase,
  toPinyinSnakeIdentifier,
  toResourceCodeFromClassName,
} from '@/lib/identifier'
import { normalizeBaseModels, resolvedBaseModelProperties } from './base-models'

const NUMBER_TYPES = new Set(['long', 'int', 'integer', 'double', 'bigdecimal', 'decimal'])

export interface ModelFeatureLocation {
  featureId: number | string
  packageName: string
  contributorId: string
}

export function normalizeModelDraft(value: JsonObject): LowcodeModelDraft {
  const tableName = stringValue(value.tableName)
  const className = databaseIdentifierToPascalCase(tableName)
  const normalizedValue = { ...value, className, tableName }
  const routeConfig = normalizeRoute(value.routeConfig, normalizedValue)
  return {
    ...normalizedValue,
    id: optionalIdentifier(value.id),
    featureId: optionalIdentifier(value.featureId) ?? 0,
    modelCode: stringValue(value.modelCode),
    name: stringValue(value.name),
    packageName: stringValue(value.packageName),
    className,
    tableName,
    modelType: enumValue(value.modelType, ['ENTITY', 'MAPPED_SUPERCLASS', 'EMBEDDABLE'], 'ENTITY'),
    status: numberValue(value.status, 1),
    version: numberValue(value.version, 1),
    contributorId: optionalString(value.contributorId),
    entityConfig: normalizeEntityConfig(value.entityConfig),
    routeConfig: {
      ...routeConfig,
      className,
      qualifiedName: generatedQualifiedName(stringValue(value.packageName), className),
    },
    remark: optionalString(value.remark),
    fields: arrayValue(value.fields).map((item, index) => normalizeField(item, index)),
    queries: arrayValue(value.queries).map((item, index) => normalizeQuery(item, index)),
    relations: arrayValue(value.relations).map((item, index) => normalizeRelation(item, index)),
  }
}

export function applyModelFeatureLocation(
  draft: LowcodeModelDraft,
  location?: ModelFeatureLocation,
): LowcodeModelDraft {
  if (!location) return draft
  return normalizeModelDraft({
    ...draft,
    featureId: location.featureId,
    packageName: location.packageName,
    contributorId: location.contributorId,
    routeConfig: {
      ...draft.routeConfig,
      packageName: location.packageName,
    },
  })
}

export function applyAgentModelDraft(
  current: LowcodeModelDraft,
  generated: JsonObject,
): LowcodeModelDraft {
  const generatedFields = mergeAgentObjects(
    current.fields,
    arrayValueOrCurrent(generated.fields, current.fields),
    'fieldCode',
  )
  const generatedQueries = mergeAgentQueries(
    current.queries,
    arrayValueOrCurrent(generated.queries, current.queries),
  )
  const generatedRelations = mergeAgentObjects(
    current.relations,
    arrayValueOrCurrent(generated.relations, current.relations),
    'relationCode',
  )
  return normalizeModelDraft({
    ...current,
    ...generated,
    id: current.id,
    status: current.status,
    version: current.version,
    contributorId: current.contributorId,
    routeConfig: current.routeConfig,
    entityConfig: generated.entityConfig ?? current.entityConfig,
    fields: generatedFields,
    queries: generatedQueries,
    relations: generatedRelations,
  })
}

function mergeAgentObjects(
  current: JsonObject[],
  generated: JsonObject[],
  codeProperty: string,
): JsonObject[] {
  const currentByCode = new Map(current.map((item) => [stringValue(item[codeProperty]), item]))
  return generated.map((item) => {
    const existing = currentByCode.get(stringValue(item[codeProperty]))
    return existing ? { ...existing, ...item, id: existing.id ?? item.id } : item
  })
}

function mergeAgentQueries(current: LowcodeQueryDraft[], generated: JsonObject[]): JsonObject[] {
  const currentByCode = new Map(current.map((query) => [query.queryCode, query]))
  return generated.map((query) => {
    const existing = currentByCode.get(stringValue(query.queryCode))
    const items = arrayValue(query.items).length ? arrayValue(query.items) : arrayValue(query.conditions)
    return {
      ...existing,
      ...query,
      id: existing?.id ?? query.id,
      items: mergeQueryConditions(existing?.items ?? [], items),
    }
  })
}

function mergeQueryConditions(current: LowcodeQueryConditionDraft[], generated: JsonObject[]): JsonObject[] {
  const currentByKey = new Map(current.map((item) => [queryConditionKey(item), item]))
  return generated.map((item) => {
    const existing = currentByKey.get(queryConditionKey(item))
    return existing ? { ...existing, ...item, id: existing.id ?? item.id } : item
  })
}

function queryConditionKey(value: JsonObject): string {
  return `${numberValue(value.orderNo, 0)}:${stringValue(value.fieldCode)}`
}

function arrayValueOrCurrent(value: JsonValue | undefined, current: JsonObject[]): JsonObject[] {
  return Array.isArray(value) ? arrayValue(value) : current
}

export function modelFieldKey(field: LowcodeFieldDraft, index: number): string {
  if (field.id != null) return `id:${field.id}`
  if (field.fieldCode) return `code:${field.fieldCode}`
  return `index:${index}`
}

export function diffModelDraft(
  previous: LowcodeModelDraft,
  current: LowcodeModelDraft,
): LowcodeModelRecentChanges {
  const sections: LowcodeModelDesignerSection[] = []
  const modelKeys: Array<keyof LowcodeModelDraft> = [
    'name',
    'modelCode',
    'packageName',
    'className',
    'tableName',
    'modelType',
    'remark',
  ]
  if (modelKeys.some((key) => valuesDiffer(previous[key], current[key]))) sections.push('model')
  if (valuesDiffer(previous.fields, current.fields)) sections.push('fields')
  if (valuesDiffer(previous.entityConfig, current.entityConfig)) sections.push('model')
  if (valuesDiffer(previous.queries, current.queries)) sections.push('queries')
  if (valuesDiffer(previous.relations, current.relations)) sections.push('relations')

  const fieldKeys = current.fields.flatMap((field, index) =>
    valuesDiffer(previous.fields[index], field) ? [modelFieldKey(field, index)] : [])
  return { sections, fieldKeys }
}

function valuesDiffer(previous: unknown, current: unknown): boolean {
  return JSON.stringify(previous) !== JSON.stringify(current)
}

export function createRouteProperty(): LowcodeRoutePropertyDraft {
  return {
    name: '',
    type: 'string',
    format: null,
    required: false,
    arrayItemType: null,
    description: null,
    dictionaryCode: null,
  }
}

export function createRouteQueryField(): LowcodeRouteQueryFieldDraft {
  return {
    propertyName: '',
    parameterName: '',
    operator: 'EQ',
    type: 'string',
    format: null,
    endParameterName: null,
    required: false,
    stateCases: [],
    description: null,
  }
}

export function createRouteTree(): LowcodeRouteTreeDraft {
  return {
    parentIdProperty: 'parentId',
    childrenProperty: 'children',
    keywordProperty: 'name',
    sortProperty: null,
  }
}

export function createRouteExcel(modelName = ''): LowcodeRouteExcelDraft {
  return {
    importEnabled: true,
    exportEnabled: true,
    customImport: false,
    customExport: false,
    fileName: `${modelName || '模型'}数据.xls`,
    templateFileName: `${modelName || '模型'}导入模板.xls`,
    sheetName: '数据',
    templateSheetName: '数据',
    importColumns: [],
    exportColumns: [],
  }
}

export function createApiSchema(type = 'object'): LowcodeApiSchemaDraft {
  return {
    type,
    typeRef: null,
    format: null,
    description: null,
    properties: {},
    required: [],
    items: null,
    enumValues: [],
    oneOf: [],
  }
}

export function createEntityConfig(): LowcodeEntityConfigDraft {
  return {
    sourceMode: 'GENERATED',
    sourceQualifiedName: null,
    baseMode: 'DEFAULT',
    baseModels: ['BASE_ENTITY'],
    superTypes: [],
    relationOrderings: {},
    inheritedProperties: [],
    inheritedRelationCodes: [],
    formulaProperties: [],
    transientProperties: [],
    microServiceName: 'metadata-generated',
    inheritanceRoot: null,
    inheritanceSubtype: null,
  }
}

export function createFormulaProperty(index: number): LowcodeFormulaPropertyDraft {
  const label = `计算属性 ${index + 1}`
  return {
    propertyCode: toPinyinSnakeIdentifier(label),
    label,
    kotlinType: 'String',
    kind: 'KOTLIN',
    expression: '',
    dependencies: [],
    nullable: false,
    description: null,
  }
}

export function createTransientProperty(index: number): LowcodeTransientPropertyDraft {
  const label = `复杂计算属性 ${index + 1}`
  return {
    propertyCode: toPinyinSnakeIdentifier(label),
    label,
    kotlinType: 'String',
    kind: 'RESOLVER',
    resolverValueType: null,
    nullable: false,
    description: null,
    dictionaryCode: null,
  }
}

export function createApiBody(contentType = 'application/json'): LowcodeApiBodyDraft {
  return {
    contentType,
    required: true,
    description: null,
    schema: createApiSchema(),
  }
}

export function createApiParameter(): LowcodeApiParameterDraft {
  return {
    name: '',
    location: 'QUERY',
    required: false,
    description: null,
    schema: createApiSchema('string'),
  }
}

export function createCustomOperation(index: number, basePath = ''): LowcodeCustomOperationDraft {
  return {
    operationCode: `customOperation${index + 1}`,
    name: `自定义操作 ${index + 1}`,
    description: null,
    path: `${basePath.replace(/\/$/, '')}/custom-operation-${index + 1}`,
    method: 'POST',
    transport: 'HTTP',
    implementation: 'GENERATED',
    authenticated: true,
    permission: null,
    callContext: false,
    parameters: [],
    requestBody: createApiBody(),
    responseBody: createApiBody(),
    responseEnvelope: true,
  }
}

export function createField(index: number): LowcodeFieldDraft {
  return {
    orderNo: index + 1,
    fieldCode: '',
    label: '',
    kotlinType: 'String',
    dbColumn: '',
    required: false,
    createWritable: true,
    updateWritable: true,
    listVisible: true,
    formVisible: true,
    formControl: 'input',
    dictCode: null,
    enumStorage: null,
    defaultValue: null,
    remark: null,
    serialized: false,
    key: false,
    maxLength: null,
  }
}

export function createQuery(index: number, fieldCode = ''): LowcodeQueryDraft {
  return {
    orderNo: index + 1,
    queryCode: '',
    label: '',
    logic: 'AND',
    items: fieldCode ? [createQueryCondition(0, fieldCode)] : [],
  }
}

export function createQueryCondition(index: number, fieldCode = ''): LowcodeQueryConditionDraft {
  return {
    orderNo: index + 1,
    fieldCode,
    operator: 'EQ',
    valueType: 'SINGLE',
    paramName: null,
  }
}

export function createRelation(index: number): LowcodeRelationDraft {
  return {
    orderNo: index + 1,
    relationCode: '',
    label: '',
    relationType: 'MANY_TO_ONE',
    targetModelId: null,
    targetModelCode: null,
    joinColumn: null,
    mappedBy: null,
    joinTable: null,
    joinTableJoinColumn: null,
    joinTableInverseColumn: null,
    dissociateAction: 'NONE',
    required: false,
    createWritable: true,
    updateWritable: true,
    listVisible: false,
    formVisible: true,
  }
}

export function moveItem<T extends { orderNo: number }>(items: T[], from: number, to: number): T[] {
  if (from === to || from < 0 || to < 0 || from >= items.length || to >= items.length) {
    return items
  }
  const reordered = [...items]
  const [moved] = reordered.splice(from, 1)
  reordered.splice(to, 0, moved)
  return reordered.map((item, index) => ({ ...item, orderNo: index + 1 }))
}

export function removeItem<T extends { orderNo: number }>(items: T[], index: number): T[] {
  return items.filter((_, itemIndex) => itemIndex !== index)
    .map((item, itemIndex) => ({ ...item, orderNo: itemIndex + 1 }))
}

export function applyModelTableName(draft: LowcodeModelDraft, tableName: string): LowcodeModelDraft {
  const previousDefaultCode = toResourceCodeFromClassName(
    databaseIdentifierToPascalCase(draft.tableName),
  )
  const className = databaseIdentifierToPascalCase(tableName)
  const identityFollowsTable = draft.id == null
    && (!draft.modelCode || draft.modelCode === previousDefaultCode)
  const modelCode = identityFollowsTable
    ? toResourceCodeFromClassName(className)
    : draft.modelCode
  return {
    ...draft,
    tableName,
    className,
    modelCode,
    routeConfig: {
      ...draft.routeConfig,
      className,
      qualifiedName: generatedQualifiedName(draft.packageName, className),
      path: identityFollowsTable
        ? replaceConventionValue(
            draft.routeConfig.path,
            suggestedRoutePath(previousDefaultCode),
            suggestedRoutePath(modelCode),
          )
        : draft.routeConfig.path,
    },
  }
}

export function applyFieldCode(field: LowcodeFieldDraft, fieldCode: string): LowcodeFieldDraft {
  return {
    ...field,
    fieldCode,
    dbColumn: replaceConventionValue(field.dbColumn, toSnakeCase(field.fieldCode), toSnakeCase(fieldCode)),
  }
}

export function applyQueryLogic(query: LowcodeQueryDraft, logic: LowcodeQueryLogic): LowcodeQueryDraft {
  if (logic === 'AND') {
    return { ...query, logic }
  }
  return {
    ...query,
    logic,
    items: query.items.map((item) => ({
      ...item,
      operator: 'LIKE',
      valueType: 'SINGLE',
      paramName: 'keyword',
    })),
  }
}

export function applyQueryOperator(
  condition: LowcodeQueryConditionDraft,
  operator: LowcodeQueryOperator,
  kotlinType: string,
): LowcodeQueryConditionDraft {
  return {
    ...condition,
    operator,
    valueType: queryValueType(operator, kotlinType),
  }
}

export interface QueryableModelProperty {
  code: string
  label: string
  kotlinType: string
  required: boolean
}

type ModelPropertySource = Pick<LowcodeModelSummary, 'modelCode'> & {
  entityConfig?: LowcodeEntityConfigDraft | LowcodeEntityConfigSummary | null
  fields?: Array<LowcodeFieldDraft | LowcodeModelFieldSummary>
  relations?: Array<LowcodeRelationDraft | LowcodeModelRelationSummary>
}

export function queryableModelProperties(
  model: ModelPropertySource,
  models: LowcodeModelSummary[] = [],
): QueryableModelProperty[] {
  const properties = new Map<string, QueryableModelProperty>()
  const modelsByCode = new Map(models.map((candidate) => [candidate.modelCode, candidate]))
  const visited = new Set<string>()

  const append = (source: ModelPropertySource): void => {
    if (visited.has(source.modelCode)) return
    visited.add(source.modelCode)
    const parentCode = source.entityConfig?.inheritanceSubtype?.parentModelCode
    const parent = parentCode ? modelsByCode.get(parentCode) : undefined
    if (parent) append(parent)

    if (source.entityConfig) {
      resolvedBaseModelProperties(source.entityConfig).forEach((property) => properties.set(property.name, {
        code: property.name,
        label: property.description || property.name,
        kotlinType: property.kotlinType,
        required: property.required,
      }))
    }
    ;(source.fields ?? []).forEach((field) => properties.set(field.fieldCode, {
      code: field.fieldCode,
      label: field.label || field.fieldCode || '未命名字段',
      kotlinType: field.kotlinType,
      required: field.required,
    }))
    ;(source.entityConfig?.formulaProperties ?? []).forEach((property) => properties.set(property.propertyCode, {
      code: property.propertyCode,
      label: `${property.label || property.propertyCode}（计算）`,
      kotlinType: property.kotlinType,
      required: !property.nullable,
    }))
    ;(source.entityConfig?.transientProperties ?? []).forEach((property) => properties.set(property.propertyCode, {
      code: property.propertyCode,
      label: `${property.label || property.propertyCode}（复杂计算）`,
      kotlinType: property.kotlinType,
      required: !property.nullable,
    }))
    ;(source.relations ?? [])
      .filter((relation) => relation.relationType === 'MANY_TO_ONE' || relation.relationType === 'ONE_TO_ONE')
      .forEach((relation) => {
        const code = `${relation.relationCode}Id`
        properties.set(code, {
          code,
          label: `${relation.label || relation.relationCode} ID`,
          kotlinType: 'Long',
          required: relation.required,
        })
      })
  }

  append(model)
  return Array.from(properties.values())
}

export function queryValueType(operator: LowcodeQueryOperator, kotlinType: string): LowcodeQueryValueType {
  if (operator === 'IN' || operator === 'NOT_IN') {
    return 'MULTIPLE'
  }
  if (operator !== 'BETWEEN' && operator !== 'TIME_RANGE') {
    return 'SINGLE'
  }
  const normalizedType = kotlinType.trim().toLowerCase()
  if (normalizedType === 'localdate') {
    return 'DATE_RANGE'
  }
  if (normalizedType === 'localdatetime') {
    return 'DATETIME_RANGE'
  }
  return operator === 'BETWEEN' && NUMBER_TYPES.has(normalizedType) ? 'RANGE' : 'SINGLE'
}

export function applyRelationKind(
  relation: LowcodeRelationDraft,
  relationType: LowcodeRelationKind,
): LowcodeRelationDraft {
  const next = { ...relation, relationType }
  if (relationType === 'MANY_TO_ONE') {
    return {
      ...next,
      joinColumn: next.joinColumn || suggestedJoinColumn(next.relationCode),
      mappedBy: null,
      joinTable: null,
      joinTableJoinColumn: null,
      joinTableInverseColumn: null,
    }
  }
  if (relationType === 'ONE_TO_MANY') {
    return {
      ...next,
      joinColumn: null,
      dissociateAction: 'NONE',
      joinTable: null,
      joinTableJoinColumn: null,
      joinTableInverseColumn: null,
    }
  }
  if (relationType === 'ONE_TO_ONE') {
    return {
      ...next,
      joinTable: null,
      joinTableJoinColumn: null,
      joinTableInverseColumn: null,
    }
  }
  return {
    ...next,
    joinColumn: null,
    mappedBy: null,
    dissociateAction: 'NONE',
  }
}

export function applyRelationCode(relation: LowcodeRelationDraft, relationCode: string): LowcodeRelationDraft {
  const ownsJoinColumn = relation.relationType === 'MANY_TO_ONE' ||
    (relation.relationType === 'ONE_TO_ONE' && !relation.mappedBy)
  return {
    ...relation,
    relationCode,
    joinColumn: ownsJoinColumn
      ? replaceConventionValue(
          relation.joinColumn ?? '',
          suggestedJoinColumn(relation.relationCode),
          suggestedJoinColumn(relationCode),
        ) || null
      : relation.joinColumn,
  }
}

export function validateModelDraft(draft: LowcodeModelDraft): string[] {
  const errors: string[] = []
  required(draft.name, '模型注释', errors)
  required(draft.modelCode, '模型内部标识', errors)
  required(draft.tableName, '数据库表名', errors)
  if (draft.tableName.trim() && !draft.className) {
    errors.push('数据库表名无法生成 Kotlin 文件名')
  }
  required(draft.routeConfig.path, 'Controller 主路径', errors)
  if (!draft.routeConfig.path.startsWith('/')) {
    errors.push('Controller 主路径必须以 / 开头')
  }
  draft.fields.forEach((field, index) => {
    required(field.label, `字段 ${index + 1} 名称`, errors)
    required(field.fieldCode, `字段 ${index + 1} 属性名`, errors)
    required(field.kotlinType, `字段 ${index + 1} Kotlin 类型`, errors)
    required(field.dbColumn, `字段 ${index + 1} 数据库列名`, errors)
  })
  draft.queries.forEach((query, index) => {
    required(query.label, `查询 ${index + 1} 名称`, errors)
    required(query.queryCode, `查询 ${index + 1} 方法名`, errors)
    if (query.items.length === 0) {
      errors.push(`查询 ${index + 1} 至少需要一个条件`)
    }
    query.items.forEach((condition, conditionIndex) => {
      required(condition.fieldCode, `查询 ${index + 1} 条件 ${conditionIndex + 1} 字段`, errors)
    })
  })
  draft.relations.forEach((relation, index) => {
    required(relation.label, `关联 ${index + 1} 名称`, errors)
    required(relation.relationCode, `关联 ${index + 1} 属性名`, errors)
    if (!relation.targetModelId && !relation.targetModelCode?.trim()) {
      errors.push(`关联 ${index + 1} 需要目标模型`)
    }
    validateRelationMapping(relation, index, errors)
  })
  errors.push(...validateCustomOperations(draft.routeConfig.customOperations))
  return errors
}

export function validateCustomOperations(operations: LowcodeCustomOperationDraft[]): string[] {
  const errors: string[] = []
  operations.forEach((operation, index) => {
    const label = `Controller 操作 ${index + 1}`
    required(operation.name, `${label} 名称`, errors)
    required(operation.operationCode, `${label} 方法名`, errors)
    required(operation.path, `${label} 路径`, errors)
    if (!operation.path.startsWith('/')) {
      errors.push(`${label} 路径必须以 / 开头`)
    }
    if (operation.transport !== 'HTTP' && operation.responseEnvelope) {
      errors.push(`${label} 的 ${operation.transport} 传输不能使用 CommonResult 响应包装`)
    }
    if (operation.implementation === 'EXISTING_REST' && operation.transport !== 'HTTP') {
      errors.push(`${label} 关联既有 REST 时传输类型必须为 HTTP`)
    }
    if (operation.implementation === 'EXISTING_REST' && operation.callContext) {
      errors.push(`${label} 关联既有 REST 时不能传递调用上下文`)
    }
    if (operation.callContext && !operation.authenticated) {
      errors.push(`${label} 只有认证操作才能传递调用上下文`)
    }
    if (operation.callContext && operation.parameters.some((parameter) => parameter.name === 'context')) {
      errors.push(`${label} 传递调用上下文时不能声明 context 参数`)
    }
    operation.parameters.forEach((parameter, parameterIndex) => {
      required(parameter.name, `${label} 参数 ${parameterIndex + 1} 名称`, errors)
      if (parameter.location === 'PATH' && !parameter.required) {
        errors.push(`${label} 路径参数 ${parameter.name || parameterIndex + 1} 必须设为必填`)
      }
    })
  })
  return errors
}

function normalizeRoute(value: JsonValue | undefined, model: JsonObject): LowcodeRouteDraft {
  const route = objectValue(value)
  const packageName = stringValue(route.packageName) || stringValue(model.packageName)
  const className = stringValue(route.className) || stringValue(model.className)
  return {
    packageName,
    qualifiedName: stringValue(route.qualifiedName) || generatedQualifiedName(packageName, className),
    className,
    modelCode: optionalString(route.modelCode) || stringValue(model.modelCode),
    description: optionalString(route.description),
    path: stringValue(route.path) || suggestedRoutePath(stringValue(model.modelCode)),
    aliasPaths: stringArray(route.aliasPaths),
    fetchPaths: stringArray(route.fetchPaths),
    excludePaths: stringArray(route.excludePaths),
    enabledOperations: stringArray(route.enabledOperations).length
      ? stringArray(route.enabledOperations)
      : ['PAGE', 'LIST_BY_CONDITION', 'SIMPLE_LIST', 'GET', 'CREATE', 'UPDATE', 'UPSERT', 'DELETE', 'DELETE_LIST'],
    tree: hasObjectProperties(route.tree) ? normalizeTree(route.tree) : null,
    excel: hasObjectProperties(route.excel) ? normalizeExcel(route.excel) : null,
    properties: arrayValue(route.properties).map(normalizeRouteProperty),
    queryFields: arrayValue(route.queryFields).map(normalizeRouteQueryField),
    defaultOrders: arrayValue(route.defaultOrders).map((value) => ({
      propertyName: stringValue(value.propertyName),
      direction: enumValue(value.direction, ['ASC', 'DESC'], 'ASC'),
    })),
    customOperations: arrayValue(route.customOperations).map(normalizeCustomOperation),
    dtoSchemas: arrayValue(route.dtoSchemas).map(normalizeNamedDtoSchema),
    agentExposure: normalizeAgentExposure(route.agentExposure),
  }
}

export function normalizeAgentExposure(value: JsonValue | undefined): LowcodeAgentExposureDraft {
  const operations = objectValue(objectValue(value).operations)
  return {
    operations: Object.fromEntries(Object.entries(operations)
      .filter(([operationCode]) => operationCode.trim())
      .map(([operationCode, rawExposure]) => [
        operationCode,
        {
          confirmation: enumValue(
            objectValue(rawExposure).confirmation,
            ['AUTO', 'REQUIRED'],
            'REQUIRED',
          ),
        },
      ])),
  }
}

export function normalizeCustomOperation(value: JsonObject): LowcodeCustomOperationDraft {
  const transport = enumValue(value.transport, ['HTTP', 'SSE', 'WEBSOCKET'], 'HTTP')
  return {
    operationCode: stringValue(value.operationCode),
    name: stringValue(value.name),
    description: optionalString(value.description),
    path: stringValue(value.path),
    method: enumValue(value.method, ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'], 'POST'),
    transport,
    implementation: enumValue<LowcodeOperationImplementation>(
      value.implementation,
      ['GENERATED', 'EXISTING_REST'],
      'GENERATED',
    ),
    authenticated: booleanValue(value.authenticated, true),
    permission: optionalString(value.permission),
    callContext: booleanValue(value.callContext, false),
    parameters: arrayValue(value.parameters).map(normalizeApiParameter),
    requestBody: hasObjectProperties(value.requestBody) ? normalizeApiBody(value.requestBody) : null,
    responseBody: hasObjectProperties(value.responseBody) ? normalizeApiBody(value.responseBody) : null,
    responseEnvelope: transport === 'HTTP' && booleanValue(value.responseEnvelope, true),
  }
}

function normalizeApiParameter(value: JsonObject): LowcodeApiParameterDraft {
  return {
    ...value,
    name: stringValue(value.name),
    location: enumValue(value.location, ['PATH', 'QUERY', 'HEADER', 'COOKIE'], 'QUERY'),
    required: booleanValue(value.required, false),
    description: optionalString(value.description),
    schema: normalizeApiSchema(value.schema),
  }
}

function normalizeApiBody(value: JsonValue): LowcodeApiBodyDraft {
  const body = objectValue(value)
  return {
    ...body,
    contentType: stringValue(body.contentType) || 'application/json',
    required: booleanValue(body.required, true),
    description: optionalString(body.description),
    schema: normalizeApiSchema(body.schema),
  }
}

export function normalizeApiSchema(value: JsonValue | undefined): LowcodeApiSchemaDraft {
  const schema = objectValue(value)
  return {
    ...schema,
    type: optionalString(schema.type),
    typeRef: normalizeDtoRef(schema.typeRef),
    format: optionalString(schema.format),
    description: optionalString(schema.description),
    properties: Object.fromEntries(
      Object.entries(objectValue(schema.properties)).map(([name, child]) => [name, normalizeApiSchema(child)]),
    ),
    required: stringArray(schema.required),
    items: hasObjectProperties(schema.items) ? normalizeApiSchema(schema.items) : null,
    enumValues: stringArray(schema.enumValues),
    oneOf: arrayValue(schema.oneOf).map(normalizeApiSchema),
  }
}

function normalizeNamedDtoSchema(value: JsonObject): LowcodeNamedDtoSchemaDraft {
  const ref = normalizeDtoRef(value.ref) ?? { modelCode: null, dtoCode: '' }
  return {
    ...value,
    ref,
    className: stringValue(value.className),
    properties: Object.fromEntries(
      Object.entries(objectValue(value.properties)).map(([name, child]) => [name, normalizeApiSchema(child)]),
    ),
    required: stringArray(value.required),
    description: optionalString(value.description),
  }
}

function normalizeEntityConfig(value: JsonValue | undefined): LowcodeEntityConfigDraft {
  const config = objectValue(value)
  const baseMode = enumValue(config.baseMode, ['DEFAULT', 'INHERITED'], 'DEFAULT')
  return {
    ...config,
    sourceMode: enumValue(config.sourceMode, ['GENERATED', 'EXISTING'], 'GENERATED'),
    sourceQualifiedName: optionalString(config.sourceQualifiedName),
    baseMode,
    baseModels: normalizeBaseModels(stringArray(config.baseModels), baseMode),
    superTypes: stringArray(config.superTypes),
    relationOrderings: Object.fromEntries(
      Object.entries(objectValue(config.relationOrderings))
        .map(([relationCode, properties]) => [relationCode, stringArray(properties)]),
    ),
    inheritedProperties: arrayValue(config.inheritedProperties).map((property) => ({
      ...property,
      name: stringValue(property.name),
      kotlinType: stringValue(property.kotlinType),
      dbColumn: stringValue(property.dbColumn),
      required: booleanValue(property.required, false),
      id: booleanValue(property.id, false),
      description: optionalString(property.description),
      defaultValue: optionalString(property.defaultValue),
    })),
    inheritedRelationCodes: stringArray(config.inheritedRelationCodes),
    formulaProperties: arrayValue(config.formulaProperties).map((property) => ({
      ...property,
      propertyCode: stringValue(property.propertyCode),
      label: stringValue(property.label),
      kotlinType: stringValue(property.kotlinType, 'String'),
      kind: enumValue(property.kind, ['KOTLIN', 'SQL'], 'KOTLIN'),
      expression: stringValue(property.expression),
      dependencies: stringArray(property.dependencies),
      nullable: booleanValue(property.nullable, false),
      description: optionalString(property.description),
    })),
    transientProperties: arrayValue(config.transientProperties).map((property) => ({
      ...property,
      propertyCode: stringValue(property.propertyCode),
      label: stringValue(property.label),
      kotlinType: stringValue(property.kotlinType, 'String'),
      kind: enumValue(property.kind, ['DRAFT', 'RESOLVER'], 'DRAFT'),
      resolverValueType: optionalString(property.resolverValueType),
      nullable: booleanValue(property.nullable, false),
      description: optionalString(property.description),
      dictionaryCode: optionalString(property.dictionaryCode),
    })),
    inheritanceRoot: hasObjectProperties(config.inheritanceRoot)
      ? {
          ...objectValue(config.inheritanceRoot),
          strategy: enumValue(
            objectValue(config.inheritanceRoot).strategy,
            ['SINGLE_TABLE', 'JOINED'],
            'JOINED',
          ),
          discriminatorField: stringValue(objectValue(config.inheritanceRoot).discriminatorField),
          instantiability: enumValue(
            objectValue(config.inheritanceRoot).instantiability,
            ['AUTO', 'ABSTRACT', 'INSTANTIABLE'],
            'ABSTRACT',
          ),
          discriminatorValue: optionalString(objectValue(config.inheritanceRoot).discriminatorValue),
          joinedTableDissociateAction: enumValue(
            objectValue(config.inheritanceRoot).joinedTableDissociateAction,
            ['DELETE', 'LAX'],
            'DELETE',
          ),
        }
      : null,
    inheritanceSubtype: hasObjectProperties(config.inheritanceSubtype)
      ? {
          ...objectValue(config.inheritanceSubtype),
          parentModelCode: stringValue(objectValue(config.inheritanceSubtype).parentModelCode),
          discriminatorValue: optionalString(objectValue(config.inheritanceSubtype).discriminatorValue),
          instantiability: enumValue(
            objectValue(config.inheritanceSubtype).instantiability,
            ['AUTO', 'ABSTRACT', 'INSTANTIABLE'],
            'AUTO',
          ),
        }
      : null,
    microServiceName: optionalString(config.microServiceName) ?? (Object.keys(config).length ? null : 'metadata-generated'),
  }
}

function normalizeDtoRef(value: JsonValue | undefined): LowcodeDtoRefDraft | null {
  const ref = objectValue(value)
  const modelCode = stringValue(ref.modelCode)
  const dtoCode = stringValue(ref.dtoCode)
  return modelCode || dtoCode ? { modelCode: modelCode || null, dtoCode } : null
}

function normalizeTree(value: JsonValue): LowcodeRouteTreeDraft {
  const tree = objectValue(value)
  return {
    ...tree,
    parentIdProperty: stringValue(tree.parentIdProperty),
    childrenProperty: stringValue(tree.childrenProperty),
    keywordProperty: stringValue(tree.keywordProperty),
    sortProperty: optionalString(tree.sortProperty),
  }
}

function normalizeExcel(value: JsonValue): LowcodeRouteExcelDraft {
  const excel = objectValue(value)
  return {
    ...excel,
    importEnabled: booleanValue(excel.importEnabled, false),
    exportEnabled: booleanValue(excel.exportEnabled, false),
    customImport: booleanValue(excel.customImport, false),
    customExport: booleanValue(excel.customExport, false),
    fileName: stringValue(excel.fileName),
    templateFileName: stringValue(excel.templateFileName),
    sheetName: stringValue(excel.sheetName) || '数据',
    templateSheetName: stringValue(excel.templateSheetName) || stringValue(excel.sheetName) || '数据',
    importColumns: arrayValue(excel.importColumns).map(normalizeRouteProperty),
    exportColumns: arrayValue(excel.exportColumns).map(normalizeRouteProperty),
  }
}

function normalizeRouteProperty(value: JsonObject): LowcodeRoutePropertyDraft {
  return {
    ...value,
    name: stringValue(value.name),
    type: stringValue(value.type) || 'string',
    format: optionalString(value.format),
    required: booleanValue(value.required, false),
    identifier: booleanValue(value.identifier, false),
    createWritable: booleanValue(value.createWritable, true),
    updateWritable: booleanValue(value.updateWritable, true),
    arrayItemType: optionalString(value.arrayItemType),
    description: optionalString(value.description),
    dictionaryCode: optionalString(value.dictionaryCode),
  }
}

function normalizeRouteQueryField(value: JsonObject): LowcodeRouteQueryFieldDraft {
  return {
    ...value,
    propertyName: stringValue(value.propertyName),
    parameterName: stringValue(value.parameterName),
    operator: stringValue(value.operator) || 'EQ',
    type: stringValue(value.type) || 'string',
    format: optionalString(value.format),
    endParameterName: optionalString(value.endParameterName),
    required: booleanValue(value.required, false),
    stateCases: arrayValue(value.stateCases),
    description: optionalString(value.description),
  }
}

function validateRelationMapping(relation: LowcodeRelationDraft, index: number, errors: string[]): void {
  const label = `关联 ${index + 1}`
  const ownsJoinColumn = relation.relationType === 'MANY_TO_ONE'
    || (relation.relationType === 'ONE_TO_ONE' && Boolean(relation.joinColumn?.trim()))
  if (relation.dissociateAction !== 'NONE' && !ownsJoinColumn) {
    errors.push(`${label} 只有持有 JoinColumn 时才能配置删除行为`)
  }
  if (relation.dissociateAction === 'SET_NULL' && relation.required) {
    errors.push(`${label} 使用置空外键时不能设为必填`)
  }
  if (relation.relationType === 'MANY_TO_ONE') {
    required(relation.joinColumn ?? '', `${label} JoinColumn`, errors)
    return
  }
  if (relation.relationType === 'ONE_TO_MANY') {
    required(relation.mappedBy ?? '', `${label} mappedBy`, errors)
    return
  }
  if (relation.relationType === 'ONE_TO_ONE') {
    if (Boolean(relation.joinColumn?.trim()) === Boolean(relation.mappedBy?.trim())) {
      errors.push(`${label} 必须且只能配置 JoinColumn 或 mappedBy`)
    }
    return
  }
  required(relation.joinTable ?? '', `${label} JoinTable`, errors)
}

export function toSnakeCase(value: string): string {
  return value
    .trim()
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/[\s.-]+/g, '_')
    .replace(/_+/g, '_')
    .toLowerCase()
}

export function toPascalCase(value: string): string {
  return value
    .trim()
    .split(/[\s._-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('')
}

function normalizeField(value: JsonObject, index: number): LowcodeFieldDraft {
  return {
    ...value,
    id: optionalIdentifier(value.id),
    orderNo: numberValue(value.orderNo, index + 1),
    fieldCode: stringValue(value.fieldCode),
    label: stringValue(value.label),
    kotlinType: stringValue(value.kotlinType, 'String'),
    dbColumn: stringValue(value.dbColumn),
    required: booleanValue(value.required, false),
    createWritable: booleanValue(value.createWritable, true),
    updateWritable: booleanValue(value.updateWritable, true),
    listVisible: booleanValue(value.listVisible, true),
    formVisible: booleanValue(value.formVisible, true),
    formControl: stringValue(value.formControl, 'input'),
    dictCode: optionalString(value.dictCode),
    enumStorage: optionalEnumValue(value.enumStorage, ['NAME', 'ORDINAL'] as const),
    defaultValue: optionalString(value.defaultValue),
    remark: optionalString(value.remark),
    serialized: booleanValue(value.serialized, false),
    key: booleanValue(value.key, false),
    maxLength: value.maxLength == null ? null : numberValue(value.maxLength, 0),
  }
}

function normalizeQuery(value: JsonObject, index: number): LowcodeQueryDraft {
  return {
    ...value,
    id: optionalIdentifier(value.id),
    orderNo: numberValue(value.orderNo, index + 1),
    queryCode: stringValue(value.queryCode),
    label: stringValue(value.label),
    logic: enumValue(value.logic, ['AND', 'OR'], 'AND'),
    items: arrayValue(value.items).map((item, itemIndex) => normalizeCondition(item, itemIndex)),
  }
}

function normalizeCondition(value: JsonObject, index: number): LowcodeQueryConditionDraft {
  return {
    ...value,
    id: optionalIdentifier(value.id),
    orderNo: numberValue(value.orderNo, index + 1),
    fieldCode: stringValue(value.fieldCode),
    operator: enumValue(
      value.operator,
      [
        'EQ',
        'NE',
        'LIKE',
        'STARTS_WITH',
        'ENDS_WITH',
        'GT',
        'GE',
        'LT',
        'LE',
        'IN',
        'NOT_IN',
        'BETWEEN',
        'TIME_RANGE',
        'NULL_STATE',
        'ZERO_STATE',
      ],
      'EQ',
    ),
    valueType: enumValue(value.valueType, ['SINGLE', 'RANGE', 'DATE_RANGE', 'DATETIME_RANGE', 'MULTIPLE'], 'SINGLE'),
    paramName: optionalString(value.paramName),
  }
}

function normalizeRelation(value: JsonObject, index: number): LowcodeRelationDraft {
  return {
    ...value,
    id: optionalIdentifier(value.id),
    orderNo: numberValue(value.orderNo, index + 1),
    relationCode: stringValue(value.relationCode),
    label: stringValue(value.label),
    relationType: enumValue(value.relationType, ['MANY_TO_ONE', 'ONE_TO_MANY', 'ONE_TO_ONE', 'MANY_TO_MANY'], 'MANY_TO_ONE'),
    targetModelId: optionalIdentifier(value.targetModelId),
    targetModelCode: optionalString(value.targetModelCode),
    joinColumn: optionalString(value.joinColumn),
    mappedBy: optionalString(value.mappedBy),
    joinTable: optionalString(value.joinTable),
    joinTableJoinColumn: optionalString(value.joinTableJoinColumn),
    joinTableInverseColumn: optionalString(value.joinTableInverseColumn),
    dissociateAction: enumValue(value.dissociateAction, ['NONE', 'LAX', 'CHECK', 'SET_NULL', 'DELETE'], 'NONE'),
    required: booleanValue(value.required, false),
    createWritable: booleanValue(value.createWritable, true),
    updateWritable: booleanValue(value.updateWritable, true),
    listVisible: booleanValue(value.listVisible, false),
    formVisible: booleanValue(value.formVisible, true),
  }
}

function arrayValue(value: JsonValue | undefined): JsonObject[] {
  if (!Array.isArray(value)) {
    return []
  }
  return value.filter(isJsonObject)
}

function stringArray(value: JsonValue | undefined): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

function objectValue(value: JsonValue | undefined): JsonObject {
  return value && isJsonObject(value) ? value : {}
}

function hasObjectProperties(value: JsonValue | undefined): value is JsonObject {
  return Boolean(value && isJsonObject(value) && Object.keys(value).length)
}

function stringValue(value: JsonValue | undefined, fallback = ''): string {
  return typeof value === 'string' ? value : fallback
}

function optionalString(value: JsonValue | undefined): string | null {
  return typeof value === 'string' ? value : null
}

function optionalIdentifier(value: JsonValue | undefined): number | string | undefined {
  return typeof value === 'number' || typeof value === 'string' ? value : undefined
}

function numberValue(value: JsonValue | undefined, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function booleanValue(value: JsonValue | undefined, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback
}

function enumValue<T extends string>(value: JsonValue | undefined, values: readonly T[], fallback: T): T {
  return typeof value === 'string' && values.includes(value as T) ? value as T : fallback
}

function optionalEnumValue<T extends string>(value: JsonValue | undefined, values: readonly T[]): T | null {
  return typeof value === 'string' && values.includes(value as T) ? value as T : null
}

function required(value: string | null | undefined, label: string, errors: string[]): void {
  if (!value?.trim()) {
    errors.push(`${label}不能为空`)
  }
}

function generatedQualifiedName(packageName: string, className: string): string {
  return [packageName, 'generated', className].filter(Boolean).join('.')
}

function replaceConventionValue(current: string, previousConvention: string, nextConvention: string): string {
  return !current || current === previousConvention ? nextConvention : current
}

function suggestedJoinColumn(relationCode: string): string {
  const base = toSnakeCase(relationCode.replace(/List$/, ''))
  return base ? `${base}_id` : ''
}

function suggestedRoutePath(modelCode: string): string {
  const path = toSnakeCase(modelCode).replaceAll('_', '-')
  return path ? `/${path}` : ''
}

function isJsonObject(value: JsonValue): value is JsonObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
