<script setup lang="ts" generic="Row extends Record<string, unknown>">
import type { HTMLAttributes } from 'vue'
import { computed } from 'vue'

import { TableCell } from '@/components/generated/shadcn/table'
import type { JsonPrimitive } from '@/types'

import MetadataColumnAdjustDialog from './MetadataColumnAdjustDialog.vue'
import type { MetadataPatchApplication, MetadataTableDescriptor } from './metadata-table'

defineOptions({ inheritAttrs: false })

const props = defineProps<{
  class?: HTMLAttributes['class']
  descriptor: MetadataTableDescriptor<Row>
  rows: Row[]
  row: Row
  columnKey: string
  context?: Record<string, JsonPrimitive>
  disabled?: boolean
}>()
const emit = defineEmits<{
  apply: [application: MetadataPatchApplication<Row>]
}>()

const column = computed(() => props.descriptor.columns.find((candidate) => candidate.key === props.columnKey))
</script>

<template>
  <TableCell v-bind="$attrs" :class="['metadata-adjustable-cell', $props.class]">
    <div class="metadata-adjustable-cell-layout">
      <div class="metadata-adjustable-cell-content"><slot /></div>
      <MetadataColumnAdjustDialog
        v-if="column?.editable && !disabled"
        adjust-scope="cell"
        :column-key="columnKey"
        :context="context"
        :descriptor="descriptor"
        :rows="rows"
        :selected-row-keys="[descriptor.rowKey(row)]"
        :show-settings="false"
        @apply="emit('apply', $event)"
      />
    </div>
  </TableCell>
</template>
