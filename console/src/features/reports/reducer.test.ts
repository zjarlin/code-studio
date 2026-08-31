import { describe, expect, it } from 'vitest'

import { emptyReportDocument, type ReportDatasetSpec } from './models'
import { createReportHistory, reportReducer } from './reducer'

const dataset: ReportDatasetSpec = {
  key: 'orders',
  name: '订单',
  source: 'MODEL',
  modelCode: 'order',
  operationId: null,
  parameterBindings: {},
  fields: [],
}

describe('report reducer', () => {
  it('keeps every row within the 12-column contract and supports history', () => {
    const initial = createReportHistory(emptyReportDocument())
    const withDataset = reportReducer(initial, { type: 'addDataset', dataset })
    const rowKey = withDataset.present.rows[0]!.key
    const withMetric = reportReducer(withDataset, { type: 'addBlock', rowKey, blockKind: 'METRIC' })
    const blockKey = withMetric.present.rows[0]!.blocks[0]!.key
    const resized = reportReducer(withMetric, { type: 'resizeBlock', blockKey, columnSpan: 20 })

    expect(resized.present.rows[0]!.blocks[0]!.columnSpan).toBe(12)
    expect(reportReducer(resized, { type: 'undo' }).present).toEqual(withMetric.present)
    expect(reportReducer(reportReducer(resized, { type: 'undo' }), { type: 'redo' }).present).toEqual(resized.present)
  })

  it('moves blocks only when the destination has capacity', () => {
    let state = createReportHistory(emptyReportDocument())
    state = reportReducer(state, { type: 'addDataset', dataset })
    const firstRow = state.present.rows[0]!.key
    state = reportReducer(state, { type: 'addBlock', rowKey: firstRow, blockKind: 'TABLE' })
    state = reportReducer(state, { type: 'addRow' })
    const secondRow = state.present.rows[1]!.key
    const tableKey = state.present.rows[0]!.blocks[0]!.key
    state = reportReducer(state, { type: 'moveBlock', blockKey: tableKey, targetRowKey: secondRow })

    expect(state.present.rows[0]!.blocks).toHaveLength(0)
    expect(state.present.rows[1]!.blocks[0]!.key).toBe(tableKey)
  })

  it('removes blocks that reference a removed operation', () => {
    let state = createReportHistory(emptyReportDocument())
    state = reportReducer(state, { type: 'addDataset', dataset })
    const rowKey = state.present.rows[0]!.key
    state = reportReducer(state, { type: 'addBlock', rowKey, blockKind: 'IMAGE' })
    state = reportReducer(state, { type: 'removeDataset', datasetKey: dataset.key })

    expect(state.present.datasets).toHaveLength(0)
    expect(state.present.rows[0]!.blocks).toHaveLength(0)
  })

  it('keeps tables at 12 columns and removes deleted parameter bindings', () => {
    let state = createReportHistory(emptyReportDocument())
    state = reportReducer(state, { type: 'addParameter', parameter: { key: 'date', label: '日期', type: 'DATE', required: true, defaultValue: null, options: [] } })
    state = reportReducer(state, { type: 'addDataset', dataset: { ...dataset, parameterBindings: { date: { kind: 'PARAMETER', parameterKey: 'date', literal: null } } } })
    const rowKey = state.present.rows[0]!.key
    state = reportReducer(state, { type: 'addBlock', rowKey, blockKind: 'TABLE' })
    const tableKey = state.present.rows[0]!.blocks[0]!.key
    state = reportReducer(state, { type: 'resizeBlock', blockKey: tableKey, columnSpan: 3 })
    state = reportReducer(state, { type: 'removeParameter', parameterKey: 'date' })

    expect(state.present.rows[0]!.blocks[0]!.columnSpan).toBe(12)
    expect(state.present.datasets[0]!.parameterBindings).toEqual({})
  })
})
