import { afterEach, describe, expect, it, vi } from 'vitest'

import { emptyReportDocument } from './models'
import { fetchReportSourceCatalog, isJsonPointer, resolveJsonPointer, rowsFromResult, runReportDatasets } from './runner'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('report data normalization', () => {
  it('normalizes page and list payloads into rows', () => {
    expect(rowsFromResult({ list: [{ id: 1 }] })).toEqual([{ id: 1 }])
    expect(rowsFromResult([{ id: 2 }])).toEqual([{ id: 2 }])
    expect(rowsFromResult({ total: 3 })).toEqual([{ total: 3 }])
  })

  it('resolves escaped JSON Pointer segments', () => {
    expect(resolveJsonPointer({ 'a/b': { '~value': 7 } }, '/a~1b/~0value')).toBe(7)
    expect(resolveJsonPointer({ value: 2 }, 'value')).toBeUndefined()
  })

  it('caps direct data source rows and rejects malformed pointers', () => {
    const rows = Array.from({ length: 205 }, (_, index) => ({ index }))

    expect(rowsFromResult(rows)).toHaveLength(200)
    expect(isJsonPointer('/items/0/name')).toBe(true)
    expect(isJsonPointer('/items/~2')).toBe(false)
  })

  it('rejects a missing required report parameter before data execution', async () => {
    const document = emptyReportDocument()
    document.parameters.push({ key: 'date', label: '日期', type: 'DATE', required: true, defaultValue: null, options: [] })

    await expect(runReportDatasets(document, {})).rejects.toThrow('缺少必填参数：日期')
  })

  it('resolves OpenAPI under the configured host API prefix', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({
        code: 0,
        msg: '',
        data: { apiBaseUrl: '/admin-api', openApiPath: '/v3/api-docs' },
      }))
      .mockResolvedValueOnce(Response.json({ openapi: '3.1.0', paths: {} }))
    vi.stubGlobal('fetch', fetcher)

    await fetchReportSourceCatalog()

    const openApiRequest = fetcher.mock.calls[1]?.[0]
    expect(new URL(String(openApiRequest)).pathname).toBe('/admin-api/v3/api-docs')
  })

  it('runs data requests under the configured host API prefix', async () => {
    const document = emptyReportDocument()
    document.datasets = [{
      key: 'source',
      name: '数据源',
      source: 'OPENAPI',
      modelCode: null,
      operationId: 'getSource',
      parameterBindings: {},
      fields: [],
    }]
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({
        code: 0,
        msg: '',
        data: { apiBaseUrl: '/admin-api', openApiPath: '/v3/api-docs' },
      }))
      .mockResolvedValueOnce(Response.json({
        openapi: '3.1.0',
        paths: {
          '/source': {
            get: {
              operationId: 'getSource',
              responses: { 200: { content: { 'application/json': { schema: { type: 'object' } } } } },
            },
          },
        },
      }))
      .mockResolvedValueOnce(Response.json({ code: 0, msg: '', data: { value: 7 } }))
    vi.stubGlobal('fetch', fetcher)

    await runReportDatasets(document, {})

    expect(new URL(String(fetcher.mock.calls[2]?.[0])).pathname).toBe('/admin-api/source')
  })

  it('catalogs only stable GET operations with safe inputs and recognizable JSON responses', async () => {
    const objectResponse = { responses: { 200: { content: { 'application/json': { schema: { type: 'object', properties: { value: { type: 'number' } } } } } } } }
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ code: 0, msg: '', data: { apiBaseUrl: '/', openApiPath: '/v3/api-docs' } }))
      .mockResolvedValueOnce(Response.json({
        openapi: '3.1.0',
        paths: {
          '/valid': { get: { operationId: 'getValid', ...objectResponse } },
          '/unstable': { get: { operationId: 'bad id', ...objectResponse } },
          '/body': { get: { operationId: 'getWithBody', requestBody: {}, ...objectResponse } },
          '/scalar': { get: { operationId: 'getScalar', responses: { 200: { content: { 'application/json': { schema: { type: 'string' } } } } } } },
          '/header': { get: { operationId: 'getHeader', parameters: [{ name: 'key', in: 'header' }], ...objectResponse } },
        },
      }))
    vi.stubGlobal('fetch', fetcher)

    const catalog = await fetchReportSourceCatalog()

    expect(catalog.operations.map(({ operationId }) => operationId)).toEqual(['getValid'])
  })

  it('rejects an OpenAPI operation after it drifts to a GET request body', async () => {
    const document = emptyReportDocument()
    document.datasets = [{
      key: 'source',
      name: '数据源',
      source: 'OPENAPI',
      modelCode: null,
      operationId: 'getSource',
      parameterBindings: {},
      fields: [],
    }]
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ code: 0, msg: '', data: { apiBaseUrl: '/', openApiPath: '/v3/api-docs' } }))
      .mockResolvedValueOnce(Response.json({
        openapi: '3.1.0',
        paths: {
          '/source': {
            get: {
              operationId: 'getSource',
              requestBody: {},
              responses: { 200: { content: { 'application/json': { schema: { type: 'object' } } } } },
            },
          },
        },
      }))
    vi.stubGlobal('fetch', fetcher)

    const result = await runReportDatasets(document, {})

    expect(result.errors.source).toContain('未找到唯一 GET 操作')
  })
})
