<script setup lang="ts">
import { ArrowDown, ArrowUp, Send, Square, Trash2 } from '@lucide/vue'
import { computed, ref } from 'vue'

import AgentModelSelector from '@/components/composed/agent-model-selector/AgentModelSelector.vue'
import IconButton from '@/components/composed/icon-button/IconButton.vue'
import { Input } from '@/components/generated/shadcn/input'
import { Textarea } from '@/components/generated/shadcn/textarea'
import type {
  AgentContextUsage,
  AgentProviderModel,
  AgentQueuedPrompt,
  AgentReasoningEffort,
  AgentSendBehavior,
} from '@/types'

const props = withDefaults(defineProps<{
  prompt: string
  modelValue: string
  models: AgentProviderModel[]
  reasoningEffort: AgentReasoningEffort
  queuedPrompts?: AgentQueuedPrompt[]
  contextUsage?: AgentContextUsage
  loadingModels?: boolean
  streaming?: boolean
  busy?: boolean
  notice?: string
  placeholder: string
  sendLabel?: string
  compact?: boolean
}>(), {
  queuedPrompts: () => [],
  contextUsage: undefined,
  loadingModels: false,
  streaming: false,
  busy: false,
  notice: '',
  sendLabel: '发送消息',
  compact: false,
})

const emit = defineEmits<{
  'update:prompt': [value: string]
  'update:modelValue': [value: string]
  'update:reasoningEffort': [value: AgentReasoningEffort]
  submit: [behavior: AgentSendBehavior]
  stop: []
  refreshModels: []
  removeQueued: [id: string]
  moveQueued: [id: string, direction: -1 | 1]
  updateQueued: [id: string, text: string]
}>()

const sendBehavior = ref<AgentSendBehavior>('queue')
const selectedModel = computed(() => props.models.find((model) => model.id === props.modelValue))
const selectedAvailable = computed(() => Boolean(selectedModel.value))
const canSubmit = computed(() => props.prompt.trim().length > 0 && selectedAvailable.value && !props.busy)
const usage = computed<AgentContextUsage>(() => props.contextUsage ?? {
  inputTokens: 0,
  outputTokens: 0,
  totalTokens: 0,
  contextWindow: selectedModel.value?.contextWindow ?? 128_000,
  contextWindowEstimated: selectedModel.value?.contextWindowEstimated ?? true,
  compactedMessages: 0,
})
const usagePercent = computed(() => Math.min(
  100,
  Math.round(usage.value.inputTokens / Math.max(usage.value.contextWindow, 1) * 100),
))

function submit(behavior = sendBehavior.value): void {
  if (!canSubmit.value) return
  emit('submit', behavior)
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.shiftKey) return
  event.preventDefault()
  const behavior = event.metaKey || event.ctrlKey
    ? (sendBehavior.value === 'queue' ? 'steer' : 'queue')
    : sendBehavior.value
  submit(behavior)
}

function updateReasoning(event: Event): void {
  emit('update:reasoningEffort', (event.target as HTMLSelectElement).value as AgentReasoningEffort)
}

function compactNumber(value: number): string {
  return new Intl.NumberFormat('zh-CN', {
    maximumFractionDigits: 1,
    notation: 'compact',
  }).format(value)
}
</script>

<template>
  <div class="agent-composer-shell" :class="{ compact }">
    <p v-if="notice" class="agent-chat-notice">{{ notice }}</p>
    <div v-if="queuedPrompts.length" class="agent-prompt-queue" aria-label="待发送消息">
      <div v-for="(item, index) in queuedPrompts" :key="item.id" class="agent-queued-prompt">
        <span class="agent-queue-order">{{ index + 1 }}</span>
        <Input
          :aria-label="`编辑待发送消息 ${index + 1}`"
          :model-value="item.text"
          @update:model-value="emit('updateQueued', item.id, String($event))" />
        <small>{{ item.modelId }}</small>
        <IconButton
          :disabled="index === 0"
          :icon="ArrowUp"
          :label="`上移待发送消息 ${index + 1}`"
          @click="emit('moveQueued', item.id, -1)" />
        <IconButton
          :disabled="index === queuedPrompts.length - 1"
          :icon="ArrowDown"
          :label="`下移待发送消息 ${index + 1}`"
          @click="emit('moveQueued', item.id, 1)" />
        <IconButton
          :icon="Trash2"
          :label="`删除待发送消息 ${index + 1}`"
          variant="danger"
          @click="emit('removeQueued', item.id)" />
      </div>
    </div>

    <form class="agent-composer" @submit.prevent="submit()">
      <Textarea
        :model-value="prompt"
        aria-label="向元数据智能体发送消息"
        :disabled="busy"
        :placeholder="placeholder"
        rows="2"
        @keydown="handleKeydown"
        @update:model-value="emit('update:prompt', String($event))" />

      <div class="agent-composer-toolbar">
        <select v-model="sendBehavior" aria-label="发送方式">
          <option value="queue">排队</option>
          <option value="steer">引导</option>
        </select>

        <div class="agent-composer-spacer" />

        <AgentModelSelector
          :disabled="busy"
          :loading="loadingModels"
          :models="models"
          :model-value="modelValue"
          @refresh="emit('refreshModels')"
          @update:model-value="emit('update:modelValue', $event)" />

        <select
          aria-label="推理强度"
          :disabled="busy"
          :value="reasoningEffort"
          @change="updateReasoning">
          <option value="provider">默认推理</option>
          <option value="minimal">最小</option>
          <option value="low">低</option>
          <option value="medium">中</option>
          <option value="high">高</option>
          <option value="xhigh">极高</option>
        </select>

        <details class="agent-context-usage">
          <summary :aria-label="`上下文窗口已用 ${usagePercent}%`">
            <span :style="{ '--usage': `${usagePercent * 3.6}deg` }" />
            {{ usagePercent }}%
          </summary>
          <div>
            <strong>上下文窗口</strong>
            <span>已用 {{ compactNumber(usage.inputTokens) }}，共 {{ compactNumber(usage.contextWindow) }}</span>
            <small v-if="usage.contextWindowEstimated">窗口大小为估算值</small>
            <small v-if="usage.compactedMessages">已自动压缩 {{ usage.compactedMessages }} 条消息</small>
          </div>
        </details>

        <IconButton
          v-if="streaming"
          :icon="Square"
          label="停止生成"
          @click="emit('stop')" />
        <IconButton
          class="agent-send-button"
          :disabled="!canSubmit"
          :icon="Send"
          :label="streaming ? (sendBehavior === 'queue' ? '排队发送' : '引导当前任务') : sendLabel"
          @click="submit()" />
      </div>
    </form>
  </div>
</template>
