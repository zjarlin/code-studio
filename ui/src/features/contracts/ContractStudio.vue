<script setup lang="ts">
import {
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
import { toPinyinSnakeIdentifier, toResourceCodeFromClassName } from '@/lib/identifier'

import { LowcodeApi } from '../../lowcode-api'
import type {
  JsonObject,
  LowcodeApiContractDraft,
  LowcodeApiTypeOption,
  LowcodeApiContractSummary,
  LowcodeContractPreview,
  LowcodePreviewFile,
  LowcodeValidationResult,
  LowcodeModelSummary,
  LowcodeDtoResourceSummary,
} from '../../types'
import CustomOperationEditor from './CustomOperationEditor.vue'
import { toPascalCase } from '../model-studio/model-draft'
import {
  applyAgentContractDraft,
  applyContractClassName,
  applyContractCode,
  completeContractMetadata,
  createEmptyContract,
  normalizeContractDraft,
  updateContractOperations,
  validateContractDraft,
} from './contract-draft'

type WorkspaceView = 'assistant' | 'editor' | 'preview'
type NoticeTone = 'error' | 'success' | 'warning'
interface ContractCreationContext {
  featureId: number | string
  packageName: string
  contributorId: string
}

const props = withDefaults(defineProps<{
  embedded?: boolean
  initialContractCode?: string
  createRequest?: number
  creationContext?: ContractCreationContext
}>(), {
  embedded: false,
  initialContractCode: '',
  createRequest: 0,
  creationContext: undefined,
})

const emit = defineEmits<{
  deleted: [contractCode: string]
  saved: [contractCode: string]
  selected: [contractCode: string]
}>()

const api = new LowcodeApi()
const contracts = ref<LowcodeApiContractSummary[]>([])
const models = ref<LowcodeModelSummary[]>([])
const dtos = ref<LowcodeDtoResourceSummary[]>([])
const selectedId = ref<number | string>()
const contract = shallowRef<LowcodeApiContractDraft>(createEmptyContract())
const validation = ref<LowcodeValidationResult>()
const preview = ref<LowcodeContractPreview>()
const selectedFile = ref<LowcodePreviewFile>()
const search = ref('')
const notice = ref('')
const noticeTone = ref<NoticeTone>('success')
const busy = ref(false)
const dirty = ref(false)
const view = ref<WorkspaceView>('editor')
const assistantDraftRevision = ref(0)

const selectedContract = computed(() =>
  contracts.value.find((item) => String(item.id) === String(selectedId.value)))
const assistantDraftIdentity = computed(() => `${selectedId.value ?? 'new'}:${assistantDraftRevision.value}`)
const filteredContracts = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) {
    return contracts.value
  }
  return contracts.value.filter((item) =>
    `${item.name} ${item.contractCode} ${item.contributorId ?? ''}`.toLowerCase().includes(keyword),
  )
})
const typeOptions = computed<LowcodeApiTypeOption[]>(() => {
  const options: LowcodeApiTypeOption[] = []
  for (const model of models.value) {
    if (contract.value.contributorId && model.contributorId !== contract.value.contributorId) continue
    if (model.modelType === 'ENTITY') {
      options.push({
        modelCode: model.modelCode,
        dtoCode: '',
        className: model.className || model.modelCode,
        kind: 'ENTITY',
      })
    }
  }
  for (const dto of dtos.value) {
    if (dto.kind === 'STRUCTURE') continue
    if (contract.value.contributorId && dto.contributorId !== contract.value.contributorId) continue
    options.push({ modelCode: null, dtoCode: dto.dtoCode, className: dto.className, kind: dto.kind })
  }
  return options
})

onMounted(() => {
  void run(async () => {
    await refreshContracts()
    if (props.initialContractCode) {
      await selectContractByCode(props.initialContractCode)
    } else if (props.createRequest > 0) {
      createContract()
    }
  })
})

watch(() => props.initialContractCode, (contractCode) => {
  if (contractCode && contractCode !== contract.value.contractCode) {
    void selectContractByCode(contractCode)
  }
})

watch(() => props.createRequest, (request, previous) => {
  if (request > previous) {
    createContract()
  }
})

async function refreshContracts(): Promise<void> {
  const [contractList, modelList, dtoList] = await Promise.all([api.contracts(), api.models(), api.dtos()])
  contracts.value = contractList
  models.value = modelList
  dtos.value = dtoList
}

async function selectContract(item: LowcodeApiContractSummary): Promise<void> {
  if (dirty.value && !window.confirm('放弃当前未保存的修改？')) {
    emit('selected', contract.value.contractCode)
    return
  }
  await run(async () => {
    const detail = await api.contractDetail(item.id)
    selectedId.value = item.id
    contract.value = normalizeContractDraft(detail)
    assistantDraftRevision.value += 1
    resetGeneratedState()
    dirty.value = false
    emit('selected', item.contractCode)
  })
}

function createContract(): void {
  if (dirty.value && !window.confirm('放弃当前未保存的修改？')) {
    emit('selected', contract.value.contractCode)
    return
  }
  selectedId.value = undefined
  contract.value = {
    ...createEmptyContract(),
    featureId: props.creationContext?.featureId ?? 0,
    packageName: props.creationContext?.packageName ?? createEmptyContract().packageName,
    contributorId: props.creationContext?.contributorId ?? null,
  }
  assistantDraftRevision.value += 1
  resetGeneratedState()
  dirty.value = false
  emit('selected', '')
}

function updateOperations(operations: LowcodeApiContractDraft['operations']): void {
  contract.value = updateContractOperations(contract.value, operations)
  validation.value = undefined
  dirty.value = true
}

function updateContract(patch: Partial<LowcodeApiContractDraft>): void {
  contract.value = { ...contract.value, ...patch }
  resetValidationState()
}

function updateContractName(name: string): void {
  const previousDefault = toPinyinSnakeIdentifier(contract.value.name)
  const previousClassName = previousDefault
    ? `${toPascalCase(previousDefault)}Service`
    : ''
  const className = `${toPascalCase(toPinyinSnakeIdentifier(name))}Service`
  const identityFollowsName = (!contract.value.contractCode || contract.value.contractCode === previousDefault)
    && (!contract.value.className || contract.value.className === previousClassName)
  contract.value = identityFollowsName
    ? applyContractCode(
        { ...contract.value, name, className },
        toResourceCodeFromClassName(className, 'Service'),
      )
    : { ...contract.value, name }
  resetValidationState()
}

function updateContractClassName(className: string): void {
  contract.value = applyContractClassName(contract.value, className)
  resetValidationState()
}

function resetValidationState(): void {
  validation.value = undefined
  preview.value = undefined
  selectedFile.value = undefined
  dirty.value = true
}

function textValue(event: Event): string {
  return (event.target as HTMLInputElement | HTMLTextAreaElement).value
}

function numberValue(event: Event): number {
  return Number.parseInt((event.target as HTMLInputElement).value, 10) || 1
}

function checkedValue(event: Event): boolean {
  return (event.target as HTMLInputElement).checked
}

async function validateContract(): Promise<LowcodeValidationResult | undefined> {
  contract.value = completeContractMetadata(contract.value)
  const errors = validateContractDraft(contract.value)
  if (errors.length > 0) {
    notice.value = errors.join('；')
    noticeTone.value = 'error'
    return undefined
  }
  let result: LowcodeValidationResult | undefined
  await run(async () => {
    result = await api.validateContract(contract.value)
    validation.value = result
    notice.value = result.valid ? 'Service 校验通过' : result.errors.join('；')
    noticeTone.value = result.valid ? 'success' : 'error'
  })
  return result
}

function applyAssistantContract(value: JsonObject): void {
  commitAssistantContract(applyAgentContractDraft(contract.value, value))
}

function applyAssistantDisplayText(value: JsonObject): void {
  commitAssistantContract(value as LowcodeApiContractDraft)
}

function commitAssistantContract(generated: LowcodeApiContractDraft): void {
  contract.value = generated
  resetGeneratedState()
  dirty.value = true
}

async function saveContract(): Promise<void> {
  const result = await validateContract()
  if (!result?.valid) {
    return
  }
  await run(async () => {
    const saved = await api.saveContract(contract.value)
    const id = contract.value.id ?? saved
    dirty.value = false
    await refreshContracts()
    const persisted = contracts.value.find((item) => String(item.id) === String(id))
    if (persisted) {
      await selectContract(persisted)
    }
    notice.value = 'Service 已保存'
    noticeTone.value = 'success'
    emit('saved', contract.value.contractCode)
  })
}

async function deleteContract(): Promise<void> {
  if (!selectedId.value || !window.confirm(`删除 Service“${selectedContract.value?.name ?? contract.value.name}”？`)) {
    return
  }
  await run(async () => {
    const deletedCode = selectedContract.value?.contractCode ?? contract.value.contractCode
    await api.deleteContract(selectedId.value as number | string)
    await refreshContracts()
    dirty.value = false
    createContract()
    notice.value = 'Service 已删除；下次编译将清理对应生成文件'
    noticeTone.value = 'success'
    emit('deleted', deletedCode)
  })
}

async function selectContractByCode(contractCode: string): Promise<void> {
  const item = contracts.value.find((candidate) => candidate.contractCode === contractCode)
  if (item) {
    await selectContract(item)
  }
}

async function loadPreview(): Promise<void> {
  if (!selectedId.value) {
    notice.value = '请先保存 Service'
    noticeTone.value = 'warning'
    return
  }
  if (dirty.value) {
    notice.value = '请先保存当前修改，再查看生成结果'
    noticeTone.value = 'warning'
    return
  }
  await run(async () => {
    preview.value = await api.previewContract(selectedId.value as number | string)
    selectedFile.value = preview.value.files[0]
    view.value = 'preview'
  })
}

async function downloadContract(): Promise<void> {
  if (!selectedId.value || dirty.value) {
    notice.value = selectedId.value ? '请先保存当前修改，再下载 Service' : '请先保存 Service'
    noticeTone.value = 'warning'
    return
  }
  await run(() => api.downloadContract(selectedId.value as number | string))
}

async function refreshWorkspace(): Promise<void> {
  if (dirty.value && !window.confirm('放弃当前未保存的修改并刷新？')) {
    return
  }
  await refreshContracts()
  const selected = contracts.value.find((item) => String(item.id) === String(selectedId.value))
  if (selected) {
    const detail = await api.contractDetail(selected.id)
    contract.value = normalizeContractDraft(detail)
    assistantDraftRevision.value += 1
    dirty.value = false
    resetGeneratedState()
  }
}

function resetGeneratedState(): void {
  validation.value = undefined
  preview.value = undefined
  selectedFile.value = undefined
  view.value = 'editor'
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

</script>

<template>
  <main class="studio-main model-studio-main contract-studio-main" :class="{ 'embedded-studio-main': embedded }">
    <aside v-if="!embedded" class="model-sidebar">
      <div class="sidebar-toolbar">
        <SearchInput v-model="search" label="搜索 Service" />
        <IconButton aria-label="新建 Service" :icon="Plus" label="新建 Service" tooltip @click="createContract()" />
      </div>
      <div class="model-list">
        <button
          v-for="item in filteredContracts"
          :key="item.id"
          class="model-item"
          :class="{ active: String(item.id) === String(selectedId) }"
          type="button"
          @click="selectContract(item)">
          <span class="model-state" :class="{ enabled: item.status === 1 }" />
          <span class="model-copy">
            <strong>{{ item.name }}</strong>
            <span>{{ item.className }}</span>
          </span>
          <span class="model-version">v{{ item.version }}</span>
        </button>
        <div v-if="!filteredContracts.length" class="empty-list">暂无 Service</div>
      </div>
    </aside>

    <section class="model-workspace">
      <div class="workspace-toolbar">
        <div class="view-tabs workspace-view-tabs" role="tablist" aria-label="Service 工作区视图">
          <button type="button" :class="{ active: view === 'editor' }" @click="view = 'editor'">Service</button>
          <button type="button" :class="{ active: view === 'assistant' }" @click="view = 'assistant'">智能体</button>
          <button type="button" :class="{ active: view === 'preview' }" @click="loadPreview">生成结果</button>
        </div>
        <div class="workspace-title">
          <strong>{{ selectedContract?.name ?? '新 Service' }}</strong>
          <span>{{ selectedContract?.contributorId ?? contract.className ?? '未保存' }}</span>
        </div>
        <div class="workspace-actions" role="group" aria-label="Service 操作">
          <IconButton aria-label="刷新 Service" :disabled="busy" :icon="RefreshCw" label="刷新 Service" tooltip @click="run(refreshWorkspace)" />
          <IconButton v-if="selectedId" aria-label="下载 Service" :disabled="busy" :icon="Download" label="下载 Service" tooltip @click="downloadContract" />
          <IconButton v-if="selectedId" aria-label="删除 Service" :disabled="busy" :icon="Trash2" label="删除 Service" tooltip variant="danger" @click="deleteContract" />
          <Button aria-label="保存 Service" :disabled="busy" size="sm" @click="saveContract"><Save data-icon="inline-start" />保存 Service</Button>
        </div>
      </div>

      <div v-if="notice" class="notice-bar" :class="noticeTone">{{ notice }}</div>

      <div
        v-show="view !== 'preview'"
        class="metadata-authoring-layout"
        :class="{ 'assistant-focused': view === 'assistant' }">
        <div v-show="view === 'editor'" class="editor-scroll contract-editor-scroll metadata-editor-pane">
          <div class="contract-editor">
            <section class="designer-section contract-service-definition" aria-labelledby="contract-service-title">
              <header class="designer-section-heading">
                <div>
                  <h2 id="contract-service-title">领域 Service</h2>
                  <span>{{ contract.operations.length }} 个领域方法</span>
                </div>
              </header>
              <div class="form-grid model-form-grid">
                <label class="form-field"><span>Service 注释 <b>*</b></span><input :value="contract.name" @input="updateContractName(textValue($event))"></label>
                <label class="form-field"><span>领域 Service 接口 <b>*</b></span><input :value="contract.className" placeholder="DispatchPlanService" @input="updateContractClassName(textValue($event))"></label>
                <label class="form-field"><span>基础路径 <b>*</b></span><input :value="contract.path" placeholder="/dispatch-plans" @input="updateContract({ path: textValue($event) })"></label>
                <label class="form-field form-field-wide"><span>业务包名 <b>*</b></span><input :readonly="creationContext != null" :value="contract.packageName" placeholder="application.dispatch" @input="updateContract({ packageName: textValue($event) })"></label>
                <label class="form-field"><span>Contributor ID <b>*</b></span><input :readonly="creationContext != null" :value="contract.contributorId ?? ''" placeholder="example.dispatch" @input="updateContract({ contributorId: textValue($event) || null })"></label>
                <label class="form-field"><span>版本</span><input :value="contract.version" min="1" type="number" @input="updateContract({ version: numberValue($event) })"></label>
                <label class="switch-field">
                  <input :checked="contract.status === 1" type="checkbox" @change="updateContract({ status: checkedValue($event) ? 1 : 0 })">
                  <span><strong>启用 Service</strong><small>{{ contract.status === 1 ? '参与生成' : '已停用' }}</small></span>
                </label>
                <label class="form-field form-field-wide"><span>领域说明</span><textarea :value="contract.description ?? ''" rows="3" @input="updateContract({ description: textValue($event) || null })" /></label>
              </div>
            </section>
          <CustomOperationEditor
            :agent-exposure="contract.agentExposure"
            :base-path="contract.path"
            :type-options="typeOptions"
            :model-value="contract.operations"
            @update:agent-exposure="updateContract({ agentExposure: $event })"
            @update:model-value="updateOperations"
            />
          </div>
        </div>
        <MetadataAssistantPanel
          :draft="contract"
          :draft-identity="assistantDraftIdentity"
          :focused="view === 'assistant'"
          scope="contract"
          @apply="applyAssistantContract"
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
          <CodeBlock v-if="selectedFile" :content="selectedFile.content" language="kotlin" line-numbers />
          <div v-else class="loading-state">暂无生成文件</div>
        </div>
      </div>
    </section>

  </main>
</template>
