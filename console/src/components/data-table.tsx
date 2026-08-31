import {
  createColumnHelper,
  tableFeatures,
  useTable,
  type RowData,
} from '@tanstack/react-table'
import { type ReactNode, useMemo } from 'react'

const features = tableFeatures({})

export interface DataColumn<T extends RowData> {
  key: keyof T & string
  header: string
  cell?: (value: T[keyof T], row: T) => ReactNode
  width?: string
}

export interface DataTableProps<T extends RowData> {
  columns: DataColumn<T>[]
  data: T[]
  emptyText: string
  getRowId: (row: T) => string
  onRowClick?: (row: T) => void
  selectedRowId?: string
}

export function DataTable<T extends RowData>({
  columns: definitions,
  data,
  emptyText,
  getRowId,
  onRowClick,
  selectedRowId,
}: DataTableProps<T>) {
  const columns = useMemo(() => {
    const helper = createColumnHelper<typeof features, T>()
    return helper.columns(definitions.map((definition) => helper.accessor(
      (row) => row[definition.key],
      {
        id: definition.key,
        header: definition.header,
        cell: ({ getValue, row }) => {
          const value = getValue() as T[keyof T]
          return definition.cell ? definition.cell(value, row.original) : String(value ?? '')
        },
      },
    )))
  }, [definitions])
  const table = useTable({ features, columns, data, getRowId })

  return (
    <div className="table-scroll">
      <table className="data-table">
        <thead>
          {table.getHeaderGroups().map((group) => (
            <tr key={group.id}>
              {group.headers.map((header, index) => (
                <th key={header.id} style={{ width: definitions[index]?.width }}>
                  {header.isPlaceholder ? null : <table.FlexRender header={header} />}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody>
          {table.getRowModel().rows.map((row) => (
            <tr
              aria-selected={row.id === selectedRowId}
              className={onRowClick ? 'clickable-row' : undefined}
              key={row.id}
              onClick={() => onRowClick?.(row.original)}
            >
              {row.getAllCells().map((cell) => (
                <td key={cell.id}><table.FlexRender cell={cell} /></td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {!table.getRowModel().rows.length && <div className="empty-state">{emptyText}</div>}
    </div>
  )
}
