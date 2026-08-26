<script setup lang="ts">
import { ArrowDown, ArrowUp, GitBranch, Plus, Save, Search, Settings2, Trash2 } from '@lucide/vue'
import { computed, onMounted, ref, shallowRef, watch } from 'vue'

import IconButton from '@/components/composed/icon-button/IconButton.vue'
import MetadataColumnAdjustDialog from '@/components/composed/metadata-table/MetadataColumnAdjustDialog.vue'
import MetadataTableCell from '@/components/composed/metadata-table/MetadataTableCell.vue'
import MetadataTableHead from '@/components/composed/metadata-table/MetadataTableHead.vue'
import { createTableRevision } from '@/components/composed/metadata-table/metadata-table'
import type { MetadataPatchApplication, MetadataTableDescriptor } from '@/components/composed/metadata-table/metadata-table'
import { Button } from '@/components/generated/shadcn/button'
import { Checkbox } from '@/components/generated/shadcn/checkbox'
import { Dialog, DialogDescription, DialogHeader, DialogScrollContent, DialogTitle } from '@/components/generated/shadcn/dialog'
import { Input } from '@/components/generated/shadcn/input'
import { Table, TableBody, TableCell, TableEmpty, TableHeader, TableRow } from '@/components/generated/shadcn/table'
import { LowcodeApi } from '@/lowcode-api'
import type { JsonObject, LowcodeModelDraft, LowcodeModelSummary, LowcodeQueryConditionDraft, LowcodeQueryLogic, LowcodeQueryOperator, LsiLibraryFeature } from '@/types'

import { applyQueryLogic, applyQueryOperator, createQuery, createQueryCondition, moveItem, normalizeModelDraft, queryableModelProperties, queryValueType } from '../model-studio/model-draft'
import { resourceBelongsToFeatureScope } from './library-resource-index'
import { libraryQueryRow, libraryQueryRows, mergeLibraryQueryRow, queryDraftFromRow, removeLibraryQueryRow } from './library-query-table'
import type { LibraryQueryRow } from './library-query-table'

type LibraryConditionRow = LowcodeQueryConditionDraft & Record<string, unknown>

const props = withDefaults(defineProps<{
  createRequest?: number
  features: LsiLibraryFeature[]
  selectedFeatureId?: number | string
}>(), { createRequest: 0 })
const emit = defineEmits<{ changed: [] }>()

const api = new LowcodeApi()
const modelSummaries = shallowRef<LowcodeModelSummary[]>([])
const modelDrafts = shallowRef(new Map<string, LowcodeModelDraft>())
const rows = shallowRef<LibraryQueryRow[]>([])
const selected = ref(new Set<string>())
const dirty = ref(new Set<string>())
const saving = ref(new Set<string>())
const search = ref('')
const createModelId = ref('')
const notice = ref('')
const dialogOpen = ref(false)
const activeRowKey = ref('')
let localSequence = 0

const queryOperators: Array<{ value: LowcodeQueryOperator, label: string }> = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'LIKE', label: '包含' },
  { value: 'STARTS_WITH', label: '开头匹配' },
  { value: 'ENDS_WITH', label: '结尾匹配' },
  { value: 'GT', label: '大于' },
  { value: 'GE', label: '大于等于' },
  { value: 'LT', label: '小于' },
  { value: 'LE', label: '小于等于' },
  { value: 'IN', label: '包含任一' },
  { value: 'NOT_IN', label: '排除数组' },
  { value: 'BETWEEN', label: '区间' },
  { value: 'TIME_RANGE', label: '时间范围' },
  { value: 'NULL_STATE', label: '空值状态' },
  { value: 'ZERO_STATE', label: '零值状态' },
]

const visibleRows = computed<LibraryQueryRow[]>(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) {
    return rows.value
  }
  return rows.value.filter((row) => querySearchText(row).includes(keyword))
})
const descriptor = computed<MetadataTableDescriptor<LibraryQueryRow>>(() => {
  const value: Omit<MetadataTableDescriptor<LibraryQueryRow>, 'revision'> = {
    tableId: 'library.queries',
    rowIdentityKey: 'rowKey',
    rowKey: (row) => row.rowKey,
    columns: [
      { key: 'modelCode', label: '所属模型', kind: 'scalar', context: true },
      { key: 'queryCode', label: '方法名', kind: 'scalar', editable: true, context: true },
      { key: 'label', label: '查询名称', kind: 'scalar', editable: true, context: false },
      {
        key: 'logic', label: '组合逻辑', kind: 'enum', editable: true, context: true,
        options: [{ label: '全部满足', value: 'AND' }, { label: '关键词匹配', value: 'OR' }],
      },
      { key: 'conditionCount', label: '查询条件', kind: 'collection', context: false },
    ],
    operations: ['translate', 'replace', 'fill', 'custom'],
  }
  return { ...value, revision: createTableRevision(value, visibleRows.value) }
})
const activeRow = computed(() => rows.value.find((row) => row.rowKey === activeRowKey.value))
const activeModel = computed(() => activeRow.value
  ? modelDrafts.value.get(String(activeRow.value.modelId))
  : undefined)
const fieldOptions = computed(() => activeModel.value
  ? queryableModelProperties(activeModel.value, modelSummaries.value)
  : [])
const conditionRows = computed<LibraryConditionRow[]>(() => (activeRow.value?.items ?? []) as LibraryConditionRow[])
const conditionDescriptor = computed<MetadataTableDescriptor<LibraryConditionRow>>(() => {
  const query = activeRow.value
  const value: Omit<MetadataTableDescriptor<LibraryConditionRow>, 'revision'> = {
    tableId: `library.query.conditions:${query?.rowKey ?? 'none'}`,
    rowIdentityKey: 'id|orderNo',
    rowKey: (condition) => condition.id != null ? `id:${condition.id}` : `order:${condition.orderNo}`,
    columns: [
      {
        key: 'fieldCode', label: '字段', kind: 'enum', editable: true, context: true,
        options: fieldOptions.value.map((field) => ({ label: field.label, value: field.code })),
      },
      {
        key: 'operator', label: '操作符', kind: 'enum', editable: query?.logic !== 'OR', context: true,
        options: queryOperators.map((operator) => ({ label: operator.label, value: operator.value })),
      },
      { key: 'paramName', label: '参数名', kind: 'scalar', editable: query?.logic !== 'OR', context: true },
      { key: 'valueType', label: '值类型', kind: 'enum', context: true },
    ],
    operations: ['replace', 'fill', 'custom'],
  }
  return { ...value, revision: createTableRevision(value, conditionRows.value) }
})
const ownedModels = computed(() => [...modelDrafts.value.values()])

onMounted(async () => {
  await refresh()
  if (props.createRequest > 0) {
    createQueryRow()
  }
})
watch(() => [props.selectedFeatureId, props.features.map((feature) => feature.id).join('|')], refresh)
watch(() => props.createRequest, (request, previous) => {
  if (request > previous) {
    createQueryRow()
  }
})

async function refresh(): Promise<void> {
  notice.value = ''
  try {
    const allModels = await api.models()
    const ownedModels = allModels.filter((model) => owns(model.featureId))
    const drafts = await Promise.all(ownedModels.map(async (model) => normalizeModelDraft(await api.detail(model.id))))
    modelSummaries.value = allModels
    modelDrafts.value = new Map(drafts.map((model) => [String(model.id), model]))
    rows.value = libraryQueryRows(drafts)
    selected.value = new Set()
    dirty.value = new Set()
    createModelId.value = String(ownedModels[0]?.id ?? '')
  } catch (cause) {
    notice.value = cause instanceof Error ? cause.message : '读取查询失败'
  }
}

function owns(featureId?: number | string): boolean {
  return resourceBelongsToFeatureScope(featureId, props.features, props.selectedFeatureId)
}

function querySearchText(row: LibraryQueryRow): string {
  return [row.modelName, row.modelCode, row.label, row.queryCode].join(' ').toLowerCase()
}

function patchRow(row: LibraryQueryRow, patch: Partial<LibraryQueryRow>): void {
  Object.assign(row, patch)
  rows.value = [...rows.value]
  markDirty(row.rowKey)
}

function updateLogic(row: LibraryQueryRow, logic: LowcodeQueryLogic): void {
  const query = applyQueryLogic(queryDraftFromRow(row), logic)
  patchRow(row, { logic: query.logic, items: query.items, conditionCount: query.items.length })
}

function createQueryRow(): void {
  const model = modelDrafts.value.get(createModelId.value)
  if (!model) {
    return
  }
  const firstField = queryableModelProperties(model, modelSummaries.value)[0]?.code ?? ''
  const query = createQuery(model.queries.length, firstField)
  const row = libraryQueryRow(model, query, model.queries.length, `local-query:${localSequence += 1}`)
  rows.value = [...rows.value, row]
  markDirty(row.rowKey)
  openConditions(row)
}

async function save(row: LibraryQueryRow): Promise<void> {
  setSaving(row.rowKey, true)
  notice.value = ''
  try {
    const latest = normalizeModelDraft(await api.detail(row.modelId))
    const command = mergeLibraryQueryRow(latest, row)
    const validation = await api.validate(command as JsonObject)
    if (!validation.valid) {
      throw new Error(validation.errors.join('；'))
    }
    await api.save(command as JsonObject)
    const persisted = normalizeModelDraft(await api.detail(row.modelId))
    const savedQuery = persisted.queries.find((query) => query.queryCode === row.queryCode.trim())
    if (!savedQuery) {
      throw new Error('查询已保存，但无法读取最新查询快照')
    }
    const savedRow = libraryQueryRow(persisted, savedQuery, savedQuery.orderNo - 1, row.rowKey)
    Object.assign(row, savedRow)
    modelDrafts.value = new Map(modelDrafts.value).set(String(row.modelId), persisted)
    setDirty(row.rowKey, false)
    notice.value = `${row.label || row.queryCode}已保存`
    emit('changed')
  } catch (cause) {
    notice.value = cause instanceof Error ? cause.message : '保存查询失败'
  } finally {
    setSaving(row.rowKey, false)
  }
}

async function deleteRow(row: LibraryQueryRow): Promise<void> {
  if (!window.confirm(`删除查询“${row.label || row.queryCode || '未命名查询'}”？`)) {
    return
  }
  if (row.queryId == null) {
    rows.value = rows.value.filter((candidate) => candidate.rowKey !== row.rowKey)
    return
  }
  setSaving(row.rowKey, true)
  notice.value = ''
  try {
    const latest = normalizeModelDraft(await api.detail(row.modelId))
    const command = removeLibraryQueryRow(latest, row)
    const validation = await api.validate(command as JsonObject)
    if (!validation.valid) {
      throw new Error(validation.errors.join('；'))
    }
    await api.save(command as JsonObject)
    rows.value = rows.value.filter((candidate) => candidate.rowKey !== row.rowKey)
    modelDrafts.value = new Map(modelDrafts.value).set(String(row.modelId), command)
    notice.value = '查询已删除'
    emit('changed')
  } catch (cause) {
    notice.value = cause instanceof Error ? cause.message : '删除查询失败'
  } finally {
    setSaving(row.rowKey, false)
  }
}

function openConditions(row: LibraryQueryRow): void {
  activeRowKey.value = row.rowKey
  dialogOpen.value = true
}

function addCondition(): void {
  const row = activeRow.value
  if (!row) {
    return
  }
  const fieldCode = fieldOptions.value[0]?.code ?? ''
  const condition = createQueryCondition(row.items.length, fieldCode)
  if (row.logic === 'OR') {
    condition.operator = 'LIKE'
    condition.paramName = 'keyword'
  }
  patchRow(row, { items: [...row.items, condition], conditionCount: row.items.length + 1 })
}

function patchCondition(index: number, patch: Partial<LowcodeQueryConditionDraft>): void {
  const row = activeRow.value
  if (!row) {
    return
  }
  const items = row.items.map((condition, conditionIndex) => conditionIndex === index
    ? normalizeCondition({ ...condition, ...patch }, row.logic)
    : condition)
  patchRow(row, { items, conditionCount: items.length })
}

function normalizeCondition(condition: LowcodeQueryConditionDraft, logic: LowcodeQueryLogic): LowcodeQueryConditionDraft {
  if (logic === 'OR') {
    return { ...condition, operator: 'LIKE', valueType: 'SINGLE', paramName: 'keyword' }
  }
  const kotlinType = fieldOptions.value.find((field) => field.code === condition.fieldCode)?.kotlinType ?? ''
  return applyQueryOperator(condition, condition.operator, kotlinType)
}

function moveCondition(from: number, to: number): void {
  const row = activeRow.value
  if (!row) {
    return
  }
  patchRow(row, { items: moveItem(row.items, from, to) })
}

function deleteCondition(index: number): void {
  const row = activeRow.value
  if (!row) {
    return
  }
  const items = row.items.filter((_, itemIndex) => itemIndex !== index)
    .map((condition, itemIndex) => ({ ...condition, orderNo: itemIndex + 1 }))
  patchRow(row, { items, conditionCount: items.length })
}

function conditionValueType(condition: LowcodeQueryConditionDraft): string {
  const kotlinType = fieldOptions.value.find((field) => field.code === condition.fieldCode)?.kotlinType ?? ''
  return queryValueType(condition.operator, kotlinType)
}

function eventValue(event: Event): string {
  return (event.target as HTMLSelectElement).value
}

function updateLogicFromEvent(row: LibraryQueryRow, event: Event): void {
  updateLogic(row, eventValue(event) as LowcodeQueryLogic)
}

function updateConditionField(index: number, event: Event): void {
  patchCondition(index, { fieldCode: eventValue(event) })
}

function updateConditionOperator(index: number, event: Event): void {
  patchCondition(index, { operator: eventValue(event) as LowcodeQueryOperator })
}

function applyConditionPatches(application: MetadataPatchApplication<LibraryConditionRow>): void {
  const row = activeRow.value
  if (!row) return
  const items = (application.rows as LowcodeQueryConditionDraft[])
    .map((condition) => normalizeCondition(condition, row.logic))
  patchRow(row, { items, conditionCount: items.length })
}

function applyPatches(application: MetadataPatchApplication<Record<string, unknown>>): void {
  const previous = new Map(rows.value.map((row) => [row.rowKey, row]))
  const patched = new Map((application.rows as LibraryQueryRow[]).map((row) => {
    const before = previous.get(row.rowKey)
    if (!before || before.logic === row.logic) return [row.rowKey, row]
    const normalized = applyQueryLogic(queryDraftFromRow(row), row.logic)
    return [row.rowKey, { ...row, items: normalized.items, conditionCount: normalized.items.length }]
  }))
  rows.value = rows.value.map((row) => patched.get(row.rowKey) ?? row)
  application.applied.forEach((patch) => markDirty(patch.rowKey))
  notice.value = application.conflicts.length ? `${application.conflicts.length} 个单元格发生冲突，已跳过` : ''
}

function toggleSelection(row: LibraryQueryRow, value: boolean | 'indeterminate'): void {
  const next = new Set(selected.value)
  if (value === true) {
    next.add(row.rowKey)
  } else {
    next.delete(row.rowKey)
  }
  selected.value = next
}

function markDirty(rowKey: string): void {
  setDirty(rowKey, true)
}

function setDirty(rowKey: string, value: boolean): void {
  const next = new Set(dirty.value)
  if (value) {
    next.add(rowKey)
  } else {
    next.delete(rowKey)
  }
  dirty.value = next
}

function setSaving(rowKey: string, value: boolean): void {
  const next = new Set(saving.value)
  if (value) {
    next.add(rowKey)
  } else {
    next.delete(rowKey)
  }
  saving.value = next
}
</script>

<template>
  <section class="library-table-workspace" :data-active-query="activeRowKey">
    <header class="library-table-toolbar library-query-toolbar">
      <div><strong>查询</strong><span>{{ selectedFeatureId != null ? '当前功能分类' : '当前 Library 全部模型' }} · {{ visibleRows.length }} 项</span></div>
      <div class="library-query-toolbar-actions">
        <label class="studio-search-field"><Search /><Input v-model="search" type="search" placeholder="搜索模型或查询" /></label>
        <select v-model="createModelId" aria-label="新查询所属模型"><option v-for="model in ownedModels" :key="String(model.id)" :value="String(model.id)">{{ model.name }}</option></select>
        <Button :disabled="!createModelId" size="sm" variant="outline" @click="createQueryRow"><Plus />新建查询</Button>
      </div>
    </header>
    <p v-if="notice" class="studio-notice" :class="{ error: !notice.endsWith('已保存') && notice !== '查询已删除' }">{{ notice }}</p>
    <Table class="metadata-table library-query-table">
      <TableHeader><TableRow>
        <MetadataTableHead class="metadata-select-column" mode="system" />
        <MetadataTableHead mode="system">所属模型</MetadataTableHead>
        <MetadataTableHead mode="agent">
          方法名
          <template #action><MetadataColumnAdjustDialog column-key="queryCode" :descriptor="descriptor" :rows="visibleRows" :selected-row-keys="[...selected]" @apply="applyPatches" /></template>
        </MetadataTableHead>
        <MetadataTableHead mode="agent">
          查询名称
          <template #action><MetadataColumnAdjustDialog column-key="label" :descriptor="descriptor" :rows="visibleRows" :selected-row-keys="[...selected]" @apply="applyPatches" /></template>
        </MetadataTableHead>
        <MetadataTableHead mode="agent">
          组合逻辑
          <template #action><MetadataColumnAdjustDialog column-key="logic" :descriptor="descriptor" :rows="visibleRows" :selected-row-keys="[...selected]" @apply="applyPatches" /></template>
        </MetadataTableHead>
        <MetadataTableHead mode="system">对象 / 集合</MetadataTableHead>
        <MetadataTableHead class="metadata-action-column" mode="system">操作</MetadataTableHead>
      </TableRow></TableHeader>
      <TableBody>
        <TableEmpty v-if="!visibleRows.length" :colspan="7">当前范围暂无查询</TableEmpty>
        <TableRow v-for="row in visibleRows" :key="row.rowKey" :data-state="selected.has(row.rowKey) ? 'selected' : undefined">
          <TableCell><Checkbox :model-value="selected.has(row.rowKey)" @update:model-value="toggleSelection(row, $event)" /></TableCell>
          <TableCell><strong>{{ row.modelName }}</strong></TableCell>
          <MetadataTableCell column-key="queryCode" :context="{ modelCode: row.modelCode }" :descriptor="descriptor" :row="row" :rows="visibleRows" @apply="applyPatches"><Input :model-value="row.queryCode" @update:model-value="patchRow(row, { queryCode: String($event) })" /></MetadataTableCell>
          <MetadataTableCell column-key="label" :context="{ modelCode: row.modelCode }" :descriptor="descriptor" :row="row" :rows="visibleRows" @apply="applyPatches"><Input :model-value="row.label" @update:model-value="patchRow(row, { label: String($event) })" /></MetadataTableCell>
          <MetadataTableCell column-key="logic" :context="{ modelCode: row.modelCode }" :descriptor="descriptor" :row="row" :rows="visibleRows" @apply="applyPatches"><select :value="row.logic" @change="updateLogicFromEvent(row, $event)"><option value="AND">AND · 全部满足</option><option value="OR">OR · 关键词匹配</option></select></MetadataTableCell>
          <TableCell><Button size="sm" variant="outline" @click="openConditions(row)"><Settings2 />{{ row.conditionCount }} 个条件</Button></TableCell>
          <TableCell><div class="metadata-row-actions"><IconButton :disabled="!dirty.has(row.rowKey) || saving.has(row.rowKey)" :icon="Save" :label="`保存${row.label || row.queryCode}`" @click="save(row)" /><IconButton :disabled="saving.has(row.rowKey)" :icon="Trash2" :label="`删除${row.label || row.queryCode}`" variant="danger" @click="deleteRow(row)" /></div></TableCell>
        </TableRow>
      </TableBody>
    </Table>

    <Dialog v-model:open="dialogOpen">
      <DialogScrollContent class="library-resource-dialog library-query-dialog">
        <DialogHeader><DialogTitle>{{ activeRow?.label || activeRow?.queryCode || '查询条件' }}</DialogTitle><DialogDescription>条件集合使用表格配置；关闭后保留在当前查询草稿，仍需保存查询行。</DialogDescription></DialogHeader>
        <div class="library-query-condition-toolbar"><span>{{ activeRow?.modelName }} · {{ activeRow?.logic }}</span><Button :disabled="!fieldOptions.length" size="sm" variant="outline" @click="addCondition"><Plus />添加条件</Button></div>
        <Table class="metadata-table library-condition-table">
          <TableHeader><TableRow>
            <MetadataTableHead mode="system">#</MetadataTableHead>
            <MetadataTableHead
              v-for="column in conditionDescriptor.columns.slice(0, 3)"
              :key="String(column.key)"
              :mode="column.editable ? 'agent' : 'system'">
              {{ column.label }}
              <template v-if="activeRow && column.editable" #action><MetadataColumnAdjustDialog :column-key="String(column.key)" :context="{ modelCode: activeRow.modelCode, queryCode: activeRow.queryCode }" :descriptor="conditionDescriptor" :rows="conditionRows" @apply="applyConditionPatches" /></template>
            </MetadataTableHead>
            <MetadataTableHead mode="system">值类型</MetadataTableHead>
            <MetadataTableHead class="metadata-action-column" mode="system">操作</MetadataTableHead>
          </TableRow></TableHeader>
          <TableBody>
            <TableEmpty v-if="!activeRow?.items.length" :colspan="6">尚未配置查询条件</TableEmpty>
            <TableRow v-for="(condition, index) in activeRow?.items ?? []" :key="condition.id ?? `${condition.fieldCode}:${index}`">
              <TableCell>{{ index + 1 }}</TableCell>
              <MetadataTableCell column-key="fieldCode" :context="{ modelCode: activeRow?.modelCode ?? '', queryCode: activeRow?.queryCode ?? '' }" :descriptor="conditionDescriptor" :row="condition as LibraryConditionRow" :rows="conditionRows" @apply="applyConditionPatches"><select :value="condition.fieldCode" @change="updateConditionField(index, $event)"><option value="" disabled>选择字段</option><option v-for="field in fieldOptions" :key="field.code" :value="field.code">{{ field.label }} · {{ field.code }}</option></select></MetadataTableCell>
              <MetadataTableCell column-key="operator" :context="{ modelCode: activeRow?.modelCode ?? '', queryCode: activeRow?.queryCode ?? '' }" :descriptor="conditionDescriptor" :disabled="activeRow?.logic === 'OR'" :row="condition as LibraryConditionRow" :rows="conditionRows" @apply="applyConditionPatches"><select :disabled="activeRow?.logic === 'OR'" :value="condition.operator" @change="updateConditionOperator(index, $event)"><option v-for="operator in queryOperators" :key="operator.value" :value="operator.value">{{ operator.label }}</option></select></MetadataTableCell>
              <MetadataTableCell column-key="paramName" :context="{ modelCode: activeRow?.modelCode ?? '', queryCode: activeRow?.queryCode ?? '' }" :descriptor="conditionDescriptor" :disabled="activeRow?.logic === 'OR'" :row="condition as LibraryConditionRow" :rows="conditionRows" @apply="applyConditionPatches"><Input :disabled="activeRow?.logic === 'OR'" :model-value="activeRow?.logic === 'OR' ? 'keyword' : (condition.paramName ?? '')" @update:model-value="patchCondition(index, { paramName: String($event) || null })" /></MetadataTableCell>
              <TableCell><Input :model-value="conditionValueType(condition)" disabled /></TableCell>
              <TableCell><div class="metadata-row-actions"><IconButton :disabled="index === 0" :icon="ArrowUp" label="上移条件" @click="moveCondition(index, index - 1)" /><IconButton :disabled="index === (activeRow?.items.length ?? 0) - 1" :icon="ArrowDown" label="下移条件" @click="moveCondition(index, index + 1)" /><IconButton :icon="Trash2" label="删除条件" variant="danger" @click="deleteCondition(index)" /></div></TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </DialogScrollContent>
    </Dialog>
  </section>
</template>
