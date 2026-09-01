import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type {
  ConstantCommand,
  ConstantItemCommand,
  ConstantView,
  LibraryFeatureView,
} from '@generated/openapi/models'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import { QueryState } from '@/components/composed/query-state/query-state'
import { Field, FieldError, FieldGroup, FieldLabel } from '@platform/ui/components/generated/shadcn/field'
import { Input } from '@platform/ui/components/generated/shadcn/input'

import { fetchConstants, persistConstant, removeConstant } from './commands'

type LibraryFeature = NonNullable<LibraryFeatureView>
type ConstantGroup = NonNullable<ConstantView>

const CONSTANT_TYPES = ['BOOLEAN', 'INT', 'LONG', 'STRING'] as const

export function ConstantWorkspace({ editable, feature }: Readonly<{
  editable: boolean
  feature: LibraryFeature
}>) {
  const queryClient = useQueryClient()
  const queryKey = ['constants', feature.id] as const
  const constants = useQuery({
    queryKey,
    queryFn: () => fetchConstants(feature.id),
  })
  const [selectedId, setSelectedId] = useState<number>()
  const [creating, setCreating] = useState(false)
  const selected: ConstantGroup | undefined = creating
    ? undefined
    : constants.data?.find((group) => group.id === selectedId) ?? constants.data?.[0]
  const save = useMutation({
    mutationFn: persistConstant,
    onSuccess: async (saved) => {
      setCreating(false)
      setSelectedId(saved.id)
      await queryClient.invalidateQueries({ queryKey })
    },
  })
  const remove = useMutation({
    mutationFn: removeConstant,
    onSuccess: async () => {
      setSelectedId(undefined)
      await queryClient.invalidateQueries({ queryKey })
    },
  })

  return (
    <section aria-label={`${feature.name}常量`} className="constant-workspace">
      <div className="constant-toolbar">
        <div>
          <strong>常量</strong>
          <span>{constants.data?.length ?? 0} 组</span>
        </div>
        <CatalogAction
          disabled={!editable}
          elementKey="studio.library.constant.create"
          onClick={() => setCreating(true)}
        />
      </div>
      <QueryState error={constants.error} pending={constants.isPending}>
        <div className="constant-layout">
          <nav aria-label={`${feature.name}常量组`} className="constant-index">
            {(constants.data ?? []).map((group) => (
              <button
                aria-current={!creating && group.id === selected?.id ? 'page' : undefined}
                className="constant-index-item"
                key={group.id}
                onClick={() => {
                  setCreating(false)
                  setSelectedId(group.id)
                }}
                type="button"
              >
                <strong>{group.objectName}</strong>
                <span>{group.constants?.length ?? 0} 项</span>
              </button>
            ))}
            {!constants.data?.length && <p className="feature-index-empty">尚未创建常量组</p>}
          </nav>
          {(selected || creating) ? (
            <ConstantEditor
              editable={editable}
              error={save.error ?? remove.error}
                    feature={feature}
              groupCount={constants.data?.length ?? 0}
              initial={selected}
              key={selected?.id ?? (creating ? 'new' : 'empty')}
              onCancel={() => setCreating(false)}
              onDelete={selected ? () => {
                if (window.confirm(`确认删除 ${selected.objectName}？`)) {
                  remove.mutate(selected.id)
                }
              } : undefined}
              onSave={(command) => save.mutate(command)}
              pending={save.isPending || remove.isPending}
            />
          ) : <div className="empty-state">新建常量组后维护常量项</div>}
        </div>
      </QueryState>
    </section>
  )
}

function ConstantEditor({ editable, error, feature, groupCount, initial, onCancel, onDelete, onSave, pending }: Readonly<{
  editable: boolean
  error: Error | null
  feature: LibraryFeature
  groupCount: number
  initial?: ConstantGroup
  onCancel: () => void
  onDelete?: () => void
  onSave: (command: ConstantCommand) => void
  pending: boolean
}>) {
  const [draft, setDraft] = useState<ConstantCommand>(() => initial ? {
    id: initial.id,
    featureId: initial.featureId,
    groupCode: initial.groupCode,
    objectName: initial.objectName,
    description: initial.description,
    status: 1,
    constants: initial.constants ?? [],
  } : createConstantDraft(feature, groupCount))

  function updateItem(index: number, update: Partial<ConstantItemCommand>) {
    setDraft({
      ...draft,
      constants: draft.constants.map((item, itemIndex) => itemIndex === index ? { ...item, ...update } : item),
    })
  }

  return (
    <form className="constant-editor" onSubmit={(event) => {
      event.preventDefault()
      onSave({
        ...draft,
        groupCode: draft.groupCode.trim(),
        objectName: draft.objectName.trim(),
        description: draft.description.trim(),
        constants: draft.constants.map((item) => ({
          ...item,
          name: item.name.trim(),
          value: item.value.trim(),
          description: item.description.trim(),
        })),
      })
    }}>
      <FieldGroup>
        <div className="metadata-form-grid">
          <Field data-disabled={!editable}>
            <FieldLabel htmlFor="constant-object-name">对象名</FieldLabel>
            <Input
              disabled={!editable}
              id="constant-object-name"
              onChange={(event) => setDraft({ ...draft, objectName: event.target.value })}
              pattern="[A-Z][A-Za-z0-9_]*"
              required
              value={draft.objectName}
            />
          </Field>
          <Field data-disabled={!editable}>
            <FieldLabel htmlFor="constant-group-code">组代码</FieldLabel>
            <Input
              disabled={!editable}
              id="constant-group-code"
              onChange={(event) => setDraft({ ...draft, groupCode: event.target.value })}
              pattern="[a-z][A-Za-z0-9]*"
              required
              value={draft.groupCode}
            />
          </Field>
        </div>
        <Field data-disabled={!editable}>
          <FieldLabel htmlFor="constant-description">说明</FieldLabel>
          <Input
            disabled={!editable}
            id="constant-description"
            onChange={(event) => setDraft({ ...draft, description: event.target.value })}
            required
            value={draft.description}
          />
        </Field>
      </FieldGroup>
      <div className="constant-items-heading">
        <strong>常量项</strong>
        {editable && (
          <CatalogAction
            elementKey="studio.library.constant.item.create"
            onClick={() => setDraft({
              ...draft,
              constants: [...draft.constants, createConstantItem(draft.constants.length)],
            })}
          />
        )}
      </div>
      <div className="constant-table" role="table" aria-label="常量项">
        <div className="constant-row constant-row-head" role="row">
          <span role="columnheader">名称</span>
          <span role="columnheader">类型</span>
          <span role="columnheader">值</span>
          <span role="columnheader">注释</span>
          <span aria-label="操作" role="columnheader" />
        </div>
        {draft.constants.map((item, index) => (
          <div className="constant-row" key={item.id ?? index} role="row">
            <Input
              aria-label="常量名"
              disabled={!editable}
              onChange={(event) => updateItem(index, { name: event.target.value })}
              required
              value={item.name}
            />
            <select
              aria-label="常量类型"
              disabled={!editable}
              onChange={(event) => updateItem(index, { type: event.target.value })}
              value={item.type}
            >
              {CONSTANT_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
            </select>
            {item.type === 'BOOLEAN' ? (
              <select
                aria-label="常量值"
                disabled={!editable}
                onChange={(event) => updateItem(index, { value: event.target.value })}
                value={item.value}
              >
                <option value="true">true</option>
                <option value="false">false</option>
              </select>
            ) : (
              <Input
                aria-label="常量值"
                disabled={!editable}
                onChange={(event) => updateItem(index, { value: event.target.value })}
                required
                value={item.value}
              />
            )}
            <Input
              aria-label="常量注释"
              disabled={!editable}
              onChange={(event) => updateItem(index, { description: event.target.value })}
              required
              value={item.description}
            />
            {editable && (
              <CatalogIconAction
                elementKey="studio.library.constant.item.delete"
                onClick={() => setDraft({
                  ...draft,
                  constants: draft.constants.filter((_, itemIndex) => itemIndex !== index),
                })}
                variant="destructive"
              />
            )}
          </div>
        ))}
        {!draft.constants.length && <div className="constant-table-empty">没有常量项</div>}
      </div>
      <FieldError>{error?.message}</FieldError>
      {editable && (
        <footer className="metadata-form-actions">
          {!initial && <CatalogAction elementKey="studio.library.constant.cancel" onClick={onCancel} variant="ghost" />}
          {onDelete && (
            <CatalogAction
              disabled={pending}
              elementKey="studio.library.constant.delete"
              onClick={onDelete}
              variant="destructive"
            />
          )}
          <CatalogAction
            disabled={pending || !draft.groupCode.trim() || !draft.objectName.trim() || !draft.description.trim()}
            elementKey="studio.library.constant.save"
            type="submit"
            variant="primary"
          />
        </footer>
      )}
    </form>
  )
}

function createConstantDraft(feature: LibraryFeature, groupCount: number): ConstantCommand {
  const baseName = feature.featureCode
    .split(/[._-]/)
    .filter(Boolean)
    .map((segment) => `${segment.charAt(0).toUpperCase()}${segment.slice(1)}`)
    .join('') || 'Feature'
  const suffix = groupCount ? String(groupCount + 1) : ''
  return {
    featureId: feature.id,
    groupCode: `${baseName.charAt(0).toLowerCase()}${baseName.slice(1)}Constants${suffix}`,
    objectName: `${baseName}Constants${suffix}`,
    description: `${feature.name}常量`,
    status: 1,
    constants: [createConstantItem(0)],
  }
}

function createConstantItem(index: number): ConstantItemCommand {
  return {
    name: `ITEM_${index + 1}`,
    type: 'STRING',
    value: '',
    description: '',
  }
}
