import { flushPromises, shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import MetadataAssistantPanel from '@/components/composed/metadata-assistant/MetadataAssistantPanel.vue'

import ModelDesigner from './ModelDesigner.vue'
import ModelStudio from './ModelStudio.vue'

vi.mock('@/lowcode-api', () => ({
  LowcodeApi: class {
    schema = vi.fn().mockResolvedValue({
      components: {
        schemas: {
          LowcodeModelCommand: {
            properties: {},
            type: 'object',
          },
        },
      },
    })

    models = vi.fn().mockResolvedValue([])

    modelPage = vi.fn().mockResolvedValue({
      rows: [],
      totalPageCount: 0,
      totalRowCount: 0,
    })
  },
}))

describe('ModelStudio', () => {
  it('keeps the model assistant without a duplicate assistant workspace tab', () => {
    const wrapper = shallowMount(ModelStudio)
    const workspaceTabs = wrapper.get('[aria-label="模型工作区视图"]')

    expect(workspaceTabs.findAll('button').map((button) => button.text())).toEqual(['模型', '生成结果'])
    expect(wrapper.findComponent(MetadataAssistantPanel).exists()).toBe(true)
    expect(workspaceTabs.text()).not.toContain('智能体')
  })

  it('passes assistant field changes to the model designer for recent-change highlighting', async () => {
    const wrapper = shallowMount(ModelStudio)
    await flushPromises()

    wrapper.findComponent(MetadataAssistantPanel).vm.$emit('apply', {
      fields: [{
        fieldCode: 'userName',
        label: '用户名',
        kotlinType: 'String',
        dbColumn: 'user_name',
      }],
      queries: [],
      relations: [],
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.findComponent(ModelDesigner).props('recentChanges')).toEqual({
      sections: ['fields'],
      fieldKeys: ['code:userName'],
    })
  })
})
