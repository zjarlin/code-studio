import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type {
  DtoCommand,
  DtoCommandKind,
  DtoCommandSelectionMode,
  DtoCommandVisibility,
  DtoFieldCommand,
} from '@generated/openapi/models'
import { downloadDto, previewDto } from '@generated/openapi/client'
import {
  DtoCommandKind as DtoKinds,
  DtoCommandSelectionMode as SelectionModes,
  DtoCommandVisibility as VisibilityModes,
} from '@generated/openapi/models'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import { QueryState } from '@/components/composed/query-state/query-state'
import { Field, FieldError, FieldGroup, FieldLabel } from '@platform/ui/components/generated/shadcn/field'
import { Input } from '@platform/ui/components/generated/shadcn/input'
import { Textarea } from '@platform/ui/components/generated/shadcn/textarea'

import { fetchDtos, persistDto, removeDto } from './commands'
import { requireApiData } from '@/lib/http'

export function DtoWorkspace({ editable, feature }: Readonly<{
  editable: boolean
  feature: { id: number; name: string; featureCode: string }
}>) {
  const queryClient = useQueryClient()
  const queryKey = ['dtos', feature.id] as const
  const dtos = useQuery({ queryKey, queryFn: () => fetchDtos(feature.id) })
  const [selectedId, setSelectedId] = useState<number>()
  const [creating, setCreating] = useState(false)
  const selected = creating ? undefined : dtos.data?.find((dto) => dto.id === selectedId) ?? dtos.data?.[0]
  const preview = useQuery({
    queryKey: ['dto-preview', selected?.id],
    enabled: selected?.id != null,
    queryFn: async () => requireApiData(await previewDto({ id: selected!.id as number }), 'DTO 预览响应缺少 data'),
  })
  const save = useMutation({
    mutationFn: persistDto,
    onSuccess: async () => {
      setCreating(false)
      await queryClient.invalidateQueries({ queryKey })
    },
  })
  const remove = useMutation({
    mutationFn: removeDto,
    onSuccess: async () => {
      setSelectedId(undefined)
      await queryClient.invalidateQueries({ queryKey })
    },
  })

  return (
    <section aria-label={`${feature.name} DTO`} className="metadata-resource-workspace">
      <header className="constant-toolbar">
        <div><strong>DTO</strong><span>{dtos.data?.length ?? 0} 个</span></div>
        <CatalogAction disabled={!editable} elementKey="studio.library.dto.create" onClick={() => setCreating(true)} />
      </header>
      <QueryState error={dtos.error} pending={dtos.isPending}>
        <div className="resource-layout">
          <nav aria-label={`${feature.name} DTO`} className="resource-index">
            {(dtos.data ?? []).map((dto) => (
              <button
                aria-current={!creating && dto.id === selected?.id ? 'page' : undefined}
                className="resource-index-item"
                key={dto.id}
                onClick={() => { setCreating(false); if (dto.id != null) setSelectedId(dto.id) }}
                type="button"
              >
                <strong>{dto.name}</strong><span>{dto.dtoCode}</span>
              </button>
            ))}
            {!dtos.data?.length && <p className="feature-index-empty">尚未创建 DTO</p>}
          </nav>
          {(selected || creating) ? (
            <DtoEditor
              editable={editable}
              error={save.error ?? remove.error}
              featureId={feature.id}
              initial={selected}
              key={selected?.id ?? (creating ? 'new' : 'empty')}
              onCancel={() => setCreating(false)}
              onDelete={selected?.id != null ? () => remove.mutate(selected.id as number) : undefined}
              onSave={(command) => save.mutate(command)}
              pending={save.isPending || remove.isPending}
            />
          ) : <div className="empty-state">新建 DTO 后配置输出字段</div>}
        </div>
        {selected?.id != null && <div className="preview-panel">
          <div className="resource-section-heading"><strong>生成结果</strong><button className="text-action" disabled={preview.isPending} onClick={() => downloadGeneratedDto(selected.id as number)} type="button">下载 ZIP</button></div>
          {preview.error && <p className="form-error">{preview.error.message}</p>}
          {preview.data?.files.map((file) => <details key={file.filePath}><summary>{file.filePath}</summary><pre>{file.content}</pre></details>)}
        </div>}
      </QueryState>
    </section>
  )
}

async function downloadGeneratedDto(id: number): Promise<void> {
  const blob = await downloadDto({ id })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `dto-${id}.zip`
  anchor.click()
  URL.revokeObjectURL(url)
}

function DtoEditor({ editable, error, featureId, initial, onCancel, onDelete, onSave, pending }: Readonly<{
  editable: boolean
  error: Error | null
  featureId: number
  initial?: DtoCommand
  onCancel: () => void
  onDelete?: () => void
  onSave: (command: DtoCommand) => void
  pending: boolean
}>) {
  const [draft, setDraft] = useState<DtoCommand>(() => initial ? structuredClone(initial) : createDto(featureId))
  const [advancedError, setAdvancedError] = useState<string>()

  function updateField(index: number, patch: Partial<DtoFieldCommand>) {
    setDraft({ ...draft, fields: (draft.fields ?? []).map((field, fieldIndex) => fieldIndex === index ? { ...field, ...patch } : field) })
  }

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      const excludedPaths = parseJsonArray<string>(draft.excludedPaths)
      setAdvancedError(undefined)
      onSave({
        ...draft,
        dtoCode: draft.dtoCode.trim(),
        name: draft.name.trim(),
        className: draft.className.trim(),
        packageName: draft.packageName?.trim(),
        sourceModelCode: draft.sourceModelCode?.trim() || null,
        description: draft.description?.trim() || null,
        excludedPaths,
      })
    } catch (cause) {
      setAdvancedError(cause instanceof Error ? cause.message : '排除路径 JSON 无效')
    }
  }

  return (
    <form className="metadata-form" onSubmit={submit}>
      <FieldGroup>
        <div className="metadata-form-grid">
          <Field data-disabled={!editable}><FieldLabel htmlFor="dto-name">名称</FieldLabel><Input disabled={!editable} id="dto-name" onChange={(event) => setDraft({ ...draft, name: event.target.value })} required value={draft.name} /></Field>
          <Field data-disabled={!editable}><FieldLabel htmlFor="dto-code">代码</FieldLabel><Input disabled={!editable} id="dto-code" onChange={(event) => setDraft({ ...draft, dtoCode: event.target.value })} pattern="[a-z][A-Za-z0-9_.-]*" required value={draft.dtoCode} /></Field>
          <Field data-disabled={!editable}><FieldLabel htmlFor="dto-class">类名</FieldLabel><Input disabled={!editable} id="dto-class" onChange={(event) => setDraft({ ...draft, className: event.target.value })} required value={draft.className} /></Field>
          <Field data-disabled={!editable}><FieldLabel htmlFor="dto-package">包名</FieldLabel><Input disabled={!editable} id="dto-package" onChange={(event) => setDraft({ ...draft, packageName: event.target.value })} required value={draft.packageName ?? ''} /></Field>
        </div>
        <div className="metadata-form-grid">
          <Field data-disabled={!editable}><FieldLabel htmlFor="dto-kind">用途</FieldLabel><select disabled={!editable} id="dto-kind" onChange={(event) => setDraft({ ...draft, kind: event.target.value as DtoCommandKind })} value={draft.kind}>{Object.values(DtoKinds).map((kind) => <option key={kind} value={kind}>{kind}</option>)}</select></Field>
          <Field data-disabled={!editable}><FieldLabel htmlFor="dto-visibility">可见性</FieldLabel><select disabled={!editable} id="dto-visibility" onChange={(event) => setDraft({ ...draft, visibility: event.target.value as DtoCommandVisibility })} value={draft.visibility ?? VisibilityModes.PUBLIC}>{Object.values(VisibilityModes).map((mode) => <option key={mode} value={mode}>{mode}</option>)}</select></Field>
          <Field data-disabled={!editable}><FieldLabel htmlFor="dto-selection">字段策略</FieldLabel><select disabled={!editable} id="dto-selection" onChange={(event) => setDraft({ ...draft, selectionMode: event.target.value as DtoCommandSelectionMode })} value={draft.selectionMode ?? SelectionModes.EXPLICIT}>{Object.values(SelectionModes).map((mode) => <option key={mode} value={mode}>{mode}</option>)}</select></Field>
          <Field data-disabled={!editable}><FieldLabel htmlFor="dto-source">来源模型代码</FieldLabel><Input disabled={!editable} id="dto-source" onChange={(event) => setDraft({ ...draft, sourceModelCode: event.target.value || null })} value={draft.sourceModelCode ?? ''} /></Field>
        </div>
        <Field data-disabled={!editable}><FieldLabel htmlFor="dto-description">说明</FieldLabel><Textarea disabled={!editable} id="dto-description" onChange={(event) => setDraft({ ...draft, description: event.target.value })} value={draft.description ?? ''} /></Field>
      </FieldGroup>
      <div className="resource-section-heading"><strong>字段</strong>{editable && <CatalogAction elementKey="studio.library.dto.field.create" onClick={() => setDraft({ ...draft, fields: [...(draft.fields ?? []), createField((draft.fields ?? []).length)] })} />}</div>
      <div className="resource-table" role="table" aria-label="DTO 字段">
        <div className="resource-row resource-row-head" role="row"><span>名称</span><span>来源路径</span><span>Kotlin 类型</span><span>说明</span><span>操作</span></div>
        {(draft.fields ?? []).map((field, index) => (
          <div className="resource-row dto-resource-row" key={`${field.name}-${index}`} role="row">
            <Input aria-label="DTO 字段名" disabled={!editable} onChange={(event) => updateField(index, { name: event.target.value })} required value={field.name} />
            <Input aria-label="DTO 来源路径" disabled={!editable} onChange={(event) => updateField(index, { sourcePath: event.target.value })} value={field.sourcePath ?? ''} />
            <Input aria-label="DTO Kotlin 类型" disabled={!editable} onChange={(event) => updateField(index, { kotlinType: { qualifiedName: event.target.value } })} value={field.kotlinType?.qualifiedName ?? ''} />
            <Input aria-label="DTO 字段说明" disabled={!editable} onChange={(event) => updateField(index, { description: event.target.value })} value={field.description ?? ''} />
            {editable && <CatalogIconAction elementKey="studio.library.dto.field.delete" onClick={() => setDraft({ ...draft, fields: (draft.fields ?? []).filter((_, fieldIndex) => fieldIndex !== index) })} variant="destructive" />}
          </div>
        ))}
        {!(draft.fields ?? []).length && <div className="resource-table-empty">尚未配置字段</div>}
      </div>
      <Field data-disabled={!editable}><FieldLabel htmlFor="dto-excluded">排除路径 JSON</FieldLabel><Textarea disabled={!editable} id="dto-excluded" onChange={(event) => setDraft({ ...draft, excludedPaths: parseJsonValue(event.target.value) as string[] })} value={formatJson(draft.excludedPaths)} /></Field>
      {(advancedError || error) && <FieldError>{advancedError ?? error?.message}</FieldError>}
      {editable && <footer className="metadata-form-actions">
        {!initial && <CatalogAction elementKey="studio.library.dto.cancel" onClick={onCancel} variant="ghost" />}
        {onDelete && <CatalogAction disabled={pending} elementKey="studio.library.dto.delete" onClick={onDelete} variant="destructive" />}
        <CatalogAction disabled={pending} elementKey="studio.library.dto.save" type="submit" variant="primary" />
      </footer>}
    </form>
  )
}

function createDto(featureId: number): DtoCommand {
  return {
    featureId,
    dtoCode: 'newDto',
    name: '新 DTO',
    packageName: '',
    className: 'NewDto',
    kind: DtoKinds.OUTPUT,
    visibility: VisibilityModes.PUBLIC,
    sourceModelCode: null,
    selectionMode: SelectionModes.EXPLICIT,
    excludedPaths: [],
    fields: [createField(0)],
    annotations: [],
    superTypes: [],
    status: 1,
    version: 1,
    description: '',
  }
}

function createField(index: number): DtoFieldCommand {
  return { name: `field${index + 1}`, sourcePath: `field${index + 1}`, description: '', nullability: 'INHERIT', kotlinType: { qualifiedName: 'kotlin.String' }, validations: [], annotations: [] }
}

function parseJsonArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  if (typeof value !== 'string' || !value.trim()) return []
  const parsed: unknown = JSON.parse(value)
  if (!Array.isArray(parsed)) throw new Error('排除路径必须是数组 JSON')
  return parsed as T[]
}

function parseJsonValue(value: string): unknown {
  try { return value.trim() ? JSON.parse(value) : [] } catch { return value }
}

function formatJson(value: unknown): string {
  return typeof value === 'string' ? value : JSON.stringify(value ?? [], null, 2)
}
