import { useState } from 'react'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import type {
  SpreadsheetCellSelection,
  SpreadsheetTemplateDocument,
  SpreadsheetVariableType,
} from '@/features/spreadsheet-templates/models'

interface ToolPanelProps {
  document: SpreadsheetTemplateDocument
  selection: SpreadsheetCellSelection
  sheetKey: string
  onSheetChange: (sheetKey: string) => void
  commit: (document: SpreadsheetTemplateDocument) => void
}

export function SpreadsheetToolPanel({ document, selection, sheetKey, onSheetChange, commit }: Readonly<ToolPanelProps>) {
  const [variableKey, setVariableKey] = useState('')
  const [variableLabel, setVariableLabel] = useState('')
  const [variableType, setVariableType] = useState<SpreadsheetVariableType>('TEXT')
  const [ledgerKey, setLedgerKey] = useState('')
  const [ledgerName, setLedgerName] = useState('')
  const [activeLedgerKey, setActiveLedgerKey] = useState('')
  const [fieldKey, setFieldKey] = useState('')
  const [fieldLabel, setFieldLabel] = useState('')
  const [fieldType, setFieldType] = useState<SpreadsheetVariableType>('TEXT')
  const activeLedger = document.ledgers.find((ledger) => ledger.key === activeLedgerKey) ?? document.ledgers[0]

  const addVariable = () => {
    const key = variableKey.trim()
    const label = variableLabel.trim()
    if (!stableIdentifier(key) || !label || document.variables.some((variable) => variable.key === key)) return
    commit({
      ...document,
      variables: [...document.variables, { key, label, type: variableType, required: false, defaultValue: null }],
    })
    setVariableKey('')
    setVariableLabel('')
  }
  const removeVariable = (key: string) => commit({
    ...document,
    variables: document.variables.filter((variable) => variable.key !== key),
    bindings: document.bindings.filter((binding) => binding.variableKey !== key),
  })
  const addLedger = () => {
    const key = ledgerKey.trim()
    const name = ledgerName.trim()
    if (!stableIdentifier(key) || !name || document.ledgers.some((ledger) => ledger.key === key)) return
    commit({
      ...document,
      ledgers: [...document.ledgers, {
        key,
        name,
        sheetKey: selection.sheetKey,
        firstRow: selection.row,
        maxRows: 200,
        fields: [],
      }],
    })
    setActiveLedgerKey(key)
    setLedgerKey('')
    setLedgerName('')
  }
  const removeLedger = (key: string) => {
    commit({ ...document, ledgers: document.ledgers.filter((ledger) => ledger.key !== key) })
    setActiveLedgerKey('')
  }
  const addField = () => {
    if (!activeLedger) return
    const key = fieldKey.trim()
    const label = fieldLabel.trim()
    if (!stableIdentifier(key) || !label || activeLedger.fields.some((field) => field.key === key)) return
    commit({
      ...document,
      ledgers: document.ledgers.map((ledger) => ledger.key === activeLedger.key ? {
        ...ledger,
        fields: [...ledger.fields, { key, label, type: fieldType, column: selection.column, required: false }],
      } : ledger),
    })
    setFieldKey('')
    setFieldLabel('')
  }
  const removeField = (ledgerKeyValue: string, key: string) => commit({
    ...document,
    ledgers: document.ledgers.map((ledger) => ledger.key === ledgerKeyValue
      ? { ...ledger, fields: ledger.fields.filter((field) => field.key !== key) }
      : ledger),
  })

  return (
    <aside className="spreadsheet-tools">
      <section>
        <h2>工作表</h2>
        <select aria-label="工作表" onChange={(event) => onSheetChange(event.target.value)} value={sheetKey}>
          {document.sheets.map((sheet) => <option key={sheet.key} value={sheet.key}>{sheet.name}</option>)}
        </select>
      </section>

      <section>
        <h2>变量</h2>
        <div className="spreadsheet-compact-form">
          <input aria-label="变量 key" onChange={(event) => setVariableKey(event.target.value)} placeholder="projectName" value={variableKey} />
          <input aria-label="变量名称" onChange={(event) => setVariableLabel(event.target.value)} placeholder="工程名称" value={variableLabel} />
          <select aria-label="变量类型" onChange={(event) => setVariableType(event.target.value as SpreadsheetVariableType)} value={variableType}>
            {VARIABLE_TYPES.map((type) => <option key={type} value={type}>{TYPE_LABELS[type]}</option>)}
          </select>
          <CatalogAction elementKey="studio.spreadsheet-templates.variable.add" onClick={addVariable} />
        </div>
        <div className="spreadsheet-item-list">
          {document.variables.map((variable) => (
            <div className="spreadsheet-list-row" key={variable.key}>
              <span><strong>{variable.label}</strong><small>{variable.key} · {TYPE_LABELS[variable.type]}</small></span>
              <CatalogIconAction elementKey="studio.spreadsheet-templates.variable.remove" onClick={() => removeVariable(variable.key)} />
            </div>
          ))}
        </div>
      </section>

      <section>
        <h2>台账</h2>
        <div className="spreadsheet-compact-form">
          <input aria-label="台账 key" onChange={(event) => setLedgerKey(event.target.value)} placeholder="records" value={ledgerKey} />
          <input aria-label="台账名称" onChange={(event) => setLedgerName(event.target.value)} placeholder="检测台账" value={ledgerName} />
          <CatalogAction elementKey="studio.spreadsheet-templates.ledger.add" onClick={addLedger} />
        </div>
        {document.ledgers.length ? (
          <select aria-label="当前台账" onChange={(event) => setActiveLedgerKey(event.target.value)} value={activeLedger?.key ?? ''}>
            {document.ledgers.map((ledger) => <option key={ledger.key} value={ledger.key}>{ledger.name}</option>)}
          </select>
        ) : null}
        {activeLedger ? (
          <div className="spreadsheet-ledger-editor">
            <div className="spreadsheet-list-row">
              <span><strong>{activeLedger.name}</strong><small>{activeLedger.sheetKey} · 第 {activeLedger.firstRow + 1} 行</small></span>
              <CatalogIconAction elementKey="studio.spreadsheet-templates.ledger.remove" onClick={() => removeLedger(activeLedger.key)} />
            </div>
            <div className="spreadsheet-compact-form">
              <input aria-label="字段 key" onChange={(event) => setFieldKey(event.target.value)} placeholder="sampleCode" value={fieldKey} />
              <input aria-label="字段名称" onChange={(event) => setFieldLabel(event.target.value)} placeholder="样品编号" value={fieldLabel} />
              <select aria-label="字段类型" onChange={(event) => setFieldType(event.target.value as SpreadsheetVariableType)} value={fieldType}>
                {VARIABLE_TYPES.map((type) => <option key={type} value={type}>{TYPE_LABELS[type]}</option>)}
              </select>
              <CatalogAction elementKey="studio.spreadsheet-templates.ledger.field.add" onClick={addField} />
            </div>
            {activeLedger.fields.map((field) => (
              <div className="spreadsheet-list-row" key={field.key}>
                <span><strong>{field.label}</strong><small>{field.key} · {columnName(field.column)}</small></span>
                <CatalogIconAction elementKey="studio.spreadsheet-templates.ledger.field.remove" onClick={() => removeField(activeLedger.key, field.key)} />
              </div>
            ))}
          </div>
        ) : null}
      </section>
    </aside>
  )
}

export const VARIABLE_TYPES: SpreadsheetVariableType[] = ['TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'DATETIME', 'IMAGE']
export const TYPE_LABELS: Record<SpreadsheetVariableType, string> = {
  TEXT: '文本', NUMBER: '数字', BOOLEAN: '布尔', DATE: '日期', DATETIME: '日期时间', IMAGE: '图片',
}

function stableIdentifier(value: string): boolean {
  return /^[a-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*$/.test(value)
}

function columnName(column: number): string {
  let value = column + 1
  let result = ''
  while (value) {
    value -= 1
    result = String.fromCharCode(65 + value % 26) + result
    value = Math.floor(value / 26)
  }
  return result
}
