<script setup lang="ts">
import { computed, reactive, watch } from 'vue'

import CodeBlock from '@/components/composed/code-block/CodeBlock.vue'
import { Field, FieldGroup, FieldLabel } from '@/components/generated/shadcn/field'
import { Input } from '@/components/generated/shadcn/input'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@/components/generated/shadcn/select'

import { DEFAULT_API_CODE_PREFERENCES, generateTypeScriptRequest } from '../typescript-codegen'
import type {
  ApiCodeClient,
  ApiCodeImportStyle,
  ApiCodePreferences,
  ApiDocument,
  ApiOperation,
} from '../types'

const STORAGE_KEY = 'api-studio.typescript-code-preferences'

const props = defineProps<{
  document: ApiDocument
  operation: ApiOperation
}>()

const preferences = reactive<ApiCodePreferences>(readPreferences())
const reference = computed(() => preferences[preferences.client])
const source = computed(() => generateTypeScriptRequest(props.operation, props.document, preferences))

watch(preferences, (value) => {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
}, { deep: true })

function setClient(value: unknown): void {
  if (value === 'axios' || value === 'alova') {
    preferences.client = value
  }
}

function setImportStyle(value: unknown): void {
  if (value === 'default' || value === 'named' || value === 'none') {
    reference.value.importStyle = value
  }
}

function readPreferences(): ApiCodePreferences {
  try {
    const saved = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? '{}') as Partial<ApiCodePreferences>
    return {
      client: validClient(saved.client) ? saved.client : DEFAULT_API_CODE_PREFERENCES.client,
      axios: normalizeReference(saved.axios, 'axios'),
      alova: normalizeReference(saved.alova, 'alova'),
    }
  } catch {
    return structuredClone(DEFAULT_API_CODE_PREFERENCES)
  }
}

function normalizeReference(
  value: Partial<ApiCodePreferences['axios']> | undefined,
  client: ApiCodeClient,
): ApiCodePreferences['axios'] {
  const fallback = DEFAULT_API_CODE_PREFERENCES[client]
  return {
    instanceName: typeof value?.instanceName === 'string' ? value.instanceName : fallback.instanceName,
    importPath: typeof value?.importPath === 'string' ? value.importPath : fallback.importPath,
    importStyle: validImportStyle(value?.importStyle) ? value.importStyle : fallback.importStyle,
  }
}

function validClient(value: unknown): value is ApiCodeClient {
  return value === 'axios' || value === 'alova'
}

function validImportStyle(value: unknown): value is ApiCodeImportStyle {
  return value === 'default' || value === 'named' || value === 'none'
}
</script>

<template>
  <div class="api-code-sample">
    <FieldGroup class="api-code-settings">
      <Field>
        <FieldLabel>请求库</FieldLabel>
        <Select :model-value="preferences.client" @update:model-value="setClient">
          <SelectTrigger aria-label="请求库"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectLabel>请求库</SelectLabel>
              <SelectItem value="axios">Axios</SelectItem>
              <SelectItem value="alova">Alova</SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>
      </Field>
      <Field>
        <FieldLabel>实例变量</FieldLabel>
        <Input v-model="reference.instanceName" aria-label="请求实例变量" autocomplete="off" />
      </Field>
      <Field>
        <FieldLabel>导入路径</FieldLabel>
        <Input
          v-model="reference.importPath"
          aria-label="请求实例导入路径"
          autocomplete="off"
          :disabled="reference.importStyle === 'none'"
        />
      </Field>
      <Field>
        <FieldLabel>导入方式</FieldLabel>
        <Select :model-value="reference.importStyle" @update:model-value="setImportStyle">
          <SelectTrigger aria-label="请求实例导入方式"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectLabel>导入方式</SelectLabel>
              <SelectItem value="default">默认导入</SelectItem>
              <SelectItem value="named">命名导入</SelectItem>
              <SelectItem value="none">已有全局实例</SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>
      </Field>
    </FieldGroup>
    <CodeBlock :content="source" language="typescript" line-numbers />
  </div>
</template>
