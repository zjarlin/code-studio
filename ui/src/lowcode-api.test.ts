import { afterEach, describe, expect, it, vi } from 'vitest'

import { configureLowcodeApiAccessToken, LowcodeApi } from './lowcode-api'

describe('low-code API client', () => {
  afterEach(() => {
    configureLowcodeApiAccessToken(() => '')
    vi.restoreAllMocks()
  })

  it('adds the configured bearer token to metadata requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 0,
      msg: '',
      data: [],
    })))
    vi.stubGlobal('fetch', fetchMock)
    configureLowcodeApiAccessToken(() => ' studio-token ')

    await new LowcodeApi().models()

    expect(fetchMock).toHaveBeenCalledWith('/studio/api/lowcode/model/list', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer studio-token' }),
    }))
  })

  it('loads a model page and normalizes pagination totals', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        code: 0,
        msg: '',
        data: {
          rows: [{ id: '-1', modelCode: 'example', modelType: 'ENTITY', name: 'Example', status: 1, version: 1 }],
          totalRowCount: '49',
          totalPageCount: 5,
        },
      })),
    )
    vi.stubGlobal('fetch', fetchMock)

    const page = await new LowcodeApi().modelPage(1, 10)

    expect(fetchMock).toHaveBeenCalledWith('/studio/api/lowcode/model/page', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ pageNumber: 1, pageSize: 10, condition: {} }),
    }))
    expect(page.rows).toHaveLength(1)
    expect(page.totalRowCount).toBe(49)
    expect(page.totalPageCount).toBe(5)
  })

  it('uses the generated Library Studio routes', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 0,
        msg: '',
        data: { list: [{ id: '-1', code: 'example', displayName: 'Example', spec: { features: [] } }], total: '1' },
      })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 0, msg: '', data: true })))
    vi.stubGlobal('fetch', fetchMock)
    const api = new LowcodeApi()

    const libraries = await api.libraries()
    await api.deleteLibrary(9)

    expect(libraries).toHaveLength(1)
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/studio/api/lowcode/library/page?pageNo=1&pageSize=1000', expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/studio/api/lowcode/library/delete', expect.objectContaining({
      method: 'DELETE',
      body: '[9]',
    }))
  })

  it('submits feature ownership without writable generated locations', async () => {
    const fetchMock = vi.fn().mockImplementation(async () =>
      new Response(JSON.stringify({ code: 0, msg: '', data: 7 })))
    vi.stubGlobal('fetch', fetchMock)

    await new LowcodeApi().save({
      featureId: 17,
      modelCode: 'inspectionTask',
      packageName: 'example.inspection.task',
      contributorId: 'example.catalog',
      routeConfig: {
        packageName: 'example.inspection.task',
        qualifiedName: 'example.inspection.task.generated.entity.InspectionTask',
        featurePackageName: 'example.inspection.task',
        path: '/inspection/task',
      },
    })

    const payload = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    expect(payload.featureId).toBe(17)
    expect(payload).not.toHaveProperty('packageName')
    expect(payload).not.toHaveProperty('contributorId')
    expect(payload.routeConfig).toEqual({ path: '/inspection/task' })
  })

  it('removes computed locations from DTO and Service writes', async () => {
    const fetchMock = vi.fn().mockImplementation(async () =>
      new Response(JSON.stringify({ code: 0, msg: '', data: 7 })))
    vi.stubGlobal('fetch', fetchMock)
    const api = new LowcodeApi()
    const dto = {
      featureId: 18,
      dtoCode: 'inspectionTaskView',
      packageName: 'example.inspection.task',
      contributorId: 'example.catalog',
    } as Parameters<LowcodeApi['saveDto']>[0]
    const service = {
      featureId: 18,
      contractCode: 'inspectionTask',
      packageName: 'example.inspection.task',
      contributorId: 'example.catalog',
    } as Parameters<LowcodeApi['saveContract']>[0]

    await api.saveDto(dto)
    await api.saveContract(service)

    for (const call of fetchMock.mock.calls) {
      const payload = JSON.parse(String(call[1]?.body))
      expect(payload.featureId).toBe(18)
      expect(payload).not.toHaveProperty('packageName')
      expect(payload).not.toHaveProperty('contributorId')
    }
  })

  it('loads upstream agent models and persists a conversation selection', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 0,
        msg: '',
        data: [{ id: 'gpt-5-mini' }, { id: 'provider-chat-model' }],
      })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 0, msg: '', data: true })))
    vi.stubGlobal('fetch', fetchMock)
    const api = new LowcodeApi()

    const models = await api.agentModels()
    await api.updateAgentConversationModel('21', 'provider-chat-model')

    expect(models.map((model) => model.id)).toEqual(['gpt-5-mini', 'provider-chat-model'])
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/studio/api/agent/models', expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/studio/api/agent/conversations/model', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ conversationId: '21', modelId: 'provider-chat-model' }),
    }))
  })

  it('loads and applies workspace display text patches through dedicated endpoints', async () => {
    const context = {
      tableId: 'metadata.display-text:workspace:1',
      revision: 'revision-1',
      targetColumnKey: 'value',
      rowIdentityKey: 'targetKey',
      context: { metadataScope: 'workspace' },
      operations: ['translate'],
      columns: [],
      rows: [],
      selection: { rowKeys: [], filteredRowCount: 0 },
    } as const
    const result = {
      tableId: context.tableId,
      revision: context.revision,
      patches: [],
      questions: [],
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 0, msg: '', data: context })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 0, msg: '', data: 3 })))
    vi.stubGlobal('fetch', fetchMock)
    const api = new LowcodeApi()

    await expect(api.agentDisplayTextContext()).resolves.toMatchObject(context)
    await expect(api.applyAgentDisplayText(result)).resolves.toBe(3)

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/studio/api/lowcode/agent/display-text/context', expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/studio/api/lowcode/agent/display-text/apply', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(result),
    }))
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/configuration/apply'))).toBe(false)
  })

  it('calls configured structured output by agent code', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 0,
      msg: '',
      data: { name: 'ENABLED', type: 'INT', value: '1', description: '开启状态。' },
    })))
    vi.stubGlobal('fetch', fetchMock)

    const output = await new LowcodeApi().generateStructuredOutput(
      'constantItemCompletion',
      { description: '开', value: '1' },
    )

    expect(output.name).toBe('ENABLED')
    expect(fetchMock).toHaveBeenCalledWith('/studio/api/agent/structured-output', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        agentCode: 'constantItemCompletion',
        input: { description: '开', value: '1' },
      }),
    }))
  })

  it('reports an actionable error when the platform proxy returns an empty 502', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 502 })))

    await expect(new LowcodeApi().agentSettings()).rejects.toThrow(
      '平台服务暂不可用（HTTP 502），请确认后端已启动或代理地址配置正确',
    )
  })

  it('keeps a backend business error instead of replacing it with the HTTP error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 401,
      msg: '登录已过期',
      data: null,
    }), { status: 401 })))

    await expect(new LowcodeApi().agentSettings()).rejects.toMatchObject({
      code: 401,
      message: '登录已过期',
    })
  })
})
