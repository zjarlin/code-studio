import { describe, expect, it, vi } from 'vitest'

import { ConstantApi } from './constant-api'

describe('constant metadata API', () => {
  it('scopes constant metadata by feature id', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(result([]))
    vi.stubGlobal('fetch', fetcher)
    const api = new ConstantApi()

    await api.list({ featureId: 9 })

    expect(fetcher).toHaveBeenCalledWith('/studio/api/lowcode/constant/list', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        featureId: 9,
      }),
    }))
  })

  it('saves comments together with constant values', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(result({ id: '1' }))
    vi.stubGlobal('fetch', fetcher)
    const api = new ConstantApi()

    await api.save({
      featureId: 9,
      groupCode: 'messageStatus',
      featurePackageName: 'example.message',
      contributorId: ':example:message',
      objectName: 'MessageConstants',
      description: '消息常量。',
      constants: [{ name: 'ENABLED_STATUS', type: 'INT', value: '1', description: '已启用状态。' }],
    })

    expect(fetcher).toHaveBeenCalledWith('/studio/api/lowcode/constant/save', expect.objectContaining({
      body: expect.stringContaining('"description":"已启用状态。"'),
    }))
    expect(String(fetcher.mock.calls[0]?.[1]?.body)).not.toContain('featurePackageName')
  })
})

function result(data: unknown): Response {
  return new Response(JSON.stringify({ code: 0, msg: '', data }), { status: 200 })
}
