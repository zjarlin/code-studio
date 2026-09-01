import { afterEach, describe, expect, it, vi } from 'vitest'

import { openServerSentEvents } from './streaming'

afterEach(() => vi.unstubAllGlobals())

describe('generated SSE transport', () => {
  it('parses named JSON events and the completion marker', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response([
      'event: progress',
      'id: 7',
      'data: {"value":1}',
      '',
      'data: [DONE]',
      '',
    ].join('\n'), { headers: { 'Content-Type': 'text/event-stream' } }))
    vi.stubGlobal('fetch', fetcher)
    const events: unknown[] = []
    const done = vi.fn()

    await openServerSentEvents('/events', {}, { onEvent: (event) => events.push(event), onDone: done })

    expect(events).toEqual([{ event: 'progress', id: '7', data: { value: 1 } }])
    expect(done).toHaveBeenCalledOnce()
  })
})
