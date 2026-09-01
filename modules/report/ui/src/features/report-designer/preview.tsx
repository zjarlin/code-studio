import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import type { ReportDocument } from '@/features/reports/models'
import { ReportParameterInput } from '@/features/reports/parameter-input'
import { ReportRenderer } from '@/features/reports/renderer'
import { runReportDatasets } from '@/features/reports/runner'

export function DesignerPreview({ document, onBack }: Readonly<{ document: ReportDocument; onBack: () => void }>) {
  const defaults = Object.fromEntries(document.parameters.map((parameter) => [parameter.key, parameter.defaultValue ?? '']))
  const [values, setValues] = useState<Record<string, string>>(defaults)
  const [submitted, setSubmitted] = useState<Record<string, string>>(defaults)
  const [runSequence, setRunSequence] = useState(0)
  const results = useQuery({
    queryKey: ['report-preview', document, submitted, runSequence],
    queryFn: () => runReportDatasets(document, submitted),
    enabled: document.datasets.length > 0,
  })
  const datasetErrors = results.data?.errors ?? {}
  const printDisabled = results.isFetching || Boolean(results.error) || Object.keys(datasetErrors).length > 0
  return (
    <div className="designer-preview">
      <header className="report-runbar no-print">
        <CatalogAction elementKey="studio.report-designer.preview.back" onClick={onBack} />
        <div className="report-parameters">
          {document.parameters.map((parameter) => (
            <ReportParameterInput
              key={parameter.key}
              onChange={(value) => setValues((current) => ({ ...current, [parameter.key]: value }))}
              parameter={parameter}
              value={values[parameter.key] ?? ''}
            />
          ))}
        </div>
        <div className="page-actions">
          <CatalogAction elementKey="studio.report-designer.preview.run" onClick={() => { setSubmitted({ ...values }); setRunSequence((current) => current + 1) }} />
          <CatalogAction disabled={printDisabled} elementKey="studio.report-designer.preview.print" onClick={() => window.print()} />
        </div>
      </header>
      {results.error ? <div className="content-status content-status-error" role="alert">{results.error.message}</div> : null}
      {Object.entries(datasetErrors).map(([datasetKey, message]) => <div className="dataset-error" key={datasetKey} role="alert"><strong>{datasetKey}</strong><span>{message}</span></div>)}
      <ReportRenderer document={document} results={results.data?.values ?? {}} />
    </div>
  )
}
