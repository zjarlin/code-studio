<script setup lang="ts">
import { Ban, CheckCircle2, CircleX, LoaderCircle } from '@lucide/vue'
import { computed } from 'vue'

import type { AgentObservationData } from '@/types'

import type { AgentMessagePart } from './agent-message-renderers'

const props = defineProps<{ part: AgentMessagePart }>()
const data = computed<AgentObservationData>(() => 'data' in props.part
  ? props.part.data as AgentObservationData
  : { phase: 'agent', state: 'running', label: '' })
</script>

<template>
  <div class="agent-observation" :data-state="data.state">
    <LoaderCircle v-if="data.state === 'running'" class="agent-observation-spinner" />
    <CheckCircle2 v-else-if="data.state === 'completed'" />
    <Ban v-else-if="data.state === 'cancelled'" />
    <CircleX v-else />
    <span>{{ data.label }}</span>
  </div>
</template>
