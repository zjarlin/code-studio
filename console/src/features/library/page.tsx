import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import type { LibraryView } from '@generated/openapi/models'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { DataTable, type DataColumn } from '@/components/composed/data-table/data-table'
import { PageHeader } from '@/components/composed/page-header/page-header'
import { QueryState } from '@/components/composed/query-state/query-state'
import type { CatalogPageProps } from '@/features/page-registry'

import {
  createLibrary,
  fetchLibraries,
  fetchStudioConfig,
  type CreateLibraryInput,
} from './commands'

const columns: DataColumn<LibraryView>[] = [
  { key: 'displayName', header: 'Library' },
  { key: 'code', header: '代码' },
  { key: 'version', header: '版本', width: '72px' },
  {
    key: 'status',
    header: '状态',
    width: '84px',
    cell: (value) => <span className={`status-dot ${value === 1 ? 'is-active' : ''}`}>{value === 1 ? '已启用' : '已停用'}</span>,
  },
]

export default function LibraryPage({ route }: CatalogPageProps) {
  const queryClient = useQueryClient()
  const libraries = useQuery({ queryKey: ['libraries'], queryFn: fetchLibraries })
  const config = useQuery({ queryKey: ['studio-config'], queryFn: fetchStudioConfig })
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState<string>()
  const [creating, setCreating] = useState(false)
  const visibleLibraries = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase()
    if (!keyword) return libraries.data ?? []
    return (libraries.data ?? []).filter((library) =>
      `${library.displayName} ${library.code} ${library.spec.description ?? ''}`.toLocaleLowerCase().includes(keyword),
    )
  }, [libraries.data, query])
  const selected = visibleLibraries.find((library) => String(library.id) === selectedId) ?? visibleLibraries[0]

  return (
    <div className="page-frame">
      <PageHeader
        actions={(
          <>
            <CatalogAction elementKey="studio.library.refresh" onClick={() => libraries.refetch()} />
            <CatalogAction elementKey="studio.library.create" onClick={() => setCreating(true)} variant="primary" />
          </>
        )}
        route={route}
      />
      <div className="toolbar">
        <input
          aria-label="搜索 Library"
          onChange={(event) => setQuery(event.target.value)}
          placeholder="搜索名称、代码或说明"
          type="search"
          value={query}
        />
        <span>{visibleLibraries.length} 项</span>
      </div>
      <div className="workspace-grid">
        <section className="workspace-main" aria-label="Library 目录">
          <QueryState error={libraries.error} pending={libraries.isPending}>
            <DataTable
              columns={columns}
              data={visibleLibraries}
              emptyText="没有匹配的 Library"
              getRowId={(library) => String(library.id)}
              onRowClick={(library) => setSelectedId(String(library.id))}
              selectedRowId={selected ? String(selected.id) : undefined}
            />
          </QueryState>
        </section>
        <aside className="inspector" aria-label="Library 摘要">
          {selected ? <LibraryInspector library={selected} /> : <div className="empty-state">选择 Library 查看摘要</div>}
        </aside>
      </div>
      {creating && (
        <CreateLibraryDialog
          contributorId={config.data?.editableContributorId ?? ''}
          onClose={() => setCreating(false)}
          onCreated={() => queryClient.invalidateQueries({ queryKey: ['libraries'] })}
        />
      )}
    </div>
  )
}

function LibraryInspector({ library }: Readonly<{ library: LibraryView }>) {
  return (
    <>
      <div className="inspector-heading">
        <span className="eyebrow">Library</span>
        <h2>{library.displayName}</h2>
        <p>{library.spec.description || '未填写说明'}</p>
      </div>
      <dl className="definition-list">
        <div><dt>代码</dt><dd>{library.code}</dd></div>
        <div><dt>类型</dt><dd>{library.spec.kind}</dd></div>
        <div><dt>贡献者</dt><dd>{library.spec.contributorId}</dd></div>
        <div><dt>包前缀</dt><dd>{library.spec.packagePrefix}</dd></div>
        <div><dt>扫描包</dt><dd>{library.spec.scanPackage}</dd></div>
      </dl>
    </>
  )
}

function CreateLibraryDialog({ contributorId, onClose, onCreated }: Readonly<{
  contributorId: string
  onClose: () => void
  onCreated: () => void
}>) {
  const [draft, setDraft] = useState<CreateLibraryInput>({
    code: '',
    displayName: '',
    packagePrefix: 'com.example.application',
    contributorId,
  })
  const mutation = useMutation({
    mutationFn: createLibrary,
    onSuccess: () => {
      onCreated()
      onClose()
    },
  })

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section aria-labelledby="create-library-title" aria-modal="true" className="dialog" role="dialog">
        <header>
          <h2 id="create-library-title">新建库</h2>
          <p>建立通用 Library 元数据边界。</p>
        </header>
        <form onSubmit={(event) => {
          event.preventDefault()
          mutation.mutate({ ...draft, contributorId })
        }}>
          <label>名称<input required value={draft.displayName} onChange={(event) => setDraft({ ...draft, displayName: event.target.value })} /></label>
          <label>代码<input pattern="[a-z][a-z0-9-]*" required value={draft.code} onChange={(event) => setDraft({ ...draft, code: event.target.value })} /></label>
          <label>包前缀<input required value={draft.packagePrefix} onChange={(event) => setDraft({ ...draft, packagePrefix: event.target.value })} /></label>
          {mutation.error && <p className="form-error" role="alert">{mutation.error.message}</p>}
          <footer>
            <CatalogAction elementKey="studio.library.create.cancel" onClick={onClose} type="button" variant="ghost" />
            <CatalogAction
              disabled={!contributorId || mutation.isPending}
              elementKey="studio.library.create.submit"
              type="submit"
              variant="primary"
            />
          </footer>
        </form>
      </section>
    </div>
  )
}
