<script setup lang="ts">
import { ChevronRight, Plus, Trash2 } from '@lucide/vue'

import IconButton from '@/components/composed/icon-button/IconButton.vue'
import { Button } from '@/components/generated/shadcn/button'

import type {
  LowcodeAgentConfirmation,
  LowcodeAgentExposureDraft,
  LowcodeApiParameterDraft,
  LowcodeApiSchemaDraft,
  LowcodeApiTypeOption,
  LowcodeCustomOperationDraft,
} from '../../types'
import {
  createApiBody,
  createApiParameter,
  createCustomOperation,
  normalizeApiSchema,
} from '../model-studio/model-draft'

const props = withDefaults(defineProps<{
  basePath: string
  modelValue: LowcodeCustomOperationDraft[]
  agentExposure?: LowcodeAgentExposureDraft
  typeOptions?: LowcodeApiTypeOption[]
}>(), {
  agentExposure: () => ({ operations: {} }),
})

const emit = defineEmits<{
  'update:modelValue': [value: LowcodeCustomOperationDraft[]]
  'update:agentExposure': [value: LowcodeAgentExposureDraft]
}>()

const httpMethods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
const operationTransports = ['HTTP', 'SSE', 'WEBSOCKET']
const operationImplementations = [
  { value: 'GENERATED', label: '平台生成' },
  { value: 'EXISTING_REST', label: '关联既有 REST' },
] as const
const parameterLocations = ['PATH', 'QUERY', 'HEADER', 'COOKIE']

function updateOperations(operations: LowcodeCustomOperationDraft[]): void {
  emit('update:modelValue', operations)
}

function addOperation(): void {
  updateOperations([
    ...props.modelValue,
    createCustomOperation(props.modelValue.length, props.basePath),
  ])
}

function patchOperation(index: number, patch: Partial<LowcodeCustomOperationDraft>): void {
  updateOperations(props.modelValue.map((operation, operationIndex) =>
    operationIndex === index ? { ...operation, ...patch } : operation))
}

function deleteOperation(index: number): void {
  updateOperations(props.modelValue.filter((_, operationIndex) => operationIndex !== index))
}

function canExposeToAgent(operation: LowcodeCustomOperationDraft): boolean {
  return operation.implementation === 'GENERATED' && operation.transport === 'HTTP'
}

function isExposedToAgent(operationCode: string): boolean {
  return Boolean(props.agentExposure.operations[operationCode])
}

function toggleAgentExposure(operation: LowcodeCustomOperationDraft, exposed: boolean): void {
  const operations = { ...props.agentExposure.operations }
  if (exposed) {
    operations[operation.operationCode] = {
      confirmation: operation.method === 'GET' ? 'AUTO' : 'REQUIRED',
    }
  } else {
    delete operations[operation.operationCode]
  }
  emit('update:agentExposure', { operations })
}

function updateAgentConfirmation(operationCode: string, confirmation: string): void {
  emit('update:agentExposure', {
    operations: {
      ...props.agentExposure.operations,
      [operationCode]: { confirmation: confirmation as LowcodeAgentConfirmation },
    },
  })
}

function updateTransport(index: number, value: string): void {
  const transport = value as LowcodeCustomOperationDraft['transport']
  const operation = props.modelValue[index]
  const responseContentType = transport === 'SSE' ? 'text/event-stream' : 'application/json'
  patchOperation(index, {
    transport,
    method: transport === 'HTTP' ? operation.method : 'GET',
    requestBody: transport === 'SSE' ? null : operation.requestBody,
    responseBody: operation.responseBody
      ? { ...operation.responseBody, contentType: responseContentType }
      : null,
    responseEnvelope: transport === 'HTTP' && operation.responseEnvelope,
  })
}

function updateImplementation(index: number, value: string): void {
  const implementation = value as LowcodeCustomOperationDraft['implementation']
  patchOperation(index, {
    implementation,
    transport: implementation === 'EXISTING_REST' ? 'HTTP' : props.modelValue[index].transport,
    callContext: implementation === 'GENERATED' && props.modelValue[index].callContext,
  })
}

function updateAuthentication(index: number, authenticated: boolean): void {
  patchOperation(index, {
    authenticated,
    callContext: authenticated && props.modelValue[index].callContext,
  })
}

function addParameter(operationIndex: number): void {
  const operation = props.modelValue[operationIndex]
  patchOperation(operationIndex, { parameters: [...operation.parameters, createApiParameter()] })
}

function patchParameter(
  operationIndex: number,
  parameterIndex: number,
  patch: Partial<LowcodeApiParameterDraft>,
): void {
  const operation = props.modelValue[operationIndex]
  patchOperation(operationIndex, {
    parameters: operation.parameters.map((parameter, index) =>
      index === parameterIndex ? { ...parameter, ...patch } : parameter),
  })
}

function deleteParameter(operationIndex: number, parameterIndex: number): void {
  const operation = props.modelValue[operationIndex]
  patchOperation(operationIndex, {
    parameters: operation.parameters.filter((_, index) => index !== parameterIndex),
  })
}

function schemaText(schema: LowcodeApiSchemaDraft): string {
  return JSON.stringify(schema, null, 2)
}

type BodyKey = 'requestBody' | 'responseBody'

function referencedSchema(schema: LowcodeApiSchemaDraft): LowcodeApiSchemaDraft {
  return schema.type === 'array' && schema.items ? schema.items : schema
}

function typeRefValue(schema: LowcodeApiSchemaDraft): string {
  const typeRef = referencedSchema(schema).typeRef
  return typeRef ? `${typeRef.modelCode ?? ''}:${typeRef.dtoCode}` : ''
}

function typeOptionValue(option: LowcodeApiTypeOption): string {
  return `${option.modelCode ?? ''}:${option.dtoCode}`
}

function bodyCardinality(schema: LowcodeApiSchemaDraft): 'single' | 'list' {
  return schema.type === 'array' ? 'list' : 'single'
}

function setBodyTypeRef(
  operationIndex: number,
  body: BodyKey,
  value: string,
): void {
  const operation = props.modelValue[operationIndex]
  const currentBody = operation[body] ?? createApiBody()
  const [modelCode, dtoCode = ''] = value.split(':')
  const selectedSchema = value
    ? { ...normalizeApiSchema({}), type: null, typeRef: { modelCode: modelCode || null, dtoCode } }
    : normalizeApiSchema({ type: 'object' })
  const schema = bodyCardinality(currentBody.schema) === 'list'
    ? { ...normalizeApiSchema({ type: 'array' }), items: selectedSchema }
    : selectedSchema
  patchOperation(operationIndex, { [body]: { ...currentBody, schema } })
}

function setBodyCardinality(
  operationIndex: number,
  body: BodyKey,
  cardinality: 'single' | 'list',
): void {
  const operation = props.modelValue[operationIndex]
  const currentBody = operation[body] ?? createApiBody()
  const selectedSchema = referencedSchema(currentBody.schema)
  patchOperation(operationIndex, {
    [body]: {
      ...currentBody,
      schema: cardinality === 'list'
        ? { ...normalizeApiSchema({ type: 'array' }), items: selectedSchema }
        : selectedSchema,
    },
  })
}

function availableTypes(body: BodyKey): LowcodeApiTypeOption[] {
  return (props.typeOptions ?? []).filter((option) =>
    option.kind === 'ENTITY'
    || (body === 'requestBody' && option.kind === 'INPUT')
    || (body === 'responseBody' && (option.kind === 'OUTPUT' || option.kind === 'VIEW')))
}

function updateSchema(
  event: Event,
  operationIndex: number,
  body: 'requestBody' | 'responseBody',
): void {
  const target = event.target as HTMLTextAreaElement
  try {
    const schema = normalizeApiSchema(JSON.parse(target.value))
    target.setCustomValidity('')
    const operation = props.modelValue[operationIndex]
    const currentBody = operation[body] ?? createApiBody()
    patchOperation(operationIndex, { [body]: { ...currentBody, schema } })
  } catch {
    target.setCustomValidity('JSON Schema 格式错误')
    target.reportValidity()
  }
}

function textValue(event: Event): string {
  return (event.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value
}

function checkedValue(event: Event): boolean {
  return (event.target as HTMLInputElement).checked
}
</script>

<template>
  <div class="api-block custom-operation-block">
    <div class="api-block-heading">
      <strong>Controller 操作</strong>
      <Button size="sm" variant="outline" @click="addOperation"><Plus />添加操作</Button>
    </div>
    <div v-if="modelValue.length" class="designer-items custom-operation-list">
      <details
        v-for="(operation, operationIndex) in modelValue"
        :key="`${operation.operationCode}:${operationIndex}`"
        class="designer-item"
        open>
        <summary>
          <ChevronRight class="disclosure-icon" :size="14" />
          <span class="item-order">{{ operationIndex + 1 }}</span>
          <span class="item-summary">
            <strong>{{ operation.name || '未命名操作' }}</strong>
            <small>{{ operation.transport }} · {{ operation.method }} · {{ operation.path || '/' }}</small>
          </span>
          <span class="item-actions">
            <IconButton aria-label="删除操作" :icon="Trash2" label="删除操作" tooltip variant="danger" @click.stop.prevent="deleteOperation(operationIndex)" />
          </span>
        </summary>
        <div class="item-editor custom-operation-editor">
          <div class="form-grid model-form-grid">
            <label class="form-field form-field-wide"><span>操作名称 <b>*</b></span><input :value="operation.name" @input="patchOperation(operationIndex, { name: textValue($event) })"></label>
            <label class="form-field"><span>实现来源</span><select :value="operation.implementation" @change="updateImplementation(operationIndex, textValue($event))"><option v-for="implementation in operationImplementations" :key="implementation.value" :value="implementation.value">{{ implementation.label }}</option></select></label>
            <label class="form-field"><span>传输类型</span><select :value="operation.transport" @change="updateTransport(operationIndex, textValue($event))"><option v-for="transport in operationTransports" :key="transport" :value="transport">{{ transport }}</option></select></label>
            <label class="form-field"><span>HTTP 方法</span><select :disabled="operation.transport !== 'HTTP'" :value="operation.method" @change="patchOperation(operationIndex, { method: textValue($event) as LowcodeCustomOperationDraft['method'] })"><option v-for="method in httpMethods" :key="method" :value="method">{{ method }}</option></select></label>
            <label class="form-field form-field-wide"><span>路由路径 <b>*</b></span><input :value="operation.path" @input="patchOperation(operationIndex, { path: textValue($event) })"></label>
            <label v-if="operation.authenticated" class="form-field form-field-wide"><span>权限标识</span><input :value="operation.permission ?? ''" @input="patchOperation(operationIndex, { permission: textValue($event) || null })"></label>
            <label class="form-field form-field-wide"><span>说明</span><textarea :value="operation.description ?? ''" rows="2" @input="patchOperation(operationIndex, { description: textValue($event) || null })" /></label>
          </div>
          <div class="switch-row">
            <label class="compact-switch"><input :checked="operation.authenticated" type="checkbox" @change="updateAuthentication(operationIndex, checkedValue($event))"><span>认证</span></label>
            <label class="compact-switch"><input :checked="operation.callContext" :disabled="!operation.authenticated || operation.implementation === 'EXISTING_REST'" type="checkbox" @change="patchOperation(operationIndex, { callContext: checkedValue($event) })"><span>调用上下文</span></label>
            <label class="compact-switch"><input :checked="operation.responseEnvelope" :disabled="operation.transport !== 'HTTP'" type="checkbox" @change="patchOperation(operationIndex, { responseEnvelope: checkedValue($event) })"><span>CommonResult</span></label>
            <label class="compact-switch"><input :checked="isExposedToAgent(operation.operationCode)" :disabled="!canExposeToAgent(operation)" type="checkbox" @change="toggleAgentExposure(operation, checkedValue($event))"><span>Agent</span></label>
            <label v-if="isExposedToAgent(operation.operationCode)" class="agent-confirmation"><span>确认</span><select :value="agentExposure.operations[operation.operationCode]?.confirmation" @change="updateAgentConfirmation(operation.operationCode, textValue($event))"><option value="AUTO">自动执行</option><option value="REQUIRED">执行前确认</option></select></label>
            <label class="compact-switch"><input :checked="Boolean(operation.requestBody)" :disabled="operation.transport === 'SSE'" type="checkbox" @change="patchOperation(operationIndex, { requestBody: checkedValue($event) ? createApiBody() : null })"><span>请求体</span></label>
            <label class="compact-switch"><input :checked="Boolean(operation.responseBody)" type="checkbox" @change="patchOperation(operationIndex, { responseBody: checkedValue($event) ? createApiBody(operation.transport === 'SSE' ? 'text/event-stream' : 'application/json') : null })"><span>响应体</span></label>
          </div>

          <div class="custom-contract-section">
            <div class="api-block-heading"><strong>参数</strong><Button size="sm" variant="outline" @click="addParameter(operationIndex)"><Plus />添加参数</Button></div>
            <div class="contract-table">
              <div v-for="(parameter, parameterIndex) in operation.parameters" :key="`parameter-${parameterIndex}`" class="contract-row custom-parameter-row">
                <input :value="parameter.name" placeholder="参数名" @input="patchParameter(operationIndex, parameterIndex, { name: textValue($event) })">
                <select :value="parameter.location" @change="patchParameter(operationIndex, parameterIndex, { location: textValue($event) as LowcodeApiParameterDraft['location'], required: textValue($event) === 'PATH' || parameter.required })"><option v-for="location in parameterLocations" :key="location" :value="location">{{ location }}</option></select>
                <input :value="parameter.schema.type ?? ''" placeholder="Schema 类型" @input="patchParameter(operationIndex, parameterIndex, { schema: { ...parameter.schema, type: textValue($event) || null } })">
                <input :value="parameter.description ?? ''" placeholder="说明" @input="patchParameter(operationIndex, parameterIndex, { description: textValue($event) || null })">
                <label class="compact-switch"><input :checked="parameter.required" type="checkbox" @change="patchParameter(operationIndex, parameterIndex, { required: checkedValue($event) })"><span>必填</span></label>
                <IconButton aria-label="删除操作参数" :icon="Trash2" label="删除操作参数" tooltip variant="danger" @click="deleteParameter(operationIndex, parameterIndex)" />
              </div>
              <div v-if="!operation.parameters.length" class="inline-empty">尚未配置参数</div>
            </div>
          </div>

          <div class="custom-schema-grid">
            <div v-if="operation.requestBody" class="form-field custom-body-editor">
              <span>请求类型</span>
              <select :value="typeRefValue(operation.requestBody.schema)" @change="setBodyTypeRef(operationIndex, 'requestBody', textValue($event))"><option value="">自定义 Schema</option><option v-for="option in availableTypes('requestBody')" :key="typeOptionValue(option)" :value="typeOptionValue(option)">{{ option.className }} · {{ option.kind === 'ENTITY' ? '实体' : option.kind }}</option></select>
              <select :value="bodyCardinality(operation.requestBody.schema)" @change="setBodyCardinality(operationIndex, 'requestBody', textValue($event) as 'single' | 'list')"><option value="single">单个</option><option value="list">列表</option></select>
              <input :value="operation.requestBody.contentType" @input="patchOperation(operationIndex, { requestBody: { ...operation.requestBody!, contentType: textValue($event) } })">
              <textarea :value="operation.requestBody.description ?? ''" rows="2" placeholder="请求体说明" @input="patchOperation(operationIndex, { requestBody: { ...operation.requestBody!, description: textValue($event) || null } })" />
              <details class="custom-schema-details"><summary>高级 Schema</summary><textarea class="schema-editor" :value="schemaText(operation.requestBody.schema)" rows="8" spellcheck="false" @change="updateSchema($event, operationIndex, 'requestBody')" /></details>
            </div>
            <div v-if="operation.responseBody" class="form-field custom-body-editor">
              <span>响应类型</span>
              <select :value="typeRefValue(operation.responseBody.schema)" @change="setBodyTypeRef(operationIndex, 'responseBody', textValue($event))"><option value="">自定义 Schema</option><option v-for="option in availableTypes('responseBody')" :key="typeOptionValue(option)" :value="typeOptionValue(option)">{{ option.className }} · {{ option.kind === 'ENTITY' ? '实体' : option.kind }}</option></select>
              <select :value="bodyCardinality(operation.responseBody.schema)" @change="setBodyCardinality(operationIndex, 'responseBody', textValue($event) as 'single' | 'list')"><option value="single">单个</option><option value="list">列表</option></select>
              <input :value="operation.responseBody.contentType" @input="patchOperation(operationIndex, { responseBody: { ...operation.responseBody!, contentType: textValue($event) } })">
              <textarea :value="operation.responseBody.description ?? ''" rows="2" placeholder="响应体说明" @input="patchOperation(operationIndex, { responseBody: { ...operation.responseBody!, description: textValue($event) || null } })" />
              <details class="custom-schema-details"><summary>高级 Schema</summary><textarea class="schema-editor" :value="schemaText(operation.responseBody.schema)" rows="8" spellcheck="false" @change="updateSchema($event, operationIndex, 'responseBody')" /></details>
            </div>
          </div>
        </div>
      </details>
    </div>
    <div v-else class="inline-empty">暂无操作</div>
  </div>
</template>
