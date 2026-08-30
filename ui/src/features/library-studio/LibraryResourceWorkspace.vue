<script setup lang="ts">
import { ChevronLeft, ChevronRight, Plus, Save, Settings2, WandSparkles } from '@lucide/vue'
import { computed, defineAsyncComponent, onMounted, ref, shallowRef, watch } from 'vue'

import IconButton from '@/components/composed/icon-button/IconButton.vue'
import MetadataColumnAdjustDialog from '@/components/composed/metadata-table/MetadataColumnAdjustDialog.vue'
import MetadataTableCell from '@/components/composed/metadata-table/MetadataTableCell.vue'
import MetadataTableHead from '@/components/composed/metadata-table/MetadataTableHead.vue'
import { createTableRevision } from '@/components/composed/metadata-table/metadata-table'
import type { MetadataPatchApplication, MetadataTableDescriptor } from '@/components/composed/metadata-table/metadata-table'
import { Button } from '@/components/generated/shadcn/button'
import { Dialog, DialogDescription, DialogHeader, DialogScrollContent, DialogTitle } from '@/components/generated/shadcn/dialog'
import { Input } from '@/components/generated/shadcn/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/generated/shadcn/select'
import { Switch } from '@/components/generated/shadcn/switch'
import { Table, TableBody, TableCell, TableEmpty, TableHeader, TableRow } from '@/components/generated/shadcn/table'
import { LowcodeApi } from '@/lowcode-api'
import { databaseIdentifierToPascalCase, toResourceCodeFromClassName } from '@/lib/identifier'
import type { JsonObject, LowcodeDtoResourceDraft, LowcodeDtoResourceSummary, LowcodeModelDraft, LowcodeModelSummary, LsiLibraryFeature, LsiLibrarySpec } from '@/types'

import { applyDtoClassName, applyDtoKind, normalizeDtoResource } from '../dto-studio/dto-draft'
import { applyModelTableName, normalizeModelDraft } from '../model-studio/model-draft'
import { featurePackageName } from './library-draft'
import { resourceBelongsToFeatureScope } from './library-resource-index'

const DtoStudio = defineAsyncComponent(() => import('../dto-studio/DtoStudio.vue'))
const ModelStudio = defineAsyncComponent(() => import('../model-studio/ModelStudio.vue'))

export type LibraryResourceTab = 'models' | 'dtos'

interface ResourceRow extends Record<string, unknown> {
  rowKey: string
  id?: number | string
  featureId?: number | string
  code: string
  name: string
  description: string | null
  packageName: string
  contributorId: string
  className: string
  tableName: string
  kind: string
  visibility: string
  sourceModelCode: string | null
  selectionMode: string
  enabled: boolean
  version: number
  detailLabel: string
  fieldSummary: string
  modelType: string
}

const props = withDefaults(defineProps<{
  resource: LibraryResourceTab
  createRequest?: number
  features: LsiLibraryFeature[]
  selectedFeatureId?: number | string
  librarySpec: LsiLibrarySpec
}>(), { createRequest: 0 })
const emit = defineEmits<{ changed: [] }>()
const api = new LowcodeApi()
const models = ref<LowcodeModelSummary[]>([])
const dtos = shallowRef<LowcodeDtoResourceSummary[]>([])
const rows = ref<ResourceRow[]>([])
const draftModels = shallowRef(new Map<string, LowcodeModelDraft>())
const dirty = ref(new Set<string>())
const saving = ref(new Set<string>())
const notice = ref('')
const dialogOpen = ref(false)
const activeCode = ref('')
const editorRequest = ref(0)
const modelPageNumber = ref(1)
const modelTotalRowCount = ref(0)
const modelTotalPageCount = ref(0)
const loading = ref(false)
const modelPageSize = 10
let localSequence = 0

const resourceLabel = computed(() => ({ models: '模型', dtos: 'DTO' })[props.resource])
const contributorId = computed(() => props.librarySpec.contributorId)
const selectedFeature = computed(() => props.features.find((feature) => String(feature.id) === String(props.selectedFeatureId)))
const creationContext = computed(() => selectedFeature.value ? {
  featureId: selectedFeature.value.id,
  packageName: featurePackageName(props.librarySpec, selectedFeature.value),
  contributorId: contributorId.value,
} : undefined)
const editorCreationContext = computed(() => {
  const row = rows.value.find((item) => item.code === activeCode.value)
  const feature = row
    ? props.features.find((item) => String(item.id) === String(row.featureId))
    : selectedFeature.value
  return feature ? {
    featureId: feature.id,
    packageName: featurePackageName(props.librarySpec, feature),
    contributorId: contributorId.value,
  } : undefined
})
const descriptor = computed<MetadataTableDescriptor<ResourceRow>>(() => {
  if (props.resource === 'dtos') {
    const value: Omit<MetadataTableDescriptor<ResourceRow>, 'revision'> = {
      tableId: 'library.resources:dtos',
      rowIdentityKey: 'id|code',
      rowKey: (row) => row.rowKey,
      columns: [
        { key: 'code', label: 'DTO 编码', kind: 'scalar', context: true },
        {
          key: 'featureId', label: '功能分类', kind: 'enum', editable: true, context: true,
          options: props.features.map((feature) => ({ label: feature.name, value: String(feature.id) })),
        },
        { key: 'name', label: '注释', kind: 'scalar', editable: true, context: true },
        { key: 'className', label: '类名', kind: 'scalar', editable: true, context: true },
        {
          key: 'kind', label: '用途', kind: 'enum', editable: true, context: true,
          options: ['OUTPUT', 'INPUT', 'STRUCTURE'].map((value) => ({ label: value, value })),
        },
        {
          key: 'visibility', label: 'Kotlin 可见性', kind: 'enum', editable: true, context: true,
          options: ['PUBLIC', 'INTERNAL'].map((value) => ({ label: value, value })),
        },
        {
          key: 'sourceModelCode', label: '来源实体', kind: 'enum', editable: true, context: true,
          options: [
            { label: '无，独立 DTO', value: null },
            ...models.value.filter((model) => model.modelType === 'ENTITY')
              .map((model) => ({ label: model.name, value: model.modelCode })),
          ],
        },
        {
          key: 'selectionMode', label: '字段策略', kind: 'enum', editable: true, context: true,
          options: [
            ['手动选择', 'EXPLICIT'],
            ['全部标量', 'ALL_SCALAR_FIELDS'],
            ['全部表字段', 'ALL_TABLE_FIELDS'],
            ['全部深层字段', 'ALL_DEEP_FIELDS'],
          ].map(([label, value]) => ({ label, value })),
        },
        { key: 'version', label: '版本', kind: 'scalar', editable: true, context: true },
        { key: 'enabled', label: '启用', kind: 'boolean', editable: true, context: true },
        { key: 'description', label: '说明', kind: 'scalar', editable: true, context: true },
        { key: 'packageName', label: '业务包名', kind: 'scalar', editable: true, context: true },
        { key: 'contributorId', label: 'Contributor ID', kind: 'scalar', editable: true, context: true },
        { key: 'fieldSummary', label: '字段', kind: 'collection', context: true },
      ],
      operations: ['translate', 'replace', 'fill', 'custom'],
    }
    return { ...value, revision: createTableRevision(value, rows.value) }
  }
  const value: Omit<MetadataTableDescriptor<ResourceRow>, 'revision'> = {
    tableId: `library.resources:${props.resource}`,
    rowIdentityKey: 'id|code',
    rowKey: (row) => row.rowKey,
    columns: [
      {
        key: 'featureId', label: '功能分类', kind: 'enum', editable: true, context: true,
        options: props.features.map((feature) => ({ label: feature.name, value: String(feature.id) })),
      },
      { key: 'code', label: '资源编码', kind: 'scalar', context: true },
      { key: 'className', label: 'Kotlin 文件名', kind: 'scalar', context: true },
      { key: 'tableName', label: '数据库表名', kind: 'scalar', editable: props.resource === 'models', context: true },
      {
        key: 'modelType', label: '模型类型', kind: 'enum', editable: props.resource === 'models', context: true,
        options: [
          { label: '实体', value: 'ENTITY' },
          { label: '映射父类', value: 'MAPPED_SUPERCLASS' },
          { label: '嵌入类型', value: 'EMBEDDABLE' },
        ],
      },
      { key: 'name', label: '注释', kind: 'scalar', editable: true, context: false },
      { key: 'version', label: '版本', kind: 'scalar', editable: true, context: true },
      { key: 'enabled', label: '启用', kind: 'boolean', editable: true, context: true },
      { key: 'description', label: '备注 / 说明', kind: 'scalar', editable: true, context: false },
      { key: 'packageName', label: '计算包名', kind: 'scalar', context: true },
    ],
    operations: ['translate', 'replace', 'fill', 'custom'],
  }
  return { ...value, revision: createTableRevision(value, rows.value) }
})
const dtoVisibleColumns = computed(() => descriptor.value.columns.filter((column) => column.key !== 'code'))
const dtoColumnContext = computed(() => ({
  resource: 'dtos',
  features: props.features.map((feature) => `${feature.id}:${feature.name}`).join('；'),
  models: models.value.filter((model) => model.modelType === 'ENTITY')
    .map((model) => `${model.modelCode}:${model.name}`).join('；'),
}))
const modelPageRangeLabel = computed(() => {
  if (modelTotalRowCount.value === 0) return '0 项'
  const first = (modelPageNumber.value - 1) * modelPageSize + 1
  const last = Math.min(first + models.value.length - 1, modelTotalRowCount.value)
  return `${first}-${last} / ${modelTotalRowCount.value}`
})
const modelPageLabel = computed(() => modelTotalPageCount.value === 0
  ? '0 / 0'
  : `${modelPageNumber.value} / ${modelTotalPageCount.value}`)

onMounted(async () => {
  await refresh()
  if (props.createRequest > 0) createResource()
})
watch(() => [props.resource, props.selectedFeatureId, props.features.map((feature) => feature.id).join('|')], () => {
  modelPageNumber.value = 1
  void refresh()
})
watch(() => props.createRequest, (request, previous) => {
  if (request > previous) createResource()
})

async function refresh(): Promise<void> {
  loading.value = true
  notice.value = ''
  try {
    const modelsRequest = loadModels()
    const dtosRequest = props.resource === 'dtos' ? api.dtos() : Promise.resolve([])
    const [allModels, allDtos] = await Promise.all([modelsRequest, dtosRequest])
    models.value = allModels.filter((item) => owns(item.featureId))
    dtos.value = allDtos.filter((item) => owns(item.featureId))
    rows.value = currentRows()
    draftModels.value = new Map()
    dirty.value = new Set()
  } catch (cause) {
    notice.value = cause instanceof Error ? cause.message : `读取${resourceLabel.value}失败`
  } finally {
    loading.value = false
  }
}

async function loadModels(): Promise<LowcodeModelSummary[]> {
  const condition: JsonObject = { contributorId: contributorId.value }
  if (selectedFeature.value) condition.featureId = selectedFeature.value.id
  if (props.resource !== 'models') return api.models(condition)
  let page = await api.modelPage(modelPageNumber.value, modelPageSize, condition)
  const lastPageNumber = Math.max(page.totalPageCount, 1)
  if (modelPageNumber.value > lastPageNumber) {
    modelPageNumber.value = lastPageNumber
    page = await api.modelPage(modelPageNumber.value, modelPageSize, condition)
  }
  modelTotalRowCount.value = page.totalRowCount
  modelTotalPageCount.value = page.totalPageCount
  return page.rows
}

async function changeModelPage(nextPageNumber: number): Promise<void> {
  if (loading.value || dirty.value.size > 0 || nextPageNumber < 1 || nextPageNumber > modelTotalPageCount.value) return
  modelPageNumber.value = nextPageNumber
  await refresh()
}

function owns(featureId?: number | string): boolean {
  return resourceBelongsToFeatureScope(featureId, props.features, props.selectedFeatureId)
}

function currentRows(): ResourceRow[] {
  if (props.resource === 'models') return models.value.map((item) => ({
    rowKey: `model:${item.id}`, id: item.id, featureId: String(item.featureId),
    code: item.modelCode, name: item.name, description: item.remark ?? null,
    packageName: item.packageName ?? '',
    contributorId: item.contributorId ?? contributorId.value,
    className: databaseIdentifierToPascalCase(item.tableName ?? ''),
    tableName: item.tableName ?? '',
    kind: '', visibility: '', sourceModelCode: null, selectionMode: '',
    enabled: item.status === 1, version: item.version, modelType: item.modelType,
    detailLabel: `${item.fields?.length ?? 0} 属性 · ${item.relations?.length ?? 0} 关联`,
    fieldSummary: '',
  }))
  return dtos.value.map((item) => ({
    rowKey: `dto:${item.id}`, id: item.id, featureId: String(item.featureId),
    code: item.dtoCode, name: item.name, description: item.description ?? null,
    packageName: item.packageName, contributorId: item.contributorId ?? contributorId.value,
    className: item.className, tableName: '', kind: item.kind, visibility: item.visibility ?? 'PUBLIC',
    sourceModelCode: item.sourceModel?.modelCode ?? null, selectionMode: item.selectionMode,
    enabled: item.status === 1, version: item.version, detailLabel: `${item.fields.length} 字段`,
    fieldSummary: item.fields.map((field) => field.name).join(', '), modelType: '',
  }))
}

function edit(row: ResourceRow, values: Partial<ResourceRow>): void {
  const previousDefaultCode = toResourceCodeFromClassName(
    databaseIdentifierToPascalCase(row.tableName),
  )
  const identityFollowsTable = row.id == null && (!row.code || row.code === previousDefaultCode)
  Object.assign(row, values)
  if (props.resource === 'models' && values.tableName !== undefined) {
    row.className = databaseIdentifierToPascalCase(row.tableName)
    if (identityFollowsTable) row.code = toResourceCodeFromClassName(row.className)
  }
  if (values.featureId != null) {
    const feature = props.features.find((item) => String(item.id) === String(values.featureId))
    row.packageName = feature ? featurePackageName(props.librarySpec, feature) : ''
  }
  if (props.resource === 'dtos' && values.kind === 'STRUCTURE') {
    row.sourceModelCode = null
    row.selectionMode = 'EXPLICIT'
  }
  rows.value = [...rows.value]
  dirty.value = new Set(dirty.value).add(row.rowKey)
}

function calculatedPackage(row: ResourceRow): string {
  const feature = props.features.find((item) => String(item.id) === String(row.featureId))
  return feature ? featurePackageName(props.librarySpec, feature) : ''
}

async function save(row: ResourceRow): Promise<void> {
  if (row.featureId == null || row.featureId === '') {
    notice.value = '请选择功能分类'
    return
  }
  saving.value = new Set(saving.value).add(row.rowKey)
  notice.value = ''
  try {
    if (props.resource === 'models') {
      const command = row.id == null
        ? draftModels.value.get(row.rowKey)
        : normalizeModelDraft(await api.detail(row.id))
      if (!command) throw new Error('模型草稿不存在')
      const packageName = calculatedPackage(row)
      const tableBoundCommand = applyModelTableName({
        ...command,
        packageName,
        routeConfig: { ...command.routeConfig, packageName },
      }, row.tableName.trim())
      Object.assign(command, tableBoundCommand, {
        featureId: row.featureId,
        modelCode: row.code.trim(), name: row.name.trim(),
        tableName: row.tableName.trim(), modelType: row.modelType, version: row.version,
        status: row.enabled ? 1 : 0, remark: row.description?.trim() || null,
        packageName, contributorId: contributorId.value,
      })
      const validation = await api.validate(command as JsonObject)
      if (!validation.valid) throw new Error(validation.errors.join('；'))
      await api.save(command as JsonObject)
    } else if (props.resource === 'dtos' && row.id != null) {
      const loaded = normalizeDtoResource(await api.dtoDetail(row.id))
      const renamed = applyDtoClassName(loaded, row.className.trim())
      const adjusted = applyDtoKind(renamed, row.kind as LowcodeDtoResourceDraft['kind'])
      const sourceModelCode = row.kind === 'STRUCTURE' ? null : row.sourceModelCode?.trim() || null
      const sourceChanged = sourceModelCode !== loaded.sourceModelCode
      const command: LowcodeDtoResourceDraft = {
        ...adjusted,
        featureId: row.featureId as number | string,
        name: row.name.trim(),
        packageName: row.packageName.trim(),
        contributorId: row.contributorId.trim() || null,
        visibility: row.visibility as LowcodeDtoResourceDraft['visibility'],
        sourceModelCode,
        selectionMode: row.kind === 'STRUCTURE'
          ? 'EXPLICIT'
          : row.selectionMode as LowcodeDtoResourceDraft['selectionMode'],
        excludedPaths: sourceChanged ? [] : adjusted.excludedPaths,
        fields: sourceChanged ? [] : adjusted.fields,
        version: row.version,
        description: row.description?.trim() || null,
        status: row.enabled ? 1 : 0,
      }
      const validation = await api.validateDto(command)
      if (!validation.valid) throw new Error(validation.errors.join('；'))
      await api.saveDto(command)
    }
    notice.value = `${row.name || row.code}已保存`
    await refresh()
    emit('changed')
  } catch (cause) {
    notice.value = cause instanceof Error ? cause.message : '保存失败'
  } finally {
    saving.value = new Set([...saving.value].filter((key) => key !== row.rowKey))
  }
}

function openEditor(row: ResourceRow): void {
  if (row.id == null) return
  activeCode.value = row.code
  dialogOpen.value = true
}

function createResource(): void {
  if (props.resource === 'models') {
    createModelRow()
    return
  }
  if (!creationContext.value) {
    notice.value = '请先选择功能分类'
    return
  }
  activeCode.value = ''
  editorRequest.value += 1
  dialogOpen.value = true
}

function createModelRow(): void {
  const context = creationContext.value
  const rowKey = `model:new:${localSequence += 1}`
  const draft = normalizeModelDraft({
    featureId: context?.featureId ?? 0,
    packageName: context?.packageName ?? '',
    contributorId: contributorId.value,
  })
  draftModels.value = new Map(draftModels.value).set(rowKey, draft)
  rows.value = [...rows.value, {
    rowKey, featureId: context?.featureId, code: '', name: '', description: null,
    packageName: context?.packageName ?? '', contributorId: contributorId.value,
    className: '', tableName: '', kind: '', visibility: '', sourceModelCode: null, selectionMode: '',
    enabled: true, version: 1, detailLabel: '首次保存后配置', fieldSummary: '', modelType: 'ENTITY',
  }]
  dirty.value = new Set(dirty.value).add(rowKey)
}

async function saved(): Promise<void> {
  dialogOpen.value = false
  await refresh()
  emit('changed')
}

function applyPatches(application: MetadataPatchApplication<ResourceRow>): void {
  const previousByKey = new Map(rows.value.map((row) => [row.rowKey, row]))
  rows.value = application.rows.map((row) => {
    const previous = previousByKey.get(row.rowKey)
    if (props.resource === 'models' && previous && previous.tableName !== row.tableName) {
      const previousDefaultCode = toResourceCodeFromClassName(databaseIdentifierToPascalCase(previous.tableName))
      const className = databaseIdentifierToPascalCase(row.tableName)
      return {
        ...row,
        className,
        code: previous.id == null && (!previous.code || previous.code === previousDefaultCode)
          ? toResourceCodeFromClassName(className)
          : row.code,
      }
    }
    if (previous && previous.featureId !== row.featureId && previous.packageName === row.packageName) {
      return { ...row, packageName: calculatedPackage(row) }
    }
    if (row.kind === 'STRUCTURE') {
      return { ...row, sourceModelCode: null, selectionMode: 'EXPLICIT' }
    }
    return row
  })
  const nextDirty = new Set(dirty.value)
  application.applied.forEach((patch) => nextDirty.add(patch.rowKey))
  dirty.value = nextDirty
}

function dtoColumnClass(key: string): string {
  return `library-dto-column library-dto-column-${key.replace(/[A-Z]/g, (value) => `-${value.toLowerCase()}`)}`
}

</script>

<template>
  <section class="library-table-workspace">
    <header class="library-table-toolbar">
      <div><strong>{{ resourceLabel }}</strong><span>{{ selectedFeature ? `当前分类：${selectedFeature.name}` : '当前 Library 全部分类' }}</span></div>
      <Button size="sm" variant="outline" @click="createResource"><Plus />新建{{ resourceLabel }}</Button>
    </header>
    <p v-if="notice" class="studio-notice" :class="{ error: !notice.endsWith('已保存') }">{{ notice }}</p>
    <div class="library-resource-table-scroll">
      <Table v-if="resource === 'dtos'" class="metadata-table library-resource-table library-dto-resource-table">
        <TableHeader><TableRow>
          <MetadataTableHead
            v-for="column in dtoVisibleColumns"
            :key="column.key"
            :class="dtoColumnClass(String(column.key))"
            :mode="column.editable ? 'agent' : 'system'">
            {{ column.label }}
            <template v-if="column.editable" #action>
              <MetadataColumnAdjustDialog
                :column-key="String(column.key)"
                :context="dtoColumnContext"
                :descriptor="descriptor"
                :rows="rows"
                @apply="applyPatches" />
            </template>
          </MetadataTableHead>
          <MetadataTableHead class="metadata-action-column" mode="system">操作</MetadataTableHead>
        </TableRow></TableHeader>
        <TableBody>
          <TableEmpty v-if="!rows.length" :colspan="dtoVisibleColumns.length + 1">当前范围暂无 DTO</TableEmpty>
          <TableRow v-for="row in rows" :key="row.rowKey">
            <MetadataTableCell class="library-dto-column-feature-id" column-key="featureId" :context="dtoColumnContext" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches">
              <Select :model-value="String(row.featureId ?? '')" @update:model-value="edit(row, { featureId: String($event) })">
                <SelectTrigger><SelectValue placeholder="选择分类" /></SelectTrigger>
                <SelectContent><SelectItem v-for="feature in features" :key="feature.id" :value="String(feature.id)">{{ feature.name }}</SelectItem></SelectContent>
              </Select>
            </MetadataTableCell>
            <MetadataTableCell class="library-dto-column-name" column-key="name" :context="dtoColumnContext" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches"><Input :model-value="row.name" @update:model-value="edit(row, { name: String($event) })" /></MetadataTableCell>
            <MetadataTableCell class="library-dto-column-class-name" column-key="className" :context="dtoColumnContext" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches"><Input :model-value="row.className" @update:model-value="edit(row, { className: String($event) })" /></MetadataTableCell>
            <MetadataTableCell class="library-dto-column-kind" column-key="kind" :context="dtoColumnContext" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches">
              <Select :model-value="row.kind" @update:model-value="edit(row, { kind: String($event) })">
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent><SelectItem value="OUTPUT">OUTPUT</SelectItem><SelectItem value="INPUT">INPUT</SelectItem><SelectItem value="STRUCTURE">STRUCTURE</SelectItem></SelectContent>
              </Select>
            </MetadataTableCell>
            <MetadataTableCell class="library-dto-column-visibility" column-key="visibility" :context="dtoColumnContext" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches">
              <Select :model-value="row.visibility" @update:model-value="edit(row, { visibility: String($event) })">
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent><SelectItem value="PUBLIC">PUBLIC</SelectItem><SelectItem value="INTERNAL">INTERNAL</SelectItem></SelectContent>
              </Select>
            </MetadataTableCell>
            <MetadataTableCell class="library-dto-column-source-model-code" column-key="sourceModelCode" :context="dtoColumnContext" :descriptor="descriptor" :disabled="row.kind === 'STRUCTURE'" :row="row" :rows="rows" @apply="applyPatches">
              <Select
                :disabled="row.kind === 'STRUCTURE'"
                :model-value="row.sourceModelCode ?? '__independent__'"
                @update:model-value="edit(row, { sourceModelCode: $event === '__independent__' ? null : String($event) })">
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="__independent__">无，独立 DTO</SelectItem>
                  <SelectItem v-for="model in models.filter((item) => item.modelType === 'ENTITY')" :key="model.modelCode" :value="model.modelCode">{{ model.name }} · {{ model.className }}</SelectItem>
                </SelectContent>
              </Select>
            </MetadataTableCell>
            <MetadataTableCell class="library-dto-column-selection-mode" column-key="selectionMode" :context="dtoColumnContext" :descriptor="descriptor" :disabled="row.kind === 'STRUCTURE' || !row.sourceModelCode" :row="row" :rows="rows" @apply="applyPatches">
              <Select
                :disabled="row.kind === 'STRUCTURE' || !row.sourceModelCode"
                :model-value="row.selectionMode"
                @update:model-value="edit(row, { selectionMode: String($event) })">
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent><SelectItem value="EXPLICIT">手动选择</SelectItem><SelectItem value="ALL_SCALAR_FIELDS">全部标量</SelectItem><SelectItem value="ALL_TABLE_FIELDS">全部表字段</SelectItem><SelectItem value="ALL_DEEP_FIELDS">全部深层字段</SelectItem></SelectContent>
              </Select>
            </MetadataTableCell>
            <MetadataTableCell class="library-dto-column-version" column-key="version" :context="dtoColumnContext" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches"><Input :model-value="row.version" min="1" type="number" @update:model-value="edit(row, { version: Number($event) || 1 })" /></MetadataTableCell>
            <MetadataTableCell class="library-dto-column-enabled" column-key="enabled" :context="dtoColumnContext" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches"><Switch :model-value="row.enabled" @update:model-value="edit(row, { enabled: $event })" /></MetadataTableCell>
            <MetadataTableCell class="library-dto-column-description" column-key="description" :context="dtoColumnContext" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches"><Input :model-value="row.description ?? ''" @update:model-value="edit(row, { description: String($event) })" /></MetadataTableCell>
            <MetadataTableCell class="library-dto-column-package-name" column-key="packageName" :context="dtoColumnContext" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches"><Input :model-value="row.packageName" @update:model-value="edit(row, { packageName: String($event) })" /></MetadataTableCell>
            <MetadataTableCell class="library-dto-column-contributor-id" column-key="contributorId" :context="dtoColumnContext" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches"><Input :model-value="row.contributorId" @update:model-value="edit(row, { contributorId: String($event) })" /></MetadataTableCell>
            <TableCell class="library-dto-column-field-summary">
              <Button :disabled="row.id == null" :title="row.fieldSummary" size="sm" variant="ghost" @click="openEditor(row)"><WandSparkles />{{ row.detailLabel }}</Button>
            </TableCell>
            <TableCell class="metadata-row-actions">
              <IconButton :disabled="row.id == null" :icon="Settings2" :label="row.id == null ? '首次保存后配置高级设置' : `配置${row.name}`" @click="openEditor(row)" />
              <IconButton :disabled="!dirty.has(row.rowKey) || saving.has(row.rowKey)" :icon="Save" :label="`保存${row.name || row.code}`" @click="save(row)" />
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>

      <Table v-else class="metadata-table library-resource-table">
        <TableHeader><TableRow>
          <MetadataTableHead mode="agent">
            功能分类
            <template #action><MetadataColumnAdjustDialog column-key="featureId" :context="{ resource: props.resource }" :descriptor="descriptor" :rows="rows" @apply="applyPatches" /></template>
          </MetadataTableHead>
          <MetadataTableHead mode="agent">
            注释
            <template #action><MetadataColumnAdjustDialog column-key="name" :context="{ resource: props.resource }" :descriptor="descriptor" :rows="rows" @apply="applyPatches" /></template>
          </MetadataTableHead>
          <MetadataTableHead v-if="resource === 'models'" mode="system">Kotlin 文件名</MetadataTableHead>
          <MetadataTableHead v-if="resource === 'models'" mode="agent">
            数据库表名
            <template #action><MetadataColumnAdjustDialog column-key="tableName" :context="{ resource: props.resource }" :descriptor="descriptor" :rows="rows" @apply="applyPatches" /></template>
          </MetadataTableHead>
          <MetadataTableHead v-if="resource === 'models'" mode="agent">
            模型类型
            <template #action><MetadataColumnAdjustDialog column-key="modelType" :context="{ resource: props.resource }" :descriptor="descriptor" :rows="rows" @apply="applyPatches" /></template>
          </MetadataTableHead>
          <MetadataTableHead mode="agent">
            版本
            <template #action><MetadataColumnAdjustDialog column-key="version" :context="{ resource: props.resource }" :descriptor="descriptor" :rows="rows" @apply="applyPatches" /></template>
          </MetadataTableHead>
          <MetadataTableHead mode="agent">
            启用
            <template #action><MetadataColumnAdjustDialog column-key="enabled" :context="{ resource: props.resource }" :descriptor="descriptor" :rows="rows" @apply="applyPatches" /></template>
          </MetadataTableHead>
          <MetadataTableHead mode="agent">
            备注 / 说明
            <template #action><MetadataColumnAdjustDialog column-key="description" :context="{ resource: props.resource }" :descriptor="descriptor" :rows="rows" @apply="applyPatches" /></template>
          </MetadataTableHead>
          <MetadataTableHead mode="system">计算包名</MetadataTableHead>
          <MetadataTableHead class="metadata-action-column" mode="system">操作</MetadataTableHead>
        </TableRow></TableHeader>
        <TableBody>
          <TableEmpty v-if="!rows.length" :colspan="resource === 'models' ? 10 : 7">当前范围暂无{{ resourceLabel }}</TableEmpty>
          <TableRow v-for="row in rows" :key="row.rowKey">
            <MetadataTableCell column-key="featureId" :context="{ resource: props.resource }" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches">
              <Select :model-value="row.featureId == null ? '' : String(row.featureId)" @update:model-value="edit(row, { featureId: String($event) })">
                <SelectTrigger><SelectValue placeholder="选择分类" /></SelectTrigger>
                <SelectContent><SelectItem v-for="feature in features" :key="feature.id" :value="String(feature.id)">{{ feature.name }}</SelectItem></SelectContent>
              </Select>
            </MetadataTableCell>
            <MetadataTableCell column-key="name" :context="{ resource: props.resource }" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches"><Input :model-value="row.name" @update:model-value="edit(row, { name: String($event) })" /></MetadataTableCell>
            <TableCell v-if="resource === 'models'"><Input class="bg-muted/40 text-muted-foreground" :model-value="row.className ? `${row.className}.kt` : ''" readonly /></TableCell>
            <MetadataTableCell v-if="resource === 'models'" column-key="tableName" :context="{ resource: props.resource }" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches"><Input :model-value="row.tableName" @update:model-value="edit(row, { tableName: String($event) })" /></MetadataTableCell>
            <MetadataTableCell v-if="resource === 'models'" column-key="modelType" :context="{ resource: props.resource }" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches">
              <Select :model-value="row.modelType" @update:model-value="edit(row, { modelType: String($event) })"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="ENTITY">实体</SelectItem><SelectItem value="MAPPED_SUPERCLASS">映射父类</SelectItem><SelectItem value="EMBEDDABLE">嵌入类型</SelectItem></SelectContent></Select>
            </MetadataTableCell>
            <MetadataTableCell column-key="version" :context="{ resource: props.resource }" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches"><Input :model-value="row.version" min="1" type="number" @update:model-value="edit(row, { version: Number($event) })" /></MetadataTableCell>
            <MetadataTableCell column-key="enabled" :context="{ resource: props.resource }" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches"><Switch :model-value="row.enabled" @update:model-value="edit(row, { enabled: $event })" /></MetadataTableCell>
            <MetadataTableCell column-key="description" :context="{ resource: props.resource }" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches"><Input :model-value="row.description ?? ''" @update:model-value="edit(row, { description: String($event) })" /></MetadataTableCell>
            <TableCell><code>{{ calculatedPackage(row) }}</code></TableCell>
            <TableCell class="metadata-row-actions">
              <IconButton :disabled="row.id == null" :icon="Settings2" :label="row.id == null ? '首次保存后配置高级设置' : `配置${row.name}`" @click="openEditor(row)" />
              <IconButton :disabled="!dirty.has(row.rowKey) || saving.has(row.rowKey)" :icon="Save" :label="`保存${row.name || row.code}`" @click="save(row)" />
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <nav v-if="resource === 'models'" class="model-pagination library-resource-pagination" aria-label="Library 模型分页">
      <span class="model-page-range">{{ modelPageRangeLabel }}</span>
      <div class="model-page-controls">
        <IconButton
          :disabled="loading || dirty.size > 0 || modelPageNumber <= 1"
          :icon="ChevronLeft"
          label="上一页"
          tooltip
          @click="changeModelPage(modelPageNumber - 1)"
        />
        <span class="model-page-label" aria-live="polite">{{ modelPageLabel }}</span>
        <IconButton
          :disabled="loading || dirty.size > 0 || modelPageNumber >= modelTotalPageCount"
          :icon="ChevronRight"
          label="下一页"
          tooltip
          @click="changeModelPage(modelPageNumber + 1)"
        />
      </div>
    </nav>

    <Dialog v-model:open="dialogOpen">
      <DialogScrollContent class="library-resource-dialog">
        <DialogHeader><DialogTitle>配置{{ resourceLabel }}</DialogTitle><DialogDescription>{{ resource === 'models' ? '配置字段、查询、关联、继承、路由和生成结果。' : '配置对象和集合元数据。' }}</DialogDescription></DialogHeader>
        <ModelStudio v-if="resource === 'models'" :key="`model:${activeCode}`" :creation-context="editorCreationContext" embedded :initial-model-code="activeCode" :show-identity-configuration="false" @deleted="saved" @saved="saved" />
        <DtoStudio v-else :key="`dto:${activeCode}:${editorRequest}`" :create-request="editorRequest" :creation-context="editorCreationContext" :initial-dto-code="activeCode" @deleted="saved" @saved="saved" />
      </DialogScrollContent>
    </Dialog>
  </section>
</template>
