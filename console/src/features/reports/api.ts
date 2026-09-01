import { requestData } from '@/lib/http'

import type {
  PageResult,
  PublishedReportListItemView,
  PublishedReportView,
  ReportDocument,
  ReportListItemView,
  ReportPublicationView,
  ReportView,
} from './models'

const REPORTS_ENDPOINT = '/console/api/reports'
const PUBLISHED_REPORTS_ENDPOINT = '/console/api/published-reports'

export function fetchReports(): Promise<PageResult<ReportListItemView>> {
  return requestData(`${REPORTS_ENDPOINT}?pageNo=1&pageSize=200`)
}

export function fetchReport(reportKey: string): Promise<ReportView> {
  return requestData(reportPath(REPORTS_ENDPOINT, reportKey))
}

export function createReport(reportKey: string, document: ReportDocument): Promise<ReportView> {
  return requestData(REPORTS_ENDPOINT, {
    method: 'POST',
    body: JSON.stringify({ reportKey, document }),
  })
}

export function updateReport(reportKey: string, expectedRevision: number, document: ReportDocument): Promise<ReportView> {
  return requestData(reportPath(REPORTS_ENDPOINT, reportKey), {
    method: 'PUT',
    body: JSON.stringify({ expectedRevision, document }),
  })
}

export function deleteReport(reportKey: string): Promise<boolean> {
  return requestData(reportPath(REPORTS_ENDPOINT, reportKey), { method: 'DELETE' })
}

export function publishReport(reportKey: string, expectedRevision: number): Promise<ReportPublicationView> {
  return requestData(`${reportPath(REPORTS_ENDPOINT, reportKey)}/publication`, {
    method: 'POST',
    body: JSON.stringify({ expectedRevision }),
  })
}

export async function saveAndPublishReport(input: Readonly<{
  reportKey: string
  revision: number
  document: ReportDocument
  saveRequired: boolean
  publishedRevision: number | null
}>): Promise<ReportView> {
  const saved = input.saveRequired
    ? input.revision > 0
      ? await updateReport(input.reportKey, input.revision, input.document)
      : await createReport(input.reportKey, input.document)
    : {
        reportKey: input.reportKey,
        revision: input.revision,
        document: input.document,
        publishedRevision: input.publishedRevision,
      }
  let publication: ReportPublicationView
  try {
    publication = await publishReport(saved.reportKey, saved.revision)
  } catch (cause) {
    throw new ReportPublicationError(saved, cause, input.saveRequired)
  }
  return {
    ...saved,
    document: publication.document,
    publishedRevision: publication.publishedRevision,
  }
}

export class ReportPublicationError extends Error {
  readonly savedReport: ReportView

  constructor(savedReport: ReportView, cause: unknown, draftSaved: boolean) {
    const detail = cause instanceof Error ? cause.message : '未知错误'
    super(draftSaved ? `草稿已保存，但发布失败：${detail}` : `发布失败：${detail}`)
    this.name = 'ReportPublicationError'
    this.savedReport = savedReport
  }
}

export function unpublishReport(reportKey: string): Promise<boolean> {
  return requestData(`${reportPath(REPORTS_ENDPOINT, reportKey)}/publication`, { method: 'DELETE' })
}

export function fetchPublishedReports(): Promise<PageResult<PublishedReportListItemView>> {
  return requestData(`${PUBLISHED_REPORTS_ENDPOINT}?pageNo=1&pageSize=200`)
}

export function fetchPublishedReport(reportKey: string): Promise<PublishedReportView> {
  return requestData(reportPath(PUBLISHED_REPORTS_ENDPOINT, reportKey))
}

function reportPath(base: string, reportKey: string): string {
  return `${base}/${encodeURIComponent(reportKey)}`
}
