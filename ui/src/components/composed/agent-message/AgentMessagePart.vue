<script setup lang="ts">
import { computed } from 'vue'

import type { AgentMessagePart } from './agent-message-renderers'
import { resolveAgentMessageRenderer } from './agent-message-renderers'

const props = defineProps<{ part: AgentMessagePart }>()
const emit = defineEmits<{
  functionOutput: [callId: string, output: unknown]
}>()
const renderer = computed(() => resolveAgentMessageRenderer(props.part.type))
</script>

<template>
  <component
    :is="renderer"
    :part="part"
    @output="(callId: string, output: unknown) => emit('functionOutput', callId, output)" />
</template>
