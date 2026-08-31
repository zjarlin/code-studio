import { useQuery } from '@tanstack/react-query'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useMemo, useState } from 'react'

import { CatalogAction } from '@/components/catalog-action'
import { DataTable, type DataColumn } from '@/components/data-table'
import { PageHeader } from '@/components/page-header'
import { QueryState } from '@/components/query-state'
import type { CatalogPageProps } from '@/features/page-registry'
import {
  fetchPublishedReport,
  fetchPublishedReports,
} from '@/features/reports/api'
import type { PublishedReportListItemView, PublishedReportView } from '@/features/reports/models'
import { ReportParameterInput } from '@/features/reports/parameter-input'
import { ReportRenderer } from '@/features/reports/renderer'
import { runReportDatasets } from '@/features/reports/runner'

const columns: DataColumn<PublishedReportListItemView>[] = [
  { key: 'name', header: '名称' },
  { key: 'reportKey', header: '标识', cell: (value) => <code>{String(value)}</code> },
  { key: 'publishedRevision', header: '发布修订', width: '100px' },
  { key: 'description', header: '说明' },
]

export default function ReportLibraryPage({ route }: CatalogPageProps) {
  const { reportKey } = useSearch({ from: '/$' })
  return reportKey
    ? <PublishedReport route={route} reportKey={reportKey} />
    : <PublishedReportList route={route} />
}

function PublishedReportList({ route }: CatalogPageProps) {
  const navigate = useNavigate({ from: '/$' })
  const reports = useQuery({ queryKey: ['published-reports'], queryFn: fetchPublishedReports })
  const [query, setQuery] = useState('')
  const [selectedKey, setSelectedKey] = useState<string>()
  const rows = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase()
    if (!keyword) return reports.data?.rows ?? []
    return (reports.data?.rows ?? []).filter((report) =>
      `${report.name} ${report.reportKey} ${report.description ?? ''}`.toLocaleLowerCase().includes(keyword),
    )
  }, [query, reports.data?.rows])
  const selected = rows.find(({ reportKey }) => reportKey === selectedKey) ?? rows[0]

  return (
    <div className="page-frame">
      <PageHeader
        actions={<CatalogAction elementKey="reports.library.refresh" onClick={() => reports.refetch()} />}
        route={route}
      />
      <div className="toolbar">
        <input aria-label="搜索报表" onChange={(event) => setQuery(event.target.value)} placeholder="搜索名称、标识或说明" type="search" value={query} />
        <span>{rows.length} 项</span>
      </div>
      <div className="workspace-grid">
        <section className="workspace-main" aria-label="已发布报表">
          <QueryState error={reports.error} pending={reports.isPending}>
            <DataTable
              columns={columns}
              data={rows}
              emptyText="当前账号没有可查看的已发布报表"
              getRowId={(report) => report.reportKey}
              onRowClick={(report) => setSelectedKey(report.reportKey)}
              selectedRowId={selected?.reportKey}
            />
          </QueryState>
        </section>
        <aside className="inspector" aria-label="报表摘要">
          {selected ? (
            <>
              <div className="inspector-heading">
                <span className="eyebrow">已发布</span>
                <h2>{selected.name}</h2>
                <p>{selected.description || '未填写说明'}</p>
              </div>
              <dl className="definition-list">
                <div><dt>标识</dt><dd><code>{selected.reportKey}</code></dd></div>
                <div><dt>修订</dt><dd>{selected.publishedRevision}</dd></div>
              </dl>
              <CatalogAction
                className="inspector-action"
                elementKey="reports.library.open"
                onClick={() => navigate({ search: { reportKey: selected.reportKey } })}
                variant="primary"
              />
            </>
          ) : <div className="empty-state">选择报表查看摘要</div>}
        </aside>
      </div>
    </div>
  )
}

function PublishedReport({ reportKey, route }: CatalogPageProps & { reportKey: string }) {
  const navigate = useNavigate({ from: '/$' })
  const report = useQuery({ queryKey: ['published-report', reportKey], queryFn: () => fetchPublishedReport(reportKey) })
  return (
    <div className="page-frame report-viewer-page">
      <PageHeader
        actions={<CatalogAction elementKey="reports.library.back" onClick={() => navigate({ search: {} })} />}
        route={{ ...route, name: report.data?.document.name ?? route.name, description: report.data?.document.description }}
      />
      <QueryState error={report.error} pending={report.isPending}>
        {report.data ? <PublishedReportOutput report={report.data} /> : null}
      </QueryState>
    </div>
  )
}

function PublishedReportOutput({ report }: Readonly<{ report: PublishedReportView }>) {
  const initialValues = Object.fromEntries(report.document.parameters.map((parameter) => [parameter.key, parameter.defaultValue ?? '']))
  const [values, setValues] = useState<Record<string, string>>(initialValues)
  const [submitted, setSubmitted] = useState<Record<string, string>>(initialValues)
  const [runSequence, setRunSequence] = useState(0)
  const results = useQuery({
    queryKey: ['published-report-results', report.reportKey, report.publishedRevision, submitted, runSequence],
    queryFn: () => runReportDatasets(report.document, submitted),
    enabled: report.document.datasets.length > 0,
  })
  const datasetErrors = results.data?.errors ?? {}
  const printDisabled = results.isFetching || Boolean(results.error) || Object.keys(datasetErrors).length > 0
  return (
    <>
      <div className="report-runbar no-print">
        <div className="report-parameters">
          {report.document.parameters.map((parameter) => (
            <ReportParameterInput
              key={parameter.key}
              onChange={(value) => setValues((current) => ({ ...current, [parameter.key]: value }))}
              parameter={parameter}
              value={values[parameter.key] ?? ''}
            />
          ))}
        </div>
        <div className="page-actions">
          <CatalogAction elementKey="reports.library.run" onClick={() => { setSubmitted({ ...values }); setRunSequence((current) => current + 1) }} />
          <CatalogAction disabled={printDisabled} elementKey="reports.library.print" onClick={() => window.print()} />
        </div>
      </div>
      {results.error ? <div className="content-status content-status-error" role="alert">{results.error.message}</div> : null}
      <ReportRenderer document={report.document} errors={datasetErrors} results={results.data?.values ?? {}} />
    </>
  )
}
