import type { Dispatch } from 'react'

import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import { createReportKey, type ReportBlockSpec, type ReportDatasetField, type ReportDocument, type ReportTableBlock } from '@/features/reports/models'
import type { ReportAction } from '@/features/reports/reducer'

export function ReportInspector({
  blockKey,
  dispatch,
  document,
  editable,
  persisted,
  reportKey,
  setReportKey,
}: Readonly<{
  blockKey?: string
  dispatch: Dispatch<ReportAction>
  document: ReportDocument
  editable: boolean
  persisted: boolean
  reportKey: string
  setReportKey: (value: string) => void
}>) {
  const block = document.rows.flatMap((row) => row.blocks).find(({ key }) => key === blockKey)
  if (!block) {
    return (
      <div className="designer-properties">
        <h2>报表设置</h2>
        <label>标识<input disabled={!editable || persisted} onChange={(event) => setReportKey(event.target.value)} value={reportKey} /></label>
        <label>名称<input disabled={!editable} onChange={(event) => dispatch({ type: 'updateDocument', name: event.target.value, description: document.description })} value={document.name} /></label>
        <label>说明<textarea disabled={!editable} onChange={(event) => dispatch({ type: 'updateDocument', name: document.name, description: event.target.value.trim() ? event.target.value : null })} value={document.description ?? ''} /></label>
        <label>页面方向<select disabled={!editable} onChange={(event) => dispatch({ type: 'updatePage', page: { ...document.page, orientation: event.target.value as ReportDocument['page']['orientation'] } })} value={document.page.orientation}><option value="PORTRAIT">纵向</option><option value="LANDSCAPE">横向</option></select></label>
        <label>页边距<select disabled={!editable} onChange={(event) => dispatch({ type: 'updatePage', page: { ...document.page, marginMm: Number(event.target.value) as 8 | 12 | 20 } })} value={document.page.marginMm}><option value={8}>8 mm</option><option value={12}>12 mm</option><option value={20}>20 mm</option></select></label>
      </div>
    )
  }

  const replace = (next: ReportBlockSpec) => dispatch({ type: 'replaceBlock', block: next })
  return (
    <div className="designer-properties">
      <h2>{blockLabel(block)}</h2>
      {block.kind === 'TABLE' ? <label>列跨度<input disabled type="number" value={12} /></label> : <label>列跨度<input disabled={!editable} max={12} min={1} onChange={(event) => dispatch({ type: 'resizeBlock', blockKey: block.key, columnSpan: Number(event.target.value) })} type="number" value={block.columnSpan} /></label>}
      {block.kind === 'TEXT' ? (
        <label>文本<textarea disabled={!editable} onChange={(event) => replace({ ...block, text: event.target.value })} value={block.text} /></label>
      ) : (
        <DatasetSelect block={block} document={document} disabled={!editable} onChange={replace} />
      )}
      {block.kind === 'METRIC' ? (
        <>
          <label>标签<input disabled={!editable} onChange={(event) => replace({ ...block, label: event.target.value })} value={block.label} /></label>
          <label>聚合<select disabled={!editable} onChange={(event) => replace({ ...block, aggregate: event.target.value as typeof block.aggregate })} value={block.aggregate}><option value="FIRST">FIRST</option><option value="COUNT">COUNT</option><option value="SUM">SUM</option><option value="AVG">AVG</option><option value="MIN">MIN</option><option value="MAX">MAX</option></select></label>
          {block.aggregate !== 'COUNT' ? <label>值指针<input disabled={!editable} onChange={(event) => replace({ ...block, valuePointer: event.target.value })} value={block.valuePointer} /></label> : null}
        </>
      ) : null}
      {block.kind === 'TABLE' ? (
        <TableProperties
          block={block}
          disabled={!editable}
          fields={document.datasets.find(({ key }) => key === block.datasetKey)?.fields ?? []}
          onChange={replace}
        />
      ) : null}
      {block.kind === 'CHART' ? (
        <>
          <label>图表<select disabled={!editable} onChange={(event) => replace({ ...block, chartKind: event.target.value as typeof block.chartKind })} value={block.chartKind}><option value="BAR">柱状图</option><option value="LINE">折线图</option><option value="PIE">饼图</option></select></label>
          <label>分类指针<input disabled={!editable} onChange={(event) => replace({ ...block, categoryPointer: event.target.value })} value={block.categoryPointer} /></label>
          <label>数值指针<input disabled={!editable} onChange={(event) => replace({ ...block, valuePointer: event.target.value })} value={block.valuePointer} /></label>
        </>
      ) : null}
      {block.kind === 'IMAGE' ? (
        <>
          <label>地址指针<input disabled={!editable} onChange={(event) => replace({ ...block, sourcePointer: event.target.value })} value={block.sourcePointer} /></label>
          <label>替代文本<input disabled={!editable} onChange={(event) => replace({ ...block, alt: event.target.value })} value={block.alt} /></label>
        </>
      ) : null}
    </div>
  )
}

function DatasetSelect({ block, disabled, document, onChange }: Readonly<{
  block: Exclude<ReportBlockSpec, { kind: 'TEXT' }>
  disabled: boolean
  document: ReportDocument
  onChange: (block: ReportBlockSpec) => void
}>) {
  return (
    <label>数据集<select disabled={disabled} onChange={(event) => onChange({ ...block, datasetKey: event.target.value })} value={block.datasetKey}>
      {document.datasets.map((dataset) => <option key={dataset.key} value={dataset.key}>{dataset.name}</option>)}
    </select></label>
  )
}

function TableProperties({ block, disabled, fields, onChange }: Readonly<{
  block: ReportTableBlock
  disabled: boolean
  fields: ReportDatasetField[]
  onChange: (block: ReportBlockSpec) => void
}>) {
  const addColumn = () => {
    const field = fields.find((candidate) => !block.columns.some(({ key }) => key === candidate.key))
    const column = field
      ? { key: field.key, label: field.label, valuePointer: field.pointer }
      : { key: createReportKey('column'), label: '新列', valuePointer: '' }
    onChange({ ...block, columns: [...block.columns, column] })
  }
  return (
    <>
      <label>最大行数<input disabled={disabled} max={200} min={1} onChange={(event) => onChange({ ...block, rowLimit: Math.max(1, Math.min(200, Number(event.target.value) || 1)) })} type="number" value={block.rowLimit} /></label>
      <div className="table-column-heading"><span>表格列</span><CatalogIconAction disabled={disabled} elementKey="studio.report-designer.table.column.add" onClick={addColumn} /></div>
      {block.columns.map((column) => (
        <div className="table-column-editor" key={column.key}>
          <input aria-label="列名称" disabled={disabled} onChange={(event) => onChange({ ...block, columns: block.columns.map((item) => item.key === column.key ? { ...item, label: event.target.value } : item) })} value={column.label} />
          <input aria-label="列字段" disabled={disabled} list={`field-${block.key}`} onChange={(event) => onChange({ ...block, columns: block.columns.map((item) => item.key === column.key ? { ...item, valuePointer: event.target.value } : item) })} value={column.valuePointer} />
          <CatalogIconAction disabled={disabled} elementKey="studio.report-designer.table.column.remove" onClick={() => onChange({ ...block, columns: block.columns.filter(({ key }) => key !== column.key) })} />
        </div>
      ))}
      <datalist id={`field-${block.key}`}>{fields.map((field) => <option key={field.key} value={field.pointer}>{field.label}</option>)}</datalist>
    </>
  )
}

function blockLabel(block: ReportBlockSpec): string {
  return ({ TEXT: '文本属性', METRIC: '指标属性', TABLE: '表格属性', CHART: '图表属性', IMAGE: '图片属性' })[block.kind]
}
