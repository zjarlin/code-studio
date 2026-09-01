import { createColumnHelper, tableFeatures, useTable, type RowData } from '@tanstack/react-table'
import { type KeyboardEvent, type ReactNode, useMemo } from 'react'

import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from '@/components/generated/shadcn/empty'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/generated/shadcn/table'

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
  const rows = table.getRowModel().rows

  if (!rows.length) {
    return (
      <Empty>
        <EmptyHeader>
          <EmptyTitle>暂无数据</EmptyTitle>
          <EmptyDescription>{emptyText}</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }

  return (
    <Table>
      <TableHeader>
        {table.getHeaderGroups().map((group) => (
          <TableRow key={group.id}>
            {group.headers.map((header, index) => (
              <TableHead key={header.id} style={{ width: definitions[index]?.width }}>
                {header.isPlaceholder ? null : <table.FlexRender header={header} />}
              </TableHead>
            ))}
          </TableRow>
        ))}
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow
            aria-selected={row.id === selectedRowId}
            data-state={row.id === selectedRowId ? 'selected' : undefined}
            key={row.id}
            onClick={() => onRowClick?.(row.original)}
            onKeyDown={(event) => activateRow(event, () => onRowClick?.(row.original))}
            tabIndex={onRowClick ? 0 : undefined}
          >
            {row.getAllCells().map((cell) => (
              <TableCell key={cell.id}><table.FlexRender cell={cell} /></TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function activateRow(event: KeyboardEvent<HTMLTableRowElement>, action: () => void) {
  if (event.key !== 'Enter' && event.key !== ' ') return
  event.preventDefault()
  action()
}
