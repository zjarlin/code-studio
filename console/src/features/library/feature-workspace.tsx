import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { LibraryFeatureCommand, LibraryFeatureView, LibraryView } from '@generated/openapi/models'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { QueryState } from '@/components/composed/query-state/query-state'
import { Field, FieldError, FieldGroup, FieldLabel } from '@platform/ui/components/generated/shadcn/field'
import { Input } from '@platform/ui/components/generated/shadcn/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@platform/ui/components/generated/shadcn/tabs'
import { Textarea } from '@platform/ui/components/generated/shadcn/textarea'

import {
  fetchLibraryFeatures,
  persistLibraryFeature,
  removeLibraryFeature,
} from './commands'
import { ConstantWorkspace } from './constant-workspace'
import { ConventionWorkspace } from './convention-workspace'
import { DtoWorkspace } from './dto-workspace'
import { ModelWorkspace } from './model-workspace'
import { LibraryPreviewWorkspace } from './library-preview-workspace'

type Library = NonNullable<LibraryView>
type LibraryFeature = NonNullable<LibraryFeatureView>

export function FeatureWorkspace({ editable, library }: Readonly<{
  editable: boolean
  library: Library
}>) {
  const queryClient = useQueryClient()
  const queryKey = ['library-features', library.id] as const
  const features = useQuery({
    queryKey,
    queryFn: () => fetchLibraryFeatures(library.id),
  })
  const [selectedId, setSelectedId] = useState<number>()
  const [creating, setCreating] = useState(false)
  const selected: LibraryFeature | undefined = creating
    ? undefined
    : features.data?.find((feature) => feature.id === selectedId) ?? features.data?.[0]
  const save = useMutation({
    mutationFn: persistLibraryFeature,
    onSuccess: async (saved) => {
      setCreating(false)
      setSelectedId(saved.id)
      await queryClient.invalidateQueries({ queryKey })
    },
  })
  const remove = useMutation({
    mutationFn: removeLibraryFeature,
    onSuccess: async () => {
      setSelectedId(undefined)
      await queryClient.invalidateQueries({ queryKey })
    },
  })

  return (
    <section className="feature-workspace" aria-label={`${library.displayName}功能目录`}>
      <header className="workspace-section-header">
        <div>
          <span className="eyebrow">功能目录</span>
          <h2>{library.displayName}</h2>
        </div>
        <CatalogAction
          disabled={!editable}
          elementKey="studio.library.feature.create"
          onClick={() => setCreating(true)}
        />
      </header>
      <QueryState error={features.error} pending={features.isPending}>
        <div className="feature-layout">
          <nav aria-label="功能目录" className="feature-index">
            {(features.data ?? []).map((feature) => (
              <button
                aria-current={!creating && feature.id === selected?.id ? 'page' : undefined}
                className="feature-index-item"
                key={feature.id}
                onClick={() => {
                  setCreating(false)
                  setSelectedId(feature.id)
                }}
                type="button"
              >
                <strong>{feature.name}</strong>
                <span>{feature.featureCode}</span>
              </button>
            ))}
            {!features.data?.length && <p className="feature-index-empty">尚未创建功能目录</p>}
          </nav>
          <div className="feature-editor">
            <Tabs defaultValue="definition" key={selected?.id ?? (creating ? 'new' : 'empty')}>
              <TabsList aria-label="功能工作区" variant="line">
                <TabsTrigger value="definition">定义</TabsTrigger>
                <TabsTrigger disabled={!selected} value="models">模型</TabsTrigger>
                <TabsTrigger disabled={!selected} value="dtos">DTO</TabsTrigger>
                <TabsTrigger disabled={!selected} value="conventions">约定文件</TabsTrigger>
                <TabsTrigger disabled={!selected} value="constants">常量</TabsTrigger>
                <TabsTrigger value="preview">预览</TabsTrigger>
              </TabsList>
              <TabsContent value="definition">
                {(selected || creating) ? (
                  <FeatureEditor
                    editable={editable}
                    error={save.error ?? remove.error}
                    features={features.data ?? []}
                    initial={selected}
                    libraryId={library.id}
                    onCancel={() => setCreating(false)}
                    onDelete={selected ? () => remove.mutate(selected.id) : undefined}
                    onSave={(command) => save.mutate(command)}
                    pending={save.isPending || remove.isPending}
                  />
                ) : <div className="empty-state">新建功能目录后配置元数据资源</div>}
              </TabsContent>
              <TabsContent value="models">
                {selected && <ModelWorkspace editable={editable} feature={selected} />}
              </TabsContent>
              <TabsContent value="dtos">
                {selected && <DtoWorkspace editable={editable} feature={selected} />}
              </TabsContent>
              <TabsContent value="conventions">
                {selected && <ConventionWorkspace editable={editable} feature={selected} />}
              </TabsContent>
              <TabsContent value="constants">
                {selected && <ConstantWorkspace editable={editable} feature={selected} />}
              </TabsContent>
              <TabsContent value="preview">
                <LibraryPreviewWorkspace libraryId={library.id} featureId={selected?.id} featureName={selected?.name} />
              </TabsContent>
            </Tabs>
          </div>
        </div>
      </QueryState>
    </section>
  )
}

function FeatureEditor({ editable, error, features, initial, libraryId, onCancel, onDelete, onSave, pending }: Readonly<{
  editable: boolean
  error: Error | null
  features: LibraryFeature[]
  initial?: LibraryFeature
  libraryId: number
  onCancel: () => void
  onDelete?: () => void
  onSave: (command: LibraryFeatureCommand) => void
  pending: boolean
}>) {
  const [draft, setDraft] = useState<LibraryFeatureCommand>(() => initial ?? {
    libraryId,
    parentId: null,
    featureCode: `feature${features.length + 1}`,
    name: `功能 ${features.length + 1}`,
    description: null,
  })

  return (
    <form className="metadata-form" onSubmit={(event) => {
      event.preventDefault()
      onSave({
        ...draft,
        featureCode: draft.featureCode.trim(),
        name: draft.name.trim(),
        description: draft.description?.trim() || null,
      })
    }}>
      <FieldGroup>
        <div className="metadata-form-grid">
          <Field data-disabled={!editable}>
            <FieldLabel htmlFor="feature-name">名称</FieldLabel>
            <Input
              disabled={!editable}
              id="feature-name"
              onChange={(event) => setDraft({ ...draft, name: event.target.value })}
              required
              value={draft.name}
            />
          </Field>
          <Field data-disabled={!editable}>
            <FieldLabel htmlFor="feature-code">代码</FieldLabel>
            <Input
              disabled={!editable}
              id="feature-code"
              onChange={(event) => setDraft({ ...draft, featureCode: event.target.value })}
              pattern="[a-z][A-Za-z0-9_]*(\.[a-z][A-Za-z0-9_]*)*"
              required
              value={draft.featureCode}
            />
          </Field>
        </div>
        <Field data-disabled={!editable}>
          <FieldLabel htmlFor="feature-parent">父级</FieldLabel>
          <select
            disabled={!editable}
            id="feature-parent"
            onChange={(event) => setDraft({ ...draft, parentId: event.target.value ? Number(event.target.value) : null })}
            value={draft.parentId ?? ''}
          >
            <option value="">无</option>
            {features.filter((feature) => feature.id !== draft.id).map((feature) => (
              <option key={feature.id} value={feature.id}>{feature.name}</option>
            ))}
          </select>
        </Field>
        <Field data-disabled={!editable}>
          <FieldLabel htmlFor="feature-description">说明</FieldLabel>
          <Textarea
            disabled={!editable}
            id="feature-description"
            onChange={(event) => setDraft({ ...draft, description: event.target.value })}
            value={draft.description ?? ''}
          />
        </Field>
        <FieldError>{error?.message}</FieldError>
      </FieldGroup>
      {editable && (
        <footer className="metadata-form-actions">
          {!initial && <CatalogAction elementKey="studio.library.feature.cancel" onClick={onCancel} variant="ghost" />}
          {onDelete && (
            <CatalogAction
              disabled={pending}
              elementKey="studio.library.feature.delete"
              onClick={onDelete}
              variant="destructive"
            />
          )}
          <CatalogAction
            disabled={pending || !draft.name.trim() || !draft.featureCode.trim()}
            elementKey="studio.library.feature.save"
            type="submit"
            variant="primary"
          />
        </footer>
      )}
    </form>
  )
}
