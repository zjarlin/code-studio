<script setup lang="ts">
import { Code, Download, GitCompareArrows, Plus, RefreshCw, Save, Trash2 } from '@lucide/vue'
import { computed, onMounted, ref, shallowRef, watch } from 'vue'

import CodeBlock from '@/components/composed/code-block/CodeBlock.vue'
import IconButton from '@/components/composed/icon-button/IconButton.vue'
import MetadataAssistantPanel from '@/components/composed/metadata-assistant/MetadataAssistantPanel.vue'
import MetadataColumnAdjustDialog from '@/components/composed/metadata-table/MetadataColumnAdjustDialog.vue'
import MetadataTableCell from '@/components/composed/metadata-table/MetadataTableCell.vue'
import MetadataTableHead from '@/components/composed/metadata-table/MetadataTableHead.vue'
import { createTableRevision } from '@/components/composed/metadata-table/metadata-table'
import type { MetadataPatchApplication, MetadataTableDescriptor } from '@/components/composed/metadata-table/metadata-table'
import { Button } from '@/components/generated/shadcn/button'
import { Badge } from '@/components/generated/shadcn/badge'
import { Tabs, TabsList, TabsTrigger } from '@/components/generated/shadcn/tabs'
import {
  Table,
  TableBody,
  TableCell,
  TableHeader,
  TableRow,
} from '@/components/generated/shadcn/table'
import { LowcodeApi } from '@/lowcode-api'
import { toPinyinSnakeIdentifier } from '@/lib/identifier'
import type {
  LowcodeDtoFieldDraft,
  LowcodeDtoPreview,
  LowcodeDtoReuseAnalysis,
  LowcodeDtoResourceDraft,
  LowcodeDtoResourceSummary,
  LowcodeApiTypeOption,
  LsiValidationRuleMetadata,
  LsiValidationValueKind,
  LowcodeModelSummary,
  LowcodePreviewFile,
  LowcodeValidationResult,
  JsonObject,
} from '@/types'

import {
  applyAgentDtoDraft,
  createDtoResource,
  createDtoResourceField,
  createFieldSchema,
  createKotlinType,
  dtoClassSuffix,
  applyDtoClassName,
  applyDtoKind,
  normalizeDtoResource,
  validateDtoResource,
} from './dto-draft'
import ApiSchemaEditor from './ApiSchemaEditor.vue'
import KotlinTypeEditor from './KotlinTypeEditor.vue'

interface DtoCreationContext {
  featureId: number | string
  packageName: string
  contributorId: string
}

interface DtoFieldRow extends LowcodeDtoFieldDraft, Record<string, unknown> {
  metadataRowKey: string
}

const props = withDefaults(defineProps<{
  initialDtoCode?: string
  createRequest?: number
  creationContext?: DtoCreationContext
}>(), {
  initialDtoCode: '',
  createRequest: 0,
  creationContext: undefined,
})

const emit = defineEmits<{
  deleted: [dtoCode: string]
  saved: [dtoCode: string]
  selected: [dtoCode: string]
}>()

const api = new LowcodeApi()
const dtos = shallowRef<LowcodeDtoResourceSummary[]>([])
const models = shallowRef<LowcodeModelSummary[]>([])
const validationRules = shallowRef<LsiValidationRuleMetadata[]>([])
const dto = shallowRef<LowcodeDtoResourceDraft>(createDtoResource(props.creationContext))
const validation = ref<LowcodeValidationResult>()
const preview = ref<LowcodeDtoPreview>()
const reuseAnalysis = ref<LowcodeDtoReuseAnalysis>()
const selectedFile = ref<LowcodePreviewFile>()
const busy = ref(false)
const dirty = ref(false)
const notice = ref('')
const noticeTone = ref<'error' | 'success' | 'warning'>('success')
const view = ref<'assistant' | 'editor' | 'preview' | 'reuse'>('editor')
const assistantDraftRevision = ref(0)

const independent = computed(() => !dto.value.sourceModelCode)
const structure = computed(() => dto.value.kind === 'STRUCTURE')
const dtoTypeColumnKey = computed(() => structure.value
  ? 'kotlinType'
  : independent.value ? 'schema' : 'sourcePath')
const assistantDraftIdentity = computed(() => `${dto.value.id ?? 'new'}:${assistantDraftRevision.value}`)
const dtoFieldRows = computed<DtoFieldRow[]>(() => dto.value.fields.map((field, index) => ({
  ...field,
  metadataRowKey: `${field.sourcePath || field.name || 'new'}:${index}`,
})))
const fieldTableDescriptor = computed<MetadataTableDescriptor<DtoFieldRow>>(() => {
  const value: Omit<MetadataTableDescriptor<DtoFieldRow>, 'revision'> = {
    tableId: `dto.fields:${dto.value.dtoCode || 'new'}`,
    rowIdentityKey: 'sourcePath|index',
    rowKey: (field) => field.metadataRowKey,
    columns: [
      { key: 'name', label: '字段名', kind: 'scalar', editable: true, context: true },
      { key: 'description', label: '字段说明', kind: 'scalar', editable: true, context: true },
      structure.value
        ? { key: 'kotlinType', label: 'Kotlin 类型', kind: 'object', editable: true, context: true }
        : independent.value
          ? { key: 'schema', label: 'API 类型', kind: 'object', editable: true, context: true }
          : { key: 'sourcePath', label: '来源属性', kind: 'scalar', editable: true, context: true },
      {
        key: 'nullability', label: '可空策略', kind: 'enum', editable: true, context: true,
        options: [
          ...(!independent.value ? [{ label: '继承', value: 'INHERIT' }] : []),
          { label: '非空', value: 'NON_NULL' },
          { label: '可空', value: 'NULLABLE' },
        ],
      },
      { key: 'validations', label: '校验', kind: 'collection', editable: !structure.value, context: true },
    ],
    operations: ['replace', 'fill', 'custom'],
  }
  return { ...value, revision: createTableRevision(value, dtoFieldRows.value) }
})
const modelOptions = computed(() => models.value.filter((model) =>
  model.modelType === 'ENTITY' && (!dto.value.contributorId || model.contributorId === dto.value.contributorId)))
const schemaTypeOptions = computed<LowcodeApiTypeOption[]>(() => [
  ...modelOptions.value.map((model) => ({
    modelCode: model.modelCode,
    dtoCode: '',
    className: model.className || model.modelCode,
    kind: 'ENTITY' as const,
  })),
  ...dtos.value
    .filter((candidate) =>
      candidate.status === 1
      && candidate.kind !== 'STRUCTURE'
      && candidate.dtoCode !== dto.value.dtoCode
      && (!dto.value.contributorId || candidate.contributorId === dto.value.contributorId))
    .map((candidate) => ({
      modelCode: null,
      dtoCode: candidate.dtoCode,
      className: candidate.className,
      kind: candidate.kind,
    })),
])

onMounted(() => run(async () => {
  await refreshResources()
  if (props.initialDtoCode) await selectDtoByCode(props.initialDtoCode)
  else if (props.createRequest > 0) createDto()
}))

watch(() => props.initialDtoCode, (dtoCode) => {
  if (dtoCode && dtoCode !== dto.value.dtoCode) void selectDtoByCode(dtoCode)
})

watch(() => props.createRequest, (request, previous) => {
  if (request > previous) createDto()
})

function patchDto(patch: Partial<LowcodeDtoResourceDraft>): void {
  dto.value = { ...dto.value, ...patch }
  markDirty()
}

function updateName(name: string): void {
  const previousCode = toPinyinSnakeIdentifier(dto.value.name)
  const suffix = dtoClassSuffix(dto.value.kind)
  const previousClass = dto.value.name ? `${toClassName(dto.value.name)}${suffix}` : ''
  const nextClass = `${toClassName(name)}${suffix}`
  const identityFollowsName = (!dto.value.dtoCode || dto.value.dtoCode === previousCode)
    && (!dto.value.className || dto.value.className === previousClass)
  patchDto(identityFollowsName
    ? applyDtoClassName({ ...dto.value, name, dtoCode: '' }, nextClass)
    : { name })
}

function updateKind(kind: LowcodeDtoResourceDraft['kind']): void {
  dto.value = applyDtoKind(dto.value, kind)
  markDirty()
}

function updateClassName(className: string): void {
  patchDto(applyDtoClassName(dto.value, className))
}

function updateSourceModel(sourceModelCode: string): void {
  if (structure.value) return
  patchDto({
    sourceModelCode: sourceModelCode || null,
    selectionMode: 'EXPLICIT',
    excludedPaths: [],
    fields: [],
  })
}

function addField(): void {
  patchDto({ fields: [...dto.value.fields, createDtoResourceField(dto.value.kind, independent.value)] })
}

function patchField(index: number, patch: Partial<LowcodeDtoFieldDraft>): void {
  patchDto({ fields: dto.value.fields.map((field, fieldIndex) => fieldIndex === index ? { ...field, ...patch } : field) })
}

function applyFieldPatches(application: MetadataPatchApplication<DtoFieldRow>): void {
  const fields = application.rows.map(({ metadataRowKey: _, ...field }) => field as LowcodeDtoFieldDraft)
  patchDto({ fields })
}

function applyAssistantDto(value: JsonObject): void {
  const generated = applyAgentDtoDraft(dto.value, value)
  commitAssistantDto(generated)
}

function applyAssistantDisplayText(value: JsonObject): void {
  commitAssistantDto(value as LowcodeDtoResourceDraft)
}

function commitAssistantDto(generated: LowcodeDtoResourceDraft): void {
  dto.value = props.creationContext
    ? {
        ...generated,
        featureId: props.creationContext.featureId,
        packageName: props.creationContext.packageName,
        contributorId: props.creationContext.contributorId,
      }
    : generated
  markDirty()
}

function deleteField(index: number): void {
  patchDto({ fields: dto.value.fields.filter((_, fieldIndex) => fieldIndex !== index) })
}

function fieldValueKind(field: LowcodeDtoFieldDraft): LsiValidationValueKind | undefined {
  if (independent.value) {
    if (field.schema?.type === 'string') return 'TEXT'
    if (field.schema?.type === 'array' && field.schema.items?.type === 'string') return 'TEXT_COLLECTION'
    if (field.schema?.type === 'array' || field.schema?.type === 'object') return 'COLLECTION'
    return undefined
  }
  const sourceType = models.value
    .find((model) => model.modelCode === dto.value.sourceModelCode)
    ?.fields?.find((property) => property.fieldCode === field.sourcePath)
    ?.kotlinType
  if (!sourceType) return undefined
  if (sourceType === 'String' || sourceType === 'kotlin.String') return 'TEXT'
  if (/^(?:kotlin\.collections\.)?(?:List|Set)<(?:kotlin\.)?String>$/.test(sourceType)) return 'TEXT_COLLECTION'
  if (/^(?:kotlin\.collections\.)?(?:List|Set|Map)</.test(sourceType)) return 'COLLECTION'
  return undefined
}

function availableValidationRules(field: LowcodeDtoFieldDraft): LsiValidationRuleMetadata[] {
  if (structure.value) return []
  const valueKind = fieldValueKind(field)
  return validationRules.value.filter((rule) => valueKind && rule.supportedValueKinds.includes(valueKind))
}

function validationConfigured(field: LowcodeDtoFieldDraft, code: string): boolean {
  return field.validations.some((rule) => rule.code === code)
}

function toggleValidation(index: number, metadata: LsiValidationRuleMetadata, enabled: boolean): void {
  const field = dto.value.fields[index]
  const validations = enabled
    ? [...field.validations.filter((rule) => rule.code !== metadata.code), {
        code: metadata.code,
        message: null,
        parameters: Object.fromEntries(metadata.parameters.map((parameter) => [parameter.code, ''])),
      }]
    : field.validations.filter((rule) => rule.code !== metadata.code)
  patchField(index, { validations })
}

function updateValidationParameter(index: number, code: string, parameterCode: string, value: string): void {
  const field = dto.value.fields[index]
  patchField(index, {
    validations: field.validations.map((rule) => rule.code === code
      ? { ...rule, parameters: { ...rule.parameters, [parameterCode]: value } }
      : rule),
  })
}

function updateValidationMessage(index: number, code: string, message: string): void {
  const field = dto.value.fields[index]
  patchField(index, {
    validations: field.validations.map((rule) => rule.code === code
      ? { ...rule, message: message.trim() || null }
      : rule),
  })
}

async function saveDto(): Promise<void> {
  const errors = validateDtoResource(dto.value)
  if (errors.length) {
    notice.value = errors.join('；')
    noticeTone.value = 'error'
    return
  }
  await run(async () => {
    validation.value = await api.validateDto(dto.value)
    if (!validation.value.valid) {
      notice.value = validation.value.errors.join('；')
      noticeTone.value = 'error'
      return
    }
    const saved = await api.saveDto(dto.value)
    const id = dto.value.id ?? saved
    await refreshResources()
    const selected = dtos.value.find((item) => String(item.id) === String(id))
    if (selected) dto.value = normalizeDtoResource(await api.dtoDetail(selected.id))
    dirty.value = false
    notice.value = 'DTO 已保存'
    noticeTone.value = 'success'
    emit('saved', dto.value.dtoCode)
  })
}

async function deleteDto(): Promise<void> {
  if (!dto.value.id || !window.confirm(`删除 DTO ${dto.value.dtoCode}？`)) return
  const dtoCode = dto.value.dtoCode
  await run(async () => {
    await api.deleteDto(dto.value.id as number | string)
    emit('deleted', dtoCode)
  })
}

async function loadPreview(): Promise<void> {
  if (!dto.value.id || dirty.value) {
    notice.value = dirty.value ? '请先保存当前修改' : '请先保存 DTO'
    noticeTone.value = 'warning'
    return
  }
  await run(async () => {
    preview.value = await api.previewDto(dto.value.id as number | string)
    selectedFile.value = preview.value.files[0]
    view.value = 'preview'
  })
}

async function loadReuseAnalysis(): Promise<void> {
  view.value = 'reuse'
  const errors = validateDtoResource(dto.value)
  if (errors.length) {
    notice.value = errors.join('；')
    noticeTone.value = 'error'
    return
  }
  await run(async () => {
    reuseAnalysis.value = await api.analyzeDtoReuse(dto.value)
  })
}

function changeView(next: string | number): void {
  if (next === 'preview') void loadPreview()
  else if (next === 'reuse') void loadReuseAnalysis()
  else if (next === 'assistant') view.value = 'assistant'
  else view.value = 'editor'
}

async function downloadDto(): Promise<void> {
  if (!dto.value.id || dirty.value) return
  await run(() => api.downloadDto(dto.value.id as number | string))
}

async function refreshWorkspace(): Promise<void> {
  if (dirty.value && !window.confirm('放弃当前未保存的修改并刷新？')) return
  await refreshResources()
  if (dto.value.dtoCode) await selectDtoByCode(dto.value.dtoCode)
}

async function refreshResources(): Promise<void> {
  ;[dtos.value, models.value, validationRules.value] = await Promise.all([
    api.dtos(),
    api.models(),
    api.dtoValidationRules(),
  ])
}

async function selectDtoByCode(dtoCode: string): Promise<void> {
  if (!dtos.value.length) await refreshResources()
  const selected = dtos.value.find((item) => item.dtoCode === dtoCode)
  if (!selected) return
  dto.value = normalizeDtoResource(await api.dtoDetail(selected.id))
  assistantDraftRevision.value += 1
  dirty.value = false
  view.value = 'editor'
  reuseAnalysis.value = undefined
  emit('selected', dtoCode)
}

function createDto(): void {
  dto.value = createDtoResource(props.creationContext)
  assistantDraftRevision.value += 1
  dirty.value = true
  view.value = 'editor'
}

function markDirty(): void {
  dirty.value = true
  validation.value = undefined
  preview.value = undefined
  reuseAnalysis.value = undefined
}

async function run(action: () => Promise<unknown>): Promise<void> {
  busy.value = true
  notice.value = ''
  try {
    await action()
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '操作失败'
    noticeTone.value = 'error'
  } finally {
    busy.value = false
  }
}

function textValue(event: Event): string {
  return (event.target as HTMLInputElement).value
}

function toClassName(value: string): string {
  return toPinyinSnakeIdentifier(value).split('_').filter(Boolean)
    .map((part) => `${part.charAt(0).toUpperCase()}${part.slice(1)}`).join('')
}

function checkedValue(event: Event): boolean {
  return (event.target as HTMLInputElement).checked
}

function fileLanguage(file?: LowcodePreviewFile): string {
  return file?.filePath.endsWith('.kt') ? 'kotlin' : 'text'
}

function candidateOtherName(candidate: LowcodeDtoReuseAnalysis['candidates'][number]): string {
  return candidate.leftQualifiedName === reuseAnalysis.value?.draftQualifiedName
    ? candidate.rightQualifiedName
    : candidate.leftQualifiedName
}

function candidateCoverage(candidate: LowcodeDtoReuseAnalysis['candidates'][number]): number {
  return candidate.leftQualifiedName === reuseAnalysis.value?.draftQualifiedName
    ? candidate.leftCoverage
    : candidate.rightCoverage
}

function candidateOrigins(candidate: LowcodeDtoReuseAnalysis['candidates'][number]): string[] {
  const qualifiedName = candidateOtherName(candidate)
  return reuseAnalysis.value?.structures.find((item) => item.qualifiedName === qualifiedName)?.origins ?? []
}

function relationLabel(relation: LowcodeDtoReuseAnalysis['candidates'][number]['relation']): string {
  if (relation === 'EXACT') return '完全相同'
  if (relation === 'CONTAINS') return '包含'
  return '高相似'
}

function formatPercent(value: number): string {
  return `${Math.round(value * 100)}%`
}

function formatSnapshotTime(value: number): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <main class="studio-main embedded-studio-main dto-studio-main">
    <section class="model-workspace">
      <div class="workspace-toolbar">
        <Tabs :model-value="view" class="workspace-tabs" @update:model-value="changeView">
          <TabsList class="workspace-view-tabs" aria-label="DTO 工作区视图">
            <TabsTrigger value="editor">DTO</TabsTrigger>
            <TabsTrigger value="assistant">智能体</TabsTrigger>
            <TabsTrigger value="preview">生成结果</TabsTrigger>
            <TabsTrigger value="reuse"><GitCompareArrows />复用分析</TabsTrigger>
          </TabsList>
        </Tabs>
        <div class="workspace-title"><strong>{{ dto.name || '新 DTO' }}</strong><span>{{ dto.contributorId ?? dto.className ?? '未保存' }}</span></div>
        <div class="workspace-actions" role="group" aria-label="DTO 操作">
          <IconButton :disabled="busy" :icon="RefreshCw" label="刷新 DTO" tooltip @click="refreshWorkspace" />
          <IconButton v-if="dto.id" :disabled="busy || dirty" :icon="Code" label="查看生成结果" tooltip @click="loadPreview" />
          <IconButton v-if="dto.id" :disabled="busy || dirty" :icon="Download" label="下载 DTO" tooltip @click="downloadDto" />
          <IconButton v-if="dto.id" :disabled="busy" :icon="Trash2" label="删除 DTO" tooltip variant="danger" @click="deleteDto" />
          <Button :disabled="busy" size="sm" @click="saveDto"><Save data-icon="inline-start" />保存 DTO</Button>
        </div>
      </div>

      <div v-if="notice" class="notice-bar" :class="noticeTone">{{ notice }}</div>

      <div
        v-if="view === 'editor' || view === 'assistant'"
        class="metadata-authoring-layout"
        :class="{ 'assistant-focused': view === 'assistant' }">
        <div v-if="view === 'editor'" class="editor-scroll metadata-editor-pane">
          <section class="designer-section">
          <header class="designer-section-heading"><div><h2>DTO 定义</h2><span>{{ independent ? '独立结构' : '实体投影' }}</span></div></header>
          <div class="form-grid model-form-grid">
            <label class="form-field"><span>DTO 注释 <b>*</b></span><input :value="dto.name" @input="updateName(textValue($event))"></label>
            <label class="form-field"><span>类名 <b>*</b></span><input :value="dto.className" @input="updateClassName(textValue($event))"></label>
            <label class="form-field"><span>用途</span><select :value="dto.kind" @change="updateKind(textValue($event) as LowcodeDtoResourceDraft['kind'])"><option value="OUTPUT">OUTPUT</option><option value="INPUT">INPUT</option><option value="STRUCTURE">STRUCTURE</option></select></label>
            <label class="form-field"><span>Kotlin 可见性</span><select :value="dto.visibility" @change="patchDto({ visibility: textValue($event) as LowcodeDtoResourceDraft['visibility'] })"><option value="PUBLIC">PUBLIC</option><option value="INTERNAL">INTERNAL</option></select></label>
            <label class="form-field form-field-wide"><span>业务包名 <b>*</b></span><input :readonly="creationContext != null" :value="dto.packageName" @input="patchDto({ packageName: textValue($event) })"></label>
            <label class="form-field"><span>Contributor ID <b>*</b></span><input :readonly="creationContext != null" :value="dto.contributorId ?? ''" @input="patchDto({ contributorId: textValue($event) || null })"></label>
            <label v-if="!structure" class="form-field"><span>来源实体</span><select :value="dto.sourceModelCode ?? ''" @change="updateSourceModel(textValue($event))"><option value="">无，独立 DTO</option><option v-for="model in modelOptions" :key="model.modelCode" :value="model.modelCode">{{ model.name }}<template v-if="model.className"> · {{ model.className }}</template></option></select></label>
            <label v-if="!independent" class="form-field"><span>字段策略</span><select :value="dto.selectionMode" @change="patchDto({ selectionMode: textValue($event) as LowcodeDtoResourceDraft['selectionMode'] })"><option value="EXPLICIT">手动选择</option><option value="ALL_SCALAR_FIELDS">全部标量</option><option value="ALL_TABLE_FIELDS">全部表字段</option><option value="ALL_DEEP_FIELDS">全部深层字段</option></select></label>
            <label class="form-field"><span>版本</span><input :value="dto.version" min="1" type="number" @input="patchDto({ version: Number(textValue($event)) || 1 })"></label>
            <label class="switch-field"><input :checked="dto.status === 1" type="checkbox" @change="patchDto({ status: checkedValue($event) ? 1 : 0 })"><span><strong>启用 DTO</strong><small>{{ dto.status === 1 ? '参与生成' : '已停用' }}</small></span></label>
            <label class="form-field form-field-wide"><span>说明</span><textarea :value="dto.description ?? ''" rows="2" @input="patchDto({ description: textValue($event) || null })" /></label>
          </div>
          </section>

          <section class="designer-section">
          <header class="designer-section-heading"><div><h2>字段</h2><span>{{ dto.fields.length }} 个字段</span></div><Button size="sm" @click="addField"><Plus data-icon="inline-start" />添加字段</Button></header>
          <div class="table-shell">
            <Table>
              <TableHeader><TableRow>
                <MetadataTableHead mode="agent">
                  字段名
                  <template #action><MetadataColumnAdjustDialog column-key="name" :context="{ dtoCode: dto.dtoCode, dtoName: dto.name }" :descriptor="fieldTableDescriptor" :rows="dtoFieldRows" @apply="applyFieldPatches" /></template>
                </MetadataTableHead>
                <MetadataTableHead mode="agent">
                  字段说明
                  <template #action><MetadataColumnAdjustDialog column-key="description" :context="{ dtoCode: dto.dtoCode, dtoName: dto.name }" :descriptor="fieldTableDescriptor" :rows="dtoFieldRows" @apply="applyFieldPatches" /></template>
                </MetadataTableHead>
                <MetadataTableHead mode="agent">
                  {{ structure ? 'Kotlin 类型' : independent ? 'API 类型' : '来源属性' }}
                  <template #action><MetadataColumnAdjustDialog :column-key="dtoTypeColumnKey" :context="{ dtoCode: dto.dtoCode, dtoName: dto.name }" :descriptor="fieldTableDescriptor" :rows="dtoFieldRows" @apply="applyFieldPatches" /></template>
                </MetadataTableHead>
                <MetadataTableHead mode="agent">
                  可空策略
                  <template #action><MetadataColumnAdjustDialog column-key="nullability" :context="{ dtoCode: dto.dtoCode, dtoName: dto.name }" :descriptor="fieldTableDescriptor" :rows="dtoFieldRows" @apply="applyFieldPatches" /></template>
                </MetadataTableHead>
                <MetadataTableHead :mode="structure ? 'system' : 'agent'">
                  校验
                  <template v-if="!structure" #action><MetadataColumnAdjustDialog column-key="validations" :context="{ dtoCode: dto.dtoCode, dtoName: dto.name }" :descriptor="fieldTableDescriptor" :rows="dtoFieldRows" @apply="applyFieldPatches" /></template>
                </MetadataTableHead>
                <MetadataTableHead aria-label="操作" mode="system" />
              </TableRow></TableHeader>
              <TableBody>
                <TableRow v-for="(field, index) in dto.fields" :key="index">
                  <MetadataTableCell column-key="name" :context="{ dtoCode: dto.dtoCode, dtoName: dto.name }" :descriptor="fieldTableDescriptor" :row="dtoFieldRows[index]" :rows="dtoFieldRows" @apply="applyFieldPatches"><input :value="field.name" @input="patchField(index, { name: textValue($event), sourcePath: field.sourcePath || textValue($event) })"></MetadataTableCell>
                  <MetadataTableCell class="dto-description-cell" column-key="description" :context="{ dtoCode: dto.dtoCode, dtoName: dto.name }" :descriptor="fieldTableDescriptor" :row="dtoFieldRows[index]" :rows="dtoFieldRows" @apply="applyFieldPatches"><input :value="field.description ?? ''" placeholder="字段说明" @input="patchField(index, { description: textValue($event) || null })"></MetadataTableCell>
                  <MetadataTableCell v-if="structure" class="dto-kotlin-type-cell" column-key="kotlinType" :context="{ dtoCode: dto.dtoCode, dtoName: dto.name }" :descriptor="fieldTableDescriptor" :row="dtoFieldRows[index]" :rows="dtoFieldRows" @apply="applyFieldPatches">
                    <KotlinTypeEditor
                      :model-value="field.kotlinType ?? createKotlinType()"
                      @update:model-value="patchField(index, { kotlinType: $event, schema: null })"
                    />
                  </MetadataTableCell>
                  <MetadataTableCell v-else-if="independent" class="dto-schema-cell" column-key="schema" :context="{ dtoCode: dto.dtoCode, dtoName: dto.name }" :descriptor="fieldTableDescriptor" :row="dtoFieldRows[index]" :rows="dtoFieldRows" @apply="applyFieldPatches">
                    <ApiSchemaEditor
                      :model-value="field.schema ?? createFieldSchema('string')"
                      :type-options="schemaTypeOptions"
                      @update:model-value="patchField(index, { schema: $event })"
                    />
                  </MetadataTableCell>
                  <MetadataTableCell v-else column-key="sourcePath" :context="{ dtoCode: dto.dtoCode, dtoName: dto.name }" :descriptor="fieldTableDescriptor" :row="dtoFieldRows[index]" :rows="dtoFieldRows" @apply="applyFieldPatches"><input :value="field.sourcePath" placeholder="实体属性路径" @input="patchField(index, { sourcePath: textValue($event) })"></MetadataTableCell>
                  <MetadataTableCell column-key="nullability" :context="{ dtoCode: dto.dtoCode, dtoName: dto.name }" :descriptor="fieldTableDescriptor" :row="dtoFieldRows[index]" :rows="dtoFieldRows" @apply="applyFieldPatches"><select :value="field.nullability" @change="patchField(index, { nullability: textValue($event) as LowcodeDtoFieldDraft['nullability'] })"><option v-if="!independent" value="INHERIT">继承</option><option value="NON_NULL">非空</option><option value="NULLABLE">可空</option></select></MetadataTableCell>
                  <MetadataTableCell v-if="!structure" class="dto-validation-cell" column-key="validations" :context="{ dtoCode: dto.dtoCode, dtoName: dto.name }" :descriptor="fieldTableDescriptor" :row="dtoFieldRows[index]" :rows="dtoFieldRows" @apply="applyFieldPatches">
                    <label v-for="rule in availableValidationRules(field)" :key="rule.code" class="dto-validation-rule" :title="rule.description">
                      <span><input :checked="validationConfigured(field, rule.code)" type="checkbox" @change="toggleValidation(index, rule, checkedValue($event))">{{ rule.name }}</span>
                      <template v-if="validationConfigured(field, rule.code)">
                        <input
                          :placeholder="rule.defaultMessage"
                          :value="field.validations.find((value) => value.code === rule.code)?.message ?? ''"
                          @change="updateValidationMessage(index, rule.code, textValue($event))">
                        <input
                          v-for="parameter in rule.parameters"
                          :key="parameter.code"
                          :max="parameter.maximum ?? undefined"
                          :min="parameter.minimum ?? undefined"
                          :placeholder="parameter.name"
                          :title="parameter.description"
                          :type="parameter.kind === 'INTEGER' ? 'number' : 'text'"
                          :value="field.validations.find((value) => value.code === rule.code)?.parameters?.[parameter.code] ?? ''"
                          @change="updateValidationParameter(index, rule.code, parameter.code, textValue($event))">
                      </template>
                    </label>
                    <span v-if="!availableValidationRules(field).length" class="dto-validation-empty">无适用规则</span>
                  </MetadataTableCell>
                  <TableCell v-else class="dto-validation-cell"><span class="dto-validation-empty">不适用</span></TableCell>
                  <TableCell><IconButton :icon="Trash2" label="删除字段" tooltip variant="danger" @click="deleteField(index)" /></TableCell>
                </TableRow>
                <TableRow v-if="!dto.fields.length"><TableCell colspan="6" class="inline-empty">尚未配置字段</TableCell></TableRow>
              </TableBody>
            </Table>
          </div>
          </section>
        </div>
        <MetadataAssistantPanel
          :draft="dto"
          :draft-identity="assistantDraftIdentity"
          :focused="view === 'assistant'"
          :related-models="modelOptions"
          scope="dto"
          @apply="applyAssistantDto"
          @apply-display-text="applyAssistantDisplayText" />
      </div>

      <div v-else-if="view === 'reuse'" class="editor-scroll metadata-editor-pane reuse-analysis-pane">
        <section class="designer-section">
          <header class="designer-section-heading">
            <div><h2>复用候选</h2><span>{{ reuseAnalysis?.candidates.length ?? 0 }} 个</span></div>
            <Badge v-if="reuseAnalysis" :variant="reuseAnalysis.metadataStale ? 'destructive' : 'secondary'">
              {{ reuseAnalysis.metadataStale ? '元数据已变更' : '快照有效' }}
            </Badge>
          </header>
          <div v-if="reuseAnalysis" class="reuse-snapshot-meta">
            <span>{{ reuseAnalysis.draftQualifiedName }}</span>
            <span>{{ formatSnapshotTime(reuseAnalysis.snapshotGeneratedAtEpochMillis) }}</span>
          </div>
          <div class="table-shell">
            <Table>
              <TableHeader><TableRow>
                <MetadataTableHead mode="system">候选类型</MetadataTableHead>
                <MetadataTableHead mode="system">关系</MetadataTableHead>
                <MetadataTableHead mode="system">共享字段</MetadataTableHead>
                <MetadataTableHead mode="system">草稿覆盖率</MetadataTableHead>
                <MetadataTableHead mode="system">Jaccard</MetadataTableHead>
                <MetadataTableHead mode="system">来源</MetadataTableHead>
                <MetadataTableHead mode="system">兼容提示</MetadataTableHead>
              </TableRow></TableHeader>
              <TableBody>
                <TableRow v-for="candidate in reuseAnalysis?.candidates ?? []" :key="`${candidate.leftQualifiedName}:${candidate.rightQualifiedName}`">
                  <TableCell class="reuse-qualified-name">{{ candidateOtherName(candidate) }}</TableCell>
                  <TableCell><Badge variant="outline">{{ relationLabel(candidate.relation) }}</Badge></TableCell>
                  <TableCell>{{ candidate.sharedProperties.join(', ') }}</TableCell>
                  <TableCell>{{ formatPercent(candidateCoverage(candidate)) }}</TableCell>
                  <TableCell>{{ formatPercent(candidate.jaccard) }}</TableCell>
                  <TableCell><div class="reuse-origins"><Badge v-for="origin in candidateOrigins(candidate)" :key="origin" variant="secondary">{{ origin }}</Badge></div></TableCell>
                  <TableCell>{{ candidate.constructorOrderCompatible ? '顺序一致' : '顺序不同' }} · {{ candidate.defaultValuesCompatible ? '默认值一致' : '默认值不同' }}</TableCell>
                </TableRow>
                <TableRow v-if="!reuseAnalysis?.candidates.length"><TableCell colspan="7" class="inline-empty">暂无可复用候选</TableCell></TableRow>
              </TableBody>
            </Table>
          </div>
        </section>
      </div>

      <div v-else class="preview-layout">
        <nav class="file-list" aria-label="DTO 生成文件"><button v-for="file in preview?.files ?? []" :key="file.filePath" :class="{ active: file.filePath === selectedFile?.filePath }" type="button" @click="selectedFile = file"><Code /><span>{{ file.filePath }}</span></button></nav>
        <CodeBlock v-if="selectedFile" :content="selectedFile.content" :language="fileLanguage(selectedFile)" line-numbers />
      </div>
    </section>
  </main>
</template>

<style scoped>
.dto-schema-cell {
  min-width: 520px;
}

.dto-description-cell {
  min-width: 220px;
}

.dto-kotlin-type-cell {
  min-width: 440px;
}

.workspace-tabs {
  flex: none;
  gap: 0;
}

.reuse-analysis-pane {
  min-width: 0;
}

.reuse-snapshot-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-bottom: 12px;
  color: var(--muted-foreground);
  font-family: var(--font-mono);
  font-size: 12px;
}

.reuse-qualified-name {
  min-width: 260px;
  font-family: var(--font-mono);
  font-size: 12px;
}

.reuse-origins {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.dto-validation-cell {
  min-width: 220px;
}

.dto-validation-rule {
  display: grid;
  gap: 4px;
  margin-bottom: 7px;
}

.dto-validation-rule > span {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.dto-validation-rule input[type='checkbox'] {
  width: 15px;
  height: 15px;
}

.dto-validation-empty {
  color: var(--muted-foreground);
  font-size: 11px;
}
</style>
