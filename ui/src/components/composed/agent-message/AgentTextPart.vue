<script setup lang="ts">
import { computed } from 'vue'

import type { AgentMessagePart } from './agent-message-renderers'

const props = defineProps<{ part: AgentMessagePart }>()
const text = computed(() => props.part.type === 'text' ? props.part.text : '')
</script>

<template>
  <p v-if="text" class="agent-message-text">{{ text }}</p>
</template>
