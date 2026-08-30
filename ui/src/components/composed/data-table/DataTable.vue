<script setup lang="ts" generic="TData extends RowData">
import type { RowData } from '@tanstack/vue-table'
import { FlexRender, useTable } from '@tanstack/vue-table'
import { computed } from 'vue'

import {
  Table,
  TableBody,
  TableCell,
  TableEmpty,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/generated/shadcn/table'
import { dataTableFeatures, type DataTableColumn } from './data-table'

const props = withDefaults(defineProps<{
  columns: DataTableColumn<TData>[]
  data: readonly TData[]
  emptyText?: string
}>(), {
  emptyText: '暂无数据',
})

const columns = computed(() => props.columns)
const data = computed(() => props.data)
const table = useTable<typeof dataTableFeatures, TData>({
  features: dataTableFeatures,
  columns,
  data,
})
</script>

<template>
  <Table>
    <TableHeader>
      <TableRow v-for="headerGroup in table.getHeaderGroups()" :key="headerGroup.id">
        <TableHead v-for="header in headerGroup.headers" :key="header.id">
          <FlexRender v-if="!header.isPlaceholder" :header="header" />
        </TableHead>
      </TableRow>
    </TableHeader>
    <TableBody>
      <template v-if="table.getRowModel().rows.length">
        <TableRow v-for="row in table.getRowModel().rows" :key="row.id">
          <TableCell v-for="cell in row.getAllCells()" :key="cell.id">
            <FlexRender :cell="cell" />
          </TableCell>
        </TableRow>
      </template>
      <TableEmpty v-else :colspan="columns.length">{{ emptyText }}</TableEmpty>
    </TableBody>
  </Table>
</template>
