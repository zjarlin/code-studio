import { describe, expect, it } from 'vitest'

import {
  collectApiOperations,
  fileReferenceFields,
  groupApiOperations,
  multipartFields,
  requestBodySchema,
  requestBodySample,
  responseDocumentation,
  schemaRows,
} from './openapi'
import type { ApiDocument } from './types'

describe('api studio OpenAPI projection', () => {
  const document: ApiDocument = {
    tags: [{ name: 'Users', description: '用户接口' }],
    components: {
      schemas: {
        UserInput: {
          type: 'object',
          properties: {
            name: { type: 'string', example: 'Ada', description: '用户名称。' },
            enabled: { type: 'boolean', default: true, description: '是否启用。' },
          },
          required: ['name'],
        },
      },
    },
    paths: {
      '/users': {
        post: {
          operationId: 'createUser',
          summary: '创建用户',
          tags: ['Users'],
          requestBody: {
            required: true,
            content: { 'application/json': { schema: { $ref: '#/components/schemas/UserInput' } } },
          },
          responses: { '201': { description: 'created' } },
          'x-lowcode-contract': true,
          'x-lowcode-transport': 'HTTP',
          'x-permission': 'POST:/system/user/create',
        },
      },
      '/users/{id}': {
        get: { summary: '读取用户', parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'integer' } }] },
      },
    },
  }

  it('collects operations and groups them by OpenAPI tags', () => {
    const operations = collectApiOperations(document)
    expect(operations.map((operation) => operation.id)).toEqual(['createUser', 'get:/users/{id}'])
    expect(operations[0]).toMatchObject({
      lowcodeContract: true,
      transport: 'HTTP',
      permission: 'POST:/system/user/create',
      metadataIssues: [],
    })
    expect(operations[1]).toMatchObject({
      lowcodeContract: false,
      transport: 'HTTP',
      metadataIssues: ['缺少分组', '缺少响应'],
    })
    const groups = groupApiOperations(document, operations)
    expect(groups.map((group) => group.name).sort()).toEqual(['Users', '未分组'].sort())
    expect(groups.find((group) => group.name === 'Users')).toEqual(
      expect.objectContaining({ description: '用户接口', operations: [operations[0]] }),
    )
    expect(groups.find((group) => group.name === '未分组')).toEqual(
      expect.objectContaining({ operations: [operations[1]] }),
    )
  })

  it('merges route aliases with the same method and contract into one operation', () => {
    const simpleList = {
      summary: '按条件获取用户列表',
      tags: ['Users'],
      parameters: [{ name: 'name', in: 'query' as const, schema: { type: 'string' } }],
      responses: { '200': { description: 'success' } },
    }
    const operations = collectApiOperations({
      paths: {
        '/users/simple-list': {
          get: { ...simpleList, 'x-permission': 'GET:/users/simple-list' },
        },
        '/users/list': {
          get: { ...simpleList, 'x-permission': 'GET:/users/list' },
        },
      },
    })

    expect(operations).toHaveLength(1)
    expect(operations[0]).toMatchObject({
      id: 'get:/users/simple-list',
      path: '/users/simple-list',
      permission: 'GET:/users/simple-list',
      addresses: [
        { path: '/users/simple-list', permission: 'GET:/users/simple-list' },
        { path: '/users/list', permission: 'GET:/users/list' },
      ],
    })
  })

  it('keeps operations separate when their request contracts differ', () => {
    const operations = collectApiOperations({
      paths: {
        '/users/list': {
          get: {
            summary: '获取用户列表',
            tags: ['Users'],
            parameters: [{ name: 'name', in: 'query', schema: { type: 'string' } }],
          },
        },
        '/users/list-active': {
          get: {
            summary: '获取用户列表',
            tags: ['Users'],
            parameters: [{ name: 'enabled', in: 'query', schema: { type: 'boolean' } }],
          },
        },
      },
    })

    expect(operations).toHaveLength(2)
  })

  it('creates a request body from referenced component schemas', () => {
    const operation = collectApiOperations(document)[0]
    expect(requestBodySample(operation, document)).toBe('{\n  "name": "Ada",\n  "enabled": true\n}')
    expect(schemaRows(requestBodySchema(operation), document)).toEqual([
      { path: 'name', type: 'string', required: true, description: '用户名称。', depth: 0 },
      { path: 'enabled', type: 'boolean', required: false, description: '是否启用。', depth: 0 },
    ])
  })

  it('projects multipart schemas into form fields', () => {
    const uploadDocument: ApiDocument = {
      components: {
        schemas: {
          UploadForm: {
            type: 'object',
            required: ['file'],
            properties: {
              file: { type: 'string', format: 'binary', description: '待上传文件' },
              directory: { type: 'string' },
            },
          },
        },
      },
      paths: {
        '/files': {
          post: {
            requestBody: {
              content: { 'multipart/form-data': { schema: { $ref: '#/components/schemas/UploadForm' } } },
            },
          },
        },
      },
    }
    const operation = collectApiOperations(uploadDocument)[0]

    expect(requestBodySample(operation, uploadDocument)).toBe('')
    expect(multipartFields(operation, uploadDocument)).toEqual([
      { name: 'file', required: true, schema: expect.objectContaining({ format: 'binary' }) },
      { name: 'directory', required: false, schema: expect.objectContaining({ type: 'string' }) },
    ])
  })

  it('projects only supported lowcode references into file upload fields', () => {
    const referenceDocument: ApiDocument = {
      components: {
        schemas: {
          ArticleInput: {
            type: 'object',
            required: ['coverFileId'],
            properties: {
              coverFileId: {
                type: 'integer',
                format: 'int64',
                'x-lowcode-reference': { targetModelCode: 'storedFile', propertyName: 'coverFile' },
              },
              categoryId: {
                type: 'integer',
                format: 'int64',
                'x-lowcode-reference': { targetModelCode: 'category', propertyName: 'category' },
              },
            },
          },
        },
      },
      paths: {
        '/articles': {
          post: {
            requestBody: {
              content: { 'application/json': { schema: { $ref: '#/components/schemas/ArticleInput' } } },
            },
          },
        },
      },
    }
    const operation = collectApiOperations(referenceDocument)[0]

    expect(fileReferenceFields(operation, referenceDocument)).toEqual([
      {
        name: 'coverFileId',
        required: true,
        uploadOperationId: 'uploadFileWithId',
        schema: expect.objectContaining({ format: 'int64' }),
      },
    ])
  })

  it('flattens response schema comments across refs and allOf', () => {
    const responseDocument: ApiDocument = {
      components: {
        schemas: {
          CommonResult: {
            type: 'object',
            properties: {
              code: { type: 'integer', description: '业务码。' },
              data: { type: ['object', 'null'] },
            },
            required: ['code'],
          },
          User: {
            type: 'object',
            properties: {
              nickname: { type: 'string', description: '用户昵称。' },
            },
          },
        },
      },
    }
    const rows = schemaRows(
      {
        allOf: [
          { $ref: '#/components/schemas/CommonResult' },
          {
            type: 'object',
            properties: { data: { $ref: '#/components/schemas/User' } },
          },
        ],
      },
      responseDocument,
    )

    expect(rows).toEqual([
      { path: 'code', type: 'integer', required: true, description: '业务码。', depth: 0 },
      { path: 'data', type: 'object', required: false, depth: 0 },
      { path: 'data.nickname', type: 'string', required: false, description: '用户昵称。', depth: 1 },
    ])
  })

  it('projects every response status and its headers for protocol documentation', () => {
    const operation = collectApiOperations({
      paths: {
        '/files/{id}': {
          get: {
            summary: '下载文件',
            tags: ['Files'],
            responses: {
              '200': {
                description: '完整文件',
                content: { 'application/octet-stream': { schema: { type: 'string', format: 'binary' } } },
                headers: {
                  ETag: { description: '文件版本', schema: { type: 'string' }, example: '"v1"' },
                },
              },
              '206': {
                description: '部分文件',
                headers: {
                  'Content-Range': { description: '返回区间', schema: { type: 'string' } },
                },
              },
              '416': { description: '区间越界' },
            },
          },
        },
      },
    })[0]

    expect(responseDocumentation(operation, {})).toEqual([
      expect.objectContaining({
        status: '200',
        description: '完整文件',
        contentTypes: ['application/octet-stream'],
        headers: [{ name: 'ETag', type: 'string', description: '文件版本', example: '"v1"' }],
      }),
      expect.objectContaining({
        status: '206',
        description: '部分文件',
        headers: [{ name: 'Content-Range', type: 'string', description: '返回区间' }],
      }),
      expect.objectContaining({ status: '416', description: '区间越界', headers: [] }),
    ])
  })
})
