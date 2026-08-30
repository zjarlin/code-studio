<script setup lang="ts">
import { computed } from 'vue'

import type { AgentMessagePart } from './agent-message-renderers'

const props = defineProps<{ part: AgentMessagePart }>()
const preview = computed(() => JSON.stringify(props.part, null, 2))
</script>

<template>
  <details class="agent-unknown-part">
    <summary>{{ part.type }}</summary>
    <pre>{{ preview }}</pre>
  </details>
</template>
