import { afterEach, describe, expect, it, vi } from 'vitest'

import { buildApiUrl, buildRequestUrl, downloadApiResponseFile, executeApiRequest } from './api-client'
import type { ApiRequestInput } from './types'

const input: ApiRequestInput = {
  baseUrl: 'http://localhost:48080',
  document: {},
  operation: {
    id: 'searchUsers',
    method: 'get',
    path: '/users/{id}',
    addresses: [{ path: '/users/{id}' }],
    summary: '读取用户',
    tags: ['Users'],
    parameters: [],
    responses: {},
    lowcodeContract: false,
    transport: 'HTTP',
    metadataIssues: [],
  },
  pathValues: { id: 'a user' },
  queryValues: { page: '2', empty: '' },
  headerValues: { Accept: 'application/json' },
  bodyText: '',
  multipartValues: {},
  accessToken: 'dev-token',
}

describe('api studio request client', () => {
  afterEach(() => vi.restoreAllMocks())

  it('builds encoded path and query parameters', () => {
    expect(buildRequestUrl(input)).toBe('http://localhost:48080/users/a%20user?page=2')
  })

  it('applies the application API prefix without duplicating documented paths', () => {
    const prefixedInput: ApiRequestInput = {
      ...input,
      baseUrl: 'http://localhost:48080/admin-api',
      operation: { ...input.operation, path: '/system/user/{id}' },
    }
    const alreadyPrefixedInput: ApiRequestInput = {
      ...prefixedInput,
      operation: { ...input.operation, path: '/admin-api/system/user/{id}' },
    }

    expect(buildRequestUrl(prefixedInput)).toBe('http://localhost:48080/admin-api/system/user/a%20user?page=2')
    expect(buildRequestUrl(alreadyPrefixedInput)).toBe('http://localhost:48080/admin-api/system/user/a%20user?page=2')
  })

  it('applies the same API prefix rules to arbitrary endpoints', () => {
    expect(buildApiUrl('http://localhost:48080/admin-api', '/catalog/items'))
      .toBe('http://localhost:48080/admin-api/catalog/items')
    expect(buildApiUrl('http://localhost:48080/admin-api', '/admin-api/catalog/items'))
      .toBe('http://localhost:48080/admin-api/catalog/items')
  })

  it('sends bearer authentication and exposes response details', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 0, data: { ok: true } }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const response = await executeApiRequest(input)

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:48080/users/a%20user?page=2',
      expect.objectContaining({
        method: 'GET',
        headers: { Accept: 'application/json', Authorization: 'Bearer dev-token' },
      }),
    )
    expect(response.status).toBe(200)
    expect(response.body).toEqual({ code: 0, data: { ok: true } })
    expect(response.curl).toContain("--header 'Authorization: Bearer dev-token'")
  })

  it('builds multipart requests without setting the content type boundary manually', async () => {
    const file = new File(['content'], 'example.txt', { type: 'text/plain' })
    const uploadInput: ApiRequestInput = {
      ...input,
      document: {},
      operation: {
        ...input.operation,
        method: 'post',
        path: '/infra/file/upload',
        requestBody: {
          required: true,
          content: {
            'multipart/form-data': {
              schema: {
                type: 'object',
                required: ['file'],
                properties: {
                  file: { type: 'string', format: 'binary' },
                  directory: { type: 'string' },
                  keepOriginalName: { type: 'boolean' },
                },
              },
            },
          },
        },
      },
      pathValues: {},
      queryValues: {},
      headerValues: {},
      multipartValues: { file, directory: 'site/media', keepOriginalName: true },
    }
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', {
      status: 200,
      headers: { 'content-type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await executeApiRequest(uploadInput)
    const request = fetchMock.mock.calls[0]?.[1] as RequestInit
    const body = request.body as FormData

    expect(request.headers).toEqual({ Authorization: 'Bearer dev-token' })
    expect(body.get('file')).toBe(file)
    expect(body.get('directory')).toBe('site/media')
    expect(body.get('keepOriginalName')).toBe('true')
    expect(response.curl).toContain("--form 'file=@example.txt'")
    expect(response.curl).toContain("--form 'directory=site/media'")
  })

  it('rejects multipart requests with missing required fields', async () => {
    const uploadInput: ApiRequestInput = {
      ...input,
      operation: {
        ...input.operation,
        method: 'post',
        requestBody: {
          content: {
            'multipart/form-data': {
              schema: { type: 'object', required: ['file'], properties: { file: { format: 'binary' } } },
            },
          },
        },
      },
    }

    await expect(executeApiRequest(uploadInput)).rejects.toThrow('file')
  })

  it('reads documented binary responses as downloadable files', async () => {
    const binaryInput: ApiRequestInput = {
      ...input,
      operation: {
        ...input.operation,
        path: '/users/import-template',
        responses: {
          '200': {
            content: {
              'application/vnd.ms-excel': { schema: { type: 'string', format: 'binary' } },
            },
          },
        },
      },
      pathValues: {},
      queryValues: {},
    }
    const bytes = new Uint8Array([0xd0, 0xcf, 0x11, 0xe0])
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(bytes, {
        status: 200,
        headers: {
          'content-disposition': "attachment; filename*=UTF-8''User%E5%AF%BC%E5%85%A5%E6%A8%A1%E6%9D%BF.xls",
          'content-type': 'application/vnd.ms-excel',
        },
      }),
    ))

    const response = await executeApiRequest(binaryInput)

    expect(response.bodyText).toBe('')
    expect(response.body).toBeNull()
    expect(response.file).toMatchObject({
      contentType: 'application/vnd.ms-excel',
      fileName: 'User导入模板.xls',
      size: bytes.byteLength,
    })
  })

  it('keeps JSON errors readable for documented binary operations', async () => {
    const binaryInput: ApiRequestInput = {
      ...input,
      operation: {
        ...input.operation,
        responses: {
          '200': {
            content: {
              'application/octet-stream': { schema: { type: 'string', format: 'binary' } },
            },
          },
        },
      },
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 401, msg: '未登录' }), {
        status: 401,
        headers: { 'content-type': 'application/json' },
      }),
    ))

    const response = await executeApiRequest(binaryInput)

    expect(response.file).toBeUndefined()
    expect(response.body).toEqual({ code: 401, msg: '未登录' })
  })

  it('downloads a response file and releases its object URL', () => {
    vi.useFakeTimers()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:download')
    const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)

    downloadApiResponseFile({
      blob: new Blob(['content']),
      contentType: 'application/octet-stream',
      fileName: 'example.bin',
      size: 7,
    })
    vi.runAllTimers()

    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(click).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:download')
    vi.useRealTimers()
  })
})
