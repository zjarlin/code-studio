import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import EditableMetadataGrid from './EditableMetadataGrid.vue'

describe('EditableMetadataGrid', () => {
  it('renders the configured rows and count', () => {
    const wrapper = mount(EditableMetadataGrid, {
      props: { columns: 1, count: 1, title: '字段' },
      slots: {
        header: '<thead><tr><th>名称</th></tr></thead>',
        body: '<tbody><tr><td>设备名称</td></tr></tbody>',
      },
    })

    expect(wrapper.text()).toContain('字段')
    expect(wrapper.text()).toContain('1')
    expect(wrapper.text()).toContain('设备名称')
  })

  it('renders an empty state without mounting body content', () => {
    const wrapper = mount(EditableMetadataGrid, {
      props: { columns: 2, count: 0, emptyText: '暂无字段', title: '字段' },
      slots: { body: '<span>不应出现</span>' },
    })

    expect(wrapper.text()).toContain('暂无字段')
    expect(wrapper.text()).not.toContain('不应出现')
  })
})
