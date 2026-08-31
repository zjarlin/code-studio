import { describe, expect, it } from 'vitest'

import { emptyReportDocument } from './models'
import { isJsonPointer, resolveJsonPointer, rowsFromResult, runReportDatasets } from './runner'

describe('report data normalization', () => {
  it('normalizes page and list payloads into rows', () => {
    expect(rowsFromResult({ list: [{ id: 1 }] })).toEqual([{ id: 1 }])
    expect(rowsFromResult([{ id: 2 }])).toEqual([{ id: 2 }])
    expect(rowsFromResult({ total: 3 })).toEqual([{ total: 3 }])
  })

  it('resolves escaped JSON Pointer segments', () => {
    expect(resolveJsonPointer({ 'a/b': { '~value': 7 } }, '/a~1b/~0value')).toBe(7)
    expect(resolveJsonPointer({ value: 2 }, 'value')).toBeUndefined()
  })

  it('caps direct data source rows and rejects malformed pointers', () => {
    const rows = Array.from({ length: 205 }, (_, index) => ({ index }))

    expect(rowsFromResult(rows)).toHaveLength(200)
    expect(isJsonPointer('/items/0/name')).toBe(true)
    expect(isJsonPointer('/items/~2')).toBe(false)
  })

  it('rejects a missing required report parameter before data execution', async () => {
    const document = emptyReportDocument()
    document.parameters.push({ key: 'date', label: '日期', type: 'DATE', required: true, defaultValue: null, options: [] })

    await expect(runReportDatasets(document, {})).rejects.toThrow('缺少必填参数：日期')
  })
})
