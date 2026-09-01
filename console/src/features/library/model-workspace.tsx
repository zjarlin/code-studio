import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type {
  FieldCommand,
  ModelCommand,
  ModelCommandModelType,
  QueryCommand,
  RelationCommand,
} from '@generated/openapi/models'
import { downloadModel, previewModel } from '@generated/openapi/client'
import { ModelCommandModelType as ModelTypes } from '@generated/openapi/models'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import { QueryState } from '@/components/composed/query-state/query-state'
import { Field, FieldError, FieldGroup, FieldLabel } from '@platform/ui/components/generated/shadcn/field'
import { Input } from '@platform/ui/components/generated/shadcn/input'
import { Textarea } from '@platform/ui/components/generated/shadcn/textarea'

import { fetchModels, persistModel, removeModel } from './commands'
import { requireApiData } from '@/lib/http'

import { QueryEditor } from './query-editor'

export function ModelWorkspace({ editable, feature }: Readonly<{
  editable: boolean
  feature: { id: number; name: string; featureCode: string }
}>) {
  const queryClient = useQueryClient()
  const queryKey = ['models', feature.id] as const
  const models = useQuery({ queryKey, queryFn: () => fetchModels(feature.id) })
  const [selectedId, setSelectedId] = useState<number>()
  const [creating, setCreating] = useState(false)
  const selected = creating ? undefined : models.data?.find((model) => model.id === selectedId) ?? models.data?.[0]
  const preview = useQuery({
    queryKey: ['model-preview', selected?.id],
    enabled: selected?.id != null,
    queryFn: async () => requireApiData(await previewModel({ id: selected!.id as number }), '模型预览响应缺少 data'),
  })
  const save = useMutation({
    mutationFn: persistModel,
    onSuccess: async () => {
      setCreating(false)
      await queryClient.invalidateQueries({ queryKey })
    },
  })
  const remove = useMutation({
    mutationFn: removeModel,
    onSuccess: async () => {
      setSelectedId(undefined)
      await queryClient.invalidateQueries({ queryKey })
    },
  })

  return (
    <section aria-label={`${feature.name}模型`} className="metadata-resource-workspace">
      <header className="constant-toolbar">
        <div><strong>模型</strong><span>{models.data?.length ?? 0} 个</span></div>
        <CatalogAction disabled={!editable} elementKey="studio.library.model.create" onClick={() => setCreating(true)} />
      </header>
      <QueryState error={models.error} pending={models.isPending}>
        <div className="resource-layout">
          <nav aria-label={`${feature.name}模型`} className="resource-index">
            {(models.data ?? []).map((model) => (
              <button
                aria-current={!creating && model.id === selected?.id ? 'page' : undefined}
                className="resource-index-item"
                key={model.id}
                onClick={() => { setCreating(false); if (model.id != null) setSelectedId(model.id) }}
                type="button"
              >
                <strong>{model.name}</strong><span>{model.modelCode}</span>
              </button>
            ))}
            {!models.data?.length && <p className="feature-index-empty">尚未创建模型</p>}
          </nav>
          {(selected || creating) ? (
            <ModelEditor
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
          ) : <div className="empty-state">新建模型后配置字段与查询</div>}
        </div>
        {selected?.id != null && <div className="preview-panel">
          <div className="resource-section-heading"><strong>生成结果</strong><button className="text-action" disabled={preview.isPending} onClick={() => downloadGeneratedModel(selected.id as number)} type="button">下载 ZIP</button></div>
          {preview.error && <p className="form-error">{preview.error.message}</p>}
          {preview.data?.files.map((file) => <details key={file.filePath}><summary>{file.filePath}</summary><pre>{file.content}</pre></details>)}
        </div>}
      </QueryState>
    </section>
  )
}

async function downloadGeneratedModel(id: number): Promise<void> {
  const blob = await downloadModel({ id })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `model-${id}.zip`
  anchor.click()
  URL.revokeObjectURL(url)
}

function ModelEditor({ editable, error, featureId, initial, onCancel, onDelete, onSave, pending }: Readonly<{
  editable: boolean
  error: Error | null
  featureId: number
  initial?: ModelCommand
  onCancel: () => void
  onDelete?: () => void
  onSave: (command: ModelCommand) => void
  pending: boolean
}>) {
  const [draft, setDraft] = useState<ModelCommand>(() => initial ? cloneModel(initial) : createModel(featureId))
  const [advancedError, setAdvancedError] = useState<string>()

  function updateField(index: number, patch: Partial<FieldCommand>) {
    setDraft({
      ...draft,
      fields: (draft.fields ?? []).map((field, fieldIndex) => fieldIndex === index ? { ...field, ...patch } : field),
    })
  }

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      const queries = parseJsonArray<QueryCommand>(draft.queries)
      const relations = parseJsonArray<RelationCommand>(draft.relations)
      setAdvancedError(undefined)
      onSave({
        ...draft,
        modelCode: draft.modelCode.trim(),
        name: draft.name.trim(),
        className: draft.className.trim(),
        tableName: draft.tableName.trim(),
        remark: draft.remark?.trim() || null,
        queries,
        relations,
      })
    } catch (cause) {
      setAdvancedError(cause instanceof Error ? cause.message : '高级配置 JSON 无效')
    }
  }

  return (
    <form className="metadata-form" onSubmit={submit}>
      <FieldGroup>
        <div className="metadata-form-grid">
          <Field data-disabled={!editable}><FieldLabel htmlFor="model-name">名称</FieldLabel><Input disabled={!editable} id="model-name" onChange={(event) => setDraft({ ...draft, name: event.target.value })} required value={draft.name} /></Field>
          <Field data-disabled={!editable}><FieldLabel htmlFor="model-code">代码</FieldLabel><Input disabled={!editable} id="model-code" onChange={(event) => setDraft({ ...draft, modelCode: event.target.value })} pattern="[a-z][A-Za-z0-9_.-]*" required value={draft.modelCode} /></Field>
          <Field data-disabled={!editable}><FieldLabel htmlFor="model-class">类名</FieldLabel><Input disabled={!editable} id="model-class" onChange={(event) => setDraft({ ...draft, className: event.target.value })} required value={draft.className} /></Field>
          <Field data-disabled={!editable}><FieldLabel htmlFor="model-table">表名</FieldLabel><Input disabled={!editable} id="model-table" onChange={(event) => setDraft({ ...draft, tableName: event.target.value })} required value={draft.tableName} /></Field>
        </div>
        <Field data-disabled={!editable}><FieldLabel htmlFor="model-type">模型类型</FieldLabel><select disabled={!editable} id="model-type" onChange={(event) => setDraft({ ...draft, modelType: event.target.value as ModelCommandModelType })} value={draft.modelType ?? ModelTypes.ENTITY}>{Object.values(ModelTypes).map((type) => <option key={type} value={type}>{type}</option>)}</select></Field>
        <Field data-disabled={!editable}><FieldLabel htmlFor="model-remark">说明</FieldLabel><Textarea disabled={!editable} id="model-remark" onChange={(event) => setDraft({ ...draft, remark: event.target.value })} value={draft.remark ?? ''} /></Field>
      </FieldGroup>
      <div className="resource-section-heading"><strong>字段</strong>{editable && <CatalogAction elementKey="studio.library.model.field.create" onClick={() => setDraft({ ...draft, fields: [...(draft.fields ?? []), createField((draft.fields ?? []).length)] })} />}</div>
      <div className="resource-table" role="table" aria-label="模型字段">
        <div className="resource-row resource-row-head" role="row"><span>代码</span><span>名称</span><span>Kotlin 类型</span><span>数据库列</span><span>必填</span><span>操作</span></div>
        {(draft.fields ?? []).map((field, index) => (
          <div className="resource-row" key={field.id ?? index} role="row">
            <Input aria-label="字段代码" disabled={!editable} onChange={(event) => updateField(index, { fieldCode: event.target.value })} required value={field.fieldCode} />
            <Input aria-label="字段名称" disabled={!editable} onChange={(event) => updateField(index, { label: event.target.value })} required value={field.label} />
            <Input aria-label="字段类型" disabled={!editable} onChange={(event) => updateField(index, { kotlinType: event.target.value })} required value={field.kotlinType} />
            <Input aria-label="数据库列" disabled={!editable} onChange={(event) => updateField(index, { dbColumn: event.target.value })} required value={field.dbColumn} />
            <input aria-label="字段必填" checked={field.required ?? false} disabled={!editable} onChange={(event) => updateField(index, { required: event.target.checked })} type="checkbox" />
            {editable && <CatalogIconAction elementKey="studio.library.model.field.delete" onClick={() => setDraft({ ...draft, fields: (draft.fields ?? []).filter((_, fieldIndex) => fieldIndex !== index) })} variant="destructive" />}
          </div>
        ))}
        {!(draft.fields ?? []).length && <div className="resource-table-empty">尚未配置字段</div>}
      </div>
      <QueryEditor editable={editable} fields={draft.fields ?? []} onChange={(queries) => setDraft({ ...draft, queries })} queries={parseJsonArray<QueryCommand>(draft.queries)} />
      <Field data-disabled={!editable}><FieldLabel htmlFor="model-relations">关联定义 JSON</FieldLabel><Textarea disabled={!editable} id="model-relations" onChange={(event) => setDraft({ ...draft, relations: parseJsonValue(event.target.value) as RelationCommand[] })} value={formatJson(draft.relations)} /></Field>
      {(advancedError || error) && <FieldError>{advancedError ?? error?.message}</FieldError>}
      {editable && <footer className="metadata-form-actions">
        {!initial && <CatalogAction elementKey="studio.library.model.cancel" onClick={onCancel} variant="ghost" />}
        {onDelete && <CatalogAction disabled={pending} elementKey="studio.library.model.delete" onClick={onDelete} variant="destructive" />}
        <CatalogAction disabled={pending} elementKey="studio.library.model.save" type="submit" variant="primary" />
      </footer>}
    </form>
  )
}

function createModel(featureId: number): ModelCommand {
  return {
    featureId,
    modelCode: 'newModel',
    name: '新模型',
    className: 'NewModel',
    tableName: 'new_model',
    modelType: ModelTypes.ENTITY,
    status: 1,
    version: 1,
    fields: [createField(0)],
    queries: [],
    relations: [],
  }
}

function createField(index: number): FieldCommand {
  return {
    orderNo: index + 1,
    fieldCode: `field${index + 1}`,
    label: `字段 ${index + 1}`,
    kotlinType: 'kotlin.String',
    dbColumn: `field_${index + 1}`,
    required: false,
    createWritable: true,
    updateWritable: true,
    listVisible: true,
    formVisible: true,
    key: false,
  }
}

function cloneModel(model: ModelCommand): ModelCommand {
  return structuredClone(model)
}

function parseJsonArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  if (typeof value !== 'string' || !value.trim()) return []
  const parsed: unknown = JSON.parse(value)
  if (!Array.isArray(parsed)) throw new Error('查询或关联定义必须是数组 JSON')
  return parsed as T[]
}

function parseJsonValue(value: string): unknown {
  try { return value.trim() ? JSON.parse(value) : [] } catch { return value }
}

function formatJson(value: unknown): string {
  return typeof value === 'string' ? value : JSON.stringify(value ?? [], null, 2)
}
