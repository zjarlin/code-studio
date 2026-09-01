import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Send, Square, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { createAgentConversation, listAgentConversations, listAgentModels } from '@generated/openapi/client'
import type { AgentConversationView, AgentMessageView, AgentProviderModel } from '@generated/openapi/models'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { PageHeader } from '@/components/composed/page-header/page-header'
import { QueryState } from '@/components/composed/query-state/query-state'
import type { CatalogPageProps } from '@/features/page-registry'
import { applicationRequestOptions } from '@/lib/application-client'
import { requireApiData } from '@/lib/http'
import { openServerSentEvents } from '@/lib/streaming'

import { changeAgentConversationModel, fetchAgentMessages, removeAgentConversation } from './commands'

type StreamEvent = {
  type?: string
  delta?: string
  message?: string
}

export default function AgentChatPage({ route }: CatalogPageProps) {
  const queryClient = useQueryClient()
  const conversations = useQuery({
    queryKey: ['agent-conversations'],
    queryFn: async () => requireApiData(await listAgentConversations(await applicationRequestOptions()), 'Agent 会话响应缺少 data'),
  })
  const models = useQuery({
    queryKey: ['agent-models'],
    queryFn: async () => requireApiData(await listAgentModels(await applicationRequestOptions()), 'Agent 模型响应缺少 data'),
  })
  const [selectedId, setSelectedId] = useState<string>()
  const [creating, setCreating] = useState(false)
  const [search, setSearch] = useState('')
  const [draft, setDraft] = useState('')
  const [streamError, setStreamError] = useState<string>()
  const [localMessages, setLocalMessages] = useState<AgentMessageView[]>([])
  const controller = useRef<AbortController | undefined>(undefined)
  const create = useMutation({
    mutationFn: async ({ modelId, title }: { modelId: string; title: string }) => requireApiData(
      await createAgentConversation({ title: title.trim() || null, modelId }, await applicationRequestOptions()),
      'Agent 会话创建响应缺少 data',
    ),
    onSuccess: async () => { setCreating(false); await queryClient.invalidateQueries({ queryKey: ['agent-conversations'] }) },
  })
  const selected = conversations.data?.find((item) => String(item.id) === selectedId) ?? conversations.data?.[0]
  const messages = useQuery({
    queryKey: ['agent-messages', selected?.id],
    enabled: selected?.id != null,
    queryFn: () => fetchAgentMessages(selected!.id as number),
  })
  useEffect(() => { setLocalMessages(messages.data ?? []); setStreamError(undefined) }, [messages.data, selected?.id])
  const visibleConversations = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase()
    return (conversations.data ?? []).filter((item) => !keyword || `${item.title} ${item.modelId ?? ''}`.toLocaleLowerCase().includes(keyword))
  }, [conversations.data, search])
  const deleteConversation = useMutation({
    mutationFn: removeAgentConversation,
    onSuccess: async () => { setSelectedId(undefined); await queryClient.invalidateQueries({ queryKey: ['agent-conversations'] }) },
  })
  const updateModel = useMutation({
    mutationFn: ({ conversationId, modelId }: { conversationId: number; modelId: string }) => changeAgentConversationModel(conversationId, modelId),
    onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['agent-conversations'] }) },
  })

  async function send(): Promise<void> {
    const text = draft.trim()
    if (!text || !selected || controller.current) return
    const baseUrl = (await applicationRequestOptions()).baseUrl
    const assistantId = `local-${Date.now()}`
    setDraft('')
    setStreamError(undefined)
    setLocalMessages((current) => [...current, { id: `local-user-${Date.now()}`, role: 'user', parts: [{ type: 'text', text }] }, { id: assistantId, role: 'assistant', parts: [{ type: 'text', text: '' }] }])
    const active = new AbortController()
    controller.current = active
    try {
      await openServerSentEvents<StreamEvent>('/v1/responses', {
        baseUrl,
        body: JSON.stringify({ model: selected.modelId, input: text, conversation: selected.externalId, stream: true, store: true }),
        method: 'POST',
        signal: active.signal,
      }, {
        onEvent: (event) => {
          if (event.data.type === 'error') throw new Error(event.data.message || 'Agent 执行失败')
          if (event.data.type === 'response.output_text.delta' && event.data.delta) {
            setLocalMessages((current) => current.map((message) => message.id === assistantId ? { ...message, parts: [{ type: 'text', text: `${messageText(message)}${event.data.delta}` }] } : message))
          }
        },
      })
      await queryClient.invalidateQueries({ queryKey: ['agent-messages', selected.id] })
    } catch (cause) {
      if (!(cause instanceof DOMException && cause.name === 'AbortError')) setStreamError(cause instanceof Error ? cause.message : 'Agent 执行失败')
    } finally { controller.current = undefined }
  }

  return (
    <div className="page-frame agent-chat-page">
      <PageHeader actions={<CatalogAction disabled={!models.data?.length || create.isPending} elementKey="agent.chat.create" onClick={() => setCreating(true)} variant="primary" />} route={route} />
      <div className="agent-chat-layout">
        <aside className="agent-conversation-sidebar" aria-label="对话列表">
          <div className="agent-sidebar-heading"><strong>对话</strong><span>{visibleConversations.length}</span></div>
          <input aria-label="搜索对话" onChange={(event) => setSearch(event.target.value)} placeholder="搜索对话" type="search" value={search} />
          <QueryState error={conversations.error} pending={conversations.isPending}>
            <nav className="agent-conversation-list">
              {visibleConversations.map((conversation) => <button aria-current={conversation.id === selected?.id ? 'page' : undefined} className="agent-conversation-item" key={conversation.id} onClick={() => setSelectedId(String(conversation.id))} type="button"><strong>{conversation.title}</strong><span>{conversation.modelId || '未选择模型'}</span></button>)}
              {!visibleConversations.length && <p className="feature-index-empty">尚未创建对话</p>}
            </nav>
          </QueryState>
        </aside>
        <main className="agent-chat-panel" aria-label="Agent 对话">
          {selected ? <>
            <header className="agent-chat-header"><div><span className="eyebrow">智能体对话</span><h2>{selected.title}</h2></div><div className="agent-chat-header-actions"><select aria-label="选择模型" disabled={!models.data?.length || updateModel.isPending} onChange={(event) => updateModel.mutate({ conversationId: selected.id, modelId: event.target.value })} value={selected.modelId ?? ''}>{(models.data ?? []).map((model) => <option key={model.id} value={model.id}>{model.id}</option>)}</select><button aria-label="删除对话" className="icon-button" onClick={() => { if (window.confirm(`确认删除 ${selected.title}？`)) deleteConversation.mutate(selected.id) }} title="删除对话" type="button"><Trash2 /></button></div></header>
            <QueryState error={messages.error} pending={messages.isPending}><div className="agent-message-list">{localMessages.map((message) => <article className={`agent-message agent-message-${message.role}`} key={message.id}><span className="agent-message-role">{message.role === 'user' ? '你' : 'Agent'}</span><p>{messageText(message) || (message.role === 'assistant' && controller.current ? '思考中…' : '')}</p></article>)}{!localMessages.length && <div className="empty-state">发送第一条消息开始对话</div>}</div></QueryState>
            {streamError && <p className="form-error" role="alert">{streamError}</p>}
            <form className="agent-composer" onSubmit={(event) => { event.preventDefault(); void send() }}><textarea aria-label="消息" disabled={Boolean(controller.current)} onChange={(event) => setDraft(event.target.value)} placeholder="输入消息" rows={3} value={draft} /><div><span>{selected.modelId || '未选择模型'}</span>{controller.current ? <button aria-label="停止生成" className="icon-button" onClick={() => controller.current?.abort()} title="停止生成" type="button"><Square /></button> : <button aria-label="发送消息" className="icon-button primary" disabled={!draft.trim()} title="发送消息" type="submit"><Send /></button>}</div></form>
          </> : <div className="empty-state">新建对话后开始聊天</div>}
        </main>
      </div>
      {creating && <CreateConversationDialog error={create.error} models={models.data ?? []} onClose={() => setCreating(false)} onSubmit={(title, modelId) => create.mutate({ title, modelId })} pending={create.isPending} />}
    </div>
  )
}

function CreateConversationDialog({ error, models, onClose, onSubmit, pending }: Readonly<{ error: Error | null; models: AgentProviderModel[]; onClose: () => void; onSubmit: (title: string, modelId: string) => void; pending: boolean }>) {
  const [title, setTitle] = useState('')
  const [modelId, setModelId] = useState(models[0]?.id ?? '')
  return <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section aria-labelledby="create-conversation-title" aria-modal="true" className="dialog" role="dialog"><header><h2 id="create-conversation-title">新建对话</h2><p>选择模型并为这次对话命名。</p></header><form onSubmit={(event) => { event.preventDefault(); onSubmit(title, modelId) }}><label>名称<input autoFocus onChange={(event) => setTitle(event.target.value)} placeholder="新对话" value={title} /></label><label>模型<select onChange={(event) => setModelId(event.target.value)} required value={modelId}>{models.map((model) => <option key={model.id} value={model.id}>{model.id}</option>)}</select></label>{error && <p className="form-error" role="alert">{error.message}</p>}<footer><CatalogAction elementKey="agent.chat.create.cancel" onClick={onClose} type="button" variant="ghost" /><CatalogAction disabled={!modelId || pending} elementKey="agent.chat.create.submit" type="submit" variant="primary" /></footer></form></section></div>
}

function messageText(message: AgentMessageView): string {
  return (message.parts ?? []).map((part) => {
    const value = part as Record<string, unknown>
    if (typeof value.text === 'string') return value.text
    if (typeof value.content === 'string') return value.content
    const data = value.data
    if (data && typeof data === 'object' && typeof (data as Record<string, unknown>).text === 'string') return String((data as Record<string, unknown>).text)
    return ''
  }).join('')
}
