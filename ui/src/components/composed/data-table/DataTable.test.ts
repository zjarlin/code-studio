import { mount } from '@vue/test-utils'
import { defineComponent, nextTick, ref } from 'vue'
import { describe, expect, it } from 'vitest'

import DataTable from './DataTable.vue'
import type { DataTableColumn } from './data-table'

interface ExampleRow {
  name: string
  type: string
}

const columns: DataTableColumn<ExampleRow>[] = [
  { accessorKey: 'name', header: '名称' },
  { accessorKey: 'type', header: '类型' },
]

describe('DataTable', () => {
  it('renders column definitions and reacts to row changes', async () => {
    const rows = ref<ExampleRow[]>([{ name: 'status', type: 'boolean' }])
    const host = defineComponent({
      components: { DataTable },
      setup() {
        return { columns, rows }
      },
      template: '<DataTable :columns="columns" :data="rows" />',
    })
    const wrapper = mount(host)

    expect(wrapper.text()).toContain('名称')
    expect(wrapper.text()).toContain('status')

    rows.value = [{ name: 'createdAt', type: 'timestamp' }]
    await nextTick()

    expect(wrapper.text()).not.toContain('status')
    expect(wrapper.text()).toContain('createdAt')
  })
})
