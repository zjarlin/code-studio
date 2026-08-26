<script setup lang="ts" generic="Row extends Record<string, unknown>">
import { Check, Languages, LoaderCircle, Replace, WandSparkles } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import StructuredOutputSettings from '@/components/composed/structured-output-action/StructuredOutputSettings.vue'
import { Button } from '@/components/generated/shadcn/button'
import { Checkbox } from '@/components/generated/shadcn/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/generated/shadcn/dialog'
import { Input } from '@/components/generated/shadcn/input'
import { LowcodeApi } from '@/lowcode-api'
import type {
  JsonObject,
  JsonPrimitive,
  MetadataCellPatch,
  MetadataTableContext,
  MetadataTablePatchResult,
} from '@/types'

import {
  applyMetadataPatches,
  createLiteralReplacementPatches,
  normalizeMetadataPatchResult,
  toMetadataJsonValue,
} from './metadata-table'
import type {
  MetadataPatchApplication,
  MetadataTableColumn,
  MetadataTableDescriptor,
} from './metadata-table'

type Operation = 'translate' | 'replace' | 'fill' | 'custom'
const props = withDefaults(defineProps<{
  descriptor: MetadataTableDescriptor<Row>
  rows: Row[]
  columnKey: string
  selectedRowKeys?: string[]
  context?: Record<string, JsonPrimitive>
  agentCode?: string
  adjustScope?: 'cell' | 'column'
  showSettings?: boolean
}>(), {
  adjustScope: 'column',
  agentCode: 'metadataColumnCompletion',
  selectedRowKeys: () => [],
  showSettings: true,
})
const emit = defineEmits<{
  apply: [application: MetadataPatchApplication<Row>]
}>()

const api = new LowcodeApi()
const open = ref(false)
const operation = ref<Operation>('translate')
const targetLanguage = ref('英文')
const search = ref('')
const replacement = ref('')
const instruction = ref('')
const loading = ref(false)
const notice = ref('')
const result = ref<MetadataTablePatchResult>()
const selectedPatchKeys = ref(new Set<string>())

const column = computed<MetadataTableColumn<Row> | undefined>(() =>
  props.descriptor.columns.find((candidate) => candidate.key === props.columnKey),
)
const scopedRows = computed(() => {
  if (!props.selectedRowKeys.length) return props.rows
  const selected = new Set(props.selectedRowKeys)
  return props.rows.filter((row) => selected.has(props.descriptor.rowKey(row)))
})
const selectedPatches = computed(() => result.value?.patches.filter((patch) =>
  selectedPatchKeys.value.has(patchKey(patch)),
) ?? [])
const canRun = computed(() => Boolean(column.value?.editable) && scopedRows.value.length > 0)
const scopeLabel = computed(() => props.adjustScope === 'cell' ? '单元格' : '列')
const textColumn = computed(() => column.value?.kind === 'scalar' && scopedRows.value.some((row) =>
  typeof row[props.columnKey] === 'string',
))

watch(open, (value) => {
  if (value && !textColumn.value) operation.value = 'custom'
})

function tableContext(): MetadataTableContext {
  const visibleColumns = props.descriptor.columns.filter((item) => item.key === props.columnKey || item.context)
  return {
    tableId: props.descriptor.tableId,
    revision: props.descriptor.revision,
    targetColumnKey: props.columnKey,
    rowIdentityKey: props.descriptor.rowIdentityKey,
    context: props.context ?? {},
    operations: props.descriptor.operations,
    columns: visibleColumns.map((item) => ({
      key: String(item.key),
      label: item.label,
      kind: item.kind,
      agentEditable: Boolean(item.editable),
      context: item.key === props.columnKey ? false : item.context,
      options: item.options,
    })),
    rows: scopedRows.value.map((row) => ({
      rowKey: props.descriptor.rowKey(row),
      values: Object.fromEntries(
        visibleColumns.map((item) => [String(item.key), toMetadataJsonValue(row[item.key])]),
      ),
    })),
    selection: {
      rowKeys: scopedRows.value.map(props.descriptor.rowKey),
      filteredRowCount: props.rows.length,
    },
  }
}

function removeChineseFullStops(): void {
  operation.value = 'replace'
  search.value = '。'
  replacement.value = ''
  notice.value = ''
  result.value = undefined
  if (!canRun.value) return
  setResult(createLiteralReplacementPatches(
    props.descriptor,
    scopedRows.value,
    props.columnKey,
    search.value,
    replacement.value,
  ))
}

async function runAdjustment(): Promise<void> {
  notice.value = ''
  result.value = undefined
  if (!canRun.value) return
  if (operation.value === 'replace') {
    if (!search.value) {
      notice.value = '请输入查找内容'
      return
    }
    setResult(createLiteralReplacementPatches(
      props.descriptor,
      scopedRows.value,
      props.columnKey,
      search.value,
      replacement.value,
    ))
    return
  }
  loading.value = true
  try {
    const output = await api.generateStructuredOutput(props.agentCode, {
      operation: operationPayload(),
      table: tableContext() as unknown as JsonObject,
    })
    setResult(normalizeMetadataPatchResult(output))
  } catch (cause) {
    notice.value = cause instanceof Error ? cause.message : '智能调整失败'
  } finally {
    loading.value = false
  }
}

function confirmPatches(): void {
  if (!result.value) return
  const application = applyMetadataPatches(
    props.descriptor,
    props.rows,
    result.value,
    selectedPatches.value,
  )
  emit('apply', application)
  notice.value = application.conflicts.length
    ? `已应用 ${application.applied.length} 项，跳过 ${application.conflicts.length} 项冲突`
    : `已应用 ${application.applied.length} 项到本地草稿`
  if (!application.conflicts.length) open.value = false
}

function operationInstruction(): string {
  if (operation.value === 'translate') return `翻译为${targetLanguage.value}，保留专有名词和空值。`
  if (operation.value === 'fill') return '仅补全目标列中的空值。'
  return instruction.value.trim() || '根据上下文调整目标列，使其语义清晰一致。'
}

function operationPayload(): JsonObject {
  const payload = {
    type: operation.value,
    instruction: operationInstruction(),
  }
  if (operation.value !== 'translate') return payload
  return { ...payload, targetLanguage: targetLanguage.value }
}

function setResult(value: MetadataTablePatchResult): void {
  result.value = value
  selectedPatchKeys.value = new Set(value.patches.map(patchKey))
  notice.value = value.questions?.join('；') ?? ''
}

function togglePatch(patch: MetadataCellPatch, selected: boolean | 'indeterminate'): void {
  const next = new Set(selectedPatchKeys.value)
  if (selected === true) next.add(patchKey(patch))
  else next.delete(patchKey(patch))
  selectedPatchKeys.value = next
}

function patchKey(patch: MetadataCellPatch): string {
  return `${patch.rowKey}:${patch.columnKey}`
}

function displayValue(value: JsonPrimitive): string {
  if (value === null) return '空'
  if (value === '') return '空字符串'
  return String(value)
}

function displayMatch(edit: MetadataCellPatch['edits'][number]): string {
  return edit.path ? `${edit.path}：${displayValue(edit.match)}` : displayValue(edit.match)
}
</script>

<template>
  <Dialog v-model:open="open">
    <div class="metadata-column-ai-actions" @click.stop>
      <DialogTrigger as-child>
        <Button class="metadata-column-action" size="icon-sm" type="button" variant="ghost" :aria-label="`AI 调整${column?.label ?? columnKey}${scopeLabel}`">
          <WandSparkles />
        </Button>
      </DialogTrigger>
      <StructuredOutputSettings v-if="showSettings" :agent-code="agentCode" />
    </div>
    <DialogContent class="metadata-adjust-dialog">
      <DialogHeader>
        <DialogTitle>智能调整“{{ column?.label }}”{{ scopeLabel }}</DialogTitle>
        <DialogDescription>
          将处理 {{ scopedRows.length }} 行；只应用匹配片段，未匹配内容保持原样。<template v-if="showSettings">模型、提示词和 Schema 可由设置按钮配置。</template>
        </DialogDescription>
      </DialogHeader>

      <div class="metadata-adjust-operations" role="group" aria-label="列调整方式">
        <Button v-if="textColumn" :variant="operation === 'translate' ? 'secondary' : 'ghost'" @click="operation = 'translate'"><Languages />翻译</Button>
        <Button v-if="textColumn" :variant="operation === 'replace' ? 'secondary' : 'ghost'" @click="operation = 'replace'"><Replace />替换</Button>
        <Button :variant="operation === 'fill' ? 'secondary' : 'ghost'" @click="operation = 'fill'">补全空值</Button>
        <Button :variant="operation === 'custom' ? 'secondary' : 'ghost'" @click="operation = 'custom'"><WandSparkles />自定义</Button>
      </div>

      <div v-if="textColumn" class="metadata-adjust-presets" role="group" aria-label="常用调整">
        <span>常用调整</span>
        <Button size="sm" type="button" variant="outline" :disabled="!canRun" @click="removeChineseFullStops">
          <Replace />去掉中文句号
        </Button>
      </div>

      <div class="metadata-adjust-fields">
        <label v-if="operation === 'translate'"><span>目标语言</span><Input v-model="targetLanguage" /></label>
        <template v-else-if="operation === 'replace'">
          <label><span>查找</span><Input v-model="search" /></label>
          <label><span>替换为</span><Input v-model="replacement" /></label>
        </template>
        <label v-else-if="operation === 'custom'"><span>调整要求</span><Input v-model="instruction" /></label>
      </div>

      <Button :disabled="loading || !canRun" type="button" @click="runAdjustment">
        <LoaderCircle v-if="loading" class="agent-spin" /><WandSparkles v-else />生成 Patch
      </Button>
      <p v-if="notice" class="metadata-adjust-notice">{{ notice }}</p>

      <div v-if="result" class="metadata-patch-preview">
        <div class="metadata-patch-row metadata-patch-head"><span /><span>路径 / 匹配片段</span><span>替换片段</span></div>
        <div v-for="patch in result.patches" :key="patchKey(patch)" class="metadata-patch-row">
          <Checkbox :model-value="selectedPatchKeys.has(patchKey(patch))" @update:model-value="togglePatch(patch, $event)" />
          <span class="metadata-patch-fragments"><span v-for="(edit, index) in patch.edits" :key="index">{{ displayMatch(edit) }}</span></span>
          <strong class="metadata-patch-fragments"><span v-for="(edit, index) in patch.edits" :key="index">{{ displayValue(edit.replacement) }}</span></strong>
        </div>
        <div v-if="!result.patches.length" class="inline-empty">没有可应用的差异</div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="open = false">取消</Button>
        <Button :disabled="!selectedPatches.length" @click="confirmPatches"><Check />应用到草稿</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
