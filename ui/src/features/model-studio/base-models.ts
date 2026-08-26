import type {
  LowcodeBaseModel,
  LowcodeEntityBaseMode,
  LowcodeInheritedPropertyDraft,
} from '../../types'

const BASE_PROPERTY_DEFINITIONS: Record<string, ResolvedBaseModelProperty> = {
  id: property('id', 'Long', true),
  createTime: property('createTime', 'LocalDateTime', true),
  updateTime: property('updateTime', 'LocalDateTime'),
  updater: property('updater', 'AuditPrincipal'),
  creator: property('creator', 'AuditPrincipal'),
  tenantId: property('tenantId', 'Long'),
  namespace: property('namespace', 'String'),
  nodeType: property('nodeType', 'String', true),
  name: property('name', 'String'),
  description: property('description', 'String'),
  remark: property('remark', 'String'),
  sort: property('sort', 'Int'),
  status: property('status', 'Int', true),
  deleted: property('deleted', 'Int', true),
  deletedTime: property('deletedTime', 'LocalDateTime'),
  treeLevel: property('treeLevel', 'Int'),
  version: property('version', 'Int', true),
}

export const LOWCODE_BASE_MODEL_OPTIONS: ReadonlyArray<{
  value: LowcodeBaseModel
  label: string
  typeName: string
  properties: ReadonlyArray<ResolvedBaseModelProperty>
}> = [
  baseModel('BASE_ENTITY', '基础实体', 'BaseEntity', 'id', 'createTime', 'updateTime', 'updater', 'creator'),
  baseModel('SNOWFLAKE_ID', '雪花主键', 'BaseSnowflakeId', 'id'),
  baseModel('CREATE_TIME', '创建时间', 'BaseCreateTime', 'createTime'),
  baseModel('UPDATE_TIME', '更新时间', 'BaseUpdateTime', 'updateTime'),
  baseModel('AUDIT', '操作人', 'BaseAudit', 'updater', 'creator'),
  baseModel('TENANT', '租户', 'BaseTenant', 'tenantId'),
  baseModel('NAMESPACE', '命名空间', 'BaseNamespace', 'namespace'),
  baseModel('NODE', '关系节点', 'BaseNode', 'id', 'name', 'namespace', 'nodeType'),
  baseModel('NAMED', '名称', 'BaseNamed', 'name'),
  baseModel('DESCRIPTION', '描述', 'BaseDescription', 'description'),
  baseModel('REMARK', '备注', 'BaseRemark', 'remark'),
  baseModel('SORT', '排序', 'BaseSort', 'sort'),
  baseModel('STATUS', '状态', 'BaseStatus', 'status'),
  baseModel('DELETED', '逻辑删除标记', 'BaseDeleted', 'deleted'),
  baseModel('DELETED_TIME', '删除时间逻辑删除', 'BaseDeletedTime', 'deletedTime'),
  baseModel('TREE_LEVEL', '树层级', 'BaseTreeLevel', 'treeLevel'),
  baseModel('VERSION', '乐观锁', 'BaseVersion', 'version'),
]

const LOWCODE_BASE_MODEL_VALUES = new Set(LOWCODE_BASE_MODEL_OPTIONS.map((option) => option.value))
const BASE_ENTITY_COMPONENT_VALUES = new Set<LowcodeBaseModel>([
  'SNOWFLAKE_ID',
  'CREATE_TIME',
  'UPDATE_TIME',
  'AUDIT',
])
const NODE_COMPONENT_VALUES = new Set<LowcodeBaseModel>([
  'BASE_ENTITY',
  'SNOWFLAKE_ID',
  'NAMESPACE',
  'NAMED',
])

export function normalizeBaseModels(values: string[], baseMode: LowcodeEntityBaseMode): LowcodeBaseModel[] {
  const selected = Array.from(new Set(values.filter((value): value is LowcodeBaseModel =>
    LOWCODE_BASE_MODEL_VALUES.has(value as LowcodeBaseModel))))
  const withoutEntityComponents = selected.includes('BASE_ENTITY')
    ? selected.filter((value) => !BASE_ENTITY_COMPONENT_VALUES.has(value))
    : selected
  const normalized = withoutEntityComponents.includes('NODE')
    ? withoutEntityComponents.filter((value) => value === 'NODE' || !NODE_COMPONENT_VALUES.has(value))
    : withoutEntityComponents
  return normalized.length || baseMode === 'INHERITED' ? normalized : ['BASE_ENTITY']
}

export function toggleBaseModelSelection(
  values: LowcodeBaseModel[],
  model: LowcodeBaseModel,
  selected: boolean,
): LowcodeBaseModel[] {
  const mutuallyExclusive = model === 'DELETED'
    ? 'DELETED_TIME'
    : model === 'DELETED_TIME' ? 'DELETED' : null
  let next = values.filter((candidate) => candidate !== model && candidate !== mutuallyExclusive)
  if (!selected) return normalizeBaseModels(next, 'INHERITED')

  if (model === 'BASE_ENTITY') {
    next = next.filter((candidate) => !BASE_ENTITY_COMPONENT_VALUES.has(candidate))
  } else if (BASE_ENTITY_COMPONENT_VALUES.has(model)) {
    next = next.filter((candidate) => candidate !== 'BASE_ENTITY')
  }
  if (model === 'NODE') {
    next = next.filter((candidate) => !NODE_COMPONENT_VALUES.has(candidate))
  } else if (NODE_COMPONENT_VALUES.has(model)) {
    next = next.filter((candidate) => candidate !== 'NODE')
  }
  return [...next, model]
}

export function resolvedBaseModelProperties(
  config: {
    baseMode: LowcodeEntityBaseMode
    baseModels: LowcodeBaseModel[]
    inheritedProperties: Array<Pick<
      LowcodeInheritedPropertyDraft,
      'name' | 'description' | 'kotlinType' | 'required'
    >>
  },
): ResolvedBaseModelProperty[] {
  const models = normalizeBaseModels(config.baseModels, config.baseMode)
  return [
    ...models.flatMap((model) =>
      LOWCODE_BASE_MODEL_OPTIONS.find((option) => option.value === model)?.properties ?? []),
    ...config.inheritedProperties.map((property) => ({
      name: property.name,
      description: property.description ?? null,
      kotlinType: property.kotlinType,
      required: property.required,
    })),
  ]
}

export interface ResolvedBaseModelProperty {
  name: string
  description: string | null
  kotlinType: string
  required: boolean
}

function baseProperties(...names: string[]): ReadonlyArray<ResolvedBaseModelProperty> {
  return names.map((name) => BASE_PROPERTY_DEFINITIONS[name])
}

function baseModel(
  value: LowcodeBaseModel,
  label: string,
  typeName: string,
  ...propertyNames: string[]
) {
  return { value, label, typeName, properties: baseProperties(...propertyNames) }
}

function property(name: string, kotlinType: string, required = false): ResolvedBaseModelProperty {
  return { name, description: name, kotlinType, required }
}
