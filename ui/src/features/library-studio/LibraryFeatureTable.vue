<script setup lang="ts">
import { Plus, Save, Trash2 } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import IconButton from '@/components/composed/icon-button/IconButton.vue'
import MetadataColumnAdjustDialog from '@/components/composed/metadata-table/MetadataColumnAdjustDialog.vue'
import MetadataTableCell from '@/components/composed/metadata-table/MetadataTableCell.vue'
import MetadataTableHead from '@/components/composed/metadata-table/MetadataTableHead.vue'
import { createTableRevision } from '@/components/composed/metadata-table/metadata-table'
import type { MetadataPatchApplication, MetadataTableDescriptor } from '@/components/composed/metadata-table/metadata-table'
import { Button } from '@/components/generated/shadcn/button'
import { Input } from '@/components/generated/shadcn/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/generated/shadcn/select'
import { Table, TableBody, TableCell, TableEmpty, TableHeader, TableRow } from '@/components/generated/shadcn/table'
import type { LsiLibraryFeature } from '@/types'
import { featureCodeFromName } from './library-draft'

interface FeatureRow extends Record<string, unknown> {
  rowKey: string
  id?: number | string
  libraryId: number | string
  parentId?: number | string | null
  featureCode: string
  name: string
  description?: string | null
}

const props = defineProps<{
  features: LsiLibraryFeature[]
  packagePrefix: string
  createFeature: () => Omit<LsiLibraryFeature, 'id'>
  saveRow: (feature: Omit<LsiLibraryFeature, 'id'> & { id?: number | string }) => Promise<LsiLibraryFeature>
}>()
const emit = defineEmits<{
  delete: [feature: LsiLibraryFeature]
  select: [featureId: number | string]
}>()

const rows = ref<FeatureRow[]>([])
const dirty = ref(new Set<string>())
const saving = ref(new Set<string>())
const error = ref('')
let sequence = 0

const parentOptions = computed(() => props.features)
const descriptor = computed<MetadataTableDescriptor<FeatureRow>>(() => {
  const value: Omit<MetadataTableDescriptor<FeatureRow>, 'revision'> = {
    tableId: 'library.features',
    rowIdentityKey: 'id|featureCode',
    rowKey: (row) => row.rowKey,
    columns: [
      { key: 'featureCode', label: '功能编码', kind: 'scalar', context: true },
      { key: 'name', label: '名称', kind: 'scalar', editable: true, context: true },
      {
        key: 'parentId', label: '父分类', kind: 'enum', editable: true, context: true,
        options: [
          { label: 'Library 根', value: null },
          ...parentOptions.value.map((feature) => ({ label: feature.name, value: String(feature.id) })),
        ],
      },
      { key: 'description', label: '说明', kind: 'scalar', editable: true, context: false },
    ],
    operations: ['translate', 'replace', 'fill', 'custom'],
  }
  return { ...value, revision: createTableRevision(value, rows.value) }
})

watch(() => props.features, (features) => {
  rows.value = features.map((feature) => ({ ...feature, rowKey: `feature:${feature.id}` }))
  dirty.value = new Set()
}, { immediate: true })

function addRow(): void {
  const feature = props.createFeature()
  const rowKey = `feature:new:${sequence += 1}`
  rows.value = [...rows.value, { ...feature, rowKey }]
  dirty.value = new Set(dirty.value).add(rowKey)
}

function patch(row: FeatureRow, values: Partial<FeatureRow>): void {
  if (row.id == null && (values.name !== undefined || values.parentId !== undefined)) {
    const parentId = values.parentId !== undefined ? values.parentId : row.parentId
    const parentCode = parentOptions.value.find((item) => String(item.id) === String(parentId))?.featureCode ?? ''
    values.featureCode = featureCodeFromName(values.name ?? row.name, parentCode)
  }
  Object.assign(row, values)
  rows.value = [...rows.value]
  dirty.value = new Set(dirty.value).add(row.rowKey)
}

async function save(row: FeatureRow): Promise<void> {
  const rowKey = row.rowKey
  saving.value = new Set(saving.value).add(row.rowKey)
  error.value = ''
  try {
    const saved = await props.saveRow({
      id: row.id,
      libraryId: row.libraryId,
      parentId: row.parentId ?? null,
      featureCode: row.featureCode.trim(),
      name: row.name.trim(),
      description: row.description?.trim() || null,
    })
    Object.assign(row, saved, { rowKey: `feature:${saved.id}` })
    dirty.value = new Set([...dirty.value].filter((key) => key !== rowKey))
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存功能分类失败'
  } finally {
    saving.value = new Set([...saving.value].filter((key) => key !== rowKey))
  }
}

function remove(row: FeatureRow): void {
  if (row.id == null) {
    rows.value = rows.value.filter((candidate) => candidate.rowKey !== row.rowKey)
    return
  }
  emit('delete', row as LsiLibraryFeature)
}

function packageName(row: FeatureRow): string {
  return `${props.packagePrefix.replace(/\.$/, '')}.${row.featureCode.trim()}`
}

function applyPatches(application: MetadataPatchApplication<FeatureRow>): void {
  rows.value = application.rows.map((row) => {
    if (row.id != null) return row
    const parentCode = parentOptions.value.find((item) => String(item.id) === String(row.parentId))?.featureCode ?? ''
    return { ...row, featureCode: featureCodeFromName(row.name, parentCode) }
  })
  const nextDirty = new Set(dirty.value)
  application.applied.forEach((patch) => nextDirty.add(patch.rowKey))
  dirty.value = nextDirty
}
</script>

<template>
  <section class="library-table-workspace">
    <header class="library-table-toolbar">
      <div><strong>功能分类</strong><span>分类关系使用 ID 保存，源码包路径由名称自动生成。</span></div>
      <Button size="sm" variant="outline" @click="addRow"><Plus />新建分类</Button>
    </header>
    <p v-if="error" class="studio-notice error">{{ error }}</p>
    <Table class="metadata-table">
      <TableHeader><TableRow>
        <MetadataTableHead mode="agent">
          名称
          <template #action><MetadataColumnAdjustDialog column-key="name" :context="{ packagePrefix: props.packagePrefix }" :descriptor="descriptor" :rows="rows" @apply="applyPatches" /></template>
        </MetadataTableHead>
        <MetadataTableHead mode="agent">
          父分类
          <template #action><MetadataColumnAdjustDialog column-key="parentId" :context="{ packagePrefix: props.packagePrefix }" :descriptor="descriptor" :rows="rows" @apply="applyPatches" /></template>
        </MetadataTableHead>
        <MetadataTableHead mode="agent">
          说明
          <template #action><MetadataColumnAdjustDialog column-key="description" :context="{ packagePrefix: props.packagePrefix }" :descriptor="descriptor" :rows="rows" @apply="applyPatches" /></template>
        </MetadataTableHead>
        <MetadataTableHead mode="system">计算包名</MetadataTableHead>
        <MetadataTableHead class="metadata-action-column" mode="system">操作</MetadataTableHead>
      </TableRow></TableHeader>
      <TableBody>
        <TableEmpty v-if="!rows.length" :colspan="5">暂无功能分类</TableEmpty>
        <TableRow v-for="row in rows" :key="row.rowKey" @click="row.id != null && emit('select', row.id)">
          <MetadataTableCell column-key="name" :context="{ packagePrefix: props.packagePrefix }" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches">
            <Input :aria-label="`${row.name}名称`" :model-value="row.name" @click.stop @update:model-value="patch(row, { name: String($event) })" />
          </MetadataTableCell>
          <MetadataTableCell column-key="parentId" :context="{ packagePrefix: props.packagePrefix }" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches" @click.stop>
            <Select :model-value="row.parentId == null ? 'root' : String(row.parentId)" @update:model-value="patch(row, { parentId: $event === 'root' ? null : String($event) })">
              <SelectTrigger><SelectValue placeholder="Library 根" /></SelectTrigger>
              <SelectContent><SelectItem value="root">Library 根</SelectItem><SelectItem v-for="parent in parentOptions.filter((item) => String(item.id) !== String(row.id))" :key="parent.id" :value="String(parent.id)">{{ parent.name }}</SelectItem></SelectContent>
            </Select>
          </MetadataTableCell>
          <MetadataTableCell column-key="description" :context="{ packagePrefix: props.packagePrefix }" :descriptor="descriptor" :row="row" :rows="rows" @apply="applyPatches">
            <Input :model-value="row.description ?? ''" @click.stop @update:model-value="patch(row, { description: String($event) })" />
          </MetadataTableCell>
          <TableCell><code>{{ packageName(row) }}</code></TableCell>
          <TableCell class="metadata-row-actions" @click.stop>
            <IconButton :disabled="!dirty.has(row.rowKey) || saving.has(row.rowKey)" :icon="Save" :label="`保存${row.name}`" @click="save(row)" />
            <IconButton :icon="Trash2" :label="`删除${row.name}`" variant="danger" @click="remove(row)" />
          </TableCell>
        </TableRow>
      </TableBody>
    </Table>
  </section>
</template>
