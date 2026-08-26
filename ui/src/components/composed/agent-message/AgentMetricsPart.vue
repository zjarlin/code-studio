<script setup lang="ts">
import { computed } from 'vue'

import type { AgentMetric } from '@/types'

import type { AgentMessagePart } from './agent-message-renderers'

const props = defineProps<{ part: AgentMessagePart }>()
const metrics = computed<AgentMetric[]>(() => 'data' in props.part
  ? props.part.data as AgentMetric[]
  : [])
</script>

<template>
  <div class="agent-metrics-grid">
    <div v-for="metric in metrics" :key="metric.label" class="agent-metric">
      <span>{{ metric.label }}</span>
      <strong>{{ metric.value }}<small v-if="metric.unit"> {{ metric.unit }}</small></strong>
      <em v-if="metric.change">{{ metric.change }}</em>
    </div>
  </div>
</template>
