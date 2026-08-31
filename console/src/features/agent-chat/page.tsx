import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { CatalogAction } from '@/components/catalog-action'
import { DataTable, type DataColumn } from '@/components/data-table'
import { PageHeader } from '@/components/page-header'
import { QueryState } from '@/components/query-state'
import type { CatalogPageProps } from '@/features/page-registry'

import {
  createConversation,
  fetchAgentModels,
  fetchConversations,
  type AgentConversation,
  type AgentModel,
} from './api'

const columns: DataColumn<AgentConversation>[] = [
  { key: 'title', header: '对话' },
  { key: 'modelId', header: '模型', width: '180px', cell: (value) => String(value || '-') },
  { key: 'updateTime', header: '最近更新', width: '180px', cell: (value) => formatDate(String(value)) },
]

export default function AgentChatPage({ route }: CatalogPageProps) {
  const queryClient = useQueryClient()
  const conversations = useQuery({ queryKey: ['agent-conversations'], queryFn: fetchConversations })
  const models = useQuery({ queryKey: ['agent-models'], queryFn: fetchAgentModels })
  const [selectedId, setSelectedId] = useState<string>()
  const [creating, setCreating] = useState(false)
  const create = useMutation({
    mutationFn: ({ modelId, title }: { modelId: string; title: string }) => createConversation(title, modelId),
    onSuccess: () => {
      setCreating(false)
      return queryClient.invalidateQueries({ queryKey: ['agent-conversations'] })
    },
  })
  const selected = conversations.data?.find((item) => String(item.id) === selectedId) ?? conversations.data?.[0]

  return (
    <div className="page-frame">
      <PageHeader
        actions={(
          <CatalogAction
            disabled={!models.data?.length || create.isPending}
            elementKey="agent.chat.create"
            onClick={() => setCreating(true)}
            variant="primary"
          />
        )}
        route={route}
      />
      <div className="toolbar">
        <span>{conversations.data?.length ?? 0} 个对话</span>
        {create.error && <span className="toolbar-error">{create.error.message}</span>}
      </div>
      <div className="workspace-grid">
        <section className="workspace-main" aria-label="对话目录">
          <QueryState error={conversations.error} pending={conversations.isPending}>
            <DataTable
              columns={columns}
              data={conversations.data ?? []}
              emptyText="尚未创建对话"
              getRowId={(item) => String(item.id)}
              onRowClick={(item) => setSelectedId(String(item.id))}
              selectedRowId={selected ? String(selected.id) : undefined}
            />
          </QueryState>
        </section>
        <aside className="inspector" aria-label="对话摘要">
          {selected ? (
            <>
              <div className="inspector-heading">
                <span className="eyebrow">智能体对话</span>
                <h2>{selected.title}</h2>
                <p>{selected.externalId}</p>
              </div>
              <dl className="definition-list">
                <div><dt>模型</dt><dd>{selected.modelId || '未选择'}</dd></div>
                <div><dt>创建时间</dt><dd>{formatDate(selected.createTime)}</dd></div>
                <div><dt>最近更新</dt><dd>{formatDate(selected.updateTime)}</dd></div>
              </dl>
            </>
          ) : <div className="empty-state">新建对话后在此查看摘要</div>}
        </aside>
      </div>
      {creating && (
        <CreateConversationDialog
          error={create.error}
          models={models.data ?? []}
          onClose={() => setCreating(false)}
          onSubmit={(title, modelId) => create.mutate({ title, modelId })}
          pending={create.isPending}
        />
      )}
    </div>
  )
}

function CreateConversationDialog({ error, models, onClose, onSubmit, pending }: Readonly<{
  error: Error | null
  models: AgentModel[]
  onClose: () => void
  onSubmit: (title: string, modelId: string) => void
  pending: boolean
}>) {
  const [title, setTitle] = useState('')
  const [modelId, setModelId] = useState(models[0]?.id ?? '')

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section aria-labelledby="create-conversation-title" aria-modal="true" className="dialog" role="dialog">
        <header>
          <h2 id="create-conversation-title">新建对话</h2>
          <p>选择模型并为这次对话命名。</p>
        </header>
        <form onSubmit={(event) => {
          event.preventDefault()
          onSubmit(title, modelId)
        }}>
          <label>
            名称
            <input autoFocus onChange={(event) => setTitle(event.target.value)} placeholder="新对话" value={title} />
          </label>
          <label>
            模型
            <select onChange={(event) => setModelId(event.target.value)} required value={modelId}>
              {models.map((model) => <option key={model.id} value={model.id}>{model.id}</option>)}
            </select>
          </label>
          {error && <p className="form-error" role="alert">{error.message}</p>}
          <footer>
            <CatalogAction elementKey="agent.chat.create.cancel" onClick={onClose} type="button" variant="ghost" />
            <CatalogAction disabled={!modelId || pending} elementKey="agent.chat.create.submit" type="submit" variant="primary" />
          </footer>
        </form>
      </section>
    </div>
  )
}

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.valueOf()) ? value : new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(date)
}
