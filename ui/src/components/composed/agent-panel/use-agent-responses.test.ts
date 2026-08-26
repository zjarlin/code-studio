import { describe, expect, it, vi } from 'vitest'

import { AgentResponsesTransport } from './agent-responses-transport'
import { useAgentResponses } from './use-agent-responses'

describe('useAgentResponses', () => {
  it('projects opted in run events into one updatable trace message', async () => {
    const requests: Request[] = []
    const transport = new AgentResponsesTransport(async (input, init) => {
      const request = input instanceof Request
        ? input
        : new Request(new URL(input.toString(), 'http://studio.test'), init)
      requests.push(request.clone())
      return traceStreamResponse()
    })
    const chat = useAgentResponses({
      model: () => 'gpt-5-mini',
      transport,
    })

    await chat.send('检查工具')

    const requestBody = await requests[0]!.json() as { metadata: Record<string, string> }
    expect(requestBody.metadata.agent_trace).toBe('true')
    expect(chat.messages.value).toHaveLength(3)
    const trace = chat.messages.value[1]!
    expect(trace.id).toBe('trace_test')
    expect(trace.parts).toHaveLength(1)
    expect(trace.parts[0]).toMatchObject({
      type: 'data-observation',
      id: 'run_test_0',
      data: {
        phase: 'model',
        state: 'completed',
        label: '模型响应完成',
      },
    })
    expect(chat.messages.value[2]?.parts[0]).toMatchObject({ type: 'text', text: '完成' })
  })
})

function traceStreamResponse(): Response {
  const response = {
    id: 'resp_test',
    object: 'response',
    status: 'completed',
    output: [],
  }
  const events = [
    { type: 'response.created', response: { ...response, status: 'in_progress' }, sequence_number: 0 },
    {
      type: 'agent.run',
      response_id: 'resp_test',
      event_id: 'run_test_0',
      phase: 'model',
      state: 'running',
      label: '正在请求模型',
      sequence_number: 1,
    },
    {
      type: 'agent.run',
      response_id: 'resp_test',
      event_id: 'run_test_0',
      phase: 'model',
      state: 'completed',
      label: '模型响应完成',
      sequence_number: 2,
    },
    {
      type: 'response.output_item.added',
      item: { id: 'msg_test', type: 'message' },
      sequence_number: 3,
    },
    {
      type: 'response.output_text.delta',
      item_id: 'msg_test',
      delta: '完成',
      sequence_number: 4,
    },
    { type: 'response.completed', response, sequence_number: 5 },
  ]
  const payload = events
    .map(event => `event: ${event.type}\ndata: ${JSON.stringify(event)}\n\n`)
    .join('') + 'data: [DONE]\n\n'
  return new Response(payload, {
    headers: { 'content-type': 'text/event-stream' },
  })
}
