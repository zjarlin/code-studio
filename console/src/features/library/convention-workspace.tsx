import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { ConventionFileCommand, ConventionFileCommandKind, ConventionFileView } from '@generated/openapi/models'
import { ConventionFileCommandKind as ConventionKinds } from '@generated/openapi/models'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import { QueryState } from '@/components/composed/query-state/query-state'
import { Field, FieldError, FieldGroup, FieldLabel } from '@platform/ui/components/generated/shadcn/field'
import { Input } from '@platform/ui/components/generated/shadcn/input'
import { Textarea } from '@platform/ui/components/generated/shadcn/textarea'

import { fetchConventionFiles, persistConventionFile, removeConventionFile } from './commands'

type ConventionFile = NonNullable<ConventionFileView>

export function ConventionWorkspace({ editable, feature }: Readonly<{
  editable: boolean
  feature: { id: number; name: string; featureCode: string }
}>) {
  const queryClient = useQueryClient()
  const queryKey = ['convention-files', feature.id] as const
  const files = useQuery({ queryKey, queryFn: () => fetchConventionFiles(feature.id) })
  const [selectedId, setSelectedId] = useState<number>()
  const [creating, setCreating] = useState(false)
  const selected = creating ? undefined : files.data?.find((file) => file.id === selectedId) ?? files.data?.[0]
  const save = useMutation({
    mutationFn: persistConventionFile,
    onSuccess: async () => { setCreating(false); await queryClient.invalidateQueries({ queryKey }) },
  })
  const remove = useMutation({
    mutationFn: removeConventionFile,
    onSuccess: async () => { setSelectedId(undefined); await queryClient.invalidateQueries({ queryKey }) },
  })

  return (
    <section aria-label={`${feature.name}约定文件`} className="metadata-resource-workspace">
      <header className="constant-toolbar">
        <div><strong>约定文件</strong><span>{files.data?.length ?? 0} 个</span></div>
        <CatalogAction disabled={!editable} elementKey="studio.library.convention.create" onClick={() => setCreating(true)} />
      </header>
      <QueryState error={files.error} pending={files.isPending}>
        <div className="resource-layout">
          <nav aria-label={`${feature.name}约定文件`} className="resource-index">
            {(files.data ?? []).map((file) => (
              <button
                aria-current={!creating && file.id === selected?.id ? 'page' : undefined}
                className="resource-index-item"
                key={file.id}
                onClick={() => { setCreating(false); setSelectedId(file.id) }}
                type="button"
              >
                <strong>{file.name}</strong><span>{file.kind} · {file.fileCode}</span>
              </button>
            ))}
            {!files.data?.length && <p className="feature-index-empty">尚未创建约定文件</p>}
          </nav>
          {(selected || creating) ? (
            <ConventionEditor
              editable={editable}
              error={save.error ?? remove.error}
              featureId={feature.id}
              initial={selected}
              key={selected?.id ?? (creating ? 'new' : 'empty')}
              onCancel={() => setCreating(false)}
              onDelete={selected ? () => remove.mutate(selected.id) : undefined}
              onSave={(command) => save.mutate(command)}
              pending={save.isPending || remove.isPending}
            />
          ) : <div className="empty-state">新建约定文件后配置 Service 或定时任务</div>}
        </div>
      </QueryState>
    </section>
  )
}

function ConventionEditor({ editable, error, featureId, initial, onCancel, onDelete, onSave, pending }: Readonly<{
  editable: boolean
  error: Error | null
  featureId: number
  initial?: ConventionFile
  onCancel: () => void
  onDelete?: () => void
  onSave: (command: ConventionFileCommand) => void
  pending: boolean
}>) {
  const [draft, setDraft] = useState<ConventionFileCommand>(() => initial ? {
    id: initial.id,
    featureId: initial.featureId,
    fileCode: initial.fileCode,
    name: initial.name,
    className: initial.className,
    kind: initial.kind,
    status: initial.status,
    description: initial.description,
  } : createConventionFile(featureId))

  return (
    <form className="metadata-form" onSubmit={(event) => {
      event.preventDefault()
      onSave({ ...draft, fileCode: draft.fileCode.trim(), name: draft.name.trim(), className: draft.className.trim(), description: draft.description?.trim() || null })
    }}>
      <FieldGroup>
        <div className="metadata-form-grid">
          <Field data-disabled={!editable}><FieldLabel htmlFor="convention-name">名称</FieldLabel><Input disabled={!editable} id="convention-name" onChange={(event) => setDraft({ ...draft, name: event.target.value })} required value={draft.name} /></Field>
          <Field data-disabled={!editable}><FieldLabel htmlFor="convention-code">文件编码</FieldLabel><Input disabled={!editable} id="convention-code" onChange={(event) => setDraft({ ...draft, fileCode: event.target.value })} pattern="[a-z][A-Za-z0-9_.-]*" required value={draft.fileCode} /></Field>
          <Field data-disabled={!editable}><FieldLabel htmlFor="convention-class">类名</FieldLabel><Input disabled={!editable} id="convention-class" onChange={(event) => setDraft({ ...draft, className: event.target.value })} required value={draft.className} /></Field>
          <Field data-disabled={!editable}><FieldLabel htmlFor="convention-kind">类型</FieldLabel><select disabled={!editable} id="convention-kind" onChange={(event) => setDraft({ ...draft, kind: event.target.value as ConventionFileCommandKind })} value={draft.kind}>{Object.values(ConventionKinds).map((kind) => <option key={kind} value={kind}>{kind === 'SCHEDULED_JOB' ? '定时任务' : 'Service'}</option>)}</select></Field>
        </div>
        <Field data-disabled={!editable}><FieldLabel htmlFor="convention-description">说明</FieldLabel><Textarea disabled={!editable} id="convention-description" onChange={(event) => setDraft({ ...draft, description: event.target.value })} value={draft.description ?? ''} /></Field>
      </FieldGroup>
      {(error) && <FieldError>{error.message}</FieldError>}
      {editable && <footer className="metadata-form-actions">
        {!initial && <CatalogAction elementKey="studio.library.convention.cancel" onClick={onCancel} variant="ghost" />}
        {onDelete && <CatalogAction disabled={pending} elementKey="studio.library.convention.delete" onClick={onDelete} variant="destructive" />}
        <CatalogAction disabled={pending} elementKey="studio.library.convention.save" type="submit" variant="primary" />
      </footer>}
    </form>
  )
}

function createConventionFile(featureId: number): ConventionFileCommand {
  return { featureId, fileCode: 'service', name: '新 Service', className: 'NewService', kind: ConventionKinds.SERVICE, status: 1, description: null }
}
