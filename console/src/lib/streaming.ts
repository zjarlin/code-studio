import { authenticatedFetch } from './access-context'
import { resolveApiPath, type ApiRequestOptions } from './http'

export interface ServerSentEvent<T> {
  data: T
  event?: string
  id?: string
}

export interface ServerSentEventHandlers<T> {
  onEvent: (event: ServerSentEvent<T>) => void
  onDone?: () => void
}

export async function openServerSentEvents<T>(
  path: string,
  init: ApiRequestOptions,
  handlers: ServerSentEventHandlers<T>,
): Promise<void> {
  const { baseUrl, ...requestInit } = init
  const headers = new Headers(requestInit.headers)
  headers.set('Accept', 'text/event-stream')
  if (requestInit.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await authenticatedFetch(resolveApiPath(path, baseUrl), { ...requestInit, headers })
  if (!response.ok || !response.body) {
    const detail = await response.text().catch(() => '')
    throw new Error(detail || `HTTP ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done }).replaceAll('\r\n', '\n')
    const frames = buffer.split('\n\n')
    buffer = frames.pop() ?? ''
    for (const frame of frames) {
      if (emitServerSentEvent(frame, handlers)) return
    }
    if (done) break
  }
  if (buffer && emitServerSentEvent(buffer, handlers)) return
  handlers.onDone?.()
}

export interface WebSocketRequestOptions {
  baseUrl?: string
  protocols?: string | string[]
}

export function openGeneratedWebSocket(path: string, options: WebSocketRequestOptions = {}): WebSocket {
  if (typeof window === 'undefined') throw new Error('WebSocket 只能在浏览器中建立')
  const url = new URL(resolveApiPath(path, options.baseUrl), window.location.origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return new WebSocket(url, options.protocols)
}

function emitServerSentEvent<T>(frame: string, handlers: ServerSentEventHandlers<T>): boolean {
  let event: string | undefined
  let id: string | undefined
  const data: string[] = []
  frame.split('\n').forEach((line) => {
    if (line.startsWith('event:')) event = line.slice(6).trimStart()
    else if (line.startsWith('id:')) id = line.slice(3).trimStart()
    else if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
  })
  if (!data.length) return false
  const payload = data.join('\n')
  if (payload === '[DONE]') {
    handlers.onDone?.()
    return true
  }
  handlers.onEvent({ data: JSON.parse(payload) as T, event, id })
  return false
}
