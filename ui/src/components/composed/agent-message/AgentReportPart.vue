<script setup lang="ts">
import { BarChart3 } from '@lucide/vue'
import { computed } from 'vue'

import type { AgentReportData } from '@/types'

import type { AgentMessagePart } from './agent-message-renderers'

const props = defineProps<{ part: AgentMessagePart }>()
const report = computed<AgentReportData>(() => 'data' in props.part
  ? props.part.data as AgentReportData
  : { title: '', metrics: [] })
</script>

<template>
  <section class="agent-report">
    <header><BarChart3 /><div><strong>{{ report.title }}</strong><span v-if="report.period">{{ report.period }}</span></div></header>
    <p v-if="report.summary">{{ report.summary }}</p>
    <div v-if="report.metrics?.length" class="agent-metrics-grid">
      <div v-for="metric in report.metrics" :key="metric.label" class="agent-metric">
        <span>{{ metric.label }}</span><strong>{{ metric.value }} {{ metric.unit ?? '' }}</strong><em v-if="metric.change">{{ metric.change }}</em>
      </div>
    </div>
  </section>
</template>
