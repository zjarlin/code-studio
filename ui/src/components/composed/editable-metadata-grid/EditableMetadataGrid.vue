<script setup lang="ts">
import { computed } from 'vue'

import { Table, TableBody, TableEmpty } from '@/components/generated/shadcn/table'

const props = withDefaults(defineProps<{
  columns: number
  count: number
  emptyText?: string
  minWidth?: number
  title: string
}>(), {
  emptyText: '暂无配置',
  minWidth: 840,
})

const tableStyle = computed<Record<string, string>>(() => ({
  '--metadata-grid-min-width': `${props.minWidth}px`,
}))
</script>

<template>
  <section class="editable-metadata-grid">
    <header class="editable-metadata-grid-toolbar">
      <div>
        <strong>{{ title }}</strong>
        <small>{{ count }}</small>
      </div>
      <slot name="actions" />
    </header>
    <Table class="editable-metadata-grid-table" :style="tableStyle">
      <slot name="header" />
      <slot v-if="count > 0" name="body" />
      <TableBody v-else>
        <TableEmpty :colspan="columns">{{ emptyText }}</TableEmpty>
      </TableBody>
    </Table>
  </section>
</template>

<style scoped>
.editable-metadata-grid {
  min-width: 0;
  overflow: hidden;
  background: var(--background);
  border: 1px solid var(--border);
  border-radius: 6px;
}

.editable-metadata-grid-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  min-height: 38px;
  padding: 4px 6px 4px 10px;
  border-bottom: 1px solid var(--border);
}

.editable-metadata-grid-toolbar > div:first-child {
  display: flex;
  gap: 7px;
  align-items: center;
  min-width: 0;
}

.editable-metadata-grid-toolbar strong {
  overflow: hidden;
  font-size: 11px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editable-metadata-grid-toolbar small {
  min-width: 20px;
  padding: 1px 5px;
  color: var(--muted-foreground);
  font-family: var(--font-mono);
  font-size: 9px;
  text-align: center;
  background: var(--muted);
  border-radius: 4px;
}

.editable-metadata-grid-table :deep(th) {
  height: 30px;
  padding: 0 7px;
  color: var(--muted-foreground);
  font-size: 9px;
  font-weight: 650;
  background: var(--muted);
  white-space: nowrap;
}

.editable-metadata-grid-table {
  min-width: var(--metadata-grid-min-width);
}

.editable-metadata-grid-table :deep(td) {
  height: 38px;
  padding: 3px 5px;
  vertical-align: middle;
  background: var(--background);
}

.editable-metadata-grid-table :deep(tr:hover td) {
  background: color-mix(in srgb, var(--muted) 55%, var(--background));
}

.editable-metadata-grid-table :deep(input),
.editable-metadata-grid-table :deep(select) {
  width: 100%;
  min-width: 0;
  height: 28px;
  padding: 0 6px;
  color: var(--foreground);
  font-size: 10px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 4px;
  outline: 0;
}

.editable-metadata-grid-table :deep(input:hover),
.editable-metadata-grid-table :deep(select:hover) {
  border-color: var(--border);
  background: var(--background);
}

.editable-metadata-grid-table :deep(input:focus),
.editable-metadata-grid-table :deep(select:focus) {
  border-color: var(--ring);
  background: var(--background);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--ring) 18%, transparent);
}

.editable-metadata-grid-table :deep(input:disabled),
.editable-metadata-grid-table :deep(select:disabled) {
  color: var(--muted-foreground);
  cursor: not-allowed;
}

.editable-metadata-grid-table :deep([data-slot='checkbox']) {
  margin: 0 auto;
}
</style>
