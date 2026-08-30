<script setup lang="ts">
import type { HTMLAttributes } from 'vue'

import { TableHead } from '@/components/generated/shadcn/table'

export type MetadataEntryMode = 'agent' | 'manual' | 'system'

defineProps<{
  class?: HTMLAttributes['class']
  mode: MetadataEntryMode
}>()
</script>

<template>
  <TableHead
    :class="['metadata-semantic-head', `metadata-semantic-head-${mode}`, $props.class]"
    :data-metadata-entry="mode"
  >
    <span class="metadata-semantic-head-content">
      <span v-if="mode === 'manual'" aria-hidden="true" class="metadata-manual-marker" title="手动填写">✋🏻</span>
      <span class="metadata-semantic-head-label"><slot /></span>
      <span v-if="$slots.action" class="metadata-semantic-head-action"><slot name="action" /></span>
    </span>
  </TableHead>
</template>

<style scoped>
.metadata-semantic-head {
  border-bottom: 1px solid var(--border);
}

.metadata-semantic-head-content {
  display: flex;
  gap: 5px;
  align-items: center;
  min-width: 0;
}

.metadata-semantic-head-label {
  overflow: hidden;
  text-overflow: ellipsis;
}

.metadata-manual-marker {
  flex: 0 0 auto;
  font-size: 11px;
  line-height: 1;
}

.metadata-semantic-head-action {
  display: inline-flex;
  gap: 1px;
  align-items: center;
  margin-left: auto;
}
</style>
