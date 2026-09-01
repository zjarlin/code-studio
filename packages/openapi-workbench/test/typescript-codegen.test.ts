import ts from 'typescript'
import { describe, expect, it } from 'vitest'

import { collectApiOperations } from '../src/openapi'
import { DEFAULT_API_CODE_PREFERENCES, generateTypeScriptRequest } from '../src/typescript-codegen'
import type { ApiCodePreferences, ApiDocument } from '../src/types'

const document: ApiDocument = {
  components: {
    schemas: {
      CommonResult: {
        type: 'object',
        required: ['code', 'data'],
        properties: {
          code: { type: 'integer', description: '业务状态码。' },
          data: { type: 'object' },
        },
      },
      UserInput: {
        type: 'object',
        required: ['name'],
        properties: {
          name: { type: 'string', description: '用户名称。' },
          enabled: { type: 'boolean' },
        },
      },
    },
  },
  paths: {
    '/users/{id}': {
      put: {
        operationId: 'updateUser',
        summary: '更新用户',
        tags: ['Users'],
        parameters: [
          { name: 'id', in: 'path', required: true, schema: { type: 'integer' } },
          { name: 'notify', in: 'query', schema: { type: 'boolean' } },
        ],
        requestBody: {
          required: true,
          content: { 'application/json': { schema: { $ref: '#/components/schemas/UserInput' } } },
        },
        responses: {
          '200': {
            content: {
              'application/json': {
                schema: {
                  allOf: [
                    { $ref: '#/components/schemas/CommonResult' },
                    { type: 'object', properties: { data: { $ref: '#/components/schemas/UserInput' } } },
                  ],
                },
              },
            },
          },
        },
      },
    },
    '/users/import': {
      post: {
        summary: '导入用户',
        tags: ['Users'],
        requestBody: {
          required: true,
          content: {
            'multipart/form-data': {
              schema: {
                type: 'object',
                required: ['file'],
                properties: {
                  file: { type: 'string', format: 'binary' },
                  overwrite: { type: 'boolean' },
                },
              },
            },
          },
        },
        responses: { '200': { content: { 'application/json': { schema: { type: 'integer' } } } } },
      },
    },
    '/users/avatar': {
      post: {
        operationId: 'delete',
        summary: '可选头像',
        tags: ['Users'],
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
        responses: {},
      },
    },
    '/news/{id}': {
      put: {
        operationId: 'site_example_news_contract_AdminNewsService_updateNews',
        summary: '更新新闻',
        tags: ['News'],
        parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'integer' } }],
        requestBody: {
          required: true,
          content: { 'application/json': { schema: { $ref: '#/components/schemas/UserInput' } } },
        },
        responses: {},
      },
    },
    '/files/{id}': {
      get: {
        operationId: 'getFileContent',
        summary: '下载文件',
        tags: ['Files'],
        parameters: [
          { name: 'id', in: 'path', required: true, schema: { type: 'integer' } },
          { name: 'Range', in: 'header', schema: { type: 'string' } },
          { name: 'If-Range', in: 'header', schema: { type: 'string' } },
        ],
        responses: {
          '200': {
            content: {
              'application/octet-stream': { schema: { type: 'string', format: 'binary' } },
            },
          },
          '206': {
            content: {
              'application/octet-stream': { schema: { type: 'string', format: 'binary' } },
            },
          },
        },
      },
    },
  },
}

describe('API Studio TypeScript request generator', () => {
  it('renders a copyable axios instance request with path, query, body and response types', () => {
    const operation = collectApiOperations(document).find((item) => item.id === 'updateUser')!
    const source = generateTypeScriptRequest(operation, document, DEFAULT_API_CODE_PREFERENCES)

    expect(source).toContain("import request from '@/config/axios'")
    expect(source).toContain('export interface UpdateUserPath')
    expect(source).toContain('export type UpdateUserBody = {')
    expect(source).toContain('export type UpdateUserResponse = {')
    expect(source).toContain('data: {\n    /** 用户名称。 */\n    name: string')
    expect(source).toContain('url: `/users/${encodeURIComponent(String(path.id))}`')
    expect(source).toContain("return request.request<UpdateUserResponse>({")
    expect(source).toContain('    params,')
    expect(source).toContain('    data,')
    expectTypeScript(source, AXIOS_DECLARATION)
  })

  it('renders multipart FormData through a configured named alova instance', () => {
    const operation = collectApiOperations(document).find((item) => item.path === '/users/import')!
    const preferences: ApiCodePreferences = {
      ...DEFAULT_API_CODE_PREFERENCES,
      client: 'alova',
      alova: {
        instanceName: 'api',
        importPath: '@/api/alova',
        importStyle: 'named',
      },
    }
    const source = generateTypeScriptRequest(operation, document, preferences)

    expect(source).toContain("import { api } from '@/api/alova'")
    expect(source).toContain('file: File')
    expect(source).toContain("formData.append('file', form.file)")
    expect(source).toContain('if (form.overwrite !== undefined) {')
    expect(source).toContain("return api.Post<PostUsersImportResponse>('/users/import', formData)")
    expect(source).not.toContain('Content-Type')
    expectTypeScript(source, ALOVA_DECLARATION)
  })

  it('keeps optional multipart bodies type-safe and avoids reserved identifiers', () => {
    const operation = collectApiOperations(document).find((item) => item.id === 'delete')!
    const preferences: ApiCodePreferences = {
      ...DEFAULT_API_CODE_PREFERENCES,
      axios: {
        instanceName: 'class',
        importPath: '@/config/axios',
        importStyle: 'default',
      },
    }
    const source = generateTypeScriptRequest(operation, document, preferences)

    expect(source).toContain('export function requestDelete(form?: RequestDeleteForm)')
    expect(source).toContain('if (form) {')
    expect(source).toContain('data: form ? formData : undefined')
    expect(source).toContain('return request.request<RequestDeleteResponse>')
    expectTypeScript(source, AXIOS_DECLARATION)
  })

  it('removes qualified operation namespaces from generated identifiers', () => {
    const operation = collectApiOperations(document).find((item) => item.path === '/news/{id}')!
    const source = generateTypeScriptRequest(operation, document, DEFAULT_API_CODE_PREFERENCES)

    expect(source).toContain('export interface UpdateNewsPath')
    expect(source).toContain('export function updateNews(path: UpdateNewsPath, data: UpdateNewsBody)')
    expect(source).not.toContain('AdminNewsService')
    expectTypeScript(source, AXIOS_DECLARATION)
  })

  it('preserves application operation ids that use underscores', () => {
    const operation = {
      ...collectApiOperations(document).find((item) => item.path === '/news/{id}')!,
      id: 'news_publish_preview_now',
    }
    const source = generateTypeScriptRequest(operation, document, DEFAULT_API_CODE_PREFERENCES)

    expect(source).toContain('export function newsPublishPreviewNow')
    expectTypeScript(source, AXIOS_DECLARATION)
  })

  it('renders binary range downloads as Blob requests with header types', () => {
    const operation = collectApiOperations(document).find((item) => item.id === 'getFileContent')!
    const source = generateTypeScriptRequest(operation, document, DEFAULT_API_CODE_PREFERENCES)

    expect(source).toContain('export interface GetFileContentHeaders')
    expect(source).toContain('Range?: string')
    expect(source).toContain("'If-Range'?: string")
    expect(source).toContain('export type GetFileContentResponse = Blob')
    expect(source).toContain("responseType: 'blob'")
    expectTypeScript(source, AXIOS_DECLARATION)
  })
})

function expectTypeScript(source: string, clientDeclaration: string): void {
  const files = new Map([
    ['/generated.ts', source],
    ['/client.d.ts', clientDeclaration],
  ])
  const options: ts.CompilerOptions = {
    lib: ['lib.es2022.d.ts', 'lib.dom.d.ts'],
    module: ts.ModuleKind.ESNext,
    moduleResolution: ts.ModuleResolutionKind.Bundler,
    noEmit: true,
    strict: true,
    target: ts.ScriptTarget.ES2022,
  }
  const host = ts.createCompilerHost(options)
  const originalFileExists = host.fileExists
  const originalGetSourceFile = host.getSourceFile
  const originalReadFile = host.readFile
  host.fileExists = (fileName) => files.has(fileName) || originalFileExists(fileName)
  host.readFile = (fileName) => files.get(fileName) ?? originalReadFile(fileName)
  host.getSourceFile = (fileName, languageVersion) => {
    const content = files.get(fileName)
    return content === undefined
      ? originalGetSourceFile(fileName, languageVersion)
      : ts.createSourceFile(fileName, content, languageVersion, true)
  }
  const program = ts.createProgram([...files.keys()], options, host)
  const diagnostics = ts.getPreEmitDiagnostics(program).map((diagnostic) =>
    ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'),
  )
  expect(diagnostics).toEqual([])
}

const AXIOS_DECLARATION = `
declare module '@/config/axios' {
  const request: { request<T>(config: unknown): Promise<T> }
  export default request
}
`

const ALOVA_DECLARATION = `
declare module '@/api/alova' {
  export const api: { Post<T>(url: string, data?: unknown, config?: unknown): Promise<T> }
}
`
