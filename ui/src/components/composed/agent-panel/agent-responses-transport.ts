import type {
  AgentContextSnapshot,
  AgentContextSnapshotRequest,
  AgentResponseFunctionTool,
  AgentResponseObject,
} from '@/types'

export interface AgentResponseCreateInput {
  model: string
  input: string | Array<Record<string, unknown>>
  instructions?: string
  metadata?: Record<string, string>
  reasoning?: { effort: string }
  tools?: AgentResponseFunctionTool[]
  tool_choice?: string | Record<string, unknown>
  parallel_tool_calls?: boolean
  max_tool_calls?: number
  previous_response_id?: string
  conversation?: string
  store?: boolean
}

export interface AgentResponseStreamEvent {
  type: string
  sequence_number?: number
  response?: AgentResponseObject
  response_id?: string
  item?: Record<string, unknown>
  event_id?: string
  item_id?: string
  output_index?: number
  content_index?: number
  delta?: string
  arguments?: string
  name?: string
  phase?: string
  state?: string
  label?: string
  data_type?: string
  data?: Record<string, unknown>
  code?: string
  message?: string
}

export class AgentResponsesTransport {
  constructor(private readonly fetcher: typeof fetch = globalThis.fetch.bind(globalThis)) {}

  async createContextSnapshot(request: AgentContextSnapshotRequest, signal?: AbortSignal): Promise<AgentContextSnapshot> {
    const response = await this.fetcher('/studio/api/agent/context-snapshots', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
      signal,
    })
    return readProtocolResponse<AgentContextSnapshot>(response)
  }

  async create(
    input: AgentResponseCreateInput,
    onEvent: (event: AgentResponseStreamEvent) => void,
    signal?: AbortSignal,
  ): Promise<AgentResponseObject> {
    const response = await this.fetcher('/v1/responses', {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ ...input, stream: true }),
      signal,
    })
    if (!response.ok) throw await protocolError(response)
    if (!response.body) throw new Error('Responses 流没有响应体')

    let completed: AgentResponseObject | undefined
    await consumeResponseEventStream(response.body, (event) => {
      onEvent(event)
      if (event.response && ['response.completed', 'response.incomplete', 'response.failed'].includes(event.type)) {
        completed = event.response
      }
      if (event.type === 'error') {
        throw new Error(event.message || 'Responses 流执行失败')
      }
    })
    if (!completed) throw new Error('Responses 流缺少终态事件')
    return completed
  }

  async cancel(responseId: string, signal?: AbortSignal): Promise<AgentResponseObject> {
    const response = await this.fetcher(`/v1/responses/${encodeURIComponent(responseId)}/cancel`, {
      method: 'POST',
      signal,
    })
    return readProtocolResponse<AgentResponseObject>(response)
  }
}

export async function consumeResponseEventStream(
  stream: ReadableStream<Uint8Array>,
  onEvent: (event: AgentResponseStreamEvent) => void,
): Promise<void> {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    const frames = buffer.split(/\r?\n\r?\n/)
    buffer = frames.pop() ?? ''
    for (const frame of frames) parseEventFrame(frame, onEvent)
    if (done) break
  }
  if (buffer.trim()) parseEventFrame(buffer, onEvent)
}

function parseEventFrame(frame: string, onEvent: (event: AgentResponseStreamEvent) => void): void {
  const data = frame.split(/\r?\n/)
    .filter(line => line.startsWith('data:'))
    .map(line => line.slice(5).trimStart())
    .join('\n')
  if (!data || data === '[DONE]') return
  onEvent(JSON.parse(data) as AgentResponseStreamEvent)
}

async function readProtocolResponse<T>(response: Response): Promise<T> {
  if (!response.ok) throw await protocolError(response)
  const payload = await response.text()
  if (!payload.trim()) {
    throw new Error(`Responses 服务返回空响应（HTTP ${response.status}）`)
  }
  try {
    return JSON.parse(payload) as T
  } catch (cause) {
    throw new Error(`Responses 服务返回了无效的 JSON（HTTP ${response.status}）`, { cause })
  }
}

async function protocolError(response: Response): Promise<Error> {
  const body = await response.json().catch(() => undefined) as {
    error?: { message?: string; code?: string }
  } | undefined
  const message = body?.error?.message || `请求失败：${response.status}`
  return new Error(body?.error?.code ? `${message} (${body.error.code})` : message)
}
