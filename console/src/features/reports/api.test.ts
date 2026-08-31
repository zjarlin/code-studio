import { afterEach, describe, expect, it, vi } from 'vitest'

import { fetchPublishedReports, fetchReports } from './api'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('report API paths', () => {
  it('uses only the console API namespace', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValue(new Response(JSON.stringify({ code: 0, msg: 'ok', data: { rows: [], totalRowCount: 0, totalPageCount: 0 } })))
    vi.stubGlobal('fetch', fetcher)

    await fetchReports()
    await fetchPublishedReports()

    expect(fetcher.mock.calls.map(([path]) => String(path))).toEqual([
      '/console/api/reports?pageNo=1&pageSize=200',
      '/console/api/published-reports?pageNo=1&pageSize=200',
    ])
  })
})
