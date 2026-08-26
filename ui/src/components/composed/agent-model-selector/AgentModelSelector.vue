<script setup lang="ts">
import { RefreshCw } from '@lucide/vue'
import { computed, watch } from 'vue'

import IconButton from '@/components/composed/icon-button/IconButton.vue'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/generated/shadcn/select'
import type { AgentProviderModel } from '@/types'

const props = withDefaults(defineProps<{
  modelValue: string
  models: AgentProviderModel[]
  loading?: boolean
  disabled?: boolean
}>(), {
  loading: false,
  disabled: false,
})
const emit = defineEmits<{
  'update:modelValue': [value: string]
  refresh: []
}>()

const selectedAvailable = computed(() => props.models.some((model) => model.id === props.modelValue))
const placeholder = computed(() => {
  if (props.loading) return '正在读取模型…'
  if (!props.models.length) return '暂无可用模型'
  return '选择对话模型'
})
const selectedLabel = computed(() => {
  if (!props.modelValue) return placeholder.value
  if (!selectedAvailable.value) return `${props.modelValue}（上游已不可用）`
  return props.modelValue
})

watch(
  () => [props.modelValue, props.models] as const,
  () => {
    const fallbackModelId = props.models[0]?.id
    if (!fallbackModelId || selectedAvailable.value) return
    emit('update:modelValue', fallbackModelId)
  },
  { immediate: true },
)

function updateValue(value: unknown): void {
  if (typeof value === 'string') {
    emit('update:modelValue', value)
  }
}
</script>

<template>
  <div class="agent-model-selector">
    <Select
      :disabled="disabled || loading || !models.length"
      :model-value="modelValue || undefined"
      @update:model-value="updateValue">
      <SelectTrigger aria-label="选择对话模型" class="min-w-0 flex-1" size="sm">
        <SelectValue>{{ selectedLabel }}</SelectValue>
      </SelectTrigger>
      <SelectContent>
        <SelectGroup>
          <SelectItem v-if="modelValue && !selectedAvailable" :value="modelValue">
            {{ modelValue }}（上游已不可用）
          </SelectItem>
          <SelectItem v-for="model in models" :key="model.id" :value="model.id">
            {{ model.id }}
          </SelectItem>
        </SelectGroup>
      </SelectContent>
    </Select>
    <IconButton
      :disabled="disabled || loading"
      :icon="RefreshCw"
      label="刷新上游模型"
      @click="emit('refresh')" />
  </div>
</template>
