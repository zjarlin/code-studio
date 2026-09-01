import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { DataTable, type DataColumn } from './data-table'

interface Row {
  id: string
  name: string
  status: number
}

const rows: Row[] = [
  { id: 'library', name: 'Platform Core', status: 1 },
  { id: 'agent', name: 'Agent Runtime', status: 0 },
]

const columns: DataColumn<Row>[] = [
  { key: 'name', header: '名称' },
  { key: 'status', header: '状态', cell: (value) => value === 1 ? '启用' : '停用' },
]

describe('DataTable', () => {
  it('renders TanStack Table rows and forwards row selection', () => {
    const onRowClick = vi.fn()
    render(
      <DataTable
        columns={columns}
        data={rows}
        emptyText="无数据"
        getRowId={(row) => row.id}
        onRowClick={onRowClick}
        selectedRowId="library"
      />,
    )

    expect(screen.getByText('Platform Core').closest('tr')).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByText('停用')).toBeInTheDocument()
    fireEvent.click(screen.getByText('Agent Runtime'))
    expect(onRowClick).toHaveBeenCalledWith(rows[1])
  })
})
