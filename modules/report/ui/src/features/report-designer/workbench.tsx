import { DragDropProvider, type DragEndEvent } from '@dnd-kit/react'
import { useState, type Dispatch } from 'react'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import type { ReportBlockKind, ReportDocument } from '@/features/reports/models'
import type { ReportAction } from '@/features/reports/reducer'

import { DesignerCanvas } from './canvas'
import { ReportInspector } from './inspector'
import { DesignerToolPanel } from './tool-panel'

interface DragData {
  sourceType?: 'palette' | 'block'
  blockKind?: ReportBlockKind
  blockKey?: string
  rowKey?: string
}

export function DesignerWorkbench({ dispatch, document, editable, persisted, reportKey, setReportKey }: Readonly<{
  dispatch: Dispatch<ReportAction>
  document: ReportDocument
  editable: boolean
  persisted: boolean
  reportKey: string
  setReportKey: (value: string) => void
}>) {
  const [activeTool, setActiveTool] = useState<'components' | 'sources'>('components')
  const [selectedBlockKey, setSelectedBlockKey] = useState<string>()
  const [drawer, setDrawer] = useState<'tools' | 'inspector'>()
  const onDragEnd = (event: DragEndEvent) => {
    if (event.canceled) return
    const source = event.operation.source?.data as DragData | undefined
    const target = event.operation.target?.data as DragData | undefined
    if (!target?.rowKey) return
    if (source?.sourceType === 'palette' && source.blockKind) {
      dispatch({ type: 'addBlock', rowKey: target.rowKey, blockKind: source.blockKind })
    }
    if (source?.sourceType === 'block' && source.blockKey) {
      dispatch({ type: 'moveBlock', blockKey: source.blockKey, targetRowKey: target.rowKey })
    }
  }

  return (
    <DragDropProvider onDragEnd={onDragEnd}>
      <div className="designer-workbench">
        <aside className={`designer-tools${drawer === 'tools' ? ' is-open' : ''}`}>
          <div className="designer-panel-tabs" role="tablist" aria-label="设计资源">
            <CatalogAction
              aria-selected={activeTool === 'components'}
              className={activeTool === 'components' ? 'is-active' : ''}
              elementKey="studio.report-designer.panel.components"
              onClick={() => setActiveTool('components')}
              role="tab"
              variant="ghost"
            />
            <CatalogAction
              aria-selected={activeTool === 'sources'}
              className={activeTool === 'sources' ? 'is-active' : ''}
              elementKey="studio.report-designer.panel.sources"
              onClick={() => setActiveTool('sources')}
              role="tab"
              variant="ghost"
            />
          </div>
          <DesignerToolPanel active={activeTool} dispatch={dispatch} document={document} editable={editable} />
        </aside>
        <DesignerCanvas
          dispatch={dispatch}
          document={document}
          editable={editable}
          onSelect={(blockKey) => {
            setSelectedBlockKey(blockKey)
            if (blockKey) setDrawer('inspector')
          }}
          selectedBlockKey={selectedBlockKey}
        />
        <aside className={`designer-inspector${drawer === 'inspector' ? ' is-open' : ''}`}>
          <ReportInspector
            blockKey={selectedBlockKey}
            dispatch={dispatch}
            document={document}
            editable={editable}
            persisted={persisted}
            reportKey={reportKey}
            setReportKey={setReportKey}
          />
          {selectedBlockKey ? (
            <CatalogAction
              disabled={!editable}
              elementKey="studio.report-designer.widget.delete"
              onClick={() => {
                dispatch({ type: 'deleteBlock', blockKey: selectedBlockKey })
                setSelectedBlockKey(undefined)
              }}
              variant="ghost"
            />
          ) : null}
        </aside>
        {drawer ? <div aria-hidden="true" className="designer-drawer-backdrop" onClick={() => setDrawer(undefined)} /> : null}
        <div className="designer-mobile-dock no-print">
          <CatalogIconAction elementKey="studio.report-designer.panel.components" onClick={() => setDrawer(drawer === 'tools' ? undefined : 'tools')} />
          <CatalogIconAction elementKey="studio.report-designer.panel.inspector" onClick={() => setDrawer(drawer === 'inspector' ? undefined : 'inspector')} />
        </div>
      </div>
    </DragDropProvider>
  )
}
