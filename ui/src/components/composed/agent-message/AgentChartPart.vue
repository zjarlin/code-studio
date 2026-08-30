<script setup lang="ts">
import { computed } from 'vue'

import type { AgentChartData } from '@/types'

import type { AgentMessagePart } from './agent-message-renderers'

const props = defineProps<{ part: AgentMessagePart }>()
const chart = computed<AgentChartData>(() => 'data' in props.part
  ? props.part.data as AgentChartData
  : { series: [] })
const maximum = computed(() => Math.max(...chart.value.series.map((item) => item.value), 1))
</script>

<template>
  <figure class="agent-chart">
    <figcaption v-if="chart.title">{{ chart.title }}</figcaption>
    <div v-for="item in chart.series" :key="item.label" class="agent-chart-row">
      <span>{{ item.label }}</span>
      <div><i :style="{ width: `${Math.max((item.value / maximum) * 100, 2)}%`, backgroundColor: item.color }" /></div>
      <strong>{{ item.value }}</strong>
    </div>
  </figure>
</template>
