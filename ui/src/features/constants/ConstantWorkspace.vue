<script setup lang="ts">
import { Braces, Plus, Save, Search, Trash2, X } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import IconButton from '@/components/composed/icon-button/IconButton.vue'
import StructuredOutputAction from '@/components/composed/structured-output-action/StructuredOutputAction.vue'
import StructuredOutputSettings from '@/components/composed/structured-output-action/StructuredOutputSettings.vue'
import { Badge } from '@/components/generated/shadcn/badge'
import { Button } from '@/components/generated/shadcn/button'
import { Input } from '@/components/generated/shadcn/input'
import { toResourceCodeFromClassName } from '@/lib/identifier'
import { zhCN } from '@/studio-i18n'
import type { LsiApplicationFeature } from '@/types'
import type { JsonObject } from '@/types'

import {
  ConstantApi,
  ConstantApiError,
} from './constant-api'
import type {
  ConstantGroup,
  ConstantItem,
  ConstantValueType,
} from './constant-api'

const props = withDefaults(defineProps<{
  createRequest?: number
  feature?: LsiApplicationFeature
  features?: LsiApplicationFeature[]
  selectedFeatureCode?: string
}>(), { createRequest: 0 })
const emit = defineEmits<{
  countChange: [count: number]
}>()

const api = new ConstantApi()
const messages = zhCN.constants
const groups = ref<ConstantGroup[]>([])
const search = ref('')
const loading = ref(false)
const mutating = ref(false)
const error = ref('')
const notice = ref('')
let handledCreateRequest = 0

const availableFeatures = computed(() => {
  const features = props.features?.length ? props.features : props.feature ? [props.feature] : []
  const selected = features.find((feature) => feature.featureCode === props.selectedFeatureCode)
  return selected ? [selected] : features
})
const defaultFeature = computed(() =>
  availableFeatures.value.find((feature) => feature.featureCode === props.selectedFeatureCode)
  ?? availableFeatures.value[0],
)
const draft = ref<ConstantGroup>(emptyGroup())
const editorFeature = computed(() =>
  availableFeatures.value.find((feature) => String(feature.featureId) === String(draft.value.featureId))
  ?? defaultFeature.value,
)
const scopeName = computed(() => defaultFeature.value?.name ?? '当前 Library')

const visibleGroups = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return groups.value
  return groups.value.filter((group) =>
    group.groupCode.toLowerCase().includes(keyword)
    || group.objectName.toLowerCase().includes(keyword)
    || group.description.toLowerCase().includes(keyword),
  )
})

watch(
  () => [props.selectedFeatureCode, availableFeatures.value.map((feature) => feature.featureId).join('|')],
  load,
  { immediate: true },
)
watch(() => props.createRequest, (request, previous) => {
  if (request > previous) {
    handledCreateRequest = request
    createGroup()
  }
})

async function load(): Promise<void> {
  loading.value = true
  clearMessage()
  try {
    groups.value = (await Promise.all(availableFeatures.value.map((feature) => api.list(feature))))
      .flat()
      .filter((group, index, all) => all.findIndex((candidate) => String(candidate.id) === String(group.id)) === index)
    emit('countChange', groups.value.length)
    if (props.createRequest > handledCreateRequest) {
      handledCreateRequest = props.createRequest
      draft.value = emptyGroup()
      return
    }
    const active = groups.value.find((group) => String(group.id) === String(draft.value.id))
      ?? groups.value[0]
    draft.value = active ? cloneGroup(active) : emptyGroup()
  } catch (cause) {
    handleError(cause)
  } finally {
    loading.value = false
  }
}

function selectGroup(group: ConstantGroup): void {
  clearMessage()
  draft.value = cloneGroup(group)
}

function createGroup(): void {
  clearMessage()
  draft.value = emptyGroup()
}

function updateObjectName(objectName: string): void {
  const previousDefault = toResourceCodeFromClassName(draft.value.objectName)
  const identityFollowsName = draft.value.id === undefined && (
    !draft.value.groupCode || draft.value.groupCode === previousDefault
  )
  draft.value = {
    ...draft.value,
    objectName,
    groupCode: identityFollowsName
      ? toResourceCodeFromClassName(objectName)
      : draft.value.groupCode,
  }
}

function addConstant(): void {
  draft.value.constants.push(emptyConstant())
}

function removeConstant(index: number): void {
  draft.value.constants.splice(index, 1)
}

function constantCompletionInput(constant: ConstantItem): JsonObject {
  return {
    feature: {
      code: editorFeature.value?.featureCode ?? '',
      name: editorFeature.value?.name ?? '',
      packageName: editorFeature.value?.packageName ?? '',
    },
    group: {
      code: draft.value.groupCode,
      objectName: draft.value.objectName,
      description: draft.value.description,
    },
    constant: {
      name: constant.name,
      type: constant.type,
      value: constant.value,
      description: constant.description,
    },
  }
}

function canCompleteConstant(constant: ConstantItem): boolean {
  return [constant.name, constant.value, constant.description].some((value) => value.trim().length > 0)
}

function applyConstantCompletion(index: number, output: JsonObject): void {
  const current = draft.value.constants[index]
  if (!current) return
  const generatedType = output.type
  const type = typeof generatedType === 'string' && valueTypes.some((item) => item.value === generatedType)
    ? generatedType as ConstantValueType
    : current.type
  draft.value.constants[index] = {
    ...current,
    name: generatedText(output.name) || current.name,
    type,
    value: current.value || generatedText(output.value),
    description: generatedText(output.description) || current.description,
  }
  clearMessage()
  notice.value = messages.constantCompleted
}

function generatedText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function showCompletionError(message: string): void {
  notice.value = ''
  error.value = message
}

async function save(): Promise<void> {
  const validationError = validateDraft(draft.value)
  if (validationError) {
    error.value = validationError
    return
  }
  await mutate(async () => {
    const saved = await api.save(normalizeGroup(draft.value))
    await load()
    const persisted = groups.value.find((group) => String(group.id) === String(saved.id))
    if (persisted) draft.value = cloneGroup(persisted)
    notice.value = messages.groupSaved
  })
}

async function deleteGroup(): Promise<void> {
  const id = draft.value.id
  const confirmation = messages.deleteGroupConfirmation.replace('{name}', draft.value.objectName)
  if (id === undefined || !window.confirm(confirmation)) return
  await mutate(async () => {
    await api.delete(id)
    draft.value = emptyGroup()
    await load()
    notice.value = messages.groupDeleted
  })
}

async function mutate(action: () => Promise<void>): Promise<void> {
  mutating.value = true
  clearMessage()
  try {
    await action()
  } catch (cause) {
    handleError(cause)
  } finally {
    mutating.value = false
  }
}

function emptyGroup(): ConstantGroup {
  return {
    groupCode: '',
    featureId: defaultFeature.value?.featureId ?? 0,
    featurePackageName: defaultFeature.value?.packageName ?? '',
    contributorId: defaultFeature.value?.contributorId ?? '',
    objectName: '',
    description: '',
    constants: [emptyConstant()],
  }
}

function emptyConstant(): ConstantItem {
  return { name: '', type: 'STRING', value: '', description: '' }
}

function cloneGroup(group: ConstantGroup): ConstantGroup {
  return {
    ...group,
    constants: group.constants.map((constant) => ({ ...constant })),
  }
}

function normalizeGroup(group: ConstantGroup): ConstantGroup {
  return {
    ...group,
    groupCode: group.groupCode.trim(),
    featureId: editorFeature.value?.featureId ?? group.featureId,
    featurePackageName: editorFeature.value?.packageName ?? group.featurePackageName,
    contributorId: editorFeature.value?.contributorId ?? group.contributorId,
    objectName: group.objectName.trim(),
    description: group.description.trim(),
    constants: group.constants.map((constant) => ({
      ...constant,
      name: constant.name.trim(),
      value: constant.value,
      description: constant.description.trim(),
    })),
  }
}

function validateDraft(group: ConstantGroup): string | undefined {
  if (!/^[a-z][A-Za-z0-9]*$/.test(group.groupCode.trim())) return messages.groupCodeCamelCase
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(group.objectName.trim())) return messages.objectNameInvalid
  if (!group.description.trim()) return messages.objectCommentRequired
  if (group.constants.length === 0) return messages.groupItemRequired
  for (const constant of group.constants) {
    if (!/^[A-Z][A-Z0-9_]*$/.test(constant.name.trim())) return messages.constantNameInvalid
    if (!constant.description.trim()) {
      return messages.constantCommentRequired.replace('{name}', constant.name || messages.unnamedConstant)
    }
  }
  return undefined
}

function handleError(cause: unknown): void {
  error.value = cause instanceof Error ? cause.message : messages.operationFailed
  if (cause instanceof ConstantApiError && cause.code === 401) {
    error.value = messages.unauthorized
  } else if (cause instanceof TypeError) {
    error.value = messages.networkFailed
  }
}

function clearMessage(): void {
  error.value = ''
  notice.value = ''
}

const valueTypes: Array<{ value: ConstantValueType, label: string }> = [
  { value: 'STRING', label: 'String' },
  { value: 'INT', label: 'Int' },
  { value: 'LONG', label: 'Long' },
  { value: 'BOOLEAN', label: 'Boolean' },
]
</script>

<template>
  <div class="constant-workspace">
    <aside class="constant-index-pane">
      <header class="constant-index-toolbar">
        <div class="constant-search">
          <Search />
          <Input v-model="search" :aria-label="messages.searchGroups" :placeholder="messages.searchGroups" />
        </div>
        <IconButton :icon="Plus" :label="messages.createGroup" @click="createGroup" />
      </header>

      <div class="constant-index" role="list" :aria-label="`${scopeName}${messages.groupAriaSuffix}`">
        <div class="constant-index-root">
          <Braces />
          <strong>{{ scopeName }}</strong>
          <Badge variant="secondary">{{ groups.length }}</Badge>
        </div>
        <div v-if="loading" class="constant-index-state">{{ messages.loading }}</div>
        <button
          v-for="group in visibleGroups"
          :key="String(group.id ?? group.groupCode)"
          class="constant-index-item"
          :class="{ active: String(group.id) === String(draft.id) }"
          type="button"
          @click="selectGroup(group)"
        >
          <strong>{{ group.objectName }}</strong>
        </button>
        <div v-if="!loading && visibleGroups.length === 0" class="constant-index-state">{{ messages.noGroups }}</div>
      </div>
    </aside>

    <section class="constant-editor-pane">
      <header class="constant-editor-toolbar">
        <div>
          <strong>{{ draft.objectName || messages.newGroup }}</strong>
          <code>{{ editorFeature?.packageName }}.generated.constants</code>
        </div>
        <div class="constant-editor-actions">
          <IconButton
            v-if="draft.id !== undefined"
            :disabled="mutating"
            :icon="Trash2"
            :label="messages.deleteGroup"
            @click="deleteGroup"
          />
          <Button :disabled="mutating" size="sm" type="button" @click="save">
            <Save />{{ messages.save }}
          </Button>
        </div>
      </header>

      <div v-if="error || notice" class="constant-message" :class="{ error: Boolean(error) }">
        <span>{{ error || notice }}</span>
        <button :aria-label="messages.closeMessage" type="button" @click="clearMessage"><X /></button>
      </div>

      <div class="constant-editor-content">
        <div class="constant-group-fields">
          <label>
            <span>{{ messages.kotlinObjectName }}</span>
            <Input :model-value="draft.objectName" :placeholder="messages.objectNamePlaceholder" @update:model-value="updateObjectName(String($event))" />
          </label>
          <label class="constant-group-description">
            <span>{{ messages.objectComment }}</span>
            <Input v-model="draft.description" :placeholder="messages.objectCommentPlaceholder" />
          </label>
        </div>

        <div class="constant-table-toolbar">
          <strong>{{ messages.items }}</strong>
          <div class="constant-table-actions">
            <StructuredOutputSettings agent-code="constantItemCompletion" />
            <IconButton :icon="Plus" :label="messages.addConstant" @click="addConstant" />
          </div>
        </div>
        <div class="constant-table" role="table" :aria-label="messages.items">
          <div class="constant-table-row constant-table-head" role="row">
            <span data-metadata-entry="agent">{{ messages.name }}</span>
            <span data-metadata-entry="agent">{{ messages.type }}</span>
            <span data-metadata-entry="manual">✋🏻 {{ messages.value }}</span>
            <span data-metadata-entry="agent">{{ messages.comment }}</span>
            <span data-metadata-entry="system" />
          </div>
          <div
            v-for="(constant, index) in draft.constants"
            :key="constant.id ?? index"
            class="constant-table-row"
            role="row"
          >
            <Input
              v-model="constant.name"
              :aria-label="messages.constantName"
              :placeholder="messages.constantNamePlaceholder"
            />
            <select v-model="constant.type" :aria-label="messages.constantType">
              <option v-for="type in valueTypes" :key="type.value" :value="type.value">{{ type.label }}</option>
            </select>
            <select v-if="constant.type === 'BOOLEAN'" v-model="constant.value" :aria-label="messages.constantValue">
              <option value="true">true</option>
              <option value="false">false</option>
            </select>
            <Input v-else v-model="constant.value" :aria-label="messages.constantValue" />
            <Input
              v-model="constant.description"
              :aria-label="messages.constantComment"
              :placeholder="messages.constantCommentPlaceholder"
            />
            <div class="constant-row-actions">
              <StructuredOutputAction
                agent-code="constantItemCompletion"
                :disabled="!canCompleteConstant(constant)"
                :input="constantCompletionInput(constant)"
                :label="canCompleteConstant(constant) ? 'AI 补全常量' : messages.constantCompletionInputRequired"
                @error="showCompletionError"
                @generated="applyConstantCompletion(index, $event)"
              />
              <IconButton :icon="Trash2" :label="messages.deleteConstant" @click="removeConstant(index)" />
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.constant-workspace {
  display: grid;
  height: 100%;
  min-height: 420px;
  grid-template-columns: minmax(190px, 240px) minmax(0, 1fr);
  border-top: 1px solid var(--border);
}

.constant-index-pane {
  min-width: 0;
  border-right: 1px solid var(--border);
  background: var(--muted);
}

.constant-index-toolbar,
.constant-editor-toolbar,
.constant-table-toolbar,
.constant-editor-actions,
.constant-table-actions,
.constant-row-actions,
.constant-index-root,
.constant-search {
  display: flex;
  align-items: center;
}

.constant-index-toolbar {
  gap: 6px;
  padding: 8px;
  border-bottom: 1px solid var(--border);
}

.constant-search {
  position: relative;
  min-width: 0;
  flex: 1;
}

.constant-search > svg {
  position: absolute;
  left: 8px;
  width: 14px;
  pointer-events: none;
}

.constant-search input {
  height: 30px;
  padding-left: 28px;
}

.constant-index {
  overflow: auto;
  height: calc(100% - 47px);
  padding: 6px;
}

.constant-index-root {
  gap: 6px;
  padding: 7px;
  font-size: 12px;
}

.constant-index-root svg {
  width: 14px;
}

.constant-index-root .badge {
  margin-left: auto;
}

.constant-index-item {
  display: flex;
  width: 100%;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  padding: 7px 9px;
  border: 1px solid transparent;
  border-radius: 4px;
  text-align: left;
}

.constant-index-item:hover,
.constant-index-item.active {
  border-color: var(--border);
  background: var(--background);
}

.constant-index-item strong,
.constant-index-item code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.constant-index-item strong {
  font-size: 12px;
}

.constant-index-item code,
.constant-index-state {
  color: var(--muted-foreground);
  font-size: 10px;
}

.constant-index-state {
  padding: 8px;
}

.constant-editor-pane {
  display: grid;
  min-width: 0;
  grid-template-rows: 58px auto minmax(0, 1fr);
}

.constant-editor-pane:not(:has(.constant-message)) {
  grid-template-rows: 58px minmax(0, 1fr);
}

.constant-editor-toolbar {
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--border);
}

.constant-editor-toolbar > div:first-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.constant-editor-toolbar strong {
  font-size: 14px;
}

.constant-editor-toolbar code {
  overflow: hidden;
  color: var(--muted-foreground);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.constant-editor-actions {
  gap: 4px;
}

.constant-table-actions,
.constant-row-actions {
  gap: 2px;
}

.constant-editor-actions svg,
.constant-table-toolbar svg {
  width: 14px;
}

.constant-message {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: space-between;
  padding: 5px 12px;
  border-bottom: 1px solid var(--border);
  color: var(--success);
  font-size: 11px;
}

.constant-message.error {
  color: var(--destructive);
}

.constant-message button svg {
  width: 14px;
}

.constant-editor-content {
  overflow: auto;
  min-width: 0;
  padding: 14px;
}

.constant-group-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding-bottom: 14px;
}

.constant-group-fields label {
  display: grid;
  gap: 5px;
  color: var(--muted-foreground);
  font-size: 11px;
}

.constant-group-fields input,
.constant-table input,
.constant-table select {
  height: 32px;
}

.constant-table-toolbar {
  justify-content: space-between;
  min-height: 36px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
}

.constant-table {
  min-width: 720px;
}

.constant-table-row {
  display: grid;
  min-height: 46px;
  align-items: center;
  grid-template-columns: minmax(170px, 1fr) 110px minmax(150px, 1fr) minmax(220px, 1.4fr) 70px;
  gap: 8px;
  border-bottom: 1px solid var(--border);
}

.constant-table-head {
  min-height: 34px;
  color: var(--muted-foreground);
  font-size: 10px;
}

.constant-table-head > span {
  display: flex;
  align-items: center;
  align-self: stretch;
  padding: 0 8px;
  border-bottom: 1px solid var(--border);
}

.constant-table select {
  width: 100%;
  padding: 0 8px;
  border: 1px solid var(--input);
  border-radius: 4px;
  background: var(--background);
  font-size: 12px;
}

@media (max-width: 900px) {
  .constant-workspace {
    grid-template-columns: 180px minmax(0, 1fr);
  }

  .constant-group-fields {
    grid-template-columns: 1fr;
  }
}
</style>
