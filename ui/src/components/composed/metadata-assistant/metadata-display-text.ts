import {
  applyMetadataPatches,
  createTableRevision,
} from '@/components/composed/metadata-table/metadata-table'
import type {
  MetadataPatchConflict,
  MetadataTableDescriptor,
} from '@/components/composed/metadata-table/metadata-table'
import type {
  JsonObject,
  JsonValue,
  MetadataCellPatch,
  MetadataTableContext,
  MetadataTablePatchResult,
} from '@/types'

import {
  collectMetadataDisplayTextTargets,
} from './metadata-display-text-targets'
import type { MetadataDisplayTextTarget } from './metadata-display-text-targets'

export type MetadataDisplayTextScope = 'contract' | 'dto' | 'model'

interface MetadataDisplayTextRow extends Record<string, unknown> {
  targetKey: string
  context: string
  value: string
}

interface MetadataDisplayTextSnapshot {
  descriptor: MetadataTableDescriptor<MetadataDisplayTextRow>
  rows: MetadataDisplayTextRow[]
  targets: Map<string, MetadataDisplayTextTarget>
}

export interface MetadataDisplayTextApplication {
  draft: JsonObject
  applied: MetadataCellPatch[]
  conflicts: MetadataPatchConflict[]
}

export function createMetadataDisplayTextContext(
  scope: MetadataDisplayTextScope,
  draft: JsonObject,
  draftIdentity: string,
): MetadataTableContext {
  const snapshot = createSnapshot(scope, draft, draftIdentity)
  return {
    tableId: snapshot.descriptor.tableId,
    revision: snapshot.descriptor.revision,
    targetColumnKey: 'value',
    rowIdentityKey: snapshot.descriptor.rowIdentityKey,
    context: { metadataScope: scope, draftIdentity },
    operations: snapshot.descriptor.operations,
    columns: snapshot.descriptor.columns.map((column) => ({
      key: String(column.key),
      label: column.label,
      kind: column.kind,
      agentEditable: Boolean(column.editable),
      context: column.context,
    })),
    rows: snapshot.rows.map((row) => ({
      rowKey: row.targetKey,
      values: {
        targetKey: row.targetKey,
        context: row.context,
        value: row.value,
      },
    })),
    selection: {
      rowKeys: snapshot.rows.map((row) => row.targetKey),
      filteredRowCount: snapshot.rows.length,
    },
  }
}

export function applyMetadataDisplayTextPatches(
  scope: MetadataDisplayTextScope,
  draft: JsonObject,
  draftIdentity: string,
  result: MetadataTablePatchResult,
): MetadataDisplayTextApplication {
  const clonedDraft = cloneJsonObject(draft)
  const snapshot = createSnapshot(scope, clonedDraft, draftIdentity)
  const application = applyMetadataPatches(snapshot.descriptor, snapshot.rows, result)
  const rowsByKey = new Map(application.rows.map((row) => [row.targetKey, row]))

  for (const patch of application.applied) {
    const row = rowsByKey.get(patch.rowKey)
    const target = snapshot.targets.get(patch.rowKey)
    if (!row || !target || typeof row.value !== 'string') {
      continue
    }
    target.replace(row.value)
  }

  return {
    draft: clonedDraft,
    applied: application.applied,
    conflicts: application.conflicts,
  }
}

function createSnapshot(
  scope: MetadataDisplayTextScope,
  draft: JsonObject,
  draftIdentity: string,
): MetadataDisplayTextSnapshot {
  const targets = collectMetadataDisplayTextTargets(scope, draft)
  const rows = targets.map((target) => ({
    targetKey: target.key,
    context: target.context,
    value: target.value,
  }))
  const descriptorBase: Omit<MetadataTableDescriptor<MetadataDisplayTextRow>, 'revision'> = {
    tableId: `metadata.display-text:${scope}:${draftIdentity}`,
    rowIdentityKey: 'targetKey',
    rowKey: (row) => row.targetKey,
    columns: [
      { key: 'targetKey', label: '目标', kind: 'scalar', context: true },
      { key: 'context', label: '语义上下文', kind: 'scalar', context: true },
      { key: 'value', label: '展示文本', kind: 'scalar', editable: true, context: false },
    ],
    operations: ['translate'],
  }
  const descriptor = {
    ...descriptorBase,
    revision: createTableRevision(descriptorBase, rows),
  }
  return {
    descriptor,
    rows,
    targets: new Map(targets.map((target) => [target.key, target])),
  }
}

function cloneJsonObject(value: JsonObject): JsonObject {
  return cloneJsonValue(value) as JsonObject
}

function cloneJsonValue(value: JsonValue | undefined): JsonValue | undefined {
  if (Array.isArray(value)) {
    return value.map((item) => cloneJsonValue(item) as JsonValue)
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, child]) => [key, cloneJsonValue(child)]),
    )
  }
  return value
}
