import OpenAI from 'openai'

import { describe, expect, it } from 'vitest'

describe('OpenAI Responses SDK compatibility', () => {
  it('uses the standard base URL for responses and conversations', async () => {
    const requests: RecordedRequest[] = []
    const client = clientWithFixtureTransport(requests)

    const created = await client.responses.create({ model: 'gpt-5-mini', input: 'hello' })
    const retrieved = await client.responses.retrieve(created.id)
    const cancelled = await client.responses.cancel(created.id)
    const conversation = await client.conversations.create({ metadata: { scene: 'studio' } })

    expect(created.id).toBe('resp_test')
    expect(created.output_text).toBe('ok')
    expect(retrieved.status).toBe('completed')
    expect(cancelled.status).toBe('cancelled')
    expect(conversation.id).toBe('conv_test')
    expect(requests.map(request => `${request.method} ${request.path}`)).toEqual([
      'POST /v1/responses',
      'GET /v1/responses/resp_test',
      'POST /v1/responses/resp_test/cancel',
      'POST /v1/conversations',
    ])
    expect(requests[0]?.body).toEqual({ model: 'gpt-5-mini', input: 'hello' })
  })

  it('parses standard Responses SSE events through the official SDK', async () => {
    const client = clientWithFixtureTransport([])
    const stream = await client.responses.create({
      model: 'gpt-5-mini',
      input: 'stream',
      stream: true,
    })

    const events = []
    for await (const event of stream) events.push(event)

    expect(events.map(event => event.type)).toEqual([
      'response.created',
      'response.output_text.delta',
      'response.completed',
    ])
    expect(events[1]).toMatchObject({ delta: 'ok', sequence_number: 1 })
  })

  it('keeps the standard stream parseable when trace extensions are explicitly requested', async () => {
    const client = clientWithFixtureTransport([])
    const stream = await client.responses.create({
      model: 'gpt-5-mini',
      input: 'stream',
      stream: true,
      metadata: { agent_trace: 'true' },
    })

    const eventTypes: string[] = []
    for await (const event of stream) eventTypes.push(event.type)

    expect(eventTypes).toEqual([
      'response.created',
      'agent.run',
      'response.output_text.delta',
      'response.completed',
    ])
  })

  it('surfaces the OpenAI error envelope as an SDK APIError', async () => {
    const client = clientWithFixtureTransport([])

    await expect(client.responses.create({ model: 'gpt-5-mini', input: 'reject' }))
      .rejects.toMatchObject({
        status: 400,
        code: 'unsupported_parameter',
        param: 'input',
        type: 'invalid_request_error',
      })
  })
})

interface RecordedRequest {
  method: string
  path: string
  body?: unknown
}

function clientWithFixtureTransport(requests: RecordedRequest[]): OpenAI {
  return new OpenAI({
    apiKey: 'test-key',
    baseURL: 'https://agent.test/v1',
    dangerouslyAllowBrowser: true,
    fetch: async (input, init) => {
      const request = input instanceof Request ? input : new Request(input, init)
      const url = new URL(request.url)
      const bodyText = request.method === 'GET' ? '' : await request.clone().text()
      requests.push({
        method: request.method,
        path: url.pathname,
        body: bodyText ? JSON.parse(bodyText) : undefined,
      })

      if (bodyText.includes('"reject"')) {
        return jsonResponse(
          {
            error: {
              message: 'input is not supported',
              type: 'invalid_request_error',
              param: 'input',
              code: 'unsupported_parameter',
            },
          },
          400,
        )
      }
      if (url.pathname === '/v1/conversations') {
        return jsonResponse({
          id: 'conv_test',
          object: 'conversation',
          created_at: 1_755_600_000,
          metadata: { scene: 'studio' },
        })
      }
      if (url.pathname.endsWith('/cancel')) {
        return jsonResponse(responseFixture('cancelled'))
      }
      if (bodyText.includes('"stream":true')) {
        return streamResponse(bodyText.includes('"agent_trace":"true"'))
      }
      return jsonResponse(responseFixture('completed'))
    },
  })
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function streamResponse(includeTrace = false): Response {
  const created = responseFixture('in_progress')
  const completed = responseFixture('completed')
  const events: Array<[string, Record<string, unknown>]> = [
    ['response.created', { type: 'response.created', response: created, sequence_number: 0 }],
    [
      'response.output_text.delta',
      {
        type: 'response.output_text.delta',
        item_id: 'msg_test',
        output_index: 0,
        content_index: 0,
        delta: 'ok',
        logprobs: [],
        sequence_number: 1,
      },
    ],
    ['response.completed', { type: 'response.completed', response: completed, sequence_number: 2 }],
  ]
  if (includeTrace) {
    events.splice(1, 0, [
      'agent.run',
      {
        type: 'agent.run',
        response_id: 'resp_test',
        event_id: 'run_test_0',
        phase: 'model',
        state: 'running',
        label: '正在请求模型',
        sequence_number: 1,
      },
    ])
    events[2]![1].sequence_number = 2
    events[3]![1].sequence_number = 3
  }
  const payload = events
    .map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`)
    .join('') + 'data: [DONE]\n\n'
  return new Response(payload, {
    headers: { 'content-type': 'text/event-stream' },
  })
}

function responseFixture(status: 'in_progress' | 'completed' | 'cancelled') {
  const completed = status === 'completed'
  return {
    id: 'resp_test',
    object: 'response',
    created_at: 1_755_600_000,
    completed_at: completed ? 1_755_600_001 : null,
    status,
    error: null,
    incomplete_details: null,
    instructions: null,
    max_output_tokens: null,
    model: 'gpt-5-mini',
    output: completed
      ? [{
          id: 'msg_test',
          type: 'message',
          status: 'completed',
          role: 'assistant',
          content: [{ type: 'output_text', text: 'ok', annotations: [], logprobs: [] }],
        }]
      : [],
    output_text: completed ? 'ok' : '',
    parallel_tool_calls: true,
    previous_response_id: null,
    reasoning: null,
    store: true,
    temperature: null,
    text: { format: { type: 'text' } },
    tool_choice: 'auto',
    tools: [],
    top_p: null,
    truncation: 'disabled',
    usage: completed
      ? {
          input_tokens: 1,
          input_tokens_details: { cached_tokens: 0 },
          output_tokens: 1,
          output_tokens_details: { reasoning_tokens: 0 },
          total_tokens: 2,
        }
      : null,
    metadata: {},
    background: false,
    conversation: null,
    max_tool_calls: null,
    prompt: null,
    prompt_cache_key: null,
    safety_identifier: null,
    service_tier: null,
    user: null,
  }
}
