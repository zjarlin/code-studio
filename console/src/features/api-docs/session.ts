import {
  executeApiRequest,
  isBusinessOperation,
  requestBodySample,
  requestContentType,
  requestFormFields,
  schemaSample,
} from '@platform/openapi-workbench'
import type {
  ApiDocument,
  ApiHistoryEntry,
  ApiMultipartValue,
  ApiOperation,
  ApiParameter,
  ApiResponseState,
} from '@platform/openapi-workbench'
import { useEffect, useState } from 'react'

import { authenticatedFetch } from '@/lib/access-context'

import type { ApiCatalog } from './catalog'

export interface CustomHeader {
  id: number
  name: string
  value: string
}

export interface ApiRequestDraft {
  bodyFile?: File
  bodyText: string
  contentType?: string
  customHeaders: CustomHeader[]
  formValues: Record<string, ApiMultipartValue>
  headerValues: Record<string, string>
  pathValues: Record<string, string>
  queryValues: Record<string, string>
}

export interface ApiWorkbenchSession {
  draft: ApiRequestDraft
  error: string
  history: ApiHistoryEntry[]
  manualToken: string
  pending: boolean
  response?: ApiResponseState
  selected?: ApiOperation
  clearResponse: () => void
  reset: () => void
  select: (operation: ApiOperation, path?: string) => void
  send: () => Promise<void>
  setDraft: React.Dispatch<React.SetStateAction<ApiRequestDraft>>
  setManualToken: (value: string) => void
}

const HISTORY_STORAGE_KEY = 'api-workbench.history'
let nextHeaderId = 1

export function useApiWorkbenchSession(catalog: ApiCatalog): ApiWorkbenchSession {
  const initial = firstBusinessOperation(catalog) ?? catalog.operations[0]
  const [selected, setSelected] = useState<ApiOperation | undefined>(initial)
  const [draft, setDraft] = useState(() => createRequestDraft(initial, catalog.document))
  const [manualToken, setManualToken] = useState('')
  const [response, setResponse] = useState<ApiResponseState>()
  const [history, setHistory] = useState<ApiHistoryEntry[]>(readHistory)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const current = catalog.operations.find((operation) => operation.id === selected?.id)
    const next = current ?? firstBusinessOperation(catalog) ?? catalog.operations[0]
    setSelected(next)
    setDraft(createRequestDraft(next, catalog.document))
    setResponse(undefined)
    setError('')
  }, [catalog])

  function select(operation: ApiOperation, path = operation.path): void {
    const address = operation.addresses.find((item) => item.path === path)
    const active = address ? { ...operation, path: address.path, permission: address.permission } : operation
    setSelected(active)
    setDraft(createRequestDraft(active, catalog.document))
    setResponse(undefined)
    setError('')
  }

  function reset(): void {
    setDraft(createRequestDraft(selected, catalog.document))
    setResponse(undefined)
    setError('')
  }

  async function send(): Promise<void> {
    if (!selected || pending) return
    setPending(true)
    setError('')
    try {
      const result = await executeApiRequest({
        accessToken: manualToken,
        baseUrl: catalog.baseUrl,
        bodyFile: draft.bodyFile,
        bodyText: draft.bodyText,
        contentType: draft.contentType,
        document: catalog.document,
        headerValues: mergeHeaders(draft.headerValues, draft.customHeaders),
        multipartValues: draft.formValues,
        operation: selected,
        pathValues: draft.pathValues,
        queryValues: draft.queryValues,
      }, { fetcher: authenticatedFetch })
      setResponse(result)
      const entry: ApiHistoryEntry = {
        createdAt: new Date().toISOString(),
        durationMs: result.durationMs,
        id: selected.id,
        method: selected.method,
        path: selected.path,
        status: result.status,
      }
      const nextHistory = [entry, ...history].slice(0, 20)
      setHistory(nextHistory)
      writeHistory(nextHistory)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '请求失败')
    } finally {
      setPending(false)
    }
  }

  return {
    clearResponse: () => {
      setResponse(undefined)
      setError('')
    },
    draft,
    error,
    history,
    manualToken,
    pending,
    reset,
    response,
    select,
    selected,
    send,
    setDraft,
    setManualToken,
  }
}

export function createRequestDraft(
  operation: ApiOperation | undefined,
  document: ApiDocument,
): ApiRequestDraft {
  const contentType = requestContentType(operation)
  const values = { path: {}, query: {}, header: {} } as Record<'path' | 'query' | 'header', Record<string, string>>
  operation?.parameters.forEach((parameter) => {
    if (parameter.in === 'cookie') return
    values[parameter.in][parameter.name] = stringifyParameterValue(initialParameterValue(parameter, document))
  })
  const formValues = Object.fromEntries(
    requestFormFields(operation, document, contentType).map((field) => [field.name, initialFormValue(field.schema, document)]),
  )
  return {
    bodyText: operation ? requestBodySample(operation, document, contentType) : '',
    contentType,
    customHeaders: [],
    formValues,
    headerValues: values.header,
    pathValues: values.path,
    queryValues: values.query,
  }
}

export function newCustomHeader(): CustomHeader {
  return { id: nextHeaderId++, name: '', value: '' }
}

function firstBusinessOperation(catalog: ApiCatalog): ApiOperation | undefined {
  return catalog.operations.find((operation) => isBusinessOperation({
    baseUrl: catalog.baseUrl,
    document: catalog.document,
    operation,
  }))
}

function initialParameterValue(parameter: ApiParameter, document: ApiDocument): unknown {
  if (parameter.example !== undefined) return parameter.example
  if (parameter.schema?.example !== undefined) return parameter.schema.example
  if (parameter.schema?.default !== undefined) return parameter.schema.default
  if (parameter.schema?.enum?.[0] !== undefined) return parameter.schema.enum[0]
  if (parameter.in === 'query' && ['pageNo', 'pageSize'].includes(parameter.name)) return 1
  return parameter.in === 'path' ? schemaSample(parameter.schema, document) : ''
}

function initialFormValue(schema: ApiParameter['schema'], document: ApiDocument): ApiMultipartValue {
  if (schema?.format === 'binary' || schema?.items?.format === 'binary') return undefined
  const sample = schemaSample(schema, document)
  if (Array.isArray(sample)) return sample.map(String)
  if (typeof sample === 'boolean') return sample
  return sample === null || sample === undefined ? '' : String(sample)
}

function stringifyParameterValue(value: unknown): string {
  if (value === null || value === undefined) return ''
  return typeof value === 'string' ? value : String(value)
}

function mergeHeaders(values: Record<string, string>, custom: CustomHeader[]): Record<string, string> {
  const headers = { ...values }
  custom.forEach(({ name, value }) => {
    if (name.trim()) headers[name.trim()] = value
  })
  return headers
}

function readHistory(): ApiHistoryEntry[] {
  if (typeof window === 'undefined') return []
  try {
    const stored: unknown = JSON.parse(window.localStorage.getItem(HISTORY_STORAGE_KEY) ?? '[]')
    if (!Array.isArray(stored)) return []
    return stored.flatMap((value): ApiHistoryEntry[] => {
      if (!value || typeof value !== 'object' || Array.isArray(value)) return []
      const entry = value as Record<string, unknown>
      if (typeof entry.id !== 'string' || typeof entry.path !== 'string' || typeof entry.createdAt !== 'string') return []
      if (!isHttpMethod(entry.method)) return []
      return [{
        createdAt: entry.createdAt,
        durationMs: typeof entry.durationMs === 'number' ? entry.durationMs : undefined,
        id: entry.id,
        method: entry.method,
        path: entry.path,
        status: typeof entry.status === 'number' ? entry.status : undefined,
      }]
    }).slice(0, 20)
  } catch {
    return []
  }
}

function writeHistory(history: ApiHistoryEntry[]): void {
  if (typeof window !== 'undefined') window.localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(history))
}

function isHttpMethod(value: unknown): value is ApiHistoryEntry['method'] {
  return typeof value === 'string' && ['get', 'post', 'put', 'patch', 'delete', 'head', 'options'].includes(value)
}
