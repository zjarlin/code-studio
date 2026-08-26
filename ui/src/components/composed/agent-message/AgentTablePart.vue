<script setup lang="ts">
import { computed } from 'vue'

import type { AgentTableData } from '@/types'

import type { AgentMessagePart } from './agent-message-renderers'

const props = defineProps<{ part: AgentMessagePart }>()
const table = computed<AgentTableData>(() => 'data' in props.part
  ? props.part.data as AgentTableData
  : { columns: [], rows: [] })
</script>

<template>
  <div class="agent-result-table-wrap">
    <table>
      <thead><tr><th v-for="column in table.columns" :key="column.key">{{ column.label }}</th></tr></thead>
      <tbody>
        <tr v-for="(row, rowIndex) in table.rows" :key="rowIndex">
          <td v-for="column in table.columns" :key="column.key">{{ row[column.key] }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
