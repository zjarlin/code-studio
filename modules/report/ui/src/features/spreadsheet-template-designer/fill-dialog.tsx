import { useState } from 'react'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import type {
  SpreadsheetLedgerFieldSpec,
  SpreadsheetTemplateDocument,
  SpreadsheetTemplateFillInput,
  SpreadsheetVariableSpec,
} from '@/features/spreadsheet-templates/models'

interface FillDialogProps {
  document: SpreadsheetTemplateDocument
  error?: string
  pending: boolean
  onClose: () => void
  onSubmit: (command: SpreadsheetTemplateFillInput) => void
}

export function SpreadsheetFillDialog({ document, error, pending, onClose, onSubmit }: Readonly<FillDialogProps>) {
  const [values, setValues] = useState<Record<string, string>>(() => Object.fromEntries(
    document.variables.flatMap((variable) => variable.defaultValue == null ? [] : [[variable.key, variable.defaultValue]]),
  ))
  const [ledgers, setLedgers] = useState<Record<string, Array<Record<string, string>>>>({})
  const setValue = (key: string, value: string) => setValues((current) => ({ ...current, [key]: value }))
  const setLedgerValue = (ledgerKey: string, rowIndex: number, fieldKey: string, value: string) => setLedgers((current) => ({
    ...current,
    [ledgerKey]: (current[ledgerKey] ?? []).map((row, index) => index === rowIndex ? { ...row, [fieldKey]: value } : row),
  }))
  const addLedgerRow = (ledgerKey: string) => setLedgers((current) => ({
    ...current,
    [ledgerKey]: [...(current[ledgerKey] ?? []), {}],
  }))
  const removeLedgerRow = (ledgerKey: string, rowIndex: number) => setLedgers((current) => ({
    ...current,
    [ledgerKey]: (current[ledgerKey] ?? []).filter((_, index) => index !== rowIndex),
  }))

  return (
    <div className="spreadsheet-dialog-backdrop" role="presentation">
      <form className="spreadsheet-fill-dialog" onSubmit={(event) => { event.preventDefault(); onSubmit({ values, ledgers }) }}>
        <header>
          <div><h2>生成 {document.name}</h2><small>{document.source.fileName}</small></div>
          <CatalogIconAction elementKey="studio.spreadsheet-templates.fill.close" onClick={onClose} type="button" />
        </header>
        <div className="spreadsheet-fill-content">
          {document.variables.length ? (
            <section>
              <h3>变量</h3>
              <div className="spreadsheet-fill-fields">
                {document.variables.map((variable) => (
                  <label key={variable.key}>{variable.label}{variable.required ? ' *' : ''}
                    <FillInput onChange={(value) => setValue(variable.key, value)} value={values[variable.key] ?? ''} variable={variable} />
                  </label>
                ))}
              </div>
            </section>
          ) : null}
          {document.ledgers.map((ledger) => (
            <section key={ledger.key}>
              <div className="spreadsheet-dialog-section-heading">
                <h3>{ledger.name}</h3>
                <CatalogAction elementKey="studio.spreadsheet-templates.fill.ledger-row.add" onClick={() => addLedgerRow(ledger.key)} type="button" />
              </div>
              <div className="spreadsheet-ledger-fill-table">
                {(ledgers[ledger.key] ?? []).map((row, rowIndex) => (
                  <div className="spreadsheet-ledger-fill-row" key={rowIndex}>
                    {ledger.fields.map((field) => (
                      <label key={field.key}>{field.label}
                        <LedgerInput field={field} onChange={(value) => setLedgerValue(ledger.key, rowIndex, field.key, value)} value={row[field.key] ?? ''} />
                      </label>
                    ))}
                    <CatalogIconAction elementKey="studio.spreadsheet-templates.fill.ledger-row.remove" onClick={() => removeLedgerRow(ledger.key, rowIndex)} type="button" />
                  </div>
                ))}
              </div>
            </section>
          ))}
          {error ? <div className="designer-issue" role="alert">{error}</div> : null}
        </div>
        <footer>
          <CatalogAction elementKey="studio.spreadsheet-templates.fill.close" onClick={onClose} type="button" variant="ghost" />
          <CatalogAction disabled={pending} elementKey="studio.spreadsheet-templates.fill.generate" type="submit" variant="primary" />
        </footer>
      </form>
    </div>
  )
}

function FillInput({ variable, value, onChange }: Readonly<{
  variable: SpreadsheetVariableSpec
  value: string
  onChange: (value: string) => void
}>) {
  if (variable.type === 'IMAGE') {
    return <input accept="image/png,image/jpeg" onChange={(event) => readImage(event.target.files?.[0], onChange)} required={variable.required} type="file" />
  }
  if (variable.type === 'BOOLEAN') {
    return <select onChange={(event) => onChange(event.target.value)} required={variable.required} value={value}><option value="">请选择</option><option value="true">是</option><option value="false">否</option></select>
  }
  const type = variable.type === 'NUMBER' ? 'number' : variable.type === 'DATE' ? 'date' : variable.type === 'DATETIME' ? 'datetime-local' : 'text'
  return <input onChange={(event) => onChange(event.target.value)} required={variable.required} type={type} value={value} />
}

function LedgerInput({ field, value, onChange }: Readonly<{
  field: SpreadsheetLedgerFieldSpec
  value: string
  onChange: (value: string) => void
}>) {
  const variable = { ...field, defaultValue: null }
  return <FillInput onChange={onChange} value={value} variable={variable} />
}

function readImage(file: File | undefined, onChange: (value: string) => void): void {
  if (!file) return onChange('')
  const reader = new FileReader()
  reader.addEventListener('load', () => onChange(typeof reader.result === 'string' ? reader.result : ''))
  reader.readAsDataURL(file)
}
