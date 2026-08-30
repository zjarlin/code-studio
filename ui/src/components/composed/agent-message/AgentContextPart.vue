<script setup lang="ts">
import { Gauge } from '@lucide/vue'
import { computed } from 'vue'

import type { AgentContextUsage } from '@/types'

import type { AgentMessagePart } from './agent-message-renderers'

const props = defineProps<{ part: AgentMessagePart }>()
const usage = computed(() => (
  'data' in props.part ? props.part.data : {}
) as unknown as AgentContextUsage)

function compactNumber(value: number): string {
  return new Intl.NumberFormat('zh-CN', {
    maximumFractionDigits: 1,
    notation: 'compact',
  }).format(value)
}
</script>

<template>
  <div class="agent-context-part">
    <Gauge />
    <span>{{ compactNumber(usage.inputTokens) }} / {{ compactNumber(usage.contextWindow) }}</span>
    <small v-if="usage.contextWindowEstimated">估算窗口</small>
    <small v-if="usage.compactedMessages">已压缩 {{ usage.compactedMessages }} 条</small>
  </div>
</template>
