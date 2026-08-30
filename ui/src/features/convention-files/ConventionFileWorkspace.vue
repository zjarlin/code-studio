<script setup lang="ts">
import { Clock3, Plus, Save, Trash2 } from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'

import IconButton from '@/components/composed/icon-button/IconButton.vue'
import { Button } from '@/components/generated/shadcn/button'
import { Input } from '@/components/generated/shadcn/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/generated/shadcn/select'
import { Switch } from '@/components/generated/shadcn/switch'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/generated/shadcn/table'
import { LowcodeApi } from '@/lowcode-api'
import type { ConventionFileCommand, ConventionFileKind, ConventionFileSummary, LsiLibraryFeature, LsiLibrarySpec } from '@/types'

import { featurePackageName } from '../library-studio/library-draft'

interface ConventionFileRow extends ConventionFileCommand {
  rowKey: string
  packageName?: string
  contributorId?: string
}

const props = withDefaults(defineProps<{
  createKind?: ConventionFileKind
  createRequest?: number
  features: LsiLibraryFeature[]
  readOnly?: boolean
  selectedFeatureId?: number | string
  librarySpec: LsiLibrarySpec
}>(), { createKind: 'SERVICE', createRequest: 0, readOnly: false })
const emit = defineEmits<{ changed: [] }>()
const api = new LowcodeApi()
const rows = ref<ConventionFileRow[]>([])
const loading = ref(false)
const saving = ref(new Set<string>())
const notice = ref('')
let localSequence = 0

const visibleFeatures = computed(() => props.selectedFeatureId == null
  ? props.features
  : props.features.filter((feature) => String(feature.id) === String(props.selectedFeatureId)))

onMounted(async () => {
  await refresh()
  if (props.createRequest > 0) addRow(props.createKind)
})

watch(() => [props.selectedFeatureId, props.features.map((feature) => feature.id).join('|')], () => {
  void refresh()
})

watch(() => props.createRequest, (request, previous) => {
  if (request > previous) addRow(props.createKind)
})

async function refresh(): Promise<void> {
  loading.value = true
  notice.value = ''
  try {
    const values = await api.conventionFiles()
    rows.value = values.filter((value) => owns(value.featureId)).map(toRow)
  } catch (cause) {
    notice.value = cause instanceof Error ? cause.message : '读取约定文件失败'
  } finally {
    loading.value = false
  }
}

function owns(featureId: number | string): boolean {
  return visibleFeatures.value.some((feature) => String(feature.id) === String(featureId))
}

function toRow(value: ConventionFileSummary): ConventionFileRow {
  return {
    ...value,
    rowKey: `convention-file:${value.id}`,
  }
}

function addRow(kind: ConventionFileKind = 'SERVICE'): void {
  if (props.readOnly) return
  const feature = visibleFeatures.value[0]
  if (!feature) {
    notice.value = '请先创建功能目录'
    return
  }
  rows.value = [{
    rowKey: `convention-file:new:${localSequence += 1}`,
    featureId: feature.id,
    fileCode: '',
    name: '',
    className: '',
    kind,
    status: 1,
    description: null,
  }, ...rows.value]
}

function update(row: ConventionFileRow, values: Partial<ConventionFileRow>): void {
  Object.assign(row, values)
  rows.value = [...rows.value]
}

function updateKind(row: ConventionFileRow, value: unknown): void {
  update(row, { kind: String(value) as ConventionFileKind })
}

function calculatedPackage(row: ConventionFileRow): string {
  const feature = props.features.find((item) => String(item.id) === String(row.featureId))
  if (!feature) return ''
  const suffix = row.kind === 'SERVICE' ? 'service' : 'job'
  return `${featurePackageName(props.librarySpec, feature)}.${suffix}`
}

async function save(row: ConventionFileRow): Promise<void> {
  saving.value = new Set(saving.value).add(row.rowKey)
  notice.value = ''
  try {
    const command: ConventionFileCommand = {
      id: row.id,
      featureId: row.featureId,
      fileCode: row.fileCode.trim(),
      name: row.name.trim(),
      className: row.className.trim(),
      kind: row.kind,
      status: row.status,
      description: row.description?.trim() || null,
    }
    const validation = await api.validateConventionFile(command)
    if (!validation.valid) throw new Error(validation.errors.join('；'))
    await api.saveConventionFile(command)
    notice.value = `约定文件“${command.name}”已保存`
    await refresh()
    emit('changed')
  } catch (cause) {
    notice.value = cause instanceof Error ? cause.message : '保存约定文件失败'
  } finally {
    saving.value = new Set([...saving.value].filter((key) => key !== row.rowKey))
  }
}

async function remove(row: ConventionFileRow): Promise<void> {
  if (row.id == null) {
    rows.value = rows.value.filter((item) => item.rowKey !== row.rowKey)
    return
  }
  if (!window.confirm(`删除约定文件“${row.name}”？`)) return
  try {
    await api.deleteConventionFile(row.id)
    await refresh()
    emit('changed')
  } catch (cause) {
    notice.value = cause instanceof Error ? cause.message : '删除约定文件失败'
  }
}
</script>

<template>
  <section class="convention-file-workspace">
    <header class="convention-file-toolbar">
      <div><strong>Service / 定时任务</strong><span>约定文件由生成器创建，业务实现由 IDE 维护</span></div>
      <div class="convention-file-create-actions">
        <Button :disabled="readOnly" size="sm" type="button" variant="outline" @click="addRow('SERVICE')"><Plus />Service</Button>
        <Button :disabled="readOnly" size="sm" type="button" @click="addRow('SCHEDULED_JOB')"><Clock3 />定时任务</Button>
      </div>
    </header>

    <p v-if="notice" class="convention-file-notice" role="status">{{ notice }}</p>

    <div class="convention-file-table-wrap">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>类型</TableHead>
            <TableHead>功能归属</TableHead>
            <TableHead>类名</TableHead>
            <TableHead>文件编码</TableHead>
            <TableHead>注释</TableHead>
            <TableHead>生成包名</TableHead>
            <TableHead>启用</TableHead>
            <TableHead>说明</TableHead>
            <TableHead class="convention-file-actions">操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableEmpty v-if="!loading && rows.length === 0" :colspan="9">暂无约定文件</TableEmpty>
          <TableRow v-for="row in rows" :key="row.rowKey">
            <TableCell>
              <Select :disabled="readOnly" :model-value="row.kind" @update:model-value="updateKind(row, $event)">
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="SERVICE">Service</SelectItem>
                  <SelectItem value="SCHEDULED_JOB">定时任务</SelectItem>
                </SelectContent>
              </Select>
            </TableCell>
            <TableCell>
              <Select :disabled="readOnly" :model-value="String(row.featureId)" @update:model-value="update(row, { featureId: String($event) })">
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent><SelectItem v-for="feature in visibleFeatures" :key="feature.id" :value="String(feature.id)">{{ feature.name }}</SelectItem></SelectContent>
              </Select>
            </TableCell>
            <TableCell><Input :disabled="readOnly" :model-value="row.className" placeholder="OrderService" @update:model-value="update(row, { className: String($event) })" /></TableCell>
            <TableCell><Input :disabled="readOnly" :model-value="row.fileCode" placeholder="order" @update:model-value="update(row, { fileCode: String($event) })" /></TableCell>
            <TableCell><Input :disabled="readOnly" :model-value="row.name" placeholder="订单服务" @update:model-value="update(row, { name: String($event) })" /></TableCell>
            <TableCell><code>{{ calculatedPackage(row) }}</code></TableCell>
            <TableCell><Switch :disabled="readOnly" :model-value="row.status === 1" @update:model-value="update(row, { status: $event ? 1 : 0 })" /></TableCell>
            <TableCell><Input :disabled="readOnly" :model-value="row.description ?? ''" @update:model-value="update(row, { description: String($event) })" /></TableCell>
            <TableCell class="convention-file-actions">
              <IconButton :disabled="readOnly || saving.has(row.rowKey)" :icon="Save" :label="`保存${row.name || row.className || '约定文件'}`" tooltip @click="save(row)" />
              <IconButton :disabled="readOnly" :icon="Trash2" :label="`删除${row.name || row.className || '约定文件'}`" tooltip variant="danger" @click="remove(row)" />
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  </section>
</template>

<style scoped>
.convention-file-workspace { display: grid; min-height: 0; gap: 12px; padding: 12px; }
.convention-file-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.convention-file-toolbar div { display: grid; gap: 2px; }
.convention-file-toolbar .convention-file-create-actions { display: flex; gap: 8px; }
.convention-file-toolbar span { color: var(--muted-foreground); font-size: 12px; }
.convention-file-notice { margin: 0; color: var(--muted-foreground); font-size: 13px; }
.convention-file-table-wrap { min-width: 0; overflow: auto; border: 1px solid var(--border); }
.convention-file-table-wrap table { min-width: 1120px; }
.convention-file-table-wrap :deep(th), .convention-file-table-wrap :deep(td) { height: 42px; padding: 6px 8px; }
.convention-file-table-wrap :deep(input), .convention-file-table-wrap :deep(button[role='combobox']) { height: 32px; }
.convention-file-table-wrap code { white-space: nowrap; font-size: 12px; }
.convention-file-actions { width: 84px; white-space: nowrap; }
@media (max-width: 720px) {
  .convention-file-toolbar { align-items: flex-start; }
  .convention-file-toolbar span { display: none; }
}
</style>
