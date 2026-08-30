import type { ColumnDef, RowData } from '@tanstack/vue-table'
import { tableFeatures } from '@tanstack/vue-table'

export const dataTableFeatures = tableFeatures({})

export type DataTableColumn<TData extends RowData> = ColumnDef<typeof dataTableFeatures, TData>
