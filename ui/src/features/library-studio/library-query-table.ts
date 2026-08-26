import type { LowcodeModelDraft, LowcodeQueryConditionDraft, LowcodeQueryDraft } from '@/types'

export interface LibraryQueryRow extends Record<string, unknown> {
  rowKey: string
  modelId: number | string
  modelCode: string
  modelName: string
  queryId?: number | string
  originalQueryCode: string
  orderNo: number
  queryCode: string
  label: string
  logic: 'AND' | 'OR'
  items: LowcodeQueryConditionDraft[]
  conditionCount: number
}

export function libraryQueryRows(models: LowcodeModelDraft[]): LibraryQueryRow[] {
  return models.flatMap((model) => model.queries.map((query, index) => libraryQueryRow(model, query, index)))
}

export function libraryQueryRow(
  model: LowcodeModelDraft,
  query: LowcodeQueryDraft,
  index = query.orderNo - 1,
  rowKey = query.id == null
    ? `model:${String(model.id)}:query:${query.queryCode || index}`
    : `query:${String(query.id)}`,
): LibraryQueryRow {
  return {
    rowKey,
    modelId: model.id ?? '',
    modelCode: model.modelCode,
    modelName: model.name,
    queryId: query.id,
    originalQueryCode: query.queryCode,
    orderNo: query.orderNo,
    queryCode: query.queryCode,
    label: query.label,
    logic: query.logic,
    items: query.items.map((item) => ({ ...item })),
    conditionCount: query.items.length,
  }
}

export function queryDraftFromRow(row: LibraryQueryRow): LowcodeQueryDraft {
  return {
    id: row.queryId,
    orderNo: row.orderNo,
    queryCode: row.queryCode.trim(),
    label: row.label.trim(),
    logic: row.logic,
    items: row.items.map((item, index) => ({ ...item, orderNo: index + 1 })),
  }
}

export function mergeLibraryQueryRow(model: LowcodeModelDraft, row: LibraryQueryRow): LowcodeModelDraft {
  const index = model.queries.findIndex((query) => queryMatchesRow(query, row))
  const replacement = queryDraftFromRow(row)
  if (index < 0) {
    replacement.orderNo = model.queries.length + 1
    return { ...model, queries: [...model.queries, replacement] }
  }
  const queries = model.queries.map((query, queryIndex) => queryIndex === index
    ? { ...replacement, orderNo: query.orderNo }
    : query)
  return { ...model, queries }
}

export function removeLibraryQueryRow(model: LowcodeModelDraft, row: LibraryQueryRow): LowcodeModelDraft {
  const queries = model.queries
    .filter((query) => !queryMatchesRow(query, row))
    .map((query, index) => ({ ...query, orderNo: index + 1 }))
  return { ...model, queries }
}

function queryMatchesRow(query: LowcodeQueryDraft, row: LibraryQueryRow): boolean {
  if (row.queryId != null && query.id != null && String(query.id) === String(row.queryId)) {
    return true
  }
  return Boolean(row.originalQueryCode) && query.queryCode === row.originalQueryCode
}
