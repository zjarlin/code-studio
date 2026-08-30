<script setup lang="ts">
import {
  Bot,
  Languages,
  MessageSquarePlus,
  PanelLeft,
  Sparkles,
  Settings2,
  Trash2,
  Wrench,
} from '@lucide/vue'
import { computed, nextTick, onMounted, ref } from 'vue'

import AgentComposer from '@/components/composed/agent-composer/AgentComposer.vue'
import AgentPanel from '@/components/composed/agent-panel/AgentPanel.vue'
import { useAgentResponses } from '@/components/composed/agent-panel/use-agent-responses'
import IconButton from '@/components/composed/icon-button/IconButton.vue'
import SearchInput from '@/components/composed/search-input/SearchInput.vue'
import { Button } from '@/components/generated/shadcn/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/generated/shadcn/dialog'
import { Input } from '@/components/generated/shadcn/input'

import { LowcodeApi } from '../../lowcode-api'
import type {
  AgentConversationSummary,
  AgentChatMode,
  AgentContextUsage,
  AgentContextSnapshotRequest,
  AgentProviderModel,
  AgentProviderSettingsView,
  AgentQueuedPrompt,
  AgentReasoningEffort,
  AgentSendBehavior,
  MetadataTableContext,
} from '../../types'

const DISPLAY_TEXT_PROMPT = '检查并中文化全部未翻译的展示元数据。仅翻译候选表中的展示文本，并在语义明确时补全空说明；保留技术缩写，不得修改 ID、编码、字段、类型、路径、映射、顺序或结构。'
const DISPLAY_TEXT_INSTRUCTION = '检查全部候选展示文本，翻译尚未中文化的项，并仅在语义明确时补全空说明；保留通用技术缩写和已有准确中文，无法可靠处理时写入 questions。'

const api = new LowcodeApi()
const conversations = ref<AgentConversationSummary[]>([])
const conversationSelectionTouched = ref(false)
const models = ref<AgentProviderModel[]>([])
const selectedModelId = ref('')
const selectedConversationId = ref<number | string>()
const search = ref('')
const prompt = ref('')
const queuedPrompts = ref<AgentQueuedPrompt[]>([])
const reasoningEffort = ref<AgentReasoningEffort>('provider')
const chatMode = ref<AgentChatMode>('auto')
const displayTextContext = ref<MetadataTableContext>()
const loading = ref(false)
const loadingMessages = ref(false)
const modelsLoading = ref(false)
const settingsOpen = ref(false)
const sidebarOpen = ref(false)
const settings = ref<AgentProviderSettingsView>({
  baseUrl: 'https://api.openai.com',
  apiKeyConfigured: false,
})
const baseUrlDraft = ref(settings.value.baseUrl)
const apiKeyDraft = ref('')
const notice = ref('')

const chat = useAgentResponses({
  model: () => selectedModelId.value,
  reasoningEffort: () => reasoningEffort.value,
  conversation: () => conversations.value.find(
    conversation => conversation.id === selectedConversationId.value,
  )?.externalId,
  contextSnapshot: agentContextSnapshot,
  onFinish: () => {
    void refreshConversations()
    window.setTimeout(() => void drainQueue(), 0)
  },
})

const filteredConversations = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return keyword
    ? conversations.value.filter((conversation) => conversation.title.toLowerCase().includes(keyword))
    : conversations.value
})
const selectedConversation = computed(() => conversations.value.find(
  (conversation) => conversation.id === selectedConversationId.value,
))
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

onMounted(async () => {
  await run(async () => {
    const currentSettings = await loadSettings()
    await Promise.all([
      refreshConversations(),
      currentSettings.apiKeyConfigured ? refreshModels() : Promise.resolve(),
    ])
    if (currentSettings && conversations.value[0] && !conversationSelectionTouched.value) {
      await selectConversation(conversations.value[0], false)
    }
  })
})

async function loadSettings(): Promise<AgentProviderSettingsView> {
  settings.value = await api.agentSettings()
  return settings.value
}

async function refreshConversations(): Promise<void> {
  conversations.value = await api.agentConversations()
}

async function refreshModels(): Promise<void> {
  modelsLoading.value = true
  try {
    models.value = await api.agentModels()
    if (!models.value.some((model) => model.id === selectedModelId.value)) {
      selectedModelId.value = models.value[0]?.id ?? ''
    }
  } finally {
    modelsLoading.value = false
  }
}

async function selectConversation(
  conversation: AgentConversationSummary,
  userInitiated = true,
): Promise<void> {
  if (isStreaming.value || conversation.id === selectedConversationId.value) return
  if (userInitiated) conversationSelectionTouched.value = true
  loadingMessages.value = true
  notice.value = ''
  try {
    leaveDisplayTextMode()
    const messages = await api.agentMessages(conversation.id)
    if (!userInitiated && conversationSelectionTouched.value) return
    selectedModelId.value = conversation.modelId || models.value[0]?.id || ''
    selectedConversationId.value = conversation.id
    sidebarOpen.value = false
    await nextTick()
    chat.reset()
    chat.messages.value = messages
  } catch (error) {
    showError(error)
  } finally {
    loadingMessages.value = false
  }
}

function startConversation(): void {
  if (isStreaming.value) return
  conversationSelectionTouched.value = true
  selectedConversationId.value = undefined
  leaveDisplayTextMode()
  chat.reset()
  prompt.value = ''
  queuedPrompts.value = []
  notice.value = ''
  sidebarOpen.value = false
}

async function deleteConversation(conversation: AgentConversationSummary): Promise<void> {
  if (isStreaming.value || !window.confirm(`删除会话“${conversation.title}”？`)) return
  await run(async () => {
    await api.deleteAgentConversation(conversation.id)
    if (selectedConversationId.value === conversation.id) {
      startConversation()
    }
    await refreshConversations()
  })
}

async function submitPrompt(behavior: AgentSendBehavior): Promise<void> {
  const text = prompt.value.trim()
  if (!text) return
  if (isStreaming.value) {
    prompt.value = ''
    if (behavior === 'queue') {
      queuedPrompts.value.push(buildQueuedPrompt(text))
      return
    }
    await stopGeneration()
    chat.error.value = undefined
    await nextTick()
  }
  await sendPromptNow(text)
}

async function stopGeneration(): Promise<void> {
  chat.stop()
}

async function sendPromptNow(text: string): Promise<void> {
  if (!settings.value.apiKeyConfigured) {
    openSettings()
    notice.value = '请先保存 API Key'
    return
  }
  if (!selectedModelAvailable.value) {
    notice.value = '请选择上游可用模型'
    return
  }
  if (chatMode.value === 'display-text' && !displayTextContext.value) {
    notice.value = '请重新加载待中文化元数据'
    return
  }

  notice.value = ''
  if (!selectedConversationId.value) {
    loading.value = true
    try {
      const conversationId = await api.createAgentConversation(text, selectedModelId.value)
      selectedConversationId.value = conversationId
      await refreshConversations()
      await nextTick()
    } catch (error) {
      showError(error)
      return
    } finally {
      loading.value = false
    }
  }

  prompt.value = ''
  await chat.send(text)
}

function buildQueuedPrompt(text: string): AgentQueuedPrompt {
  return {
    id: crypto.randomUUID(),
    text,
    modelId: selectedModelId.value,
    reasoningEffort: reasoningEffort.value,
    mode: chatMode.value,
  }
}

async function drainQueue(): Promise<void> {
  if (isStreaming.value || loading.value || !queuedPrompts.value.length) return
  const next = queuedPrompts.value.shift()
  if (!next) return
  selectedModelId.value = next.modelId
  reasoningEffort.value = next.reasoningEffort
  chatMode.value = next.mode ?? 'auto'
  await nextTick()
  await sendPromptNow(next.text)
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

function chooseConfigurationPrompt(value: string): void {
  leaveDisplayTextMode()
  chatMode.value = 'configuration'
  prompt.value = value
}

async function chooseDisplayTextPrompt(): Promise<void> {
  await run(async () => {
    const context = await api.agentDisplayTextContext()
    displayTextContext.value = undefined
    if (!context.rows.length) {
      if (chatMode.value === 'display-text') chatMode.value = 'auto'
      prompt.value = ''
      notice.value = '全部展示元数据均已中文化，无需处理'
      return
    }
    displayTextContext.value = context
    chatMode.value = 'display-text'
    prompt.value = DISPLAY_TEXT_PROMPT
    notice.value = `已加载 ${context.rows.length} 项待处理展示元数据`
  })
}

function changeChatMode(mode: Exclude<AgentChatMode, 'display-text'>): void {
  leaveDisplayTextMode()
  chatMode.value = mode
}

function leaveDisplayTextMode(): void {
  displayTextContext.value = undefined
  if (chatMode.value !== 'display-text') return
  chatMode.value = 'auto'
  prompt.value = ''
}

function agentContextSnapshot(): AgentContextSnapshotRequest | undefined {
  if (chatMode.value === 'configuration') {
    return { scene: 'agent.workspace', state: { scope: 'workspace' } }
  }
  if (chatMode.value !== 'display-text' || !displayTextContext.value) return undefined
  return {
    scene: 'agent.workspace.display-text',
    state: {
      scope: 'table',
      operation: {
        type: 'translate',
        targetLanguage: '中文',
        instruction: DISPLAY_TEXT_INSTRUCTION,
      },
      table: displayTextContext.value,
    },
  }
}

async function changeModel(modelId: string): Promise<void> {
  selectedModelId.value = modelId
  const conversationId = selectedConversationId.value
  if (!conversationId) return
  await run(async () => {
    await api.updateAgentConversationModel(conversationId, modelId)
  })
}

function openSettings(): void {
  baseUrlDraft.value = settings.value.baseUrl
  apiKeyDraft.value = ''
  settingsOpen.value = true
}

async function saveSettings(): Promise<void> {
  await run(async () => {
    settings.value = await api.updateAgentSettings({
      baseUrl: baseUrlDraft.value,
      apiKey: apiKeyDraft.value.trim() || undefined,
    })
    apiKeyDraft.value = ''
    settingsOpen.value = false
    await refreshModels()
    notice.value = models.value.length ? 'OpenAI 连接已保存' : '连接已保存，但上游未返回模型'
  })
}

async function run(action: () => Promise<void>): Promise<void> {
  loading.value = true
  notice.value = ''
  try {
    await action()
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

function showError(error: unknown): void {
  notice.value = error instanceof Error ? error.message : '操作失败'
}

function formatConversationTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? ''
    : new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(date)
}
</script>

<template>
  <main class="agent-chat-workspace">
    <aside class="agent-chat-sidebar" :class="{ open: sidebarOpen }">
      <div class="agent-sidebar-toolbar">
        <SearchInput v-model="search" label="搜索会话" />
        <IconButton :icon="MessageSquarePlus" label="新建会话" @click="startConversation" />
      </div>

      <div class="agent-conversation-list">
        <p v-if="!filteredConversations.length" class="agent-conversation-empty">暂无会话</p>
        <div
          v-for="conversation in filteredConversations"
          :key="conversation.id"
          class="agent-conversation-item"
          :data-selected="conversation.id === selectedConversationId">
          <button type="button" @click="selectConversation(conversation)">
            <span>{{ conversation.title }}</span>
            <small>{{ formatConversationTime(conversation.updateTime) }}</small>
          </button>
          <IconButton
            :disabled="isStreaming"
            :icon="Trash2"
            :label="`删除会话 ${conversation.title}`"
            variant="danger"
            @click="deleteConversation(conversation)" />
        </div>
      </div>

      <Button class="agent-settings-button" type="button" variant="ghost" @click="openSettings">
        <Settings2 />
        <span>设置</span>
        <small>{{ settings.apiKeyConfigured ? settings.apiKeyMasked : 'API Key 未配置' }}</small>
      </Button>
    </aside>

    <button v-if="sidebarOpen" class="agent-sidebar-backdrop" aria-label="关闭会话列表" type="button" @click="sidebarOpen = false" />

    <AgentPanel
      class="agent-chat-main"
      empty-class="agent-chat-empty-state"
      :loading="loadingMessages"
      :messages="chat.messages.value"
      viewport-class="agent-message-viewport"
      @function-output="chat.submitFunctionOutput">
      <template #header>
        <header class="agent-chat-header">
        <IconButton class="agent-mobile-sidebar-trigger" :icon="PanelLeft" label="打开会话列表" @click="sidebarOpen = true" />
        <div class="agent-chat-heading">
          <strong>{{ selectedConversation?.title ?? '新对话' }}</strong>
          <span>对话与元数据配置</span>
        </div>
        <IconButton :icon="Settings2" label="设置 OpenAI 连接" @click="openSettings" />
        </header>
      </template>

      <template #empty>
        <div class="agent-chat-empty-state">
          <div class="agent-empty-mark"><Bot /></div>
          <h1>把需求变成可验证的元数据</h1>
          <p>描述模型、字段、关联和查询，智能体会输出通过 Schema 校验的结构化结果。</p>
          <div class="agent-prompt-suggestions">
            <button
              class="agent-display-text-suggestion"
              :disabled="loading"
              type="button"
              @click="chooseDisplayTextPrompt">
              <Languages />
              <span>中文化全部未翻译元数据</span>
            </button>
            <button type="button" @click="chooseConfigurationPrompt('设计客户、订单和订单明细模型，包含分页查询')">客户与订单模型</button>
            <button type="button" @click="chooseConfigurationPrompt('把年度经营报表需求拆成数据模型和查询契约')">年度报表元数据</button>
          </div>
        </div>
      </template>

      <template #footer>
        <footer class="agent-composer-area">
        <div class="agent-mode-segment" role="radiogroup" aria-label="对话处理模式">
          <button
            :data-active="chatMode === 'auto'"
            role="radio"
            :aria-checked="chatMode === 'auto'"
            type="button"
            @click="changeChatMode('auto')">
            <Sparkles />
            智能识别
          </button>
          <button
            :data-active="chatMode === 'configuration'"
            role="radio"
            :aria-checked="chatMode === 'configuration'"
            type="button"
            @click="changeChatMode('configuration')">
            <Wrench />
            配置模式
          </button>
          <button
            :data-active="chatMode === 'display-text'"
            role="radio"
            :aria-checked="chatMode === 'display-text'"
            :disabled="loading"
            type="button"
            @click="chooseDisplayTextPrompt">
            <Languages />
            中文化
          </button>
        </div>
        <AgentComposer
          :model-value="selectedModelId"
          v-model:prompt="prompt"
          v-model:reasoning-effort="reasoningEffort"
          :busy="loading"
          :context-usage="latestContextUsage"
          :loading-models="modelsLoading"
          :models="models"
          :notice="notice || chat.error.value?.message"
          placeholder="描述你需要的模型、契约或报表…"
          :queued-prompts="queuedPrompts"
          :streaming="isStreaming"
          @move-queued="moveQueued"
          @refresh-models="run(refreshModels)"
          @remove-queued="removeQueued"
          @stop="stopGeneration"
          @submit="submitPrompt"
          @update:model-value="changeModel"
          @update-queued="updateQueued" />
        </footer>
      </template>
    </AgentPanel>

    <Dialog v-model:open="settingsOpen">
      <DialogContent class="agent-settings-dialog">
        <form @submit.prevent="saveSettings">
          <DialogHeader>
            <DialogTitle>OpenAI 连接</DialogTitle>
            <DialogDescription>连接信息保存在服务端，API Key 加密落库且不会回传明文。</DialogDescription>
          </DialogHeader>
          <div class="agent-settings-fields">
            <label for="agent-base-url">
              <span>Base URL</span>
              <Input id="agent-base-url" v-model="baseUrlDraft" autocomplete="url" placeholder="https://api.openai.com" required />
            </label>
            <label for="agent-api-key">
              <span>API Key</span>
              <Input id="agent-api-key" v-model="apiKeyDraft" autocomplete="new-password" placeholder="sk-..." type="password" />
              <small v-if="settings.apiKeyConfigured">当前 {{ settings.apiKeyMasked }}，留空保存则保留原密钥。</small>
            </label>
          </div>
          <DialogFooter>
            <Button type="button" variant="ghost" @click="settingsOpen = false">取消</Button>
            <Button :disabled="loading" type="submit">保存</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </main>
</template>

<style scoped>
.agent-display-text-suggestion {
  display: inline-flex;
  grid-column: 1 / -1;
  gap: 7px;
  align-items: center;
  justify-content: center;
  color: var(--foreground);
  font-weight: 600;
  background: var(--muted);
  border-color: color-mix(in srgb, var(--primary) 55%, var(--border));
}

.agent-display-text-suggestion svg {
  width: 14px;
  height: 14px;
}
</style>
