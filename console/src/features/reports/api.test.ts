import { afterEach, describe, expect, it, vi } from 'vitest'

import { fetchPublishedReports, fetchReports, ReportPublicationError, saveAndPublishReport } from './api'
import { emptyReportDocument } from './models'

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

  it('saves a new draft before publishing and returns the published cache state', async () => {
    const document = emptyReportDocument('销售报表')
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({
        code: 0,
        msg: '',
        data: { reportKey: 'sales', revision: 1, document, publishedRevision: null },
      }))
      .mockResolvedValueOnce(Response.json({
        code: 0,
        msg: '',
        data: { reportKey: 'sales', publishedRevision: 1, document },
      }))
    vi.stubGlobal('fetch', fetcher)

    const saved = await saveAndPublishReport({
      reportKey: 'sales',
      revision: 0,
      document,
      saveRequired: true,
      publishedRevision: null,
    })

    expect(fetcher.mock.calls.map(([path, init]) => [String(path), init?.method])).toEqual([
      ['/console/api/reports', 'POST'],
      ['/console/api/reports/sales/publication', 'POST'],
    ])
    expect(saved.publishedRevision).toBe(1)
  })

  it('exposes a saved draft when publication fails so the caller can retry its revision', async () => {
    const document = emptyReportDocument('销售报表')
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({
        code: 0,
        msg: '',
        data: { reportKey: 'sales', revision: 1, document, publishedRevision: null },
      }))
      .mockResolvedValueOnce(Response.json({ code: 503, msg: '发布暂不可用', data: null }, { status: 503 }))
    vi.stubGlobal('fetch', fetcher)

    const failure = await saveAndPublishReport({
      reportKey: 'sales',
      revision: 0,
      document,
      saveRequired: true,
      publishedRevision: null,
    }).catch((error: unknown) => error)

    expect(failure).toBeInstanceOf(ReportPublicationError)
    expect((failure as ReportPublicationError).savedReport.revision).toBe(1)
    expect((failure as Error).message).toContain('草稿已保存')
  })
})
