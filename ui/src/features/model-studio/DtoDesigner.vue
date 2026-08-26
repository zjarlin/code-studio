<script setup lang="ts">
import { Calculator, Plus, Trash2, Workflow } from '@lucide/vue'
import { computed } from 'vue'

import IconButton from '@/components/composed/icon-button/IconButton.vue'
import { Button } from '@/components/generated/shadcn/button'
import { Checkbox } from '@/components/generated/shadcn/checkbox'
import {
  Field,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from '@/components/generated/shadcn/field'

import type {
  LowcodeBaseModel,
  LowcodeEntityConfigDraft,
  LowcodeFieldDraft,
  LowcodeFormulaPropertyDraft,
  LowcodeInheritedPropertyDraft,
  LowcodeModelSummary,
  LowcodeRelationDraft,
  LowcodeTransientPropertyDraft,
} from '../../types'
import { toPinyinSnakeIdentifier } from '@/lib/identifier'
import {
  createFormulaProperty,
  createTransientProperty,
  queryableModelProperties,
} from './model-draft'
import {
  LOWCODE_BASE_MODEL_OPTIONS,
  toggleBaseModelSelection,
} from './base-models'

const props = defineProps<{
  modelCode?: string
  contributorId?: string | null
  entityConfig: LowcodeEntityConfigDraft
  fields: LowcodeFieldDraft[]
  models: LowcodeModelSummary[]
  relations: LowcodeRelationDraft[]
}>()

const emit = defineEmits<{
  'update:entityConfig': [value: LowcodeEntityConfigDraft]
  'adopt-base-model': [entityConfig: LowcodeEntityConfigDraft, propertyNames: string[]]
}>()

const formulaDependencyOptions = computed(() => [
  ...queryableModelProperties({
    modelCode: props.modelCode ?? '',
    entityConfig: props.entityConfig,
    fields: props.fields,
    relations: props.relations,
  }, props.models).map((property) => property.code),
].filter(Boolean))

type InheritanceMode = 'NONE' | 'ROOT' | 'SUBTYPE'

const inheritanceMode = computed<InheritanceMode>(() => {
  if (props.entityConfig.inheritanceRoot) return 'ROOT'
  if (props.entityConfig.inheritanceSubtype) return 'SUBTYPE'
  return 'NONE'
})

const inheritanceParentOptions = computed(() => props.models.filter((model) =>
  model.modelType === 'ENTITY'
  && model.status === 1
  && model.modelCode !== props.modelCode
  && model.contributorId === props.contributorId
  && Boolean(model.entityConfig?.inheritanceRoot || model.entityConfig?.inheritanceSubtype)))

function patchEntityConfig(patch: Partial<LowcodeEntityConfigDraft>): void {
  emit('update:entityConfig', { ...props.entityConfig, ...patch })
}

function updateInheritanceMode(mode: InheritanceMode): void {
  if (mode === 'ROOT') {
    patchEntityConfig({
      baseMode: 'DEFAULT',
      baseModels: props.entityConfig.baseModels.length ? props.entityConfig.baseModels : ['BASE_ENTITY'],
      inheritanceRoot: {
        strategy: 'JOINED',
        discriminatorField: props.fields[0]?.fieldCode ?? '',
        instantiability: 'ABSTRACT',
        discriminatorValue: null,
        joinedTableDissociateAction: 'DELETE',
      },
      inheritanceSubtype: null,
    })
    return
  }
  if (mode === 'SUBTYPE') {
    patchEntityConfig({
      baseMode: 'INHERITED',
      baseModels: [],
      superTypes: [],
      inheritedProperties: [],
      inheritanceRoot: null,
      inheritanceSubtype: {
        parentModelCode: inheritanceParentOptions.value[0]?.modelCode ?? '',
        discriminatorValue: '',
        instantiability: 'AUTO',
      },
    })
    return
  }
  patchEntityConfig({
    baseMode: props.entityConfig.inheritanceSubtype ? 'DEFAULT' : props.entityConfig.baseMode,
    baseModels: props.entityConfig.inheritanceSubtype ? ['BASE_ENTITY'] : props.entityConfig.baseModels,
    inheritanceRoot: null,
    inheritanceSubtype: null,
  })
}

function patchInheritanceRoot(
  patch: Partial<NonNullable<LowcodeEntityConfigDraft['inheritanceRoot']>>,
): void {
  if (!props.entityConfig.inheritanceRoot) return
  patchEntityConfig({ inheritanceRoot: { ...props.entityConfig.inheritanceRoot, ...patch } })
}

function patchInheritanceSubtype(
  patch: Partial<NonNullable<LowcodeEntityConfigDraft['inheritanceSubtype']>>,
): void {
  if (!props.entityConfig.inheritanceSubtype) return
  patchEntityConfig({ inheritanceSubtype: { ...props.entityConfig.inheritanceSubtype, ...patch } })
}

function updateSuperTypes(value: string): void {
  patchEntityConfig({ superTypes: value.split(/[\n,]/).map((item) => item.trim()).filter(Boolean) })
}

function updateBaseMode(baseMode: LowcodeEntityConfigDraft['baseMode']): void {
  patchEntityConfig(baseMode === 'DEFAULT'
    ? {
        baseMode,
        baseModels: props.entityConfig.baseModels.length > 0
          ? props.entityConfig.baseModels
          : ['BASE_ENTITY' as LowcodeBaseModel],
        superTypes: [],
        inheritedProperties: [],
      }
    : { baseMode })
}

function toggleBaseModel(model: LowcodeBaseModel, selected: boolean): void {
  const baseModels = toggleBaseModelSelection(props.entityConfig.baseModels, model, selected)
  const entityConfig = { ...props.entityConfig, baseModels }
  const propertyNames = selected
    ? LOWCODE_BASE_MODEL_OPTIONS
        .find((option) => option.value === model)
        ?.properties.map((property) => property.name) ?? []
    : []
  emit('adopt-base-model', entityConfig, propertyNames)
}

function addInheritedProperty(): void {
  patchEntityConfig({
    inheritedProperties: [
      ...props.entityConfig.inheritedProperties,
      { name: '', kotlinType: 'String', dbColumn: '', required: false, id: false, description: null },
    ],
  })
}

function patchInheritedProperty(index: number, patch: Partial<LowcodeInheritedPropertyDraft>): void {
  patchEntityConfig({
    inheritedProperties: props.entityConfig.inheritedProperties.map((property, propertyIndex) =>
      propertyIndex === index ? { ...property, ...patch } : property),
  })
}

function deleteInheritedProperty(index: number): void {
  patchEntityConfig({
    inheritedProperties: props.entityConfig.inheritedProperties.filter((_, propertyIndex) => propertyIndex !== index),
  })
}

function addFormulaProperty(): void {
  patchEntityConfig({
    formulaProperties: [
      ...props.entityConfig.formulaProperties,
      createFormulaProperty(props.entityConfig.formulaProperties.length),
    ],
  })
}

function patchFormulaProperty(index: number, patch: Partial<LowcodeFormulaPropertyDraft>): void {
  patchEntityConfig({
    formulaProperties: props.entityConfig.formulaProperties.map((property, propertyIndex) =>
      propertyIndex === index ? { ...property, ...patch } : property),
  })
}

function updateFormulaLabel(index: number, label: string): void {
  const property = props.entityConfig.formulaProperties[index]
  const previousDefault = toPinyinSnakeIdentifier(property.label)
  patchFormulaProperty(index, {
    label,
    propertyCode: !property.propertyCode || property.propertyCode === previousDefault
      ? toPinyinSnakeIdentifier(label)
      : property.propertyCode,
  })
}

function updateFormulaKind(index: number, kind: LowcodeFormulaPropertyDraft['kind']): void {
  patchFormulaProperty(index, {
    kind,
    dependencies: kind === 'SQL' ? [] : props.entityConfig.formulaProperties[index].dependencies,
  })
}

function updateFormulaDependencies(index: number, event: Event): void {
  const select = event.target as HTMLSelectElement
  patchFormulaProperty(index, {
    dependencies: [...select.selectedOptions].map((option) => option.value),
  })
}

function deleteFormulaProperty(index: number): void {
  patchEntityConfig({
    formulaProperties: props.entityConfig.formulaProperties.filter((_, propertyIndex) => propertyIndex !== index),
  })
}

function addTransientProperty(): void {
  patchEntityConfig({
    transientProperties: [
      ...props.entityConfig.transientProperties,
      createTransientProperty(props.entityConfig.transientProperties.length),
    ],
  })
}

function patchTransientProperty(index: number, patch: Partial<LowcodeTransientPropertyDraft>): void {
  patchEntityConfig({
    transientProperties: props.entityConfig.transientProperties.map((property, propertyIndex) =>
      propertyIndex === index ? { ...property, ...patch } : property),
  })
}

function updateTransientLabel(index: number, label: string): void {
  const property = props.entityConfig.transientProperties[index]
  const previousDefault = toPinyinSnakeIdentifier(property.label)
  patchTransientProperty(index, {
    label,
    propertyCode: !property.propertyCode || property.propertyCode === previousDefault
      ? toPinyinSnakeIdentifier(label)
      : property.propertyCode,
  })
}

function updateTransientKind(index: number, kind: LowcodeTransientPropertyDraft['kind']): void {
  patchTransientProperty(index, {
    kind,
    resolverValueType: kind === 'DRAFT' ? null : props.entityConfig.transientProperties[index].resolverValueType,
  })
}

function deleteTransientProperty(index: number): void {
  patchEntityConfig({
    transientProperties: props.entityConfig.transientProperties.filter((_, propertyIndex) => propertyIndex !== index),
  })
}

function textValue(event: Event): string {
  return (event.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value
}

function checkedValue(event: Event): boolean {
  return (event.target as HTMLInputElement).checked
}
</script>

<template>
  <div class="designer-section">
    <FieldSet class="base-model-settings">
      <FieldLegend>继承基模型</FieldLegend>
      <FieldGroup class="base-model-grid">
        <Field
          v-for="option in LOWCODE_BASE_MODEL_OPTIONS"
          :key="option.value"
          class="base-model-option"
          :data-disabled="entityConfig.inheritanceSubtype ? '' : undefined"
          orientation="horizontal"
        >
          <Checkbox
            :id="`base-model-${option.value.toLowerCase()}`"
            :data-base-model="option.value"
            :disabled="Boolean(entityConfig.inheritanceSubtype)"
            :model-value="entityConfig.baseModels.includes(option.value)"
            @update:model-value="toggleBaseModel(option.value, $event === true)"
          />
          <FieldLabel :for="`base-model-${option.value.toLowerCase()}`">
            <span class="base-model-option-title">
              <strong>{{ option.label }}</strong>
              <code>{{ option.typeName }}</code>
            </span>
            <small>{{ option.properties.map((property) => property.name).join(' · ') }}</small>
          </FieldLabel>
        </Field>
      </FieldGroup>
    </FieldSet>

    <details class="dto-advanced-settings">
      <summary><span>实体高级设置</span><small>自定义继承 · Formula · Transient</small></summary>
      <div class="api-block">
      <div class="api-block-heading"><strong>实体基础类型</strong></div>
      <div class="form-grid model-form-grid">
        <label class="form-field"><span>实体源码</span><select :value="entityConfig.sourceMode" @change="patchEntityConfig({ sourceMode: textValue($event) as LowcodeEntityConfigDraft['sourceMode'], sourceQualifiedName: textValue($event) === 'EXISTING' ? entityConfig.sourceQualifiedName : null })"><option value="GENERATED">平台生成</option><option value="EXISTING">复用既有实体</option></select></label>
        <label v-if="entityConfig.sourceMode === 'EXISTING'" class="form-field form-field-wide"><span>既有实体全限定名</span><input :value="entityConfig.sourceQualifiedName ?? ''" placeholder="example.domain.ExistingEntity" @input="patchEntityConfig({ sourceQualifiedName: textValue($event) || null })"></label>
        <label class="form-field"><span>基模型模式</span><select :value="entityConfig.baseMode" @change="updateBaseMode(textValue($event) as LowcodeEntityConfigDraft['baseMode'])"><option value="DEFAULT">平台基模型</option><option value="INHERITED">自定义继承</option></select></label>
        <label class="form-field"><span>Jimmer 微服务</span><input :value="entityConfig.microServiceName ?? ''" @input="patchEntityConfig({ microServiceName: textValue($event) || null })"></label>
        <label class="form-field"><span>表继承角色</span><select :value="inheritanceMode" @change="updateInheritanceMode(textValue($event) as InheritanceMode)"><option value="NONE">普通实体</option><option value="ROOT">继承根模型</option><option value="SUBTYPE">继承子类型</option></select></label>
        <template v-if="entityConfig.inheritanceRoot">
          <label class="form-field"><span>存储策略</span><select :value="entityConfig.inheritanceRoot.strategy" @change="patchInheritanceRoot({ strategy: textValue($event) as NonNullable<LowcodeEntityConfigDraft['inheritanceRoot']>['strategy'] })"><option value="JOINED">JOINED</option><option value="SINGLE_TABLE">SINGLE_TABLE</option></select></label>
          <label class="form-field"><span>判别字段</span><select :value="entityConfig.inheritanceRoot.discriminatorField" @change="patchInheritanceRoot({ discriminatorField: textValue($event) })"><option value="">请选择非空标量字段</option><option v-for="field in fields.filter((item) => item.required && !item.serialized)" :key="field.fieldCode" :value="field.fieldCode">{{ field.label || field.fieldCode }} · {{ field.fieldCode }}</option></select></label>
          <label class="form-field"><span>根类型可实例化</span><select :value="entityConfig.inheritanceRoot.instantiability" @change="patchInheritanceRoot({ instantiability: textValue($event) as NonNullable<LowcodeEntityConfigDraft['inheritanceRoot']>['instantiability'] })"><option value="ABSTRACT">抽象</option><option value="INSTANTIABLE">可实例化</option><option value="AUTO">自动</option></select></label>
          <label v-if="entityConfig.inheritanceRoot.instantiability === 'INSTANTIABLE'" class="form-field"><span>根类型判别值</span><input :value="entityConfig.inheritanceRoot.discriminatorValue ?? ''" @input="patchInheritanceRoot({ discriminatorValue: textValue($event) || null })"></label>
          <label v-if="entityConfig.inheritanceRoot.strategy === 'JOINED'" class="form-field"><span>JOINED 删除策略</span><select :value="entityConfig.inheritanceRoot.joinedTableDissociateAction" @change="patchInheritanceRoot({ joinedTableDissociateAction: textValue($event) as NonNullable<LowcodeEntityConfigDraft['inheritanceRoot']>['joinedTableDissociateAction'] })"><option value="DELETE">Jimmer 删除分支行</option><option value="LAX">数据库级联删除</option></select></label>
        </template>
        <template v-if="entityConfig.inheritanceSubtype">
          <label class="form-field"><span>父模型</span><select :value="entityConfig.inheritanceSubtype.parentModelCode" @change="patchInheritanceSubtype({ parentModelCode: textValue($event) })"><option value="">请选择父模型</option><option v-for="model in inheritanceParentOptions" :key="model.modelCode" :value="model.modelCode">{{ model.name }}<template v-if="model.className"> · {{ model.className }}</template></option></select></label>
          <label class="form-field"><span>子类型可实例化</span><select :value="entityConfig.inheritanceSubtype.instantiability" @change="patchInheritanceSubtype({ instantiability: textValue($event) as NonNullable<LowcodeEntityConfigDraft['inheritanceSubtype']>['instantiability'] })"><option value="AUTO">自动</option><option value="ABSTRACT">抽象</option><option value="INSTANTIABLE">可实例化</option></select></label>
          <label v-if="entityConfig.inheritanceSubtype.instantiability !== 'ABSTRACT'" class="form-field"><span>子类型判别值</span><input :value="entityConfig.inheritanceSubtype.discriminatorValue ?? ''" @input="patchInheritanceSubtype({ discriminatorValue: textValue($event) || null })"></label>
        </template>
        <label v-if="entityConfig.baseMode === 'INHERITED'" class="form-field form-field-wide"><span>父类型</span><textarea :value="entityConfig.superTypes.join('\n')" rows="2" @input="updateSuperTypes(textValue($event))" /></label>
      </div>
      <template v-if="entityConfig.baseMode === 'INHERITED'">
        <div class="api-block-heading"><strong>继承属性</strong><Button size="sm" variant="outline" @click="addInheritedProperty"><Plus />添加属性</Button></div>
        <div class="contract-table">
          <div v-for="(property, index) in entityConfig.inheritedProperties" :key="`inherited-${index}`" class="contract-row dto-property-row">
            <input :value="property.name" placeholder="属性" @input="patchInheritedProperty(index, { name: textValue($event) })">
            <input :value="property.kotlinType" placeholder="Kotlin 类型" @input="patchInheritedProperty(index, { kotlinType: textValue($event) })">
            <input :value="property.dbColumn" placeholder="数据库列" @input="patchInheritedProperty(index, { dbColumn: textValue($event) })">
            <label class="compact-switch"><input :checked="property.required" type="checkbox" @change="patchInheritedProperty(index, { required: checkedValue($event) })"><span>非空</span></label>
            <label class="compact-switch"><input :checked="property.id" type="checkbox" @change="patchInheritedProperty(index, { id: checkedValue($event) })"><span>主键</span></label>
            <IconButton aria-label="删除继承属性" :icon="Trash2" label="删除继承属性" tooltip variant="danger" @click="deleteInheritedProperty(index)" />
          </div>
          <div v-if="!entityConfig.inheritedProperties.length" class="inline-empty">暂无继承属性</div>
        </div>
      </template>
      </div>

      <div class="api-block formula-property-block">
      <div class="api-block-heading">
        <div><strong>计算属性</strong><span>Formula / TransientResolver</span></div>
        <div class="item-actions">
          <Button size="sm" variant="outline" @click="addFormulaProperty"><Calculator />添加公式</Button>
          <Button size="sm" variant="outline" @click="addTransientProperty"><Workflow />添加复杂计算</Button>
        </div>
      </div>
      <div class="contract-table">
        <div
          v-for="(property, index) in entityConfig.formulaProperties"
          :key="`formula-${index}`"
          class="contract-row formula-property-row"
        >
          <label class="form-field"><span>名称</span><input :value="property.label" placeholder="显示名称" @input="updateFormulaLabel(index, textValue($event))"></label>
          <label class="form-field"><span>属性名</span><input :value="property.propertyCode" placeholder="displayName" @input="patchFormulaProperty(index, { propertyCode: textValue($event) })"></label>
          <label class="form-field"><span>Kotlin 类型</span><input :value="property.kotlinType" placeholder="String" @input="patchFormulaProperty(index, { kotlinType: textValue($event) })"></label>
          <label class="form-field"><span>计算方式</span><select :value="property.kind" @change="updateFormulaKind(index, textValue($event) as LowcodeFormulaPropertyDraft['kind'])"><option value="KOTLIN">Kotlin</option><option value="SQL">SQL</option></select></label>
          <label v-if="property.kind === 'KOTLIN'" class="form-field formula-dependencies"><span>依赖属性</span><select :value="property.dependencies" multiple @change="updateFormulaDependencies(index, $event)"><option v-for="option in formulaDependencyOptions.filter((code) => code !== property.propertyCode)" :key="option" :value="option">{{ option }}</option></select></label>
          <label class="form-field formula-expression"><span>{{ property.kind === 'SQL' ? 'SQL 表达式' : 'Kotlin 表达式' }}</span><textarea :value="property.expression" :placeholder="property.kind === 'SQL' ? 'select ... where owner_id = %alias.id' : 'firstName + lastName'" rows="2" @input="patchFormulaProperty(index, { expression: textValue($event) })" /></label>
          <label class="compact-switch"><input :checked="property.nullable" type="checkbox" @change="patchFormulaProperty(index, { nullable: checkedValue($event) })"><span>可空</span></label>
          <IconButton aria-label="删除计算属性" :icon="Trash2" label="删除计算属性" tooltip variant="danger" @click="deleteFormulaProperty(index)" />
        </div>
        <div v-if="!entityConfig.formulaProperties.length" class="inline-empty">暂无 Formula 属性</div>
      </div>
      <div class="api-block-heading transient-property-heading"><strong>Transient 属性</strong></div>
      <div class="contract-table">
        <div
          v-for="(property, index) in entityConfig.transientProperties"
          :key="`transient-${index}`"
          class="contract-row transient-property-row"
        >
          <label class="form-field"><span>名称</span><input :value="property.label" placeholder="显示名称" @input="updateTransientLabel(index, textValue($event))"></label>
          <label class="form-field"><span>属性名</span><input :value="property.propertyCode" placeholder="calculatedValue" @input="patchTransientProperty(index, { propertyCode: textValue($event) })"></label>
          <label class="form-field"><span>Kotlin 属性类型</span><input :value="property.kotlinType" placeholder="String" @input="patchTransientProperty(index, { kotlinType: textValue($event) })"></label>
          <label class="form-field"><span>取值方式</span><select :value="property.kind" @change="updateTransientKind(index, textValue($event) as LowcodeTransientPropertyDraft['kind'])"><option value="RESOLVER">Resolver</option><option value="DRAFT">Draft 填充</option></select></label>
          <label class="form-field transient-resolver-value"><span>Resolver 值类型</span><input :value="property.resolverValueType ?? ''" :disabled="property.kind === 'DRAFT'" placeholder="默认同属性类型" @input="patchTransientProperty(index, { resolverValueType: textValue($event) || null })"></label>
          <label class="form-field transient-dictionary"><span>字典标识</span><input :value="property.dictionaryCode ?? ''" @input="patchTransientProperty(index, { dictionaryCode: textValue($event) || null })"></label>
          <label class="compact-switch"><input :checked="property.nullable" type="checkbox" @change="patchTransientProperty(index, { nullable: checkedValue($event) })"><span>可空</span></label>
          <IconButton aria-label="删除 Transient 属性" :icon="Trash2" label="删除 Transient 属性" tooltip variant="danger" @click="deleteTransientProperty(index)" />
        </div>
        <div v-if="!entityConfig.transientProperties.length" class="inline-empty">暂无 Transient 属性</div>
      </div>
      </div>
    </details>

  </div>
</template>

<style scoped>
.dto-advanced-settings {
  margin-bottom: 12px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 6px;
}

.dto-advanced-settings > summary {
  display: flex;
  gap: 8px;
  align-items: center;
  min-height: 36px;
  padding: 0 10px;
  color: var(--secondary-foreground);
  font-size: 10px;
  cursor: pointer;
  list-style: none;
  background: var(--muted);
}

.dto-advanced-settings > summary::-webkit-details-marker {
  display: none;
}

.dto-advanced-settings > summary span {
  font-weight: 650;
}

.dto-advanced-settings > summary small {
  color: var(--muted-foreground);
  font-family: var(--font-mono);
  font-size: 9px;
}

.dto-advanced-settings > .api-block {
  margin: 0;
  padding: 12px;
  border-top: 1px solid var(--border);
}

.base-model-settings {
  gap: 10px;
  margin-bottom: 12px;
  padding: 12px 0;
  border-block: 1px solid var(--border);
}

.base-model-settings > legend {
  margin: 0;
  font-size: 12px;
}

.base-model-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 8px;
}

.base-model-option {
  min-width: 0;
  min-height: 54px;
  gap: 9px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--background);
}

.base-model-option:has([data-state='checked']) {
  border-color: var(--primary);
  background: color-mix(in srgb, var(--primary) 7%, var(--background));
}

.base-model-option > label {
  min-width: 0;
  width: 100%;
  flex-direction: column;
  gap: 2px;
  cursor: pointer;
}

.base-model-option-title {
  display: flex;
  min-width: 0;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.base-model-option-title strong {
  color: var(--foreground);
  font-size: 12px;
}

.base-model-option-title code,
.base-model-option small {
  display: block;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  color: var(--muted-foreground);
  font-family: var(--font-mono);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.base-model-option-title code {
  max-width: 48%;
}

.base-model-option small {
  width: 100%;
}

</style>
