import { computed, ref } from 'vue'

import {
  collectApiOperations,
  downloadApiResponseFile,
  executeApiRequest,
  fileReferenceFields,
  groupApiOperations,
  loadStudioOpenApi,
  requestBodySample,
  resolveStudioApiBaseUrl,
  schemaSample,
} from '@platform/openapi-workbench'
import type {
  ApiDocument,
  ApiFileReferenceUploadState,
  ApiGroup,
  ApiHistoryEntry,
  ApiMultipartValue,
  ApiOperation,
  ApiParameterLocation,
  ApiResponseState,
  FileUploadOutput,
} from '@platform/openapi-workbench'

import { loadStudioConfig } from '@/studio-config'
import type { StudioConfig } from '@/studio-config'
import type { CommonResult } from '@/types'

const HISTORY_STORAGE_KEY = 'api-studio.history'

export function useApiStudio(accessToken: (contributorId?: string) => string) {
  const document = ref<ApiDocument>()
  const config = ref<StudioConfig>()
  const operations = ref<ApiOperation[]>([])
  const selectedOperation = ref<ApiOperation>()
  const query = ref('')
  const metadataFilter = ref<'all' | 'incomplete'>('all')
  const pathValues = ref<Record<string, string>>({})
  const queryValues = ref<Record<string, string>>({})
  const headerValues = ref<Record<string, string>>({})
  const bodyText = ref('')
  const multipartValues = ref<Record<string, ApiMultipartValue>>({})
  const fileReferenceUploads = ref<Record<string, ApiFileReferenceUploadState>>({})
  const response = ref<ApiResponseState>()
  const history = ref<ApiHistoryEntry[]>(readHistory())
  const loading = ref(false)
  const error = ref('')

  const baseUrl = computed(() => resolveStudioApiBaseUrl(
    config.value?.apiBaseUrl,
    window.location.origin,
    __DEFAULT_API_BASE__,
  ))
  const groups = computed<ApiGroup[]>(() => groupApiOperations(document.value ?? {}, filteredOperations.value))
  const incompleteCount = computed(() => operations.value.filter((operation) => operation.metadataIssues.length > 0).length)
  const filteredOperations = computed(() => {
    const keyword = query.value.trim().toLowerCase()
    return operations.value.filter((operation) => {
      if (metadataFilter.value === 'incomplete' && operation.metadataIssues.length === 0) {
        return false
      }
      const paths = operation.addresses.map((address) => address.path).join(' ')
      return !keyword || `${operation.method} ${paths} ${operation.summary} ${operation.tags.join(' ')}`
        .toLowerCase()
        .includes(keyword)
    })
  })

  async function load(): Promise<void> {
    loading.value = true
    error.value = ''
    try {
      config.value = await loadStudioConfig()
      await loadStudioDocument(config.value)
    } catch (cause) {
      clearDocument()
      error.value = cause instanceof Error ? cause.message : '读取 OpenAPI 失败'
    } finally {
      loading.value = false
    }
  }

  function selectOperation(operation: ApiOperation): void {
    selectedOperation.value = operation
    const nextPathValues: Record<string, string> = {}
    const nextQueryValues: Record<string, string> = {}
    const nextHeaderValues: Record<string, string> = {}
    operation.parameters.forEach((parameter) => {
      const value = initialParameterValue(parameter, document.value ?? {})
      const stringValue = stringifyValue(value)
      if (parameter.in === 'path') {
        nextPathValues[parameter.name] = stringValue
      } else if (parameter.in === 'query') {
        nextQueryValues[parameter.name] = stringValue
      } else if (parameter.in === 'header') {
        nextHeaderValues[parameter.name] = stringValue
      }
    })
    pathValues.value = nextPathValues
    queryValues.value = nextQueryValues
    headerValues.value = nextHeaderValues
    bodyText.value = requestBodySample(operation, document.value ?? {})
    multipartValues.value = {}
    fileReferenceUploads.value = {}
    response.value = undefined
    error.value = ''
  }

  function selectOperationPath(path: string): void {
    const operation = selectedOperation.value
    const address = operation?.addresses.find((candidate) => candidate.path === path)
    if (!operation || !address || operation.path === address.path) {
      return
    }
    selectedOperation.value = {
      ...operation,
      path: address.path,
      permission: address.permission,
    }
    response.value = undefined
    error.value = ''
  }

  function updateMultipartField(name: string, value: ApiMultipartValue): void {
    multipartValues.value = { ...multipartValues.value, [name]: value }
  }

  async function uploadFileReference(fieldName: string, file: File): Promise<void> {
    const operation = selectedOperation.value
    const loadedDocument = document.value ?? {}
    const field = fileReferenceFields(operation, loadedDocument).find((item) => item.name === fieldName)
    if (!field) {
      return
    }
    if (!baseUrl.value) {
      setFileReferenceUpload(fieldName, {
        loading: false,
        fileName: file.name,
        error: '当前宿主未配置 API 访问地址',
      })
      return
    }
    let nextBody: Record<string, unknown>
    try {
      nextBody = parseJsonObject(bodyText.value)
    } catch (cause) {
      setFileReferenceUpload(fieldName, { loading: false, fileName: file.name, error: errorMessage(cause) })
      return
    }
    const uploadOperation = operations.value.find((item) => item.id === field.uploadOperationId)
    if (!uploadOperation) {
      setFileReferenceUpload(fieldName, {
        loading: false,
        fileName: file.name,
        error: `OpenAPI 中缺少 ${field.uploadOperationId} 上传操作`,
      })
      return
    }

    setFileReferenceUpload(fieldName, { loading: true, fileName: file.name })
    try {
      const result = await executeApiRequest({
        baseUrl: baseUrl.value,
        document: loadedDocument,
        operation: uploadOperation,
        pathValues: {},
        queryValues: {},
        headerValues: {},
        bodyText: '',
        multipartValues: { file },
        accessToken: accessToken(config.value?.contributorId),
      })
      if (result.status >= 400) {
        throw new Error(`上传失败：${result.status} ${result.statusText}`)
      }
      const uploadOutput = readFileUploadOutput(result.body)
      nextBody[fieldName] = uploadOutput.id
      bodyText.value = JSON.stringify(nextBody, null, 2)
      setFileReferenceUpload(fieldName, { loading: false, fileName: file.name, fileId: uploadOutput.id })
    } catch (cause) {
      setFileReferenceUpload(fieldName, { loading: false, fileName: file.name, error: errorMessage(cause) })
    }
  }

  function setFileReferenceUpload(name: string, state: ApiFileReferenceUploadState): void {
    fileReferenceUploads.value = { ...fileReferenceUploads.value, [name]: state }
  }

  function updateField(location: ApiParameterLocation, name: string, value: string): void {
    const target = location === 'path' ? pathValues : location === 'query' ? queryValues : headerValues
    target.value = { ...target.value, [name]: value }
  }

  async function send(): Promise<void> {
    if (!selectedOperation.value) {
      return
    }
    if (selectedOperation.value.transport !== 'HTTP') {
      error.value = `${selectedOperation.value.transport} 契约需要对应协议客户端`
      return
    }
    if (!baseUrl.value) {
      error.value = '当前宿主未配置 API 访问地址'
      return
    }
    loading.value = true
    error.value = ''
    try {
      const result = await executeApiRequest({
        baseUrl: baseUrl.value,
        document: document.value ?? {},
        operation: selectedOperation.value,
        pathValues: pathValues.value,
        queryValues: queryValues.value,
        headerValues: headerValues.value,
        bodyText: bodyText.value,
        multipartValues: multipartValues.value,
        accessToken: accessToken(config.value?.contributorId),
      })
      response.value = result
      if (result.file && result.status < 400) {
        downloadApiResponseFile(result.file)
      }
      const entry: ApiHistoryEntry = {
        id: selectedOperation.value.id,
        method: selectedOperation.value.method,
        path: selectedOperation.value.path,
        status: result.status,
        durationMs: result.durationMs,
        createdAt: new Date().toISOString(),
      }
      history.value = [
        entry,
        ...history.value.filter((item) => item.id !== entry.id || item.path !== entry.path),
      ].slice(0, 8)
      writeHistory(history.value)
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '请求失败'
    } finally {
      loading.value = false
    }
  }

  async function loadStudioDocument(studioConfig: StudioConfig): Promise<void> {
    clearDocument()
    const loaded = await loadStudioOpenApi(studioConfig, {
      defaultApiBase: __DEFAULT_API_BASE__,
      onRetry: (message) => {
        error.value = message
      },
    })
    error.value = ''
    document.value = loaded
    operations.value = collectApiOperations(loaded)
    selectedOperation.value = undefined
    if (operations.value[0]) {
      selectOperation(operations.value[0])
    }
  }

  function clearDocument(): void {
    document.value = undefined
    operations.value = []
    selectedOperation.value = undefined
    response.value = undefined
  }

  return {
    baseUrl,
    bodyText,
    config,
    document,
    error,
    fileReferenceUploads,
    filteredOperations,
    groups,
    headerValues,
    history,
    loading,
    incompleteCount,
    load,
    operations,
    metadataFilter,
    multipartValues,
    pathValues,
    query,
    queryValues,
    response,
    selectOperation,
    selectOperationPath,
    selectedOperation,
    send,
    updateField,
    updateMultipartField,
    uploadFileReference,
  }
}

function parseJsonObject(value: string): Record<string, unknown> {
  const parsed: unknown = JSON.parse(value.trim() || '{}')
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('请求体必须是 JSON 对象，才能自动回填文件 ID')
  }
  return parsed as Record<string, unknown>
}

function readFileUploadOutput(body: unknown): FileUploadOutput {
  if (!body || typeof body !== 'object') {
    throw new Error('上传响应不符合 FileUploadOutput 契约')
  }
  const result = body as Partial<CommonResult<unknown>>
  if (result.code !== 0) {
    throw new Error(typeof result.msg === 'string' && result.msg ? result.msg : '文件上传失败')
  }
  const data = result.data
  if (!data || typeof data !== 'object') {
    throw new Error('上传响应不符合 FileUploadOutput 契约')
  }
  const output = data as Partial<FileUploadOutput>
  if (typeof output.id !== 'string' || typeof output.url !== 'string') {
    throw new Error('上传响应不符合 FileUploadOutput 契约')
  }
  return { id: output.id, url: output.url }
}

function errorMessage(cause: unknown): string {
  return cause instanceof Error ? cause.message : '文件上传失败'
}

function readHistory(): ApiHistoryEntry[] {
  try {
    const value = JSON.parse(window.localStorage.getItem(HISTORY_STORAGE_KEY) ?? '[]')
    return Array.isArray(value) ? value : []
  } catch {
    return []
  }
}

function writeHistory(value: ApiHistoryEntry[]): void {
  window.localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(value))
}

function stringifyValue(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  if (typeof value === 'string') {
    return value
  }
  return JSON.stringify(value)
}

function initialParameterValue(parameter: ApiOperation['parameters'][number], document: ApiDocument): unknown {
  const schema = parameter.schema
  if (parameter.example !== undefined || schema?.example !== undefined || schema?.default !== undefined) {
    return parameter.example ?? schema?.example ?? schema?.default
  }
  if (parameter.in === 'query') {
    if (parameter.name === 'pageNo' || parameter.name === 'pageSize') {
      return 1
    }
    return ''
  }
  return parameter.in === 'path' ? schemaSample(parameter.schema, document) : ''
}
