import type { CSSProperties, KeyboardEvent } from 'react'
import { useMemo } from 'react'

import type {
  SpreadsheetCellSelection,
  SpreadsheetRangeSpec,
  SpreadsheetSheetSpec,
  SpreadsheetStyleSpec,
  SpreadsheetTemplateDocument,
} from '@/features/spreadsheet-templates/models'
import { cellAddress } from '@/features/spreadsheet-templates/models'

interface SpreadsheetCanvasProps {
  document: SpreadsheetTemplateDocument
  sheetKey: string
  selection: SpreadsheetCellSelection
  onSelect: (selection: SpreadsheetCellSelection) => void
}

export function SpreadsheetCanvas({ document, sheetKey, selection, onSelect }: Readonly<SpreadsheetCanvasProps>) {
  const sheet = document.sheets.find((candidate) => candidate.key === sheetKey) ?? document.sheets[0]
  const styles = useMemo(() => new Map(document.styles.map((style) => [style.key, style])), [document.styles])
  if (!sheet) return null
  const merged = mergedCellIndex(sheet)
  const cells = new Map(sheet.cells.map((cell) => [`${cell.row}:${cell.column}`, cell]))
  const edits = new Map(document.edits.filter((edit) => edit.sheetKey === sheet.key).map((edit) => [`${edit.row}:${edit.column}`, edit.value]))
  const bindings = new Map<string, string[]>()
  document.bindings.filter((binding) => binding.sheetKey === sheet.key).forEach((binding) => {
    const key = `${binding.range.fromRow}:${binding.range.fromColumn}`
    bindings.set(key, [...(bindings.get(key) ?? []), binding.variableKey])
  })
  const columnWidths = Array.from({ length: sheet.columnCount }, (_, column) =>
    sheet.hiddenColumns.includes(column) ? 0 : sheet.columnWidthsPx[String(column)] ?? 72)
  const rowHeights = Array.from({ length: sheet.rowCount }, (_, row) => sheet.rowHeightsPx[String(row)] ?? 24)
  const gridStyle = {
    gridTemplateColumns: `42px ${columnWidths.map((width) => `${width}px`).join(' ')}`,
    gridTemplateRows: `28px ${rowHeights.map((height) => `${height}px`).join(' ')}`,
  }

  const moveSelection = (event: KeyboardEvent, row: number, column: number) => {
    const delta = event.key === 'ArrowUp' ? [-1, 0]
      : event.key === 'ArrowDown' ? [1, 0]
        : event.key === 'ArrowLeft' ? [0, -1]
          : event.key === 'ArrowRight' ? [0, 1]
            : undefined
    if (!delta) return
    event.preventDefault()
    onSelect({
      sheetKey: sheet.key,
      row: Math.max(0, Math.min(sheet.rowCount - 1, row + delta[0]!)),
      column: Math.max(0, Math.min(sheet.columnCount - 1, column + delta[1]!)),
    })
  }

  return (
    <div className="spreadsheet-viewport">
      <div className="spreadsheet-grid" role="grid" aria-label={sheet.name} style={gridStyle}>
        <div className="spreadsheet-corner" />
        {columnWidths.map((width, column) => width > 0 ? (
          <div className="spreadsheet-column-header" key={column} style={{ gridColumn: column + 2, gridRow: 1 }}>
            {cellAddress(0, column).replace(/\d+$/, '')}
          </div>
        ) : null)}
        {rowHeights.map((_, row) => (
          <div className="spreadsheet-row-header" key={row} style={{ gridColumn: 1, gridRow: row + 2 }}>{row + 1}</div>
        ))}
        {rowHeights.flatMap((_, row) => columnWidths.map((width, column) => {
          const coordinate = `${row}:${column}`
          if (width === 0 || merged.covered.has(coordinate)) return null
          const cell = cells.get(coordinate)
          const range = merged.origins.get(coordinate) ?? { fromRow: row, fromColumn: column, toRow: row, toColumn: column }
          const selected = selection.sheetKey === sheet.key && selection.row === row && selection.column === column
          const variableKeys = bindings.get(coordinate) ?? []
          const displayValue = edits.get(coordinate) ?? cell?.displayValue ?? ''
          return (
            <button
              aria-label={`${cellAddress(row, column)} ${displayValue}`.trim()}
              className={`spreadsheet-cell${selected ? ' is-selected' : ''}${variableKeys.length ? ' has-binding' : ''}${outsidePrintArea(sheet, range) ? ' is-outside-print' : ''}`}
              key={coordinate}
              onClick={() => onSelect({ sheetKey: sheet.key, row, column })}
              onKeyDown={(event) => moveSelection(event, row, column)}
              role="gridcell"
              style={{ ...cellGridStyle(range), ...excelStyle(styles.get(cell?.styleKey ?? 0)) }}
              title={cell?.formula ? `=${cell.formula}` : undefined}
              type="button"
            >
              <span>{displayValue}</span>
              {variableKeys.length ? <small>{variableKeys.map((key) => `{{${key}}}`).join(' ')}</small> : null}
            </button>
          )
        }))}
        {sheet.images.map((image) => (
          <img
            alt=""
            className="spreadsheet-image"
            key={image.key}
            src={`data:${image.mediaType};base64,${image.dataBase64}`}
            style={cellGridStyle(image.range)}
          />
        ))}
      </div>
    </div>
  )
}

function mergedCellIndex(sheet: SpreadsheetSheetSpec) {
  const origins = new Map<string, SpreadsheetRangeSpec>()
  const covered = new Set<string>()
  sheet.mergedRanges.forEach((range) => {
    origins.set(`${range.fromRow}:${range.fromColumn}`, range)
    for (let row = range.fromRow; row <= range.toRow; row += 1) {
      for (let column = range.fromColumn; column <= range.toColumn; column += 1) {
        if (row !== range.fromRow || column !== range.fromColumn) covered.add(`${row}:${column}`)
      }
    }
  })
  return { origins, covered }
}

function cellGridStyle(range: SpreadsheetRangeSpec): CSSProperties {
  return {
    gridColumn: `${range.fromColumn + 2} / ${range.toColumn + 3}`,
    gridRow: `${range.fromRow + 2} / ${range.toRow + 3}`,
  }
}

function excelStyle(style: SpreadsheetStyleSpec | undefined): CSSProperties {
  if (!style) return {}
  return {
    alignItems: verticalAlignment(style.verticalAlignment),
    backgroundColor: style.fillColor ?? undefined,
    borderBottom: border(style.borderBottom),
    borderLeft: border(style.borderLeft),
    borderRight: border(style.borderRight),
    borderTop: border(style.borderTop),
    color: style.textColor ?? undefined,
    fontFamily: style.fontFamily ?? undefined,
    fontSize: style.fontSizePt ? `${style.fontSizePt}pt` : undefined,
    fontStyle: style.italic ? 'italic' : undefined,
    fontWeight: style.bold ? 700 : undefined,
    justifyContent: horizontalAlignment(style.horizontalAlignment),
    whiteSpace: style.wrapText ? 'pre-wrap' : 'nowrap',
  }
}

function border(value: string | null | undefined): string | undefined {
  if (!value) return undefined
  const width = value.includes('THICK') || value.includes('DOUBLE') ? 2 : 1
  return `${width}px solid #8c929d`
}

function horizontalAlignment(value: string | null): CSSProperties['justifyContent'] {
  if (value === 'CENTER') return 'center'
  if (value === 'RIGHT') return 'flex-end'
  return 'flex-start'
}

function verticalAlignment(value: string | null): CSSProperties['alignItems'] {
  if (value === 'CENTER') return 'center'
  if (value === 'BOTTOM') return 'flex-end'
  return 'flex-start'
}

function outsidePrintArea(sheet: SpreadsheetSheetSpec, range: SpreadsheetRangeSpec): boolean {
  const area = sheet.print.area
  if (!area) return false
  return range.toRow < area.fromRow || range.fromRow > area.toRow || range.toColumn < area.fromColumn || range.fromColumn > area.toColumn
}
