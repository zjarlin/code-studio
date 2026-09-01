import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useBlocker, useNavigate, useSearch } from '@tanstack/react-router'
import { useReducer, useRef, useState } from 'react'

import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import type { CatalogPageProps } from '@/features/page-registry'
import {
  deleteSpreadsheetTemplate,
  fetchSpreadsheetTemplate,
  fetchSpreadsheetTemplates,
  fillSpreadsheetTemplate,
  importSpreadsheetTemplate,
  updateSpreadsheetTemplate,
} from '@/features/spreadsheet-templates/operations'
import { downloadBlob } from '@/features/spreadsheet-templates/download'
import {
  createSpreadsheetTemplateHistory,
  spreadsheetTemplateHistoryReducer,
} from '@/features/spreadsheet-templates/history'
import type {
  SpreadsheetCellSelection,
  SpreadsheetTemplateDocument,
  SpreadsheetTemplateFillCommand,
  SpreadsheetTemplateListItemView,
  SpreadsheetTemplateView,
} from '@/features/spreadsheet-templates/models'

import { SpreadsheetCanvas } from './canvas'
import { SpreadsheetFillDialog } from './fill-dialog'
import { SpreadsheetInspector } from './inspector'
import { SpreadsheetToolPanel } from './tool-panel'

export default function SpreadsheetTemplateDesignerPage({ route }: CatalogPageProps) {
  const { templateKey } = useSearch({ from: '/$' })
  const templates = useQuery({ queryKey: ['spreadsheet-templates'], queryFn: fetchSpreadsheetTemplates })
  const template = useQuery({
    queryKey: ['spreadsheet-template', templateKey],
    queryFn: () => fetchSpreadsheetTemplate(templateKey!),
    enabled: Boolean(templateKey),
  })
  if (templateKey && template.isPending) return <div className="content-status" aria-busy="true">正在读取 Excel 模板…</div>
  if (template.error) return <div className="content-status content-status-error" role="alert">{template.error.message}</div>
  return (
    <SpreadsheetTemplateSession
      initial={template.data}
      key={`${template.data?.templateKey ?? 'new'}:${template.data?.revision ?? 0}`}
      routeName={route.name}
      templateOptions={templates.data?.rows ?? []}
    />
  )
}

function SpreadsheetTemplateSession({ initial, routeName, templateOptions }: Readonly<{
  initial?: SpreadsheetTemplateView
  routeName: string
  templateOptions: SpreadsheetTemplateListItemView[]
}>) {
  const navigate = useNavigate({ from: '/$' })
  const queryClient = useQueryClient()
  const fileInput = useRef<HTMLInputElement>(null)
  const baseline = useRef<SpreadsheetTemplateDocument | undefined>(initial?.document)
  const [history, dispatch] = useReducer(
    spreadsheetTemplateHistoryReducer,
    initial?.document ?? EMPTY_DOCUMENT,
    createSpreadsheetTemplateHistory,
  )
  const [draftSelection, setDraftSelection] = useState(initial?.templateKey ?? '')
  const [templateKey, setTemplateKey] = useState('')
  const [templateName, setTemplateName] = useState('')
  const [sheetKey, setSheetKey] = useState(initial?.document.sheets[0]?.key ?? '')
  const [selection, setSelection] = useState<SpreadsheetCellSelection>({
    sheetKey: initial?.document.sheets[0]?.key ?? '', row: 0, column: 0,
  })
  const [issue, setIssue] = useState<string>()
  const [fillOpen, setFillOpen] = useState(false)
  const [leftOpen, setLeftOpen] = useState(false)
  const [rightOpen, setRightOpen] = useState(false)
  const document = initial ? history.present : undefined
  const dirty = Boolean(document && baseline.current && document !== baseline.current)

  useBlocker({
    shouldBlockFn: () => dirty && !window.confirm('当前模板修改尚未保存，确定离开吗？'),
    enableBeforeUnload: dirty,
    disabled: !dirty,
  })

  const importer = useMutation({
    mutationFn: (file: File) => importSpreadsheetTemplate({ templateKey: templateKey.trim(), name: templateName.trim(), file }),
    onSuccess: (saved) => {
      setIssue(undefined)
      queryClient.setQueryData(['spreadsheet-template', saved.templateKey], saved)
      queryClient.invalidateQueries({ queryKey: ['spreadsheet-templates'] })
      navigate({ search: { templateKey: saved.templateKey }, replace: true })
    },
    onError: (error) => setIssue(error.message),
  })
  const saver = useMutation({
    mutationFn: () => updateSpreadsheetTemplate(initial!.templateKey, initial!.revision, history.present),
    onSuccess: (saved) => {
      baseline.current = saved.document
      dispatch({ type: 'reset', document: saved.document })
      setIssue(undefined)
      queryClient.setQueryData(['spreadsheet-template', saved.templateKey], saved)
      queryClient.invalidateQueries({ queryKey: ['spreadsheet-templates'] })
    },
    onError: (error) => setIssue(error.message),
  })
  const remover = useMutation({
    mutationFn: () => deleteSpreadsheetTemplate(initial!.templateKey),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['spreadsheet-template', initial!.templateKey] })
      queryClient.invalidateQueries({ queryKey: ['spreadsheet-templates'] })
      navigate({ search: {}, replace: true })
    },
    onError: (error) => setIssue(error.message),
  })
  const filler = useMutation({
    mutationFn: (command: SpreadsheetTemplateFillCommand) => fillSpreadsheetTemplate(initial!.templateKey, command),
    onSuccess: (blob) => {
      downloadBlob(blob, document!.source.fileName)
      setIssue(undefined)
      setFillOpen(false)
    },
    onError: (error) => setIssue(error.message),
  })

  const chooseFile = () => {
    if (!/^[a-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*$/.test(templateKey.trim())) {
      setIssue('模板 key 必须是稳定英文标识')
      return
    }
    fileInput.current?.click()
  }
  const selectSheet = (nextSheetKey: string) => {
    setSheetKey(nextSheetKey)
    setSelection({ sheetKey: nextSheetKey, row: 0, column: 0 })
  }
  const selectCell = (next: SpreadsheetCellSelection) => {
    setSelection(next)
    setRightOpen(true)
  }
  const commit = (next: SpreadsheetTemplateDocument) => dispatch({ type: 'commit', document: next })

  return (
    <div className="spreadsheet-designer-page">
      <header className="spreadsheet-commandbar">
        <div className="spreadsheet-template-picker">
          <select aria-label="选择 Excel 模板" onChange={(event) => setDraftSelection(event.target.value)} value={draftSelection}>
            <option value="">选择模板</option>
            {templateOptions.map((template) => <option key={template.templateKey} value={template.templateKey}>{template.name}</option>)}
          </select>
          <CatalogIconAction disabled={!draftSelection} elementKey="studio.spreadsheet-templates.open" onClick={() => navigate({ search: { templateKey: draftSelection } })} />
          <span>{routeName}</span>
          {initial ? <small>{dirty ? '未保存' : `r${initial.revision}`}</small> : null}
        </div>
        <div className="spreadsheet-import-fields">
          <input aria-label="模板 key" onChange={(event) => setTemplateKey(event.target.value)} placeholder="inspection-record" value={templateKey} />
          <input aria-label="模板名称" onChange={(event) => setTemplateName(event.target.value)} placeholder="模板名称" value={templateName} />
          <input ref={fileInput} accept=".xls,.xlsx,.xlsm" hidden onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) importer.mutate(file)
            event.target.value = ''
          }} type="file" />
          <CatalogIconAction disabled={importer.isPending} elementKey="studio.spreadsheet-templates.upload" onClick={chooseFile} />
        </div>
        {history && initial ? (
          <div className="spreadsheet-command-actions">
            <CatalogIconAction elementKey="studio.spreadsheet-templates.panel.left" onClick={() => setLeftOpen((value) => !value)} />
            <CatalogIconAction disabled={!history.past.length} elementKey="studio.spreadsheet-templates.undo" onClick={() => dispatch({ type: 'undo' })} />
            <CatalogIconAction disabled={!history.future.length} elementKey="studio.spreadsheet-templates.redo" onClick={() => dispatch({ type: 'redo' })} />
            <CatalogIconAction disabled={!dirty || saver.isPending} elementKey="studio.spreadsheet-templates.save" onClick={() => saver.mutate()} />
            <CatalogIconAction disabled={dirty} elementKey="studio.spreadsheet-templates.fill" onClick={() => setFillOpen(true)} />
            <CatalogIconAction elementKey="studio.spreadsheet-templates.panel.right" onClick={() => setRightOpen((value) => !value)} />
            <CatalogIconAction disabled={remover.isPending} elementKey="studio.spreadsheet-templates.delete" onClick={() => window.confirm('确定删除当前模板吗？') && remover.mutate()} />
          </div>
        ) : null}
      </header>
      {issue ? <div className="designer-issue" role="alert">{issue}</div> : null}
      {document ? (
        <main className={`spreadsheet-workbench${leftOpen ? ' is-left-open' : ''}${rightOpen ? ' is-right-open' : ''}`}>
          <SpreadsheetToolPanel commit={commit} document={document} onSheetChange={selectSheet} selection={selection} sheetKey={sheetKey} />
          <section className="spreadsheet-stage">
            <div className="spreadsheet-stage-meta">
              <strong>{document.sheets.find((sheet) => sheet.key === sheetKey)?.name}</strong>
              <span>{document.source.macroEnabled ? 'XLSM' : document.source.fileName.split('.').at(-1)?.toUpperCase()}</span>
              {document.source.containsExternalLinks ? <span>外链</span> : null}
            </div>
            <SpreadsheetCanvas document={document} onSelect={selectCell} selection={selection} sheetKey={sheetKey} />
          </section>
          <SpreadsheetInspector commit={commit} document={document} selection={selection} />
        </main>
      ) : (
        <div className="spreadsheet-empty-state"><strong>Excel 模板</strong><span>.xls · .xlsx · .xlsm</span></div>
      )}
      {fillOpen && document ? (
        <SpreadsheetFillDialog
          document={document}
          error={filler.error?.message}
          onClose={() => setFillOpen(false)}
          onSubmit={(command) => filler.mutate({ ...command, expectedRevision: initial!.revision })}
          pending={filler.isPending}
        />
      ) : null}
    </div>
  )
}

const EMPTY_DOCUMENT: SpreadsheetTemplateDocument = {
  version: 1,
  name: '',
  description: null,
  source: {
    fileName: '',
    mediaType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    sha256: '0'.repeat(64),
    macroEnabled: false,
    containsExternalLinks: false,
  },
  styles: [],
  sheets: [{
    key: 'sheet1',
    name: 'Sheet1',
    rowCount: 1,
    columnCount: 1,
    columnWidthsPx: { 0: 72 },
    rowHeightsPx: { 0: 24 },
    hiddenColumns: [],
    cells: [],
    mergedRanges: [],
    images: [],
    print: {
      area: null,
      landscape: false,
      paperSize: 0,
      fitWidth: 0,
      fitHeight: 0,
      marginLeft: 0,
      marginRight: 0,
      marginTop: 0,
      marginBottom: 0,
    },
  }],
  variables: [],
  bindings: [],
  ledgers: [],
  edits: [],
}
