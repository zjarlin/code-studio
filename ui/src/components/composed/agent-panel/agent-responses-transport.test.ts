import { describe, expect, it, vi } from 'vitest'

import {
  AgentResponsesTransport,
  consumeResponseEventStream,
  type AgentResponseStreamEvent,
} from './agent-responses-transport'

describe('AgentResponsesTransport', () => {
  it('binds the default fetch implementation to the global receiver', async () => {
    const originalFetch = globalThis.fetch
    const receiver = globalThis
    const fetcher = vi.fn(function (this: typeof globalThis) {
      if (this !== receiver) throw new TypeError('Illegal invocation')
      return Promise.resolve(new Response(
        JSON.stringify({ id: 'ctx_test', expires_at: 123 }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ))
    }) as typeof fetch
    globalThis.fetch = fetcher

    try {
      const transport = new AgentResponsesTransport()
      await expect(transport.createContextSnapshot({ scene: 'model.detail' })).resolves.toMatchObject({
        id: 'ctx_test',
      })
    } finally {
      globalThis.fetch = originalFetch
    }
  })

  it('parses response events split across arbitrary network chunks', async () => {
    const encoder = new TextEncoder()
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event: response.created\ndata: {"type":"response.cre'))
        controller.enqueue(encoder.encode('ated","sequence_number":0}\n\nevent: response.output_text.delta\n'))
        controller.enqueue(encoder.encode('data: {"type":"response.output_text.delta","sequence_number":1,"delta":"ok"}\n\ndata: [DONE]\n\n'))
        controller.close()
      },
    })
    const events: AgentResponseStreamEvent[] = []

    await consumeResponseEventStream(stream, event => events.push(event))

    expect(events.map(event => event.type)).toEqual([
      'response.created',
      'response.output_text.delta',
    ])
    expect(events.map(event => event.sequence_number)).toEqual([0, 1])
  })

  it('creates immutable page context snapshots through the extension route', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(
      JSON.stringify({ id: 'ctx_test', expires_at: 123 }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ))
    const transport = new AgentResponsesTransport(fetcher)

    const snapshot = await transport.createContextSnapshot({
      scene: 'model.detail',
      resource_refs: [{ type: 'model', id: 'customer' }],
      draft: { modelCode: 'customer' },
    })

    expect(snapshot.id).toBe('ctx_test')
    expect(fetcher).toHaveBeenCalledWith('/studio/api/agent/context-snapshots', expect.objectContaining({
      method: 'POST',
    }))
  })

  it('reports a successful response with an empty body as a protocol error', async () => {
    const transport = new AgentResponsesTransport(
      vi.fn<typeof fetch>().mockResolvedValue(new Response('', { status: 200 })),
    )

    await expect(transport.createContextSnapshot({ scene: 'model.detail' })).rejects.toThrow(
      'Responses 服务返回空响应（HTTP 200）',
    )
  })
})
