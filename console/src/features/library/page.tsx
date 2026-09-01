import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import type { LibraryView } from '@generated/openapi/models'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { DataTable, type DataColumn } from '@/components/composed/data-table/data-table'
import { PageHeader } from '@/components/composed/page-header/page-header'
import { QueryState } from '@/components/composed/query-state/query-state'
import type { CatalogPageProps } from '@/features/page-registry'

import { FeatureWorkspace } from './feature-workspace'
import {
  createLibrary,
  fetchLibraries,
  fetchStudioConfig,
  persistLibrary,
  removeLibrary,
  type CreateLibraryInput,
} from './commands'

type Library = NonNullable<LibraryView>

const columns: DataColumn<Library>[] = [
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
  const [editing, setEditing] = useState<Library>()
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
          {selected ? <LibraryInspector library={selected} onDelete={() => {
            if (window.confirm(`确认删除 ${selected.displayName}？`)) {
              void removeLibrary(selected.id).then(() => queryClient.invalidateQueries({ queryKey: ['libraries'] }))
            }
          }} onEdit={() => setEditing(selected)} /> : <div className="empty-state">选择 Library 查看摘要</div>}
        </aside>
      </div>
      {selected && <FeatureWorkspace editable={selected.spec.contributorId === config.data?.editableContributorId} library={selected} />}
      {creating && (
        <CreateLibraryDialog
          contributorId={config.data?.editableContributorId ?? ''}
          onClose={() => setCreating(false)}
          onCreated={() => queryClient.invalidateQueries({ queryKey: ['libraries'] })}
        />
      )}
      {editing && <EditLibraryDialog
        initial={editing}
        onClose={() => setEditing(undefined)}
        onSaved={() => queryClient.invalidateQueries({ queryKey: ['libraries'] })}
      />}
    </div>
  )
}

function LibraryInspector({ library, onDelete, onEdit }: Readonly<{ library: Library; onDelete: () => void; onEdit: () => void }>) {
  return (
    <>
      <div className="inspector-heading">
        <span className="eyebrow">Library</span>
        <div className="inspector-title-row"><h2>{library.displayName}</h2><div className="inspector-actions"><CatalogAction elementKey="studio.library.edit" onClick={onEdit} /><CatalogAction elementKey="studio.library.delete" onClick={onDelete} variant="destructive" /></div></div>
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

function EditLibraryDialog({ initial, onClose, onSaved }: Readonly<{ initial: Library; onClose: () => void; onSaved: () => void }>) {
  const [draft, setDraft] = useState(() => ({
    displayName: initial.displayName,
    code: initial.code,
    packagePrefix: initial.spec.packagePrefix,
    scanPackage: initial.spec.scanPackage,
    description: initial.spec.description ?? '',
    status: initial.status,
    applicationSelectable: initial.spec.applicationSelectable ?? true,
  }))
  const mutation = useMutation({
    mutationFn: () => persistLibrary({
      id: initial.id,
      code: draft.code.trim(),
      displayName: draft.displayName.trim(),
      version: initial.version,
      status: draft.status,
      spec: {
        ...initial.spec,
        packagePrefix: draft.packagePrefix.trim(),
        scanPackage: draft.scanPackage.trim(),
        description: draft.description.trim() || null,
        applicationSelectable: draft.applicationSelectable,
      },
    }),
    onSuccess: () => { onSaved(); onClose() },
  })
  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section aria-labelledby="edit-library-title" aria-modal="true" className="dialog" role="dialog">
        <header><h2 id="edit-library-title">编辑 Library</h2><p>更新通用 Library 元数据。</p></header>
        <form onSubmit={(event) => { event.preventDefault(); mutation.mutate() }}>
          <label>名称<input required value={draft.displayName} onChange={(event) => setDraft({ ...draft, displayName: event.target.value })} /></label>
          <label>代码<input pattern="[a-z][a-z0-9-]*" required value={draft.code} onChange={(event) => setDraft({ ...draft, code: event.target.value })} /></label>
          <label>包前缀<input required value={draft.packagePrefix} onChange={(event) => setDraft({ ...draft, packagePrefix: event.target.value })} /></label>
          <label>扫描包<input required value={draft.scanPackage} onChange={(event) => setDraft({ ...draft, scanPackage: event.target.value })} /></label>
          <label>说明<textarea rows={3} value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></label>
          <label className="checkbox-field"><input checked={draft.status === 1} onChange={(event) => setDraft({ ...draft, status: event.target.checked ? 1 : 0 })} type="checkbox" />启用</label>
          <label className="checkbox-field"><input checked={draft.applicationSelectable} onChange={(event) => setDraft({ ...draft, applicationSelectable: event.target.checked })} type="checkbox" />允许应用选择</label>
          {mutation.error && <p className="form-error" role="alert">{mutation.error.message}</p>}
          <footer><CatalogAction elementKey="studio.library.edit.cancel" onClick={onClose} type="button" variant="ghost" /><CatalogAction disabled={mutation.isPending} elementKey="studio.library.edit.submit" type="submit" variant="primary" /></footer>
        </form>
      </section>
    </div>
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
