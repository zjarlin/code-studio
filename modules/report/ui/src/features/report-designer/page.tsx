import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useBlocker, useNavigate, useSearch } from '@tanstack/react-router'
import { useReducer, useRef, useState } from 'react'

import { useCatalog } from '@/catalog/context'
import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import { QueryState } from '@/components/composed/query-state/query-state'
import type { CatalogPageProps } from '@/features/page-registry'
import {
  createReport,
  deleteReport,
  fetchReport,
  fetchReports,
  ReportPublicationError,
  saveAndPublishReport,
  unpublishReport,
  updateReport,
} from '@/features/reports/operations'
import { emptyReportDocument, type ReportDocument, type ReportListItemView, type ReportView } from '@/features/reports/models'
import { createReportHistory, reportReducer } from '@/features/reports/reducer'
import { validateReportForPublish, validateReportKey } from '@/features/reports/validation'

import { DesignerPreview } from './preview'
import { DesignerWorkbench } from './workbench'

export default function ReportDesignerPage({ route }: CatalogPageProps) {
  const { mode = 'edit', reportKey } = useSearch({ from: '/$' })
  const persistedReportKey = reportKey && reportKey !== 'new' ? reportKey : undefined
  const reports = useQuery({ queryKey: ['reports'], queryFn: fetchReports })
  const report = useQuery({
    queryKey: ['report', persistedReportKey],
    queryFn: () => fetchReport(persistedReportKey!),
    enabled: Boolean(persistedReportKey),
  })
  const [newDocument] = useState(() => emptyReportDocument())

  if (persistedReportKey && report.isPending) return <div className="content-status" aria-busy="true">正在读取报表草稿…</div>
  if (report.error) return <div className="content-status content-status-error" role="alert">{report.error.message}</div>
  const initial = report.data ?? { reportKey: '', revision: 0, document: newDocument, publishedRevision: null }
  return (
    <DesignerSession
      initial={initial}
      key={`${initial.reportKey}:${initial.revision}`}
      mode={mode}
      reportOptions={reports.data?.rows ?? []}
      routeName={route.name}
    />
  )
}

export function DesignerSession({ initial, mode, reportOptions, routeName }: Readonly<{
  initial: ReportView
  mode: 'edit' | 'preview'
  reportOptions: ReportListItemView[]
  routeName: string
}>) {
  const navigate = useNavigate({ from: '/$' })
  const queryClient = useQueryClient()
  const catalog = useCatalog()
  const [history, dispatch] = useReducer(reportReducer, initial.document, createReportHistory)
  const [reportKey, setReportKey] = useState(initial.reportKey)
  const [revision, setRevision] = useState(initial.revision)
  const [publishedRevision, setPublishedRevision] = useState(initial.publishedRevision)
  const [draftSelection, setDraftSelection] = useState(initial.reportKey || 'new')
  const [issue, setIssue] = useState<string>()
  const baseline = useRef<ReportDocument>(initial.document)
  const dirty = history.present !== baseline.current || (revision === 0 && reportKey.trim() !== initial.reportKey)
  const editable = catalog.elementsByKey.has(revision > 0 ? 'studio.report-designer.save' : 'studio.report-designer.create')

  const acceptSaved = (saved: ReportView, document: ReportDocument) => {
    baseline.current = document
    setRevision(saved.revision)
    setPublishedRevision(saved.publishedRevision)
    setReportKey(saved.reportKey)
    setDraftSelection(saved.reportKey)
    queryClient.setQueryData(['report', saved.reportKey], saved)
    queryClient.invalidateQueries({ queryKey: ['reports'] })
  }

  useBlocker({
    shouldBlockFn: () => dirty && !window.confirm('当前修改尚未保存，确定离开吗？'),
    enableBeforeUnload: dirty,
    disabled: !dirty,
  })

  const saveDraft = useMutation({
    mutationFn: async (input: { document: ReportDocument; reportKey: string; revision: number }) => {
      const keyError = validateReportKey(input.reportKey)
      if (keyError) throw new Error(keyError)
      if (!input.document.name.trim()) throw new Error('报表名称不能为空')
      return input.revision > 0
        ? updateReport(input.reportKey, input.revision, input.document)
        : createReport(input.reportKey, input.document)
    },
    onSuccess: (saved, input) => {
      acceptSaved(saved, input.document)
      setIssue(undefined)
      navigate({ search: { reportKey: saved.reportKey, mode: 'edit' }, replace: true, ignoreBlocker: true })
    },
    onError: (error) => setIssue(error.message),
  })
  const publish = useMutation({
    mutationFn: async (input: {
      document: ReportDocument
      reportKey: string
      revision: number
      saveRequired: boolean
      publishedRevision: number | null
    }) => {
      const keyError = validateReportKey(input.reportKey)
      if (keyError) throw new Error(keyError)
      const errors = validateReportForPublish(input.document)
      if (errors.length) throw new Error(errors.join('；'))
      return saveAndPublishReport(input)
    },
    onSuccess: (saved, input) => {
      acceptSaved(saved, input.document)
      setIssue(undefined)
      queryClient.invalidateQueries({ queryKey: ['published-reports'] })
      navigate({ search: { reportKey: saved.reportKey, mode: 'edit' }, replace: true, ignoreBlocker: true })
    },
    onError: (error, input) => {
      if (error instanceof ReportPublicationError) {
        acceptSaved(error.savedReport, input.document)
      }
      setIssue(error.message)
    },
  })
  const unpublish = useMutation({
    mutationFn: () => unpublishReport(reportKey),
    onSuccess: () => {
      setPublishedRevision(null)
      setIssue(undefined)
      queryClient.setQueryData<ReportView>(['report', reportKey], (current) =>
        current ? { ...current, publishedRevision: null } : current,
      )
      queryClient.invalidateQueries({ queryKey: ['reports'] })
      queryClient.invalidateQueries({ queryKey: ['published-reports'] })
    },
    onError: (error) => setIssue(error.message),
  })
  const remove = useMutation({
    mutationFn: () => deleteReport(reportKey),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['report', reportKey] })
      queryClient.invalidateQueries({ queryKey: ['reports'] })
      navigate({ search: { reportKey: 'new', mode: 'edit' }, replace: true, ignoreBlocker: true })
    },
    onError: (error) => setIssue(error.message),
  })

  const switchReport = (nextReportKey: string) => {
    navigate({ search: { reportKey: nextReportKey || 'new', mode: 'edit' } })
  }
  const canEdit = editable && !saveDraft.isPending && !publish.isPending
  if (mode === 'preview') {
    return <DesignerPreview document={history.present} onBack={() => navigate({ search: { reportKey: reportKey || 'new', mode: 'edit' }, ignoreBlocker: true })} />
  }

  return (
    <div className="designer-page">
      <header className="designer-commandbar no-print">
        <div className="designer-title">
          <select aria-label="选择报表草稿" onChange={(event) => setDraftSelection(event.target.value)} value={draftSelection}>
            <option value="new">新建报表</option>
            {reportOptions.map((report) => <option key={report.reportKey} value={report.reportKey}>{report.name}</option>)}
          </select>
          <CatalogIconAction elementKey="studio.report-designer.open" onClick={() => switchReport(draftSelection)} />
          <span>{routeName}</span>
          <small>{dirty ? '未保存' : publishedRevision === revision ? '已发布' : publishedRevision ? '有未发布修改' : '草稿'}</small>
        </div>
        <div className="designer-command-actions">
          <CatalogIconAction disabled={!history.past.length || !canEdit} elementKey="studio.report-designer.undo" onClick={() => dispatch({ type: 'undo' })} />
          <CatalogIconAction disabled={!history.future.length || !canEdit} elementKey="studio.report-designer.redo" onClick={() => dispatch({ type: 'redo' })} />
          <CatalogAction elementKey="studio.report-designer.preview" onClick={() => navigate({ search: { reportKey: reportKey || 'new', mode: 'preview' }, ignoreBlocker: true })} />
          <CatalogAction
            disabled={!canEdit}
            elementKey={revision > 0 ? 'studio.report-designer.save' : 'studio.report-designer.create'}
            onClick={() => saveDraft.mutate({ document: history.present, reportKey: reportKey.trim(), revision })}
            variant="primary"
          />
          <CatalogAction
            disabled={publish.isPending || ((dirty || revision === 0) && !editable)}
            elementKey="studio.report-designer.publish"
            onClick={() => publish.mutate({
              document: history.present,
              reportKey: reportKey.trim(),
              revision,
              saveRequired: dirty || revision === 0,
              publishedRevision,
            })}
          />
          {publishedRevision ? <CatalogAction disabled={unpublish.isPending} elementKey="studio.report-designer.unpublish" onClick={() => unpublish.mutate()} variant="ghost" /> : null}
          {revision > 0 ? <CatalogIconAction disabled={remove.isPending} elementKey="studio.report-designer.delete" onClick={() => window.confirm('确定删除当前报表吗？') && remove.mutate()} /> : null}
        </div>
      </header>
      {issue ? <div className="designer-issue" role="alert">{issue}</div> : null}
      <QueryState error={null} pending={false}>
        <DesignerWorkbench dispatch={dispatch} document={history.present} editable={canEdit} persisted={revision > 0} reportKey={reportKey} setReportKey={setReportKey} />
      </QueryState>
    </div>
  )
}
