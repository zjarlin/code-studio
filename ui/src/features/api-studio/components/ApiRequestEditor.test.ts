import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ApiRequestEditor from './ApiRequestEditor.vue'
import type { ApiOperation } from '../types'

const operation: ApiOperation = {
  id: 'uploadFile',
  method: 'post',
  path: '/files',
  addresses: [{ path: '/files' }],
  summary: '上传文件',
  tags: ['Files'],
  parameters: [],
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
  responses: {},
  lowcodeContract: false,
  transport: 'HTTP',
  metadataIssues: [],
}

describe('ApiRequestEditor', () => {
  it('renders multipart controls from the OpenAPI schema', () => {
    const wrapper = mount(ApiRequestEditor, {
      props: {
        operation,
        document: {},
        pathValues: {},
        queryValues: {},
        headerValues: {},
        bodyText: '',
        multipartValues: {},
        fileReferenceUploads: {},
        loading: false,
      },
    })

    expect(wrapper.find('input[type="file"]').exists()).toBe(true)
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(true)
    expect(wrapper.find('textarea').exists()).toBe(false)
  })

  it('renders an upload action for a stored file reference in a JSON request body', async () => {
    const referenceOperation: ApiOperation = {
      ...operation,
      id: 'createArticle',
      path: '/articles',
      summary: '新增文章',
      requestBody: {
        content: {
          'application/json': {
            schema: {
              type: 'object',
              properties: {
                coverFileId: {
                  type: 'integer',
                  'x-lowcode-reference': { targetModelCode: 'storedFile', propertyName: 'coverFile' },
                },
              },
            },
          },
        },
      },
    }
    const wrapper = mount(ApiRequestEditor, {
      props: {
        operation: referenceOperation,
        document: {},
        pathValues: {},
        queryValues: {},
        headerValues: {},
        bodyText: '{\n  "coverFileId": 1\n}',
        multipartValues: {},
        fileReferenceUploads: {},
        loading: false,
      },
    })

    expect(wrapper.text()).toContain('coverFileId')
    expect(wrapper.text()).toContain('选择文件')
    expect(wrapper.find('textarea').exists()).toBe(true)

    const input = wrapper.find('.api-file-reference-picker input')
    const file = new File(['content'], 'cover.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    expect(wrapper.emitted('uploadFileReference')?.[0]).toEqual(['coverFileId', file])
  })

  it('renders OpenAPI field descriptions beside an editable JSON request body', () => {
    const bodyOperation: ApiOperation = {
      ...operation,
      id: 'createUser',
      path: '/users',
      summary: '新增用户',
      requestBody: {
        required: true,
        content: {
          'application/json': {
            schema: { $ref: '#/components/schemas/UserInput' },
          },
        },
      },
    }
    const wrapper = mount(ApiRequestEditor, {
      props: {
        operation: bodyOperation,
        document: {
          components: {
            schemas: {
              UserInput: {
                type: 'object',
                required: ['name'],
                properties: {
                  name: { type: 'string', description: '用户名称。' },
                  enabled: { type: 'boolean', description: '是否启用。' },
                },
              },
            },
          },
        },
        pathValues: {},
        queryValues: {},
        headerValues: {},
        bodyText: '{\n  "name": "Ada",\n  "enabled": true\n}',
        multipartValues: {},
        fileReferenceUploads: {},
        loading: false,
      },
    })

    const rows = wrapper.findAll('.api-request-schema-row')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('name')
    expect(rows[0].text()).toContain('必填')
    expect(rows[0].text()).toContain('用户名称。')
    expect(rows[1].text()).toContain('是否启用。')
    expect(wrapper.get('textarea').element.value).toContain('"name": "Ada"')
  })
})
