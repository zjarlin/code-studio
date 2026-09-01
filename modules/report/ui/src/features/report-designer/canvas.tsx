import { useDraggable, useDroppable } from '@dnd-kit/react'
import { useEffect, useRef, useState, type Dispatch, type PointerEvent as ReactPointerEvent } from 'react'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import { CatalogIcon } from '@/components/composed/catalog-icon/catalog-icon'
import type { ReportBlockSpec, ReportDocument } from '@/features/reports/models'
import type { ReportAction } from '@/features/reports/reducer'

const A4_PORTRAIT_WIDTH = 794
const A4_PORTRAIT_HEIGHT = 1123

export function DesignerCanvas({ dispatch, document, editable, onSelect, selectedBlockKey }: Readonly<{
  dispatch: Dispatch<ReportAction>
  document: ReportDocument
  editable: boolean
  onSelect: (blockKey?: string) => void
  selectedBlockKey?: string
}>) {
  const viewportRef = useRef<HTMLDivElement>(null)
  const [zoom, setZoom] = useState(0.75)
  const landscape = document.page.orientation === 'LANDSCAPE'
  const pageWidth = landscape ? A4_PORTRAIT_HEIGHT : A4_PORTRAIT_WIDTH
  const pageHeight = landscape ? A4_PORTRAIT_WIDTH : A4_PORTRAIT_HEIGHT
  const updateZoom = (value: number) => setZoom(Math.max(0.25, Math.min(2, value)))
  const fit = () => {
    const width = viewportRef.current?.clientWidth ?? pageWidth
    updateZoom((width - 48) / pageWidth)
  }

  return (
    <section className="designer-stage">
      <header className="designer-canvas-toolbar no-print">
        <CatalogAction disabled={!editable} elementKey="studio.report-designer.row.add" onClick={() => dispatch({ type: 'addRow' })} />
        <span className="toolbar-spacer" />
        <CatalogIconAction elementKey="studio.report-designer.zoom.out" onClick={() => updateZoom(zoom - 0.1)} />
        <span className="zoom-value">{Math.round(zoom * 100)}%</span>
        <CatalogIconAction elementKey="studio.report-designer.zoom.in" onClick={() => updateZoom(zoom + 0.1)} />
        <CatalogIconAction elementKey="studio.report-designer.zoom.reset" onClick={() => updateZoom(1)} />
        <CatalogIconAction elementKey="studio.report-designer.zoom.fit" onClick={fit} />
      </header>
      <div className="designer-canvas-viewport" ref={viewportRef}>
        <div className="designer-canvas-scale" style={{ width: pageWidth * zoom, minHeight: pageHeight * zoom }}>
          <article
            aria-label="A4 报表画布"
            className={`designer-a4-canvas${landscape ? ' is-landscape' : ''}`}
            onClick={(event) => event.target === event.currentTarget && onSelect(undefined)}
            style={{
              minHeight: pageHeight,
              padding: `${document.page.marginMm * 96 / 25.4}px`,
              transform: `scale(${zoom})`,
              width: pageWidth,
            }}
          >
            <header className="designer-document-heading">
              <h1>{document.name || '未命名报表'}</h1>
              {document.description ? <p>{document.description}</p> : null}
            </header>
            {document.rows.map((row) => (
              <DesignerRow
                dispatch={dispatch}
                editable={editable}
                key={row.key}
                onSelect={onSelect}
                row={row}
                selectedBlockKey={selectedBlockKey}
              />
            ))}
          </article>
        </div>
      </div>
    </section>
  )
}

function DesignerRow({ dispatch, editable, onSelect, row, selectedBlockKey }: Readonly<{
  dispatch: Dispatch<ReportAction>
  editable: boolean
  onSelect: (blockKey?: string) => void
  row: ReportDocument['rows'][number]
  selectedBlockKey?: string
}>) {
  const { isDropTarget, ref } = useDroppable({ id: row.key, data: { rowKey: row.key }, disabled: !editable })
  return (
    <div className={isDropTarget ? 'report-grid-editor-row is-drop-target' : 'report-grid-editor-row'} ref={ref}>
      {row.blocks.map((block) => (
        <DesignerBlock
          block={block}
          dispatch={dispatch}
          editable={editable}
          key={block.key}
          onSelect={onSelect}
          selected={block.key === selectedBlockKey}
        />
      ))}
      {!row.blocks.length ? (
        <>
          <div className="designer-empty-row">空行</div>
          {editable ? (
            <span className="designer-empty-row-action">
              <CatalogIconAction
                elementKey="studio.report-designer.row.delete"
                onClick={() => dispatch({ type: 'deleteRow', rowKey: row.key })}
              />
            </span>
          ) : null}
        </>
      ) : null}
    </div>
  )
}

function DesignerBlock({ block, dispatch, editable, onSelect, selected }: Readonly<{
  block: ReportBlockSpec
  dispatch: Dispatch<ReportAction>
  editable: boolean
  onSelect: (blockKey?: string) => void
  selected: boolean
}>) {
  const { handleRef, isDragging, ref } = useDraggable({
    id: block.key,
    data: { sourceType: 'block', blockKey: block.key },
    disabled: !editable,
  })
  return (
    <section
      aria-label={blockTitle(block)}
      className={`designer-block${isDragging ? ' is-dragging' : ''}`}
      data-selected={selected}
      onClick={(event) => {
        event.stopPropagation()
        onSelect(block.key)
      }}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') onSelect(block.key)
        if (event.key === 'Delete' && editable) dispatch({ type: 'deleteBlock', blockKey: block.key })
      }}
      ref={ref}
      role="group"
      style={{ gridColumn: `span ${block.columnSpan}` }}
      tabIndex={0}
    >
      {editable ? <span aria-label="拖动组件" className="designer-drag-handle" ref={handleRef} role="button" tabIndex={0}><CatalogIcon name="grip-vertical" /></span> : null}
      <span className="designer-block-kind">{block.kind}</span>
      <strong>{blockTitle(block)}</strong>
      <small>{blockDetail(block)}</small>
      {editable && block.kind !== 'TABLE' ? <ResizeHandle block={block} dispatch={dispatch} /> : null}
    </section>
  )
}

function ResizeHandle({ block, dispatch }: Readonly<{ block: ReportBlockSpec; dispatch: Dispatch<ReportAction> }>) {
  const handleRef = useRef<HTMLSpanElement>(null)
  const startRef = useRef<{ pointerX: number; span: number; columnWidth: number } | undefined>(undefined)
  const previewRef = useRef(block.columnSpan)
  useEffect(() => {
    previewRef.current = block.columnSpan
  }, [block.columnSpan])
  const finish = () => {
    if (!startRef.current) return
    dispatch({ type: 'resizeBlock', blockKey: block.key, columnSpan: previewRef.current })
    startRef.current = undefined
  }
  const start = (event: ReactPointerEvent<HTMLSpanElement>) => {
    event.stopPropagation()
    const rowWidth = handleRef.current?.closest('.report-grid-editor-row')?.clientWidth ?? A4_PORTRAIT_WIDTH
    startRef.current = { pointerX: event.clientX, span: block.columnSpan, columnWidth: rowWidth / 12 }
    event.currentTarget.setPointerCapture(event.pointerId)
  }
  const move = (event: ReactPointerEvent<HTMLSpanElement>) => {
    const startState = startRef.current
    if (!startState) return
    const delta = Math.round((event.clientX - startState.pointerX) / startState.columnWidth)
    const nextSpan = Math.max(1, Math.min(12, startState.span + delta))
    previewRef.current = nextSpan
    const blockElement = event.currentTarget.closest<HTMLElement>('.designer-block')
    if (blockElement) blockElement.style.gridColumn = `span ${nextSpan}`
  }
  return (
    <span
      aria-label="调整列跨度"
      className="designer-resize-handle"
      onKeyDown={(event) => {
        if (event.key === 'ArrowLeft') dispatch({ type: 'resizeBlock', blockKey: block.key, columnSpan: block.columnSpan - 1 })
        if (event.key === 'ArrowRight') dispatch({ type: 'resizeBlock', blockKey: block.key, columnSpan: block.columnSpan + 1 })
      }}
      onPointerDown={start}
      onPointerMove={move}
      onPointerUp={finish}
      ref={handleRef}
      role="separator"
      tabIndex={0}
    />
  )
}

function blockTitle(block: ReportBlockSpec): string {
  if (block.kind === 'TEXT') return block.text || '文本'
  if (block.kind === 'METRIC') return block.label || '指标'
  if (block.kind === 'TABLE') return '数据表格'
  if (block.kind === 'CHART') return `${block.chartKind} 图表`
  return block.alt || '数据图片'
}

function blockDetail(block: ReportBlockSpec): string {
  if (block.kind === 'TEXT') return `${block.columnSpan} 列`
  if (block.kind === 'TABLE') return `${block.columns.length} 列字段 · 最多 ${Math.min(block.rowLimit, 200)} 行`
  if (block.kind === 'CHART') return `${block.categoryPointer} · ${block.valuePointer}`
  if (block.kind === 'METRIC') return `${block.aggregate} · ${block.valuePointer}`
  return block.sourcePointer
}
