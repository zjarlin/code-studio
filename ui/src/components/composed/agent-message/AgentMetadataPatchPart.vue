<script setup lang="ts">
import { CheckCircle2, Languages, LoaderCircle, Sparkles } from '@lucide/vue'
import { computed, ref } from 'vue'

import { Badge } from '@/components/generated/shadcn/badge'
import { Button } from '@/components/generated/shadcn/button'
import { LowcodeApi } from '@/lowcode-api'
import type { MetadataTablePatchResult } from '@/types'

import type { AgentMessagePart } from './agent-message-renderers'

const props = defineProps<{ part: AgentMessagePart }>()
const api = new LowcodeApi()
const applying = ref(false)
const appliedCount = ref<number>()
const applyError = ref('')
const applyBlocked = ref(false)
const data = computed(() => ('data' in props.part ? props.part.data : {}) as MetadataTablePatchResult)
const questions = computed(() => data.value.questions ?? [])
const workspaceDisplayText = computed(() => data.value.tableId === 'metadata.display-text:workspace')
const canApply = computed(() => workspaceDisplayText.value
  && !applying.value
  && appliedCount.value === undefined
  && !applyBlocked.value
  && questions.value.length === 0
  && Boolean(data.value.patches?.length))

async function applyWorkspaceDisplayText(): Promise<void> {
  if (!canApply.value) return
  if (!window.confirm(`应用这 ${data.value.patches.length} 项展示文本中文化 Patch？`)) return
  applying.value = true
  applyError.value = ''
  try {
    appliedCount.value = await api.applyAgentDisplayText(data.value)
  } catch (error) {
    applyBlocked.value = true
    applyError.value = error instanceof Error ? error.message : '应用中文化 Patch 失败'
  } finally {
    applying.value = false
  }
}
</script>

<template>
  <section class="agent-structured-result">
    <header><div><Sparkles /><strong>表格 Patch</strong></div><Badge variant="secondary">{{ data.patches?.length ?? 0 }} 项</Badge></header>
    <p class="agent-result-summary">{{ data.summary }}</p>
    <p>调整基于当前表格版本生成，应用前会校验数据是否已变化。</p>
    <footer v-if="workspaceDisplayText" class="agent-configuration-actions">
      <p v-if="questions.length" class="agent-configuration-error">{{ questions.join('；') }}</p>
      <p v-else-if="applyError" class="agent-configuration-error">{{ applyError }}</p>
      <div v-if="appliedCount !== undefined" class="agent-configuration-success">
        <CheckCircle2 />
        <span>已应用 {{ appliedCount }} 项展示文本</span>
      </div>
      <Button
        v-else
        :disabled="!canApply"
        size="sm"
        type="button"
        @click="applyWorkspaceDisplayText">
        <LoaderCircle v-if="applying" class="agent-spin" />
        <Languages v-else />
        {{ applying ? '正在应用' : '应用中文化' }}
      </Button>
    </footer>
  </section>
</template>
