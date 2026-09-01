import { useEffect, useMemo, useState } from 'react'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import type {
  SpreadsheetBindingTarget,
  SpreadsheetCellSelection,
  SpreadsheetRangeSpec,
  SpreadsheetTemplateDocument,
} from '@/features/spreadsheet-templates/models'
import { cellAddress } from '@/features/spreadsheet-templates/models'

interface InspectorProps {
  document: SpreadsheetTemplateDocument
  selection: SpreadsheetCellSelection
  commit: (document: SpreadsheetTemplateDocument) => void
}

export function SpreadsheetInspector({ document, selection, commit }: Readonly<InspectorProps>) {
  const sheet = document.sheets.find((candidate) => candidate.key === selection.sheetKey)
  const cell = sheet?.cells.find((candidate) => candidate.row === selection.row && candidate.column === selection.column)
  const existingEdit = document.edits.find((edit) =>
    edit.sheetKey === selection.sheetKey && edit.row === selection.row && edit.column === selection.column)
  const [cellValue, setCellValue] = useState(existingEdit?.value ?? cell?.displayValue ?? '')
  const [templateName, setTemplateName] = useState(document.name)
  const [description, setDescription] = useState(document.description ?? '')
  const [variableKey, setVariableKey] = useState(document.variables[0]?.key ?? '')
  const [rowSpan, setRowSpan] = useState(1)
  const [columnSpan, setColumnSpan] = useState(1)
  const selectedVariable = document.variables.find((variable) => variable.key === variableKey)
  const mergedRange = sheet?.mergedRanges.find((range) => contains(range, selection.row, selection.column))
  const target: SpreadsheetBindingTarget = selectedVariable?.type === 'IMAGE'
    ? 'IMAGE_ANCHOR'
    : mergedRange ? 'MERGED_RANGE' : 'CELL'
  const selectionBindings = useMemo(() => document.bindings.filter((binding) =>
    binding.sheetKey === selection.sheetKey && contains(binding.range, selection.row, selection.column)), [document.bindings, selection])

  useEffect(() => {
    setCellValue(existingEdit?.value ?? cell?.displayValue ?? '')
  }, [cell?.displayValue, existingEdit?.value, selection.column, selection.row, selection.sheetKey])
  useEffect(() => {
    if (!document.variables.some((variable) => variable.key === variableKey)) {
      setVariableKey(document.variables[0]?.key ?? '')
    }
  }, [document.variables, variableKey])
  useEffect(() => {
    setTemplateName(document.name)
    setDescription(document.description ?? '')
  }, [document.description, document.name])

  const applyCellEdit = () => {
    const edits = document.edits.filter((edit) =>
      edit.sheetKey !== selection.sheetKey || edit.row !== selection.row || edit.column !== selection.column)
    const sourceValue = cell?.displayValue ?? ''
    commit({
      ...document,
      edits: cellValue === sourceValue ? edits : [...edits, { ...selection, value: cellValue }],
    })
  }
  const addBinding = () => {
    if (!selectedVariable || !sheet) return
    const origin = mergedRange ?? {
      fromRow: selection.row,
      fromColumn: selection.column,
      toRow: selection.row,
      toColumn: selection.column,
    }
    const range = selectedVariable.type === 'IMAGE' ? {
      fromRow: selection.row,
      fromColumn: selection.column,
      toRow: Math.min(sheet.rowCount - 1, selection.row + rowSpan - 1),
      toColumn: Math.min(sheet.columnCount - 1, selection.column + columnSpan - 1),
    } : origin
    const key = `${selectedVariable.key}-at-${sheet.key}-${selection.row + 1}-${selection.column + 1}`
    const bindings = document.bindings.filter((binding) => binding.key !== key)
    commit({
      ...document,
      bindings: [...bindings, { key, variableKey: selectedVariable.key, sheetKey: sheet.key, target, range }],
    })
  }
  const removeBinding = (key: string) => commit({
    ...document,
    bindings: document.bindings.filter((binding) => binding.key !== key),
  })
  const updateVariable = (changes: Partial<NonNullable<typeof selectedVariable>>) => {
    if (!selectedVariable) return
    commit({
      ...document,
      variables: document.variables.map((variable) => variable.key === selectedVariable.key
        ? { ...variable, ...changes }
        : variable),
    })
  }

  return (
    <aside className="spreadsheet-inspector">
      <section>
        <h2>模板</h2>
        <label>名称<input value={templateName} onBlur={() => templateName !== document.name && commit({ ...document, name: templateName })} onChange={(event) => setTemplateName(event.target.value)} /></label>
        <label>说明<textarea value={description} onBlur={() => description !== (document.description ?? '') && commit({ ...document, description: description || null })} onChange={(event) => setDescription(event.target.value)} /></label>
        <dl className="spreadsheet-source-facts">
          <div><dt>源文件</dt><dd>{document.source.fileName}</dd></div>
          <div><dt>宏</dt><dd>{document.source.macroEnabled ? '保留' : '无'}</dd></div>
          <div><dt>外链</dt><dd>{document.source.containsExternalLinks ? '保留' : '无'}</dd></div>
        </dl>
      </section>

      <section>
        <h2>{cellAddress(selection.row, selection.column)}</h2>
        {cell?.formula ? <code>= {cell.formula}</code> : null}
        <label>单元格内容<textarea onChange={(event) => setCellValue(event.target.value)} value={cellValue} /></label>
        <CatalogAction elementKey="studio.spreadsheet-templates.cell.apply" onClick={applyCellEdit} />
      </section>

      <section>
        <h2>变量绑定</h2>
        <select aria-label="绑定变量" onChange={(event) => setVariableKey(event.target.value)} value={variableKey}>
          <option value="">选择变量</option>
          {document.variables.map((variable) => <option key={variable.key} value={variable.key}>{variable.label}</option>)}
        </select>
        {selectedVariable ? (
          <>
            <label className="spreadsheet-checkbox">
              <input checked={selectedVariable.required} onChange={(event) => updateVariable({ required: event.target.checked })} type="checkbox" />必填
            </label>
            {selectedVariable.type !== 'IMAGE' ? (
              <label>默认值<input onChange={(event) => updateVariable({ defaultValue: event.target.value || null })} value={selectedVariable.defaultValue ?? ''} /></label>
            ) : (
              <div className="spreadsheet-span-fields">
                <label>行跨度<input min="1" onChange={(event) => setRowSpan(Number(event.target.value))} type="number" value={rowSpan} /></label>
                <label>列跨度<input min="1" onChange={(event) => setColumnSpan(Number(event.target.value))} type="number" value={columnSpan} /></label>
              </div>
            )}
            <CatalogAction elementKey="studio.spreadsheet-templates.binding.assign" onClick={addBinding} />
          </>
        ) : null}
        {selectionBindings.map((binding) => (
          <div className="spreadsheet-list-row" key={binding.key}>
            <span><strong>{`{{${binding.variableKey}}}`}</strong><small>{binding.target}</small></span>
            <CatalogIconAction elementKey="studio.spreadsheet-templates.binding.remove" onClick={() => removeBinding(binding.key)} />
          </div>
        ))}
      </section>
    </aside>
  )
}

function contains(range: SpreadsheetRangeSpec, row: number, column: number): boolean {
  return row >= range.fromRow && row <= range.toRow && column >= range.fromColumn && column <= range.toColumn
}
