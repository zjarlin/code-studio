<script setup lang="ts">
import { computed } from 'vue'
import { Plus, Trash2 } from '@lucide/vue'

import { Button } from '@/components/generated/shadcn/button'

import type {
  LowcodeAgentConfirmation,
  LowcodeAgentExposureDraft,
  LowcodeApiTypeOption,
  LowcodeCustomOperationDraft,
  LowcodeModelDraft,
  LowcodeModelSummary,
  LowcodeRouteDraft,
  LowcodeRouteOrderDraft,
  LowcodeRouteOrderDirection,
} from '../../types'
import CustomOperationEditor from '../contracts/CustomOperationEditor.vue'
import {
  createRouteExcel,
  createRouteTree,
} from './model-draft'
import { resolvedBaseModelProperties } from './base-models'

const AGENT_OPERATIONS = new Set(['GET', 'PAGE', 'SIMPLE_LIST', 'TREE', 'DELETE', 'DELETE_LIST'])
const AUTO_AGENT_OPERATIONS = new Set(['GET', 'PAGE', 'SIMPLE_LIST', 'TREE'])

const props = defineProps<{
  model: Pick<LowcodeModelDraft, 'className' | 'modelCode' | 'name' | 'contributorId' | 'fields' | 'entityConfig'>
  modelValue: LowcodeRouteDraft
  models: LowcodeModelSummary[]
}>()

const emit = defineEmits<{
  change: []
  'update:modelValue': [value: LowcodeRouteDraft]
}>()

const routeOperations = computed(() => {
  const entityType = props.model.className || props.model.modelCode || 'Entity'
  return [
    { value: 'PAGE', label: '分页', method: 'GET', input: '分页参数', output: `PageResult<${entityType}>` },
    { value: 'LIST_BY_CONDITION', label: '条件列表', method: 'POST', input: entityType, output: `List<${entityType}>` },
    { value: 'SIMPLE_LIST', label: '精简列表', method: 'GET', input: '查询参数', output: `List<${entityType}>` },
    { value: 'GET', label: '详情', method: 'GET', input: 'Long', output: entityType },
    { value: 'CREATE', label: '新增', method: 'POST', input: entityType, output: entityType },
    { value: 'UPDATE', label: '更新', method: 'PUT', input: entityType, output: 'Int' },
    { value: 'UPSERT', label: '保存或更新', method: 'PUT', input: entityType, output: entityType },
    { value: 'DELETE', label: '删除', method: 'DELETE', input: 'Long', output: 'Boolean' },
    { value: 'DELETE_LIST', label: '批量删除', method: 'DELETE', input: 'List<Long>', output: 'Int' },
  ]
})

const typeOptions = computed<LowcodeApiTypeOption[]>(() => props.models
  .filter((model) => !props.model.contributorId || model.contributorId === props.model.contributorId)
  .flatMap((model) => [
  ...(model.modelType === 'ENTITY' ? [{
    modelCode: model.modelCode,
    dtoCode: '',
    className: model.className || model.modelCode,
    kind: 'ENTITY' as const,
  }] : []),
  ]))

const orderPropertyOptions = computed(() => Array.from(new Set([
  ...resolvedBaseModelProperties(props.model.entityConfig).map((property) => property.name),
  ...props.model.fields.map((field) => field.fieldCode),
])))

function addDefaultOrder(): void {
  const selected = new Set(props.modelValue.defaultOrders.map((order) => order.propertyName))
  const propertyName = orderPropertyOptions.value.find((property) => !selected.has(property)) ?? ''
  patchRoute({
    defaultOrders: [...props.modelValue.defaultOrders, { propertyName, direction: 'ASC' }],
  })
}

function updateDefaultOrder(
  index: number,
  patch: { propertyName?: string, direction?: string },
): void {
  const normalizedPatch: Partial<LowcodeRouteOrderDraft> = {
    ...(patch.propertyName !== undefined ? { propertyName: patch.propertyName } : {}),
    ...(patch.direction ? { direction: patch.direction as LowcodeRouteOrderDirection } : {}),
  }
  patchRoute({
    defaultOrders: props.modelValue.defaultOrders.map((order, orderIndex) =>
      orderIndex === index ? { ...order, ...normalizedPatch } : order),
  })
}

function removeDefaultOrder(index: number): void {
  patchRoute({
    defaultOrders: props.modelValue.defaultOrders.filter((_, orderIndex) => orderIndex !== index),
  })
}

function patchRoute(patch: Partial<LowcodeRouteDraft>): void {
  emit('update:modelValue', { ...props.modelValue, ...patch })
  emit('change')
}

function updateRouteList(key: 'aliasPaths', value: string): void {
  patchRoute({
    [key]: value.split(/[\n,]/).map((item) => item.trim()).filter(Boolean),
  })
}

function toggleRouteOperation(operation: string, enabled: boolean): void {
  const operations = new Set(props.modelValue.enabledOperations)
  if (enabled) {
    operations.add(operation)
  } else {
    operations.delete(operation)
  }
  const agentOperations = { ...props.modelValue.agentExposure.operations }
  if (!enabled) delete agentOperations[operation]
  patchRoute({
    enabledOperations: [...operations],
    agentExposure: { operations: agentOperations },
  })
}

function toggleTree(enabled: boolean): void {
  const agentOperations = { ...props.modelValue.agentExposure.operations }
  if (!enabled) delete agentOperations.TREE
  patchRoute({
    tree: enabled ? createRouteTree() : null,
    agentExposure: { operations: agentOperations },
  })
}

function isAgentOperationAvailable(operation: string): boolean {
  if (!AGENT_OPERATIONS.has(operation)) return false
  return operation === 'TREE'
    ? Boolean(props.modelValue.tree)
    : props.modelValue.enabledOperations.includes(operation)
}

function isAgentOperationExposed(operation: string): boolean {
  return Boolean(props.modelValue.agentExposure.operations[operation])
}

function toggleAgentOperation(operation: string, exposed: boolean): void {
  const operations = { ...props.modelValue.agentExposure.operations }
  if (exposed) {
    operations[operation] = {
      confirmation: AUTO_AGENT_OPERATIONS.has(operation) ? 'AUTO' : 'REQUIRED',
    }
  } else {
    delete operations[operation]
  }
  patchRoute({ agentExposure: { operations } })
}

function updateAgentConfirmation(operation: string, confirmation: string): void {
  patchRoute({
    agentExposure: {
      operations: {
        ...props.modelValue.agentExposure.operations,
        [operation]: { confirmation: confirmation as LowcodeAgentConfirmation },
      },
    },
  })
}

function updateCustomOperations(customOperations: LowcodeCustomOperationDraft[]): void {
  const agentOperations = { ...props.modelValue.agentExposure.operations }
  if (customOperations.length === props.modelValue.customOperations.length) {
    props.modelValue.customOperations.forEach((operation, index) => {
      const next = customOperations[index]
      const exposure = agentOperations[operation.operationCode]
      if (exposure && next?.operationCode && next.operationCode !== operation.operationCode) {
        delete agentOperations[operation.operationCode]
        agentOperations[next.operationCode] = exposure
      }
    })
  }
  const customOperationByCode = new Map(
    customOperations.map((operation) => [operation.operationCode, operation]),
  )
  Object.keys(agentOperations).forEach((operationCode) => {
    if (AGENT_OPERATIONS.has(operationCode)) return
    const operation = customOperationByCode.get(operationCode)
    if (!operation || operation.implementation !== 'GENERATED' || operation.transport !== 'HTTP') {
      delete agentOperations[operationCode]
    }
  })
  patchRoute({
    customOperations,
    agentExposure: { operations: agentOperations },
  })
}

function updateAgentExposure(agentExposure: LowcodeAgentExposureDraft): void {
  patchRoute({ agentExposure })
}

function toggleExcel(enabled: boolean): void {
  patchRoute({ excel: enabled ? createRouteExcel(props.model.name) : null })
}

function textValue(event: Event): string {
  return (event.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value
}

function checkedValue(event: Event): boolean {
  return (event.target as HTMLInputElement).checked
}
</script>

<template>
  <div class="controller-designer">
    <header class="designer-section-heading">
      <div>
        <h2>Controller</h2>
        <span>{{ modelValue.enabledOperations.length + modelValue.customOperations.length }} 个方法</span>
      </div>
    </header>

    <div class="form-grid model-form-grid controller-identity-grid">
      <label class="form-field"><span>主路径 <b>*</b></span><input :value="modelValue.path" @input="patchRoute({ path: textValue($event) })"></label>
      <label class="form-field"><span>Controller 类</span><input :value="modelValue.className" disabled></label>
    </div>

    <section class="api-block controller-operation-block">
      <div class="api-block-heading"><strong>实体 CRUD</strong></div>
      <div class="controller-operation-table">
        <div class="controller-operation-row controller-operation-header">
          <span>方法</span><span>HTTP</span><span>入参</span><span>出参</span><span>启用</span><span>Agent</span><span>确认</span>
        </div>
        <div v-for="operation in routeOperations" :key="operation.value" class="controller-operation-row">
          <strong>{{ operation.label }}</strong>
          <code>{{ operation.method }}</code>
          <code>{{ operation.input }}</code>
          <code>{{ operation.output }}</code>
          <input
            :aria-label="`${operation.label}启用状态`"
            :checked="modelValue.enabledOperations.includes(operation.value)"
            type="checkbox"
            @change="toggleRouteOperation(operation.value, checkedValue($event))"
          >
          <input
            :aria-label="`${operation.label} Agent 暴露状态`"
            :checked="isAgentOperationExposed(operation.value)"
            :disabled="!isAgentOperationAvailable(operation.value)"
            type="checkbox"
            @change="toggleAgentOperation(operation.value, checkedValue($event))"
          >
          <select
            aria-label="Agent 执行确认策略"
            :disabled="!isAgentOperationExposed(operation.value)"
            :value="modelValue.agentExposure.operations[operation.value]?.confirmation ?? 'REQUIRED'"
            @change="updateAgentConfirmation(operation.value, textValue($event))">
            <option value="AUTO">自动</option><option value="REQUIRED">确认</option>
          </select>
        </div>
      </div>
    </section>

    <CustomOperationEditor
      :agent-exposure="modelValue.agentExposure"
      :base-path="modelValue.path"
      :type-options="typeOptions"
      :model-value="modelValue.customOperations"
      @update:agent-exposure="updateAgentExposure"
      @update:model-value="updateCustomOperations"
    />

    <details class="controller-advanced-settings">
      <summary>扩展配置</summary>
      <div class="controller-advanced-content">
        <label class="form-field"><span>别名路径</span><textarea :value="modelValue.aliasPaths.join('\n')" rows="2" @input="updateRouteList('aliasPaths', textValue($event))" /></label>

        <div class="api-block">
          <div class="api-block-heading">
            <strong>分页默认排序</strong>
            <Button size="sm" variant="outline" type="button" @click="addDefaultOrder"><Plus :size="14" />添加排序</Button>
          </div>
          <div v-for="(order, index) in modelValue.defaultOrders" :key="`default-order-${index}`" class="default-order-row">
            <span>{{ index + 1 }}</span>
            <select aria-label="排序字段" :value="order.propertyName" @change="updateDefaultOrder(index, { propertyName: textValue($event) })">
              <option value="" disabled>选择字段</option>
              <option v-for="property in orderPropertyOptions" :key="property" :value="property">{{ property }}</option>
            </select>
            <select aria-label="排序方向" :value="order.direction" @change="updateDefaultOrder(index, { direction: textValue($event) })">
              <option value="ASC">升序</option>
              <option value="DESC">降序</option>
            </select>
            <Button :aria-label="`删除第 ${index + 1} 项排序`" title="删除排序" size="icon" variant="ghost" type="button" @click="removeDefaultOrder(index)"><Trash2 :size="14" /></Button>
          </div>
          <div v-if="!modelValue.defaultOrders.length" class="inline-empty">未配置时按 id 降序</div>
        </div>

        <div class="api-block">
          <div class="api-block-heading">
            <strong>树结构</strong>
            <div class="switch-row">
              <label class="compact-switch"><input :checked="Boolean(modelValue.tree)" type="checkbox" @change="toggleTree(checkedValue($event))"><span>启用</span></label>
              <label class="compact-switch"><input :checked="isAgentOperationExposed('TREE')" :disabled="!isAgentOperationAvailable('TREE')" type="checkbox" @change="toggleAgentOperation('TREE', checkedValue($event))"><span>Agent</span></label>
              <select v-if="isAgentOperationExposed('TREE')" aria-label="树查询 Agent 执行确认策略" :value="modelValue.agentExposure.operations.TREE?.confirmation" @change="updateAgentConfirmation('TREE', textValue($event))"><option value="AUTO">自动执行</option><option value="REQUIRED">执行前确认</option></select>
            </div>
          </div>
          <div v-if="modelValue.tree" class="form-grid model-form-grid">
            <label class="form-field"><span>父节点字段</span><input :value="modelValue.tree.parentIdProperty" @input="patchRoute({ tree: { ...modelValue.tree!, parentIdProperty: textValue($event) } })"></label>
            <label class="form-field"><span>子节点字段</span><input :value="modelValue.tree.childrenProperty" @input="patchRoute({ tree: { ...modelValue.tree!, childrenProperty: textValue($event) } })"></label>
            <label class="form-field"><span>关键词字段</span><input :value="modelValue.tree.keywordProperty" @input="patchRoute({ tree: { ...modelValue.tree!, keywordProperty: textValue($event) } })"></label>
            <label class="form-field"><span>排序字段</span><input :value="modelValue.tree.sortProperty ?? ''" @input="patchRoute({ tree: { ...modelValue.tree!, sortProperty: textValue($event) || null } })"></label>
          </div>
        </div>

        <div class="api-block">
          <div class="api-block-heading">
            <strong>Excel</strong>
            <label class="compact-switch"><input :checked="Boolean(modelValue.excel)" type="checkbox" @change="toggleExcel(checkedValue($event))"><span>启用</span></label>
          </div>
          <template v-if="modelValue.excel">
            <div class="operation-grid">
              <label class="compact-switch"><input :checked="modelValue.excel.importEnabled" type="checkbox" @change="patchRoute({ excel: { ...modelValue.excel!, importEnabled: checkedValue($event) } })"><span>导入</span></label>
              <label class="compact-switch"><input :checked="modelValue.excel.exportEnabled" type="checkbox" @change="patchRoute({ excel: { ...modelValue.excel!, exportEnabled: checkedValue($event) } })"><span>导出</span></label>
              <label class="compact-switch"><input :checked="modelValue.excel.customImport" type="checkbox" @change="patchRoute({ excel: { ...modelValue.excel!, customImport: checkedValue($event) } })"><span>自定义导入</span></label>
              <label class="compact-switch"><input :checked="modelValue.excel.customExport" type="checkbox" @change="patchRoute({ excel: { ...modelValue.excel!, customExport: checkedValue($event) } })"><span>自定义导出</span></label>
            </div>
            <div class="form-grid model-form-grid">
              <label class="form-field"><span>导出文件名</span><input :value="modelValue.excel.fileName" @input="patchRoute({ excel: { ...modelValue.excel!, fileName: textValue($event) } })"></label>
              <label class="form-field"><span>模板文件名</span><input :value="modelValue.excel.templateFileName" @input="patchRoute({ excel: { ...modelValue.excel!, templateFileName: textValue($event) } })"></label>
              <label class="form-field"><span>数据 Sheet</span><input :value="modelValue.excel.sheetName" @input="patchRoute({ excel: { ...modelValue.excel!, sheetName: textValue($event) } })"></label>
              <label class="form-field"><span>模板 Sheet</span><input :value="modelValue.excel.templateSheetName" @input="patchRoute({ excel: { ...modelValue.excel!, templateSheetName: textValue($event) } })"></label>
            </div>
          </template>
        </div>
      </div>
    </details>
  </div>
</template>

<style scoped>
.controller-designer {
  display: grid;
  width: min(100%, 1160px);
  gap: 14px;
  margin: 0 auto;
}

.controller-identity-grid {
  max-width: none;
}

.controller-operation-table {
  display: grid;
  overflow-x: auto;
}

.controller-operation-row {
  display: grid;
  grid-template-columns: minmax(110px, 1fr) 72px minmax(150px, 1fr) minmax(190px, 1.25fr) 54px 54px 86px;
  gap: 8px;
  align-items: center;
  min-width: 870px;
  min-height: 38px;
  padding: 4px 8px;
  border-top: 1px solid var(--border);
}

.controller-operation-header {
  min-height: 30px;
  color: var(--muted-foreground);
  font-size: 9px;
  font-weight: 650;
  background: var(--muted);
  border-top: 0;
}

.controller-operation-row strong {
  font-size: 11px;
}

.controller-operation-row code {
  overflow: hidden;
  font-family: var(--font-mono);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.controller-operation-row > input[type='checkbox'] {
  width: 15px;
  height: 15px;
  margin: 0 auto;
}

.controller-operation-row > select {
  min-width: 0;
  height: 28px;
  font-size: 10px;
}

.controller-advanced-settings {
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 6px;
}

.controller-advanced-settings > summary {
  min-height: 36px;
  padding: 9px 10px;
  color: var(--secondary-foreground);
  font-size: 10px;
  font-weight: 650;
  cursor: pointer;
  background: var(--muted);
}

.controller-advanced-content {
  display: grid;
  gap: 12px;
  padding: 12px;
}

.controller-advanced-content > .api-block {
  margin: 0;
}

.default-order-row {
  display: grid;
  grid-template-columns: 28px minmax(160px, 1fr) 100px auto;
  gap: 8px;
  align-items: center;
  padding: 6px 8px;
  border-top: 1px solid var(--border);
}
</style>
