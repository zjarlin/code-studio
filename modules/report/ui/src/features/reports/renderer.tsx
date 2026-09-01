import { lazy, Suspense, useEffect, useState, type CSSProperties } from 'react'

import { DataTable, type DataColumn } from '@/components/composed/data-table/data-table'
import { authenticatedFetch } from '@/lib/access-context'
import { useAccessContextGeneration } from '@/lib/access-context-sync'

import type { ReportBlockSpec, ReportDatasetResults, ReportDocument, ReportMetricBlock, ReportTableBlock } from './models'
import { resolveJsonPointer, rowsFromResult } from './runner'

const ReportChart = lazy(() => import('./chart').then((module) => ({ default: module.ReportChart })))

export function ReportRenderer({ document, errors = {}, results }: Readonly<{
  document: ReportDocument
  errors?: Record<string, string>
  results: ReportDatasetResults
}>) {
  const style = { '--report-margin': `${document.page.marginMm}mm` } as CSSProperties
  return (
    <article
      className={`report-sheet report-sheet-${document.page.orientation.toLowerCase()}`}
      aria-label={document.name}
      style={style}
    >
      <section className="report-paper">
        <header className="report-paper-heading">
          <h1>{document.name}</h1>
          {document.description ? <p>{document.description}</p> : null}
        </header>
        {document.rows.map((row) => (
          <div className={row.blocks.some(({ kind }) => kind === 'TABLE') ? 'report-grid-row report-grid-row-table' : 'report-grid-row'} key={row.key}>
            {row.blocks.map((block) => (
              <section
                className={`report-output report-output-${block.kind.toLowerCase()}`}
                key={block.key}
                style={{ gridColumn: `span ${block.columnSpan}` }}
              >
                {block.kind !== 'TEXT' && errors[block.datasetKey]
                  ? <div className="report-block-error" role="alert">{errors[block.datasetKey]}</div>
                  : <BlockOutput block={block} result={block.kind === 'TEXT' ? undefined : results[block.datasetKey]} />}
              </section>
            ))}
          </div>
        ))}
      </section>
    </article>
  )
}

function BlockOutput({ block, result }: Readonly<{ block: ReportBlockSpec; result: unknown }>) {
  if (block.kind === 'TEXT') return <p className="report-text">{block.text}</p>
  if (block.kind === 'METRIC') {
    return <div className="report-metric"><span>{block.label}</span><strong>{formatValue(metricValue(block, result))}</strong></div>
  }
  if (block.kind === 'TABLE') return <ReportTable block={block} result={result} />
  if (block.kind === 'CHART') {
    const rows = rowsFromResult(result)
    const categories = rows.map((row) => formatValue(resolveJsonPointer(row, block.categoryPointer)))
    const values = rows.map((row) => Number(resolveJsonPointer(row, block.valuePointer) ?? 0))
    return (
      <Suspense fallback={<div className="report-block-empty" aria-busy="true">正在加载图表…</div>}>
        <ReportChart categories={categories} kind={block.chartKind} values={values} />
      </Suspense>
    )
  }
  const firstRow = rowsFromResult(result)[0]
  const source = resolveJsonPointer(result, block.sourcePointer) ?? resolveJsonPointer(firstRow, block.sourcePointer)
  return typeof source === 'string' && source
    ? <AuthenticatedReportImage alt={block.alt} source={source} />
    : <div className="report-block-empty">暂无图片</div>
}

function metricValue(block: ReportMetricBlock, result: unknown): unknown {
  const rows = rowsFromResult(result)
  if (block.aggregate === 'COUNT') return rows.length
  const rootValue = resolveJsonPointer(result, block.valuePointer)
  if (block.aggregate === 'FIRST') return rootValue ?? resolveJsonPointer(rows[0], block.valuePointer)
  const values = rows
    .map((row) => Number(resolveJsonPointer(row, block.valuePointer)))
    .filter(Number.isFinite)
  if (!values.length) return undefined
  if (block.aggregate === 'SUM') return values.reduce((sum, value) => sum + value, 0)
  if (block.aggregate === 'AVG') return values.reduce((sum, value) => sum + value, 0) / values.length
  if (block.aggregate === 'MIN') return Math.min(...values)
  return Math.max(...values)
}

function ReportTable({ block, result }: Readonly<{ block: ReportTableBlock; result: unknown }>) {
  const sourceRows = rowsFromResult(result).slice(0, block.rowLimit)
  const rows = sourceRows.map((source, index) => ({
    rowKey: String(index),
    ...Object.fromEntries(block.columns.map((column) => [column.key, resolveJsonPointer(source, column.valuePointer)])),
  }))
  const columns: DataColumn<Record<string, unknown>>[] = block.columns.map((column) => ({
    key: column.key,
    header: column.label,
  }))
  return (
    <DataTable
      columns={columns}
      data={rows}
      emptyText="暂无数据"
      getRowId={(row) => String(row.rowKey)}
    />
  )
}

function AuthenticatedReportImage({ alt, source }: Readonly<{ alt: string; source: string }>) {
  const generation = useAccessContextGeneration()
  const [loaded, setLoaded] = useState<{ generation: number; source: string; url: string }>()
  const [failure, setFailure] = useState<{ generation: number; source: string; message: string }>()
  useEffect(() => {
    const controller = new AbortController()
    let objectUrl: string | undefined
    const load = async () => {
      try {
        if (!source.startsWith('/') || source.startsWith('//')) throw new Error('图片地址必须是同源相对路径')
        const target = new URL(source, window.location.origin)
        if (target.origin !== window.location.origin) throw new Error('图片地址必须与管理后台同源')
        const response = await authenticatedFetch(target, { signal: controller.signal })
        if (!response.ok) throw new Error(`图片请求失败：HTTP ${response.status}`)
        objectUrl = URL.createObjectURL(await response.blob())
        setLoaded({ generation, source, url: objectUrl })
      } catch (cause) {
        if (!controller.signal.aborted) {
          setFailure({ generation, source, message: cause instanceof Error ? cause.message : '图片请求失败' })
        }
      }
    }
    void load()
    return () => {
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [generation, source])
  const error = failure?.generation === generation && failure.source === source ? failure.message : undefined
  const url = loaded?.generation === generation && loaded.source === source ? loaded.url : undefined
  if (error) return <div className="report-block-empty" role="alert">{error}</div>
  return url
    ? <img className="report-image" alt={alt} src={url} />
    : <div className="report-block-empty" aria-busy="true">正在加载图片…</div>
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'number') return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 4 }).format(value)
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
