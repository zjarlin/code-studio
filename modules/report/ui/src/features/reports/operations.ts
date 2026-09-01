import {
  createReport as createReportRequest,
  deleteReport as deleteReportRequest,
  getPublishedReport as getPublishedReportRequest,
  getReport as getReportRequest,
  listPublishedReports,
  listReports,
  publishReport as publishReportRequest,
  unpublishReport as unpublishReportRequest,
  updateReport as updateReportRequest,
} from '@generated/openapi/client'

import { requireApiData } from '@/lib/http'

import type {
  PageResult,
  PublishedReportListItemView,
  PublishedReportView,
  ReportDocument,
  ReportListItemView,
  ReportPublicationView,
  ReportView,
} from './models'

export async function fetchReports(): Promise<PageResult<ReportListItemView>> {
  return requireApiData(await listReports({ pageNo: 1, pageSize: 200 })) as PageResult<ReportListItemView>
}

export async function fetchReport(reportKey: string): Promise<ReportView> {
  return requireApiData(await getReportRequest(reportKey)) as ReportView
}

export async function createReport(reportKey: string, document: ReportDocument): Promise<ReportView> {
  return requireApiData(await createReportRequest({ reportKey, document })) as ReportView
}

export async function updateReport(
  reportKey: string,
  expectedRevision: number,
  document: ReportDocument,
): Promise<ReportView> {
  return requireApiData(await updateReportRequest(reportKey, {
    expectedRevision,
    document,
  })) as ReportView
}

export async function deleteReport(reportKey: string): Promise<boolean> {
  return requireApiData(await deleteReportRequest(reportKey))
}

export async function publishReport(reportKey: string, expectedRevision: number): Promise<ReportPublicationView> {
  return requireApiData(await publishReportRequest(reportKey, { expectedRevision })) as ReportPublicationView
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

export async function unpublishReport(reportKey: string): Promise<boolean> {
  return requireApiData(await unpublishReportRequest(reportKey))
}

export async function fetchPublishedReports(): Promise<PageResult<PublishedReportListItemView>> {
  return requireApiData(await listPublishedReports({ pageNo: 1, pageSize: 200 })) as PageResult<PublishedReportListItemView>
}

export async function fetchPublishedReport(reportKey: string): Promise<PublishedReportView> {
  return requireApiData(await getPublishedReportRequest(reportKey)) as PublishedReportView
}
