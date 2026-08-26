import { ref, type Ref } from 'vue'

import type {
  AgentContextSnapshotRequest,
  AgentFunctionCallData,
  AgentObservationData,
  AgentReasoningEffort,
  AgentResponseFunctionTool,
  AgentResponseObject,
  AgentUiMessage,
} from '@/types'

import {
  AgentResponsesTransport,
  type AgentResponseCreateInput,
  type AgentResponseStreamEvent,
} from './agent-responses-transport'

export type AgentResponsesStatus = 'ready' | 'submitted' | 'streaming' | 'error'
type AgentMessagePart = AgentUiMessage['parts'][number]
type AgentTextPart = Extract<AgentMessagePart, { type: 'text' }>
type AgentFunctionCallPart = AgentMessagePart & {
  type: 'data-function-call'
  data: AgentFunctionCallData
}

export interface AgentResponsesChatOptions {
  model: () => string
  instructions?: () => string | undefined
  reasoningEffort?: () => AgentReasoningEffort
  conversation?: () => string | undefined
  tools?: () => AgentResponseFunctionTool[]
  contextSnapshot?: () => AgentContextSnapshotRequest | undefined
  onFinish?: (response: AgentResponseObject) => void
  transport?: AgentResponsesTransport
}

export interface AgentResponsesChat {
  messages: Ref<AgentUiMessage[]>
  status: Ref<AgentResponsesStatus>
  error: Ref<Error | undefined>
  send(text: string): Promise<void>
  submitFunctionOutput(callId: string, output: unknown): Promise<void>
  stop(): void
  reset(): void
}

export function useAgentResponses(options: AgentResponsesChatOptions): AgentResponsesChat {
  const transport = options.transport ?? new AgentResponsesTransport()
  const messages = ref<AgentUiMessage[]>([])
  const status = ref<AgentResponsesStatus>('ready')
  const error = ref<Error>()
  let previousResponseId: string | undefined
  let activeResponseId: string | undefined
  let controller: AbortController | undefined

  async function send(text: string): Promise<void> {
    messages.value = [...messages.value, userMessage(text)]
    await sendInput(text)
  }

  async function submitFunctionOutput(callId: string, output: unknown): Promise<void> {
    updateFunctionCall(callId, current => ({ ...current, status: 'submitted' }))
    await sendInput([
      {
        type: 'function_call_output',
        call_id: callId,
        output: typeof output === 'string' ? output : JSON.stringify(output),
      },
    ])
  }

  async function sendInput(input: AgentResponseCreateInput['input']): Promise<void> {
    controller?.abort()
    controller = new AbortController()
    status.value = 'submitted'
    error.value = undefined
    try {
      const snapshot = options.contextSnapshot?.()
      const context = snapshot
        ? await transport.createContextSnapshot(snapshot, controller.signal)
        : undefined
      const effort = options.reasoningEffort?.()
      const request: AgentResponseCreateInput = {
        model: options.model(),
        input,
        instructions: options.instructions?.(),
        metadata: {
          agent_trace: 'true',
          ...(context ? { agent_context_snapshot_id: context.id } : {}),
        },
        reasoning: effort && effort !== 'provider' ? { effort } : undefined,
        tools: options.tools?.(),
        tool_choice: 'auto',
        parallel_tool_calls: true,
        conversation: options.conversation?.(),
        previous_response_id: options.conversation?.() ? undefined : previousResponseId,
        store: true,
      }
      const response = await transport.create(request, projectEvent, controller.signal)
      previousResponseId = response.id
      activeResponseId = undefined
      if (response.status === 'failed') {
        throw new Error(response.error?.message || '智能体执行失败')
      }
      status.value = 'ready'
      options.onFinish?.(response)
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === 'AbortError') {
        status.value = 'ready'
        return
      }
      error.value = cause instanceof Error ? cause : new Error('智能体执行失败')
      status.value = 'error'
      throw error.value
    } finally {
      controller = undefined
    }
  }

  function projectEvent(event: AgentResponseStreamEvent): void {
    if (event.type === 'response.created') {
      activeResponseId = event.response?.id
      status.value = 'streaming'
      return
    }
    if (event.type === 'agent.run') {
      projectRunEvent(event)
      return
    }
    if (event.type === 'agent.data' && event.item_id && event.data_type) {
      const part = {
        type: event.data_type,
        id: event.event_id,
        data: event.data ?? {},
      } as AgentMessagePart
      const existing = messages.value.find(message => message.id === event.item_id)
      if (existing) {
        replaceMessage(event.item_id, message => ({ ...message, parts: [...message.parts, part] }))
      } else {
        messages.value = [...messages.value, { id: event.item_id, role: 'assistant', parts: [part] }]
      }
      return
    }
    if (event.type === 'response.output_item.added') {
      const item = event.item
      if (item?.type === 'message' && typeof item.id === 'string') {
        const existing = messages.value.find(message => message.id === item.id)
        if (existing) {
          replaceMessage(item.id, message => ({ ...message, parts: [...message.parts, { type: 'text', text: '' }] }))
        } else {
          messages.value = [...messages.value, assistantTextMessage(item.id)]
        }
      }
      if (item?.type === 'function_call' && typeof item.id === 'string') {
        const call: AgentFunctionCallData = {
          itemId: item.id,
          callId: String(item.call_id ?? ''),
          name: String(item.name ?? ''),
          arguments: String(item.arguments ?? ''),
          status: 'streaming',
        }
        messages.value = [...messages.value, assistantFunctionMessage(call)]
      }
      return
    }
    if (event.type === 'response.output_text.delta' && event.item_id) {
      updateMessagePart(event.item_id, 'text', part => ({
        ...part,
        text: `${(part as AgentTextPart).text ?? ''}${event.delta ?? ''}`,
      }))
      return
    }
    if (event.type === 'response.function_call_arguments.delta' && event.item_id) {
      updateMessagePart(event.item_id, 'data-function-call', part => ({
        ...part,
        data: {
          ...(part as AgentFunctionCallPart).data,
          arguments: `${(part as AgentFunctionCallPart).data.arguments}${event.delta ?? ''}`,
        },
      }))
      return
    }
    if (event.type === 'response.function_call_arguments.done' && event.item_id) {
      updateMessagePart(event.item_id, 'data-function-call', part => ({
        ...part,
        data: {
          ...(part as AgentFunctionCallPart).data,
          name: event.name ?? (part as AgentFunctionCallPart).data.name,
          arguments: event.arguments ?? (part as AgentFunctionCallPart).data.arguments,
          status: 'pending',
        },
      }))
      return
    }
    if (['response.completed', 'response.incomplete'].includes(event.type) && event.response?.usage) {
      const usage = event.response.usage
      const target = [...messages.value].reverse().find(message => message.role === 'assistant')
      if (target) {
        replaceMessage(target.id, message => ({
          ...message,
          parts: [...message.parts, {
            type: 'data-context',
            id: `${event.response?.id}-usage`,
            data: {
              inputTokens: usage.input_tokens,
              outputTokens: usage.output_tokens,
              totalTokens: usage.total_tokens,
              contextWindow: 0,
              contextWindowEstimated: true,
              compactedMessages: 0,
            },
          }],
        }))
      }
    }
  }

  function projectRunEvent(event: AgentResponseStreamEvent): void {
    const responseId = event.response_id ?? activeResponseId
    if (!responseId || !event.event_id || !event.phase || !event.state || !event.label) return
    const messageId = traceMessageId(responseId)
    const data: AgentObservationData = {
      phase: event.phase,
      state: event.state,
      label: event.label,
    }
    const existing = messages.value.find(message => message.id === messageId)
    if (!existing) {
      messages.value = [...messages.value, assistantTraceMessage(messageId, event.event_id, data)]
      return
    }
    replaceMessage(messageId, message => {
      const partIndex = message.parts.findIndex(part => 'id' in part && part.id === event.event_id)
      if (partIndex < 0) {
        return {
          ...message,
          parts: [...message.parts, observationPart(event.event_id!, data)],
        }
      }
      return {
        ...message,
        parts: message.parts.map((part, index) => index === partIndex
          ? observationPart(event.event_id!, data)
          : part),
      }
    })
  }

  function updateFunctionCall(
    callId: string,
    updater: (data: AgentFunctionCallData) => AgentFunctionCallData,
  ): void {
    messages.value = messages.value.map(message => ({
      ...message,
      parts: message.parts.map(part => {
        if (part.type !== 'data-function-call') return part
        const data = part.data as AgentFunctionCallData
        return data.callId === callId ? { ...part, data: updater(data) } : part
      }),
    }))
  }

  function updateMessagePart(
    messageId: string,
    type: string,
    updater: (part: AgentMessagePart) => AgentMessagePart,
  ): void {
    replaceMessage(messageId, message => ({
      ...message,
      parts: message.parts.map(part => part.type === type ? updater(part) : part),
    }))
  }

  function replaceMessage(messageId: string, updater: (message: AgentUiMessage) => AgentUiMessage): void {
    messages.value = messages.value.map(message => message.id === messageId ? updater(message) : message)
  }

  function stop(): void {
    controller?.abort()
    if (activeResponseId) void transport.cancel(activeResponseId).catch(() => undefined)
  }

  function reset(): void {
    stop()
    messages.value = []
    previousResponseId = undefined
    activeResponseId = undefined
    error.value = undefined
    status.value = 'ready'
  }

  return { messages, status, error, send, submitFunctionOutput, stop, reset }
}

function userMessage(text: string): AgentUiMessage {
  return {
    id: `msg_${crypto.randomUUID()}`,
    role: 'user',
    parts: [{ type: 'text', text }],
  }
}

function assistantTextMessage(id: string): AgentUiMessage {
  return {
    id,
    role: 'assistant',
    parts: [{ type: 'text', text: '' }],
  }
}

function assistantTraceMessage(
  messageId: string,
  eventId: string,
  data: AgentObservationData,
): AgentUiMessage {
  return {
    id: messageId,
    role: 'assistant',
    parts: [observationPart(eventId, data)],
  }
}

function observationPart(eventId: string, data: AgentObservationData): AgentMessagePart {
  return {
    type: 'data-observation',
    id: eventId,
    data,
  }
}

function traceMessageId(responseId: string): string {
  return `trace_${responseId.replace(/^resp_/, '')}`
}

function assistantFunctionMessage(data: AgentFunctionCallData): AgentUiMessage {
  return {
    id: data.itemId,
    role: 'assistant',
    parts: [{ type: 'data-function-call', id: data.itemId, data }],
  }
}
