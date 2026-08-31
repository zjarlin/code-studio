import {
  createReportKey,
  REPORT_GRID_COLUMNS,
  type ReportBlockKind,
  type ReportBlockSpec,
  type ReportDatasetSpec,
  type ReportDocument,
  type ReportPageSpec,
  type ReportParameter,
} from './models'

export interface ReportHistory {
  past: ReportDocument[]
  present: ReportDocument
  future: ReportDocument[]
}

export type ReportAction =
  | { type: 'reset'; document: ReportDocument }
  | { type: 'undo' }
  | { type: 'redo' }
  | { type: 'addDataset'; dataset: ReportDatasetSpec }
  | { type: 'replaceDataset'; dataset: ReportDatasetSpec }
  | { type: 'removeDataset'; datasetKey: string }
  | { type: 'addParameter'; parameter: ReportParameter }
  | { type: 'replaceParameter'; parameter: ReportParameter }
  | { type: 'removeParameter'; parameterKey: string }
  | { type: 'updateDocument'; name: string; description: string | null }
  | { type: 'updatePage'; page: ReportPageSpec }
  | { type: 'addRow' }
  | { type: 'addBlock'; rowKey: string; blockKind: ReportBlockKind }
  | { type: 'moveBlock'; blockKey: string; targetRowKey: string }
  | { type: 'resizeBlock'; blockKey: string; columnSpan: number }
  | { type: 'replaceBlock'; block: ReportBlockSpec }
  | { type: 'deleteBlock'; blockKey: string }

export function createReportHistory(document: ReportDocument): ReportHistory {
  return { past: [], present: document, future: [] }
}

export function reportReducer(state: ReportHistory, action: ReportAction): ReportHistory {
  if (action.type === 'reset') return createReportHistory(action.document)
  if (action.type === 'undo') return undo(state)
  if (action.type === 'redo') return redo(state)

  const document = reduceDocument(state.present, action)
  if (document === state.present) return state
  return {
    past: [...state.past.slice(-49), state.present],
    present: document,
    future: [],
  }
}

function reduceDocument(document: ReportDocument, action: Exclude<ReportAction, { type: 'reset' | 'undo' | 'redo' }>): ReportDocument {
  switch (action.type) {
    case 'addDataset':
      if (document.datasets.some(({ key }) => key === action.dataset.key)) return document
      return { ...document, datasets: [...document.datasets, action.dataset] }
    case 'replaceDataset':
      return replaceByKey(document, 'datasets', action.dataset)
    case 'removeDataset':
      return removeDataset(document, action.datasetKey)
    case 'addParameter':
      if (document.parameters.some(({ key }) => key === action.parameter.key)) return document
      return { ...document, parameters: [...document.parameters, action.parameter] }
    case 'replaceParameter':
      return replaceByKey(document, 'parameters', action.parameter)
    case 'removeParameter':
      return removeParameter(document, action.parameterKey)
    case 'updateDocument':
      if (document.name === action.name && document.description === action.description) return document
      return { ...document, name: action.name, description: action.description }
    case 'updatePage':
      return document.page.orientation === action.page.orientation && document.page.marginMm === action.page.marginMm
        ? document
        : { ...document, page: action.page }
    case 'addRow':
      return { ...document, rows: [...document.rows, { key: createReportKey('row'), blocks: [] }] }
    case 'addBlock':
      return addBlock(document, action.rowKey, action.blockKind)
    case 'moveBlock':
      return moveBlock(document, action.blockKey, action.targetRowKey)
    case 'resizeBlock':
      return updateBlock(document, action.blockKey, (block, row) => resizeBlock(block, action.columnSpan, row.blocks))
    case 'replaceBlock':
      return updateBlock(document, action.block.key, (block, row) => resizeBlock(
        { ...action.block, key: block.key },
        action.block.columnSpan,
        row.blocks,
      ))
    case 'deleteBlock':
      return {
        ...document,
        rows: document.rows.map((row) => ({
          ...row,
          blocks: row.blocks.filter(({ key }) => key !== action.blockKey),
        })),
      }
  }
}

function replaceByKey<K extends 'datasets' | 'parameters'>(
  document: ReportDocument,
  collection: K,
  value: ReportDocument[K][number],
): ReportDocument {
  const current = document[collection]
  if (!current.some(({ key }) => key === value.key)) return document
  return { ...document, [collection]: current.map((item) => item.key === value.key ? value : item) }
}

function addBlock(document: ReportDocument, rowKey: string, blockKind: ReportBlockKind): ReportDocument {
  const datasetKey = document.datasets[0]?.key
  if (blockKind !== 'TEXT' && !datasetKey) return document
  const block = createBlock(blockKind, datasetKey)
  const target = document.rows.find((row) => row.key === rowKey)
  if (!target) return document
  if ((block.kind === 'TABLE' && target.blocks.length > 0)
    || target.blocks.some(({ kind }) => kind === 'TABLE')
    || usedColumns(target.blocks) + block.columnSpan > REPORT_GRID_COLUMNS) {
    return { ...document, rows: [...document.rows, { key: createReportKey('row'), blocks: [block] }] }
  }
  return {
    ...document,
    rows: document.rows.map((row) => row.key === rowKey ? { ...row, blocks: [...row.blocks, block] } : row),
  }
}

function moveBlock(document: ReportDocument, blockKey: string, targetRowKey: string): ReportDocument {
  const source = document.rows.find((row) => row.blocks.some(({ key }) => key === blockKey))
  const target = document.rows.find((row) => row.key === targetRowKey)
  const block = source?.blocks.find(({ key }) => key === blockKey)
  if (!source || !target || !block || source.key === target.key) return document
  if ((block.kind === 'TABLE' && target.blocks.length > 0) || target.blocks.some(({ kind }) => kind === 'TABLE')) return document
  if (usedColumns(target.blocks) + block.columnSpan > REPORT_GRID_COLUMNS) return document
  return {
    ...document,
    rows: document.rows.map((row) => {
      if (row.key === source.key) return { ...row, blocks: row.blocks.filter(({ key }) => key !== blockKey) }
      if (row.key === target.key) return { ...row, blocks: [...row.blocks, block] }
      return row
    }),
  }
}

function removeDataset(document: ReportDocument, datasetKey: string): ReportDocument {
  if (!document.datasets.some(({ key }) => key === datasetKey)) return document
  return {
    ...document,
    datasets: document.datasets.filter(({ key }) => key !== datasetKey),
    rows: document.rows.map((row) => ({
      ...row,
      blocks: row.blocks.filter((block) => datasetKeyOf(block) !== datasetKey),
    })),
  }
}

function removeParameter(document: ReportDocument, parameterKey: string): ReportDocument {
  if (!document.parameters.some(({ key }) => key === parameterKey)) return document
  return {
    ...document,
    parameters: document.parameters.filter(({ key }) => key !== parameterKey),
    datasets: document.datasets.map((dataset) => ({
      ...dataset,
      parameterBindings: Object.fromEntries(
        Object.entries(dataset.parameterBindings).filter(([, binding]) =>
          binding.kind !== 'PARAMETER' || binding.parameterKey !== parameterKey,
        ),
      ),
    })),
  }
}

function updateBlock(
  document: ReportDocument,
  blockKey: string,
  update: (block: ReportBlockSpec, row: ReportDocument['rows'][number]) => ReportBlockSpec,
): ReportDocument {
  let found = false
  const rows = document.rows.map((row) => ({
    ...row,
    blocks: row.blocks.map((block) => {
      if (block.key !== blockKey) return block
      found = true
      return update(block, row)
    }),
  }))
  return found ? { ...document, rows } : document
}

function createBlock(kind: ReportBlockKind, datasetKey?: string): ReportBlockSpec {
  const key = createReportKey('block')
  switch (kind) {
    case 'TEXT': return { key, kind, text: '文本内容', columnSpan: 12 }
    case 'METRIC': return { key, kind, datasetKey: datasetKey!, label: '指标', valuePointer: '/total', aggregate: 'FIRST', columnSpan: 4 }
    case 'TABLE': return { key, kind, datasetKey: datasetKey!, columns: [], rowLimit: 20, columnSpan: 12 }
    case 'CHART': return { key, kind, datasetKey: datasetKey!, chartKind: 'BAR', categoryPointer: '/name', valuePointer: '/value', columnSpan: 8 }
    case 'IMAGE': return { key, kind, datasetKey: datasetKey!, sourcePointer: '/url', alt: '', columnSpan: 4 }
  }
}

function clampSpan(block: ReportBlockSpec, span: number, blocks: ReportBlockSpec[]): number {
  if (block.kind === 'TABLE') return REPORT_GRID_COLUMNS
  const available = REPORT_GRID_COLUMNS - usedColumns(blocks.filter(({ key }) => key !== block.key))
  return Math.max(1, Math.min(available, Math.round(span)))
}

function resizeBlock(block: ReportBlockSpec, span: number, blocks: ReportBlockSpec[]): ReportBlockSpec {
  if (block.kind === 'TABLE') return { ...block, columnSpan: 12 }
  return { ...block, columnSpan: clampSpan(block, span, blocks) }
}

function usedColumns(blocks: ReportBlockSpec[]): number {
  return blocks.reduce((sum, block) => sum + block.columnSpan, 0)
}

function datasetKeyOf(block: ReportBlockSpec): string | undefined {
  return block.kind === 'TEXT' ? undefined : block.datasetKey
}

function undo(state: ReportHistory): ReportHistory {
  const previous = state.past.at(-1)
  if (!previous) return state
  return { past: state.past.slice(0, -1), present: previous, future: [state.present, ...state.future] }
}

function redo(state: ReportHistory): ReportHistory {
  const next = state.future[0]
  if (!next) return state
  return { past: [...state.past, state.present], present: next, future: state.future.slice(1) }
}
