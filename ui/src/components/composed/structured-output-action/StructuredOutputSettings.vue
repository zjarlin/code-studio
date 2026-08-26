<script setup lang="ts">
import { Settings2 } from '@lucide/vue'
import { ref, watch } from 'vue'

import AgentModelSelector from '@/components/composed/agent-model-selector/AgentModelSelector.vue'
import IconButton from '@/components/composed/icon-button/IconButton.vue'
import { Button } from '@/components/generated/shadcn/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/generated/shadcn/dialog'
import { Input } from '@/components/generated/shadcn/input'
import { Textarea } from '@/components/generated/shadcn/textarea'
import { LowcodeApi } from '@/lowcode-api'
import type { AgentDefinitionDraft, AgentProviderModel } from '@/types'

import {
  formatJson,
  normalizeAgentDraft,
  parseJsonObject,
  validateAgentDraft,
} from './agent-definition-draft'

const props = defineProps<{
  agentCode: string
}>()

const api = new LowcodeApi()
const open = ref(false)
const loading = ref(false)
const modelsLoading = ref(false)
const models = ref<AgentProviderModel[]>([])
const draft = ref<AgentDefinitionDraft>()
const schemaText = ref('')
const error = ref('')
const notice = ref('')

watch(open, (value) => {
  if (value) {
    void load()
  }
})

async function load(): Promise<void> {
  loading.value = true
  clearMessage()
  try {
    const summaries = await api.agents()
    const summary = summaries.find((item) => item.agentCode === props.agentCode)
    if (!summary) {
      throw new Error(`结构化输出配置不存在：${props.agentCode}`)
    }
    const loaded = normalizeAgentDraft(await api.agentDetail(summary.id))
    draft.value = loaded
    schemaText.value = formatJson(loaded.structuredOutput.schema)
    await refreshModels()
  } catch (cause) {
    showError(cause)
  } finally {
    loading.value = false
  }
}

async function refreshModels(): Promise<void> {
  modelsLoading.value = true
  clearMessage()
  try {
    models.value = await api.agentModels()
  } catch (cause) {
    showError(cause)
  } finally {
    modelsLoading.value = false
  }
}

async function save(): Promise<void> {
  const current = draft.value
  if (!current) return
  loading.value = true
  clearMessage()
  try {
    const command: AgentDefinitionDraft = {
      ...current,
      modelCode: current.modelCode.trim(),
      instructions: current.instructions.trim(),
      structuredOutput: {
        ...current.structuredOutput,
        name: current.structuredOutput.name.trim(),
        schema: parseJsonObject(schemaText.value, '输出 Schema'),
        strict: true,
      },
    }
    const localErrors = validateAgentDraft(command)
    if (localErrors.length) {
      throw new Error(localErrors.join('；'))
    }
    const validation = await api.validateAgent(command)
    if (!validation.valid) {
      throw new Error(validation.errors.join('；'))
    }
    await api.saveAgent(command)
    draft.value = command
    schemaText.value = formatJson(command.structuredOutput.schema)
    notice.value = '结构化输出设置已保存'
  } catch (cause) {
    showError(cause)
  } finally {
    loading.value = false
  }
}

function showError(cause: unknown): void {
  error.value = cause instanceof Error ? cause.message : '读取结构化输出设置失败'
}

function clearMessage(): void {
  error.value = ''
  notice.value = ''
}
</script>

<template>
  <IconButton :icon="Settings2" label="设置 AI 补全" @click="open = true" />

  <Dialog v-model:open="open">
    <DialogContent class="structured-output-settings-dialog sm:max-w-3xl">
      <form @submit.prevent="save">
        <DialogHeader>
          <DialogTitle>结构化输出设置</DialogTitle>
          <DialogDescription>配置当前补全功能的模型、提示词和 JSON Schema，保存后下一次魔法棒调用立即生效。</DialogDescription>
        </DialogHeader>

        <div v-if="error || notice" class="structured-output-settings-message" :class="{ error: Boolean(error) }">
          {{ error || notice }}
        </div>

        <div v-if="draft" class="structured-output-settings-fields">
          <label>
            <span>模型</span>
            <AgentModelSelector
              v-model="draft.modelCode"
              :disabled="loading"
              :loading="modelsLoading"
              :models="models"
              @refresh="refreshModels" />
          </label>
          <label>
            <span>输出名称</span>
            <Input v-model="draft.structuredOutput.name" autocomplete="off" />
          </label>
          <label class="structured-output-settings-wide">
            <span>系统提示词</span>
            <Textarea v-model="draft.instructions" rows="7" />
          </label>
          <label class="structured-output-settings-wide">
            <span>输出 Schema</span>
            <Textarea v-model="schemaText" class="structured-output-schema-editor" rows="14" spellcheck="false" />
          </label>
        </div>
        <div v-else-if="loading" class="structured-output-settings-state">正在读取设置…</div>

        <DialogFooter>
          <Button type="button" variant="ghost" @click="open = false">关闭</Button>
          <Button :disabled="loading || !draft" type="submit">保存设置</Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>

<style scoped>
.structured-output-settings-dialog form {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.structured-output-settings-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
  max-height: min(68vh, 680px);
  overflow: auto;
  padding: 2px;
}

.structured-output-settings-fields label {
  display: grid;
  gap: 5px;
  min-width: 0;
  color: var(--muted-foreground);
  font-size: 11px;
  font-weight: 600;
}

.structured-output-settings-wide {
  grid-column: 1 / -1;
}

.structured-output-schema-editor {
  min-height: 260px;
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1.55;
}

.structured-output-settings-message,
.structured-output-settings-state {
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--success);
  font-size: 11px;
}

.structured-output-settings-message.error {
  color: var(--destructive);
}

@media (max-width: 720px) {
  .structured-output-settings-fields {
    grid-template-columns: minmax(0, 1fr);
  }

  .structured-output-settings-wide {
    grid-column: 1;
  }
}
</style>
