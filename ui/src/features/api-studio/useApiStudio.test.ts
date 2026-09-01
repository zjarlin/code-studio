import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useApiStudio } from './useApiStudio'
import type { ApiDocument } from '@platform/openapi-workbench'

const document: ApiDocument = {
  openapi: '3.1.0',
  components: {
    schemas: {
      BannerInput: {
        type: 'object',
        properties: {
          title: { type: 'string' },
          imageFileId: {
            type: ['integer', 'null'],
            format: 'int64',
            'x-lowcode-reference': { targetModelCode: 'storedFile', propertyName: 'imageFile' },
          },
        },
      },
    },
  },
  paths: {
    '/infra/file/upload-with-id': {
      post: {
        operationId: 'uploadFileWithId',
        requestBody: {
          content: {
            'multipart/form-data': {
              schema: {
                type: 'object',
                required: ['file'],
                properties: { file: { type: 'string', format: 'binary' } },
              },
            },
          },
        },
        responses: { '200': { description: 'uploaded' } },
      },
    },
    '/site/banner/create': {
      post: {
        operationId: 'createBanner',
        requestBody: {
          content: { 'application/json': { schema: { $ref: '#/components/schemas/BannerInput' } } },
        },
        responses: { '200': { description: 'created' } },
      },
    },
  },
}

const studioConfig = {
  contributorId: 'example-app',
  displayName: 'Example Application',
  apiBaseUrl: 'http://localhost:48080/admin-api',
  openApiPath: '/v3/api-docs',
  editableContributorId: 'example-app',
  capabilities: ['metadata', 'api'],
}

function configResponse(): Response {
  return new Response(JSON.stringify(studioConfig), { status: 200 })
}

describe('useApiStudio file references', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.restoreAllMocks()
  })

  it('loads OpenAPI from the current host config', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(configResponse())
      .mockResolvedValueOnce(new Response(JSON.stringify(document), { status: 200 }))
    const studio = useApiStudio(() => 'studio-token')

    await studio.load()

    expect(studio.config.value?.contributorId).toBe('example-app')
    expect(studio.baseUrl.value).toBe('http://localhost:48080/admin-api')
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/studio/config')
    expect(fetchMock).toHaveBeenNthCalledWith(2, 'http://localhost:48080/admin-api/v3/api-docs')
  })

  it('clears the previous document when the current host loses its OpenAPI route', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(configResponse())
      .mockResolvedValueOnce(new Response(JSON.stringify(document), { status: 200 }))
      .mockResolvedValueOnce(configResponse())
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 404, msg: '请求地址不存在' }), { status: 200 }))
    const studio = useApiStudio(() => '')

    await studio.load()
    expect(studio.operations.value).not.toHaveLength(0)

    await studio.load()

    expect(studio.error.value).toBe('读取 Example Application OpenAPI 失败：请求地址不存在')
    expect(studio.document.value).toBeUndefined()
    expect(studio.operations.value).toHaveLength(0)
    expect(studio.selectedOperation.value).toBeUndefined()
  })

  it('uploads a file and writes the returned id into the JSON request body', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(configResponse())
      .mockResolvedValueOnce(new Response(JSON.stringify(document), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 0,
        msg: '',
        data: {
          id: '2091794774325555200',
          url: '/infra/file/content/2091794774325555200',
        },
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))
    const studio = useApiStudio(() => '')
    await studio.load()
    studio.selectOperation(studio.operations.value.find((operation) => operation.id === 'createBanner')!)

    await studio.uploadFileReference('imageFileId', new File(['image'], 'cover.png', { type: 'image/png' }))

    expect(JSON.parse(studio.bodyText.value)).toEqual({ title: '', imageFileId: '2091794774325555200' })
    expect(studio.fileReferenceUploads.value.imageFileId).toMatchObject({
      loading: false,
      fileName: 'cover.png',
      fileId: '2091794774325555200',
    })
    expect(fetchMock.mock.calls[2][0]).toBe('http://localhost:48080/admin-api/infra/file/upload-with-id')
    const uploadRequest = fetchMock.mock.calls[2][1]
    expect(uploadRequest?.body).toBeInstanceOf(FormData)
    expect((uploadRequest?.body as FormData).get('file')).toBeInstanceOf(File)
  })

  it('rejects numeric file ids from obsolete upload responses', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(configResponse())
      .mockResolvedValueOnce(new Response(JSON.stringify(document), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 0,
        msg: '',
        data: { id: 42, url: '/infra/file/content/42' },
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))
    const studio = useApiStudio(() => '')
    await studio.load()
    studio.selectOperation(studio.operations.value.find((operation) => operation.id === 'createBanner')!)

    await studio.uploadFileReference('imageFileId', new File(['image'], 'cover.png', { type: 'image/png' }))

    expect(studio.fileReferenceUploads.value.imageFileId).toMatchObject({
      loading: false,
      fileName: 'cover.png',
      error: '上传响应不符合 FileUploadOutput 契约',
    })
    expect(JSON.parse(studio.bodyText.value)).toEqual({ title: '', imageFileId: '' })
  })

  it('searches every alias and switches the active request address', async () => {
    const listOperation = {
      summary: '获取横幅列表',
      tags: ['Banners'],
      responses: { '200': { description: 'success' } },
    }
    const aliasDocument: ApiDocument = {
      openapi: '3.1.0',
      paths: {
        '/site/banner/simple-list': { get: listOperation },
        '/site/banner/list-all': { get: listOperation },
      },
    }
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(configResponse())
      .mockResolvedValueOnce(new Response(JSON.stringify(aliasDocument), { status: 200 }))
    const studio = useApiStudio(() => '')

    await studio.load()
    studio.query.value = 'list-all'

    expect(studio.filteredOperations.value).toHaveLength(1)
    expect(studio.selectedOperation.value?.path).toBe('/site/banner/simple-list')
    studio.selectOperationPath('/site/banner/list-all')
    expect(studio.selectedOperation.value).toMatchObject({
      path: '/site/banner/list-all',
      addresses: [
        { path: '/site/banner/simple-list' },
        { path: '/site/banner/list-all' },
      ],
    })
  })
})
