export type SpreadsheetCellType = 'EMPTY' | 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'ERROR' | 'FORMULA'
export type SpreadsheetVariableType = 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'IMAGE'
export type SpreadsheetBindingTarget = 'CELL' | 'MERGED_RANGE' | 'IMAGE_ANCHOR'

export interface SpreadsheetSourceSpec {
  fileName: string
  mediaType: string
  sha256: string
  macroEnabled: boolean
  containsExternalLinks: boolean
}

export interface SpreadsheetStyleSpec {
  key: number
  fontFamily: string | null
  fontSizePt: number | null
  bold: boolean
  italic: boolean
  textColor: string | null
  fillColor: string | null
  horizontalAlignment: string | null
  verticalAlignment: string | null
  wrapText: boolean
  borderTop: string | null
  borderRight: string | null
  borderBottom: string | null
  borderLeft: string | null
  numberFormat: string | null
}

export interface SpreadsheetRangeSpec {
  fromRow: number
  fromColumn: number
  toRow: number
  toColumn: number
}

export interface SpreadsheetCellSpec {
  row: number
  column: number
  type: SpreadsheetCellType
  displayValue: string
  formula: string | null
  styleKey: number
}

export interface SpreadsheetImageSpec {
  key: string
  mediaType: string
  dataBase64: string
  range: SpreadsheetRangeSpec
}

export interface SpreadsheetPrintSpec {
  area: SpreadsheetRangeSpec | null
  landscape: boolean
  paperSize: number
  fitWidth: number
  fitHeight: number
  marginLeft: number
  marginRight: number
  marginTop: number
  marginBottom: number
}

export interface SpreadsheetSheetSpec {
  key: string
  name: string
  rowCount: number
  columnCount: number
  columnWidthsPx: Record<string, number>
  rowHeightsPx: Record<string, number>
  hiddenColumns: number[]
  cells: SpreadsheetCellSpec[]
  mergedRanges: SpreadsheetRangeSpec[]
  images: SpreadsheetImageSpec[]
  print: SpreadsheetPrintSpec
}

export interface SpreadsheetVariableSpec {
  key: string
  label: string
  type: SpreadsheetVariableType
  required: boolean
  defaultValue: string | null
}

export interface SpreadsheetBindingSpec {
  key: string
  variableKey: string
  sheetKey: string
  target: SpreadsheetBindingTarget
  range: SpreadsheetRangeSpec
}

export interface SpreadsheetLedgerFieldSpec {
  key: string
  label: string
  type: SpreadsheetVariableType
  column: number
  required: boolean
}

export interface SpreadsheetLedgerSpec {
  key: string
  name: string
  sheetKey: string
  firstRow: number
  maxRows: number
  fields: SpreadsheetLedgerFieldSpec[]
}

export interface SpreadsheetCellEditSpec {
  sheetKey: string
  row: number
  column: number
  value: string
}

export interface SpreadsheetTemplateDocument {
  version: number
  name: string
  description: string | null
  source: SpreadsheetSourceSpec
  styles: SpreadsheetStyleSpec[]
  sheets: SpreadsheetSheetSpec[]
  variables: SpreadsheetVariableSpec[]
  bindings: SpreadsheetBindingSpec[]
  ledgers: SpreadsheetLedgerSpec[]
  edits: SpreadsheetCellEditSpec[]
}

export interface SpreadsheetTemplateListItemView {
  templateKey: string
  revision: number
  name: string
  fileName: string
  macroEnabled: boolean
}

export interface SpreadsheetTemplateView {
  templateKey: string
  revision: number
  document: SpreadsheetTemplateDocument
}

export interface SpreadsheetTemplateFillCommand {
  expectedRevision: number
  values: Record<string, string>
  ledgers: Record<string, Array<Record<string, string>>>
}

export type SpreadsheetTemplateFillInput = Omit<SpreadsheetTemplateFillCommand, 'expectedRevision'>

export interface SpreadsheetTemplateDraftSpec {
  name: string
  description: string | null
  variables: SpreadsheetVariableSpec[]
  bindings: SpreadsheetBindingSpec[]
  ledgers: SpreadsheetLedgerSpec[]
  edits: SpreadsheetCellEditSpec[]
}

export interface PageResult<T> {
  rows: T[]
  totalRowCount: number
  totalPageCount: number
}

export interface SpreadsheetCellSelection {
  sheetKey: string
  row: number
  column: number
}

export function cellAddress(row: number, column: number): string {
  let value = column + 1
  let letters = ''
  while (value > 0) {
    value -= 1
    letters = String.fromCharCode(65 + value % 26) + letters
    value = Math.floor(value / 26)
  }
  return `${letters}${row + 1}`
}

export function stableKey(value: string): string {
  const words = value.trim().split(/[^A-Za-z0-9]+/).filter(Boolean)
  if (!words.length) return ''
  const [first = '', ...rest] = words
  const key = first.toLowerCase() + rest.map((word) => `${word[0]?.toUpperCase() ?? ''}${word.slice(1)}`).join('')
  return /^[a-z]/.test(key) ? key : `field${key}`
}
