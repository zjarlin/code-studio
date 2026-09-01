import { describe, expect, it } from 'vitest'

import { createSpreadsheetTemplateHistory, spreadsheetTemplateHistoryReducer } from './history'
import type { SpreadsheetTemplateDocument } from './models'

describe('spreadsheet template history', () => {
  it('commits one document per edit and supports undo and redo', () => {
    const original = { name: 'A' } as SpreadsheetTemplateDocument
    const edited = { name: 'B' } as SpreadsheetTemplateDocument
    const committed = spreadsheetTemplateHistoryReducer(createSpreadsheetTemplateHistory(original), { type: 'commit', document: edited })
    const undone = spreadsheetTemplateHistoryReducer(committed, { type: 'undo' })
    const redone = spreadsheetTemplateHistoryReducer(undone, { type: 'redo' })

    expect(committed.past).toEqual([original])
    expect(undone.present).toBe(original)
    expect(redone.present).toBe(edited)
  })
})

