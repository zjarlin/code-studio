import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { TooltipProvider } from '@/components/generated/shadcn/tooltip'
import type { LsiLibraryFeature } from '@/types'
import LibraryFeatureTable from './LibraryFeatureTable.vue'

const feature: LsiLibraryFeature = {
  id: 2,
  libraryId: 7,
  parentId: 1,
  featureCode: 'inspection.plan',
  name: '巡检计划',
  description: '巡检计划配置',
}

describe('LibraryFeatureTable', () => {
  it('saves relation fields and renders a computed package', async () => {
    const saveRow = vi.fn().mockResolvedValue(feature)
    const host = defineComponent(() => () => h(TooltipProvider, null, {
      default: () => h(LibraryFeatureTable, {
        features: [feature],
        packagePrefix: 'com.example.application.catalog',
        createFeature: () => ({ ...feature, id: undefined }) as never,
        saveRow,
      }),
    }))
    const wrapper = mount(host)

    expect(wrapper.text()).not.toContain('功能编码')
    expect(wrapper.text()).toContain('com.example.application.catalog.inspection.plan')
    await wrapper.get('input[aria-label="巡检计划名称"]').setValue('巡检任务')
    await wrapper.get('button[aria-label="保存巡检任务"]').trigger('click')
    await flushPromises()

    expect(saveRow).toHaveBeenCalledWith(expect.objectContaining({
      id: 2,
      libraryId: 7,
      parentId: 1,
      featureCode: 'inspection.plan',
      name: '巡检任务',
    }))
  })
})
