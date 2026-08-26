<script setup lang="ts">
import { Plus, Trash2 } from '@lucide/vue'
import { computed } from 'vue'

import IconButton from '@/components/composed/icon-button/IconButton.vue'
import { Button } from '@/components/generated/shadcn/button'
import type { LowcodeApiSchemaDraft, LowcodeApiTypeOption } from '@/types'

import { normalizeApiSchema } from '../model-studio/model-draft'

defineOptions({ name: 'ApiSchemaEditor' })

const props = withDefaults(defineProps<{
  modelValue: LowcodeApiSchemaDraft
  typeOptions?: LowcodeApiTypeOption[]
  depth?: number
}>(), {
  typeOptions: () => [],
  depth: 0,
})

const emit = defineEmits<{
  'update:modelValue': [value: LowcodeApiSchemaDraft]
}>()

type SchemaKind =
  | 'string'
  | 'long'
  | 'int'
  | 'double'
  | 'decimal'
  | 'boolean'
  | 'date'
  | 'date-time'
  | 'object'
  | 'array'
  | 'reference'
  | 'oneOf'

const kind = computed<SchemaKind>(() => schemaKind(props.modelValue))
const properties = computed(() => Object.entries(props.modelValue.properties ?? {}))
const referenceValue = computed(() => {
  const ref = props.modelValue.typeRef
  if (!ref) return ''
  return ref.modelCode ? `entity:${ref.modelCode}` : `dto:${ref.dtoCode}`
})

function update(value: Partial<LowcodeApiSchemaDraft>): void {
  emit('update:modelValue', normalizeApiSchema({ ...props.modelValue, ...value }))
}

function updateKind(value: SchemaKind): void {
  emit('update:modelValue', schemaForKind(value, props.typeOptions[0]))
}

function updateReference(value: string): void {
  const [kindName, code = ''] = value.split(':')
  update({
    type: null,
    typeRef: kindName === 'entity'
      ? { modelCode: code, dtoCode: '' }
      : { modelCode: null, dtoCode: code },
    format: null,
    properties: {},
    required: [],
    items: null,
    enumValues: [],
    oneOf: [],
  })
}

function addProperty(): void {
  const name = nextPropertyName(props.modelValue.properties ?? {})
  update({
    type: 'object',
    properties: { ...(props.modelValue.properties ?? {}), [name]: schemaForKind('string') },
  })
}

function renameProperty(index: number, name: string): void {
  const current = properties.value[index]
  if (!current || !name || properties.value.some(([candidate], candidateIndex) => candidate === name && candidateIndex !== index)) {
    return
  }
  const previousName = current[0]
  const entries = properties.value.map(([key, schema], entryIndex) =>
    entryIndex === index ? [name, schema] : [key, schema])
  update({
    properties: Object.fromEntries(entries),
    required: (props.modelValue.required ?? []).map((required) => required === previousName ? name : required),
  })
}

function updateProperty(index: number, schema: LowcodeApiSchemaDraft): void {
  const entries = properties.value.map(([name, current], entryIndex) =>
    [name, entryIndex === index ? schema : current])
  update({ properties: Object.fromEntries(entries) })
}

function deleteProperty(index: number): void {
  const deletedName = properties.value[index]?.[0]
  update({
    properties: Object.fromEntries(properties.value.filter((_, entryIndex) => entryIndex !== index)),
    required: (props.modelValue.required ?? []).filter((name) => name !== deletedName),
  })
}

function setPropertyRequired(name: string, required: boolean): void {
  const names = new Set(props.modelValue.required ?? [])
  if (required) names.add(name)
  else names.delete(name)
  update({ required: [...names] })
}

function addVariant(): void {
  update({ oneOf: [...(props.modelValue.oneOf ?? []), schemaForKind('string')] })
}

function updateVariant(index: number, schema: LowcodeApiSchemaDraft): void {
  update({ oneOf: (props.modelValue.oneOf ?? []).map((current, currentIndex) =>
    currentIndex === index ? schema : current) })
}

function deleteVariant(index: number): void {
  update({ oneOf: (props.modelValue.oneOf ?? []).filter((_, currentIndex) => currentIndex !== index) })
}

function textValue(event: Event): string {
  return (event.target as HTMLInputElement | HTMLSelectElement).value
}

function checkedValue(event: Event): boolean {
  return (event.target as HTMLInputElement).checked
}

function schemaKind(schema: LowcodeApiSchemaDraft): SchemaKind {
  if (schema.oneOf?.length) return 'oneOf'
  if (schema.typeRef) return 'reference'
  if (schema.type === 'object') return 'object'
  if (schema.type === 'array') return 'array'
  if (schema.type === 'boolean') return 'boolean'
  if (schema.type === 'integer' && schema.format === 'int64') return 'long'
  if (schema.type === 'integer') return 'int'
  if (schema.type === 'number' && schema.format === 'decimal') return 'decimal'
  if (schema.type === 'number') return 'double'
  if (schema.format === 'date') return 'date'
  if (schema.format === 'date-time') return 'date-time'
  return 'string'
}

function schemaForKind(value: SchemaKind, firstType?: LowcodeApiTypeOption): LowcodeApiSchemaDraft {
  return normalizeApiSchema(whenSchemaKind(value, firstType))
}

function whenSchemaKind(
  value: SchemaKind,
  firstType?: LowcodeApiTypeOption,
): Partial<LowcodeApiSchemaDraft> {
  switch (value) {
    case 'long': return { type: 'integer', format: 'int64' }
    case 'int': return { type: 'integer', format: 'int32' }
    case 'double': return { type: 'number', format: 'double' }
    case 'decimal': return { type: 'number', format: 'decimal' }
    case 'boolean': return { type: 'boolean' }
    case 'date': return { type: 'string', format: 'date' }
    case 'date-time': return { type: 'string', format: 'date-time' }
    case 'object': return { type: 'object', properties: {}, required: [] }
    case 'array': return { type: 'array', items: schemaForKind('string') }
    case 'reference': return {
      typeRef: firstType?.modelCode
        ? { modelCode: firstType.modelCode, dtoCode: '' }
        : { modelCode: null, dtoCode: firstType?.dtoCode ?? '' },
    }
    case 'oneOf': return { oneOf: [schemaForKind('string'), schemaForKind('long')] }
    default: return { type: 'string' }
  }
}

function nextPropertyName(current: Record<string, LowcodeApiSchemaDraft>): string {
  let index = Object.keys(current).length + 1
  while (`field${index}` in current) index += 1
  return `field${index}`
}
</script>

<template>
  <div class="api-schema-editor" :class="{ nested: depth > 0 }">
    <div class="schema-toolbar">
      <select data-schema-kind :value="kind" @change="updateKind(textValue($event) as SchemaKind)">
        <option value="string">String</option>
        <option value="long">Long</option>
        <option value="int">Int</option>
        <option value="double">Double</option>
        <option value="decimal">BigDecimal</option>
        <option value="boolean">Boolean</option>
        <option value="date">LocalDate</option>
        <option value="date-time">LocalDateTime</option>
        <option value="object">对象</option>
        <option value="array">列表</option>
        <option value="reference">实体 / DTO 引用</option>
        <option value="oneOf">联合类型</option>
      </select>
      <select
        v-if="kind === 'reference'"
        data-schema-reference
        :value="referenceValue"
        @change="updateReference(textValue($event))">
        <option value="">选择引用类型</option>
        <option
          v-for="option in typeOptions"
          :key="`${option.modelCode ?? ''}:${option.dtoCode}`"
          :value="option.modelCode ? `entity:${option.modelCode}` : `dto:${option.dtoCode}`">
          {{ option.className }} · {{ option.kind === 'ENTITY' ? '实体' : 'DTO' }}
        </option>
      </select>
      <input
        :value="modelValue.description ?? ''"
        class="schema-description"
        placeholder="类型说明"
        @input="update({ description: textValue($event) || null })">
    </div>

    <div v-if="kind === 'array'" class="schema-nested-block">
      <span>元素类型</span>
      <ApiSchemaEditor
        :depth="depth + 1"
        :model-value="modelValue.items ?? schemaForKind('string')"
        :type-options="typeOptions"
        @update:model-value="update({ items: $event })"
      />
    </div>

    <div v-if="kind === 'object'" class="schema-nested-block">
      <div class="schema-section-heading">
        <span>对象属性</span>
        <Button size="sm" type="button" variant="outline" @click="addProperty"><Plus />添加属性</Button>
      </div>
      <div v-for="([name, schema], index) in properties" :key="`${name}:${index}`" class="schema-property-row">
        <div class="schema-property-meta">
          <input :value="name" aria-label="属性名" @change="renameProperty(index, textValue($event))">
          <label><input :checked="modelValue.required.includes(name)" type="checkbox" @change="setPropertyRequired(name, checkedValue($event))">必填</label>
          <IconButton :icon="Trash2" label="删除属性" tooltip variant="danger" @click="deleteProperty(index)" />
        </div>
        <ApiSchemaEditor
          :depth="depth + 1"
          :model-value="schema"
          :type-options="typeOptions"
          @update:model-value="updateProperty(index, $event)"
        />
      </div>
      <div v-if="!properties.length" class="schema-empty">暂无对象属性</div>
    </div>

    <div v-if="kind === 'oneOf'" class="schema-nested-block">
      <div class="schema-section-heading">
        <span>候选类型</span>
        <Button size="sm" type="button" variant="outline" @click="addVariant"><Plus />添加候选</Button>
      </div>
      <div v-for="(schema, index) in modelValue.oneOf" :key="index" class="schema-variant-row">
        <span>{{ index + 1 }}</span>
        <ApiSchemaEditor
          :depth="depth + 1"
          :model-value="schema"
          :type-options="typeOptions"
          @update:model-value="updateVariant(index, $event)"
        />
        <IconButton :icon="Trash2" label="删除候选类型" tooltip variant="danger" @click="deleteVariant(index)" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.api-schema-editor {
  display: grid;
  min-width: 0;
  gap: 8px;
}

.api-schema-editor.nested {
  padding-left: 10px;
  border-left: 2px solid var(--border);
}

.schema-toolbar {
  display: grid;
  grid-template-columns: minmax(132px, 0.7fr) minmax(150px, 1fr) minmax(150px, 1fr);
  gap: 6px;
}

.schema-toolbar > :only-child,
.schema-toolbar > :nth-last-child(2):first-child {
  grid-column: auto;
}

.schema-toolbar input,
.schema-toolbar select,
.schema-property-meta > input {
  width: 100%;
  min-width: 0;
  height: 30px;
}

.schema-nested-block {
  display: grid;
  min-width: 0;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border);
}

.schema-section-heading,
.schema-property-meta,
.schema-variant-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.schema-section-heading {
  justify-content: space-between;
}

.schema-section-heading > span,
.schema-nested-block > span {
  color: var(--muted-foreground);
  font-size: 11px;
  font-weight: 600;
}

.schema-property-row {
  display: grid;
  min-width: 0;
  grid-template-columns: 150px minmax(320px, 1fr);
  gap: 8px;
}

.schema-property-meta {
  align-self: start;
}

.schema-property-meta > input {
  flex: 1;
}

.schema-property-meta > label {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  font-size: 11px;
}

.schema-variant-row {
  align-items: start;
}

.schema-variant-row > .api-schema-editor {
  flex: 1;
}

.schema-variant-row > span {
  width: 20px;
  padding-top: 7px;
  color: var(--muted-foreground);
  font-family: var(--font-mono);
  font-size: 10px;
  text-align: center;
}

.schema-empty {
  padding: 12px;
  color: var(--muted-foreground);
  font-size: 11px;
  text-align: center;
  border: 1px dashed var(--border);
}

@media (max-width: 900px) {
  .schema-toolbar,
  .schema-property-row {
    grid-template-columns: 1fr;
  }
}
</style>
