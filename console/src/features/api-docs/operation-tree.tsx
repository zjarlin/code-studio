import { ChevronDown, ChevronRight, Clock3, Search } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'

import { groupApiOperations } from '@platform/openapi-workbench'
import type { ApiDocument, ApiHistoryEntry, ApiOperation } from '@platform/openapi-workbench'

interface OperationTreeProps {
  document: ApiDocument
  history: ApiHistoryEntry[]
  onHistorySelect: (entry: ApiHistoryEntry) => void
  onSelect: (operation: ApiOperation) => void
  onShowAllChange: (value: boolean) => void
  operations: ApiOperation[]
  query: string
  selected?: ApiOperation
  setQuery: (value: string) => void
  showAll: boolean
  totalCount: number
}

export function OperationTree({
  document,
  history,
  onHistorySelect,
  onSelect,
  onShowAllChange,
  operations,
  query,
  selected,
  setQuery,
  showAll,
  totalCount,
}: OperationTreeProps) {
  const groups = useMemo(() => groupApiOperations(document, operations), [document, operations])
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set(selected?.tags ?? []))

  useEffect(() => {
    if (!selected?.tags.length) return
    setExpanded((current) => new Set([...current, ...selected.tags]))
  }, [selected])

  function toggleGroup(name: string): void {
    setExpanded((current) => {
      const next = new Set(current)
      if (next.has(name)) next.delete(name)
      else next.add(name)
      return next
    })
  }

  return (
    <aside className="api-tree" aria-label="API 接口树">
      <div className="api-tree-toolbar">
        <label className="api-search">
          <Search aria-hidden="true" />
          <input
            aria-label="搜索 API"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="方法、路径、摘要、分组"
            type="search"
            value={query}
          />
        </label>
        <div className="api-scope-control" aria-label="端点范围">
          <button aria-pressed={!showAll} onClick={() => onShowAllChange(false)} type="button">业务接口</button>
          <button aria-pressed={showAll} onClick={() => onShowAllChange(true)} type="button">全部端点</button>
        </div>
        <span className="api-count">{operations.length} / {totalCount} 个端点</span>
      </div>

      <div className="api-tree-scroll">
        {groups.map((group) => {
          const open = Boolean(query.trim()) || expanded.has(group.name)
          return (
            <section className="api-group" key={group.name}>
              <button className="api-group-toggle" onClick={() => toggleGroup(group.name)} type="button">
                {open ? <ChevronDown /> : <ChevronRight />}
                <span title={group.description}>{group.name}</span>
                <small>{group.operations.length}</small>
              </button>
              {open && (
                <div className="api-operation-list">
                  {group.operations.map((operation) => (
                    <button
                      aria-current={selected?.id === operation.id && selected.path === operation.path ? 'true' : undefined}
                      className="api-operation-item"
                      key={`${group.name}:${operation.id}:${operation.path}`}
                      onClick={() => onSelect(operation)}
                      type="button"
                    >
                      <span className={`method method-${operation.method}`}>{operation.method.toUpperCase()}</span>
                      <span>
                        <strong>{operation.summary}</strong>
                        <code>{operation.path}</code>
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </section>
          )
        })}
        {!groups.length && <div className="api-empty">没有匹配的 API</div>}

        {history.length > 0 && (
          <section className="api-history" aria-label="请求历史">
            <h3><Clock3 />请求历史</h3>
            {history.map((entry, index) => (
              <button key={`${entry.createdAt}:${index}`} onClick={() => onHistorySelect(entry)} type="button">
                <span className={`method method-${entry.method}`}>{entry.method.toUpperCase()}</span>
                <span><code>{entry.path}</code><small>{entry.status ?? '-'} · {entry.durationMs ?? '-'} ms</small></span>
              </button>
            ))}
          </section>
        )}
      </div>
    </aside>
  )
}
