<script setup lang="ts">
import {
  ChevronLeft,
  ChevronRight,
  Code,
  Download,
  Plus,
  RefreshCw,
  Save,
  Trash2,
} from '@lucide/vue'
import { computed, onMounted, ref, shallowRef, watch } from 'vue'

import CodeBlock from '@/components/composed/code-block/CodeBlock.vue'
import IconButton from '@/components/composed/icon-button/IconButton.vue'
import MetadataAssistantPanel from '@/components/composed/metadata-assistant/MetadataAssistantPanel.vue'
import SearchInput from '@/components/composed/search-input/SearchInput.vue'
import { Button } from '@/components/generated/shadcn/button'

import { LowcodeApi } from '../../lowcode-api'
import type {
  JsonObject,
  LowcodeModelDraft,
  LowcodeModelDesignerSection,
  LowcodeModelRecentChanges,
  LowcodeModelSummary,
  LowcodePreview,
  LowcodePreviewFile,
  LowcodeRouteDraft,
  LowcodeValidationResult,
} from '../../types'
import ControllerDesigner from './ControllerDesigner.vue'
import ModelDesigner from './ModelDesigner.vue'
import { applyAgentModelDraft, applyModelFeatureLocation, diffModelDraft, normalizeModelDraft, validateModelDraft } from './model-draft'
import type { ModelFeatureLocation } from './model-draft'

type WorkspaceView = 'editor' | 'preview'
type NoticeTone = 'error' | 'success' | 'warning'
type ModelStudioMode = 'controller' | 'entity'

const props = withDefaults(defineProps<{
  embedded?: boolean
  initialModelCode?: string
  initialSection?: LowcodeModelDesignerSection
  createRequest?: number
  creationContext?: ModelFeatureLocation
  mode?: ModelStudioMode
  showIdentityConfiguration?: boolean
}>(), {
  embedded: false,
  initialModelCode: '',
  initialSection: 'model',
  createRequest: 0,
  creationContext: undefined,
  mode: 'entity',
  showIdentityConfiguration: true,
})

const emit = defineEmits<{
  deleted: [modelCode: string]
  saved: [modelCode: string]
  selected: [modelCode: string]
}>()

const api = new LowcodeApi()
const pageSize = 10
const ready = ref(false)
const models = shallowRef<LowcodeModelSummary[]>([])
const modelOptions = shallowRef<LowcodeModelSummary[]>([])
const selectedId = ref<number | string>()
const model = shallowRef<LowcodeModelDraft>(normalizeModelDraft({}))
const validation = ref<LowcodeValidationResult>()
const preview = ref<LowcodePreview>()
const selectedFile = ref<LowcodePreviewFile>()
const search = ref('')
const notice = ref('')
const noticeTone = ref<NoticeTone>('success')
const busy = ref(false)
const dirty = ref(false)
const view = ref<WorkspaceView>('editor')
const pageNumber = ref(0)
const serverTotalRowCount = ref(0)
const serverTotalPageCount = ref(0)
const assistantDraftRevision = ref(0)
const recentAssistantChanges = ref<LowcodeModelRecentChanges>({ sections: [], fieldKeys: [] })

const selectedModel = computed(() => modelOptions.value.find((item) => String(item.id) === String(selectedId.value)))
const assistantDraftIdentity = computed(() => `${selectedId.value ?? 'new'}:${assistantDraftRevision.value}`)
const searchKeyword = computed(() => search.value.trim().toLowerCase())
const matchingModelOptions = computed(() => {
  const keyword = searchKeyword.value
  if (!keyword) {
    return modelOptions.value
  }
  return modelOptions.value.filter((item) =>
    `${item.name} ${item.modelCode} ${item.contributorId ?? ''}`.toLowerCase().includes(keyword),
  )
})
const visibleModels = computed(() => {
  if (!searchKeyword.value) {
    return models.value
  }
  const start = pageNumber.value * pageSize
  return matchingModelOptions.value.slice(start, start + pageSize)
})
const totalRowCount = computed(() => searchKeyword.value
  ? matchingModelOptions.value.length
  : serverTotalRowCount.value)
const totalPageCount = computed(() => searchKeyword.value
  ? Math.ceil(matchingModelOptions.value.length / pageSize)
  : serverTotalPageCount.value)
const pageRangeLabel = computed(() => {
  if (totalRowCount.value === 0) {
    return '0 项'
  }
  const first = pageNumber.value * pageSize + 1
  const last = Math.min(first + visibleModels.value.length - 1, totalRowCount.value)
  return `${first}-${last} / ${totalRowCount.value}`
})
const pageLabel = computed(() => totalPageCount.value === 0
  ? '0 / 0'
  : `${pageNumber.value + 1} / ${totalPageCount.value}`)
const canGoPrevious = computed(() => pageNumber.value > 0)
const canGoNext = computed(() => pageNumber.value + 1 < totalPageCount.value)
const canPersist = computed(() => ready.value)
const modelResourceLabel = computed(() => props.mode === 'controller'
  ? 'Controller'
  : ({
      EMBEDDABLE: '嵌入类型',
      ENTITY: '实体',
      MAPPED_SUPERCLASS: '映射父类',
    })[model.value.modelType])
const saveModelLabel = computed(() => `保存${modelResourceLabel.value}`)
const editorTitle = computed(() => props.mode === 'controller' ? 'Controller' : '模型')

watch(search, (value) => {
  pageNumber.value = 0
  if (!value.trim()) {
    void run(refreshModels)
  }
})

onMounted(async () => {
  await run(async () => {
    model.value = normalizeModelDraft({})
    ready.value = true
    await refreshModelData()
    if (props.initialModelCode) {
      await selectModelByCode(props.initialModelCode)
    } else if (props.createRequest > 0) {
      createModel()
    }
  })
})

watch(() => props.initialModelCode, (modelCode) => {
  if (modelCode && modelCode !== model.value.modelCode) {
    void selectModelByCode(modelCode)
  }
})

watch(() => props.createRequest, (request, previous) => {
  if (request > previous && ready.value) {
    createModel()
  }
})

async function refreshModelData(): Promise<void> {
  modelOptions.value = await api.models()
  if (searchKeyword.value) {
    clampSearchPage()
    return
  }
  await refreshModels()
}

async function refreshModels(requestedPageNumber = pageNumber.value): Promise<void> {
  let resolvedPageNumber = requestedPageNumber
  let page = await api.modelPage(resolvedPageNumber, pageSize)
  const lastPageNumber = Math.max(page.totalPageCount - 1, 0)
  if (resolvedPageNumber > lastPageNumber) {
    resolvedPageNumber = lastPageNumber
    page = await api.modelPage(resolvedPageNumber, pageSize)
  }
  pageNumber.value = resolvedPageNumber
  models.value = page.rows
  serverTotalRowCount.value = page.totalRowCount
  serverTotalPageCount.value = page.totalPageCount
}

function clampSearchPage(): void {
  const lastPageNumber = Math.max(Math.ceil(matchingModelOptions.value.length / pageSize) - 1, 0)
  pageNumber.value = Math.min(pageNumber.value, lastPageNumber)
}

async function changePage(nextPageNumber: number): Promise<void> {
  if (busy.value || nextPageNumber < 0 || nextPageNumber >= totalPageCount.value) {
    return
  }
  if (searchKeyword.value) {
    pageNumber.value = nextPageNumber
    return
  }
  await run(() => refreshModels(nextPageNumber))
}

async function selectModel(item: LowcodeModelSummary): Promise<void> {
  if (dirty.value && !window.confirm('放弃当前未保存的修改？')) {
    emit('selected', model.value.modelCode)
    return
  }
  await run(async () => {
    const detail = await api.detail(item.id)
    selectedId.value = item.id
    const command = normalizeModelDraft(detail)
    model.value = applyModelFeatureLocation(command, props.creationContext)
    clearRecentAssistantChanges()
    assistantDraftRevision.value += 1
    validation.value = undefined
    preview.value = undefined
    selectedFile.value = undefined
    dirty.value = false
    view.value = 'editor'
    emit('selected', item.modelCode)
  })
}

function createModel(): void {
  if (dirty.value && !window.confirm('放弃当前未保存的修改？')) {
    emit('selected', model.value.modelCode)
    return
  }
  selectedId.value = undefined
  const created = normalizeModelDraft({})
  model.value = applyModelFeatureLocation(created, props.creationContext)
  clearRecentAssistantChanges()
  assistantDraftRevision.value += 1
  validation.value = undefined
  preview.value = undefined
  selectedFile.value = undefined
  dirty.value = false
  view.value = 'editor'
  emit('selected', '')
}

function onModelChange(): void {
  clearRecentAssistantChanges()
  validation.value = undefined
  dirty.value = true
}

function updateRouteConfig(routeConfig: LowcodeRouteDraft): void {
  model.value = { ...model.value, routeConfig }
  onModelChange()
}

function applyAssistantModel(value: JsonObject): void {
  const current = model.value
  const generated = applyAgentModelDraft(current, value)
  commitAssistantModel(current, generated)
}

function applyAssistantDisplayText(value: JsonObject): void {
  const current = model.value
  const generated = value as LowcodeModelDraft
  commitAssistantModel(current, generated)
}

function commitAssistantModel(current: LowcodeModelDraft, generated: LowcodeModelDraft): void {
  recentAssistantChanges.value = diffModelDraft(current, generated)
  model.value = generated
  validation.value = undefined
  preview.value = undefined
  selectedFile.value = undefined
  dirty.value = true
  view.value = 'editor'
}

function clearRecentAssistantChanges(): void {
  recentAssistantChanges.value = { sections: [], fieldKeys: [] }
}

async function validateModel(): Promise<LowcodeValidationResult | undefined> {
  const draftErrors = validateModelDraft(model.value)
  if (draftErrors.length > 0) {
    notice.value = draftErrors.join('；')
    noticeTone.value = 'error'
    return undefined
  }
  let result: LowcodeValidationResult | undefined
  await run(async () => {
    result = await api.validate(model.value)
    validation.value = result
    if (!result.valid) {
      notice.value = result.errors.join('；')
      noticeTone.value = 'error'
      return
    }
    notice.value = result.warnings.length > 0
      ? `模型校验通过；${result.warnings.join('；')}`
      : '模型校验通过'
    noticeTone.value = result.warnings.length > 0 ? 'warning' : 'success'
  })
  return result
}

async function saveModel(): Promise<void> {
  const result = await validateModel()
  if (!result?.valid) {
    return
  }
  await run(async () => {
    const saved = await api.save(model.value)
    const id = model.value.id ?? saved
    dirty.value = false
    pageNumber.value = 0
    await refreshModelData()
    const persisted = modelOptions.value.find((item) => String(item.id) === String(id))
    if (persisted) {
      await selectModel(persisted)
    }
    notice.value = `${modelResourceLabel.value}已保存`
    noticeTone.value = 'success'
    emit('saved', model.value.modelCode)
  })
}

async function deleteModel(): Promise<void> {
  if (!selectedId.value || !window.confirm(`删除模型“${selectedModel.value?.name ?? model.value.name}”？`)) {
    return
  }
  await run(async () => {
    const deletedCode = selectedModel.value?.modelCode ?? model.value.modelCode
    await api.delete(selectedId.value as number | string)
    await refreshModelData()
    dirty.value = false
    createModel()
    notice.value = '模型已删除'
    noticeTone.value = 'success'
    emit('deleted', deletedCode)
  })
}

async function selectModelByCode(modelCode: string): Promise<void> {
  if (!modelOptions.value.length) {
    return
  }
  const item = modelOptions.value.find((candidate) => candidate.modelCode === modelCode)
  if (item) {
    await selectModel(item)
  }
}

async function loadPreview(): Promise<void> {
  if (!selectedId.value) {
    notice.value = '请先保存模型'
    noticeTone.value = 'warning'
    return
  }
  if (dirty.value) {
    notice.value = '请先保存当前修改，再查看生成结果'
    noticeTone.value = 'warning'
    return
  }
  await run(async () => {
    preview.value = await api.preview(selectedId.value as number | string)
    selectedFile.value = preview.value.files[0]
    view.value = 'preview'
  })
}

async function downloadModel(): Promise<void> {
  if (!selectedId.value) {
    notice.value = '请先保存模型'
    noticeTone.value = 'warning'
    return
  }
  if (dirty.value) {
    notice.value = '请先保存当前修改，再下载源码'
    noticeTone.value = 'warning'
    return
  }
  await run(() => api.download(selectedId.value as number | string))
}

async function refreshWorkspace(): Promise<void> {
  if (dirty.value && !window.confirm('放弃当前未保存的修改并刷新？')) {
    return
  }
  await refreshModelData()
  const selected = modelOptions.value.find((item) => String(item.id) === String(selectedId.value))
  if (selected) {
    const detail = await api.detail(selected.id)
    model.value = normalizeModelDraft(detail)
    clearRecentAssistantChanges()
    assistantDraftRevision.value += 1
    validation.value = undefined
    preview.value = undefined
    selectedFile.value = undefined
    dirty.value = false
    view.value = 'editor'
  }
}

async function run(action: () => Promise<void>): Promise<void> {
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

function fileLanguage(file?: LowcodePreviewFile): string {
  if (!file) {
    return 'text'
  }
  if (file.filePath.endsWith('.kt')) {
    return 'kotlin'
  }
  if (file.filePath.endsWith('.sql')) {
    return 'sql'
  }
  return 'text'
}
</script>

<template>
  <main class="studio-main model-studio-main" :class="{ 'embedded-studio-main': embedded }">
    <aside v-if="!embedded" class="model-sidebar">
      <div class="sidebar-toolbar">
        <SearchInput v-model="search" label="搜索模型" />
        <IconButton
          aria-label="新建模型"
          :icon="Plus"
          label="新建模型"
          tooltip
          @click="createModel"
        />
      </div>
      <div class="model-list">
        <button
          v-for="item in visibleModels"
          :key="item.id"
          class="model-item"
          :class="{ active: String(item.id) === String(selectedId) }"
          type="button"
          @click="selectModel(item)">
          <span class="model-state" :class="{ enabled: item.status === 1 }" />
          <span class="model-copy">
            <strong>{{ item.name }}</strong>
            <span>{{ item.className || item.tableName }}</span>
          </span>
          <span class="model-version">v{{ item.version }}</span>
        </button>
        <div v-if="!visibleModels.length" class="empty-list">暂无模型</div>
      </div>
      <nav class="model-pagination" aria-label="模型列表分页">
        <span class="model-page-range">{{ pageRangeLabel }}</span>
        <div class="model-page-controls">
          <IconButton
            :disabled="busy || !canGoPrevious"
            :icon="ChevronLeft"
            label="上一页"
            tooltip
            @click="changePage(pageNumber - 1)"
          />
          <span class="model-page-label" aria-live="polite">{{ pageLabel }}</span>
          <IconButton
            :disabled="busy || !canGoNext"
            :icon="ChevronRight"
            label="下一页"
            tooltip
            @click="changePage(pageNumber + 1)"
          />
        </div>
      </nav>
    </aside>

    <section class="model-workspace">
      <div class="workspace-toolbar">
        <div class="view-tabs workspace-view-tabs model-workspace-view-tabs" role="tablist" aria-label="模型工作区视图">
          <button type="button" :class="{ active: view === 'editor' }" @click="view = 'editor'">{{ editorTitle }}</button>
          <button type="button" :class="{ active: view === 'preview' }" @click="loadPreview">生成结果</button>
        </div>
        <div class="workspace-title">
          <strong>{{ selectedModel?.name ?? '新模型' }}</strong>
          <span>{{ selectedModel?.contributorId ?? model.className ?? '未保存' }}</span>
        </div>
        <div class="workspace-actions" role="group" :aria-label="`${modelResourceLabel}操作`">
          <IconButton
            aria-label="刷新模型"
            :disabled="busy"
            :icon="RefreshCw"
            label="刷新模型"
            tooltip
            @click="run(refreshWorkspace)"
          />
          <IconButton
            v-if="selectedId && mode === 'entity'"
            aria-label="下载源码"
            :disabled="busy"
            :icon="Download"
            label="下载源码"
            tooltip
            @click="downloadModel"
          />
          <IconButton
            v-if="selectedId && mode === 'entity'"
            aria-label="删除模型"
            :disabled="busy"
            :icon="Trash2"
            label="删除模型"
            tooltip
            variant="danger"
            @click="deleteModel"
          />
          <Button :aria-label="saveModelLabel" :disabled="busy || !canPersist" size="sm" @click="saveModel">
            <Save data-icon="inline-start" />{{ saveModelLabel }}
          </Button>
        </div>
      </div>

      <div v-if="notice" class="notice-bar" :class="noticeTone">
        {{ notice }}
      </div>

      <div
        v-show="view !== 'preview'"
        class="metadata-authoring-layout"
        :class="{ 'controller-authoring-layout': mode === 'controller' }">
        <div class="editor-scroll metadata-editor-pane">
          <ControllerDesigner
            v-if="ready && mode === 'controller'"
            :model="model"
            :model-value="model.routeConfig"
            :models="modelOptions"
            @change="onModelChange"
            @update:model-value="updateRouteConfig"
          />
          <ModelDesigner
            v-else-if="ready"
            v-model="model"
            :initial-section="initialSection"
            :models="modelOptions"
            :recent-changes="recentAssistantChanges"
            :show-identity-configuration="showIdentityConfiguration"
            @change="onModelChange"
          />
          <div v-else class="loading-state">正在读取模型...</div>
        </div>
        <MetadataAssistantPanel
          v-if="mode === 'entity'"
          :draft="model"
          :draft-identity="assistantDraftIdentity"
          :related-models="modelOptions"
          scope="model"
          @apply="applyAssistantModel"
          @apply-display-text="applyAssistantDisplayText" />
      </div>

      <div v-if="view === 'preview'" class="preview-layout">
        <nav class="file-list" aria-label="生成文件">
          <button
            v-for="file in preview?.files ?? []"
            :key="file.filePath"
            type="button"
            :class="{ active: file.filePath === selectedFile?.filePath }"
            @click="selectedFile = file">
            <Code :size="15" />
            <span>{{ file.filePath }}</span>
          </button>
        </nav>
        <div class="code-preview">
          <CodeBlock
            v-if="selectedFile"
            :content="selectedFile.content"
            :language="fileLanguage(selectedFile)"
            line-numbers
          />
          <div v-else class="loading-state">暂无生成文件</div>
        </div>
      </div>
    </section>
  </main>
</template>
