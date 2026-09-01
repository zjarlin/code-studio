import { afterEach, describe, expect, it, vi } from 'vitest'

import { fillSpreadsheetTemplate, updateSpreadsheetTemplate } from './operations'
import type { SpreadsheetTemplateDocument } from './models'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('generated spreadsheet template operations', () => {
  it('updates only the editable draft overlay', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(Response.json({ code: 0, msg: '', data: {} }))
    vi.stubGlobal('fetch', fetcher)
    const document = {
      name: 'Example',
      description: null,
      source: { fileName: 'source.xlsm' },
      styles: [{ key: 0 }],
      sheets: [{ key: 'sheet1' }],
      variables: [],
      bindings: [],
      ledgers: [],
      edits: [],
    } as unknown as SpreadsheetTemplateDocument

    await updateSpreadsheetTemplate('example', 3, document)

    const body = JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))
    expect(body).toEqual({
      expectedRevision: 3,
      draft: {
        name: 'Example',
        description: null,
        variables: [],
        bindings: [],
        ledgers: [],
        edits: [],
      },
    })
    expect(body.draft).not.toHaveProperty('source')
    expect(body.draft).not.toHaveProperty('sheets')
  })

  it('binds generated files to the visible revision', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response('workbook'))
    vi.stubGlobal('fetch', fetcher)

    await fillSpreadsheetTemplate('example', { expectedRevision: 4, values: {}, ledgers: {} })

    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({
      expectedRevision: 4,
      values: {},
      ledgers: {},
    })
  })
})
