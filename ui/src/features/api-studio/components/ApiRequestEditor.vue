<script setup lang="ts">
import { FileUp, LoaderCircle, Send } from '@lucide/vue'
import { computed } from 'vue'

import { Button } from '@/components/generated/shadcn/button'
import { Textarea } from '@/components/generated/shadcn/textarea'

import {
  fileReferenceFields,
  multipartFields,
  requestBodySchema,
  requestContentType,
  schemaRows,
} from '../openapi'
import type {
  ApiDocument,
  ApiFileReferenceUploadState,
  ApiMultipartField,
  ApiMultipartValue,
  ApiOperation,
  ApiParameter,
  ApiParameterLocation,
} from '../types'

const props = defineProps<{
  operation?: ApiOperation
  document: ApiDocument
  pathValues: Record<string, string>
  queryValues: Record<string, string>
  headerValues: Record<string, string>
  bodyText: string
  multipartValues: Record<string, ApiMultipartValue>
  fileReferenceUploads: Record<string, ApiFileReferenceUploadState>
  loading: boolean
}>()

const emit = defineEmits<{
  updateField: [location: ApiParameterLocation, name: string, value: string]
  updateBody: [value: string]
  updateMultipartField: [name: string, value: ApiMultipartValue]
  uploadFileReference: [name: string, file: File]
  send: []
}>()

const parameterSections: Array<{ location: ApiParameterLocation; label: string }> = [
  { location: 'path', label: '路径参数' },
  { location: 'query', label: '查询参数' },
  { location: 'header', label: '请求头' },
  { location: 'cookie', label: 'Cookie' },
]

const visibleParameterSections = computed(() =>
  parameterSections.filter((section) => parameters(section.location).length > 0),
)
const canSend = computed(() => props.operation?.transport === 'HTTP')
const contentType = computed(() => requestContentType(props.operation) ?? 'application/json')
const formFields = computed(() => multipartFields(props.operation, props.document))
const isMultipart = computed(() => contentType.value === 'multipart/form-data')
const referenceFields = computed(() => fileReferenceFields(props.operation, props.document))
const bodySchemaRows = computed(() => schemaRows(requestBodySchema(props.operation), props.document))

function parameters(location: ApiParameterLocation): ApiParameter[] {
  return props.operation?.parameters.filter((parameter) => parameter.in === location) ?? []
}

function valueFor(parameter: ApiParameter): string {
  if (parameter.in === 'path') {
    return props.pathValues[parameter.name] ?? ''
  }
  if (parameter.in === 'query') {
    return props.queryValues[parameter.name] ?? ''
  }
  return props.headerValues[parameter.name] ?? ''
}

function inputType(parameter: ApiParameter): string {
  return parameter.schema?.type === 'integer' || parameter.schema?.type === 'number' ? 'number' : 'text'
}

function updateField(event: Event, location: ApiParameterLocation, name: string): void {
  emit('updateField', location, name, (event.target as HTMLInputElement).value)
}

function isBinary(schema: ApiMultipartField['schema']): boolean {
  return schema.format === 'binary'
}

function isBinaryArray(schema: ApiMultipartField['schema']): boolean {
  return schema.type === 'array' && schema.items?.format === 'binary'
}

function updateFile(event: Event, field: ApiMultipartField): void {
  const files = Array.from((event.target as HTMLInputElement).files ?? [])
  emit('updateMultipartField', field.name, isBinaryArray(field.schema) ? files : files[0])
}

function updateFormValue(event: Event, field: ApiMultipartField): void {
  const input = event.target as HTMLInputElement | HTMLSelectElement
  emit('updateMultipartField', field.name, field.schema.type === 'boolean' ? (input as HTMLInputElement).checked : input.value)
}

function uploadReferenceFile(event: Event, name: string): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    emit('uploadFileReference', name, file)
  }
  input.value = ''
}

</script>

<template>
  <section class="api-request-editor">
    <div class="api-panel-heading">
      <div>
        <span class="api-panel-kicker">Request</span>
        <h2>请求参数</h2>
      </div>
      <Button
        :disabled="loading || !operation || !canSend"
        size="sm"
        @click="emit('send')">
        <Send />
        {{ loading ? '请求中' : canSend ? '发送请求' : operation?.transport }}
      </Button>
    </div>

    <div v-if="operation" class="api-request-scroll">
      <div v-for="section in visibleParameterSections" :key="section.location" class="api-field-section">
        <div class="api-field-section-heading">{{ section.label }}</div>
        <label v-for="parameter in parameters(section.location)" :key="`${parameter.in}:${parameter.name}`" class="api-field">
          <span>
            {{ parameter.name }}
            <em v-if="parameter.required">必填</em>
          </span>
          <input
            :type="inputType(parameter)"
            :value="valueFor(parameter)"
            :placeholder="parameter.description ?? ''"
            @input="updateField($event, parameter.in, parameter.name)"
          />
          <small v-if="parameter.description">{{ parameter.description }}</small>
        </label>
      </div>

      <div v-if="operation.requestBody" class="api-field-section api-body-section">
        <div class="api-field-section-heading">
          <span>请求体</span>
          <span class="api-content-type">{{ contentType }}</span>
        </div>
        <div v-if="isMultipart" class="api-multipart-fields">
          <label v-for="field in formFields" :key="field.name" class="api-field api-multipart-field">
            <span>
              {{ field.name }}
              <em v-if="field.required">必填</em>
            </span>
            <input
              v-if="isBinary(field.schema) || isBinaryArray(field.schema)"
              type="file"
              :multiple="isBinaryArray(field.schema)"
              @change="updateFile($event, field)"
            />
            <input
              v-else-if="field.schema.type === 'boolean'"
              class="api-checkbox"
              type="checkbox"
              :checked="multipartValues[field.name] === true"
              @change="updateFormValue($event, field)"
            />
            <select
              v-else-if="field.schema.enum?.length"
              :value="multipartValues[field.name] ?? ''"
              @change="updateFormValue($event, field)">
              <option value="">请选择</option>
              <option v-for="option in field.schema.enum" :key="String(option)" :value="String(option)">
                {{ option }}
              </option>
            </select>
            <Textarea
              v-else-if="field.schema.type === 'array'"
              :model-value="Array.isArray(multipartValues[field.name]) ? (multipartValues[field.name] as string[]).join('\n') : ''"
              rows="4"
              @update:model-value="emit('updateMultipartField', field.name, String($event).split('\n').map((value) => value.trim()).filter(Boolean))"
            />
            <input
              v-else
              :type="field.schema.type === 'integer' || field.schema.type === 'number' ? 'number' : 'text'"
              :value="multipartValues[field.name] ?? ''"
              @input="updateFormValue($event, field)"
            />
            <small v-if="field.schema.description">{{ field.schema.description }}</small>
          </label>
        </div>
        <template v-else>
          <div v-if="bodySchemaRows.length" class="api-request-schema" aria-label="请求体字段说明">
            <div class="api-request-schema-heading">
              <strong>字段说明</strong>
              <span>{{ bodySchemaRows.length }} 个字段</span>
            </div>
            <div class="api-request-schema-list">
              <div v-for="row in bodySchemaRows" :key="row.path" class="api-request-schema-row">
                <div class="api-request-schema-identity" :style="{ paddingInlineStart: `${row.depth * 12}px` }">
                  <code>{{ row.path }}</code>
                  <span>{{ row.type }}</span>
                  <em v-if="row.required">必填</em>
                </div>
                <p>{{ row.description || '暂无说明' }}</p>
              </div>
            </div>
          </div>
          <div v-if="referenceFields.length" class="api-file-reference-fields">
            <div v-for="field in referenceFields" :key="field.name" class="api-file-reference-field">
              <div class="api-file-reference-copy">
                <strong>{{ field.name }}<em v-if="field.required">必填</em></strong>
                <small>{{ field.schema.description ?? '上传后自动回填文件 ID' }}</small>
              </div>
              <label class="api-file-reference-picker" :class="{ disabled: fileReferenceUploads[field.name]?.loading }">
                <LoaderCircle v-if="fileReferenceUploads[field.name]?.loading" class="api-spin" :size="15" />
                <FileUp v-else :size="15" />
                <span>{{ fileReferenceUploads[field.name]?.loading ? '上传中' : '选择文件' }}</span>
                <input
                  type="file"
                  :disabled="fileReferenceUploads[field.name]?.loading"
                  @change="uploadReferenceFile($event, field.name)"
                />
              </label>
              <small v-if="fileReferenceUploads[field.name]?.error" class="api-file-reference-error">
                {{ fileReferenceUploads[field.name]?.error }}
              </small>
              <small v-else-if="fileReferenceUploads[field.name]?.fileId" class="api-file-reference-success">
                {{ fileReferenceUploads[field.name]?.fileName }} · ID {{ fileReferenceUploads[field.name]?.fileId }}
              </small>
            </div>
          </div>
          <Textarea
            :model-value="bodyText"
            rows="12"
            spellcheck="false"
            @update:model-value="emit('updateBody', String($event))"
          />
        </template>
        <small>{{ operation.requestBody.description ?? '请求体会按接口声明的内容类型发送。' }}</small>
      </div>
      <div v-if="!operation.parameters.length && !operation.requestBody" class="api-no-parameters">
        该接口不需要额外参数，可以直接发送。
      </div>
    </div>
    <div v-else class="api-empty-state">从左侧选择一个接口开始。</div>
  </section>
</template>
