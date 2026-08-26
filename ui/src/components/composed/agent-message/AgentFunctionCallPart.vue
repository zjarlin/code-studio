<script setup lang="ts">
import { Check, X } from '@lucide/vue'
import { computed } from 'vue'

import { Button } from '@/components/generated/shadcn/button'
import type { AgentFunctionCallData } from '@/types'

import type { AgentMessagePart } from './agent-message-renderers'

const props = defineProps<{ part: AgentMessagePart }>()
const emit = defineEmits<{
  output: [callId: string, output: unknown]
}>()
type FunctionCallPart = AgentMessagePart & { type: 'data-function-call'; data: AgentFunctionCallData }
const data = computed(() => (props.part as FunctionCallPart).data)
const formattedArguments = computed(() => {
  try {
    return JSON.stringify(JSON.parse(data.value.arguments), null, 2)
  } catch {
    return data.value.arguments
  }
})
</script>

<template>
  <section class="agent-function-call" :data-status="data.status">
    <header>
      <strong>{{ data.name }}</strong>
      <span>{{ data.status === 'submitted' ? '已提交' : '等待确认' }}</span>
    </header>
    <pre>{{ formattedArguments }}</pre>
    <footer v-if="data.status === 'pending'">
      <Button size="sm" type="button" @click="emit('output', data.callId, { approved: false })">
        <X data-icon="inline-start" />
        拒绝
      </Button>
      <Button size="sm" type="button" @click="emit('output', data.callId, { approved: true })">
        <Check data-icon="inline-start" />
        批准
      </Button>
    </footer>
  </section>
</template>
