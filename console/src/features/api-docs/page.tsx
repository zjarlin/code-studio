import { useQuery } from '@tanstack/react-query'
import { useDeferredValue, useMemo, useState } from 'react'

import { isBusinessOperation } from '@platform/openapi-workbench'
import type { ApiHistoryEntry, ApiOperation } from '@platform/openapi-workbench'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { PageHeader } from '@/components/composed/page-header/page-header'
import { QueryState } from '@/components/composed/query-state/query-state'
import type { CatalogPageProps } from '@/features/page-registry'

import { fetchApiCatalog, type ApiCatalog } from './catalog'
import { AuthDialog } from './auth-dialog'
import { DocumentationPanel } from './documentation-panel'
import { OperationTree } from './operation-tree'
import { RequestPanel } from './request-panel'
import { ResponsePanel } from './response-panel'
import { useApiWorkbenchSession } from './session'

export default function ApiDocsPage({ route }: CatalogPageProps) {
  const query = useQuery({ queryKey: ['openapi-catalog'], queryFn: fetchApiCatalog })
  return (
    <QueryState error={query.error} pending={query.isPending}>
      {query.data && <ApiWorkbench catalog={query.data} refresh={() => query.refetch()} route={route} />}
    </QueryState>
  )
}

export function ApiWorkbench({ catalog, refresh, route }: Readonly<{
  catalog: ApiCatalog
  refresh: () => void
  route: CatalogPageProps['route']
}>) {
  const session = useApiWorkbenchSession(catalog)
  const [query, setQuery] = useState('')
  const deferredQuery = useDeferredValue(query.trim().toLocaleLowerCase())
  const [showAll, setShowAll] = useState(false)
  const [requestView, setRequestView] = useState<'debug' | 'docs'>('debug')
  const [authOpen, setAuthOpen] = useState(false)
  const [mobilePane, setMobilePane] = useState<'tree' | 'request' | 'response'>('request')
  const businessOperations = useMemo(
    () => catalog.operations.filter((operation) => isBusinessOperation({
      baseUrl: catalog.baseUrl,
      document: catalog.document,
      operation,
    })),
    [catalog],
  )
  const scopedOperations = showAll ? catalog.operations : businessOperations
  const visibleOperations = useMemo(() => scopedOperations.filter((operation) => {
    if (!deferredQuery) return true
    return `${operation.method} ${operation.path} ${operation.summary} ${operation.tags.join(' ')}`
      .toLocaleLowerCase()
      .includes(deferredQuery)
  }), [deferredQuery, scopedOperations])

  function changeScope(value: boolean): void {
    setShowAll(value)
    if (!value && session.selected && !businessOperations.some((operation) => operation.id === session.selected?.id)) {
      const first = businessOperations[0]
      if (first) session.select(first)
    }
  }

  function selectOperation(operation: ApiOperation): void {
    session.select(operation)
    setMobilePane('request')
  }

  function selectHistory(entry: ApiHistoryEntry): void {
    const operation = catalog.operations.find((candidate) => candidate.id === entry.id)
    if (!operation) return
    session.select(operation, entry.path)
    setMobilePane('response')
  }

  return (
    <div className="page-frame api-page">
      <PageHeader
        actions={(
          <>
            {session.manualToken && <span className="api-auth-active">临时鉴权已启用</span>}
            <CatalogAction elementKey="studio.api-docs.auth" onClick={() => setAuthOpen(true)} />
            <CatalogAction elementKey="studio.api-docs.refresh" onClick={refresh} />
          </>
        )}
        route={route}
      />

      <nav className="api-mobile-tabs" aria-label="移动端工作区">
        <button aria-pressed={mobilePane === 'tree'} onClick={() => setMobilePane('tree')} type="button">接口</button>
        <button aria-pressed={mobilePane === 'request'} onClick={() => setMobilePane('request')} type="button">请求</button>
        <button aria-pressed={mobilePane === 'response'} onClick={() => setMobilePane('response')} type="button">响应</button>
      </nav>

      <main className="api-workbench">
        <div className={`api-pane api-pane-tree ${mobilePane === 'tree' ? 'is-mobile-active' : ''}`}>
          <OperationTree
            document={catalog.document}
            history={session.history}
            onHistorySelect={selectHistory}
            onSelect={selectOperation}
            onShowAllChange={changeScope}
            operations={visibleOperations}
            query={query}
            selected={session.selected}
            setQuery={setQuery}
            showAll={showAll}
            totalCount={catalog.operations.length}
          />
        </div>

        <section className={`api-pane api-request-panel ${mobilePane === 'request' ? 'is-mobile-active' : ''}`} aria-label="请求工作区">
          <nav className="api-panel-tabs" aria-label="请求视图">
            <button aria-pressed={requestView === 'debug'} onClick={() => setRequestView('debug')} type="button">调试</button>
            <button aria-pressed={requestView === 'docs'} onClick={() => setRequestView('docs')} type="button">文档</button>
          </nav>
          {session.selected ? (
            requestView === 'debug' ? (
              <RequestPanel
                document={catalog.document}
                draft={session.draft}
                error={session.error}
                onChange={session.setDraft}
                onReset={session.reset}
                onSend={session.send}
                operation={session.selected}
                pending={session.pending}
              />
            ) : <DocumentationPanel document={catalog.document} operation={session.selected} />
          ) : <div className="api-empty">选择接口后查看请求和文档</div>}
        </section>

        <div className={`api-pane api-pane-response ${mobilePane === 'response' ? 'is-mobile-active' : ''}`}>
          <ResponsePanel error={session.error} pending={session.pending} response={session.response} />
        </div>
      </main>

      <AuthDialog
        onChange={session.setManualToken}
        onClose={() => setAuthOpen(false)}
        open={authOpen}
        token={session.manualToken}
      />
    </div>
  )
}
