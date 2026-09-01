import { cleanup, fireEvent, render, waitFor, within } from '@testing-library/react'
import type { ButtonHTMLAttributes } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { collectApiOperations } from '@platform/openapi-workbench'
import type { ApiDocument } from '@platform/openapi-workbench'

import type { CatalogEntry } from '@/catalog/types'

import type { ApiCatalog } from './catalog'
import { ApiWorkbench } from './page'

const actionNames: Record<string, string> = {
  'studio.api-docs.auth': '鉴权',
  'studio.api-docs.copy-curl': '复制 cURL',
  'studio.api-docs.refresh': '刷新',
  'studio.api-docs.reset': '重置',
  'studio.api-docs.send': '发送',
}

vi.mock('@/components/composed/catalog-action/catalog-action', () => ({
  CatalogAction: ({ elementKey, variant: _variant, ...props }: {
    elementKey: string
    variant?: string
  } & ButtonHTMLAttributes<HTMLButtonElement>) => <button {...props}>{actionNames[elementKey] ?? elementKey}</button>,
}))

afterEach(() => {
  cleanup()
  window.localStorage.clear()
  delete window.adminHostBridge
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('API workbench', () => {
  it('searches the business tree, toggles all endpoints, selects operations and opens documentation', () => {
    const view = renderWorkbench()

    expect(view.getByText('2 / 3 个端点')).toBeInTheDocument()
    fireEvent.change(view.getByLabelText('搜索 API'), { target: { value: '创建' } })
    const tree = within(view.getByLabelText('API 接口树'))
    expect(tree.getByText('创建用户')).toBeInTheDocument()
    expect(tree.queryByText('读取用户')).not.toBeInTheDocument()

    fireEvent.click(view.getByText('全部端点'))
    expect(view.getByText('1 / 3 个端点')).toBeInTheDocument()
    fireEvent.change(view.getByLabelText('搜索 API'), { target: { value: '' } })
    expect(view.getByText('3 / 3 个端点')).toBeInTheDocument()

    fireEvent.click(view.getByText('创建用户'))
    fireEvent.click(view.getByText('文档'))
    expect(view.getByText('接口说明')).toBeInTheDocument()
    expect(view.getByText('创建一个用户。')).toBeInTheDocument()
    expect(view.getByText('请求 Schema')).toBeInTheDocument()
  })

  it('validates and resets a required body before sending', async () => {
    const fetcher = vi.fn<typeof fetch>()
    vi.stubGlobal('fetch', fetcher)
    const view = renderWorkbench()
    fireEvent.click(view.getByText('创建用户'))
    const actions = within(view.getByRole('group', { name: '接口操作' }))
    expect(actions.getByText('发送')).toBeInTheDocument()
    expect(actions.getByText('重置')).toBeInTheDocument()
    const body = view.getByLabelText('请求体')
    expect((body as HTMLTextAreaElement).value).toContain('Ada')

    fireEvent.change(body, { target: { value: '' } })
    fireEvent.click(view.getByText('发送'))
    const alerts = await view.findAllByRole('alert')
    expect(alerts.every((alert) => alert.textContent?.includes('必填请求体'))).toBe(true)
    expect(fetcher).not.toHaveBeenCalled()

    fireEvent.click(view.getByText('重置'))
    expect((view.getByLabelText('请求体') as HTMLTextAreaElement).value).toContain('Ada')
  })

  it('sends a real draft, exposes business errors and stores metadata-only history', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(Response.json(
      { code: 500, msg: '业务校验失败', data: null },
      { status: 200, headers: { 'x-trace-id': 'trace' } },
    ))
    vi.stubGlobal('fetch', fetcher)
    const view = renderWorkbench()

    fireEvent.click(view.getByText('鉴权'))
    fireEvent.change(view.getByLabelText('Bearer Token'), { target: { value: 'secret-token' } })
    fireEvent.click(view.getByText('应用'))
    fireEvent.change(view.getByDisplayValue('1'), { target: { value: '42' } })
    fireEvent.click(view.getByText('发送'))

    await waitFor(() => expect(view.getByText('业务校验失败')).toBeInTheDocument())
    expect(view.getByText('500')).toBeInTheDocument()
    expect(String(fetcher.mock.calls[0]?.[0])).toContain('/admin-api/a-users/42')
    expect(new Headers(fetcher.mock.calls[0]?.[1]?.headers).get('Authorization')).toBe('Bearer secret-token')

    const history = window.localStorage.getItem('api-workbench.history') ?? ''
    const entries = JSON.parse(history) as Array<Record<string, unknown>>
    expect(entries[0]?.path).toBe('/admin-api/a-users/{id}')
    expect(history).not.toContain('secret-token')
    expect(history).not.toContain('业务校验失败')
  })
})

function renderWorkbench() {
  return render(<ApiWorkbench catalog={catalog()} refresh={vi.fn()} route={route} />)
}

function catalog(): ApiCatalog {
  const document: ApiDocument = {
    openapi: '3.1.0',
    info: { title: 'Application API', version: '1.0' },
    tags: [
      { name: 'Users', description: '用户接口' },
      { name: 'Actuator', description: '运行状态' },
    ],
    paths: {
      '/admin-api/a-users/{id}': {
        get: {
          operationId: 'getUser',
          summary: '读取用户',
          tags: ['Users'],
          parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'integer' } }],
          responses: { '200': { description: '成功' } },
        },
      },
      '/admin-api/users': {
        post: {
          operationId: 'createUser',
          summary: '创建用户',
          description: '创建一个用户。',
          tags: ['Users'],
          requestBody: {
            required: true,
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  required: ['name'],
                  properties: { name: { type: 'string', example: 'Ada', description: '姓名' } },
                },
              },
            },
          },
          responses: { '200': { description: '成功' } },
        },
      },
      '/actuator/health': {
        get: {
          operationId: 'health',
          summary: '健康检查',
          tags: ['Actuator'],
          responses: { '200': { description: '成功' } },
        },
      },
    },
  }
  return {
    baseUrl: `${window.location.origin}/admin-api`,
    config: {
      apiBaseUrl: '/admin-api',
      capabilities: [],
      contributorId: 'application',
      displayName: 'Application',
      editableContributorId: 'application',
      openApiPath: '/v3/api-docs',
    },
    document,
    operations: collectApiOperations(document),
  }
}

const route: CatalogEntry = {
  enabled: true,
  kind: 'ROUTE',
  name: 'API 文档',
  order: 20,
  path: '/console/studio/api-docs',
  permissions: [],
  routeKey: 'studio.api-docs',
}
