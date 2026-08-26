import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import MetadataTableHead from './MetadataTableHead.vue'

describe('MetadataTableHead', () => {
  it('marks manual columns with a hand and a red semantic state', () => {
    const wrapper = mount(MetadataTableHead, {
      props: { mode: 'manual' },
      slots: { default: '属性名' },
    })

    expect(wrapper.attributes('data-metadata-entry')).toBe('manual')
    expect(wrapper.text()).toContain('✋🏻')
    expect(wrapper.classes()).toContain('metadata-semantic-head-manual')
  })

  it('renders agent columns without the manual marker', () => {
    const wrapper = mount(MetadataTableHead, {
      props: { mode: 'agent' },
      slots: { default: '注释', action: '<button aria-label="AI 调整注释" />' },
    })

    expect(wrapper.attributes('data-metadata-entry')).toBe('agent')
    expect(wrapper.text()).not.toContain('✋🏻')
    expect(wrapper.get('[aria-label="AI 调整注释"]').attributes('aria-label')).toBe('AI 调整注释')
  })
})
