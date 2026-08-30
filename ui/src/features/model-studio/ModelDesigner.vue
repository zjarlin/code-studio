<script setup lang="ts">
import {
  ArrowDown,
  ArrowUp,
  Braces,
  Columns3,
  GitBranch,
  Link,
  Plus,
  Sparkles,
  Trash2,
} from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import EditableMetadataGrid from '@/components/composed/editable-metadata-grid/EditableMetadataGrid.vue'
import IconButton from '@/components/composed/icon-button/IconButton.vue'
import MetadataColumnAdjustDialog from '@/components/composed/metadata-table/MetadataColumnAdjustDialog.vue'
import MetadataTableCell from '@/components/composed/metadata-table/MetadataTableCell.vue'
import MetadataTableHead from '@/components/composed/metadata-table/MetadataTableHead.vue'
import { createTableRevision } from '@/components/composed/metadata-table/metadata-table'
import type { MetadataPatchApplication, MetadataTableDescriptor } from '@/components/composed/metadata-table/metadata-table'
import { Button } from '@/components/generated/shadcn/button'
import { Checkbox } from '@/components/generated/shadcn/checkbox'
import {
  TableBody,
  TableCell,
  TableHeader,
  TableRow,
} from '@/components/generated/shadcn/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/generated/shadcn/tabs'
import type {
  LowcodeDissociateAction,
  LowcodeFieldDraft,
  LowcodeEnumStorage,
  LowcodeModelDraft,
  LowcodeModelDesignerSection,
  LowcodeModelKind,
  LowcodeModelRecentChanges,
  LowcodeModelSummary,
  LowcodeQueryConditionDraft,
  LowcodeQueryDraft,
  LowcodeQueryLogic,
  LowcodeQueryOperator,
  LowcodeRelationDraft,
  LowcodeRelationKind,
} from '../../types'
import DtoDesigner from './DtoDesigner.vue'
import {
  applyFieldCode,
  applyModelTableName,
  applyQueryLogic,
  applyQueryOperator,
  applyRelationCode,
  applyRelationKind,
  createField,
  createQuery,
  createQueryCondition,
  createRelation,
  moveItem,
  queryValueType,
  queryableModelProperties,
  removeItem,
  modelFieldKey,
} from './model-draft'

const props = withDefaults(defineProps<{
  initialSection?: LowcodeModelDesignerSection
  modelValue: LowcodeModelDraft
  models: LowcodeModelSummary[]
  recentChanges?: LowcodeModelRecentChanges
  showIdentityConfiguration?: boolean
}>(), {
  recentChanges: () => ({ sections: [], fieldKeys: [] }),
  showIdentityConfiguration: true,
})

const emit = defineEmits<{
  change: []
  'update:modelValue': [value: LowcodeModelDraft]
}>()

const activeSection = ref<LowcodeModelDesignerSection>('model')

type FieldRow = LowcodeFieldDraft & Record<string, unknown>
type ConditionRow = LowcodeQueryConditionDraft & Record<string, unknown>
type RelationRow = LowcodeRelationDraft & Record<string, unknown> & {
  primaryMapping: string | null
  localMapping: string | null
  inverseMapping: string | null
}

const fieldTableDescriptor = computed<MetadataTableDescriptor<FieldRow>>(() => {
  const descriptor: Omit<MetadataTableDescriptor<FieldRow>, 'revision'> = {
    tableId: `model.fields:${props.modelValue.modelCode || 'new'}`,
    rowIdentityKey: 'id|fieldCode',
    rowKey: (field) => field.id != null ? `id:${field.id}` : `code:${field.fieldCode}`,
    columns: [
      { key: 'label', label: '注释', kind: 'scalar', editable: true, context: true },
      { key: 'fieldCode', label: '属性名', kind: 'scalar', editable: true, context: true },
      { key: 'kotlinType', label: 'Kotlin 类型', kind: 'scalar', editable: true, context: true },
      { key: 'dbColumn', label: '数据库列', kind: 'scalar', editable: true, context: true },
      {
        key: 'formControl', label: '表单控件', kind: 'enum', editable: true, context: true,
        options: formControls.map((value) => ({ label: value, value })),
      },
      { key: 'dictCode', label: '字典', kind: 'scalar', editable: true, context: true },
      {
        key: 'enumStorage', label: '枚举存储', kind: 'enum', editable: true, context: true,
        options: enumStorageOptions.map((option) => ({ label: option.label, value: option.value || null })),
      },
      { key: 'defaultValue', label: '默认值', kind: 'scalar', editable: true, context: true },
      { key: 'remark', label: '备注', kind: 'scalar', editable: true, context: true },
      { key: 'required', label: '必填', kind: 'boolean', editable: true, context: true },
      { key: 'createWritable', label: '新增可写', kind: 'boolean', editable: true, context: true },
      { key: 'updateWritable', label: '修改可写', kind: 'boolean', editable: true, context: true },
      { key: 'key', label: '自然键', kind: 'boolean', editable: true, context: true },
      { key: 'serialized', label: 'JSON', kind: 'boolean', editable: true, context: true },
      { key: 'listVisible', label: '列表', kind: 'boolean', editable: true, context: true },
      { key: 'formVisible', label: '表单', kind: 'boolean', editable: true, context: true },
    ],
    operations: ['translate', 'replace', 'fill', 'custom'],
  }
  return { ...descriptor, revision: createTableRevision(descriptor, props.modelValue.fields as FieldRow[]) }
})
const relationRows = computed<RelationRow[]>(() => props.modelValue.relations.map(toRelationRow))
const relationTableDescriptor = computed<MetadataTableDescriptor<RelationRow>>(() => {
  const descriptor: Omit<MetadataTableDescriptor<RelationRow>, 'revision'> = {
    tableId: `model.relations:${props.modelValue.modelCode || 'new'}`,
    rowIdentityKey: 'id|relationCode',
    rowKey: (relation) => relation.id != null ? `id:${relation.id}` : `code:${relation.relationCode}`,
    columns: [
      { key: 'label', label: '注释', kind: 'scalar', editable: true, context: true },
      { key: 'relationCode', label: '属性名', kind: 'scalar', editable: true, context: true },
      {
        key: 'relationType', label: '类型', kind: 'enum', editable: true, context: true,
        options: relationKinds.map((option) => ({ label: option.label, value: option.value })),
      },
      {
        key: 'targetModelCode', label: '目标模型', kind: 'enum', editable: true, context: true,
        options: props.models.map((model) => ({ label: model.name, value: model.modelCode })),
      },
      { key: 'primaryMapping', label: 'JoinColumn / mappedBy', kind: 'scalar', editable: true, context: true },
      { key: 'localMapping', label: 'JoinTable / 本端列', kind: 'scalar', editable: true, context: true },
      { key: 'inverseMapping', label: '目标端列', kind: 'scalar', editable: true, context: true },
      {
        key: 'dissociateAction', label: '删除行为', kind: 'enum', editable: true, context: true,
        options: dissociateActions.map((option) => ({ label: option.label, value: option.value })),
      },
      { key: 'required', label: '必填', kind: 'boolean', editable: true, context: true },
      { key: 'createWritable', label: '新增可写', kind: 'boolean', editable: true, context: true },
      { key: 'updateWritable', label: '修改可写', kind: 'boolean', editable: true, context: true },
      { key: 'listVisible', label: '列表', kind: 'boolean', editable: true, context: true },
      { key: 'formVisible', label: '表单', kind: 'boolean', editable: true, context: true },
    ],
    operations: ['translate', 'replace', 'fill', 'custom'],
  }
  return { ...descriptor, revision: createTableRevision(descriptor, relationRows.value) }
})

watch(() => props.initialSection, (section) => {
  if (section) {
    activeSection.value = section === 'api' ? 'model' : section
  }
}, { immediate: true })

const fieldTypes = [
  'String',
  'Long',
  'Int',
  'Double',
  'BigDecimal',
  'Boolean',
  'LocalDate',
  'LocalDateTime',
  'UUID',
]
const formControls = ['input', 'textarea', 'select', 'radio', 'checkbox', 'switch', 'date', 'datetime', 'number']
const queryOperators: Array<{ value: LowcodeQueryOperator, label: string }> = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'LIKE', label: '包含' },
  { value: 'STARTS_WITH', label: '前缀匹配' },
  { value: 'ENDS_WITH', label: '后缀匹配' },
  { value: 'GT', label: '大于' },
  { value: 'GE', label: '大于等于' },
  { value: 'LT', label: '小于' },
  { value: 'LE', label: '小于等于' },
  { value: 'IN', label: '多值匹配' },
  { value: 'NOT_IN', label: '排除多值' },
  { value: 'BETWEEN', label: '双参数区间' },
  { value: 'TIME_RANGE', label: '时间范围数组' },
  { value: 'NULL_STATE', label: '空值状态' },
  { value: 'ZERO_STATE', label: '零值状态' },
]
const relationKinds: Array<{ value: LowcodeRelationKind, label: string }> = [
  { value: 'MANY_TO_ONE', label: '多对一' },
  { value: 'ONE_TO_MANY', label: '一对多' },
  { value: 'ONE_TO_ONE', label: '一对一' },
  { value: 'MANY_TO_MANY', label: '多对多' },
]
const dissociateActions: Array<{ value: LowcodeDissociateAction, label: string }> = [
  { value: 'NONE', label: '不处理' },
  { value: 'CHECK', label: '拒绝删除' },
  { value: 'SET_NULL', label: '置空外键' },
  { value: 'DELETE', label: '级联删除' },
  { value: 'LAX', label: '宽松处理' },
]
const enumStorageOptions = [
  { value: '', label: '不适用' },
  { value: 'NAME', label: '按名称' },
  { value: 'ORDINAL', label: '按序号' },
]
const fieldOptions = computed(() => queryableModelProperties(props.modelValue, props.models))
const recentSections = computed(() => new Set(props.recentChanges.sections))
const recentFieldKeys = computed(() => new Set(props.recentChanges.fieldKeys))

function sectionRecentlyChanged(section: LowcodeModelDesignerSection): boolean {
  return recentSections.value.has(section)
}

function fieldRecentlyChanged(field: LowcodeFieldDraft, index: number): boolean {
  return recentFieldKeys.value.has(modelFieldKey(field, index))
}

function updateModel(patch: Partial<LowcodeModelDraft>): void {
  emit('update:modelValue', { ...props.modelValue, ...patch })
  emit('change')
}

function adoptBaseModel(entityConfig: LowcodeModelDraft['entityConfig'], propertyNames: string[]): void {
  const inheritedNames = new Set(propertyNames)
  const fields = props.modelValue.fields.filter((field) => !inheritedNames.has(field.fieldCode))
  updateModel({ entityConfig, fields })
}

function updateModelType(value: string): void {
  updateModel({ modelType: value as LowcodeModelKind })
}

function updateIdentity(patch: Pick<Partial<LowcodeModelDraft>, 'packageName'>): void {
  const packageName = patch.packageName ?? props.modelValue.packageName
  updateModel({
    ...patch,
    routeConfig: {
      ...props.modelValue.routeConfig,
      packageName,
      qualifiedName: [packageName, 'generated', props.modelValue.className].filter(Boolean).join('.'),
    },
  })
}

function updateTableName(tableName: string): void {
  emit('update:modelValue', applyModelTableName(props.modelValue, tableName))
  emit('change')
}

function addField(): void {
  updateModel({ fields: [...props.modelValue.fields, createField(props.modelValue.fields.length)] })
  activeSection.value = 'fields'
}

function patchField(index: number, patch: Partial<LowcodeFieldDraft>): void {
  const fields = props.modelValue.fields.map((field, fieldIndex) => fieldIndex === index ? { ...field, ...patch } : field)
  updateModel({ fields })
}

function updateFieldCode(index: number, value: string): void {
  const previousCode = props.modelValue.fields[index].fieldCode
  const fields = props.modelValue.fields.map((field, fieldIndex) => fieldIndex === index ? applyFieldCode(field, value) : field)
  const queries = props.modelValue.queries.map((query) => ({
    ...query,
    items: query.items.map((condition) => condition.fieldCode === previousCode
      ? { ...condition, fieldCode: value }
      : condition),
  }))
  updateModel({ fields, queries })
}

function updateFieldType(index: number, kotlinType: string): void {
  const fieldCode = props.modelValue.fields[index].fieldCode
  const fields = props.modelValue.fields.map((field, fieldIndex) => fieldIndex === index
    ? { ...field, kotlinType }
    : field)
  const queries = props.modelValue.queries.map((query) => ({
    ...query,
    items: query.items.map((condition) => condition.fieldCode === fieldCode
      ? { ...condition, valueType: queryValueType(condition.operator, kotlinType) }
      : condition),
  }))
  updateModel({ fields, queries })
}

function updateFieldEnumStorage(index: number, value: string): void {
  const enumStorage: LowcodeEnumStorage | null = value === 'NAME' || value === 'ORDINAL' ? value : null
  patchField(index, { enumStorage })
}

function moveField(from: number, to: number): void {
  updateModel({ fields: moveItem(props.modelValue.fields, from, to) })
}

function deleteField(index: number): void {
  updateModel({ fields: removeItem(props.modelValue.fields, index) })
}

function addQuery(): void {
  const firstField = fieldOptions.value[0]?.code ?? ''
  updateModel({ queries: [...props.modelValue.queries, createQuery(props.modelValue.queries.length, firstField)] })
  activeSection.value = 'queries'
}

function patchQuery(index: number, patch: Partial<LowcodeQueryDraft>): void {
  const queries = props.modelValue.queries.map((query, queryIndex) => queryIndex === index ? { ...query, ...patch } : query)
  updateModel({ queries })
}

function updateQueryLogic(index: number, logic: LowcodeQueryLogic): void {
  const queries = props.modelValue.queries.map((query, queryIndex) => queryIndex === index
    ? applyQueryLogic(query, logic)
    : query)
  updateModel({ queries })
}

function addCondition(queryIndex: number): void {
  const query = props.modelValue.queries[queryIndex]
  const firstField = fieldOptions.value[0]?.code ?? ''
  let condition = createQueryCondition(query.items.length, firstField)
  if (query.logic === 'OR') {
    condition = { ...condition, operator: 'LIKE', paramName: 'keyword' }
  }
  patchQuery(queryIndex, { items: [...query.items, condition] })
}

function patchCondition(
  queryIndex: number,
  conditionIndex: number,
  patch: Partial<LowcodeQueryConditionDraft>,
): void {
  const query = props.modelValue.queries[queryIndex]
  const items = query.items.map((item, itemIndex) => itemIndex === conditionIndex ? { ...item, ...patch } : item)
  patchQuery(queryIndex, { items })
}

function updateConditionField(queryIndex: number, conditionIndex: number, fieldCode: string): void {
  const query = props.modelValue.queries[queryIndex]
  const condition = query.items[conditionIndex]
  const field = fieldOptions.value.find((candidate) => candidate.code === fieldCode)
  const updated = applyQueryOperator({ ...condition, fieldCode }, condition.operator, field?.kotlinType ?? '')
  patchCondition(queryIndex, conditionIndex, updated)
}

function updateConditionOperator(
  queryIndex: number,
  conditionIndex: number,
  value: string,
): void {
  const operator = value as LowcodeQueryOperator
  const condition = props.modelValue.queries[queryIndex].items[conditionIndex]
  const field = fieldOptions.value.find((candidate) => candidate.code === condition.fieldCode)
  patchCondition(queryIndex, conditionIndex, applyQueryOperator(condition, operator, field?.kotlinType ?? ''))
}

function moveCondition(queryIndex: number, from: number, to: number): void {
  const query = props.modelValue.queries[queryIndex]
  patchQuery(queryIndex, { items: moveItem(query.items, from, to) })
}

function deleteCondition(queryIndex: number, conditionIndex: number): void {
  const query = props.modelValue.queries[queryIndex]
  patchQuery(queryIndex, { items: removeItem(query.items, conditionIndex) })
}

function moveQuery(from: number, to: number): void {
  updateModel({ queries: moveItem(props.modelValue.queries, from, to) })
}

function deleteQuery(index: number): void {
  updateModel({ queries: removeItem(props.modelValue.queries, index) })
}

function addRelation(): void {
  updateModel({ relations: [...props.modelValue.relations, createRelation(props.modelValue.relations.length)] })
  activeSection.value = 'relations'
}

function patchRelation(index: number, patch: Partial<LowcodeRelationDraft>): void {
  const relations = props.modelValue.relations.map((relation, relationIndex) => relationIndex === index
    ? { ...relation, ...patch }
    : relation)
  updateModel({ relations })
}

function updateRelationCode(index: number, value: string): void {
  const relations = props.modelValue.relations.map((relation, relationIndex) => relationIndex === index
    ? applyRelationCode(relation, value)
    : relation)
  updateModel({ relations })
}

function updateRelationKind(index: number, value: string): void {
  const relationType = value as LowcodeRelationKind
  const relations = props.modelValue.relations.map((relation, relationIndex) => relationIndex === index
    ? applyRelationKind(relation, relationType)
    : relation)
  updateModel({ relations })
}

function updateRelationTarget(index: number, value: string): void {
  const target = props.models.find((model) => String(model.id) === value)
  patchRelation(index, {
    targetModelId: target?.id ?? null,
    targetModelCode: target?.modelCode ?? null,
  })
}

function relationTargetValue(relation: LowcodeRelationDraft): string {
  const target = props.models.find((model) => relation.targetModelId != null
    ? String(model.id) === String(relation.targetModelId)
    : model.modelCode === relation.targetModelCode)
  return target == null ? '' : String(target.id)
}

function relationOwnsJoinColumn(relation: LowcodeRelationDraft): boolean {
  return relation.relationType === 'MANY_TO_ONE'
    || (relation.relationType === 'ONE_TO_ONE' && Boolean(relation.joinColumn))
}

function relationKindClass(relationType: LowcodeRelationKind): string {
  return `relation-kind-${relationType.toLowerCase().replaceAll('_', '-')}`
}

function updateDissociateAction(index: number, value: string): void {
  patchRelation(index, { dissociateAction: value as LowcodeDissociateAction })
}

function moveRelation(from: number, to: number): void {
  updateModel({ relations: moveItem(props.modelValue.relations, from, to) })
}

function deleteRelation(index: number): void {
  updateModel({ relations: removeItem(props.modelValue.relations, index) })
}

function applyFieldPatches(application: MetadataPatchApplication<FieldRow>): void {
  const columnsByRow = appliedColumnsByRow(application)
  const patchedRows = application.rows as FieldRow[]
  const fields = props.modelValue.fields.map((field, index) => {
    const rowKey = fieldTableDescriptor.value.rowKey(field as FieldRow)
    const columns = columnsByRow.get(rowKey)
    if (!columns) return field
    let next = field
    for (const columnKey of columns) {
      next = columnKey === 'fieldCode'
        ? applyFieldCode(next, String(patchedRows[index].fieldCode))
        : { ...next, [columnKey]: patchedRows[index][columnKey] }
    }
    return next
  })
  if (![...columnsByRow.values()].some((columns) => columns.has('fieldCode') || columns.has('kotlinType'))) {
    updateModel({ fields })
    return
  }
  const renamedFields = new Map(props.modelValue.fields.map((field, index) => [field.fieldCode, fields[index].fieldCode]))
  const types = new Map(fields.map((field) => [field.fieldCode, field.kotlinType]))
  const queries = props.modelValue.queries.map((query) => ({
    ...query,
    items: query.items.map((condition) => {
      const fieldCode = renamedFields.get(condition.fieldCode) ?? condition.fieldCode
      return applyQueryOperator(
        { ...condition, fieldCode },
        condition.operator,
        types.get(fieldCode) ?? '',
      )
    }),
  }))
  updateModel({ fields, queries })
}

function conditionTableDescriptor(query: LowcodeQueryDraft): MetadataTableDescriptor<ConditionRow> {
  const descriptor: Omit<MetadataTableDescriptor<ConditionRow>, 'revision'> = {
    tableId: `model.query.conditions:${props.modelValue.modelCode || 'new'}:${query.queryCode || query.orderNo}`,
    rowIdentityKey: 'id|orderNo',
    rowKey: (condition) => condition.id != null ? `id:${condition.id}` : `order:${condition.orderNo}`,
    columns: [
      {
        key: 'fieldCode', label: '字段', kind: 'enum', editable: true, context: true,
        options: fieldOptions.value.map((field) => ({ label: field.label, value: field.code })),
      },
      {
        key: 'operator', label: '操作符', kind: 'enum', editable: query.logic !== 'OR', context: true,
        options: queryOperators.map((operator) => ({ label: operator.label, value: operator.value })),
      },
      { key: 'paramName', label: '参数名', kind: 'scalar', editable: query.logic !== 'OR', context: true },
      { key: 'valueType', label: '值类型', kind: 'enum', context: true },
    ],
    operations: ['replace', 'fill', 'custom'],
  }
  return { ...descriptor, revision: createTableRevision(descriptor, query.items as ConditionRow[]) }
}

function applyConditionPatches(queryIndex: number, application: MetadataPatchApplication<ConditionRow>): void {
  const query = props.modelValue.queries[queryIndex]
  const items = (application.rows as LowcodeQueryConditionDraft[]).map((condition) => {
    if (query.logic === 'OR') {
      return { ...condition, operator: 'LIKE' as const, valueType: 'SINGLE' as const, paramName: 'keyword' }
    }
    const field = fieldOptions.value.find((candidate) => candidate.code === condition.fieldCode)
    return applyQueryOperator(condition, condition.operator, field?.kotlinType ?? '')
  })
  patchQuery(queryIndex, { items })
}

function applyRelationPatches(application: MetadataPatchApplication<RelationRow>): void {
  const columnsByRow = appliedColumnsByRow(application)
  const patchedRows = application.rows as RelationRow[]
  const relations = props.modelValue.relations.map((relation, index) => {
    const rowKey = relationTableDescriptor.value.rowKey(relation as RelationRow)
    const columns = columnsByRow.get(rowKey)
    if (!columns) return relation
    let next = relation
    for (const columnKey of columns) {
      next = applyRelationColumn(next, patchedRows[index], columnKey)
    }
    return next
  })
  updateModel({ relations })
}

function appliedColumnsByRow<Row>(application: MetadataPatchApplication<Row>): Map<string, Set<string>> {
  const result = new Map<string, Set<string>>()
  for (const patch of application.applied) {
    const columns = result.get(patch.rowKey) ?? new Set<string>()
    columns.add(patch.columnKey)
    result.set(patch.rowKey, columns)
  }
  return result
}

function toRelationRow(relation: LowcodeRelationDraft): RelationRow {
  return {
    ...relation,
    primaryMapping: relation.relationType === 'ONE_TO_MANY'
      ? relation.mappedBy ?? null
      : relation.relationType === 'MANY_TO_MANY'
        ? relation.joinTable ?? null
        : relation.joinColumn ?? null,
    localMapping: relation.relationType === 'ONE_TO_ONE'
      ? relation.mappedBy ?? null
      : relation.relationType === 'MANY_TO_MANY'
        ? relation.joinTableJoinColumn ?? null
        : null,
    inverseMapping: relation.relationType === 'MANY_TO_MANY'
      ? relation.joinTableInverseColumn ?? null
      : null,
  }
}

function applyRelationColumn(
  relation: LowcodeRelationDraft,
  row: RelationRow,
  columnKey: string,
): LowcodeRelationDraft {
  if (columnKey === 'relationCode') return applyRelationCode(relation, row.relationCode)
  if (columnKey === 'relationType') return applyRelationKind(relation, row.relationType)
  if (columnKey === 'targetModelCode') {
    const target = props.models.find((model) => model.modelCode === row.targetModelCode)
    return { ...relation, targetModelId: target?.id ?? null, targetModelCode: target?.modelCode ?? null }
  }
  if (columnKey === 'primaryMapping') return applyPrimaryRelationMapping(relation, nullableText(row.primaryMapping))
  if (columnKey === 'localMapping') return applyLocalRelationMapping(relation, nullableText(row.localMapping))
  if (columnKey === 'inverseMapping') {
    return relation.relationType === 'MANY_TO_MANY'
      ? { ...relation, joinTableInverseColumn: nullableText(row.inverseMapping) }
      : relation
  }
  if (['label', 'dissociateAction', 'required', 'createWritable', 'updateWritable', 'listVisible', 'formVisible'].includes(columnKey)) {
    return { ...relation, [columnKey]: row[columnKey] }
  }
  return relation
}

function applyPrimaryRelationMapping(relation: LowcodeRelationDraft, value: string | null): LowcodeRelationDraft {
  if (relation.relationType === 'ONE_TO_MANY') return { ...relation, mappedBy: value }
  if (relation.relationType === 'MANY_TO_MANY') return { ...relation, joinTable: value }
  return {
    ...relation,
    joinColumn: value,
    mappedBy: relation.relationType === 'ONE_TO_ONE' && value ? null : relation.mappedBy,
    dissociateAction: value ? relation.dissociateAction : 'NONE',
  }
}

function applyLocalRelationMapping(relation: LowcodeRelationDraft, value: string | null): LowcodeRelationDraft {
  if (relation.relationType === 'MANY_TO_MANY') return { ...relation, joinTableJoinColumn: value }
  if (relation.relationType !== 'ONE_TO_ONE') return relation
  return {
    ...relation,
    mappedBy: value,
    joinColumn: value ? null : relation.joinColumn,
    dissociateAction: value ? 'NONE' : relation.dissociateAction,
  }
}

function nullableText(value: unknown): string | null {
  return typeof value === 'string' && value ? value : null
}

function relationColumnEnabled(relation: RelationRow, columnKey: string): boolean {
  if (columnKey === 'targetModelCode') return props.models.length > 0
  if (columnKey === 'localMapping') return relation.relationType === 'ONE_TO_ONE' || relation.relationType === 'MANY_TO_MANY'
  if (columnKey === 'inverseMapping') return relation.relationType === 'MANY_TO_MANY'
  if (columnKey === 'dissociateAction') return relationOwnsJoinColumn(relation)
  return true
}

function relationColumnRowKeys(columnKey: string): string[] {
  return relationRows.value
    .filter((relation) => relationColumnEnabled(relation, columnKey))
    .map(relationTableDescriptor.value.rowKey)
}

function booleanColumnClass(columnKey: string): string | undefined {
  return ['required', 'createWritable', 'updateWritable', 'key', 'serialized', 'listVisible', 'formVisible'].includes(columnKey)
    ? 'grid-boolean-column'
    : undefined
}

function textValue(event: Event): string {
  return (event.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value
}

function numberInputValue(event: Event): number {
  const value = Number((event.target as HTMLInputElement).value)
  return Number.isFinite(value) ? value : 1
}

function checkedValue(event: Event): boolean {
  return (event.target as HTMLInputElement).checked
}

function conditionValueType(condition: LowcodeQueryConditionDraft): string {
  const field = props.modelValue.fields.find((candidate) => candidate.fieldCode === condition.fieldCode)
  return queryValueType(condition.operator, field?.kotlinType ?? '')
}
</script>

<template>
  <Tabs v-model="activeSection" class="model-designer" orientation="horizontal">
    <TabsList class="designer-nav" aria-label="模型配置">
      <TabsTrigger value="model" :data-recently-changed="sectionRecentlyChanged('model') || undefined">
        <Braces />
        <span>模型</span>
      </TabsTrigger>
      <TabsTrigger value="fields" :data-recently-changed="sectionRecentlyChanged('fields') || undefined">
        <Columns3 />
        <span>属性</span>
        <small>{{ modelValue.fields.length }}</small>
      </TabsTrigger>
      <TabsTrigger value="queries" :data-recently-changed="sectionRecentlyChanged('queries') || undefined">
        <GitBranch />
        <span>查询</span>
        <small>{{ modelValue.queries.length }}</small>
      </TabsTrigger>
      <TabsTrigger value="relations" :data-recently-changed="sectionRecentlyChanged('relations') || undefined">
        <Link />
        <span>关联</span>
        <small>{{ modelValue.relations.length }}</small>
      </TabsTrigger>
    </TabsList>

    <div class="designer-content">
      <TabsContent value="model" class="designer-section">
        <header class="designer-section-heading">
          <div>
            <h2 id="model-section-title">{{ showIdentityConfiguration ? '模型定义' : '实体高级设置' }}</h2>
            <span>{{ modelValue.id ? `ID ${modelValue.id}` : '尚未保存' }}</span>
          </div>
        </header>

        <div v-if="showIdentityConfiguration" class="form-grid model-form-grid">
          <label class="form-field">
            <span>模型注释 <b>*</b></span>
            <input :value="modelValue.name" autocomplete="off" @input="updateModel({ name: textValue($event) })">
          </label>
          <label class="form-field">
            <span>Kotlin 文件名</span>
            <input class="bg-muted/40 text-muted-foreground" :value="modelValue.className ? `${modelValue.className}.kt` : ''" readonly>
          </label>
          <label class="form-field">
            <span>数据库表名 <b>*</b></span>
            <input
              :value="modelValue.tableName"
              autocomplete="off"
              placeholder="例如 biz_job"
              @input="updateTableName(textValue($event))">
          </label>
          <label class="form-field form-field-wide">
            <span>业务包名 <b>*</b></span>
            <input :value="modelValue.packageName" autocomplete="off" placeholder="com.example.application.work" @input="updateIdentity({ packageName: textValue($event) })">
          </label>
          <label class="form-field">
            <span>模型类型</span>
            <select :value="modelValue.modelType" @change="updateModelType(textValue($event))">
              <option value="ENTITY">实体</option>
              <option value="MAPPED_SUPERCLASS">映射父类</option>
              <option value="EMBEDDABLE">嵌入类型</option>
            </select>
          </label>
          <label class="form-field">
            <span>Contributor ID <b>*</b></span>
            <input
              :value="modelValue.contributorId ?? ''"
              autocomplete="off"
              placeholder="example.catalog"
              @input="updateModel({ contributorId: textValue($event) || null })">
          </label>
          <label class="form-field">
            <span>版本</span>
            <input :value="modelValue.version" min="1" type="number" @input="updateModel({ version: numberInputValue($event) })">
          </label>
          <label class="switch-field">
            <input :checked="modelValue.status === 1" type="checkbox" @change="updateModel({ status: checkedValue($event) ? 1 : 0 })">
            <span>
              <strong>启用模型</strong>
              <small>{{ modelValue.status === 1 ? '参与生成' : '已停用' }}</small>
            </span>
          </label>
          <label class="form-field form-field-wide">
            <span>备注</span>
            <textarea :value="modelValue.remark ?? ''" rows="3" @input="updateModel({ remark: textValue($event) || null })" />
          </label>
        </div>

        <DtoDesigner
          :model-code="modelValue.modelCode"
          :contributor-id="modelValue.contributorId"
          :entity-config="modelValue.entityConfig"
          :fields="modelValue.fields"
          :models="models"
          :relations="modelValue.relations"
          @adopt-base-model="adoptBaseModel"
          @update:entity-config="updateModel({ entityConfig: $event })"
        />
      </TabsContent>

      <TabsContent value="fields" class="designer-section">
        <EditableMetadataGrid
          :columns="18"
          :count="modelValue.fields.length"
          empty-text="尚未配置字段"
          :min-width="1400"
          title="数据字段"
        >
          <template #actions><Button size="sm" @click="addField"><Plus />添加字段</Button></template>
          <template #header>
            <TableHeader>
              <TableRow>
                <MetadataTableHead class="grid-order-column" mode="system">#</MetadataTableHead>
                <MetadataTableHead
                  v-for="column in fieldTableDescriptor.columns"
                  :key="String(column.key)"
                  :class="booleanColumnClass(String(column.key))"
                  mode="agent"
                  :title="column.key === 'label' ? '同时用于 Kotlin KDoc、数据库列注释和界面展示' : undefined">
                  {{ column.label }}
                  <template #action><MetadataColumnAdjustDialog :column-key="String(column.key)" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches" /></template>
                </MetadataTableHead>
                <MetadataTableHead class="grid-actions-column" mode="system">操作</MetadataTableHead>
              </TableRow>
            </TableHeader>
          </template>
          <template #body>
            <TableBody>
              <TableRow
                v-for="(field, index) in modelValue.fields"
                :key="field.id ?? `field-${index}`"
                :data-recently-changed="fieldRecentlyChanged(field, index) || undefined"
                :class="{ 'metadata-row-recently-changed': fieldRecentlyChanged(field, index) }">
                <TableCell class="grid-order-column">
                  <span>{{ index + 1 }}</span>
                  <span v-if="fieldRecentlyChanged(field, index)" class="recent-change-badge" title="AI 刚刚修改"><Sparkles />AI</span>
                </TableCell>
                <MetadataTableCell column-key="label" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><input :value="field.label" autocomplete="off" placeholder="注释" @input="patchField(index, { label: textValue($event) })"></MetadataTableCell>
                <MetadataTableCell column-key="fieldCode" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><input :value="field.fieldCode" autocomplete="off" placeholder="propertyName" @input="updateFieldCode(index, textValue($event))"></MetadataTableCell>
                <MetadataTableCell column-key="kotlinType" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><input :value="field.kotlinType" list="kotlin-field-types" autocomplete="off" @input="updateFieldType(index, textValue($event))"></MetadataTableCell>
                <MetadataTableCell column-key="dbColumn" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><input :value="field.dbColumn" autocomplete="off" @input="patchField(index, { dbColumn: textValue($event) })"></MetadataTableCell>
                <MetadataTableCell column-key="formControl" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><select :value="field.formControl" @change="patchField(index, { formControl: textValue($event) })"><option v-for="control in formControls" :key="control" :value="control">{{ control }}</option></select></MetadataTableCell>
                <MetadataTableCell column-key="dictCode" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><input :value="field.dictCode ?? ''" autocomplete="off" placeholder="-" @input="patchField(index, { dictCode: textValue($event) || null })"></MetadataTableCell>
                <MetadataTableCell column-key="enumStorage" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><select :value="field.enumStorage ?? ''" @change="updateFieldEnumStorage(index, textValue($event))"><option v-for="option in enumStorageOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></MetadataTableCell>
                <MetadataTableCell column-key="defaultValue" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><input :value="field.defaultValue ?? ''" autocomplete="off" placeholder="-" @input="patchField(index, { defaultValue: textValue($event) || null })"></MetadataTableCell>
                <MetadataTableCell column-key="remark" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><input :value="field.remark ?? ''" autocomplete="off" placeholder="-" @input="patchField(index, { remark: textValue($event) || null })"></MetadataTableCell>
                <MetadataTableCell class="grid-boolean-column" column-key="required" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><Checkbox :model-value="field.required" aria-label="必填" @update:model-value="patchField(index, { required: $event === true })" /></MetadataTableCell>
                <MetadataTableCell class="grid-boolean-column" column-key="createWritable" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><Checkbox :model-value="field.createWritable" aria-label="字段新增可写" @update:model-value="patchField(index, { createWritable: $event === true })" /></MetadataTableCell>
                <MetadataTableCell class="grid-boolean-column" column-key="updateWritable" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><Checkbox :model-value="field.updateWritable" aria-label="字段修改可写" @update:model-value="patchField(index, { updateWritable: $event === true })" /></MetadataTableCell>
                <MetadataTableCell class="grid-boolean-column" column-key="key" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><Checkbox :model-value="field.key" aria-label="自然键" @update:model-value="patchField(index, { key: $event === true })" /></MetadataTableCell>
                <MetadataTableCell class="grid-boolean-column" column-key="serialized" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><Checkbox :model-value="field.serialized" aria-label="JSON 序列化" @update:model-value="patchField(index, { serialized: $event === true })" /></MetadataTableCell>
                <MetadataTableCell class="grid-boolean-column" column-key="listVisible" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><Checkbox :model-value="field.listVisible" aria-label="列表显示" @update:model-value="patchField(index, { listVisible: $event === true })" /></MetadataTableCell>
                <MetadataTableCell class="grid-boolean-column" column-key="formVisible" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="fieldTableDescriptor" :row="field as FieldRow" :rows="modelValue.fields as FieldRow[]" @apply="applyFieldPatches"><Checkbox :model-value="field.formVisible" aria-label="表单显示" @update:model-value="patchField(index, { formVisible: $event === true })" /></MetadataTableCell>
                <TableCell class="grid-actions-column">
                  <div class="grid-row-actions">
                    <IconButton aria-label="上移字段" :disabled="index === 0" :icon="ArrowUp" label="上移字段" tooltip @click="moveField(index, index - 1)" />
                    <IconButton aria-label="下移字段" :disabled="index === modelValue.fields.length - 1" :icon="ArrowDown" label="下移字段" tooltip @click="moveField(index, index + 1)" />
                    <IconButton aria-label="删除字段" :icon="Trash2" label="删除字段" tooltip variant="danger" @click="deleteField(index)" />
                  </div>
                </TableCell>
              </TableRow>
            </TableBody>
          </template>
        </EditableMetadataGrid>

      </TabsContent>

      <TabsContent value="queries" class="designer-section">
        <header class="designer-section-heading">
          <div>
            <h2 id="query-section-title">查询</h2>
            <span>{{ modelValue.queries.length }} 个查询组</span>
          </div>
          <Button size="sm" @click="addQuery"><Plus />添加查询</Button>
        </header>

        <div v-if="modelValue.queries.length" class="query-grid-list">
          <section v-for="(query, queryIndex) in modelValue.queries" :key="query.id ?? `query-${queryIndex}`" class="query-grid-group">
            <header class="query-grid-heading">
              <span class="item-order">{{ queryIndex + 1 }}</span>
              <label><span>查询名称</span><input :value="query.label" placeholder="查询名称" @input="patchQuery(queryIndex, { label: textValue($event) })"></label>
              <label><span>方法名</span><input :value="query.queryCode" placeholder="findByStatus" @input="patchQuery(queryIndex, { queryCode: textValue($event) })"></label>
              <div class="query-logic-field">
                <span>组合逻辑</span>
                <div class="segmented-control">
                  <button type="button" :class="{ active: query.logic === 'AND' }" @click="updateQueryLogic(queryIndex, 'AND')">AND</button>
                  <button type="button" :class="{ active: query.logic === 'OR' }" @click="updateQueryLogic(queryIndex, 'OR')">OR</button>
                </div>
              </div>
              <div class="grid-row-actions">
                <IconButton aria-label="上移查询" :disabled="queryIndex === 0" :icon="ArrowUp" label="上移查询" tooltip @click="moveQuery(queryIndex, queryIndex - 1)" />
                <IconButton aria-label="下移查询" :disabled="queryIndex === modelValue.queries.length - 1" :icon="ArrowDown" label="下移查询" tooltip @click="moveQuery(queryIndex, queryIndex + 1)" />
                <IconButton aria-label="删除查询" :icon="Trash2" label="删除查询" tooltip variant="danger" @click="deleteQuery(queryIndex)" />
              </div>
            </header>
            <EditableMetadataGrid :columns="6" :count="query.items.length" empty-text="尚未配置查询条件" :min-width="760" title="条件">
              <template #actions><Button :disabled="!fieldOptions.length" size="sm" variant="outline" @click="addCondition(queryIndex)"><Plus />添加条件</Button></template>
              <template #header>
                <TableHeader><TableRow>
                  <MetadataTableHead class="grid-order-column" mode="system">#</MetadataTableHead>
                  <MetadataTableHead
                    v-for="column in conditionTableDescriptor(query).columns.slice(0, 3)"
                    :key="String(column.key)"
                    :mode="column.editable ? 'agent' : 'system'">
                    {{ column.label }}
                    <template v-if="column.editable" #action><MetadataColumnAdjustDialog :column-key="String(column.key)" :context="{ modelCode: modelValue.modelCode, queryCode: query.queryCode }" :descriptor="conditionTableDescriptor(query)" :rows="query.items as ConditionRow[]" @apply="applyConditionPatches(queryIndex, $event)" /></template>
                  </MetadataTableHead>
                  <MetadataTableHead mode="system">值类型</MetadataTableHead>
                  <MetadataTableHead class="grid-actions-column" mode="system">操作</MetadataTableHead>
                </TableRow></TableHeader>
              </template>
              <template #body>
                <TableBody>
                  <TableRow v-for="(condition, conditionIndex) in query.items" :key="condition.id ?? `condition-${conditionIndex}`">
                    <TableCell class="grid-order-column">{{ conditionIndex + 1 }}</TableCell>
                    <MetadataTableCell column-key="fieldCode" :context="{ modelCode: modelValue.modelCode, queryCode: query.queryCode }" :descriptor="conditionTableDescriptor(query)" :row="condition as ConditionRow" :rows="query.items as ConditionRow[]" @apply="applyConditionPatches(queryIndex, $event)"><select :value="condition.fieldCode" @change="updateConditionField(queryIndex, conditionIndex, textValue($event))"><option value="" disabled>选择字段</option><option v-for="field in fieldOptions" :key="field.code" :value="field.code">{{ field.label }} · {{ field.code }}</option></select></MetadataTableCell>
                    <MetadataTableCell column-key="operator" :context="{ modelCode: modelValue.modelCode, queryCode: query.queryCode }" :descriptor="conditionTableDescriptor(query)" :disabled="query.logic === 'OR'" :row="condition as ConditionRow" :rows="query.items as ConditionRow[]" @apply="applyConditionPatches(queryIndex, $event)"><select :disabled="query.logic === 'OR'" :value="condition.operator" @change="updateConditionOperator(queryIndex, conditionIndex, textValue($event))"><option v-for="operator in queryOperators" :key="operator.value" :value="operator.value">{{ operator.label }}</option></select></MetadataTableCell>
                    <MetadataTableCell column-key="paramName" :context="{ modelCode: modelValue.modelCode, queryCode: query.queryCode }" :descriptor="conditionTableDescriptor(query)" :disabled="query.logic === 'OR'" :row="condition as ConditionRow" :rows="query.items as ConditionRow[]" @apply="applyConditionPatches(queryIndex, $event)"><input :disabled="query.logic === 'OR'" :value="query.logic === 'OR' ? 'keyword' : (condition.paramName ?? '')" :placeholder="query.queryCode || '参数名'" @input="patchCondition(queryIndex, conditionIndex, { paramName: textValue($event) || null })"></MetadataTableCell>
                    <TableCell><input :value="conditionValueType(condition)" disabled></TableCell>
                    <TableCell class="grid-actions-column"><div class="grid-row-actions"><IconButton aria-label="上移条件" :disabled="conditionIndex === 0" :icon="ArrowUp" label="上移条件" tooltip @click="moveCondition(queryIndex, conditionIndex, conditionIndex - 1)" /><IconButton aria-label="下移条件" :disabled="conditionIndex === query.items.length - 1" :icon="ArrowDown" label="下移条件" tooltip @click="moveCondition(queryIndex, conditionIndex, conditionIndex + 1)" /><IconButton aria-label="删除条件" :icon="Trash2" label="删除条件" tooltip variant="danger" @click="deleteCondition(queryIndex, conditionIndex)" /></div></TableCell>
                  </TableRow>
                </TableBody>
              </template>
            </EditableMetadataGrid>
          </section>
        </div>
        <div v-else class="designer-empty"><GitBranch :size="22" /><span>尚未配置查询</span></div>
      </TabsContent>

      <TabsContent value="relations" class="designer-section">
        <EditableMetadataGrid :columns="15" :count="modelValue.relations.length" empty-text="尚未配置关联" :min-width="1520" title="关联">
          <template #actions><Button size="sm" @click="addRelation"><Plus />添加关联</Button></template>
          <template #header>
            <TableHeader><TableRow>
              <MetadataTableHead class="grid-order-column" mode="system">#</MetadataTableHead>
              <MetadataTableHead
                v-for="column in relationTableDescriptor.columns"
                :key="String(column.key)"
                :class="booleanColumnClass(String(column.key))"
                :mode="relationColumnRowKeys(String(column.key)).length ? 'agent' : 'system'">
                {{ column.label }}
                <template v-if="relationColumnRowKeys(String(column.key)).length" #action><MetadataColumnAdjustDialog :column-key="String(column.key)" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :rows="relationRows" :selected-row-keys="relationColumnRowKeys(String(column.key))" @apply="applyRelationPatches" /></template>
              </MetadataTableHead>
              <MetadataTableHead class="grid-actions-column" mode="system">操作</MetadataTableHead>
            </TableRow></TableHeader>
          </template>
          <template #body>
            <TableBody>
              <TableRow v-for="(relation, index) in modelValue.relations" :key="relation.id ?? `relation-${index}`">
                <TableCell class="grid-order-column">{{ index + 1 }}</TableCell>
                <MetadataTableCell column-key="label" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :row="relationRows[index]" :rows="relationRows" @apply="applyRelationPatches"><input :value="relation.label" placeholder="注释" @input="patchRelation(index, { label: textValue($event) })"></MetadataTableCell>
                <MetadataTableCell column-key="relationCode" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :row="relationRows[index]" :rows="relationRows" @apply="applyRelationPatches"><input :value="relation.relationCode" placeholder="propertyName" @input="updateRelationCode(index, textValue($event))"></MetadataTableCell>
                <MetadataTableCell column-key="relationType" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :row="relationRows[index]" :rows="relationRows" @apply="applyRelationPatches">
                  <div
                    class="relation-kind-control"
                    :class="relationKindClass(relation.relationType)"
                    :data-relation-kind="relation.relationType"
                  >
                    <select :value="relation.relationType" @change="updateRelationKind(index, textValue($event))"><option v-for="kind in relationKinds" :key="kind.value" :value="kind.value">{{ kind.label }}</option></select>
                  </div>
                </MetadataTableCell>
                <MetadataTableCell column-key="targetModelCode" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :disabled="!models.length" :row="relationRows[index]" :rows="relationRows" @apply="applyRelationPatches"><select :disabled="!models.length" :value="relationTargetValue(relation)" @change="updateRelationTarget(index, textValue($event))"><option value="" disabled>选择目标模型</option><option v-for="target in models" :key="target.id" :value="String(target.id)">{{ target.name }}<template v-if="target.className"> · {{ target.className }}</template></option></select></MetadataTableCell>
                <MetadataTableCell column-key="primaryMapping" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :row="relationRows[index]" :rows="relationRows" @apply="applyRelationPatches">
                  <input v-if="relation.relationType === 'MANY_TO_ONE'" :value="relation.joinColumn ?? ''" placeholder="JoinColumn" @input="patchRelation(index, { joinColumn: textValue($event) || null })">
                  <input v-else-if="relation.relationType === 'ONE_TO_MANY'" :value="relation.mappedBy ?? ''" placeholder="mappedBy" @input="patchRelation(index, { mappedBy: textValue($event) || null })">
                  <input v-else-if="relation.relationType === 'ONE_TO_ONE'" :value="relation.joinColumn ?? ''" placeholder="JoinColumn" @input="patchRelation(index, { joinColumn: textValue($event) || null, mappedBy: textValue($event) ? null : relation.mappedBy, dissociateAction: textValue($event) ? relation.dissociateAction : 'NONE' })">
                  <input v-else :value="relation.joinTable ?? ''" placeholder="JoinTable" @input="patchRelation(index, { joinTable: textValue($event) || null })">
                </MetadataTableCell>
                <MetadataTableCell column-key="localMapping" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :disabled="!relationColumnEnabled(relationRows[index], 'localMapping')" :row="relationRows[index]" :rows="relationRows" @apply="applyRelationPatches">
                  <input v-if="relation.relationType === 'ONE_TO_ONE'" :value="relation.mappedBy ?? ''" placeholder="mappedBy" @input="patchRelation(index, { mappedBy: textValue($event) || null, joinColumn: textValue($event) ? null : relation.joinColumn, dissociateAction: textValue($event) ? 'NONE' : relation.dissociateAction })">
                  <input v-else-if="relation.relationType === 'MANY_TO_MANY'" :value="relation.joinTableJoinColumn ?? ''" placeholder="本端外键列" @input="patchRelation(index, { joinTableJoinColumn: textValue($event) || null })">
                  <input v-else disabled value="-">
                </MetadataTableCell>
                <MetadataTableCell column-key="inverseMapping" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :disabled="!relationColumnEnabled(relationRows[index], 'inverseMapping')" :row="relationRows[index]" :rows="relationRows" @apply="applyRelationPatches"><input v-if="relation.relationType === 'MANY_TO_MANY'" :value="relation.joinTableInverseColumn ?? ''" placeholder="目标端外键列" @input="patchRelation(index, { joinTableInverseColumn: textValue($event) || null })"><input v-else disabled value="-"></MetadataTableCell>
                <MetadataTableCell column-key="dissociateAction" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :disabled="!relationColumnEnabled(relationRows[index], 'dissociateAction')" :row="relationRows[index]" :rows="relationRows" @apply="applyRelationPatches"><select :disabled="!relationOwnsJoinColumn(relation)" :value="relation.dissociateAction" @change="updateDissociateAction(index, textValue($event))"><option v-for="action in dissociateActions" :key="action.value" :value="action.value">{{ action.label }}</option></select></MetadataTableCell>
                <MetadataTableCell class="grid-boolean-column" column-key="required" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :row="relationRows[index]" :rows="relationRows" @apply="applyRelationPatches"><Checkbox :model-value="relation.required" aria-label="关联必填" @update:model-value="patchRelation(index, { required: $event === true })" /></MetadataTableCell>
                <MetadataTableCell class="grid-boolean-column" column-key="createWritable" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :row="relationRows[index]" :rows="relationRows" @apply="applyRelationPatches"><Checkbox :model-value="relation.createWritable" aria-label="关联新增可写" @update:model-value="patchRelation(index, { createWritable: $event === true })" /></MetadataTableCell>
                <MetadataTableCell class="grid-boolean-column" column-key="updateWritable" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :row="relationRows[index]" :rows="relationRows" @apply="applyRelationPatches"><Checkbox :model-value="relation.updateWritable" aria-label="关联修改可写" @update:model-value="patchRelation(index, { updateWritable: $event === true })" /></MetadataTableCell>
                <MetadataTableCell class="grid-boolean-column" column-key="listVisible" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :row="relationRows[index]" :rows="relationRows" @apply="applyRelationPatches"><Checkbox :model-value="relation.listVisible" aria-label="关联列表显示" @update:model-value="patchRelation(index, { listVisible: $event === true })" /></MetadataTableCell>
                <MetadataTableCell class="grid-boolean-column" column-key="formVisible" :context="{ modelCode: modelValue.modelCode, modelName: modelValue.name }" :descriptor="relationTableDescriptor" :row="relationRows[index]" :rows="relationRows" @apply="applyRelationPatches"><Checkbox :model-value="relation.formVisible" aria-label="关联表单显示" @update:model-value="patchRelation(index, { formVisible: $event === true })" /></MetadataTableCell>
                <TableCell class="grid-actions-column"><div class="grid-row-actions"><IconButton aria-label="上移关联" :disabled="index === 0" :icon="ArrowUp" label="上移关联" tooltip @click="moveRelation(index, index - 1)" /><IconButton aria-label="下移关联" :disabled="index === modelValue.relations.length - 1" :icon="ArrowDown" label="下移关联" tooltip @click="moveRelation(index, index + 1)" /><IconButton aria-label="删除关联" :icon="Trash2" label="删除关联" tooltip variant="danger" @click="deleteRelation(index)" /></div></TableCell>
              </TableRow>
            </TableBody>
          </template>
        </EditableMetadataGrid>
      </TabsContent>
    </div>

    <datalist id="kotlin-field-types"><option v-for="type in fieldTypes" :key="type" :value="type" /></datalist>
  </Tabs>
</template>

<style scoped>
.relation-kind-many-to-one {
  --relation-kind-color: #2563eb;
}

.relation-kind-one-to-many {
  --relation-kind-color: #15803d;
}

.relation-kind-one-to-one {
  --relation-kind-color: #7c3aed;
}

.relation-kind-many-to-many {
  --relation-kind-color: #c2410c;
}

.relation-kind-control {
  position: relative;
  min-width: 0;
  background: color-mix(in srgb, var(--relation-kind-color) 8%, var(--background));
  border: 1px solid color-mix(in srgb, var(--relation-kind-color) 32%, var(--border));
  border-radius: 4px;
}

.relation-kind-control::before {
  position: absolute;
  top: 6px;
  bottom: 6px;
  left: 5px;
  width: 3px;
  content: '';
  background: var(--relation-kind-color);
  border-radius: 2px;
  pointer-events: none;
}

.relation-kind-control > select {
  padding-left: 13px;
  color: var(--relation-kind-color);
  font-weight: 700;
  letter-spacing: 0;
}

</style>
