import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'

import { CatalogAction } from '@/components/catalog-action'
import { DataTable, type DataColumn } from '@/components/data-table'
import { PageHeader } from '@/components/page-header'
import { QueryState } from '@/components/query-state'
import type { CatalogPageProps } from '@/features/page-registry'

import { fetchApiCatalog, type ApiOperationRow } from './api'

const columns: DataColumn<ApiOperationRow>[] = [
  { key: 'method', header: '方法', width: '80px', cell: (value) => <span className={`method method-${String(value).toLowerCase()}`}>{String(value)}</span> },
  { key: 'path', header: '路径', cell: (value) => <code>{String(value)}</code> },
  { key: 'summary', header: '摘要' },
  { key: 'group', header: '分组', width: '120px' },
]

export default function ApiDocsPage({ route }: CatalogPageProps) {
  const catalog = useQuery({ queryKey: ['openapi-catalog'], queryFn: fetchApiCatalog })
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState<string>()
  const operations = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase()
    if (!keyword) return catalog.data?.operations ?? []
    return (catalog.data?.operations ?? []).filter((operation) =>
      `${operation.method} ${operation.path} ${operation.summary} ${operation.group}`.toLocaleLowerCase().includes(keyword),
    )
  }, [catalog.data?.operations, query])
  const selected = operations.find((operation) => operation.id === selectedId) ?? operations[0]

  return (
    <div className="page-frame">
      <PageHeader
        actions={<CatalogAction elementKey="studio.api-docs.refresh" onClick={() => catalog.refetch()} />}
        route={route}
      />
      <div className="toolbar">
        <input aria-label="搜索 API" onChange={(event) => setQuery(event.target.value)} placeholder="搜索方法、路径或摘要" type="search" value={query} />
        <span>{operations.length} 个端点</span>
      </div>
      <div className="workspace-grid">
        <section className="workspace-main" aria-label="API 端点">
          <QueryState error={catalog.error} pending={catalog.isPending}>
            <DataTable
              columns={columns}
              data={operations}
              emptyText="没有匹配的 API"
              getRowId={(operation) => operation.id}
              onRowClick={(operation) => setSelectedId(operation.id)}
              selectedRowId={selected?.id}
            />
          </QueryState>
        </section>
        <aside className="inspector" aria-label="API 摘要">
          {selected ? (
            <>
              <div className="inspector-heading">
                <span className="eyebrow">{selected.group}</span>
                <h2>{selected.summary}</h2>
                <p>{selected.description || '未填写说明'}</p>
              </div>
              <dl className="definition-list">
                <div><dt>方法</dt><dd>{selected.method}</dd></div>
                <div><dt>路径</dt><dd><code>{selected.path}</code></dd></div>
                <div><dt>权限</dt><dd>{selected.permission || '无额外声明'}</dd></div>
                <div><dt>契约</dt><dd>OpenAPI {catalog.data?.openapi}</dd></div>
                <div><dt>版本</dt><dd>{catalog.data?.version || '-'}</dd></div>
              </dl>
            </>
          ) : <div className="empty-state">选择 API 查看摘要</div>}
        </aside>
      </div>
    </div>
  )
}
