import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import { generateStreamClients } from './stream-client-generator.mjs'

describe('stream client generator', () => {
  it('generates typed SSE and WebSocket operations from extensions', () => {
    const source = generateStreamClients({
      paths: {
        '/events/{id}': {
          post: {
            operationId: 'watchEvents',
            'x-client-transport': 'sse',
            requestBody: { content: { 'application/json': { schema: { $ref: '#/components/schemas/WatchCommand' } } } },
            responses: { 200: { content: { 'text/event-stream': { schema: { $ref: '#/components/schemas/WatchEvent' } } } } },
          },
        },
        '/notifications': {
          get: { operationId: 'notifications', 'x-client-transport': 'websocket' },
        },
      },
    })

    assert.match(source, /streamWatchEvents/)
    assert.match(source, /openNotificationsSocket/)
    assert.match(source, /WatchCommand, WatchEvent/)
    assert.match(source, /encodeURIComponent/)
  })
})
