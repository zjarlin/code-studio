export type ReportPageOrientation = 'PORTRAIT' | 'LANDSCAPE'
export type ReportParameterType = 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'ENUM'
export type ReportDatasetSource = 'MODEL' | 'OPENAPI'
export type ReportBlockKind = 'TEXT' | 'METRIC' | 'TABLE' | 'CHART' | 'IMAGE'
export type ReportMetricAggregate = 'FIRST' | 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX'
export type ReportChartKind = 'BAR' | 'LINE' | 'PIE'
export type JsonLiteral = string | number | boolean | null

export interface ReportPageSpec {
  orientation: ReportPageOrientation
  marginMm: 8 | 12 | 20
}

export interface ReportParameterOption {
  value: string
  label: string
}

export interface ReportParameter {
  key: string
  label: string
  type: ReportParameterType
  required: boolean
  defaultValue: string | null
  options: ReportParameterOption[]
}

export type ReportParameterBinding =
  | { kind: 'PARAMETER'; parameterKey: string; literal: null }
  | { kind: 'LITERAL'; parameterKey: null; literal: JsonLiteral }

export interface ReportDatasetField {
  key: string
  label: string
  pointer: string
}

export interface ReportDatasetSpec {
  key: string
  name: string
  source: ReportDatasetSource
  modelCode: string | null
  operationId: string | null
  parameterBindings: Record<string, ReportParameterBinding>
  fields: ReportDatasetField[]
}

interface ReportBlockBase {
  key: string
  kind: ReportBlockKind
  columnSpan: number
}

export interface ReportTextBlock extends ReportBlockBase {
  kind: 'TEXT'
  text: string
}

interface ReportDataBlock extends ReportBlockBase {
  datasetKey: string
}

export interface ReportMetricBlock extends ReportDataBlock {
  kind: 'METRIC'
  label: string
  valuePointer: string
  aggregate: ReportMetricAggregate
}

export interface ReportTableColumn {
  key: string
  label: string
  valuePointer: string
}

export interface ReportTableBlock extends ReportDataBlock {
  kind: 'TABLE'
  columns: ReportTableColumn[]
  rowLimit: number
  columnSpan: 12
}

export interface ReportChartBlock extends ReportDataBlock {
  kind: 'CHART'
  chartKind: ReportChartKind
  categoryPointer: string
  valuePointer: string
}

export interface ReportImageBlock extends ReportDataBlock {
  kind: 'IMAGE'
  sourcePointer: string
  alt: string
}

export type ReportBlockSpec = ReportTextBlock | ReportMetricBlock | ReportTableBlock | ReportChartBlock | ReportImageBlock

export interface ReportRowSpec {
  key: string
  blocks: ReportBlockSpec[]
}

export interface ReportDocument {
  version: 1
  name: string
  description: string | null
  page: ReportPageSpec
  parameters: ReportParameter[]
  datasets: ReportDatasetSpec[]
  rows: ReportRowSpec[]
}

export interface ReportPublicationView {
  reportKey: string
  publishedRevision: number
  document: ReportDocument
}

export interface ReportListItemView {
  reportKey: string
  revision: number
  name: string
  description: string | null
  publishedRevision: number | null
}

export interface ReportView {
  reportKey: string
  revision: number
  document: ReportDocument
  publishedRevision: number | null
}

export interface PublishedReportListItemView {
  reportKey: string
  publishedRevision: number
  name: string
  description: string | null
}

export interface PublishedReportView {
  reportKey: string
  publishedRevision: number
  document: ReportDocument
}

export interface PageResult<T> {
  rows: T[]
  totalRowCount: number
  totalPageCount: number
}

export type ReportDatasetResults = Record<string, unknown>

export interface ReportRunResult {
  values: ReportDatasetResults
  errors: Record<string, string>
}

export interface ReportSourceOption {
  key: string
  name: string
  fields: ReportDatasetField[]
  parameters: Array<{ name: string; required: boolean }>
}

export interface ReportSourceCatalog {
  models: Array<ReportSourceOption & { modelCode: string }>
  operations: Array<ReportSourceOption & { operationId: string; path: string }>
}

export const REPORT_GRID_COLUMNS = 12
export const MAX_REPORT_ROW_COUNT = 200

export function emptyReportDocument(name = '未命名报表'): ReportDocument {
  return {
    version: 1,
    name,
    description: null,
    page: { orientation: 'PORTRAIT', marginMm: 12 },
    parameters: [],
    datasets: [],
    rows: [{ key: createReportKey('row'), blocks: [] }],
  }
}

export function createReportKey(role: string): string {
  return `${role}-${crypto.randomUUID()}`
}
