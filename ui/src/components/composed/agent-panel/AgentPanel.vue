<script setup lang="ts">
import { Bot } from '@lucide/vue'
import { nextTick, ref, watch } from 'vue'

import AgentMessagePart from '@/components/composed/agent-message/AgentMessagePart.vue'
import { snapshotAgentMessagePart } from '@/components/composed/agent-message/agent-message-renderers'
import type { AgentUiMessage } from '@/types'

const props = withDefaults(defineProps<{
  as?: string
  messages: AgentUiMessage[]
  loading?: boolean
  viewportClass?: string
  emptyClass?: string
}>(), {
  as: 'section',
  loading: false,
  viewportClass: 'agent-panel-messages',
  emptyClass: 'agent-panel-empty',
})
const emit = defineEmits<{
  functionOutput: [callId: string, output: unknown]
}>()
const viewport = ref<HTMLElement>()

watch(() => props.messages, () => {
  void nextTick(() => {
    if (viewport.value) viewport.value.scrollTop = viewport.value.scrollHeight
  })
}, { deep: true })
</script>

<template>
  <component :is="as" class="agent-panel">
    <slot name="header" />
    <div ref="viewport" :class="viewportClass">
      <slot v-if="loading" name="loading">
        <div class="agent-chat-loading">正在读取会话...</div>
      </slot>
      <slot v-else-if="!messages.length" name="empty">
        <div :class="emptyClass"><Bot /></div>
      </slot>
      <div v-else class="agent-message-list">
        <article
          v-for="message in messages"
          :key="message.id"
          class="agent-message"
          :data-role="message.role">
          <div v-if="message.role === 'assistant'" class="agent-message-avatar"><Bot /></div>
          <div class="agent-message-body">
            <AgentMessagePart
              v-for="(part, index) in message.parts"
              :key="`${message.id}-${part.type}-${index}`"
              :part="snapshotAgentMessagePart(part)"
              @function-output="(callId: string, output: unknown) => emit('functionOutput', callId, output)" />
          </div>
        </article>
      </div>
    </div>
    <slot name="footer" />
  </component>
</template>
