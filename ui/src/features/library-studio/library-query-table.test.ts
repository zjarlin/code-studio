import { describe, expect, it } from 'vitest'

import { normalizeModelDraft } from '../model-studio/model-draft'
import { libraryQueryRows, mergeLibraryQueryRow, removeLibraryQueryRow } from './library-query-table'

function model() {
  return normalizeModelDraft({
    id: 7,
    modelCode: 'inspectionTask',
    name: '巡检任务',
    packageName: 'example.inspection.task',
    contributorId: 'example.catalog',
    queries: [
      { id: 71, orderNo: 1, queryCode: 'byStatus', label: '按状态', logic: 'AND', items: [] },
      { id: 72, orderNo: 2, queryCode: 'keyword', label: '关键词', logic: 'OR', items: [] },
    ],
  })
}

describe('library query table', () => {
  it('flattens model queries into stable table rows', () => {
    const rows = libraryQueryRows([model()])

    expect(rows.map(({ rowKey }) => rowKey)).toEqual(['query:71', 'query:72'])
    expect(rows.map(({ modelCode }) => modelCode)).toEqual(['inspectionTask', 'inspectionTask'])
  })

  it('merges only the saved query into the latest model snapshot', () => {
    const row = libraryQueryRows([model()])[0]
    row.label = '按任务状态'
    const latest = model()
    latest.queries[0].id = 81
    latest.queries[1].id = 82
    latest.queries[1].label = '服务端已更新的关键词'

    const merged = mergeLibraryQueryRow(latest, row)

    expect(merged.queries).toHaveLength(2)
    expect(merged.queries.map(({ label }) => label)).toEqual(['按任务状态', '服务端已更新的关键词'])
  })

  it('removes one query and keeps deterministic ordering', () => {
    const current = model()
    const removed = removeLibraryQueryRow(current, libraryQueryRows([current])[0])

    expect(removed.queries).toHaveLength(1)
    expect(removed.queries[0]).toMatchObject({ queryCode: 'keyword', orderNo: 1 })
  })
})
