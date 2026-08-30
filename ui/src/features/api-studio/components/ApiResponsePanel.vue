<script setup lang="ts">
import { Download, File } from '@lucide/vue'
import { computed, h, ref } from 'vue'

import CodeBlock from '@/components/composed/code-block/CodeBlock.vue'
import DataTable from '@/components/composed/data-table/DataTable.vue'
import type { DataTableColumn } from '@/components/composed/data-table/data-table'
import { Button } from '@/components/generated/shadcn/button'

import { downloadApiResponseFile } from '../api-client'
import { responseDocumentation, responseSchema, schemaRows, type ApiSchemaRow } from '../openapi'
import type { ApiDocument, ApiOperation, ApiResponseState } from '../types'
import ApiTypeScriptSample from './ApiTypeScriptSample.vue'

const props = defineProps<{
  document: ApiDocument
  operation?: ApiOperation
  response?: ApiResponseState
  error: string
  loading: boolean
}>()

const view = ref<'body' | 'headers' | 'curl' | 'docs' | 'code'>('body')
const bodyContent = computed(() => {
  if (!props.response) {
    return ''
  }
  if (typeof props.response.body === 'string') {
    return props.response.body
  }
  return JSON.stringify(props.response.body, null, 2)
})
const headersContent = computed(() =>
  props.response ? JSON.stringify(props.response.headers, null, 2) : '',
)
const documentedResponses = computed(() => responseDocumentation(props.operation, props.document))
const responseRows = computed(() => schemaRows(responseSchema(props.operation, props.document, props.response?.status), props.document))
const responseColumns: DataTableColumn<ApiSchemaRow>[] = [
  {
    accessorKey: 'path',
    header: '字段',
    cell: ({ row }) => h('code', {
      style: { paddingInlineStart: `${row.original.depth * 16}px` },
    }, row.original.path),
  },
  {
    accessorKey: 'type',
    header: '类型',
    cell: ({ row }) => h('code', row.original.type),
  },
  {
    accessorKey: 'required',
    header: '必填',
    cell: ({ row }) => row.original.required ? '是' : '否',
  },
  {
    accessorKey: 'description',
    header: '说明',
    cell: ({ row }) => row.original.description || '暂无说明',
  },
]

function formatFileSize(size: number): string {
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}
</script>

<template>
  <section class="api-response-panel">
    <div class="api-panel-heading">
      <div>
        <span class="api-panel-kicker">Response</span>
        <h2>响应结果</h2>
      </div>
      <div v-if="response" class="api-response-meta">
        <strong :class="{ success: response.status < 400 }">{{ response.status }}</strong>
        <span>{{ response.statusText || '完成' }}</span>
        <span>{{ response.durationMs }} ms</span>
      </div>
    </div>

    <div v-if="response || operation" class="api-response-toolbar" role="tablist" aria-label="响应视图">
      <button type="button" role="tab" :aria-selected="view === 'body'" :class="{ active: view === 'body' }" @click="view = 'body'">Body</button>
      <button type="button" role="tab" :aria-selected="view === 'docs'" :class="{ active: view === 'docs' }" @click="view = 'docs'">文档</button>
      <button type="button" role="tab" :aria-selected="view === 'code'" :class="{ active: view === 'code' }" @click="view = 'code'">TypeScript</button>
      <button v-if="response" type="button" role="tab" :aria-selected="view === 'headers'" :class="{ active: view === 'headers' }" @click="view = 'headers'">Headers</button>
      <button v-if="response" type="button" role="tab" :aria-selected="view === 'curl'" :class="{ active: view === 'curl' }" @click="view = 'curl'">Curl</button>
    </div>

    <div v-if="loading" class="api-response-empty">正在等待响应...</div>
    <div v-else-if="error" class="api-response-error">{{ error }}</div>
    <div v-else-if="view === 'code' && operation" class="api-response-content api-code-content">
      <ApiTypeScriptSample :document="document" :operation="operation" />
    </div>
    <div v-else-if="view === 'docs' && operation" class="api-response-content">
      <div class="api-response-docs">
        <p v-if="operation.description" class="api-response-description">{{ operation.description }}</p>
        <div v-if="documentedResponses.length" class="api-response-status-list">
          <section v-for="item in documentedResponses" :key="item.status" class="api-response-status">
            <div class="api-response-status-heading">
              <code>{{ item.status }}</code>
              <strong>{{ item.description }}</strong>
            </div>
            <div v-if="item.contentTypes.length" class="api-response-content-types">
              <span v-for="contentType in item.contentTypes" :key="contentType">{{ contentType }}</span>
            </div>
            <table v-if="item.headers.length" class="api-response-header-table">
              <thead>
                <tr><th>响应头</th><th>类型</th><th>说明</th><th>示例</th></tr>
              </thead>
              <tbody>
                <tr v-for="header in item.headers" :key="header.name">
                  <td><code>{{ header.name }}</code></td>
                  <td><code>{{ header.type }}</code></td>
                  <td>{{ header.description }}</td>
                  <td><code v-if="header.example !== undefined">{{ String(header.example) }}</code></td>
                </tr>
              </tbody>
            </table>
          </section>
        </div>
        <div v-if="responseRows.length" class="api-schema-table-wrap">
          <DataTable class="api-schema-table" :columns="responseColumns" :data="responseRows" />
        </div>
        <div v-else-if="!documentedResponses.length" class="api-response-empty">接口未声明响应文档。</div>
      </div>
    </div>
    <div v-else-if="response?.file && view === 'body'" class="api-response-content">
      <div class="api-file-response">
        <File />
        <div class="api-file-response-copy">
          <strong>{{ response.file.fileName }}</strong>
          <span>{{ response.file.contentType }}</span>
          <span>{{ formatFileSize(response.file.size) }}</span>
        </div>
        <Button size="sm" type="button" @click="downloadApiResponseFile(response.file!)">
          <Download />
          下载文件
        </Button>
      </div>
    </div>
    <div v-else-if="response" class="api-response-content">
      <CodeBlock v-if="view === 'body'" :content="bodyContent" language="json" line-numbers />
      <CodeBlock v-else-if="view === 'headers'" :content="headersContent" language="json" line-numbers />
      <CodeBlock v-else :content="response.curl" language="shell" line-numbers />
    </div>
    <div v-else class="api-response-empty">
      <span class="api-empty-status">200</span>
      发送请求后，响应会显示在这里。
    </div>
  </section>
</template>
