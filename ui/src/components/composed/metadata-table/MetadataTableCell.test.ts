import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import MetadataTableCell from './MetadataTableCell.vue'
import type { MetadataTableDescriptor } from './metadata-table'

const rows: Record<string, unknown>[] = [{ code: 'one', label: '名称' }]
const descriptor: MetadataTableDescriptor<Record<string, unknown>> = {
  tableId: 'metadata.example',
  revision: 'r1',
  rowIdentityKey: 'code',
  rowKey: (row) => String(row.code),
  columns: [
    { key: 'code', label: '编码', kind: 'scalar', context: true },
    { key: 'label', label: '名称', kind: 'scalar', editable: true, context: false },
  ],
  operations: ['custom'],
}

describe('MetadataTableCell', () => {
  it('adds a row-scoped adjustment action to editable cells', () => {
    const wrapper = mount(MetadataTableCell, {
      props: { columnKey: 'label', descriptor, row: rows[0], rows },
      slots: { default: '<input value="名称">' },
      global: { stubs: { StructuredOutputSettings: true } },
    })

    expect(wrapper.get('[aria-label="AI 调整名称单元格"]').attributes('aria-label')).toBe('AI 调整名称单元格')
    expect(wrapper.find('structured-output-settings-stub').exists()).toBe(false)
  })

  it('does not add an action to context-only cells', () => {
    const wrapper = mount(MetadataTableCell, {
      props: { columnKey: 'code', descriptor, row: rows[0], rows },
    })

    expect(wrapper.find('[aria-label^="AI 调整"]').exists()).toBe(false)
  })
})
