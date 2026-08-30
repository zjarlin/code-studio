<script setup lang="ts">
import { Bot, Braces, CheckCircle2, Database, FileCode2, HelpCircle, LoaderCircle, Save } from '@lucide/vue'
import { computed, ref } from 'vue'

import { Badge } from '@/components/generated/shadcn/badge'
import { Button } from '@/components/generated/shadcn/button'
import { LowcodeApi } from '@/lowcode-api'
import type { AgentMetadataResult, MetadataConfigurationApplyResult } from '@/types'

import type { AgentMessagePart } from './agent-message-renderers'

const props = defineProps<{ part: AgentMessagePart }>()
const api = new LowcodeApi()
const applying = ref(false)
const applied = ref<MetadataConfigurationApplyResult>()
const applyError = ref('')
const data = computed<AgentMetadataResult>(() => 'data' in props.part
  ? props.part.data as AgentMetadataResult
  : {})
const models = computed(() => (data.value.models ?? []).map((model) => ({
  ...model,
  fields: model.fields ?? [],
})))
const dtos = computed(() => data.value.dtos ?? [])
const contracts = computed(() => (data.value.contracts ?? []).map((contract) => ({
  ...contract,
  operations: contract.operations ?? [],
})))
const questions = computed(() => data.value.questions ?? [])
const agents = computed(() => data.value.agents ?? [])
const isConfiguration = computed(() => props.part.type === 'data-configuration')
const changeCount = computed(() => models.value.length + dtos.value.length + contracts.value.length + agents.value.length)
const dtoCount = computed(() => dtos.value.length)

async function applyConfiguration(): Promise<void> {
  if (applying.value || applied.value || questions.value.length || !changeCount.value) return
  if (!window.confirm(`应用这 ${changeCount.value} 项元数据配置？已有对象将按内部身份更新。`)) return
  applying.value = true
  applyError.value = ''
  try {
    applied.value = await api.applyAgentMetadata(data.value)
  } catch (error) {
    applyError.value = error instanceof Error ? error.message : '应用配置失败'
  } finally {
    applying.value = false
  }
}
</script>

<template>
  <section class="agent-structured-result">
    <header>
      <div>
        <Braces />
        <strong>元数据草案</strong>
      </div>
      <div class="agent-result-counts">
        <Badge variant="secondary"><Database />{{ models.length }} 模型</Badge>
        <Badge v-if="dtoCount" variant="secondary"><Braces />{{ dtoCount }} DTO</Badge>
        <Badge variant="secondary"><FileCode2 />{{ contracts.length }} 契约</Badge>
        <Badge v-if="agents.length" variant="secondary"><Bot />{{ agents.length }} Agent</Badge>
      </div>
    </header>

    <p v-if="data.summary" class="agent-result-summary">{{ data.summary }}</p>

    <div v-if="models.length" class="agent-model-results">
        <details v-for="model in models" :key="model.modelCode" open>
          <summary>
            <span>{{ model.name }}</span>
          <small>{{ model.fields.length }} 字段</small>
        </summary>
        <p v-if="model.description">{{ model.description }}</p>
        <div v-if="model.fields.length" class="agent-result-table-wrap">
          <table>
            <thead><tr><th>字段</th><th>类型</th><th>约束</th><th>说明</th></tr></thead>
            <tbody>
              <tr v-for="field in model.fields" :key="field.fieldCode">
                <td>{{ field.label || '未命名字段' }}</td>
                <td>{{ field.kotlinType }}</td>
                <td>{{ field.required ? '必填' : '可选' }}</td>
                <td>{{ field.remark || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
    </div>

    <div v-if="dtos.length" class="agent-model-results">
      <details v-for="dto in dtos" :key="dto.dtoCode" open>
        <summary>
          <span>{{ dto.name }}</span>
          <small>{{ dto.fields.length }} 字段 · {{ dto.kind }}</small>
        </summary>
      </details>
    </div>

    <div v-if="contracts.length" class="agent-model-results">
      <details v-for="contract in contracts" :key="contract.contractCode" open>
        <summary>
          <span>{{ contract.name }}</span>
          <code>{{ contract.path }}</code>
          <small>{{ contract.operations.length }} 接口</small>
        </summary>
        <div v-if="contract.operations.length" class="agent-result-table-wrap">
          <table>
            <thead><tr><th>接口</th><th>传输</th><th>方法</th><th>路径</th></tr></thead>
            <tbody>
              <tr v-for="operation in contract.operations" :key="operation.operationCode">
                <td>{{ operation.name }}</td>
                <td>{{ operation.transport }}</td>
                <td>{{ operation.method }}</td>
                <td><code>{{ operation.path }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
    </div>

    <div v-if="agents.length" class="agent-model-results">
      <details v-for="agent in agents" :key="agent.agentCode" open>
        <summary>
          <span>{{ agent.name }}</span>
          <small>Agent</small>
        </summary>
        <p>{{ agent.instructions }}</p>
      </details>
    </div>

    <div v-if="questions.length" class="agent-result-questions">
      <HelpCircle />
      <div><strong>待确认</strong><p v-for="question in questions" :key="question">{{ question }}</p></div>
    </div>

    <footer v-if="isConfiguration" class="agent-configuration-actions">
      <p v-if="applyError" class="agent-configuration-error">{{ applyError }}</p>
      <div v-if="applied" class="agent-configuration-success">
        <CheckCircle2 />
        <span>配置已应用：{{ Object.keys(applied.modelIds).length }} 模型、{{ Object.keys(applied.dtoIds).length }} DTO、{{ Object.keys(applied.contractIds).length }} 契约、{{ Object.keys(applied.agentIds).length }} Agent</span>
      </div>
      <Button
        v-else
        :disabled="applying || questions.length > 0 || changeCount === 0"
        size="sm"
        type="button"
        @click="applyConfiguration">
        <LoaderCircle v-if="applying" class="agent-spin" />
        <Save v-else />
        {{ applying ? '正在应用' : '应用配置' }}
      </Button>
    </footer>
  </section>
</template>
