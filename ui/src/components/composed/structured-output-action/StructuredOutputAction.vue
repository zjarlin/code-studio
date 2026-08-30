<script setup lang="ts">
import { WandSparkles } from '@lucide/vue'
import { ref } from 'vue'

import IconButton from '@/components/composed/icon-button/IconButton.vue'
import { LowcodeApi } from '@/lowcode-api'
import type { JsonObject } from '@/types'

const props = withDefaults(defineProps<{
  agentCode: string
  input: JsonObject
  disabled?: boolean
  label?: string
}>(), {
  disabled: false,
  label: 'AI 补全',
})
const emit = defineEmits<{
  generated: [output: JsonObject]
  error: [message: string]
}>()

const api = new LowcodeApi()
const busy = ref(false)

async function generate(): Promise<void> {
  busy.value = true
  try {
    const output = await api.generateStructuredOutput(props.agentCode, props.input)
    emit('generated', output)
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : 'AI 补全失败'
    emit('error', message)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <IconButton
    :disabled="disabled || busy"
    :icon="WandSparkles"
    :label="busy ? 'AI 正在补全' : label"
    @click="generate"
  />
</template>
