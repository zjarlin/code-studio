<script setup lang="ts">
import type { ApiGroup, ApiOperation } from '@platform/openapi-workbench'

defineProps<{
  groups: ApiGroup[]
  selectedId?: string
}>()

const emit = defineEmits<{
  select: [operation: ApiOperation]
}>()

function methodClass(method: string): string {
  return `method-${method}`
}
</script>

<template>
  <nav class="api-operation-tree" aria-label="API 接口列表">
    <section v-for="group in groups" :key="group.name" class="api-operation-group">
      <div class="api-operation-group-heading">
        <strong>{{ group.name }}</strong>
        <span>{{ group.operations.length }}</span>
      </div>
      <p v-if="group.description" class="api-operation-group-description">{{ group.description }}</p>
      <button
        v-for="operation in group.operations"
        :key="operation.id"
        type="button"
        class="api-operation-item"
        :class="{ active: operation.id === selectedId }"
        :aria-pressed="operation.id === selectedId"
        @click="emit('select', operation)">
        <span class="api-method" :class="methodClass(operation.method)">{{ operation.method }}</span>
        <span class="api-operation-copy">
          <strong>{{ operation.summary }}</strong>
          <span class="api-operation-address-preview">
            <span v-for="address in operation.addresses" :key="address.path">{{ address.path }}</span>
          </span>
        </span>
      </button>
    </section>
    <div v-if="!groups.length" class="api-empty-state">没有匹配的接口</div>
  </nav>
</template>
