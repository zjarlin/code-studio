import type { JsonObject, JsonPrimitive, JsonValue, MetadataCellPatch, MetadataTableColumn as MetadataTableColumnContract, MetadataTableColumnKind, MetadataTableOperation, MetadataTablePatchResult, MetadataValueEdit } from '@/types'

export type { MetadataCellPatch, MetadataTablePatchResult } from '@/types'

export type MetadataColumnKind = MetadataTableColumnKind

export interface MetadataTableColumn<Row extends object = Record<string, unknown>> extends Omit<MetadataTableColumnContract, 'key' | 'agentEditable'> {
  key: Extract<keyof Row, string> | string
  editable?: boolean
  options?: Array<{ label: string, value: JsonPrimitive }>
}

export interface MetadataTableDescriptor<Row extends object = Record<string, unknown>> {
  tableId: string
  revision: string
  rowIdentityKey: string
  rowKey: (row: Row) => string
  columns: MetadataTableColumn<Row>[]
  operations: MetadataTableOperation[]
}

export interface MetadataPatchConflict {
  patch: MetadataCellPatch
  reason: 'table' | 'revision' | 'row' | 'column' | 'scalar' | 'value' | 'path' | 'match' | 'replacement'
}

export interface MetadataPatchApplication<Row> {
  rows: Row[]
  applied: MetadataCellPatch[]
  conflicts: MetadataPatchConflict[]
}

export function createTableRevision<Row extends object>(
  descriptor: Pick<MetadataTableDescriptor<Row>, 'tableId' | 'rowKey' | 'columns'>,
  rows: Row[],
): string {
  const values = rows.map((row) => ({
    rowKey: descriptor.rowKey(row),
    values: Object.fromEntries(descriptor.columns.map((column) => [
      column.key,
      toMetadataJsonValue(row[column.key as keyof Row]),
    ])),
  }))
  return stableHash(JSON.stringify({ tableId: descriptor.tableId, values }))
}

export function createLiteralReplacementPatches<Row extends object>(
  descriptor: MetadataTableDescriptor<Row>,
  rows: Row[],
  columnKey: string,
  search: string,
  replacement: string,
): MetadataTablePatchResult {
  requireAgentColumn(descriptor, columnKey)
  if (!search) throw new Error('查找内容不能为空')
  const patches = rows.flatMap((row) => {
    const value = row[columnKey as keyof Row]
    if (typeof value !== 'string' || !value.includes(search)) return []
    return [{
      rowKey: descriptor.rowKey(row),
      columnKey,
      expectedValue: value,
      edits: Array.from({ length: literalOccurrenceCount(value, search) }, () => ({
        path: null,
        match: search,
        replacement,
      })),
    }]
  })
  return { tableId: descriptor.tableId, revision: descriptor.revision, patches, questions: [] }
}

export function applyMetadataPatches<Row extends object>(
  descriptor: MetadataTableDescriptor<Row>,
  rows: Row[],
  result: MetadataTablePatchResult,
  selectedPatches: MetadataCellPatch[] = result.patches,
): MetadataPatchApplication<Row> {
  const next = rows.map((row) => ({ ...row }))
  const byKey = new Map(next.map((row) => [descriptor.rowKey(row), row]))
  const columns = new Map(descriptor.columns.map((column) => [column.key, column]))
  const applied: MetadataCellPatch[] = []
  const conflicts: MetadataPatchConflict[] = []

  selectedPatches.forEach((patch) => {
    const resolution = resolvePatch(descriptor, result, byKey, columns, patch)
    if ('reason' in resolution) {
      conflicts.push({ patch, reason: resolution.reason })
      return
    }
    const row = byKey.get(patch.rowKey) as Row
    Object.assign(row, { [patch.columnKey]: resolution.value })
    applied.push(patch)
  })
  return { rows: next, applied, conflicts }
}

export function normalizeMetadataPatchResult(value: JsonObject): MetadataTablePatchResult {
  const tableId = requiredString(value.tableId, 'tableId')
  const revision = requiredString(value.revision, 'revision')
  const rawPatches = Array.isArray(value.patches) ? value.patches : []
  const patches = rawPatches.map((patch, index) => normalizePatch(patch, index))
  const questions = Array.isArray(value.questions)
    ? value.questions.filter((question): question is string => typeof question === 'string')
    : []
  return {
    tableId,
    revision,
    patches,
    questions,
    summary: typeof value.summary === 'string' ? value.summary : undefined,
  }
}

function resolvePatch<Row extends object>(
  descriptor: MetadataTableDescriptor<Row>,
  result: MetadataTablePatchResult,
  rows: Map<string, Row>,
  columns: Map<string, MetadataTableColumn<Row>>,
  patch: MetadataCellPatch,
): { value: JsonValue } | { reason: MetadataPatchConflict['reason'] } {
  if (result.tableId !== descriptor.tableId) return { reason: 'table' }
  if (result.revision !== descriptor.revision) return { reason: 'revision' }
  const row = rows.get(patch.rowKey)
  if (!row) return { reason: 'row' }
  const column = columns.get(patch.columnKey)
  if (!column?.editable) return { reason: 'column' }
  if (!isJsonPrimitive(patch.expectedValue) || !patch.edits.length) return { reason: 'scalar' }
  const currentValue = toMetadataJsonValue(row[patch.columnKey as keyof Row])
  if (['object', 'collection', 'map'].includes(column.kind)) {
    if (patch.expectedValue !== null || !structuredValueMatchesColumn(column.kind, currentValue)) {
      return { reason: 'value' }
    }
    return resolveStructuredEdits(currentValue, patch.edits)
  }
  if (!['scalar', 'enum', 'boolean'].includes(column.kind)) return { reason: 'column' }
  if (!isJsonPrimitive(currentValue)) return { reason: 'value' }
  if (patch.expectedValue !== null && currentValue !== patch.expectedValue) return { reason: 'value' }
  return resolveEditedValue(column, currentValue, patch.edits)
}

function isJsonPrimitive(value: unknown): value is JsonPrimitive {
  return value === null || ['string', 'number', 'boolean'].includes(typeof value)
}

export function toMetadataJsonValue(value: unknown): JsonValue {
  if (isJsonPrimitive(value)) return value
  if (Array.isArray(value)) return value.map(toMetadataJsonValue)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, child]) => [key, toMetadataJsonValue(child)]))
  }
  return null
}

function normalizePatch(value: unknown, index: number): MetadataCellPatch {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`结构化输出 patches[${index}] 不是对象`)
  }
  const patch = value as Record<string, unknown>
  const expectedValue = patch.expectedValue
  if (!isJsonPrimitive(expectedValue)) {
    throw new Error(`结构化输出 patches[${index}] 只能修改标量值`)
  }
  if (!Array.isArray(patch.edits) || !patch.edits.length) {
    throw new Error(`结构化输出 patches[${index}].edits 不能为空`)
  }
  return {
    rowKey: requiredString(patch.rowKey, `patches[${index}].rowKey`),
    columnKey: requiredString(patch.columnKey, `patches[${index}].columnKey`),
    expectedValue,
    edits: patch.edits.map((edit, editIndex) => normalizeEdit(edit, index, editIndex)),
  }
}

function normalizeEdit(value: unknown, patchIndex: number, editIndex: number): MetadataValueEdit {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`结构化输出 patches[${patchIndex}].edits[${editIndex}] 不是对象`)
  }
  const edit = value as Record<string, unknown>
  const path = edit.path
  if (path !== undefined && path !== null && typeof path !== 'string') {
    throw new Error(`结构化输出 patches[${patchIndex}].edits[${editIndex}].path 必须是字符串或空`)
  }
  if (!isJsonPrimitive(edit.match) || !isJsonPrimitive(edit.replacement)) {
    throw new Error(`结构化输出 patches[${patchIndex}].edits[${editIndex}] 只能替换标量片段`)
  }
  return { path: path ?? null, match: edit.match, replacement: edit.replacement }
}

function requiredString(value: unknown, field: string): string {
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(`结构化输出缺少 ${field}`)
  }
  return value
}

function requireAgentColumn<Row extends object>(descriptor: MetadataTableDescriptor<Row>, columnKey: string): void {
  if (!descriptor.columns.find((column) => column.key === columnKey)?.editable) {
    throw new Error(`列不允许智能调整: ${columnKey}`)
  }
}

function resolveEditedValue<Row extends object>(
  column: MetadataTableColumn<Row>,
  expectedValue: JsonPrimitive,
  edits: MetadataValueEdit[],
): { value: JsonPrimitive } | { reason: 'scalar' | 'match' | 'replacement' } {
  if (edits.some((edit) => edit.path !== null)) return { reason: 'scalar' }
  if (column.kind === 'scalar' && typeof expectedValue === 'string') {
    return resolveTextEdits(expectedValue, edits)
  }
  if (edits.length !== 1 || edits[0].match !== expectedValue) return { reason: 'match' }
  const replacement = edits[0].replacement
  if (!replacementMatchesColumn(column, expectedValue, replacement)) return { reason: 'replacement' }
  return { value: replacement }
}

function resolveStructuredEdits(
  expectedValue: JsonObject | JsonValue[],
  edits: MetadataValueEdit[],
): { value: JsonObject | JsonValue[] } | { reason: 'scalar' | 'path' | 'match' | 'replacement' } {
  if (edits.some((edit) => edit.path === null)) return { reason: 'path' }
  const grouped = new Map<string, MetadataValueEdit[]>()
  for (const edit of edits) {
    const path = edit.path as string
    const pathEdits = grouped.get(path) ?? []
    pathEdits.push(edit)
    grouped.set(path, pathEdits)
  }
  const value = cloneJsonValue(expectedValue)
  for (const [path, pathEdits] of grouped) {
    const target = resolveJsonPointer(value, path)
    if (!target) return { reason: 'path' }
    const current = jsonTargetValue(target)
    if (!isJsonPrimitive(current)) return { reason: 'path' }
    const resolution = typeof current === 'string'
      ? resolveTextEdits(current, pathEdits)
      : resolveAtomicEdits(current, pathEdits)
    if ('reason' in resolution) return resolution
    setJsonTargetValue(target, resolution.value)
  }
  return { value }
}

function resolveAtomicEdits(
  expectedValue: JsonPrimitive,
  edits: MetadataValueEdit[],
): { value: JsonPrimitive } | { reason: 'match' | 'replacement' } {
  if (edits.length !== 1 || edits[0].match !== expectedValue) return { reason: 'match' }
  const replacement = edits[0].replacement
  if (expectedValue !== null && typeof replacement !== typeof expectedValue) return { reason: 'replacement' }
  return { value: replacement }
}

function structuredValueMatchesColumn(
  kind: MetadataTableColumnKind,
  value: JsonValue,
): value is JsonObject | JsonValue[] {
  if (kind === 'collection') return Array.isArray(value)
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function cloneJsonValue<T extends JsonValue>(value: T): T {
  if (Array.isArray(value)) return value.map((item) => cloneJsonValue(item)) as T
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, child]) => [key, cloneJsonValue(child ?? null)])) as T
  }
  return value
}

interface JsonPointerTarget {
  parent: JsonObject | JsonValue[]
  key: string | number
}

function resolveJsonPointer(value: JsonObject | JsonValue[], path: string): JsonPointerTarget | undefined {
  const tokens = jsonPointerTokens(path)
  if (!tokens?.length) return undefined
  let current: JsonValue = value
  for (const token of tokens.slice(0, -1)) {
    const child = jsonChild(current, token)
    if (!child || typeof child !== 'object') return undefined
    current = child
  }
  const parent = current as JsonObject | JsonValue[]
  const key = jsonChildKey(parent, tokens.at(-1) as string)
  return key === undefined ? undefined : { parent, key }
}

function jsonPointerTokens(path: string): string[] | undefined {
  if (!path.startsWith('/') || /~(?:[^01]|$)/.test(path)) return undefined
  return path.slice(1).split('/').map((token) => token.replaceAll('~1', '/').replaceAll('~0', '~'))
}

function jsonChild(value: JsonValue, token: string): JsonValue | undefined {
  if (!value || typeof value !== 'object') return undefined
  const key = jsonChildKey(value, token)
  if (key === undefined) return undefined
  return Array.isArray(value)
    ? value[key as number]
    : value[key as string]
}

function jsonChildKey(value: JsonObject | JsonValue[], token: string): string | number | undefined {
  if (Array.isArray(value)) {
    if (!/^(0|[1-9]\d*)$/.test(token)) return undefined
    const index = Number(token)
    return index < value.length ? index : undefined
  }
  if (['__proto__', 'prototype', 'constructor'].includes(token)) return undefined
  return Object.prototype.hasOwnProperty.call(value, token) ? token : undefined
}

function jsonTargetValue(target: JsonPointerTarget): JsonValue | undefined {
  return Array.isArray(target.parent)
    ? target.parent[target.key as number]
    : target.parent[target.key as string]
}

function setJsonTargetValue(target: JsonPointerTarget, value: JsonPrimitive): void {
  if (Array.isArray(target.parent)) {
    target.parent[target.key as number] = value
  } else {
    target.parent[target.key as string] = value
  }
}

function resolveTextEdits(
  expectedValue: string,
  edits: MetadataValueEdit[],
): { value: string } | { reason: 'scalar' | 'match' } {
  if (edits.some((edit) => typeof edit.match !== 'string' || typeof edit.replacement !== 'string')) {
    return { reason: 'scalar' }
  }
  if (expectedValue === '') {
    const [edit] = edits
    return edits.length === 1 && edit.match === ''
      ? { value: edit.replacement as string }
      : { reason: 'match' }
  }
  let cursor = 0
  let value = ''
  let matched = false
  for (const edit of edits as Array<{ match: string, replacement: string }>) {
    if (!edit.match) return { reason: 'match' }
    const index = expectedValue.indexOf(edit.match, cursor)
    if (index < 0) continue
    value += expectedValue.slice(cursor, index) + edit.replacement
    cursor = index + edit.match.length
    matched = true
  }
  return matched
    ? { value: value + expectedValue.slice(cursor) }
    : { reason: 'match' }
}

function replacementMatchesColumn<Row extends object>(
  column: MetadataTableColumn<Row>,
  expectedValue: JsonPrimitive,
  replacement: JsonPrimitive,
): boolean {
  if (column.kind === 'boolean') return typeof replacement === 'boolean'
  if (column.kind === 'enum') {
    return !column.options?.length || column.options.some((option) => option.value === replacement)
  }
  return expectedValue === null || typeof replacement === typeof expectedValue
}

function literalOccurrenceCount(value: string, search: string): number {
  let count = 0
  let cursor = 0
  while (cursor <= value.length - search.length) {
    const index = value.indexOf(search, cursor)
    if (index < 0) break
    count += 1
    cursor = index + search.length
  }
  return count
}

function stableHash(value: string): string {
  let hash = 2166136261
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return (hash >>> 0).toString(16).padStart(8, '0')
}
