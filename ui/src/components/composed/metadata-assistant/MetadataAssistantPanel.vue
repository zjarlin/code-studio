<script setup lang="ts">
import { Bot, Check, Sparkles } from '@lucide/vue'
import { computed, nextTick, onMounted, ref, watch } from 'vue'

import AgentComposer from '@/components/composed/agent-composer/AgentComposer.vue'
import AgentPanel from '@/components/composed/agent-panel/AgentPanel.vue'
import { useAgentResponses } from '@/components/composed/agent-panel/use-agent-responses'
import {
  CONTRACT_METADATA_PROMPT_ACTIONS,
  DTO_METADATA_PROMPT_ACTIONS,
  isMetadataDisplayTextTranslationRequest,
  MODEL_METADATA_PROMPT_ACTIONS,
} from '@/components/composed/metadata-assistant/metadata-prompt-actions'
import type { MetadataPromptAction } from '@/components/composed/metadata-assistant/metadata-prompt-actions'
import {
  applyMetadataDisplayTextPatches,
  createMetadataDisplayTextContext,
} from '@/components/composed/metadata-assistant/metadata-display-text'
import type { MetadataDisplayTextScope } from '@/components/composed/metadata-assistant/metadata-display-text'
import { normalizeMetadataPatchResult } from '@/components/composed/metadata-table/metadata-table'
import { Button } from '@/components/generated/shadcn/button'
import { LowcodeApi } from '@/lowcode-api'
import type {
  AgentMetadataResult,
  AgentContextUsage,
  AgentProviderModel,
  AgentQueuedPrompt,
  AgentReasoningEffort,
  AgentSendBehavior,
  AgentUiMessage,
  JsonObject,
  LowcodeModelSummary,
  MetadataTablePatchResult,
} from '@/types'

type MetadataScope = MetadataDisplayTextScope
type MetadataPromptMode = 'display-text-translation' | 'draft'
type MetadataQueuedPrompt = AgentQueuedPrompt & { metadataMode: MetadataPromptMode }
type LatestMetadataResult =
  | { kind: 'display-text'; value: MetadataTablePatchResult }
  | { kind: 'draft'; value: JsonObject }

const props = withDefaults(defineProps<{
  scope: MetadataScope
  draft: JsonObject
  draftIdentity: string
  relatedModels?: LowcodeModelSummary[]
  focused?: boolean
}>(), {
  relatedModels: () => [],
  focused: false,
})
const emit = defineEmits<{
  apply: [value: JsonObject]
  applyDisplayText: [value: JsonObject]
}>()

const api = new LowcodeApi()
const models = ref<AgentProviderModel[]>([])
const selectedModelId = ref('')
const prompt = ref('')
const queuedPrompts = ref<MetadataQueuedPrompt[]>([])
const reasoningEffort = ref<AgentReasoningEffort>('provider')
const pendingPromptMode = ref<MetadataPromptMode>('draft')
const activePromptMode = ref<MetadataPromptMode>('draft')
const notice = ref('')
const loading = ref(false)
const modelsLoading = ref(false)

const chat = useAgentResponses({
  model: () => selectedModelId.value,
  reasoningEffort: () => reasoningEffort.value,
  contextSnapshot: metadataContextSnapshot,
  onFinish: () => {
    window.setTimeout(() => void drainQueue(), 0)
  },
})

const scopeLabel = computed(() => ({ contract: '契约', dto: 'DTO', model: '模型' })[props.scope])
const panelTitle = computed(() => `${scopeLabel.value}助手`)
const contextLabel = computed(() => {
  const key = props.scope === 'model'
    ? props.draft.modelCode
    : props.scope === 'dto'
      ? props.draft.dtoCode
      : props.draft.contractCode
  return typeof key === 'string' && key.trim() ? key : `新${scopeLabel.value}`
})
const placeholder = computed(() => ({
  contract: '描述接口、参数、请求体或调整要求…',
  dto: '描述 DTO 用途、字段、类型或调整要求…',
  model: '描述字段、查询、关联或调整要求…',
})[props.scope])
const promptActions = computed(() => ({
  contract: CONTRACT_METADATA_PROMPT_ACTIONS,
  dto: DTO_METADATA_PROMPT_ACTIONS,
  model: MODEL_METADATA_PROMPT_ACTIONS,
})[props.scope])
const isStreaming = computed(() => chat.status.value === 'submitted' || chat.status.value === 'streaming')
const selectedModelAvailable = computed(() => models.value.some((model) => model.id === selectedModelId.value))
const latestContextUsage = computed<AgentContextUsage | undefined>(() => {
  for (const message of [...chat.messages.value].reverse()) {
    for (const part of [...message.parts].reverse()) {
      if (part.type === 'data-context') return part.data as AgentContextUsage
    }
  }
  return undefined
})
const latestResult = computed<LatestMetadataResult | undefined>(() => {
  for (const message of [...chat.messages.value].reverse()) {
    for (const part of [...message.parts].reverse()) {
      if (part.type === 'data-metadata-patch') {
        return { kind: 'display-text', value: part.data as MetadataTablePatchResult }
      }
      if (part.type !== 'data-metadata') continue
      const metadata = part.data as AgentMetadataResult
      const value = props.scope === 'model'
        ? metadata.models?.[0]
        : props.scope === 'dto'
          ? metadata.dtos?.[0]
          : metadata.contracts?.[0]
      if (value) return { kind: 'draft', value: value as unknown as JsonObject }
    }
  }
  return undefined
})

watch(() => `${props.scope}:${props.draftIdentity}`, resetConversation)
onMounted(refreshModels)

async function submitPrompt(behavior: AgentSendBehavior): Promise<void> {
  const text = prompt.value.trim()
  if (!text) return
  const mode = resolvePromptMode(text)
  if (isStreaming.value) {
    prompt.value = ''
    pendingPromptMode.value = 'draft'
    if (behavior === 'queue') {
      queuedPrompts.value.push(buildQueuedPrompt(text, mode))
      return
    }
    await stopGeneration()
    chat.error.value = undefined
    await nextTick()
  }
  await sendPromptNow(text, mode)
}

async function stopGeneration(): Promise<void> {
  chat.stop()
}

async function sendPromptNow(text: string, mode: MetadataPromptMode = 'draft'): Promise<void> {
  loading.value = true
  notice.value = ''
  try {
    const settings = await api.agentSettings()
    if (!settings.apiKeyConfigured) {
      notice.value = '请先在 Agents 中配置 AI 连接'
      return
    }
    if (!selectedModelAvailable.value) {
      notice.value = '请选择上游可用模型'
      return
    }
    prompt.value = ''
    pendingPromptMode.value = 'draft'
    loading.value = false
    activePromptMode.value = mode
    await chat.send(text)
  } catch (error) {
    notice.value = error instanceof Error ? error.message : `${scopeLabel.value}生成失败`
  } finally {
    activePromptMode.value = 'draft'
    loading.value = false
  }
}

function buildQueuedPrompt(text: string, metadataMode: MetadataPromptMode): MetadataQueuedPrompt {
  return {
    id: crypto.randomUUID(),
    text,
    modelId: selectedModelId.value,
    reasoningEffort: reasoningEffort.value,
    metadataMode,
  }
}

async function drainQueue(): Promise<void> {
  if (isStreaming.value || loading.value || !queuedPrompts.value.length) return
  const next = queuedPrompts.value.shift()
  if (!next) return
  selectedModelId.value = next.modelId
  reasoningEffort.value = next.reasoningEffort
  await nextTick()
  await sendPromptNow(next.text, next.metadataMode)
}

function removeQueued(id: string): void {
  queuedPrompts.value = queuedPrompts.value.filter((item) => item.id !== id)
}

function moveQueued(id: string, direction: -1 | 1): void {
  const index = queuedPrompts.value.findIndex((item) => item.id === id)
  const target = index + direction
  if (index < 0 || target < 0 || target >= queuedPrompts.value.length) return
  const next = [...queuedPrompts.value]
  ;[next[index], next[target]] = [next[target]!, next[index]!]
  queuedPrompts.value = next
}

function updateQueued(id: string, text: string): void {
  queuedPrompts.value = queuedPrompts.value.map((item) => item.id === id ? { ...item, text } : item)
}

async function refreshModels(): Promise<void> {
  modelsLoading.value = true
  try {
    const settings = await api.agentSettings()
    if (!settings.apiKeyConfigured) {
      models.value = []
      selectedModelId.value = ''
      return
    }
    models.value = await api.agentModels()
    if (!models.value.some((model) => model.id === selectedModelId.value)) {
      selectedModelId.value = models.value[0]?.id ?? ''
    }
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '读取上游模型失败'
  } finally {
    modelsLoading.value = false
  }
}

async function changeModel(modelId: string): Promise<void> {
  selectedModelId.value = modelId
}

function metadataContextSnapshot(): {
  scene: string
  draft?: JsonObject
  state: Record<string, unknown>
} {
  if (activePromptMode.value === 'display-text-translation') {
    return {
      scene: `metadata.${props.scope}.display-text`,
      state: {
        scope: 'table',
        operation: {
          type: 'translate',
          targetLanguage: '中文',
          instruction: '检查全部展示文本，翻译尚未中文化的项，并仅在语义明确时补全空说明；保留通用技术缩写和已有准确中文，无法可靠补全时写入 questions。',
        },
        table: createMetadataDisplayTextContext(props.scope, props.draft, props.draftIdentity),
      },
    }
  }
  return {
    scene: `metadata.${props.scope}`,
    draft: props.draft,
    state: metadataContext(),
  }
}

function metadataContext(): Record<string, unknown> {
  if (props.scope === 'contract') {
    return {
      scope: 'contract',
      currentContract: props.draft,
    }
  }
  if (props.scope === 'dto') {
    return {
      scope: 'dto',
      currentDto: props.draft,
      availableModels: props.relatedModels.map((model) => ({
        modelCode: model.modelCode,
        name: model.name,
        modelType: model.modelType,
        fields: model.fields?.map((field) => ({
          fieldCode: field.fieldCode,
          label: field.label,
          kotlinType: field.kotlinType,
          required: field.required,
        })) ?? [],
      })),
    }
  }
  return {
    scope: 'model',
    currentModel: props.draft,
    availableModels: props.relatedModels.map((model) => ({
      modelCode: model.modelCode,
      name: model.name,
      modelType: model.modelType,
    })),
  }
}

function applyLatest(): void {
  if (!latestResult.value) return
  if (latestResult.value.kind === 'display-text') {
    applyLatestDisplayText(latestResult.value.value)
    return
  }
  emit('apply', latestResult.value.value)
  notice.value = `已应用到当前${scopeLabel.value}`
}

function applyLatestDisplayText(value: MetadataTablePatchResult): void {
  try {
    const result = normalizeMetadataPatchResult(value as unknown as JsonObject)
    if (result.questions.length) {
      notice.value = result.questions.join('；')
      return
    }
    const application = applyMetadataDisplayTextPatches(
      props.scope,
      props.draft,
      props.draftIdentity,
      result,
    )
    if (application.conflicts.length) {
      notice.value = `当前${scopeLabel.value}已变化，${application.conflicts.length} 项 Patch 未应用，请重新检查`
      return
    }
    if (!application.applied.length) {
      notice.value = `当前${scopeLabel.value}没有需要中文化的展示文本`
      return
    }
    emit('applyDisplayText', application.draft)
    notice.value = `已中文化 ${application.applied.length} 项展示文本`
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '翻译结果无效'
  }
}

function fillSuggestion(action: MetadataPromptAction): void {
  prompt.value = action.prompt
  pendingPromptMode.value = action.mode ?? 'draft'
}

function resolvePromptMode(text: string): MetadataPromptMode {
  if (pendingPromptMode.value === 'display-text-translation') {
    return pendingPromptMode.value
  }
  return isMetadataDisplayTextTranslationRequest(text) ? 'display-text-translation' : 'draft'
}

function resetConversation(): void {
  if (isStreaming.value) void stopGeneration()
  chat.reset()
  prompt.value = ''
  queuedPrompts.value = []
  pendingPromptMode.value = 'draft'
  activePromptMode.value = 'draft'
  notice.value = ''
}

</script>

<template>
  <AgentPanel
    as="aside"
    class="metadata-assistant-panel"
    :class="{ focused }"
    :aria-label="panelTitle"
    empty-class="metadata-assistant-empty"
    :messages="chat.messages.value"
    viewport-class="metadata-assistant-messages">
    <template #header>
      <header class="metadata-assistant-header">
      <div class="metadata-assistant-heading">
        <span><Bot /></span>
        <div>
          <strong>{{ panelTitle }}</strong>
          <small>{{ contextLabel }}</small>
        </div>
      </div>
      <div class="metadata-assistant-actions">
        <Button v-if="latestResult" size="sm" type="button" @click="applyLatest">
          <Check data-icon="inline-start" />
          {{ latestResult.kind === 'display-text' ? '应用翻译' : '应用结果' }}
        </Button>
      </div>
      </header>
    </template>

    <template #empty>
      <div class="metadata-assistant-empty">
        <Sparkles />
        <strong>描述{{ scopeLabel }}元数据</strong>
      </div>
    </template>

    <template #footer>
      <footer class="metadata-assistant-composer-area">
      <section class="metadata-assistant-prompt-actions" aria-label="常用修改">
        <strong>常用修改</strong>
        <div>
          <button
            v-for="action in promptActions"
            :key="action.label"
            type="button"
            :title="action.prompt"
            @click="fillSuggestion(action)">
            <Sparkles />
            <span><strong>{{ action.label }}</strong><small>{{ action.description }}</small></span>
          </button>
        </div>
      </section>
      <AgentComposer
        v-model:prompt="prompt"
        v-model:reasoning-effort="reasoningEffort"
        :busy="loading"
        compact
        :context-usage="latestContextUsage"
        :loading-models="modelsLoading"
        :models="models"
        :model-value="selectedModelId"
        :notice="notice || chat.error.value?.message"
        :placeholder="placeholder"
        :queued-prompts="queuedPrompts"
        :send-label="`发送${scopeLabel}需求`"
        :streaming="isStreaming"
        @move-queued="moveQueued"
        @refresh-models="refreshModels"
        @remove-queued="removeQueued"
        @stop="stopGeneration"
        @submit="submitPrompt"
        @update:model-value="changeModel"
        @update-queued="updateQueued" />
      </footer>
    </template>
  </AgentPanel>
</template>
